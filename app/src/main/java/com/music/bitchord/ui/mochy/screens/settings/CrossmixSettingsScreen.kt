// Ported from Mochy Music (GPL-3.0): ui/screens/settings/CrossmixSettingsScreen.kt
//
// ADAPTATIONS — wired to ChordWave's REAL transition engine:
// - Mode + length drive AppSettings, not Mochy's crossfade prefs:
//   OFF -> crossfade 0 s, smart fade off; AUTO_CROSSFADE -> crossfade 5 s;
//   MANUAL_CROSSFADE -> crossfade N s; CROSSMIX -> smart fade (Automix) ON
//   with the 5 s crossfade kept as the fallback while a pair is still being
//   analysed (AppSettings.smartFadeEnabled doc). CrossfadeEnabledKey /
//   CrossfadeDurationKey are NOT used: CW already owns that setting in
//   classic AppSettings (SharedPreferences), and the Classic settings sheet
//   shows the same values. The Mochy CrossmixModeKey pref is still stored
//   (it is the mode UI's own state) and selectMode() keeps it and the
//   engine in sync both ways (see selectedMode: Automix on from Classic
//   reads back as CROSSMIX).
// - Manual range is 1..12 s, NOT Mochy's 1..20 s: 12 s is ChordWave's real
//   range (classic SettingsSheet slider is 0..12). See NEEDS.md §(c) C1.
// - The Crossmix controls (style / length / tempo / stunts / always-mix)
//   write their Mochy prefs but have NO effect on Automix, which plans
//   each transition from its own analysis — an in-UI note says so, and
//   NEEDS.md §(b) B2 records it. The gapless toggle is likewise pref-only
//   (CW has no gapless setting).
// - backLabel is "Settings" (Mochy: "Audio Lab" — in Modern the page sits
//   under Settings, route settings/crossmix).
// - TransitionModeGrid / TransitionModeCard / TransitionPicture /
//   TransitionStyle / TransportFadeSection are same-package siblings
//   (separate Phase 4 port); LabSelect is the internal one already in
//   ui.mochy.menu.songedit; LabCollapsible / LabSegmented / ListenToAudio
//   come from the LabControls.kt closure in this staging dir.
// - ListenToAudio() is a no-op stub (CW has no audio tap), so the
//   transition pictures render static.

package com.music.bitchord.ui.mochy.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.R
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.ui.mochy.constants.CrossfadeGaplessKey
import com.music.bitchord.ui.mochy.constants.CrossmixAlwaysMixKey
import com.music.bitchord.ui.mochy.constants.CrossmixLength
import com.music.bitchord.ui.mochy.constants.CrossmixLengthKey
import com.music.bitchord.ui.mochy.constants.CrossmixMode
import com.music.bitchord.ui.mochy.constants.CrossmixModeKey
import com.music.bitchord.ui.mochy.constants.CrossmixStyleAuto
import com.music.bitchord.ui.mochy.constants.CrossmixStyleKey
import com.music.bitchord.ui.mochy.constants.CrossmixStuntsKey
import com.music.bitchord.ui.mochy.constants.CrossmixTempoMatch
import com.music.bitchord.ui.mochy.constants.CrossmixTempoMatchKey
import com.music.bitchord.ui.mochy.constants.CrossmixTestToolsKey
import com.music.bitchord.ui.mochy.constants.DefaultTransitionMode
import com.music.bitchord.ui.mochy.constants.HideSettingsDescriptionsKey
import com.music.bitchord.ui.mochy.crossmix.CrossmixStyle
import com.music.bitchord.ui.mochy.crossmix.StyleUse
import com.music.bitchord.ui.mochy.component.SettingsGroup
import com.music.bitchord.ui.mochy.component.SettingsGroupStyle
import com.music.bitchord.ui.mochy.component.SubSettingsScaffold
import com.music.bitchord.ui.mochy.menu.songedit.LabSelect
import com.music.bitchord.ui.mochy.navigation.MochyNavigator
import com.music.bitchord.ui.mochy.screens.audiolab.LabCard
import com.music.bitchord.ui.mochy.screens.audiolab.LabCollapsible
import com.music.bitchord.ui.mochy.screens.audiolab.LabColors
import com.music.bitchord.ui.mochy.screens.audiolab.LabSegmented
import com.music.bitchord.ui.mochy.screens.audiolab.LabSlider
import com.music.bitchord.ui.mochy.screens.audiolab.LabToggleRow
import com.music.bitchord.ui.mochy.screens.audiolab.ListenToAudio
import com.music.bitchord.ui.mochy.theme.InterFontFamily
import com.music.bitchord.ui.mochy.utils.rememberEnumPreference
import com.music.bitchord.ui.mochy.utils.rememberPreference
import kotlin.math.roundToInt

private const val AutoCrossfadeDurationSeconds = 5f
private const val ManualMinSeconds = 1f
/** ChordWave's real range: the classic crossfade slider tops out at 12 s (Mochy: 20 s). */
private const val ManualMaxSeconds = 12f
private val HeadingColor = Color.White.copy(alpha = 0.82f)

@Composable
fun CrossmixSettingsScreen(navigator: MochyNavigator) {
    val (storedMode, onStoredModeChange) = rememberEnumPreference(
        CrossmixModeKey,
        defaultValue = DefaultTransitionMode,
    )
    // the engine's own state, read live so a change from Classic settings shows here too
    val crossfadeSeconds by AppSettings.crossfadeSeconds.collectAsStateWithLifecycle()
    val smartFade by AppSettings.smartFadeEnabled.collectAsStateWithLifecycle()
    val (disableForGapless, onDisableForGaplessChange) = rememberPreference(
        CrossfadeGaplessKey,
        defaultValue = true,
    )
    val (testTools, _) = rememberPreference(CrossmixTestToolsKey, defaultValue = false)
    val (hideDescriptions) = rememberPreference(HideSettingsDescriptionsKey, defaultValue = true)

    val manualSeconds = crossfadeSeconds.toFloat().coerceIn(ManualMinSeconds, ManualMaxSeconds)

    // the stored mode is the UI's state; the engine wins when they disagree
    // (Automix switched on from Classic reads back as Crossmix, and a stored
    // Crossmix with Automix off falls back to the plain fade that is left).
    val selectedMode = when {
        smartFade -> CrossmixMode.CROSSMIX
        storedMode == CrossmixMode.CROSSMIX && crossfadeSeconds == 0 -> CrossmixMode.OFF
        storedMode == CrossmixMode.CROSSMIX -> CrossmixMode.MANUAL_CROSSFADE
        storedMode == CrossmixMode.OFF && crossfadeSeconds > 0 -> CrossmixMode.MANUAL_CROSSFADE
        else -> storedMode
    }

    fun selectMode(mode: CrossmixMode) {
        onStoredModeChange(mode)
        when (mode) {
            CrossmixMode.OFF -> {
                AppSettings.setSmartFadeEnabled(false)
                AppSettings.setCrossfadeSeconds(0)
            }
            CrossmixMode.AUTO_CROSSFADE -> {
                AppSettings.setSmartFadeEnabled(false)
                AppSettings.setCrossfadeSeconds(AutoCrossfadeDurationSeconds.toInt())
            }
            CrossmixMode.MANUAL_CROSSFADE -> {
                AppSettings.setSmartFadeEnabled(false)
                AppSettings.setCrossfadeSeconds(manualSeconds.roundToInt())
            }
            CrossmixMode.CROSSMIX -> {
                // Automix plans the transitions; the 5 s crossfade stays as
                // the fallback while a pair is still being analysed.
                AppSettings.setCrossfadeSeconds(AutoCrossfadeDurationSeconds.toInt())
                AppSettings.setSmartFadeEnabled(true)
            }
        }
    }

    val crossmixTools = crossmixTestItems(isVisible = testTools && selectedMode == CrossmixMode.CROSSMIX)

    // the crossmix picture glows with the music (no-op stub in ChordWave: no audio tap)
    ListenToAudio()

    SubSettingsScaffold(
        title = stringResource(R.string.transition_title),
        backLabel = "Settings",
        onBack = { navigator.popBackStack() },
    ) {
        TransitionHeading(stringResource(R.string.transition_music_heading))
        LabCard(padding = 10.dp) {
            LabToggleRow(
                title = stringResource(R.string.transition_off),
                subtitle = stringResource(R.string.transition_off_desc),
                checked = selectedMode == CrossmixMode.OFF,
                // turning "off" off brings back the everyday mix
                onCheckedChange = { selectMode(if (it) CrossmixMode.OFF else DefaultTransitionMode) },
            )
        }
        Spacer(Modifier.height(10.dp))

        TransitionModeGrid(selected = selectedMode) { mode, opened ->
            val selected = mode == selectedMode
            when (mode) {
                CrossmixMode.AUTO_CROSSFADE -> TransitionModeCard(
                    title = stringResource(R.string.transition_auto),
                    selected = selected,
                    opened = opened,
                    onSelect = { selectMode(mode) },
                    picture = { TransitionPicture(TransitionStyle.Auto, overlap = 0.3f, active = selected) },
                ) {
                    CardNote(stringResource(R.string.transition_auto_desc, AutoCrossfadeDurationSeconds.toInt()), hideDescriptions)
                }
                CrossmixMode.MANUAL_CROSSFADE -> TransitionModeCard(
                    title = stringResource(R.string.transition_manual),
                    selected = selected,
                    opened = opened,
                    onSelect = { selectMode(mode) },
                    picture = {
                        TransitionPicture(
                            TransitionStyle.Manual,
                            overlap = 0.08f + 0.42f * (manualSeconds - ManualMinSeconds) / (ManualMaxSeconds - ManualMinSeconds),
                            active = selected,
                        )
                    },
                ) {
                    CardNote(stringResource(R.string.transition_manual_desc), hideDescriptions)
                    ManualLength(manualSeconds) { AppSettings.setCrossfadeSeconds(it.roundToInt()) }
                }
                else -> TransitionModeCard(
                    title = stringResource(R.string.transition_crossmix),
                    selected = selected,
                    opened = opened,
                    onSelect = { selectMode(CrossmixMode.CROSSMIX) },
                    picture = { TransitionPicture(TransitionStyle.CrossMix, overlap = 0.42f, active = selected) },
                ) {
                    CardNote(stringResource(R.string.crossmix_desc), hideDescriptions)
                    CrossmixControls()
                    LabCollapsible(crossmixTools.any { it.isVisible }) {
                        Spacer(Modifier.height(8.dp))
                        SettingsGroup(style = SettingsGroupStyle.Grouped, items = crossmixTools)
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        LabCollapsible(selectedMode != CrossmixMode.OFF) {
            LabCard(padding = 10.dp) {
                LabToggleRow(
                    title = "Keep gapless albums intact",
                    subtitle = "Album tracks that run straight into each other play through unmixed",
                    checked = disableForGapless,
                    onCheckedChange = onDisableForGaplessChange,
                )
            }
        }

        TransportFadeSection()
        Spacer(Modifier.height(120.dp))
    }
}

/** the soft heading over each group on the transition page */
@Composable
internal fun TransitionHeading(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 22.sp,
        letterSpacing = (-0.2).sp,
        color = HeadingColor,
        modifier = modifier.padding(start = 4.dp, end = 16.dp, bottom = 10.dp),
    )
}

// the small line under an open card's picture, gone when descriptions are hidden
@Composable
private fun CardNote(text: String, hidden: Boolean) {
    if (hidden) return
    Text(
        text = text,
        fontFamily = InterFontFamily,
        fontSize = 12.sp,
        lineHeight = 15.sp,
        color = LabColors.Secondary,
        modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 10.dp),
    )
}

// the manual blend's length, from a quick one-second cut to a long twelve-second wash
@Composable
private fun ManualLength(seconds: Float, onSecondsChange: (Float) -> Unit) {
    Column(Modifier.padding(start = 6.dp, end = 6.dp, top = 10.dp)) {
        LabSlider(
            label = stringResource(R.string.transition_manual_length),
            valueText = stringResource(R.string.transition_seconds, seconds.roundToInt()),
            value = seconds,
            onValueChange = { onSecondsChange(it.roundToInt().toFloat()) },
            valueRange = ManualMinSeconds..ManualMaxSeconds,
            steps = (ManualMaxSeconds - ManualMinSeconds).toInt() - 1,
        )
        Row(Modifier.fillMaxWidth()) {
            Text(
                stringResource(R.string.transition_seconds, ManualMinSeconds.toInt()),
                fontFamily = InterFontFamily, fontSize = 11.sp, color = LabColors.Secondary, modifier = Modifier.weight(1f),
            )
            Text(stringResource(R.string.transition_seconds, ManualMaxSeconds.toInt()), fontFamily = InterFontFamily, fontSize = 11.sp, color = LabColors.Secondary)
        }
    }
}

// the styles a listener can pin, grouped the way the planner thinks of them: everyday moves, then lifts, falls and the flavours it never picks by itself
private val PinnableStyles: List<CrossmixStyle> =
    listOf(StyleUse.Everyday, StyleUse.Lift, StyleUse.Fall, StyleUse.Flavour).flatMap { use -> CrossmixStyle.entries.filter { it.use == use } }

/**
 * The listener's say over a mix: which transition, how long, how far tempos
 * bend to meet, and whether the stunts are in the pool. STORED ONLY in
 * ChordWave: these prefs have no effect on Automix, which plans each
 * transition from its own analysis (see NEEDS.md §(b) B2) — the note
 * under the controls says so in the UI.
 */
@Composable
private fun CrossmixControls() {
    val (styleName, onStyleNameChange) = rememberPreference(CrossmixStyleKey, defaultValue = CrossmixStyleAuto)
    val (length, onLengthChange) = rememberEnumPreference(CrossmixLengthKey, defaultValue = CrossmixLength.MEDIUM)
    val (tempoMatch, onTempoMatchChange) = rememberEnumPreference(CrossmixTempoMatchKey, defaultValue = CrossmixTempoMatch.FULL)
    val (stunts, onStuntsChange) = rememberPreference(CrossmixStuntsKey, defaultValue = true)
    val (alwaysMix, onAlwaysMixChange) = rememberPreference(CrossmixAlwaysMixKey, defaultValue = true)
    val pinned = PinnableStyles.indexOfFirst { it.name == styleName }

    Spacer(Modifier.height(16.dp))
    ControlHeading(stringResource(R.string.crossmix_style_title), stringResource(R.string.crossmix_style_desc))
    Spacer(Modifier.height(12.dp))
    LabSelect(
        options = listOf(stringResource(R.string.crossmix_style_auto)) + PinnableStyles.map { it.label },
        selectedIndex = pinned + 1,
        onSelect = { onStyleNameChange(if (it == 0) CrossmixStyleAuto else PinnableStyles[it - 1].name) },
        contentDescription = stringResource(R.string.crossmix_style_choose),
    )

    Spacer(Modifier.height(18.dp))
    ControlHeading(stringResource(R.string.crossmix_length_title), stringResource(R.string.crossmix_length_desc))
    Spacer(Modifier.height(12.dp))
    LabSegmented(
        options = listOf(
            stringResource(R.string.crossmix_length_short),
            stringResource(R.string.crossmix_length_medium),
            stringResource(R.string.crossmix_length_long),
        ),
        selectedIndex = length.ordinal,
        onSelect = { onLengthChange(CrossmixLength.entries[it]) },
        modifier = Modifier.padding(horizontal = 6.dp),
    )

    Spacer(Modifier.height(18.dp))
    ControlHeading(stringResource(R.string.crossmix_tempo_title), stringResource(R.string.crossmix_tempo_desc))
    Spacer(Modifier.height(12.dp))
    LabSegmented(
        options = listOf(
            stringResource(R.string.crossmix_tempo_off),
            stringResource(R.string.crossmix_tempo_gentle),
            stringResource(R.string.crossmix_tempo_full),
        ),
        selectedIndex = tempoMatch.ordinal,
        onSelect = { onTempoMatchChange(CrossmixTempoMatch.entries[it]) },
        modifier = Modifier.padding(horizontal = 6.dp),
    )

    Spacer(Modifier.height(10.dp))
    LabToggleRow(
        title = stringResource(R.string.crossmix_always_mix_title),
        subtitle = stringResource(R.string.crossmix_always_mix_desc),
        checked = alwaysMix,
        onCheckedChange = onAlwaysMixChange,
    )
    LabToggleRow(
        title = stringResource(R.string.crossmix_stunts_title),
        subtitle = stringResource(R.string.crossmix_stunts_desc),
        checked = stunts,
        onCheckedChange = onStuntsChange,
    )
    CardNote(
        "ChordWave mixes with Automix, which plans each transition from its own analysis — " +
            "these choices are saved for a future crossmix engine.",
        hidden = false,
    )
}

@Composable
private fun ControlHeading(title: String, detail: String) {
    Column(Modifier.padding(horizontal = 6.dp)) {
        Text(title, fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = LabColors.Primary)
        Text(detail, fontFamily = InterFontFamily, fontSize = 12.sp, lineHeight = 15.sp, color = LabColors.Secondary)
    }
}
