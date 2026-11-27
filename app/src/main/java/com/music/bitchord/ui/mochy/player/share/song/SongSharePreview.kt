// Ported from Mochy Music (GPL-3.0): ui/player/share/song/SongSharePreview.kt
// SongSharePreview.kt

package com.music.bitchord.ui.mochy.player.share.song

import android.content.Context
import android.graphics.SurfaceTexture
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.view.Surface
import android.view.TextureView
import android.view.ViewGroup
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import com.music.bitchord.playback.ChunkedDataSource
import com.music.bitchord.ui.mochy.component.PopupSheetState
import com.music.bitchord.ui.mochy.component.damageBoundary
import com.music.bitchord.ui.mochy.player.CanvasArtworkPlayer
import com.music.bitchord.ui.mochy.player.share.ShareHeight
import com.music.bitchord.ui.mochy.player.share.ShareRegions
import com.music.bitchord.ui.mochy.player.share.ShareWidth
import com.music.bitchord.ui.mochy.utils.VideoTrack
import com.music.bitchord.ui.mochy.player.share.song.export.followAppProxy
import okhttp3.OkHttpClient
import timber.log.Timber
import kotlin.math.max
import kotlin.math.min

// whether this page is the one in front: a page pushed over it stops its picture moving until it's back on top
@Composable
internal fun PopupSheetState.rememberIsFront(): Boolean {
    val depth = remember { frames.size }
    return frames.count { !it.closing } <= depth
}

// the 360 x 640 picture shrunk to fit the box: laid out at a smaller density instead of scaled, so the card's glass lines up by plain positions
@Composable
internal fun SharePictureFit(modifier: Modifier, corner: Dp = 26.dp, fill: Boolean = false, content: @Composable () -> Unit) {
    BoxWithConstraints(modifier.then(if (fill) Modifier.clipToBounds() else Modifier), contentAlignment = Alignment.Center) {
        // fill crops the picture to the box, for the small tiles; otherwise it fits whole
        val k = if (fill) max(maxWidth / ShareWidth, maxHeight / ShareHeight) else min(maxWidth / ShareWidth, maxHeight / ShareHeight)
        val density = LocalDensity.current
        // a moving picture repaints only its own box, not the whole sheet with it
        Box(Modifier.requiredSize(ShareWidth * k, ShareHeight * k).clip(RoundedCornerShape(corner)).damageBoundary()) {
            CompositionLocalProvider(LocalDensity provides Density(density.density * k, fontScale = 1f)) { content() }
        }
    }
}

// fits the card-only view into the available box by adjusting density (not post-scale) so text and shaders stay razor sharp
@Composable
internal fun ShareCardOnlyFit(
    session: SongShareSession,
    positionMs: () -> Long,
    moving: Boolean,
    playing: Boolean,
    modifier: Modifier = Modifier,
    maxScale: Float = 1.3f,
    onCardTap: (() -> Unit)? = null,
) {
    val cardWidth = cardWidthOf(session.kind)
    // Remember measured height/width ratio so tall lyrics cards scale smoothly to fit inside the preview box
    var aspect by remember(session.kind) {
        mutableFloatStateOf(if (session.kind == SongShareKind.MusicOnly) 1.55f else 0.9f)
    }
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val k = min(maxWidth / cardWidth, maxHeight / (cardWidth * aspect)).coerceIn(0.35f, maxScale)
        val density = LocalDensity.current
        Box(Modifier.damageBoundary()) {
            CompositionLocalProvider(LocalDensity provides Density(density.density * k, fontScale = 1f)) {
                SongShareCardOnly(
                    session = session,
                    positionMs = positionMs,
                    layers = ShareLayers.Everything,
                    moving = moving,
                    onCardTap = onCardTap,
                    video = { ShareVideoLayer(session, playing = playing, source = session.activeVideoSource) },
                    modifier = Modifier.onSizeChanged { sz ->
                        if (sz.width > 0 && sz.height > 0) {
                            val measured = sz.height.toFloat() / sz.width.toFloat()
                            if (kotlin.math.abs(measured - aspect) > 0.01f) aspect = measured
                        }
                    },
                )
            }
        }
    }
}

// the editor's live picture: drag or pinch to move the background, tap the card to style it; only the front page's picture moves
@Composable
internal fun SongSharePreview(
    session: SongShareSession,
    live: Boolean,
    modifier: Modifier = Modifier,
    onCardTap: (() -> Unit)? = null,
    movable: Boolean = true,
    regions: ShareRegions? = null,
    overlay: (@Composable BoxScope.() -> Unit)? = null,
) {
    val resting = remember(session) { { session.windowStartMs } }
    if (session.isCardOnly) {
        ShareCardOnlyFit(
            session = session,
            positionMs = if (live) session.playheadMs else resting,
            moving = live && session.isVideo,
            playing = live && session.previewPlays,
            modifier = modifier,
            onCardTap = onCardTap,
        )
        return
    }
    SharePictureFit(modifier) {
        Box {
            SongSharePicture(
                session = session,
                positionMs = if (live) session.playheadMs else resting,
                layers = ShareLayers.Everything,
                moving = live && session.isVideo,
                reportsCard = true,
                onCardTap = onCardTap,
                regions = regions,
                video = { ShareVideoLayer(session, playing = live && session.previewPlays) },
                modifier = if (movable) {
                    Modifier.pointerInput(session) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            session.moveBackground(zoom, pan.x / size.width, pan.y / size.height)
                        }
                    }
                } else {
                    Modifier
                },
            )
            overlay?.invoke(this)
        }
    }
}

// the background video under the preview, started over each time the song's window loops
@Composable
internal fun ShareVideoLayer(
    session: SongShareSession,
    playing: Boolean,
    modifier: Modifier = Modifier.fillMaxSize(),
    source: ShareVideoSource? = session.activeVideoSource,
) {
    val loops = session.preview?.loops ?: 0
    when (source) {
        null, ShareVideoSource.LiquidCover -> Unit
        ShareVideoSource.AnimatedCover -> session.animatedCover.found?.let { url ->
            CanvasArtworkPlayer(primaryUrl = url, fallbackUrl = null, isPlaying = playing, modifier = modifier)
        }
        ShareVideoSource.MusicVideo -> session.musicVideo.found?.let { music ->
            val track = music.trackFor(PreviewVideoShortSide) ?: return@let
            val start = music.videoMsAt(session.windowStartMs).coerceAtLeast(0L)
            val mime = track.mimeType.substringBefore(';').ifBlank { MimeTypes.VIDEO_MP4 }
            val item = remember(track.url, start, session.windowLengthMs) {
                clippedItem(MediaItem.Builder().setUri(track.url).setMimeType(mime), start, session.windowLengthMs)
            }
            LoopingVideo(item, music.streams.headers, playing, restartKey = loops, modifier = modifier)
        }
        ShareVideoSource.YouTube -> session.pickedYouTubeVideo.found?.let { yt ->
            val track = yt.trackFor(PreviewVideoShortSide) ?: return@let
            val (start, length) = youtubeClipWindow(yt, session.windowStartMs, session.windowLengthMs)
            val mime = track.mimeType.substringBefore(';').ifBlank { MimeTypes.VIDEO_MP4 }
            val item = remember(track.url, start, length) {
                clippedItem(MediaItem.Builder().setUri(track.url).setMimeType(mime), start, length)
            }
            LoopingVideo(item, yt.streams.headers, playing, restartKey = loops, modifier = modifier)
        }
        is ShareVideoSource.Own -> {
            val item = remember(source) { clippedItem(MediaItem.Builder().setUri(source.uri), source.startMs, source.lengthMs) }
            LoopingVideo(item, emptyMap(), playing, restartKey = loops, modifier = modifier)
        }
    }
}

// the preview keeps to a light picture; the export reads the sharpest one
private const val PreviewVideoShortSide = 720

// bounded range size for the preview's reads, the main video session's own
private const val PreviewChunkBytes = 1L * 1024 * 1024

internal fun clippedItem(builder: MediaItem.Builder, startMs: Long, lengthMs: Long): MediaItem =
    builder
        .setClippingConfiguration(
            MediaItem.ClippingConfiguration.Builder()
                .setStartPositionMs(startMs.coerceAtLeast(0L))
                .setEndPositionMs(startMs.coerceAtLeast(0L) + lengthMs)
                .build(),
        )
        .build()

// the music video's sharpest h264 picture up to maxShortSide, which every phone decodes in hardware; any codec when it has no h264
internal fun ShareMusicVideo.trackFor(maxShortSide: Int): VideoTrack? {
    val fitting = streams.tracks.filter { it.shortSide in 1..maxShortSide }
    val h264 = fitting.filter { it.codecs.startsWith("avc1") && it.mimeType.contains("mp4") }
    return (h264.ifEmpty { fitting }).maxByOrNull { it.shortSide * 1000 + it.fps.coerceAtMost(60) }
        ?: streams.tracks.minByOrNull { it.shortSide }
}

// a clip that loops on the canvas thread, cropped to fill, so loading and releasing it never stall the sheet
@OptIn(UnstableApi::class)
@Composable
private fun LoopingVideo(item: MediaItem, headers: Map<String, String>, playing: Boolean, restartKey: Int, modifier: Modifier) {
    val context = LocalContext.current
    val factory = remember(headers) {
        val http = OkHttpDataSource.Factory(OkHttpClient.Builder().followAppProxy().build())
            .setDefaultRequestProperties(headers)
        // googlevideo paces one open-ended read down to about playback speed,
        // while bounded ranges are served at line rate (see ChunkedDataSource):
        // read plainly, this preview never built a buffer and sat blank. The
        // main video session reads the same URLs chunked for the same reason.
        DefaultMediaSourceFactory(
            DefaultDataSource.Factory(context, ChunkedDataSource.Factory(http, PreviewChunkBytes)),
        )
    }
    val frame = remember {
        AspectRatioFrameLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        }
    }
    val texture = remember { ShareLoopTexture() }
    val currentPlaying by rememberUpdatedState(playing)
    val player = remember(factory) { ShareLoopSession(context, factory, currentPlaying).also { texture.session = it } }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                Timber.w(error, "SongShare preview video failed to play")
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    frame.post { frame.setAspectRatio(videoSize.width.toFloat() / videoSize.height) }
                }
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
    LaunchedEffect(player, item) { player.load(item, currentPlaying) }
    LaunchedEffect(player, playing) { player.setPlaying(playing) }
    LaunchedEffect(player, restartKey) { if (restartKey > 0) player.restart() }
    AndroidView(
        factory = { viewContext ->
            frame.apply {
                if (childCount == 0) {
                    addView(
                        TextureView(viewContext).apply {
                            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                            surfaceTextureListener = texture
                        },
                    )
                }
            }
        },
        modifier = modifier,
    )
}

// One looping video's player, touched only on its own thread; the main thread
// just posts to it. A private port of Mochy's CanvasVideoSession (from
// ui/player/CanvasArtworkPlayer.kt), kept private to this file because the
// canvas chunk never ported the original — if a later chunk ports
// CanvasVideoSession at ui.mochy.player, LoopingVideo should switch to it and
// these two classes should go away.
private object ShareLoopThread {
    val handler: Handler by lazy { Handler(HandlerThread("ShareLoopVideo").apply { start() }.looper) }
}

private class ShareLoopSession(
    context: Context,
    mediaSourceFactory: MediaSource.Factory,
    playing: Boolean,
) {
    private val handler = ShareLoopThread.handler
    private var player: ExoPlayer? = null
    private var surface: Surface? = null

    init {
        handler.post {
            player = ExoPlayer.Builder(context)
                .setLooper(handler.looper)
                .setMediaSourceFactory(mediaSourceFactory)
                .build()
                .apply {
                    // no size/bitrate caps: Mochy's resolver handed this session
                    // a list of renditions to cap, ChordWave's supplies exactly
                    // one track (VideoEngine) — a cap below it leaves nothing
                    // to select and the preview stays blank
                    trackSelectionParameters = trackSelectionParameters
                        .buildUpon()
                        .setForceHighestSupportedBitrate(false)
                        .build()
                    setAudioAttributes(
                        AudioAttributes
                            .Builder()
                            .setUsage(C.USAGE_MEDIA)
                            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                            .build(),
                        false,
                    )
                    volume = 0f
                    repeatMode = Player.REPEAT_MODE_ONE
                    videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
                    playWhenReady = playing
                    surface?.let(::setVideoSurface)
                }
        }
    }

    fun setPlaying(playing: Boolean) = handler.post {
        player?.let { if (it.playWhenReady != playing) it.playWhenReady = playing }
    }

    fun addListener(listener: Player.Listener) = handler.post { player?.addListener(listener) }

    fun removeListener(listener: Player.Listener) = handler.post { player?.removeListener(listener) }

    fun load(mediaItem: MediaItem, playing: Boolean) = handler.post {
        player?.apply {
            stop()
            setMediaItem(mediaItem)
            prepare()
            playWhenReady = playing
        }
    }

    // back to the start of what's loaded, so a share's video starts over with its song
    fun restart() = handler.post { player?.seekTo(0L) }

    fun attach(texture: SurfaceTexture) = handler.post {
        surface?.release()
        surface = Surface(texture).also { player?.setVideoSurface(it) }
    }

    // waits here, off the main thread, until the decoder lets go of the texture, then hands it back to be freed
    fun detach(texture: SurfaceTexture) = handler.post {
        surface?.let { player?.clearVideoSurface(it) }
        surface?.release()
        surface = null
        mainHandler.post { texture.release() }
    }

    fun release() = handler.post {
        player?.release()
        player = null
        surface?.release()
        surface = null
    }

    private companion object {
        val mainHandler = Handler(Looper.getMainLooper())
    }
}

// Keeps the view's texture alive until the video thread is done with it, and
// gives it to whichever session is current. A private port of Mochy's
// CanvasTexture (ui/player/CanvasArtworkPlayer.kt); see ShareLoopSession.
private class ShareLoopTexture : TextureView.SurfaceTextureListener {
    private var available: SurfaceTexture? = null

    var session: ShareLoopSession? = null
        set(value) {
            field = value
            available?.let { value?.attach(it) }
        }

    override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
        available = texture
        session?.attach(texture)
    }

    override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
        available = null
        val current = session
        if (current == null) return true
        current.detach(texture)
        // false: the texture is freed after the decoder lets go of it, not while it still draws into it
        return false
    }

    override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) = Unit

    override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit
}
