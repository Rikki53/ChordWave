// Ported from Mochy Music (GPL-3.0): viewmodels/HomeViewModel.kt
// (CommunityPlaylistItem, lines 96-99).
package com.music.bitchord.ui.mochy.models

import com.music.bitchord.ui.mochy.models.innertube.PlaylistItem
import com.music.bitchord.ui.mochy.models.innertube.SongItem
import kotlinx.serialization.Serializable

@Serializable
data class CommunityPlaylistItem(
    val playlist: PlaylistItem,
    val songs: List<SongItem>
)
