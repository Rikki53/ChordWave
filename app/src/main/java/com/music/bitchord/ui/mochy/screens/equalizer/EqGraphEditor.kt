// the equalizer's graph tab: the live engine curve, drawn from the ten
// EqLayout slots, with the manual bands as draggable nodes
//
// Ported from Mochy Music (GPL-3.0): ui/screens/equalizer/EqGraphEditor.kt
// (Plot / curvePath / drawGrid / the canvas gesture code, near-verbatim).
//
// CHORDWAVE REWIRING — the engine is a fixed 10-slot layout, not Mochy's
// free parametric nodes, so the editor adapts instead of pretending:
// - Nodes sit at the 7 MANUAL_BANDS_HZ centres and drag VERTICALLY ONLY
//   (gain); frequency, filter type and Q are fixed by EqLayout. No
//   double-tap to add, no remove, no per-node enable switch.
// - The curve drawn is the LIVE engine curve (EqState.nodes through the
//   ported ResponseCurve), so in DYNAMIC mode it shows the tone pad's
//   curve. There the nodes are the 3 tone slots, shown but not draggable,
//   with a note and a switch to Manual. (Porting the tone pad itself into
//   Modern is separate work — see NEEDS.md.)
// - The live spectrum is gone: ChordWave has no AudioTap (same call as
//   5F's LabSpectrum drop).
package com.music.bitchord.ui.mochy.screens.equalizer

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.bitchord.R
import com.music.bitchord.data.settings.EqualizerMode
import com.music.bitchord.playback.EqLayout
import com.music.bitchord.playback.FilterKind
import com.music.bitchord.ui.mochy.component.damageBoundary
import com.music.bitchord.ui.mochy.eq.EqNode
import com.music.bitchord.ui.mochy.eq.audio.ResponseCurve
import com.music.bitchord.ui.mochy.eq.data.FilterType
import com.music.bitchord.ui.mochy.eq.data.ParametricEQBand
import com.music.bitchord.ui.mochy.eq.filterTypeLabel
import com.music.bitchord.ui.mochy.eq.frequencyLabel
import com.music.bitchord.ui.mochy.eq.gainLabel
import com.music.bitchord.ui.mochy.screens.audiolab.LabCard
import com.music.bitchord.ui.mochy.screens.audiolab.LabChip
import com.music.bitchord.ui.mochy.screens.audiolab.LabChips
import com.music.bitchord.ui.mochy.screens.audiolab.LabColors
import com.music.bitchord.ui.mochy.screens.audiolab.LabSectionLabel
import com.music.bitchord.ui.mochy.screens.audiolab.LabSlider
import com.music.bitchord.ui.mochy.theme.InterFontFamily
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

internal const val GraphRangeDb = 12.0
private const val MinLog = 1.30103 // log10(20)
private const val MaxLog = 4.30103 // log10(20000)
internal val GraphFrequencies: DoubleArray = ResponseCurve.logSpaced(220)
private val GridFrequencies = listOf(20.0, 50.0, 100.0, 200.0, 500.0, 1000.0, 2000.0, 5000.0, 10_000.0, 20_000.0)
private val GridLabels = listOf("20", "50", "100", "200", "500", "1k", "2k", "5k", "10k", "20k")

/** where things sit inside the plot */
internal class Plot(val left: Float, val top: Float, val right: Float, val bottom: Float, val rangeDb: Double = GraphRangeDb) {
    fun x(f: Double): Float = left + ((log10(f) - MinLog) / (MaxLog - MinLog)).toFloat() * (right - left)
    fun frequencyAt(x: Float): Double = 10.0.pow(MinLog + ((x - left) / (right - left)).coerceIn(0f, 1f) * (MaxLog - MinLog))
    fun y(db: Double): Float = top + ((rangeDb - db) / (2 * rangeDb)).toFloat() * (bottom - top)
    fun gainAt(y: Float): Double = rangeDb - ((y - top) / (bottom - top)).coerceIn(0f, 1f) * 2 * rangeDb
}

/** one dot on the graph: a manual band (draggable) or a tone slot (shown, fixed) */
private class DisplayNode(val frequency: Double, val gain: Double, val colorIndex: Int, val band: Int)

@Composable
fun EqGraphSection(
    nodes: List<EqNode>,
    bands: List<Float>,
    mode: EqualizerMode,
    toneX: Int,
    toneY: Int,
    toneFocused: Boolean,
    selected: Int,
    onSelect: (Int) -> Unit,
    onGain: (index: Int, gain: Double) -> Unit,
    onSwitchToManual: () -> Unit,
) {
    val manual = mode == EqualizerMode.MANUAL
    val index = selected.coerceIn(0, (EqLayout.MANUAL_COUNT - 1).coerceAtLeast(0))
    LabSectionLabel(stringResource(R.string.mochy_eq_graph_editor))
    LabCard(padding = 12.dp) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.mochy_eq_freq_response),
                fontFamily = InterFontFamily, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.2.sp,
                color = LabColors.Secondary, modifier = Modifier.weight(1f),
            )
            if (manual) {
                val gain = bands.getOrElse(index) { 0f }.toDouble()
                Text(
                    "${frequencyLabel(EqLayout.MANUAL_BANDS_HZ[index].toDouble())} ${gainLabel(gain)}",
                    fontFamily = InterFontFamily, fontWeight = FontWeight.Bold, fontSize = 12.sp,
                    color = LabColors.node(index),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        EqGraph(
            nodes = nodes,
            bands = bands,
            mode = mode,
            toneFocused = toneFocused,
            selected = index,
            onSelect = onSelect,
            onGain = onGain,
        )
        Spacer(Modifier.height(10.dp))
        if (manual) {
            Text(
                stringResource(R.string.mochy_eq_graph_hint_manual),
                fontFamily = InterFontFamily, fontSize = 12.sp, color = LabColors.Faint,
                modifier = Modifier.padding(horizontal = 6.dp),
            )
        } else {
            val focus = stringResource(if (toneFocused) R.string.equalizer_focused else R.string.equalizer_broad)
            Text(
                stringResource(R.string.mochy_eq_tone_note, signedSteps(toneX), signedSteps(toneY), focus),
                fontFamily = InterFontFamily, fontSize = 12.sp, color = LabColors.Faint,
                modifier = Modifier.padding(horizontal = 6.dp),
            )
        }
    }
    Spacer(Modifier.height(12.dp))

    if (manual) {
        LabChips(
            chips = (0 until EqLayout.MANUAL_COUNT).map {
                LabChip(it.toString(), frequencyLabel(EqLayout.MANUAL_BANDS_HZ[it].toDouble()), LabColors.node(it))
            },
            selectedKey = index.toString(),
            onSelect = { onSelect(it.toInt()) },
        )
        Spacer(Modifier.height(12.dp))
        NodeDetail(
            band = index,
            gain = bands.getOrElse(index) { 0f }.toDouble(),
            color = LabColors.node(index),
            onGain = { onGain(index, it) },
            onReset = { onGain(index, 0.0) },
        )
    } else {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(LabColors.Row)
                .clickable(onClick = onSwitchToManual),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                stringResource(R.string.mochy_eq_switch_manual),
                fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                color = Color.White,
            )
        }
    }
}

private fun signedSteps(v: Int): String = if (v > 0) "+$v" else if (v < 0) "−${-v}" else "0"

/** the ten engine slots as response bands, so the graph draws the live curve in either mode */
private fun slotBands(nodes: List<EqNode>, toneFocused: Boolean): List<ParametricEQBand> =
    nodes.mapIndexedNotNull { i, node ->
        val slot = EqLayout.slots.getOrNull(i) ?: return@mapIndexedNotNull null
        val type = when (slot.kind) {
            FilterKind.LOW_SHELF -> FilterType.LSC
            FilterKind.HIGH_SHELF -> FilterType.HSC
            FilterKind.BELL -> FilterType.PK
        }
        val q = when {
            i < EqLayout.MANUAL_COUNT -> EqLayout.MANUAL_Q.toDouble()
            slot.kind == FilterKind.BELL -> if (toneFocused) 2.2 else 0.7
            else -> if (toneFocused) 0.9 else 0.5
        }
        ParametricEQBand(frequency = slot.frequencyHz.toDouble(), gain = node.gain, q = q, filterType = type)
    }

@Composable
private fun EqGraph(
    nodes: List<EqNode>,
    bands: List<Float>,
    mode: EqualizerMode,
    toneFocused: Boolean,
    selected: Int,
    onSelect: (Int) -> Unit,
    onGain: (index: Int, gain: Double) -> Unit,
) {
    val manual = mode == EqualizerMode.MANUAL
    val haptic = LocalHapticFeedback.current
    val measurer = rememberTextMeasurer(cacheSize = 32)
    val curve = remember { ResponseCurve(GraphFrequencies) }
    val slotBands = remember(nodes, toneFocused) { slotBands(nodes, toneFocused) }
    val perNode = remember(slotBands) {
        slotBands.map { band -> DoubleArray(GraphFrequencies.size).also { curve.accumulate(band, it) } }
    }
    val total = remember(perNode) {
        DoubleArray(GraphFrequencies.size).also { sum -> perNode.forEach { r -> for (i in sum.indices) sum[i] += r[i] } }
    }
    // the dots: the 7 manual bands when they drive the sound, the 3 tone slots otherwise
    val display = remember(bands, nodes, manual) {
        if (manual) {
            (0 until EqLayout.MANUAL_COUNT).map {
                DisplayNode(EqLayout.MANUAL_BANDS_HZ[it].toDouble(), bands.getOrElse(it) { 0f }.toDouble(), it, it)
            }
        } else {
            listOf(EqLayout.TONE_LOW, EqLayout.TONE_MID, EqLayout.TONE_HIGH).map { slot ->
                DisplayNode(
                    EqLayout.slots[slot].frequencyHz.toDouble(),
                    nodes.getOrElse(slot) { EqNode(0.0, 0.0) }.gain,
                    slot, slot,
                )
            }
        }
    }

    val currentDisplay by rememberUpdatedState(display)
    val currentOnGain by rememberUpdatedState(onGain)
    val currentOnSelect by rememberUpdatedState(onSelect)
    var dragging by remember { mutableIntStateOf(-1) }
    val selectedPulse by animateFloatAsState(if (dragging >= 0) 1f else 0f, spring(dampingRatio = 0.6f, stiffness = 500f), label = "drag")

    val labelStyle = TextStyle(fontFamily = InterFontFamily, fontSize = 10.sp, color = LabColors.Faint)
    val plotOf: DrawScope.() -> Plot = {
        Plot(left = 30.dp.toPx(), top = 10.dp.toPx(), right = size.width - 10.dp.toPx(), bottom = size.height - 24.dp.toPx())
    }
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(LabColors.Stage)
            .border(1.dp, LabColors.StageLine, RoundedCornerShape(18.dp))
            .damageBoundary()
            .pointerInput(manual) {
                if (!manual) return@pointerInput
                detectTapGestures(
                    onTap = { pos ->
                        val plot = Plot(30.dp.toPx(), 10.dp.toPx(), size.width - 10.dp.toPx(), size.height - 24.dp.toPx())
                        val hit = nearest(currentDisplay, plot, pos, 44.dp.toPx())
                        if (hit >= 0) currentOnSelect(currentDisplay[hit].band)
                    },
                )
            }
            .pointerInput(manual) {
                if (!manual) return@pointerInput
                // a press on a node grabs it (the page doesn't scroll); anywhere else falls through.
                // frequency is fixed by the engine, so only the vertical move matters.
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val plot = Plot(30.dp.toPx(), 10.dp.toPx(), size.width - 10.dp.toPx(), size.height - 24.dp.toPx())
                    val hit = nearest(currentDisplay, plot, down.position, 30.dp.toPx())
                    if (hit < 0) return@awaitEachGesture
                    down.consume()
                    currentOnSelect(currentDisplay[hit].band)
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    dragging = hit
                    val grabbed = currentDisplay[hit]
                    val dy = down.position.y - plot.y(grabbed.gain)
                    var wasZero = abs(grabbed.gain) < 0.05
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            change.consume()
                            val g = snap(plot.gainAt(change.position.y - dy))
                            val isZero = g == 0.0
                            if (isZero && !wasZero) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            wasZero = isZero
                            currentOnGain(currentDisplay[hit].band, g)
                        }
                    } finally {
                        dragging = -1
                    }
                }
            },
    ) {
        val plot = plotOf()
        drawGrid(plot, measurer, labelStyle)

        clipRect(plot.left, plot.top, plot.right, plot.bottom) {
            val zero = plot.y(0.0)
            perNode.forEachIndexed { i, response ->
                if (!slotBands[i].enabled) return@forEachIndexed
                val color = LabColors.node(i)
                val emphasis = if (manual && i == selected) 1f else 0.55f
                val path = curvePath(plot, response)
                val fill = Path().apply {
                    addPath(path)
                    lineTo(plot.right, zero)
                    lineTo(plot.left, zero)
                    close()
                }
                drawPath(fill, color.copy(alpha = 0.16f * emphasis))
                drawPath(path, color.copy(alpha = 0.75f * emphasis), style = Stroke(width = 1.2.dp.toPx()))
            }
            // the sound you hear: every band summed
            drawPath(
                curvePath(plot, total),
                Color.White,
                style = Stroke(width = 2.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }

        display.forEach { n ->
            val center = Offset(plot.x(n.frequency), plot.y(n.gain.coerceIn(-GraphRangeDb, GraphRangeDb)))
            val color = LabColors.node(n.colorIndex)
            val isSelected = manual && n.band == selected
            val r = (if (isSelected) 8.5.dp else 6.5.dp).toPx() + if (isSelected) 3.dp.toPx() * selectedPulse else 0f
            if (isSelected) {
                drawCircle(color.copy(alpha = 0.18f + 0.1f * selectedPulse), r * 2.1f, center)
            }
            drawCircle(Color.Black, r + 2.dp.toPx(), center)
            drawCircle(color, r, center)
            if (isSelected) drawCircle(Color.White, r + 2.dp.toPx(), center, style = Stroke(1.5.dp.toPx()))
        }
    }
}

private fun snap(db: Double): Double {
    val g = (db * 10).roundToInt() / 10.0
    return if (abs(g) < 0.35) 0.0 else g.coerceIn(-GraphRangeDb, GraphRangeDb)
}

private fun nearest(nodes: List<DisplayNode>, plot: Plot, pos: Offset, radius: Float): Int {
    var best = -1
    var bestD = radius
    nodes.forEachIndexed { i, n ->
        val d = hypot(pos.x - plot.x(n.frequency), pos.y - plot.y(n.gain.coerceIn(-GraphRangeDb, GraphRangeDb)))
        if (d < bestD) {
            bestD = d
            best = i
        }
    }
    return best
}

internal fun curvePath(plot: Plot, response: DoubleArray, frequencies: DoubleArray = GraphFrequencies): Path {
    val path = Path()
    for (i in frequencies.indices) {
        val x = plot.x(frequencies[i])
        val y = plot.y(response[i])
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    return path
}

internal fun DrawScope.drawGrid(
    plot: Plot,
    measurer: androidx.compose.ui.text.TextMeasurer,
    style: TextStyle,
    yLabels: List<Pair<Double, String>> = listOf(12.0 to "+12", 6.0 to "+6", 0.0 to "0", -6.0 to "-6", -12.0 to "-12"),
) {
    val line = Color.White.copy(alpha = 0.06f)
    yLabels.forEach { (db, label) ->
        val y = plot.y(db)
        drawLine(if (db == 0.0) Color.White.copy(alpha = 0.16f) else line, Offset(plot.left, y), Offset(plot.right, y), 1.dp.toPx())
        val layout = measurer.measure(label, style)
        drawText(layout, topLeft = Offset(plot.left - layout.size.width - 6.dp.toPx(), y - layout.size.height / 2f))
    }
    GridFrequencies.forEachIndexed { i, f ->
        val x = plot.x(f)
        drawLine(line, Offset(x, plot.top), Offset(x, plot.bottom), 1.dp.toPx())
        val layout = measurer.measure(GridLabels[i], style)
        val lx = (x - layout.size.width / 2f).coerceIn(plot.left - 4.dp.toPx(), plot.right - layout.size.width.toFloat())
        drawText(layout, topLeft = Offset(lx, plot.bottom + 6.dp.toPx()))
    }
}

@Composable
private fun NodeDetail(
    band: Int,
    gain: Double,
    color: Color,
    onGain: (Double) -> Unit,
    onReset: () -> Unit,
) {
    val slot = EqLayout.slots[band]
    val type = when (slot.kind) {
        FilterKind.LOW_SHELF -> FilterType.LSC
        FilterKind.HIGH_SHELF -> FilterType.HSC
        FilterKind.BELL -> FilterType.PK
    }
    LabCard(padding = 18.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.mochy_eq_band_title, frequencyLabel(slot.frequencyHz.toDouble())),
                    fontFamily = InterFontFamily, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color.White,
                )
                Text(
                    "${filterTypeLabel(type).lowercase()} · Q ${String.format("%.1f", EqLayout.MANUAL_Q)}" +
                        " · ${gainLabel(gain)}",
                    fontFamily = InterFontFamily, fontSize = 13.sp, color = LabColors.Secondary,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        LabSlider(
            label = "GAIN",
            valueText = gainLabel(gain),
            value = gain.toFloat(),
            onValueChange = { v -> onGain(snap(v.toDouble())) },
            valueRange = -GraphRangeDb.toFloat()..GraphRangeDb.toFloat(),
        )
        Spacer(Modifier.height(14.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(LabColors.Row)
                .clickable(onClick = onReset),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                stringResource(R.string.mochy_eq_reset_band),
                fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                color = Color.White,
            )
        }
    }
}
