// Ported from Mochy Music (GPL-3.0): viewmodels/ArtistAlbumsViewModel.kt,
// re-implemented over ChordWave sources: LibraryArtistSource reads the
// artist's library row and albums from the signed-in account's library
// (Mochy read its Room database). No filters: Mochy's artistAlbumsPreview
// applies none here either (the explicit filter lives on ArtistViewModel's
// libraryAlbums, not on this page).
//
// Adaptations vs Mochy:
// - No saved-state handle: the host calls load(artistId) with the route
//   arg (coordinator decision — the route is available from
//   navigator.currentDetail). A repeated load() with the same id is a
//   no-op; a new id resets artist/albums first, so a reused VM never
//   shows the previous artist's albums while loading.
// (phase2-detail NEEDS.md §(a) C7.)
package com.music.bitchord.ui.mochy.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.bitchord.ui.mochy.models.Album
import com.music.bitchord.ui.mochy.models.Artist
import com.music.bitchord.ui.mochy.utils.ArtistPageCache
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class ArtistAlbumsViewModel @Inject constructor() : ViewModel() {
    private val _artist = MutableStateFlow<Artist?>(null)
    val artist: StateFlow<Artist?> = _artist

    private val _albums = MutableStateFlow<List<Album>>(emptyList())
    val albums: StateFlow<List<Album>> = _albums

    /** The args the current page belongs to; null until load() is called. */
    @Volatile
    private var loadedKey: String? = null

    /**
     * Loads the artist's library albums. The coordinator patches the
     * ported screen to call this with the route arg.
     */
    fun load(artistId: String) {
        if (artistId == loadedKey) return
        loadedKey = artistId
        _artist.value = null
        _albums.value = emptyList()
        viewModelScope.launch(Dispatchers.IO) {
            val nameHint = ArtistPageCache.header(artistId)?.name
            val page = LibraryArtistSource.libraryPage().getOrNull() ?: return@launch
            if (artistId != loadedKey) return@launch
            _artist.value = LibraryArtistSource.artistFor(page, artistId, nameHint)
            _albums.value = LibraryArtistSource.albumsFor(page, artistId, nameHint)
        }
    }
}
