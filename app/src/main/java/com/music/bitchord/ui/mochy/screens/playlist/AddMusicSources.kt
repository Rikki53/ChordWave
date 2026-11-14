// Ported from Mochy Music (GPL-3.0): ui/screens/playlist/AddMusicSources.kt
// Only the search half ports: the library filtering and the debounce are
// pure, and the online leg goes through AddMusicViewModel.searchOnlineSongs
// (NEEDS.md C6). loadPlaylistSuggestions / songsLike / findOnYouTube /
// addPicksToPlaylist and their helpers (interleave, nameKey) are engine —
// they read the database and call YouTube — so they live in the view-model
// contract, not here.
package com.music.bitchord.ui.mochy.screens.playlist

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import com.music.bitchord.ui.mochy.models.Song
import com.music.bitchord.ui.mochy.models.innertube.SongItem
import com.music.bitchord.ui.mochy.viewmodels.AddMusicViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/*
 * Where the "Add music" sheet's search comes from: the library first,
 * filtered here and shown at once, then YouTube's songs underneath once
 * they arrive (via the view model).
 */

/** Library matches shown above the online results, so a short query can't bury them. */
private const val MaxLibraryHits = 20

/** Wait for a pause in typing before searching, so we don't fire a request per letter. */
private const val SearchDebounceMs = 300L

/** What a search turned up: library hits straight away, then YouTube's songs (null while loading). */
internal class SongSearchResults(
    /** What was searched for, which can trail the text field while the user is still typing. */
    val query: String,
    val library: List<Song>,
    val online: List<SongItem>?,
    val onlineFailed: Boolean,
)

/**
 * Searches as the user types: their library first, filtered here and shown at once, then
 * YouTube's songs underneath once they arrive.
 */
@Composable
internal fun rememberSongSearch(
    needle: String,
    library: List<Song>?,
    viewModel: AddMusicViewModel,
): SongSearchResults? {
    val currentLibrary by rememberUpdatedState(library)
    var results by remember { mutableStateOf<SongSearchResults?>(null) }

    LaunchedEffect(needle) {
        if (needle.isEmpty()) {
            results = null
            return@LaunchedEffect
        }
        delay(SearchDebounceMs)

        val hits = withContext(Dispatchers.Default) {
            currentLibrary.orEmpty().filter { it.matches(needle) }.take(MaxLibraryHits)
        }
        results = SongSearchResults(query = needle, library = hits, online = null, onlineFailed = false)

        val found = withContext(Dispatchers.IO) {
            viewModel.searchOnlineSongs(needle)
        }
        // A song already listed under the library hits isn't worth a second row.
        val hitIds = hits.mapTo(HashSet()) { it.id }
        results = SongSearchResults(
            query = needle,
            library = hits,
            online = found.orEmpty().filter { it.id !in hitIds }.distinctBy { it.id },
            onlineFailed = found == null,
        )
    }
    return results
}

private fun Song.matches(needle: String): Boolean =
    song.title.contains(needle, ignoreCase = true) ||
        artists.any { it.name.contains(needle, ignoreCase = true) }
