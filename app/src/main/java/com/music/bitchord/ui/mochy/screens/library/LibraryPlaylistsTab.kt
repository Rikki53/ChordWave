// The Playlists tab and the Playlist page's own head (batch 34): Classic's
// three entries — Liked music, New playlist, Import from link — always on
// screen, then the listener's playlists. Before this the Playlist page
// could only say "Nothing to show here" to anyone who hadn't made one yet.

package com.music.bitchord.ui.mochy.screens.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.bitchord.R
import com.music.bitchord.ui.mochy.component.LikedCover
import com.music.bitchord.ui.mochy.component.RevealSeenState
import com.music.bitchord.ui.mochy.component.revealOnAppear
import com.music.bitchord.ui.mochy.models.Playlist
import com.music.bitchord.ui.mochy.models.Song
import com.music.bitchord.ui.mochy.navigation.MochyNavigator
import com.music.bitchord.ui.mochy.screens.search.SearchColors

/**
 * Liked music, New playlist and Import from link — the three entries
 * Classic's Playlist page opens with, in Modern's own cards. Shown whether
 * or not the listener has made a playlist yet.
 */
@Composable
fun PlaylistActionCards(
    likedSongs: List<Song>,
    navigator: MochyNavigator,
    onNewPlaylist: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        LibraryWideCard(
            title = stringResource(R.string.liked),
            count = likedSongs.size,
            covers = likedSongs.take(4).map { it.song.thumbnailUrl },
            onClick = { navigator.navigate("auto_playlist/liked") },
            sharedElementKey = "playlist-liked",
            art = { LikedCover(it) },
            modifier = Modifier.padding(horizontal = LibraryHorizontalPadding),
        )
        Spacer(Modifier.height(10.dp))
        LibraryCardGrid(
            entries = listOf(
                LibraryCardEntry(
                    title = stringResource(R.string.new_playlist),
                    count = 0,
                    covers = emptyList(),
                    onClick = onNewPlaylist,
                    icon = {
                        Icon(
                            painter = painterResource(R.drawable.add),
                            contentDescription = null,
                            tint = SearchColors.Primary,
                        )
                    },
                ),
                LibraryCardEntry(
                    title = stringResource(R.string.import_from_link),
                    count = 0,
                    covers = emptyList(),
                    onClick = { navigator.navigate("import_providers") },
                    icon = {
                        Icon(
                            painter = painterResource(R.drawable.link),
                            contentDescription = null,
                            tint = SearchColors.Primary,
                        )
                    },
                ),
            ),
        )
    }
}

/**
 * The Playlists tab: the three entries above, then every playlist the
 * listener has. Cards and rows reveal on appear like Radio's (batch 24),
 * and the tab scrolls under LibraryScaffold's collapsing header, so the
 * title and search box behave exactly as on Home (batch 34).
 */
fun LazyListScope.libraryPlaylistsTab(
    playlists: List<Playlist>,
    likedSongs: List<Song>,
    navigator: MochyNavigator,
    onNewPlaylist: () -> Unit,
    onPlaylistMenu: (Playlist) -> Unit,
    revealSeen: RevealSeenState,
) {
    item(key = "playlists_actions") {
        PlaylistActionCards(
            likedSongs = likedSongs,
            navigator = navigator,
            onNewPlaylist = onNewPlaylist,
            modifier = Modifier.revealOnAppear("playlists_actions", revealSeen, cascade = true),
        )
        Spacer(Modifier.height(22.dp))
    }

    item(key = "playlists_title") {
        LibraryCapsLabel(
            stringResource(R.string.playlists),
            modifier = Modifier.revealOnAppear("playlists_title", revealSeen, cascade = true),
        )
        Spacer(Modifier.height(8.dp))
    }

    if (playlists.isEmpty()) {
        item(key = "playlists_none") {
            Text(
                text = stringResource(R.string.library_playlists_none),
                color = SearchColors.Secondary,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = LibraryHorizontalPadding),
            )
        }
    } else {
        items(playlists, key = { "playlist_${it.id}" }) { playlist ->
            LibraryListRow(
                title = playlist.playlist.name,
                subtitle = "${playlist.songCount} songs",
                thumbnailUrl = playlist.thumbnails.firstOrNull(),
                onClick = { navigator.navigate("local_playlist/${playlist.id}") },
                onLongClick = { onPlaylistMenu(playlist) },
                sharedElementKey = "playlist-${playlist.id}",
                modifier = Modifier.revealOnAppear("playlist_${playlist.id}", revealSeen, cascade = true),
            )
        }
    }
}
