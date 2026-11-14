// Ported from Mochy Music (GPL-3.0): viewmodels/TopPlaylistViewModel.kt
// Mochy's flow is kept: topSongs combines topPeriod with
// HideVideoSongsKey and queries the most-played songs for the period,
// capped at the route arg's size.
//
// Adaptations vs Mochy:
// - No saved-state handle: the route arg comes from load(top), which the
//   coordinator calls from the screen. `top` is therefore a `var ... by
//   mutableStateOf("")` set by load(), not Mochy's val. The VM queries
//   nothing before the first load() call (topSongs stays empty).
// - No MusicDatabase: most-played comes from ListeningStats.summary, the
//   same source the staged adapters use (RankedSong carries the whole CW
//   Song plus ms + plays; the ms lands on totalPlayTime).
// - MyTopFilter is a rolling window (past 24h / week / month / year);
//   CW's ReplayPeriod is calendar-aligned (this week Mon–today, this
//   month, this year). DAY has no ReplayPeriod at all, so it shares
//   THIS_WEEK — the closest window ChordWave can answer (NEEDS.md C5).
// - Mochy called top.toInt() and would crash on a bad arg; a bad arg here
//   reads as an empty list instead.

package com.music.bitchord.ui.mochy.viewmodels

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.bitchord.data.stats.ListeningStats
import com.music.bitchord.data.stats.ReplayPeriod
import com.music.bitchord.ui.mochy.constants.HideVideoSongsKey
import com.music.bitchord.ui.mochy.constants.MyTopFilter
import com.music.bitchord.ui.mochy.models.Song
import com.music.bitchord.ui.mochy.utils.dataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import timber.log.Timber

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TopPlaylistViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : ViewModel() {
    // B1 calls for a String val; a var by mutableStateOf reads as a String
    // and lets load() update it when the host hands over a new size.
    var top by mutableStateOf("")
        private set

    val topPeriod = MutableStateFlow(MyTopFilter.ALL_TIME)

    /** The size the current page belongs to; null until load() is called. */
    private val loadedTop = MutableStateFlow<String?>(null)

    val topSongs =
        combine(
            topPeriod,
            context.dataStore.data.map { it[HideVideoSongsKey] ?: false }.distinctUntilChanged(),
            loadedTop,
        ) { period, hideVideoSongs, top -> Triple(period, hideVideoSongs, top) }
            .flatMapLatest { (period, hideVideoSongs, top) ->
                val size = top?.toIntOrNull() ?: return@flatMapLatest flowOf(emptyList<Song>())
                flow {
                    val songs = runCatching { ListeningStats.summary(period.toReplayPeriod()) }
                        .onFailure { Timber.w(it, "TopPlaylist: stats unavailable") }
                        .getOrNull()?.songs.orEmpty()
                        .take(size)
                        .map { it.toTopMochySong() }
                    emit(if (hideVideoSongs) songs.filter { !it.song.isVideo } else songs)
                }
            }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    /**
     * (Re)initialises the VM for a route size ("10", "25", "50", "100").
     * The coordinator patches the ported screen to call this; the VM
     * queries nothing before the first call.
     */
    fun load(top: String) {
        val key = top.trim()
        if (key.isEmpty() || key == loadedTop.value) return
        loadedTop.value = key
        this.top = key
    }
}

/**
 * MyTopFilter is a rolling window (past 24h / week / month / year); CW's
 * ReplayPeriod is calendar-aligned (this week Mon–today, this month, this
 * year). DAY has no ReplayPeriod at all, so it shares THIS_WEEK — the
 * closest window ChordWave can answer. See NEEDS.md C5.
 */
private fun MyTopFilter.toReplayPeriod(): ReplayPeriod = when (this) {
    MyTopFilter.ALL_TIME -> ReplayPeriod.ALL_TIME
    MyTopFilter.DAY, MyTopFilter.WEEK -> ReplayPeriod.THIS_WEEK
    MyTopFilter.MONTH -> ReplayPeriod.THIS_MONTH
    MyTopFilter.YEAR -> ReplayPeriod.THIS_YEAR
}
