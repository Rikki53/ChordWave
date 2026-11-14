// Ported from Mochy Music (GPL-3.0): db/entities/Album.kt
// Room-free: relation annotations stripped; the embedded
// AlbumEntity and the artist list are plain constructor values.

package com.music.bitchord.ui.mochy.models

import androidx.compose.runtime.Immutable

@Immutable
data class Album(
    val album: AlbumEntity,
    val artists: List<ArtistEntity> = emptyList(),
    val songCountListened: Int? = 0,
    val timeListened: Long? = 0
) : LocalItem() {
    override val id: String
        get() = album.id
    override val title: String
        get() = album.title
    override val thumbnailUrl: String?
        get() = album.thumbnailUrl
    val description: String?
        get() = album.description
}
