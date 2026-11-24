// Ported from Mochy Music (GPL-3.0): ui/screens/library/LibraryGenreScreen.kt
// ADAPTATIONS: navigation param -> MochyNavigator; the genre comes from the
// route (LocalGenresViewModel has no SavedStateHandle arg — CW screens take
// route args as parameters). Stats use generatedStats, like HistoryScreen.
// LibraryGenreScreen.kt
//
// One genre from the Library's Genres tab, as a playlist: every local file tagged with it,
// a to z, with Play, shuffle and search.

package com.music.bitchord.ui.mochy.screens.library

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.music.bitchord.R
import com.music.bitchord.ui.mochy.LocalPlayerAwareWindowInsets
import com.music.bitchord.ui.mochy.component.LocalMenuState
import com.music.bitchord.ui.mochy.component.SongSwipeBox
import com.music.bitchord.ui.mochy.component.detail.PlaylistEndOfLine
import com.music.bitchord.ui.mochy.component.detail.PlaylistTrackBones
import com.music.bitchord.ui.mochy.component.detail.PlaylistTrackRow
import com.music.bitchord.ui.mochy.component.librarySwipeAction
import com.music.bitchord.ui.mochy.component.queueSwipeAction
import com.music.bitchord.ui.mochy.extensions.toMediaItem
import com.music.bitchord.ui.mochy.models.Song
import com.music.bitchord.ui.mochy.navigation.MochyNavigator
import com.music.bitchord.ui.mochy.playback.LocalPlayerConnection
import com.music.bitchord.ui.mochy.playback.queues.ListQueue
import com.music.bitchord.ui.mochy.player.menu.SongMenu
import com.music.bitchord.ui.mochy.screens.playlist.GeneratedNoResults
import com.music.bitchord.ui.mochy.screens.playlist.GeneratedPlaylistPage
import com.music.bitchord.ui.mochy.screens.playlist.generatedStats
import com.music.bitchord.ui.mochy.utils.makeTimeString
import com.music.bitchord.ui.mochy.viewmodels.LocalGenresViewModel

@Composable
fun LibraryGenreScreen(navigator: MochyNavigator, genreName: String) {
    val viewModel: LocalGenresViewModel = hiltViewModel()
    val genres by viewModel.genres.collectAsState()
    val genre = genres?.firstOrNull { it.name.equals(genreName, ignoreCase = true) }
    val songs = genre?.songs.orEmpty()
    val playerConnection = LocalPlayerConnection.current ?: return
    val menuState = LocalMenuState.current
    // states, read in the rows only, so a play or pause doesn't recompose the page
    val nowPlaying = playerConnection.mediaMetadata.collectAsState()
    val isPlaying = playerConnection.isEffectivelyPlaying.collectAsState()
    val currentQueue by playerConnection.queueTitle.collectAsState()

    val title = genre?.name ?: genreName
    var query by remember { mutableStateOf(TextFieldValue()) }
    val listState = rememberLazyListState()
    val totalSeconds = remember(songs) { songs.sumOf { it.song.duration.coerceAtLeast(0) } }
    val shown = remember(songs, query.text) {
        val q = query.text.trim()
        if (q.isEmpty()) songs else songs.filter { song -> song.song.title.contains(q, true) || song.artists.any { it.name.contains(q, true) } }
    }
    val isThisQueue = currentQueue == title

    fun play(start: Song?, shuffle: Boolean) {
        if (songs.isEmpty()) return
        val ordered = if (shuffle) songs.shuffled() else songs
        playerConnection.playQueue(
            ListQueue(
                title = title,
                items = ordered.map { it.toMediaItem() },
                startIndex = start?.let { ordered.indexOf(it).coerceAtLeast(0) } ?: 0,
            ),
        )
    }

    GeneratedPlaylistPage(
        title = title,
        coverUrl = songs.firstOrNull { !it.song.thumbnailUrl.isNullOrEmpty() }?.song?.thumbnailUrl,
        stats = generatedStats(songs.size, totalSeconds),
        lazyListState = listState,
        contentPadding = LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Bottom)
            .union(WindowInsets.ime).asPaddingValues(),
        isPlaying = isThisQueue && isPlaying.value,
        onPlayClick = { if (isThisQueue) playerConnection.togglePlayPause() else play(null, shuffle = false) },
        onShuffleClick = { play(null, shuffle = true) },
        query = query,
        onQueryChange = { query = it },
        searchPlaceholder = stringResource(R.string.lib_genre_search),
        onBackClick = { navigator.popBackStack() },
        onBackLongClick = { navigator.clearDetails() },
    ) {
        if (genres == null) {
            item(key = "loading", contentType = "loading") { PlaylistTrackBones() }
            return@GeneratedPlaylistPage
        }
        if (shown.isEmpty()) {
            item(key = "empty", contentType = "empty") {
                if (query.text.isNotBlank()) GeneratedNoResults(query.text) else LibraryEmptyState()
            }
        }
        items(shown, key = { it.id }, contentType = { "track" }) { song ->
            SongSwipeBox(
                library = { librarySwipeAction(song.song) },
                queue = { queueSwipeAction { song.toMediaItem() } },
            ) {
                val active = song.id == nowPlaying.value?.id
                PlaylistTrackRow(
                    thumbnailUrl = song.song.thumbnailUrl,
                    songId = song.id,
                    title = song.song.title,
                    subtitle = listOfNotNull(
                        song.artists.joinToString { it.name }.ifBlank { null },
                        song.song.duration.takeIf { it > 0 }?.let { makeTimeString(it * 1000L) },
                    ).joinToString(" • "),
                    isActive = active,
                    isPlaying = active && isPlaying.value,
                    onClick = { if (active) playerConnection.togglePlayPause() else play(song, shuffle = false) },
                    onLongClick = {
                        menuState.show { SongMenu(originalSong = song, navigator = navigator, onDismiss = menuState::dismiss) }
                    },
                    onMenuClick = {
                        menuState.show { SongMenu(originalSong = song, navigator = navigator, onDismiss = menuState::dismiss) }
                    },
                )
            }
        }
        if (query.text.isBlank() && songs.isNotEmpty()) {
            item(key = "end_of_line", contentType = "end") { PlaylistEndOfLine(songCount = songs.size, totalSeconds = totalSeconds) }
        }
        item(key = "bottom_spacer", contentType = "spacer") { Spacer(Modifier.height(50.dp)) }
    }
}
