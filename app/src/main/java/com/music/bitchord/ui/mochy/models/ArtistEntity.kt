// Ported from Mochy Music (GPL-3.0): db/entities/ArtistEntity.kt
// Room-free: plain data class. Room annotations
// are stripped; field names, types and defaults are unchanged.
// toggleLike() is dropped — it fires YouTube network requests (see NEEDS.md).
// generateArtistId() uses the Kotlin stdlib instead of Apache Commons
// RandomStringUtils, which ChordWave does not depend on; the shape is the
// same ("LA" + 8 letters).

package com.music.bitchord.ui.mochy.models

import androidx.compose.runtime.Immutable
import java.time.LocalDateTime

@Immutable
data class ArtistEntity(
    val id: String,
    val name: String,
    val thumbnailUrl: String? = null,
    val channelId: String? = null,
    val lastUpdateTime: LocalDateTime = LocalDateTime.now(),
    val bookmarkedAt: LocalDateTime? = null,
    val isLocal: Boolean = false
) {
    val isYouTubeArtist: Boolean
        get() = id.startsWith("UC") || id.startsWith("FEmusic_library_privately_owned_artist")

    val isPrivatelyOwnedArtist: Boolean
        get() = id.startsWith("FEmusic_library_privately_owned_artist")

    fun localToggleLike() = copy(
        bookmarkedAt = if (bookmarkedAt != null) null else LocalDateTime.now(),
    )

    companion object {
        fun generateArtistId(): String {
            val letters = ('a'..'z') + ('A'..'Z')
            return "LA" + (1..8).map { letters.random() }.joinToString("")
        }
    }
}
