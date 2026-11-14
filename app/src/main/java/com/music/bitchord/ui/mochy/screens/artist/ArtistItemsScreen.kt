// Ported from Mochy Music (GPL-3.0): ui/screens/artist/ArtistItemsScreen.kt
// ArtistItemsScreen.kt — One shelf of an artist page opened as a full page.
//
// Port notes (see staging/phase2-detail/NEEDS.md for the full list):
// - Navigation goes through MochyNavigator; route strings are Mochy's verbatim.
//   Back is popBackStack(); back-to-main is clearDetails().
// - The screen never touches the database: data comes through the VM contract.
// - LibraryIndexRail + indexSections are already staged (phase2-library);
//   ItemsPage is already staged (phase2-search-adapter2).

package com.music.bitchord.ui.mochy.screens.artist

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.music.bitchord.ui.mochy.LocalPlayerAwareWindowInsets
import com.music.bitchord.ui.mochy.component.coverTransitionKey
import com.music.bitchord.ui.mochy.component.detail.TrackShape
import com.music.bitchord.ui.mochy.navigation.MochyNavigator
import com.music.bitchord.ui.mochy.playback.LocalPlayerConnection
import com.music.bitchord.ui.mochy.extensions.toMediaItem
import com.music.bitchord.ui.mochy.models.toMediaMetadata
import com.music.bitchord.ui.mochy.playback.queues.ListQueue
import com.music.bitchord.ui.mochy.playback.queues.YouTubeQueue
import com.music.bitchord.ui.mochy.component.HomeCardHighlight
import com.music.bitchord.ui.mochy.component.HomeVideoCard
import com.music.bitchord.ui.mochy.component.LocalMenuState
import com.music.bitchord.ui.mochy.component.SongSwipeBox
import com.music.bitchord.ui.mochy.component.containerTransformSource
import com.music.bitchord.ui.mochy.component.detail.CreatorUi
import com.music.bitchord.ui.mochy.component.detail.PlaylistDetailScaffold
import com.music.bitchord.ui.mochy.component.detail.PlaylistEndOfLine
import com.music.bitchord.ui.mochy.component.detail.PlaylistHeader
import com.music.bitchord.ui.mochy.component.detail.PlaylistInset
import com.music.bitchord.ui.mochy.component.detail.PlaylistStatsStyle
import com.music.bitchord.ui.mochy.component.detail.PlaylistTrackBones
import com.music.bitchord.ui.mochy.component.detail.PlaylistTrackRow
import com.music.bitchord.ui.mochy.component.detail.playlistCoverHeight
import com.music.bitchord.ui.mochy.component.librarySwipeAction
import com.music.bitchord.ui.mochy.component.queueSwipeAction
import com.music.bitchord.ui.mochy.component.rememberRevealSeenState
import com.music.bitchord.ui.mochy.component.revealOnAppear
import com.music.bitchord.ui.mochy.player.menu.YouTubeAlbumMenu
import com.music.bitchord.ui.mochy.player.menu.YouTubeArtistMenu
import com.music.bitchord.ui.mochy.player.menu.YouTubePlaylistMenu
import com.music.bitchord.ui.mochy.player.menu.YouTubeSongMenu
import com.music.bitchord.ui.mochy.screens.library.LibraryIndexRail
import com.music.bitchord.ui.mochy.screens.library.indexSections
import com.music.bitchord.ui.mochy.utils.ArtistPageCache
import com.music.bitchord.ui.mochy.utils.ArtistVideos
import com.music.bitchord.ui.mochy.utils.makeTimeString
import com.music.bitchord.ui.mochy.viewmodels.ArtistItemsViewModel
import com.music.bitchord.ui.mochy.models.innertube.AlbumItem
import com.music.bitchord.ui.mochy.models.innertube.ArtistItem
import com.music.bitchord.ui.mochy.models.innertube.PlaylistItem
import com.music.bitchord.ui.mochy.models.innertube.SongItem
import com.music.bitchord.ui.mochy.models.innertube.WatchEndpoint
import com.music.bitchord.ui.mochy.models.innertube.YTItem
import kotlinx.coroutines.launch

/** what a shelf's page holds, read off its items: YouTube's titles are in the account's language */
private enum class ItemsKind(val word: String) {
    Songs("songs"), Albums("albums"), Videos("videos"), Playlists("playlists"), Artists("artists")
}

private fun kindOf(items: List<YTItem>): ItemsKind? {
    val first = items.firstOrNull() ?: return null
    return when (first) {
        is SongItem -> {
            val songs = items.filterIsInstance<SongItem>()
            if (songs.count { it.album != null } * 2 >= songs.size) ItemsKind.Songs else ItemsKind.Videos
        }
        is AlbumItem -> ItemsKind.Albums
        is PlaylistItem -> ItemsKind.Playlists
        is ArtistItem -> ItemsKind.Artists
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ArtistItemsScreen(
    navigator: MochyNavigator,
    @Suppress("UNUSED_PARAMETER") scrollBehavior: TopAppBarScrollBehavior,
    artistId: String?,
    browseId: String?,
    params: String?,
    viewModel: ArtistItemsViewModel = hiltViewModel(),
) {
    // Coordinator: route args rule — the host passes the route args; the VM loads on them.
    LaunchedEffect(artistId, browseId, params) { viewModel.load(artistId, browseId, params) }
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val queueTitle by playerConnection.queueTitle.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    val pageTitle by viewModel.title.collectAsState()
    val itemsPage by viewModel.itemsPage.collectAsState()
    val loadFailed by viewModel.loadFailed.collectAsState()
    val items = remember(itemsPage) { itemsPage?.items.orEmpty().distinctBy { it.id } }
    val kind = remember(items) { kindOf(items) }

    val (bannerName, bannerUrl) = rememberArtistBanner(viewModel.artistId)
    val artistName = bannerName.orEmpty()
    // the videos page wears a video's own frame instead of the artist's banner
    val coverUrl = if (kind == ItemsKind.Videos) {
        items.firstOrNull()?.id?.let { "https://i.ytimg.com/vi/$it/hq720.jpg" }
    } else {
        bannerUrl
    }

    // "Albums" -> "twenty one pilots albums"; a page titled with the artist's own name says what it holds
    val heading = when {
        kind == ItemsKind.Songs -> if (artistName.isNotBlank()) "Top $artistName songs" else pageTitle
        artistName.isBlank() -> pageTitle
        pageTitle.isBlank() || pageTitle.equals(artistName, ignoreCase = true) -> "$artistName ${kind?.word.orEmpty()}"
        else -> "$artistName ${pageTitle.replaceFirstChar { it.lowercase() }}"
    }

    var isGrid by rememberSaveable { mutableStateOf<Boolean?>(null) }
    val gridMode = isGrid ?: (kind == ItemsKind.Albums || kind == ItemsKind.Playlists || kind == ItemsKind.Artists)

    val lazyListState = rememberLazyListState()
    val revealSeen = rememberRevealSeenState()
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp

    LaunchedEffect(lazyListState) {
        snapshotFlow { lazyListState.layoutInfo.visibleItemsInfo.any { it.key == "loading_more" } }
            .collect { if (it) viewModel.loadMore() }
    }

    val songs = remember(items) { items.filterIsInstance<SongItem>() }
    val isThisQueue = heading.isNotBlank() && queueTitle == heading

    fun playSongs(start: SongItem? = null, shuffled: Boolean = false) {
        if (songs.isEmpty()) return
        val list = if (shuffled) songs.shuffled() else songs
        playerConnection.playQueue(
            ListQueue(
                title = heading,
                items = list.map { it.toMediaItem() },
                startIndex = start?.let { list.indexOf(it).coerceAtLeast(0) } ?: 0,
            )
        )
    }

    fun showMenu(item: YTItem) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        menuState.show {
            when (item) {
                is SongItem -> YouTubeSongMenu(song = item, navigator = navigator, onDismiss = menuState::dismiss)
                is AlbumItem -> YouTubeAlbumMenu(albumItem = item, navigator = navigator, onDismiss = menuState::dismiss)
                is ArtistItem -> YouTubeArtistMenu(artist = item, onDismiss = menuState::dismiss)
                is PlaylistItem -> YouTubePlaylistMenu(
                    playlist = item,
                    coroutineScope = coroutineScope,
                    onDismiss = menuState::dismiss,
                    onImportedPlaylist = { navigator.navigate("local_playlist/$it") },
                )
            }
        }
    }

    fun open(item: YTItem) {
        when (item) {
            is SongItem -> if (item.id == mediaMetadata?.id) {
                playerConnection.togglePlayPause()
            } else if (kind == ItemsKind.Songs) {
                playSongs(start = item)
            } else {
                playerConnection.playQueue(YouTubeQueue(item.endpoint ?: WatchEndpoint(videoId = item.id), item.toMediaMetadata()))
            }
            is AlbumItem -> navigator.navigate("album/${item.id}")
            is ArtistItem -> navigator.navigate("artist/${item.id}")
            is PlaylistItem -> navigator.navigate("online_playlist/${item.id}")
        }
    }

    // the A-Z list: rows in index order, and where each letter starts for the rail
    val indexed = remember(items, gridMode, kind) {
        if (!gridMode && (kind == ItemsKind.Albums || kind == ItemsKind.Playlists)) indexSections(items) { it.title } else null
    }
    val rows = remember(indexed, items) { indexed?.flatMap { it.second } ?: items }
    val sectionStarts = remember(indexed) {
        buildMap {
            var position = 0
            indexed?.forEach { (section, entries) ->
                // +1 for the header item above the rows
                put(section.key, position + 1)
                position += entries.size
            }
        }
    }

    PlaylistDetailScaffold(
        sharedElementKey = null,
        coverUrl = coverUrl,
        lazyListState = lazyListState,
        onBackClick = { navigator.popBackStack() },
        onBackLongClick = { navigator.clearDetails() },
        contentPadding = LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Bottom).asPaddingValues(),
        overlay = {
            if (indexed != null && rows.size >= 12) {
                LibraryIndexRail(
                    sections = indexed.map { it.first },
                    onSectionSelected = { key ->
                        sectionStarts[key]?.let { index -> coroutineScope.launch { lazyListState.animateScrollToItem(index) } }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        },
    ) {
        if (itemsPage == null) {
            item(key = "loading_header") {
                Spacer(Modifier.height(playlistCoverHeight() * 0.975f))
            }
            item(key = "loading_rows") {
                if (loadFailed) ItemsRetry(onRetry = viewModel::retry) else PlaylistTrackBones()
            }
            return@PlaylistDetailScaffold
        }

        item(key = "header", contentType = "header") {
            if (kind == ItemsKind.Songs) {
                PlaylistHeader(
                    title = heading,
                    creators = listOfNotNull(
                        artistName.takeIf { it.isNotBlank() }?.let {
                            CreatorUi(name = it, avatarUrl = viewModel.artistId?.let { id -> ArtistPageCache.header(id)?.avatarUrl }, artistId = viewModel.artistId)
                        }
                    ),
                    stats = "${songs.size} ${if (songs.size == 1) "song" else "songs"}",
                    isPlaying = isThisQueue && isPlaying,
                    onPlayClick = { if (isThisQueue) playerConnection.togglePlayPause() else playSongs() },
                    onShuffleClick = { playSongs(shuffled = true) },
                    onCreatorClick = { creator -> creator.artistId?.let { navigator.navigate("artist/$it") } },
                )
            } else {
                ArtistSubpageHeader(
                    title = heading,
                    subtitle = "${items.size}${if (itemsPage?.continuation != null) "+" else ""} ${kind?.word.orEmpty()}",
                    // the artists' page is a grid of faces only
                    isGrid = if (kind == ItemsKind.Artists) null else gridMode,
                    onGridChange = { isGrid = it },
                )
            }
        }

        when {
            kind == ItemsKind.Songs -> itemsIndexed(rows, key = { _, it -> it.id }, contentType = { _, _ -> "track" }) { position, item ->
                val song = item as SongItem
                SongSwipeBox(
                    modifier = Modifier.animateItem(),
                    library = { librarySwipeAction(song) },
                    queue = { queueSwipeAction { song.toMediaItem() } },
                ) {
                    val active = song.id == mediaMetadata?.id
                    PlaylistTrackRow(
                        thumbnailUrl = song.thumbnail,
                        songId = song.id,
                        title = song.title,
                        subtitle = listOfNotNull(
                            song.album?.name ?: song.artists.joinToString { it.name },
                            song.duration?.let { makeTimeString(it * 1000L) },
                        ).joinToString(" • "),
                        isActive = active,
                        isPlaying = active && isPlaying,
                        modifier = Modifier.revealOnAppear(key = "song_${song.id}", seenState = revealSeen, delayMillis = minOf(position, 8) * 28),
                        onClick = { open(song) },
                        onLongClick = { showMenu(song) },
                        onMenuClick = { showMenu(song) },
                    )
                }
            }

            kind == ItemsKind.Videos && !gridMode -> items(rows, key = { it.id }, contentType = { "video_card" }) { item ->
                val video = item as SongItem
                val tidy = remember(video.id, artistName) { ArtistVideos.homeVideo(video, artistName) }
                val active = video.id == mediaMetadata?.id
                HomeVideoCard(
                    title = tidy.title,
                    subtitle = listOfNotNull(
                        tidy.subtitle.ifBlank { null },
                        video.duration?.let { makeTimeString(it * 1000L) },
                    ).joinToString(" · "),
                    videoId = video.id,
                    fallbackThumbnailUrl = video.thumbnail,
                    width = screenWidth - PlaylistInset * 2,
                    isActive = active,
                    isPlaying = active && isPlaying,
                    modifier = Modifier
                        .padding(horizontal = PlaylistInset, vertical = 7.dp)
                        .animateItem()
                        .revealOnAppear(key = "video_${video.id}", seenState = revealSeen)
                        .combinedClickable(
                            interactionSource = null,
                            indication = HomeCardHighlight,
                            onClick = { open(video) },
                            onLongClick = { showMenu(video) },
                        ),
                )
            }

            gridMode -> items(rows.chunked(2), key = { pair -> pair.joinToString("|") { it.id } }, contentType = { "grid_row" }) { pair ->
                SubpageGridRow(
                    items = pair,
                    modifier = Modifier
                        .padding(vertical = 6.dp)
                        .animateItem()
                        .revealOnAppear(key = "grid_${pair.first().id}", seenState = revealSeen),
                ) { item ->
                    when (item) {
                        is SongItem -> {
                            val tidy = remember(item.id, artistName) { ArtistVideos.homeVideo(item, artistName) }
                            val active = item.id == mediaMetadata?.id
                            SubpageVideoCell(
                                title = tidy.title,
                                subtitle = item.duration?.let { makeTimeString(it * 1000L) },
                                videoId = item.id,
                                fallbackThumbnailUrl = item.thumbnail,
                                isActive = active,
                                isPlaying = active && isPlaying,
                                onClick = { open(item) },
                                onLongClick = { showMenu(item) },
                            )
                        }
                        else -> {
                            val active = item is AlbumItem && item.id == mediaMetadata?.album?.id
                            SubpageSquareCell(
                                title = item.title,
                                subtitle = cellSubtitle(item),
                                thumbnailUrl = item.thumbnail,
                                round = item is ArtistItem,
                                sharedElementKey = coverTransitionKey(item),
                                isActive = active,
                                isPlaying = active && isPlaying,
                                onClick = { open(item) },
                                onLongClick = { showMenu(item) },
                            )
                        }
                    }
                }
            }

            else -> itemsIndexed(rows, key = { _, it -> it.id }, contentType = { _, _ -> "row" }) { position, item ->
                val active = item is AlbumItem && item.id == mediaMetadata?.album?.id
                PlaylistTrackRow(
                    thumbnailUrl = item.thumbnail,
                    songId = (item as? SongItem)?.id,
                    title = item.title,
                    subtitle = cellSubtitle(item).orEmpty(),
                    isActive = active,
                    isPlaying = active && isPlaying,
                    coverShape = if (item is ArtistItem) CircleShape else TrackShape,
                    coverModifier = Modifier.containerTransformSource(
                        key = coverTransitionKey(item),
                        cornerRadius = 9.dp,
                        coverUrl = item.thumbnail,
                    ),
                    modifier = Modifier
                        .animateItem()
                        .revealOnAppear(key = "row_${item.id}", seenState = revealSeen, delayMillis = minOf(position, 8) * 28),
                    onClick = { open(item) },
                    onLongClick = { showMenu(item) },
                    onMenuClick = { showMenu(item) },
                )
            }
        }

        if (itemsPage?.continuation != null) {
            item(key = "loading_more") {
                PlaylistTrackBones(rows = 2)
            }
        } else if (kind == ItemsKind.Songs && songs.isNotEmpty()) {
            item(key = "end_of_line") {
                PlaylistEndOfLine(songCount = songs.size, totalSeconds = songs.sumOf { it.duration ?: 0 })
            }
        }

        item(key = "bottom_spacer") {
            Spacer(Modifier.height(50.dp))
        }
    }
}

private fun cellSubtitle(item: YTItem): String? = when (item) {
    is AlbumItem -> item.year?.toString()
    is PlaylistItem -> listOfNotNull(item.author?.name, item.songCountText).joinToString(" · ").ifBlank { null }
    is SongItem -> item.artists.joinToString { it.name }.ifBlank { null }
    is ArtistItem -> null
}

@Composable
private fun ItemsRetry(onRetry: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp)
    ) {
        Text(
            text = "Couldn't load this page. Tap to try again",
            style = PlaylistStatsStyle,
            color = Color.White.copy(alpha = 0.7f),
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .clickable(onClick = onRetry)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}
