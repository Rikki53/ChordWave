// Ported from Mochy Music (GPL-3.0): ui/menu/AddToPlaylistDialogOnline.kt
// AddToPlaylistDialogOnline.kt
//
// ADAPTATIONS:
// - The duplicate dialog is DROPPED: dead in Mochy (`showDuplicateDialog`
//   was never set true, and `songIds` / `duplicates` were never assigned).
// - Per-song search is simplified: Mochy's viewStateMap / ItemsPage dance
//   becomes taking the first SongItem directly from
//   YtMusicRepository.searchPage(query, CwSearchFilter.SONGS) ->
//   page.rows.mapNotNull { it.toYTItem() }. The found song is added to an
//   online playlist via YtMusicRepository.addToPlaylist; a local playlist
//   is a STUB (Timber.w + TODO — no CW store). The "Liked songs" row likes
//   the found song via MenuActions.toggleSongLike.
// - The work runs SEQUENTIALLY in one IO coroutine instead of Mochy's
//   nested launches (which raced the progress counter), and
//   onProgressStart(false) now fires at TRUE completion — Mochy fired it
//   at second-to-last (songsIdx == songsTot - 1).

package com.music.bitchord.ui.mochy.player.menu

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.music.bitchord.R
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.model.SearchFilter as CwSearchFilter
import com.music.bitchord.ui.mochy.component.CreatePlaylistDialog
import com.music.bitchord.ui.mochy.component.ListDialog
import com.music.bitchord.ui.mochy.component.ListItem
import com.music.bitchord.ui.mochy.component.PlaylistListItem
import com.music.bitchord.ui.mochy.component.SortHeader
import com.music.bitchord.ui.mochy.constants.AddToPlaylistSortDescendingKey
import com.music.bitchord.ui.mochy.constants.AddToPlaylistSortTypeKey
import com.music.bitchord.ui.mochy.constants.ListThumbnailSize
import com.music.bitchord.ui.mochy.constants.PlaylistSortType
import com.music.bitchord.ui.mochy.models.Song
import com.music.innertube.models.SongItem
import com.music.bitchord.ui.mochy.utils.listItemShape
import com.music.bitchord.ui.mochy.utils.rememberEnumPreference
import com.music.bitchord.ui.mochy.utils.rememberPreference
import com.music.bitchord.ui.mochy.viewmodels.PlaylistsViewModel
import com.music.bitchord.ui.mochy.viewmodels.toYTItem
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber

@Composable
fun AddToPlaylistDialogOnline(
    isVisible: Boolean,
    allowSyncing: Boolean = true,
    initialTextFieldValue: String? = null,
    songs: SnapshotStateList<Song>,
    onDismiss: () -> Unit,
    onProgressStart: (Boolean) -> Unit,
    onPercentageChange: (Int) -> Unit,
    viewModel: PlaylistsViewModel = hiltViewModel()
) {
    val coroutineScope = rememberCoroutineScope()
    val (sortType, onSortTypeChange) = rememberEnumPreference(
        AddToPlaylistSortTypeKey,
        PlaylistSortType.NAME
    )
    val (sortDescending, onSortDescendingChange) = rememberPreference(
        AddToPlaylistSortDescendingKey,
        false
    )
    val playlists by viewModel.allPlaylists.collectAsState()

    var showCreatePlaylistDialog by rememberSaveable {
        mutableStateOf(false)
    }

    var searchQuery by rememberSaveable {
        mutableStateOf("")
    }
    val filteredPlaylists = remember(playlists, searchQuery) {
        if (searchQuery.isBlank()) {
            playlists
        } else {
            playlists.filter { it.playlist.name.contains(searchQuery, ignoreCase = true) }
        }
    }

    // Resolves each song to its YouTube id by searching "title - artists",
    // then hands the id to [onFound]. Runs sequentially so the progress
    // counts are exact.
    val resolveAll: (onFound: suspend (Song, String) -> Unit) -> Unit = { onFound ->
        coroutineScope.launch(Dispatchers.IO) {
            onDismiss()
            val total = songs.count().toDouble()
            var done = 0.0
            onProgressStart(true)
            songs.reversed().forEach { song ->
                try {
                    var allArtists = ""
                    song.artists.forEach { artist ->
                        allArtists += " ${URLDecoder.decode(artist.name, StandardCharsets.UTF_8.toString())}"
                    }
                    val query = "${song.title} - $allArtists"
                    val firstSong = YtMusicRepository.searchPage(query, CwSearchFilter.SONGS)
                        .getOrNull()?.rows
                        ?.mapNotNull { it.toYTItem() }
                        ?.filterIsInstance<SongItem>()
                        ?.firstOrNull()
                    if (firstSong != null) {
                        onFound(song, firstSong.id)
                    }
                } catch (e: Exception) {
                    Timber.tag("ERROR").v(e.toString())
                }
                done += 1
                onPercentageChange(((done / total) * 100).toInt())
            }
            onProgressStart(false)
        }
    }

    // The list steps aside while the create sheet is up: that is a sheet in the app's own window,
    // and a platform dialog left open would sit on top of it.
    if (isVisible && !showCreatePlaylistDialog) {
        val totalItemsCount = 1 + filteredPlaylists.size
        ListDialog(
            onDismiss = onDismiss
        ) {
            item {
                ListItem(
                    title = stringResource(R.string.create_playlist),
                    thumbnailContent = {
                        Image(
                            painter = painterResource(id = R.drawable.playlist_add),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onBackground),
                            modifier = Modifier.size(ListThumbnailSize)
                        )
                    },
                    shape = listItemShape(index = 0, count = totalItemsCount),
                    modifier = Modifier.clickable {
                        showCreatePlaylistDialog = true
                    }
                )
            }

            if (playlists.isNotEmpty()) {
                item {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = {
                            Text(
                                text = stringResource(R.string.search),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(
                                        painter = painterResource(R.drawable.close),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                            cursorColor = MaterialTheme.colorScheme.primary,
                        ),
                        textStyle = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }
            }

            if (playlists.isNotEmpty()) {
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 16.dp),
                    ) {
                        SortHeader(
                            sortType = sortType,
                            sortDescending = sortDescending,
                            onSortTypeChange = onSortTypeChange,
                            onSortDescendingChange = onSortDescendingChange,
                            sortTypeText = { sortType ->
                                when (sortType) {
                                    PlaylistSortType.CREATE_DATE -> R.string.sort_by_create_date
                                    PlaylistSortType.NAME -> R.string.sort_by_name
                                    PlaylistSortType.SONG_COUNT -> R.string.sort_by_song_count
                                    PlaylistSortType.LAST_UPDATED -> R.string.sort_by_last_updated
                                }
                            },
                        )
                    }
                }
            }

            if (filteredPlaylists.isEmpty() && searchQuery.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.no_results_found),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }

            itemsIndexed(filteredPlaylists) { index, playlist ->
                PlaylistListItem(
                    playlist = playlist,
                    shape = listItemShape(index = index + 1, count = totalItemsCount),
                    modifier = Modifier.clickable {
                        val browseId = playlist.playlist.browseId
                        // resolveAll calls back once per song; the honest
                        // answer for a local playlist is said once, not once
                        // per song (see AddToPlaylistDialog.pick).
                        var warned = false
                        resolveAll { _, videoId ->
                            if (browseId != null) {
                                YtMusicRepository.addToPlaylist(browseId, listOf(videoId))
                            } else if (!warned) {
                                warned = true
                                Timber.w("AddToPlaylistDialogOnline: cannot add to local playlist ${playlist.id} — no store")
                                announcePlaylistAddFailed(playlist)
                            }
                        }
                    }
                )
            }

            item {
                ListItem(
                    modifier = Modifier.clickable {
                        resolveAll { _, videoId ->
                            coroutineScope.toggleSongLike(videoId, true)
                        }
                    },
                    title = stringResource(R.string.liked_songs),
                    thumbnailContent = {
                        Image(
                            painter = painterResource(id = R.drawable.favorite),
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onBackground)
                        )
                    },
                    trailingContent = {}
                )
            }

            item {
                Text(
                    text = stringResource(R.string.playlist_add_local_to_synced_note),
                    fontSize = TextUnit(12F, TextUnitType.Sp),
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            }
        }
    }

    if (showCreatePlaylistDialog) {
        CreatePlaylistDialog(
            onDismiss = { showCreatePlaylistDialog = false },
            initialTextFieldValue = initialTextFieldValue,
            allowSyncing = allowSyncing
        )
    }
}
