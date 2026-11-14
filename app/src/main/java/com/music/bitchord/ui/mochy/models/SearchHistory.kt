// Ported from Mochy Music (GPL-3.0): db/entities/SearchHistory.kt (shape only)
// Mochy's SearchHistory is a Room @Entity with a unique index on `query`.
// ChordWave keeps query history in its own SearchHistory store
// (com.music.bitchord.data.settings.SearchHistory); this plain data class
// is the shape the Modern UI passes around — the search adapters map
// between the two. Same pattern as Event/PlaylistSong.
package com.music.bitchord.ui.mochy.models

data class SearchHistory(
    val id: Long = 0,
    val query: String,
)
