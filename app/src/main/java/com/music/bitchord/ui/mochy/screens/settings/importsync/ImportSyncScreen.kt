// Ported from Mochy Music (GPL-3.0):
// ui/screens/settings/importsync/ImportSyncScreen.kt
//
// ADAPTATIONS:
// - Navigation: MochyNavigator instead of NavController (no NavHost in the
//   Modern host).
// - Devices row DROPPED: Mochy's Devices/remote-control screens are out of
//   scope for the clone (MAPPING.md), and CW has no rememberDevicesLink /
//   showDeviceIntegration. See NEEDS.md.
// - Brand: "Export to Mochy CSV" -> "Export to ChordWave CSV", export file
//   "mochy-library-…" -> "chordwave-library-…", "Mochy backup" ->
//   "ChordWave backup".
package com.music.bitchord.ui.mochy.screens.settings.importsync

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.music.bitchord.R
import com.music.bitchord.ui.mochy.component.DefaultDialog
import com.music.bitchord.ui.mochy.component.SettingsGroup
import com.music.bitchord.ui.mochy.component.SettingsGroupStyle
import com.music.bitchord.ui.mochy.component.SettingsItem
import com.music.bitchord.ui.mochy.component.SubSettingsScaffold
import com.music.bitchord.ui.mochy.navigation.MochyNavigator
import com.music.bitchord.ui.mochy.utils.rememberYouTubeSignedIn
import com.music.bitchord.ui.mochy.viewmodels.ImportSyncViewModel
import java.time.LocalDate

/** experimental hub: import from other services, sync a youtube account, tunemymusic csv, and backup */
@Composable
fun ImportSyncScreen(
    navigator: MochyNavigator,
    viewModel: ImportSyncViewModel = hiltViewModel(),
) {
    val progress by viewModel.importProgress.collectAsState()
    val exportMessage by viewModel.exportMessage.collectAsState()
    val signedIn = rememberYouTubeSignedIn()

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) viewModel.export(uri)
    }

    SubSettingsScaffold(
        title = "Import & sync",
        onBack = { navigator.popBackStack() },
    ) {
        if (progress.isRunning || progress.isDone) {
            val resync = progress.resync
            ImportInfoCard {
                Text(
                    text = when {
                        resync != null && progress.isRunning -> "Re-syncing \"${resync.playlistName}\"…"
                        progress.isRunning -> "Importing… ${progress.processedTracks} of ${progress.totalTracks}"
                        progress.cancelled -> "Import stopped"
                        resync != null && resync.failed.size < resync.playlists ->
                            (if (resync.playlists == 1) "Re-synced \"${resync.playlistName}\": " else "Re-synced ${resync.playlists} playlists: ") +
                                if (resync.added == 1) "1 new song" else "${resync.added} new songs"
                        progress.error != null -> "Import failed"
                        else -> "Import finished: ${progress.matchedTracks} of ${progress.totalTracks} songs"
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
                if (progress.isRunning && progress.totalTracks > 0) {
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { progress.processedTracks.toFloat() / progress.totalTracks },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            SettingsGroup(
                style = SettingsGroupStyle.Grouped,
                items = listOf(
                    SettingsItem(
                        title = { Text(if (progress.isRunning) "See progress" else "See results") },
                        icon = painterResource(R.drawable.playlist_play),
                        iconShape = CircleShape,
                        onClick = { navigator.navigate(ImportProgressRoute) },
                    )
                ),
            )
            Spacer(Modifier.height(16.dp))
        }

        SettingsGroup(
            style = SettingsGroupStyle.Grouped,
            items = listOf(
                SettingsItem(
                    title = { Text("Import") },
                    highlightKey = "Import",
                    descriptionText = "Sign in and pick playlists",
                    detail = "Sign in to Spotify, Apple Music, YouTube Music or Tidal and pick playlists",
                    icon = painterResource(R.drawable.playlist_add),
                    iconShape = CircleShape,
                    onClick = { navigator.navigate(ImportProvidersRoute) },
                ),
                SettingsItem(
                    title = { Text("Sync") },
                    highlightKey = "Sync",
                    descriptionText = if (signedIn) "YouTube account connected: playlists, likes and history"
                    else "Connect your YouTube account to keep playlists, likes and history in step",
                    icon = painterResource(R.drawable.sync),
                    iconShape = CircleShape,
                    onClick = { navigator.navigate(YouTubeSyncRoute) },
                ),
                SettingsItem(
                    title = { Text("TuneMyMusic CSV") },
                    highlightKey = "TuneMyMusic CSV",
                    descriptionText = "Amazon Music, SoundCloud, Deezer and more",
                    detail = "Any other service, such as Amazon Music, SoundCloud or Deezer libraries",
                    icon = painterResource(R.drawable.web_link),
                    iconShape = CircleShape,
                    onClick = { navigator.navigate(ImportTuneMyMusicRoute) },
                ),
            ),
        )

        Spacer(Modifier.height(16.dp))

        SettingsGroup(
            title = "Backup",
            style = SettingsGroupStyle.Grouped,
            items = listOf(
                SettingsItem(
                    title = { Text("Export to ChordWave CSV") },
                    highlightKey = "Export to ChordWave CSV",
                    descriptionText = "Liked songs and playlists",
                    detail = "Liked songs and playlists, import it back from Import → ChordWave backup",
                    icon = painterResource(R.drawable.backup),
                    iconShape = CircleShape,
                    onClick = { exportLauncher.launch("chordwave-library-${LocalDate.now()}.csv") },
                ),
            ),
        )
    }

    exportMessage?.let { message ->
        val working = message == "Exporting…"
        DefaultDialog(
            onDismiss = { if (!working) viewModel.clearExportMessage() },
            buttons = {
                if (!working) TextButton(onClick = viewModel::clearExportMessage) { Text("OK") }
            },
        ) {
            Text(message, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
