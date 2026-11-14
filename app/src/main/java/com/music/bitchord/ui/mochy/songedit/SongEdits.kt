// Ported from Mochy Music (GPL-3.0), songedit/SongEdits.kt — ADAPTED:
// Mochy's follow(database, scope) reads a Room table ChordWave doesn't have,
// so the store is in-memory for the session (edits don't survive a restart
// yet — persisting them to DataStore is Phase 6 hardening). put() is the
// write path the edit sheet uses; of()/all/applyTo are verbatim.
package com.music.bitchord.ui.mochy.songedit

import com.music.bitchord.ui.mochy.models.MediaMetadata
import com.music.bitchord.ui.mochy.models.SongEdit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// every hand edit kept in memory, so queue items and the player can wear them without a database read on the main thread
object SongEdits {
    private val _all = MutableStateFlow<Map<String, SongEdit>>(emptyMap())
    val all: StateFlow<Map<String, SongEdit>> = _all.asStateFlow()

    fun of(songId: String?): SongEdit? = songId?.let { _all.value[it] }

    /** Save (or replace) the hand edit for one song. Session-only until a store lands. */
    fun put(edit: SongEdit) {
        _all.value = _all.value + (edit.songId to edit)
    }

    /** Drop the hand edit for one song. */
    fun remove(songId: String) {
        _all.value = _all.value - songId
    }

    fun applyTo(metadata: MediaMetadata): MediaMetadata = of(metadata.id)?.applyTo(metadata) ?: metadata
}
