// Ported from ChordWave Classic (GPL-3.0):
// ui/screens/DriveDevicesScreen.kt + ui/screens/DriveBackupAlerts.kt
// (the restore confirmation)
//
// ADAPTATIONS:
// - Modern look: SubSettingsScaffold + grouped SettingsGroup rows and a
//   DefaultDialog for the restore confirmation, which lives on this screen —
//   Classic raises it to MainActivity for its frosted alert, which needs the
//   app's haze; the Modern dialog doesn't.
// - Same engine: every action calls DriveBackup directly; nothing duplicated.
// - Brand: user-visible strings reuse the existing drive_* resources, which
//   carry no "Mochy"/"BitChord" wording.
package com.music.bitchord.ui.mochy.screens.settings.importsync

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.R
import com.music.bitchord.data.backup.DriveBackup
import com.music.bitchord.data.backup.RemoteDevice
import com.music.bitchord.ui.mochy.component.DefaultDialog
import com.music.bitchord.ui.mochy.component.SettingsGroup
import com.music.bitchord.ui.mochy.component.SettingsGroupStyle
import com.music.bitchord.ui.mochy.component.SettingsItem
import com.music.bitchord.ui.mochy.component.SubSettingsScaffold
import com.music.bitchord.ui.mochy.navigation.MochyNavigator
import com.music.bitchord.ui.screens.describe
import kotlinx.coroutines.launch

/**
 * Google Drive backup → which device this phone is.
 *
 * Asked once, when an account with devices in it is connected, and again
 * whenever the listener wants to restore: back up as a **new device**, whose
 * listening adds to every other device's in the Replay, or **restore as** one
 * already in Drive, which this phone then becomes — its listening and settings
 * come down, and this phone carries on writing them.
 */
@Composable
fun DriveDevicesScreen(
    navigator: MochyNavigator,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by DriveBackup.settings.collectAsStateWithLifecycle()
    val devices by DriveBackup.devices.collectAsStateWithLifecycle()
    var confirmRestore by remember { mutableStateOf<RemoteDevice?>(null) }

    LaunchedEffect(Unit) { DriveBackup.refreshDevices(context) }

    SubSettingsScaffold(
        title = stringResource(R.string.drive_devices_title),
        onBack = { navigator.popBackStack() },
    ) {
        if (settings.needsDevice) {
            SettingsGroup(
                style = SettingsGroupStyle.Grouped,
                items = listOf(
                    SettingsItem(
                        title = { Text(stringResource(R.string.drive_new_device)) },
                        highlightKey = "Back up as a new device",
                        descriptionText = DriveBackup.deviceName(),
                        icon = painterResource(R.drawable.backup),
                        iconShape = CircleShape,
                        onClick = {
                            DriveBackup.chooseNewDevice(context)
                            navigator.popBackStack()
                        },
                    ),
                ),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.drive_new_device_footer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
        }

        SettingsGroup(
            title = stringResource(R.string.drive_restore_as_header),
            style = SettingsGroupStyle.Grouped,
            items = when (val d = devices) {
                DriveBackup.Devices.Unknown, DriveBackup.Devices.Checking -> listOf(
                    SettingsItem(
                        title = { Text(stringResource(R.string.drive_devices_checking)) },
                        icon = painterResource(R.drawable.cloud),
                        iconShape = CircleShape,
                        trailingContent = {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                            )
                        },
                    ),
                )
                is DriveBackup.Devices.Failed -> listOf(
                    SettingsItem(
                        title = { Text(stringResource(R.string.drive_devices_failed, d.message)) },
                        icon = painterResource(R.drawable.warning),
                        iconShape = CircleShape,
                        onClick = { scope.launch { DriveBackup.refreshDevices(context) } },
                    ),
                )
                is DriveBackup.Devices.Loaded -> {
                    val others = d.list.filter { it.deviceId != settings.identity.deviceId }
                    if (others.isEmpty()) {
                        listOf(
                            SettingsItem(
                                title = { Text(stringResource(R.string.drive_devices_none_other)) },
                                icon = painterResource(R.drawable.cloud),
                                iconShape = CircleShape,
                            ),
                        )
                    } else {
                        others.map { device ->
                            SettingsItem(
                                title = { Text(device.name) },
                                highlightKey = "Restore as ${device.name}",
                                descriptionText = describe(device),
                                icon = painterResource(R.drawable.smartphone),
                                iconShape = CircleShape,
                                trailingContent = {
                                    TextButton(onClick = { confirmRestore = device }) {
                                        Text(stringResource(R.string.drive_restore_action))
                                    }
                                },
                                onClick = { confirmRestore = device },
                            )
                        }
                    }
                }
            },
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.drive_restore_as_footer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(32.dp))
    }

    confirmRestore?.let { device ->
        DefaultDialog(
            onDismiss = { confirmRestore = null },
            horizontalAlignment = Alignment.Start,
            title = { Text(stringResource(R.string.drive_restore_confirm_title, device.name)) },
            buttons = {
                TextButton(onClick = { confirmRestore = null }) {
                    Text(stringResource(android.R.string.cancel))
                }
                TextButton(
                    onClick = {
                        confirmRestore = null
                        DriveBackup.clearResult()
                        DriveBackup.restoreAs(context, device)
                        // Back to the backup page, where the progress bar
                        // shows the restore running.
                        navigator.popBackStack()
                    },
                ) { Text(stringResource(R.string.drive_restore_action)) }
            },
        ) {
            Text(
                stringResource(R.string.drive_restore_confirm_body, device.name),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
