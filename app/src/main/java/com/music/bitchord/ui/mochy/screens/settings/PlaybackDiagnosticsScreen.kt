// Ported from Mochy Music (GPL-3.0): ui/screens/settings/PlaybackDiagnosticsScreen.kt
//
// ADAPTATIONS — rewired to what ChordWave can actually measure (the
// StreamStatsAdapter pattern from Phase 3):
// - Mochy's screen shows PoToken state + a PlaybackLogManager log
//   (per-track client + "Stream validated" entries). ChordWave has NO
//   PoToken concept and keeps NO playback log, so both are gone —
//   honestly, not faked. In their place the card shows the LIVE stream
//   snapshot from NerdStats.current (via rememberNerdSnapshot()): source
//   attribution via StreamStatsAdapter.playingFrom(), codec / sample
//   rate / bitrate / channels from the measured snapshot, the active
//   output device via StreamStatsAdapter.activeOutputDevice(), the
//   equalizer band count and float-output permission, plus Automix's own
//   detail line (AppSettings.automixDetail) when a mix is planned.
// - "Copy logs" becomes "Copy diagnostics": it copies the summary shown
//   on screen (real function, honest label). "Clear logs" is DROPPED —
//   there is no log to clear. The log list is replaced by a short note
//   saying ChordWave keeps no playback log and the snapshot refreshes
//   live. See NEEDS.md §(b) B4.
// - NavController -> MochyNavigator; clipboard label rebranded to
//   "chordwave playback diagnostics".

package com.music.bitchord.ui.mochy.screens.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.music.bitchord.R
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.ui.mochy.component.SettingsGroup
import com.music.bitchord.ui.mochy.component.SettingsGroupStyle
import com.music.bitchord.ui.mochy.component.SettingsItem
import com.music.bitchord.ui.mochy.component.SubSettingsScaffold
import com.music.bitchord.ui.mochy.models.MediaMetadata
import com.music.bitchord.ui.mochy.navigation.MochyNavigator
import com.music.bitchord.ui.mochy.playback.LocalPlayerConnection
import com.music.bitchord.ui.mochy.player.info.StreamStatsAdapter
import com.music.bitchord.ui.mochy.player.info.rememberNerdSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@Composable
fun PlaybackDiagnosticsScreen(navigator: MochyNavigator) {
    val context = LocalContext.current
    val snapshot by rememberNerdSnapshot()
    val playerConnection = LocalPlayerConnection.current
    val metadataFlow: StateFlow<MediaMetadata?> = remember(playerConnection) {
        playerConnection?.mediaMetadata ?: MutableStateFlow(null)
    }
    val song by metadataFlow.collectAsState()
    val automixDetail by AppSettings.automixDetail.collectAsState()

    val playingFrom = remember(song, snapshot) {
        val current = song
        if (current != null) StreamStatsAdapter.playingFrom(current, snapshot).name
        else snapshot?.sourceName?.takeIf { it.isNotBlank() && it != "Unknown" } ?: "—"
    }
    val facts = remember(snapshot) { StreamStatsAdapter.streamFacts(snapshot) }
    // read fresh every composition: the route can change without the snapshot moving
    val outputDevice = StreamStatsAdapter.activeOutputDevice(context)
    val equalizerBands = StreamStatsAdapter.equalizerBands()

    val summary = remember(snapshot, playingFrom, facts, outputDevice, equalizerBands, automixDetail) {
        buildString {
            appendLine("ChordWave playback diagnostics")
            appendLine("Source: $playingFrom")
            appendLine("Codec: ${facts?.codec ?: "—"}")
            appendLine("Sample rate: ${snapshot?.sampleRateHz?.let { "${it / 1_000} kHz" } ?: "—"}")
            appendLine("Bitrate: ${snapshot?.bitrateKbps?.let { "$it kbps" } ?: "—"}")
            appendLine("Channels: ${snapshot?.channels?.toString() ?: "—"}")
            appendLine("Output: ${outputDevice?.name ?: "—"}")
            appendLine("Equalizer: ${equalizerBands?.let { "$it bands" } ?: "Off"}")
            appendLine("Float output: ${if (StreamStatsAdapter.floatAllowed()) "Allowed" else "Off"}")
            automixDetail?.let { appendLine("Automix: $it") }
        }
    }

    SubSettingsScaffold(
        title = "Playback diagnostics",
        onBack = { navigator.popBackStack() },
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = if (snapshot != null) "Stream active" else "Nothing playing",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (snapshot != null) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )

                Spacer(Modifier.height(8.dp))

                DiagnosticsRow("Source", playingFrom)
                DiagnosticsRow("Codec", facts?.codec ?: "—")
                DiagnosticsRow("Sample rate", snapshot?.sampleRateHz?.let { "${it / 1_000} kHz" } ?: "—")
                DiagnosticsRow("Bitrate", snapshot?.bitrateKbps?.let { "$it kbps" } ?: "—")
                DiagnosticsRow("Channels", snapshot?.channels?.toString() ?: "—")
                DiagnosticsRow("Output device", outputDevice?.name ?: "—")
                DiagnosticsRow("Equalizer", equalizerBands?.let { "$it bands" } ?: "Off")
                DiagnosticsRow("Float output", if (StreamStatsAdapter.floatAllowed()) "Allowed" else "Off")
                automixDetail?.let { DiagnosticsRow("Automix", it) }
            }
        }

        SettingsGroup(
            style = SettingsGroupStyle.Grouped,
            items = listOf(
                SettingsItem(
                    title = { Text("Copy diagnostics") },
                    descriptionText = "Copy the summary above to the clipboard",
                    icon = painterResource(R.drawable.key),
                    iconShape = CircleShape,
                    onClick = {
                        copyDiagnostics(context, summary)
                        Toast.makeText(context, "Diagnostics copied", Toast.LENGTH_SHORT).show()
                    },
                ),
            ),
        )

        Text(
            text = "ChordWave keeps no playback log. The snapshot above refreshes live from the playing stream.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
        )

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun DiagnosticsRow(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
        )
    }
}

private fun copyDiagnostics(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText("chordwave playback diagnostics", text))
}
