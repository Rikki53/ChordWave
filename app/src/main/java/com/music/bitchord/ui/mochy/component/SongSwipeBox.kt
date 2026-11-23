// Ported from Mochy Music (GPL-3.0): ui/component/SongSwipeBox.kt
// SongSwipeBox.kt
//
// ADAPTATION: "Add to playlist" opens AddToPlaylistDialog (online add
// works; a local playlist answers honestly — see the dialog), and
// "Details" opens CW's own SongInfoDialog (Classic) instead of Mochy's
// media-info screen: the swiped MediaItem's tagged Mochy metadata converts
// straight to the CW Song the dialog needs. Everything else is verbatim.

package com.music.bitchord.ui.mochy.component

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.datastore.preferences.core.Preferences
import androidx.media3.common.MediaItem
import com.music.bitchord.R
import com.music.bitchord.data.model.Song as CwSong
import com.music.bitchord.ui.components.SongInfoDialog
import com.music.bitchord.ui.mochy.constants.SwipeLeftActionKey
import com.music.bitchord.ui.mochy.constants.SwipeRightActionKey
import com.music.bitchord.ui.mochy.models.MediaMetadata
import com.music.bitchord.ui.mochy.player.menu.AddToPlaylistDialog
import com.music.bitchord.ui.mochy.utils.rememberEnumPreference

// what sliding a song row can do, picked per side in Settings > Customisation
enum class SongSwipeAction(
    val title: String,
    @DrawableRes val icon: Int,
    val color: Color,
    @StringRes val pillLabel: Int,
) {
    Library("Add or remove from library", R.drawable.library_add, SwipeSaveColor, R.string.add_to_library),
    Queue("Add to queue", R.drawable.queue_music, SwipeQueueColor, R.string.add_to_queue),
    Download("Download", R.drawable.download, SwipeDownloadColor, R.string.action_download),
    PlayNext("Play next", R.drawable.playlist_play, SwipePlayNextColor, R.string.play_next),
    AddToPlaylist("Add to playlist", R.drawable.playlist_add, SwipePlaylistColor, R.string.add_to_playlist),
    Details("View details", R.drawable.info, SwipeDetailsColor, R.string.details),
}

val DefaultSwipeRight = SongSwipeAction.Library
val DefaultSwipeLeft = SongSwipeAction.Queue

@Composable
fun SongSwipeBox(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    slab: Boolean = true,
    library: @Composable () -> SwipeAction,
    queue: @Composable () -> SwipeAction,
    content: @Composable () -> Unit,
) {
    var infoSong by remember { mutableStateOf<CwSong?>(null) }
    infoSong?.let { song ->
        SongInfoDialog(song) { infoSong = null }
    }
    var playlistSong by remember { mutableStateOf<CwSong?>(null) }
    playlistSong?.let { song ->
        AddToPlaylistDialog(
            isVisible = true,
            onGetSong = { listOf(song.videoId) },
            onDismiss = { playlistSong = null },
            topSongId = song.videoId,
            topSongArtworkUrl = song.thumbnailUrl,
        )
    }
    SwipeActionsBox(
        modifier = modifier,
        enabled = enabled,
        slab = slab,
        start = {
            chosenSwipeAction(
                key = SwipeRightActionKey,
                default = DefaultSwipeRight,
                library = library,
                queue = queue,
                onOpenPlaylistDialog = { playlistSong = it.toInfoSong() },
                onOpenDetailsDialog = { infoSong = it.toInfoSong() },
            )
        },
        end = {
            chosenSwipeAction(
                key = SwipeLeftActionKey,
                default = DefaultSwipeLeft,
                library = library,
                queue = queue,
                onOpenPlaylistDialog = { playlistSong = it.toInfoSong() },
                onOpenDetailsDialog = { infoSong = it.toInfoSong() },
            )
        },
        content = content,
    )
}

@Composable
private fun chosenSwipeAction(
    key: Preferences.Key<String>,
    default: SongSwipeAction,
    library: @Composable () -> SwipeAction,
    queue: @Composable () -> SwipeAction,
    onOpenPlaylistDialog: (MediaItem) -> Unit,
    onOpenDetailsDialog: (MediaItem) -> Unit,
): SwipeAction {
    val (chosen) = rememberEnumPreference(key, defaultValue = default)
    return when (chosen) {
        SongSwipeAction.Library -> library()
        SongSwipeAction.Queue -> queue()
        SongSwipeAction.Download -> downloadSwipeAction(queue().mediaItem)
        SongSwipeAction.PlayNext -> playNextSwipeAction(queue().mediaItem)
        SongSwipeAction.AddToPlaylist -> addToPlaylistSwipeAction(queue().mediaItem, onOpenPlaylistDialog)
        SongSwipeAction.Details -> detailsSwipeAction(queue().mediaItem, onOpenDetailsDialog)
    }
}

/**
 * The swiped item as the CW Song the info dialog needs. Queue items carry
 * their Mochy metadata as the tag (see MediaItemExt.buildMediaItem), which
 * converts exactly; anything else falls back to the player's own metadata
 * and the media id, which is the video id for online tracks.
 */
private fun MediaItem.toInfoSong(): CwSong {
    val tagged = localConfiguration?.tag as? MediaMetadata
    if (tagged != null) return tagged.toCwSong()
    val meta = mediaMetadata
    return CwSong(
        videoId = mediaId,
        title = meta.title?.toString().orEmpty(),
        artist = meta.artist?.toString().orEmpty(),
        thumbnailUrl = meta.artworkUri?.toString(),
    )
}
