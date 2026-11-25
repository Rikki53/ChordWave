// LibraryImportViewModel adapter — phase2-library NEEDS.md §(a) C2 (NOT a
// Mochy port: Mochy's VM reads DownloadUtil + LocalImportService +
// ImportHistory + Room; this one reads CW's DownloadSession + the
// LocalImportEngine/LocalImportStore in ui.mochy.importer).
//
// Every member the ported Library screens read is exposed here with the
// declared name and type; the checklist in this dir's NEEDS.md ticks them
// off one by one.
//
// The downloads batch comes from DownloadSession (the session's downloads,
// with their titles — Downloads.active alone is videoId -> state and has no
// titles to show). The local-import batch comes from the engine. Both flows
// are live from construction: there is no load() to call, and nothing here
// does work until a screen collects.
//
// The Manage-imports actions (delete all / undo / per-service delete) have
// no store behind them yet — CW has no service-import engine — so they toast
// honestly that there is nothing to undo. The Manage-local actions are real:
// the store keeps import sessions for undo and removal.

package com.music.bitchord.ui.mochy.viewmodels

import android.content.Context
import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.bitchord.R
import com.music.bitchord.data.OfflinePlaylists
import com.music.bitchord.download.DownloadProgress
import com.music.bitchord.download.DownloadSession
import com.music.bitchord.download.Downloads
import com.music.bitchord.ui.mochy.component.TopToaster
import com.music.bitchord.ui.mochy.importer.DeviceMusicScanner
import com.music.bitchord.ui.mochy.importer.DeviceSong
import com.music.bitchord.ui.mochy.importer.ImportSource
import com.music.bitchord.ui.mochy.importer.LocalFolders
import com.music.bitchord.ui.mochy.importer.LocalImportEngine
import com.music.bitchord.ui.mochy.importer.LocalImportStore
import com.music.bitchord.ui.mochy.models.TransferBatch
import com.music.bitchord.ui.mochy.models.TransferItem
import com.music.bitchord.ui.mochy.models.TransferState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import timber.log.Timber
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** A storage scan for the Add music sheet. */
sealed interface DeviceScan {
    data object Idle : DeviceScan
    data object Scanning : DeviceScan
    data class Found(val songs: List<DeviceSong>) : DeviceScan
}

/**
 * Backs the Library's import side: the progress of downloads and local imports, the storage scan
 * behind Add music, and the Manage sheets' undo and delete actions. Results are told with a toast
 * at the top, since the sheet that asked has usually closed by then.
 */
@HiltViewModel
class LibraryImportViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : ViewModel() {

    /** This session's downloads, in the order they were asked for. */
    val downloads: StateFlow<TransferBatch> = DownloadSession.state
        .map { state ->
            TransferBatch(
                state.items.map { item ->
                    val (transferState, progress) = when (val p = item.progress) {
                        DownloadProgress.Queued, DownloadProgress.WaitingForWifi ->
                            TransferState.Waiting to 0f
                        is DownloadProgress.Running -> TransferState.Running to p.fraction
                        DownloadProgress.Done -> TransferState.Done to 0f
                        is DownloadProgress.Failed -> TransferState.Failed to 0f
                    }
                    TransferItem(
                        key = item.videoId,
                        title = item.song.title,
                        subtitle = item.song.artist.takeIf { it.isNotBlank() },
                        artworkUrl = item.song.thumbnailUrl,
                        state = transferState,
                        progress = progress,
                        songId = item.videoId.takeIf { transferState == TransferState.Done },
                    )
                },
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TransferBatch())

    val localImports: StateFlow<TransferBatch> = LocalImportEngine.batch

    private val _scan = MutableStateFlow<DeviceScan>(DeviceScan.Idle)
    val scan: StateFlow<DeviceScan> = _scan.asStateFlow()

    init {
        LocalImportEngine.init(context)
    }

    /** Looks for songs on the phone that aren't in the library yet. */
    fun scanDevice() {
        if (_scan.value == DeviceScan.Scanning) return
        _scan.value = DeviceScan.Scanning
        viewModelScope.launch {
            val known = withContext(Dispatchers.IO) { knownLocalUris() }
            val found = DeviceMusicScanner.scan(context, known)
            // Downloads are already on the Local shelf; offering them again
            // would import a second copy of each.
            val downloadUris = withContext(Dispatchers.IO) { LocalImportEngine.downloadMediaUris(context) }
            _scan.value = DeviceScan.Found(found.filter { it.uri.toString() !in downloadUris })
        }
    }

    /**
     * Every file already in the library, in both uri forms: a folder file
     * counts by its MediaStore uri too, so the phone-wide scan doesn't offer
     * it a second time.
     */
    private fun knownLocalUris(): Set<String> = LocalImportStore.knownUris()

    /**
     * A music folder the user just picked: kept for later re-scans, and its songs that aren't in
     * the library yet are imported now. Returns false when there was nothing to start.
     */
    suspend fun addFolder(tree: Uri): Boolean {
        // A tree uri is the only thing the folder walk understands; anything
        // else (or a provider that won't grant lasting access) ends here with
        // a toast, never with a crash in the picker's callback (batch 3 item 4).
        if (tree.scheme != ContentResolver.SCHEME_CONTENT) {
            TopToaster.show("Couldn't keep access to that folder. Try picking it again.", iconRes = R.drawable.library_music)
            return false
        }
        val files = try {
            withContext(Dispatchers.IO) {
                runCatching { LocalFolders.add(context, tree) }.getOrNull() ?: return@withContext null
                LocalFolders.newAudioFiles(context, tree, knownLocalUris())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Library: couldn't read picked folder $tree")
            null
        }
        if (files == null) {
            TopToaster.show("Couldn't keep access to that folder. Try picking it again.", iconRes = R.drawable.library_music)
            return false
        }
        return startFolderImport(files, emptyMessage = "Every song in that folder is already in your library")
    }

    /**
     * Re-scan: reads the saved music folders again for new files. Returns true
     * when an import started. (Mochy also repairs missing covers here; CW's
     * covers come from MediaStore/embedded art at import time, so there is
     * nothing to repair — see NEEDS.md.)
     */
    suspend fun refreshLocal(): Boolean {
        val files = withContext(Dispatchers.IO) {
            val known = knownLocalUris()
            LocalFolders.read(context).flatMap { LocalFolders.newAudioFiles(context, it, known) }.distinct()
        }
        if (files.isEmpty()) return false
        return startFolderImport(files, emptyMessage = null)
    }

    /** True if ChordWave has music folders the user picked. */
    fun hasFolders(): Boolean = LocalFolders.read(context).isNotEmpty()

    private fun startFolderImport(files: List<Uri>, emptyMessage: String?): Boolean {
        if (files.isEmpty()) {
            emptyMessage?.let { TopToaster.show(it, iconRes = R.drawable.library_music) }
            return false
        }
        if (!LocalImportEngine.importFolderFiles(files)) {
            TopToaster.show("An import is still running. Try again once it's done.", iconRes = R.drawable.library_music)
            return false
        }
        return true
    }

    fun importPicked(uris: List<Uri>): Boolean = LocalImportEngine.importPicked(uris)

    fun importDeviceSongs(songs: List<DeviceSong>): Boolean = LocalImportEngine.importDeviceSongs(songs)

    fun clearLocalImport() = LocalImportEngine.clearFinished()

    /** Stops every download that hasn't finished. The ones already done stay in the library. */
    fun cancelAllDownloads() {
        val running = downloads.value.items.filter { it.state == TransferState.Waiting || it.state == TransferState.Running }
        running.forEach { Downloads.cancel(it.key) }
        TopToaster.show(
            if (running.size == 1) "Cancelled 1 download" else "Cancelled ${running.size} downloads",
            iconRes = R.drawable.library_music,
        )
    }

    // ---- Manage imports ----
    // No service-import store exists yet (the account import engine is Phase
    // 4 scope), so these answer honestly instead of pretending to delete.

    fun deleteAllImports() = report { "Nothing imported yet" }

    fun undoLatestImport() = report { "There's no import to undo" }

    fun deleteImportsFrom(source: ImportSource) = report { "No imports from ${source.label} yet" }

    // ---- Manage local ----

    fun undoLatestLocalImport() = report {
        LocalImportStore.undoLatestSession()
            ?.let { "Undid your last local import: ${songs(it)} removed" }
            ?: "There's no local import to undo"
    }

    fun removeLocalSongs(songIds: Collection<String>) = report {
        LocalImportStore.remove(songIds)
        "Removed ${songs(songIds.size)}. The files are still on your phone."
    }

    /**
     * Fills the playlist "Create playlist" just made with a finished batch. The create sheet saves
     * the playlist on a background thread and doesn't wait for it, so this waits for it to exist.
     */
    fun fillPlaylist(playlistId: String, songIds: List<String>) {
        if (songIds.isEmpty()) return
        val id = playlistId.removePrefix(OfflinePlaylists.PREFIX)
        viewModelScope.launch(Dispatchers.IO) {
            withTimeoutOrNull(5_000) {
                OfflinePlaylists.playlists.first { list -> list.any { it.id == id } }
            } ?: return@launch
            val songs = songIds.mapNotNull { LocalImportStore.cwSong(it) }
            if (songs.isNotEmpty()) OfflinePlaylists.add(listOf(id), songs)
        }
    }

    private fun report(action: suspend () -> String) {
        viewModelScope.launch {
            val message = action()
            TopToaster.show(message, iconRes = R.drawable.library_music)
        }
    }

    private fun songs(count: Int) = if (count == 1) "1 song" else "$count songs"
}
