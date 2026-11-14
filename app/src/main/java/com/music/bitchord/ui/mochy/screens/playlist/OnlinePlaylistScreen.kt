// Ported from Mochy Music (GPL-3.0): ui/screens/playlist/OnlinePlaylistScreen.kt
// Adaptations: navController -> MochyNavigator (route strings verbatim,
// navigateUp -> popBackStack, backToMain -> clearDetails); hiltViewModel from
// androidx.hilt.navigation.compose; the database is never touched — saving the playlist
// goes through viewModel.toggleSaved() (NEEDS.md C1); the download-state block
// is dropped — Mochy computed downloadState here but never read it on this
// screen; menus take navigator per the staged signatures. Unused-in-Mochy
// imports dropped: width, Icon, MaterialTheme, Text, Color, painterResource.
package com.music.bitchord.ui.mochy.screens.playlist

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastForEachReversed
import androidx.hilt.navigation.compose.hiltViewModel
import com.music.bitchord.R
import com.music.bitchord.ui.mochy.LocalPlayerAwareWindowInsets
import com.music.bitchord.ui.mochy.component.HomeCardHighlight
import com.music.bitchord.ui.mochy.component.HomeCardSpacing
import com.music.bitchord.ui.mochy.component.HomeCoverCard
import com.music.bitchord.ui.mochy.component.LocalMenuState
import com.music.bitchord.ui.mochy.component.SongSwipeBox
import com.music.bitchord.ui.mochy.component.coverTransitionKey
import com.music.bitchord.ui.mochy.component.detail.CreatorUi
import com.music.bitchord.ui.mochy.component.detail.FeaturedArtistsRow
import com.music.bitchord.ui.mochy.component.detail.PlaylistDetailScaffold
import com.music.bitchord.ui.mochy.component.detail.PlaylistEndOfLine
import com.music.bitchord.ui.mochy.component.detail.PlaylistHeader
import com.music.bitchord.ui.mochy.component.detail.PlaylistInset
import com.music.bitchord.ui.mochy.component.detail.PlaylistModeBar
import com.music.bitchord.ui.mochy.component.detail.PlaylistSearchBar
import com.music.bitchord.ui.mochy.component.detail.PlaylistSectionTitle
import com.music.bitchord.ui.mochy.component.detail.PlaylistSelectTick
import com.music.bitchord.ui.mochy.component.detail.PlaylistTrackBones
import com.music.bitchord.ui.mochy.component.detail.PlaylistTrackRow
import com.music.bitchord.ui.mochy.component.detail.featuredArtistsOf
import com.music.bitchord.ui.mochy.component.librarySwipeAction
import com.music.bitchord.ui.mochy.component.queueSwipeAction
import com.music.bitchord.ui.mochy.component.rememberRevealSeenState
import com.music.bitchord.ui.mochy.component.revealOnAppear
import com.music.bitchord.ui.mochy.extensions.toMediaItem
import com.music.bitchord.ui.mochy.models.innertube.AlbumItem
import com.music.bitchord.ui.mochy.models.innertube.ArtistItem
import com.music.bitchord.ui.mochy.models.innertube.PlaylistItem
import com.music.bitchord.ui.mochy.models.innertube.SongItem
import com.music.bitchord.ui.mochy.models.innertube.WatchEndpoint
import com.music.bitchord.ui.mochy.navigation.MochyNavigator
import com.music.bitchord.ui.mochy.playback.LocalPlayerConnection
import com.music.bitchord.ui.mochy.playback.queues.YouTubePlaylistQueue
import com.music.bitchord.ui.mochy.playback.queues.YouTubeQueue
import com.music.bitchord.ui.mochy.player.menu.YouTubeAlbumMenu
import com.music.bitchord.ui.mochy.player.menu.YouTubeArtistMenu
import com.music.bitchord.ui.mochy.player.menu.YouTubePlaylistMenu
import com.music.bitchord.ui.mochy.player.menu.YouTubeSelectionSongMenu
import com.music.bitchord.ui.mochy.player.menu.YouTubeSongMenu
import com.music.bitchord.ui.mochy.utils.makeTimeString
import com.music.bitchord.ui.mochy.viewmodels.OnlinePlaylistViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun OnlinePlaylistScreen(
    navigator: MochyNavigator,
    scrollBehavior: TopAppBarScrollBehavior,
    playlistId: String,
    viewModel: OnlinePlaylistViewModel = hiltViewModel(),
) {
    // Coordinator: route args rule — the host passes playlistId; the VM loads on it.
    LaunchedEffect(playlistId) { viewModel.load(playlistId) }
    val context = LocalContext.current
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val coroutineScope = rememberCoroutineScope()

    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val queueTitle by playerConnection.queueTitle.collectAsState()

    val playlist by viewModel.playlist.collectAsState()
    val songs by viewModel.playlistSongs.collectAsState()
    val dbPlaylist by viewModel.dbPlaylist.collectAsState()
    val relatedItems by viewModel.relatedItems.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()

    val lazyListState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    val revealSeen = rememberRevealSeenState()

    var isSearching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }

    val filteredSongs = remember(songs, query) {
        if (query.text.isEmpty()) songs.mapIndexed { i, s -> i to s }
        else songs.mapIndexed { i, s -> i to s }.filter {
            it.second.title.contains(query.text, true) ||
                    it.second.artists.fastAny { a -> a.name.contains(query.text, true) }
        }
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

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(isSearching) { if (isSearching) focusRequester.requestFocus() }
    // each new search shows its matches from the first, not from wherever the last one was scrolled (#128)
    LaunchedEffect(query.text) {
        if (isSearching) lazyListState.scrollToItem(0)
    }

    LaunchedEffect(filteredSongs) {
        selection.fastForEachReversed { songId ->
            if (filteredSongs.find { it.second.id == songId } == null) {
                selection.remove(songId)
            }
        }
    }

    if (isSearching) {
        BackHandler {
            isSearching = false
            query = TextFieldValue()
        }
    } else if (inSelectMode) {
        BackHandler(onBack = onExitSelectionMode)
    }

    val coverUrl = playlist?.thumbnail ?: songs.firstOrNull { !it.thumbnail.isNullOrEmpty() }?.thumbnail
    val totalSeconds = remember(songs) { songs.sumOf { it.duration ?: 0 } }
    val featuredArtists = remember(songs) {
        featuredArtistsOf(songs.flatMap { it.artists }, id = { it.id }, name = { it.name })
    }

    PlaylistDetailScaffold(
        sharedElementKey = "playlist-${viewModel.playlistId}",
        coverUrl = coverUrl,
        lazyListState = lazyListState,
        onBackClick = { navigator.popBackStack() },
        onBackLongClick = { navigator.clearDetails() },
        contentPadding = LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Bottom)
            .union(WindowInsets.ime).asPaddingValues(),
        onMoreClick = playlist?.let { currentPlaylist ->
            {
                menuState.show {
                    YouTubePlaylistMenu(
                        playlist = currentPlaylist,
                        songs = songs,
                        coroutineScope = coroutineScope,
                        onDismiss = menuState::dismiss,
                        onImportedPlaylist = { playlistId ->
                            navigator.navigate("local_playlist/$playlistId")
                        },
                    )
                }
            }
        },
        topBarOverride = if (isSearching) {
            {
                PlaylistSearchBar(
                    query = query,
                    onQueryChange = { query = it },
                    placeholder = stringResource(R.string.search_in_playlist),
                    focusRequester = focusRequester,
                    onClose = {
                        isSearching = false
                        query = TextFieldValue()
                    },
                )
            }
        } else null,
        modeBar = if (inSelectMode) {
            PlaylistModeBar.Select(
                count = selection.size,
                allSelected = selection.size == filteredSongs.size && selection.isNotEmpty(),
                onToggleAll = {
                    val selectAll = selection.size != filteredSongs.size
                    selection.clear()
                    if (selectAll) selection.addAll(filteredSongs.map { it.second.id })
                },
                onMore = {
                    menuState.show {
                        YouTubeSelectionSongMenu(
                            songSelection = filteredSongs.filter { it.second.id in selection }.map { it.second },
                            onDismiss = menuState::dismiss,
                            clearAction = onExitSelectionMode
                        )
                    }
                },
                onClose = onExitSelectionMode,
            )
        } else null,
        overlay = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        },
    ) {
        val currentPlaylist = playlist
        if (currentPlaylist == null || songs.isEmpty()) {
            if (isLoading) {
                item(key = "loading_header") {
                    Spacer(Modifier.height(com.music.bitchord.ui.mochy.component.detail.playlistCoverHeight() * 0.975f))
                }
                item(key = "loading_tracks") {
                    PlaylistTrackBones()
                }
            }
            return@PlaylistDetailScaffold
        }

        if (!isSearching) {
            item(key = "playlist_header") {
                // read here, not at the top of the screen: a new queue then only redraws the header
                val isThisQueue = queueTitle == currentPlaylist.title
                val isEditableRemote = currentPlaylist.id == "LM" || currentPlaylist.isEditable
                PlaylistHeader(
                    onSearchClick = { isSearching = true },
                    title = currentPlaylist.title,
                    creators = listOfNotNull(
                        currentPlaylist.author?.let { CreatorUi(name = it.name, artistId = it.id) }
                    ),
                    stats = playlistStats(context.resources.getQuantityString(R.plurals.n_song, songs.size, songs.size), totalSeconds),
                    isPlaying = isThisQueue && isPlaying,
                    onPlayClick = {
                        if (isThisQueue) {
                            playerConnection.togglePlayPause()
                        } else {
                            playerConnection.playQueue(
                                YouTubePlaylistQueue(
                                    playlistId = currentPlaylist.id,
                                    playlistTitle = currentPlaylist.title,
                                    initialSongs = songs,
                                    initialContinuation = viewModel.continuation
                                )
                            )
                        }
                    },
                    onShuffleClick = {
                        playerConnection.playQueue(
                            YouTubePlaylistQueue(
                                playlistId = currentPlaylist.id,
                                playlistTitle = currentPlaylist.title,
                                initialSongs = songs.shuffled(),
                                initialContinuation = viewModel.continuation
                            )
                        )
                    },
                    isSaved = if (isEditableRemote) null else dbPlaylist?.playlist?.bookmarkedAt != null,
                    onSaveClick = {
                        viewModel.toggleSaved()
                    },
                )
            }
        }

        itemsIndexed(
            items = filteredSongs,
            key = { _, (index, song) -> "${index}_${song.id}" },
            contentType = { _, _ -> "track" },
        ) { position, (_, songItem) ->
            val onCheckedChange: (Boolean) -> Unit = {
                if (it) selection.add(songItem.id) else selection.remove(songItem.id)
            }

            SongSwipeBox(
                modifier = Modifier.animateItem(),
                enabled = !inSelectMode,
                library = { librarySwipeAction(songItem) },
                queue = { queueSwipeAction { songItem.toMediaItem() } },
            ) {
                val active = mediaMetadata?.id == songItem.id
                PlaylistTrackRow(
                    thumbnailUrl = songItem.thumbnail,
                    songId = songItem.id,
                    title = songItem.title,
                    subtitle = "${songItem.artists.joinToString { it.name }} • ${makeTimeString(songItem.duration?.times(1000L))}",
                    isActive = active,
                    isPlaying = active && isPlaying,
                    modifier = Modifier.revealOnAppear(
                        key = "track_${songItem.id}",
                        seenState = revealSeen,
                        delayMillis = minOf(position, 8) * 28,
                    ),
                    onClick = {
                        if (inSelectMode) {
                            onCheckedChange(songItem.id !in selection)
                        } else if (songItem.id == mediaMetadata?.id) {
                            playerConnection.togglePlayPause()
                        } else {
                            playerConnection.playQueue(
                                YouTubePlaylistQueue(
                                    playlistId = currentPlaylist.id,
                                    playlistTitle = currentPlaylist.title,
                                    initialSongs = filteredSongs.map { it.second },
                                    initialContinuation = viewModel.continuation,
                                    startIndex = position
                                )
                            )
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
                            YouTubeSongMenu(songItem, navigator, menuState::dismiss)
                        }
                    },
                    selected = inSelectMode && songItem.id in selection,
                    trailing = if (inSelectMode) {
                        { PlaylistSelectTick(checked = songItem.id in selection) }
                    } else null,
                )
            }
        }

        if (isLoadingMore) {
            item(key = "loading_more") {
                PlaylistTrackBones(rows = 2)
            }
        }

        if (!isSearching) {
            if (!isLoadingMore && viewModel.continuation == null) {
                item(key = "end_of_line") {
                    PlaylistEndOfLine(songCount = songs.size, totalSeconds = totalSeconds)
                }
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

            if (relatedItems.isNotEmpty()) {
                item(key = "related_title") {
                    PlaylistSectionTitle(
                        title = "Related Playlists",
                        modifier = Modifier.padding(top = 28.dp, bottom = 8.dp),
                    )
                }

                // Home's cards on the page's own inset: the same corners, type and spacing as
                // every other row of covers, and each one grows into its page like from Home
                item(key = "related_items") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = PlaylistInset),
                        horizontalArrangement = Arrangement.spacedBy(HomeCardSpacing),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(relatedItems.distinctBy { it.id }, key = { it.id }) { item ->
                            HomeCoverCard(
                                title = item.title,
                                subtitle = when (item) {
                                    is PlaylistItem -> listOfNotNull(item.author?.name, item.songCountText).joinToString(" · ").ifBlank { null }
                                    is AlbumItem -> listOfNotNull(
                                        item.artists?.joinToString { it.name }?.ifBlank { null },
                                        item.year?.toString(),
                                    ).joinToString(" · ").ifBlank { null }
                                    is SongItem -> item.artists.joinToString { it.name }
                                    is ArtistItem -> null
                                },
                                thumbnailUrl = item.thumbnail,
                                circular = item is ArtistItem,
                                sharedElementKey = coverTransitionKey(item),
                                modifier = Modifier
                                    .combinedClickable(
                                        interactionSource = null,
                                        indication = HomeCardHighlight,
                                        onClick = {
                                            when (item) {
                                                is PlaylistItem -> navigator.navigate("online_playlist/${item.id}")
                                                is AlbumItem -> navigator.navigate("album/${item.browseId}")
                                                is ArtistItem -> navigator.navigate("artist/${item.id}")
                                                is SongItem -> playerConnection.playQueue(
                                                    YouTubeQueue(WatchEndpoint(videoId = item.id))
                                                )
                                            }
                                        },
                                        onLongClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            menuState.show {
                                                when (item) {
                                                    is PlaylistItem -> YouTubePlaylistMenu(
                                                        playlist = item,
                                                        coroutineScope = coroutineScope,
                                                        onDismiss = menuState::dismiss,
                                                        onImportedPlaylist = { playlistId ->
                                                            navigator.navigate("local_playlist/$playlistId")
                                                        }
                                                    )
                                                    is SongItem -> YouTubeSongMenu(
                                                        song = item,
                                                        navigator = navigator,
                                                        onDismiss = menuState::dismiss
                                                    )
                                                    is AlbumItem -> YouTubeAlbumMenu(
                                                        albumItem = item,
                                                        navigator = navigator,
                                                        onDismiss = menuState::dismiss
                                                    )
                                                    is ArtistItem -> YouTubeArtistMenu(
                                                        artist = item,
                                                        onDismiss = menuState::dismiss
                                                    )
                                                }
                                            }
                                        }
                                    )
                            )
                        }
                    }
                }
            }
        }

        item(key = "bottom_spacer") {
            Spacer(Modifier.height(50.dp))
        }
    }
}

/** "21 songs · 1h 23m" */
internal fun playlistStats(songCountText: String, totalSeconds: Int): String {
    if (totalSeconds <= 0) return songCountText
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val duration = if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
    return "$songCountText · $duration"
}
