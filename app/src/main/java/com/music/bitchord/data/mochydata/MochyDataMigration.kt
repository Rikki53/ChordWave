// The one-time migration of ChordWave's legacy library data into the Mochy
// Room database — the shared data source of the two-engine architecture
// (RENCANA-DUA-MESIN.md Tahap 2, "Basis data"; decisions: one shared source,
// shared statistics/history).
//
// Safety contract:
// - The legacy stores are NEVER modified or deleted here. SharedPreferences
//   (offline playlists, search history, the downloads index) and the
//   monthly listening buckets stay exactly as they were, so the Classic UI
//   keeps working untouched and a failed migration loses nothing.
// - Before anything is written to Room, the legacy files are copied to
//   filesDir/mochy_migration_backup/v1-<timestamp>/ with a manifest.
// - The migration is idempotent: deterministic ids + IGNORE inserts +
//   rebuilt playlist membership mean running it twice lands on the same
//   rows. Its state lives in its own preferences file; a completed run is
//   not repeated, a failed one can be retried on the next launch.
// - On any failure the state is FAILED and the Modern UI keeps reading the
//   legacy stores (see MochyLibraryRepository.ready).
//
// What moves:
// - OfflinePlaylists (incl. the built-in Liked list) -> playlist/song rows.
// - ListeningStats monthly buckets -> song rows, PlayCountEntity per
//   (song, year, month), and totalPlayTime. Per-play Event rows cannot be
//   reconstructed from ChordWave's monthly aggregates (the raw plays were
//   never stored), so none are fabricated.
// - Downloads (the files already live in Music/ChordWave; only their
//   metadata moves) -> song rows marked downloaded.
// - SearchHistory -> search_history rows.
// AppSettings stays the parent store for settings, per the plan.

package com.music.bitchord.data.mochydata

import android.content.Context
import android.content.SharedPreferences
import com.music.bitchord.data.DebugLog
import com.music.bitchord.data.OfflinePlaylists
import com.music.bitchord.data.settings.SearchHistory
import com.music.bitchord.data.stats.StoredBucket
import com.music.bitchord.download.Downloads
import com.mochyy.mochymusic.db.InternalDatabase
import com.mochyy.mochymusic.db.MusicDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** The single app-wide [MusicDatabase], opened once. */
object MochyDatabaseHolder {
    @Volatile
    private var instance: MusicDatabase? = null

    fun get(context: Context): MusicDatabase =
        instance ?: synchronized(this) {
            instance ?: InternalDatabase.newInstance(context.applicationContext).also { instance = it }
        }
}

object MochyDataMigration {

    const val MIGRATION_VERSION = 1

    private const val PREF_NAME = "mochy_data_migration"
    private const val KEY_STATE = "state"
    private const val KEY_VERSION = "version"
    private const val KEY_BACKUP = "backup_path"
    private const val KEY_FINISHED_AT = "finished_at"
    private const val KEY_ERROR = "error"
    private const val KEY_COUNTS = "counts"

    enum class Status { NOT_STARTED, RUNNING, DONE, FAILED }

    data class Report(
        val status: Status,
        val songs: Int = 0,
        val playlists: Int = 0,
        val playCounts: Int = 0,
        val searchHistory: Int = 0,
        val backupPath: String? = null,
        val error: String? = null,
    )

    private val _status = MutableStateFlow(Status.NOT_STARTED)
    val status: StateFlow<Status> = _status.asStateFlow()

    private val lock = Mutex()

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun currentStatus(context: Context): Status {
        val p = prefs(context)
        if (p.getInt(KEY_VERSION, 0) != MIGRATION_VERSION) return Status.NOT_STARTED
        return runCatching { Status.valueOf(p.getString(KEY_STATE, null) ?: "") }
            .getOrDefault(Status.NOT_STARTED)
            .also { _status.value = it }
    }

    /**
     * Runs the migration when it has not completed yet. Safe to call on
     * every launch: a DONE state returns at once, and the work itself is
     * idempotent. Must be called after OfflinePlaylists, SearchHistory,
     * ListeningStats and Downloads are initialised (BitChordApplication
     * does, on IO).
     */
    suspend fun runIfNeeded(context: Context): Report = lock.withLock {
        val app = context.applicationContext
        if (currentStatus(app) == Status.DONE) {
            return Report(Status.DONE, backupPath = prefs(app).getString(KEY_BACKUP, null))
        }
        _status.value = Status.RUNNING
        prefs(app).edit().putString(KEY_STATE, Status.RUNNING.name).apply()
        try {
            val report = withContext(Dispatchers.IO) { migrate(app) }
            prefs(app).edit()
                .putString(KEY_STATE, Status.DONE.name)
                .putInt(KEY_VERSION, MIGRATION_VERSION)
                .putString(KEY_BACKUP, report.backupPath)
                .putLong(KEY_FINISHED_AT, System.currentTimeMillis())
                .putString(
                    KEY_COUNTS,
                    "songs=${report.songs} playlists=${report.playlists} " +
                        "playCounts=${report.playCounts} searchHistory=${report.searchHistory}",
                )
                .remove(KEY_ERROR)
                .apply()
            _status.value = Status.DONE
            DebugLog.i("MochyMigration", "done: $report")
            report
        } catch (e: Exception) {
            DebugLog.w("MochyMigration", "failed, legacy stores untouched: ${e.message}")
            prefs(app).edit()
                .putString(KEY_STATE, Status.FAILED.name)
                .putInt(KEY_VERSION, MIGRATION_VERSION)
                .putString(KEY_ERROR, e.message ?: e.javaClass.simpleName)
                .apply()
            _status.value = Status.FAILED
            Report(Status.FAILED, error = e.message)
        }
    }

    private suspend fun migrate(context: Context): Report {
        val backupDir = backupLegacyStores(context)
        val db = MochyDatabaseHolder.get(context)
        val now = LocalDateTime.now()
        val nowMillis = now.toRoomMillis()

        var songRows = 0
        var playlistRows = 0
        var playCountRows = 0
        var searchRows = 0

        // Collect everything first, then write in one transaction: a crash
        // mid-write rolls back instead of leaving a half-migrated library.
        val mappedSongs = LinkedHashMap<String, MappedSong>()
        fun collect(mapped: MappedSong) {
            // First writer wins for the row itself; later passes only add
            // flags through the targeted updates below.
            mappedSongs.putIfAbsent(mapped.song.id, mapped)
        }

        // 1. Offline playlists (incl. Liked).
        val offlinePlaylists = OfflinePlaylists.playlists.value
        val likedIds = mutableListOf<String>()
        for (playlist in offlinePlaylists) {
            for (song in playlist.songs) {
                collect(song.toRoomSong(isLocal = true, inLibraryAt = now))
                if (playlist.isLiked) likedIds += song.videoId
            }
        }

        // 2. Listening buckets -> songs + play counts + total play time.
        val playCounts = mutableListOf<com.mochyy.mochymusic.db.entities.PlayCountEntity>()
        val totalPlayTimes = HashMap<String, Long>()
        val bucketDir = File(context.filesDir, "listening")
        val json = Json { ignoreUnknownKeys = true }
        bucketDir.listFiles { f -> f.name.endsWith(".json") }?.sortedBy { it.name }?.forEach { file ->
            val bucket = runCatching {
                json.decodeFromString(StoredBucket.serializer(), file.readText())
            }.getOrNull() ?: return@forEach
            val parts = bucket.month.split("-")
            val year = parts.getOrNull(0)?.toIntOrNull() ?: return@forEach
            val month = parts.getOrNull(1)?.toIntOrNull() ?: return@forEach
            for (track in bucket.tracks) {
                if (track.id.isBlank()) continue
                val (mapped, count) = track.toRoomRows(year, month)
                collect(mapped)
                playCounts += count
                totalPlayTimes[track.id] = (totalPlayTimes[track.id] ?: 0L) + track.ms
            }
        }

        // 3. Downloads metadata (files stay in Music/ChordWave).
        val downloaded = runCatching { Downloads.getDownloadedSongs(context) }.getOrDefault(emptyList())
        val downloadedIds = mutableListOf<String>()
        for (song in downloaded) {
            collect(song.toRoomSong(isDownloaded = true))
            downloadedIds += song.videoId
        }

        // 4. Search history (oldest first, so the newest gets the highest id).
        val searchEntries = SearchHistory.recent.value.reversed().map { it.toRoomSearchHistory() }

        db.withTransaction {
            val all = mappedSongs.values.toList()
            insertArtists(all.flatMap { it.artists }.distinctBy { it.id })
            insertAlbums(all.mapNotNull { it.album }.distinctBy { it.id })
            insertSongs(all.map { it.song })
            songRows = all.size
            insertSongArtistMaps(all.flatMap { it.songArtistMaps }.distinct())
            insertSongAlbumMaps(all.mapNotNull { it.songAlbumMap }.distinct())
            insertAlbumArtistMaps(all.flatMap { it.albumArtistMaps }.distinct())

            insertPlaylists(offlinePlaylists.map { it.toRoomPlaylist() })
            playlistRows = offlinePlaylists.size
            for (playlist in offlinePlaylists) {
                val roomId = playlistIdFor(playlist.id)
                deletePlaylistSongs(roomId)
                insertPlaylistSongMaps(playlist.toRoomSongMaps())
            }

            if (likedIds.isNotEmpty()) {
                markLiked(likedIds.distinct(), nowMillis)
                markInLibrary(likedIds.distinct(), nowMillis)
            }
            if (downloadedIds.isNotEmpty()) markDownloaded(downloadedIds.distinct(), nowMillis)
            insertPlayCounts(playCounts)
            playCountRows = playCounts.size
            for ((id, total) in totalPlayTimes) setTotalPlayTime(id, total)
            insertSearchHistory(searchEntries)
            searchRows = searchEntries.size
        }

        return Report(
            status = Status.DONE,
            songs = songRows,
            playlists = playlistRows,
            playCounts = playCountRows,
            searchHistory = searchRows,
            backupPath = backupDir.absolutePath,
        )
    }

    /**
     * Copies the legacy stores aside before Room is touched. An existing
     * v1 backup is kept (it is the oldest, pre-migration copy) instead of
     * being overwritten by a retry.
     */
    private fun backupLegacyStores(context: Context): File {
        val root = File(context.filesDir, "mochy_migration_backup")
        root.listFiles { f -> f.isDirectory && f.name.startsWith("v$MIGRATION_VERSION-") }
            ?.minByOrNull { it.name }
            ?.let { return it }

        val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
        val dir = File(root, "v$MIGRATION_VERSION-$stamp").apply { mkdirs() }
        val manifest = StringBuilder("ChordWave legacy backup before Mochy Room migration v$MIGRATION_VERSION\n")

        fun copy(source: File, name: String) {
            if (!source.isFile) {
                manifest.append("missing: ${source.path}\n")
                return
            }
            val target = File(dir, name)
            source.copyTo(target, overwrite = true)
            manifest.append("$name <- ${source.path} (${target.length()} bytes)\n")
        }

        val prefsDir = File(context.dataDir, "shared_prefs")
        copy(File(prefsDir, "bitchord_offline_playlists.xml"), "bitchord_offline_playlists.xml")
        copy(File(prefsDir, "bitchord_settings.xml"), "bitchord_settings.xml")
        val listening = File(context.filesDir, "listening")
        listening.listFiles { f -> f.name.endsWith(".json") }?.sortedBy { it.name }?.forEach { file ->
            copy(file, "listening-${file.name}")
        }
        File(dir, "manifest.txt").writeText(manifest.toString())
        return dir
    }
}
