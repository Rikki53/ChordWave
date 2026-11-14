// Ported from Mochy Music (GPL-3.0): db/entities/AlbumWithSongs.kt
// Plain data class: Mochy's is a Room @Relation, the CW port is NOT a Room
// class — AlbumViewModel builds it from YtMusicRepository.browseSongs().
// (phase2-detail NEEDS.md §(a) C1.)
package com.music.bitchord.ui.mochy.models

import androidx.compose.runtime.Immutable

@Immutable
data class AlbumWithSongs(
    val album: AlbumEntity,
    val artists: List<ArtistEntity>,
    val songs: List<Song>,
)
