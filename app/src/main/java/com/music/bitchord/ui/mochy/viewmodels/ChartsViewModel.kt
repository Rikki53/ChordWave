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

@HiltViewModel
class ChartsViewModel @Inject constructor() : ViewModel() {
    private val _chartsPage = MutableStateFlow<ChartsPage?>(null)
    val chartsPage = _chartsPage.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    fun loadCharts() {
        // Checked and set synchronously on the calling thread, so two calls
        // in the same frame cannot both slip through (Mochy set the flag
        // inside the launch, after the check had already passed).
        if (_isLoading.value) return
        _isLoading.value = true
        _error.value = null
        viewModelScope.launch(Dispatchers.IO) {
            try {
                YtMusicRepository.moodGenreShelves("FEmusic_charts", CHARTS_PARAMS)
                    .onSuccess { shelves ->
                        _chartsPage.value = ChartsPage(
                            sections = shelves.toChartSections(),
                            continuation = null,
                        )
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
}
