// Ported from Mochy Music (GPL-3.0): ui/screens/search/suggestions/SuggestionModels.kt
// SuggestionModels.kt — the Apple Music chart entries the suggestions
// screens show (top songs, trending artists, top albums, top videos).

package com.music.bitchord.ui.mochy.screens.search.suggestions

data class SuggestionTrack(
    val rank: Int,
    val title: String,
    val artist: String,
    val thumbnailUrl: String?,
    val appleMusicUrl: String? = null
)

data class SuggestionArtist(
    val rank: Int,
    val name: String,
    val thumbnailUrl: String?
)

data class SuggestionAlbum(
    val rank: Int,
    val title: String,
    val artist: String,
    val thumbnailUrl: String?,
    val appleMusicUrl: String? = null
)
