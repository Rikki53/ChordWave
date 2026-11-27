// Ported from Mochy Music (GPL-3.0): providers/innertube/.../pages/MoodAndGenres.kt
// Data classes only — the fromSectionListRendererContent companion needs
// Mochy's renderer types and does NOT port. The MoodAndGenresViewModel
// adapter maps CW's MoodGenreSection to these. @Serializable because
// ExplorePage (also @Serializable) holds List<MoodAndGenres.Item>.
package com.music.bitchord.ui.mochy.models.innertube
import com.music.innertube.models.BrowseEndpoint

import kotlinx.serialization.Serializable

@Serializable
data class MoodAndGenres(val title: String, val items: List<Item>) {
    @Serializable
    data class Item(val title: String, val stripeColor: Long, val endpoint: BrowseEndpoint)
}
