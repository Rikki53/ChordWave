// Ported from Mochy Music (GPL-3.0): ui/screens/SectionDetailScreen.kt
// SectionDetailScreen.kt — A Home section opened as a full page (speed dial, quick picks, ...).
//
// Port notes (see staging/phase2-detail/NEEDS.md for the full list):
// - Navigation goes through MochyNavigator; route strings are Mochy's verbatim.
//   Back is popBackStack(); back-to-main is clearDetails().
// - homeViewModel is the staged HomeViewModel, passed in by the host (Mochy
//   shared Home's back-stack entry; NEEDS.md §(d).2).
// - GeneratedPlaylistPage + generatedStats are already staged
//   (phase2-playlist). PlaylistMenu is NEEDS.md C9 and takes navigator
//   instead of navController.

package com.music.bitchord.ui.mochy.screens

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.music.bitchord.ui.mochy.LocalPlayerAwareWindowInsets
import com.music.bitchord.ui.mochy.component.detail.TrackShape
import com.music.bitchord.ui.mochy.models.LocalItem
import com.music.bitchord.ui.mochy.navigation.MochyNavigator
import com.music.bitchord.ui.mochy.playback.LocalPlayerConnection
import com.music.bitchord.R
import com.music.bitchord.ui.mochy.models.Album
import com.music.bitchord.ui.mochy.models.Artist
import com.music.bitchord.ui.mochy.models.Playlist
import com.music.bitchord.ui.mochy.models.Song
import com.music.bitchord.ui.mochy.extensions.toMediaItem
import com.music.bitchord.ui.mochy.models.toMediaMetadata
import com.music.bitchord.ui.mochy.playback.queues.ListQueue
import com.music.bitchord.ui.mochy.component.LocalMenuState
import com.music.bitchord.ui.mochy.component.SongSwipeBox
import com.music.bitchord.ui.mochy.component.containerTransformSource
import com.music.bitchord.ui.mochy.component.detail.FeaturedArtistUi
import com.music.bitchord.ui.mochy.component.detail.FeaturedArtistsRow
import com.music.bitchord.ui.mochy.component.detail.PlaylistEndOfLine
import com.music.bitchord.ui.mochy.component.detail.PlaylistTrackRow
import com.music.bitchord.ui.mochy.component.librarySwipeAction
import com.music.bitchord.ui.mochy.component.queueSwipeAction
import com.music.bitchord.ui.mochy.component.rememberRevealSeenState
import com.music.bitchord.ui.mochy.component.revealOnAppear
import com.music.bitchord.ui.mochy.player.menu.AlbumMenu
import com.music.bitchord.ui.mochy.player.menu.ArtistMenu
import com.music.bitchord.ui.mochy.player.menu.PlaylistMenu
import com.music.bitchord.ui.mochy.player.menu.SongMenu
import com.music.bitchord.ui.mochy.player.menu.YouTubeAlbumMenu
import com.music.bitchord.ui.mochy.player.menu.YouTubeArtistMenu
import com.music.bitchord.ui.mochy.player.menu.YouTubePlaylistMenu
import com.music.bitchord.ui.mochy.player.menu.YouTubeSongMenu
import com.music.bitchord.ui.mochy.screens.playlist.GeneratedNoResults
import com.music.bitchord.ui.mochy.screens.playlist.GeneratedPlaylistPage
import com.music.bitchord.ui.mochy.screens.playlist.generatedStats
import com.music.bitchord.ui.mochy.utils.makeTimeString
import com.music.bitchord.ui.mochy.viewmodels.HomeViewModel
import com.music.bitchord.ui.mochy.models.innertube.AlbumItem
import com.music.bitchord.ui.mochy.models.innertube.ArtistItem
import com.music.bitchord.ui.mochy.models.innertube.PlaylistItem
import com.music.bitchord.ui.mochy.models.innertube.SongItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SectionDetailScreen(
    navigator: MochyNavigator,
    sectionId: String,
    homeViewModel: HomeViewModel
) {
    val speedDialItems by homeViewModel.speedDialItems.collectAsState()
    val quickPicks by homeViewModel.quickPicks.collectAsState()
    val forgottenFavorites by homeViewModel.forgottenFavorites.collectAsState()
    val keepListening by homeViewModel.keepListening.collectAsState()
    val accountName by homeViewModel.accountName.collectAsState()
    val recentlyPlayed by homeViewModel.recentlyPlayed.collectAsState()
    val mostPlayedSongsForHome by homeViewModel.mostPlayedSongsForHome.collectAsState()
    val communityPlaylists by homeViewModel.communityPlaylists.collectAsState()
    val allTimeHits by homeViewModel.allTimeHits.collectAsState()
    val liveShows by homeViewModel.liveRow.collectAsState()
    val musicVideos by homeViewModel.musicVideoRow.collectAsState()

    val playerConnection = LocalPlayerConnection.current ?: return
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val queueTitle by playerConnection.queueTitle.collectAsState()

    val title = when (sectionId) {
        "speed_dial" -> stringResource(R.string.speed_dial)
        "quick_picks" -> stringResource(R.string.quick_picks)
        "forgotten_favorites" -> stringResource(R.string.dont_forget_these_songs)
        "history" -> stringResource(R.string.home_history)
        "recently_played" -> stringResource(R.string.recently_played)
        "most_played" -> stringResource(R.string.home_most_played)
        "from_the_community" -> stringResource(R.string.from_the_community)
        "all_time_hits" -> stringResource(R.string.all_time_hits)
        "live_shows" -> stringResource(R.string.live_shows)
        "music_videos" -> stringResource(R.string.music_videos_for_you)
        else -> ""
    }

    val items = remember(
        sectionId, speedDialItems, quickPicks, forgottenFavorites, keepListening,
        recentlyPlayed, mostPlayedSongsForHome, communityPlaylists,
        allTimeHits, liveShows, musicVideos,
    ) {
        when (sectionId) {
            "speed_dial" -> speedDialItems.filterIsInstance<SongItem>()
            "quick_picks" -> quickPicks?.filterIsInstance<Song>() ?: emptyList()
            "forgotten_favorites" -> forgottenFavorites ?: emptyList()
            "history" -> keepListening?.filterIsInstance<Song>() ?: emptyList()
            "recently_played" -> recentlyPlayed ?: emptyList()
            "most_played" -> mostPlayedSongsForHome ?: emptyList()
            "from_the_community" -> communityPlaylists?.map { it.playlist } ?: emptyList()
            "all_time_hits" -> allTimeHits ?: emptyList()
            "live_shows" -> liveShows?.map { it.item } ?: emptyList()
            "music_videos" -> musicVideos?.map { it.item } ?: emptyList()
            else -> emptyList()
        }
    }

    var query by remember { mutableStateOf(TextFieldValue()) }
    val lazyListState = rememberLazyListState()
    val revealSeen = rememberRevealSeenState()

    // the playable part of the section, in order, as one queue: local songs or YouTube songs
    val localSongs = remember(items) { items.filterIsInstance<Song>() }
    val ytSongs = remember(items) { items.filterIsInstance<SongItem>() }
    val songCount = localSongs.size + ytSongs.size
    val totalSeconds = remember(items) {
        localSongs.sumOf { it.song.duration } + ytSongs.sumOf { it.duration ?: 0 }
    }
    val featuredArtists = remember(items) {
        val ids = LinkedHashMap<String, Pair<String, Int>>()
        localSongs.flatMap { it.artists }.forEach { a -> ids[a.id] = a.name to ((ids[a.id]?.second ?: 0) + 1) }
        ytSongs.flatMap { it.artists }.forEach { a ->
            val id = a.id ?: return@forEach
            ids[id] = a.name to ((ids[id]?.second ?: 0) + 1)
        }
        ids.entries.sortedByDescending { it.value.second }.take(10).map { FeaturedArtistUi(it.key, it.value.first) }
    }
    val visible = remember(items, query.text) {
        val q = query.text.trim()
        if (q.isEmpty()) items else items.filter { item ->
            val (name, sub) = rowText(item)
            name.contains(q, ignoreCase = true) || sub.contains(q, ignoreCase = true)
        }
    }
    val coverUrl = remember(items) { items.firstNotNullOfOrNull { rowThumbnail(it) } }
    val isThisQueue = queueTitle == title

    fun playAll(startItem: Any? = null, shuffled: Boolean = false) {
        if (localSongs.isNotEmpty()) {
            val list = if (shuffled) localSongs.shuffled() else localSongs
            playerConnection.playQueue(
                ListQueue(
                    title = title,
                    items = list.map { it.toMediaItem() },
                    startIndex = (startItem as? Song)?.let { list.indexOf(it).coerceAtLeast(0) } ?: 0,
                )
            )
        } else if (ytSongs.isNotEmpty()) {
            val list = if (shuffled) ytSongs.shuffled() else ytSongs
            playerConnection.playQueue(
                ListQueue(
                    title = title,
                    items = list.map { it.toMediaMetadata().toMediaItem() },
                    startIndex = (startItem as? SongItem)?.let { list.indexOf(it).coerceAtLeast(0) } ?: 0,
                )
            )
        }
    }

    fun open(item: Any) {
        when (item) {
            is Song, is SongItem -> {
                val id = if (item is Song) item.id else (item as SongItem).id
                if (id == mediaMetadata?.id) playerConnection.togglePlayPause() else playAll(item)
            }
            is Album -> navigator.navigate("album/${item.id}")
            is Artist -> navigator.navigate("artist/${item.id}")
            is Playlist -> navigator.navigate(if (item.id == "liked") "auto_playlist/liked" else "local_playlist/${item.id}")
            is AlbumItem -> navigator.navigate("album/${item.id}")
            is ArtistItem -> navigator.navigate("artist/${item.id}")
            is PlaylistItem -> navigator.navigate("online_playlist/${item.id}")
        }
    }

    fun showMenu(item: Any) {
        menuState.show {
            when (item) {
                is Song -> SongMenu(originalSong = item, navigator = navigator, onDismiss = menuState::dismiss)
                is SongItem -> YouTubeSongMenu(song = item, navigator = navigator, onDismiss = menuState::dismiss)
                is Album -> AlbumMenu(originalAlbum = item, navigator = navigator, onDismiss = menuState::dismiss)
                is Artist -> ArtistMenu(originalArtist = item, coroutineScope = coroutineScope, onDismiss = menuState::dismiss)
                is Playlist -> PlaylistMenu(playlist = item, coroutineScope = coroutineScope, onDismiss = menuState::dismiss, navigator = navigator)
                is AlbumItem -> YouTubeAlbumMenu(albumItem = item, navigator = navigator, onDismiss = menuState::dismiss)
                is ArtistItem -> YouTubeArtistMenu(artist = item, onDismiss = menuState::dismiss)
                is PlaylistItem -> YouTubePlaylistMenu(
                    playlist = item,
                    coroutineScope = coroutineScope,
                    onDismiss = menuState::dismiss,
                    onImportedPlaylist = { navigator.navigate("local_playlist/$it") },
                )
                else -> {}
            }
        }
    }

    GeneratedPlaylistPage(
        title = title,
        coverUrl = coverUrl,
        stats = if (songCount > 0) generatedStats(songCount, totalSeconds) else "${items.size} items",
        lazyListState = lazyListState,
        contentPadding = LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Bottom)
            .union(WindowInsets.ime).asPaddingValues(),
        isPlaying = isThisQueue && isPlaying,
        onPlayClick = { if (isThisQueue) playerConnection.togglePlayPause() else playAll() },
        onShuffleClick = { playAll(shuffled = true) },
        query = query,
        onQueryChange = { query = it },
        searchPlaceholder = "Search in $title",
        onBackClick = { navigator.popBackStack() },
        onBackLongClick = { navigator.clearDetails() },
        creator = accountName,
    ) {
        if (visible.isEmpty() && query.text.isNotBlank()) {
            item(key = "no_results") { GeneratedNoResults(query.text) }
        }

        itemsIndexed(
            items = visible,
            key = { index, item -> "${rowKey(item)}_$index" },
            contentType = { _, item -> if (item is Song || item is SongItem) "track" else "entity" },
        ) { position, item ->
            val (name, sub) = rowText(item)
            val id = rowKey(item)
            val row: @Composable () -> Unit = {
                val active = id == mediaMetadata?.id || (item is AlbumItem && item.id == mediaMetadata?.album?.id)
                PlaylistTrackRow(
                    thumbnailUrl = rowThumbnail(item),
                    songId = id.takeIf { item is Song || item is SongItem },
                    title = name,
                    subtitle = sub,
                    isActive = active,
                    isPlaying = active && isPlaying,
                    coverShape = if (item is ArtistItem || item is Artist) CircleShape else TrackShape,
                    // albums and playlists open growing out of their cover, as from Home
                    coverModifier = Modifier.containerTransformSource(
                        key = when (item) {
                            is Album -> "album-${item.id}"
                            is Playlist -> "playlist-${item.id}"
                            is AlbumItem -> "album-${item.id}"
                            is PlaylistItem -> "playlist-${item.id}"
                            else -> null
                        },
                        cornerRadius = 9.dp,
                        coverUrl = rowThumbnail(item),
                    ),
                    modifier = Modifier.revealOnAppear(
                        key = "row_$id",
                        seenState = revealSeen,
                        delayMillis = minOf(position, 8) * 28,
                    ),
                    onClick = { open(item) },
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        showMenu(item)
                    },
                    onMenuClick = { showMenu(item) },
                )
            }
            // songs slide like everywhere else: into the library or onto the queue, as set in Settings > Gesture
            when (item) {
                is Song -> SongSwipeBox(
                    modifier = Modifier.animateItem(),
                    library = { librarySwipeAction(item.song) },
                    queue = { queueSwipeAction { item.toMediaItem() } },
                ) { row() }
                is SongItem -> SongSwipeBox(
                    modifier = Modifier.animateItem(),
                    library = { librarySwipeAction(item) },
                    queue = { queueSwipeAction { item.toMediaMetadata().toMediaItem() } },
                ) { row() }
                else -> androidx.compose.foundation.layout.Box(modifier = Modifier.animateItem()) { row() }
            }
        }

        if (query.text.isBlank() && items.isNotEmpty()) {
            item(key = "end_of_line") {
                PlaylistEndOfLine(songCount = if (songCount > 0) songCount else items.size, totalSeconds = totalSeconds)
            }
        }

        if (query.text.isBlank() && featuredArtists.isNotEmpty() && sectionId != "history") {
            item(key = "featured_artists") {
                FeaturedArtistsRow(
                    artists = featuredArtists,
                    onArtistClick = { navigator.navigate("artist/${it.id}") },
                    modifier = Modifier.padding(top = 25.dp),
                )
            }
        }

        item(key = "bottom_spacer") {
            Spacer(Modifier.height(50.dp))
        }
    }
}

private fun rowKey(item: Any): String = when (item) {
    is LocalItem -> item.id
    is SongItem -> item.id
    is AlbumItem -> item.id
    is ArtistItem -> item.id
    is PlaylistItem -> item.id
    else -> item.hashCode().toString()
}

private fun rowThumbnail(item: Any): String? = when (item) {
    is Song -> item.song.thumbnailUrl
    is SongItem -> item.thumbnail
    is Album -> item.album.thumbnailUrl
    // a library playlist's own thumbnailUrl is always null; its covers live in thumbnails
    is Playlist -> item.thumbnails.firstOrNull()
    is LocalItem -> item.thumbnailUrl
    is AlbumItem -> item.thumbnail
    is ArtistItem -> item.thumbnail
    is PlaylistItem -> item.thumbnail
    else -> null
}?.takeIf { it.isNotEmpty() }

/** title and the line under it */
private fun rowText(item: Any): Pair<String, String> = when (item) {
    is Song -> item.song.title to "${item.artists.joinToString { it.name }} • ${makeTimeString(item.song.duration * 1000L)}"
    is SongItem -> item.title to listOfNotNull(
        item.artists.joinToString { it.name }.ifBlank { null },
        item.duration?.let { makeTimeString(it * 1000L) },
    ).joinToString(" • ")
    is Album -> item.album.title to listOfNotNull(
        item.artists.joinToString { it.name }.ifBlank { null },
        item.album.year?.toString(),
    ).joinToString(" • ")
    is Playlist -> item.playlist.name to "${item.songCount} songs"
    is Artist -> item.title to "Artist"
    is AlbumItem -> item.title to listOfNotNull(
        item.artists?.joinToString { it.name }?.ifBlank { null },
        item.year?.toString(),
    ).joinToString(" • ")
    is ArtistItem -> item.title to "Artist"
    is PlaylistItem -> item.title to listOfNotNull(item.author?.name, item.songCountText).joinToString(" • ")
    else -> "" to ""
}

fun Long.formatAsDuration(): String {
    val seconds = this
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    return if (h > 0) String.format("%dh %02dm", h, m) else String.format("%dm", m)
}
