// Ported from Mochy Music (GPL-3.0): ui/screens/library/LibraryPlaylistsScreen.kt
// ADAPTATIONS: navigation param -> MochyNavigator (route strings verbatim);
// Mochy packages -> com.music.bitchord.ui.mochy.* staged equivalents. See NEEDS.md.
// LibraryPlaylistsScreen.kt

package com.music.bitchord.ui.mochy.screens.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.ui.mochy.navigation.MochyNavigator
import com.music.bitchord.ui.mochy.importer.ImportSource
import com.music.bitchord.ui.mochy.component.CreatePlaylistDialog
import com.music.bitchord.ui.mochy.component.LocalMenuState
import com.music.bitchord.ui.mochy.component.rememberRevealSeenState
import com.music.bitchord.ui.mochy.player.menu.PlaylistMenu
import com.music.bitchord.ui.mochy.viewmodels.LibraryHomeViewModel
import com.music.bitchord.ui.mochy.viewmodels.LibraryShelfKind

/**
 * The library's playlists, or with a [shelf] the Local or Cached tab's: only the playlists that
 * have one of that tab's songs in them. With [source], only the playlists imported from that service.
 */
@Composable
fun LibraryPlaylistsScreen(
    navigator: MochyNavigator,
    modifier: Modifier = Modifier,
    pureBlack: Boolean = false,
    shelf: LibraryShelfKind? = null,
    source: ImportSource? = null,
) {
    val viewModel: LibraryHomeViewModel = hiltViewModel()
    val all by (shelf?.let(viewModel::shelf)?.playlists ?: viewModel.playlists).collectAsStateWithLifecycle()
    val playlistSources by viewModel.playlistSources.collectAsState()
    // the account load state only drives the library page — a shelf's
    // playlists and an imported source's come from their own stores.
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val loadFailed by viewModel.loadFailed.collectAsStateWithLifecycle()
    val playlists = remember(all, playlistSources, source) {
        if (source == null) all else all.filter { playlistSources[it.id] == source }
    }
    val menuState = LocalMenuState.current
    val coroutineScope = rememberCoroutineScope()
    val likedSongs by viewModel.likedSongs.collectAsStateWithLifecycle()
    val revealSeen = rememberRevealSeenState()
    var creatingPlaylist by remember { mutableStateOf(false) }

    LibraryEntityListScreen(
        title = "Playlist",
        subtitle = when {
            source != null -> "Playlists imported from ${source.label}."
            shelf == LibraryShelfKind.Local -> "Playlists with music on this device."
            shelf == LibraryShelfKind.Cached -> "Playlists with songs in your cache."
            else -> "All playlist on your library listed here."
        },
        searchPlaceholder = "Find a playlist by its name",
        items = playlists,
        idOf = { it.id },
        nameOf = { it.playlist.name },
        subtitleOf = { "${it.songCount} songs" },
        thumbnailOf = { it.thumbnails.firstOrNull() },
        // the same key Home's cards use, so the playlist opens growing out of its cover
        sharedElementKeyOf = { "playlist-${it.id}" },
        onClick = { playlist -> navigator.navigate("local_playlist/${playlist.id}") },
        onLongClick = { playlist ->
            menuState.show {
                PlaylistMenu(
                    playlist = playlist,
                    coroutineScope = coroutineScope,
                    onDismiss = menuState::dismiss,
                    navigator = navigator,
                )
            }
        },
        modifier = modifier,
        pureBlack = pureBlack,
        isLoading = shelf == null && source == null && isLoading,
        loadFailed = shelf == null && source == null && loadFailed,
        onRetry = if (shelf == null && source == null) viewModel::load else null,
        // The main Playlist page always opens with Classic's entries —
        // Liked music, New playlist, Import from link — so it is never
        // the bare "Nothing to show here" it used to be (batch 34). The
        // shelf and imported-source lists stay plain filtered lists.
        header = if (shelf == null && source == null) {
            {
                PlaylistActionCards(
                    likedSongs = likedSongs,
                    navigator = navigator,
                    onNewPlaylist = { creatingPlaylist = true },
                )
            }
        } else {
            null
        },
        revealSeen = if (shelf == null && source == null) revealSeen else null,
    )

    if (creatingPlaylist) {
        CreatePlaylistDialog(
            onDismiss = { creatingPlaylist = false },
            onPlaylistCreated = { playlistId -> navigator.navigate("local_playlist/$playlistId") },
        )
    }
}
