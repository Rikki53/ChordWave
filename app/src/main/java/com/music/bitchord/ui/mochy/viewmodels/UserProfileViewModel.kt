// UserProfileViewModel.kt
//
// What the profile page shows: listening totals. Mochy reads these from its
// Room event table; ChordWave reads them from ListeningStats (the same
// buckets Replay uses), so no second history store is needed.
//
// Ported from Mochy Music (GPL-3.0): viewmodels/UserProfileViewModel.kt —
// adapted: recentSongs/playlists/likedCount/topArtists are NOT ported (they
// feed Mochy's morph UserProfilePage, which the simplified ChordWave profile
// does not have). Only the listen totals are kept.

package com.music.bitchord.ui.mochy.viewmodels

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.bitchord.data.stats.ListeningStats
import com.music.bitchord.data.stats.ReplayPeriod
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One "Total listens" card: how many different songs, and for how long, over [label]'s span. */
@Immutable
data class ListenTotal(val label: String, val songs: Int, val playTimeMs: Long) {
    companion object {
        /** Shown until the first count arrives, so the numbers roll up from zero instead of popping in. */
        val Placeholder = listOf(ListenTotal("ALL TIME", 0, 0L), ListenTotal("THIS MONTH", 0, 0L), ListenTotal("THIS WEEK", 0, 0L))
    }
}

@HiltViewModel
class UserProfileViewModel @Inject constructor() : ViewModel() {

    private val _listens = MutableStateFlow<List<ListenTotal>?>(null)
    val listens: StateFlow<List<ListenTotal>?> = _listens.asStateFlow()

    /** All-time listening time in ms, for the profile's listening-time card. */
    val totalListeningMs: Long get() = _listens.value?.firstOrNull()?.playTimeMs ?: 0L

    init {
        refresh()
    }

    /** Re-reads the totals from ListeningStats (the buckets only grow while music plays). */
    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            val allTime = ListeningStats.summary(ReplayPeriod.ALL_TIME)
            val month = ListeningStats.summary(ReplayPeriod.THIS_MONTH)
            val week = ListeningStats.summary(ReplayPeriod.THIS_WEEK)
            _listens.value = listOf(
                ListenTotal("ALL TIME", allTime.distinctSongs, allTime.totalMs),
                ListenTotal("THIS MONTH", month.distinctSongs, month.totalMs),
                ListenTotal("THIS WEEK", week.distinctSongs, week.totalMs),
            )
        }
    }
}
