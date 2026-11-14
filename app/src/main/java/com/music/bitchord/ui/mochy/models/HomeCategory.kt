// Ported from Mochy Music (GPL-3.0): viewmodels/HomeRecommender.kt
// (HomeCategory, lines 41-65). Data class only — the builder
// (HomeRecommender) is a separate Phase 2D work item.
package com.music.bitchord.ui.mochy.models

import com.music.bitchord.ui.mochy.models.innertube.BrowseEndpoint
import com.music.bitchord.ui.mochy.models.innertube.YTItem
import kotlinx.serialization.Serializable

@Serializable
data class HomeCategory(
    val id: String,
    val kind: Kind,
    val title: String,
    val items: List<YTItem>,
    val thumbnailUrl: String? = null,
    // where the title's chevron goes: the artist, or the genre's page
    val endpoint: BrowseEndpoint? = null,
) {
    enum class Kind {
        /** "Because you like <song>": songs that grow out of one you play a lot (Apple's and Spotify's song shelves) */
        BecauseYouLike,

        /** "More like <artist>": other artists' songs from that artist's radio, the way Spotify's artist shelves work */
        MoreLikeArtist,

        /** a genre your top artists are filed under */
        YourGenre,

        /** a genre you don't play yet, so Home isn't only more of the same */
        NewGenre,

        /** this year's singles and albums from the artists you play most (Spotify's Release Radar idea) */
        NewFromArtists,
    }
}
