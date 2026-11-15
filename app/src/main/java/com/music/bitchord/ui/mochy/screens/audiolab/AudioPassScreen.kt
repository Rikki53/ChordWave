// dac's stuff, audio pass's page: the dac's or headphones' details, the output switches and the bit-perfect modes
//
// Ported from Mochy Music (GPL-3.0): ui/screens/audiolab/AudioPassScreen.kt,
// rewired to ChordWave's real output stack (see NEEDS.md):
// - No UsbDacReader / BluetoothCodecWatcher / AudioPass engine in
//   ChordWave: everything reads from AudioOutputStatus (route, DAC rates,
//   mixer rate, Bluetooth telemetry) and BitPerfectOutput.status.
// - The three modes are CW's BitPerfectMode (USB_DIRECT / USB_ANDROID /
//   DAP_EXCLUSIVE) with CW's own availability rules (USB_ANDROID needs
//   Android 14, DAP_EXCLUSIVE needs 8.1, Direct USB any version). Mochy's
//   Song's rate and Exclusive USB rows have no CW engine and are not shown.
// - Passthrough on/off IS the mode (OFF = off); the last non-off mode is
//   remembered so switching back on restores it.
// - Hi-res output (32-bit) is the real OutputPcmMode FLOAT_32 switch;
//   the Hi-fi resampler has no engine and is shown disabled.
// - Direct USB volume goes through DacVolume (the DAC's own steps); USB
//   permission is read once from UsbManager (no live permission flow —
//   see NEEDS.md). The fixed-volume toggle reuses the classic confirm
//   strings in a PopupSheet.
// - The bit-perfect / untouched distinction needs a source-lossless
//   signal ChordWave doesn't expose here, so an exact stream always
//   reads as bit-perfect (see NEEDS.md).
package com.music.bitchord.ui.mochy.screens.audiolab

import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.R
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.BitPerfectMode
import com.music.bitchord.data.settings.OutputPcmMode
import com.music.bitchord.playback.AudioOutputStatus
import com.music.bitchord.playback.AudioRouting
import com.music.bitchord.playback.audio.bitperfect.BitPerfectOutput
import com.music.bitchord.playback.audio.bitperfect.DacVolume
import com.music.bitchord.playback.audio.bitperfect.usb.UsbDirectDevices
import com.music.bitchord.playback.audio.bluetooth.BluetoothTelemetry
import com.music.bitchord.ui.mochy.component.AppSwitch
import com.music.bitchord.ui.mochy.component.LocalMenuState
import com.music.bitchord.ui.mochy.component.LocalZoomOutOverlayState
import com.music.bitchord.ui.mochy.navigation.MochyNavigator
import com.music.bitchord.ui.mochy.playback.LocalPlayerConnection
import com.music.bitchord.ui.mochy.playback.audio.pass.PassBlocker
import com.music.bitchord.ui.mochy.screens.update.SheetPrimaryButton
import com.music.bitchord.ui.mochy.theme.InterFontFamily
import com.music.bitchord.ui.mochy.viewmodels.AudioLabViewModel
import java.util.Locale

/** what the status card is about: whichever output plays now, with a plugged-in dac first when nothing else is */
private sealed interface Shown {
    data class Dac(val output: AudioOutputStatus.Snapshot) : Shown
    data class Bluetooth(val telemetry: BluetoothTelemetry?, val profile: String?, val deviceName: String) : Shown
    data object Wired : Shown
    data object Nothing : Shown
}

@Composable
fun AudioPassScreen(
    navigator: MochyNavigator,
    viewModel: AudioLabViewModel = hiltViewModel(),
) {
    val output by AudioOutputStatus.current.collectAsStateWithLifecycle()
    val mode by AppSettings.bitPerfectMode.collectAsStateWithLifecycle()
    val passStatus by BitPerfectOutput.status.collectAsStateWithLifecycle()
    val vmBlockers by viewModel.passBlockers.collectAsState()
    val canRestore by viewModel.canRestorePassBlockers.collectAsState()
    val musicVolume = LocalPlayerConnection.current?.musicVolume?.collectAsState()
    val volume = musicVolume?.value
    val blockers = remember(vmBlockers, volume) {
        if (volume != null && volume < 1f) vmBlockers + PassBlocker.VOLUME else vmBlockers
    }

    // the last mode that wasn't off, so switching passthrough back on restores it
    var lastMode by remember { mutableStateOf(BitPerfectMode.USB_DIRECT) }
    LaunchedEffect(mode) { if (mode != BitPerfectMode.OFF) lastMode = mode }
    val passthrough = mode != BitPerfectMode.OFF
    val driven = mode == BitPerfectMode.USB_DIRECT && passStatus.active

    val shown = when {
        output.routeKind == AudioRouting.Kind.BLUETOOTH -> Shown.Bluetooth(output.bluetoothTelemetry, output.bluetoothProfile, output.deviceName)
        output.isUsb -> Shown.Dac(output)
        output.routeKind == AudioRouting.Kind.WIRED -> Shown.Wired
        else -> Shown.Nothing
    }

    AudioLabPage(
        title = LabTitle(
            stringResource(R.string.audiopass_title_first),
            stringResource(R.string.audiopass_title_second),
            firstSize = 64.sp,
            secondSize = 30.sp,
            secondDim = true,
            secondBadge = stringResource(R.string.audiopass_beta),
        ),
        onBack = { navigator.popBackStack() },
        subtitle = subtitleFor(shown),
    ) {
        StatusCard(shown, output.systemMixerRateHz, driven)

        Spacer(Modifier.height(24.dp))
        LabSheetTitle(stringResource(R.string.audiopass_options))
        val preferUsb by AppSettings.preferUsbDac.collectAsStateWithLifecycle()
        val pcmMode by AppSettings.outputPcmMode.collectAsStateWithLifecycle()
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OptionTile(
                stringResource(R.string.audiopass_prefer_usb), preferUsb,
                { on -> AppSettings.setPreferUsbDac(on) }, Modifier.weight(1f).fillMaxHeight(),
            )
            OptionTile(
                stringResource(R.string.audiopass_hires), pcmMode == OutputPcmMode.FLOAT_32,
                { on -> AppSettings.setOutputPcmMode(if (on) OutputPcmMode.FLOAT_32 else OutputPcmMode.PCM_16) },
                Modifier.weight(1f).fillMaxHeight(),
            )
        }

        Spacer(Modifier.height(24.dp))
        LabSheetTitle(stringResource(R.string.audiopass_exclusive))
        LabCard(padding = 10.dp) {
            // No resampler engine in ChordWave: visibly disabled.
            LabToggleRow(
                title = stringResource(R.string.audiopass_resampler),
                subtitle = stringResource(R.string.audiopass_resampler_desc),
                checked = false,
                onCheckedChange = {},
                enabled = false,
            )
            Note(stringResource(R.string.audiolab_no_engine_resampler))
        }
        Spacer(Modifier.height(12.dp))
        // switching it on while something changes the sound offers to turn those off then and there
        val overlay = LocalZoomOutOverlayState.current
        val offerToClear = {
            if (blockers.isNotEmpty()) overlay.showPassBlockersOffer(blockers) { viewModel.clearPassBlockers() }
        }
        PassthroughCard(
            on = passthrough,
            onChange = { turnedOn ->
                if (turnedOn) {
                    AppSettings.setBitPerfectMode(lastMode)
                    offerToClear()
                } else {
                    AppSettings.setBitPerfectMode(BitPerfectMode.OFF)
                }
            },
            mode = if (passthrough) mode else lastMode,
            onMode = { picked -> AppSettings.setBitPerfectMode(picked) },
            status = passStatus.takeIf { passthrough },
            deviceName = output.deviceName,
        )

        LabCollapsible(passthrough && mode == BitPerfectMode.USB_DIRECT) {
            Spacer(Modifier.height(12.dp))
            DirectUsbCard(passStatus.active)
        }
        LabCollapsible(passthrough) {
            Spacer(Modifier.height(12.dp))
            BlockersCard(
                active = blockers,
                lossyToo = mode == BitPerfectMode.USB_DIRECT,
                canRestore = canRestore,
                onClear = { viewModel.clearPassBlockers() },
                onRestore = { viewModel.restorePassBlockers() },
            )
        }
    }
}

@Composable
private fun subtitleFor(shown: Shown): String = when (shown) {
    is Shown.Dac -> stringResource(R.string.audiopass_joined, shown.output.deviceName, stringResource(R.string.audiopass_kind_usb))
    is Shown.Bluetooth -> stringResource(R.string.audiopass_status_bluetooth)
    Shown.Wired -> stringResource(R.string.audiopass_value_jack)
    Shown.Nothing -> stringResource(R.string.audiopass_kind_speaker)
}

// ---- status ------------------------------------------------------------------------------

@Composable
private fun StatusCard(shown: Shown, mixerRate: Int?, driven: Boolean) {
    val status = when (shown) {
        is Shown.Dac -> if (driven) R.string.audiopass_status_driven else R.string.audiopass_status_connected
        is Shown.Bluetooth -> R.string.audiopass_status_bluetooth
        Shown.Wired -> R.string.audiopass_status_wired
        Shown.Nothing -> R.string.audiopass_status_none
    }
    LabCard(padding = 20.dp) {
        Text(
            stringResource(R.string.audiopass_status_label),
            fontFamily = InterFontFamily, fontWeight = FontWeight.Bold, fontSize = 20.sp,
            letterSpacing = (-0.02).em, color = Color(0xFF8E8E93),
        )
        Text(
            stringResource(status),
            fontFamily = InterFontFamily, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 38.sp,
            letterSpacing = (-0.03).em, color = Color.White,
        )
        Spacer(Modifier.height(14.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(LabColors.StageLine))
        Spacer(Modifier.height(14.dp))
        when (shown) {
            is Shown.Dac -> InfoRows(dacRows(shown.output, mixerRate))
            is Shown.Bluetooth -> BluetoothDetails(shown.telemetry, shown.profile, shown.deviceName)
            Shown.Wired -> InfoRows(listOfNotNull(
                stringResource(R.string.audiopass_info_output) to stringResource(R.string.audiopass_value_jack),
                mixerRate?.let { stringResource(R.string.audiopass_info_mixer) to stringResource(R.string.audiopass_value_khz, khz(it)) },
            ))
            Shown.Nothing -> {
                Note(stringResource(R.string.audiopass_status_none_hint))
                mixerRate?.let {
                    Spacer(Modifier.height(12.dp))
                    InfoRows(listOf(stringResource(R.string.audiopass_info_mixer) to stringResource(R.string.audiopass_value_khz, khz(it))))
                }
            }
        }
    }
}

@Composable
private fun dacRows(output: AudioOutputStatus.Snapshot, mixerRate: Int?): List<Pair<String, String>> {
    val rates = output.sampleRatesHz.toList()
    return listOfNotNull(
        stringResource(R.string.audiopass_info_dac) to output.deviceName,
        stringResource(R.string.audiopass_info_takes) to
            if (rates.isEmpty()) stringResource(R.string.audiopass_value_any_rate)
            else stringResource(R.string.audiopass_value_khz, rateSpan(rates)),
        stringResource(R.string.audiopass_info_bitperfect) to
            stringResource(if (output.directPlaybackSupported) R.string.audiopass_value_yes else R.string.audiopass_value_not_offered),
        mixerRate?.let { stringResource(R.string.audiopass_info_mixer) to stringResource(R.string.audiopass_value_khz, khz(it)) },
        stringResource(R.string.audiopass_info_exact) to
            if (output.outputExact) stringResource(R.string.audiopass_value_yes)
            else output.outputExactDetail ?: stringResource(R.string.audiopass_value_no),
    )
}

@Composable
private fun ColumnScope.BluetoothDetails(telemetry: BluetoothTelemetry?, profile: String?, deviceName: String) {
    // ChordWave's telemetry has no LDAC level or codec alternatives, and no
    // permission flow — what Android reported is shown as it is.
    when {
        profile == "LE Audio" -> {
            InfoRows(listOf(
                stringResource(R.string.audiopass_info_headphones) to (telemetry?.deviceName ?: deviceName),
                stringResource(R.string.audiopass_info_codec) to stringResource(R.string.audiopass_value_le_audio),
            ))
            Spacer(Modifier.height(14.dp))
            Note(stringResource(R.string.audiopass_bt_reencoded))
        }
        telemetry != null && telemetry.hasNamedCodec -> {
            InfoRows(bluetoothRows(telemetry))
            Spacer(Modifier.height(14.dp))
            Note(stringResource(R.string.audiopass_bt_reencoded))
        }
        else -> {
            InfoRows(listOf(
                stringResource(R.string.audiopass_info_headphones) to (telemetry?.deviceName ?: deviceName),
                stringResource(R.string.audiopass_info_codec) to stringResource(R.string.audiopass_value_unknown),
            ))
            Spacer(Modifier.height(14.dp))
            Note(stringResource(R.string.audiopass_bt_unknown_hint))
        }
    }
}

@Composable
private fun bluetoothRows(telemetry: BluetoothTelemetry): List<Pair<String, String>> = listOfNotNull(
    stringResource(R.string.audiopass_info_headphones) to (telemetry.deviceName ?: stringResource(R.string.audiopass_value_unknown)),
    stringResource(R.string.audiopass_info_codec) to listOfNotNull(
        telemetry.codecName,
        telemetry.sampleRateHz?.let { stringResource(R.string.audiopass_value_khz, khz(it)) },
        telemetry.bitDepth?.let { stringResource(R.string.audiopass_value_bits, it.toString()) },
    ).joinToString(" · "),
    stringResource(R.string.audiopass_info_bitrate) to telemetry.bitrateLabel,
)

/** a key and its value per line, in the monospace the mocks use for raw facts */
@Composable
private fun InfoRows(rows: List<Pair<String, String>>) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        rows.forEach { (key, value) ->
            Row {
                Text(
                    key.uppercase(),
                    fontFamily = FontFamily.Monospace, fontSize = 10.sp, lineHeight = 15.sp, letterSpacing = 0.04.em,
                    color = LabColors.Faint, modifier = Modifier.width(104.dp),
                )
                Text(value, fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 15.sp, color = Color(0xFFD1D1D6))
            }
        }
    }
}

@Composable
private fun Note(text: String, color: Color = LabColors.Secondary) {
    Text(text, fontFamily = InterFontFamily, fontSize = 12.sp, lineHeight = 16.sp, color = color)
}

@Composable
private fun PillButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (enabled) Color(0xFF3A3A3F) else LabColors.Chip)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            text, fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 12.sp,
            color = if (enabled) Color.White else LabColors.Faint,
        )
    }
}

// ---- options -----------------------------------------------------------------------------

/** half-width card with its name over a switch, two to a row as in the mock */
@Composable
private fun OptionTile(title: String, checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(26.dp))
            .background(LabColors.Card)
            .clickable { onChange(!checked) }
            .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        Text(title, fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 19.sp, color = Color.White)
        Spacer(Modifier.weight(1f).heightIn(min = 12.dp))
        AppSwitch(checked = checked, onCheckedChange = onChange)
    }
}

// ---- passthrough -------------------------------------------------------------------------

/** one of ChordWave's exclusive modes: its minimum Android version, if it has one, and its strings */
private class PassModeRow(
    val mode: BitPerfectMode,
    val minSdk: Int?,
    @StringRes val title: Int,
    @StringRes val description: Int,
)

private val PassModes = listOf(
    PassModeRow(BitPerfectMode.USB_DIRECT, null, R.string.audiopass_mode_direct_usb, R.string.audiopass_mode_direct_usb_desc),
    PassModeRow(BitPerfectMode.USB_ANDROID, 34, R.string.audiopass_mode_bit_perfect, R.string.audiopass_mode_bit_perfect_desc),
    PassModeRow(BitPerfectMode.DAP_EXCLUSIVE, 27, R.string.audiopass_mode_exclusive_dac, R.string.audiopass_mode_exclusive_dac_desc),
)

/** whether this phone can use this mode at all — ChordWave's own rules, as in the classic settings */
private val BitPerfectMode.isSupported: Boolean
    get() = when (this) {
        BitPerfectMode.OFF -> true
        BitPerfectMode.USB_ANDROID -> Build.VERSION.SDK_INT >= 34
        BitPerfectMode.DAP_EXCLUSIVE -> Build.VERSION.SDK_INT >= 27
        BitPerfectMode.USB_DIRECT -> true
    }

@Composable
private fun PassthroughCard(
    on: Boolean,
    onChange: (Boolean) -> Unit,
    mode: BitPerfectMode,
    onMode: (BitPerfectMode) -> Unit,
    status: BitPerfectOutput.Status?,
    deviceName: String,
) {
    LabCard(padding = 10.dp) {
        LabToggleRow(
            title = stringResource(R.string.audiopass_passthrough),
            subtitle = stringResource(R.string.audiopass_passthrough_desc),
            checked = on,
            onCheckedChange = onChange,
        )
        LabCollapsible(on) {
            Spacer(Modifier.height(8.dp))
            LabPanel {
                Text(
                    stringResource(R.string.audiopass_modes).uppercase(),
                    fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, letterSpacing = 1.3.sp,
                    color = LabColors.Secondary,
                )
                PassModes.forEach { option ->
                    val available = option.mode.isSupported
                    QualityRow(
                        title = stringResource(option.title),
                        description = if (available) stringResource(option.description)
                            else stringResource(R.string.audiopass_mode_needs, androidName(option.minSdk ?: 0)),
                        selected = option.mode == mode,
                        enabled = available,
                        onClick = { onMode(option.mode) },
                    )
                }
            }
            statusLine(status, deviceName)?.let { line ->
                Spacer(Modifier.height(10.dp))
                Box(Modifier.padding(horizontal = 6.dp)) { Note(line, color = Color(0xFFD1D1D6)) }
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

/** what the exclusive output is doing with the song playing now, in a sentence */
@Composable
internal fun statusLine(status: BitPerfectOutput.Status?, deviceName: String): String? {
    if (status == null) return null
    val device = status.deviceName ?: deviceName
    return when {
        !status.active && status.fallbackReason != null ->
            stringResource(R.string.audiopass_now_fell, khz(status.sourceRateHz), status.sourceBits.toString())
        !status.active -> stringResource(R.string.audiopass_now_waiting)
        status.dacLimited -> stringResource(
            R.string.bit_perfect_status_dac_limited,
            device,
            String.format(Locale.ROOT, "%.1f", status.sampleRateHz / 1000.0),
            status.outputBits,
        )
        !status.exact -> stringResource(R.string.audiopass_now_inexact, device)
        else -> stringResource(
            R.string.audiopass_now_bitperfect,
            device,
            khz(status.sampleRateHz),
            status.outputBits.toString(),
        )
    }
}

// ---- direct usb --------------------------------------------------------------------------

@Composable
private fun DirectUsbCard(passActive: Boolean) {
    val context = LocalContext.current
    val menu = LocalMenuState.current
    val fixedVolume by AppSettings.usbAllowFixedVolume.collectAsStateWithLifecycle()
    val hwVolume by BitPerfectOutput.hardwareVolume.collectAsStateWithLifecycle()
    val dacLevel by DacVolume.level.collectAsStateWithLifecycle()

    // USB permission has no live flow in ChordWave: read it when this card
    // (re)composes — playback starting re-reads it.
    val manager = remember(context) { UsbDirectDevices.manager(context) }
    val device = remember(manager, passActive) { manager?.let { UsbDirectDevices.audioDevice(it) } }
    val permitted = remember(manager, device) { device?.let { manager?.hasPermission(it) } }

    LabCard(padding = 10.dp) {
        Column(Modifier.padding(horizontal = 6.dp, vertical = 6.dp)) {
            Text(
                stringResource(R.string.audiopass_usb_title),
                fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Color.White,
            )
            Spacer(Modifier.height(4.dp))
            when (permitted) {
                null -> Note(stringResource(R.string.audiopass_usb_no_dac))
                false -> {
                    Note(stringResource(R.string.audiopass_usb_permission_hint))
                    Spacer(Modifier.height(10.dp))
                    PillButton(stringResource(R.string.audiopass_usb_permission)) {
                        UsbDirectDevices.requestPermission(context)
                    }
                }
                true -> Unit
            }
        }
        val snapshot = hwVolume
        if (snapshot != null && snapshot.available) {
            Spacer(Modifier.height(6.dp))
            LabPanel {
                // the bar moves in the DAC's own steps, read back after each change
                val range = (snapshot.maxDb - snapshot.minDb).coerceAtLeast(0.01)
                val fromDb = ((snapshot.currentDb - snapshot.minDb) / range).toFloat().coerceIn(0f, 1f)
                LabSlider(
                    label = stringResource(R.string.audiopass_dac_volume),
                    valueText = stringResource(
                        R.string.audiopass_value_db,
                        String.format(Locale.ROOT, "%.1f", snapshot.currentDb),
                    ),
                    value = dacLevel ?: fromDb,
                    onValueChange = { level -> DacVolume.set(level) },
                    valueRange = 0f..1f,
                )
                Note(stringResource(R.string.audiopass_dac_volume_desc))
            }
        }
        LabToggleRow(
            title = stringResource(R.string.usb_allow_fixed_volume),
            subtitle = stringResource(R.string.usb_allow_fixed_volume_subtitle),
            checked = fixedVolume,
            onCheckedChange = { on ->
                if (on) {
                    // full scale can be dangerously loud: ask first, as the classic settings do
                    menu.show {
                        Text(
                            stringResource(R.string.usb_allow_fixed_volume_confirm_title),
                            fontFamily = InterFontFamily, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White,
                        )
                        Spacer(Modifier.height(8.dp))
                        Note(stringResource(R.string.usb_allow_fixed_volume_confirm_message))
                        Spacer(Modifier.height(16.dp))
                        SheetPrimaryButton(stringResource(R.string.audiopass_fixed_volume_allow)) {
                            AppSettings.setUsbAllowFixedVolume(true)
                            menu.dismiss()
                        }
                        Spacer(Modifier.height(10.dp))
                        PillButton(stringResource(R.string.cancel)) { menu.dismiss() }
                    }
                } else {
                    AppSettings.setUsbAllowFixedVolume(false)
                }
            },
        )
    }
}

internal fun androidName(sdk: Int): String = when (sdk) {
    27 -> "8.1"
    34 -> "14"
    else -> "7"
}

// ---- blockers ----------------------------------------------------------------------------

@Composable
private fun BlockersCard(active: Set<PassBlocker>, lossyToo: Boolean, canRestore: Boolean, onClear: () -> Unit, onRestore: () -> Unit) {
    LabCard(padding = 20.dp) {
        Row(verticalAlignment = Alignment.Top) {
            Text(
                stringResource(if (active.isEmpty()) R.string.audiopass_blockers_clear_title else R.string.audiopass_blockers_title),
                fontFamily = InterFontFamily, fontWeight = FontWeight.Bold, fontSize = 17.sp, lineHeight = 21.sp,
                color = Color.White, modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(10.dp))
            PillButton(stringResource(R.string.audiopass_blockers_button), enabled = active.isNotEmpty(), onClick = onClear)
        }
        Spacer(Modifier.height(12.dp))
        PassBlocker.entries.forEach { blocker ->
            val on = blocker in active
            Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(if (on) Color.White else LabColors.Faint))
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(blocker.label),
                    fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                    color = if (on) Color.White else LabColors.Faint,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Note(stringResource(if (lossyToo) R.string.audiopass_blocker_lossy_direct else R.string.audiopass_blocker_lossy))
        // what "turn these off" switched off, waiting to come back exactly as it was
        if (canRestore) {
            Spacer(Modifier.height(14.dp))
            PillButton(stringResource(R.string.audiopass_blockers_restore), onClick = onRestore)
        }
    }
}

internal val PassBlocker.label: Int
    @StringRes get() = when (this) {
        PassBlocker.EQUALIZER -> R.string.audiopass_blocker_equalizer
        PassBlocker.AUDIO_FX -> R.string.audiopass_blocker_fx
        PassBlocker.VOLUME -> R.string.audiopass_blocker_volume
        PassBlocker.NORMALIZATION -> R.string.audiopass_blocker_normalization
        PassBlocker.SPEED -> R.string.audiopass_blocker_speed
        PassBlocker.TRANSITIONS -> R.string.audiopass_blocker_transitions
        PassBlocker.SILENCE_OR_HAPTICS -> R.string.audiopass_blocker_silence
    }

// ---- numbers -----------------------------------------------------------------------------

internal fun khz(rate: Int): String =
    if (rate % 1000 == 0) "${rate / 1000}" else String.format(Locale.ROOT, "%.1f", rate / 1000.0)

/** "44.1–384", or one number when there's only one */
private fun rateSpan(rates: List<Int>): String {
    val low = rates.minOrNull() ?: return ""
    val high = rates.max()
    return if (low == high) khz(low) else "${khz(low)}–${khz(high)}"
}
