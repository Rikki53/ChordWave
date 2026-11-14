// PlaylistSources.kt — one place that answers "what playlist is this id?"
// for the adapters that read playlists by route id (LocalPlaylistViewModel,
// AddMusicViewModel). Two kinds exist in ChordWave: local-file playlists in
// OfflinePlaylists (bare ids — "liked" or "p<base36>") and server pages
// addressed by browse id. A Mochy local id ("LP" + 8 letters) names neither
// — there is no store for YouTube-song playlists yet (NEEDS.md C2) — so it
// resolves to nothing rather than to a garbage server request.

package com.music.bitchord.ui.mochy.viewmodels

import com.music.bitchord.data.OfflinePlaylist
import com.music.bitchord.data.OfflinePlaylists
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.model.Song as CwSong

/** A local-file playlist by its route id, or null when the id isn't one. */
internal fun offlinePlaylistFor(playlistId: String): OfflinePlaylist? =
    OfflinePlaylists.get(OfflinePlaylists.PREFIX + playlistId)

/**
 * The browse id a playlist id opens as, or null when the id names no
 * server page. VL/MPRE ids browse as-is; everything else takes a VL
 * prefix (PL/OLAK/RD/UU/SE, and bare ids such as LM = Liked Music).
 * Mirrors HomeViewModel.playlistSongs, with a lenient fallback for ids
 * that carry no prefix at all.
 */
internal fun browseIdForPlaylist(playlistId: String): String? = when {
    playlistId.startsWith("VL") || playlistId.startsWith("MPRE") -> playlistId
    playlistId.startsWith("LP") -> null // a Mochy local id: no server page until a local store exists
    playlistId.isBlank() -> null
    else -> "VL$playlistId"
}

/**
 * Every entry of a playlist, in order: local-file playlists from the
 * store, server playlists paged whole (playlistEntries, capped at
 * MAX_PAGES like the Classic reorder flow), falling back to the first
 * browse page when the full list can't be read (entries without a
 * setVideoId, or a list past the page cap). Empty when the id names
 * nothing.
 */
internal suspend fun fetchPlaylistSongs(playlistId: String): List<CwSong> {
    offlinePlaylistFor(playlistId)?.let { return it.songs }
    val browseId = browseIdForPlaylist(playlistId) ?: return emptyList()
    YtMusicRepository.playlistEntries(browseId).getOrNull()?.let { return it }
    return YtMusicRepository.browseSongs(browseId).getOrNull()?.songs.orEmpty()
}

/**
 * Pages a browse feed to its end (or MAX_PAGES), returning every song in
 * order. Used where a complete list is the only honest answer (Liked
 * Music); callers that only need the first screenful use browseSongs
 * directly and page behind it like OnlinePlaylistViewModel does.
 */
internal suspend fun fetchAllBrowseSongs(browseId: String): List<CwSong> {
    val first = YtMusicRepository.browseSongs(browseId).getOrNull() ?: return emptyList()
    val out = first.songs.toMutableList()
    var token = first.continuation
    var pages = 1
    while (token != null && pages < YtMusicRepository.MAX_PAGES) {
        val next = YtMusicRepository.moreSongs(token).getOrNull() ?: break
        out += next.songs
        token = next.continuation
        pages++
    }
    return out.distinctBy { it.videoId }
}
