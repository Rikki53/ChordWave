// LocalImportEngine.kt — ChordWave adapter for Mochy's LocalImportService
// (same job, CW-owned storage: file -> tag read -> Song with isLocal=true).
//
// CW has no local-import service: LocalMediaRepository only scans MediaStore
// on demand, and MediaTagger/Mp3Tagger/... write tags, they don't read them.
// So this engine reads tags itself with MediaMetadataRetriever, keeps the
// imported files in LocalImportStore, and publishes the batch the progress
// card and sheet watch.
//
// One import at a time: a second start while one runs returns false, and the
// caller toasts "An import is still running" — the same guard Mochy's
// service has. The work runs on the engine's own IO scope, so it survives
// the sheet closing (and the ViewModel going away with it).

package com.music.bitchord.ui.mochy.importer

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.music.bitchord.download.DownloadStore
import com.music.bitchord.ui.mochy.models.TransferBatch
import com.music.bitchord.ui.mochy.models.TransferItem
import com.music.bitchord.ui.mochy.models.TransferState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File

object LocalImportEngine {

    private lateinit var appContext: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _batch = MutableStateFlow(TransferBatch())

    /** The import running now, or the last one finished (until [clearFinished]). */
    val batch: StateFlow<TransferBatch> = _batch.asStateFlow()

    @Volatile
    private var running = false

    fun init(context: Context) {
        if (this::appContext.isInitialized) return
        appContext = context.applicationContext
        LocalImportStore.init(appContext)
    }

    /** Files picked by hand from the system picker. False when an import is already running. */
    fun importPicked(uris: List<Uri>): Boolean {
        if (uris.isEmpty()) return false
        return startImport(uris.map { Input(it, fileNameOf(it), null, null, null) })
    }

    /** Songs picked from the storage scan. False when an import is already running. */
    fun importDeviceSongs(songs: List<DeviceSong>): Boolean {
        if (songs.isEmpty()) return false
        return startImport(
            songs.map { Input(it.uri, it.title, it.artist, it.artworkUri, it.durationMs) },
        )
    }

    /** New audio files from a picked music folder. False when an import is already running. */
    fun importFolderFiles(uris: List<Uri>): Boolean {
        if (uris.isEmpty()) return false
        return startImport(uris.map { Input(it, fileNameOf(it), null, null, null) })
    }

    /** Clears a finished batch, so the next import starts with a clean sheet. A running batch is left alone. */
    fun clearFinished() {
        if (!running) _batch.value = TransferBatch()
    }

    /**
     * The MediaStore uris of the files in this app's download folder
     * (Music/ChordWave, plus the legacy Music/BitChord): the scan offers
     * every song on the phone, and a download is already on the Local shelf —
     * offering it again would import a second copy of it.
     */
    fun downloadMediaUris(context: Context): Set<String> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return emptySet()
        return runCatching {
            val out = HashSet<String>()
            for (folder in listOf(DownloadStore.FOLDER, "BitChord")) {
                context.contentResolver.query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    arrayOf(MediaStore.Audio.Media._ID),
                    "${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ?",
                    arrayOf("%$folder%"),
                    null,
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                    while (cursor.moveToNext()) {
                        out += ContentUris.withAppendedId(
                            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                            cursor.getLong(idCol),
                        ).toString()
                    }
                }
            }
            out
        }.getOrDefault(emptySet())
    }

    private class Input(
        val uri: Uri,
        val title: String,
        val artist: String?,
        val artworkUri: String?,
        val durationMs: Long?,
    )

    private fun startImport(inputs: List<Input>): Boolean {
        if (inputs.isEmpty()) return false
        synchronized(this) {
            if (running) return false
            running = true
        }
        _batch.value = TransferBatch(
            inputs.map {
                TransferItem(
                    key = it.uri.toString(),
                    title = it.title,
                    subtitle = it.artist,
                    artworkUrl = it.artworkUri,
                    state = TransferState.Waiting,
                )
            },
        )
        scope.launch {
            try {
                val sessionId = System.currentTimeMillis().toString(36)
                val records = mutableListOf<LocalImportStore.Record>()
                inputs.forEachIndexed { index, input ->
                    updateItem(index) { it.copy(state = TransferState.Running) }
                    val record = runCatching { readRecord(input, sessionId) }.getOrNull()
                    if (record != null) {
                        records += record
                        updateItem(index) {
                            it.copy(
                                state = TransferState.Done,
                                title = record.title,
                                subtitle = record.artist,
                                artworkUrl = record.artworkUri,
                                songId = record.uri,
                            )
                        }
                    } else {
                        updateItem(index) { it.copy(state = TransferState.Failed) }
                    }
                }
                // the store write runs on the engine's own scope: an uncaught
                // throw here would take the app down with the import
                if (records.isNotEmpty()) {
                    runCatching { LocalImportStore.add(appContext, records) }
                        .onFailure { Timber.w(it, "LocalImport: couldn't save imported files") }
                }
            } finally {
                running = false
            }
        }
        return true
    }

    private fun updateItem(index: Int, change: (TransferItem) -> TransferItem) {
        _batch.update { batch ->
            if (index !in batch.items.indices) {
                batch
            } else {
                batch.copy(items = batch.items.toMutableList().also { it[index] = change(it[index]) })
            }
        }
    }

    /**
     * Reads one file's tags. Null when the file can't be opened at all (the
     * batch marks it Failed); a file whose tags are missing still imports
     * under its file name.
     */
    private fun readRecord(input: Input, sessionId: String): LocalImportStore.Record? {
        // Lasting access for a hand-picked file, best effort: the picker's
        // grant dies with the process otherwise, and the song would stop
        // playing after a restart. MediaStore files don't need it.
        if (input.uri.scheme == ContentResolver.SCHEME_CONTENT) {
            runCatching {
                appContext.contentResolver.takePersistableUriPermission(
                    input.uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
        }
        var title = input.title
        var artist = input.artist
        var album: String? = null
        var genre: String? = null
        var durationMs = input.durationMs ?: 0L
        var artworkUri = input.artworkUri
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(appContext, input.uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?.takeIf { it.isNotBlank() }?.let { title = it }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?.takeIf { it.isNotBlank() }?.let { artist = it }
            album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                ?.takeIf { it.isNotBlank() }
            genre = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)
                ?.takeIf { it.isNotBlank() }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()?.let { durationMs = it }
            val embedded = retriever.embeddedPicture
            if (embedded != null && embedded.isNotEmpty()) {
                saveArtwork(input.uri, embedded)?.let { artworkUri = it }
            }
        } catch (e: Exception) {
            Timber.w(e, "LocalImport: couldn't read ${input.uri}")
            return null
        } finally {
            runCatching { retriever.release() }
        }
        // Prefer the MediaStore uri: it plays with storage access alone, and
        // it is the identity the rest of the app already gives the file.
        val mediaStoreUri = LocalFolders.mediaStoreUri(appContext, input.uri)
        val primary = mediaStoreUri?.toString() ?: input.uri.toString()
        val alt = if (mediaStoreUri != null) input.uri.toString() else null
        return LocalImportStore.Record(
            uri = primary,
            altUri = alt,
            title = title,
            artist = artist,
            album = album,
            genre = genre,
            durationMs = durationMs,
            artworkUri = artworkUri,
            addedAtMs = System.currentTimeMillis(),
            sessionId = sessionId,
        )
    }

    /** Embedded cover art, saved once under the cache dir so rows can show it. */
    private fun saveArtwork(uri: Uri, bytes: ByteArray): String? = runCatching {
        val dir = File(appContext.cacheDir, "mochy_local_art").apply { mkdirs() }
        val name = uri.toString().hashCode().toUInt().toString(36) + ".jpg"
        val file = File(dir, name)
        if (!file.exists()) file.writeBytes(bytes)
        Uri.fromFile(file).toString()
    }.getOrNull()

    private fun fileNameOf(uri: Uri): String {
        // A document id reads "primary:Music/song.mp3" — the name is the last
        // segment after the volume and the folders. Only a placeholder: the
        // tag read replaces it with the real title moments later.
        val last = uri.lastPathSegment?.substringAfterLast(':')?.substringAfterLast('/').orEmpty()
        return last.substringBeforeLast('.').takeIf { it.isNotBlank() } ?: "Unknown song"
    }
}
