// PassthroughStat.kt — ported from Mochy Music (GPL-3.0): ui/player/info/PassthroughStat.kt
//
// Adaptation from Mochy (see NEEDS.md): Mochy reads the live state off
// MusicService (service.audioPass.status/skip, service.passBlockers).
// ChordWave has no audio-pass engine, so the state arrives as one
// AudioPassSnapshot param instead; null means "no engine yet" and the
// stat line is omitted. The wording logic is verbatim, and the audiolab
// helpers it used (androidName, statusLine, PassBlocker.label) are copied
// here as private helpers from Mochy's ui/screens/audiolab/AudioPassScreen.kt.

package com.music.bitchord.ui.mochy.player.info

import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.music.bitchord.R
import com.music.bitchord.ui.mochy.constants.AudioPassMode
import com.music.bitchord.ui.mochy.constants.AudioPassModeKey
import com.music.bitchord.ui.mochy.constants.UsbPassthroughKey
import com.music.bitchord.ui.mochy.playback.audio.AudioOutputs
import com.music.bitchord.ui.mochy.playback.audio.pass.AudioPassSnapshot
import com.music.bitchord.ui.mochy.playback.audio.pass.AudioPassSkip
import com.music.bitchord.ui.mochy.playback.audio.pass.AudioPassStatus
import com.music.bitchord.ui.mochy.playback.audio.pass.PassBlocker
import com.music.bitchord.ui.mochy.utils.rememberEnumPreference
import com.music.bitchord.ui.mochy.utils.rememberPreference
import java.util.Locale

// the output card's line on passthrough: straight to the dac, or why this song wasn't (issue 235 had no way to tell)
@Composable
fun rememberPassthroughStat(device: AudioOutputs.Device?, audioPass: AudioPassSnapshot?): Pair<String, String>? {
    val (on) = rememberPreference(UsbPassthroughKey, defaultValue = false)
    val (mode) = rememberEnumPreference(AudioPassModeKey, defaultValue = AudioPassMode.Default)
    // TODO(Phase 3 adapter): no audio-pass engine in ChordWave yet — without
    // a snapshot there is nothing true to say, so the line stays hidden.
    if (audioPass == null) return null
    val status = audioPass.status
    val skip = audioPass.skip
    val blockers = audioPass.blockers
    // off and nothing to pass to, there's nothing worth a line
    if (!on && device?.kind != AudioOutputs.Kind.USB) return null
    val resources = LocalContext.current.resources
    val value = when {
        !on -> stringResource(R.string.audiopass_stat_off)
        !mode.exclusive -> stringResource(R.string.audiopass_stat_song_rate)
        Build.VERSION.SDK_INT < mode.minSdk -> stringResource(R.string.audiopass_stat_old_android, androidName(mode.minSdk))
        blockers.isNotEmpty() -> stringResource(
            R.string.audiopass_stat_blocked,
            blockers.joinToString(", ") { resources.getString(it.label) },
        )
        status is AudioPassStatus.Playing || status is AudioPassStatus.Fell -> statusLine(status).orEmpty()
        else -> stringResource(skip?.message ?: R.string.audiopass_stat_unknown)
    }
    return stringResource(R.string.audiopass_stat_label) to value
}

private val AudioPassSkip.message: Int
    get() = when (this) {
        AudioPassSkip.Busy -> R.string.audiopass_stat_skip_busy
        AudioPassSkip.Format -> R.string.audiopass_stat_skip_format
        AudioPassSkip.Lossy -> R.string.audiopass_stat_skip_lossy
        AudioPassSkip.FellBefore -> R.string.audiopass_stat_skip_fell
        AudioPassSkip.NoDac -> R.string.audiopass_stat_skip_no_dac
        AudioPassSkip.NoUsbAccess -> R.string.audiopass_stat_skip_no_access
        AudioPassSkip.DacCantPlay -> R.string.audiopass_stat_skip_dac_cant
    }

// ---- copied from Mochy's ui/screens/audiolab/AudioPassScreen.kt ----

private fun androidName(sdk: Int): String = when (sdk) {
    27 -> "8.1"
    34 -> "14"
    else -> "7"
}

@Composable
private fun statusLine(status: AudioPassStatus?): String? = when (status) {
    null, AudioPassStatus.Off -> null
    AudioPassStatus.Waiting -> stringResource(R.string.audiopass_now_waiting)
    is AudioPassStatus.Fell -> stringResource(R.string.audiopass_now_fell, khz(status.sampleRate), status.sourceBits.toString())
    is AudioPassStatus.Playing -> when {
        !status.exact -> stringResource(R.string.audiopass_now_inexact, status.device)
        status.lossless -> stringResource(R.string.audiopass_now_bitperfect, status.device, khz(status.sampleRate), status.bits.toString())
        else -> stringResource(R.string.audiopass_now_untouched, status.device, khz(status.sampleRate), status.bits.toString())
    }
}

private val PassBlocker.label: Int
    @StringRes get() = when (this) {
        PassBlocker.EQUALIZER -> R.string.audiopass_blocker_equalizer
        PassBlocker.AUDIO_FX -> R.string.audiopass_blocker_fx
        PassBlocker.VOLUME -> R.string.audiopass_blocker_volume
        PassBlocker.NORMALIZATION -> R.string.audiopass_blocker_normalization
        PassBlocker.SPEED -> R.string.audiopass_blocker_speed
        PassBlocker.TRANSITIONS -> R.string.audiopass_blocker_transitions
        PassBlocker.SILENCE_OR_HAPTICS -> R.string.audiopass_blocker_silence
    }

private fun khz(rate: Int): String =
    if (rate % 1000 == 0) "${rate / 1000}" else String.format(Locale.ROOT, "%.1f", rate / 1000.0)
