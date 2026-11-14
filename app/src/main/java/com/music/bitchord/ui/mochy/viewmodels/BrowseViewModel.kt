// Ported from Mochy Music (GPL-3.0): viewmodels/BrowseViewModel.kt,
// re-implemented over ChordWave sources. Mochy browses once and flattens
// the page's sections; CW's repo has no browse-with-title call (its
// homeSupplement() returns shelves only), so this VM browses through
// Innertube directly and parses the response with the repo's own parsers:
// parseBrowseHeader for the title, parseHome for shelf pages, and
// parseLibraryItemPage as the fallback for grid pages (the "More" pages
// parseHome cannot read — it only knows carousel/shelf renderers).
// No filters: Mochy's BrowseViewModel applies none either.
//
// Adaptations vs Mochy:
// - No saved-state handle: the host calls load(browseId) with the route
//   arg (coordinator decision — the route is available from
//   navigator.currentDetail). A repeated load() with the same id is a
//   no-op; a new id resets title/items first, so a reused VM never shows
//   the previous page while loading.
// (phase2-detail NEEDS.md §(a) C3.)
package com.music.bitchord.ui.mochy.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.bitchord.data.innertube.Innertube
import com.music.bitchord.data.innertube.InnertubeParser
import com.music.bitchord.ui.mochy.models.innertube.YTItem
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

@HiltViewModel
class BrowseViewModel @Inject constructor() : ViewModel() {
    val items = MutableStateFlow<List<YTItem>?>(emptyList())
    val title = MutableStateFlow<String?>("")

    /** The args the current page belongs to; null until load() is called. */
    @Volatile
    private var loadedKey: String? = null

    /**
     * Browses one endpoint's items as a flat grid. The coordinator patches
     * the ported screen to call this with the route arg.
     */
    fun load(browseId: String) {
        if (browseId == loadedKey) return
        loadedKey = browseId
        items.value = emptyList()
        title.value = ""
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { Innertube.browse(browseId) }
                .onSuccess { response ->
                    if (browseId != loadedKey) return@onSuccess
                    title.value = InnertubeParser.parseBrowseHeader(response)?.title.orEmpty()
                    val fromShelves = InnertubeParser.parseHome(response)
                        .flatMap { shelf -> shelf.items.mapNotNull { it.toYTItem(shelf.layout) } }
                    val all = if (fromShelves.isNotEmpty()) {
                        fromShelves
                    } else {
                        // a grid page (mixed-for-you, a library feed): the
                        // shelves parser sees nothing, the item parser does
                        InnertubeParser.parseLibraryItemPage(response).items
                            .mapNotNull { it.toYTItem() }
                    }
                    items.value = all.distinctBy { it.id }
                }
                .onFailure { Timber.w(it, "Browse: failed for $browseId") }
        }
    }
}
