// Ported from Mochy Music (GPL-3.0): providers/innertube/.../pages/SearchSummaryPage.kt
// Data classes + pure filter helpers only (verbatim). The fromMusic*Renderer
// companions need Mochy's renderer types and do NOT port — the adapter builds
// pages from ChordWave search rows instead (Mappers4.toSearchSummaryPage).
package com.music.bitchord.ui.mochy.models.innertube
import com.music.innertube.models.YTItem
import com.music.innertube.models.filterExplicit
import com.music.innertube.models.filterVideoSongs
import com.music.innertube.models.filterYoutubeShorts

data class SearchSummary(
    val title: String,
    val items: List<YTItem>,
)

data class SearchSummaryPage(
    val summaries: List<SearchSummary>,
) {
    fun filterExplicit(enabled: Boolean) =
        if (enabled) {
            SearchSummaryPage(
                summaries.mapNotNull { s ->
                    SearchSummary(
                        title = s.title,
                        items =
                            s.items.filterExplicit().ifEmpty {
                                return@mapNotNull null
                            },
                    )
                },
            )
        } else {
            this
        }

    fun filterVideoSongs(disableVideos: Boolean) =
        if (disableVideos) {
            SearchSummaryPage(
                summaries.mapNotNull { s ->
                    SearchSummary(
                        title = s.title,
                        items =
                            s.items.filterVideoSongs(true).ifEmpty {
                                return@mapNotNull null
                            },
                    )
                },
            )
        } else {
            this
        }

    fun filterYoutubeShorts(enabled: Boolean = false) =
        if (enabled) {
            SearchSummaryPage(
                summaries.mapNotNull { s ->
                    SearchSummary(
                        title = s.title,
                        items =
                            s.items.filterYoutubeShorts(true).ifEmpty {
                                return@mapNotNull null
                            },
                    )
                },
            )
        } else {
            this
        }
}
