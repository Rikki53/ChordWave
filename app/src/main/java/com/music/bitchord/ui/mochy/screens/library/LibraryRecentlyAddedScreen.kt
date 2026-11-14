// Ported from Mochy Music (GPL-3.0): ui/screens/library/LibraryRecentlyAddedScreen.kt
// ADAPTATIONS: navigation param -> MochyNavigator (route strings verbatim);
// Mochy packages -> com.music.bitchord.ui.mochy.* staged equivalents. See NEEDS.md.
// LibraryRecentlyAddedScreen.kt
//
// Songs only - every library song, or with a shelf the Local or Cached tab's songs. The underlying
// query orders by when each song entered the library (a local one: when it was downloaded or
// imported; a cached one: when it was last played), which is the one timeline shared across the library; albums/artists/playlists carry no
// comparable timestamp, so a combined "recently added" feed would need new queries rather than a
// merge of what exists.

package com.music.bitchord.ui.mochy.screens.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.ui.mochy.navigation.MochyNavigator
import com.music.bitchord.ui.mochy.playback.LocalPlayerConnection
import com.music.bitchord.ui.mochy.extensions.toMediaItem
import com.music.bitchord.ui.mochy.playback.queues.ListQueue
import com.music.bitchord.ui.mochy.viewmodels.LibraryHomeViewModel
import com.music.bitchord.ui.mochy.viewmodels.LibraryShelfKind

@Composable
fun LibraryRecentlyAddedScreen(
    navigator: MochyNavigator,
    modifier: Modifier = Modifier,
    pureBlack: Boolean = false,
    shelf: LibraryShelfKind? = null,
) {
    val viewModel: LibraryHomeViewModel = hiltViewModel()
    // with the lifecycle: the Cached shelf looks at the cache every couple of seconds while watched
    val songs by (shelf?.let(viewModel::shelf)?.songs ?: viewModel.songs).collectAsStateWithLifecycle()
    val queueTitle = shelf?.label ?: "Recently added"
    val playerConnection = LocalPlayerConnection.current ?: return

    val entries = remember(songs) {
        songs.map {
            LibraryGridEntry(
                id = it.id,
                title = it.title,
                subtitle = it.artists.joinToString { a -> a.name }.ifBlank { null },
                thumbnailUrl = it.song.thumbnailUrl,
            )
        }
    }

    fun play(startIndex: Int, shuffle: Boolean) {
        if (songs.isEmpty()) return
        val ordered = if (shuffle) songs.shuffled() else songs
        playerConnection.playQueue(
            ListQueue(
                title = queueTitle,
                items = ordered.map { it.toMediaItem() },
                startIndex = if (shuffle) 0 else startIndex,
            ),
        )
    }

    LibraryGridScreen(
        title = "Added",
        subtitle = "Recently",
        searchPlaceholder = "Find song listed here, by the lyrics, or the artist",
        entries = entries,
        onOpen = { entry -> play(songs.indexOfFirst { it.id == entry.id }.coerceAtLeast(0), shuffle = false) },
        onPlay = { play(0, shuffle = false) },
        onShuffle = { play(0, shuffle = true) },
        modifier = modifier,
        pureBlack = pureBlack,
    )
}
