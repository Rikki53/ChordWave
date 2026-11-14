// Ported from Mochy Music (GPL-3.0): importer/LinkImporter.kt (LinkSource enum
// only). The LinkImporter engine (fetching public playlists/albums from share
// links) is NOT ported — the ImportSyncViewModel adapter owns it (see
// NEEDS.md); CW's SpotifyImporter covers the Spotify leg already.
package com.music.bitchord.ui.mochy.importer

enum class LinkSource(val label: String, val example: String) {
    SPOTIFY("Spotify", "https://open.spotify.com/playlist/…"),
    APPLE_MUSIC("Apple Music", "https://music.apple.com/us/playlist/…"),
    DEEZER("Deezer", "https://www.deezer.com/playlist/…"),
    YOUTUBE("YouTube Music", "https://music.youtube.com/playlist?list=…"),
}
