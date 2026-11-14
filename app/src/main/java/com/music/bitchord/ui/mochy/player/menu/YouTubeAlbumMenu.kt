// Ported from Mochy Music (GPL-3.0): ui/menu/YouTubeAlbumMenu.kt
// YouTubeAlbumMenu.kt
//
// NOTE on the source path: the task brief placed these menus under
// ui/player/menu/, but in the Mochy source they live in ui/menu/ — the
// header above names the real file. The CW package below
// (ui.mochy.player.menu) follows the brief's output layout.
//
// ADAPTATIONS (CW has no LocalDatabase / LocalDownloadUtil / Mochy YouTube
// engine — see NEEDS.md):
// - The album's tracks come from YtMusicRepository.browseSongs(browseId)
//   instead of Mochy's fetch-into-db (YouTube.album + insert). The menu
//   no longer imports the album into a local store at all.
// - The header save state comes in as a param (Mochy read it from its db
//   album record) and is kept in local rememberSaveable state; the write
//   is YtMusicRepository.setSaved(album playlistId, saved).
// - Play / Shuffle build YouTubeAlbumRadio(playlistId) exactly as in Mochy
//   (thin port in this phase; resolution is Phase 3) and hand it to
//   MochyPlayerConnection.playQueue.
// - Download state is the aggregate over the loaded tracks
//   (downloadStateForAll); download/remove go through Downloads.
// - The artist picker lists the item's own artists (Mochy listed the db
//   album's artists); artists without an id are skipped.
// - Navigation goes through MochyNavigator (param renamed navController ->
//   navigator, same route strings).
// - Pin state comes in as a param; the toggle is a caller callback, and
//   without one the row logs a Phase 3 TODO (CW has no pin store yet).
// - STUBBED (log + row stays): Add to playlist (AddToPlaylistDialog not
//   ported).
// - DROPPED: the AddToPlaylistDialog + already-in-playlist error dialog,
//   the isPortrait/configuration dead reads.

package com.music.bitchord.ui.mochy.player.menu

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.exoplayer.offline.Download
import com.music.bitchord.R
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.download.Downloads
import com.music.bitchord.ui.mochy.component.ListDialog
import com.music.bitchord.ui.mochy.component.Material3MenuGroup
import com.music.bitchord.ui.mochy.component.Material3MenuItemData
import com.music.bitchord.ui.mochy.component.NewAction
import com.music.bitchord.ui.mochy.component.NewActionGrid
import com.music.bitchord.ui.mochy.component.SheetHeader
import com.music.bitchord.ui.mochy.component.SheetHeaderButton
import com.music.bitchord.ui.mochy.component.SheetHeaderGap
import com.music.bitchord.ui.mochy.component.downloadStateForAll
import com.music.bitchord.ui.mochy.component.toCwSong
import com.music.bitchord.ui.mochy.constants.ListItemHeight
import com.music.bitchord.ui.mochy.extensions.toMediaItem
import com.music.bitchord.ui.mochy.models.Song
import com.music.bitchord.ui.mochy.models.innertube.AlbumItem
import com.music.bitchord.ui.mochy.models.toMochySong
import com.music.bitchord.ui.mochy.navigation.MochyNavigator
import com.music.bitchord.ui.mochy.playback.LocalPlayerConnection
import com.music.bitchord.ui.mochy.playback.queues.YouTubeAlbumRadio
import kotlinx.coroutines.launch
import timber.log.Timber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YouTubeAlbumMenu(
    albumItem: AlbumItem,
    navigator: MochyNavigator,
    onDismiss: () -> Unit,
    isSaved: Boolean = false,
    isPinned: Boolean = false,
    onTogglePin: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val coroutineScope = rememberCoroutineScope()
    var songs by remember {
        mutableStateOf(emptyList<Song>())
    }
    var saved by rememberSaveable { mutableStateOf(isSaved) }

    LaunchedEffect(albumItem.browseId) {
        songs = YtMusicRepository.browseSongs(albumItem.browseId).getOrNull()
            ?.songs.orEmpty().map { it.toMochySong() }
    }

    val downloadState = downloadStateForAll(songs.map { it.id })

    var showSelectArtistDialog by rememberSaveable {
        mutableStateOf(false)
    }

    if (showSelectArtistDialog) {
        ListDialog(
            onDismiss = { showSelectArtistDialog = false },
        ) {
            items(
                items = albumItem.artists.orEmpty().mapNotNull { artist ->
                    artist.id?.let { id -> id to artist.name }
                }.distinctBy { it.first },
                key = { it.first },
            ) { (artistId, artistName) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .height(ListItemHeight)
                        .clickable {
                            navigator.navigate("artist/$artistId")
                            showSelectArtistDialog = false
                            onDismiss()
                        }
                        .padding(horizontal = 12.dp),
                ) {
                    Box(
                        contentAlignment = Alignment.CenterStart,
                        modifier = Modifier
                            .fillParentMaxWidth()
                            .height(ListItemHeight)
                            .clickable {
                                showSelectArtistDialog = false
                                onDismiss()
                                navigator.navigate("artist/$artistId")
                            }
                            .padding(horizontal = 24.dp),
                    ) {
                        Text(
                            text = artistName,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }

    // The sheet's header, as in the album-menu mock: cover, title, the artists, and save.
    SheetHeader(
        thumbnailUrl = albumItem.thumbnail,
        title = albumItem.title,
        subtitle = listOfNotNull(
            albumItem.artists?.joinToString { it.name }?.ifBlank { null },
            albumItem.year?.toString(),
        ).joinToString(" • "),
        trailing = {
            SheetHeaderButton(
                icon = if (saved) R.drawable.favorite else R.drawable.favorite_border,
                tint = if (saved) MaterialTheme.colorScheme.error else Color.White,
                onClick = {
                    saved = !saved
                    coroutineScope.launch {
                        YtMusicRepository.setSaved(albumItem.playlistId, saved)
                    }
                },
            )
        },
    )

    Spacer(Modifier.height(SheetHeaderGap))

    LazyColumn(
        contentPadding = PaddingValues(
            start = 0.dp,
            top = 0.dp,
            end = 0.dp,
            bottom = 8.dp + WindowInsets.systemBars.asPaddingValues().calculateBottomPadding(),
        ),
    ) {
        item {
            NewActionGrid(
                actions = listOfNotNull(
                        NewAction(
                            icon = {
                                Icon(
                                    painter = painterResource(R.drawable.play),
                                    contentDescription = null,
                                    modifier = Modifier.size(28.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            text = stringResource(R.string.play),
                            onClick = {
                                onDismiss()
                                if (songs.isNotEmpty()) {
                                    playerConnection.playQueue(YouTubeAlbumRadio(albumItem.playlistId))
                                }
                            }
                        ),
                        NewAction(
                            icon = {
                                Icon(
                                    painter = painterResource(R.drawable.shuffle),
                                    contentDescription = null,
                                    modifier = Modifier.size(28.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            text = stringResource(R.string.shuffle),
                            onClick = {
                                onDismiss()
                                if (songs.isNotEmpty()) {
                                    playerConnection.playQueue(YouTubeAlbumRadio(albumItem.playlistId))
                                }
                            }
                        ),
                    NewAction(
                        icon = {
                            Icon(
                                painter = painterResource(R.drawable.share),
                                contentDescription = null,
                                modifier = Modifier.size(28.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        text = stringResource(R.string.share),
                        onClick = {
                            onDismiss()
                            context.shareText(albumItem.shareLink)
                        }
                    ),
                    NewAction(
                        icon = {
                            Icon(
                                painter = painterResource(R.drawable.album),
                                contentDescription = null,
                            )
                        },
                        text = stringResource(R.string.view_album),
                        onClick = {
                            onDismiss()
                            navigator.navigate("album/${albumItem.browseId}")
                        }
                    ),
                    albumItem.artists?.takeIf { it.isNotEmpty() }?.let { artists ->
                        NewAction(
                            icon = {
                                Icon(
                                    painter = painterResource(R.drawable.artist),
                                    contentDescription = null,
                                )
                            },
                            text = stringResource(R.string.view_artist),
                            onClick = {
                                if (artists.size == 1) {
                                    navigator.navigate("artist/${artists[0].id}")
                                    onDismiss()
                                } else {
                                    showSelectArtistDialog = true
                                }
                            }
                        )
                    },
                ),
                modifier = Modifier.padding(bottom = 12.dp),
                columns = if (false) 1 else 3
            )
        }
        item {
            Material3MenuGroup(
                items = listOfNotNull(
                    if (true) {
                        Material3MenuItemData(
                            title = { Text(text = stringResource(R.string.play_next)) },
                            description = { Text(text = stringResource(R.string.play_next_desc)) },
                            icon = {
                                Icon(
                                    painter = painterResource(R.drawable.playlist_play),
                                    contentDescription = null,
                                )
                            },
                            onClick = {
                                if (songs.isNotEmpty()) {
                                    playerConnection.playNext(songs.map { it.toMediaItem() })
                                }
                                onDismiss()
                            }
                        )
                    } else null,
                    if (true) {
                        Material3MenuItemData(
                            title = { Text(text = stringResource(R.string.add_to_queue)) },
                            description = { Text(text = stringResource(R.string.add_to_queue_desc)) },
                            icon = {
                                Icon(
                                    painter = painterResource(R.drawable.queue_music),
                                    contentDescription = null,
                                )
                            },
                            onClick = {
                                if (songs.isNotEmpty()) {
                                    playerConnection.addToQueue(songs.map { it.toMediaItem() })
                                }
                                onDismiss()
                            }
                        )
                    } else null,
                    Material3MenuItemData(
                        title = { Text(text = stringResource(R.string.add_to_playlist)) },
                        description = { Text(text = stringResource(R.string.add_to_playlist_desc)) },
                        icon = {
                            Icon(
                                painter = painterResource(R.drawable.playlist_add),
                                contentDescription = null,
                            )
                        },
                        onClick = {
                            // TODO(Phase 3): AddToPlaylistDialog is not ported yet.
                            Timber.w("YouTubeAlbumMenu: add-to-playlist needs Phase 3 (AddToPlaylistDialog)")
                        }
                    ),
                    Material3MenuItemData(
                        title = {
                            Text(
                                text = if (isPinned) "Unpin from library" else "Pin to library"
                            )
                        },
                        icon = {
                            Icon(
                                painter = painterResource(if (isPinned) R.drawable.remove else R.drawable.add),
                                contentDescription = null,
                            )
                        },
                        onClick = {
                            if (onTogglePin != null) {
                                onTogglePin()
                                onDismiss()
                            } else {
                                // TODO(Phase 3): CW has no pin store yet.
                                Timber.w("YouTubeAlbumMenu: pin needs Phase 3 (pin store)")
                            }
                        }
                    )
                )
            )
        }

        item { Spacer(modifier = Modifier.height(12.dp)) }

        item {
            Material3MenuGroup(
                items = listOf(
                    when (downloadState) {
                        Download.STATE_COMPLETED -> {
                            Material3MenuItemData(
                                title = {
                                    Text(
                                        text = stringResource(R.string.remove_download)
                                    )
                                },
                                icon = {
                                    Icon(
                                        painter = painterResource(R.drawable.offline),
                                        contentDescription = null
                                    )
                                },
                                onClick = {
                                    songs.forEach { song ->
                                        coroutineScope.removeDownload(context, song.id)
                                    }
                                }
                            )
                        }
                        Download.STATE_QUEUED, Download.STATE_DOWNLOADING -> {
                            Material3MenuItemData(
                                title = { Text(text = stringResource(R.string.downloading)) },
                                icon = {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.dp
                                    )
                                },
                                onClick = {
                                    songs.forEach { song ->
                                        coroutineScope.removeDownload(context, song.id)
                                    }
                                }
                            )
                        }
                        else -> {
                            Material3MenuItemData(
                                title = { Text(text = stringResource(R.string.action_download)) },
                                description = { Text(text = stringResource(R.string.download_desc)) },
                                icon = {
                                    Icon(
                                        painter = painterResource(R.drawable.download),
                                        contentDescription = null,
                                    )
                                },
                                onClick = {
                                    songs.forEach { song ->
                                        Downloads.enqueue(context, song.toCwSong(), from = "menu")
                                    }
                                }
                            )
                        }
                    }
                )
            )
        }

    }
}
