// Ported from Mochy Music (GPL-3.0):
// providers/innertube/.../pages/ArtistPage.kt — the plain data classes
// only. The renderer companions (fromSectionListRendererContent and friends)
// do NOT port: the adapter maps ChordWave's already-parsed ArtistPage onto
// this shape (Mappers6.toMochyArtistPage), it does not parse renderers.
// (phase2-detail NEEDS.md §(a) C4.)
//
// NOTE: com.music.bitchord.data.model.ArtistPage is a different type in a
// different package (CW's parsed artist landing page) — the two do not
// clash. Call sites that need both alias the CW one (CwArtistPage).
package com.music.bitchord.ui.mochy.models.innertube

data class ArtistSection(
    val title: String,
    val items: List<YTItem>,
    val moreEndpoint: BrowseEndpoint?,
)

data class ArtistPage(
    val artist: ArtistItem,
    val sections: List<ArtistSection>,
    val description: String?,
    val subscriberCountText: String?,
    val monthlyListenerCount: String? = null,
    val descriptionRuns: List<Run>? = null,
    val subscribed: Boolean? = null,
)
