// Ported from Mochy Music (GPL-3.0): db/entities/Song.kt
// Room-free: relation annotations stripped; the embedded
// SongEntity, the artist list and the album are plain constructor values.
// The `format: FormatEntity?` field is DROPPED — FormatEntity is not
// ported (see NEEDS.md).

package com.music.bitchord.ui.mochy.models

import androidx.compose.runtime.Immutable

@Immutable
data class Song
@JvmOverloads
constructor(
    val song: SongEntity,
    val artists: List<ArtistEntity>,
    val album: AlbumEntity? = null,
) : LocalItem() {
    override val id: String
        get() = song.id
    override val title: String
        get() = song.title
    override val thumbnailUrl: String?
        get() = song.thumbnailUrl
    val romanizeLyrics: Boolean
        get() = song.romanizeLyrics
}
