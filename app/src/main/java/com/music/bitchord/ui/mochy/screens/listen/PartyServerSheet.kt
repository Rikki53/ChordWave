// The party server's address, as a PopupSheet — the Modern form of the
// Classic PartyServerEditor, which was an AddonEditorAlert (haze dialog)
// mounted at the app root because the haze card sampled a backdrop layer and
// needed a window-sized scrim. Neither constraint applies here:
// PopupSheetHost is already app-level in the Modern host, so a sheet is the
// natural home, and the editor no longer needs anything from MainActivity.
//
// Self-contained on purpose: everything it edits lives in ListenTogether, so
// hoisting it cost one call rather than half the screen's state.
//
// Never shows the default address, even as a placeholder: this box exists to
// take somebody else's server, and the one this build uses is not shown
// anywhere — see ListenTogether.customServerUrl.

package com.music.bitchord.ui.mochy.screens.listen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.R
import com.music.bitchord.data.listentogether.ListenTogether
import com.music.bitchord.data.listentogether.ServerConnectionState
import com.music.bitchord.data.listentogether.ServerUrlError
import com.music.bitchord.data.listentogether.ServerUrlValidationResult
import com.music.bitchord.ui.mochy.component.PopupSheetHandle
import com.music.bitchord.ui.mochy.component.PopupSheetState
import com.music.bitchord.ui.mochy.component.SheetLabel
import com.music.bitchord.ui.mochy.component.SheetPillButton
import com.music.bitchord.ui.mochy.component.SheetTextField
import com.music.bitchord.ui.mochy.component.TopToaster
import com.music.bitchord.ui.mochy.utils.stableSystemBars
import kotlinx.coroutines.launch

fun PopupSheetState.showPartyServerEditorSheet() {
    var sheet: PopupSheetHandle? = null
    sheet = showWithHandle {
        PartyServerEditorContent(onDismiss = { sheet?.dismiss() })
    }
}

@Composable
private fun PartyServerEditorContent(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val customServer by ListenTogether.customServerUrl.collectAsStateWithLifecycle()
    val party by ListenTogether.state.collectAsStateWithLifecycle()

    // Seeded once, at the moment the sheet opens: this is a draft of the
    // stored address, and re-seeding it from the store while it is being
    // typed into would overwrite the typing the first time anything else
    // touched it.
    var input by remember { mutableStateOf(customServer) }
    // What the last Test or Save said, and whether it was good news. Null
    // until one of them has run — the sheet shows its description then.
    var status by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    var busy by remember { mutableStateOf(false) }
    val navBottom = WindowInsets.stableSystemBars.asPaddingValues().calculateBottomPadding()

    val save: () -> Unit = {
        when (val validation = ListenTogether.parseAndNormalizeServerUrl(input)) {
            // In the sheet, where the address being complained about still
            // is. A toast over a sheet is a sentence about a field it is
            // covering.
            is ServerUrlValidationResult.Invalid ->
                status = context.getString(validation.error.toMessageRes()) to false

            is ServerUrlValidationResult.Valid -> {
                busy = true
                status = null
                scope.launch {
                    val resolved = ListenTogether.setCustomServerUrl(validation.normalizedUrl)
                    busy = false
                    val messageRes = when (resolved) {
                        is ServerConnectionState.CustomOnline ->
                            R.string.listen_together_custom_server_connected
                        is ServerConnectionState.CustomFallback ->
                            R.string.listen_together_custom_server_unreachable_fallback
                        is ServerConnectionState.DefaultOnline ->
                            R.string.listen_together_switched_to_default
                        is ServerConnectionState.Offline ->
                            if (ListenTogether.customServerUrl.value.isNotBlank()) {
                                R.string.listen_together_status_all_offline
                            } else {
                                R.string.listen_together_server_offline
                            }
                        ServerConnectionState.Checking -> null
                    }
                    if (messageRes != null) {
                        TopToaster.show(context.getString(messageRes))
                    }
                    onDismiss()
                }
            }
        }
    }

    val remove: () -> Unit = {
        busy = true
        status = null
        scope.launch {
            ListenTogether.setCustomServerUrl("")
            busy = false
            TopToaster.show(context.getString(R.string.listen_together_switched_to_default))
            onDismiss()
        }
    }

    val test: () -> Unit = {
        when (val validation = ListenTogether.parseAndNormalizeServerUrl(input)) {
            is ServerUrlValidationResult.Invalid ->
                status = context.getString(validation.error.toMessageRes()) to false

            is ServerUrlValidationResult.Valid -> {
                busy = true
                status = null
                scope.launch {
                    val probe = ListenTogether.probeHealthWithLatency(
                        validation.normalizedUrl,
                        SERVER_PROBE_TIMEOUT_MS,
                    )
                    status = if (probe.isOnline) {
                        context.getString(R.string.listen_together_server_online, probe.latencyMs) to true
                    } else {
                        context.getString(R.string.listen_together_server_offline) to false
                    }
                    busy = false
                }
            }
        }
    }

    Column(Modifier.fillMaxWidth()) {
        SheetLabel(stringResource(R.string.listen_together_custom_server))
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.listen_together_custom_server_footer),
            color = Color.White.copy(alpha = 0.55f),
            fontSize = 13.sp,
            lineHeight = 18.sp,
        )
        Spacer(Modifier.height(20.dp))
        SheetTextField(
            value = input,
            // A result describes the address it was run against, so the
            // moment that address is edited it stops being true and is
            // cleared. Left up, it would report "Connected" over a URL
            // nobody has tried.
            onValueChange = {
                input = it
                status = null
            },
            placeholder = "https://party.example.com",
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Done,
            ),
        )
        val currentStatus = status
        if (currentStatus != null) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = currentStatus.first,
                style = MaterialTheme.typography.bodyMedium,
                color = if (currentStatus.second) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
            )
        }
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth()) {
            SheetPillButton(
                text = stringResource(R.string.test),
                enabled = input.isNotBlank() && !busy,
                onClick = test,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(10.dp))
            SheetPillButton(
                text = stringResource(R.string.save),
                enabled = input.isNotBlank() && !busy,
                onClick = save,
                modifier = Modifier.weight(1f),
            )
        }
        // Nothing to remove until there is one stored, and never while a
        // party is live on it: changing the address under a live membership
        // would leave this device holding a token for a server it no longer
        // talks to.
        if (customServer.isNotBlank() && !party.inParty) {
            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = remove,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                Text(
                    text = stringResource(R.string.listen_together_remove_server),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        Spacer(Modifier.height(12.dp + navBottom))
    }
}

private fun ServerUrlError.toMessageRes(): Int = when (this) {
    ServerUrlError.Whitespace -> R.string.listen_together_err_whitespace
    ServerUrlError.InvalidScheme -> R.string.listen_together_invalid_server_url
    ServerUrlError.InvalidHost -> R.string.listen_together_invalid_server_url
    ServerUrlError.InvalidPort -> R.string.listen_together_err_invalid_port
    ServerUrlError.InvalidPath -> R.string.listen_together_err_invalid_path
    ServerUrlError.HasPath -> R.string.listen_together_err_no_path
    ServerUrlError.HasQuery -> R.string.listen_together_err_no_query
    ServerUrlError.HasFragment -> R.string.listen_together_err_no_fragment
    ServerUrlError.Malformed -> R.string.listen_together_invalid_server_url
}

/**
 * How long the server editor waits on an address before calling it unreachable.
 *
 * Longer than the background health check, because this one was *asked* for: a
 * person who has just pressed Test is watching a spinner and will wait, where
 * the periodic check has to stay off the radio.
 */
private const val SERVER_PROBE_TIMEOUT_MS = 8_000L
