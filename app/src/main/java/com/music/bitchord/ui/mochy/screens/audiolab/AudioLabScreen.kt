// audio lab home: big cards for the equalizer and effects, shorter ones for quality and transitions, each drawing shows its state
//
// Ported from Mochy Music (GPL-3.0): ui/screens/audiolab/AudioLabScreen.kt.
// ChordWave rewiring (see NEEDS.md):
// - Navigation is the MochyNavigator (route strings verbatim Mochy).
// - The equalizer state comes from the real engine via AudioLabViewModel
//   (AppSettings); there is no AutoEq correction yet, so the status line
//   is just the preset name.
// - No provider modules in ChordWave: the quality tile always reads the
//   YouTube ceiling (AppSettings.effectiveAudioQuality).
// - The USB/Bluetooth status comes from AudioOutputStatus (real), not a
//   player-service audioOutputs list.
// - The equalizer sketch's live level is gone (no AudioTap): it moves on
//   the frame clock at rest, like the other pictures.
package com.music.bitchord.ui.mochy.screens.audiolab

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.R
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.AudioQuality
import com.music.bitchord.playback.AudioOutputStatus
import com.music.bitchord.playback.AudioRouting
import com.music.bitchord.ui.mochy.component.damageBoundary
import com.music.bitchord.ui.mochy.component.pressBounce
import com.music.bitchord.ui.mochy.constants.CrossmixMode
import com.music.bitchord.ui.mochy.constants.CrossmixModeKey
import com.music.bitchord.ui.mochy.constants.DefaultTransitionMode
import com.music.bitchord.ui.mochy.eq.EqState
import com.music.bitchord.ui.mochy.navigation.MochyNavigator
import com.music.bitchord.ui.mochy.theme.InterFontFamily
import com.music.bitchord.ui.mochy.utils.rememberEnumPreference
import com.music.bitchord.ui.mochy.viewmodels.AudioLabViewModel
import kotlin.math.roundToInt
import kotlin.math.sin

private val HubCard = Color(0xFF222225)
private val Sketch = Color(0xFF6A6A70)

@Composable
fun AudioLabScreen(
    navigator: MochyNavigator,
    viewModel: AudioLabViewModel = hiltViewModel(),
) {
    val eq by viewModel.eq.collectAsState()
    val presets by viewModel.presets.collectAsState()
    val reverb by viewModel.reverb.collectAsState()
    val loudness by viewModel.loudness.collectAsState()
    val spatial by viewModel.spatial.collectAsState()
    val playback by viewModel.playback.collectAsState()
    val volume by viewModel.volume.collectAsState()
    val wifiQuality by AppSettings.audioQualityWifi.collectAsStateWithLifecycle()
    val cellularQuality by AppSettings.audioQualityCellular.collectAsStateWithLifecycle()
    val metered by AppSettings.meteredConnection.collectAsStateWithLifecycle()
    val ceilingOverride by AppSettings.streamCeilingOverride.collectAsStateWithLifecycle()
    val quality = AppSettings.ceilingFor(wifiQuality, cellularQuality, metered, ceilingOverride)
    val (mode) = rememberEnumPreference(CrossmixModeKey, defaultValue = DefaultTransitionMode)

    val effects = activeEffects(reverb, loudness, spatial, playback, volume)

    AudioLabPage(
        title = LabTitle("Audio", "Lab"),
        onBack = { navigator.popBackStack() },
    ) {
        val equalizer: @Composable (Modifier) -> Unit = { modifier ->
            BigHubCard(
                title = "Equalizer",
                status = equalizerStatus(eq, presets.associate { it.id to it.name }),
                on = eq.enabled,
                onClick = { navigator.navigate("equalizer") },
                modifier = modifier,
            ) { EqualizerSketch(eq) }
        }
        val effect: @Composable (Modifier) -> Unit = { modifier ->
            BigHubCard(
                title = "Audio Effect",
                status = effectsStatus(effects),
                on = effects.isNotEmpty(),
                onClick = { navigator.navigate("audio_fx") },
                modifier = modifier,
            ) { FxSketch(active = effects.isNotEmpty()) }
        }
        val qualityCard: @Composable (Modifier) -> Unit = { modifier ->
            SmallHubCard(
                title = "Audio Quality",
                status = qualityName(quality),
                onClick = { navigator.navigate("audio_quality") },
                modifier = modifier,
            ) { QualitySketch(quality) }
        }
        val transition: @Composable (Modifier) -> Unit = { modifier ->
            SmallHubCard(
                title = "Transition",
                status = when (mode) {
                    CrossmixMode.OFF -> "Off"
                    CrossmixMode.AUTO_CROSSFADE -> "Auto"
                    CrossmixMode.MANUAL_CROSSFADE -> "Manual"
                    CrossmixMode.CROSSMIX -> "CrossMix"
                },
                onClick = { navigator.navigate("crossmix_settings") },
                modifier = modifier,
            ) { TransitionSketch(active = mode != CrossmixMode.OFF) }
        }

        val output by AudioOutputStatus.current.collectAsStateWithLifecycle()
        val usbDac = output.isUsb
        val pass: @Composable (Modifier) -> Unit = { modifier ->
            SmallHubCard(
                title = stringResource(R.string.audiopass_hub_title),
                status = when {
                    usbDac -> output.deviceName.ifBlank { stringResource(R.string.audiopass_kind_usb) }
                    output.routeKind == AudioRouting.Kind.BLUETOOTH -> stringResource(R.string.audiopass_status_bluetooth)
                    else -> stringResource(R.string.audiopass_status_none)
                },
                onClick = { navigator.navigate("audio_pass") },
                modifier = modifier,
            ) { PassSketch(plugged = usbDac) }
        }

        BoxWithConstraints(Modifier.fillMaxWidth()) {
            if (maxWidth >= 560.dp) {
                // a tablet: side by side, so the cards don't stretch into long thin bars
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.height(IntrinsicSize.Min)) {
                        equalizer(Modifier.weight(1f).fillMaxHeight())
                        effect(Modifier.weight(1f).fillMaxHeight())
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.height(IntrinsicSize.Min)) {
                        qualityCard(Modifier.weight(1f).fillMaxHeight())
                        transition(Modifier.weight(1f).fillMaxHeight())
                        pass(Modifier.weight(1f).fillMaxHeight())
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    equalizer(Modifier)
                    effect(Modifier)
                    qualityCard(Modifier)
                    transition(Modifier)
                    pass(Modifier)
                }
            }
        }
    }
}

/** the effects status line, first names then "and 2 more" as in the mock */
private fun effectsStatus(effects: List<ActiveEffect>): String {
    val names = effects.map { it.label.uppercase() }
    return when {
        names.isEmpty() -> "ALL OFF"
        names.size <= 2 -> names.joinToString(", ")
        else -> names.take(2).joinToString(", ") + " and ${names.size - 2} more"
    }
}

private fun equalizerStatus(eq: EqState, userPresets: Map<String, String>): String {
    if (!eq.enabled) return "OFF"
    val curve = eq.presetId?.let { id -> userPresets[id] } ?: "Custom"
    return curve.uppercase()
}

private fun qualityName(q: AudioQuality): String = "YouTube · ${q.label}"

/** the equalizer's and effects' card, a green dot and the state in small type with the name under it */
@Composable
private fun BigHubCard(
    title: String,
    status: String,
    on: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    sketch: @Composable BoxScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .fillMaxWidth()
            // at least the mock's height; a big system font makes it taller instead of cutting it
            .heightIn(min = 128.dp)
            .pressBounce(interaction, pressedScale = 0.97f)
            .clip(RoundedCornerShape(26.dp))
            .background(HubCard)
            .clickable(interaction, null, onClick = onClick)
            .damageBoundary(),
    ) {
        Box(Modifier.matchParentSize()) { sketch() }
        Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 18.dp)) {
            StatusLine(status, on, Modifier.fillMaxWidth(0.66f))
            Spacer(Modifier.height(30.dp))
            Text(
                title,
                fontFamily = InterFontFamily, fontWeight = FontWeight.Bold, fontSize = 30.sp,
                letterSpacing = (-0.03).em, color = Color.White,
            )
        }
    }
}

/** the shorter card for quality and transitions: the name, its state under it, the drawing at the right */
@Composable
private fun SmallHubCard(
    title: String,
    status: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    sketch: @Composable BoxScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .pressBounce(interaction, pressedScale = 0.97f)
            .clip(RoundedCornerShape(20.dp))
            .background(HubCard)
            .clickable(interaction, null, onClick = onClick)
            .damageBoundary(),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(Modifier.matchParentSize()) { sketch() }
        Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp).fillMaxWidth(0.62f)) {
            Text(title, fontFamily = InterFontFamily, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White)
            Text(
                status.uppercase(),
                fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = Color(0xFF8A8A8E),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun StatusLine(status: String, on: Boolean, modifier: Modifier = Modifier) {
    val dot by androidx.compose.animation.animateColorAsState(
        if (on) Color(0xFF8BE07A) else Color(0xFF5C5C63), tween(300), label = "dot",
    )
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(dot))
        Spacer(Modifier.width(8.dp))
        Text(
            status,
            fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = Color(0xFF8A8A8E),
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

/** the sketch's own clock: still unless it's on screen and something should move */
@Composable
private fun sketchTime(moving: () -> Boolean = { true }): State<Float> {
    val onScreen = rememberOnScreen()
    val held = rememberPicturesHeld(onScreen)
    return rememberLabTime(held, moving)
}

/** three sliders on a tilted panel, set to your curve's lows, mids and highs */
@Composable
private fun BoxScope.EqualizerSketch(eq: EqState) {
    fun band(from: Double, to: Double): Float {
        if (!eq.enabled) return 0f
        val inBand = eq.nodes.filter { it.enabled && it.frequency >= from && it.frequency < to }
        return if (inBand.isEmpty()) 0f else (inBand.sumOf { it.gain } / inBand.size).toFloat()
    }
    val low by animateFloatAsState(band(0.0, 250.0), spring(dampingRatio = 0.6f, stiffness = 140f), label = "low")
    val mid by animateFloatAsState(band(250.0, 4000.0), spring(dampingRatio = 0.6f, stiffness = 140f), label = "mid")
    val high by animateFloatAsState(band(4000.0, 30_000.0), spring(dampingRatio = 0.6f, stiffness = 140f), label = "high")
    val level = remember { LevelFollower() }
    val time = sketchTime { eq.enabled }
    val last = remember { floatArrayOf(-1f) }
    Canvas(Modifier.fillMaxSize()) {
        val t = time.value
        if (t != last[0]) { level.update(0.016f); last[0] = t }
        val panel = size.height * 1.05f
        val origin = Offset(size.width - panel * 0.78f, size.height * 0.05f)
        rotate(-14f, Offset(origin.x + panel / 2, origin.y + panel / 2)) {
            drawRoundRect(Sketch, origin, Size(panel, panel), CornerRadius(panel * 0.2f), style = Stroke(3.5.dp.toPx()))
            listOf(low, mid, high).forEachIndexed { i, gain ->
                val x = origin.x + panel * (0.25f + i * 0.22f)
                val top = origin.y + panel * 0.16f
                val bottom = origin.y + panel * 0.84f
                drawLine(Sketch, Offset(x, top), Offset(x, bottom), 4.dp.toPx(), StrokeCap.Round)
                val bob = level.punch * 4.dp.toPx() * sin(t * 9f + i * 2f)
                val y = (top + bottom) / 2 - (gain / 12f) * (bottom - top) / 2 + bob
                drawCircle(HubCard, 11.dp.toPx(), Offset(x, y))
                drawCircle(if (eq.enabled) Color(0xFF9A9AA0) else Sketch, 11.dp.toPx(), Offset(x, y), style = Stroke(4.dp.toPx()))
            }
        }
    }
}

/** big outlined FX letters off the edge; a light runs through them while an effect is on */
@Composable
private fun BoxScope.FxSketch(active: Boolean) {
    val measurer = rememberTextMeasurer()
    val glow by animateFloatAsState(if (active) 1f else 0f, tween(500), label = "fx")
    val time = sketchTime { active }
    Canvas(Modifier.fillMaxSize()) {
        val style = TextStyle(
            fontFamily = InterFontFamily, fontWeight = FontWeight.Light, fontStyle = FontStyle.Italic,
            fontSize = (size.height / density * 1.05f).sp,
            drawStyle = Stroke(width = 1.6.dp.toPx()),
        )
        val layout = measurer.measure("FX", style)
        val topLeft = Offset(size.width - layout.size.width * 0.86f, (size.height - layout.size.height) / 2f + size.height * 0.08f)
        drawText(layout, color = Sketch, topLeft = topLeft)
        if (glow > 0.01f) {
            val sweep = (time.value * 0.35f) % 1f
            val x = topLeft.x + layout.size.width * (sweep * 1.6f - 0.3f)
            drawText(
                layout,
                brush = Brush.horizontalGradient(
                    listOf(Color.Transparent, Color.White.copy(alpha = 0.85f * glow), Color.Transparent),
                    startX = x - 60.dp.toPx(), endX = x + 60.dp.toPx(),
                ),
                topLeft = topLeft,
            )
        }
    }
}

/** a little waveform, as coarse as what's streaming */
@Composable
private fun BoxScope.QualitySketch(quality: AudioQuality) {
    val fine = when (quality) {
        AudioQuality.LOSSLESS -> 1f
        AudioQuality.HIGH -> 0.65f
        AudioQuality.MEDIUM -> 0.35f
        AudioQuality.LOW -> 0f
    }
    val resolution by animateFloatAsState(fine, tween(600), label = "res")
    Canvas(Modifier.fillMaxHeight().width(120.dp).align(Alignment.CenterEnd).padding(end = 14.dp)) {
        val step = (5f - 3.6f * resolution).dp.toPx()
        val levels = (3 + 12 * resolution).roundToInt()
        var x = 0f
        while (x < size.width) {
            val p = x / size.width
            val a = (0.35f + 0.35f * sin(p * 23f) + 0.3f * sin(p * 57f + 1f)).coerceIn(0f, 1f) * (1f - p * 0.6f)
            val q = (a * levels).roundToInt() / levels.toFloat()
            val half = q * size.height * 0.32f
            drawLine(Sketch, Offset(x, size.height / 2 - half), Offset(x, size.height / 2 + half), (step * 0.55f).coerceAtLeast(1.dp.toPx()))
            x += step
        }
    }
}

/** a usb-c plug on its cable, brighter while a dac is plugged in */
@Composable
private fun BoxScope.PassSketch(plugged: Boolean) {
    val on by animateFloatAsState(if (plugged) 1f else 0.4f, tween(400), label = "plug")
    Canvas(Modifier.fillMaxHeight().width(72.dp).align(Alignment.CenterEnd).padding(end = 14.dp, top = 20.dp, bottom = 20.dp)) {
        val mid = size.height / 2f
        val color = Sketch.copy(alpha = on)
        val head = Size(size.width * 0.18f, size.height * 0.36f)
        drawRoundRect(color, Offset(0f, mid - head.height / 2f), head, CornerRadius(1.5.dp.toPx()), style = Stroke(1.6.dp.toPx()))
        val body = Size(size.width * 0.42f, size.height * 0.62f)
        drawRoundRect(color, Offset(head.width, mid - body.height / 2f), body, CornerRadius(4.dp.toPx()), style = Stroke(2.dp.toPx()))
        drawLine(color, Offset(head.width + body.width, mid), Offset(size.width, mid), 2.dp.toPx(), StrokeCap.Round)
    }
}

/** two songs' volumes crossing */
@Composable
private fun BoxScope.TransitionSketch(active: Boolean) {
    val on by animateFloatAsState(if (active) 1f else 0.4f, tween(400), label = "t")
    Canvas(Modifier.fillMaxHeight().width(56.dp).align(Alignment.CenterEnd).padding(end = 14.dp, top = 18.dp, bottom = 18.dp)) {
        val w = size.width
        val h = size.height
        val out = Path().apply { moveTo(0f, 0f); cubicTo(w * 0.5f, 0f, w * 0.5f, h, w, h) }
        val inn = Path().apply { moveTo(0f, h); cubicTo(w * 0.5f, h, w * 0.5f, 0f, w, 0f) }
        drawPath(out, Sketch.copy(alpha = on), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
        drawPath(inn, Sketch.copy(alpha = on), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
    }
}
