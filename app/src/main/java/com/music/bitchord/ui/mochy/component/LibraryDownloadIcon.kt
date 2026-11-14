// Ported from Mochy Music (GPL-3.0): ui/component/LibraryDownloadIcon.kt
// Adapted for ChordWave: the batch is rebuilt from Downloads.active (ChordWave has no
// download util), and covers are artwork-less translucent discs (see NEEDS.md).

package com.music.bitchord.ui.mochy.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.music.bitchord.R
import com.music.bitchord.download.DownloadState
import com.music.bitchord.download.Downloads
import com.music.bitchord.ui.mochy.utils.resize
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** the download badge: its ring, and the cover disc inside it */
private val BadgeSize = 26.dp
private val CoverSize = 19.dp
private val RingWidth = 2.dp

/** how many covers show in the pile; older ones are already inside */
private const val PileDepth = 3

/** how far each cover lower in the pile peeks out, up and to the right */
private val PileStep = 4.dp

/** where a cover starts its drop, above the badge */
private val DropHeight = 24.dp

private val PopIn = spring<Float>(dampingRatio = 0.45f, stiffness = 420f)
private val DropIn = spring<Float>(dampingRatio = 0.5f, stiffness = 300f)
private val Settle = spring<Float>(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow)

/** one cover dropped into the badge */
private class DroppedCover(val key: String, val url: String?) {
    /** 0 above the badge, 1 landed in it; the spring overshoots, so it bounces */
    val drop = Animatable(0f)
    /** its place in the pile: 0 on top, counting down */
    val depth = Animatable(0f)
}

/** one download in the batch: its id, and the cover URL — always null, because CW's download
 *  state carries no artwork, so the pile renders as translucent discs (see [PileCover]). */
private data class BatchItem(val key: String, val artworkUrl: String?)

/** the batch the badge reads: whether anything is downloading, its overall progress, and the
 *  items in it. Mochy got this from its download util; here it is rebuilt from CW's
 *  [Downloads.active] (see [batchFrom]). */
private data class DownloadBatch(
    val isRunning: Boolean,
    val fraction: Float,
    val items: List<BatchItem>,
)

/**
 * the batch from CW's active downloads: running while any entry is queued, waiting for wifi, or
 * running; the ring's progress is the mean of the running fractions (0 while nothing has
 * started yet); the pile's covers are the active ids, artwork-less.
 */
private fun batchFrom(active: Map<String, DownloadState>): DownloadBatch {
    val running = active.values.filterIsInstance<DownloadState.Running>()
    return DownloadBatch(
        isRunning = active.values.any {
            it is DownloadState.Queued || it is DownloadState.WaitingForWifi || it is DownloadState.Running
        },
        fraction = if (running.isEmpty()) 0f else running.map { it.fraction }.average().toFloat(),
        items = active.keys.map { BatchItem(it, null) },
    )
}

/**
 * The Library tab's icon, which turns into a download badge while songs download. The icon shrinks
 * away and a circled download badge pops out in its place; each song's cover drops into the badge
 * with a bounce, and a bulk download piles them up, the older covers peeking out behind. The
 * badge's outline fills as the batch goes. Once everything is done it pops back into the icon.
 *
 * Adapted: the batch comes from CW's Downloads.active instead of Mochy's download util, and the
 * covers are artwork-less translucent discs, because CW download state carries no artwork — the
 * drop, pile, ring and pop animations are all unchanged. See NEEDS.md.
 */
@Composable
fun LibraryDownloadIcon(icon: @Composable () -> Unit) {
    // ChordWave has no download util: the batch is rebuilt from Downloads.active (see batchFrom).
    val activeState = Downloads.active.collectAsState()
    val batchState = remember {
        derivedStateOf { batchFrom(activeState.value) }
    }
    val downloading by remember { derivedStateOf { batchState.value.isRunning } }
    // between the half-second updates the ring glides instead of stepping
    val progress by animateFloatAsState(
        targetValue = if (downloading) batchState.value.fraction else 1f,
        animationSpec = tween(450),
        label = "libraryDownloadProgress",
    )

    val iconScale = remember { Animatable(1f) }
    val badgeScale = remember { Animatable(0f) }
    val covers = remember { mutableStateListOf<DroppedCover>() }

    LaunchedEffect(downloading) {
        if (downloading) {
            // the icon shrinks away first, then the badge pops out where it was
            iconScale.animateTo(0f, tween(150, easing = FastOutLinearInEasing))
            badgeScale.animateTo(1f, PopIn)
        } else if (badgeScale.value > 0f) {
            // let the full ring show for a moment, then a little swell and back to the icon
            delay(500)
            badgeScale.animateTo(1.12f, tween(110))
            badgeScale.animateTo(0f, tween(140, easing = FastOutLinearInEasing))
            covers.clear()
            iconScale.animateTo(1f, PopIn)
        }
    }

    // each song that joins the batch drops its cover in; a batch of many drops a few, one after another
    LaunchedEffect(Unit) {
        val seen = HashSet<String>()
        snapshotFlow { batchState.value }.collect { batch ->
            if (!batch.isRunning) {
                seen.clear()
                return@collect
            }
            val fresh = batch.items.filter { seen.add(it.key) }.takeLast(PileDepth)
            fresh.forEachIndexed { i, item ->
                val cover = DroppedCover(item.key, item.artworkUrl)
                covers += cover
                if (covers.size > PileDepth + 1) covers.removeAt(0)
                // the ones already there sink one place down the pile
                covers.forEachIndexed { index, c -> launch { c.depth.animateTo((covers.lastIndex - index).toFloat(), Settle) } }
                launch {
                    // wait for the badge before the first drop, then a short beat between covers
                    delay(if (badgeScale.value < 0.5f) 220L + 90L * i else 90L * i)
                    cover.drop.animateTo(1f, DropIn)
                }
            }
        }
    }

    Box(contentAlignment = Alignment.Center) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.graphicsLayer {
                scaleX = iconScale.value
                scaleY = iconScale.value
            },
        ) { icon() }

        val badgeShown by remember { derivedStateOf { badgeScale.value > 0.001f } }
        if (badgeShown) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(BadgeSize)
                    .graphicsLayer {
                        scaleX = badgeScale.value
                        scaleY = badgeScale.value
                    },
            ) {
                // drawn bottom of the pile first, so the newest cover lands on top
                covers.forEach { cover ->
                    key(cover.key) { PileCover(cover) }
                }
                // a light shade over the covers keeps the arrow readable on any artwork
                Box(
                    modifier = Modifier
                        .size(CoverSize)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.3f)),
                )
                Icon(
                    painter = painterResource(R.drawable.arrow_downward),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(12.dp),
                )
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val stroke = RingWidth.toPx()
                    val inset = stroke / 2f
                    val arcSize = Size(size.width - stroke, size.height - stroke)
                    drawCircle(
                        color = Color.White.copy(alpha = 0.32f),
                        radius = size.minDimension / 2f - inset,
                        style = Stroke(stroke),
                    )
                    drawArc(
                        color = Color.White,
                        startAngle = -90f,
                        sweepAngle = 360f * progress.coerceIn(0f, 1f),
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = arcSize,
                        style = Stroke(stroke, cap = StrokeCap.Round),
                    )
                }
            }
        }
    }
}

/** one cover in the pile: dropping in from above, or resting at its place in the pile */
@Composable
private fun PileCover(cover: DroppedCover) {
    val modifier = Modifier
        .size(CoverSize)
        .graphicsLayer {
            val p = cover.drop.value
            val d = cover.depth.value
            // deeper in the pile: smaller, dimmer and nudged up and right so its edge peeks out
            val pile = 1f - 0.1f * d
            val dropScale = 1.25f + (1f - 1.25f) * p
            scaleX = pile * dropScale
            scaleY = pile * dropScale
            translationX = d * PileStep.toPx()
            translationY = -d * PileStep.toPx() - (1f - p) * DropHeight.toPx()
            rotationZ = (1f - p) * -12f
            alpha = (p * 3f).coerceIn(0f, 1f) * (1f - 0.3f * d).coerceIn(0f, 1f)
        }
        .clip(CircleShape)
    if (cover.url == null) {
        Box(modifier.background(Color.White.copy(alpha = 0.18f)))
    } else {
        AsyncImage(
            model = cover.url.resize(96, 96),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier,
        )
    }
}
