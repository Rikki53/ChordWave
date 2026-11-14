// Ported from Mochy Music (GPL-3.0): ui/screens/playlist/CachePlaylistScreen.kt
// Adaptations: navController -> MochyNavigator (route strings verbatim,
// navigateUp -> popBackStack, backToMain -> clearDetails); hiltViewModel from
// androidx.hilt.navigation.compose; creator "mochy" -> "ChordWave";
// toggleSongsDownload takes the screen's coroutineScope (Downloads.delete is
// suspend — see GeneratedPlaylistKit).
package com.music.bitchord.ui.mochy.screens.playlist

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEachReversed
import androidx.hilt.navigation.compose.hiltViewModel
import com.music.bitchord.R
import com.music.bitchord.ui.mochy.LocalPlayerAwareWindowInsets
import com.music.bitchord.ui.mochy.component.EmptyPlaceholder
import com.music.bitchord.ui.mochy.component.LocalMenuState
import com.music.bitchord.ui.mochy.component.SongSwipeBox
import com.music.bitchord.ui.mochy.component.detail.FeaturedArtistsRow
import com.music.bitchord.ui.mochy.component.detail.PlaylistEndOfLine
import com.music.bitchord.ui.mochy.component.detail.PlaylistModeBar
import com.music.bitchord.ui.mochy.component.detail.PlaylistSelectTick
import com.music.bitchord.ui.mochy.component.detail.PlaylistSortControl
import com.music.bitchord.ui.mochy.component.detail.PlaylistTrackRow
import com.music.bitchord.ui.mochy.component.detail.featuredArtistsOf
import com.music.bitchord.ui.mochy.component.librarySwipeAction
import com.music.bitchord.ui.mochy.component.queueSwipeAction
import com.music.bitchord.ui.mochy.component.rememberRevealSeenState
import com.music.bitchord.ui.mochy.component.revealOnAppear
import com.music.bitchord.ui.mochy.constants.HideExplicitKey
import com.music.bitchord.ui.mochy.constants.SongSortDescendingKey
import com.music.bitchord.ui.mochy.constants.SongSortType
import com.music.bitchord.ui.mochy.constants.SongSortTypeKey
import com.music.bitchord.ui.mochy.extensions.toMediaItem
import com.music.bitchord.ui.mochy.navigation.MochyNavigator
import com.music.bitchord.ui.mochy.playback.LocalPlayerConnection
import com.music.bitchord.ui.mochy.playback.queues.ListQueue
import com.music.bitchord.ui.mochy.player.menu.CachePlaylistMenu
import com.music.bitchord.ui.mochy.player.menu.SelectionSongMenu
import com.music.bitchord.ui.mochy.player.menu.SongMenu
import com.music.bitchord.ui.mochy.utils.makeTimeString
import com.music.bitchord.ui.mochy.utils.rememberEnumPreference
import com.music.bitchord.ui.mochy.utils.rememberPreference
import com.music.bitchord.ui.mochy.viewmodels.CachePlaylistViewModel
import java.time.LocalDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CachePlaylistScreen(
    navigator: MochyNavigator,
    @Suppress("UNUSED_PARAMETER") scrollBehavior: TopAppBarScrollBehavior,
    viewModel: CachePlaylistViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val menuState = LocalMenuState.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val queueTitle by playerConnection.queueTitle.collectAsState()
    val cachedSongs by viewModel.cachedSongs.collectAsState()

    val (sortType, onSortTypeChange) = rememberEnumPreference(SongSortTypeKey, SongSortType.CREATE_DATE)
    val (sortDescending, onSortDescendingChange) = rememberPreference(SongSortDescendingKey, true)
    val hideExplicit by rememberPreference(key = HideExplicitKey, defaultValue = false)
    val title = stringResource(R.string.cached_playlist)

    val sortedSongs = remember(cachedSongs, sortType, sortDescending, hideExplicit) {
        val visible = if (hideExplicit) cachedSongs.filter { !it.song.explicit } else cachedSongs
        val sorted = when (sortType) {
            SongSortType.CREATE_DATE -> visible.sortedBy { it.song.dateDownload ?: LocalDateTime.MIN }
            SongSortType.NAME -> visible.sortedBy { it.song.title }
            SongSortType.ARTIST -> visible.sortedBy { song -> song.artists.joinToString(separator = "") { it.name } }
            SongSortType.PLAY_TIME -> visible.sortedBy { it.song.totalPlayTime }
        }
        if (sortDescending) sorted.reversed() else sorted
    }

    var inSelectMode by rememberSaveable { mutableStateOf(false) }
    val selection = rememberSaveable(
        saver = listSaver<MutableList<String>, String>(
            save = { it.toList() },
            restore = { it.toMutableStateList() }
        )
    ) { mutableStateListOf() }
    val onExitSelectionMode = {
        inSelectMode = false
        selection.clear()
    }
    if (inSelectMode) BackHandler(onBack = onExitSelectionMode)

    var query by remember { mutableStateOf(TextFieldValue()) }
    val lazyListState = rememberLazyListState()
    val revealSeen = rememberRevealSeenState()

    val filteredSongs = remember(sortedSongs, query.text) {
        val q = query.text.trim()
        if (q.isEmpty()) sortedSongs else sortedSongs.filter { song ->
            song.title.contains(q, true) || song.artists.any { it.name.contains(q, true) }
        }
    }
    LaunchedEffect(filteredSongs) {
        selection.fastForEachReversed { id -> if (filteredSongs.none { it.id == id }) selection.remove(id) }
    }

    val totalSeconds = remember(sortedSongs) { sortedSongs.sumOf { it.song.duration } }
    val featuredArtists = remember(sortedSongs) {
        featuredArtistsOf(sortedSongs.flatMap { it.artists }, id = { it.id }, name = { it.name })
    }
    val downloadState = rememberSongsDownloadState(remember(sortedSongs) { sortedSongs.map { it.song.id } })
    val isThisQueue = queueTitle == title

    fun play(startIndex: Int = 0, shuffled: Boolean = false) {
        if (sortedSongs.isEmpty()) return
        val list = if (shuffled) sortedSongs.shuffled() else sortedSongs
        playerConnection.playQueue(ListQueue(title = title, items = list.map { it.toMediaItem() }, startIndex = startIndex))
    }

    GeneratedPlaylistPage(
        title = title,
        coverUrl = sortedSongs.firstOrNull { !it.song.thumbnailUrl.isNullOrEmpty() }?.song?.thumbnailUrl,
        stats = generatedStats(sortedSongs.size, totalSeconds),
        lazyListState = lazyListState,
        contentPadding = LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Bottom)
            .union(WindowInsets.ime).asPaddingValues(),
        isPlaying = isThisQueue && isPlaying,
        onPlayClick = { if (isThisQueue) playerConnection.togglePlayPause() else play() },
        onShuffleClick = { play(shuffled = true) },
        query = query,
        onQueryChange = { query = it },
        searchPlaceholder = "Search cached songs",
        onBackClick = { navigator.popBackStack() },
        onBackLongClick = { navigator.clearDetails() },
        creator = "ChordWave",
        onMoreClick = {
            menuState.show {
                CachePlaylistMenu(
                    downloadState = downloadState,
                    onQueue = { playerConnection.addToQueue(sortedSongs.map { it.toMediaItem() }) },
                    onDownload = { toggleSongsDownload(coroutineScope, context, sortedSongs, downloadState) },
                    onDismiss = menuState::dismiss,
                )
            }
        },
        sortControl = {
            PlaylistSortControl(
                current = sortType,
                descending = sortDescending,
                options = SongSortType.entries,
                label = { type ->
                    stringResource(
                        when (type) {
                            SongSortType.CREATE_DATE -> R.string.sort_by_create_date
                            SongSortType.NAME -> R.string.sort_by_name
                            SongSortType.ARTIST -> R.string.sort_by_artist
                            SongSortType.PLAY_TIME -> R.string.sort_by_play_time
                        }
                    )
                },
                onSelect = onSortTypeChange,
                onToggleDirection = { onSortDescendingChange(!sortDescending) },
            )
        },
        modeBar = if (inSelectMode) {
            PlaylistModeBar.Select(
                count = selection.size,
                allSelected = selection.size == filteredSongs.size && selection.isNotEmpty(),
                onToggleAll = {
                    val selectAll = selection.size != filteredSongs.size
                    selection.clear()
                    if (selectAll) selection.addAll(filteredSongs.map { it.id })
                },
                onMore = {
                    menuState.show {
                        SelectionSongMenu(
                            songSelection = filteredSongs.filter { it.id in selection },
                            onDismiss = menuState::dismiss,
                            clearAction = onExitSelectionMode,
                        )
                    }
                },
                onClose = onExitSelectionMode,
            )
        } else null,
    ) {
        if (sortedSongs.isEmpty()) {
            item(key = "empty_placeholder") {
                EmptyPlaceholder(icon = R.drawable.music_note, text = stringResource(R.string.playlist_is_empty))
            }
        } else if (filteredSongs.isEmpty()) {
            item(key = "no_results") { GeneratedNoResults(query.text) }
        }

        itemsIndexed(filteredSongs, key = { _, song -> song.id }, contentType = { _, _ -> "track" }) { position, song ->
            val onCheckedChange: (Boolean) -> Unit = {
                if (it) selection.add(song.id) else selection.remove(song.id)
            }
            SongSwipeBox(
                modifier = Modifier.animateItem(),
                enabled = !inSelectMode,
                library = { librarySwipeAction(song.song) },
                queue = { queueSwipeAction { song.toMediaItem() } },
            ) {
                val active = song.id == mediaMetadata?.id
                PlaylistTrackRow(
                    thumbnailUrl = song.song.thumbnailUrl,
                    songId = song.id,
                    title = song.song.title,
                    subtitle = "${song.artists.joinToString { it.name }} • ${makeTimeString(song.song.duration * 1000L)}",
                    isActive = active,
                    isPlaying = active && isPlaying,
                    modifier = Modifier.revealOnAppear(
                        key = "track_${song.id}",
                        seenState = revealSeen,
                        delayMillis = minOf(position, 8) * 28,
                    ),
                    onClick = {
                        if (inSelectMode) {
                            onCheckedChange(song.id !in selection)
                        } else if (song.id == mediaMetadata?.id) {
                            playerConnection.togglePlayPause()
                        } else {
                            play(startIndex = sortedSongs.indexOfFirst { it.id == song.id }.coerceAtLeast(0))
                        }
                    },
                    onLongClick = {
                        if (!inSelectMode) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            inSelectMode = true
                            onCheckedChange(true)
                        }
                    },
                    onMenuClick = {
                        menuState.show {
                            SongMenu(
                                originalSong = song,
                                navigator = navigator,
                                onDismiss = menuState::dismiss,
                                isFromCache = true,
                            )
                        }
                    },
                    selected = inSelectMode && song.id in selection,
                    trailing = if (inSelectMode) {
                        { PlaylistSelectTick(checked = song.id in selection) }
                    } else null,
                )
            }
        }

        if (query.text.isBlank() && sortedSongs.isNotEmpty()) {
            item(key = "end_of_line") {
                PlaylistEndOfLine(songCount = sortedSongs.size, totalSeconds = totalSeconds)
            }
            if (featuredArtists.isNotEmpty()) {
                item(key = "featured_artists") {
                    FeaturedArtistsRow(
                        artists = featuredArtists,
                        onArtistClick = { navigator.navigate("artist/${it.id}") },
                        modifier = Modifier.padding(top = 25.dp),
                    )
                }
            }
        }

        item(key = "bottom_spacer") {
            Spacer(Modifier.height(50.dp))
        }
    }
}
