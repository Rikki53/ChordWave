// Ported from Mochy Music (GPL-3.0): ui/screens/library/LibrarySongsScreen.kt
// ADAPTATIONS: navigation param -> MochyNavigator (route strings verbatim);
// Mochy packages -> com.music.bitchord.ui.mochy.* staged equivalents. See NEEDS.md.
// LibrarySongsScreen.kt

package com.music.bitchord.ui.mochy.screens.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.music.bitchord.R
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.ui.mochy.navigation.MochyNavigator
import com.music.bitchord.ui.mochy.playback.LocalPlayerConnection
import com.music.bitchord.ui.mochy.extensions.toMediaItem
import com.music.bitchord.ui.mochy.playback.queues.ListQueue
import com.music.bitchord.ui.mochy.component.LocalMenuState
import com.music.bitchord.ui.mochy.component.SongSwipeBox
import com.music.bitchord.ui.mochy.component.librarySwipeAction
import com.music.bitchord.ui.mochy.component.queueSwipeAction
import com.music.bitchord.ui.mochy.player.menu.SongMenu
import com.music.bitchord.ui.mochy.utils.listeningTime
import com.music.bitchord.ui.mochy.utils.makeTimeString
import com.music.bitchord.ui.mochy.viewmodels.LibraryHomeViewModel
import com.music.bitchord.ui.mochy.viewmodels.LibraryShelfKind

/**
 * Songs only — every library song, or with a [shelf] the Local or Cached tab's
 * songs (batch 11: the screen ignored the shelf, so "library/local/songs"
 * would have listed the account library instead of the tab's songs).
 */
@Composable
fun LibrarySongsScreen(
    navigator: MochyNavigator,
    modifier: Modifier = Modifier,
    pureBlack: Boolean = false,
    shelf: LibraryShelfKind? = null,
) {
    val viewModel: LibraryHomeViewModel = hiltViewModel()
    // with the lifecycle: the Cached shelf looks at the cache every couple of seconds while watched
    val songs by (shelf?.let(viewModel::shelf)?.songs ?: viewModel.songs).collectAsStateWithLifecycle()
    // the account load state only drives the library page — a shelf's
    // songs come from its own source, not from the account fetch.
    val isLoading by viewModel.isLoading.collectAsState()
    val loadFailed by viewModel.loadFailed.collectAsState()
    val playerConnection = LocalPlayerConnection.current ?: return
    val menuState = LocalMenuState.current
    // The shelf's page wears the shelf's name, like its queue does; the
    // library page keeps its own title.
    val title = shelf?.label ?: stringResource(R.string.lib_all_songs_title)
    val totalSeconds = remember(songs) { songs.sumOf { it.song.duration.coerceAtLeast(0) } }

    LibraryEntityListScreen(
        title = title,
        subtitle = stringResource(
            R.string.lib_stats_in_library_time,
            pluralStringResource(R.plurals.n_song, songs.size, songs.size),
            listeningTime(totalSeconds),
        ),
        searchPlaceholder = stringResource(R.string.lib_songs_search),
        items = songs,
        idOf = { it.id },
        nameOf = { it.song.title },
        subtitleOf = { song ->
            listOfNotNull(
                song.artists.joinToString { a -> a.name }.ifBlank { null },
                song.song.duration.takeIf { it > 0 }?.let { makeTimeString(it * 1000L) },
            ).joinToString(" • ").takeIf { it.isNotBlank() }
        },
        thumbnailOf = { it.song.thumbnailUrl },
        playingIdOf = { it.id },
        onClick = { song ->
            // Queue the alphabetical order the user is looking at, not the underlying
            // create-date order — otherwise tapping a row starts a queue that jumps somewhere
            // unrelated to what's on screen.
            val ordered = sortForLibraryIndex(songs) { it.song.title }
            playerConnection.playQueue(
                ListQueue(
                    title = title,
                    items = ordered.map { it.toMediaItem() },
                    startIndex = ordered.indexOf(song).coerceAtLeast(0),
                ),
            )
        },
        onLongClick = { song ->
            menuState.show {
                SongMenu(
                    originalSong = song,
                    navigator = navigator,
                    onDismiss = menuState::dismiss,
                )
            }
        },
        modifier = modifier,
        pureBlack = pureBlack,
        isLoading = shelf == null && isLoading,
        loadFailed = shelf == null && loadFailed,
        onRetry = if (shelf == null) viewModel::load else null,
        // left queues the song, right takes it out of the library (it's already in it)
        rowWrapper = { song, row ->
            SongSwipeBox(
                slab = false,
                library = { librarySwipeAction(song.song) },
                queue = { queueSwipeAction { song.toMediaItem() } },
            ) {
                row()
            }
        },
    )
}
