// RadioViewModel — the Modern radio page's state, over the unchanged
// RadioBrowser engine (RadioBrowserApi + RadioFavorites). A fresh
// ViewModel: the Classic RadioViewModel lives inside classic
// radio/RadioScreen.kt and is never imported here — this one only shares
// the engine and the "radio_favorites" prefs file, so favourites stay in
// sync between Classic and Modern.
//
// The search field itself lives in the screen (a TextFieldValue, Mochy
// SearchField's shape); the ViewModel only owns the search results, so
// there is no second copy of the query to drift. Search is submit-only,
// like the Classic tab: one request per search, not one per keystroke.

package com.music.bitchord.ui.mochy.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.bitchord.radio.RadioBrowserApi
import com.music.bitchord.radio.RadioFavorites
import com.music.bitchord.radio.RadioStation
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

@HiltViewModel
class RadioViewModel @Inject constructor(
    @ApplicationContext context: Context,
) : ViewModel() {

    /** Favourite stations, shared with the Classic radio tab. */
    val favorites = RadioFavorites(context)

    private val _indonesia = MutableStateFlow<List<RadioStation>>(emptyList())
    val indonesia: StateFlow<List<RadioStation>> = _indonesia.asStateFlow()

    private val _top = MutableStateFlow<List<RadioStation>>(emptyList())
    val top: StateFlow<List<RadioStation>> = _top.asStateFlow()

    /** Null while browsing; a list (possibly empty) once a search has run. */
    private val _results = MutableStateFlow<List<RadioStation>?>(null)
    val results: StateFlow<List<RadioStation>?> = _results.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _failed = MutableStateFlow(false)
    val failed: StateFlow<Boolean> = _failed.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _loading.value = true
            _failed.value = false
            try {
                _indonesia.value = RadioBrowserApi.stationsForCountry("Indonesia")
                _top.value = RadioBrowserApi.topStations()
            } catch (t: Throwable) {
                Timber.w(t, "RadioViewModel: station lists failed to load")
                _failed.value = true
            } finally {
                _loading.value = false
            }
        }
    }

    fun submitSearch(term: String) {
        val query = term.trim()
        if (query.isEmpty()) return
        viewModelScope.launch {
            _loading.value = true
            try {
                _results.value = RadioBrowserApi.search(query)
            } catch (t: Throwable) {
                Timber.w(t, "RadioViewModel: search failed for \"$query\"")
                _results.value = emptyList()
            } finally {
                _loading.value = false
            }
        }
    }

    /** Back to browsing: the search results go away. */
    fun clearSearch() {
        _results.value = null
    }
}
