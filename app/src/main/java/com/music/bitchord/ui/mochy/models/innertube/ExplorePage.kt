// Ported from Mochy Music (GPL-3.0): providers/innertube/.../pages/ExplorePage.kt
// Data class only, @Serializable kept. The adapter fills it from ChordWave's
// FEmusic_explore shelves (new releases) + moodAndGenres().
package com.music.bitchord.ui.mochy.models.innertube

import kotlinx.serialization.Serializable

@Serializable
data class ExplorePage(
    val newReleaseAlbums: List<AlbumItem>,
    val moodAndGenres: List<MoodAndGenres.Item>,
)
