// Ported from Mochy Music (GPL-3.0): viewmodels/YouTubeBrowseViewModel.kt,
// re-implemented over ChordWave sources. Mochy browses once and applies
// the explicit / video-song / shorts filters; CW's closest repo call,
// moodGenreShelves(browseId, params), returns shelves with no page title,
// so this VM browses through Innertube directly (one request) and parses
// the response with the repo's own parsers — parseBrowseHeader for the
// title, parseHome for the shelves (Mappers4.toBrowseResult), with
// parseLibraryItemPage as the fallback for grid pages.
//
// Adaptations vs Mochy:
// - No saved-state handle: the host calls load(browseId, params) with the
//   route args (coordinator decision — the route is available from
//   navigator.currentDetail). A repeated load() with the same args is a
//   no-op; new args reset result first, so a reused VM never shows the
//   previous page while loading.
// - params "null" (the literal string) is treated as absent, the way
//   Mochy's GenreViewModel does.
// (phase2-detail NEEDS.md §(a) C3.)
package com.music.bitchord.ui.mochy.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.bitchord.data.innertube.Innertube
import com.music.bitchord.data.innertube.InnertubeParser
import com.music.bitchord.ui.mochy.constants.HideExplicitKey
import com.music.bitchord.ui.mochy.constants.HideVideoSongsKey
import com.music.bitchord.ui.mochy.constants.HideYoutubeShortsKey
import com.music.bitchord.ui.mochy.models.innertube.BrowseResult
import com.music.bitchord.ui.mochy.utils.dataStore
import com.music.bitchord.ui.mochy.utils.get
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

@HiltViewModel
class YouTubeBrowseViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : ViewModel() {
    val result = MutableStateFlow<BrowseResult?>(null)

    /** The args the current page belongs to; null until load() is called. */
    @Volatile
    private var loadedKey: String? = null

    /**
     * Browses one endpoint. The coordinator patches the ported screen to
     * call this with the route args.
     */
    fun load(browseId: String, params: String?) {
        val effectiveParams = params?.takeIf { it != "null" }
        val key = "$browseId:${effectiveParams.orEmpty()}"
        if (key == loadedKey) return
        loadedKey = key
        result.value = null
        viewModelScope.launch(Dispatchers.IO) {
            val hideExplicit = context.dataStore.get(HideExplicitKey, false)
            val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, false)
            val hideYoutubeShorts = context.dataStore.get(HideYoutubeShortsKey, true)
            runCatching { Innertube.browse(browseId, effectiveParams) }
                .onSuccess { response ->
                    if (key != loadedKey) return@onSuccess
                    val pageTitle = InnertubeParser.parseBrowseHeader(response)?.title
                    val shelves = InnertubeParser.parseHome(response)
                    val page = if (shelves.isNotEmpty()) {
                        shelves.toBrowseResult(pageTitle)
                    } else {
                        // a grid page: one section holding every card
                        val cards = InnertubeParser.parseLibraryItemPage(response).items
                            .mapNotNull { it.toYTItem() }
                            .distinctBy { it.id }
                        BrowseResult(
                            title = pageTitle,
                            items = if (cards.isEmpty()) emptyList()
                            else listOf(BrowseResult.Item(title = pageTitle, items = cards)),
                        )
                    }
                    result.value = page
                        .filterExplicit(hideExplicit)
                        .filterVideoSongs(hideVideoSongs)
                        .filterYoutubeShorts(hideYoutubeShorts)
                }
                .onFailure { Timber.w(it, "YouTubeBrowse: failed for $browseId") }
        }
    }
}
