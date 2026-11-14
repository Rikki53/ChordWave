// Ported from Mochy Music (GPL-3.0): db/entities/PlaylistEntity.kt
// Room-free: plain data class. Room annotations
// are stripped; field names, types and defaults are unchanged.
// toggleLike() is dropped — it fires a YouTube network request (see NEEDS.md).
// generatePlaylistId() uses the Kotlin stdlib instead of Apache Commons
// RandomStringUtils, which ChordWave does not depend on; the shape is the
// same ("LP" + 8 letters).

package com.music.bitchord.ui.mochy.models

import androidx.compose.runtime.Immutable
import java.time.LocalDateTime

@Immutable
data class PlaylistEntity(
    val id: String = generatePlaylistId(),
    val name: String,
    val browseId: String? = null,
    val createdAt: LocalDateTime? = LocalDateTime.now(),
    val lastUpdateTime: LocalDateTime? = LocalDateTime.now(),
    val isEditable: Boolean = true,
    val bookmarkedAt: LocalDateTime? = null,
    val remoteSongCount: Int? = null,
    val playEndpointParams: String? = null,
    val thumbnailUrl: String? = null,
    val shuffleEndpointParams: String? = null,
    val radioEndpointParams: String? = null,
    val isLocal: Boolean = false,
    val isAutoSync: Boolean = false,
    /** Free text the owner wrote about the playlist. Local only - never sent to YouTube. */
    val description: String? = null,
) {
    companion object {
        const val LIKED_PLAYLIST_ID = "LP_LIKED"
        const val DOWNLOADED_PLAYLIST_ID = "LP_DOWNLOADED"

        fun generatePlaylistId(): String {
            val letters = ('a'..'z') + ('A'..'Z')
            return "LP" + (1..8).map { letters.random() }.joinToString("")
        }
    }

    val shareLink: String?
        get() {
            return if (browseId != null)
                "https://music.youtube.com/playlist?list=$browseId"
            else null
        }

    fun localToggleLike() = copy(
        bookmarkedAt = if (bookmarkedAt != null) null else LocalDateTime.now()
    )
}
