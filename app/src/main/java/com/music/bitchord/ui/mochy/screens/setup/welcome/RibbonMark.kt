// The welcome page's centerpiece (batch 22): ChordWave's own mint ribbon
// mark, alive — it breathes, sways, and floats while soft waves flow
// behind it, instead of another app's mascot character.

package com.music.bitchord.ui.mochy.screens.setup.welcome

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.music.bitchord.R
import kotlin.math.PI
import kotlin.math.sin

/** The ribbon mint, sampled from the launcher icon. */
internal val RibbonMint = Color(0xFF5CF0D2)

/**
 * The animated mint ribbon: flowing sine waves drift behind a soft glow
 * while the mark itself floats and breathes. [seconds] comes from the
 * welcome page's one clock, so it moves in step with the words and dots.
 */
@Composable
fun AnimatedRibbonMark(seconds: Float, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val center = Offset(w / 2f, h / 2f)

            // a soft mint glow breathing behind the mark
            val glowPulse = 0.5f + 0.5f * sin(seconds * 1.1f)
            drawCircle(
                color = RibbonMint.copy(alpha = 0.10f + 0.05f * glowPulse),
                radius = w * (0.30f + 0.02f * glowPulse),
                center = center,
            )
            drawCircle(
                color = RibbonMint.copy(alpha = 0.07f),
                radius = w * 0.42f,
                center = center,
            )

            // three waves flowing across behind the mark, each on its own
            // slow drift so they never line up the same way twice
            for (wave in 0..2) {
                val midY = h * (0.38f + wave * 0.12f)
                val amp = h * (0.045f + wave * 0.012f)
                val speed = 0.55f + wave * 0.22f
                val phase = seconds * speed * 2f * PI.toFloat() + wave * 1.7f
                val path = Path()
                val steps = 48
                for (i in 0..steps) {
                    val x = w * i / steps
                    val y = midY + sin(phase + x / w * 2f * PI.toFloat() * 1.4f) * amp
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(
                    path,
                    color = RibbonMint.copy(alpha = 0.34f - wave * 0.09f),
                    style = Stroke(width = (5f - wave).dp.toPx(), cap = StrokeCap.Round),
                )
            }
        }

        // the mark itself: a gentle float, a slow breath, a hint of sway
        val floatY = sin(seconds * 1.25f) * 7f
        val breath = 1f + sin(seconds * 0.9f) * 0.025f
        val sway = sin(seconds * 0.7f) * 2.2f
        Image(
            painter = painterResource(R.drawable.ic_chordwave_mark),
            contentDescription = null,
            colorFilter = ColorFilter.tint(RibbonMint),
            modifier = Modifier
                .size(168.dp)
                .graphicsLayer {
                    translationY = floatY.dp.toPx()
                    scaleX = breath
                    scaleY = breath
                    rotationZ = sway
                },
        )
    }
}
