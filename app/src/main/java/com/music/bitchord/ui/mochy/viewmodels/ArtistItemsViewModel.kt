// Ported from Mochy Music (GPL-3.0): viewmodels/ArtistItemsViewModel.kt,
// re-implemented over ChordWave sources. Mochy fetches one shelf's page
// through YouTube.artistItems(); CW has no single equivalent, so the fetch
// picks by what the endpoint holds:
// - a song shelf (top songs) pages through YtMusicRepository.browseSongs()
//   / moreSongs(), which also supply the page title from the header;
// - anything else (albums, artists, playlists, videos) is a card grid,
//   browsed through Innertube directly and parsed with the repo's own
//   parseLibraryItemPage() (the parser itemsPaged uses for exactly these
//   pages) + parseBrowseHeader() for the title, with continuations
//   followed through Innertube.browseContinuation().
//
// Adaptations vs Mochy:
// - No saved-state handle: the host calls load(artistId, browseId, params)
//   with the route args (coordinator decision — the route is available
//   from navigator.currentDetail). A repeated load() with the same args
//   is a no-op; new args reset title/itemsPage first, so a reused VM
//   never shows the previous shelf while loading. artistId is a val
//   backed by the loaded arg (null until load() runs).
// - params "null" (the literal string — the route builder writes it when
//   params are absent) is treated as absent, the way Mochy's
//   GenreViewModel does.
// - A null/blank browseId fails the load instead of crashing on Mochy's
//   browseId!!.
// (phase2-detail NEEDS.md §(a) C5.)
package com.music.bitchord.ui.mochy.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.innertube.Innertube
import com.music.bitchord.data.innertube.InnertubeParser
import com.music.bitchord.ui.mochy.constants.HideExplicitKey
import com.music.bitchord.ui.mochy.constants.HideVideoSongsKey
import com.music.bitchord.ui.mochy.models.ItemsPage
import com.music.bitchord.ui.mochy.models.innertube.filterExplicit
import com.music.bitchord.ui.mochy.models.innertube.filterVideoSongs
import com.music.bitchord.ui.mochy.utils.dataStore
import com.music.bitchord.ui.mochy.utils.get
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

@HiltViewModel
class ArtistItemsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private var loadedArtistId: String? = null

    /** The route's artist id; null until load() runs. */
    val artistId: String? get() = loadedArtistId

    val title = MutableStateFlow("")
    val itemsPage = MutableStateFlow<ItemsPage?>(null)
    val loadFailed = MutableStateFlow(false)

    private var browseId: String? = null
    private var params: String? = null

    /** true when the loaded page came from browseSongs (its continuation pages through moreSongs) */
    private var songPage = false

    private var loadingMore = false

    /** The args the current page belongs to; null until load() is called. */
    @Volatile
    private var loadedKey: String? = null

    /**
     * Loads one shelf's page. The coordinator patches the ported screen to
     * call this with the route args.
     */
    fun load(artistId: String?, browseId: String?, params: String?) {
        val effectiveParams = params?.takeIf { it != "null" }
        val key = "$artistId|$browseId|${effectiveParams.orEmpty()}"
        if (key == loadedKey) return
        loadedKey = key
        loadedArtistId = artistId
        this.browseId = browseId
        this.params = effectiveParams
        title.value = ""
        itemsPage.value = null
        loadFailed.value = false
        loadingMore = false
        viewModelScope.launch(Dispatchers.IO) { fetch() }
    }

    fun retry() {
        loadFailed.value = false
        loadedKey = null
        load(loadedArtistId, browseId, params)
    }

    private suspend fun fetch() {
        val id = browseId
        if (id.isNullOrBlank()) {
            loadFailed.value = true
            return
        }
        val hideExplicit = context.dataStore.get(HideExplicitKey, false)
        val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, false)
        // Song shelves page through browseSongs; a card grid answers it
        // with no songs, which is what sends the fetch down the grid path.
        val songResult = YtMusicRepository.browseSongs(id).getOrNull()
        if (songResult != null && songResult.songs.isNotEmpty()) {
            if (id != browseId) return
            songPage = true
            title.value = songResult.header?.title.orEmpty()
            itemsPage.value = ItemsPage(
                items = songResult.songs.map { it.toSongItem() }
                    .distinctBy { it.id }
                    .filterExplicit(hideExplicit)
                    .filterVideoSongs(hideVideoSongs),
                continuation = songResult.continuation,
            )
            return
        }
        runCatching { Innertube.browse(id, params) }
            .onSuccess { response ->
                if (id != browseId) return@onSuccess
                songPage = false
                title.value = InnertubeParser.parseBrowseHeader(response)?.title.orEmpty()
                val cards = InnertubeParser.parseLibraryItemPage(response)
                itemsPage.value = ItemsPage(
                    items = cards.items.mapNotNull { it.toYTItem() }
                        .distinctBy { it.id }
                        .filterExplicit(hideExplicit)
                        .filterVideoSongs(hideVideoSongs),
                    continuation = cards.continuation,
                )
            }
            .onFailure {
                Timber.w(it, "ArtistItems: browse failed for $id")
                loadFailed.value = true
            }
    }

    fun loadMore() {
        // the list asks again on every frame the last row is in view; one page at a time
        if (loadingMore) return
        val oldItemsPage = itemsPage.value ?: return
        val continuation = oldItemsPage.continuation ?: return
        loadingMore = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val hideExplicit = context.dataStore.get(HideExplicitKey, false)
                val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, false)
                if (songPage) {
                    YtMusicRepository.moreSongs(continuation)
                        .onSuccess { page ->
                            itemsPage.update {
                                ItemsPage(
                                    items = (oldItemsPage.items + page.songs.map { song -> song.toSongItem() })
                                        .distinctBy { it.id }
                                        .filterExplicit(hideExplicit)
                                        .filterVideoSongs(hideVideoSongs),
                                    continuation = page.continuation,
                                )
                            }
                        }
                        .onFailure { Timber.w(it, "ArtistItems: moreSongs failed") }
                } else {
                    runCatching { Innertube.browseContinuation(continuation) }
                        .onSuccess { response ->
                            val cards = InnertubeParser.parseLibraryItemPage(response)
                            itemsPage.update {
                                ItemsPage(
                                    items = (oldItemsPage.items + cards.items.mapNotNull { item -> item.toYTItem() })
                                        .distinctBy { it.id }
                                        .filterExplicit(hideExplicit)
                                        .filterVideoSongs(hideVideoSongs),
                                    continuation = cards.continuation,
                                )
                            }
                        }
                        .onFailure { Timber.w(it, "ArtistItems: continuation failed") }
                }
            } finally {
                loadingMore = false
            }
        }
    }
}
