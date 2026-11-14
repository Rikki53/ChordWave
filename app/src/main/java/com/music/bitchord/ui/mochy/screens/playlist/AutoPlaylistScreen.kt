// Ported from Mochy Music (GPL-3.0): ui/screens/playlist/AutoPlaylistScreen.kt
// Adaptations: navController -> MochyNavigator (route strings verbatim,
// navigateUp -> popBackStack, backToMain -> clearDetails); hiltViewModel from
// androidx.hilt.navigation.compose; download state/action via CW Downloads
// (downloadStateForAll / enqueue / cancel / delete) instead of
// Mochy's download util + download service; AutoDownloads.LikedId is the
// literal "liked" (Mochy's value) until the auto-downloads engine lands;
// the "liked here" separator wears ChordWave's own mark
// (ic_chordwave_mark, worktree) and name, not Mochy's; the squircle's
// background is the literal Color(0xFF1A1A1A) — Mochy's
// ic_launcher_background value — because ChordWave has no such color
// resource. ImportSource / ImportSourceIcon are already staged
// (phase2-library); the adapter returns empty sections until ChordWave
// gains an importer.
package com.music.bitchord.ui.mochy.screens.playlist

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults.Indicator
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
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
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEachReversed
import androidx.compose.ui.util.fastSumBy
import androidx.datastore.preferences.core.edit
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.exoplayer.offline.Download
import com.music.bitchord.R
import com.music.bitchord.download.Downloads
import com.music.bitchord.ui.mochy.LocalPlayerAwareWindowInsets
import com.music.bitchord.ui.mochy.component.DefaultDialog
import com.music.bitchord.ui.mochy.component.DraggableScrollbar
import com.music.bitchord.ui.mochy.component.EmptyPlaceholder
import com.music.bitchord.ui.mochy.component.LikedCover
import com.music.bitchord.ui.mochy.component.LikedCoverColors
import com.music.bitchord.ui.mochy.component.LocalMenuState
import com.music.bitchord.ui.mochy.component.SongSwipeBox
import com.music.bitchord.ui.mochy.component.detail.FeaturedArtistsRow
import com.music.bitchord.ui.mochy.component.detail.PlaylistDetailScaffold
import com.music.bitchord.ui.mochy.component.detail.PlaylistEndOfLine
import com.music.bitchord.ui.mochy.component.detail.PlaylistHeader
import com.music.bitchord.ui.mochy.component.detail.PlaylistInset
import com.music.bitchord.ui.mochy.component.detail.PlaylistModeBar
import com.music.bitchord.ui.mochy.component.detail.PlaylistSearchBar
import com.music.bitchord.ui.mochy.component.detail.PlaylistSelectTick
import com.music.bitchord.ui.mochy.component.detail.PlaylistSortControl
import com.music.bitchord.ui.mochy.component.detail.PlaylistStatsStyle
import com.music.bitchord.ui.mochy.component.detail.PlaylistTrackBones
import com.music.bitchord.ui.mochy.component.detail.PlaylistTrackRow
import com.music.bitchord.ui.mochy.component.detail.featuredArtistsOf
import com.music.bitchord.ui.mochy.component.detail.playlistCoverHeight
import com.music.bitchord.ui.mochy.component.downloadStateForAll
import com.music.bitchord.ui.mochy.component.librarySwipeAction
import com.music.bitchord.ui.mochy.component.queueSwipeAction
import com.music.bitchord.ui.mochy.component.rememberLikedCoverColors
import com.music.bitchord.ui.mochy.component.rememberRevealSeenState
import com.music.bitchord.ui.mochy.component.revealOnAppear
import com.music.bitchord.ui.mochy.component.toCwSong
import com.music.bitchord.ui.mochy.constants.LastPlayedLikedSongsTimeKey
import com.music.bitchord.ui.mochy.constants.LikedSongSortDescendingKey
import com.music.bitchord.ui.mochy.constants.LikedSongSortType
import com.music.bitchord.ui.mochy.constants.LikedSongSortTypeKey
import com.music.bitchord.ui.mochy.constants.SongSortDescendingKey
import com.music.bitchord.ui.mochy.constants.SongSortType
import com.music.bitchord.ui.mochy.constants.SongSortTypeKey
import com.music.bitchord.ui.mochy.constants.YtmSyncKey
import com.music.bitchord.ui.mochy.extensions.toMediaItem
import com.music.bitchord.ui.mochy.importer.ImportSource
import com.music.bitchord.ui.mochy.models.Song
import com.music.bitchord.ui.mochy.navigation.MochyNavigator
import com.music.bitchord.ui.mochy.playback.LocalPlayerConnection
import com.music.bitchord.ui.mochy.playback.queues.ListQueue
import com.music.bitchord.ui.mochy.player.menu.AutoPlaylistMenu
import com.music.bitchord.ui.mochy.player.menu.SelectionSongMenu
import com.music.bitchord.ui.mochy.player.menu.SongMenu
import com.music.bitchord.ui.mochy.screens.library.ImportSourceIcon
import com.music.bitchord.ui.mochy.screens.setup.onboarding.SquircleShape
import com.music.bitchord.ui.mochy.utils.dataStore
import com.music.bitchord.ui.mochy.utils.makeTimeString
import com.music.bitchord.ui.mochy.utils.rememberEnumPreference
import com.music.bitchord.ui.mochy.utils.rememberPreference
import com.music.bitchord.ui.mochy.viewmodels.AutoPlaylistViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AutoPlaylistScreen(
    navigator: MochyNavigator,
    scrollBehavior: TopAppBarScrollBehavior,
    playlist: String,
    viewModel: AutoPlaylistViewModel = hiltViewModel(),
) {
    // Coordinator: route args rule — the host passes the auto-playlist key; the VM loads on it.
    LaunchedEffect(playlist) { viewModel.load(playlist) }
    val context = LocalContext.current
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val queueTitle by playerConnection.queueTitle.collectAsState()
    val playlist = when (viewModel.playlist) {
        "liked" -> stringResource(R.string.liked)
        "uploaded" -> stringResource(R.string.uploaded_playlist)
        "downloaded" -> stringResource(R.string.offline)
        "local" -> "Local"
        "local_liked" -> "Local liked songs"
        "cached" -> "Cached"
        "cached_liked" -> "Cached liked songs"
        "songs" -> "All Songs"
        "recently_added" -> "Recently Added"
        else -> viewModel.playlist
    }

    val content by viewModel.content.collectAsStateWithLifecycle()
    val songs = content?.songs
    val revealSeen = rememberRevealSeenState()

    var isSearching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf(TextFieldValue()) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(isSearching) {
        if (isSearching) {
            focusRequester.requestFocus()
        }
    }

    val (ytmSync) = rememberPreference(YtmSyncKey, true)

    val totalSeconds = remember(songs) { songs?.fastSumBy { it.song.duration } ?: 0 }

    val playlistType = when (viewModel.playlist) {
        "liked" -> PlaylistType.LIKE
        "downloaded" -> PlaylistType.DOWNLOAD
        "uploaded" -> PlaylistType.UPLOADED
        else -> PlaylistType.OTHER
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

    if (isSearching) {
        BackHandler {
            isSearching = false
            query = TextFieldValue()
        }
    } else if (inSelectMode) {
        BackHandler(onBack = onExitSelectionMode)
    }

    val (sortType, onSortTypeChange) = rememberEnumPreference(SongSortTypeKey, SongSortType.CREATE_DATE)
    val (sortDescending, onSortDescendingChange) = rememberPreference(SongSortDescendingKey, true)
    // Liked songs sorts on its own, with import order on top of the song sorts
    val (likedSortType, onLikedSortTypeChange) = rememberEnumPreference(LikedSongSortTypeKey, LikedSongSortType.CREATE_DATE)
    val (likedSortDescending, onLikedSortDescendingChange) = rememberPreference(LikedSongSortDescendingKey, true)

    // a file imported from the device is never a download - it's on the device already - so
    // only the rest say whether this list is downloaded
    val downloadState = downloadStateForAll(
        remember(songs) { songs.orEmpty().filterNot { it.song.isLocal }.map { it.song.id } }
    )

    LaunchedEffect(Unit) {
        if (ytmSync) {
            withContext(Dispatchers.IO) {
                if (playlistType == PlaylistType.LIKE) viewModel.syncLikedSongs()
                if (playlistType == PlaylistType.UPLOADED) viewModel.syncUploadedSongs()
            }
        }
    }

    var showRemoveDownloadDialog by remember { mutableStateOf(false) }

    if (showRemoveDownloadDialog) {
        DefaultDialog(
            onDismiss = { showRemoveDownloadDialog = false },
            content = {
                Text(
                    text = stringResource(R.string.remove_download_playlist_confirm, playlist),
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
                        coroutineScope.launch {
                            songs?.forEach { song ->
                                Downloads.cancel(song.song.id)
                                Downloads.delete(context, song.song.id)
                            }
                        }
                    },
                ) {
                    Text(text = stringResource(android.R.string.ok))
                }
            },
        )
    }

    fun matchesQuery(song: Song) = query.text.isEmpty() ||
        song.song.title.contains(query.text, true) ||
        song.artists.any { it.name.contains(query.text, true) }

    val filteredSongs = remember(songs, query) {
        songs?.filter(::matchesQuery) ?: emptyList()
    }
    // import order: the same songs under a small heading per service
    val filteredSections = remember(content, query) {
        content?.sections.orEmpty()
            .map { section -> section.copy(songs = section.songs.filter(::matchesQuery)) }
            .filter { it.songs.isNotEmpty() }
    }

    LaunchedEffect(filteredSongs) {
        selection.fastForEachReversed { songId ->
            if (filteredSongs.find { it.id == songId } == null) {
                selection.remove(songId)
            }
        }
    }

    val featuredArtists = remember(songs) {
        featuredArtistsOf(songs.orEmpty().flatMap { it.artists }, id = { it.id }, name = { it.name })
    }

    val state = rememberLazyListState()
    // each new search shows its matches from the first, not from wherever the last one was scrolled (#128)
    LaunchedEffect(query.text) {
        if (isSearching) state.scrollToItem(0)
    }
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val pullRefreshState = rememberPullToRefreshState()
    val canRefresh = playlistType == PlaylistType.LIKE || playlistType == PlaylistType.UPLOADED
    val coverUrl = remember(songs) {
        songs?.firstOrNull { !it.song.thumbnailUrl.isNullOrEmpty() }?.song?.thumbnailUrl
    }

    fun rememberLikedPlay() {
        if (viewModel.playlist == "liked") {
            coroutineScope.launch {
                context.dataStore.edit { it[LastPlayedLikedSongsTimeKey] = System.currentTimeMillis() }
            }
        }
    }

    fun playAll(items: List<Song>, startIndex: Int = 0) {
        rememberLikedPlay()
        playerConnection.playQueue(
            ListQueue(title = playlist, items = items.map { it.toMediaItem() }, startIndex = startIndex)
        )
    }

    val isLiked = viewModel.playlist == "liked"
    // the Local and Cached pages' liked songs wear the same cover as Liked songs, the one their cards show
    val hasLikedCover = isLiked || viewModel.playlist == "local_liked" || viewModel.playlist == "cached_liked"
    val likedCoverColors = if (hasLikedCover) rememberLikedCoverColors() else null

    PlaylistDetailScaffold(
        sharedElementKey = "playlist-${viewModel.playlist}",
        coverUrl = coverUrl,
        // Liked songs has a cover of its own, the one its cards show, instead of its first song's
        cover = if (hasLikedCover) {
            { modifier -> LikedCover(modifier, heartSize = 0.3f, heartAlignment = BiasAlignment(0f, -0.28f)) }
        } else null,
        coverThemeColor = likedCoverColors?.let(LikedCoverColors::theme),
        lazyListState = state,
        onBackClick = { navigator.popBackStack() },
        onBackLongClick = { navigator.clearDetails() },
        contentPadding = LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Bottom).asPaddingValues(),
        modifier = if (canRefresh) {
            Modifier.pullToRefresh(
                state = pullRefreshState,
                isRefreshing = isRefreshing,
                onRefresh = viewModel::refresh
            )
        } else Modifier,
        onMoreClick = {
            val current = songs.orEmpty()
            menuState.show {
                AutoPlaylistMenu(
                    downloadState = downloadState,
                    onQueue = { playerConnection.addToQueue(current.map { it.toMediaItem() }) },
                    onDownload = {
                        when (downloadState) {
                            Download.STATE_COMPLETED -> showRemoveDownloadDialog = true
                            Download.STATE_DOWNLOADING -> {
                                current.forEach { song ->
                                    Downloads.cancel(song.song.id)
                                }
                            }
                            else -> {
                                // imported files have no YouTube id to download from
                                current.filterNot { it.song.isLocal }.forEach { song ->
                                    Downloads.enqueue(context, song.toCwSong())
                                }
                            }
                        }
                    },
                    onDismiss = { menuState.dismiss() },
                    onSearch = { isSearching = true },
                    // Mochy's AutoDownloads.LikedId; the auto-downloads engine is not ported yet
                    autoDownloadId = if (isLiked) "liked" else null,
                )
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
        overlay = {
            DraggableScrollbar(
                modifier = Modifier
                    .padding(LocalPlayerAwareWindowInsets.current.asPaddingValues())
                    .align(Alignment.CenterEnd),
                scrollState = state,
                headerItems = if (isSearching) 0 else 1
            )
            if (canRefresh) {
                Indicator(
                    isRefreshing = isRefreshing,
                    state = pullRefreshState,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(LocalPlayerAwareWindowInsets.current.asPaddingValues()),
                )
            }
        },
    ) {
        val current = songs
        if (current == null) {
            item(key = "loading_header") {
                Spacer(Modifier.height(playlistCoverHeight() * 0.975f))
            }
            item(key = "loading_tracks") {
                PlaylistTrackBones()
            }
            return@PlaylistDetailScaffold
        }

        if (current.isEmpty()) {
            item(key = "empty_placeholder") {
                EmptyPlaceholder(
                    icon = R.drawable.music_note,
                    text = stringResource(R.string.playlist_is_empty),
                )
            }
            return@PlaylistDetailScaffold
        }

        if (!isSearching) {
            item(key = "playlist_header") {
                val isThisQueue = queueTitle == playlist
                PlaylistHeader(
                    onSearchClick = { isSearching = true },
                    title = playlist,
                    creators = emptyList(),
                    stats = playlistStats(
                        context.resources.getQuantityString(R.plurals.n_song, current.size, current.size),
                        totalSeconds,
                    ),
                    isPlaying = isThisQueue && isPlaying,
                    onPlayClick = {
                        if (isThisQueue) playerConnection.togglePlayPause() else playAll(current)
                    },
                    onShuffleClick = { playAll(current.shuffled()) },
                    sortControl = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isLiked) {
                            PlaylistSortControl(
                                current = likedSortType,
                                descending = likedSortDescending,
                                options = LikedSongSortType.entries,
                                label = { type ->
                                    when (type) {
                                        LikedSongSortType.CREATE_DATE -> stringResource(R.string.sort_by_create_date)
                                        LikedSongSortType.IMPORT_ORDER -> "Import order"
                                        LikedSongSortType.NAME -> stringResource(R.string.sort_by_name)
                                        LikedSongSortType.ARTIST -> stringResource(R.string.sort_by_artist)
                                        LikedSongSortType.PLAY_TIME -> stringResource(R.string.sort_by_play_time)
                                    }
                                },
                                onSelect = onLikedSortTypeChange,
                                onToggleDirection = { onLikedSortDescendingChange(!likedSortDescending) },
                            )
                        } else {
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
                        }
                        }
                    },
                )
            }
        }

        val trackRow: @Composable LazyItemScope.(Int, Song) -> Unit = { index, song ->
            val onCheckedChange: (Boolean) -> Unit = {
                if (it) selection.add(song.id) else selection.remove(song.id)
            }

            SongSwipeBox(
                modifier = Modifier.animateItem(),
                enabled = !inSelectMode,
                library = { librarySwipeAction(song.song) },
                queue = { queueSwipeAction { song.toMediaItem() } },
            ) {
                val active = song.song.id == mediaMetadata?.id
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
                        delayMillis = minOf(index, 8) * 28,
                    ),
                    onClick = {
                        if (inSelectMode) {
                            onCheckedChange(song.id !in selection)
                        } else if (song.song.id == mediaMetadata?.id) {
                            playerConnection.togglePlayPause()
                        } else {
                            playAll(current, startIndex = current.indexOfFirst { it.id == song.id })
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

        if (filteredSections.isEmpty()) {
            itemsIndexed(
                items = filteredSongs,
                key = { _, song -> song.id },
                contentType = { _, _ -> "track" },
            ) { index, song -> trackRow(index, song) }
        } else {
            filteredSections.forEach { section ->
                item(key = "source_${section.source?.key ?: "chordwave"}") {
                    LikedSourceSeparator(
                        source = section.source,
                        modifier = Modifier
                            .animateItem()
                            .revealOnAppear(key = "source_${section.source?.key ?: "chordwave"}", seenState = revealSeen),
                    )
                }
                itemsIndexed(
                    items = section.songs,
                    key = { _, song -> song.id },
                    contentType = { _, _ -> "track" },
                ) { index, song -> trackRow(index, song) }
            }
        }

        if (!isSearching) {
            item(key = "end_of_line") {
                PlaylistEndOfLine(songCount = current.size, totalSeconds = totalSeconds)
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

/**
 * the small line above each service's songs in Liked songs' import order: its app icon, its name,
 * then a hairline across. the ones liked here get ChordWave's own.
 */
@Composable
private fun LikedSourceSeparator(source: ImportSource?, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = PlaylistInset, end = PlaylistInset, top = 14.dp, bottom = 4.dp),
    ) {
        if (source != null) {
            ImportSourceIcon(source, size = 14.dp)
        } else {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(14.dp)
                    .clip(SquircleShape)
                    .background(Color(0xFF1A1A1A)),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_chordwave_mark),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(9.dp),
                )
            }
        }
        Spacer(Modifier.width(6.dp))
        Text(
            text = source?.label?.replaceFirstChar { it.uppercase() } ?: "ChordWave",
            style = PlaylistStatsStyle,
            color = Color.White.copy(alpha = 0.62f),
            maxLines = 1,
        )
        Spacer(Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(Color.White.copy(alpha = 0.08f)),
        )
    }
}

enum class PlaylistType {
    LIKE, DOWNLOAD, UPLOADED, OTHER
}
