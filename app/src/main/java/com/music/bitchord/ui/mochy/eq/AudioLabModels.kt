// Audio Lab settings models, ported from Mochy Music (GPL-3.0):
// eq/AudioLabStore.kt (Loudness/Spatial/Normalize/Playback/VolumeSettings,
// MinSpeed/MaxSpeed) and eq/ReverbSettings.kt (ReverbSettings,
// ReverbPresets), plus the eq/audio enums the screens read (SpatialMode,
// ReplayGainMode).
//
// What changed for ChordWave, and why:
// - The `toParams()` / `toCurve()` converters are gone: they fed Mochy's own
//   audio processors, which do not exist here. The AudioLabViewModel is the
//   only thing that reads these models, and it translates the engine-backed
//   fields onto AppSettings (see its header).
// - EqSettings is NOT ported: ChordWave's equalizer is a fixed 10-slot
//   layout driven by AppSettings (EqualizerPreset / tone pad), not Mochy's
//   free parametric nodes. The hub only needs "is it on, which preset, what
//   curve" — that's EqState below, built from the real engine curve
//   (manualCurve / toneCurve) so the hub's sketch draws what is playing.
// - PassBlocker is NOT redeclared here: it already exists in
//   ui/mochy/playback/audio/pass/AudioPassState.kt (Phase 3).
package com.music.bitchord.ui.mochy.eq

import kotlin.math.pow

/** Playback-speed slider ends, verbatim Mochy. */
const val MinSpeed = 0.1f
const val MaxSpeed = 8f

/** One band of the live EQ curve, for the hub's sketch (low/mid/high averages). */
data class EqNode(
    val frequency: Double,
    val gain: Double,
    val enabled: Boolean = true,
)

/** What the Audio Lab hub shows about the equalizer. */
data class EqState(
    val enabled: Boolean,
    /** ChordWave preset name (EqualizerPreset.name), or null when the curve is custom. */
    val presetId: String?,
    /** Display name: the preset's, or "Custom". */
    val presetName: String,
    /** The live curve's gains, one per EqLayout slot. */
    val nodes: List<EqNode>,
)

/** One equalizer preset, for the hub's status line. */
data class EqPresetInfo(val id: String, val name: String)

/** Reverb. ChordWave has NO reverb engine: the card is shown disabled and
 *  these values are only the picture's defaults. */
data class ReverbSettings(
    val enabled: Boolean = false,
    val mix: Float = 0.5f,
    val roomSize: Float = 0.55f,
    val damping: Float = 0.45f,
    val wet: Float = 0.6f,
    val dry: Float = 1f,
    val width: Float = 1f,
    val preDelayMs: Float = 12f,
    val lowCutHz: Float = 180f,
    val presetId: String? = ReverbPresets.Default.id,
) {
    fun withPreset(preset: ReverbPresets.Preset) = copy(
        mix = preset.mix,
        roomSize = preset.roomSize,
        damping = preset.damping,
        wet = preset.wet,
        dry = 1f,
        width = preset.width,
        preDelayMs = preset.preDelayMs,
        lowCutHz = preset.lowCutHz,
        presetId = preset.id,
    )
}

/** Rooms to start from; any slider moved afterwards makes it custom. */
object ReverbPresets {
    class Preset(
        val id: String,
        val name: String,
        val mix: Float,
        val roomSize: Float,
        val damping: Float,
        val wet: Float,
        val width: Float,
        val preDelayMs: Float,
        val lowCutHz: Float,
    )

    val Default = Preset("room", "Room", 0.5f, 0.55f, 0.45f, 0.6f, 1f, 12f, 180f)

    val All = listOf(
        Default,
        Preset("studio", "Studio", 0.4f, 0.3f, 0.65f, 0.45f, 0.8f, 5f, 250f),
        Preset("bathroom", "Bathroom", 0.5f, 0.42f, 0.05f, 0.7f, 0.6f, 2f, 120f),
        Preset("plate", "Plate", 0.45f, 0.65f, 0.1f, 0.55f, 1f, 0f, 300f),
        Preset("hall", "Hall", 0.5f, 0.78f, 0.35f, 0.6f, 1f, 25f, 150f),
        Preset("cathedral", "Cathedral", 0.55f, 0.95f, 0.2f, 0.7f, 1f, 40f, 120f),
        Preset("stadium", "Stadium", 0.6f, 0.9f, 0.5f, 0.65f, 1f, 60f, 200f),
        Preset("dream", "Dreamy", 0.7f, 0.93f, 0.6f, 0.85f, 1f, 30f, 300f),
    )
}

/** Loudness boost + compression. ChordWave has NO boost/compressor engine:
 *  the card is shown disabled. (Loudness *normalization* is a separate,
 *  real setting — AppSettings.loudnessNormalization — wired to the
 *  "Even out songs" toggle instead.) */
data class LoudnessSettings(
    val enabled: Boolean = false,
    val boostDb: Float = 4f,
    val compression: Float = 0.3f,
)

enum class SpatialMode { Crossfeed, Speakers }

/** Spatial audio. Only `enabled` drives an engine (AppSettings.spatialAudio
 *  -> SpatialAudioProcessor, a fixed stereo widener). Mode, cutoff, feed,
 *  angle, amount, surround and mono have no ChordWave engine: the controls
 *  are shown disabled and the values stay at these defaults. */
data class SpatialSettings(
    val enabled: Boolean = false,
    val mode: SpatialMode = SpatialMode.Crossfeed,
    val cutoffHz: Float = 700f,
    val feedDb: Float = 4.5f,
    val speakerAngle: Float = 30f,
    val amount: Float = 1f,
    val surround: Boolean = false,
    val mono: Boolean = false,
)

enum class ReplayGainMode { Track, Album }

/** ReplayGain mode + preamp. ChordWave normalizes from YouTube's own
 *  loudness figure (on/off only), so these have no engine: shown disabled. */
data class NormalizeSettings(
    val replayGainMode: ReplayGainMode = ReplayGainMode.Track,
    val preampDb: Float = 3f,
)

/** Playback speed + pitch. Only `speed` drives an engine
 *  (AppSettings.playbackSpeed -> ExoPlayer). ChordWave always preserves
 *  pitch and has no pitch control, so `preservePitch` stays true and
 *  `pitchSemitones` stays 0; their controls are shown disabled. */
data class PlaybackSettings(
    val speed: Float = 1f,
    val preservePitch: Boolean = true,
    val pitchSemitones: Float = 0f,
) {
    /** Effective pitch multiplier: 1 while pitch is preserved. Verbatim Mochy. */
    val pitch: Float
        get() = if (preservePitch) 2f.pow(pitchSemitones / 12f) else speed

    val isNormal: Boolean get() = speed == 1f && pitch == 1f
}

/** Exponential volume curve + limit. ChordWave has NO volume-curve engine:
 *  the card is shown disabled. */
data class VolumeSettings(
    val enabled: Boolean = false,
    val exponent: Float = 2f,
    val limit: Float = 1f,
)
