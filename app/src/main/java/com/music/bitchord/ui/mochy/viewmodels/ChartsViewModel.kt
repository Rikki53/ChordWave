// Ported from Mochy Music (GPL-3.0): viewmodels/ChartsViewModel.kt
// The charts page for the Modern Explore screen, re-implemented over
// ChordWave sources. CW has no charts endpoint (HomeViewModel's allTimeHits
// stays empty for the same reason), so the page is browsed through
// YtMusicRepository.moodGenreShelves("FEmusic_charts", params) — CW's only
// public browse call that takes params, and the one Mochy's getChartsPage
// passes ("ggMGCgQIgAQ%3D") — with the shelves turned into chart sections
// by Mappers4.toChartSections. Mochy's shape is kept: chartsPage /
// isLoading / error as StateFlows, loadCharts() fetching once.
//
// Adaptations vs Mochy:
// - loadMore() is NOT ported: CW's browse calls return shelves with no
//   continuation token, so there is nothing to follow. The screen does not
//   read it (B4), and continuation is always null on the built page.
// - loadCharts() guards double-load: a second call while one is running is
//   dropped (Mochy had no guard; the screen only calls it when the page is
//   null, but a recomposition could still double-fire it).
// - On failure the previous page is kept (Mochy set the error string and
//   left the page untouched) and the failure is logged through Timber.
// - ChartType.NEW_RELEASES is never assigned: Mochy gave it to grid
//   sections, and CW's parseHome only reads carousel + plain shelves (see
//   NEEDS.md).
// - Refresh (feedback 24 Nov 2026, item 9): the Explore tab is disposed
//   on switch, so the screen calls loadCharts() on every revisit — but
//   a reload only fires when the page is missing or older than
//   STALE_AFTER_MS. A reload bypasses the repository's
//   moodGenreShelfCache (forceRefresh), which never expires on its own
//   and would otherwise serve the first fetch forever.

package com.music.bitchord.ui.mochy.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.ui.mochy.models.innertube.ChartsPage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

/** Mochy's getChartsPage params (YouTube.kt:951): the charts tab selector. */
private const val CHARTS_PARAMS = "ggMGCgQIgAQ%3D"

/** A charts page older than this is refetched on revisit (15 minutes). */
private const val STALE_AFTER_MS = 15 * 60 * 1000L

@HiltViewModel
class ChartsViewModel @Inject constructor() : ViewModel() {
    private val _chartsPage = MutableStateFlow<ChartsPage?>(null)
    val chartsPage = _chartsPage.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    /** Wall time of the last successful load; 0 until the first one lands. */
    private var lastLoadedAt = 0L

    /**
     * Loads the charts page when it is missing or stale; a fresh page is
     * left alone, so a quick tab hop costs no network. [forceRefresh]
     * (the screen's retry button) reloads regardless.
     */
    fun loadCharts(forceRefresh: Boolean = false) {
        // Checked and set synchronously on the calling thread, so two calls
        // in the same frame cannot both slip through (Mochy set the flag
        // inside the launch, after the check had already passed).
        if (_isLoading.value) return
        if (!forceRefresh && _chartsPage.value != null && !isStale()) return
        _isLoading.value = true
        _error.value = null
        // A reload must bypass the repository cache (it never expires);
        // the first load reads through it like any other caller.
        val bypassCache = forceRefresh || _chartsPage.value != null
        viewModelScope.launch(Dispatchers.IO) {
            try {
                YtMusicRepository.moodGenreShelves("FEmusic_charts", CHARTS_PARAMS, forceRefresh = bypassCache)
                    .onSuccess { shelves ->
                        _chartsPage.value = ChartsPage(
                            sections = shelves.toChartSections(),
                            continuation = null,
                        )
                        lastLoadedAt = System.currentTimeMillis()
                    }
                    .onFailure { e ->
                        _error.value = "Failed to load charts: ${e.message}"
                        Timber.w(e, "Charts: loadCharts failed, keeping previous page")
                    }
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun isStale(): Boolean =
        System.currentTimeMillis() - lastLoadedAt > STALE_AFTER_MS
}
