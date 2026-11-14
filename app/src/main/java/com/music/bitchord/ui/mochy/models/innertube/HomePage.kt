// Ported from Mochy Music (GPL-3.0): providers/innertube/.../pages/HomePage.kt
// (DATA CLASSES ONLY — the companion renderer parsers
// fromMusicCarouselShelfRenderer / fromMusicTwoRowItemRenderer /
// Chip.fromChipCloudChipRenderer are dropped: they depend on innertube
// renderer types CW never parses. The HomeViewModel adapter constructs
// HomePage from CW's HomeFeed/HomeShelf/ShelfItem instead. The
// filterExplicit / filterVideoSongs helpers are kept verbatim.)
package com.music.bitchord.ui.mochy.models.innertube

import kotlinx.serialization.Serializable

@Serializable
data class HomePage(
    val chips: List<Chip>?,
    val sections: List<Section>,
    val continuation: String? = null,
) {
    @Serializable
    data class Chip(
        val title: String,
        val endpoint: BrowseEndpoint?,
        val deselectEndPoint: BrowseEndpoint?,
    )

    @Serializable
    data class Section(
        val title: String,
        val label: String?,
        val thumbnail: String?,
        val endpoint: BrowseEndpoint?,
        val items: List<YTItem>,
    )

    fun filterExplicit(enabled: Boolean = true) =
        if (enabled) {
            copy(sections = sections.map {
                it.copy(items = it.items.filterExplicit())
            })
        } else this

    fun filterVideoSongs(disableVideos: Boolean = false) =
        if (disableVideos) {
            copy(sections = sections.map { section ->
                section.copy(items = section.items.filterVideoSongs(true))
            })
        } else this
}
