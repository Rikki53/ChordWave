// StatsSummary.kt
//
// Everything the Stats page shows, worked out from the same monthly buckets
// the Replay reads (see ListeningStats.stats). The shapes mirror Mochy's
// recap data (StatsRange / ListeningStats / RecapSong / RecapArtist) so the
// ported StatsScreen draws them unchanged; the one thing ChordWave's
// aggregate buckets cannot answer is a listening *session* (Mochy reads
// per-play events), so StatsHabits carries no session fields and the
// HabitsCard drops that tile instead of inventing a number.

package com.music.bitchord.data.stats

import androidx.compose.runtime.Immutable
import java.time.LocalDate
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale

@Immutable
data class StatsRange(val kind: Kind, val year: Int, val month: Int = 1) {
    enum class Kind { Month, Year }

    val start: LocalDate get() = if (kind == Kind.Month) LocalDate.of(year, month, 1) else LocalDate.of(year, 1, 1)
    val endExclusive: LocalDate get() = if (kind == Kind.Month) start.plusMonths(1) else start.plusYears(1)

    val monthName: String get() = Month.of(month).getDisplayName(TextStyle.FULL, Locale.getDefault())

    /** What the page calls the period: "October", or "2026" for a whole year. */
    val label: String get() = if (kind == Kind.Month) monthName else "$year"

    fun previous(): StatsRange =
        if (kind == Kind.Month) monthOf(start.minusMonths(1)) else StatsRange(Kind.Year, year - 1)

    /** Days in the month, or 12 for a year: how many buckets [StatsSummary.bucketMs] has. */
    val bucketCount: Int get() = if (kind == Kind.Month) start.lengthOfMonth() else 12

    companion object {
        fun monthOf(date: LocalDate) = StatsRange(Kind.Month, date.year, date.monthValue)
        fun yearOf(date: LocalDate) = StatsRange(Kind.Year, date.year)
    }
}

@Immutable
data class StatsSong(
    val id: String,
    val title: String,
    val artistName: String,
    val artistThumbnailUrl: String?,
    val thumbnailUrl: String?,
    val playTimeMs: Long,
    val plays: Int,
)

@Immutable
data class StatsArtist(
    val id: String,
    val name: String,
    val thumbnailUrl: String?,
    val playTimeMs: Long,
    val plays: Int,
    /** The song by this artist played the most in the period. */
    val topSong: StatsSong?,
)

// the one day a single song got played the most, if it's at least a few times
@Immutable
data class StatsRepeatDay(val song: StatsSong, val date: LocalDate, val plays: Int)

// when and how steadily the listening happened, for the habits card
@Immutable
data class StatsHabits(
    val activeDays: Int,
    val elapsedDays: Int,
    val longestStreak: Int,
    val biggestDay: LocalDate?,
    val biggestDayMs: Long,
)

@Immutable
class StatsSummary(
    val range: StatsRange,
    val totalMs: Long,
    val plays: Int,
    val songCount: Int,
    val artistCount: Int,
    val albumCount: Int,
    /** Most played first (by plays, then time), up to [MaxSongs]. */
    val topSongs: List<StatsSong>,
    /** Most listened first (by time), up to [MaxArtists]. */
    val topArtists: List<StatsArtist>,
    /** Listening time per weekday, Monday first. */
    val weekdayMs: List<Long>,
    /** Listening time per hour of the day, 0..23. */
    val hourMs: List<Long>,
    /** Listening time per day of the month, or per month of the year. */
    val bucketMs: List<Long>,
    val habits: StatsHabits? = null,
    // songs never played before this period, best first, with how many there were and how long they played
    val newSongs: List<StatsSong> = emptyList(),
    val newSongCount: Int = 0,
    val newMs: Long = 0L,
    val repeat: StatsRepeatDay? = null,
    // the period before this one, null when nothing was played then; partial means only up to the same day
    val previousTotalMs: Long? = null,
    val previousIsPartial: Boolean = false,
) {
    val isEmpty: Boolean get() = plays == 0

    companion object {
        const val MaxSongs = 20
        const val MaxArtists = 25
    }
}

@Immutable
data class GenreShare(val name: String, val fraction: Float, val playTimeMs: Long)

/** "12h 05m" style, the way the Stats page spells listening time. */
fun formatHoursMinutes(ms: Long): String {
    val minutes = (ms / 60_000L).coerceAtLeast(0L)
    return "${minutes / 60}h ${(minutes % 60).toString().padStart(2, '0')}m"
}

/** Hours with one decimal ("3.4"), for the Stats rows. */
fun formatHoursDecimal(ms: Long): String {
    val tenths = (ms / 360_000L).coerceAtLeast(0L)
    return "${tenths / 10}.${tenths % 10}"
}
