// Ported from Mochy Music (GPL-3.0): ui/menu/AlbumMenu.kt
// AlbumMenu.kt
//
// NOTE on the source path: the task brief placed these menus under
// ui/player/menu/, but in the Mochy source they live in ui/menu/ — the
// header above names the real file. The CW package below
// (ui.mochy.player.menu) follows the brief's output layout.
//
// ADAPTATIONS (CW has no LocalDatabase / LocalDownloadUtil / Mochy YouTube
// engine — see NEEDS.md):
// - The album is the passed-in `originalAlbum`; its tracks come from
//   YtMusicRepository.browseSongs(album.id) instead of the db's albumSongs.
//   A local album (no browse id on YouTube) loads no tracks, so Play /
//   Shuffle / Play next / Add to queue stay inert for it, as they did for
//   an empty album in Mochy.
// - The header save state is local rememberSaveable state; the write is
//   YtMusicRepository.setSaved(album playlistId, saved). A local album has
//   no playlist id, so saving it logs a TODO instead.
// - Download state is the aggregate over the loaded tracks
//   (downloadStateForAll); download/remove go through Downloads.
// - Shuffle drops playerConnection.service.getAutomix(): MochyPlayerConnection
//   has no service/getAutomix. The shuffled ListQueue still plays.
// - Navigation goes through MochyNavigator (param renamed navController ->
//   navigator, same route strings).
// - Pin state comes in as a param; the toggle is a caller callback, and
//   without one the row logs a Phase 3 TODO (CW has no pin store yet).
// - STUBBED (log + row stays): Add to playlist (AddToPlaylistDialog not
//   ported), Refetch (no local store to refetch into).
// - DROPPED: the AddToPlaylistDialog + already-in-playlist error dialog,
//   the isPortrait/configuration dead reads.

package com.music.bitchord.ui.mochy.player.menu

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.exoplayer.offline.Download.STATE_COMPLETED
import androidx.media3.exoplayer.offline.Download.STATE_DOWNLOADING
import androidx.media3.exoplayer.offline.Download.STATE_QUEUED
import coil3.compose.AsyncImage
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
import com.music.bitchord.ui.mochy.constants.ListThumbnailSize
import com.music.bitchord.ui.mochy.extensions.toMediaItem
import com.music.bitchord.ui.mochy.models.Album
import com.music.bitchord.ui.mochy.models.Song
import com.music.bitchord.ui.mochy.models.toMochySong
import com.music.bitchord.ui.mochy.navigation.MochyNavigator
import com.music.bitchord.ui.mochy.playback.LocalPlayerConnection
import com.music.bitchord.ui.mochy.playback.queues.ListQueue
import kotlinx.coroutines.launch
import timber.log.Timber

@Composable
fun AlbumMenu(
    originalAlbum: Album,
    navigator: MochyNavigator,
    onDismiss: () -> Unit,
    isPinned: Boolean = false,
    onTogglePin: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val album = originalAlbum
    var songs by remember {
        mutableStateOf(emptyList<Song>())
    }

    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(album.id) {
        songs = YtMusicRepository.browseSongs(album.id).getOrNull()
            ?.songs.orEmpty().map { it.toMochySong() }
    }

    val downloadState = downloadStateForAll(songs.map { it.id })

    var refetchIconDegree by remember { mutableFloatStateOf(0f) }

    val rotationAnimation by animateFloatAsState(
        targetValue = refetchIconDegree,
        animationSpec = tween(durationMillis = 800),
        label = "",
    )

    var saved by rememberSaveable { mutableStateOf(album.album.bookmarkedAt != null) }

    var showSelectArtistDialog by rememberSaveable {
        mutableStateOf(false)
    }

    if (showSelectArtistDialog) {
        ListDialog(
            onDismiss = { showSelectArtistDialog = false },
        ) {
            items(
                items = album.artists.distinctBy { it.id },
                key = { it.id },
            ) { artist ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                    Modifier
                        .height(ListItemHeight)
                        .clickable {
                            navigator.navigate("artist/${artist.id}")
                            showSelectArtistDialog = false
                            onDismiss()
                        }
                        .padding(horizontal = 12.dp),
                ) {
                    Box(
                        modifier = Modifier.padding(8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        AsyncImage(
                            model = artist.thumbnailUrl,
                            contentDescription = null,
                            modifier =
                            Modifier
                                .size(ListThumbnailSize)
                                .clip(CircleShape),
                        )
                    }
                    Text(
                        text = artist.name,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier =
                        Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp),
                    )
                }
            }
        }
    }

    // The sheet's header, as in the album-menu mock: cover, title, the artists, and save.
    SheetHeader(
        thumbnailUrl = album.album.thumbnailUrl,
        title = album.album.title,
        subtitle = listOfNotNull(
            album.artists.joinToString { it.name }.ifBlank { null },
            album.album.year?.toString(),
        ).joinToString(" • "),
        trailing = {
            SheetHeaderButton(
                icon = if (saved) R.drawable.favorite else R.drawable.favorite_border,
                tint = if (saved) MaterialTheme.colorScheme.error else Color.White,
                onClick = {
                    val playlistId = album.album.playlistId
                    if (playlistId == null) {
                        // TODO(Phase 3): a local album has no YouTube playlist
                        // id to save against, and CW has no local save store.
                        Timber.w("AlbumMenu: cannot save a local album (no playlist id)")
                    } else {
                        saved = !saved
                        coroutineScope.launch {
                            YtMusicRepository.setSaved(playlistId, saved)
                        }
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
                                    playerConnection.playQueue(
                                        ListQueue(
                                            title = album.album.title,
                                            items = songs.map(Song::toMediaItem)
                                        )
                                    )
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
                                    playerConnection.playQueue(
                                        ListQueue(
                                            title = album.album.title,
                                            items = songs.shuffled().map(Song::toMediaItem)
                                        )
                                    )
                                }
                            }
                        ),
                    // only an album youtube knows has a link, a local album or one not opened yet has none
                    album.album.playlistId?.let { listId ->
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
                                context.shareText("https://music.youtube.com/playlist?list=$listId")
                            }
                        )
                    },
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
                            navigator.navigate("album/${album.album.id}")
                        }
                    ),
                    if (album.artists.isNotEmpty()) {
                        NewAction(
    icon = {
                                Icon(
                                    painter = painterResource(R.drawable.artist),
                                    contentDescription = null,
                                )
                            },
                            text = stringResource(R.string.view_artist),
                            onClick = {
                                if (album.artists.size == 1) {
                                    navigator.navigate("artist/${album.artists[0].id}")
                                    onDismiss()
                                } else {
                                    showSelectArtistDialog = true
                                }
                            }
                        )
                    } else null,
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
                                    contentDescription = null
                                )
                            },
                            onClick = {
                                onDismiss()
                                playerConnection.playNext(songs.map { it.toMediaItem() })
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
                                    contentDescription = null
                                )
                            },
                            onClick = {
                                onDismiss()
                                playerConnection.addToQueue(songs.map { it.toMediaItem() })
                            }
                        )
                    } else null,
                    Material3MenuItemData(
                        title = { Text(text = stringResource(R.string.add_to_playlist)) },
                        description = { Text(text = stringResource(R.string.add_to_playlist_desc)) },
                        icon = {
                            Icon(
                                painter = painterResource(R.drawable.playlist_add),
                                contentDescription = null
                            )
                        },
                        onClick = {
                            // TODO(Phase 3): AddToPlaylistDialog is not ported yet.
                            Timber.w("AlbumMenu: add-to-playlist needs Phase 3 (AddToPlaylistDialog)")
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
                                Timber.w("AlbumMenu: pin needs Phase 3 (pin store)")
                            }
                        }
                    )
                )
            )
        }

        // a local album is already on the phone, so there's nothing to download or refetch from youtube
        if (album.album.isLocal) return@LazyColumn

        item { Spacer(modifier = Modifier.height(12.dp)) }

        item {
            Material3MenuGroup(
                items = listOf(
                    when (downloadState) {
                        STATE_COMPLETED -> {
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
                        STATE_QUEUED, STATE_DOWNLOADING -> {
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
                                        contentDescription = null
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

        item { Spacer(modifier = Modifier.height(12.dp)) }

        item {
            Material3MenuGroup(
                items = listOf(
                    Material3MenuItemData(
                        title = { Text(text = stringResource(R.string.refetch)) },
                        description = { Text(text = stringResource(R.string.refetch_desc)) },
                        icon = {
                            Icon(
                                painter = painterResource(R.drawable.sync),
                                contentDescription = null,
                                modifier = Modifier.graphicsLayer(rotationZ = rotationAnimation)
                            )
                        },
                        onClick = {
                            refetchIconDegree -= 360
                            // TODO(Phase 3): refetching rewrites the local library
                            // record, and CW has no local library store to rewrite.
                            Timber.w("AlbumMenu: refetch needs Phase 3 (no local library store)")
                        }
                    )
                )
            )
        }
    }
}
