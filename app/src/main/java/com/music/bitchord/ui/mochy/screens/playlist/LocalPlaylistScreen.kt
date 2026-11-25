// Ported from Mochy Music (GPL-3.0): ui/screens/playlist/LocalPlaylistScreen.kt
// Adaptations: navController -> MochyNavigator (route strings verbatim,
// navigateUp -> popBackStack, backToMain -> clearDetails, popBackStack ->
// popBackStack); hiltViewModel from androidx.hilt.navigation.compose;
// the database is never touched — every mutation goes through the view model
// (NEEDS.md C2): deletePlaylist, moveSong, removeSong, restoreSong,
// toggleSaved, syncFromYouTube, recordPlay, removeDownloads; related
// playlists come from viewModel.relatedPlaylists (collected at the top,
// not inside the list item); download state/action via CW Downloads
// (downloadStateForAll / enqueue / cancel) instead of Mochy's download util +
// ExoDownloadService; showEditPlaylistSheet loses its database param
// (NEEDS.md C8); the MutableList.move helper Mochy imports from
// extensions is a private extension at the bottom of this file (its only
// call site). Unused-in-Mochy imports dropped: SnackbarDuration,
// SnackbarResult, TextRange, TextFieldDialog.
package com.music.bitchord.ui.mochy.screens.playlist

import android.annotation.SuppressLint
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastForEachReversed
import androidx.compose.ui.util.fastSumBy
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.exoplayer.offline.Download
import coil3.compose.AsyncImage
import com.music.bitchord.R
import com.music.bitchord.download.Downloads
import com.music.bitchord.ui.mochy.LocalPlayerAwareWindowInsets
import com.music.bitchord.ui.mochy.component.BoneColor
import com.music.bitchord.ui.mochy.component.DefaultDialog
import com.music.bitchord.ui.mochy.component.DraggableScrollbar
import com.music.bitchord.ui.mochy.component.EmptyPlaceholder
import com.music.bitchord.ui.mochy.component.HomeCardHighlight
import com.music.bitchord.ui.mochy.component.LocalMenuState
import com.music.bitchord.ui.mochy.component.SongSwipeBox
import com.music.bitchord.ui.mochy.component.TopToaster
import com.music.bitchord.ui.mochy.component.detail.CreatorUi
import com.music.bitchord.ui.mochy.component.detail.FeaturedArtistsRow
import com.music.bitchord.ui.mochy.component.detail.PlaylistDetailScaffold
import com.music.bitchord.ui.mochy.component.detail.PlaylistEndOfLine
import com.music.bitchord.ui.mochy.component.detail.PlaylistHeader
import com.music.bitchord.ui.mochy.component.detail.PlaylistModeBar
import com.music.bitchord.ui.mochy.component.detail.PlaylistSearchBar
import com.music.bitchord.ui.mochy.component.detail.PlaylistSectionTitle
import com.music.bitchord.ui.mochy.component.detail.PlaylistSelectTick
import com.music.bitchord.ui.mochy.component.detail.PlaylistSortControl
import com.music.bitchord.ui.mochy.component.detail.PlaylistTrackBones
import com.music.bitchord.ui.mochy.component.detail.PlaylistTrackRow
import com.music.bitchord.ui.mochy.component.detail.featuredArtistsOf
import com.music.bitchord.ui.mochy.component.detail.playlistCoverHeight
import com.music.bitchord.ui.mochy.component.downloadStateForAll
import com.music.bitchord.ui.mochy.component.librarySwipeAction
import com.music.bitchord.ui.mochy.component.queueSwipeAction
import com.music.bitchord.ui.mochy.component.removeFromPlaylistSwipeAction
import com.music.bitchord.ui.mochy.component.rememberRevealSeenState
import com.music.bitchord.ui.mochy.component.revealOnAppear
import com.music.bitchord.ui.mochy.component.showEditPlaylistSheet
import com.music.bitchord.ui.mochy.component.toCwSong
import com.music.bitchord.ui.mochy.constants.AccountNameKey
import com.music.bitchord.ui.mochy.constants.PlaylistEditLockKey
import com.music.bitchord.ui.mochy.constants.PlaylistSongSortDescendingKey
import com.music.bitchord.ui.mochy.constants.PlaylistSongSortType
import com.music.bitchord.ui.mochy.constants.PlaylistSongSortTypeKey
import com.music.bitchord.ui.mochy.constants.ProfilePicUriKey
import com.music.bitchord.ui.mochy.extensions.toMediaItem
import com.music.bitchord.ui.mochy.models.PlaylistSong
import com.music.bitchord.ui.mochy.navigation.MochyNavigator
import com.music.bitchord.ui.mochy.utils.resize
import com.music.bitchord.ui.mochy.playback.LocalPlayerConnection
import com.music.bitchord.ui.mochy.playback.queues.ListQueue
import com.music.bitchord.ui.mochy.player.menu.LocalPlaylistMenu
import com.music.bitchord.ui.mochy.player.menu.SelectionSongMenu
import com.music.bitchord.ui.mochy.player.menu.SongMenu
import com.music.bitchord.ui.mochy.utils.makeTimeString
import com.music.bitchord.ui.mochy.utils.rememberEnumPreference
import com.music.bitchord.ui.mochy.utils.rememberPreference
import com.music.bitchord.ui.mochy.viewmodels.LocalPlaylistViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@SuppressLint("RememberReturnType")
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LocalPlaylistScreen(
    navigator: MochyNavigator,
    scrollBehavior: TopAppBarScrollBehavior,
    playlistId: String,
    viewModel: LocalPlaylistViewModel = hiltViewModel(),
) {
    // Coordinator: route args rule — the host passes playlistId; the VM loads on it.
    LaunchedEffect(playlistId) { viewModel.load(playlistId) }
    val context = LocalContext.current
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val queueTitle by playerConnection.queueTitle.collectAsState()

    val playlist by viewModel.playlist.collectAsState()
    val songs by viewModel.playlistSongs.collectAsState()
    val relatedPlaylistsSource by viewModel.relatedPlaylists.collectAsState()
    val mutableSongs = remember { mutableStateListOf<PlaylistSong>() }
    val playlistLength = remember(songs) { songs.fastSumBy { it.song.song.duration } }
    val (sortType, onSortTypeChange) = rememberEnumPreference(PlaylistSongSortTypeKey, PlaylistSongSortType.CUSTOM)
    val (sortDescending, onSortDescendingChange) = rememberPreference(PlaylistSongSortDescendingKey, true)
    var locked by rememberPreference(PlaylistEditLockKey, defaultValue = true)
    var showSortDialog by remember { mutableStateOf(false) }
    val (accountName) = rememberPreference(AccountNameKey, "")
    val (profilePicUri) = rememberPreference(ProfilePicUriKey, "")

    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val revealSeen = rememberRevealSeenState()

    var isSearching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }

    val filteredSongs = remember(songs, query) {
        if (query.text.isEmpty()) {
            songs
        } else {
            songs.filter { song ->
                song.song.song.title.contains(query.text, ignoreCase = true) ||
                    song.song.artists.fastAny { it.name.contains(query.text, ignoreCase = true) }
            }
        }
    }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(isSearching) {
        if (isSearching) focusRequester.requestFocus()
    }

    var inSelectMode by rememberSaveable { mutableStateOf(false) }
    val selection = rememberSaveable(
        saver = listSaver<MutableList<Int>, Int>(
            save = { it.toList() },
            restore = { it.toMutableStateList() }
        )
    ) { mutableStateListOf() }
    val onExitSelectionMode = {
        inSelectMode = false
        selection.clear()
    }

    val downloadState = downloadStateForAll(remember(songs) { songs.map { it.song.id } })

    val editable: Boolean = playlist?.playlist?.isEditable == true
    // unlocking from the menu turns the rows into drag handles; the bar's tick locks them again
    val isEditingOrder = sortType == PlaylistSongSortType.CUSTOM && !locked && !isSearching && editable && !inSelectMode

    if (isSearching) {
        BackHandler {
            isSearching = false
            query = TextFieldValue()
        }
    } else if (inSelectMode) {
        BackHandler(onBack = onExitSelectionMode)
    } else if (isEditingOrder) {
        BackHandler { locked = true }
    }

    LaunchedEffect(songs) {
        selection.fastForEachReversed { mapId ->
            if (songs.find { it.map.id == mapId } == null) {
                selection.remove(Integer.valueOf(mapId))
            }
        }
    }

    LaunchedEffect(songs) {
        mutableSongs.apply {
            clear()
            addAll(songs)
        }
    }

    var showRemoveDownloadDialog by remember { mutableStateOf(false) }
    if (showRemoveDownloadDialog) {
        DefaultDialog(
            onDismiss = { showRemoveDownloadDialog = false },
            content = {
                Text(
                    text = stringResource(R.string.remove_download_playlist_confirm, playlist?.playlist?.name.orEmpty()),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 18.dp),
                )
            },
            buttons = {
                TextButton(onClick = { showRemoveDownloadDialog = false }) {
                    Text(text = stringResource(android.R.string.cancel))
                }
                TextButton(
                    onClick = {
                        showRemoveDownloadDialog = false
                        viewModel.removeDownloads()
                    }
                ) {
                    Text(text = stringResource(android.R.string.ok))
                }
            },
        )
    }

    var showDeletePlaylistDialog by remember { mutableStateOf(false) }
    if (showDeletePlaylistDialog) {
        DefaultDialog(
            onDismiss = { showDeletePlaylistDialog = false },
            content = {
                Text(
                    text = stringResource(R.string.delete_playlist_confirm, playlist?.playlist?.name.orEmpty()),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 18.dp)
                )
            },
            buttons = {
                TextButton(onClick = { showDeletePlaylistDialog = false }) {
                    Text(text = stringResource(android.R.string.cancel))
                }
                TextButton(
                    onClick = {
                        showDeletePlaylistDialog = false
                        viewModel.deletePlaylist()
                        navigator.popBackStack()
                    }
                ) {
                    Text(text = stringResource(android.R.string.ok))
                }
            }
        )
    }

    // the header is the only item above the songs, and only while not searching
    val headerItems by rememberUpdatedState(if (isSearching) 0 else 1)
    val lazyListState = rememberLazyListState()
    // each new search shows its matches from the first, not from wherever the last one was scrolled (#128)
    LaunchedEffect(query.text) {
        if (isSearching) lazyListState.scrollToItem(0)
    }
    var dragInfo by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val reorderableState = rememberReorderableLazyListState(
        lazyListState = lazyListState,
        scrollThresholdPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues()
    ) { from, to ->
        if (to.index >= headerItems && from.index >= headerItems) {
            val currentDragInfo = dragInfo
            dragInfo = if (currentDragInfo == null) {
                (from.index - headerItems) to (to.index - headerItems)
            } else {
                currentDragInfo.first to (to.index - headerItems)
            }
            mutableSongs.move(from.index - headerItems, to.index - headerItems)
        }
    }

    LaunchedEffect(reorderableState.isAnyItemDragging) {
        if (!reorderableState.isAnyItemDragging) {
            dragInfo?.let { (from, to) ->
                viewModel.moveSong(from, to)
                dragInfo = null
            }
        }
    }

    val coverUrl = remember(playlist, songs) {
        playlist?.thumbnails?.firstOrNull()
            ?: songs.firstOrNull { !it.song.song.thumbnailUrl.isNullOrEmpty() }?.song?.song?.thumbnailUrl
    }
    val featuredArtists = remember(songs) {
        featuredArtistsOf(songs.flatMap { it.song.artists }, id = { it.id }, name = { it.name })
    }
    val creatorAvatar = profilePicUri.takeIf { it.isNotBlank() }?.let { if (it.contains("://")) it else "file://$it" }

    fun play(items: List<PlaylistSong>, startIndex: Int = 0) {
        val current = playlist ?: return
        playerConnection.playQueue(
            ListQueue(
                title = current.playlist.name,
                items = items.map { it.song.toMediaItem() },
                startIndex = startIndex,
            )
        )
        viewModel.recordPlay()
    }

    val openPlaylistMenu: (() -> Unit)? = playlist?.let { currentPlaylist ->
        {
            menuState.show {
                LocalPlaylistMenu(
                    playlist = currentPlaylist,
                    songs = songs,
                    context = context,
                    downloadState = downloadState,
                    // Over the menu, not instead of it: Done comes back to it.
                    onEdit = {
                        val top = songs.minWithOrNull(compareBy({ it.map.position }, { it.map.id }))?.song
                        menuState.showEditPlaylistSheet(
                            playlist = currentPlaylist.playlist,
                            context = context,
                            topSongId = top?.id,
                            topSongArtworkUrl = top?.song?.thumbnailUrl,
                        )
                    },
                    onSync = {
                        viewModel.syncFromYouTube()
                        coroutineScope.launch(Dispatchers.Main) {
                            snackbarHostState.showSnackbar(context.getString(R.string.playlist_synced))
                        }
                    },
                    onDelete = { showDeletePlaylistDialog = true },
                    onDownload = {
                        when (downloadState) {
                            Download.STATE_COMPLETED -> showRemoveDownloadDialog = true
                            Download.STATE_DOWNLOADING -> {
                                songs.forEach { song ->
                                    Downloads.cancel(song.song.id)
                                }
                            }
                            else -> {
                                // imported files are already on the phone, with nothing online to download
                                songs.filterNot { it.song.song.isLocal }.forEach { song ->
                                    Downloads.enqueue(context, song.song.toCwSong())
                                }
                            }
                        }
                    },
                    onQueue = {
                        playerConnection.addToQueue(items = songs.map { it.song.toMediaItem() })
                    },
                    onDismiss = { menuState.dismiss() },
                    locked = locked,
                    onToggleLock = {
                        locked = !locked
                        // dragging only means something in your own order
                        if (!locked) onSortTypeChange(PlaylistSongSortType.CUSTOM)
                    },
                    onShowSortDialog = { showSortDialog = true },
                    onSearch = { isSearching = true },
                    onPlayNext = {
                        playerConnection.playNext(songs.map { it.song.toMediaItem() })
                    },
                    navigator = navigator,
                )
            }
        }
    }

    PlaylistDetailScaffold(
        sharedElementKey = "playlist-${viewModel.playlistId}",
        coverUrl = coverUrl,
        lazyListState = lazyListState,
        onBackClick = { navigator.popBackStack() },
        onBackLongClick = { navigator.clearDetails() },
        contentPadding = LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Bottom)
            .union(WindowInsets.ime).asPaddingValues(),
        onMoreClick = openPlaylistMenu,
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
        modeBar = when {
            inSelectMode -> PlaylistModeBar.Select(
                count = selection.size,
                allSelected = selection.size == songs.size && selection.isNotEmpty(),
                onToggleAll = {
                    val selectAll = selection.size != songs.size
                    selection.clear()
                    if (selectAll) selection.addAll(songs.map { it.map.id })
                },
                onMore = {
                    menuState.show {
                        SelectionSongMenu(
                            songSelection = selection.mapNotNull { mapId -> songs.find { it.map.id == mapId }?.song },
                            songPosition = selection.mapNotNull { mapId -> songs.find { it.map.id == mapId }?.map },
                            onDeleteFromPlaylist = { maps -> maps.forEach { viewModel.removeSong(it) } },
                            onDismiss = menuState::dismiss,
                            clearAction = onExitSelectionMode
                        )
                    }
                },
                onClose = onExitSelectionMode,
            )
            isEditingOrder -> PlaylistModeBar.EditOrder(
                onMore = openPlaylistMenu,
                onDone = { locked = true },
            )
            else -> null
        },
        overlay = {
            DraggableScrollbar(
                modifier = Modifier
                    .padding(LocalPlayerAwareWindowInsets.current.union(WindowInsets.ime).asPaddingValues())
                    .align(Alignment.CenterEnd),
                scrollState = lazyListState,
                headerItems = headerItems
            )
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.union(WindowInsets.ime))
                    .align(Alignment.BottomCenter),
            )
        },
    ) {
        val currentPlaylist = playlist
        if (currentPlaylist == null) {
            item(key = "loading_header") {
                Spacer(Modifier.height(playlistCoverHeight() * 0.975f))
            }
            item(key = "loading_tracks") {
                PlaylistTrackBones()
            }
            return@PlaylistDetailScaffold
        }

        if (songs.isEmpty() && currentPlaylist.playlist.remoteSongCount == 0) {
            item(key = "empty_placeholder") {
                EmptyPlaceholder(
                    icon = R.drawable.music_note,
                    text = stringResource(R.string.playlist_is_empty),
                    modifier = Modifier.animateItem()
                )
            }
        } else if (!isSearching) {
            item(key = "playlist_header") {
                val isThisQueue = queueTitle == currentPlaylist.playlist.name
                val savedRemote = currentPlaylist.playlist.browseId != null && !currentPlaylist.playlist.isEditable
                PlaylistHeader(
                    onSearchClick = { isSearching = true },
                    title = currentPlaylist.playlist.name,
                    creators = if (accountName.isNotBlank()) listOf(CreatorUi(name = accountName, avatarUrl = creatorAvatar)) else emptyList(),
                    stats = playlistStats(
                        context.resources.getQuantityString(R.plurals.n_song, songs.size, songs.size),
                        playlistLength,
                    ),
                    isPlaying = isThisQueue && isPlaying,
                    onPlayClick = {
                        if (isThisQueue) playerConnection.togglePlayPause() else play(songs)
                    },
                    onShuffleClick = { play(songs.shuffled()) },
                    isSaved = if (savedRemote) currentPlaylist.playlist.bookmarkedAt != null else null,
                    onSaveClick = {
                        viewModel.toggleSaved()
                    },
                    sortControl = {
                        PlaylistSortControl(
                            current = sortType,
                            descending = sortDescending,
                            options = PlaylistSongSortType.entries,
                            label = { stringResource(playlistSortLabel(it)) },
                            onSelect = onSortTypeChange,
                            onToggleDirection = { onSortDescendingChange(!sortDescending) },
                            showDirection = { it != PlaylistSongSortType.CUSTOM },
                        )
                    },
                )
            }
        }

        itemsIndexed(
            items = if (isSearching) filteredSongs else mutableSongs,
            key = { _, song -> song.map.id },
            contentType = { _, _ -> "track" },
        ) { index, song ->
            ReorderableItem(
                state = reorderableState,
                key = song.map.id,
            ) {
                val currentItem by rememberUpdatedState(song)

                fun deleteFromPlaylist() {
                    viewModel.removeSong(currentItem.map)
                }

                // takes the song out, with a few seconds to put it back exactly where it was
                fun removeWithUndo() {
                    val removed = currentItem
                    deleteFromPlaylist()
                    TopToaster.show(
                        text = context.getString(
                            R.string.playlist_removed_song_from,
                            removed.song.song.title,
                            playlist?.playlist?.name ?: context.getString(R.string.playlist_fallback_name),
                        ),
                        thumbnail = removed.song.song.thumbnailUrl,
                        iconRes = R.drawable.playlist_add,
                        actionLabel = context.getString(R.string.undo),
                        onAction = {
                            viewModel.restoreSong(removed.map)
                        },
                    )
                }

                val onCheckedChange: (Boolean) -> Unit = {
                    if (it) selection.add(song.map.id) else selection.remove(Integer.valueOf(song.map.id))
                }

                val content: @Composable () -> Unit = {
                    val active = song.song.id == mediaMetadata?.id
                    PlaylistTrackRow(
                        thumbnailUrl = song.song.song.thumbnailUrl,
                        songId = song.song.id,
                        title = song.song.song.title,
                        subtitle = "${song.song.artists.joinToString { it.name }} • ${makeTimeString(song.song.song.duration * 1000L)}",
                        isActive = active,
                        isPlaying = active && isPlaying,
                        modifier = Modifier.revealOnAppear(
                            key = "track_${song.map.id}",
                            seenState = revealSeen,
                            delayMillis = minOf(index, 8) * 28,
                        ),
                        onClick = {
                            if (inSelectMode) {
                                onCheckedChange(!selection.contains(song.map.id))
                            } else if (song.song.id == mediaMetadata?.id) {
                                playerConnection.togglePlayPause()
                            } else {
                                play(songs, startIndex = songs.indexOfFirst { it.map.id == song.map.id })
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
                                    originalSong = song.song,
                                    playlistSong = song,
                                    playlistBrowseId = playlist?.playlist?.browseId,
                                    navigator = navigator,
                                    onDismiss = menuState::dismiss,
                                    onRemoveFromPlaylist = { viewModel.removeSong(it) },
                                )
                            }
                        },
                        selected = inSelectMode && selection.contains(song.map.id),
                        trailing = when {
                            inSelectMode -> {
                                { PlaylistSelectTick(checked = selection.contains(song.map.id)) }
                            }
                            // only the handle while reordering, so a tap can't open a menu mid-drag
                            isEditingOrder -> {
                                {
                                    IconButton(
                                        onClick = { },
                                        modifier = Modifier.draggableHandle(),
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.drag_handle),
                                            contentDescription = stringResource(R.string.playlist_drag_to_move),
                                            tint = Color.White.copy(alpha = 0.78f),
                                        )
                                    }
                                }
                            }
                            else -> null
                        },
                    )
                }

                SongSwipeBox(
                    modifier = Modifier.animateItem(),
                    enabled = !inSelectMode,
                    // in your own playlist the library action takes the song out; a saved one you
                    // can't edit adds it to your library instead. Settings > Gesture sets the side.
                    library = {
                        if (editable) {
                            removeFromPlaylistSwipeAction { removeWithUndo() }
                        } else {
                            librarySwipeAction(song.song.song)
                        }
                    },
                    queue = { queueSwipeAction { song.song.toMediaItem() } },
                ) {
                    content()
                }
            }
        }

        if (!isSearching && editable) {
            item(key = "add_music_row") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(onClick = {
                            menuState.showAddMusicSheet(
                                playlistId = viewModel.playlistId,
                                startInLibrary = songs.isEmpty(),
                            )
                        })
                        .padding(start = 36.dp, end = 24.dp, top = 9.dp, bottom = 9.dp)
                        .animateItem(),
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(39.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(BoneColor),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.add),
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Text(
                        text = stringResource(R.string.add_music),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.padding(horizontal = 11.dp),
                    )
                }
            }
        }

        if (!isSearching && songs.isNotEmpty()) {
            item(key = "end_of_line") {
                PlaylistEndOfLine(songCount = songs.size, totalSeconds = playlistLength)
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

        if (!isSearching) {
            item(key = "related_playlists") {
                val relatedPlaylists = remember(relatedPlaylistsSource) {
                    relatedPlaylistsSource.shuffled().take(5)
                }

                if (relatedPlaylists.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 28.dp)
                    ) {
                        PlaylistSectionTitle(title = stringResource(R.string.playlist_related))
                        Spacer(Modifier.height(9.dp))
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 36.dp),
                            horizontalArrangement = Arrangement.spacedBy(15.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(relatedPlaylists, key = { it.id }) { related ->
                                // no clip on the whole card: its rounded corner cut into the song count
                                // under the cover. The press shows Home's highlight around it instead.
                                Column(
                                    modifier = Modifier
                                        .width(137.dp)
                                        .clickable(interactionSource = null, indication = HomeCardHighlight) {
                                            navigator.navigate("local_playlist/${related.id}")
                                        }
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(1f)
                                            .clip(RoundedCornerShape(20.dp))
                                            .background(BoneColor)
                                    ) {
                                        val thumbUrl = related.playlist.thumbnailUrl ?: related.thumbnails.firstOrNull()
                                        if (thumbUrl != null) {
                                            AsyncImage(
                                                model = thumbUrl.resize(320, 320),
                                                contentDescription = null,
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                        } else {
                                            Icon(
                                                painter = painterResource(R.drawable.music_note),
                                                contentDescription = null,
                                                tint = Color.White.copy(alpha = 0.6f),
                                                modifier = Modifier
                                                    .size(44.dp)
                                                    .align(Alignment.Center)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = related.playlist.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White.copy(alpha = 0.92f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = pluralStringResource(R.plurals.n_song, related.songCount, related.songCount),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White.copy(alpha = 0.5f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item(key = "bottom_spacer") {
            Spacer(Modifier.height(50.dp))
        }
    }

    if (showSortDialog) {
        AlertDialog(
            onDismissRequest = { showSortDialog = false },
            title = { Text(stringResource(R.string.playlist_sort_order)) },
            text = {
                Column {
                    PlaylistSongSortType.entries.forEach { type ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (sortType == type) onSortDescendingChange(!sortDescending) else onSortTypeChange(type)
                                    showSortDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = sortType == type,
                                onClick = {
                                    if (sortType == type) onSortDescendingChange(!sortDescending) else onSortTypeChange(type)
                                    showSortDialog = false
                                }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = stringResource(playlistSortLabel(type)) +
                                    if (sortType == type) (if (sortDescending) stringResource(R.string.playlist_sort_desc) else stringResource(R.string.playlist_sort_asc)) else "",
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSortDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

private fun playlistSortLabel(type: PlaylistSongSortType): Int = when (type) {
    PlaylistSongSortType.CUSTOM -> R.string.sort_by_custom
    PlaylistSongSortType.CREATE_DATE -> R.string.sort_by_create_date
    PlaylistSongSortType.NAME -> R.string.sort_by_name
    PlaylistSongSortType.ARTIST -> R.string.sort_by_artist
    PlaylistSongSortType.PLAY_TIME -> R.string.sort_by_play_time
}

/** Mochy's extensions/ListExt.kt move(), kept private here — this screen is its only call site. */
private fun <T> MutableList<T>.move(fromIndex: Int, toIndex: Int): MutableList<T> {
    add(toIndex, removeAt(fromIndex))
    return this
}
