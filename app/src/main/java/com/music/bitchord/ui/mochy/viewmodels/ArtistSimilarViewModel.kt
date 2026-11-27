// Ported from Mochy Music (GPL-3.0): viewmodels/ArtistSimilarViewModel.kt,
// re-implemented over ChordWave sources: YtMusicRepository.artistLanding()
// supplies the artist's page and each similar artist's page (Mappers6
// maps them onto Mochy's ArtistPage shape), ArtistPageCache.picks() the
// Home box's picks that lead every group. The grouping rules are Mochy's
// verbatim: picks first, then the page's own similar-artist and playlist
// shelves; songs = picks + three top songs interleaved per similar artist
// (limit 15); albums = picks + each similar artist's newest.
//
// Adaptations vs Mochy:
// - No saved-state handle: the host calls load(artistId) with the route
//   arg (coordinator decision — the route is available from
//   navigator.currentDetail). A repeated load() with the same id is a
//   no-op. artistId is a val backed by the loaded arg ("" until load()
//   runs); picks reads ArtistPageCache live, so it follows the loaded id.
// - Pages come from artistLanding() (one request each) instead of
//   Mochy's full artist fetch: the deep load only needs each similar
//   artist's top songs and newest album, which the landing page carries.
//   Pages are shared through ArtistPageStore, so an artist opened from
//   here later shows instantly.
// (phase2-detail NEEDS.md §(a) C6.)
package com.music.bitchord.ui.mochy.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.ui.mochy.models.ArtistGroup
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.ArtistItem
import com.music.bitchord.ui.mochy.models.innertube.ArtistPage
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.bitchord.ui.mochy.utils.ArtistPageCache
import com.music.bitchord.ui.mochy.utils.ArtistVideos
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

/** the world around one artist, grouped by kind rather than by who it came from */
data class SimilarGroups(
    val artists: List<ArtistItem> = emptyList(),
    val playlists: List<PlaylistItem> = emptyList(),
    val songs: List<SongItem> = emptyList(),
    val albums: List<AlbumItem> = emptyList(),
)

@HiltViewModel
class ArtistSimilarViewModel @Inject constructor() : ViewModel() {
    private var loadedArtistId = ""

    /** The route's artist id; "" until load() runs. */
    val artistId: String get() = loadedArtistId

    private val _page = MutableStateFlow<ArtistPage?>(null)
    val page: StateFlow<ArtistPage?> = _page

    /** what the Home box showed, when the page was opened from one: it leads every group */
    val picks: ArtistGroup?
        get() = loadedArtistId.takeIf { it.isNotEmpty() }?.let { ArtistPageCache.picks(it) }

    private val _groups = MutableStateFlow<SimilarGroups?>(null)
    val groups: StateFlow<SimilarGroups?> = _groups

    /** the songs and albums come from the similar artists' own pages, so they land a little later */
    private val _deepLoading = MutableStateFlow(true)
    val deepLoading: StateFlow<Boolean> = _deepLoading

    /** The args the current page belongs to; null until load() is called. */
    @Volatile
    private var loadedKey: String? = null

    /**
     * Loads the similar-artists page for one artist. The coordinator
     * patches the ported screen to call this with the route arg; a
     * repeated load() with the same id is a no-op.
     */
    fun load(artistId: String) {
        if (artistId == loadedKey) return
        loadedKey = artistId
        loadedArtistId = artistId
        _page.value = ArtistPageStore.page(artistId)
        val picksNow = ArtistPageCache.picks(artistId)
        _groups.value = picksNow?.let {
            SimilarGroups(
                artists = it.items.filterIsInstance<ArtistItem>().distinctBy { item -> item.id },
                playlists = it.items.filterIsInstance<PlaylistItem>().distinctBy { item -> item.id },
                songs = it.items.filterIsInstance<SongItem>().distinctBy { item -> item.id },
                albums = it.items.filterIsInstance<AlbumItem>().distinctBy { item -> item.id },
            )
        }
        _deepLoading.value = true
        viewModelScope.launch { run() }
    }

    private suspend fun run() {
        val id = loadedArtistId
        val picksNow = ArtistPageCache.picks(id)
        val pickedSongs = picksNow?.items.orEmpty().filterIsInstance<SongItem>().distinctBy { it.id }
        val pickedAlbums = picksNow?.items.orEmpty().filterIsInstance<AlbumItem>().distinctBy { it.id }
        val pickedPlaylists = picksNow?.items.orEmpty().filterIsInstance<PlaylistItem>().distinctBy { it.id }
        val pickedArtists = picksNow?.items.orEmpty().filterIsInstance<ArtistItem>().distinctBy { it.id }

        val page = _page.value ?: withContext(Dispatchers.IO) {
            YtMusicRepository.artistLanding(id)
                .onFailure { Timber.w(it, "ArtistSimilar: landing failed for $id") }
                .getOrNull()?.toMochyArtistPage(id)
        }?.also {
            ArtistPageStore.putPage(id, it)
            _page.value = it
        }
        if (id != loadedKey) return
        if (page == null) {
            _groups.value = _groups.value ?: SimilarGroups()
            _deepLoading.value = false
            return
        }

        val artists = (pickedArtists + page.sections
            .filter { it.items.firstOrNull() is ArtistItem }
            .flatMap { it.items.filterIsInstance<ArtistItem>() })
            .distinctBy { it.id }
        val playlists = (pickedPlaylists + page.sections
            .filter { it.items.firstOrNull() is PlaylistItem }
            .flatMap { it.items.filterIsInstance<PlaylistItem>() })
            .distinctBy { it.id }
        _groups.value = SimilarGroups(artists = artists, playlists = playlists, songs = pickedSongs, albums = pickedAlbums)

        // a handful of the similar artists' pages at once, each given a few seconds; the slow
        // or failing ones are just left out
        val pages = withContext(Dispatchers.IO) {
            artists.take(5).map { artist ->
                async {
                    ArtistPageStore.page(artist.id)
                        ?: withTimeoutOrNull(8_000) {
                            YtMusicRepository.artistLanding(artist.id).getOrNull()?.toMochyArtistPage(artist.id)
                        }?.also { ArtistPageStore.putPage(artist.id, it) }
                }
            }.awaitAll()
        }.filterNotNull()

        // the box's own songs first, then three top songs from each similar artist, one artist
        // at a time so no single one fills the row
        // one list, deduplicated as a whole: a repeated id would crash the row's keys
        val songs = (pickedSongs + ArtistVideos.interleave(
            pages.map { similar ->
                similar.sections
                    .firstOrNull { (it.items.firstOrNull() as? SongItem)?.album != null }
                    ?.items?.filterIsInstance<SongItem>()
                    ?.take(3)
                    .orEmpty()
            },
            limit = 15,
        )).distinctBy { it.id }
        // and each one's newest record
        val albums = (pickedAlbums + pages.mapNotNull { similar ->
            similar.sections
                .filter { it.items.firstOrNull() is AlbumItem }
                .flatMap { it.items.filterIsInstance<AlbumItem>() }
                .maxByOrNull { it.year ?: 0 }
        }).distinctBy { it.id }

        if (id != loadedKey) return
        _groups.value = SimilarGroups(artists = artists, playlists = playlists, songs = songs, albums = albums)
        _deepLoading.value = false
    }
}
