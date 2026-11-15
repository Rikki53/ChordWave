// Ported from Mochy Music (GPL-3.0): ui/screens/library/LibraryArtistsScreen.kt
// ADAPTATIONS: navigation param -> MochyNavigator (route strings verbatim);
// Mochy packages -> com.music.bitchord.ui.mochy.* staged equivalents. See NEEDS.md.
// LibraryArtistsScreen.kt

package com.music.bitchord.ui.mochy.screens.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.ui.mochy.navigation.MochyNavigator
import com.music.bitchord.ui.mochy.component.LocalMenuState
import com.music.bitchord.ui.mochy.player.menu.ArtistMenu
import com.music.bitchord.ui.mochy.viewmodels.LibraryHomeViewModel
import com.music.bitchord.ui.mochy.viewmodels.LibraryShelfKind

/**
 * The library's artists, or with a [shelf] the Local or Cached tab's: only the artists of that
 * tab's songs, each with how many of its songs are there.
 */
@Composable
fun LibraryArtistsScreen(
    navigator: MochyNavigator,
    modifier: Modifier = Modifier,
    pureBlack: Boolean = false,
    shelf: LibraryShelfKind? = null,
) {
    val viewModel: LibraryHomeViewModel = hiltViewModel()
    val artists by (shelf?.let(viewModel::shelf)?.artists ?: viewModel.artists).collectAsStateWithLifecycle()
    // the account load state only drives the library page — a shelf's
    // artists come from its own songs, not from the account fetch.
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val loadFailed by viewModel.loadFailed.collectAsStateWithLifecycle()
    val menuState = LocalMenuState.current
    val coroutineScope = rememberCoroutineScope()

    LibraryEntityListScreen(
        title = "Artist",
        subtitle = when (shelf) {
            LibraryShelfKind.Local -> "Artists with music on this device."
            LibraryShelfKind.Cached -> "Artists with songs in your cache."
            null -> "All artist on your library listed here."
        },
        searchPlaceholder = "Find an artist by name",
        items = artists,
        idOf = { it.id },
        nameOf = { it.artist.name },
        subtitleOf = {
            val count = if (it.songCount == 1) "1 song" else "${it.songCount} songs"
            when (shelf) {
                LibraryShelfKind.Local -> "$count on this device"
                LibraryShelfKind.Cached -> "$count cached"
                null -> count
            }
        },
        thumbnailOf = { it.artist.thumbnailUrl },
        roundThumbnails = true,
        onClick = { artist -> navigator.navigate("artist/${artist.id}") },
        onLongClick = { artist ->
            menuState.show {
                ArtistMenu(
                    originalArtist = artist,
                    coroutineScope = coroutineScope,
                    onDismiss = menuState::dismiss,
                )
            }
        },
        modifier = modifier,
        pureBlack = pureBlack,
        isLoading = shelf == null && isLoading,
        loadFailed = shelf == null && loadFailed,
        onRetry = if (shelf == null) viewModel::load else null,
    )
}
