// VideoEngine.kt — the video engine behind the Mochy video UI (Phase 3 adapter).
//
// ChordWave resolves video through InnerTubeX, not through a raw innertube
// PlayerResponse: InnerTubeXResolver.extractVideo returns ONE video-only
// stream (the best at or under a height cap) plus the heights the video is
// offered at. There is no format list to pick families from, so Mochy's
// pickVideoFamily / pickSharpFamily rules (h264 first, then vp9/av1; one
// track per size; 30fps beats 60; sharp family past 1080p only where the
// phone hardware-decodes it) do not apply here — InnerTubeX's
// live-benchmarked client catalog chooses the rendition instead, exactly as
// ChordWave's own music-video player (ui/player/MusicVideoPlayer.kt) does.
// The VideoStreams shape is preserved: one track, smallest-first trivially,
// and availableSizes carries the heights list so the quality menu still
// offers every size — picking one re-resolves at that cap (see
// YouTubeVideoSession.setQuality).
//
// What InnerTubeX does not report, the track honestly leaves blank: itag 0,
// codecs "", fps 0, bitrate 0, and no init/index ranges — so the track is
// NOT chunked and plays progressive. The DASH path (VideoDash) only activates
// if a future resolver supplies ranged tracks.

package com.music.bitchord.ui.mochy.player.video

import android.net.Uri
import com.metrolist.innertubex.extraction.StreamResolveException
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.innertube.InnerTubeXResolver
import com.music.bitchord.ui.mochy.lyrics.SongVideoMap
import com.music.bitchord.ui.mochy.utils.VideoStreams
import com.music.bitchord.ui.mochy.utils.VideoTrack
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

/** The tallest picture a resolve asks for: the top of YouTube's ladder. */
private const val MaxVideoHeight = 2160

/** A googlevideo URL lives about six hours; a resolve with no `expire` in its URL is trusted that long. */
private const val StreamTtlMs = 6 * 60 * 60 * 1000L

/** What the plain ask fails with when the age gate is why (same set as MusicVideoEngine). */
private val EscalatedReasons = setOf(
    StreamResolveException.Reason.AGE_RESTRICTED,
    StreamResolveException.Reason.NO_PLAYABLE_STREAM,
)

/** Videos the age gate stopped, asked as age-restricted from the start next time. */
private val ageGated = ConcurrentHashMap.newKeySet<String>()

/** Minted picture URLs and the (videoId, client profile) that minted them — what a mid-playback refusal reports. */
private val streamProfiles = ConcurrentHashMap<String, Pair<String, String>>()

private const val MaxRememberedProfiles = 64

/** The InnerTubeX session generation the sets above were filled under; a sign-in/out clears them. */
@Volatile
private var seenSessionGeneration = -1

/**
 * One video's streams: the best video-only picture at or under [maxHeight],
 * with the headers its fetch has to wear, when its links run out, and the
 * sizes the video is offered at.
 *
 * [durationMs] is the video's length when the caller knows it (the matched
 * song row's duration text, or the playing song's length for its own video);
 * 0 when nobody knows — the sync loop then falls back to the playing song's
 * length (see DriveYouTubeVideo.target) and the player reports its own
 * duration once loaded.
 */
suspend fun resolveVideoStreams(
    videoId: String,
    maxHeight: Int = MaxVideoHeight,
    durationMs: Long = 0L,
): Result<VideoStreams> {
    if (videoId.isBlank()) return Result.failure(IllegalArgumentException("empty videoId"))
    val stream = try {
        resolvePicture(videoId, maxHeight)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Timber.w("VideoEngine: could not resolve the picture for $videoId: ${e.message}")
        return Result.failure(e)
    } ?: return Result.failure(IllegalStateException("No video-only stream for $videoId"))
    if (streamProfiles.size >= MaxRememberedProfiles) streamProfiles.clear()
    streamProfiles[stream.url] = stream.videoId to stream.profileId
    val uri = Uri.parse(stream.url)
    val track = VideoTrack(
        // InnerTubeX does not report itags, codecs, fps or bitrate for the
        // video stream it picks; nothing downstream needs them (the share
        // preview's h264 preference falls back to the only track, and the
        // DASH builder needs ranges, which are absent too).
        itag = 0,
        url = stream.url,
        mimeType = stream.mimeType,
        codecs = "",
        width = stream.width,
        height = stream.height,
        fps = 0,
        bitrate = 0,
        initRange = null,
        indexRange = null,
        // Every progressive googlevideo URL carries the whole size as `clen`.
        contentLength = uri.getQueryParameter("clen")?.toLongOrNull(),
    )
    // Heights are heights; the menu lists short sides. One video keeps its
    // aspect across renditions, so the chosen stream's aspect converts them.
    val aspect = if (stream.width > 0 && stream.height > 0) stream.width.toFloat() / stream.height else 0f
    val availableSizes = stream.heights
        .map { height -> if (aspect > 0f) minOf(height, (height * aspect).roundToInt()) else height }
        .filter { it > 0 }
        .distinct()
        .sortedDescending()
    return Result.success(
        VideoStreams(
            videoId = videoId,
            tracks = listOf(track),
            headers = stream.headers,
            expiresAtMs = uri.getQueryParameter("expire")?.toLongOrNull()?.times(1000L)
                ?: (System.currentTimeMillis() + StreamTtlMs),
            durationMs = durationMs,
            availableSizes = availableSizes,
        ),
    )
}

/**
 * The picture for [videoId], asked plainly and then — when that found
 * nothing — as age-restricted, which InnerTubeX needs told (same escalation
 * as MusicVideoEngine.resolve).
 */
private suspend fun resolvePicture(videoId: String, cap: Int): InnerTubeXResolver.VideoStream? =
    withContext(Dispatchers.IO) {
        val generation = InnerTubeXResolver.sessionGeneration.value
        if (generation != seenSessionGeneration) {
            seenSessionGeneration = generation
            ageGated.clear()
            streamProfiles.clear()
        }
        if (videoId !in ageGated) {
            val plain = try {
                InnerTubeXResolver.extractVideo(videoId, cap)
            } catch (e: StreamResolveException) {
                if (e.reason !in EscalatedReasons) throw e
                null
            }
            if (plain != null) return@withContext plain
            Timber.d("VideoEngine: nothing asked plainly for $videoId; asking as age-restricted")
        }
        InnerTubeXResolver.extractVideo(videoId, cap, ageRestricted = true)
            ?.also { ageGated += videoId }
    }

/**
 * A picture's URL was refused mid-playback (403/404/410 from googlevideo):
 * the client that minted it is benched for this video, so the next resolve
 * asks the next client in InnerTubeX's catalog, and the cipher config gets a
 * refresh — the same signal MusicVideoEngine sends.
 */
fun noteVideoRefused(url: String) {
    val (videoId, profileId) = streamProfiles[url] ?: return
    InnerTubeXResolver.exclude(videoId, profileId)
    InnerTubeXResolver.onVideoRefused()
}

/**
 * Where a song's moments sit in its music video, from YouTube Music's own
 * pairing — the `segmentMap` its Song/Video switch reads. Exact, and nothing
 * is measured to get it: it arrives with the pair itself.
 *
 * Null when YouTube did not pair this exact song and video itself (the pair
 * came from a search match, or the user is signed out) — measuring the
 * alignment by listening to both audios, as Mochy's VideoAudioSync did, is
 * NOT implemented: it needs both streams decoded and cross-correlated, a
 * build of its own. Callers fall back to the caption-based lyric timeline,
 * then to playing the two side by side.
 */
suspend fun matchSongToVideo(songId: String, videoId: String): SongVideoMap? {
    val map = YtMusicRepository.versionTimeMap(songId, videoId) ?: return null
    val runs = map.segments.map { segment ->
        SongVideoMap.Run(
            fromSongMs = segment.fromStartMs,
            toSongMs = segment.fromStartMs + segment.durationMs,
            deltaMs = segment.toStartMs - segment.fromStartMs,
        )
    }
    return SongVideoMap(runs)
}

/** "3:24" or "1:02:03" to ms; 0 when the text is not a duration. */
fun parseDurationText(durationText: String?): Long {
    if (durationText.isNullOrBlank()) return 0L
    val parts = durationText.trim().split(":")
    if (parts.isEmpty() || parts.size > 3) return 0L
    var seconds = 0L
    for (part in parts) {
        val value = part.toLongOrNull() ?: return 0L
        if (value < 0L) return 0L
        seconds = seconds * 60 + value
    }
    return seconds * 1000L
}
