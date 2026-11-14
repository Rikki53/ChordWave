// MusicImportService.kt — REPLACES the Phase 4C stub (same path, same
// public API, plus the progress flow and the re-sync/cancel entry points
// Mochy's service has). Ported from Mochy Music (GPL-3.0):
// importer/MusicImportService.kt, rebuilt on ChordWave engines.
//
// WHAT IS REAL: matching (exact video ids, else a YouTube Music search
// judged by CW's TrackMatcher — the same judgement SpotifyImporter uses),
// progress reporting, cancellation, and the YouTube mirror leg
// (createPlaylist + addToPlaylist + rate, all real CW APIs).
//
// WHAT IS BLOCKED (playlist NEEDS C2, open product decision): CW has NO
// local-playlist store for YouTube songs, so an import has nowhere to
// land on this phone. startImport therefore runs the match (+ the mirror
// the user asked for) and then reports the gap honestly through
// ImportProgress.error — Timber.w + a user-visible message, never a
// silent no-op and never a claim that anything was saved locally.
// When the store lands, only the landing step is missing: the matched
// video ids are already in hand. Import-finished notifications
// (Mochy's ImportNotices) are NOT ported — Phase 6.
//
// DEVIATION from Mochy: no re-sync queue. Mochy's startResync queues
// requests behind a running re-sync; with no import store nothing can
// produce a ResyncRequest yet, so a single-run startResync is enough
// until the real resync engine lands.
package com.music.bitchord.ui.mochy.importer

import android.content.Context
import com.music.bitchord.auth.SignedIn
import com.music.bitchord.data.LikeState
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.model.LikeStatus
import com.music.bitchord.data.model.PlaylistPrivacy
import com.music.bitchord.data.model.SearchFilter
import com.music.bitchord.data.model.SearchResult
import com.music.bitchord.data.sources.TrackMatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import timber.log.Timber
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicImportService @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        if (throwable !is CancellationException) {
            Timber.e(throwable, "Music import coroutine exception")
        }
    }
    private val importJob = SupervisorJob()
    private val importScope = CoroutineScope(Dispatchers.IO + importJob + exceptionHandler)
    private var runningJob: Job? = null

    private val _progress = MutableStateFlow(ImportProgress())
    val progress: StateFlow<ImportProgress> = _progress.asStateFlow()

    val isRunning: Boolean get() = runningJob?.isActive == true

    /** starts an import in the background; false if one is already running */
    fun startImport(parsed: ParsedImport, options: ImportOptions = ImportOptions()): Boolean {
        if (isRunning) return false
        _progress.value = ImportProgress(isRunning = true, totalTracks = parsed.totalSongs, source = parsed.provider)
        runningJob = importScope.launch {
            try {
                runImport(parsed, options)
                _progress.update { it.copy(isRunning = false, isDone = true, currentLabel = "") }
            } catch (e: CancellationException) {
                _progress.update { it.copy(isRunning = false, isDone = true, cancelled = true, currentLabel = "") }
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Import failed")
                _progress.update {
                    it.copy(isRunning = false, isDone = true, currentLabel = "", error = e.message ?: "Import failed")
                }
            }
        }
        return true
    }

    /** re-reads imported playlists and adds songs new on the service to their end. */
    fun startResync(requests: List<ResyncRequest>): Boolean {
        val first = requests.firstOrNull() ?: return true
        if (isRunning) return false
        _progress.value = ImportProgress(
            isRunning = true,
            source = first.source,
            resync = ResyncSummary(first.playlistName, playlists = requests.size),
        )
        runningJob = importScope.launch {
            try {
                runResync(requests)
                _progress.update { it.copy(isRunning = false, isDone = true, currentLabel = "") }
            } catch (e: CancellationException) {
                _progress.update { it.copy(isRunning = false, isDone = true, cancelled = true, currentLabel = "") }
                throw e
            }
        }
        return true
    }

    /** stops after the current song; everything imported so far stays */
    fun cancel() {
        runningJob?.cancel()
    }

    /** forgets a finished import's summary so the next one starts clean */
    fun clearFinished() {
        if (!isRunning) _progress.value = ImportProgress()
    }

    private suspend fun runImport(parsed: ParsedImport, options: ImportOptions) {
        val work = buildList {
            parsed.likedSongs.forEach { add(WorkTrack(it, playlistName = null, isLiked = true)) }
            parsed.librarySongs.forEach { add(WorkTrack(it, playlistName = null, isLiked = false)) }
            parsed.playlists.forEach { (name, tracks) ->
                tracks.forEach { add(WorkTrack(it, playlistName = name, isLiked = false)) }
            }
        }
        val matched = matchAll(work)

        var mirror: MirrorResult? = null
        if (options.mirrorToYouTube && matched.isNotEmpty()) {
            if (SignedIn.state(context).value) {
                val result = mirrorToYouTube(matched)
                mirror = result
                _progress.update { it.copy(youtubeFailures = result.failures) }
            } else {
                _progress.update { it.copy(youtubeFailures = parsed.playlists.keys.toList()) }
            }
        }

        // The import has nowhere to land locally yet (no local-playlist
        // store for YouTube songs — playlist NEEDS C2). Say so, with what
        // did happen on YouTube, so the run is never a silent no-op.
        val created = mirror?.playlistsCreated ?: 0
        val liked = mirror?.likedAdded ?: 0
        val gap = if (created > 0 || liked > 0) {
            val parts = listOfNotNull(
                created.takeIf { it > 0 }?.let { "$it ${if (it == 1) "playlist" else "playlists"}" },
                liked.takeIf { it > 0 }?.let { "$it liked ${if (it == 1) "song" else "songs"}" },
            ).joinToString(" and ")
            "Created on YouTube ($parts), but couldn't save a copy on this phone yet — local playlists for YouTube songs aren't available yet."
        } else {
            "Couldn't save the import on this phone yet — local playlists for YouTube songs aren't available yet. Nothing was changed."
        }
        Timber.w("Import matched %d of %d tracks; no local store, reporting the gap", matched.size, work.size)
        _progress.update { it.copy(error = gap) }
    }

    private suspend fun runResync(requests: List<ResyncRequest>) {
        for (request in requests) {
            _progress.update {
                it.copy(
                    source = request.source,
                    currentLabel = "Reading \"${request.playlistName}\" on ${request.source.label}",
                    resync = it.resync?.copy(playlistName = request.playlistName),
                )
            }
            try {
                val read = request.read()
                val work = read.tracks.map { WorkTrack(it, playlistName = request.playlistName, isLiked = false) }
                _progress.update { it.copy(totalTracks = it.totalTracks + work.size) }
                matchAll(work)
                // The read matched, but no song can be added: the playlist
                // lives in no local store yet (playlist NEEDS C2).
                _progress.update { progress ->
                    val summary = progress.resync
                    progress.copy(resync = summary?.copy(done = summary.done + 1))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Re-sync of ${request.playlistName} failed")
                _progress.update { progress ->
                    val summary = progress.resync
                    progress.copy(
                        error = "Couldn't re-sync \"${request.playlistName}\" — local playlists for YouTube songs aren't available yet.",
                        resync = summary?.copy(
                            done = summary.done + 1,
                            failed = summary.failed + request.playlistName,
                        ),
                    )
                }
            }
        }
        _progress.update {
            it.copy(
                error = it.error
                    ?: "Re-sync read the playlists, but couldn't add the new songs — local playlists for YouTube songs aren't available yet.",
            )
        }
    }

    /** every track to its YouTube video id, with live progress; exact ids skip the search */
    private suspend fun matchAll(work: List<WorkTrack>): List<MatchedTrack> = coroutineScope {
        val processed = AtomicInteger(0)
        val matchedCount = AtomicInteger(0)
        val semaphore = Semaphore(4)
        work.map { item ->
            async(Dispatchers.IO) {
                semaphore.withPermit {
                    val videoId = matchTrack(item.track)
                    val done = processed.incrementAndGet()
                    if (videoId != null) matchedCount.incrementAndGet()
                    _progress.update {
                        it.copy(
                            processedTracks = done,
                            matchedTracks = matchedCount.get(),
                            currentLabel = "Matching \"${item.track.title}\"",
                            unmatched = if (videoId == null) {
                                it.unmatched + UnmatchedTrack(item.track, item.playlistName)
                            } else {
                                it.unmatched
                            },
                        )
                    }
                    videoId?.let { MatchedTrack(item.track, it, item.playlistName, item.isLiked) }
                }
            }
        }.awaitAll().filterNotNull()
    }

    /**
     * The YouTube Music song that is [track], or null if there is none: the
     * best candidate by title, artist, length and album, and failing that the
     * search's first hit. Mirrors SpotifyImporter.matchTrack's judgement over
     * an ImportedTrack instead of a SpotifyTrack.
     */
    private suspend fun matchTrack(track: ImportedTrack): String? {
        track.videoId?.let { return it }
        val query = listOf(track.title, track.artist).filter { it.isNotBlank() }.joinToString(" ")
        if (query.isBlank()) return null
        val candidates = YtMusicRepository.search(query, SearchFilter.SONGS).getOrNull()
            ?.filterIsInstance<SearchResult.Track>()
            ?.map { it.song }
            .orEmpty()
        if (candidates.isEmpty()) return null
        val target = TrackMatcher.Target(
            title = track.title,
            artist = track.artist,
            durationSec = track.durationMs?.takeIf { it > 0 }?.div(1000)?.toInt(),
            album = track.album,
        )
        return (TrackMatcher.best(candidates, target) ?: candidates.firstOrNull())?.videoId
    }

    /**
     * The mirror leg: private YouTube playlists for the imported ones, and
     * likes for the liked songs. Library songs (an account's whole library)
     * have no YouTube equivalent to mirror into, so they are skipped.
     */
    private suspend fun mirrorToYouTube(matched: List<MatchedTrack>): MirrorResult {
        var created = 0
        val failures = mutableListOf<String>()
        val byPlaylist = matched.filter { it.playlistName != null }.groupBy { it.playlistName }
        for ((name, tracks) in byPlaylist) {
            val playlistName = name ?: continue
            val videoIds = tracks.map { it.videoId }.distinct()
            val playlistId = YtMusicRepository.createPlaylist(playlistName, PlaylistPrivacy.PRIVATE).getOrNull()
            if (playlistId == null) {
                failures += playlistName
                continue
            }
            var ok = true
            for (chunk in videoIds.chunked(100)) {
                if (YtMusicRepository.addToPlaylist(playlistId, chunk).isFailure) {
                    ok = false
                    break
                }
            }
            if (ok) created++ else failures += playlistName
        }
        val likedIds = matched.filter { it.isLiked }.map { it.videoId }.distinct()
        val likedOk = mutableListOf<String>()
        for (videoId in likedIds) {
            if (YtMusicRepository.rate(videoId, LikeStatus.LIKE).isSuccess) likedOk += videoId
        }
        if (likedOk.isNotEmpty()) LikeState.seedLiked(likedOk.toSet())
        if (likedOk.size < likedIds.size) failures += "Liked songs"
        return MirrorResult(created, likedOk.size, failures)
    }

    private class WorkTrack(
        val track: ImportedTrack,
        val playlistName: String?,
        val isLiked: Boolean,
    )

    private class MatchedTrack(
        val track: ImportedTrack,
        val videoId: String,
        val playlistName: String?,
        val isLiked: Boolean,
    )

    private class MirrorResult(
        val playlistsCreated: Int,
        val likedAdded: Int,
        val failures: List<String>,
    )
}
