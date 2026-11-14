// Ported from Mochy Music (GPL-3.0): ui/screens/library/LibraryImportedScreen.kt
// ADAPTATIONS: navigation param -> MochyNavigator (route strings verbatim);
// Mochy packages -> com.music.bitchord.ui.mochy.* staged equivalents. See NEEDS.md.
// LibraryImportedScreen.kt
//
// "Imported from Spotify": every song one service brought in, in the same A-Z list as Songs, with the
// service's own icon where the Library home has the avatar.

package com.music.bitchord.ui.mochy.screens.library

import com.music.bitchord.ui.mochy.importer.ImportSource
import com.music.bitchord.ui.mochy.screens.search.AvatarSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.music.bitchord.ui.mochy.component.removeImportSwipeAction
import com.music.bitchord.ui.mochy.player.menu.SongMenu
import com.music.bitchord.ui.mochy.viewmodels.LibraryImportedViewModel

@Composable
fun LibraryImportedScreen(
    navigator: MochyNavigator,
    modifier: Modifier = Modifier,
    pureBlack: Boolean = false,
    importSource: ImportSource? = null,
) {
    val viewModel: LibraryImportedViewModel = hiltViewModel()
    // Coordinator: route args rule — the host passes the {source} route arg
    // (ImportSource.fromKey); the VM is activity-scoped so it loads per source.
    // Named importSource: the body already reads viewModel.source below.
    LaunchedEffect(importSource) { importSource?.let { viewModel.load(it) } }
    val songs by viewModel.songs.collectAsState()
    val playerConnection = LocalPlayerConnection.current ?: return
    val menuState = LocalMenuState.current
    val source = viewModel.source

    val label = source?.label ?: "an unknown source"
    val sourceIcon: (@Composable () -> Unit)? = if (source != null) {
        { ImportSourceIcon(source, size = AvatarSize) }
    } else {
        null
    }

    LibraryEntityListScreen(
        title = source?.label ?: "Imported",
        subtitle = when (songs.size) {
            1 -> "1 song imported from $label."
            else -> "${songs.size} songs imported from $label."
        },
        searchPlaceholder = "Find a song imported from $label",
        items = songs,
        idOf = { it.id },
        nameOf = { it.song.title },
        subtitleOf = { it.artists.joinToString { a -> a.name }.ifBlank { null } },
        thumbnailOf = { it.song.thumbnailUrl },
        playingIdOf = { it.id },
        onClick = { song ->
            // The order on screen, so the queue starts where the user tapped and runs down the list.
            val ordered = sortForLibraryIndex(songs) { it.song.title }
            playerConnection.playQueue(
                ListQueue(
                    title = "Imported from $label",
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
        rowWrapper = { song, row ->
            SongSwipeBox(
                slab = false,
                library = { if (source != null) removeImportSwipeAction(song.song, source) else librarySwipeAction(song.song) },
                queue = { queueSwipeAction { song.toMediaItem() } },
            ) {
                row()
            }
        },
        trailing = sourceIcon,
    )
}
