// Ported from Mochy Music (GPL-3.0): ui/menu/YouTubeSelectionSongMenu.kt
// YouTubeSelectionSongMenu.kt
//
// ADAPTATIONS:
// - Library/like writes go through MenuActions (sendLibraryFeedback /
//   toggleSongLike) instead of Mochy's database + innertube calls.
// - Download state via staged downloadStateForAll; enqueue/remove via
//   Downloads.enqueue / MenuActions.removeDownload.
// - allLiked / allInLibrary are computed from SongItem.toMediaMetadata()
//   exactly as Mochy did; the staged mapper sets liked=false and
//   inLibrary=null, so both are always false — the same as Mochy, where
//   a fresh YouTube item had no local row either. Kept faithful.
// - The block that builds Song entities from SongItem metadata for
//   AddToPlaylistDialogOnline is kept verbatim (the dialog needs Songs).

package com.music.bitchord.ui.mochy.player.menu

import com.music.bitchord.ui.mochy.player.LikeRed

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
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
import androidx.compose.runtime.toMutableStateList
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
import com.music.bitchord.ui.mochy.component.SheetActionCard
import com.music.bitchord.ui.mochy.component.SheetActionCardRow
import com.music.bitchord.ui.mochy.component.downloadStateForAll
import com.music.bitchord.ui.mochy.component.toCwSong
import com.music.bitchord.ui.mochy.extensions.toMediaItem
import com.music.bitchord.ui.mochy.models.AlbumEntity
import com.music.bitchord.ui.mochy.models.ArtistEntity
import com.music.bitchord.ui.mochy.models.Song
import com.music.bitchord.ui.mochy.models.SongEntity
import com.music.bitchord.ui.mochy.models.innertube.SongItem
import com.music.bitchord.ui.mochy.models.toMediaMetadata
import com.music.bitchord.ui.mochy.playback.LocalPlayerConnection
import com.music.bitchord.ui.mochy.playback.queues.ListQueue

@Composable
fun YouTubeSelectionSongMenu(
    songSelection: List<SongItem>,
    onDismiss: () -> Unit,
    clearAction: () -> Unit,
) {
    val context = LocalContext.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val coroutineScope = rememberCoroutineScope()

    var showChoosePlaylistDialog by rememberSaveable {
        mutableStateOf(false)
    }

    val downloadState = downloadStateForAll(songSelection.map { it.id })

    var showRemoveDownloadDialog by remember {
        mutableStateOf(false)
    }

    val allLiked by remember(songSelection) {
        mutableStateOf(
            songSelection.isNotEmpty() && songSelection.all { song ->

                val metadata = song.toMediaMetadata()
                metadata.liked
            }
        )
    }

    val allInLibrary by remember(songSelection) {
        mutableStateOf(
            songSelection.all { song ->
                val metadata = song.toMediaMetadata()
                metadata.inLibrary != null
            }
        )
    }

    AddToPlaylistDialogOnline(
        isVisible = showChoosePlaylistDialog,
        songs = remember {
            songSelection.map { song ->

                val metadata = song.toMediaMetadata()
                Song(
                    song = SongEntity(
                        id = metadata.id,
                        title = metadata.title,
                        duration = metadata.duration,
                        thumbnailUrl = metadata.thumbnailUrl,
                        albumId = metadata.album?.id,
                        albumName = metadata.album?.title,
                        liked = metadata.liked,
                        totalPlayTime = 0,
                        inLibrary = metadata.inLibrary,
                        isLocal = false,
                        libraryAddToken = metadata.libraryAddToken,
                        libraryRemoveToken = metadata.libraryRemoveToken
                    ),
                    artists = metadata.artists.map { artist ->
                        ArtistEntity(
                            id = artist.id ?: "",
                            name = artist.name
                        )
                    },
                    album = metadata.album?.let { album ->
                        AlbumEntity(
                            id = album.id,
                            title = album.title,
                            thumbnailUrl = metadata.thumbnailUrl,
                            songCount = 0,
                            duration = 0
                        )
                    }
                )
            }.toMutableStateList()
        },
        onProgressStart = { },
        onPercentageChange = { },
        onDismiss = {
            showChoosePlaylistDialog = false
        },
    )

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
                            coroutineScope.removeDownload(context, song.id)
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
            top = 8.dp,
            end = 0.dp,
            bottom = 8.dp + WindowInsets.systemBars.asPaddingValues().calculateBottomPadding(),
        ),
    ) {
        item {
            // Playing the picked songs and saving them to a playlist are why people select songs.
            SheetActionCardRow(modifier = Modifier.padding(bottom = 10.dp)) {
                SheetActionCard(
                    icon = R.drawable.play,
                    title = stringResource(R.string.play),
                    onClick = {
                        playerConnection.playQueue(
                            ListQueue(
                                title = context.getString(R.string.queue_all_songs),
                                items = songSelection.map { it.toMediaItem() },
                            ),
                        )
                        clearAction()
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                )
                SheetActionCard(
                    icon = R.drawable.playlist_add,
                    title = stringResource(R.string.add_to_playlist),
                    onClick = { showChoosePlaylistDialog = true },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        item {
            Material3MenuGroup(
                listOfNotNull(
                    if (true) Material3MenuItemData(
                        icon = { Icon(painterResource(R.drawable.shuffle), null) },
                        title = { Text(stringResource(R.string.shuffle)) },
                        onClick = {
                            playerConnection.playQueue(
                                ListQueue(
                                    title = context.getString(R.string.queue_all_songs),
                                    items = songSelection.shuffled().map { it.toMediaItem() },
                                ),
                            )
                            clearAction()
                            onDismiss()
                        },
                    ) else null,
                    if (true) Material3MenuItemData(
                        icon = { Icon(painterResource(R.drawable.queue_music), null) },
                        title = { Text(stringResource(R.string.add_to_queue)) },
                        onClick = {
                            playerConnection.addToQueue(songSelection.map { it.toMediaItem() })
                            clearAction()
                            onDismiss()
                        },
                    ) else null,
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
                                    addToken = song.libraryAddToken,
                                    removeToken = song.libraryRemoveToken,
                                )
                            }
                            clearAction()
                            onDismiss()
                        }
                    ),
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
                                    clearAction()
                                    onDismiss()
                                }
                            )
                        }
                    },
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
                                tint = if (allLiked) LikeRed else LocalContentColor.current,
                            )
                        },
                        onClick = {
                            songSelection.forEach { song ->
                                coroutineScope.toggleSongLike(song.id, !allLiked)
                            }
                            clearAction()
                            onDismiss()
                        }
                    )
                ),
            )
        }
    }
}
