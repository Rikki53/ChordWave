// audio lab's equalizer: one curve shaped as a graph or as sliders, with an optional headphone correction
//
// Ported from Mochy Music (GPL-3.0): ui/screens/equalizer/EqualizerScreen.kt
// (structure, tab switch, presets row, output section).
//
// CHORDWAVE REWIRING — the engine is AppSettings' fixed 10-slot layout
// (see EqLayout), not Mochy's free parametric nodes:
// - State comes from 5F's AudioLabViewModel (eq/presets, the live engine
//   curve) and this feature's EqualizerViewModel (the AutoEq correction).
//   Writes go straight to AppSettings — the service applies them live.
// - Presets are ChordWave's EqualizerPreset entries (localized via the
//   existing eq_preset_* strings). There are no user presets to save or
//   delete in ChordWave, so Save/Delete are dropped (see NEEDS.md).
// - Preset picks, Reset, band edits and AutoEq all force MANUAL mode: in
//   DYNAMIC mode the tone pad drives the engine and the bands would not
//   be heard. A mode row (Dynamic/Manual) sits above the presets so the
//   switch is visible, never silent.
// - Output has no preamp control to port: ChordWave sets headroom
//   automatically (EqCurve.preampDb). The section shows the live make-up
//   value, read-only, with one line saying so.
// - The tone pad itself is not ported here (Classic only for now); the
//   graph/bands tabs show the tone curve read-only in DYNAMIC mode with a
//   switch to Manual. See NEEDS.md.
package com.music.bitchord.ui.mochy.screens.equalizer

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material3.Text
import com.music.bitchord.R
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.EqualizerMode
import com.music.bitchord.playback.EqualizerPreset
import com.music.bitchord.playback.manualCurve
import com.music.bitchord.playback.toneCurve
import com.music.bitchord.ui.mochy.eq.EqPresetInfo
import com.music.bitchord.ui.mochy.eq.EqState
import com.music.bitchord.ui.mochy.eq.autoeq.AutoEqCorrection
import com.music.bitchord.ui.mochy.eq.gainLabel
import com.music.bitchord.ui.mochy.navigation.MochyNavigator
import com.music.bitchord.ui.mochy.screens.audiolab.AudioLabPage
import com.music.bitchord.ui.mochy.screens.audiolab.LabCard
import com.music.bitchord.ui.mochy.screens.audiolab.LabChip
import com.music.bitchord.ui.mochy.screens.audiolab.LabChips
import com.music.bitchord.ui.mochy.screens.audiolab.LabColors
import com.music.bitchord.ui.mochy.screens.audiolab.LabSectionLabel
import com.music.bitchord.ui.mochy.screens.audiolab.LabSegmented
import com.music.bitchord.ui.mochy.screens.audiolab.LabTextAction
import com.music.bitchord.ui.mochy.screens.audiolab.LabTitle
import com.music.bitchord.ui.mochy.screens.audiolab.LabToggleRow
import com.music.bitchord.ui.mochy.screens.audiolab.ListenToAudio
import com.music.bitchord.ui.mochy.screens.audiolab.animatedDim
import com.music.bitchord.ui.mochy.theme.InterFontFamily
import com.music.bitchord.ui.mochy.viewmodels.AudioLabViewModel
import com.music.bitchord.ui.mochy.viewmodels.EqualizerViewModel
import kotlin.math.abs

private val Tabs = listOf(
    R.string.mochy_eq_tab_graph,
    R.string.mochy_eq_tab_bands,
    R.string.mochy_eq_tab_autoeq,
)

/**
 * Route `equalizer`. [initialTab] is 0 unless the host deep-links here
 * (the Settings "AutoEQ" entry can pass 2 — see NEEDS.md).
 */
@Composable
fun EqualizerScreen(
    navigator: MochyNavigator,
    initialTab: Int = 0,
    audioLabViewModel: AudioLabViewModel = hiltViewModel(),
    equalizerViewModel: EqualizerViewModel = hiltViewModel(),
) {
    val eq by audioLabViewModel.eq.collectAsState()
    val presets by audioLabViewModel.presets.collectAsState()
    val correction by equalizerViewModel.correction.collectAsState()
    val correctionEnabled by equalizerViewModel.correctionEnabled.collectAsState()
    val mode by AppSettings.equalizerMode.collectAsStateWithLifecycle()
    val bands by AppSettings.equalizerBands.collectAsStateWithLifecycle()
    val toneX by AppSettings.equalizerToneX.collectAsStateWithLifecycle()
    val toneY by AppSettings.equalizerToneY.collectAsStateWithLifecycle()
    val toneFocused by AppSettings.equalizerFocused.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(initialTab.coerceIn(0, Tabs.size - 1)) }
    var selectedNode by rememberSaveable { mutableIntStateOf(0) }

    // the graph's spectrum listens while this page is open (no-op in
    // ChordWave: no AudioTap — kept for parity, see LabControls)
    ListenToAudio()

    // Band edits always land on the manual bands. The editors gate their
    // gestures on MANUAL mode, so the mode switch here is a safety net;
    // the flows are read fresh on every call, never closed over.
    val onGain: (Int, Double) -> Unit = { index, gain ->
        if (AppSettings.equalizerMode.value != EqualizerMode.MANUAL) {
            AppSettings.setEqualizerMode(EqualizerMode.MANUAL)
        }
        val updated = AppSettings.equalizerBands.value.toMutableList()
        if (index in updated.indices) {
            updated[index] = gain.toFloat()
            AppSettings.setEqualizerBands(updated)
        }
    }
    val onSwitchToManual = { AppSettings.setEqualizerMode(EqualizerMode.MANUAL) }
    val onPickPreset: (String) -> Unit = { id ->
        val preset = runCatching { EqualizerPreset.valueOf(id) }.getOrNull()
        if (preset != null && preset != EqualizerPreset.CUSTOM) {
            AppSettings.setEqualizerMode(EqualizerMode.MANUAL)
            AppSettings.setEqualizerPreset(preset)
        }
    }
    val onResetPreset = {
        AppSettings.setEqualizerMode(EqualizerMode.MANUAL)
        AppSettings.setEqualizerPreset(EqualizerPreset.FLAT)
    }

    AudioLabPage(
        title = LabTitle("Audio", stringResource(R.string.mochy_eq_title)),
        onBack = { navigator.popBackStack() },
    ) {
        LabCard(padding = 10.dp) {
            LabToggleRow(
                title = stringResource(R.string.mochy_eq_title),
                subtitle = statusLine(eq, correction, correctionEnabled),
                checked = eq.enabled,
                onCheckedChange = AppSettings::setEqualizerEnabled,
                leadingDot = true,
            )
        }
        Spacer(Modifier.height(14.dp))
        // plain loop, not map: stringResource is @Composable and cannot
        // be called inside a collection lambda
        val tabLabels = mutableListOf<String>()
        for (res in Tabs) tabLabels += stringResource(res)
        LabSegmented(options = tabLabels, selectedIndex = tab, onSelect = { tab = it })
        Spacer(Modifier.height(18.dp))

        val dim = animatedDim(!eq.enabled)
        Column(Modifier.fillMaxWidth().graphicsLayer { alpha = dim }) {
            AnimatedContent(
                targetState = tab,
                transitionSpec = {
                    val dir = if (targetState > initialState) 1 else -1
                    (fadeIn(tween(220, 60)) + slideInHorizontally(spring(dampingRatio = 0.9f, stiffness = 420f)) { it / 8 * dir }) togetherWith fadeOut(tween(120))
                },
                label = "eqTab",
            ) { page ->
                Column(Modifier.fillMaxWidth()) {
                    when (page) {
                        0, 1 -> {
                            ModeRow(mode = mode)
                            Spacer(Modifier.height(18.dp))
                            Presets(
                                eq = eq,
                                presets = presets,
                                onPick = onPickPreset,
                                onReset = onResetPreset,
                            )
                            Spacer(Modifier.height(18.dp))
                            if (page == 0) {
                                EqGraphSection(
                                    nodes = eq.nodes,
                                    bands = bands,
                                    mode = mode,
                                    toneX = toneX,
                                    toneY = toneY,
                                    toneFocused = toneFocused,
                                    selected = selectedNode,
                                    onSelect = { selectedNode = it },
                                    onGain = onGain,
                                    onSwitchToManual = onSwitchToManual,
                                )
                            } else {
                                EqBandsSection(
                                    bands = bands,
                                    mode = mode,
                                    onGain = onGain,
                                    onSwitchToManual = onSwitchToManual,
                                )
                            }
                        }
                        else -> AutoEqTab(viewModel = equalizerViewModel)
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            Output()
        }
        Spacer(Modifier.height(120.dp))
    }
}

@Composable
private fun ModeRow(mode: EqualizerMode) {
    LabSectionLabel(stringResource(R.string.mochy_eq_mode))
    LabChips(
        chips = listOf(
            LabChip(EqualizerMode.DYNAMIC.name, stringResource(R.string.equalizer_dynamic)),
            LabChip(EqualizerMode.MANUAL.name, stringResource(R.string.equalizer_manual)),
        ),
        selectedKey = mode.name,
        onSelect = { AppSettings.setEqualizerMode(EqualizerMode.valueOf(it)) },
    )
}

@Composable
private fun statusLine(eq: EqState, correction: AutoEqCorrection?, correctionEnabled: Boolean): String {
    if (!eq.enabled) return stringResource(R.string.mochy_eq_status_off)
    val correctionNow = correction
    if (correctionNow != null && correctionEnabled) {
        return stringResource(R.string.mochy_eq_status_on, correctionNow.model)
    }
    if (eq.nodes.all { abs(it.gain) < 0.05 }) return stringResource(R.string.mochy_eq_status_flat)
    val presetIdNow = eq.presetId
    val presetName = if (presetIdNow == null) stringResource(R.string.eq_preset_custom)
    else stringResource(presetLabelRes(presetIdNow))
    return stringResource(R.string.mochy_eq_status_on, presetName)
}

/** the localized preset name's string res for an EqualizerPreset.name id
 *  (5F's EqPresetInfo.name is the unlocalized displayName — this reuses
 *  the Classic screen's eq_preset_* strings instead). Plain function, not
 *  @Composable, so it can be called anywhere; resolve with stringResource
 *  at the call site. */
private fun presetLabelRes(presetId: String): Int = when (presetId) {
        "FLAT" -> R.string.eq_preset_flat
        "ACOUSTIC" -> R.string.eq_preset_acoustic
        "BASS_BOOST" -> R.string.eq_preset_bass_boost
        "BASS_CUT" -> R.string.eq_preset_bass_cut
        "VOCAL" -> R.string.eq_preset_vocal
        "TREBLE_BOOST" -> R.string.eq_preset_treble_boost
        "TREBLE_CUT" -> R.string.eq_preset_treble_cut
        "LOUDNESS" -> R.string.eq_preset_loudness
        "SPOKEN_WORD" -> R.string.eq_preset_spoken_word
        "ELECTRONIC" -> R.string.eq_preset_electronic
        "ROCK" -> R.string.eq_preset_rock
        "HIP_HOP" -> R.string.eq_preset_hip_hop
        "JAZZ" -> R.string.eq_preset_jazz
        "CLASSICAL" -> R.string.eq_preset_classical
        "SMALL_SPEAKERS" -> R.string.eq_preset_small_speakers
        "LATE_NIGHT" -> R.string.eq_preset_late_night
        else -> R.string.eq_preset_custom
    }

@Composable
private fun Presets(
    eq: EqState,
    presets: List<EqPresetInfo>,
    onPick: (String) -> Unit,
    onReset: () -> Unit,
) {
    LabSectionLabel(stringResource(R.string.mochy_eq_presets)) {
        LabTextAction(stringResource(R.string.mochy_eq_reset), R.drawable.refresh, onClick = onReset)
    }
    // plain loop, not buildList/forEach: stringResource is @Composable
    // and cannot be called inside a collection lambda
    val chips = mutableListOf<LabChip>()
    if (eq.presetId == null) chips += LabChip("custom", stringResource(R.string.eq_preset_custom))
    for (preset in presets) chips += LabChip(preset.id, stringResource(presetLabelRes(preset.id)))
    LabChips(
        chips = chips,
        selectedKey = eq.presetId ?: "custom",
        onSelect = { if (it != "custom") onPick(it) },
    )
}

/**
 * ChordWave has no preamp control: headroom is automatic
 * (EqCurve.preampDb takes back half of the curve's peak boost). So this
 * shows the live make-up value, read-only, instead of Mochy's slider and
 * auto-preamp toggle.
 */
@Composable
private fun Output() {
    val mode by AppSettings.equalizerMode.collectAsStateWithLifecycle()
    val bands by AppSettings.equalizerBands.collectAsStateWithLifecycle()
    val toneX by AppSettings.equalizerToneX.collectAsStateWithLifecycle()
    val toneY by AppSettings.equalizerToneY.collectAsStateWithLifecycle()
    val focused by AppSettings.equalizerFocused.collectAsStateWithLifecycle()
    val preamp = when (mode) {
        EqualizerMode.DYNAMIC -> toneCurve(toneX, toneY, focused).preampDb
        EqualizerMode.MANUAL -> manualCurve(bands).preampDb
    }
    LabSectionLabel(stringResource(R.string.mochy_eq_output))
    LabCard(padding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.mochy_eq_preamp),
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = LabColors.Primary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = gainLabel(preamp.toDouble()),
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = LabColors.Primary,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.mochy_eq_preamp_auto),
            fontFamily = InterFontFamily,
            fontSize = 12.sp,
            color = LabColors.Secondary,
        )
    }
}
