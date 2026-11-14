// Ported from Mochy Music (GPL-3.0): db/entities/PlaylistSong.kt +
// db/entities/PlaylistSongMap.kt (plain data classes — Room annotations
// and the @Relation stripped; no database exists in Mochy mode). A song's
// membership in a playlist: the map row carries the playlist position and
// the setVideoId YouTube needs to remove the entry
// (YtMusicRepository.removeFromPlaylist takes (setVideoId, videoId)
// pairs). SongMenu only null-checks it today; the remove row is a Phase 3
// stub until a playlist store exists.
package com.music.bitchord.ui.mochy.models

data class PlaylistSongMap(
    val id: Int = 0,
    val playlistId: String,
    val songId: String,
    val position: Int = 0,
    val setVideoId: String? = null,
)

data class PlaylistSong(
    val map: PlaylistSongMap,
    val song: Song,
)
