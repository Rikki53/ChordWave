// Ported from Mochy Music (GPL-3.0): ui/player/CanvasArtworkPlayer.kt —
// adapted: the decoder is CW's own sharedUi CanvasArtworkPlayer (ExoPlayer
// via AndroidCanvasArtworkPlayer), not Mochy's CanvasVideoSession; the
// frame colours come from its frame capture + ambientTones (the same four
// tones Mochy's VideoFrameSampler fed the flagship backdrop). Quality comes
// from AppSettings.canvasBestQuality at decoder creation (see
// newCanvasDecoder); there is no live quality switch on this path.
// onPlayingSize (Mochy's menu size signal) is NOT ported: CW has no
// PlayerCanvasPlayback, and no caller passes it.
package com.music.bitchord.ui.mochy.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import com.music.bitchord.data.canvas.CanvasArtwork
import com.music.bitchord.ui.mochy.player.flagship.ambientTones
import com.music.bitchord.ui.player.CanvasArtworkPlayer as SharedCanvasArtworkPlayer
import androidx.media3.common.MimeTypes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Locale

// the frame capture's longest edge, and how often a playing clip's colours are re-read
private const val CanvasSamplePx = 64
private const val CanvasFrameRefreshMs = 2000L

@Composable
fun CanvasArtworkPlayer(
    primaryUrl: String?,
    fallbackUrl: String?,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    // told when the first frame is up and when it's gone again, so a mirror of it knows when there's something to show
    onReadyChange: ((Boolean) -> Unit)? = null,
    // the flagship's moving backdrop, fed four tones from a tiny copy of the playing frame
    onFrameColors: ((List<Color>) -> Unit)? = null,
    // shows the whole picture with room round it instead of zooming past the box, for a module's cover that isn't the video's shape
    fit: Boolean = false,
    // a spinner on a dark stand-in while the clip has no frame yet, for places where
    // the bare fade-in would leave a blank light card that reads as an error (batch 74)
    showLoadingIndicator: Boolean = false,
) {
    val primary = primaryUrl?.takeIf { it.isNotBlank() } ?: return
    val canvas = remember(primary, fallbackUrl) {
        CanvasArtwork(url = primary, fallbackUrl = fallbackUrl?.takeIf { it.isNotBlank() })
    }
    var isVideoReady by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(
        targetValue = if (isVideoReady) 1f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "canvasAlpha"
    )
    val loadingAlpha by animateFloatAsState(
        targetValue = if (isVideoReady) 0f else 1f,
        animationSpec = tween(durationMillis = 300),
        label = "canvasLoading",
    )
    val currentOnReady by rememberUpdatedState(onReadyChange)
    val currentOnFrameColors by rememberUpdatedState(onFrameColors)
    val scope = rememberCoroutineScope()
    Box(modifier) {
        SharedCanvasArtworkPlayer(
            canvas = canvas,
            isPlaying = isPlaying,
            contentMode = if (fit) com.music.bitchord.ui.player.CanvasContentMode.FIT_PORTRAIT else com.music.bitchord.ui.player.CanvasContentMode.CROP,
            modifier = Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha },
        onRenderedChanged = { isVideoReady = it; currentOnReady?.invoke(it) },
        onFrameCaptured = { frame ->
            if (currentOnFrameColors == null) return@SharedCanvasArtworkPlayer
            scope.launch(Dispatchers.Default) {
                val bitmap = frame.asAndroidBitmap()
                val w = bitmap.width
                val h = bitmap.height
                if (w <= 0 || h <= 0) return@launch
                val pixels = IntArray(w * h)
                bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
                currentOnFrameColors?.invoke(ambientTones(pixels, w, h))
            }
        },
            refreshFrameEveryMs = if (onFrameColors != null) CanvasFrameRefreshMs else null,
            frameCapturePx = if (onFrameColors != null) CanvasSamplePx else 0,
        )
        if (showLoadingIndicator && loadingAlpha > 0f) {
            Box(
                Modifier.fillMaxSize().graphicsLayer { this.alpha = loadingAlpha }.background(Color(0xFF141416)),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    color = Color.White.copy(alpha = 0.7f),
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
    }
}

// what a canvas loop's link actually is, for the share export's media item
internal fun canvasMimeType(url: String, isFallback: Boolean = false): String {
    val lower = url.lowercase(Locale.ROOT)
    return when {
        lower.contains(".m3u8") || lower.split('?').first().endsWith(".m3u8") -> MimeTypes.APPLICATION_M3U8
        lower.contains(".mp4") -> MimeTypes.VIDEO_MP4
        // a module's own loop can be webm, which isn't a playlist either
        lower.contains(".webm") -> MimeTypes.VIDEO_WEBM
        isFallback -> MimeTypes.VIDEO_MP4
        else -> MimeTypes.APPLICATION_M3U8
    }
}
