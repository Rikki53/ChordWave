// PlayerQueueKeys.kt — preference keys + enums the queue/info chunk needs,
// ported from Mochy's constants/PreferenceKeys.kt (GPL-3.0). Only the keys
// this chunk reads are declared here, so the worktree's PreferenceKeys.kt
// stays untouched; the coordinator may fold these into it at merge time.

package com.music.bitchord.ui.mochy.constants

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

// which service the about sheet's "about" text comes from: "musixmatch", "genius", "wikipedia", "youtube", "modules"
val AboutSongSourceKey = stringPreferencesKey("aboutSongSource")

val AudioQualityKey = stringPreferencesKey("audioQuality")

enum class AudioQuality {
    /** Best available from whichever backend is active. */
    AUTO,

    // YouTube tiers
    LOW,
    MEDIUM,
    HIGH,

    // Monochrome (TIDAL-backed) tiers
    LOSSLESS,
    HI_RES_LOSSLESS,
    DOLBY_ATMOS;

    /** Tiers only the Monochrome backend can serve; YouTube has no equivalent. */
    val usesMonochrome: Boolean
        get() = this == LOSSLESS || this == HI_RES_LOSSLESS || this == DOLBY_ATMOS
}

val AudioNormalizationKey = booleanPreferencesKey("audioNormalization")
val SkipSilenceKey = booleanPreferencesKey("skipSilence")
val MusicHapticsEnabledKey = booleanPreferencesKey("musicHapticsEnabled")
val HiResOutputKey = booleanPreferencesKey("hiResFloatOutput")

val CrossmixModeKey = stringPreferencesKey("crossmixMode")

/** The selected transition experience. Legacy crossfade keys remain the playback contract. */
enum class CrossmixMode {
    AUTO_CROSSFADE,
    OFF,
    MANUAL_CROSSFADE,
    CROSSMIX,
}

// crossmix unless someone picked otherwise: the old default was a plain 5 s crossfade that never mixed anything
val DefaultTransitionMode = CrossmixMode.CROSSMIX

// where the sound goes: a plugged-in usb dac first, its own rate (or bit-perfect) on android 14+, 24-bit kept as float
val UsbPassthroughKey = booleanPreferencesKey("usbDacPassthrough")
val AudioPassModeKey = stringPreferencesKey("audioPassMode")

/** how the passthrough switch reaches the dac; minSdk is the android each one needs */
enum class AudioPassMode(val minSdk: Int) {
    SONG_RATE(34),
    BIT_PERFECT(34),
    DIRECT_USB(24),
    EXCLUSIVE_USB(27),
    EXCLUSIVE_DAC(27),
    ;

    /** whether songs go to an output only the app plays to, instead of through android's mixer */
    val exclusive: Boolean get() = this != SONG_RATE

    companion object {
        /** android 14 brought the dac's own mixer; before it only the app's own driver reaches the dac */
        val Default: AudioPassMode
            get() = if (android.os.Build.VERSION.SDK_INT >= 34) SONG_RATE else DIRECT_USB
    }
}
