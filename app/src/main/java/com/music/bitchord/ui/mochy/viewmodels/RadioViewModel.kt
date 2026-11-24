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
//
// Sections are fetched in parallel (one request each, five in total) and
// each tolerates its own failure: a dead genre endpoint leaves the other
// sections standing. A refresh is only a failure when both main lists
// (Indonesia + Top) come back empty. refreshIfStale() keeps the page
// fresh when the user comes back to the tab without hammering the
// community-run API: at most one refresh per 10 minutes.
//
// "Di Sekitar Kamu" is computed client-side from the Indonesia list —
// no extra request. Stations with Radio Browser coordinates inside
// NEARBY_RADIUS_KM of the device are sorted nearest-first (haversine);
// the screen supplies the location only after the user grants coarse
// location permission, and the section simply stays empty without it.

package com.music.bitchord.ui.mochy.viewmodels

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.bitchord.radio.RadioBrowserApi
import com.music.bitchord.radio.RadioFavorites
import com.music.bitchord.radio.RadioStation
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** A station near the device, with its distance in kilometres. */
data class NearbyStation(
    val station: RadioStation,
    val distanceKm: Double,
)

@HiltViewModel
class RadioViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : ViewModel() {

    /** Favourite stations, shared with the Classic radio tab. */
    val favorites = RadioFavorites(context)

    private val _indonesia = MutableStateFlow<List<RadioStation>>(emptyList())
    val indonesia: StateFlow<List<RadioStation>> = _indonesia.asStateFlow()

    private val _top = MutableStateFlow<List<RadioStation>>(emptyList())
    val top: StateFlow<List<RadioStation>> = _top.asStateFlow()

    /** Most-clicked worldwide; a different ranking from [top]. */
    private val _topClicked = MutableStateFlow<List<RadioStation>>(emptyList())
    val topClicked: StateFlow<List<RadioStation>> = _topClicked.asStateFlow()

    private val _pop = MutableStateFlow<List<RadioStation>>(emptyList())
    val pop: StateFlow<List<RadioStation>> = _pop.asStateFlow()

    private val _dangdut = MutableStateFlow<List<RadioStation>>(emptyList())
    val dangdut: StateFlow<List<RadioStation>> = _dangdut.asStateFlow()

    /** Nearest stations first; empty when there is no location, no
     * coordinates on the stations, or nothing inside the radius. */
    private val _nearby = MutableStateFlow<List<NearbyStation>>(emptyList())
    val nearby: StateFlow<List<NearbyStation>> = _nearby.asStateFlow()

    /** Null while browsing; a list (possibly empty) once a search has run. */
    private val _results = MutableStateFlow<List<RadioStation>?>(null)
    val results: StateFlow<List<RadioStation>?> = _results.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _failed = MutableStateFlow(false)
    val failed: StateFlow<Boolean> = _failed.asStateFlow()

    /** Last location the device reported, so a list refresh can recompute
     * the nearby section without asking for the location again. */
    private var lastLocation: Pair<Double, Double>? = null

    /** Wall time of the last refresh that filled at least one main list. */
    private var lastRefreshMs = 0L

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _loading.value = true
            _failed.value = false
            coroutineScope {
                val indonesiaJob = async {
                    runCatching { RadioBrowserApi.stationsForCountry("Indonesia", INDONESIA_LIMIT) }
                }
                val topJob = async {
                    runCatching { RadioBrowserApi.topStations(TOP_LIMIT) }
                }
                val clickedJob = async {
                    runCatching { RadioBrowserApi.topClickedStations(TOP_LIMIT) }
                }
                val popJob = async {
                    runCatching { RadioBrowserApi.stationsForTag("pop", TAG_LIMIT) }
                }
                val dangdutJob = async {
                    runCatching { RadioBrowserApi.stationsForTag("dangdut", TAG_LIMIT) }
                }
                indonesiaJob.await().onSuccess { _indonesia.value = it }
                    .onFailure { Timber.w(it, "RadioViewModel: Indonesia stations failed") }
                topJob.await().onSuccess { _top.value = it }
                    .onFailure { Timber.w(it, "RadioViewModel: top stations failed") }
                clickedJob.await().onSuccess { _topClicked.value = it }
                    .onFailure { Timber.w(it, "RadioViewModel: top-clicked stations failed") }
                popJob.await().onSuccess { _pop.value = it }
                    .onFailure { Timber.w(it, "RadioViewModel: pop stations failed") }
                dangdutJob.await().onSuccess { _dangdut.value = it }
                    .onFailure { Timber.w(it, "RadioViewModel: dangdut stations failed") }
            }
            if (_indonesia.value.isNotEmpty() || _top.value.isNotEmpty()) {
                lastRefreshMs = System.currentTimeMillis()
                recomputeNearby()
            } else {
                _failed.value = true
            }
            _loading.value = false
        }
    }

    /** Refresh on return to the tab, at most once per [STALE_AFTER_MS]. */
    fun refreshIfStale() {
        if (System.currentTimeMillis() - lastRefreshMs < STALE_AFTER_MS) return
        refresh()
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

    /**
     * Reads the device's last known location (coarse is enough — a city
     * fix still ranks stations) and recomputes the nearby section. A
     * no-op when location permission is missing or no fix exists yet;
     * the screen only calls this after the user grants permission.
     */
    fun refreshNearbyFromDevice() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return
        val fix = try {
            manager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                ?: manager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        } catch (_: SecurityException) {
            // Permission revoked between the check and the read.
            null
        } ?: return
        lastLocation = fix.latitude to fix.longitude
        recomputeNearby()
    }

    private fun recomputeNearby() {
        val (lat, lon) = lastLocation ?: run {
            _nearby.value = emptyList()
            return
        }
        _nearby.value = _indonesia.value.mapNotNull { station ->
            val stationLat = station.geoLat ?: return@mapNotNull null
            val stationLon = station.geoLong ?: return@mapNotNull null
            val distance = haversineKm(lat, lon, stationLat, stationLon)
            if (distance <= NEARBY_RADIUS_KM) NearbyStation(station, distance) else null
        }.sortedBy { it.distanceKm }.take(NEARBY_LIMIT)
    }

    private companion object {
        /** Indonesia is also the nearby section's source, so it gets the
         * biggest list: more stations means more with coordinates. */
        const val INDONESIA_LIMIT = 100
        const val TOP_LIMIT = 40
        const val TAG_LIMIT = 20
        const val NEARBY_LIMIT = 15
        const val NEARBY_RADIUS_KM = 150.0
        const val STALE_AFTER_MS = 10 * 60 * 1000L
    }
}

/** Great-circle distance in kilometres. */
private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val earthRadiusKm = 6371.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2).pow(2) +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
    return 2 * earthRadiusKm * atan2(sqrt(a), sqrt(1 - a))
}
