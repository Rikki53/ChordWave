// Ported from Mochy Music (GPL-3.0): ui/menu/YouTubePlaylistMenu.kt
// YouTubePlaylistMenu.kt
//
// NOTE on the source path: the task brief placed these menus under
// ui/player/menu/, but in the Mochy source they live in ui/menu/ — the
// header above names the real file. The CW package below
// (ui.mochy.player.menu) follows the brief's output layout.
//
// ADAPTATIONS (CW has no LocalDatabase / LocalDownloadUtil / Mochy YouTube
// engine — see NEEDS.md):
// - Save state comes in as a param (Mochy read it from its db playlist
//   record, importing the playlist on first save) and is kept in local
//   rememberSaveable state; the write is YtMusicRepository.setSaved(
//   playlist.id, saved). Mochy's toggleSavedPlaylist (a MusicDatabase
//   extension that imported the playlist + songs into the db) is dropped:
//   CW has no local playlist store to import into.
// - Play / Shuffle / Radio build YouTubeQueue from the item's endpoints
//   exactly as in Mochy and hand it to MochyPlayerConnection.playQueue.
// - Play next / Add to queue use the passed-in songs, and fetch them from
//   YtMusicRepository.playlistEntries(playlist.id) when the list is empty
//   (Mochy fetched YouTube.playlist instead).
// - Download state is the aggregate over the passed-in songs
//   (downloadStateForAll); download/remove go through Downloads. The
//   remove-download confirmation dialog is kept (DefaultDialog).
// - Pin state comes in as a param; the toggle is a caller callback, and
//   without one the row logs a Phase 3 TODO (CW has no pin store yet).
// - STUBBED (log + row stays): Add to playlist (AddToPlaylistDialog not
//   ported).
// - DROPPED: the AddToPlaylistDialog + already-in-playlist error dialog,
//   the ImportPlaylistDialog (its show state was never set to true in
//   Mochy — dead), the isPortrait/configuration dead reads.
// - `onImportedPlaylist` is kept in the signature (HomeScreen passes it)
//   but is currently unused: it fires when the import dialog lands.
// - Share is a plain ACTION_SEND intent with the item's share link.

package com.music.bitchord.ui.mochy.player.menu

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.offline.Download
import com.music.bitchord.R
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.download.Downloads
import com.music.bitchord.ui.mochy.component.DefaultDialog
import com.music.bitchord.ui.mochy.component.Material3MenuGroup
import com.music.bitchord.ui.mochy.component.Material3MenuItemData
import com.music.bitchord.ui.mochy.component.NewAction
import com.music.bitchord.ui.mochy.component.NewActionGrid
import com.music.bitchord.ui.mochy.component.SheetHeader
import com.music.bitchord.ui.mochy.component.SheetHeaderButton
import com.music.bitchord.ui.mochy.component.SheetHeaderGap
import com.music.bitchord.ui.mochy.component.downloadStateForAll
import com.music.bitchord.ui.mochy.component.toCwSong
import com.music.bitchord.ui.mochy.extensions.toMediaItem
import com.music.bitchord.ui.mochy.models.innertube.PlaylistItem
import com.music.bitchord.ui.mochy.models.innertube.SongItem
import com.music.bitchord.ui.mochy.models.toMochySong
import com.music.bitchord.ui.mochy.playback.LocalPlayerConnection
import com.music.bitchord.ui.mochy.playback.queues.YouTubeQueue
import com.music.bitchord.ui.mochy.utils.resize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YouTubePlaylistMenu(
    playlist: PlaylistItem,
    songs: List<SongItem> = emptyList(),
    coroutineScope: CoroutineScope,
    onDismiss: () -> Unit,
    selectAction: () -> Unit = {},
    canSelect: Boolean = false,
    onImportedPlaylist: (String) -> Unit = {},
    isSaved: Boolean = false,
    isPinned: Boolean = false,
    onTogglePin: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val playerConnection = LocalPlayerConnection.current ?: return
    var saved by rememberSaveable { mutableStateOf(isSaved) }

    // The sheet's header: cover, name, who made it - and save, for playlists that aren't the user's own.
    SheetHeader(
        thumbnailUrl = playlist.thumbnail,
        title = playlist.title,
        subtitle = listOfNotNull(playlist.author?.name, playlist.songCountText).joinToString(" • "),
        trailing = if (playlist.id != "LM" && !playlist.isEditable) {
            {
                SheetHeaderButton(
                    icon = if (saved) R.drawable.favorite else R.drawable.favorite_border,
                    tint = if (saved) MaterialTheme.colorScheme.error else Color.White,
                    onClick = {
                        saved = !saved
                        coroutineScope.launch {
                            YtMusicRepository.setSaved(playlist.id, saved)
                        }
                    },
                )
            }
        } else null,
    )
    Spacer(Modifier.height(SheetHeaderGap))

    val downloadState = downloadStateForAll(songs.map { it.id })

    var showRemoveDownloadDialog by remember {
        mutableStateOf(false)
    }
    if (showRemoveDownloadDialog) {
        DefaultDialog(
            onDismiss = { showRemoveDownloadDialog = false },
            content = {
                Text(
                    text = stringResource(
                        R.string.remove_download_playlist_confirm,
                        playlist.title
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 18.dp)
                )
            },
            buttons = {
                TextButton(
                    onClick = { showRemoveDownloadDialog = false }
                ) {
                    Text(text = stringResource(android.R.string.cancel))
                }
                TextButton(
                    onClick = {
                        showRemoveDownloadDialog = false
                        songs.forEach { song ->
                            coroutineScope.removeDownload(context, song.id)
                        }
                    }
                ) {
                    Text(text = stringResource(android.R.string.ok))
                }
            }
        )
    }

    // The tracks to queue, from the passed-in list when there is one, else
    // fetched fresh (Mochy fetched YouTube.playlist for the same fallback).
    // Play next resizes the covers up, as in Mochy; add-to-queue does not.
    suspend fun queueItems(resizeThumbnails: Boolean): List<MediaItem> {
        if (songs.isNotEmpty()) {
            return songs.map {
                if (resizeThumbnails) {
                    it.copy(thumbnail = it.thumbnail.resize(1200, 1200)).toMediaItem()
                } else {
                    it.toMediaItem()
                }
            }
        }
        return YtMusicRepository.playlistEntries(playlist.id).getOrNull()
            ?.map { it.toMochySong().toMediaItem() }.orEmpty()
    }

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
                actions = buildList {
                    if (true) {
                        playlist.playEndpoint?.let { playEndpoint ->
                            add(
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
                                        playerConnection.playQueue(YouTubeQueue(playEndpoint))
                                        onDismiss()
                                    }
                                )
                            )
                        }
                        playlist.shuffleEndpoint?.let { shuffleEndpoint ->
                            add(
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
                                        playerConnection.playQueue(YouTubeQueue(shuffleEndpoint))
                                        onDismiss()
                                    }
                                )
                            )
                        }
                        playlist.radioEndpoint?.let { radioEndpoint ->
                            add(
                                NewAction(
                                    icon = {
                                        Icon(
                                            painter = painterResource(R.drawable.radio),
                                            contentDescription = null,
                                            modifier = Modifier.size(28.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    },
                                    text = stringResource(R.string.start_radio),
                                    onClick = {
                                        playerConnection.playQueue(YouTubeQueue(radioEndpoint))
                                        onDismiss()
                                    }
                                )
                            )
                        }
                    }
                },
                modifier = Modifier.padding(bottom = 12.dp)
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
                                coroutineScope.launch {
                                    playerConnection.playNext(queueItems(resizeThumbnails = true))
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
                                coroutineScope.launch {
                                    playerConnection.addToQueue(queueItems(resizeThumbnails = false))
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
                            Timber.w("YouTubePlaylistMenu: add-to-playlist needs Phase 3 (AddToPlaylistDialog)")
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
                                Timber.w("YouTubePlaylistMenu: pin needs Phase 3 (pin store)")
                            }
                        }
                    )
                )
            )
        }

        item { Spacer(modifier = Modifier.height(12.dp)) }

        item {
            Material3MenuGroup(
                items = buildList {
                    if (songs.isNotEmpty()) {
                        add(
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
                                            showRemoveDownloadDialog = true
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
                                            showRemoveDownloadDialog = true
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
                    }
                    add(
                        Material3MenuItemData(
                            title = { Text(text = stringResource(R.string.share)) },
                            description = { Text(text = stringResource(R.string.share_desc)) },
                            icon = {
                                Icon(
                                    painter = painterResource(R.drawable.share),
                                    contentDescription = null,
                                )
                            },
                            onClick = {
                                context.shareText(playlist.shareLink)
                                onDismiss()
                            }
                        )
                    )
                    if (canSelect) {
                        add(
                            Material3MenuItemData(
                                title = { Text(text = stringResource(R.string.select)) },
                                icon = {
                                    Icon(
                                        painter = painterResource(R.drawable.select_all),
                                        contentDescription = null,
                                    )
                                },
                                onClick = {
                                    onDismiss()
                                    selectAction()
                                }
                            )
                        )
                    }
                }
            )
        }
    }
}
