// KaraokeRemoteScreen.kt — Phase 5 worker 5E (Karaoke restyle).
//
// The Modern (Mochy-style) restyle of the classic
// ui/screens/KaraokeRemoteScreen.kt: find the TV (scan its QR code, or pick
// it from the network list and type the code it shows), then drive its
// queue, transport and vocal slider — and, with the microphone permission
// granted, sing into it from the phone.
//
// ENGINE REUSE (nothing below changes engine behaviour): the discovery,
// client, protocol and search are the classic ones verbatim —
// KaraokeTvDiscovery, KaraokeRemoteClient, RemoteCommand,
// parseKaraokeRemoteQr, YtMusicRepository.search — and the QR scanner is
// ui/components/rememberQrCodeScanner. Only the presentation is Modern:
// SubSettingsScaffold + SettingsGroup + SheetTextField/SheetPillButton.
//
// GATE: the whole screen stays behind KaraokeFeature.ENABLED (see NEEDS.md
// §2). The entry points are gated too; the guard here is defence in depth,
// mirroring the classic EqualizerScreen/MainActivity double gate. Do NOT
// flip the flag's default — karaoke was deliberately parked by the user.
//
// Route: "karaoke_remote" (host wiring in NEEDS.md §3).

package com.music.bitchord.ui.mochy.screens.karaoke

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.R
import com.music.bitchord.karaoke.FoundTv
import com.music.bitchord.karaoke.KaraokeFeature
import com.music.bitchord.karaoke.KaraokeRemoteClient
import com.music.bitchord.karaoke.KaraokeTvDiscovery
import com.music.bitchord.karaoke.parseKaraokeRemoteQr
import com.music.bitchord.ui.components.rememberQrCodeScanner
import com.music.bitchord.ui.mochy.component.RemoteFocus
import com.music.bitchord.ui.mochy.component.SettingsGroup
import com.music.bitchord.ui.mochy.component.SettingsGroupStyle
import com.music.bitchord.ui.mochy.component.SettingsItem
import com.music.bitchord.ui.mochy.component.SheetPillButton
import com.music.bitchord.ui.mochy.component.SheetTextField
import com.music.bitchord.ui.mochy.component.SubSettingsScaffold
import com.music.bitchord.ui.mochy.navigation.MochyNavigator
import com.music.bitchord.ui.mochy.screens.search.SearchColors
import kotlinx.coroutines.delay
import timber.log.Timber

/** Route string the Modern host renders this screen at (NEEDS.md §3). */
const val KARAOKE_REMOTE_ROUTE = "karaoke_remote"

@Composable
fun KaraokeRemoteScreen(navigator: MochyNavigator) {
    SubSettingsScaffold(
        title = stringResource(R.string.karaoke_remote_title),
        onBack = { navigator.popBackStack() },
    ) {
        if (!KaraokeFeature.ENABLED) {
            // The feature is parked: say so on the page instead of showing
            // a remote that can never connect. Entry points are gated too,
            // so reaching here while parked means a stale deep link.
            LaunchedEffect(Unit) {
                Timber.w("KaraokeRemoteScreen: opened while KaraokeFeature is off")
            }
            Text(
                text = stringResource(R.string.mochy_karaoke_unavailable),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            return@SubSettingsScaffold
        }
        KaraokeRemoteBody()
    }
}

@Composable
private fun KaraokeRemoteBody() {
    val context = LocalContext.current
    val client = remember { KaraokeRemoteClient() }
    val discovery = remember { KaraokeTvDiscovery(context) }
    val connected by client.connected.collectAsStateWithLifecycle()
    val remoteState by client.state.collectAsStateWithLifecycle()
    val foundTvs by discovery.found.collectAsStateWithLifecycle()

    var selectedTv by remember { mutableStateOf<FoundTv?>(null) }
    var manualHost by remember { mutableStateOf("") }
    var manualPort by remember { mutableStateOf<Int?>(null) }
    var codeInput by remember { mutableStateOf("") }
    var connecting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        discovery.start()
        onDispose {
            discovery.stop()
            client.disconnect()
        }
    }
    LaunchedEffect(connected) {
        if (connected) {
            discovery.stop()
            connecting = false
            error = null
        }
    }
    LaunchedEffect(connecting) {
        if (!connecting) return@LaunchedEffect
        // The socket's own callbacks settle success; here we only watch for
        // the failure the client recorded, or give up politely.
        repeat(30) {
            delay(200)
            if (client.connected.value) return@LaunchedEffect
            if (client.failureReason != null) {
                error = context.getString(R.string.karaoke_remote_failed)
                connecting = false
                return@LaunchedEffect
            }
        }
        if (!client.connected.value) {
            error = context.getString(R.string.karaoke_remote_failed)
            connecting = false
        }
    }

    val launchScan = rememberQrCodeScanner { raw ->
        if (raw == null) return@rememberQrCodeScanner
        val target = parseKaraokeRemoteQr(raw)
        if (target == null) {
            error = context.getString(R.string.karaoke_remote_bad_qr)
            return@rememberQrCodeScanner
        }
        val (host, port, code) = target
        manualHost = host
        manualPort = port
        codeInput = code
        error = null
        connecting = true
        client.connect(host, port, code)
    }

    if (!connected) {
        KaraokeConnectContent(
            foundTvs = foundTvs,
            selectedTv = selectedTv,
            onSelectTv = { tv ->
                selectedTv = tv
                manualHost = tv.host
                manualPort = tv.port
            },
            manualHost = manualHost,
            onManualHostChange = { manualHost = it.trim() },
            manualPort = manualPort,
            codeInput = codeInput,
            onCodeChange = { typed ->
                codeInput = typed.filter(Char::isDigit).take(6)
            },
            error = error,
            connecting = connecting,
            onScan = { launchScan() },
            onConnect = {
                val host = manualHost
                val port = manualPort
                if (host.isNotBlank() && port != null && codeInput.length == 6) {
                    error = null
                    connecting = true
                    client.connect(host, port, codeInput)
                }
            },
        )
        return
    }

    // ---- Connected: the remote itself (KaraokeRemoteConnected.kt) ----
    val state = remoteState
    KaraokeRemoteConnectedContent(
        client = client,
        state = state,
        onDisconnect = { client.disconnect() },
    )
}

@Composable
private fun KaraokeConnectContent(
    foundTvs: List<FoundTv>,
    selectedTv: FoundTv?,
    onSelectTv: (FoundTv) -> Unit,
    manualHost: String,
    onManualHostChange: (String) -> Unit,
    manualPort: Int?,
    codeInput: String,
    onCodeChange: (String) -> Unit,
    error: String?,
    connecting: Boolean,
    onScan: () -> Unit,
    onConnect: () -> Unit,
) {
    Text(
        text = stringResource(R.string.karaoke_remote_how),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(16.dp))

    KaraokePillButton(
        icon = Icons.Rounded.QrCodeScanner,
        text = stringResource(R.string.karaoke_remote_scan),
        onClick = onScan,
    )

    Spacer(Modifier.height(20.dp))
    Text(
        text = stringResource(R.string.karaoke_remote_found_tvs),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = Color.White,
    )
    Spacer(Modifier.height(8.dp))
    if (foundTvs.isEmpty()) {
        Text(
            text = stringResource(R.string.karaoke_remote_searching),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        SettingsGroup(
            style = SettingsGroupStyle.Grouped,
            items = foundTvs.map { tv ->
                val selected = selectedTv?.host == tv.host && selectedTv?.port == tv.port
                SettingsItem(
                    icon = painterResource(R.drawable.cast),
                    title = { Text(tv.name) },
                    descriptionText = tv.host,
                    trailingContent = if (selected) {
                        {
                            Icon(
                                painter = painterResource(R.drawable.check),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    } else {
                        null
                    },
                    onClick = { onSelectTv(tv) },
                )
            },
        )
    }

    Spacer(Modifier.height(16.dp))
    SheetTextField(
        value = manualHost,
        onValueChange = onManualHostChange,
        placeholder = stringResource(R.string.mochy_karaoke_remote_host_hint),
    )
    Spacer(Modifier.height(8.dp))
    SheetTextField(
        value = codeInput,
        onValueChange = onCodeChange,
        placeholder = stringResource(R.string.karaoke_remote_code_hint),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
    )
    error?.let {
        Spacer(Modifier.height(8.dp))
        Text(
            text = it,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
    }
    Spacer(Modifier.height(16.dp))
    SheetPillButton(
        text = stringResource(
            if (connecting) R.string.karaoke_remote_connecting
            else R.string.karaoke_remote_connect,
        ),
        enabled = manualHost.isNotBlank() && manualPort != null &&
            codeInput.length == 6 && !connecting,
        onClick = onConnect,
    )
}

/**
 * The sheet pill, with an icon slot SheetPillButton doesn't have: same
 * shape and surface (a step lighter than the rows), dimmed while disabled.
 */
@Composable
internal fun KaraokePillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    enabled: Boolean = true,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 46.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(lerp(SearchColors.Field, Color.White, 0.22f))
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = RemoteFocus,
                onClick = onClick,
            )
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = if (enabled) 1f else 0.45f),
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = text,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(alpha = if (enabled) 1f else 0.45f),
            )
        }
    }
}

internal fun formatKaraokeMs(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}
