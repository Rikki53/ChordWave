// Ported from Mochy Music (GPL-3.0): ui/menu/PlaylistMenu.kt
// PlaylistMenu.kt
//
// ADAPTATIONS:
// - navController: NavController? -> navigator: MochyNavigator? (only used
//   by Re-sync to open a sign-in page; the resync state is a stub until
//   a resync engine exists — see PlaylistResyncMenu.kt).
// - dbPlaylist: the passed `playlist` (no database flow to observe).
// - Songs: from `songList` when autoPlaylist; else resolved here —
//   OfflinePlaylists for a local playlist, YtMusicRepository.playlistEntries
//   for a YouTube one — mapped with CwSong.toMochySong. Play/Shuffle/Play
//   next/Add to queue/Download all run off that list.
// - Save toggle: local rememberSaveable state + YtMusicRepository.setSaved
//   when the playlist has a browseId (YouTubePlaylistMenu pattern).
// - Pin: isPinned=false; tapping logs a Phase 3 TODO (no speed-dial store).
// - Delete: offline browseId -> OfflinePlaylists.delete; YouTube browseId
//   -> YtMusicRepository.deletePlaylist; anything else -> Timber.w TODO.
// - Radio: builds the innertube radio-for-playlist endpoint directly
//   (RDAMPL + browseId — the RD-prefix convention the staged YouTubeQueue
//   already uses for song radio, RDAMVM). Mochy fetched the playlist page
//   for its radioEndpoint; CW has no playlist fetch returning one.
//   YouTubeQueue itself is stubbed until Phase 3 either way.
// - Dropped dead `isPortrait` / `configuration` reads and the unused
//   TextRange / TextFieldValue / PlaylistListItem / IconButton /
//   LocalContentColor imports.

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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.media3.exoplayer.offline.Download
import com.music.bitchord.R
import com.music.bitchord.data.OfflinePlaylists
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.download.Downloads
import com.music.bitchord.ui.mochy.component.DefaultDialog
import com.music.bitchord.ui.mochy.component.LocalMenuState
import com.music.bitchord.ui.mochy.component.Material3MenuGroup
import com.music.bitchord.ui.mochy.component.Material3MenuItemData
import com.music.bitchord.ui.mochy.component.NewAction
import com.music.bitchord.ui.mochy.component.NewActionGrid
import com.music.bitchord.ui.mochy.component.SheetHeader
import com.music.bitchord.ui.mochy.component.SheetHeaderButton
import com.music.bitchord.ui.mochy.component.SheetHeaderGap
import com.music.bitchord.ui.mochy.component.downloadStateForAll
import com.music.bitchord.ui.mochy.component.showEditPlaylistSheet
import com.music.bitchord.ui.mochy.component.toCwSong
import com.music.bitchord.ui.mochy.extensions.toMediaItem
import com.music.bitchord.ui.mochy.models.Playlist
import com.music.bitchord.ui.mochy.models.Song
import com.music.bitchord.ui.mochy.models.toMochySong
import com.music.bitchord.ui.mochy.models.innertube.WatchEndpoint
import com.music.bitchord.ui.mochy.navigation.MochyNavigator
import com.music.bitchord.ui.mochy.playback.LocalPlayerConnection
import com.music.bitchord.ui.mochy.playback.queues.ListQueue
import com.music.bitchord.ui.mochy.playback.queues.YouTubeQueue
import com.music.bitchord.ui.mochy.playlistcover.PlaylistCoverStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber

@Composable
fun PlaylistMenu(
    playlist: Playlist,
    coroutineScope: CoroutineScope,
    onDismiss: () -> Unit,
    autoPlaylist: Boolean? = false,
    downloadPlaylist: Boolean? = false,
    songList: List<Song>? = emptyList(),
    /** Lets Re-sync open a service's sign-in page for a playlist imported from an account. */
    navigator: MochyNavigator? = null,
) {
    val context = LocalContext.current
    val playerConnection = LocalPlayerConnection.current ?: return
    // The rows below (Play, Shuffle, Play next, Add to queue, Download, the
    // edit sheet's cover song) all run off this list. It used to be empty
    // for every non-auto playlist — the header claimed no CW store holds
    // membership and no CW Song -> mochy Song mapper exists — but both exist
    // now: OfflinePlaylists for local playlists, playlistEntries for
    // YouTube ones, and CwSong.toMochySong maps either.
    var songs by remember { mutableStateOf<List<Song>>(emptyList()) }
    // The download row only appears once this is true, so a tap during the
    // fetch can't enqueue an empty list and look like a dead button.
    var songsLoaded by remember { mutableStateOf(false) }

    LaunchedEffect(autoPlaylist, songList, playlist.id) {
        songsLoaded = false
        songs = when {
            autoPlaylist == true -> songList ?: emptyList()
            else -> {
                val browseId = playlist.playlist.browseId
                when {
                    browseId == null -> {
                        Timber.w("PlaylistMenu: playlist ${playlist.id} has no browseId, songs unknown")
                        emptyList()
                    }
                    OfflinePlaylists.isOfflinePlaylist(browseId) ->
                        OfflinePlaylists.get(browseId)?.songs.orEmpty().map { it.toMochySong() }
                    else ->
                        YtMusicRepository.playlistEntries(browseId).getOrNull().orEmpty().map { it.toMochySong() }
                }
            }
        }
        songsLoaded = true
    }

    val downloadState = downloadStateForAll(songs.map { it.id })

    val editable: Boolean = playlist.playlist.isEditable == true

    // TODO(Phase 3): no speed-dial store in CW.
    val isPinned = false
    val resync = rememberPlaylistResync(playlist.id, playlist.playlist.browseId, navigator, onDismiss)

    var saved by rememberSaveable { mutableStateOf(playlist.playlist.bookmarkedAt != null) }

    val menuState = LocalMenuState.current

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
                        playlist.playlist.name
                    ),
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
                        songs.forEach { song ->
                            coroutineScope.removeDownload(context, song.id)
                        }
                    },
                ) {
                    Text(text = stringResource(android.R.string.ok))
                }
            },
        )
    }

    var showDeletePlaylistDialog by remember {
        mutableStateOf(false)
    }

    if (showDeletePlaylistDialog) {
        DefaultDialog(
            onDismiss = { showDeletePlaylistDialog = false },
            content = {
                Text(
                    text = stringResource(R.string.delete_playlist_confirm, playlist.playlist.name),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 18.dp)
                )
            },
            buttons = {
                TextButton(
                    onClick = {
                        showDeletePlaylistDialog = false
                    }
                ) {
                    Text(text = stringResource(android.R.string.cancel))
                }

                TextButton(
                    onClick = {
                        showDeletePlaylistDialog = false
                        onDismiss()
                        val browseId = playlist.playlist.browseId
                        when {
                            browseId != null && OfflinePlaylists.isOfflinePlaylist(browseId) -> {
                                OfflinePlaylists.delete(browseId)
                                // Its generated or picked cover is no use to anyone now.
                                PlaylistCoverStore.deleteStale(context.applicationContext, playlist.playlist.id, null)
                            }
                            browseId != null -> coroutineScope.launch {
                                YtMusicRepository.deletePlaylist(browseId)
                            }
                            else -> Timber.w(
                                "PlaylistMenu: cannot delete playlist ${playlist.id} — no browseId and no CW store"
                            )
                        }
                    }
                ) {
                    Text(text = stringResource(android.R.string.ok))
                }
            }
        )
    }

    // The sheet's header: cover, name, size - and save, for playlists that aren't the user's own.
    SheetHeader(
        thumbnailUrl = playlist.thumbnails.firstOrNull(),
        title = playlist.playlist.name,
        subtitle = if (playlist.songCount == 1) "1 song" else "${playlist.songCount} songs",
        trailing = if (playlist.playlist.isEditable != true) {
            {
                SheetHeaderButton(
                    icon = if (saved) R.drawable.favorite else R.drawable.favorite_border,
                    tint = if (saved) MaterialTheme.colorScheme.error else Color.White,
                    onClick = {
                        saved = !saved
                        playlist.playlist.browseId?.let { browseId ->
                            coroutineScope.launch {
                                YtMusicRepository.setSaved(browseId, saved)
                            }
                        }
                    },
                )
            }
        } else null,
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
                                            title = playlist.playlist.name,
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
                                            title = playlist.playlist.name,
                                            items = songs.shuffled().map(Song::toMediaItem)
                                        )
                                    )
                                }
                            }
                        ),
                ),
                modifier = Modifier.padding(bottom = 12.dp),
                columns = if (false) 1 else 3
            )
        }

        item {
            Material3MenuGroup(
                items = buildList {
                    if (true) {
                        playlist.playlist.browseId?.let { browseId ->
                            add(
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
                                        // RDAMPL is the innertube radio-for-playlist id prefix
                                        // (cf. RDAMVM for song radio in YouTubeQueue).
                                        playerConnection.playQueue(
                                            YouTubeQueue(WatchEndpoint(playlistId = "RDAMPL$browseId"))
                                        )
                                        onDismiss()
                                    }
                                )
                            )
                        }
                    }
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
                                    coroutineScope.launch {
                                        playerConnection.playNext(songs.map { it.toMediaItem() })
                                    }
                                    onDismiss()
                                }
                            )
                        )
                    }
                    if (true) {
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
                                    playerConnection.addToQueue(songs.map { it.toMediaItem() })
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
                    if (editable && autoPlaylist != true) {
                        add(
                            Material3MenuItemData(
                                title = { Text(text = stringResource(R.string.edit)) },
                                description = { Text(text = stringResource(R.string.edit_desc)) },
                                icon = {
                                    Icon(
                                        painter = painterResource(R.drawable.edit),
                                        contentDescription = null,
                                    )
                                },
                                // Opens over this menu, which comes back forward behind it.
                                onClick = {
                                    menuState.showEditPlaylistSheet(
                                        playlist = playlist.playlist,
                                        context = context,
                                        topSongId = songs.firstOrNull()?.id,
                                        topSongArtworkUrl = songs.firstOrNull()?.song?.thumbnailUrl,
                                    )
                                }
                            )
                        )
                    }
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
                                // TODO(Phase 3): no speed-dial store in CW.
                                Timber.w("PlaylistMenu: pin to library is not wired yet")
                                onDismiss()
                            }
                        )
                    )
                    resync.menuItem()?.let(::add)
                    if (downloadPlaylist != true && songsLoaded) {
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
                                                Downloads.enqueue(context, song.toCwSong())
                                            }
                                        }
                                    )
                                }
                            }
                        )
                    }
                    if (autoPlaylist != true) {
                        add(
                            Material3MenuItemData(
                                title = { Text(text = stringResource(R.string.delete)) },
                                description = { Text(text = stringResource(R.string.delete_desc)) },
                                icon = {
                                    Icon(
                                        painter = painterResource(R.drawable.delete),
                                        contentDescription = null,
                                    )
                                },
                                onClick = {
                                    showDeletePlaylistDialog = true
                                }
                            )
                        )
                    }
                    playlist.playlist.shareLink?.let { shareLink ->
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
                                    context.shareText(shareLink)
                                    onDismiss()
                                }
                            )
                        )
                    }
                }
            )
        }
    }
}
