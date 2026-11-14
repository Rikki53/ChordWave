// Ported from Mochy Music (GPL-3.0): db/entities/AlbumEntity.kt
// Room-free: plain data class. Room annotations
// are stripped; field names, types and defaults are unchanged.
// toggleLike() is dropped — it fires a YouTube network request (see NEEDS.md).

package com.music.bitchord.ui.mochy.models

import androidx.compose.runtime.Immutable
import java.time.LocalDateTime

@Immutable
data class AlbumEntity(
    val id: String,
    val playlistId: String? = null,
    val title: String,
    val year: Int? = null,
    val thumbnailUrl: String? = null,
    val themeColor: Int? = null,
    val songCount: Int,
    val duration: Int,
    val explicit: Boolean = false,
    val lastUpdateTime: LocalDateTime = LocalDateTime.now(),
    val bookmarkedAt: LocalDateTime? = null,
    val likedDate: LocalDateTime? = null,
    val inLibrary: LocalDateTime? = null,
    val description: String? = null,
    val isLocal: Boolean = false,
    val isUploaded: Boolean = false,
) {
    fun localToggleLike() = copy(
        bookmarkedAt = if (bookmarkedAt != null) null else LocalDateTime.now()
    )

    fun toggleUploaded() = copy(
        isUploaded = !isUploaded
    )

    fun toggleLibrary() = copy(
        inLibrary = if (inLibrary != null) null else LocalDateTime.now()
    )
}
