// PreferenceKeys.kt — SUBSET of Mochy's PreferenceKeys.kt.
// Only the keys the ported ui/mochy code reads so far. Grow this file as
// screens land; the full Mochy file is 1300+ lines and most of it belongs
// to engine/settings features ChordWave does not have (or has elsewhere).
// These keys live in the Mochy DataStore ("settings"), separate from
// ChordWave's SharedPreferences-backed AppSettings.

package com.music.bitchord.ui.mochy.constants

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

val DisableBlurKey = booleanPreferencesKey("disableBlur")

/**
 * How much work the glass blur does. Low is the lighter glass from before 8.1: the bottom bar
 * blurs only what's right under it with no grain on top, and glass draws its backdrop at a lower
 * resolution. High is the newer frosted glass.
 */
val BlurQualityKey = stringPreferencesKey("blurQuality")

enum class BlurQuality { LOW, HIGH }

// the glass of the nav bar and the mini player; a look module can swap mochy's frost for a glass shader it brings itself
val ChromeGlassStyleKey = stringPreferencesKey("chromeGlassStyle")

enum class ChromeGlassStyle {
    FROSTED,
    // the agsl glass the look that's on brings in its zip
    MODULE,
}

val PlayerSwipeUpFollowsKey = booleanPreferencesKey("playerSwipeUpFollows")
val EnableProgressiveBlurKey = booleanPreferencesKey("enableProgressiveBlur")
val BlurStrengthPxKey = floatPreferencesKey("blurStrengthPx")

// --- added for the Phase 2 Home components (NEEDS.md) ---
enum class GridItemSize {
    BIG,
    SMALL,
}

val CanvasThumbnailAnimationKey = booleanPreferencesKey("canvasThumbnailAnimation")
val CanvasWifiOnlyKey = booleanPreferencesKey("canvasWifiOnly")
val ElementBlurKey = booleanPreferencesKey("elementBlur")
val DataSaverKey = booleanPreferencesKey("dataSaver")
val DataSaverMobileOnlyKey = booleanPreferencesKey("dataSaverMobileOnly")
