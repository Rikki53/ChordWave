// Ported from Mochy Music (GPL-3.0): db/entities/Event.kt (plain data
// class — Room annotations stripped; no database exists in Mochy mode).
// A single play event: which song, when, how long it played. SongMenu
// only null-checks it (the "Remove from history" row shows when an event
// exists); CW has no local play-event store, so callers pass null until
// a history store exists.
package com.music.bitchord.ui.mochy.models

import androidx.compose.runtime.Immutable
import java.time.LocalDateTime

@Immutable
data class Event(
    val id: Long = 0,
    val songId: String,
    val timestamp: LocalDateTime,
    val playTime: Long,
)
