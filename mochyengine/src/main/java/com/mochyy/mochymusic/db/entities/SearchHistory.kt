// Ported from Mochy Music (GPL-3.0): db/entities/SearchHistory.kt — verbatim, for the Mochy Room database (two-engine Tahap 2).
package com.mochyy.mochymusic.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "search_history",
    indices = [
        Index(
            value = ["query"],
            unique = true,
        ),
    ],
)
data class SearchHistory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val query: String,
)
