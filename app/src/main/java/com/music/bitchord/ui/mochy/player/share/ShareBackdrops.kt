// Ported from Mochy Music (GPL-3.0): ui/player/share/ShareBackdrops.kt
// ShareBackdrops.kt
//
// What sits behind the words on a lyrics share picture: soft clouds made from the cover, your own
// picture, or a plain colour - and which ink reads on each.

package com.music.bitchord.ui.mochy.player.share

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.transformations
import com.music.bitchord.ui.mochy.utils.SoftBlurTransformation
import com.music.bitchord.ui.mochy.utils.resize
import kotlin.math.cos
import kotlin.math.sin

// how soft a background can go, in the share picture's 360-wide units (batch 36)
internal const val MaxBackgroundBlurUnits = 20f

// blurs whatever it wraps by [blur] (0..1) of [MaxBackgroundBlurUnits];
// phones without render effects (before Android 12) just show it sharp
internal fun Modifier.shareBackgroundBlur(blur: Float): Modifier = graphicsLayer {
    renderEffect = if (blur > 0f && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val radius = blur * MaxBackgroundBlurUnits * (size.width / ShareWidth.value)
        RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP).asComposeRenderEffect()
    } else {
        null
    }
}

/** White on dark backgrounds, near-black on light plain colours. */
internal fun contentColorOn(background: ShareBackground): Color = when (background) {
    is ShareBackground.Solid -> if (background.color.luminance() > 0.55f) Color(0xFF111111) else Color.White
    else -> Color.White
}

/** Any background: the background pager shows each choice with this. */
@Composable
internal fun BackdropOf(
    background: ShareBackground,
    thumbnailUrl: String?,
    palette: List<Color>,
    animate: Boolean,
    modifier: Modifier = Modifier,
) {
    when (background) {
        is ShareBackground.Solid -> Box(modifier.fillMaxSize().background(background.color))
        is ShareBackground.Picture -> PictureBackdrop(background, modifier)
        ShareBackground.FromMusic -> MusicBackdrop(thumbnailUrl, palette, animate, modifier)
    }
}

@Composable
private fun PictureBackdrop(bg: ShareBackground.Picture, modifier: Modifier) {
    val context = LocalContext.current
    val request = remember(bg.uri) {
        ImageRequest.Builder(context)
            .data(bg.uri)
            .size(1440)
            .build()
    }
    Box(modifier.fillMaxSize().background(Color.Black)) {
        AsyncImage(
            model = request,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = bg.opacity }
                .shareBackgroundBlur(bg.blur),
        )
    }
}

/**
 * Made from the song: its cover blurred right down, with soft clouds of the cover's own colours
 * drifting over it. The drift only runs while [animate] is on (the editors), the saved picture
 * keeps whatever moment it was shared at.
 */
@Composable
internal fun MusicBackdrop(thumbnailUrl: String?, palette: List<Color>, animate: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val cover = remember(thumbnailUrl) {
        ImageRequest.Builder(context)
            .data(thumbnailUrl?.resize(544, 544))
            .size(192)
            .transformations(SoftBlurTransformation())
            .build()
    }
    val drift = if (animate) {
        rememberInfiniteTransition(label = "shareDrift").animateFloat(
            initialValue = 0f,
            targetValue = (2 * Math.PI).toFloat(),
            animationSpec = infiniteRepeatable(tween(22_000, easing = LinearEasing), RepeatMode.Restart),
            label = "shareDriftPhase",
        )
    } else {
        null
    }
    val base = palette.firstOrNull() ?: Color(0xFF2A2A2E)
    Box(modifier.fillMaxSize().background(base)) {
        AsyncImage(
            model = cover,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = 0.75f },
        )
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind { drawMusicClouds(palette, drift?.value ?: MusicCloudsRestPhase) },
        )
    }
}

// where the clouds sit when nothing makes them drift
internal const val MusicCloudsRestPhase = 0.6f

// the cover's colours as soft clouds, placed by phase (radians, one lap is 2 pi)
internal fun DrawScope.drawMusicClouds(palette: List<Color>, phase: Float) {
    palette.take(5).forEachIndexed { i, color ->
        val angle = phase * (0.7f + i * 0.19f) + i * 1.3f
        val center = Offset(
            size.width * (0.3f + 0.2f * (i % 3)) + size.width * 0.22f * cos(angle),
            size.height * (0.2f + 0.16f * (i % 4)) + size.height * 0.12f * sin(angle),
        )
        val radius = size.minDimension * (0.7f + 0.1f * (i % 2))
        drawCircle(
            Brush.radialGradient(listOf(color.copy(alpha = 0.6f), color.copy(alpha = 0f)), center, radius),
            radius,
            center,
        )
    }
    // a touch darker toward the bottom, where the words usually end
    drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.08f), Color.Black.copy(alpha = 0.38f))))
}
