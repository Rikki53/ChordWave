// Ported from Mochy Music (GPL-3.0): viewmodels/HomeViewModel.kt
// (DailyDiscoverItem, lines 89-93). The adapter sets relatedEndpoint to
// null — CW has no `related` endpoint, and no UI file consumes it
// (DATA-MAPPING §1 row 22).
package com.music.bitchord.ui.mochy.models

import com.music.bitchord.ui.mochy.models.innertube.BrowseEndpoint
import com.music.bitchord.ui.mochy.models.innertube.YTItem

data class DailyDiscoverItem(
    val seed: Song,
    val recommendation: YTItem,
    val relatedEndpoint: BrowseEndpoint?
)
