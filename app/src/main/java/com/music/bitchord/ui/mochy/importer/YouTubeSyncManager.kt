// YouTubeSyncManager.kt — REPLACES the Phase 4C stub (same path, same
// public API, plus the state flow and the pullHistory/upload/undo entry
// points Mochy's manager has). Ported from Mochy Music (GPL-3.0):
// importer/YouTubeSyncManager.kt, rebuilt on ChordWave engines.
//
// WHAT IS REAL: syncNow refreshes the signed-in library through
// YtMusicRepository (liked songs — which seeds LikeState — owned
// playlists, account) and, when the host wires it, asks MainViewModel
// to reload its own library/playlists state (see YouTubeSignOut).
//
// WHAT IS HONESTLY LIMITED (no engine behind it in ChordWave):
// - pullHistory: CW's history IS YouTube's history — HistoryViewModel
//   reads YtMusicRepository.history(), the same feed. There is no
//   separate local play log to copy into (ListeningStats only records
//   plays as they happen, stamped "now", so backfilling it would corrupt
//   Replay). pullHistoryNow refreshes the feed and says so.
// - uploadLocalPlaylists: CW has no local playlists of YouTube songs
//   (playlist NEEDS C2); OfflinePlaylists hold on-device files, which
//   YouTube can't take. There is nothing to upload.
// - undoLastSync: no snapshot store exists (Mochy's SyncUndo). canUndo
//   is always false, so the Sync page's Undo row stays disabled; the
//   call itself reports "Nothing to undo yet."
package com.music.bitchord.ui.mochy.importer

import android.content.Context
import com.music.bitchord.R
import com.music.bitchord.auth.SignedIn
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.ui.mochy.constants.YtPullHistoryKey
import com.music.bitchord.ui.mochy.utils.dataStore
import com.music.bitchord.ui.mochy.utils.get
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/** the manual youtube actions on the sync page; automatic library sync stays in SyncUtils */
@Singleton
class YouTubeSyncManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, e -> Timber.e(e, "YouTube sync task failed") }
    )
    private var job: Job? = null

    private val _state = MutableStateFlow(YtSyncState())
    val state: StateFlow<YtSyncState> = _state.asStateFlow()

    fun syncNow() = launchTask("Syncing with YouTube…") {
        requireSignedIn()
        YtMusicRepository.library().getOrThrow()
        YtMusicRepository.userPlaylists().getOrThrow()
        YtMusicRepository.account().getOrThrow()
        YouTubeSignOut.refreshLibrary?.invoke()
        if (context.dataStore.get(YtPullHistoryKey, false)) {
            setLabel("Copying YouTube history…")
            pullHistory()
            "Synced your likes, library and playlists. Your YouTube history is already what ChordWave shows."
        } else {
            "Synced your likes, library and playlists."
        }
    }

    fun pullHistoryNow() = launchTask("Copying YouTube history…") {
        requireSignedIn()
        val plays = pullHistory()
        if (plays == 0) {
            "Nothing in your YouTube history."
        } else {
            "Your YouTube history is already what ChordWave shows — $plays ${if (plays == 1) "play" else "plays"} there."
        }
    }

    fun undoLastSync(withYouTube: Boolean) = launchTask("Undoing the last sync…") {
        Timber.w("YouTubeSyncManager: undoLastSync ignored (no sync snapshot store in ChordWave)")
        context.getString(R.string.yt_sync_undo_nothing)
    }

    fun uploadLocalPlaylists() = launchTask("Creating playlists on YouTube…") {
        requireSignedIn()
        Timber.w("YouTubeSyncManager: uploadLocalPlaylists ignored (no local YouTube-song playlists in ChordWave)")
        "No local playlists to upload — playlists on this phone hold songs from the device, which YouTube can't take."
    }

    fun clearMessage() {
        if (!_state.value.running) _state.value = YtSyncState()
    }

    private class NotSignedInException : Exception("Sign in to YouTube first.")

    private fun requireSignedIn() {
        if (!SignedIn.state(context).value) throw NotSignedInException()
    }

    private fun setLabel(label: String) {
        _state.value = _state.value.copy(label = label)
    }

    private fun launchTask(label: String, block: suspend () -> String) {
        if (job?.isActive == true) return
        _state.value = _state.value.copy(running = true, label = label, message = null)
        job = scope.launch {
            var message: String? = null
            try {
                message = block()
            } catch (e: CancellationException) {
                message = "Stopped."
                throw e
            } catch (e: NotSignedInException) {
                message = e.message
            } catch (e: Exception) {
                Timber.e(e, "YouTube sync task failed")
                message = "Something went wrong: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                // No snapshot store exists yet, so nothing can ever be undone.
                _state.value = YtSyncState(running = false, message = message, canUndo = false)
            }
        }
    }

    /**
     * Refreshes the history feed and returns how many plays it holds.
     * ChordWave reads its history from YouTube directly, so there is
     * nothing to copy into — the refresh itself is the whole job.
     */
    private suspend fun pullHistory(): Int =
        YtMusicRepository.history().getOrThrow().size
}
