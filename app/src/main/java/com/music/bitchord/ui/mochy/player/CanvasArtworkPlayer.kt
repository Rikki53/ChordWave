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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import com.music.bitchord.data.canvas.CanvasArtwork
import com.music.bitchord.ui.mochy.player.flagship.ambientTones
import com.music.bitchord.ui.player.CanvasArtworkPlayer as SharedCanvasArtworkPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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
    val currentOnReady by rememberUpdatedState(onReadyChange)
    val currentOnFrameColors by rememberUpdatedState(onFrameColors)
    val scope = rememberCoroutineScope()
    SharedCanvasArtworkPlayer(
        canvas = canvas,
        isPlaying = isPlaying,
        modifier = modifier.graphicsLayer { this.alpha = alpha },
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
}
