package com.music.bitchord.karaoke.ai

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.File
import java.nio.FloatBuffer
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
 * segment at a time, so peak extra memory stays at a handful of segment-
 * sized buffers plus the model's own activations.
 *
 * Not thread-safe: Python serialises inference behind one lock, and a
 * single separator used from one worker thread is the equivalent shape.
 */
class MdxSeparator(modelFile: File) : AutoCloseable {

    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val session: OrtSession
    private val inputName: String
    private val stft = MdxStft()

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
        val padded = FloatArray(MdxConstants.SEG * 2)
        inputSeg.copyInto(padded, 0, 0, min(inputSeg.size, padded.size))
        val left = FloatArray(MdxConstants.SEG)
        val right = FloatArray(MdxConstants.SEG)
        for (i in 0 until MdxConstants.SEG) {
            left[i] = padded[i * 2]
            right[i] = padded[i * 2 + 1]
        }
        val tensorData = stft.stftToModelInput(left, right)
        val modelOutput = runModel(tensorData)
        val vocal = stft.istftFromModelOutput(modelOutput)
        return vocal.copyOf(frames * 2)
    }

    private fun runModel(tensorData: FloatArray): FloatArray {
        val shape = longArrayOf(1, 4, MdxConstants.DIM_F.toLong(), MdxConstants.DIM_T.toLong())
        OnnxTensor.createTensor(env, FloatBuffer.wrap(tensorData), shape).use { tensor ->
            session.run(mapOf(inputName to tensor)).use { results ->
                val outTensor = results[0] as OnnxTensor
                val buffer = outTensor.floatBuffer
                val out = FloatArray(buffer.remaining())
                buffer.get(out)
                return out
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
}
