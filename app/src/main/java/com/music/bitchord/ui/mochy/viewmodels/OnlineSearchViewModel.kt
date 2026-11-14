// Ported from Mochy Music (GPL-3.0): viewmodels/OnlineSearchViewModel.kt
// Online search results for the Modern Search tab, re-implemented over
// ChordWave sources: the summary view is one searchPage(query, ALL) grouped
// into sections (Mappers4.toSearchSummaryPage), filtered tabs are
// searchPage(query, filter) with the Mochy filter mapped to CW's own
// SearchFilter enum, and loadMore follows searchContinuation. Mochy's init
// logic is kept: collect filter; null -> one cached summary fetch; a filter
// -> one fetch per filter value cached in viewStateMap; all results
// .distinctBy { it.id } and passed through filterExplicit / filterVideoSongs
// / filterYoutubeShorts with the staged keys (defaults false / false / true).
//
// Adaptations vs Mochy:
// - No saved-state handle: the query comes from the route, so the host calls
//   load(query) (coordinator decision). `query` is therefore a
//   `var ... by mutableStateOf("")` set by load(), not Mochy's val — the
//   screen only ever reads it as a String. load() resets filter /
//   summaryPage / viewStateMap for a new query; re-calling it with the same
//   query just fills whatever fetch is still missing (e.g. after a failed
//   first fetch), instead of starting over.
// - Fetches check the query is still current before writing, so a slow
//   response for an abandoned query cannot overwrite the new page.
// - addHistory is a REQUIRED ADDITION (not in Mochy's VM): the ported
//   OnlineSearchResult never touches the store itself. Same shape as
//   OnlineSearchSuggestionViewModel.addHistory (query terms filed as
//   SearchHistoryEntity rows with id "q:<term>").
// - Failures log through Timber and keep the previous value; they never
//   crash and never clear a loaded page.

package com.music.bitchord.ui.mochy.viewmodels

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.model.EntityType
import com.music.bitchord.data.model.SearchHistoryEntity
import com.music.bitchord.data.model.SearchFilter as CwSearchFilter
import com.music.bitchord.data.settings.SearchHistory as CwSearchHistory
import com.music.bitchord.ui.mochy.constants.HideExplicitKey
import com.music.bitchord.ui.mochy.constants.HideVideoSongsKey
import com.music.bitchord.ui.mochy.constants.HideYoutubeShortsKey
import com.music.bitchord.ui.mochy.models.ItemsPage
import com.music.bitchord.ui.mochy.models.innertube.SearchFilter
import com.music.bitchord.ui.mochy.models.innertube.SearchSummaryPage
import com.music.bitchord.ui.mochy.models.innertube.filterExplicit
import com.music.bitchord.ui.mochy.models.innertube.filterVideoSongs
import com.music.bitchord.ui.mochy.models.innertube.filterYoutubeShorts
import com.music.bitchord.ui.mochy.utils.PreferencesCache
import com.music.bitchord.ui.mochy.utils.dataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

/** CW files a raw search term as a TRACK entity with this id prefix (see SearchHistoryEntity.toSong). Must match OnlineSearchSuggestionViewModel's. */
private const val QUERY_ID_PREFIX = "q:"

@HiltViewModel
class OnlineSearchViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : ViewModel() {
    // B1 calls for a String val; a var by mutableStateOf reads as a String
    // and lets load() update it when the host hands over a new query.
    var query by mutableStateOf("")
        private set

    val filter = MutableStateFlow<SearchFilter?>(null)
    var summaryPage by mutableStateOf<SearchSummaryPage?>(null)
    val viewStateMap: SnapshotStateMap<String, ItemsPage?> = mutableStateMapOf()

    // Read live on every fetch, like HomeViewModel's flags, so a Settings
    // toggle takes effect without an app restart.
    private val prefs get() = PreferencesCache.current(context.dataStore)
    private val hideExplicit get() = prefs[HideExplicitKey] ?: false
    private val hideVideoSongs get() = prefs[HideVideoSongsKey] ?: false
    private val hideYoutubeShorts get() = prefs[HideYoutubeShortsKey] ?: true

    /** The query the current page belongs to; null until load() is called. */
    @Volatile
    private var loadedQuery: String? = null

    init {
        viewModelScope.launch(Dispatchers.IO) {
            filter.collect { fetchCurrent(it) }
        }
    }

    /**
     * (Re)initialises the VM for a route query. The coordinator patches the
     * ported screen to call this; the VM fetches nothing before the first
     * call (the initial filter emission finds no query and returns).
     */
    fun load(query: String) {
        val text = query.trim()
        if (text.isEmpty()) return
        if (text == loadedQuery) {
            // Same query re-requested (e.g. the host re-composed after a
            // failed first fetch): fill whatever is still missing.
            viewModelScope.launch(Dispatchers.IO) { fetchCurrent(filter.value) }
            return
        }
        loadedQuery = text
        this.query = text
        summaryPage = null
        viewStateMap.clear()
        if (filter.value == null) {
            // Setting the filter to the value it already has emits nothing,
            // so the summary fetch is launched directly.
            viewModelScope.launch(Dispatchers.IO) { fetchSummary(text) }
        } else {
            // The collector picks this up and fetches the summary.
            filter.value = null
        }
    }

    /** Mochy's collect body: fetch whatever the current filter still lacks. */
    private suspend fun fetchCurrent(filter: SearchFilter?) {
        val currentQuery = loadedQuery ?: return
        if (filter == null) {
            if (summaryPage == null) fetchSummary(currentQuery)
        } else {
            if (viewStateMap[filter.value] == null) fetchFiltered(currentQuery, filter)
        }
    }

    private suspend fun fetchSummary(query: String) {
        YtMusicRepository.searchPage(query, CwSearchFilter.ALL)
            .onSuccess { page ->
                if (query != loadedQuery) return@onSuccess
                summaryPage = page.rows.toSearchSummaryPage()
                    .filterExplicit(hideExplicit)
                    .filterVideoSongs(hideVideoSongs)
                    .filterYoutubeShorts(hideYoutubeShorts)
                    .withoutRepeats()
            }
            .onFailure { Timber.w(it, "Search: summary failed for \"$query\", keeping previous page") }
    }

    private suspend fun fetchFiltered(query: String, filter: SearchFilter) {
        YtMusicRepository.searchPage(query, filter.toCwFilter())
            .onSuccess { page ->
                if (query != loadedQuery) return@onSuccess
                viewStateMap[filter.value] = ItemsPage(
                    page.rows.mapNotNull { it.toYTItem() }
                        .distinctBy { it.id }
                        .filterExplicit(hideExplicit)
                        .filterVideoSongs(hideVideoSongs)
                        .filterYoutubeShorts(hideYoutubeShorts),
                    page.continuation,
                )
            }
            .onFailure { Timber.w(it, "Search: filtered search failed for \"$query\" (${filter.value})") }
    }

    // youtube sometimes lists the same song twice in one section, and two rows with one key closed the app
    private fun SearchSummaryPage.withoutRepeats() =
        copy(summaries = summaries.map { section -> section.copy(items = section.items.distinctBy { it.id }) })

    fun loadMore() {
        // The filter is captured before launching: the tab the user is
        // leaving must not have its page appended to (Mochy re-read the
        // filter inside the launch).
        val filter = filter.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val query = loadedQuery ?: return@launch
            val viewState = viewStateMap[filter.value] ?: return@launch
            val continuation = viewState.continuation ?: return@launch
            val next = YtMusicRepository.searchContinuation(continuation, filter.toCwFilter())
                .onFailure { Timber.w(it, "Search: loadMore failed for \"$query\" (${filter.value})") }
                .getOrNull() ?: return@launch
            if (query != loadedQuery) return@launch
            val newItems = next.rows.mapNotNull { it.toYTItem() }
                .filterExplicit(hideExplicit)
                .filterVideoSongs(hideVideoSongs)
                .filterYoutubeShorts(hideYoutubeShorts)
            viewStateMap[filter.value] = ItemsPage(
                (viewState.items + newItems).distinctBy { it.id },
                next.continuation,
            )
        }
    }

    /** Records a committed query. The screen already guards PauseSearchHistoryKey. */
    fun addHistory(query: String) {
        val text = query.trim()
        if (text.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                CwSearchHistory.record(
                    SearchHistoryEntity(
                        id = "$QUERY_ID_PREFIX$text",
                        title = text,
                        subtitle = "",
                        artworkUrl = null,
                        entityType = EntityType.TRACK,
                    )
                )
            }.onFailure { Timber.w(it, "Search: addHistory failed") }
        }
    }
}
