// Ported from Mochy Music (GPL-3.0): crossmix/CrossmixStyles.kt — the
// UI-facing surface ONLY (StyleUse + CrossmixStyle entries/labels).
// The engine stays out: FxParams, CrossmixRole, evaluate() and the
// CrossmixPlanner are Mochy's mixing engine; ChordWave has no crossmix
// engine (its beat-matched mixing is Automix / smart fade). The settings
// UI only reads entries, label, name and use.

package com.music.bitchord.ui.mochy.crossmix

import kotlin.math.min

/** when the planner may pick a style by itself: every day, only into a sharp rise or fall in energy, or never (they stay for testing by hand) */
enum class StyleUse { Everyday, Lift, Fall, Flavour }

/** the transitions crossmix can play; needsBeatMatch ones overlap on the beat, entersAtEnd ones bring the next song in after the outgoing one is gone (a drop) */
enum class CrossmixStyle(
    val label: String,
    val needsBeatMatch: Boolean,
    val entersAtEnd: Boolean,
    /** lengths that suit it, in beats */
    val lengths: IntArray,
    /** beats the outgoing side keeps ringing after the hand-over (echo and reverb tails) */
    val tailBeats: Int = 0,
    val use: StyleUse = StyleUse.Flavour,
) {
    SilkBlend("Silk blend", false, false, intArrayOf(8, 16), use = StyleUse.Everyday),
    BassSwap("Bass swap", true, false, intArrayOf(8, 16, 32), use = StyleUse.Everyday),
    FilterSweep("Filter sweep", false, false, intArrayOf(8, 16), use = StyleUse.Everyday),
    HighPassLift("High-pass lift", false, true, intArrayOf(8, 16), use = StyleUse.Lift),
    EchoOut("Echo out", false, true, intArrayOf(4, 8), tailBeats = 12, use = StyleUse.Everyday),
    DubThrow("Dub throw", false, true, intArrayOf(8), tailBeats = 16),
    ReverbWash("Reverb wash", false, false, intArrayOf(16, 24), tailBeats = 16),
    Backspin("Backspin", false, true, intArrayOf(4, 8), use = StyleUse.Lift),
    RewindWash("Rewind wash", false, true, intArrayOf(8), tailBeats = 12),
    TapeStop("Tape stop", false, true, intArrayOf(4, 8), use = StyleUse.Fall),
    PowerDown("Power down", false, true, intArrayOf(8), tailBeats = 8, use = StyleUse.Fall),
    LoopRoll("Loop roll", false, true, intArrayOf(8, 16), use = StyleUse.Lift),
    StutterGate("Stutter gate", true, false, intArrayOf(16, 32)),
    RiserBuild("Riser build", false, true, intArrayOf(16, 32), tailBeats = 4, use = StyleUse.Lift),
    Downlifter("Downlifter", false, false, intArrayOf(8, 16), tailBeats = 8, use = StyleUse.Fall),
    PhaserGlide("Phaser glide", false, false, intArrayOf(16, 32)),
    LofiMelt("Lo-fi melt", false, false, intArrayOf(16, 24)),
    SpatialOrbit("Spatial orbit", false, false, intArrayOf(16, 32), tailBeats = 8),
    DopplerPass("Doppler pass", false, false, intArrayOf(8, 16)),
    VocalHandoff("Vocal hand-off", false, true, intArrayOf(4, 8), tailBeats = 8, use = StyleUse.Everyday),
    DropCut("Drop cut", true, true, intArrayOf(8, 16), use = StyleUse.Everyday),
    GateFade("Gate fade", true, false, intArrayOf(16, 32)),
    HarmonicGlide("Harmonic glide", true, false, intArrayOf(16, 32), use = StyleUse.Everyday),
    BreakdownBridge("Breakdown bridge", false, false, intArrayOf(16, 24), tailBeats = 4),
    ThunderDrop("Thunder drop", false, true, intArrayOf(8, 16), tailBeats = 12, use = StyleUse.Lift),
    FarAway("Fade to far", false, false, intArrayOf(8, 16), tailBeats = 8, use = StyleUse.Everyday),
    Dampen("Dampen", false, false, intArrayOf(8, 16), tailBeats = 6, use = StyleUse.Everyday);

    /** the beat the low end changes hands and the next song's landing is heard: the end for a drop, otherwise up to two bars before it */
    fun swapAt(length: Int): Int = if (entersAtEnd) length else length - min(8, length / 2)

    /** both songs really play together in it: not a drop at the very end, not one fading away behind the other */
    val blendsBoth: Boolean get() = !entersAtEnd && this != FarAway && this != Dampen
}
