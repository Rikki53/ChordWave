// TransferSection.kt
// Ported from Mochy Music (GPL-3.0) — layout kept, engine replaced. Mochy exports a full .zip
// (library, history, downloads, settings) and restarts on import. ChordWave's Backup is a JSON
// file of settings + listening months only — no library, no downloads, no restart — so the copy
// says exactly that, and there is no ViewModel: the section calls Backup directly.

package com.music.bitchord.ui.mochy.screens.settings.storage

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.music.bitchord.R
import com.music.bitchord.data.stats.Backup
import com.music.bitchord.ui.mochy.component.LocalMenuState
import com.music.bitchord.ui.mochy.screens.library.showConfirm
import kotlinx.coroutines.launch

private val JsonTypes = arrayOf("application/json", "text/json", "application/octet-stream")

/** local stand-in for Mochy's TransferStep: the Backup calls are one-shot suspends, not flows */
private sealed interface BackupStep {
    data object Idle : BackupStep
    data object Working : BackupStep
    data class Done(val message: String) : BackupStep
    data class Failed(val message: String) : BackupStep
}

// export your settings and listening history to one file for a new phone, or bring such a file back in
@Composable
internal fun TransferSection() {
    val context = LocalContext.current
    val menuState = LocalMenuState.current
    val scope = rememberCoroutineScope()
    var exportStep by remember { mutableStateOf<BackupStep>(BackupStep.Idle) }
    var importStep by remember { mutableStateOf<BackupStep>(BackupStep.Idle) }
    val busy = exportStep is BackupStep.Working || importStep is BackupStep.Working

    val exportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        exportStep = BackupStep.Working
        scope.launch {
            Backup.exportTo(context, uri)
                .onSuccess { months ->
                    exportStep = BackupStep.Done(context.getString(R.string.storage_transfer_exported, months))
                }
                .onFailure { error ->
                    exportStep = BackupStep.Failed(error.message ?: context.getString(R.string.storage_transfer_failed))
                }
        }
    }
    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        importStep = BackupStep.Working
        scope.launch {
            Backup.importFrom(context, uri)
                .onSuccess { summary ->
                    importStep = BackupStep.Done(
                        context.getString(
                            R.string.storage_transfer_imported,
                            summary.months,
                            summary.settings,
                        ),
                    )
                }
                .onFailure { error ->
                    importStep = BackupStep.Failed(error.message ?: context.getString(R.string.storage_transfer_failed))
                }
        }
    }

    val importTitle = stringResource(R.string.storage_import_title)
    val importMessage = stringResource(R.string.storage_import_message)
    val importAction = stringResource(R.string.storage_import_action)
    Text(
        stringResource(R.string.storage_transfer_title),
        style = storageText(17f, FontWeight.SemiBold),
        modifier = Modifier.padding(horizontal = 2.dp),
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 14.dp)) {
        StorageActionButton(
            label = stringResource(R.string.storage_export_user_data),
            // quiet until it's running, then it says how far along it is
            detail = when (val step = exportStep) {
                is BackupStep.Working -> stringResource(R.string.storage_transfer_exporting)
                is BackupStep.Done -> step.message
                is BackupStep.Failed -> step.message
                BackupStep.Idle -> null
            },
            enabled = !busy,
            onClick = { exportPicker.launch(Backup.suggestedName()) },
        )
        StorageActionButton(
            label = stringResource(R.string.storage_import_user_data),
            detail = when (val step = importStep) {
                is BackupStep.Working -> stringResource(R.string.storage_transfer_importing)
                is BackupStep.Done -> step.message
                is BackupStep.Failed -> step.message
                BackupStep.Idle -> null
            },
            danger = true,
            enabled = !busy,
            onClick = {
                menuState.showConfirm(title = importTitle, message = importMessage, action = importAction) {
                    importStep = BackupStep.Idle
                    importPicker.launch(JsonTypes)
                }
            },
        )
    }
}
