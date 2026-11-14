// Ported from Mochy Music (GPL-3.0): ui/menu/SongMenu.kt
// SongMenu.kt
//
// NOTE on the source path: the task brief placed these menus under
// ui/player/menu/, but in the Mochy source they live in ui/menu/ — the
// header above names the real file. The CW package below
// (ui.mochy.player.menu) follows the brief's output layout.
//
// ADAPTATIONS (CW has no LocalDatabase / LocalDownloadUtil / LocalSyncUtils
// / Mochy YouTube engine — see NEEDS.md):
// - The song is the passed-in `originalSong` (Mochy re-read it from its db
//   flow); like and in-library state are local rememberSaveable state
//   initialised from the entity, and the writes go to YtMusicRepository
//   (+ LikeState) via MenuActions.
// - Download state comes from CW's Downloads flows (downloadStateFor);
//   download/remove go through Downloads.enqueue() / removeDownload().
// - Play / play next / add to queue / start radio call MochyPlayerConnection
//   (Phase 3 stubs) with the same queues Mochy built.
// - Navigation goes through MochyNavigator (param renamed navController ->
//   navigator, same route strings).
// - Pin state comes in as a param; the toggle is a caller callback, and
//   without one the row logs a Phase 3 TODO (CW has no pin store yet).
// - STUBBED (log + row stays): Add to playlist (AddToPlaylistDialog not
//   ported), Edit (SongEditSheet not ported), Details (ShowMediaInfo not
//   ported), Refetch (no local store to refetch into), Remove from history
//   / Remove from playlist (no local event/playlist store), Remove from
//   cache (no CachePlaylistViewModel).
// - DROPPED: the AddToPlaylistDialog + already-in-playlist error dialog,
//   the "Choose different version" row (importer does not exist in CW),
//   the orderedArtists lookup (dead in Mochy — never read), the
//   isPortrait/configuration dead reads.
// - Share is a plain ACTION_SEND intent with the watch link (Mochy's
//   SongShare sheet is not ported).
// - `event` / `playlistSong` use the plain ported types Event /
//   PlaylistSong (ui.mochy.models — no Room, no store behind them;
//   callers pass null until a history/playlist store exists).

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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import coil3.compose.AsyncImage
import com.music.bitchord.R
import com.music.bitchord.download.Downloads
import com.music.bitchord.ui.mochy.component.ListDialog
import com.music.bitchord.ui.mochy.component.Material3MenuGroup
import com.music.bitchord.ui.mochy.component.Material3MenuItemData
import com.music.bitchord.ui.mochy.component.SheetActionCard
import com.music.bitchord.ui.mochy.component.SheetActionCardRow
import com.music.bitchord.ui.mochy.component.SheetDownloadCard
import com.music.bitchord.ui.mochy.component.SheetHeader
import com.music.bitchord.ui.mochy.component.SheetHeaderButton
import com.music.bitchord.ui.mochy.component.SheetHeaderGap
import com.music.bitchord.ui.mochy.component.downloadStateFor
import com.music.bitchord.ui.mochy.component.toCwSong
import com.music.bitchord.ui.mochy.constants.ListItemHeight
import com.music.bitchord.ui.mochy.constants.ListThumbnailSize
import com.music.bitchord.ui.mochy.extensions.toMediaItem
import com.music.bitchord.ui.mochy.models.Event
import com.music.bitchord.ui.mochy.models.PlaylistSong
import com.music.bitchord.ui.mochy.models.Song
import com.music.bitchord.ui.mochy.models.toMediaMetadata
import com.music.bitchord.ui.mochy.navigation.MochyNavigator
import com.music.bitchord.ui.mochy.playback.LocalPlayerConnection
import com.music.bitchord.ui.mochy.playback.queues.ListQueue
import com.music.bitchord.ui.mochy.playback.queues.YouTubeQueue
import timber.log.Timber

@Composable
fun SongMenu(
    originalSong: Song,
    event: Event? = null,
    navigator: MochyNavigator,
    playlistSong: PlaylistSong? = null,
    playlistBrowseId: String? = null,
    onDismiss: () -> Unit,
    isFromCache: Boolean = false,
    isPinned: Boolean = false,
    onTogglePin: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val song = originalSong
    val downloadState = downloadStateFor(originalSong.id)
    val coroutineScope = rememberCoroutineScope()
    var refetchIconDegree by remember { mutableFloatStateOf(0f) }

    val rotationAnimation by animateFloatAsState(
        targetValue = refetchIconDegree,
        animationSpec = tween(durationMillis = 800),
        label = "",
    )

    var liked by rememberSaveable { mutableStateOf(song.song.liked) }
    var inLibrary by rememberSaveable { mutableStateOf(song.song.inLibrary != null) }

    var showSelectArtistDialog by rememberSaveable {
        mutableStateOf(false)
    }

    if (showSelectArtistDialog) {
        ListDialog(
            onDismiss = { showSelectArtistDialog = false },
        ) {
            items(
                items = song.artists.distinctBy { it.id },
                key = { it.id },
            ) { artist ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
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
                            modifier = Modifier
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
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp),
                    )
                }
            }
        }
    }

    SheetHeader(
        thumbnailUrl = song.song.thumbnailUrl,
        title = song.song.title,
        subtitle = song.artists.joinToString(", ") { it.name },
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
            // big cards. Then the song-menu mock's four rows - add to playlist, share, album,
            // artist - and the rest.
            SheetActionCardRow {
                SheetDownloadCard(
                    downloadState = downloadState,
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
                        onDismiss()
                        playerConnection.playNext(song.toMediaItem())
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
                            // TODO(Phase 3): AddToPlaylistDialog is not ported yet.
                            Timber.w("SongMenu: add-to-playlist needs Phase 3 (AddToPlaylistDialog)")
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
                            context.shareText("https://music.youtube.com/watch?v=${song.id}")
                        }
                    ),
                    if (song.song.albumId != null) {
                        Material3MenuItemData(
                            title = { Text(text = stringResource(R.string.view_album)) },
                            icon = {
                                Icon(
                                    painter = painterResource(R.drawable.album),
                                    contentDescription = null,
                                )
                            },
                            onClick = {
                                onDismiss()
                                navigator.navigate("album/${song.song.albumId}")
                            }
                        )
                    } else null,
                    Material3MenuItemData(
                        title = { Text(text = stringResource(R.string.view_artist)) },
                        icon = {
                            Icon(
                                painter = painterResource(R.drawable.artist),
                                contentDescription = null,
                            )
                        },
                        onClick = {
                            if (song.artists.size == 1) {
                                navigator.navigate("artist/${song.artists[0].id}")
                                onDismiss()
                            } else {
                                showSelectArtistDialog = true
                            }
                        }
                    ),
                )
            )
        }

        item { Spacer(modifier = Modifier.height(12.dp)) }

        item {
            Material3MenuGroup(
                items = listOf(
                    Material3MenuItemData(
                        title = { Text(text = stringResource(R.string.edit)) },
                        icon = {
                            Icon(
                                painter = painterResource(R.drawable.edit),
                                contentDescription = null,
                            )
                        },
                        onClick = {
                            // TODO(Phase 3): SongEditSheet is not ported yet.
                            Timber.w("SongMenu: edit needs Phase 3 (SongEditSheet)")
                        }
                    ),
                    Material3MenuItemData(
                        title = { Text(text = stringResource(R.string.play)) },
                        icon = {
                            Icon(
                                painter = painterResource(R.drawable.play),
                                contentDescription = null,
                            )
                        },
                        onClick = {
                            onDismiss()
                            playerConnection.playQueue(
                                ListQueue(
                                    title = song.song.title,
                                    items = listOf(song.toMediaItem()),
                                )
                            )
                        }
                    ),
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
                                onDismiss()
                                playerConnection.playQueue(YouTubeQueue.radio(song.toMediaMetadata()))
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
                                onDismiss()
                                playerConnection.addToQueue(song.toMediaItem())
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
                    add(
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
                                    Timber.w("SongMenu: pin needs Phase 3 (pin store)")
                                }
                            }
                        )
                    )
                    add(
                        Material3MenuItemData(
                            title = {
                                Text(
                                    text = stringResource(
                                        if (!inLibrary) R.string.add_to_library
                                        else R.string.remove_from_library
                                    )
                                )
                            },
                            description = { Text(text = stringResource(R.string.add_to_library_desc)) },
                            icon = {
                                Icon(
                                    painter = painterResource(
                                        if (!inLibrary) R.drawable.library_add
                                        else R.drawable.library_add_check
                                    ),
                                    contentDescription = null,
                                )
                            },
                            onClick = {
                                inLibrary = !inLibrary
                                coroutineScope.sendLibraryFeedback(
                                    song.id,
                                    inLibrary,
                                    song.song.libraryAddToken,
                                    song.song.libraryRemoveToken,
                                )
                            }
                        )
                    )
                    if (event != null) {
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
                                    // TODO(Phase 3): CW has no local play-event store; the
                                    // server-side removal needs the row's feedback token,
                                    // which Event does not carry.
                                    Timber.w("SongMenu: remove-from-history needs Phase 3 (no local event store)")
                                }
                            )
                        )
                    }
                    if (playlistSong != null) {
                        add(
                            Material3MenuItemData(
                                title = { Text(text = stringResource(R.string.remove_from_playlist)) },
                                icon = {
                                    Icon(
                                        painter = painterResource(R.drawable.delete),
                                        contentDescription = null,
                                    )
                                },
                                onClick = {
                                    // TODO(Phase 3): CW has no local playlists; the server
                                    // call (YtMusicRepository.removeFromPlaylist) needs the
                                    // entry's setVideoId, which PlaylistSong does not expose
                                    // until it is ported.
                                    Timber.w("SongMenu: remove-from-playlist needs Phase 3 (no local playlist store)")
                                }
                            )
                        )
                    }
                    if (isFromCache) {
                        add(
                            Material3MenuItemData(
                                title = { Text(text = stringResource(R.string.remove_from_cache)) },
                                icon = {
                                    Icon(
                                        painter = painterResource(R.drawable.delete),
                                        contentDescription = null,
                                    )
                                },
                                onClick = {
                                    // TODO(Phase 3): CachePlaylistViewModel does not exist in CW.
                                    Timber.w("SongMenu: remove-from-cache needs Phase 3 (CachePlaylistViewModel)")
                                }
                            )
                        )
                    }
                }
            )
        }

        item { Spacer(modifier = Modifier.height(12.dp)) }

        item {
            Material3MenuGroup(
                items = buildList {
                    add(
                        Material3MenuItemData(
                            title = { Text(text = stringResource(R.string.refetch)) },
                            description = { Text(text = stringResource(R.string.refetch_desc)) },
                            icon = {
                                Icon(
                                    painter = painterResource(R.drawable.sync),
                                    contentDescription = null,
                                    modifier = Modifier.graphicsLayer(rotationZ = rotationAnimation),
                                )
                            },
                            onClick = {
                                refetchIconDegree -= 360
                                // TODO(Phase 3): refetching rewrites the local library
                                // record, and CW has no local library store to rewrite.
                                Timber.w("SongMenu: refetch needs Phase 3 (no local library store)")
                            }
                        )
                    )
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
                                // TODO(Phase 3): ShowMediaInfo is not ported yet.
                                Timber.w("SongMenu: details needs Phase 3 (ShowMediaInfo)")
                            }
                        )
                    )
                }
            )
        }
    }
}
