package com.music.bitchord.karaoke.ai

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Dev4 regression gate for the phone OOM in AI karaoke creation.
 *
 * On the Dell build box (skipped loudly elsewhere, like [MdxParityTest]):
 *
 *  1. A synthetic 5-minute stereo song is separated in-process by the
 *     ORIGINAL in-memory implementation ([MdxSeparator.separateFull]) —
 *     the reference.
 *  2. A child JVM capped at -Xmx384m — the heap growth limit of the
 *     phone that failed — runs the NEW streaming path
 *     ([MdxSeparator.separateToFiles]) on the same song. It must finish,
 *     and its stems must match the reference to <= 1 PCM16 LSB with
 *     correlation >= 0.999999 (the streaming arithmetic is step-for-step
 *     the reference's, so anything worse is a real regression).
 *  3. A second child with the same cap runs the OLD pipeline (whole song
 *     in a FloatArray + separateFull). It must die with OutOfMemoryError,
 *     reproducing the reported failure and proving the cap is honest.
 *
 * A second test pins the streaming resampler used for non-44.1 kHz
 * sources to the in-memory one, sample for sample.
 */
class MdxStreamingLoadTest {

    private fun modelFile(): File? = listOf(
        File(System.getProperty("user.home"), "ailab/nextgenUp/models/kim_vocal_2.onnx"),
        File("/home/riki-nurjaman/ailab/nextgenUp/models/kim_vocal_2.onnx"),
    ).firstOrNull { it.isFile }

    @Test(timeout = 3_600_000)
    fun fiveMinuteSongStreamsWithinPhoneHeapAndMatchesReference() {
        val model = modelFile()
        if (model == null) {
            println("MDX-LOAD SKIPPED: kim_vocal_2.onnx not found on this machine")
            return
        }
        val sr = MdxConstants.SAMPLE_RATE
        val frames = 300 * sr // 5 minutes = 13,230,000 frames
        val work = File("build/mdx-load").apply { mkdirs() }
        val pcm = File(work, "mix5min.f32")
        writeSyntheticPcm(pcm, frames, sr)
        println("MDX-LOAD input written: ${pcm.length()} bytes")

        // 1) In-memory reference.
        val mix = readPcm(pcm, frames)
        val t0 = System.nanoTime()
        val (refVocal, refInst) = MdxSeparator(model).use { it.separateFull(mix) }
        val refSecs = (System.nanoTime() - t0) / 1e9
        val refVocalWav = File(work, "ref_vocal.wav")
        val refInstWav = File(work, "ref_inst.wav")
        Wav16FileWriter(refVocalWav, frames.toLong()).use { it.write(refVocal, refVocal.size) }
        Wav16FileWriter(refInstWav, frames.toLong()).use { it.write(refInst, refInst.size) }
        println("MDX-LOAD reference separateFull seconds=$refSecs")

        // 2) Streaming child under the phone's heap cap.
        val newDir = File(work, "child-new")
        val newResult = runChild("NEW", model, pcm, frames, newDir)
        println("MDX-LOAD child-new exit=${newResult.first}\n${newResult.second}")
        assertEquals("streaming child must succeed under -Xmx384m", 0, newResult.first)
        assertWavClose(refVocalWav, File(newDir, "vocal.wav"), "vocal")
        assertWavClose(refInstWav, File(newDir, "inst.wav"), "inst")

        // 3) Old-pipeline child under the same cap must OOM.
        val oldDir = File(work, "child-old")
        val oldResult = runChild("OLD", model, pcm, frames, oldDir)
        println("MDX-LOAD child-old exit=${oldResult.first}\n${oldResult.second}")
        assertEquals("old pipeline must OOM under -Xmx384m (exit 3)", 3, oldResult.first)
        assertTrue(
            "old child output must name the OOM",
            oldResult.second.contains("CHILD-OLD OOM"),
        )
    }

    @Test
    fun resamplePcmFileMatchesInMemoryResample() {
        val cases = listOf(
            100_003 to 48_000,
            44_100 to 22_050,
            1 to 48_000,
            2 to 48_000,
            261_120 to 48_000,
        )
        val work = File("build/mdx-load").apply { mkdirs() }
        for ((framesIn, from) in cases) {
            val input = FloatArray(framesIn * 2) { idx ->
                (sin(idx * 0.013) * 0.4 + sin(idx * 0.11) * 0.2).toFloat()
            }
            val expected = AudioPcm.resampleLinear(input, from, MdxConstants.SAMPLE_RATE)
            val inF = File(work, "rs_in.f32")
            val outF = File(work, "rs_out.f32")
            inF.outputStream().buffered().use { out ->
                val bb = ByteBuffer.allocate(input.size * 4).order(ByteOrder.LITTLE_ENDIAN)
                bb.asFloatBuffer().put(input)
                out.write(bb.array())
            }
            val outFrames = AudioPcm.resamplePcmFile(
                inF, framesIn.toLong(), from, MdxConstants.SAMPLE_RATE, outF,
            )
            assertEquals("outFrames rate=$from", (expected.size / 2).toLong(), outFrames)
            val actual = readPcm(outF, outFrames.toInt())
            assertEquals("size rate=$from", expected.size, actual.size)
            for (i in expected.indices) {
                if (expected[i] != actual[i]) {
                    fail("resample mismatch rate=$from sample=$i expected=${expected[i]} actual=${actual[i]}")
                }
            }
            println("MDX-LOAD resample $from->$44100 frames=$framesIn exact")
        }
    }

    // ---- Helpers --------------------------------------------------------------

    /** Deterministic synthetic stereo: layered sines with slow gating. */
    private fun writeSyntheticPcm(file: File, frames: Int, sr: Int) {
        val block = 16384
        val buf = FloatArray(block * 2)
        val bytes = ByteBuffer.allocate(block * 8).order(ByteOrder.LITTLE_ENDIAN)
        file.outputStream().buffered().use { out ->
            var at = 0
            while (at < frames) {
                val cnt = minOf(block, frames - at)
                for (j in 0 until cnt) {
                    val t = (at + j).toDouble() / sr
                    val gateL = 0.55 + 0.45 * sin(2.0 * PI * 0.37 * t)
                    val gateR = 0.55 + 0.45 * sin(2.0 * PI * 0.29 * t + 1.3)
                    val l = 0.26 * sin(2.0 * PI * 196.0 * t) +
                        0.20 * sin(2.0 * PI * 523.25 * t) * gateL +
                        0.16 * sin(2.0 * PI * 880.0 * t + 0.7) * gateR
                    val r = 0.24 * sin(2.0 * PI * 146.83 * t) +
                        0.20 * sin(2.0 * PI * 659.25 * t) * gateR +
                        0.15 * sin(2.0 * PI * 1046.5 * t + 2.1) * gateL
                    buf[j * 2] = l.toFloat().coerceIn(-0.95f, 0.95f)
                    buf[j * 2 + 1] = r.toFloat().coerceIn(-0.95f, 0.95f)
                }
                bytes.clear()
                bytes.asFloatBuffer().put(buf, 0, cnt * 2)
                out.write(bytes.array(), 0, cnt * 8)
                at += cnt
            }
        }
    }

    private fun readPcm(file: File, frames: Int): FloatArray {
        val data = FloatArray(frames * 2)
        var idx = 0
        file.inputStream().buffered().use { ins ->
            val buf = ByteArray(1 shl 20)
            val bb = ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN)
            while (true) {
                val r = ins.read(buf)
                if (r < 0) break
                var k = 0
                while (k < r) {
                    data[idx++] = bb.getFloat(k)
                    k += 4
                }
            }
        }
        check(idx == data.size) { "PCM size mismatch: $idx != ${data.size}" }
        return data
    }

    /** Forks [MdxStreamingChildMain] with the phone's heap cap. */
    private fun runChild(
        mode: String,
        model: File,
        pcm: File,
        frames: Int,
        dir: File,
    ): Pair<Int, String> {
        dir.mkdirs()
        val javaBin = File(System.getProperty("java.home"), "bin/java").absolutePath
        val classPath = buildString {
            append(System.getProperty("java.class.path"))
            // Belt and braces: some Gradle workers expose a trimmed
            // java.class.path; anchor the entries we cannot live without.
            val anchors = listOf(
                MdxSeparator::class.java,
                ai.onnxruntime.OrtEnvironment::class.java,
                kotlin.Unit::class.java,
            )
            for (anchor in anchors) {
                val loc = anchor.protectionDomain?.codeSource?.location
                if (loc != null) {
                    append(File.pathSeparator)
                    append(File(loc.toURI()).absolutePath)
                }
            }
        }
        val proc = ProcessBuilder(
            javaBin, "-Xmx384m", "-cp", classPath,
            "com.music.bitchord.karaoke.ai.MdxStreamingChildMain",
            mode, model.absolutePath, pcm.absolutePath, frames.toString(), dir.absolutePath,
        ).redirectErrorStream(true).start()
        val output = proc.inputStream.bufferedReader().readText()
        val finished = proc.waitFor(50, java.util.concurrent.TimeUnit.MINUTES)
        if (!finished) {
            proc.destroyForcibly()
            fail("child $mode did not finish in 50 minutes")
        }
        return proc.exitValue() to output
    }

    /** Compares two canonical-header WAVs written by [Wav16FileWriter]. */
    private fun assertWavClose(ref: File, actual: File, tag: String) {
        assertEquals("$tag length", ref.length(), actual.length())
        val a = readS16(ref)
        val b = readS16(actual)
        assertEquals("$tag samples", a.size, b.size)
        var maxDiff = 0
        var sx = 0.0; var sy = 0.0; var sxx = 0.0; var syy = 0.0; var sxy = 0.0
        for (i in a.indices) {
            val d = abs(a[i] - b[i])
            if (d > maxDiff) maxDiff = d
            val x = a[i].toDouble(); val y = b[i].toDouble()
            sx += x; sy += y; sxx += x * x; syy += y * y; sxy += x * y
        }
        val corr = (a.size * sxy - sx * sy) / sqrt((a.size * sxx - sx * sx) * (a.size * syy - sy * sy))
        println("MDX-LOAD wav-compare [$tag] maxDiffLsb=$maxDiff correlation=$corr")
        assertTrue("$tag maxDiff $maxDiff > 1 LSB", maxDiff <= 1)
        assertTrue("$tag correlation $corr < 0.999999", corr >= 0.999999)
    }

    private fun readS16(file: File): ShortArray {
        val bytes = file.readBytes()
        require(bytes.size > 44) { "wav too small: $file" }
        val count = (bytes.size - 44) / 2
        val out = ShortArray(count)
        val buf = ByteBuffer.wrap(bytes, 44, bytes.size - 44).order(ByteOrder.LITTLE_ENDIAN)
        buf.asShortBuffer().get(out)
        return out
    }
}
