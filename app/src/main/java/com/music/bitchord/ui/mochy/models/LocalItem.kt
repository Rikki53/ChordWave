// Ported from Mochy Music (GPL-3.0): db/entities/LocalItem.kt
// Room-free: pure in-memory shape, no database annotations.

package com.music.bitchord.ui.mochy.models

sealed class LocalItem {
    abstract val id: String
    abstract val title: String
    abstract val thumbnailUrl: String?
}
