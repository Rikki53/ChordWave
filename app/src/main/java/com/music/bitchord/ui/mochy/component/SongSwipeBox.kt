// Ported from Mochy Music (GPL-3.0): ui/component/SongSwipeBox.kt
// SongSwipeBox.kt
//
// ADAPTATION: the AddToPlaylistDialog branch is dropped — the dialog is not
// ported (CW has no local playlists yet; see NEEDS.md). Choosing "Add to
// playlist" as a swipe action now logs a Phase 3 TODO instead of opening
// the picker. Everything else is verbatim.

package com.music.bitchord.ui.mochy.component

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.datastore.preferences.core.Preferences
import androidx.media3.common.MediaItem
import com.music.bitchord.R
import com.music.bitchord.ui.mochy.constants.SwipeLeftActionKey
import com.music.bitchord.ui.mochy.constants.SwipeRightActionKey
import com.music.bitchord.ui.mochy.utils.rememberEnumPreference
import timber.log.Timber

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
                onOpenPlaylistDialog = {
                    // TODO(Phase 3): AddToPlaylistDialog is not ported yet.
                    Timber.w("SongSwipeBox: add-to-playlist swipe needs Phase 3 (AddToPlaylistDialog)")
                },
            )
        },
        end = {
            chosenSwipeAction(
                key = SwipeLeftActionKey,
                default = DefaultSwipeLeft,
                library = library,
                queue = queue,
                onOpenPlaylistDialog = {
                    // TODO(Phase 3): AddToPlaylistDialog is not ported yet.
                    Timber.w("SongSwipeBox: add-to-playlist swipe needs Phase 3 (AddToPlaylistDialog)")
                },
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
): SwipeAction {
    val (chosen) = rememberEnumPreference(key, defaultValue = default)
    return when (chosen) {
        SongSwipeAction.Library -> library()
        SongSwipeAction.Queue -> queue()
        SongSwipeAction.Download -> downloadSwipeAction(queue().mediaItem)
        SongSwipeAction.PlayNext -> playNextSwipeAction(queue().mediaItem)
        SongSwipeAction.AddToPlaylist -> addToPlaylistSwipeAction(queue().mediaItem, onOpenPlaylistDialog)
        SongSwipeAction.Details -> detailsSwipeAction(queue().mediaItem)
    }
}
