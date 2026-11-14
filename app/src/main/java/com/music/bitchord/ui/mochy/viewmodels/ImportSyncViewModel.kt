// Ported from Mochy Music (GPL-3.0): viewmodels/ImportSyncViewModel.kt
// (PendingImport lives in the Phase 4E file PendingImport.kt, same
// package — do NOT redeclare it here.)
//
// ADAPTATIONS:
// - No MusicDatabase parameter: ChordWave has no local import store, so
//   the CSV export reads the signed-in YouTube account instead
//   (ChordWaveCsvExporter — same file format, same summary line).
// - parseImportCsv is imported from the Phase 4C file CsvImport.kt
//   (same package) — never redeclared.
// - Sign-out goes through YouTubeSignOut (the MainViewModel bridge),
//   not Mochy's App.forgetAccount.
package com.music.bitchord.ui.mochy.viewmodels

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.bitchord.ui.mochy.importer.ChordWaveCsvExporter
import com.music.bitchord.ui.mochy.importer.ImportOptions
import com.music.bitchord.ui.mochy.importer.ImportProgress
import com.music.bitchord.ui.mochy.importer.LinkImportException
import com.music.bitchord.ui.mochy.importer.LinkImporter
import com.music.bitchord.ui.mochy.importer.MusicImportService
import com.music.bitchord.ui.mochy.importer.ParsedImport
import com.music.bitchord.ui.mochy.importer.YouTubeSignOut
import com.music.bitchord.ui.mochy.importer.YouTubeSyncManager
import com.music.bitchord.ui.mochy.importer.YtSyncState
import com.music.bitchord.ui.mochy.importer.parseImportCsv
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class ImportSyncViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val importService: MusicImportService,
    private val youTubeSync: YouTubeSyncManager,
) : ViewModel() {

    val importProgress: StateFlow<ImportProgress> = importService.progress
    val youTubeState: StateFlow<YtSyncState> = youTubeSync.state

    private val _pending = MutableStateFlow<PendingImport>(PendingImport.Idle)
    val pending: StateFlow<PendingImport> = _pending.asStateFlow()

    private val _exportMessage = MutableStateFlow<String?>(null)
    val exportMessage: StateFlow<String?> = _exportMessage.asStateFlow()

    private var loadJob: Job? = null

    fun loadLink(link: String) {
        val source = LinkImporter.detect(link)?.label ?: "link"
        loadJob?.cancel()
        _pending.value = PendingImport.Loading("Reading the $source playlist…")
        loadJob = viewModelScope.launch(Dispatchers.IO) {
            _pending.value = try {
                PendingImport.Ready(LinkImporter.fetch(link), source)
            } catch (e: CancellationException) {
                throw e
            } catch (e: LinkImportException) {
                PendingImport.Failed(e.message ?: "Couldn't read that link.")
            } catch (e: Exception) {
                Timber.e(e, "Link import failed")
                PendingImport.Failed("Couldn't read that link.")
            }
        }
    }

    /** [sourceLabel] is only for the review dialog ("ChordWave backup", "TuneMyMusic"). */
    fun loadFile(uri: Uri, sourceLabel: String) {
        loadJob?.cancel()
        _pending.value = PendingImport.Loading("Reading the file…")
        loadJob = viewModelScope.launch(Dispatchers.IO) {
            _pending.value = try {
                val size = queryColumn(uri, OpenableColumns.SIZE)?.toLongOrNull()
                if (size != null && size > MAX_CSV_BYTES) {
                    PendingImport.Failed("That file is too big to be a playlist export.")
                } else {
                    val text = context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                        ?: throw IllegalStateException("Couldn't open that file")
                    val fileName = queryColumn(uri, OpenableColumns.DISPLAY_NAME)
                        ?.substringBeforeLast('.')?.takeIf { it.isNotBlank() } ?: "Imported playlist"
                    val parsed = parseImportCsv(text, fallbackPlaylistName = fileName)
                    if (parsed.totalSongs == 0 && parsed.totalPlaylists == 0) {
                        PendingImport.Failed("No songs in that file. It needs a header row with at least a \"Track name\" column.")
                    } else {
                        PendingImport.Ready(parsed, sourceLabel)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "File import failed")
                PendingImport.Failed("Couldn't read that file. Make sure it's a .csv export.")
            }
        }
    }

    /** Something already read elsewhere (an account import) goes straight to the review dialog. */
    fun offer(parsed: ParsedImport, sourceLabel: String) {
        loadJob?.cancel()
        _pending.value = PendingImport.Ready(parsed, sourceLabel)
    }

    fun dismissPending() {
        loadJob?.cancel()
        _pending.value = PendingImport.Idle
    }

    /** False when another import is still running. */
    fun startImport(parsed: ParsedImport, mirrorToYouTube: Boolean): Boolean {
        val started = importService.startImport(parsed, ImportOptions(mirrorToYouTube = mirrorToYouTube))
        if (started) _pending.value = PendingImport.Idle
        return started
    }

    fun cancelImport() = importService.cancel()

    fun clearFinishedImport() = importService.clearFinished()

    fun export(uri: Uri) {
        _exportMessage.value = "Exporting…"
        viewModelScope.launch(Dispatchers.IO) {
            _exportMessage.value = try {
                val (csv, summary) = ChordWaveCsvExporter.build()
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    // the BOM makes Excel read names in any script correctly; our reader skips it
                    out.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
                    out.write(csv.toByteArray(Charsets.UTF_8))
                } ?: throw IllegalStateException("Couldn't write the file")
                "Exported ${summary.likedSongs} liked songs and ${summary.playlists} playlists (${summary.playlistSongs} songs)."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Export failed")
                "Export failed: ${e.message ?: "unknown error"}"
            }
        }
    }

    fun clearExportMessage() {
        _exportMessage.value = null
    }

    fun syncNow() = youTubeSync.syncNow()
    fun pullHistoryNow() = youTubeSync.pullHistoryNow()
    fun uploadLocalPlaylists() = youTubeSync.uploadLocalPlaylists()
    fun undoLastSync(withYouTube: Boolean) = youTubeSync.undoLastSync(withYouTube)
    fun clearYouTubeMessage() = youTubeSync.clearMessage()

    /** Signs out but keeps everything already synced in the library. */
    fun signOut() {
        viewModelScope.launch(Dispatchers.IO) { YouTubeSignOut.signOut(context) }
    }

    private fun queryColumn(uri: Uri, column: String): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(column), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getString(0) else null
        }
    }.getOrNull()

    private companion object {
        const val MAX_CSV_BYTES = 40L * 1024 * 1024
    }
}
