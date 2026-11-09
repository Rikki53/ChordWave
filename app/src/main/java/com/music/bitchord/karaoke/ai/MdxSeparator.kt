package com.music.bitchord.karaoke.ai

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min

/**
 * MDX-Net (Kim Vocal 2) vocal separation, the Android/Kotlin port of
 * NextGenUp's `separate()`: STFT -> ONNX (CPU) -> iSTFT per 261120-sample
 * window, windows stitched with the same triangular overlap weights the
 * Python uses, instrumental = mix - vocal.
 *
 * The model file is supplied by the caller (it is a 66 MB download, not an
 * app asset); this class only ever holds one session and processes one
 * segment at a time.
 *
 * Two entry points:
 *  - [separateFull] — the original in-memory form, kept as the reference
 *    implementation (the JVM tests pin its numbers). It holds the whole
 *    song in float arrays: mix + vocal + weight + instrumental is
 *    ~370 MB for a 5-minute song, which exceeds the per-app heap limit
 *    of real phones (384 MB on the device that reported the failure).
 *  - [separateToFiles] — the production path used by the creation
 *    service (dev4). The mix is read from a raw float32 PCM file, the
 *    overlap-add accumulation lives in scratch files, and both stems are
 *    written as WAV block by block, so the heap footprint is a handful
 *    of segment-sized buffers (~50 MB) regardless of song length. The
 *    arithmetic — window schedule, weights, per-sample accumulation
 *    order, normalisation — is step-for-step the same as [separateFull],
 *    and a JVM load test asserts the streamed output matches the
 *    in-memory reference to within one PCM16 LSB.
 *
 * All segment workspaces (de-interleave buffers, model input/output) are
 * allocated once and reused; the ONNX input tensor wraps one direct
 * ByteBuffer reused for every segment (session.run is synchronous, so
 * the buffer is never in flight twice).
 *
 * Not thread-safe: Python serialises inference behind one lock, and a
 * single separator used from one worker thread is the equivalent shape.
 */
class MdxSeparator(modelFile: File) : AutoCloseable {

    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val session: OrtSession
    private val inputName: String
    private val stft = MdxStft()

    // ---- Reused segment workspaces ------------------------------------------

    private val tensorShape =
        longArrayOf(1, 4, MdxConstants.DIM_F.toLong(), MdxConstants.DIM_T.toLong())
    private val tensorFloats = 4 * MdxConstants.DIM_F * MdxConstants.DIM_T
    private val segLeft = FloatArray(MdxConstants.SEG)
    private val segRight = FloatArray(MdxConstants.SEG)
    private val modelOut = FloatArray(tensorFloats)
    // The tensor is created from the FloatBuffer view: ORT infers the
    // element type from the buffer type, and a raw ByteBuffer would be
    // read as uint8 data. The view is backed by one direct buffer, so
    // inference still gets the data without a copy.
    private val inputFloats: java.nio.FloatBuffer =
        ByteBuffer.allocateDirect(tensorFloats * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()

    init {
        require(modelFile.isFile) { "MDX model not found: ${modelFile.absolutePath}" }
        val options = OrtSession.SessionOptions().apply {
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
        }
        session = env.createSession(modelFile.absolutePath, options)
        inputName = session.inputNames.first()
    }

    constructor(modelPath: String) : this(File(modelPath))

    override fun close() {
        session.close()
    }

    /**
     * One model window: [inputSeg] is interleaved stereo; the segment fed
     * to the model is exactly [MdxConstants.SEG] frames (zero-padded if
     * shorter, truncated if longer). Returns the vocal stem as interleaved
     * stereo with the same frame count as the input.
     */
    fun separateSegment(inputSeg: FloatArray): FloatArray {
        require(inputSeg.size % 2 == 0) { "interleaved stereo needs an even sample count" }
        val frames = inputSeg.size / 2
        val vocal = segmentVocal(inputSeg, frames)
        return vocal.copyOf(frames * 2)
    }

    /**
     * The vocal segment for [inputSeg]'s first [frames] frames, in the
     * shared SEG-sized workspace (valid in the first
     * min(frames, SEG) * 2 slots; overwritten by the next call).
     */
    private fun segmentVocal(inputSeg: FloatArray, frames: Int): FloatArray {
        val l = min(frames, MdxConstants.SEG)
        for (i in 0 until MdxConstants.SEG) {
            if (i < l) {
                segLeft[i] = inputSeg[i * 2]
                segRight[i] = inputSeg[i * 2 + 1]
            } else {
                segLeft[i] = 0f
                segRight[i] = 0f
            }
        }
        val tensorData = stft.stftToModelInput(segLeft, segRight)
        val output = runModel(tensorData)
        return stft.istftFromModelOutput(output)
    }

    private fun runModel(tensorData: FloatArray): FloatArray {
        inputFloats.clear()
        inputFloats.put(tensorData)
        inputFloats.position(0)
        OnnxTensor.createTensor(env, inputFloats, tensorShape).use { tensor ->
            session.run(mapOf(inputName to tensor)).use { results ->
                val buffer = (results[0] as OnnxTensor).floatBuffer
                buffer.get(modelOut)
                return modelOut
            }
        }
    }

    /**
     * Whole-song separation, stitching windows exactly as the Python does:
     * positions every [MdxConstants.STEP] samples, triangular weights
     * (linear 0->1 over the first [MdxConstants.OVERLAP] samples when not
     * the first window, 1->0 over the last when not the last), weighted
     * accumulation divided by the total weight (clamped at 1e-8), and
     * instrumental = mix - vocal.
     *
     * [progress] receives the finished fraction after each window;
     * when [cancelled] returns true the stitch stops early and the
     * partially covered region is still normalised by its weights.
     *
     * Returns (vocal, instrumental), both interleaved stereo, same length
     * as [mix].
     */
    fun separateFull(
        mix: FloatArray,
        progress: (Float) -> Unit = {},
        cancelled: () -> Boolean = { false },
    ): Pair<FloatArray, FloatArray> {
        require(mix.size % 2 == 0) { "interleaved stereo needs an even sample count" }
        val n = mix.size / 2
        val vocal = FloatArray(mix.size)
        val weight = FloatArray(n)
        if (n == 0) return Pair(vocal, FloatArray(0))

        var pos = 0
        var index = 0
        val positions = generateSequence(0) { it + MdxConstants.STEP }.takeWhile { it < n }.toList()
        while (pos < n) {
            if (cancelled()) break
            val l = min(MdxConstants.SEG, n - pos)
            val seg = FloatArray(l * 2)
            mix.copyInto(seg, 0, pos * 2, pos * 2 + l * 2)
            val v = separateSegment(seg)

            val ramp = min(MdxConstants.OVERLAP, l)
            val w = FloatArray(l) { 1f }
            if (pos > 0 && ramp > 1) {
                for (i in 0 until ramp) w[i] = i.toFloat() / (ramp - 1)
            } else if (pos > 0 && ramp == 1) {
                w[0] = 0f
            }
            if (pos + MdxConstants.SEG < n && ramp > 1) {
                for (j in 0 until ramp) {
                    val idx = l - ramp + j
                    val down = 1f - j.toFloat() / (ramp - 1)
                    if (down < w[idx]) w[idx] = down
                }
            }
            for (i in 0 until l) {
                val wi = w[i]
                vocal[(pos + i) * 2] += v[i * 2] * wi
                vocal[(pos + i) * 2 + 1] += v[i * 2 + 1] * wi
                weight[pos + i] += wi
            }
            index++
            progress(index.toFloat() / positions.size)
            pos += MdxConstants.STEP
        }
        val instrumental = FloatArray(mix.size)
        for (i in 0 until n) {
            val denom = max(weight[i].toDouble(), 1e-8).toFloat()
            val vl = vocal[i * 2] / denom
            val vr = vocal[i * 2 + 1] / denom
            vocal[i * 2] = vl
            vocal[i * 2 + 1] = vr
            instrumental[i * 2] = mix[i * 2] - vl
            instrumental[i * 2 + 1] = mix[i * 2 + 1] - vr
        }
        return Pair(vocal, instrumental)
    }

    // ---- Streaming whole-song separation (dev4) ------------------------------

    /**
     * [separateFull]'s arithmetic without the song-length arrays.
     *
     * [mixPcm] is the decoded song as raw float32 little-endian interleaved
     * stereo at 44.1 kHz with exactly [frames] frames. The vocal overlap-add
     * and the weight sums live in two scratch files inside [scratchDir]
     * (deleted on exit, whatever happens); [vocalWav] and [instWav] are
     * written as complete WAV PCM16 files (the caller passes its `.part`
     * paths, so a failure still leaves nothing half-renamed behind).
     *
     * Returns true when the whole song was processed. When [cancelled]
     * fires, the stitch stops as soon as the current window finishes and
     * the method returns false with the outputs incomplete — the caller
     * treats that as a cancellation and discards them, exactly as it did
     * with the in-memory path.
     */
    fun separateToFiles(
        mixPcm: File,
        frames: Long,
        vocalWav: File,
        instWav: File,
        scratchDir: File,
        progress: (Float) -> Unit = {},
        cancelled: () -> Boolean = { false },
    ): Boolean {
        require(frames >= 0 && frames <= Int.MAX_VALUE) { "song too long for the streaming path" }
        val n = frames.toInt()
        if (n == 0) {
            Wav16FileWriter(vocalWav, 0).close()
            Wav16FileWriter(instWav, 0).close()
            return true
        }
        scratchDir.mkdirs()
        val accFile = File(scratchDir, vocalWav.name + ".acc")
        val wgtFile = File(scratchDir, vocalWav.name + ".wgt")
        val mix = RandomAccessFile(mixPcm, "r")
        val acc = RandomAccessFile(accFile, "rw")
        val wgt = RandomAccessFile(wgtFile, "rw")
        try {
            // setLength zero-fills: the accumulation starts from silence,
            // exactly like a fresh FloatArray.
            acc.setLength(n.toLong() * 8)
            wgt.setLength(n.toLong() * 4)

            val ioBuf = ByteBuffer.allocate(MdxConstants.SEG * 2 * 4).order(ByteOrder.LITTLE_ENDIAN)
            val segIn = FloatArray(MdxConstants.SEG * 2)
            val accBlock = FloatArray(MdxConstants.SEG * 2)
            val wgtBlock = FloatArray(MdxConstants.SEG)
            val w = FloatArray(MdxConstants.SEG)

            val totalWindows = (n - 1) / MdxConstants.STEP + 1
            var pos = 0
            var index = 0
            while (pos < n) {
                if (cancelled()) return false
                val l = min(MdxConstants.SEG, n - pos)
                readFloats(mix, pos.toLong() * 8, segIn, l * 2, ioBuf)
                val v = segmentVocal(segIn, l)

                // The triangular weights, identical to separateFull.
                val ramp = min(MdxConstants.OVERLAP, l)
                for (i in 0 until l) w[i] = 1f
                if (pos > 0 && ramp > 1) {
                    for (i in 0 until ramp) w[i] = i.toFloat() / (ramp - 1)
                } else if (pos > 0 && ramp == 1) {
                    w[0] = 0f
                }
                if (pos + MdxConstants.SEG < n && ramp > 1) {
                    for (j in 0 until ramp) {
                        val idx = l - ramp + j
                        val down = 1f - j.toFloat() / (ramp - 1)
                        if (down < w[idx]) w[idx] = down
                    }
                }
                readFloats(acc, pos.toLong() * 8, accBlock, l * 2, ioBuf)
                readFloats(wgt, pos.toLong() * 4, wgtBlock, l, ioBuf)
                for (i in 0 until l) {
                    val wi = w[i]
                    accBlock[i * 2] += v[i * 2] * wi
                    accBlock[i * 2 + 1] += v[i * 2 + 1] * wi
                    wgtBlock[i] += wi
                }
                writeFloats(acc, pos.toLong() * 8, accBlock, l * 2, ioBuf)
                writeFloats(wgt, pos.toLong() * 4, wgtBlock, l, ioBuf)
                index++
                progress(index.toFloat() / totalWindows)
                pos += MdxConstants.STEP
            }

            // Finalise block by block: normalise by the summed weights and
            // split instrumental = mix - vocal, same math as separateFull.
            val blockFrames = 16384
            val mixBlock = FloatArray(blockFrames * 2)
            val accB = FloatArray(blockFrames * 2)
            val wgtB = FloatArray(blockFrames)
            val vocOut = FloatArray(blockFrames * 2)
            val instOut = FloatArray(blockFrames * 2)
            Wav16FileWriter(vocalWav, n.toLong()).use { vw ->
                Wav16FileWriter(instWav, n.toLong()).use { iw ->
                    var at = 0
                    while (at < n) {
                        if (cancelled()) return false
                        val cnt = min(blockFrames, n - at)
                        readFloats(mix, at.toLong() * 8, mixBlock, cnt * 2, ioBuf)
                        readFloats(acc, at.toLong() * 8, accB, cnt * 2, ioBuf)
                        readFloats(wgt, at.toLong() * 4, wgtB, cnt, ioBuf)
                        for (i in 0 until cnt) {
                            val denom = max(wgtB[i].toDouble(), 1e-8).toFloat()
                            val vl = accB[i * 2] / denom
                            val vr = accB[i * 2 + 1] / denom
                            vocOut[i * 2] = vl
                            vocOut[i * 2 + 1] = vr
                            instOut[i * 2] = mixBlock[i * 2] - vl
                            instOut[i * 2 + 1] = mixBlock[i * 2 + 1] - vr
                        }
                        vw.write(vocOut, cnt * 2)
                        iw.write(instOut, cnt * 2)
                        at += cnt
                    }
                }
            }
            return true
        } finally {
            runCatching { mix.close() }
            runCatching { acc.close() }
            runCatching { wgt.close() }
            accFile.delete()
            wgtFile.delete()
        }
    }

    /** Reads [count] floats starting at byte offset [bytePos] into [dest]. */
    private fun readFloats(
        raf: RandomAccessFile,
        bytePos: Long,
        dest: FloatArray,
        count: Int,
        ioBuf: ByteBuffer,
    ) {
        raf.seek(bytePos)
        val bytes = ioBuf.array()
        val total = count * 4
        var done = 0
        while (done < total) {
            val r = raf.read(bytes, done, total - done)
            check(r >= 0) { "unexpected end of PCM scratch file" }
            done += r
        }
        for (k in 0 until count) dest[k] = ioBuf.getFloat(k * 4)
    }

    /** Writes [count] floats from [src] at byte offset [bytePos]. */
    private fun writeFloats(
        raf: RandomAccessFile,
        bytePos: Long,
        src: FloatArray,
        count: Int,
        ioBuf: ByteBuffer,
    ) {
        for (k in 0 until count) ioBuf.putFloat(k * 4, src[k])
        raf.seek(bytePos)
        raf.write(ioBuf.array(), 0, count * 4)
    }
}
