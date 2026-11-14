// PreferenceKeys.kt — SUBSET of Mochy's PreferenceKeys.kt.
// Only the keys the ported ui/mochy code reads so far. Grow this file as
// screens land; the full Mochy file is 1300+ lines and most of it belongs
// to engine/settings features ChordWave does not have (or has elsewhere).
// These keys live in the Mochy DataStore ("settings"), separate from
// ChordWave's SharedPreferences-backed AppSettings.

package com.music.bitchord.ui.mochy.constants

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
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

// --- added for the Phase 2 Home screen (HomeScreen NEEDS.md A8) ---
// RENAMED by the coordinator: Mochy's enum value was MOCHYY ("Mochyy") —
// ChordWave's Modern mode calls the same layout MODERN ("Modern"). Safe:
// no Modern release has persisted the old value yet.
enum class HomeStyle(val displayName: String) {
    MODERN("Modern"),
    LISTEN_NOW("Listen now"),
    HOP_IN("Hop in"),
}

val HomeStyleKey = stringPreferencesKey("homeStyle")
val ProfilePicUriKey = stringPreferencesKey("profilePicUri")

// --- added for the Phase 2 HomeViewModel adapter (phase2-adapter NEEDS.md §(b)) ---
// Verbatim Mochy key names; the adapter reads the four filter flags from the
// store (Mochy's own defaults). QuickPicksKey/OfflineModeKey/
// LastPlayedLikedSongsTimeKey are declared for the Settings screen (Phase 4);
// the adapter does not read them yet (no LAST_LISTEN source, network always
// runs, no liked-play tracking).
val HideExplicitKey = booleanPreferencesKey("hideExplicit")
val HideVideoSongsKey = booleanPreferencesKey("hideVideoSongs")
val HideYoutubeShortsKey = booleanPreferencesKey("hideYoutubeShorts")
val DisableAiFilterKey = booleanPreferencesKey("disableAiFilter")
val OfflineModeKey = booleanPreferencesKey("offlineMode")
val LastPlayedLikedSongsTimeKey = longPreferencesKey("last_played_liked_songs_time")

enum class QuickPicks {
    QUICK_PICKS,
    LAST_LISTEN,
}

val QuickPicksKey = stringPreferencesKey("discover")
