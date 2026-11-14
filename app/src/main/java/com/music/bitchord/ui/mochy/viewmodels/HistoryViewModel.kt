// Ported from Mochy Music (GPL-3.0): viewmodels/HistoryViewModel.kt,
// re-implemented over ChordWave sources. Mochy groups its Room play events
// by day; CW has no local play-event store, so the events come from
// ListeningStats' day buckets — one event per track, timestamped at its
// last play (Mochy deduplicates by song id within a group anyway, so one
// event per track is the same list). The grouping rule (Today / Yesterday /
// This Week / Last Week / month-start Other) and the sort order are
// Mochy's verbatim.
//
// Adaptations vs Mochy:
// - No saved-state handle and no init load: the host calls load() (the
//   route has no args). A load() while one is already running is a no-op;
//   later loads refresh, because history grows.
// - fetchRemoteHistory() is the remote fallback, not a second source:
//   YouTube's history feed carries no timestamps, so its tracks can only
//   join the page when local stats have nothing at all (a fresh install
//   on a signed-in account), dated at fetch time. When local events
//   exist, the remote fetch changes nothing.
// - historySource / historyPage are NOT ported: the screen does not read
//   them (the history-source toggle they feed is not ported either).
// - The HideVideoSongs filter reads the pref but cannot filter anything
//   yet: the stats buckets do not record whether a track is a video, so
//   every stats-sourced song reads as audio.
// (phase2-detail NEEDS.md §(a) C2.)
package com.music.bitchord.ui.mochy.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.stats.ListeningStats
import com.music.bitchord.data.stats.StoredBucket
import com.music.bitchord.data.stats.TrackEntry
import com.music.bitchord.ui.mochy.constants.HideVideoSongsKey
import com.music.bitchord.ui.mochy.models.Event
import com.music.bitchord.ui.mochy.models.Song
import com.music.bitchord.ui.mochy.models.toMochySong
import com.music.bitchord.ui.mochy.utils.PreferencesCache
import com.music.bitchord.ui.mochy.utils.dataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

/** One play event with the song it played (Mochy's EventWithSong). */
data class EventWithSong(val event: Event, val song: Song)

sealed class DateAgo {
    data object Today : DateAgo()

    data object Yesterday : DateAgo()

    data object ThisWeek : DateAgo()

    data object LastWeek : DateAgo()

    class Other(
        val date: LocalDate,
    ) : DateAgo() {
        override fun equals(other: Any?): Boolean {
            if (other is Other) return date == other.date
            return super.equals(other)
        }

        override fun hashCode(): Int = date.hashCode()
    }
}

@HiltViewModel
class HistoryViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val _events = MutableStateFlow<Map<DateAgo, List<EventWithSong>>>(emptyMap())
    val events: StateFlow<Map<DateAgo, List<EventWithSong>>> = _events

    private var loadJob: Job? = null

    /** Builds the grouped history from local listening stats. Safe to call again to refresh. */
    fun load() {
        if (loadJob?.isActive == true) return
        loadJob = viewModelScope.launch(Dispatchers.IO) { refreshLocal() }
    }

    /**
     * The account's YouTube history, as a fallback for a fresh install
     * with no local stats. Mochy parity: the screen does not call it (the
     * history-source toggle is not ported). No-op when local events exist
     * or the account has no history to fetch.
     */
    fun fetchRemoteHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            val remote = YtMusicRepository.history()
                .onFailure { Timber.w(it, "History: remote history failed") }
                .getOrNull().orEmpty()
            if (remote.isEmpty() || _events.value.isNotEmpty()) return@launch
            // the feed carries no timestamps: the tracks land in Today, dated now
            val now = LocalDateTime.now()
            val items = remote.mapIndexed { index, song ->
                EventWithSong(
                    Event(id = index.toLong(), songId = song.videoId, timestamp = now, playTime = 0L),
                    song.toMochySong(),
                )
            }
            _events.value = group(items)
        }
    }

    private suspend fun refreshLocal() {
        val hideVideoSongs = PreferencesCache.current(context.dataStore)[HideVideoSongsKey] ?: false
        val buckets: List<StoredBucket> = ListeningStats.exportAll()
        // same merge HomeViewModel does: one entry per track across months
        val merged = LinkedHashMap<String, TrackEntry>()
        buckets.forEach { bucket ->
            bucket.tracks.forEach { entry ->
                val existing = merged[entry.id]
                if (existing == null) {
                    merged[entry.id] = entry.copy(days = entry.days.toMutableMap())
                } else {
                    existing.absorb(entry)
                }
            }
        }
        val items = merged.values
            .filter { it.last > 0 }
            .sortedByDescending { it.last }
            .mapIndexed { index, track ->
                val timestamp = Instant.ofEpochMilli(track.last)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDateTime()
                EventWithSong(
                    Event(id = index.toLong(), songId = track.id, timestamp = timestamp, playTime = track.ms),
                    track.toCwSong().toMochySong(),
                )
            }
            .filter { !hideVideoSongs || !it.song.song.isVideo }
        _events.value = group(items)
    }

    /** Mochy's grouping, verbatim: day buckets, then month-start Others, oldest last. */
    private fun group(items: List<EventWithSong>): Map<DateAgo, List<EventWithSong>> {
        val today = LocalDate.now()
        val thisMonday = today.with(DayOfWeek.MONDAY)
        val lastMonday = thisMonday.minusDays(7)
        return items
            .groupBy {
                val date = it.event.timestamp.toLocalDate()
                val daysAgo = ChronoUnit.DAYS.between(date, today).toInt()
                when {
                    daysAgo == 0 -> DateAgo.Today
                    daysAgo == 1 -> DateAgo.Yesterday
                    date >= thisMonday -> DateAgo.ThisWeek
                    date >= lastMonday -> DateAgo.LastWeek
                    else -> DateAgo.Other(date.withDayOfMonth(1))
                }
            }.toSortedMap(
                compareBy { dateAgo ->
                    when (dateAgo) {
                        DateAgo.Today -> 0L
                        DateAgo.Yesterday -> 1L
                        DateAgo.ThisWeek -> 2L
                        DateAgo.LastWeek -> 3L
                        is DateAgo.Other -> ChronoUnit.DAYS.between(dateAgo.date, today)
                    }
                },
            ).mapValues { entry ->
                entry.value.distinctBy { it.song.id }
            }
    }
}
