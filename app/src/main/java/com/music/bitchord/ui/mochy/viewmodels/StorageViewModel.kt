// Settings → storage, backed by ChordWave's own Downloads + AudioCache.
// Ported from Mochy Music's viewmodels/StorageViewModel.kt + playback/
// DownloadExporter.kt (GPL-3.0), rewired: Mochy's Media3 download cache and
// Room database are gone — CW downloads are plain files in Music/ChordWave,
// reached through Downloads.getDownloadedSongs / verifiedSavedUri, and the
// cache is AudioCache.cachedSongs().
//
// What is REAL here: measuring, the downloads list, removing downloads,
// clearing caches (audio + Coil covers), and exporting downloads as plain
// files into a chordwave-preserve folder. What is NOT: move() — ChordWave
// has no storage-move engine (moving means copying files, updating
// DownloadStore paths and restarting), so move() honestly reports
// MoveStep.Failed and the sheet shows the failure. Never silently pretend.

package com.music.bitchord.ui.mochy.viewmodels

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.music.bitchord.R
import com.music.bitchord.data.OfflinePlaylists
import com.music.bitchord.data.lyrics.EmbeddedLyrics
import com.music.bitchord.data.model.Song as CwSong
import com.music.bitchord.download.Downloads
import com.music.bitchord.playback.AudioCache
import com.music.bitchord.ui.mochy.models.Song
import com.music.bitchord.ui.mochy.utils.resize
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

// the folder everything goes into, wherever the user points the export
private const val RootFolder = "chordwave-preserve"

private const val CoverPx = 1200

// folder names inside the export, untranslated — Mochy's storage_export_folder_* values
private const val MusicFolder = "Music"
private const val PlaylistFolder = "Playlist"
private const val AlbumsFolder = "Albums"
private const val LikedFolder = "Liked songs"

@HiltViewModel
class StorageViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _usage = MutableStateFlow<StorageUsage?>(null)
    val usage: StateFlow<StorageUsage?> = _usage.asStateFlow()

    /** Every finished download, in the order Downloads returns them; null while loading. */
    private val _downloads = MutableStateFlow<List<DownloadedSong>?>(null)
    val downloads: StateFlow<List<DownloadedSong>?> = _downloads.asStateFlow()

    /** True while something is being deleted, so the buttons can't be tapped twice. */
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _exportStep = MutableStateFlow<ExportStep>(ExportStep.Idle)
    val exportStep: StateFlow<ExportStep> = _exportStep.asStateFlow()

    private val _moveStep = MutableStateFlow<MoveStep>(MoveStep.Idle)
    val moveStep: StateFlow<MoveStep> = _moveStep.asStateFlow()

    init {
        measure()
    }

    /** Walks ChordWave's folders again. The page calls it on open and whenever the downloads change. */
    fun measure() {
        viewModelScope.launch { refreshAll() }
    }

    /** Songs kept from streaming and every cover picture. Downloads and the library stay. */
    fun clearCaches() = runBusy {
        suspendCoroutine { cont ->
            AudioCache.clear { cont.resume(Unit) }
        }
        // Coil keeps the cover pictures in its own disk cache, apart from the audio cache
        runCatching {
            val loader = context.imageLoader
            loader.memoryCache?.clear()
            withContext(Dispatchers.IO) { loader.diskCache?.clear() }
        }.onFailure { Timber.w(it, "Could not clear the cover cache") }
    }

    fun removeAllDownloads() = runBusy {
        val ids = _downloads.value.orEmpty().map { it.song.id }
        ids.forEach { Downloads.delete(context, it) }
    }

    fun removeDownloads(ids: List<String>) = runBusy {
        ids.forEach { Downloads.delete(context, it) }
    }

    fun clearExport() {
        if (_exportStep.value !is ExportStep.Working) _exportStep.value = ExportStep.Idle
    }

    fun clearMoveFailure() {
        if (_moveStep.value is MoveStep.Failed) _moveStep.value = MoveStep.Idle
    }

    /** The drives on offer for [kind], read off the main thread since it measures the folder. */
    suspend fun placeChoice(kind: StoredKind): PlaceChoice = withContext(Dispatchers.IO) {
        // measure first on a cold start, so bytesToMove isn't 0 before init finishes
        if (_usage.value == null) refreshAll()
        val currentUsage = _usage.value ?: readUsage().also { _usage.value = it }
        val bytesToMove = when (kind) {
            StoredKind.Downloads -> currentUsage.downloads
            StoredKind.SongCache -> currentUsage.cache
        }
        PlaceChoice(
            current = StoragePlaces.placeOf(context, kind),
            places = StoragePlaces.places(context),
            bytesToMove = bytesToMove,
        )
    }

    /** A folder from the folder picker as the place ChordWave would save into, or null when it isn't on the phone or a card. */
    suspend fun pickedFolder(tree: Uri): StoragePlace? = withContext(Dispatchers.IO) {
        StoragePlaces.folderFromTree(context, tree)
            ?.let { StoragePlaces.folderPlace(context, StoragePlaces.rootForPicked(it)) }
    }

    /** Plain and cheap — path checks and one system flag only, no IO — so the sheet can call it during composition. */
    fun needsFolderAccess(place: StoragePlace): Boolean =
        StoragePlaces.needsFolderAccess(context, place)

    /**
     * ChordWave has NO storage-move engine: a real move would copy the files,
     * rewrite DownloadStore's paths and restart the app. So this reports the
     * failure honestly instead of pretending the move happened; the sheet
     * shows the failure and the files stay where they are.
     */
    fun move(kind: StoredKind, place: StoragePlace) {
        Timber.w("Storage move to ${place.label} is not supported: no move engine in ChordWave")
        _moveStep.value = MoveStep.Failed(kind)
    }

    /** Copies the downloaded songs out as plain files into a chordwave-preserve folder; where ChordWave keeps its own downloads never changes. */
    fun export(tree: Uri, choice: ExportChoice) {
        if (_exportStep.value is ExportStep.Working) return
        _exportStep.value = ExportStep.Working(0, 0)
        viewModelScope.launch {
            runCatching { exportNow(tree, choice) }
                .onSuccess { _exportStep.value = it }
                .onFailure {
                    Timber.e(it, "Export stopped")
                    _exportStep.value = ExportStep.Failed(context.getString(R.string.storage_export_failed))
                }
        }
    }

    private fun runBusy(block: suspend () -> Unit) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try {
                block()
            } finally {
                refreshAll()
                _busy.value = false
            }
        }
    }

    private suspend fun refreshAll() = withContext(Dispatchers.IO) {
        refreshDownloads()
        _usage.value = readUsage()
    }

    private suspend fun refreshDownloads() {
        val cwSongs = runCatching { Downloads.getDownloadedSongs(context) }.getOrNull().orEmpty()
        _downloads.value = cwSongs.map { cw -> toDownloadedSong(cw) }
    }

    private fun toDownloadedSong(cw: CwSong): DownloadedSong {
        val uriString = Downloads.verifiedSavedUri(cw.videoId) ?: cw.localUri
        val bytes = sizeOfUri(uriString)
        val addedSeconds = cw.localDateAddedSeconds
        val downloadedAt = when {
            addedSeconds != null && addedSeconds > 0 -> addedSeconds * 1000L
            else -> lastModifiedOf(uriString)
        }
        val base = cw.toDownloadedMochySong()
        // the liked flag lives in OfflinePlaylists, not on the download record
        val liked = runCatching { OfflinePlaylists.isLiked(cw) }.getOrDefault(false)
        val song = if (liked) base.copy(song = base.song.copy(liked = true)) else base
        return DownloadedSong(song, bytes.coerceAtLeast(0L), downloadedAt)
    }

    /** Bytes at [uriString]: a plain file's length, or a content row's size. 0 when it can't be told. */
    private fun sizeOfUri(uriString: String?): Long {
        if (uriString.isNullOrEmpty()) return 0L
        val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return 0L
        return when (uri.scheme) {
            "file" -> uri.path?.let(::File)?.takeIf { it.exists() }?.length() ?: 0L
            else -> runCatching {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize }
            }.getOrNull() ?: 0L
        }
    }

    /** A downloaded file's last-modified time, ms since 1970; 0 when unknown. */
    private fun lastModifiedOf(uriString: String?): Long {
        if (uriString.isNullOrEmpty()) return 0L
        val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return 0L
        if (uri.scheme != "file") return 0L
        val modified = uri.path?.let(::File)?.takeIf { it.exists() }?.lastModified() ?: 0L
        return modified.takeIf { it > 0 } ?: 0L
    }

    private suspend fun readUsage(): StorageUsage {
        val stat = StatFs(Environment.getDataDirectory().path)
        val dataDir = context.dataDir
        val databases = context.getDatabasePath("x").parentFile

        val cache = runCatching { AudioCache.cachedSongs().sumOf { it.bytes } }.getOrDefault(0L) +
            sizeOf(context.cacheDir) + sizeOf(context.codeCacheDir)
        val downloads = _downloads.value.orEmpty().sumOf { it.bytes }
        val database = sizeOf(databases)
        // the cache lives inside the data dir, the downloads don't (MediaStore) — only the cache comes off before the rest is counted
        val rest = (sizeOf(dataDir) - cache - database).coerceAtLeast(0L)
        val apk = context.applicationInfo.run {
            (listOf(sourceDir) + splitSourceDirs.orEmpty()).sumOf { File(it).length() }
        }
        val appData = rest + apk
        val total = cache + downloads + database + appData
        // everything ChordWave keeps is on the phone: the cache in its data dir, the downloads in shared storage
        val phone = DriveUsage(
            label = context.getString(R.string.storage_place_phone),
            free = stat.availableBytes,
            total = stat.totalBytes,
            isPhone = true,
            chordwave = total,
        )
        return StorageUsage(
            cache = cache,
            downloads = downloads,
            database = database,
            appData = appData,
            chordwave = total,
            drives = listOf(phone),
            phone = phone,
        )
    }

    private fun sizeOf(file: File?): Long {
        if (file == null || !file.exists()) return 0L
        return runCatching { file.walkTopDown().filter { it.isFile }.sumOf { it.length() } }.getOrDefault(0L)
    }

    // ---- Export -------------------------------------------------------------

    /** One song headed for one folder; [folderCover] is the picture that folder shares, when it has one. */
    private class Entry(val path: List<String>, val song: Song, val folderCover: String?)

    private suspend fun exportNow(tree: Uri, choice: ExportChoice): ExportStep.Done {
        refreshDownloads()
        val songs = _downloads.value.orEmpty()
        val entries = when (choice.layout) {
            ExportLayout.Playlists -> byPlaylist(songs)
            ExportLayout.Albums -> byAlbum(songs)
        }
        val documents = ExportDocumentTree(context.contentResolver, tree)
        val root = documents.folder(documents.rootId, RootFolder)
        val folders = HashMap<List<String>, String>()
        val usedNames = HashMap<List<String>, MutableSet<String>>()
        val coveredFolders = HashSet<List<String>>()
        var skipped = 0

        entries.forEachIndexed { index, entry ->
            _exportStep.value = ExportStep.Working(index, entries.size)
            val folder = folders.getOrPut(entry.path) {
                entry.path.fold(root) { parent, name -> documents.folder(parent, name) }
            }
            val uriString = Downloads.verifiedSavedUri(entry.song.id)
            val audio = uriString?.let { openAudio(it) }
            if (audio == null) {
                // a download whose file is gone is skipped, never written out short
                skipped++
                return@forEachIndexed
            }
            val base = uniqueName(usedNames.getOrPut(entry.path) { HashSet() }, baseName(entry.song))
            val extension = extensionOf(uriString)
            audio.use { input ->
                documents.write(folder, "$base.$extension") { out -> input.copyTo(out, 256 * 1024) }
            }

            if (choice.lyrics) {
                lyricsOf(uriString)?.let { (text, ext) ->
                    documents.write(folder, "$base.$ext") { it.write(text.toByteArray()) }
                }
            }
            if (choice.covers) {
                // an album folder shares one cover.jpg, a mixed folder gets a picture per song
                if (entry.folderCover != null) {
                    if (coveredFolders.add(entry.path)) {
                        writeCover(documents, folder, "cover.jpg", entry.folderCover)
                    }
                } else {
                    entry.song.song.thumbnailUrl?.let { writeCover(documents, folder, "$base.jpg", it) }
                }
            }
        }
        return ExportStep.Done(songs = entries.size - skipped, skipped = skipped)
    }

    /** Liked songs and each playlist get a folder under Playlist, everything in neither goes in Music. */
    private fun byPlaylist(songs: List<DownloadedSong>): List<Entry> {
        val byId = songs.associateBy { it.song.id }
        val out = ArrayList<Entry>()
        val placed = HashSet<String>()

        val liked = songs.filter { it.song.song.liked }
        liked.forEach { out += Entry(listOf(PlaylistFolder, LikedFolder), it.song, null) }
        placed += liked.map { it.song.id }

        val playlists = runCatching { OfflinePlaylists.playlists.value }.getOrDefault(emptyList())
        val takenNames = HashSet<String>()
        for (playlist in playlists) {
            if (playlist.isLiked) continue
            val members = playlist.songs.mapNotNull { byId[it.videoId] }.distinctBy { it.song.id }
            if (members.isEmpty()) continue
            val name = uniqueName(takenNames, ExportDocumentTree.safeName(playlist.title, playlist.id))
            members.forEach { song ->
                out += Entry(listOf(PlaylistFolder, name), song.song, null)
                placed += song.song.id
            }
        }

        songs.filter { it.song.id !in placed }.sortedBy { it.song.song.title.lowercase() }
            .forEach { out += Entry(listOf(MusicFolder), it.song, null) }
        return out
    }

    /** A folder per album under Albums with the album's cover, songs from no album go in Music. */
    private fun byAlbum(songs: List<DownloadedSong>): List<Entry> {
        val (withAlbum, loose) = songs.partition { albumTitle(it.song) != null }
        val out = ArrayList<Entry>()
        withAlbum.groupBy { it.song.album?.id ?: albumTitle(it.song)!! }.values
            .sortedBy { albumTitle(it.first().song)!!.lowercase() }
            .forEach { albumSongs ->
                val first = albumSongs.first()
                val name = ExportDocumentTree.safeName(albumTitle(first.song)!!, first.song.id)
                val cover = first.song.album?.thumbnailUrl ?: first.song.song.thumbnailUrl
                albumSongs.forEach { out += Entry(listOf(AlbumsFolder, name), it.song, cover) }
            }
        loose.sortedBy { it.song.song.title.lowercase() }.forEach { out += Entry(listOf(MusicFolder), it.song, null) }
        return out
    }

    private fun albumTitle(song: Song): String? =
        (song.album?.title ?: song.song.albumName)?.takeIf { it.isNotBlank() }

    /** "artist - title", the way most music players and file browsers expect a loose song. */
    private fun baseName(song: Song): String {
        val artist = song.artists.joinToString(", ") { it.name }
        val raw = if (artist.isBlank()) song.song.title else "$artist - ${song.song.title}"
        return ExportDocumentTree.safeName(raw, song.id)
    }

    private fun uniqueName(taken: MutableSet<String>, name: String): String {
        var candidate = name
        var n = 2
        while (!taken.add(candidate.lowercase())) candidate = "$name ($n)".also { n++ }
        return candidate
    }

    /** The downloaded file's bytes, or null when the file is gone. */
    private fun openAudio(uriString: String): InputStream? {
        val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return null
        return runCatching {
            when (uri.scheme) {
                "file" -> uri.path?.let(::File)?.takeIf { it.exists() }?.inputStream()
                else -> context.contentResolver.openInputStream(uri)
            }
        }.getOrNull()
    }

    /** The file extension to export under, read off the saved file's own name; m4a when it doesn't say. */
    private fun extensionOf(uriString: String): String {
        val name = displayNameOf(uriString)
        val ext = name?.substringAfterLast('.', "")?.takeIf { it.isNotEmpty() && it.length <= 5 && it.none { c -> c == '/' } }
        if (ext != null) return ext.lowercase()
        return sniffExtension(uriString)
    }

    private fun displayNameOf(uriString: String): String? {
        val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return null
        return when (uri.scheme) {
            "file" -> uri.path?.substringAfterLast('/')
            else -> runCatching {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
            }.getOrNull()
        }
    }

    /** Read off the file's first bytes rather than the saved format, since a download can carry any container. */
    private fun sniffExtension(uriString: String): String {
        val head = ByteArray(64)
        val read = runCatching {
            openAudio(uriString)?.use { input ->
                var total = 0
                while (total < head.size) {
                    val n = input.read(head, total, head.size - total)
                    if (n <= 0) break
                    total += n
                }
                total
            }
        }.getOrNull() ?: 0
        if (read < 4) return "m4a"
        fun at(offset: Int, text: String) = text.indices.all { head[offset + it] == text[it].code.toByte() }
        return when {
            at(0, "fLaC") -> "flac"
            // ogg holding opus is an .opus file, anything else in ogg stays .ogg
            at(0, "OggS") -> if (read >= 36 && (28..read - 8).any { at(it, "OpusHead") }) "opus" else "ogg"
            at(0, "ID3") || (head[0] == 0xFF.toByte() && (head[1].toInt() and 0xE0) == 0xE0) -> "mp3"
            at(0, "RIFF") -> "wav"
            head[0] == 0x1A.toByte() && head[1] == 0x45.toByte() && head[2] == 0xDF.toByte() && head[3] == 0xA3.toByte() -> "webm"
            read >= 8 && at(4, "ftyp") -> "m4a"
            else -> "m4a"
        }
    }

    /**
     * Timed lyrics go out as .lrc next to the song, which most players pick
     * up; untimed ones as .txt. The lyrics come from the file itself — CW
     * embeds them in downloads at download time — so there is nothing to
     * fetch and no network in the export.
     */
    private suspend fun lyricsOf(uriString: String): Pair<String, String>? {
        val lines = runCatching { EmbeddedLyrics.forUri(context, uriString) }.getOrNull()
            .orEmpty().filter { it.text.isNotBlank() }
        if (lines.isEmpty()) return null
        return if (lines.any { it.timeMs > 0 }) {
            lines.joinToString("\n") { "[${lrcTimestamp(it.timeMs)}]${it.text}" } to "lrc"
        } else {
            lines.joinToString("\n") { it.text } to "txt"
        }
    }

    private fun lrcTimestamp(timeMs: Long): String {
        val minutes = timeMs / 60_000
        val seconds = (timeMs % 60_000) / 1000
        val centiseconds = (timeMs % 1000) / 10
        return "%02d:%02d.%02d".format(minutes, seconds, centiseconds)
    }

    private suspend fun writeCover(documents: ExportDocumentTree, folder: String, name: String, url: String) {
        val request = ImageRequest.Builder(context)
            .data(url.resize(CoverPx, CoverPx))
            .allowHardware(false)
            .build()
        val bitmap = runCatching { context.imageLoader.execute(request).image?.toBitmap() }.getOrNull() ?: return
        // a missing cover never stops the songs
        runCatching {
            documents.write(folder, name) { out ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)) throw IOException("couldn't save $name")
            }
        }
    }
}
