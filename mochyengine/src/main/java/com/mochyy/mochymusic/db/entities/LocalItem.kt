// Ported from Mochy Music (GPL-3.0): db/entities/LocalItem.kt — verbatim, for the Mochy Room database (two-engine Tahap 2).
package com.mochyy.mochymusic.db.entities

sealed class LocalItem {
    abstract val id: String
    abstract val title: String
    abstract val thumbnailUrl: String?
}
