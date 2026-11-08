package com.music.bitchord.karaoke.ai

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Numerical parity gate for the MDX-Net port: the Kotlin pipeline, run on
 * the JVM with the desktop ONNX Runtime and the real Kim Vocal 2 model,
 * must reproduce the Python reference segment in ~/ailab/mdx-parity
 * (assets generated from NextGenUp's audio_engine, single segment, no
 * inter-window stitching).
 *
 * Pass bar (from spec.md): Pearson correlation >= 0.999 per channel AND
 * combined RMS error <= 1e-3 on the float -1..1 scale, against the WAV
 * reference (s16 / 32768). The same metrics against the pre-quantisation
 * float32 .npy reference are printed and asserted too when it is present.
 *
 * This test is skipped (via an early return with a loud message) only when
 * the parity assets are not on this machine at all; on the Dell build box
 * they are, and there the assertions are the contract.
 */
class MdxParityTest {

    @Test
    fun fftMatchesNaiveDftAt7680() {
        val n = 7680
        val re = DoubleArray(n) { i -> sin(i * 0.37) + 0.3 * cos(i * 1.7) }
        val im = DoubleArray(n)
        // Naive DFT at a sample of bins, including DC, Nyquist and edges of
        // the model's 3072-bin crop.
        val bins = intArrayOf(0, 1, 2, 17, 100, 1024, 3071, 3072, 3839, 3840, 5000, 7679)
        val expectedRe = HashMap<Int, Double>()
        val expectedIm = HashMap<Int, Double>()
        for (k in bins) {
            var sr = 0.0
            var si = 0.0
            for (t in 0 until n) {
                val ang = -2.0 * PI * ((k.toLong() * t) % n) / n
                sr += re[t] * cos(ang)
                si += re[t] * sin(ang)
            }
            expectedRe[k] = sr
            expectedIm[k] = si
        }
        val origRe = re.copyOf()
        ComplexFft.forward(re, im)
        for (k in bins) {
            val er = expectedRe.getValue(k)
            val ei = expectedIm.getValue(k)
            val tol = 1e-6 * maxOf(1.0, sqrt(er * er + ei * ei))
            assertEquals("FFT real bin $k", er, re[k], tol)
            assertEquals("FFT imag bin $k", ei, im[k], tol)
        }
        // Round trip: inverse(forward(x)) == x.
        ComplexFft.inverse(re, im)
        var maxErr = 0.0
        for (i in 0 until n) {
            maxErr = maxOf(maxErr, kotlin.math.abs(re[i] - origRe[i]), kotlin.math.abs(im[i]))
        }
        println("MDX-PARITY fft roundtrip maxErr=$maxErr")
        assertTrue("FFT round-trip maxErr $maxErr", maxErr < 1e-9)
    }

    @Test(timeout = 1_800_000)
    fun separateSegmentMatchesPythonReference() {
        val home = System.getProperty("user.home")
        val parityDir = listOf(
            File(home, "ailab/mdx-parity"),
            File("/home/riki-nurjaman/ailab/mdx-parity"),
        ).firstOrNull { File(it, "input_seg.wav").isFile }
        if (parityDir == null) {
            println("MDX-PARITY SKIPPED: ~/ailab/mdx-parity not found on this machine")
            return
        }
        val model = listOf(
            File(home, "ailab/nextgenUp/models/kim_vocal_2.onnx"),
            File("/home/riki-nurjaman/ailab/nextgenUp/models/kim_vocal_2.onnx"),
        ).firstOrNull { it.isFile }
        if (model == null) {
            fail("kim_vocal_2.onnx not found")
            return
        }

        val input = readWavStereoS16(File(parityDir, "input_seg.wav"))
        val refWav = readWavStereoS16(File(parityDir, "ref_vocal_seg.wav"))
        assertEquals(261120 * 2, input.size)
        assertEquals(261120 * 2, refWav.size)

        val started = System.nanoTime()
        val vocal = MdxSeparator(model).use { it.separateSegment(input) }
        val seconds = (System.nanoTime() - started) / 1e9

        val report = StringBuilder()
        fun check(tag: String, ref: FloatArray): Pair<Double, Double> {
            var corrMin = Double.MAX_VALUE
            var sqErr = 0.0
            for (ch in 0 until 2) {
                var sx = 0.0; var sy = 0.0; var sxx = 0.0; var syy = 0.0; var sxy = 0.0
                var count = 0
                var i = ch
                while (i < ref.size) {
                    val a = vocal[i].toDouble()
                    val b = ref[i].toDouble()
                    sx += a; sy += b; sxx += a * a; syy += b * b; sxy += a * b
                    count++
                    i += 2
                }
                val corr = (count * sxy - sx * sy) /
                    sqrt((count * sxx - sx * sx) * (count * syy - sy * sy))
                corrMin = minOf(corrMin, corr)
                report.append("MDX-PARITY [$tag] channel $ch correlation=$corr\n")
            }
            for (i in ref.indices) {
                val d = (vocal[i] - ref[i]).toDouble()
                sqErr += d * d
            }
            val rmsErr = sqrt(sqErr / ref.size)
            report.append("MDX-PARITY [$tag] combined rmsError=$rmsErr corrMin=$corrMin\n")
            return Pair(corrMin, rmsErr)
        }

        val (corrWav, rmsWav) = check("wav-ref", refWav)
        var npyLine = "npy-ref: absent"
        val npyFile = File(parityDir, "ref_vocal_seg_f32.npy")
        if (npyFile.isFile) {
            // .npy is (2, 261120) channel-major; interleave it.
            val planar = readNpyFloat32(npyFile)
            val refNpy = FloatArray(planar.size)
            val frames = planar.size / 2
            for (f in 0 until frames) {
                refNpy[f * 2] = planar[f]
                refNpy[f * 2 + 1] = planar[frames + f]
            }
            val (corrNpy, rmsNpy) = check("npy-ref", refNpy)
            npyLine = "npy-ref corrMin=$corrNpy rmsError=$rmsNpy"
            assertTrue("npy corr $corrNpy < 0.999", corrNpy >= 0.999)
            assertTrue("npy rmsErr $rmsNpy > 1e-3", rmsNpy <= 1e-3)
        }
        report.append("MDX-PARITY elapsedSeconds=$seconds $npyLine\n")
        print(report.toString())
        runCatching {
            val outDir = File("build/mdx-parity").apply { mkdirs() }
            File(outDir, "results.txt").writeText(report.toString())
        }
        assertTrue("wav corr $corrWav < 0.999", corrWav >= 0.999)
        assertTrue("wav rmsErr $rmsWav > 1e-3", rmsWav <= 1e-3)
    }

    private fun readWavStereoS16(file: File): FloatArray {
        val bytes = file.readBytes()
        require(bytes.size > 44 && bytes[0] == 'R'.code.toByte() && bytes[8] == 'W'.code.toByte()) {
            "not a RIFF/WAVE file: $file"
        }
        var offset = 12
        var dataOff = -1
        var dataLen = 0
        var channels = 2
        var bits = 16
        fun u32(o: Int) = ByteBuffer.wrap(bytes, o, 4).order(ByteOrder.LITTLE_ENDIAN).int
        fun u16(o: Int) = ByteBuffer.wrap(bytes, o, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 0xFFFF
        while (offset + 8 <= bytes.size) {
            val id = String(bytes, offset, 4, Charsets.US_ASCII)
            val size = u32(offset + 4)
            when (id) {
                "fmt " -> {
                    channels = u16(offset + 10)
                    bits = u16(offset + 22)
                }
                "data" -> {
                    dataOff = offset + 8
                    dataLen = size
                    break
                }
            }
            offset += 8 + size + (size and 1)
        }
        require(dataOff >= 0 && channels == 2 && bits == 16) {
            "expected stereo s16 WAV, got ch=$channels bits=$bits in $file"
        }
        val count = dataLen / 2
        val out = FloatArray(count)
        // NOTE: wrap(bytes, off, len) does NOT shift absolute gets — use a
        // sliced duplicate so index 0 is the data chunk.
        val buf = ByteBuffer.wrap(bytes, dataOff, dataLen).slice().order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until count) out[i] = buf.getShort(i * 2) / 32768f
        return out
    }

    private fun readNpyFloat32(file: File): FloatArray {
        val bytes = file.readBytes()
        require(bytes[0] == 0x93.toByte() && String(bytes, 1, 5, Charsets.US_ASCII) == "NUMPY") {
            "not an .npy file: $file"
        }
        val major = bytes[6].toInt()
        val headerLen: Int
        val dataOff: Int
        if (major == 1) {
            headerLen = ByteBuffer.wrap(bytes, 8, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 0xFFFF
            dataOff = 10 + headerLen
        } else {
            headerLen = ByteBuffer.wrap(bytes, 8, 4).order(ByteOrder.LITTLE_ENDIAN).int
            dataOff = 12 + headerLen
        }
        val header = String(bytes, dataOff - headerLen, headerLen, Charsets.US_ASCII)
        require(header.contains("'<f4'") || header.contains("\"<f4\"")) { "expected <f4 npy: $header" }
        require(!header.contains("'fortran_order': True")) { "fortran-order npy unsupported" }
        val count = (bytes.size - dataOff) / 4
        val out = FloatArray(count)
        val buf = ByteBuffer.wrap(bytes, dataOff, bytes.size - dataOff).slice().order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until count) out[i] = buf.getFloat(i * 4)
        return out
    }
}
