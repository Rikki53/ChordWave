// Ported from Mochy Music (GPL-3.0): models/ArtistListRecommendation.kt
// Verbatim: ArtistGroup only (the Mochy file holds no other type).
// YTItem comes from com.music.bitchord.ui.mochy.models.innertube, which ChordWave already
// depends on.

package com.music.bitchord.ui.mochy.models

import com.music.bitchord.ui.mochy.models.innertube.YTItem

data class ArtistGroup(
    val artistName: String,
    val artistId: String?,
    val artistThumbnailUrl: String?,
    val items: List<YTItem>,
)
