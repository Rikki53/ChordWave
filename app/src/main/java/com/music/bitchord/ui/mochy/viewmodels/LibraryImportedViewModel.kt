// LibraryImportedViewModel adapter — phase2-library NEEDS.md §(a) C3 (NOT a
// Mochy port: Mochy's VM reads its import tables through the route arg in
// its saved state; CW has no saved-state route args in this app and no
// import store yet).
//
// Backs "Imported from ...": the songs one service brought in, whatever
// playlist they went into.
//
// The route arg: the screen takes no arg and calls hiltViewModel(), and the
// VM is activity-scoped (no per-destination back stack in this app), so the source
// CANNOT be a constructor val — navigating Spotify -> Tidal would keep
// showing Spotify. The host must call load(ImportSource.fromKey(arg)) when
// it opens library/imported/{source}; see NEEDS.md for the snippet. Until
// the import engine lands, the lists stay empty whatever the source is —
// the members exist with Mochy's shapes so the screens compile.

package com.music.bitchord.ui.mochy.viewmodels

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.music.bitchord.ui.mochy.importer.ImportSource
import com.music.bitchord.ui.mochy.models.Album
import com.music.bitchord.ui.mochy.models.Song
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

/** Backs "Imported from ...": the songs one service brought in, whatever playlist they went into. */
@HiltViewModel
class LibraryImportedViewModel @Inject constructor() : ViewModel() {

    /** Null for a route with a source this build doesn't know — the screen then has nothing to show. */
    var source: ImportSource? by mutableStateOf(null)
        private set

    private val _songs = MutableStateFlow<List<Song>>(emptyList())

    /** The service's songs, name ascending. Empty until the import engine lands. */
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()

    private val _albums = MutableStateFlow<List<Album>>(emptyList())

    /** The albums this service's songs come from, for its albums card. Empty until the import engine lands. */
    val albums: StateFlow<List<Album>> = _albums.asStateFlow()

    /**
     * Opens the imported view for [source]. The host calls this with the
     * {source} route segment on every navigation to
     * library/imported/{source} — the screen itself passes nothing.
     */
    fun load(source: ImportSource?) {
        this.source = source
        // No import store yet: nothing to list for any source. When the
        // import engine lands, this is where its per-source songs/albums
        // flow gets subscribed (songs name ascending, albums from those songs).
        _songs.value = emptyList()
        _albums.value = emptyList()
        Timber.d("LibraryImported: source=%s (no import store yet)", source?.key)
    }
}
