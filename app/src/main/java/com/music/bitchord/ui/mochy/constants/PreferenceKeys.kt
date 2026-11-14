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

// --- added for the Phase 2 Search port (phase2-search NEEDS.md §(a) A5/A6) ---
// Verbatim Mochy key names (PreferenceKeys.kt:347, :106) and region map
// (:1233-1275, 42 entries). The suggestions screen persists the chosen
// region slug via SuggestionRegionKey.
val PauseSearchHistoryKey = booleanPreferencesKey("pauseSearchHistory")
val SuggestionRegionKey = stringPreferencesKey("suggestionRegion")

val SuggestionRegionSlugToName =
    mapOf(
        "system" to "System Default",
        "us" to "Global (USA)",
        "in" to "India",
        "gb" to "United Kingdom",
        "ca" to "Canada",
        "au" to "Australia",
        "jp" to "Japan",
        "kr" to "South Korea",
        "de" to "Germany",
        "fr" to "France",
        "br" to "Brazil",
        "mx" to "Mexico",
        "ru" to "Russia",
        "it" to "Italy",
        "es" to "Spain",
        "nl" to "Netherlands",
        "se" to "Sweden",
        "no" to "Norway",
        "dk" to "Denmark",
        "fi" to "Finland",
        "pl" to "Poland",
        "tr" to "Turkey",
        "za" to "South Africa",
        "ng" to "Nigeria",
        "id" to "Indonesia",
        "my" to "Malaysia",
        "ph" to "Philippines",
        "th" to "Thailand",
        "vn" to "Vietnam",
        "tw" to "Taiwan",
        "hk" to "Hong Kong",
        "sg" to "Singapore",
        "ar" to "Argentina",
        "co" to "Colombia",
        "cl" to "Chile",
        "pe" to "Peru",
        "eg" to "Egypt",
        "sa" to "Saudi Arabia",
        "ae" to "United Arab Emirates",
        "il" to "Israel"
    )

// --- added for the Phase 2 Search push (SearchScreen's pureBlack flag) ---
// Verbatim Mochy key (PreferenceKeys.kt:22). Mochy's host reads it with
// default false and passes it to SearchScreen for the page background.
val PureBlackKey = booleanPreferencesKey("pureBlack")
