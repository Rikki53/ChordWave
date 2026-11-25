package com.music.bitchord.playback

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.IBinder
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.music.bitchord.MainActivity
import com.music.bitchord.data.lyrics.EmbeddedLyrics
import com.music.bitchord.data.lyrics.LyricLine
import com.music.bitchord.data.lyrics.LyricsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.math.abs

/**
 * The floating-lyrics bubble (batch 32): the line being sung, in a small
 * window over other apps.
 *
 * A plain started service — no notification of its own. It follows playback
 * through a [MediaController] on [PlaybackService]'s session, fetches the
 * track's lyrics from the same [LyricsRepository] the player uses, and shows
 * the bubble only while something with synced lyrics is actually playing.
 * Started by the settings toggle and by [PlaybackService] when playback
 * starts with the toggle on; stopped by the toggle. Nothing here runs at all
 * until the listener asks for it and grants the overlay permission.
 */
class FloatingLyricsService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null

    private var windowManager: WindowManager? = null
    private var bubble: FrameLayout? = null
    private var bubbleText: TextView? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var bubbleAttached = false

    private var lines: List<LyricLine> = emptyList()
    private var fetchJob: Job? = null
    private var pollJob: Job? = null
    private var shownText: String? = null

    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            fetchLyricsFor(mediaItem)
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) startPolling() else {
                pollJob?.cancel()
                hideBubble()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        if (!Settings.canDrawOverlays(this)) {
            // The permission was revoked while we were away; there is
            // nothing this service may do.
            stopSelf()
            return
        }
        windowManager = getSystemService(WindowManager::class.java)
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(this, token).buildAsync().also { future ->
            future.addListener(
                {
                    val connected = runCatching { future.get() }.getOrNull() ?: return@addListener
                    controller = connected
                    connected.addListener(listener)
                    fetchLyricsFor(connected.currentMediaItem)
                    if (connected.isPlaying) startPolling()
                },
                MoreExecutors.directExecutor(),
            )
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        pollJob?.cancel()
        fetchJob?.cancel()
        hideBubble()
        controller?.removeListener(listener)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controller = null
        scope.cancel()
        super.onDestroy()
    }

    // ------------------------------------------------------------ lyrics --

    private fun fetchLyricsFor(item: MediaItem?) {
        fetchJob?.cancel()
        lines = emptyList()
        shownText = null
        hideBubble()
        val videoId = item?.mediaId?.takeIf { it.isNotBlank() } ?: return
        val title = item.mediaMetadata.title?.toString().orEmpty()
        val artist = item.mediaMetadata.artist?.toString().orEmpty()
        fetchJob = scope.launch {
            val durationMs = controller?.duration?.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0L
            val result = runCatching {
                LyricsRepository.lyrics(
                    videoId = videoId,
                    title = title,
                    artist = artist,
                    durationMs = durationMs,
                )
            }.onFailure { Timber.w(it, "Floating lyrics fetch failed") }.getOrNull()
            val fetched = result?.lines.orEmpty()
            // Only timed lyrics can follow the song; an untimed copy would
            // sit on its first line forever, so the bubble stays away.
            if (EmbeddedLyrics.isSynced(fetched)) {
                lines = fetched
                if (controller?.isPlaying == true) startPolling()
            }
        }
    }

    private fun startPolling() {
        if (pollJob?.isActive == true) return
        pollJob = scope.launch {
            while (isActive) {
                val player = controller ?: break
                if (!player.isPlaying) break
                updateBubble(player.currentPosition)
                delay(POLL_MS)
            }
        }
    }

    private fun updateBubble(positionMs: Long) {
        if (lines.isEmpty()) return
        // The last sung line at or before now; gap lines (empty text) hold
        // the previous line rather than blanking the bubble between verses.
        val line = lines.lastOrNull { it.timeMs <= positionMs && it.text.isNotBlank() } ?: return
        if (line.text == shownText) return
        shownText = line.text
        showBubble(line.text)
    }

    // ------------------------------------------------------------ bubble --

    private fun showBubble(text: String) {
        if (!Settings.canDrawOverlays(this)) return
        val view = bubble ?: buildBubble().also { bubble = it }
        bubbleText?.text = text
        if (!bubbleAttached) {
            runCatching { windowManager?.addView(view, bubbleParams) }
                .onSuccess { bubbleAttached = true }
                .onFailure { Timber.w(it, "Floating lyrics bubble could not be shown") }
        }
    }

    private fun hideBubble() {
        val view = bubble ?: return
        if (bubbleAttached) {
            runCatching { windowManager?.removeView(view) }
            bubbleAttached = false
        }
    }

    private fun buildBubble(): FrameLayout {
        val density = resources.displayMetrics.density
        fun dp(value: Int): Int = (value * density).toInt()

        val label = TextView(this).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(10), dp(16), dp(10))
        }
        bubbleText = label

        val root = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                cornerRadius = dp(22).toFloat()
                setColor(Color.argb(235, 24, 24, 28))
            }
            addView(
                label,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dp(96)
            width = dp(320)
        }
        bubbleParams = params

        // Drag to move, tap to open ChordWave, hold for play/pause.
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var moved = false
        var downAt = 0L
        root.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startX = params.x
                    startY = params.y
                    moved = false
                    downAt = System.currentTimeMillis()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (abs(dx) > dp(6) || abs(dy) > dp(6)) moved = true
                    if (moved) {
                        params.x = startX + dx.toInt()
                        params.y = startY + dy.toInt()
                        if (bubbleAttached) {
                            runCatching { windowManager?.updateViewLayout(root, params) }
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) {
                        if (System.currentTimeMillis() - downAt >= LONG_PRESS_MS) {
                            controller?.let { if (it.isPlaying) it.pause() else it.play() }
                        } else {
                            startActivity(
                                Intent(this, MainActivity::class.java)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }
                    }
                    true
                }
                else -> false
            }
        }
        return root
    }

    companion object {
        private const val POLL_MS = 250L
        private const val LONG_PRESS_MS = 450L

        fun start(context: Context) {
            runCatching { context.startService(Intent(context, FloatingLyricsService::class.java)) }
                .onFailure { Timber.w(it, "Floating lyrics service could not start") }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, FloatingLyricsService::class.java)) }
        }
    }
}
