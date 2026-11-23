// Ported from Mochy Music (GPL-3.0): ui/menu/SelectionSongsMenu.kt
// SelectionSongsMenu.kt (the composable is SelectionSongMenu, singular —
// matching what the ported screens call)
//
// ADAPTATIONS:
// - Library/like writes go through MenuActions (sendLibraryFeedback /
//   toggleSongLike) instead of Mochy's database + innertube calls.
// - Download state via staged downloadStateForAll; enqueue/remove via
//   Downloads.enqueue / MenuActions.removeDownload.
// - AddToPlaylistDialog's onGetSong just returns the ids: the dialog
//   itself does the online add in `pick` (Mochy added to the synced
//   playlist here AND returned ids — the port must not double-add).
// - "Delete from playlist" row: wired through the onDeleteFromPlaylist
//   callback, which the playlist screen supplies (LocalPlaylistViewModel
//   .removeSong handles offline and online entries alike). The row only
//   appears when the caller passes both the entry maps and the callback,
//   so it never shows where it can't act.
// - Dropped Mochy's dead `notAddedList` and the dead `isPortrait` /
//   `configuration` reads.

package com.music.bitchord.ui.mochy.player.menu

import android.annotation.SuppressLint
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.exoplayer.offline.Download
import com.music.bitchord.R
import com.music.bitchord.download.Downloads
import com.music.bitchord.ui.mochy.component.DefaultDialog
import com.music.bitchord.ui.mochy.component.Material3MenuGroup
import com.music.bitchord.ui.mochy.component.Material3MenuItemData
import com.music.bitchord.ui.mochy.component.NewAction
import com.music.bitchord.ui.mochy.component.NewActionGrid
import com.music.bitchord.ui.mochy.component.downloadStateForAll
import com.music.bitchord.ui.mochy.component.toCwSong
import com.music.bitchord.ui.mochy.extensions.toMediaItem
import com.music.bitchord.ui.mochy.models.PlaylistSongMap
import com.music.bitchord.ui.mochy.models.Song
import com.music.bitchord.ui.mochy.playback.LocalPlayerConnection
import com.music.bitchord.ui.mochy.playback.queues.ListQueue
import timber.log.Timber

@SuppressLint("MutableCollectionMutableState")
@Composable
fun SelectionSongMenu(
    songSelection: List<Song>,
    onDismiss: () -> Unit,
    clearAction: () -> Unit,
    songPosition: List<PlaylistSongMap>? = emptyList(),
    /** Removes the selected entries from their playlist; null = the delete row is hidden. */
    onDeleteFromPlaylist: ((List<PlaylistSongMap>) -> Unit)? = null,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val playerConnection = LocalPlayerConnection.current ?: return

    val allInLibrary by remember {
        mutableStateOf(
            songSelection.all {
                it.song.inLibrary != null
            },
        )
    }

    val allLiked by remember(songSelection) {
        mutableStateOf(
            songSelection.isNotEmpty() && songSelection.all {
                it.song.liked
            },
        )
    }

    val downloadState = downloadStateForAll(songSelection.map { it.id })

    var showChoosePlaylistDialog by rememberSaveable {
        mutableStateOf(false)
    }

    AddToPlaylistDialog(
        isVisible = showChoosePlaylistDialog,
        onGetSong = { songSelection.map { it.id } },
        onDismiss = {
            showChoosePlaylistDialog = false
        },
    )

    var showRemoveDownloadDialog by remember {
        mutableStateOf(false)
    }

    if (showRemoveDownloadDialog) {
        DefaultDialog(
            onDismiss = { showRemoveDownloadDialog = false },
            content = {
                Text(
                    text = stringResource(R.string.remove_download_playlist_confirm, "selection"),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 18.dp),
                )
            },
            buttons = {
                TextButton(
                    onClick = {
                        showRemoveDownloadDialog = false
                    },
                ) {
                    Text(text = stringResource(android.R.string.cancel))
                }

                TextButton(
                    onClick = {
                        showRemoveDownloadDialog = false
                        songSelection.forEach { song ->
                            coroutineScope.removeDownload(context, song.song.id)
                        }
                    },
                ) {
                    Text(text = stringResource(android.R.string.ok))
                }
            },
        )
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
                actions = listOfNotNull(
                    if (true) NewAction(
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
                            playerConnection.playQueue(
                                ListQueue(
                                    title = "Selection",
                                    items = songSelection.map { it.toMediaItem() },
                                ),
                            )
                            clearAction()
                        }
                    ) else null,
                    NewAction(
                        icon = {
                            Icon(
                                painter = painterResource(R.drawable.playlist_add),
                                contentDescription = null,
                                modifier = Modifier.size(28.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        text = stringResource(R.string.add_to_playlist),
                        onClick = {
                            showChoosePlaylistDialog = true
                        }
                    )
                ),
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }
        item {
            Material3MenuGroup(
                items = buildList {
                    if (true) {
                        add(
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
                                    onDismiss()
                                    playerConnection.playNext(songSelection.map { it.toMediaItem() })
                                    clearAction()
                                }
                            )
                        )
                        add(
                            Material3MenuItemData(
                                title = { Text(text = stringResource(R.string.shuffle)) },
                                description = { Text(text = stringResource(R.string.add_to_queue_desc)) },
                                icon = {
                                    Icon(
                                        painter = painterResource(R.drawable.shuffle),
                                        contentDescription = null,
                                    )
                                },
                                onClick = {
                                    onDismiss()
                                    playerConnection.playQueue(
                                        ListQueue(
                                            title = "Selection",
                                            items = songSelection.shuffled().map { it.toMediaItem() },
                                        ),
                                    )
                                    clearAction()
                                }
                            )
                        )
                        add(
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
                                    playerConnection.addToQueue(songSelection.map { it.toMediaItem() })
                                    clearAction()
                                }
                            )
                        )
                    }
                    add(
                        Material3MenuItemData(
                            title = {
                                Text(
                                    text = stringResource(
                                        if (allInLibrary) R.string.remove_from_library else R.string.add_to_library
                                    )
                                )
                            },
                            icon = {
                                Icon(
                                    painter = painterResource(
                                        if (allInLibrary) R.drawable.library_add_check else R.drawable.library_add
                                    ),
                                    contentDescription = null,
                                )
                            },
                            onClick = {
                                songSelection.forEach { song ->
                                    coroutineScope.sendLibraryFeedback(
                                        videoId = song.id,
                                        adding = !allInLibrary,
                                        addToken = song.song.libraryAddToken,
                                        removeToken = song.song.libraryRemoveToken,
                                    )
                                }
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
                                    icon = {
                                        Icon(
                                            painter = painterResource(R.drawable.download),
                                            contentDescription = null,
                                        )
                                    },
                                    onClick = {
                                        songSelection.forEach { song ->
                                            Downloads.enqueue(context, song.toCwSong())
                                        }
                                    }
                                )
                            }
                        }
                    )
                    add(
                        Material3MenuItemData(
                            title = {
                                Text(
                                    text = stringResource(
                                        if (allLiked) R.string.dislike_all else R.string.like_all
                                    )
                                )
                            },
                            icon = {
                                Icon(
                                    painter = painterResource(
                                        if (allLiked) R.drawable.favorite else R.drawable.favorite_border
                                    ),
                                    contentDescription = null,
                                )
                            },
                            onClick = {
                                val likedNow = songSelection.all { it.song.liked }
                                onDismiss()
                                songSelection.forEach { song ->
                                    coroutineScope.toggleSongLike(song.id, !likedNow)
                                }
                            }
                        )
                    )
                    if (songPosition?.isNotEmpty() == true && onDeleteFromPlaylist != null) {
                        add(
                            Material3MenuItemData(
                                title = { Text(text = stringResource(R.string.delete)) },
                                icon = {
                                    Icon(
                                        painter = painterResource(R.drawable.delete),
                                        contentDescription = null,
                                    )
                                },
                                onClick = {
                                    onDeleteFromPlaylist(songPosition)
                                    onDismiss()
                                    clearAction()
                                }
                            )
                        )
                    }
                }
            )
        }
    }
}
