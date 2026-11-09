package com.music.bitchord.karaoke.yt

import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.model.SearchFilter
import com.music.bitchord.data.model.SearchResult
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.sources.TrackMatcher
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.Locale
import kotlin.math.abs

/**
 * Karaoke YT (dev3): the third karaoke mode. Instead of cutting the vocal
 * out of the mix (Instan) or separating stems on-device (AI), this mode
 * finds the song's karaoke twin on YouTube — the instrumental / minus-one
 * upload that already has no vocal — and plays that video's audio under
 * the original song's metadata, so the title, artwork and lyrics on
 * screen stay the original's.
 *
 * This object owns the three things the mode needs outside the player:
 *
 *  - [search]: the candidate hunt and its scoring. Pure apart from the
 *    network: given the original [Song] it returns the ranked candidates.
 *  - [state]: what PlaybackService last decided for the song on air —
 *    searching, playing the karaoke twin, staying on the original, or
 *    nothing found. The karaoke block in settings renders exactly this.
 *  - the listener's corrections: [useOriginal] / [useKaraoke] /
 *    [choose] / [clearPairings]. Each bumps [revision], which
 *    PlaybackService collects and re-applies — the same write-to-settings,
 *    service-reacts shape the rest of karaoke uses.
 *
 * The remembered pairing itself (song id → karaoke video id) lives in
 * [AppSettings] beside the other karaoke settings; see
 * [AppSettings.karaokeYtPairing].
 */
object KaraokeYt {

    enum class Status { SEARCHING, KARAOKE, ORIGINAL, NOT_FOUND }

    /** One search result, scored against the original song. */
    data class Candidate(
        val videoId: String,
        val title: String,
        val artist: String,
        val durationSec: Int?,
        /** |candidate − original| in seconds; null when either side is unknown. */
        val durationDiffSec: Int?,
        /** The upload calls itself karaoke / instrumental / minus one / no vocal. */
        val hasKaraokeKeyword: Boolean,
        /** Fraction of the original title's tokens found on the candidate, 0f..1f. */
        val titleOverlap: Float,
        val score: Int,
    ) {
        /**
         * Good enough to swap in without asking: it calls itself a karaoke
         * version, its title is recognisably the same song, and — when the
         * original's runtime is known — its runtime agrees within the
         * widened window (karaoke uploads pad intros/outros, so this is
         * looser than a catalogue match would be).
         */
        val autoPickable: Boolean
            get() = hasKaraokeKeyword && titleOverlap >= 0.5f &&
                (durationDiffSec == null || durationDiffSec <= 20)
    }

    /** What the karaoke block shows for the song currently on air. */
    data class State(
        val songId: String,
        val status: Status,
        /** Title of the karaoke video being played, when [status] is KARAOKE. */
        val karaokeTitle: String? = null,
        val candidates: List<Candidate> = emptyList(),
    )

    val state = MutableStateFlow<State?>(null)

    /** Bumped by every listener decision below; PlaybackService re-applies on each bump. */
    val revision = MutableStateFlow(0)

    /** "Cari lagi" requests from the UI, by song id — a fresh search even after a miss. */
    val searchRequests = MutableSharedFlow<String>(extraBufferCapacity = 8)

    /** Songs the listener told to stay on the original take for now. Session-only: a
     *  deliberate "not this one" is not a fact worth persisting like a pairing is. */
    private val originalOverride = HashSet<String>()

    @Synchronized
    fun useOriginal(songId: String) {
        originalOverride += songId
        revision.value++
    }

    @Synchronized
    fun useKaraoke(songId: String) {
        originalOverride -= songId
        revision.value++
    }

    @Synchronized
    fun isOriginalOverride(songId: String): Boolean = songId in originalOverride

    /** A hand-picked candidate becomes the remembered pairing and plays. */
    fun choose(songId: String, candidate: Candidate) {
        AppSettings.setKaraokeYtPairing(songId, candidate.videoId, candidate.title)
        useKaraoke(songId)
    }

    fun clearPairings() {
        AppSettings.clearKaraokeYtPairings()
        revision.value++
    }

    fun requestSearch(songId: String) {
        searchRequests.tryEmit(songId)
    }

    fun publish(value: State?) {
        state.value = value
    }

    // ── Search & scoring ─────────────────────────────────────────────────

    private val KARAOKE_MARKERS = listOf(
        "karaoke", "instrumental", "minus one", "minusone",
        "no vocal", "novocal", "off vocal", "backing track", "tanpa vokal",
    )

    /** Upload shapes that are almost never the studio karaoke take. */
    private val PENALTY_MARKERS = listOf(
        "live", "remix", "sped up", "slowed", "nightcore", "reaction",
    )

    /** Words that say how a row is packaged, not what song it is. */
    private val TOKEN_NOISE = setOf(
        "karaoke", "instrumental", "version", "official", "video",
        "audio", "music", "lyric", "lyrics", "hd", "hq",
    )

    /**
     * The candidate hunt: the karaoke query first, then instrumental, then
     * minus one, de-duplicated by video id and ranked by [score]. Stops
     * early when the karaoke query already produced a swappable answer —
     * the other two phrasings are for songs the first one misses.
     */
    /**
     * [fallbackDurationSec] is the original's runtime as the player
     * itself reports it, for the many queued rows whose [Song] carries
     * no duration text — without it those songs would be scored with no
     * duration window at all, and a same-titled instrumental of any
     * length could swap itself in.
     */
    suspend fun search(song: Song, fallbackDurationSec: Int? = null): List<Candidate> {
        val queries = listOf(
            "${song.title} ${song.artist} karaoke",
            "${song.title} ${song.artist} instrumental",
            "${song.title} ${song.artist} minus one",
        )
        val found = LinkedHashMap<String, Song>()
        for ((index, query) in queries.withIndex()) {
            val rows = YtMusicRepository.search(query, SearchFilter.SONGS)
                .getOrDefault(emptyList())
                .filterIsInstance<SearchResult.Track>()
                .map { it.song }
            for (row in rows) {
                if (row.videoId != song.videoId) found.putIfAbsent(row.videoId, row)
            }
            if (index == 0 && found.values.any { score(song, it, fallbackDurationSec).autoPickable }) break
        }
        return found.values
            .map { score(song, it, fallbackDurationSec) }
            .sortedByDescending { it.score }
            .take(MAX_CANDIDATES)
    }

    private fun score(original: Song, candidate: Song, fallbackDurationSec: Int?): Candidate {
        val titleLower = candidate.title.lowercase(Locale.ROOT)
        val hasKeyword = KARAOKE_MARKERS.any { it in titleLower }
        val origSec = TrackMatcher.secondsOf(original.durationText) ?: fallbackDurationSec
        val candSec = TrackMatcher.secondsOf(candidate.durationText)
        val diff = if (origSec != null && candSec != null) abs(candSec - origSec) else null

        var points = 0
        if (hasKeyword) points += 40

        // How much of the original title survives on the candidate (its
        // title plus its byline — karaoke uploads often file the song
        // under the channel's name instead).
        val origTokens = tokens(original.title) - TOKEN_NOISE
        val candTokens = tokens(candidate.title) + tokens(candidate.artist)
        val titleOverlap = if (origTokens.isEmpty()) 0f
            else origTokens.count { it in candTokens }.toFloat() / origTokens.size
        points += (30 * titleOverlap).toInt()

        // The original artist named anywhere on the candidate.
        val artistTokens = tokens(original.artist)
        if (artistTokens.isNotEmpty()) {
            val hit = artistTokens.count { it in candTokens }
            points += (15 * hit) / artistTokens.size
        }

        // Runtime agreement: a karaoke take is the same recording minus
        // the vocal, so its length should nearly match. ≤8s is the
        // confident window; the score keeps credit out to 20s because
        // karaoke uploads pad the ends, and turns negative past it.
        points += when {
            diff == null -> 0
            diff <= 3 -> 30
            diff <= 8 -> 22
            diff <= 15 -> 12
            diff <= 20 -> 5
            else -> -25
        }
        if (PENALTY_MARKERS.any { it in titleLower }) points -= 15

        return Candidate(
            videoId = candidate.videoId,
            title = candidate.title,
            artist = candidate.artist,
            durationSec = candSec,
            durationDiffSec = diff,
            hasKaraokeKeyword = hasKeyword,
            titleOverlap = titleOverlap,
            score = points,
        )
    }

    private fun tokens(text: String): Set<String> =
        text.lowercase(Locale.ROOT)
            .split(Regex("[^a-z0-9]+"))
            .filter { it.length > 1 }
            .toSet()

    private const val MAX_CANDIDATES = 8
}
