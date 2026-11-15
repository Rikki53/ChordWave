// AudioLabViewModel — the Audio Lab pages' view model, in the shape of
// Mochy's viewmodels/AudioLabViewModel.kt (GPL-3.0) but rewired onto
// ChordWave's real engines. Mochy's AudioLabStore / EqualizerService /
// AudioFxService / SourceModules do not exist here; every StateFlow below
// is either an AppSettings flow (the service applies it live — see
// PlaybackService.applyEqualizer / applySpatialAudioEnabled / the
// playbackSpeed collector) or a fixed default for a control with no engine.
//
// WHAT IS REAL (a tap changes the sound):
// - eq: AppSettings equalizer (enabled, mode, bands, preset, tone pad) —
//   the nodes are the live engine curve (manualCurve / toneCurve), so the
//   hub's sketch draws what is actually playing.
// - spatial.enabled: AppSettings.spatialAudio -> SpatialAudioProcessor.
// - playback.speed: AppSettings.playbackSpeed -> ExoPlayer.
// - "Even out songs": AppSettings.loudnessNormalization (wired in the
//   screen, not here — Mochy's toggle was a DataStore pref; CW's engine
//   switch is the AppSettings one).
// - passBlockers / clearPassBlockers / restorePassBlockers: the Audio Pass
//   blockers, computed from the same AppSettings flows.
// WHAT IS NOT (controls are shown DISABLED in the screens, never silent
// no-ops; the update fns below log a Timber warning if ever called):
// - reverb, loudness boost/compression, volume curve: no CW engine.
// - spatial mode/cutoff/feed/angle/amount/surround/mono: the CW widener
//   has no parameters.
// - playback preservePitch / pitchSemitones: CW always preserves pitch.
// - normalize ReplayGain mode / preamp: CW normalizes from YouTube's own
//   figure, on/off only.
// NOT PORTED (see NEEDS.md): AutoEq (needs the equalizer screen's tab),
// the providers/source order (CW has no reorderable sources).
//
// Hard lessons honored: no combine() with 5+ flows (nested instead);
// no StateFlow.distinctUntilChanged().
package com.music.bitchord.ui.mochy.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.EqualizerMode
import com.music.bitchord.playback.EqLayout
import com.music.bitchord.playback.EqualizerPreset
import com.music.bitchord.playback.manualCurve
import com.music.bitchord.playback.toneCurve
import com.music.bitchord.ui.mochy.eq.EqNode
import com.music.bitchord.ui.mochy.eq.EqPresetInfo
import com.music.bitchord.ui.mochy.eq.EqState
import com.music.bitchord.ui.mochy.eq.LoudnessSettings
import com.music.bitchord.ui.mochy.eq.MaxSpeed
import com.music.bitchord.ui.mochy.eq.MinSpeed
import com.music.bitchord.ui.mochy.eq.NormalizeSettings
import com.music.bitchord.ui.mochy.eq.PlaybackSettings
import com.music.bitchord.ui.mochy.eq.ReverbSettings
import com.music.bitchord.ui.mochy.eq.SpatialSettings
import com.music.bitchord.ui.mochy.eq.VolumeSettings
import com.music.bitchord.ui.mochy.playback.audio.pass.PassBlocker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class AudioLabViewModel @Inject constructor() : ViewModel() {

    // ---- equalizer (real: AppSettings) -------------------------------------

    /** The presets the hub's status line can name. */
    val presets: StateFlow<List<EqPresetInfo>> = MutableStateFlow(
        EqualizerPreset.entries
            .filter { it != EqualizerPreset.CUSTOM }
            .map { EqPresetInfo(it.name, it.displayName()) },
    ).asStateFlow()

    private data class EqBase(
        val enabled: Boolean,
        val mode: EqualizerMode,
        val bands: List<Float>,
        val preset: EqualizerPreset,
    )

    private data class EqTone(val x: Int, val y: Int, val focused: Boolean)

    private val eqBase = combine(
        AppSettings.equalizerEnabled,
        AppSettings.equalizerMode,
        AppSettings.equalizerBands,
        AppSettings.equalizerPreset,
    ) { enabled, mode, bands, preset -> EqBase(enabled, mode, bands, preset) }

    private val eqTone = combine(
        AppSettings.equalizerToneX,
        AppSettings.equalizerToneY,
        AppSettings.equalizerFocused,
    ) { x, y, focused -> EqTone(x, y, focused) }

    val eq: StateFlow<EqState> = combine(eqBase, eqTone) { base, tone ->
        buildEqState(base, tone)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        buildEqState(
            EqBase(
                AppSettings.equalizerEnabled.value,
                AppSettings.equalizerMode.value,
                AppSettings.equalizerBands.value,
                AppSettings.equalizerPreset.value,
            ),
            EqTone(
                AppSettings.equalizerToneX.value,
                AppSettings.equalizerToneY.value,
                AppSettings.equalizerFocused.value,
            ),
        ),
    )

    private fun buildEqState(base: EqBase, tone: EqTone): EqState {
        val curve = when (base.mode) {
            EqualizerMode.DYNAMIC -> toneCurve(tone.x, tone.y, tone.focused)
            EqualizerMode.MANUAL -> manualCurve(base.bands)
        }
        val nodes = EqLayout.slots.mapIndexed { index, slot ->
            EqNode(
                frequency = slot.frequencyHz.toDouble(),
                gain = curve.gainsDb.getOrElse(index) { 0f }.toDouble(),
            )
        }
        val preset = base.preset
        return EqState(
            enabled = base.enabled,
            presetId = if (preset == EqualizerPreset.CUSTOM) null else preset.name,
            presetName = preset.displayName(),
            nodes = nodes,
        )
    }

    // ---- reverb / loudness / volume: no engine, fixed defaults -------------

    private val _reverb = MutableStateFlow(ReverbSettings())
    val reverb: StateFlow<ReverbSettings> = _reverb.asStateFlow()

    private val _loudness = MutableStateFlow(LoudnessSettings())
    val loudness: StateFlow<LoudnessSettings> = _loudness.asStateFlow()

    private val _volume = MutableStateFlow(VolumeSettings())
    val volume: StateFlow<VolumeSettings> = _volume.asStateFlow()

    private val _normalize = MutableStateFlow(NormalizeSettings())
    val normalize: StateFlow<NormalizeSettings> = _normalize.asStateFlow()

    /** No reverb engine in ChordWave: the card is disabled, this never runs from the UI. */
    fun updateReverb(transform: (ReverbSettings) -> ReverbSettings) {
        transform(_reverb.value)
        Timber.w("Reverb has no engine in ChordWave; the change was dropped")
    }

    /** No loudness boost/compressor engine in ChordWave: the card is disabled. */
    fun updateLoudness(transform: (LoudnessSettings) -> LoudnessSettings) {
        transform(_loudness.value)
        Timber.w("Loudness boost has no engine in ChordWave; the change was dropped")
    }

    /** No volume-curve engine in ChordWave: the card is disabled. */
    fun updateVolume(transform: (VolumeSettings) -> VolumeSettings) {
        transform(_volume.value)
        Timber.w("Volume curve has no engine in ChordWave; the change was dropped")
    }

    /** No ReplayGain engine in ChordWave: mode/preamp are disabled. */
    fun updateNormalize(transform: (NormalizeSettings) -> NormalizeSettings) {
        transform(_normalize.value)
        Timber.w("ReplayGain has no engine in ChordWave; the change was dropped")
    }

    // ---- spatial: only the on/off switch is real ----------------------------

    val spatial: StateFlow<SpatialSettings> = AppSettings.spatialAudio
        .map { SpatialSettings(enabled = it) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            SpatialSettings(enabled = AppSettings.spatialAudio.value),
        )

    fun updateSpatial(transform: (SpatialSettings) -> SpatialSettings) {
        val current = spatial.value
        val next = transform(current)
        if (next.enabled != current.enabled) AppSettings.setSpatialAudio(next.enabled)
        if (next.copy(enabled = current.enabled) != current) {
            Timber.w("Spatial parameters have no engine in ChordWave; only on/off works")
        }
    }

    // ---- playback: only the speed is real -----------------------------------

    val playback: StateFlow<PlaybackSettings> = AppSettings.playbackSpeed
        .map { PlaybackSettings(speed = it) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            PlaybackSettings(speed = AppSettings.playbackSpeed.value),
        )

    fun updatePlayback(transform: (PlaybackSettings) -> PlaybackSettings) {
        val current = playback.value
        val next = transform(current)
        val speed = next.speed.coerceIn(MinSpeed, MaxSpeed)
        if (speed != current.speed) AppSettings.setPlaybackSpeed(speed)
        if (next.preservePitch != current.preservePitch || next.pitchSemitones != current.pitchSemitones) {
            Timber.w("Pitch control has no engine in ChordWave; pitch is always preserved")
        }
    }

    // ---- audio pass blockers (real: AppSettings) ----------------------------

    // The VOLUME blocker is added by the Audio Pass screen itself: it reads
    // the player's music volume off the player connection, which this VM
    // cannot see. Everything else is computed here.
    private val blockersA = combine(
        AppSettings.equalizerEnabled,
        AppSettings.spatialAudio,
        AppSettings.loudnessNormalization,
        AppSettings.playbackSpeed,
    ) { eqOn, spatialOn, normOn, speed ->
        buildSet {
            if (eqOn) add(PassBlocker.EQUALIZER)
            if (spatialOn) add(PassBlocker.AUDIO_FX)
            if (normOn) add(PassBlocker.NORMALIZATION)
            if (speed != 1f) add(PassBlocker.SPEED)
        }
    }

    private val blockersB = combine(
        AppSettings.crossfadeSeconds,
        AppSettings.smartFadeEnabled,
        AppSettings.skipSilence,
    ) { crossfadeSeconds, smartFade, skipSilence ->
        buildSet {
            if (crossfadeSeconds > 0 || smartFade) add(PassBlocker.TRANSITIONS)
            if (skipSilence) add(PassBlocker.SILENCE_OR_HAPTICS)
        }
    }

    val passBlockers: StateFlow<Set<PassBlocker>> = combine(blockersA, blockersB) { a, b -> a + b }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), currentBlockers())

    private fun currentBlockers(): Set<PassBlocker> = buildSet {
        if (AppSettings.equalizerEnabled.value) add(PassBlocker.EQUALIZER)
        if (AppSettings.spatialAudio.value) add(PassBlocker.AUDIO_FX)
        if (AppSettings.loudnessNormalization.value) add(PassBlocker.NORMALIZATION)
        if (AppSettings.playbackSpeed.value != 1f) add(PassBlocker.SPEED)
        if (AppSettings.crossfadeSeconds.value > 0 || AppSettings.smartFadeEnabled.value) {
            add(PassBlocker.TRANSITIONS)
        }
        if (AppSettings.skipSilence.value) add(PassBlocker.SILENCE_OR_HAPTICS)
    }

    private data class BlockerSnapshot(
        val eq: Boolean,
        val spatial: Boolean,
        val normalization: Boolean,
        val speed: Float,
        val crossfadeSeconds: Int,
        val smartFade: Boolean,
        val skipSilence: Boolean,
    )

    private var blockerSnapshot: BlockerSnapshot? = null

    private val _canRestorePassBlockers = MutableStateFlow(false)
    val canRestorePassBlockers: StateFlow<Boolean> = _canRestorePassBlockers.asStateFlow()

    /** Switches every blocker off, remembering what was on so it can come back. */
    fun clearPassBlockers() {
        blockerSnapshot = BlockerSnapshot(
            eq = AppSettings.equalizerEnabled.value,
            spatial = AppSettings.spatialAudio.value,
            normalization = AppSettings.loudnessNormalization.value,
            speed = AppSettings.playbackSpeed.value,
            crossfadeSeconds = AppSettings.crossfadeSeconds.value,
            smartFade = AppSettings.smartFadeEnabled.value,
            skipSilence = AppSettings.skipSilence.value,
        )
        AppSettings.setEqualizerEnabled(false)
        AppSettings.setSpatialAudio(false)
        AppSettings.setLoudnessNormalization(false)
        AppSettings.setPlaybackSpeed(1f)
        AppSettings.setCrossfadeSeconds(0)
        AppSettings.setSmartFadeEnabled(false)
        AppSettings.setSkipSilence(false)
        _canRestorePassBlockers.value = true
    }

    /** Puts back exactly what [clearPassBlockers] switched off. */
    fun restorePassBlockers() {
        val snapshot = blockerSnapshot ?: return
        AppSettings.setEqualizerEnabled(snapshot.eq)
        AppSettings.setSpatialAudio(snapshot.spatial)
        AppSettings.setLoudnessNormalization(snapshot.normalization)
        AppSettings.setPlaybackSpeed(snapshot.speed)
        AppSettings.setCrossfadeSeconds(snapshot.crossfadeSeconds)
        AppSettings.setSmartFadeEnabled(snapshot.smartFade)
        AppSettings.setSkipSilence(snapshot.skipSilence)
        blockerSnapshot = null
        _canRestorePassBlockers.value = false
    }
}

/** "Bass Boost" from BASS_BOOST, "Flat" from FLAT. The classic screen's
 *  localizedLabel() is private to it, so the hub names presets this way. */
internal fun EqualizerPreset.displayName(): String =
    name.lowercase().split('_').joinToString(" ") { word ->
        word.replaceFirstChar { it.uppercase() }
    }
