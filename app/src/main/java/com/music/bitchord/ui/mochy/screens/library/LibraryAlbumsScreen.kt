// Ported from Mochy Music (GPL-3.0): ui/screens/library/LibraryAlbumsScreen.kt
// ADAPTATIONS: navigation param -> MochyNavigator (route strings verbatim);
// Mochy packages -> com.music.bitchord.ui.mochy.* staged equivalents. See NEEDS.md.
// LibraryAlbumsScreen.kt

package com.music.bitchord.ui.mochy.screens.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.ui.mochy.navigation.MochyNavigator
import com.music.bitchord.ui.mochy.playback.LocalPlayerConnection
import com.music.bitchord.R
import com.music.bitchord.ui.mochy.models.Album
import com.music.bitchord.ui.mochy.extensions.toMediaItem
import com.music.bitchord.ui.mochy.importer.ImportSource
import com.music.bitchord.ui.mochy.playback.queues.ListQueue
import com.music.bitchord.ui.mochy.viewmodels.LibraryHomeViewModel
import com.music.bitchord.ui.mochy.viewmodels.LibraryImportedViewModel
import com.music.bitchord.ui.mochy.viewmodels.LibraryShelfKind

/**
 * The library's albums, or with a [shelf] the Local or Cached tab's: only the albums one of that
 * tab's songs is from. Those can include an imported file's album, which is only a tag and has no
 * page - opening one plays its songs instead. With a [source], the albums one service's songs come from.
 */
@Composable
fun LibraryAlbumsScreen(
    navigator: MochyNavigator,
    modifier: Modifier = Modifier,
    pureBlack: Boolean = false,
    shelf: LibraryShelfKind? = null,
    source: ImportSource? = null,
) {
    if (shelf != null) {
        ShelfAlbumsScreen(navigator, shelf, modifier, pureBlack)
        return
    }
    if (source != null) {
        ImportedAlbumsScreen(navigator, source, modifier, pureBlack)
        return
    }

    val viewModel: LibraryHomeViewModel = hiltViewModel()
    val albums by viewModel.albums.collectAsState()
    val entries = remember(albums) { albums.toGridEntries() }

    LibraryGridScreen(
        title = stringResource(R.string.albums),
        subtitle = stringResource(R.string.library_albums_subtitle),
        searchPlaceholder = stringResource(R.string.library_albums_search),
        entries = entries,
        onOpen = { navigator.navigate("album/${it.id}") },
        // Both buttons open the newest album rather than building a queue across every album:
        // this screen only loads album metadata, so a real "play everything" would mean fetching
        // every album's tracklist up front just to make one button work.
        onPlay = { albums.firstOrNull()?.let { navigator.navigate("album/${it.id}") } },
        onShuffle = { albums.randomOrNull()?.let { navigator.navigate("album/${it.id}") } },
        modifier = modifier,
        pureBlack = pureBlack,
    )
}

@Composable
private fun ImportedAlbumsScreen(
    navigator: MochyNavigator,
    source: ImportSource,
    modifier: Modifier,
    pureBlack: Boolean,
) {
    val viewModel: LibraryImportedViewModel = hiltViewModel()
    val albums by viewModel.albums.collectAsState()
    val entries = remember(albums) { albums.toGridEntries() }

    LibraryGridScreen(
        title = stringResource(R.string.albums),
        subtitle = stringResource(R.string.library_imported_albums_subtitle, source.label),
        searchPlaceholder = stringResource(R.string.library_albums_search),
        entries = entries,
        onOpen = { navigator.navigate("album/${it.id}") },
        // same as the library's albums: only album details are loaded here, so both open one
        onPlay = { albums.firstOrNull()?.let { navigator.navigate("album/${it.id}") } },
        onShuffle = { albums.randomOrNull()?.let { navigator.navigate("album/${it.id}") } },
        modifier = modifier,
        pureBlack = pureBlack,
    )
}

private fun List<Album>.toGridEntries() = map {
    LibraryGridEntry(
        id = it.id,
        title = it.title,
        subtitle = it.artists.joinToString { a -> a.name }.ifBlank { null },
        thumbnailUrl = it.album.thumbnailUrl,
        sharedElementKey = "album-${it.id}",
    )
}

@Composable
private fun ShelfAlbumsScreen(
    navigator: MochyNavigator,
    shelf: LibraryShelfKind,
    modifier: Modifier,
    pureBlack: Boolean,
) {
    val viewModel: LibraryHomeViewModel = hiltViewModel()
    // with the lifecycle: the Cached shelf looks at the cache every couple of seconds while watched
    val albums by viewModel.shelf(shelf).albums.collectAsStateWithLifecycle()
    val playerConnection = LocalPlayerConnection.current ?: return

    val entries = remember(albums) {
        albums.map {
            LibraryGridEntry(
                id = it.key,
                title = it.title,
                subtitle = it.artists,
                thumbnailUrl = it.thumbnailUrl,
                sharedElementKey = it.albumId?.let { id -> "album-$id" },
            )
        }
    }

    fun playAll(shuffle: Boolean) {
        val songs = albums.flatMap { it.songs }
        if (songs.isEmpty()) return
        val ordered = if (shuffle) songs.shuffled() else songs
        playerConnection.playQueue(ListQueue(title = shelf.label, items = ordered.map { it.toMediaItem() }))
    }

    LibraryGridScreen(
        title = "Albums",
        subtitle = when (shelf) {
            LibraryShelfKind.Local -> "Albums with music on this device."
            LibraryShelfKind.Cached -> "Albums with songs in your cache."
        },
        searchPlaceholder = "Find an album listed here",
        entries = entries,
        onOpen = { entry ->
            val album = albums.firstOrNull { it.key == entry.id } ?: return@LibraryGridScreen
            if (album.albumId != null) {
                navigator.navigate("album/${album.albumId}")
            } else {
                playerConnection.playQueue(
                    ListQueue(title = album.title, items = album.songs.map { it.toMediaItem() }),
                )
            }
        },
        // Every album here has its songs on hand, so these play them all for real: album by
        // album, or every album's songs here shuffled together.
        onPlay = { playAll(shuffle = false) },
        onShuffle = { playAll(shuffle = true) },
        modifier = modifier,
        pureBlack = pureBlack,
    )
}
