// Ported from Mochy Music (GPL-3.0): ui/screens/artist/ArtistSongsScreen.kt
// ArtistSongsScreen.kt — The artist's songs in your library, as a playlist on their banner.
//
// Port notes (see staging/phase2-detail/NEEDS.md for the full list):
// - Navigation goes through MochyNavigator; route strings are Mochy's verbatim.
//   Back is popBackStack(); back-to-main is clearDetails().
// - The screen never touches the database: data comes through the VM contract.
// - ArtistSongSortType + its keys are NEEDS.md C11; generatedStats is
//   already staged (phase2-playlist GeneratedPlaylistKit).

package com.music.bitchord.ui.mochy.screens.artist

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.music.bitchord.ui.mochy.LocalPlayerAwareWindowInsets
import com.music.bitchord.ui.mochy.navigation.MochyNavigator
import com.music.bitchord.ui.mochy.playback.LocalPlayerConnection
import com.music.bitchord.R
import com.music.bitchord.ui.mochy.constants.ArtistSongSortDescendingKey
import com.music.bitchord.ui.mochy.constants.ArtistSongSortType
import com.music.bitchord.ui.mochy.constants.ArtistSongSortTypeKey
import com.music.bitchord.ui.mochy.extensions.toMediaItem
import com.music.bitchord.ui.mochy.playback.queues.ListQueue
import com.music.bitchord.ui.mochy.component.LocalMenuState
import com.music.bitchord.ui.mochy.component.SongSwipeBox
import com.music.bitchord.ui.mochy.component.detail.CreatorUi
import com.music.bitchord.ui.mochy.component.detail.PlaylistDetailScaffold
import com.music.bitchord.ui.mochy.component.detail.PlaylistEndOfLine
import com.music.bitchord.ui.mochy.component.detail.PlaylistHeader
import com.music.bitchord.ui.mochy.component.detail.PlaylistSortControl
import com.music.bitchord.ui.mochy.component.detail.PlaylistTrackRow
import com.music.bitchord.ui.mochy.component.librarySwipeAction
import com.music.bitchord.ui.mochy.component.queueSwipeAction
import com.music.bitchord.ui.mochy.component.rememberRevealSeenState
import com.music.bitchord.ui.mochy.component.revealOnAppear
import com.music.bitchord.ui.mochy.player.menu.SongMenu
import com.music.bitchord.ui.mochy.screens.playlist.generatedStats
import com.music.bitchord.ui.mochy.utils.makeTimeString
import com.music.bitchord.ui.mochy.utils.rememberEnumPreference
import com.music.bitchord.ui.mochy.utils.rememberPreference
import com.music.bitchord.ui.mochy.viewmodels.ArtistSongsViewModel

/** the artist's songs in your library, as a playlist on the artist's banner */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistSongsScreen(
    navigator: MochyNavigator,
    @Suppress("UNUSED_PARAMETER") scrollBehavior: TopAppBarScrollBehavior,
    artistId: String,
    viewModel: ArtistSongsViewModel = hiltViewModel(),
) {
    // Coordinator: route args rule — the host passes artistId; the VM loads on it.
    LaunchedEffect(artistId) { viewModel.load(artistId) }
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val queueTitle by playerConnection.queueTitle.collectAsState()

    val (sortType, onSortTypeChange) = rememberEnumPreference(ArtistSongSortTypeKey, ArtistSongSortType.CREATE_DATE)
    val (sortDescending, onSortDescendingChange) = rememberPreference(ArtistSongSortDescendingKey, true)

    val artist by viewModel.artist.collectAsState()
    val songs by viewModel.songs.collectAsState()
    val lazyListState = rememberLazyListState()
    val revealSeen = rememberRevealSeenState()

    val artistId = artist?.artist?.id
    val (cachedName, bannerUrl) = rememberArtistBanner(artistId)
    val name = artist?.artist?.name ?: cachedName.orEmpty()
    val title = if (name.isNotBlank()) "$name in your library" else stringResource(R.string.songs)
    val totalSeconds = remember(songs) { songs.sumOf { it.song.duration } }
    val isThisQueue = queueTitle == title

    fun play(startIndex: Int = 0, shuffled: Boolean = false) {
        if (songs.isEmpty()) return
        val list = if (shuffled) songs.shuffled() else songs
        playerConnection.playQueue(ListQueue(title = title, items = list.map { it.toMediaItem() }, startIndex = startIndex))
    }

    PlaylistDetailScaffold(
        sharedElementKey = null,
        coverUrl = bannerUrl ?: songs.firstOrNull()?.song?.thumbnailUrl,
        lazyListState = lazyListState,
        onBackClick = { navigator.popBackStack() },
        onBackLongClick = { navigator.clearDetails() },
        contentPadding = LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Bottom).asPaddingValues(),
    ) {
        item(key = "header", contentType = "header") {
            PlaylistHeader(
                title = title,
                creators = listOfNotNull(
                    name.takeIf { it.isNotBlank() }?.let { CreatorUi(name = it, avatarUrl = artist?.artist?.thumbnailUrl, artistId = artistId) }
                ),
                stats = generatedStats(songs.size, totalSeconds),
                isPlaying = isThisQueue && isPlaying,
                onPlayClick = { if (isThisQueue) playerConnection.togglePlayPause() else play() },
                onShuffleClick = { play(shuffled = true) },
                onCreatorClick = { creator -> creator.artistId?.let { navigator.navigate("artist/$it") } },
                sortControl = {
                    PlaylistSortControl(
                        current = sortType,
                        descending = sortDescending,
                        options = ArtistSongSortType.entries,
                        label = { type ->
                            stringResource(
                                when (type) {
                                    ArtistSongSortType.CREATE_DATE -> R.string.sort_by_create_date
                                    ArtistSongSortType.NAME -> R.string.sort_by_name
                                    ArtistSongSortType.PLAY_TIME -> R.string.sort_by_play_time
                                }
                            )
                        },
                        onSelect = onSortTypeChange,
                        onToggleDirection = { onSortDescendingChange(!sortDescending) },
                    )
                },
            )
        }

        itemsIndexed(songs, key = { _, song -> song.id }, contentType = { _, _ -> "track" }) { index, song ->
            SongSwipeBox(
                modifier = Modifier.animateItem(),
                library = { librarySwipeAction(song.song) },
                queue = { queueSwipeAction { song.toMediaItem() } },
            ) {
                val active = song.id == mediaMetadata?.id
                PlaylistTrackRow(
                    thumbnailUrl = song.song.thumbnailUrl,
                    songId = song.id,
                    title = song.song.title,
                    subtitle = listOfNotNull(song.album?.title, makeTimeString(song.song.duration * 1000L)).joinToString(" • "),
                    isActive = active,
                    isPlaying = active && isPlaying,
                    modifier = Modifier.revealOnAppear(key = "song_${song.id}", seenState = revealSeen, delayMillis = minOf(index, 8) * 28),
                    onClick = {
                        if (song.id == mediaMetadata?.id) playerConnection.togglePlayPause() else play(startIndex = index)
                    },
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        menuState.show {
                            SongMenu(originalSong = song, navigator = navigator, onDismiss = menuState::dismiss)
                        }
                    },
                    onMenuClick = {
                        menuState.show {
                            SongMenu(originalSong = song, navigator = navigator, onDismiss = menuState::dismiss)
                        }
                    },
                )
            }
        }

        if (songs.isNotEmpty()) {
            item(key = "end_of_line") {
                PlaylistEndOfLine(songCount = songs.size, totalSeconds = totalSeconds)
            }
        }

        item(key = "bottom_spacer") {
            Spacer(Modifier.height(50.dp))
        }
    }
}
