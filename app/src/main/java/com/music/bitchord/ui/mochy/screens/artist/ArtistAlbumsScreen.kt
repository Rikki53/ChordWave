// Ported from Mochy Music (GPL-3.0): ui/screens/artist/ArtistAlbumsScreen.kt
// ArtistAlbumsScreen.kt — The artist's albums in your library: grid or A-Z list.
//
// Port notes (see staging/phase2-detail/NEEDS.md for the full list):
// - Navigation goes through MochyNavigator; route strings are Mochy's verbatim.
//   Back is popBackStack(); back-to-main is clearDetails().
// - The screen never touches the database: data comes through the VM contract.
// - LibraryIndexRail + indexSections are already staged (phase2-library).

package com.music.bitchord.ui.mochy.screens.artist

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.music.bitchord.ui.mochy.LocalPlayerAwareWindowInsets
import com.music.bitchord.ui.mochy.navigation.MochyNavigator
import com.music.bitchord.ui.mochy.playback.LocalPlayerConnection
import com.music.bitchord.ui.mochy.models.Album
import com.music.bitchord.ui.mochy.component.LocalMenuState
import com.music.bitchord.ui.mochy.component.containerTransformSource
import com.music.bitchord.ui.mochy.component.detail.PlaylistDetailScaffold
import com.music.bitchord.ui.mochy.component.detail.PlaylistTrackRow
import com.music.bitchord.ui.mochy.component.rememberRevealSeenState
import com.music.bitchord.ui.mochy.component.revealOnAppear
import com.music.bitchord.ui.mochy.player.menu.AlbumMenu
import com.music.bitchord.ui.mochy.screens.library.LibraryIndexRail
import com.music.bitchord.ui.mochy.screens.library.indexSections
import com.music.bitchord.ui.mochy.viewmodels.ArtistAlbumsViewModel
import kotlinx.coroutines.launch

/** the artist's albums in your library: big squares two up, or an A-Z list with the rail */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistAlbumsScreen(
    navigator: MochyNavigator,
    @Suppress("UNUSED_PARAMETER") scrollBehavior: TopAppBarScrollBehavior,
    artistId: String,
    viewModel: ArtistAlbumsViewModel = hiltViewModel(),
) {
    // Coordinator: route args rule — the host passes artistId; the VM loads on it.
    LaunchedEffect(artistId) { viewModel.load(artistId) }
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    val artist by viewModel.artist.collectAsState()
    val albums by viewModel.albums.collectAsState()
    val lazyListState = rememberLazyListState()
    val revealSeen = rememberRevealSeenState()
    var isGrid by rememberSaveable { mutableStateOf(true) }

    val (cachedName, bannerUrl) = rememberArtistBanner(artist?.artist?.id)
    val name = artist?.artist?.name ?: cachedName.orEmpty()

    val indexed = remember(albums, isGrid) { if (isGrid) null else indexSections(albums) { it.album.title } }
    val rows = remember(indexed, albums) { indexed?.flatMap { it.second } ?: albums }
    val sectionStarts = remember(indexed) {
        buildMap {
            var position = 0
            indexed?.forEach { (section, entries) ->
                put(section.key, position + 1)
                position += entries.size
            }
        }
    }

    fun showMenu(album: Album) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        menuState.show {
            AlbumMenu(originalAlbum = album, navigator = navigator, onDismiss = menuState::dismiss)
        }
    }

    PlaylistDetailScaffold(
        sharedElementKey = null,
        coverUrl = bannerUrl ?: albums.firstOrNull()?.album?.thumbnailUrl,
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
        item(key = "header", contentType = "header") {
            ArtistSubpageHeader(
                title = if (name.isNotBlank()) "$name albums" else "Albums",
                subtitle = "${albums.size} in your library",
                isGrid = isGrid,
                onGridChange = { isGrid = it },
            )
        }

        if (isGrid) {
            items(rows.chunked(2), key = { pair -> pair.joinToString("|") { it.id } }, contentType = { "grid_row" }) { pair ->
                SubpageGridRow(
                    items = pair,
                    modifier = Modifier
                        .padding(vertical = 6.dp)
                        .animateItem()
                        .revealOnAppear(key = "grid_${pair.first().id}", seenState = revealSeen),
                ) { album ->
                    val active = album.id == mediaMetadata?.album?.id
                    SubpageSquareCell(
                        title = album.album.title,
                        subtitle = album.album.year?.toString(),
                        thumbnailUrl = album.album.thumbnailUrl,
                        sharedElementKey = "album-${album.id}",
                        isActive = active,
                        isPlaying = active && isPlaying,
                        onClick = { navigator.navigate("album/${album.id}") },
                        onLongClick = { showMenu(album) },
                    )
                }
            }
        } else {
            itemsIndexed(rows, key = { _, album -> album.id }, contentType = { _, _ -> "row" }) { position, album ->
                val active = album.id == mediaMetadata?.album?.id
                PlaylistTrackRow(
                    thumbnailUrl = album.album.thumbnailUrl,
                    title = album.album.title,
                    subtitle = listOfNotNull(album.album.year?.toString(), "${album.album.songCount} songs").joinToString(" • "),
                    isActive = active,
                    isPlaying = active && isPlaying,
                    coverModifier = Modifier.containerTransformSource(
                        key = "album-${album.id}",
                        cornerRadius = 9.dp,
                        coverUrl = album.album.thumbnailUrl,
                    ),
                    modifier = Modifier
                        .animateItem()
                        .revealOnAppear(key = "row_${album.id}", seenState = revealSeen, delayMillis = minOf(position, 8) * 28),
                    onClick = { navigator.navigate("album/${album.id}") },
                    onLongClick = { showMenu(album) },
                    onMenuClick = { showMenu(album) },
                )
            }
        }

        item(key = "bottom_spacer") {
            Spacer(Modifier.height(50.dp))
        }
    }
}
