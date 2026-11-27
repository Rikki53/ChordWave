// Ported from Mochy Music (GPL-3.0): ui/screens/AlbumScreen.kt
// AlbumScreen.kt — Album detail page: cover, tracks with search + multi-select, shelves.
//
// Port notes (see staging/phase2-detail/NEEDS.md for the full list):
// - Navigation goes through MochyNavigator; route strings are Mochy's verbatim.
//   Back is popBackStack(); back-to-main is clearDetails().
// - The screen never touches the database: likes go through
//   viewModel.toggleAlbumLike()/toggleSongLike(), the stream format through
//   viewModel.format (NEEDS.md §(a) C1 additions, not in Mochy's VM).
// - Mochy's automix prime (service.getAutomix) has no CW equivalent yet and is
//   dropped; player.pause()/play() are the staged connection methods.
// - PlaylistDetailScaffold + the track rows are the detail kit (NEEDS.md C10);
//   LocalAlbumRadio + SelectionSongMenu are NEEDS.md C8/C9.

package com.music.bitchord.ui.mochy.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEachReversed
import androidx.hilt.navigation.compose.hiltViewModel
import com.music.bitchord.ui.mochy.LocalPlayerAwareWindowInsets
import com.music.bitchord.ui.mochy.navigation.MochyNavigator
import com.music.bitchord.ui.mochy.playback.LocalPlayerConnection
import com.music.bitchord.R
import com.music.bitchord.ui.mochy.constants.HideExplicitKey
import com.music.bitchord.ui.mochy.constants.HideVideoSongsKey
import com.music.bitchord.ui.mochy.models.Album
import com.music.bitchord.ui.mochy.extensions.toMediaItem
import com.music.bitchord.ui.mochy.playback.queues.LocalAlbumRadio
import com.music.bitchord.ui.mochy.component.HomeCardHighlight
import com.music.bitchord.ui.mochy.component.HomeCardSpacing
import com.music.bitchord.ui.mochy.component.HomeCoverCard
import com.music.bitchord.ui.mochy.component.LocalMenuState
import com.music.bitchord.ui.mochy.component.SongSwipeBox
import com.music.bitchord.ui.mochy.component.detail.CreatorUi
import com.music.bitchord.ui.mochy.component.detail.FeaturedArtistsRow
import com.music.bitchord.ui.mochy.component.detail.PlaylistDetailScaffold
import com.music.bitchord.ui.mochy.component.detail.PlaylistEndOfLine
import com.music.bitchord.ui.mochy.component.detail.PlaylistHeader
import com.music.bitchord.ui.mochy.component.detail.PlaylistInset
import com.music.bitchord.ui.mochy.component.detail.PlaylistSectionTitle
import com.music.bitchord.ui.mochy.component.detail.PlaylistStatsStyle
import com.music.bitchord.ui.mochy.component.detail.PlaylistTrackBones
import com.music.bitchord.ui.mochy.component.detail.PlaylistModeBar
import com.music.bitchord.ui.mochy.component.detail.PlaylistSearchBar
import com.music.bitchord.ui.mochy.component.detail.PlaylistSelectTick
import com.music.bitchord.ui.mochy.component.detail.PlaylistTrackRow
import com.music.bitchord.ui.mochy.component.detail.featuredArtistsOf
import com.music.bitchord.ui.mochy.component.detail.playlistCoverHeight
import com.music.bitchord.ui.mochy.component.librarySwipeAction
import com.music.bitchord.ui.mochy.component.queueSwipeAction
import com.music.bitchord.ui.mochy.component.rememberRevealSeenState
import com.music.bitchord.ui.mochy.component.revealOnAppear
import com.music.bitchord.ui.mochy.player.menu.AlbumMenu
import com.music.bitchord.ui.mochy.player.menu.SelectionSongMenu
import com.music.bitchord.ui.mochy.player.menu.SongMenu
import com.music.bitchord.ui.mochy.player.menu.YouTubeAlbumMenu
import com.music.bitchord.ui.mochy.utils.makeTimeString
import com.music.bitchord.ui.mochy.utils.rememberPreference
import com.music.bitchord.ui.mochy.viewmodels.AlbumViewModel
import com.music.innertube.models.AlbumItem

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AlbumScreen(
    navigator: MochyNavigator,
    @Suppress("UNUSED_PARAMETER") scrollBehavior: TopAppBarScrollBehavior,
    albumId: String,
    viewModel: AlbumViewModel = hiltViewModel(),
) {
    // Coordinator: route args rule — the host passes albumId; the VM loads on it.
    LaunchedEffect(albumId) { viewModel.load(albumId) }
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current ?: return

    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    val albumWithSongs by viewModel.albumWithSongs.collectAsState()
    val otherVersions by viewModel.otherVersions.collectAsState()
    val releasesForYou by viewModel.releasesForYou.collectAsState()
    val moreByArtist by viewModel.moreByArtist.collectAsState()
    val description by viewModel.description.collectAsState()
    val loadFailed by viewModel.loadFailed.collectAsState()
    val hideExplicit by rememberPreference(key = HideExplicitKey, defaultValue = false)
    val hideVideoSongs by rememberPreference(key = HideVideoSongsKey, defaultValue = false)

    val filteredSongs = remember(albumWithSongs, hideExplicit, hideVideoSongs) {
        var songs = albumWithSongs?.songs ?: emptyList()
        if (hideExplicit) songs = songs.filter { !it.song.explicit }
        if (hideVideoSongs) songs = songs.filter { !it.song.isVideo }
        songs
    }

    // the album's own song search, from the round button beside shuffle
    var isSearching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }
    val focusRequester = remember { FocusRequester() }
    val shownSongs = remember(filteredSongs, isSearching, query.text) {
        val typed = query.text.trim()
        if (!isSearching || typed.isEmpty()) {
            filteredSongs
        } else {
            filteredSongs.filter { song ->
                song.song.title.contains(typed, ignoreCase = true) ||
                    song.artists.any { it.name.contains(typed, ignoreCase = true) }
            }
        }
    }
    val closeSearch = {
        isSearching = false
        query = TextFieldValue()
    }
    if (isSearching) {
        BackHandler(onBack = closeSearch)
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
    if (inSelectMode) {
        BackHandler(onBack = onExitSelectionMode)
    }

    LaunchedEffect(filteredSongs) {
        selection.fastForEachReversed { songId ->
            if (filteredSongs.find { it.id == songId } == null) selection.remove(songId)
        }
    }

    // the stream format of the album's first shown song, for the stats line;
    // the VM watches the same filtered list, so the screen never queries the DB
    val format by viewModel.format.collectAsState()

    val lazyListState = rememberLazyListState()
    val revealSeen = rememberRevealSeenState()
    val album = albumWithSongs
    val totalSeconds = remember(filteredSongs) { filteredSongs.sumOf { it.song.duration } }
    val featuredArtists = remember(filteredSongs) {
        featuredArtistsOf(filteredSongs.flatMap { it.artists }, id = { it.id }, name = { it.name })
    }
    val mainArtistName = album?.artists?.firstOrNull()?.name

    fun play(startIndex: Int = 0, shuffled: Boolean = false) {
        val current = album ?: return
        // Mochy primed the automix here (playerConnection.service.getAutomix);
        // CW needs no priming call — the service fills AutoPlay itself on
        // every track transition (PlaybackService.loadAutoplayForCurrentTrack).
        playerConnection.playQueue(
            if (shuffled) {
                LocalAlbumRadio(current.copy(songs = current.songs.shuffled()))
            } else {
                LocalAlbumRadio(current, startIndex = startIndex)
            }
        )
    }

    PlaylistDetailScaffold(
        sharedElementKey = "album-${viewModel.albumId}",
        coverUrl = album?.album?.thumbnailUrl,
        lazyListState = lazyListState,
        onBackClick = { navigator.popBackStack() },
        onBackLongClick = { navigator.clearDetails() },
        contentPadding = LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Bottom).asPaddingValues(),
        onMoreClick = album?.let { current ->
            {
                menuState.show {
                    AlbumMenu(
                        originalAlbum = Album(current.album, current.artists),
                        navigator = navigator,
                        onDismiss = menuState::dismiss,
                    )
                }
            }
        },
        topBarOverride = if (isSearching) {
            {
                PlaylistSearchBar(
                    query = query,
                    onQueryChange = { query = it },
                    placeholder = stringResource(R.string.search_in_album),
                    focusRequester = focusRequester,
                    onClose = closeSearch,
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
                            songSelection = selection.mapNotNull { id -> filteredSongs.find { it.id == id } },
                            onDismiss = menuState::dismiss,
                            clearAction = onExitSelectionMode,
                        )
                    }
                },
                onClose = onExitSelectionMode,
            )
        } else null,
    ) {
        if (album == null || album.songs.isEmpty()) {
            item(key = "loading_header") {
                Spacer(Modifier.height(playlistCoverHeight() * 0.975f))
            }
            item(key = "loading_tracks") {
                if (loadFailed) AlbumRetry(onRetry = viewModel::retry) else PlaylistTrackBones()
            }
            return@PlaylistDetailScaffold
        }

        if (!isSearching) item(key = "album_header", contentType = "header") {
            // read here, not at the top of the screen: a track change then only redraws the header
            val isThisAlbum = mediaMetadata?.album?.id == album.album.id
            PlaylistHeader(
                onSearchClick = { isSearching = true },
                title = album.album.title,
                creators = album.artists.map { CreatorUi(name = it.name, avatarUrl = it.thumbnailUrl, artistId = it.id) },
                // the description takes the stats' place, and the stats move under it
                description = description?.takeIf { it.isNotBlank() },
                stats = albumStats(
                    year = album.album.year,
                    songCount = filteredSongs.size,
                    totalSeconds = totalSeconds,
                    explicit = album.album.explicit,
                    format = format?.let { f ->
                        val codec = f.codecs.substringBefore('.').uppercase().ifBlank { null }
                        listOfNotNull(codec, (f.bitrate / 1000).takeIf { it > 0 }?.let { "$it kbps" }).joinToString(" ")
                    },
                ),
                isPlaying = isThisAlbum && isPlaying,
                onPlayClick = {
                    when {
                        isThisAlbum && isPlaying -> playerConnection.pause()
                        isThisAlbum -> playerConnection.play()
                        else -> play()
                    }
                },
                onShuffleClick = { play(shuffled = true) },
                isSaved = album.album.bookmarkedAt != null,
                onSaveClick = viewModel::toggleAlbumLike,
                onCreatorClick = { creator -> creator.artistId?.let { navigator.navigate("artist/$it") } },
            )
        }

        itemsIndexed(
            items = shownSongs,
            key = { _, song -> song.id },
            contentType = { _, _ -> "track" },
        ) { position, song ->
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
                            // the index in the whole album, which hidden songs may have shifted
                            play(startIndex = album.songs.indexOfFirst { it.id == song.id }.coerceAtLeast(0))
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
                            SongMenu(originalSong = song, navigator = navigator, onDismiss = menuState::dismiss)
                        }
                    },
                    selected = inSelectMode && song.id in selection,
                    liked = song.song.liked,
                    onLikeClick = { viewModel.toggleSongLike(song) },
                    trailing = if (inSelectMode) {
                        { PlaylistSelectTick(checked = song.id in selection) }
                    } else null,
                )
            }
        }

        // while searching only the matching songs show, as on a playlist
        if (isSearching) {
            item(key = "bottom_spacer") {
                Spacer(Modifier.height(50.dp))
            }
            return@PlaylistDetailScaffold
        }

        item(key = "end_of_line") {
            PlaylistEndOfLine(songCount = filteredSongs.size, totalSeconds = totalSeconds)
        }

        if (featuredArtists.isNotEmpty()) {
            item(key = "featured_artists") {
                FeaturedArtistsRow(
                    artists = featuredArtists,
                    onArtistClick = { navigator.navigate("artist/${it.id}") },
                    modifier = Modifier
                        .padding(top = 25.dp)
                        .revealOnAppear(key = "featured_artists", seenState = revealSeen),
                )
            }
        }

        albumShelf(
            key = "more_by_artist",
            title = if (mainArtistName != null) "More by $mainArtistName" else "More by this artist",
            albums = moreByArtist,
            onTitleClick = album.artists.firstOrNull()?.id?.let { id -> { navigator.navigate("artist/$id") } },
            navigator = navigator,
            onLongClick = { item ->
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                menuState.show {
                    YouTubeAlbumMenu(albumItem = item, navigator = navigator, onDismiss = menuState::dismiss)
                }
            },
        )
        albumShelf(
            key = "other_versions",
            title = "Other versions",
            albums = otherVersions,
            navigator = navigator,
            onLongClick = { item ->
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                menuState.show {
                    YouTubeAlbumMenu(albumItem = item, navigator = navigator, onDismiss = menuState::dismiss)
                }
            },
        )
        albumShelf(
            key = "releases_for_you",
            title = "Releases for you",
            albums = releasesForYou,
            navigator = navigator,
            onLongClick = { item ->
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                menuState.show {
                    YouTubeAlbumMenu(albumItem = item, navigator = navigator, onDismiss = menuState::dismiss)
                }
            },
        )

        item(key = "bottom_spacer") {
            Spacer(Modifier.height(50.dp))
        }
    }
}

/** "2016 · 14 songs · 52m · Explicit · OPUS 160 kbps" */
private fun albumStats(year: Int?, songCount: Int, totalSeconds: Int, explicit: Boolean, format: String?): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    return listOfNotNull(
        year?.toString(),
        "$songCount ${if (songCount == 1) "song" else "songs"}",
        (if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m").takeIf { totalSeconds > 0 },
        "Explicit".takeIf { explicit },
        format?.ifBlank { null },
    ).joinToString(" · ")
}

/** a titled row of Home's square cards: more by the artist, other versions, releases for you */
private fun LazyListScope.albumShelf(
    key: String,
    title: String,
    albums: List<AlbumItem>,
    navigator: MochyNavigator,
    onLongClick: (AlbumItem) -> Unit,
    onTitleClick: (() -> Unit)? = null,
) {
    val distinct = albums.distinctBy { it.id }
    if (distinct.isEmpty()) return
    item(key = "${key}_title") {
        PlaylistSectionTitle(
            title = title,
            onClick = onTitleClick,
            modifier = Modifier.padding(top = 28.dp, bottom = 8.dp),
        )
    }
    item(key = "${key}_row") {
        LazyRow(
            contentPadding = PaddingValues(horizontal = PlaylistInset),
            horizontalArrangement = Arrangement.spacedBy(HomeCardSpacing),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(items = distinct, key = { it.id }) { item ->
                HomeCoverCard(
                    title = item.title,
                    subtitle = listOfNotNull(
                        item.artists?.joinToString { it.name }?.ifBlank { null },
                        item.year?.toString(),
                    ).joinToString(" · ").ifBlank { null },
                    thumbnailUrl = item.thumbnail,
                    sharedElementKey = "album-${item.id}",
                    modifier = Modifier.combinedClickable(
                        interactionSource = null,
                        indication = HomeCardHighlight,
                        onClick = { navigator.navigate("album/${item.id}") },
                        onLongClick = { onLongClick(item) },
                    ),
                )
            }
        }
    }
}

@Composable
private fun AlbumRetry(onRetry: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp)
    ) {
        Text(
            text = "Couldn't load this album. Tap to try again",
            style = PlaylistStatsStyle,
            color = Color.White.copy(alpha = 0.7f),
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .clickable(onClick = onRetry)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}
