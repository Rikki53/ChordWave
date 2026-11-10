package com.music.bitchord.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.music.bitchord.R
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi

/**
 * The mobile-data question, asked at the tap that would spend the data.
 *
 * Deliberately the whole conversation: a question, a "don't ask again",
 * Continue and Cancel. No sizes and no estimates — what is being asked
 * for is permission, not a budget, and a number here would only invite
 * an argument about the number. Ticking the box is the one deliberate
 * way into [com.music.bitchord.download.CellularDownloadPolicy.ALWAYS_ALLOW];
 * continuing without it approves this download and nothing beyond it,
 * and cancelling approves nothing at all.
 *
 * Same frosted shell as the other alerts, for the same reason: a system
 * dialog here would look like it belongs to another app.
 */
@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun MobileDataDownloadAlert(
    hazeState: HazeState,
    onConfirm: (dontAskAgain: Boolean) -> Unit,
    onDismiss: () -> Unit,
    /**
     * Opens the alert in a window of its own, above every other window —
     * a download can be asked for from the player or from either menu,
     * each a window of its own over the activity's, and an alert drawn in
     * the activity's window would open behind whichever it was. The same
     * arrangement as [InfoAlert]'s.
     */
    inOwnWindow: Boolean = false,
) {
    if (inOwnWindow) {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        ) {
            val window = (LocalView.current.parent as? DialogWindowProvider)?.window
            // The scaffold draws its own flat scrim; the platform's would double it.
            SideEffect { window?.setDimAmount(0f) }
            MobileDataDownloadFrame(hazeState, onConfirm, onDismiss, frosted = false)
        }
    } else {
        MobileDataDownloadFrame(hazeState, onConfirm, onDismiss, frosted = true)
    }
}

@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
private fun MobileDataDownloadFrame(
    hazeState: HazeState,
    onConfirm: (dontAskAgain: Boolean) -> Unit,
    onDismiss: () -> Unit,
    frosted: Boolean,
) {
    var dontAskAgain by remember { mutableStateOf(false) }
    AlertScaffold(hazeState = hazeState, onDismiss = onDismiss, frosted = frosted) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 19.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.download_mobile_data_title),
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp, fontWeight = FontWeight.W600),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.download_mobile_data_message),
                modifier = Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 17.sp),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            // The box is the app's own rather than a Material checkbox —
            // there is no other checkbox in the app to match, and a widget
            // from another design language would be the loudest thing in
            // the alert. The whole row is the tap target, not just the box.
            Row(
                modifier = Modifier
                    .padding(top = 14.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ) { dontAskAgain = !dontAskAgain }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .then(
                            if (dontAskAgain) {
                                Modifier.background(MaterialTheme.colorScheme.primary)
                            } else {
                                Modifier.border(
                                    width = 1.5.dp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    shape = RoundedCornerShape(6.dp),
                                )
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (dontAskAgain) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.download_dont_ask_again),
                    modifier = Modifier.padding(start = 10.dp),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        AlertRule()
        AlertAction(
            label = stringResource(R.string.download_mobile_data_continue),
            emphasised = true,
            onClick = { onConfirm(dontAskAgain) },
        )
        AlertRule()
        AlertAction(label = stringResource(R.string.cancel), emphasised = false, onClick = onDismiss)
    }
}
