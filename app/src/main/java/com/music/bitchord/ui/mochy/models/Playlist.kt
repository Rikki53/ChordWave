// Ported from Mochy Music (GPL-3.0): db/entities/Playlist.kt
// Room-free: relation annotations stripped; the embedded
// PlaylistEntity and the song-thumbnail list are plain constructor values.

package com.music.bitchord.ui.mochy.models

import androidx.compose.runtime.Immutable

@Immutable
data class Playlist(
    val playlist: PlaylistEntity,
    val songCount: Int,
    val songThumbnails: List<String?>,
) : LocalItem() {
    override val id: String
        get() = playlist.id
    override val title: String
        get() = playlist.name
    override val thumbnailUrl: String?
        get() = null

    val thumbnails: List<String>
        get() {
            return if (playlist.thumbnailUrl != null)
                listOf(playlist.thumbnailUrl)
            else songThumbnails.filterNotNull()
        }
}
