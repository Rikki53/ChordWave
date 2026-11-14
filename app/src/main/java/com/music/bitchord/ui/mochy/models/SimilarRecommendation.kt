// Ported from Mochy Music (GPL-3.0): models/SimilarRecommendation.kt
// Verbatim. YTItem comes from com.music.bitchord.ui.mochy.models.innertube, which ChordWave
// already depends on; LocalItem is the ported model in this package.

package com.music.bitchord.ui.mochy.models

import com.music.bitchord.ui.mochy.models.innertube.YTItem

data class SimilarRecommendation(
    val title: LocalItem,
    val items: List<YTItem>,
)
