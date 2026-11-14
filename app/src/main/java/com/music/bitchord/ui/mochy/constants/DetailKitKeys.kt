// Ported from Mochy Music (GPL-3.0): constants/PreferenceKeys.kt
// (PlaylistStyle, PlaylistStyleKey, AddToPlaylistSortTypeKey,
// AddToPlaylistSortDescendingKey, YtSyncNewPlaylistsKey only).
//
// These are the preference declarations the detail kit + its menus read
// that no staged dir had yet (checked 15 Nov 2026: PlaylistSortType,
// SongSortType, MyTopFilter and the C9/C11 keys already live in
// phase1-deps PreferenceKeys.kt; these five do not). They live here so
// this dir compiles on its own; the coordinator may fold them into
// phase1-deps PreferenceKeys.kt at merge and delete this file — but do
// NOT add them there while this file exists, or the build breaks twice.

package com.music.bitchord.ui.mochy.constants

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

// how playlist and album headers are laid out at the top of the page
enum class PlaylistStyle(val displayName: String) {
    FLOW("Flow"),
    CLASSIC("Classic"),
    MINIMAL("Minimal"),
}

val PlaylistStyleKey = stringPreferencesKey("playlistStyle")

val AddToPlaylistSortTypeKey = stringPreferencesKey("addToPlaylistSortType")
val AddToPlaylistSortDescendingKey = booleanPreferencesKey("addToPlaylistSortDescending")

val YtSyncNewPlaylistsKey = booleanPreferencesKey("ytSyncNewPlaylists")
