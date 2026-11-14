// Ported from Mochy Music (GPL-3.0): constants/PreferenceKeys.kt (video keys only).
// Verbatim Mochy key names + enum values. These live in the Mochy DataStore
// ("settings"), separate from ChordWave's SharedPreferences-backed AppSettings.
// Kept in their own file (not merged into PreferenceKeys.kt) because this
// chunk stages read-only against the worktree; the coordinator may fold them
// into PreferenceKeys.kt on merge — the names are unique either way.

package com.music.bitchord.ui.mochy.constants

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

enum class VideoQuality(val shortSide: Int) {
    AUTO(0),
    // past 1080p youtube only has vp9 or av1, played only where the phone decodes it in hardware
    Q2160(2160),
    Q1440(1440),
    Q1080(1080),
    Q720(720),
    Q480(480),
    Q360(360),
    Q240(240),
    Q144(144),
}

val VideoQualityKey = stringPreferencesKey("videoQuality")

// the full video mode shows the whole picture with room round it, instead of zooming it to fill the screen
val VideoModeWholeVideoKey = booleanPreferencesKey("videoModeWholeVideo")

// the video behind the lyrics lines itself up with the song via the lyrics' own timing
val YtVideoBackgroundLyricsSyncKey = booleanPreferencesKey("ytVideoBackgroundLyricsSync")

// NOTE (coordinator, 15 Nov): LandscapeUnlockedKey is NOT declared here — it
// already lives in constants/PreferenceKeys.kt (same package); declaring it
// twice would break the build.
