// Preference keys the Customisation screen (Phase 5) needs that no earlier
// phase declared. Verbatim Mochy key names + defaults (GPL-3.0,
// constants/PreferenceKeys.kt), so a user coming from Mochy keeps their
// choices. The coordinator may fold these into PreferenceKeys.kt.
//
// Deliberately NOT here:
// - PlayerStatusBarKey / PlayerStatusBarDefault: already in PreferenceKeys.kt.
// - HiFiResamplerKey / AudioPassFixedVolumeKey: the Audio Pass screen does
//   not need them — the resampler has no ChordWave engine (its toggle is
//   shown disabled) and fixed DAC volume is AppSettings.usbAllowFixedVolume.
package com.music.bitchord.ui.mochy.constants

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey

/** How round covers are across the app, in px. Default 25 (see DefaultCoverRadius). */
val ThumbnailCornerRadiusKey = floatPreferencesKey("thumbnailCornerRadius")

/** Master animation on/off (the element-blur animator switch in Mochy). */
val EnableElementBlurAnimatorKey = booleanPreferencesKey("enableElementBlurAnimator")

/** Animation speed multiplier, on top of Android's own animation scale. Default 1. */
val AnimationSpeedKey = floatPreferencesKey("animationSpeed")

/** The phone's navigation bar under the open player. */
val PlayerNavBarKey = booleanPreferencesKey("playerNavBar")
const val PlayerNavBarDefault = false
