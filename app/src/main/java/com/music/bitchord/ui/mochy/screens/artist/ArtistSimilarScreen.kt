// Ported from Mochy Music (GPL-3.0): ui/screens/artist/ArtistSimilarScreen.kt
// ArtistSimilarScreen.kt — Similar artists page for one artist.
//
// Port notes (see staging/phase2-detail/NEEDS.md for the full list):
// - Navigation goes through MochyNavigator; route strings are Mochy's verbatim.
//   Back is popBackStack(); back-to-main is clearDetails().
// - The screen never touches the database: data comes through the VM contract.
// - ArtistSimilarViewModel + SimilarGroups are NEEDS.md C6.

package com.music.bitchord.ui.mochy.screens.artist

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import com.music.bitchord.ui.mochy.navigation.MochyNavigator
import com.music.bitchord.ui.mochy.utils.resize
import com.music.bitchord.ui.mochy.utils.ArtistPageCache
import com.music.bitchord.ui.mochy.viewmodels.ArtistSimilarViewModel

/** "similar to <artist>": the artists around them, the playlists they're on, those artists' songs */
@Composable
fun ArtistSimilarScreen(
    navigator: MochyNavigator,
    artistId: String,
    viewModel: ArtistSimilarViewModel = hiltViewModel(),
) {
    // Coordinator: route args rule — the host passes artistId; the VM loads on it.
    LaunchedEffect(artistId) { viewModel.load(artistId) }
    val page by viewModel.page.collectAsState()
    val groups by viewModel.groups.collectAsState()
    val deepLoading by viewModel.deepLoading.collectAsState()
    val header = ArtistPageCache.header(viewModel.artistId)

    SimilarPage(
        navigator = navigator,
        bandImageUrl = header?.bannerUrl
            ?: (page?.artist?.thumbnail ?: viewModel.picks?.artistThumbnailUrl)?.resize(544, 544),
        smallLine = "similar to",
        bigLine = header?.name ?: page?.artist?.title ?: viewModel.picks?.artistName.orEmpty(),
        groups = groups,
        deepLoading = deepLoading,
        artistsTitle = "Similar Artist",
    )
}
