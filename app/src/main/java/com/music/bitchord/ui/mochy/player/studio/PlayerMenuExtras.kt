// PlayerMenuExtras.kt
//
// The chevron-menu actions that need more than a flag already in the player: keeping the screen
// on, translating the lyrics, downloading the song and adding it to a playlist. BottomSheetPlayer
// hands these to [PlayerMenuExtras] so its own list of actions stays a list.
//
// Ported from Mochy Music (GPL-3.0): ui/player/studio/PlayerMenuExtras.kt
// Adapted to ChordWave: download state/action go through the CW bridge in
// component/CwDownloads.kt (downloadStateFor / Downloads.enqueue /
// removeDownload), the same bridge the ported menus use. Lyrics translation
// has no CW equivalent yet and is stubbed (see NEEDS.md §(b)).

package com.music.bitchord.ui.mochy.player.studio

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.media3.exoplayer.offline.Download
import com.music.bitchord.download.Downloads
import com.music.bitchord.ui.mochy.component.downloadStateFor
import com.music.bitchord.ui.mochy.component.toCwSong
import com.music.bitchord.ui.mochy.constants.KeepScreenOn
import com.music.bitchord.ui.mochy.models.MediaMetadata
import com.music.bitchord.ui.mochy.playback.MochyPlayerConnection
import com.music.bitchord.ui.mochy.player.menu.AddToPlaylistDialog
import com.music.bitchord.ui.mochy.player.menu.removeDownload
import com.music.bitchord.ui.mochy.utils.rememberPreference
import kotlinx.coroutines.CoroutineScope
import timber.log.Timber

/** What the extra chevron actions do, and whether each one is on right now. */
class PlayerMenuExtras internal constructor(
    private val context: Context,
    private val scope: CoroutineScope,
    private val song: MediaMetadata?,
    private val keepScreenOn: Boolean,
    private val setKeepScreenOn: (Boolean) -> Unit,
    private val translating: Boolean,
    private val downloadState: Int?,
    private val openLyrics: () -> Unit,
    private val showAddToPlaylist: () -> Unit,
) {
    fun isActive(action: PlayerMenuAction): Boolean = when (action) {
        PlayerMenuAction.KEEP_AWAKE -> keepScreenOn
        PlayerMenuAction.TRANSLATE_LYRICS -> translating
        PlayerMenuAction.DOWNLOAD -> downloadState == Download.STATE_COMPLETED || downloadState == Download.STATE_DOWNLOADING ||
            downloadState == Download.STATE_QUEUED
        else -> false
    }

    /** Runs [action] if it's one of these; false if the player handles it itself. */
    fun run(action: PlayerMenuAction): Boolean {
        when (action) {
            PlayerMenuAction.KEEP_AWAKE -> {
                setKeepScreenOn(!keepScreenOn)
                toast(if (keepScreenOn) "The screen can sleep again" else "The screen stays on while ChordWave is open")
            }
            PlayerMenuAction.TRANSLATE_LYRICS -> {
                // TODO(studio): lyrics translation has no ChordWave equivalent yet (Mochy
                // LyricsTranslationHelper.triggerManualTranslation /
                // triggerClearTranslations). See NEEDS.md §(b).
                Timber.w("TRANSLATE_LYRICS not implemented in ChordWave yet")
            }
            PlayerMenuAction.DOWNLOAD -> toggleDownload()
            PlayerMenuAction.ADD_TO_PLAYLIST -> if (song != null) showAddToPlaylist()
            else -> return false
        }
        return true
    }

    private fun toggleDownload() {
        val song = song ?: return
        if (isActive(PlayerMenuAction.DOWNLOAD)) {
            if (downloadState == Download.STATE_COMPLETED) {
                scope.removeDownload(context, song.id)
                toast("Removed the download")
            } else {
                Downloads.cancel(song.id)
                toast("Download stopped")
            }
            return
        }
        Downloads.enqueue(context, song.toCwSong(), from = "player-menu")
        toast("Downloading \"${song.title}\"")
    }

    private fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
}

/** Also shows the add-to-playlist sheet when its action asks for it. */
@Composable
fun rememberPlayerMenuExtras(playerConnection: MochyPlayerConnection, openLyrics: () -> Unit): PlayerMenuExtras {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val song by playerConnection.mediaMetadata.collectAsState()
    val downloadState = downloadStateFor(song?.id ?: "")
    val (keepScreenOn, setKeepScreenOn) = rememberPreference(KeepScreenOn, defaultValue = false)
    // TODO(studio): no lyrics-translation state in ChordWave yet; always off until the
    // translation engine lands. See NEEDS.md §(b).
    val translating = false
    var addingToPlaylist by remember { mutableStateOf(false) }

    song?.let { current ->
        AddToPlaylistDialog(
            isVisible = addingToPlaylist,
            onGetSong = { listOf(current.id) },
            onDismiss = { addingToPlaylist = false },
            topSongId = current.id,
            topSongArtworkUrl = current.thumbnailUrl,
        )
    }

    return PlayerMenuExtras(
        context = context,
        scope = scope,
        song = song,
        keepScreenOn = keepScreenOn,
        setKeepScreenOn = setKeepScreenOn,
        translating = translating,
        downloadState = downloadState,
        openLyrics = openLyrics,
        showAddToPlaylist = { addingToPlaylist = true },
    )
}
