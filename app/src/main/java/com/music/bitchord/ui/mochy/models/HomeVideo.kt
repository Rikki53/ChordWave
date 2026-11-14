// Ported from Mochy Music (GPL-3.0): utils/ArtistVideos.kt (HomeVideo,
// lines 15-20). Moved to ui.mochy.models per DATA-MAPPING §2.3 (HomeScreen
// imports it from there).
package com.music.bitchord.ui.mochy.models

import com.music.bitchord.ui.mochy.models.innertube.SongItem
import kotlinx.serialization.Serializable

/** a video on one of Home's banner rows, with its title tidied up for the card */
@Serializable
data class HomeVideo(
    val item: SongItem,
    val title: String,
    val subtitle: String,
)
