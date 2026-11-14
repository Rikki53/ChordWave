// Ported from Mochy Music (GPL-3.0): ui/screens/library/LibrarySongsScreen.kt
// ADAPTATIONS: navigation param -> MochyNavigator (route strings verbatim);
// Mochy packages -> com.music.bitchord.ui.mochy.* staged equivalents. See NEEDS.md.
// LibrarySongsScreen.kt

package com.music.bitchord.ui.mochy.screens.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.music.bitchord.ui.mochy.navigation.MochyNavigator
import com.music.bitchord.ui.mochy.playback.LocalPlayerConnection
import com.music.bitchord.ui.mochy.extensions.toMediaItem
import com.music.bitchord.ui.mochy.playback.queues.ListQueue
import com.music.bitchord.ui.mochy.component.LocalMenuState
import com.music.bitchord.ui.mochy.component.SongSwipeBox
import com.music.bitchord.ui.mochy.component.librarySwipeAction
import com.music.bitchord.ui.mochy.component.queueSwipeAction
import com.music.bitchord.ui.mochy.player.menu.SongMenu
import com.music.bitchord.ui.mochy.viewmodels.LibraryHomeViewModel

@Composable
fun LibrarySongsScreen(
    navigator: MochyNavigator,
    modifier: Modifier = Modifier,
    pureBlack: Boolean = false,
) {
    val viewModel: LibraryHomeViewModel = hiltViewModel()
    val songs by viewModel.songs.collectAsState()
    val playerConnection = LocalPlayerConnection.current ?: return
    val menuState = LocalMenuState.current

    LibraryEntityListScreen(
        title = "Songs",
        subtitle = "All songs on your library listed here.",
        searchPlaceholder = "Find a song by its title or artist",
        items = songs,
        idOf = { it.id },
        nameOf = { it.song.title },
        subtitleOf = { it.artists.joinToString { a -> a.name }.ifBlank { null } },
        thumbnailOf = { it.song.thumbnailUrl },
        playingIdOf = { it.id },
        onClick = { song ->
            // Queue the alphabetical order the user is looking at, not the underlying
            // create-date order — otherwise tapping a row starts a queue that jumps somewhere
            // unrelated to what's on screen.
            val ordered = sortForLibraryIndex(songs) { it.song.title }
            playerConnection.playQueue(
                ListQueue(
                    title = "Songs",
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
