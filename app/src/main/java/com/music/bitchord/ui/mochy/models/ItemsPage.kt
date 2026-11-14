// Ported from Mochy Music (GPL-3.0): models/ItemsPage.kt
// Verbatim 2-field data class.
package com.music.bitchord.ui.mochy.models

import com.music.bitchord.ui.mochy.models.innertube.YTItem

data class ItemsPage(
    val items: List<YTItem>,
    val continuation: String?,
)
