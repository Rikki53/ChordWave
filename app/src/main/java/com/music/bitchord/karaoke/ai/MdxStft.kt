package com.music.bitchord.karaoke.ai

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max

/**
 * The MDX-Net (Kim Vocal 2) spectral front/back end, ported line-for-line
 * from NextGenUp's `audio_engine.py` (`_mdx_stft` / `_mdx_istft`).
 *
 * Contract, exactly as the Python:
 *  - sample rate 44100, NFFT 7680, hop 1024, model bins 3072 of 3841,
 *    256 frames, one segment = 261120 samples;
 *  - periodic Hann window (numpy `hanning(7681)[:-1]`), float32 in Python,
 *    held here as the identical values computed in Double;
 *  - STFT: reflect-pad 3840 samples on both sides of each channel, frame
 *    with hop 1024, take the first 256 frames, rFFT 7680, raw (unscaled)
 *    values;
 *  - model tensor (1, 4, 3072, 256) float32 in the channel order
 *    [L_real, L_imag, R_real, R_imag];
 *  - iSTFT: model output back into a full (2, 3841, 256) spectrum with
 *    bins 3072..3840 zero, irFFT 7680, window again, overlap-add with hop
 *    1024, per-sample division by the overlap-added window^2 (clamped
 *    below at 1e-8), drop the 3840-sample pad, keep 261120 samples.
 *
 * Memory (dev4): every workspace — the padded channel, the FFT buffers,
 * the model tensor, the full spectra and the overlap-add accumulators —
 * is allocated ONCE per instance and reused for every segment. The
 * previous version allocated the 12.6 MB tensor plus ~25 MB of spectra
 * and double buffers per segment, which (on top of the song-length
 * arrays the old pipeline held) is what pushed phones over their
 * per-app heap limit. Reuse changes no arithmetic: each buffer is fully
 * rewritten (or explicitly re-zeroed where the old code relied on fresh
 * zeroed arrays) before use.
 *
 * The arrays handed back by [stftToModelInput] and [istft] are these
 * shared workspaces: callers must consume them before the next call.
 * Not thread-safe; one instance serves one separation worker thread.
 */
object MdxConstants {
    const val SAMPLE_RATE = 44100
    const val NFFT = 7680
    const val HOP = 1024
    const val DIM_F = 3072
    const val DIM_T = 256
    const val SEG = (DIM_T - 1) * HOP // 261120
    const val OVERLAP = 44100
    const val STEP = SEG - OVERLAP // 217020
    const val BINS = NFFT / 2 + 1 // 3841
    const val PAD = NFFT / 2 // 3840
}

class MdxStft {

    /** Periodic Hann: w[n] = 0.5 - 0.5 * cos(2 pi n / NFFT), n in [0, NFFT). */
    private val window = DoubleArray(MdxConstants.NFFT) { n ->
        0.5 - 0.5 * cos(2.0 * PI * n / MdxConstants.NFFT)
    }
    private val windowSq = DoubleArray(MdxConstants.NFFT) { window[it] * window[it] }

    // ---- Reused workspaces (see the class doc) ------------------------------

    private val padded = DoubleArray(MdxConstants.SEG + 2 * MdxConstants.PAD)
    private val fftRe = DoubleArray(MdxConstants.NFFT)
    private val fftIm = DoubleArray(MdxConstants.NFFT)
    private val tensor = FloatArray(4 * MdxConstants.DIM_F * MdxConstants.DIM_T)
    private val specRe = FloatArray(2 * MdxConstants.BINS * MdxConstants.DIM_T)
    private val specIm = FloatArray(2 * MdxConstants.BINS * MdxConstants.DIM_T)
    private val istftLength = (MdxConstants.DIM_T - 1) * MdxConstants.HOP + MdxConstants.NFFT // 268800
    private val acc = DoubleArray(istftLength)
    private val norm = DoubleArray(istftLength)
    private val segOut = FloatArray(MdxConstants.SEG * 2)

    /**
     * STFT of one stereo segment into the model's input tensor, as a flat
     * float32 array in (4, 3072, 256) C order — ready to wrap as a
     * (1, 4, 3072, 256) ONNX tensor. [left] and [right] must each hold
     * exactly [MdxConstants.SEG] samples.
     *
     * The returned array is the shared tensor workspace: it is overwritten
     * by the next call on this instance.
     */
    fun stftToModelInput(left: FloatArray, right: FloatArray): FloatArray {
        require(left.size == MdxConstants.SEG && right.size == MdxConstants.SEG) {
            "segment must be ${MdxConstants.SEG} samples per channel"
        }
        stftChannelInto(left, channelOutBase = 0)
        stftChannelInto(right, channelOutBase = 2 * MdxConstants.DIM_F * MdxConstants.DIM_T)
        return tensor
    }

    /** Interleaved-stereo convenience form of [stftToModelInput]. */
    fun stftToModelInput(interleaved: FloatArray): FloatArray {
        require(interleaved.size == MdxConstants.SEG * 2) {
            "interleaved segment must be ${MdxConstants.SEG * 2} samples"
        }
        val left = FloatArray(MdxConstants.SEG)
        val right = FloatArray(MdxConstants.SEG)
        for (i in 0 until MdxConstants.SEG) {
            left[i] = interleaved[i * 2]
            right[i] = interleaved[i * 2 + 1]
        }
        return stftToModelInput(left, right)
    }

    private fun stftChannelInto(x: FloatArray, channelOutBase: Int) {
        val pad = MdxConstants.PAD
        val n = MdxConstants.SEG
        val paddedLength = n + 2 * pad
        val out = tensor
        // numpy mode='reflect': mirror without repeating the edge sample —
        // padded index -k reads x[k], index n-1+k reads x[n-1-k].
        for (p in 0 until paddedLength) {
            val src = when {
                p < pad -> pad - p
                p < pad + n -> p - pad
                else -> 2 * n - 2 - (p - pad)
            }
            padded[p] = x[src].toDouble()
        }
        val re = fftRe
        val im = fftIm
        for (t in 0 until MdxConstants.DIM_T) {
            val start = t * MdxConstants.HOP
            for (j in 0 until MdxConstants.NFFT) {
                re[j] = padded[start + j] * window[j]
                im[j] = 0.0
            }
            ComplexFft.forward(re, im)
            // Layout: out[(part * DIM_F + f) * DIM_T + t], part 0 = real, 1 = imag.
            val realBase = channelOutBase
            val imagBase = channelOutBase + MdxConstants.DIM_F * MdxConstants.DIM_T
            for (f in 0 until MdxConstants.DIM_F) {
                out[realBase + f * MdxConstants.DIM_T + t] = re[f].toFloat()
                out[imagBase + f * MdxConstants.DIM_T + t] = im[f].toFloat()
            }
        }
    }

    /**
     * iSTFT of a full vocal spectrum (2, 3841, 256), supplied as separate
     * real/imag flat arrays in ((channel * 3841) + bin) * 256 + frame order.
     * Returns one interleaved stereo segment of [MdxConstants.SEG] frames —
     * the shared output workspace, overwritten by the next call.
     */
    fun istft(inRe: FloatArray, inIm: FloatArray): FloatArray {
        require(inRe.size == 2 * MdxConstants.BINS * MdxConstants.DIM_T)
        require(inIm.size == inRe.size)
        val frames = MdxConstants.DIM_T
        val length = istftLength
        val outInterleaved = segOut
        val re = fftRe
        val im = fftIm
        for (ch in 0 until 2) {
            acc.fill(0.0)
            norm.fill(0.0)
            val chBase = ch * MdxConstants.BINS * frames
            for (t in 0 until frames) {
                // Rebuild the Hermitian full spectrum from the rFFT half.
                for (f in 0 until MdxConstants.BINS) {
                    re[f] = inRe[chBase + f * frames + t].toDouble()
                    im[f] = inIm[chBase + f * frames + t].toDouble()
                }
                for (f in MdxConstants.BINS until MdxConstants.NFFT) {
                    val mirror = MdxConstants.NFFT - f
                    re[f] = inRe[chBase + mirror * frames + t].toDouble()
                    im[f] = -inIm[chBase + mirror * frames + t].toDouble()
                }
                ComplexFft.inverse(re, im)
                val start = t * MdxConstants.HOP
                for (j in 0 until MdxConstants.NFFT) {
                    acc[start + j] += re[j] * window[j]
                    norm[start + j] += windowSq[j]
                }
            }
            for (i in 0 until MdxConstants.SEG) {
                val p = i + MdxConstants.PAD
                val v = acc[p] / max(norm[p], 1e-8)
                outInterleaved[i * 2 + ch] = v.toFloat()
            }
        }
        return outInterleaved
    }

    /**
     * The model's output tensor (flat (4, 3072, 256), same layout as the
     * input) back to an interleaved stereo vocal segment: vocal L takes
     * channels 0/1 as real/imag, vocal R channels 2/3, bins above 3072 zero.
     */
    fun istftFromModelOutput(modelOutput: FloatArray): FloatArray {
        require(modelOutput.size == 4 * MdxConstants.DIM_F * MdxConstants.DIM_T)
        val plane = MdxConstants.DIM_F * MdxConstants.DIM_T
        val fullPlane = MdxConstants.BINS * MdxConstants.DIM_T
        for (ch in 0 until 2) {
            val realSrc = ch * 2 * plane
            val imagSrc = realSrc + plane
            val dstPlane = ch * fullPlane
            for (f in 0 until MdxConstants.DIM_F) {
                val srcBase = f * MdxConstants.DIM_T
                val dstBase = dstPlane + f * MdxConstants.DIM_T
                modelOutput.copyInto(specRe, dstBase, realSrc + srcBase, realSrc + srcBase + MdxConstants.DIM_T)
                modelOutput.copyInto(specIm, dstBase, imagSrc + srcBase, imagSrc + srcBase + MdxConstants.DIM_T)
            }
            // The spectrum above the model's bins must be zero; the arrays
            // are reused, so clear the tail the copy did not touch.
            specRe.fill(0f, dstPlane + plane, dstPlane + fullPlane)
            specIm.fill(0f, dstPlane + plane, dstPlane + fullPlane)
        }
        return istft(specRe, specIm)
    }
}
