package com.music.bitchord.karaoke.ai

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Complex FFT for arbitrary lengths, computed in Double and used by the
 * MDX-Net STFT, whose size (7680 = 2^9 * 3 * 5) is not a power of two.
 *
 * Powers of two go through an iterative radix-2 transform. Every other
 * length goes through Bluestein's chirp-Z algorithm, which rewrites the
 * DFT as a convolution evaluated with a power-of-two FFT of length
 * >= 2n - 1 (16384 for n = 7680). Bluestein was chosen over a hand-written
 * mixed-radix 2/3/5 factorisation because the chirp identity is short,
 * easy to verify against a naive DFT, and — with the convolution kernel
 * precomputed once per length — fast enough for a 256-frame segment.
 * All internal arithmetic is Double; callers cast to Float at the edges,
 * which keeps the accumulated error far below the parity tolerance.
 *
 * The inverse transform is the conjugate trick over the forward one:
 * IFFT(x) = conj(FFT(conj(x))) / n, so there is exactly one DFT
 * implementation to get right.
 */
object ComplexFft {

    /** Forward DFT, in place: X[k] = sum_n x[n] * exp(-2 pi i n k / N). */
    fun forward(re: DoubleArray, im: DoubleArray) {
        require(re.size == im.size) { "re/im size mismatch" }
        if (re.size <= 1) return
        if (isPowerOfTwo(re.size)) {
            fftPowerOfTwo(re, im, inverse = false)
        } else {
            bluesteinForward(re, im, planFor(re.size))
        }
    }

    /** Inverse DFT, in place, with the usual 1/N scaling (as in numpy's irfft). */
    fun inverse(re: DoubleArray, im: DoubleArray) {
        require(re.size == im.size) { "re/im size mismatch" }
        val n = re.size
        if (n <= 1) return
        for (i in 0 until n) im[i] = -im[i]
        forward(re, im)
        val scale = 1.0 / n
        for (i in 0 until n) {
            re[i] *= scale
            im[i] = -im[i] * scale
        }
    }

    private fun isPowerOfTwo(n: Int): Boolean = n and (n - 1) == 0

    private fun nextPowerOfTwo(n: Int): Int {
        var p = 1
        while (p < n) p = p shl 1
        return p
    }

    // ---- Radix-2 ------------------------------------------------------------

    private fun fftPowerOfTwo(re: DoubleArray, im: DoubleArray, inverse: Boolean) {
        val n = re.size
        // Bit-reversal permutation.
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j or bit
            if (i < j) {
                val tr = re[i]; re[i] = re[j]; re[j] = tr
                val ti = im[i]; im[i] = im[j]; im[j] = ti
            }
        }
        val sign = if (inverse) 1.0 else -1.0
        var len = 2
        while (len <= n) {
            val ang = sign * 2.0 * PI / len
            val wlenRe = cos(ang)
            val wlenIm = sin(ang)
            var i = 0
            while (i < n) {
                var wRe = 1.0
                var wIm = 0.0
                val half = len / 2
                for (k in 0 until half) {
                    val uRe = re[i + k]
                    val uIm = im[i + k]
                    val vRe0 = re[i + k + half]
                    val vIm0 = im[i + k + half]
                    val vRe = vRe0 * wRe - vIm0 * wIm
                    val vIm = vRe0 * wIm + vIm0 * wRe
                    re[i + k] = uRe + vRe
                    im[i + k] = uIm + vIm
                    re[i + k + half] = uRe - vRe
                    im[i + k + half] = uIm - vIm
                    val nwRe = wRe * wlenRe - wIm * wlenIm
                    wIm = wRe * wlenIm + wIm * wlenRe
                    wRe = nwRe
                }
                i += len
            }
            len = len shl 1
        }
        if (inverse) {
            val scale = 1.0 / n
            for (i in 0 until n) {
                re[i] *= scale
                im[i] *= scale
            }
        }
    }

    // ---- Bluestein ----------------------------------------------------------

    private class BluesteinPlan(val n: Int) {
        val convSize: Int = nextPowerOfTwo(2 * n - 1)
        val chirpRe = DoubleArray(n)
        val chirpIm = DoubleArray(n)
        /** FFT of the chirp kernel b, computed once and reused per transform. */
        val kernelFftRe = DoubleArray(convSize)
        val kernelFftIm = DoubleArray(convSize)

        init {
            // chirp[k] = exp(-i * pi * k^2 / n); k^2 is reduced mod 2n first,
            // because exp(-i pi k^2 / n) has period 2n in k^2 and the raw
            // k^2 (up to ~5.9e7) would cost several ulps of angle precision.
            val twoN = 2L * n
            for (k in 0 until n) {
                val sq = (k.toLong() * k.toLong()) % twoN
                val ang = -PI * sq.toDouble() / n
                chirpRe[k] = cos(ang)
                chirpIm[k] = sin(ang)
            }
            // b[m] = exp(+i * pi * m^2 / n), laid out for a circular
            // convolution: index m for m >= 0 and convSize + m for m < 0;
            // b is even (b[-m] == b[m]), so both slots take the same value.
            kernelFftRe[0] = 1.0
            for (k in 1 until n) {
                val sq = (k.toLong() * k.toLong()) % twoN
                val ang = PI * sq.toDouble() / n
                val c = cos(ang)
                val s = sin(ang)
                kernelFftRe[k] = c
                kernelFftIm[k] = s
                kernelFftRe[convSize - k] = c
                kernelFftIm[convSize - k] = s
            }
            fftPowerOfTwo(kernelFftRe, kernelFftIm, inverse = false)
        }
    }

    private val plans = HashMap<Int, BluesteinPlan>()

    @Synchronized
    private fun planFor(n: Int): BluesteinPlan = plans.getOrPut(n) { BluesteinPlan(n) }

    private fun bluesteinForward(re: DoubleArray, im: DoubleArray, plan: BluesteinPlan) {
        val n = plan.n
        val l = plan.convSize
        val aRe = DoubleArray(l)
        val aIm = DoubleArray(l)
        for (i in 0 until n) {
            val cr = plan.chirpRe[i]
            val ci = plan.chirpIm[i]
            aRe[i] = re[i] * cr - im[i] * ci
            aIm[i] = re[i] * ci + im[i] * cr
        }
        fftPowerOfTwo(aRe, aIm, inverse = false)
        for (i in 0 until l) {
            val br = plan.kernelFftRe[i]
            val bi = plan.kernelFftIm[i]
            val pr = aRe[i] * br - aIm[i] * bi
            aIm[i] = aRe[i] * bi + aIm[i] * br
            aRe[i] = pr
        }
        fftPowerOfTwo(aRe, aIm, inverse = true)
        for (k in 0 until n) {
            val cr = plan.chirpRe[k]
            val ci = plan.chirpIm[k]
            val vr = aRe[k]
            val vi = aIm[k]
            re[k] = vr * cr - vi * ci
            im[k] = vr * ci + vi * cr
        }
    }
}
