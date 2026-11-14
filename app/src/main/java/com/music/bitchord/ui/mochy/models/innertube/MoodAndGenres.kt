// Ported from Mochy Music (GPL-3.0): providers/innertube/.../pages/MoodAndGenres.kt
// Data classes only — the fromSectionListRendererContent companion needs
// Mochy's renderer types and does NOT port. The MoodAndGenresViewModel
// adapter maps CW's MoodGenreSection to these.
package com.music.bitchord.ui.mochy.models.innertube

data class MoodAndGenres(val title: String, val items: List<Item>) {
    data class Item(val title: String, val stripeColor: Long, val endpoint: BrowseEndpoint)
}
