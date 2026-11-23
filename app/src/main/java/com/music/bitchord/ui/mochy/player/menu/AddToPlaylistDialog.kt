// Ported from Mochy Music (GPL-3.0): ui/menu/AddToPlaylistDialog.kt
// AddToPlaylistDialog.kt
//
// ADAPTATIONS:
// - No database: the duplicates check fetches the playlist's current
//   entries from YouTube (YtMusicRepository.playlistEntries) when it has
//   a browseId, and is skipped (empty list) for local playlists — no CW
//   store holds their membership. Failures read as "no duplicates".
// - The add itself: online playlists go through
//   YtMusicRepository.addToPlaylist (real); local playlists are a STUB
//   (Timber.w + TODO) — OfflinePlaylists.add drops songs without a
//   localUri, and this dialog only receives video ids. PlaylistAddedHere
//   is skipped (no importer).
// - announceAddedToPlaylist is adapted: no DB, so the first song's title
//   is unavailable — the 1-song toast uses Mochy's "song" fallback, with
//   no thumbnail, and Undo is never offered (nothing undoable without
//   a store).

package com.music.bitchord.ui.mochy.player.menu

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.music.bitchord.R
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.ui.mochy.component.CreatePlaylistDialog
import com.music.bitchord.ui.mochy.component.DefaultDialog
import com.music.bitchord.ui.mochy.component.LocalMenuState
import com.music.bitchord.ui.mochy.component.PlaylistThumbnail
import com.music.bitchord.ui.mochy.component.PopupSheetHandle
import com.music.bitchord.ui.mochy.component.RemoteFocus
import com.music.bitchord.ui.mochy.component.SheetLabel
import com.music.bitchord.ui.mochy.component.SheetPillButton
import com.music.bitchord.ui.mochy.component.SheetSearchField
import com.music.bitchord.ui.mochy.component.TopToaster
import com.music.bitchord.ui.mochy.models.Playlist
import com.music.bitchord.ui.mochy.viewmodels.PlaylistsViewModel
import java.text.Collator
import java.time.LocalDateTime
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber

/** How many playlists the "recents" section shows. */
private const val RecentCount = 4

/**
 * "Add to playlist", as a sheet (the first mock): a search field, the playlists used most
 * recently, all the others, and Create playlist pinned below. Shown through the app's popup
 * system while [isVisible] - over whatever menu opened it, which comes back forward behind it.
 *
 * @param onGetSong the songs to add, given the playlist they are going into.
 * @param topSongId when the songs are being added to a playlist made right here, the first of
 *   them with its [topSongArtworkUrl]: the new playlist's cover takes its colours from it.
 */
/** The top toast for songs just added to [playlist]: what went in. No Undo — nothing is undoable without a store. */
internal fun announceAddedToPlaylist(playlist: Playlist, ids: List<String>) {
    if (ids.isEmpty()) return
    val name = playlist.playlist.name
    TopToaster.show(
        text = if (ids.size == 1) "Added song to $name" else "Added ${ids.size} songs to $name",
        thumbnail = null,
        iconRes = R.drawable.playlist_add,
        actionLabel = null,
        onAction = null,
    )
}

/** The honest answer when the pick couldn't be saved (local playlists, see pick below). */
internal fun announcePlaylistAddFailed(playlist: Playlist) {
    TopToaster.show(
        text = "Couldn't add to ${playlist.playlist.name} yet",
        thumbnail = null,
        iconRes = R.drawable.playlist_add,
        actionLabel = null,
        onAction = null,
    )
}

@Composable
fun AddToPlaylistDialog(
    isVisible: Boolean,
    allowSyncing: Boolean = true,
    initialTextFieldValue: String? = null,
    onGetSong: suspend (Playlist) -> List<String>,
    onDismiss: () -> Unit,
    viewModel: PlaylistsViewModel = hiltViewModel(),
    topSongId: String? = null,
    topSongArtworkUrl: String? = null,
) {
    val menuState = LocalMenuState.current
    val scope = rememberCoroutineScope()

    // Read through these: the sheet is composed by the popup host from lambdas made when it opened.
    val currentOnGetSong by rememberUpdatedState(onGetSong)
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val playlists = viewModel.allPlaylists.collectAsState()

    var showCreate by remember { mutableStateOf(false) }
    var showDuplicate by remember { mutableStateOf(false) }
    var selectedPlaylist by remember { mutableStateOf<Playlist?>(null) }
    var songIds by remember { mutableStateOf<List<String>?>(null) }
    var duplicates by remember { mutableStateOf(emptyList<String>()) }

    // This sheet's own handle, for closing exactly it: Create playlist opens over it.
    val sheet = remember { arrayOfNulls<PopupSheetHandle>(1) }

    val pick: (Playlist) -> Unit = { playlist ->
        selectedPlaylist = playlist
        scope.launch(Dispatchers.IO) {
            if (songIds == null) {
                songIds = currentOnGetSong(playlist)
            }
            val ids = songIds.orEmpty()
            val browseId = playlist.playlist.browseId
            duplicates = if (browseId != null) {
                val existing = runCatching {
                    YtMusicRepository.playlistEntries(browseId).getOrNull().orEmpty()
                        .map { it.videoId }.toSet()
                }.getOrElse { emptySet() }
                ids.filter { it in existing }
            } else {
                emptyList()
            }
            if (duplicates.isNotEmpty()) {
                showDuplicate = true
            } else {
                sheet[0]?.dismiss()
                currentOnDismiss()
                if (browseId != null) {
                    YtMusicRepository.addToPlaylist(browseId, ids)
                    announceAddedToPlaylist(playlist, ids)
                } else {
                    // Local (offline) playlists hold device files by
                    // localUri, and this dialog only carries video ids —
                    // there is nothing to store yet, so say so instead of
                    // announcing a save that never happened.
                    Timber.w("AddToPlaylistDialog: cannot add ${ids.size} songs to local playlist ${playlist.id} — no store")
                    announcePlaylistAddFailed(playlist)
                }
            }
        }
    }

    if (isVisible) {
        DisposableEffect(menuState) {
            // Each opening starts from a clean pick: songs are asked for again.
            songIds = null
            var closed = false
            sheet[0] = menuState.showWithHandle(
                buttonBar = {
                    SheetPillButton(
                        text = "Create playlist",
                        onClick = { showCreate = true },
                    )
                },
                onClosed = {
                    closed = true
                    currentOnDismiss()
                },
            ) {
                AddToPlaylistContent(playlists = playlists, onPick = pick)
            }
            onDispose {
                if (!closed) sheet[0]?.dismiss()
            }
        }
    }

    if (showCreate) {
        CreatePlaylistDialog(
            onDismiss = { showCreate = false },
            initialTextFieldValue = initialTextFieldValue,
            allowSyncing = allowSyncing,
            topSongId = topSongId,
            topSongArtworkUrl = topSongArtworkUrl,
        )
    }

    if (showDuplicate) {
        DefaultDialog(
            title = { Text(stringResource(R.string.duplicates)) },
            buttons = {
                TextButton(
                    onClick = {
                        showDuplicate = false
                        sheet[0]?.dismiss()
                        currentOnDismiss()
                        val keep = songIds!!.filter { !duplicates.contains(it) }
                        val playlist = selectedPlaylist!!
                        scope.launch(Dispatchers.IO) {
                            playlist.playlist.browseId?.let { browseId ->
                                YtMusicRepository.addToPlaylist(browseId, keep)
                            } ?: Timber.w(
                                "AddToPlaylistDialog: cannot add ${keep.size} songs to local playlist ${playlist.id} — no store"
                            )
                            announceAddedToPlaylist(playlist, keep)
                        }
                    }
                ) {
                    Text(stringResource(R.string.skip_duplicates))
                }

                TextButton(
                    onClick = {
                        showDuplicate = false
                        sheet[0]?.dismiss()
                        currentOnDismiss()
                        val playlist = selectedPlaylist!!
                        val ids = songIds!!
                        scope.launch(Dispatchers.IO) {
                            playlist.playlist.browseId?.let { browseId ->
                                YtMusicRepository.addToPlaylist(browseId, ids)
                            } ?: Timber.w(
                                "AddToPlaylistDialog: cannot add ${ids.size} songs to local playlist ${playlist.id} — no store"
                            )
                            announceAddedToPlaylist(playlist, ids)
                        }
                    }
                ) {
                    Text(stringResource(R.string.add_anyway))
                }

                TextButton(
                    onClick = {
                        showDuplicate = false
                    }
                ) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
            onDismiss = {
                showDuplicate = false
            }
        ) {
            Text(
                text = if (duplicates.size == 1) {
                    stringResource(R.string.duplicates_description_single)
                } else {
                    stringResource(R.string.duplicates_description_multiple, duplicates.size)
                },
                textAlign = TextAlign.Start,
                modifier = Modifier.align(Alignment.Start)
            )
        }
    }
}

@Composable
private fun AddToPlaylistContent(
    playlists: State<List<Playlist>>,
    onPick: (Playlist) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val all = playlists.value

    // Recents: the playlists touched last. Only worth a section of their own once there are more
    // playlists than it would show; with few, one list is simpler.
    val sections = remember(all, query) {
        val needle = query.trim()
        if (needle.isNotEmpty()) {
            val hits = all.filter { it.playlist.name.contains(needle, ignoreCase = true) }
            Sections(recents = emptyList(), rest = hits, searching = true)
        } else if (all.size > RecentCount) {
            val recents = all
                .sortedByDescending { it.playlist.lastUpdateTime ?: it.playlist.createdAt ?: LocalDateTime.MIN }
                .take(RecentCount)
            val collator = Collator.getInstance(Locale.getDefault()).apply { strength = Collator.PRIMARY }
            val rest = (all - recents.toSet()).sortedWith(compareBy(collator) { it.playlist.name })
            Sections(recents = recents, rest = rest, searching = false)
        } else {
            val collator = Collator.getInstance(Locale.getDefault()).apply { strength = Collator.PRIMARY }
            Sections(recents = emptyList(), rest = all.sortedWith(compareBy(collator) { it.playlist.name }), searching = false)
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        SheetSearchField(
            value = query,
            onValueChange = { query = it },
            placeholder = "Search your playlist",
        )
        Spacer(Modifier.height(16.dp))

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 12.dp),
        ) {
            if (sections.recents.isNotEmpty()) {
                item(key = "recents-label") { SheetLabel("recents", Modifier.padding(bottom = 6.dp)) }
                items(sections.recents, key = { "recent-${it.id}" }) { playlist ->
                    PlaylistPickRow(playlist = playlist, onClick = { onPick(playlist) })
                }
                item(key = "recents-gap") { Spacer(Modifier.height(14.dp)) }
            }

            if (sections.rest.isNotEmpty()) {
                item(key = "all-label") {
                    SheetLabel(if (sections.searching) "results" else "all", Modifier.padding(bottom = 6.dp))
                }
                items(sections.rest, key = { "all-${it.id}" }) { playlist ->
                    PlaylistPickRow(playlist = playlist, onClick = { onPick(playlist) })
                }
            } else if (sections.recents.isEmpty()) {
                item(key = "empty") {
                    Text(
                        text = if (sections.searching) "No playlists found" else "No playlists yet",
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 28.dp),
                    )
                }
            }
        }
    }
}

private class Sections(val recents: List<Playlist>, val rest: List<Playlist>, val searching: Boolean)

/** A playlist as the mock draws it: a rounded square cover, its name, and under it the song count. */
@Composable
private fun PlaylistPickRow(playlist: Playlist, onClick: () -> Unit) {
    val count = if (playlist.songCount == 0 && playlist.playlist.remoteSongCount != null) {
        playlist.playlist.remoteSongCount
    } else {
        playlist.songCount
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = RemoteFocus,
                onClick = onClick,
            )
            .padding(vertical = 6.dp),
    ) {
        PlaylistThumbnail(
            thumbnails = playlist.thumbnails,
            size = 52.dp,
            placeHolder = {
                Icon(
                    painter = painterResource(R.drawable.queue_music),
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(24.dp),
                )
            },
            shape = RoundedCornerShape(12.dp),
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlist.playlist.name,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = pluralStringResource(R.plurals.n_song, count, count),
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
