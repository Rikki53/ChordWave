// Ported from Mochy Music (GPL-3.0): db/entities/SongEntity.kt
// Room-free: plain data class. Room annotations
// and indices are stripped; field names, types and defaults
// are unchanged. toggleLike()/toggleLibrary() are dropped — they fire
// YouTube network requests (see NEEDS.md). The `genre` field is restored:
// Mochy's SongEntity has it (its Genres tab reads it); the first port dropped it.

package com.music.bitchord.ui.mochy.models

import androidx.compose.runtime.Immutable
import java.time.LocalDateTime

@Immutable
data class SongEntity(
    val id: String,
    val title: String,
    val duration: Int = -1,
    val thumbnailUrl: String? = null,
    val albumId: String? = null,
    val albumName: String? = null,
    // the genre tag of an imported file ("Rock; Pop" sits in both); null for
    // everything else — the Genres tab only ever sees tagged local files
    val genre: String? = null,
    val explicit: Boolean = false,
    val year: Int? = null,
    val date: LocalDateTime? = null,
    val dateModified: LocalDateTime? = null,
    val liked: Boolean = false,
    val likedDate: LocalDateTime? = null,
    val totalPlayTime: Long = 0,
    val inLibrary: LocalDateTime? = null,
    val dateDownload: LocalDateTime? = null,
    val isLocal: Boolean = false,
    val libraryAddToken: String? = null,
    val libraryRemoveToken: String? = null,
    val lyricsOffset: Int = 0,
    val romanizeLyrics: Boolean = true,
    val isDownloaded: Boolean = false,
    val isUploaded: Boolean = false,
    val isVideo: Boolean = false,
    val localUri: String? = null,
    // in the library only because it was liked, so its album stays out of Albums unless the old way is on
    val addedByLike: Boolean = false,
) {
    fun localToggleLike() = copy(
        liked = !liked,
        likedDate = if (!liked) LocalDateTime.now() else null,
    )

    fun toggleUploaded() = copy(
        isUploaded = !isUploaded
    )
}
