// Ported from Mochy Music (GPL-3.0): viewmodels/LocalGenreViewModel.kt,
// re-implemented over ChordWave sources. Mochy groups every song in its
// database by tag genre, falling back to each artist's genre from iTunes;
// CW has no artist-genre lookup, so only imported files with a genre tag
// appear here — a song tagged "Rock; Pop" sits in both, like Mochy's.
// Downloaded and streamed songs have no tags and stay out.
package com.music.bitchord.ui.mochy.viewmodels

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.bitchord.ui.mochy.importer.LocalImportStore
import com.music.bitchord.ui.mochy.models.Song
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Immutable
data class LocalGenre(val name: String, val songs: List<Song>)

fun localGenreRoute(name: String) = "local_genre/${Uri.encode(name)}"

private fun Song.tagGenres(): List<String> =
    song.genre.orEmpty().split(";").map { it.trim() }.filter { it.isNotEmpty() }

// one genre per name whatever its letter case, spelled the way it was first seen, a to z
private fun groupByGenre(songs: List<Song>): List<LocalGenre> {
    val groups = LinkedHashMap<String, Pair<String, MutableList<Song>>>()
    songs.forEach { song ->
        song.tagGenres().forEach { genre ->
            groups.getOrPut(genre.lowercase()) { genre to mutableListOf() }.second += song
        }
    }
    return groups.values.map { (name, list) -> LocalGenre(name, list) }.sortedBy { it.name.lowercase() }
}

@HiltViewModel
class LocalGenresViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : ViewModel() {
    // null while it's first read; nothing is read while the tab isn't open
    val genres: StateFlow<List<LocalGenre>?> = LocalImportStore.songs
        .map { songs -> groupByGenre(songs) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        // files imported before genre tags were read get one background pass
        viewModelScope.launch(Dispatchers.IO) { LocalImportStore.backfillGenres(context) }
    }
}
