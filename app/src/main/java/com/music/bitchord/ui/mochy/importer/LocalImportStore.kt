// LocalImportStore.kt — ChordWave adapter (no Mochy equivalent as a file:
// Mochy keeps imported files as Song rows in Room; CW has no local-file
// table, so this is the record of what the user imported from the phone).
//
// One SharedPreferences JSON document, like OfflinePlaylists: the imported
// files (uri, tags, when they came in, which import session brought them),
// published as Mochy Songs for the Local shelf. The files themselves stay
// where they are on the phone; removing a record only takes it out of the
// library.
//
// Identity: a record's id is its MediaStore content uri when the file has
// one, else the document uri it was picked by — the same identity
// LocalMediaRepository gives device files (videoId = content uri), so an
// imported file and the same file in an offline playlist are one song.

package com.music.bitchord.ui.mochy.importer

import android.content.Context
import android.content.SharedPreferences
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.music.bitchord.data.model.Song as CwSong
import com.music.bitchord.ui.mochy.models.ArtistEntity
import com.music.bitchord.ui.mochy.models.Song
import com.music.bitchord.ui.mochy.models.SongEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

object LocalImportStore {

    /** One imported file. [altUri] is the file's other form (document uri when [uri] is its MediaStore uri), so neither form is ever offered twice. */
    data class Record(
        val uri: String,
        val altUri: String?,
        val title: String,
        val artist: String?,
        val album: String?,
        val genre: String?,
        val durationMs: Long,
        val artworkUri: String?,
        val addedAtMs: Long,
        val sessionId: String,
    )

    private const val PREFS_NAME = "mochy_local_imports"
    private const val KEY_RECORDS = "records_json"
    /** Set once the one-time genre backfill has run, so it never re-reads every file. */
    private const val KEY_GENRES_BACKFILLED = "genres_backfilled_v1"

    private lateinit var prefs: SharedPreferences
    private var records: List<Record> = emptyList()

    private val _songs = MutableStateFlow<List<Song>>(emptyList())

    /** The imported files as Mochy Songs, newest first. */
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()

    fun init(context: Context) {
        if (this::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        records = load()
        publish()
    }

    private val isReady: Boolean get() = this::prefs.isInitialized

    /** Every uri already in the library, in both forms, so the scan never offers a file twice. */
    fun knownUris(): Set<String> {
        if (!isReady) return emptySet()
        return records.flatMapTo(HashSet()) { listOfNotNull(it.uri, it.altUri) }
    }

    /** Adds [incoming], skipping files already recorded. Returns how many were new. */
    fun add(context: Context, incoming: List<Record>): Int {
        if (!isReady) init(context)
        val known = records.mapTo(HashSet()) { it.uri }
        val fresh = incoming.filter { it.uri !in known }
        if (fresh.isEmpty()) return 0
        records = records + fresh
        persist()
        publish()
        return fresh.size
    }

    /** Takes [songIds] out of the library. The files stay on the phone. Returns how many went. */
    fun remove(songIds: Collection<String>): Int {
        if (!isReady || songIds.isEmpty()) return 0
        val before = records.size
        records = records.filterNot { it.uri in songIds || (it.altUri != null && it.altUri in songIds) }
        val removed = before - records.size
        if (removed > 0) {
            persist()
            publish()
        }
        return removed
    }

    /** Takes out the most recent import session. Null when there is no session to undo. */
    fun undoLatestSession(): Int? {
        if (!isReady) return null
        val latest = records.maxByOrNull { it.addedAtMs }?.sessionId ?: return null
        val removed = records.count { it.sessionId == latest }
        records = records.filterNot { it.sessionId == latest }
        persist()
        publish()
        return removed
    }

    /**
     * One-time pass over files imported before genre tags were read: their
     * records have no genre, so the Genres tab would miss them. Reads each
     * file's tag once and never runs again. Blocking — call from a
     * background thread. Files with no genre tag stay genre-less; files
     * imported after this get their genre at import time.
     */
    fun backfillGenres(context: Context) {
        if (!isReady) init(context)
        if (prefs.getBoolean(KEY_GENRES_BACKFILLED, false)) return
        val retriever = MediaMetadataRetriever()
        try {
            var changed = false
            records = records.map { record ->
                if (record.genre != null) return@map record
                val genre = runCatching {
                    retriever.setDataSource(context, Uri.parse(record.uri))
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)
                        ?.takeIf { it.isNotBlank() }
                }.getOrNull()
                if (genre == null) record else {
                    changed = true
                    record.copy(genre = genre)
                }
            }
            if (changed) {
                persist()
                publish()
            }
        } finally {
            runCatching { retriever.release() }
            prefs.edit().putBoolean(KEY_GENRES_BACKFILLED, true).apply()
        }
    }

    /** The record for [uri] (either form), as the CW Song OfflinePlaylists needs to file it. */
    fun cwSong(uri: String): CwSong? {
        val record = records.firstOrNull { it.uri == uri || it.altUri == uri } ?: return null
        return CwSong(
            videoId = record.uri,
            title = record.title,
            artist = record.artist.orEmpty(),
            thumbnailUrl = record.artworkUri,
            albumName = record.album,
            localUri = record.uri,
        )
    }

    private fun publish() {
        _songs.value = records.map { it.toSong() }.sortedByDescending { it.song.inLibrary }
    }

    private fun Record.toSong(): Song {
        val addedAt = LocalDateTime.ofInstant(Instant.ofEpochMilli(addedAtMs), ZoneId.systemDefault())
        val artistEntity = artist?.takeIf { it.isNotBlank() }?.let {
            ArtistEntity(id = it, name = it, isLocal = true)
        }
        return Song(
            song = SongEntity(
                id = uri,
                title = title,
                duration = (durationMs / 1_000).toInt().takeIf { it > 0 } ?: -1,
                thumbnailUrl = artworkUri,
                albumName = album,
                genre = genre,
                inLibrary = addedAt,
                isLocal = true,
                localUri = uri,
            ),
            artists = listOfNotNull(artistEntity),
            // No album row: the album is only a tag, so the shelf groups it
            // under "tag:<name>" and opening it plays its songs.
            album = null,
        )
    }

    private fun persist() {
        val array = JSONArray()
        records.forEach { record ->
            val json = JSONObject()
            json.put("uri", record.uri)
            putOpt(json, "altUri", record.altUri)
            json.put("title", record.title)
            putOpt(json, "artist", record.artist)
            putOpt(json, "album", record.album)
            putOpt(json, "genre", record.genre)
            json.put("durationMs", record.durationMs)
            putOpt(json, "artworkUri", record.artworkUri)
            json.put("addedAtMs", record.addedAtMs)
            json.put("sessionId", record.sessionId)
            array.put(json)
        }
        prefs.edit().putString(KEY_RECORDS, array.toString()).apply()
    }

    private fun load(): List<Record> = runCatching {
        val raw = prefs.getString(KEY_RECORDS, null) ?: return emptyList()
        val array = JSONArray(raw)
        List(array.length()) { index ->
            val json = array.getJSONObject(index)
            Record(
                uri = json.getString("uri"),
                altUri = getOpt(json, "altUri"),
                title = json.optString("title", "Unknown song"),
                artist = getOpt(json, "artist"),
                album = getOpt(json, "album"),
                genre = getOpt(json, "genre"),
                durationMs = json.optLong("durationMs", 0L),
                artworkUri = getOpt(json, "artworkUri"),
                addedAtMs = json.optLong("addedAtMs", 0L),
                sessionId = json.optString("sessionId", ""),
            )
        }
    }.onFailure { Timber.w(it, "LocalImport: couldn't read the imported-files record, starting empty") }
        .getOrDefault(emptyList())

    private fun putOpt(json: JSONObject, key: String, value: String?) {
        if (value == null) json.put(key, JSONObject.NULL) else json.put(key, value)
    }

    private fun getOpt(json: JSONObject, key: String): String? =
        if (json.isNull(key)) null else json.optString(key, null)
}
