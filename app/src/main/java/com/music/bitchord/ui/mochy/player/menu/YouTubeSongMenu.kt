// Ported from Mochy Music (GPL-3.0): ui/menu/YouTubeSongMenu.kt
// YouTubeSongMenu.kt
//
// NOTE on the source path: the task brief placed these menus under
// ui/player/menu/, but in the Mochy source they live in ui/menu/ — the
// header above names the real file. The CW package below
// (ui.mochy.player.menu) follows the brief's output layout.
//
// ADAPTATIONS (CW has no LocalDatabase / LocalDownloadUtil / LocalSyncUtils
// / Mochy YouTube engine — see NEEDS.md):
// - Like and in-library state come in as params (Mochy read them from its
//   db song record) and are kept in local rememberSaveable state; the
//   writes go to YtMusicRepository (+ LikeState) via MenuActions.
// - Download state comes from CW's Downloads flows (downloadStateFor);
//   download/remove go through Downloads.enqueue() / removeDownload().
// - Play next / add to queue / start radio call MochyPlayerConnection
//   (Phase 3 stubs) exactly as in Mochy.
// - Remove from history calls YtMusicRepository.removeFromHistory(token)
//   with the item's own token, then the onHistoryRemoved callback.
// - Navigation goes through MochyNavigator (param renamed navController ->
//   navigator, same route strings).
// - Pin state comes from SpeedDialStore (the Home speed dial's pins);
//   the row toggles the song's pin and dismisses.
// - Add to playlist opens AddToPlaylistDialog (online add works; local
//   playlists answer honestly in the dialog). Details opens CW's own
//   SongInfoDialog (Classic) — Mochy's ShowMediaInfo was NOT ported: it
//   needs Mochy's innertube fork (getMediaInfo) and a Room database CW
//   doesn't have.
// - DROPPED: the isPortrait/configuration dead reads.
// - Share opens Mochy's SongShare sheet (the "Share music" kind page:
//   music card, flowing lyrics, copy YouTube Music link), as in Mochy.

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
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import com.music.bitchord.R
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.download.Downloads
import com.music.bitchord.ui.components.SongInfoDialog
import com.music.bitchord.ui.mochy.component.ListDialog
import com.music.bitchord.ui.mochy.component.Material3MenuGroup
import com.music.bitchord.ui.mochy.component.Material3MenuItemData
import com.music.bitchord.ui.mochy.component.SheetActionCard
import com.music.bitchord.ui.mochy.component.SheetActionCardRow
import com.music.bitchord.ui.mochy.component.SheetDownloadCard
import com.music.bitchord.ui.mochy.component.SheetHeader
import com.music.bitchord.ui.mochy.component.SheetHeaderButton
import com.music.bitchord.ui.mochy.component.SheetHeaderGap
import com.music.bitchord.ui.mochy.component.downloadFractionFor
import com.music.bitchord.ui.mochy.component.downloadStateFor
import com.music.bitchord.ui.mochy.component.toCwSong
import com.music.bitchord.ui.mochy.constants.ListItemHeight
import com.music.bitchord.ui.mochy.extensions.toMediaItem
import com.music.bitchord.ui.mochy.models.MediaMetadata
import com.music.bitchord.ui.mochy.models.innertube.SongItem
import com.music.bitchord.ui.mochy.models.toMediaMetadata
import com.music.bitchord.ui.mochy.navigation.MochyNavigator
import com.music.bitchord.ui.mochy.playback.LocalPlayerConnection
import com.music.bitchord.ui.mochy.playback.queues.YouTubeQueue
import com.music.bitchord.ui.mochy.speeddial.SpeedDialItem
import com.music.bitchord.ui.mochy.speeddial.SpeedDialStore
import com.music.bitchord.ui.mochy.utils.joinByBullet
import com.music.bitchord.ui.mochy.utils.makeTimeString
import com.music.bitchord.ui.mochy.utils.resize
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

@Composable
fun YouTubeSongMenu(
    song: SongItem,
    navigator: MochyNavigator,
    onDismiss: () -> Unit,
    onHistoryRemoved: () -> Unit = {},
    isLiked: Boolean = false,
    isInLibrary: Boolean = false,
) {
    val context = LocalContext.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val pins by SpeedDialStore.all.collectAsState()
    val isPinned = pins.any { it.id == song.id }
    val downloadState = downloadStateFor(song.id)
    val downloadFraction = downloadFractionFor(song.id)
    val coroutineScope = rememberCoroutineScope()
    val artists = remember {
        song.artists.mapNotNull {
            it.id?.let { artistId ->
                MediaMetadata.Artist(id = artistId, name = it.name)
            }
        }
    }
    var liked by rememberSaveable { mutableStateOf(isLiked) }
    var inLibrary by rememberSaveable { mutableStateOf(isInLibrary) }

    var showSelectArtistDialog by rememberSaveable {
        mutableStateOf(false)
    }

    // Details opens CW's own song info dialog (Classic, SongInfoRepository)
    // — not Mochy's ShowMediaInfo, which needs Mochy's innertube fork
    // (getMediaInfo) and a Room database CW doesn't have.
    var showSongInfo by rememberSaveable { mutableStateOf(false) }

    if (showSongInfo) {
        SongInfoDialog(song.toCwSong()) { showSongInfo = false }
    }

    var showAddToPlaylist by remember { mutableStateOf(false) }

    // Opens over this menu, which comes back forward behind it; the online
    // add works, a local playlist answers honestly (see the dialog).
    AddToPlaylistDialog(
        isVisible = showAddToPlaylist,
        onGetSong = { listOf(song.id) },
        onDismiss = { showAddToPlaylist = false },
        topSongId = song.id,
        topSongArtworkUrl = song.thumbnail,
    )

    if (showSelectArtistDialog) {
        ListDialog(
            onDismiss = { showSelectArtistDialog = false },
        ) {
            items(artists) { artist ->
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
                        contentAlignment = Alignment.CenterStart,
                        modifier =
                        Modifier
                            .fillParentMaxWidth()
                            .height(ListItemHeight)
                            .clickable {
                                navigator.navigate("artist/${artist.id}")
                                showSelectArtistDialog = false
                                onDismiss()
                            }
                            .padding(horizontal = 24.dp),
                    ) {
                        Text(
                            text = artist.name,
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

    // The sheet's header, as in the song-menu mock: cover, title, the artists, and like.
    SheetHeader(
        thumbnailUrl = song.thumbnail,
        title = song.title,
        subtitle = joinByBullet(
            song.artists.joinToString { it.name },
            song.duration?.let { makeTimeString(it * 1000L) },
        ),
        trailing = {
            SheetHeaderButton(
                icon = if (liked) R.drawable.favorite else R.drawable.favorite_border,
                tint = if (liked) MaterialTheme.colorScheme.error else Color.White,
                onClick = {
                    liked = !liked
                    coroutineScope.toggleSongLike(song.id, liked)
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
            // Download and Play next are what people open a song's menu for, so they get the two
            // big cards. Then the song-menu mock's four rows - add to playlist, share, album, artist.
            SheetActionCardRow {
                SheetDownloadCard(
                    downloadState = downloadState,
                    downloadFraction = downloadFraction,
                    onDownload = {
                        Downloads.enqueue(context, song.toCwSong(), from = "menu")
                    },
                    onRemove = {
                        coroutineScope.removeDownload(context, song.id)
                    },
                    modifier = Modifier.weight(1f),
                )
                SheetActionCard(
                    icon = R.drawable.playlist_play,
                    title = stringResource(R.string.play_next),
                    onClick = {
                        playerConnection.playNext(song.copy(thumbnail = song.thumbnail.resize(1200, 1200)).toMediaItem())
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item { Spacer(modifier = Modifier.height(10.dp)) }

        item {
            Material3MenuGroup(
                items = listOfNotNull(
                    Material3MenuItemData(
                        title = { Text(text = stringResource(R.string.add_to_an_playlist)) },
                        icon = {
                            Icon(
                                painter = painterResource(R.drawable.playlist_add),
                                contentDescription = null,
                            )
                        },
                        onClick = {
                            showAddToPlaylist = true
                        }
                    ),
                    Material3MenuItemData(
                        title = { Text(text = stringResource(R.string.share)) },
                        icon = {
                            Icon(
                                painter = painterResource(R.drawable.share),
                                contentDescription = null,
                            )
                        },
                        onClick = {
                            onDismiss()
                            com.music.bitchord.ui.mochy.player.share.song.SongShare.open(song.toMediaMetadata())
                        }
                    ),
                    song.album?.let { album ->
                        Material3MenuItemData(
                            title = { Text(text = stringResource(R.string.view_album)) },
                            icon = {
                                Icon(
                                    painter = painterResource(R.drawable.album),
                                    contentDescription = null,
                                )
                            },
                            onClick = {
                                navigator.navigate("album/${album.id}")
                                onDismiss()
                            }
                        )
                    },
                    if (artists.isNotEmpty()) {
                        Material3MenuItemData(
                            title = { Text(text = stringResource(R.string.view_artist)) },
                            icon = {
                                Icon(
                                    painter = painterResource(R.drawable.artist),
                                    contentDescription = null,
                                )
                            },
                            onClick = {
                                if (artists.size == 1) {
                                    navigator.navigate("artist/${artists[0].id}")
                                    onDismiss()
                                } else {
                                    showSelectArtistDialog = true
                                }
                            }
                        )
                    } else null,
                )
            )
        }

        item { Spacer(modifier = Modifier.height(12.dp)) }

        item {
            Material3MenuGroup(
                items = listOfNotNull(

                    if (true) {
                        Material3MenuItemData(
                            title = { Text(text = stringResource(R.string.start_radio)) },
                            description = { Text(text = stringResource(R.string.start_radio_desc)) },
                            icon = {
                                Icon(
                                    painter = painterResource(R.drawable.radio),
                                    contentDescription = null,
                                )
                            },
                            onClick = {
                                playerConnection.playQueue(YouTubeQueue.radio(song.toMediaMetadata()))
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
                                playerConnection.addToQueue(song.toMediaItem())
                                onDismiss()
                            }
                        )
                    } else null
                )
            )
        }

        item { Spacer(modifier = Modifier.height(12.dp)) }

        item {
            Material3MenuGroup(
                items = buildList {
                    if (song.historyRemoveToken != null) {
                        add(
                            Material3MenuItemData(
                                title = { Text(text = stringResource(R.string.remove_from_history)) },
                                icon = {
                                    Icon(
                                        painter = painterResource(R.drawable.delete),
                                        contentDescription = null,
                                    )
                                },
                                onClick = {
                                    coroutineScope.launch {
                                        YtMusicRepository.removeFromHistory(song.historyRemoveToken!!)
                                        delay(500)
                                        onHistoryRemoved()
                                        onDismiss()
                                    }
                                }
                            )
                        )
                    }
                    add(
                        Material3MenuItemData(
                            title = {
                                Text(
                                    text = if (isPinned) stringResource(R.string.unpin_from_speed_dial) else stringResource(R.string.pin_to_speed_dial)
                                )
                            },
                            icon = {
                                Icon(
                                    painter = painterResource(if (isPinned) R.drawable.remove else R.drawable.add),
                                    contentDescription = null,
                                )
                            },
                            onClick = {
                                SpeedDialStore.toggle(
                                    SpeedDialItem(
                                        id = song.id,
                                        title = song.title,
                                        subtitle = song.artists.joinToString(", ") { it.name },
                                        thumbnailUrl = song.thumbnail,
                                        type = SpeedDialItem.TYPE_SONG,
                                        explicit = song.explicit,
                                    )
                                )
                                onDismiss()
                            }
                        )
                    )
                    add(
                        Material3MenuItemData(
                            title = {
                                Text(text = if (inLibrary) stringResource(R.string.remove_from_library) else stringResource(R.string.add_to_library))
                            },
                            description = { Text(text = stringResource(R.string.add_to_library_desc)) },
                            icon = {
                                Icon(
                                    painter = painterResource(if (inLibrary) R.drawable.library_add_check else R.drawable.library_add),
                                    contentDescription = null,
                                )
                            },
                            onClick = {
                                inLibrary = !inLibrary
                                coroutineScope.sendLibraryFeedback(
                                    song.id,
                                    inLibrary,
                                    song.libraryAddToken,
                                    song.libraryRemoveToken,
                                )
                            }
                        )
                    )
                }
            )
        }

        item { Spacer(modifier = Modifier.height(12.dp)) }

        item {
            Material3MenuGroup(
                items = buildList {
                    add(
                        Material3MenuItemData(
                            title = { Text(text = stringResource(R.string.details)) },
                            description = { Text(text = stringResource(R.string.details_desc)) },
                            icon = {
                                Icon(
                                    painter = painterResource(R.drawable.info),
                                    contentDescription = null,
                                )
                            },
                            onClick = {
                                showSongInfo = true
                            }
                        )
                    )
                }
            )
        }
    }
}
