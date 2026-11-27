package com.music.bitchord.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.music.bitchord.R
import com.music.bitchord.data.admin.AdminFlags
import com.music.bitchord.ui.mochy.component.DefaultDialog
import com.music.bitchord.ui.mochy.theme.MochyTheme

/**
 * The total maintenance block (Sistem Admin Firebase, Tahap 2):
 * while `maintenance_active` is on, this replaces the whole app —
 * there is no way past it, and playback is stopped by the services
 * observing the same flag.
 *
 * Two faces, one content (the parity rule): Modern wears Mochy's
 * [DefaultDialog] under [MochyTheme], Classic wears a Material
 * [AlertDialog] under the caller's BitChordTheme. The body is the
 * admin's `maintenance_message`, or a built-in polite text when the
 * admin left it blank.
 */
@Composable
fun MaintenanceScreen(modern: Boolean, flags: AdminFlags.Flags) {
    val message = flags.maintenanceMessage.ifBlank {
        stringResource(R.string.admin_maintenance_default_message)
    }
    val title = stringResource(R.string.admin_maintenance_title)
    if (modern) {
        MochyTheme {
            Box(Modifier.fillMaxSize().background(Color.Black))
            // DefaultDialog dismisses through onDismissRequest; an
            // empty handler makes back/scrim taps no-ops, so the
            // block cannot be dismissed while the flag is up.
            DefaultDialog(
                onDismiss = {},
                title = { Text(title) },
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    } else {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        AlertDialog(
            onDismissRequest = {},
            properties = DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
            ),
            title = { Text(title) },
            text = { Text(message) },
            confirmButton = {},
        )
    }
}
