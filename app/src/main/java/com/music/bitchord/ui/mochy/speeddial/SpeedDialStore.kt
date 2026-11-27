// Ported from Mochy Music (GPL-3.0): db/entities/SpeedDialItem.kt +
// db/daos/SpeedDialDao.kt + the pin half of db/MusicDatabase.kt — ADAPTED:
// Mochy kept pins in a Room table; CW has no Room, so the store is in
// memory as a StateFlow and on disk as one JSON document in
// SharedPreferences (the OfflinePlaylists/SongEdits pattern). The item
// shape, the 9-pin cap with the oldest evicted, and the "pins first" dial
// order are Mochy's. One deliberate deviation: an item whose type is
// unknown reads as no item (null) instead of throwing, so a corrupt row
// can't take the home feed down.
// This is the Modern Home speed dial's store — unrelated to Classic's
// AppSettings.pinnedPlaylists (playlist browseIds for the library's pinned
// row, max 5, refuse-over-limit).
package com.music.bitchord.ui.mochy.speeddial

import android.content.Context
import android.content.SharedPreferences
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.Artist
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.YTItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** One thing pinned to the Home speed dial: a song, album, artist or playlist. */
@Serializable
data class SpeedDialItem(
    val id: String,
    val secondaryId: String? = null,
    val title: String,
    val subtitle: String? = null,
    val thumbnailUrl: String? = null,
    val type: String,
    val explicit: Boolean = false,
    val createDate: Long = System.currentTimeMillis(),
) {
    companion object {
        const val TYPE_SONG = "SONG"
        const val TYPE_ALBUM = "ALBUM"
        const val TYPE_ARTIST = "ARTIST"
        const val TYPE_PLAYLIST = "PLAYLIST"
    }

    // the card the dial renders, and what a tap on it does — the same
    // YTItem shapes the auto-filled dial already uses
    fun toYTItem(): YTItem? {
        return when (type) {
            TYPE_SONG -> SongItem(
                id = id,
                title = title,
                artists = subtitle?.split(", ")?.map { Artist(name = it, id = null) } ?: emptyList(),
                thumbnail = thumbnailUrl ?: "",
                explicit = explicit,
            )
            TYPE_ALBUM -> AlbumItem(
                browseId = id,
                playlistId = secondaryId ?: "",
                title = title,
                artists = subtitle?.split(", ")?.map { Artist(name = it, id = null) },
                thumbnail = thumbnailUrl ?: "",
                explicit = explicit,
            )
            TYPE_ARTIST -> ArtistItem(
                id = id,
                title = title,
                thumbnail = thumbnailUrl,
                shuffleEndpoint = null,
                radioEndpoint = null,
            )
            TYPE_PLAYLIST -> PlaylistItem(
                id = id,
                title = title,
                author = subtitle?.let { Artist(name = it, id = null) },
                songCountText = null,
                thumbnail = thumbnailUrl,
                playEndpoint = null,
                shuffleEndpoint = null,
                radioEndpoint = null,
            )
            else -> null
        }
    }
}

/** The pins behind the Home speed dial, oldest first. */
object SpeedDialStore {
    /** How many pins the dial holds; pinning past it drops the oldest pin. */
    const val MaxPinned = 9

    private const val PREF_NAME = "bitchord_speed_dial"
    private const val KEY_PINS = "pins_json"

    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(SpeedDialItem.serializer())

    private lateinit var prefs: SharedPreferences
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _all = MutableStateFlow<List<SpeedDialItem>>(emptyList())
    val all: StateFlow<List<SpeedDialItem>> = _all.asStateFlow()

    /** Called once from the Application, before anything pins or reads the dial. */
    fun init(context: Context) {
        if (this::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        _all.value = load()
        // Everything after what was just read, however soon it comes: a pin
        // made before this collector is up is still the newest state when it is.
        val loaded = _all.value
        io.launch {
            all.filter { it !== loaded }.conflate().collect { persist(it) }
        }
    }

    fun isPinned(id: String): Boolean = _all.value.any { it.id == id }

    /** Pin [item], dropping the oldest pin when the dial is already full. */
    fun pin(item: SpeedDialItem) {
        val current = _all.value.filterNot { it.id == item.id }
        val trimmed = if (current.size >= MaxPinned) current.drop(1) else current
        _all.value = trimmed + item.copy(createDate = System.currentTimeMillis())
    }

    fun unpin(id: String) {
        _all.value = _all.value.filterNot { it.id == id }
    }

    /** Pin [item] when it isn't pinned, unpin it when it is; true when pinned afterwards. */
    fun toggle(item: SpeedDialItem): Boolean {
        return if (isPinned(item.id)) {
            unpin(item.id)
            false
        } else {
            pin(item)
            true
        }
    }

    private fun load(): List<SpeedDialItem> {
        val raw = prefs.getString(KEY_PINS, null)?.takeIf { it.isNotBlank() } ?: return emptyList()
        return runCatching { json.decodeFromString(serializer, raw) }.getOrNull().orEmpty()
    }

    private fun persist(pins: List<SpeedDialItem>) {
        val raw = runCatching { json.encodeToString(serializer, pins) }.getOrNull() ?: return
        prefs.edit().putString(KEY_PINS, raw).apply()
    }
}
