package com.music.bitchord.playback.audio

import kotlin.math.abs
import kotlin.math.sign

/**
 * Karaoke vocal suppression, Apple Music Sing style: one slider decides how
 * much of the singer survives, from the full original mix down to (almost)
 * just the band.
 *
 * ## What it actually does — and what it does not
 *
 * This is centre-channel extraction, not AI stem separation. In most mixes
 * the lead vocal (and, unavoidably, the kick, snare and bass) is mixed dead
 * centre — identical in both channels — while guitars, keys and backing
 * vocals are spread across the stereo field. Splitting each frame into a
 * centre component `mid = (L + R) / 2` and a side component
 * `side = (L − R) / 2` therefore puts most of the vocal in `mid`, and the
 * slider simply scales `mid` on its way back:
 *
 * ```
 * outL =  side + mid * vocalLevel
 * outR = −side + mid * vocalLevel
 * ```
 *
 * At [vocalLevel] 1 the two halves recombine into the untouched original —
 * which is also why bypass is exact: a processor whose level sits at 1
 * returns before it reads a single sample, like every other stage in
 * [DspChain]. At 0 only the side signal remains (the classic "OOBE" karaoke
 * trick), centre-panned instruments thin out along with the voice, and a
 * mono recording — where `side` is zero by definition — goes quiet rather
 * than karaoke. Those are the honest limits of the technique; a real
 * separator would need a model, per song, like the desktop tools use.
 *
 * Runs first in the chain, on the decoder's own stereo image, before the
 * spatial stage gets a chance to widen or crossfeed it — widening smears the
 * centre into the sides, and centre extraction after that would take the
 * smeared copy of the vocal back out of the sides too, or fail to.
 */
class KaraokeProcessor : FloatAudioProcessor {

    /** Master switch. Written from the settings thread, read on the audio thread. */
    @Volatile
    var enabled: Boolean = false

    /**
     * The slider's target: 1 = full vocal (the original mix), 0 = maximum
     * suppression. The processor glides towards it rather than stepping, so
     * dragging the slider does not zipper.
     */
    @Volatile
    var vocalLevel: Float = 1f

    private var sampleRate: Int = 48_000
    private var channelCount: Int = AudioBlock.DEFAULT_CHANNELS

    /** Where the glide currently sits; only the audio thread moves it. */
    private var currentLevel: Float = 1f

    override fun configure(sampleRate: Int, channelCount: Int) {
        if (sampleRate > 0) this.sampleRate = sampleRate
        this.channelCount = channelCount
    }

    override fun process(block: AudioBlock) {
        // Settled at full vocal: the mix is already the original, whether the
        // switch is off or the slider is parked at 100%.
        val target = if (enabled) vocalLevel.coerceIn(0f, 1f) else 1f
        if (target == 1f && currentLevel == 1f) return
        val channels = block.channelCount
        if (channels < 2 || block.frameCount == 0) {
            // Nothing separable in a mono block; just settle the glide so a
            // later stereo block does not start from a stale position.
            currentLevel = target
            return
        }
        val samples = block.samples
        val frames = block.frameCount
        // A full 0→1 sweep in ~50 ms, spread across this block's frames.
        val stepPerFrame = 1f / (sampleRate * 0.05f)
        var level = currentLevel
        var i = 0
        val end = frames * channels
        while (i < end) {
            if (level != target) {
                val delta = target - level
                level += sign(delta) * minOf(abs(delta), stepPerFrame)
            }
            val l = samples[i]
            val r = samples[i + 1]
            val mid = (l + r) * 0.5f
            val side = (l - r) * 0.5f
            samples[i] = side + mid * level
            samples[i + 1] = -side + mid * level
            i += channels
        }
        currentLevel = level
    }

    override fun flush() {
        // No filter state to clear; the glide is the only memory, and a seek
        // is no reason to re-glide the slider.
    }

    override fun reset() {
        currentLevel = if (enabled) vocalLevel.coerceIn(0f, 1f) else 1f
    }
}
