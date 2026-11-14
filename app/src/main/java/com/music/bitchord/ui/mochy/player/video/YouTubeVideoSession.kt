// Ported from Mochy Music (GPL-3.0): ui/player/video/YouTubeVideoSession.kt
// ENGINE RESTORED (Phase 3 adapter) — the ExoPlayer wiring, DASH/progressive
// media sources, stream loading and song sync. The UI-facing shape (state
// holders + method signatures + the YouTubeVideoSessions registry) is the
// verbatim port; the player runs muted, REPEAT_MODE_ONE, scale-to-fit-with-
// cropping, on a dedicated HandlerThread("YouTubeVideo"), with HTTP through
// the app's single OkHttp client (Http.client) and a small buffer (12s min /
// 40s max / 800ms to start / 1600ms to resume).
//
// Sync (NEEDS.md §(b) B1): hard-seek when |drift| > 1200ms, aimed ahead by
// learned seek latency (max 3000ms); else nudge the speed by -drift/2500ms
// clamped to ±12% (0 when settled within 40ms or paused); skip while
// BUFFERING; a seek that has not landed in 9000ms is given up.
//
// Quality: the resolver hands the session one track (see VideoEngine), so a
// cap below the loaded track re-resolves at the cap and reloads at the
// current position; stepping back up reloads the full resolve without a
// network round trip. Track selection caps (setMaxVideoSize,
// setForceHighestSupportedBitrate when fixed) are applied for the DASH path.

package com.music.bitchord.ui.mochy.player.video

import android.content.Context
import android.graphics.SurfaceTexture
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.view.Surface
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.dash.manifest.DashManifestParser
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.music.bitchord.data.Http
import com.music.bitchord.playback.ChunkedDataSource
import com.music.bitchord.ui.mochy.constants.VideoQuality
import com.music.bitchord.ui.mochy.utils.VideoStreams
import com.music.bitchord.ui.mochy.utils.VideoTrack
import com.music.bitchord.ui.mochy.utils.chunked
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.math.abs
import kotlin.math.roundToInt

// how long a session waits for someone else to pick it up after its last view goes, before letting go of the player
private const val LingerMs = 1_500L

// past this the picture jumps to the song; under it, its rate is nudged
private const val HardSeekDriftMs = 1_200L

// inside this the picture counts as settled: no nudge
private const val SettleMs = 40L

// the nudge closes the drift over this long: -drift/2500ms, clamped to ±12%
private const val NudgeSpanMs = 2_500f
private const val MaxNudge = 0.12f

// a seek that has not landed in this long is given up: the next tick may try again
private const val SeekGiveUpMs = 9_000L

// the most a seek is aimed ahead by learned seek latency
private const val MaxSeekLeadMs = 3_000L

// small buffer, per the contract: 12s min / 40s max / 800ms to start / 1600ms to resume
private const val MinBufferMs = 12_000
private const val MaxBufferMs = 40_000
private const val BufferForPlaybackMs = 800
private const val BufferForPlaybackAfterRebufferMs = 1_600

// bounded ranges, for the same reason as the audio — see ChunkedDataSource
private const val ChunkBytes = 1L * 1024 * 1024

// how long a detached texture is given for the decoder to let go before its owner frees it
private const val SurfaceReleaseDelayMs = 500L

// the manifest URI the DASH source asks for; the manifest data source ignores it and serves the built manifest
private const val ManifestUri = "dash://manifest"

// googlevideo's ways of refusing a URL (same set as the audio player)
private val RefusalCodes = setOf(403, 404, 410)

// one youtube video playing without sound, kept in step with the song; the main thread only reads its state and posts to it
@Stable
@OptIn(UnstableApi::class)
class YouTubeVideoSession internal constructor(context: Context, val videoId: String) {
    // the first picture is up, so the view can fade in
    var hasFrame by mutableStateOf(false)
        private set

    // it should be playing but is waiting on the network, so a loader shows
    var isStalled by mutableStateOf(false)
        private set

    // the short side of the picture playing right now, for "Auto (720p)"
    var playingShortSide by mutableIntStateOf(0)
        private set

    var aspect by mutableFloatStateOf(0f)
        private set

    // counts up each time the links stop working, so whoever owns the video can fetch new ones
    var failures by mutableIntStateOf(0)
        private set

    // the short sides this video comes in, biggest first
    var sizes by mutableStateOf(emptyList<Int>())
        private set

    // what's loaded, read on the main thread to tell a fresh load from a repeat
    @Volatile
    var loaded: VideoStreams? = null
        private set

    private val appContext = context.applicationContext

    // ---- engine ----

    private val videoThread = HandlerThread("YouTubeVideo").apply { start() }
    private val videoHandler = Handler(videoThread.looper)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val trackSelector = DefaultTrackSelector(appContext)

    @Volatile
    private var released = false

    // the player and everything it touches live on the video thread only
    private var player: ExoPlayer? = null
    private var loadedTrackUrl: String? = null
    private var seekLeadMs = 250L
    private var seekStartedAt = 0L
    private val textures = ArrayList<SurfaceTexture>()
    private val surfaces = HashMap<SurfaceTexture, Surface>()

    // written on the main thread, read on the video thread
    @Volatile
    private var currentQuality = VideoQuality.AUTO
    @Volatile
    private var currentAutoCap = 0
    @Volatile
    private var loadedTrack: VideoTrack? = null

    // main thread only: the in-flight re-resolve, and the max-cap resolve for stepping back up
    private var loadJob: Job? = null
    private var fullStreams: VideoStreams? = null

    private val playerListener = object : Player.Listener {
        override fun onRenderedFirstFrame() {
            mainHandler.post { hasFrame = true }
        }

        override fun onVideoSizeChanged(videoSize: VideoSize) {
            val width = videoSize.width * videoSize.pixelWidthHeightRatio
            if (width > 0f && videoSize.height > 0) {
                val newAspect = width / videoSize.height
                val shortSide = minOf(width.roundToInt(), videoSize.height)
                mainHandler.post {
                    aspect = newAspect
                    playingShortSide = shortSide
                }
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY && seekStartedAt > 0L) {
                val took = SystemClock.uptimeMillis() - seekStartedAt
                seekLeadMs = ((seekLeadMs + took) / 2).coerceIn(0L, MaxSeekLeadMs)
                seekStartedAt = 0L
            }
            updateStalled()
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            updateStalled()
        }

        override fun onPlayerError(error: PlaybackException) {
            val url = loadedTrackUrl
            if (url != null && isRefusal(error)) {
                noteVideoRefused(url)
            }
            mainHandler.post { failures += 1 }
        }
    }

    fun load(streams: VideoStreams, quality: VideoQuality, autoCap: Int, startMs: Long) {
        if (released) return
        currentQuality = quality
        currentAutoCap = autoCap
        fullStreams = streams
        val cap = effectiveCap(quality, autoCap)
        val track = streams.tracks.firstOrNull()
        if (track != null && cap < track.shortSide) {
            // The resolve came back bigger than the cap allows: ask again at
            // the cap rather than playing oversized bytes.
            loadJob?.cancel()
            loadJob = scope.launch {
                val smaller = resolveVideoStreams(videoId, maxHeight = cap, durationMs = streams.durationMs).getOrNull()
                if (released) return@launch
                if (smaller == null) {
                    Timber.w("YouTubeVideoSession: re-resolve at ${cap}p failed; keeping the ${track.shortSide}p picture")
                    publishLoaded(streams)
                    videoHandler.post { loadMedia(streams, startMs) }
                } else {
                    publishLoaded(smaller)
                    videoHandler.post { loadMedia(smaller, startMs) }
                }
            }
            return
        }
        publishLoaded(streams)
        videoHandler.post { loadMedia(streams, startMs) }
    }

    fun setQuality(quality: VideoQuality, autoCap: Int) {
        if (released) return
        currentQuality = quality
        currentAutoCap = autoCap
        videoHandler.post { applySelectionParams() }
        val streams = loaded ?: return
        val current = loadedTrack ?: return
        val cap = effectiveCap(quality, autoCap)
        val full = fullStreams?.tracks?.firstOrNull()
        val best = minOf(cap, full?.shortSide ?: current.shortSide)
        if (current.shortSide == best) return
        val fullNow = fullStreams
        if (full != null && full.shortSide == best && fullNow != null) {
            // stepping back up to a picture already resolved: no network
            loadJob?.cancel()
            publishLoaded(fullNow)
            videoHandler.post { loadMedia(fullNow, currentPositionMs()) }
            return
        }
        loadJob?.cancel()
        loadJob = scope.launch {
            val atCap = resolveVideoStreams(videoId, maxHeight = best, durationMs = streams.durationMs).getOrNull()
            if (released) return@launch
            if (atCap == null) {
                Timber.w("YouTubeVideoSession: re-resolve at ${best}p failed; keeping ${current.shortSide}p")
                return@launch
            }
            publishLoaded(atCap)
            videoHandler.post { loadMedia(atCap, currentPositionMs()) }
        }
    }

    // where the song says the picture should be (null: wherever it is), read on the main thread just now
    fun sync(targetMs: Long?, songSpeed: Float, playing: Boolean) {
        if (released) return
        videoHandler.post { syncOnVideoThread(targetMs, songSpeed, playing) }
    }

    fun attach(texture: SurfaceTexture) {
        videoHandler.post {
            if (released) return@post
            textures.remove(texture)
            textures += texture
            val surface = surfaces.getOrPut(texture) { Surface(texture) }
            player?.setVideoSurface(surface)
        }
    }

    // waits here, off the main thread, until the decoder lets go of the texture, then hands it back to be freed
    fun detach(texture: SurfaceTexture, onFreed: () -> Unit) {
        videoHandler.post {
            val wasCurrent = textures.lastOrNull() === texture
            textures.remove(texture)
            val surface = surfaces.remove(texture)
            if (wasCurrent) {
                val next = textures.lastOrNull()
                if (next != null) {
                    val nextSurface = surfaces.getOrPut(next) { Surface(next) }
                    player?.setVideoSurface(nextSurface)
                } else {
                    player?.clearVideoSurface()
                }
            }
            // The renderer's release of the old surface is asynchronous; the
            // delay gives it a beat to let go before the texture is freed.
            videoHandler.postDelayed(
                {
                    surface?.release()
                    mainHandler.post(onFreed)
                },
                SurfaceReleaseDelayMs,
            )
        }
    }

    internal fun release() {
        if (released) return
        released = true
        loadJob?.cancel()
        scope.cancel()
        videoHandler.post {
            player?.release()
            player = null
            surfaces.values.forEach { it.release() }
            surfaces.clear()
            textures.clear()
            videoThread.quitSafely()
        }
    }

    // ---- internals (main thread unless noted) ----

    private fun publishLoaded(streams: VideoStreams) {
        loaded = streams
        sizes = streams.sizes
        loadedTrack = streams.tracks.firstOrNull()
    }

    private fun effectiveCap(quality: VideoQuality, autoCap: Int): Int =
        if (quality == VideoQuality.AUTO) autoCap.takeIf { it > 0 } ?: Int.MAX_VALUE else quality.shortSide

    private fun currentPositionMs(): Long = player?.currentPosition?.coerceAtLeast(0L) ?: 0L

    // ---- internals (video thread only) ----

    private fun ensurePlayer(): ExoPlayer {
        player?.let { return it }
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(MinBufferMs, MaxBufferMs, BufferForPlaybackMs, BufferForPlaybackAfterRebufferMs)
            .build()
        return ExoPlayer.Builder(appContext)
            .setLooper(videoHandler.looper)
            .setLoadControl(loadControl)
            .setTrackSelector(trackSelector)
            .build()
            .apply {
                volume = 0f
                repeatMode = Player.REPEAT_MODE_ONE
                videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
                trackSelectionParameters = trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                    .build()
                addListener(playerListener)
                textures.lastOrNull()?.let { texture ->
                    setVideoSurface(surfaces.getOrPut(texture) { Surface(texture) })
                }
            }
            .also { player = it }
    }

    private fun loadMedia(streams: VideoStreams, startMs: Long) {
        if (released) return
        val exo = ensurePlayer()
        val track = streams.tracks.firstOrNull() ?: return
        loadedTrackUrl = track.url
        seekStartedAt = 0L
        exo.setMediaSource(mediaSourceFor(streams, track), startMs.coerceAtLeast(0L))
        // the sync loop turns playback on with the song; until its first tick the picture holds still
        exo.playWhenReady = false
        exo.prepare()
        applySelectionParams()
    }

    private fun mediaSourceFor(streams: VideoStreams, track: VideoTrack): MediaSource {
        val httpFactory = OkHttpDataSource.Factory(Http.client)
            .setDefaultRequestProperties(streams.headers)
        val chunkedFactory = ChunkedDataSource.Factory(httpFactory, ChunkBytes)
        val family = streams.tracksFor(track.shortSide)
        // Mochy's own shape: the generated manifest is parsed and sideloaded
        // into the DASH source (there is no setManifestDataSourceFactory in
        // media3 — the factory only takes one in its constructor). A manifest
        // that fails to parse falls through to the progressive source below.
        val manifest = if (family.chunked) {
            VideoDash.manifest(family, streams.durationMs)?.let { xml ->
                runCatching { DashManifestParser().parse(Uri.parse(ManifestUri), xml.byteInputStream()) }.getOrNull()
            }
        } else null
        if (manifest != null) {
            val item = MediaItem.Builder().setUri(ManifestUri).setMimeType(MimeTypes.APPLICATION_MPD).build()
            return DashMediaSource.Factory(chunkedFactory).createMediaSource(manifest, item)
        }
        return ProgressiveMediaSource.Factory(chunkedFactory)
            .createMediaSource(MediaItem.fromUri(track.url))
    }

    private fun applySelectionParams() {
        val exo = player ?: return
        val cap = effectiveCap(currentQuality, currentAutoCap)
        val track = loadedTrack
        val aspect = track?.let { if (it.height > 0) it.width.toFloat() / it.height else 0f } ?: 0f
        // the cap is a short side: cap the height on a landscape picture, the width on a portrait one
        val maxWidth = if (cap >= Int.MAX_VALUE || aspect >= 1f || aspect == 0f) Int.MAX_VALUE else cap
        val maxHeight = if (cap >= Int.MAX_VALUE || aspect < 1f && aspect != 0f) Int.MAX_VALUE else cap
        exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
            .setMaxVideoSize(maxWidth, maxHeight)
            .setForceHighestSupportedBitrate(currentQuality != VideoQuality.AUTO)
            .build()
    }

    private fun syncOnVideoThread(targetMs: Long?, songSpeed: Float, playing: Boolean) {
        val exo = player ?: return
        if (released) return
        if (exo.playbackState == Player.STATE_IDLE || exo.playbackState == Player.STATE_ENDED) return
        // a seek that has not landed in 9s is given up: the next tick may try again
        if (seekStartedAt > 0L && SystemClock.uptimeMillis() - seekStartedAt > SeekGiveUpMs) {
            seekStartedAt = 0L
        }
        if (exo.playWhenReady != playing) exo.playWhenReady = playing
        val duration = exo.duration.takeIf { it > 0L }
            ?: loaded?.durationMs?.takeIf { it > 0L }
            ?: -1L
        // out-of-range targets just play on
        val target = targetMs?.takeIf { it >= 0L && (duration <= 0L || it <= duration) }
        if (target == null) {
            // no usable target: match the song's rate and leave the picture where it is
            setSpeed(exo, songSpeed)
            return
        }
        // while the picture is waiting on the network, leave it alone
        if (exo.playbackState == Player.STATE_BUFFERING) return
        val drift = exo.currentPosition - target
        if (abs(drift) > HardSeekDriftMs) {
            if (seekStartedAt == 0L) {
                seekStartedAt = SystemClock.uptimeMillis()
                exo.seekTo((target + seekLeadMs).coerceAtLeast(0L))
            }
            return
        }
        // settled within 40ms, or paused: no nudge
        val nudge = if (!playing || abs(drift) <= SettleMs) 0f
        else (-drift / NudgeSpanMs).coerceIn(-MaxNudge, MaxNudge)
        setSpeed(exo, songSpeed * (1f + nudge))
    }

    private fun setSpeed(exo: ExoPlayer, speed: Float) {
        if (abs(exo.playbackParameters.speed - speed) > 0.005f) {
            exo.playbackParameters = PlaybackParameters(speed)
        }
    }

    private fun updateStalled() {
        val exo = player ?: return
        val stalled = exo.playWhenReady && exo.playbackState == Player.STATE_BUFFERING
        mainHandler.post { isStalled = stalled }
    }

    private fun isRefusal(error: PlaybackException): Boolean =
        generateSequence<Throwable>(error) { it.cause }
            .filterIsInstance<HttpDataSource.InvalidResponseCodeException>()
            .any { it.responseCode in RefusalCodes }
}

// one session per video, kept a moment after its last view goes, so the player turning into the full video mode keeps the same picture going
object YouTubeVideoSessions {
    private class Entry(val session: YouTubeVideoSession) {
        var users = 0
        var pendingRelease: Runnable? = null
    }

    private val entries = HashMap<String, Entry>()
    private val main = Handler(Looper.getMainLooper())

    fun acquire(context: Context, videoId: String): YouTubeVideoSession {
        // only one video plays at a time: one left waiting for a hand-over that didn't come goes as another starts
        entries.entries.removeAll { (id, other) ->
            val idle = id != videoId && other.users == 0
            if (idle) {
                other.pendingRelease?.let(main::removeCallbacks)
                other.session.release()
            }
            idle
        }
        val entry = entries.getOrPut(videoId) { Entry(YouTubeVideoSession(context, videoId)) }
        entry.pendingRelease?.let(main::removeCallbacks)
        entry.pendingRelease = null
        entry.users += 1
        return entry.session
    }

    fun release(session: YouTubeVideoSession) {
        val entry = entries[session.videoId]?.takeIf { it.session === session } ?: return
        entry.users -= 1
        if (entry.users > 0) return
        val drop = Runnable {
            if (entry.users == 0 && entries[session.videoId] === entry) {
                entries.remove(session.videoId)
                session.release()
            }
        }
        entry.pendingRelease = drop
        main.postDelayed(drop, LingerMs)
    }
}
