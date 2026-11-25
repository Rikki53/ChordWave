// ProfileVisuals.kt
//
// The pieces the profile page shares: one clock, the dot grid, the giant
// slow-moving word behind the header, and the header colours. Ported from
// Mochy Music (GPL-3.0): ui/screens/recap/RecapVisuals.kt (clock, dot grid,
// giant words) and ui/screens/recap/RecapStory.kt (header colours) — only
// what the profile page draws; the recap story itself is not ported.
// Everything animates in draw or layer lambdas only, so a running page
// redraws but never recomposes.

package com.music.bitchord.ui.mochy.screens.profile

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.music.bitchord.ui.mochy.theme.InterFontFamily
import kotlinx.coroutines.flow.first
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.roundToInt

/** The header's side inset, Mochy's HomeContentInset value (36dp) kept on this page. */
internal val ProfilePageInset = 36.dp

internal val HeaderGrey = Color(0xFF1C1C1C)
internal val HeaderText = Color(0xFF272727)
internal val HeaderDots = Color(0xFF262626)

internal val ProfileEmphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)

internal fun easeOutCubic(t: Float): Float = 1f - (1f - t) * (1f - t) * (1f - t)

internal fun lerpF(a: Float, b: Float, t: Float): Float = a + (b - a) * t

// ---------------------------------------------------------------------------------------------
// Clock

/** One time base for the page. The giant word's position is a pure function of it. */
internal object ProfileClock {
    private val epoch = System.nanoTime()
    fun seconds(frameNanos: Long): Double = (frameNanos - epoch) / 1_000_000_000.0
}

@Stable
internal class ProfileTime {
    var frameNanos by mutableLongStateOf(System.nanoTime())

    val seconds: Double get() = ProfileClock.seconds(frameNanos)

    /** Seconds wrapped to a small range, for sin/cos sway where float precision matters more than continuity. */
    val sway: Float get() = (seconds % 3600.0).toFloat()
}

/**
 * Ticks every frame while [running] and not [held]. Read it only inside draw/graphicsLayer lambdas.
 * [held] is read as a lambda so flipping it never recomposes the caller.
 */
@Composable
internal fun rememberProfileTime(running: Boolean = true, held: () -> Boolean = { false }): ProfileTime {
    val time = remember { ProfileTime() }
    val isHeld by rememberUpdatedState(held)
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        while (true) {
            if (isHeld()) snapshotFlow { isHeld() }.first { !it }
            withInfiniteAnimationFrameNanos { time.frameNanos = it }
        }
    }
    return time
}

// ---------------------------------------------------------------------------------------------
// Dot grid

/** The square dot pattern behind the page header: one tiled shader, one draw. */
internal fun Modifier.profileDots(
    color: () -> Color,
    alpha: () -> Float = { 1f },
    spacing: Dp = 17.dp,
    radius: Dp = 1.3.dp,
): Modifier = drawWithCache {
    val step = spacing.toPx().roundToInt().coerceAtLeast(4)
    val tile = ImageBitmap(step, step)
    androidx.compose.ui.graphics.Canvas(tile).drawCircle(
        Offset(step / 2f, step / 2f),
        radius.toPx(),
        Paint().apply {
            this.color = Color.White
            isAntiAlias = true
        },
    )
    val brush = ShaderBrush(ImageShader(tile, TileMode.Repeated, TileMode.Repeated))
    var lastColor = Color.Unspecified
    var filter: ColorFilter? = null
    // behind the content: over the giant word (drawn before this), under any children
    onDrawBehind {
        val a = alpha()
        if (a <= 0.004f) return@onDrawBehind
        val c = color()
        if (c != lastColor) {
            lastColor = c
            filter = ColorFilter.tint(c, BlendMode.SrcIn)
        }
        drawRect(brush, alpha = a, colorFilter = filter)
    }
}

// ---------------------------------------------------------------------------------------------
// Giant word

/** The text-plane font size: one size everywhere, so a column handed between screens matches. */
internal val GiantFontSize = 178.dp

@Stable
internal class GiantRow(word: String, filled: Boolean) {
    var word by mutableStateOf(word)

    /** 0 = empty, 1 = full. Falling, it slides out to [side]; rising, it slides in from [side]. */
    val fill = Animatable(if (filled) 1f else 0f)
    var side by mutableFloatStateOf(1f)
}

/**
 * Rows of one huge word, endlessly sliding. Rotated 90° the rows read as columns (the page header
 * shows only row 0, hugging the right edge). Rows are indexed -[half]..[half] from the plane's centre.
 */
@Stable
internal class GiantTextState(word: String, val half: Int = 4, filledRows: IntRange = 0..0) {
    val rows: List<GiantRow> = (-half..half).map { GiantRow(word, it in filledRows) }
    fun row(index: Int): GiantRow = rows[index + half]

    val rotation = Animatable(90f)

    /** Plane centre as a fraction of the width. */
    val centerX = Animatable(PageColumnCenterX)

    /** Plane centre's offset from the vertical middle, in px, screen space. */
    val offsetY = Animatable(0f)
    val scale = Animatable(1f)
    val alpha = Animatable(1f)

    /** Read in draw, so a colour made of animated values follows them without recomposing. */
    var color: () -> Color = { Color(0xFF272727) }

    companion object {
        const val PageColumnCenterX = 0.86f
    }
}

/** Half-resolution bitmaps of each word, so a 178dp glyph run is a textured quad, not a path fill. */
private class GiantWordCache(private val measurer: TextMeasurer) {
    class Word(val image: ImageBitmap, val tileWidth: Float, val drawSize: IntSize, val capCenterY: Float)

    private val words = HashMap<String, Word>()

    fun get(word: String, fontPx: Float, density: Density): Word = words.getOrPut(word) {
        val renderScale = 0.5f
        val sizePx = fontPx * renderScale
        val style = TextStyle(
            fontFamily = InterFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = (sizePx / density.density / density.fontScale).sp,
            letterSpacing = (-0.03).em,
            color = Color.White,
        )
        val layout = measurer.measure(word, style, softWrap = false, maxLines = 1, overflow = TextOverflow.Visible)
        val w = layout.size.width.coerceAtLeast(1)
        val h = layout.size.height.coerceAtLeast(1)
        val image = ImageBitmap(w, h)
        CanvasDrawScope().draw(density, LayoutDirection.Ltr, androidx.compose.ui.graphics.Canvas(image), Size(w.toFloat(), h.toFloat())) {
            drawText(layout)
        }
        // Inter's cap height is ~0.727 em: centre the capitals, not the line box, on the row
        val capCenter = layout.firstBaseline - 0.727f * sizePx / 2f
        Word(
            image = image,
            tileWidth = (w + sizePx * 0.22f) / renderScale,
            drawSize = IntSize((w / renderScale).roundToInt(), (h / renderScale).roundToInt()),
            capCenterY = capCenter / renderScale,
        )
    }
}

/**
 * Draws a [GiantTextState] across the whole of its bounds. [centerY] puts the plane's centre
 * somewhere other than the middle - the page header pins it to the middle of the *screen*.
 */
internal fun Modifier.giantText(
    state: GiantTextState,
    time: ProfileTime,
    measurer: TextMeasurer,
    centerY: (DrawScope.() -> Float)? = null,
): Modifier = drawWithCache {
    val cache = GiantWordCache(measurer)
    val fontPx = GiantFontSize.toPx()
    val capHeight = 0.727f * fontPx
    val pitch = capHeight * 1.16f
    val baseSpeed = 22.dp.toPx()
    var lastColor = Color.Unspecified
    var filter: ColorFilter? = null
    onDrawBehind {
        val planeAlpha = state.alpha.value
        if (planeAlpha <= 0.003f) return@onDrawBehind
        val color = state.color()
        if (color != lastColor) {
            lastColor = color
            filter = ColorFilter.tint(color, BlendMode.SrcIn)
        }
        val span = hypot(size.width, size.height) * 1.1f / state.scale.value.coerceAtLeast(0.2f)
        val seconds = time.seconds
        withTransform({
            translate(state.centerX.value * size.width, (centerY?.invoke(this@onDrawBehind) ?: (size.height / 2f)) + state.offsetY.value)
            rotate(state.rotation.value, pivot = Offset.Zero)
            scale(state.scale.value, state.scale.value, pivot = Offset.Zero)
        }) {
            state.rows.forEachIndexed { i, row ->
                val fill = row.fill.value
                if (fill <= 0.002f) return@forEachIndexed
                val index = i - state.half
                val word = cache.get(row.word, fontPx, this)
                val direction = if (index % 2 == 0) -1.0 else 1.0
                val speed = baseSpeed * (1.0 + 0.18 * ((index + 8) % 3))
                val phase = ((seconds * speed) % word.tileWidth) * direction
                val slide = (1f - easeOutCubic(fill)) * span * row.side
                val base = phase.toFloat() + slide
                val tiles = ceil((base + span / 2f) / word.tileWidth)
                var x = base - tiles * word.tileWidth
                val top = index * pitch - word.capCenterY
                while (x < span / 2f) {
                    drawImage(
                        image = word.image,
                        dstOffset = IntOffset(x.roundToInt(), top.roundToInt()),
                        dstSize = word.drawSize,
                        alpha = planeAlpha * fill,
                        colorFilter = filter,
                    )
                    x += word.tileWidth
                }
            }
        }
    }
}
