// Ported from Mochy Music (GPL-3.0): db/entities/PlayCountEntity.kt — verbatim, for the Mochy Room database (two-engine Tahap 2).
package com.mochyy.mochymusic.db.entities

import androidx.compose.runtime.Immutable
import androidx.room.Entity

@Immutable
@Entity(
    tableName = "playCount",
    primaryKeys = ["song", "year", "month"]
)
class PlayCountEntity(
    val song: String,
    val year: Int = -1,
    val month: Int = -1,
    val count: Int = -1,
)
