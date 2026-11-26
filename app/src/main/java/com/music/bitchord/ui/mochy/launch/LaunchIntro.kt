// LaunchIntro.kt — ported from Mochy Music (GPL-3.0): ui/launch/LaunchIntro.kt
//
// A cold start, in order:
//
//  1. The system splash shows the mint ribbon mark. On the app's first frame
//     [LaunchIntroOverlay] draws the very same mark in the very same spot
//     and the splash goes (see SplashScreenExit).
//  2. The mark holds still while the essentials come up underneath it: the
//     player connection, the theme, and Home (its logo and bones).
//     Anything slow here is invisible, nothing moves.
//  3. The mark flies into Home's logo — big in the middle, then across to
//     the left while it shrinks — crossfading from the mint splash mark
//     into the white one Home wears, with a blur through the middle, while
//     the skeleton rises in ([launchRise]).
//  4. Only then does the heavy work get the main thread ([LaunchGate.awaitIntro]).
//
// Adapted from Mochy: the flying art is ChordWave's ribbon bitmaps instead
// of Mochy's vector paths, and there is no anime-icon prelude (ChordWave
// has no alternate launcher icons). Flight, easings, rise and timings are
// Mochy's exactly (batch 38). The splash art is the launcher icon's mint
// ribbon, as the splash showed before batch 38; it crossfades into Home's
// white mark on the way in (batch 46).

package com.music.bitchord.ui.mochy.launch

import android.graphics.BitmapFactory
import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import com.music.bitchord.R
import com.music.bitchord.ui.mochy.component.BlurEffectCache
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

// the flight: still -> a gentle start -> a long settle into the logo. scale leads the move a
// little, so the big mark has shrunk before it crosses the screen
private const val FlightMillis = 660
private val MoveEasing = CubicBezierEasing(0.55f, 0f, 0.1f, 1f)
private val ScaleEasing = CubicBezierEasing(0.45f, 0f, 0.1f, 1f)

// the skeleton: each piece rises a little after the one above it
private const val RiseDelayMillis = 80
private const val RiseMillis = 520
private const val RiseStaggerMillis = 50
private const val RiseOrders = 4
private const val RiseTotalMillis = RiseMillis + (RiseOrders - 1) * RiseStaggerMillis
private val RiseEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
private val RiseDistance = 56.dp

// the essentials get this long, then the intro plays regardless. generous on purpose: Home's
// first real composition is the heaviest frame of the launch, and it has to land while the mark
// stands still, not halfway through its flight (on a debug build it can take a couple of seconds)
private const val EssentialsTimeoutMillis = 2_500L

// the splash steps aside once the first frame is up; a launch with no splash at all moves on after this
private const val SplashTimeoutMillis = 1_500L

// blur through the middle of the morph, in the splash viewport's units (it scales with the mark)
private const val BlurPeak = 8f
private const val SplashViewport = 235f

// the system splash draws its icon in a 288dp box, centred
private val SplashIconSize = 288.dp

// the splash art: the launcher icon's mint ribbon (ic_launcher_foreground,
// a square canvas) and the white in-app mark (ic_chordwave_mark), each
// with the ribbon's own bounds inside its canvas (batch 46: batch 38 drew
// the white mark for both, so the splash lost the mint ribbon the launcher
// icon -- and the pre-batch-38 splash -- showed). Batch 59: both assets
// are high-resolution now (2048px mint master, white mark regenerated at
// 2x from the same silhouette) so the start screen stays sharp and the
// ribbon's edges stay smooth on large screens; the bounds below are the
// measured alpha bounds of the new art. No black outline on either.
private const val SplashCanvas = 2048f
private const val SplashArtLeft = 403f
private const val SplashArtTop = 477f
private const val SplashArtWidth = 1230f
private const val SplashArtHeight = 1113f
private const val MarkWidth = 1420f
private const val MarkHeight = 1300f
private const val MarkArtLeft = 56f
private const val MarkArtTop = 58f
private const val MarkArtWidth = 1308f
private const val MarkArtHeight = 1188f

@Stable
class LaunchIntroState internal constructor(
    val plays: Boolean,
) {
    internal val flight = Animatable(if (plays) 0f else 1f)
    internal val rise = Animatable(if (plays) 0f else 1f)

    /** Home's logo in root coordinates: where the mark lands */
    internal var markBounds by mutableStateOf<Rect?>(null)

    /** the real Home (not its shell) has composed once */
    internal var homeComposed by mutableStateOf(false)

    /** the mark has reached the logo; the real logo shows from this frame on */
    internal var landed by mutableStateOf(!plays)

    /** everything has settled; the overlay and every launch modifier step out of the way */
    var done by mutableStateOf(!plays)
        internal set

    internal fun riseAt(order: Int): Float {
        val millis = rise.value * RiseTotalMillis - order * RiseStaggerMillis
        return RiseEasing.transform((millis / RiseMillis).coerceIn(0f, 1f))
    }
}

val LocalLaunchIntro = staticCompositionLocalOf<LaunchIntroState?> { null }

/**
 * Home's logo: the spot the launch mark lands on. It reports where it is and stays hidden until
 * the flying mark gets there, then takes over in the same frame.
 */
@Composable
fun Modifier.launchMarkTarget(): Modifier {
    val state = LocalLaunchIntro.current ?: return this
    if (state.done) return this
    return this
        .onGloballyPositioned { state.markBounds = it.boundsInRoot() }
        .graphicsLayer { alpha = if (state.landed) 1f else 0f }
}

/**
 * A piece of the skeleton that rises into place during the intro; [order] staggers it after the
 * pieces above it. Read in the draw phase only, so the rise never recomposes anything.
 */
@Composable
fun Modifier.launchRise(order: Int, distance: Dp = RiseDistance): Modifier {
    val state = LocalLaunchIntro.current ?: return this
    if (state.done) return this
    return graphicsLayer {
        val e = state.riseAt(order)
        alpha = e
        translationY = (1f - e) * distance.toPx()
        // faded per draw rather than through a buffer: some of these pieces are screen-sized
        compositingStrategy = CompositingStrategy.ModulateAlpha
    }
}

/** the real Home calls this once it has composed; the mark doesn't take off before then */
@Composable
fun ReportLaunchHomeComposed() {
    val state = LocalLaunchIntro.current ?: return
    if (state.done || state.homeComposed) return
    LaunchedEffect(state) { state.homeComposed = true }
}

/**
 * Home holds its skeleton while this is true: the first Home after a launch starts from bones,
 * so its content always arrives with its reveal instead of popping in under the intro.
 */
@Composable
fun rememberLaunchHold(): Boolean {
    var held by remember { mutableStateOf(LaunchGate.holdHome) }
    if (held) {
        LaunchedEffect(Unit) {
            // the intro always ends (its waits all time out), so this is only a backstop
            LaunchGate.awaitIntro(timeoutMillis = 15_000L)
            LaunchGate.holdHome = false
            held = false
        }
    }
    return held
}

/**
 * Sits on top of the whole app for the intro: the black of the splash, and the mark flying from
 * the splash's spot into Home's logo. [essentialsReady] is what the mark waits for before it moves.
 */
@Composable
fun LaunchIntroOverlay(state: LaunchIntroState, essentialsReady: Boolean) {
    if (state.done) return
    val ready by rememberUpdatedState(essentialsReady)
    val context = LocalContext.current
    // the splash's mint ribbon and Home's white mark (batch 46)
    // inScaled off (batch 59): the crop bounds above are in the assets' own
    // pixels, so decode at native size on every density instead of letting
    // the resource system pre-scale the bitmap out from under the crop
    val splashArt = remember {
        BitmapFactory.decodeResource(
            context.resources, R.drawable.ic_launcher_foreground,
            BitmapFactory.Options().apply { inScaled = false },
        )?.asImageBitmap()
    }
    val mark = remember {
        BitmapFactory.decodeResource(
            context.resources, R.drawable.ic_chordwave_mark,
            BitmapFactory.Options().apply { inScaled = false },
        )?.asImageBitmap()
    }

    LaunchedEffect(state) {
        val shownAt = SystemClock.uptimeMillis()
        val inTime = withTimeoutOrNull(EssentialsTimeoutMillis) {
            snapshotFlow { ready && state.homeComposed && state.markBounds != null }.first { it }
        } != null
        // the system splash still covers us until the first frame is up; flying under it would
        // spend the start of the flight where nobody can see it
        LaunchGate.awaitSplashGone(SplashTimeoutMillis)
        Timber.tag("LaunchIntro").d(
            "take-off after %dms, essentials %s (player=%b home=%b logo=%b)",
            SystemClock.uptimeMillis() - shownAt, if (inTime) "ready" else "timed out",
            ready, state.homeComposed, state.markBounds != null,
        )
        // whatever just came up draws its first (heavy) frame while the mark is still standing
        withFrameNanos { }
        withFrameNanos { }
        val flightAt = SystemClock.uptimeMillis()
        var worstFrame = 0L
        coroutineScope {
            val watch = launch {
                var last = withFrameNanos { it }
                while (true) {
                    val now = withFrameNanos { it }
                    worstFrame = maxOf(worstFrame, (now - last) / 1_000_000L)
                    last = now
                }
            }
            launch { state.rise.animateTo(1f, tween(RiseTotalMillis, RiseDelayMillis, LinearEasing)) }
            state.flight.animateTo(1f, tween(FlightMillis, easing = LinearEasing))
            state.landed = true
            watch.cancel()
        }
        Timber.tag("LaunchIntro").d(
            "landed %dms after take-off, worst frame %dms", SystemClock.uptimeMillis() - flightAt, worstFrame,
        )
        LaunchGate.finish()
        state.done = true
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // the splash's black, fading off the skeleton; it also keeps taps off the skeleton
        Spacer(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                        }
                    }
                }
                .drawBehind {
                    val backdrop = 1f - smoothstep(0.18f, 0.7f, state.flight.value)
                    if (backdrop > 0f) drawRect(Color.Black.copy(alpha = backdrop))
                }
        )

        // the mark itself, blurred through the middle of its morph
        Spacer(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val blur = BlurPeak * bell((state.flight.value - 0.04f) / 0.76f) *
                        (SplashIconSize.toPx() / SplashViewport)
                    renderEffect = BlurEffectCache.element(blur)
                }
                .drawBehind {
                    if (state.landed) return@drawBehind
                    val t = state.flight.value
                    val rect = placementAt(state, size)
                    val fade = if (state.markBounds == null) 1f - smoothstep(0.55f, 1f, t) else 1f
                    val mintAlpha = (1f - smoothstep(0.3f, 0.78f, t)) * fade
                    val whiteAlpha = smoothstep(0.12f, 0.6f, t) * fade
                    val dstOffset = IntOffset(rect.left.roundToInt(), rect.top.roundToInt())
                    val dstSize = IntSize(rect.width.roundToInt().coerceAtLeast(1), rect.height.roundToInt().coerceAtLeast(1))
                    // the mint ribbon the splash showed, cropped out of the
                    // launcher foreground's canvas
                    val splash = splashArt
                    if (splash != null && mintAlpha > 0.002f) {
                        drawImage(
                            splash,
                            srcOffset = IntOffset(SplashArtLeft.roundToInt(), SplashArtTop.roundToInt()),
                            srcSize = IntSize(SplashArtWidth.roundToInt(), SplashArtHeight.roundToInt()),
                            dstOffset = dstOffset,
                            dstSize = dstSize,
                            alpha = mintAlpha,
                        )
                    }
                    // Home's white mark, cropped the same way, taking over
                    val bitmap = mark
                    if (bitmap != null && whiteAlpha > 0.002f) {
                        drawImage(
                            bitmap,
                            srcOffset = IntOffset(MarkArtLeft.roundToInt(), MarkArtTop.roundToInt()),
                            srcSize = IntSize(MarkArtWidth.roundToInt(), MarkArtHeight.roundToInt()),
                            dstOffset = dstOffset,
                            dstSize = dstSize,
                            alpha = whiteAlpha,
                            colorFilter = ColorFilter.tint(Color.White),
                        )
                    }
                }
        )
    }
}

/** the ribbon's rect at the flight's current point, for an overlay of [size] */
private fun Density.placementAt(state: LaunchIntroState, size: Size): Rect {
    val t = state.flight.value
    val splashBox = SplashIconSize.toPx()
    // the splash fits the launcher foreground's whole square canvas into
    // its box; the ribbon sits inside that canvas at the SplashArt bounds
    val splashScale = splashBox / SplashCanvas
    val startWidth = SplashArtWidth * splashScale
    val startHeight = SplashArtHeight * splashScale
    val startCenter = Offset(
        size.width / 2f + (SplashArtLeft + SplashArtWidth / 2f - SplashCanvas / 2f) * splashScale,
        size.height / 2f + (SplashArtTop + SplashArtHeight / 2f - SplashCanvas / 2f) * splashScale,
    )

    val target = state.markBounds
    val endWidth: Float
    val endHeight: Float
    val endCenter: Offset
    if (target != null) {
        // the logo is an Icon: the mark's canvas is fitted and centred in
        // the logo's box, and the ribbon sits inside that canvas
        val logoScale = min(target.width / MarkWidth, target.height / MarkHeight)
        endWidth = MarkArtWidth * logoScale
        endHeight = MarkArtHeight * logoScale
        endCenter = Offset(
            target.center.x + (MarkArtLeft + MarkArtWidth / 2f - MarkWidth / 2f) * logoScale,
            target.center.y + (MarkArtTop + MarkArtHeight / 2f - MarkHeight / 2f) * logoScale,
        )
    } else {
        // nowhere to land (Home never laid out): settle smaller where it is and fade
        endWidth = startWidth * 0.6f
        endHeight = startHeight * 0.6f
        endCenter = startCenter
    }

    val e = ScaleEasing.transform(t)
    val width = startWidth * (endWidth / startWidth).pow(e)
    val height = startHeight * (endHeight / startHeight).pow(e)
    val center = lerp(startCenter, endCenter, MoveEasing.transform(t))
    return Rect(center.x - width / 2f, center.y - height / 2f, center.x + width / 2f, center.y + height / 2f)
}

private fun smoothstep(from: Float, to: Float, x: Float): Float {
    val t = ((x - from) / (to - from)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/** 0 at both ends, 1 in the middle, soft all the way */
private fun bell(x: Float): Float {
    if (x <= 0f || x >= 1f) return 0f
    val s = sin(PI.toFloat() * x)
    return s * s
}
