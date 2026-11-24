// Ported from ChordWave Classic (GPL-3.0):
// ui/screens/DriveBackupScreen.kt + ui/screens/DriveBackupAlerts.kt
//
// ADAPTATIONS:
// - Modern look: SubSettingsScaffold + grouped SettingsGroup rows, AppSwitch,
//   DefaultDialog/TextFieldDialog. The confirmations live on this screen —
//   Classic raises them to MainActivity for its frosted alerts, which need the
//   app's haze; the Modern dialogs don't.
// - Same engine: every action calls DriveBackup/DriveAuth directly; nothing
//   duplicated. The sign-in consent flow is kept verbatim from Classic.
// - Brand: user-visible strings reuse the existing drive_* resources, which
//   carry no "Mochy"/"BitChord" wording.
package com.music.bitchord.ui.mochy.screens.settings.importsync

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.music.bitchord.R
import com.music.bitchord.data.backup.BackupFrequency
import com.music.bitchord.data.backup.DriveAuth
import com.music.bitchord.data.backup.DriveBackup
import com.music.bitchord.data.backup.RemoteDevice
import com.music.bitchord.data.backup.ReplaySync
import com.music.bitchord.ui.mochy.component.AppSwitch
import com.music.bitchord.ui.mochy.component.DefaultDialog
import com.music.bitchord.ui.mochy.component.SettingsGroup
import com.music.bitchord.ui.mochy.component.SettingsGroupStyle
import com.music.bitchord.ui.mochy.component.SettingsItem
import com.music.bitchord.ui.mochy.component.SubSettingsScaffold
import com.music.bitchord.ui.mochy.component.TextFieldDialog
import com.music.bitchord.ui.mochy.navigation.MochyNavigator
import com.music.bitchord.ui.screens.DriveProgressBar
import com.music.bitchord.ui.screens.countOfMonths
import com.music.bitchord.ui.screens.describe
import com.music.bitchord.ui.screens.formatWhen
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Import & sync → Backup → Google Drive backup: the account, the last sync
 * and a progress bar while one runs, which device this phone is, how often it
 * syncs, and every device whose listening adds up in Replay.
 *
 * A sync or restore keeps going if the page is left: it runs on
 * [DriveBackup]'s own scope, and the bar picks it up again on coming back.
 */
@Composable
fun DriveBackupScreen(
    navigator: MochyNavigator,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by DriveBackup.settings.collectAsStateWithLifecycle()
    val progress by DriveBackup.progress.collectAsStateWithLifecycle()
    val devices by DriveBackup.devices.collectAsStateWithLifecycle()
    val result by DriveBackup.result.collectAsStateWithLifecycle()
    val available = remember { DriveAuth.available(context) }

    var connectError by remember { mutableStateOf<String?>(null) }
    // What to do once the consent sheet is answered: connecting is its own
    // errand, but "Sync now" on a lost grant should carry on afterwards.
    var afterSignIn by remember { mutableStateOf<(() -> Unit)?>(null) }
    var confirmDisconnect by remember { mutableStateOf(false) }
    var removeDevice by remember { mutableStateOf<RemoteDevice?>(null) }
    var renaming by remember { mutableStateOf<String?>(null) }

    // After connecting: a Drive with devices in it means this phone has to be
    // told which it is, so the chooser opens straight away — once, from here,
    // rather than from an effect that would reopen it on every return.
    val afterConnect: () -> Unit = {
        if (DriveBackup.settings.value.needsDevice) navigator.navigate(DriveDevicesRoute)
    }

    val consent = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { answer ->
        val then = afterSignIn
        afterSignIn = null
        // Backed out of the sheet with nothing to say: nothing to report. A
        // sheet that failed instead (an unregistered app, a network error)
        // still comes back cancelled, but with the reason in its data.
        if (answer.resultCode != Activity.RESULT_OK && answer.data == null) {
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            try {
                DriveBackup.finishSignIn(context, answer.data)
                then?.invoke()
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiException) {
                if (e.statusCode != CommonStatusCodes.CANCELED) {
                    connectError = e.message ?: context.getString(R.string.unknown_error)
                }
            } catch (e: Exception) {
                connectError = e.message ?: context.getString(R.string.unknown_error)
            }
        }
    }

    fun signIn(then: () -> Unit) {
        connectError = null
        scope.launch {
            try {
                when (val outcome = DriveBackup.beginSignIn(context)) {
                    is DriveAuth.Outcome.Granted -> {
                        DriveBackup.signedIn(context, outcome.accessToken)
                        then()
                    }
                    is DriveAuth.Outcome.NeedsConsent -> {
                        afterSignIn = then
                        consent.launch(IntentSenderRequest.Builder(outcome.intent.intentSender).build())
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                connectError = e.message ?: context.getString(R.string.unknown_error)
            }
        }
    }

    // The device list is looked up each time the page opens, so it never
    // describes a phone that has since been removed or renamed.
    LaunchedEffect(settings.account) {
        if (settings.account != null && !settings.needsSignIn) DriveBackup.refreshDevices(context)
    }

    val running = progress != DriveBackup.Progress.Idle
    val connected = settings.account != null
    val ready = connected && !settings.needsDevice
    val account = settings.account

    // How the last run went, or why the last attempt failed — shown once under
    // the sync rows until a sync succeeds.
    val resultText: String? = if (running) null else when (val r = result) {
        is DriveBackup.Result.Restored -> stringResource(
            R.string.drive_restore_as_done,
            r.device,
            context.countOfMonths(r.summary.months),
        )
        is DriveBackup.Result.Failed -> stringResource(
            if (r.restoring) R.string.drive_restore_failed else R.string.drive_sync_failed,
            r.message,
        )
        is DriveBackup.Result.Synced -> stringResource(R.string.drive_sync_done)
        null -> settings.lastError?.let { stringResource(R.string.drive_sync_failed, it) }
    }

    SubSettingsScaffold(
        title = stringResource(R.string.drive_backup_title),
        onBack = { navigator.popBackStack() },
    ) {
        if (!available) {
            SettingsGroup(
                style = SettingsGroupStyle.Grouped,
                items = listOf(
                    SettingsItem(
                        title = { Text(stringResource(R.string.drive_backup_unavailable)) },
                        icon = painterResource(R.drawable.cloud),
                        iconShape = CircleShape,
                    ),
                ),
            )
            return@SubSettingsScaffold
        }

        // ---- Last sync, and the bar while one runs --------------------------
        SettingsGroup(
            style = SettingsGroupStyle.Grouped,
            items = listOf(
                SettingsItem(
                    title = { Text(stringResource(R.string.drive_sync_last)) },
                    highlightKey = "Last sync",
                    descriptionText = if (settings.lastSyncAt > 0) {
                        formatWhen(context, settings.lastSyncAt)
                    } else {
                        stringResource(R.string.drive_backup_never)
                    },
                    icon = painterResource(R.drawable.cloud),
                    iconShape = CircleShape,
                ),
                SettingsItem(
                    title = { Text(stringResource(R.string.drive_sync_now)) },
                    highlightKey = "Sync now",
                    icon = painterResource(R.drawable.refresh),
                    iconShape = CircleShape,
                    enabled = ready && !running,
                    onClick = {
                        DriveBackup.clearResult()
                        if (settings.needsSignIn) {
                            signIn { DriveBackup.syncNow(context) }
                        } else {
                            DriveBackup.syncNow(context)
                        }
                    },
                ),
            ),
        )

        if (running) {
            Spacer(Modifier.height(16.dp))
            ImportInfoCard {
                val fraction = progressFraction(progress)
                val text = progressText(progress)
                Text(
                    text = if (fraction != null) "$text · ${(fraction * 100).toInt()}%" else text,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(Modifier.height(10.dp))
                DriveProgressBar(fraction)
            }
        } else if (resultText != null) {
            Spacer(Modifier.height(16.dp))
            ImportInfoCard {
                Text(resultText, style = MaterialTheme.typography.bodyLarge)
                if (result != null) {
                    TextButton(onClick = DriveBackup::clearResult) { Text("OK") }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // ---- Account --------------------------------------------------------
        SettingsGroup(
            title = stringResource(R.string.drive_account_header),
            style = SettingsGroupStyle.Grouped,
            items = listOf(
                when {
                    account == null -> SettingsItem(
                        title = { Text(stringResource(R.string.drive_connect)) },
                        highlightKey = "Connect Google Drive",
                        descriptionText = stringResource(R.string.drive_connect_subtitle),
                        icon = painterResource(R.drawable.person),
                        iconShape = CircleShape,
                        onClick = { signIn(afterConnect) },
                    )
                    settings.needsSignIn -> SettingsItem(
                        title = { Text(account) },
                        highlightKey = "Sign in",
                        descriptionText = stringResource(R.string.drive_sign_in_again_subtitle),
                        icon = painterResource(R.drawable.warning),
                        iconShape = CircleShape,
                        trailingContent = {
                            TextButton(onClick = { signIn {} }) {
                                Text(stringResource(R.string.drive_sign_in_again))
                            }
                        },
                        onClick = { signIn {} },
                    )
                    else -> SettingsItem(
                        title = { Text(settings.accountName ?: account) },
                        highlightKey = "Google account",
                        descriptionText = account.takeIf { settings.accountName != null },
                        icon = painterResource(R.drawable.person),
                        iconShape = CircleShape,
                        enabled = !running,
                        trailingContent = {
                            TextButton(
                                enabled = !running,
                                onClick = { confirmDisconnect = true },
                            ) { Text(stringResource(R.string.disconnect)) }
                        },
                        onClick = { confirmDisconnect = true },
                    )
                },
            ),
        )
        connectError?.let {
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.drive_connect_failed, it),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        // ---- This phone -----------------------------------------------------
        if (connected) {
            Spacer(Modifier.height(16.dp))
            SettingsGroup(
                title = stringResource(R.string.drive_this_phone_header),
                style = SettingsGroupStyle.Grouped,
                items = listOf(
                    if (settings.needsDevice) {
                        SettingsItem(
                            title = { Text(stringResource(R.string.drive_choose_device)) },
                            highlightKey = "Choose this phone's device",
                            descriptionText = stringResource(R.string.drive_choose_device_subtitle),
                            icon = painterResource(R.drawable.warning),
                            iconShape = CircleShape,
                            enabled = !running,
                            onClick = { navigator.navigate(DriveDevicesRoute) },
                        )
                    } else {
                        SettingsItem(
                            title = { Text(settings.identity.deviceName) },
                            highlightKey = "This phone",
                            descriptionText = stringResource(R.string.drive_this_phone_subtitle),
                            icon = painterResource(R.drawable.smartphone),
                            iconShape = CircleShape,
                            enabled = !running,
                            trailingContent = {
                                TextButton(
                                    enabled = !running,
                                    onClick = {
                                        DriveBackup.dismissForkNotice()
                                        renaming = settings.identity.deviceName
                                    },
                                ) { Text(stringResource(R.string.drive_rename)) }
                            },
                            onClick = {
                                DriveBackup.dismissForkNotice()
                                renaming = settings.identity.deviceName
                            },
                        )
                    },
                ),
            )
            settings.forkedTo?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.drive_forked_notice, it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // ---- Schedule -------------------------------------------------------
        Spacer(Modifier.height(16.dp))
        SettingsGroup(
            title = stringResource(R.string.drive_auto_sync_header),
            style = SettingsGroupStyle.Grouped,
            items = BackupFrequency.entries.map { frequency ->
                SettingsItem(
                    title = { Text(frequency.label()) },
                    highlightKey = "Auto sync ${frequency.name}",
                    enabled = ready,
                    trailingContent = {
                        if (settings.frequency == frequency) {
                            Icon(
                                painter = painterResource(R.drawable.check),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    },
                    onClick = { DriveBackup.setFrequency(frequency) },
                )
            } + driveToggleItem(
                title = stringResource(R.string.drive_over_cellular),
                description = stringResource(R.string.drive_over_cellular_subtitle),
                icon = R.drawable.sync,
                checked = settings.overCellular,
                enabled = ready,
                onChange = DriveBackup::setOverCellular,
            ),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.drive_auto_sync_footer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // ---- Devices --------------------------------------------------------
        if (connected && !settings.needsSignIn) {
            Spacer(Modifier.height(16.dp))
            SettingsGroup(
                title = stringResource(R.string.drive_devices_header),
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
                    is DriveBackup.Devices.Loaded -> if (d.list.isEmpty()) {
                        listOf(
                            SettingsItem(
                                title = { Text(stringResource(R.string.drive_devices_none)) },
                                icon = painterResource(R.drawable.cloud),
                                iconShape = CircleShape,
                            ),
                        )
                    } else {
                        d.list.map { device ->
                            val mine = device.deviceId == settings.identity.deviceId
                            SettingsItem(
                                title = { Text(device.name) },
                                highlightKey = "Device ${device.name}",
                                descriptionText = describe(device),
                                icon = painterResource(R.drawable.smartphone),
                                iconShape = CircleShape,
                                enabled = !running,
                                trailingContent = if (mine) {
                                    {
                                        Text(
                                            stringResource(R.string.drive_this_phone),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                } else {
                                    null
                                },
                                onClick = { removeDevice = device },
                            )
                        }
                    }
                },
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.drive_devices_footer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // ---- Restore --------------------------------------------------------
        Spacer(Modifier.height(16.dp))
        SettingsGroup(
            title = stringResource(R.string.drive_restore_header),
            style = SettingsGroupStyle.Grouped,
            items = listOf(
                SettingsItem(
                    title = { Text(stringResource(R.string.drive_restore_as_title)) },
                    highlightKey = "Restore as another device",
                    descriptionText = stringResource(
                        if (connected) R.string.drive_restore_as_subtitle else R.string.drive_restore_connect_first,
                    ),
                    icon = painterResource(R.drawable.download),
                    iconShape = CircleShape,
                    enabled = connected && !settings.needsSignIn && !running,
                    onClick = { navigator.navigate(DriveDevicesRoute) },
                ),
            ),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.drive_backup_footer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(32.dp))
    }

    if (confirmDisconnect) {
        DefaultDialog(
            onDismiss = { confirmDisconnect = false },
            horizontalAlignment = Alignment.Start,
            title = { Text(stringResource(R.string.drive_disconnect_confirm_title)) },
            buttons = {
                TextButton(onClick = { confirmDisconnect = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
                TextButton(
                    onClick = {
                        confirmDisconnect = false
                        DriveBackup.clearResult()
                        DriveBackup.disconnect(context)
                    },
                ) { Text(stringResource(R.string.disconnect)) }
            },
        ) {
            Text(
                stringResource(R.string.drive_disconnect_confirm_body),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }

    removeDevice?.let { device ->
        val mine = device.deviceId == DriveBackup.settings.value.identity.deviceId
        DefaultDialog(
            onDismiss = { removeDevice = null },
            horizontalAlignment = Alignment.Start,
            title = { Text(stringResource(R.string.drive_remove_confirm_title, device.name)) },
            buttons = {
                TextButton(onClick = { removeDevice = null }) {
                    Text(stringResource(android.R.string.cancel))
                }
                TextButton(
                    onClick = {
                        removeDevice = null
                        DriveBackup.remove(context, device)
                    },
                ) { Text(stringResource(R.string.delete)) }
            },
        ) {
            Text(
                stringResource(
                    if (mine) R.string.drive_delete_mine_body else R.string.drive_remove_confirm_body,
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }

    renaming?.let { name ->
        TextFieldDialog(
            title = { Text(stringResource(R.string.drive_rename_title)) },
            initialTextFieldValue = TextFieldValue(name),
            placeholder = { Text(DriveBackup.deviceName()) },
            isInputValid = { it.isNotBlank() },
            onDone = { DriveBackup.rename(it.take(60)) },
            onDismiss = { renaming = null },
            extraContent = {
                Text(
                    stringResource(R.string.drive_rename_message),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
        )
    }
}

/** What is happening, in words, over a bar that fills as it goes. */
@Composable
private fun progressText(progress: DriveBackup.Progress): String {
    val step = (progress as? DriveBackup.Progress.Running)?.step
    return when (step) {
        null, ReplaySync.Step.Preparing -> stringResource(R.string.drive_sync_preparing)
        is ReplaySync.Step.Uploading -> stringResource(
            R.string.drive_sync_uploading,
            (step.done + 1).coerceAtMost(step.total).coerceAtLeast(1),
            step.total.coerceAtLeast(1),
        )
        is ReplaySync.Step.Downloading -> stringResource(
            R.string.drive_sync_downloading,
            step.device,
            (step.done + 1).coerceAtMost(step.total),
            step.total,
        )
        ReplaySync.Step.Restoring -> stringResource(R.string.drive_backup_restoring)
    }
}

private fun progressFraction(progress: DriveBackup.Progress): Float? {
    val step = (progress as? DriveBackup.Progress.Running)?.step
    return when (step) {
        is ReplaySync.Step.Uploading -> step.done.fractionOf(step.total)
        is ReplaySync.Step.Downloading -> step.done.fractionOf(step.total)
        else -> null
    }
}

private fun Int.fractionOf(total: Int): Float? =
    if (total > 0) (toFloat() / total).coerceIn(0f, 1f) else null

@Composable
internal fun BackupFrequency.label(): String = stringResource(
    when (this) {
        BackupFrequency.OFF -> R.string.off
        BackupFrequency.DAILY -> R.string.drive_frequency_daily
        BackupFrequency.WEEKLY -> R.string.drive_frequency_weekly
        BackupFrequency.MONTHLY -> R.string.drive_frequency_monthly
    },
)

@Composable
private fun driveToggleItem(
    title: String,
    description: String,
    icon: Int,
    checked: Boolean,
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
) = SettingsItem(
    title = { Text(title) },
    highlightKey = title,
    descriptionText = description,
    icon = painterResource(icon),
    iconShape = CircleShape,
    enabled = enabled,
    onClick = { onChange(!checked) },
    trailingContent = { AppSwitch(checked = checked, onCheckedChange = onChange, enabled = enabled) },
)
