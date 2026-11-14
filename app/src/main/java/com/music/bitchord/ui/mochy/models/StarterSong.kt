// Ported from Mochy Music (GPL-3.0): viewmodels/HomeViewModel.kt
// (StarterSong, lines 1344-1348). Filling it is the adapter's problem
// (DATA-MAPPING §1 row 1 — empty in v1); the shape ports as-is.
package com.music.bitchord.ui.mochy.models

import com.music.bitchord.ui.mochy.models.innertube.SongItem

data class StarterSong(
    val song: SongItem,
    val artistName: String,
    val artistThumbnail: String?,
)
