package com.music.bitchord.ui.tv.dialogs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.session.MediaController
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.karaoke.KaraokeRemoteServer
import com.music.bitchord.karaoke.karaokeRemoteQrPayload
import com.music.bitchord.playback.PlayerState
import com.music.bitchord.ui.components.QrCode
import com.music.bitchord.ui.tv.components.TvDialog
import kotlin.math.roundToInt

/**
 * "Use phone as remote": while this dialog is on screen, the TV serves the
 * karaoke remote — karaoke itself is switched on, a WebSocket server and
 * its NSD advertisement live, and the pairing picture (QR + six-digit
 * code) is shown. Closing the dialog is the whole off switch: the server,
 * the advertisement and the mic track all die with it.
 */
@Composable
fun TvKaraokeRemoteDialog(
    playerState: PlayerState,
    mediaController: MediaController?,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val latestState by rememberUpdatedState(playerState)
    val latestController by rememberUpdatedState(mediaController)
    val server = remember {
        KaraokeRemoteServer(
            context = context,
            stateProvider = { latestState },
            controllerProvider = { latestController },
        )
    }
    var startError by remember { mutableStateOf<String?>(null) }
    var ipAddress by remember { mutableStateOf<String?>(null) }
    val vocalLevel by AppSettings.karaokeVocalLevel.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        // Driving the TV's karaoke from a phone presumes karaoke is on.
        AppSettings.setKaraokeEnabled(true)
        runCatching { server.start() }
            .onSuccess { ipAddress = server.localIpAddress() }
            .onFailure { startError = it.message ?: "Could not start the remote server." }
    }
    DisposableEffect(Unit) {
        onDispose { server.stop() }
    }

    TvDialog(
        title = "Karaoke Remote",
        message = "Control this TV's karaoke from your phone — same Wi-Fi network.",
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val error = startError
            val ip = ipAddress
            when {
                error != null -> Text(
                    text = error,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                )
                ip == null -> Text(
                    text = "Starting… connect the TV to Wi-Fi if this stays.",
                    style = MaterialTheme.typography.bodyLarge,
                )
                else -> {
                    QrCode(
                        content = karaokeRemoteQrPayload(ip, server.port, server.code),
                        modifier = Modifier.padding(8.dp),
                        size = 190.dp,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = server.code.chunked(3).joinToString(" "),
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 6.sp,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Scan the code with ChordWave on your phone, or pick this TV " +
                            "in the remote and type the six digits.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Vocal volume ${(vocalLevel * 100).roundToInt()}%",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
    }
}
