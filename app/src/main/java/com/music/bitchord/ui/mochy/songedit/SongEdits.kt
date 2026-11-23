// Ported from Mochy Music (GPL-3.0), songedit/SongEdits.kt — ADAPTED:
// Mochy's follow(database, scope) reads a Room table ChordWave doesn't have,
// so the store is in memory as a StateFlow and on disk as one JSON document
// in SharedPreferences (the OfflinePlaylists pattern: writes are conflated,
// off the main thread, so nothing here ever waits on the disk). Edits
// survive a restart; a broken document reads as no edits rather than
// crashing the app. put() is the write path the edit sheet uses; of()/all/
// applyTo are verbatim.
package com.music.bitchord.ui.mochy.songedit

import android.content.Context
import android.content.SharedPreferences
import com.music.bitchord.ui.mochy.models.MediaMetadata
import com.music.bitchord.ui.mochy.models.SongEdit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

// every hand edit kept in memory, so queue items and the player can wear them without a database read on the main thread
object SongEdits {
    private const val PREF_NAME = "bitchord_song_edits"
    private const val KEY_EDITS = "edits_json"

    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(SongEdit.serializer())

    private lateinit var prefs: SharedPreferences
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _all = MutableStateFlow<Map<String, SongEdit>>(emptyMap())
    val all: StateFlow<Map<String, SongEdit>> = _all.asStateFlow()

    /** Called once from the Application, before anything edits a song. */
    fun init(context: Context) {
        if (this::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        _all.value = load()
        // Everything after what was just read, however soon it comes: an edit
        // made before this collector is up is still the newest state when it is.
        val loaded = _all.value
        io.launch {
            all.filter { it !== loaded }.conflate().collect { persist(it) }
        }
    }

    fun of(songId: String?): SongEdit? = songId?.let { _all.value[it] }

    /** Save (or replace) the hand edit for one song. */
    fun put(edit: SongEdit) {
        _all.value = _all.value + (edit.songId to edit)
    }

    /** Drop the hand edit for one song. */
    fun remove(songId: String) {
        _all.value = _all.value - songId
    }

    fun applyTo(metadata: MediaMetadata): MediaMetadata = of(metadata.id)?.applyTo(metadata) ?: metadata

    private fun load(): Map<String, SongEdit> {
        val raw = prefs.getString(KEY_EDITS, null)?.takeIf { it.isNotBlank() } ?: return emptyMap()
        return runCatching { json.decodeFromString(serializer, raw) }
            .getOrNull().orEmpty().associateBy { it.songId }
    }

    private fun persist(edits: Map<String, SongEdit>) {
        val raw = runCatching { json.encodeToString(serializer, edits.values.toList()) }.getOrNull() ?: return
        prefs.edit().putString(KEY_EDITS, raw).apply()
    }
}
