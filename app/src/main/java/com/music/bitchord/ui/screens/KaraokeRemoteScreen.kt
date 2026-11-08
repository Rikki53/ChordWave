package com.music.bitchord.ui.screens

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.R
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.model.SearchFilter
import com.music.bitchord.data.model.SearchResult
import com.music.bitchord.data.model.Song
import com.music.bitchord.karaoke.FoundTv
import com.music.bitchord.karaoke.KaraokeRemoteClient
import com.music.bitchord.karaoke.KaraokeTvDiscovery
import com.music.bitchord.karaoke.RemoteCommand
import com.music.bitchord.karaoke.parseKaraokeRemoteQr
import com.music.bitchord.ui.components.rememberQrCodeScanner
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * The TV karaoke remote, as a full-screen dialog over the audio page: find
 * the TV (scan its QR code, or pick it from the network list and type the
 * code it shows), then drive its queue, transport and vocal slider — and,
 * with the microphone permission granted, sing into it from the phone.
 */
@Composable
fun KaraokeRemoteDialog(onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            KaraokeRemoteContent(onDismiss = onDismiss)
        }
    }
}

@Composable
private fun KaraokeRemoteContent(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
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

    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.karaoke_remote_heading),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onDismiss) {
                Icon(Icons.Rounded.Close, contentDescription = null)
            }
        }
        Spacer(Modifier.height(8.dp))

        if (!connected) {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = stringResource(R.string.karaoke_remote_how),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { launchScan() },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Icon(Icons.Rounded.QrCodeScanner, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.karaoke_remote_scan))
                }
                Spacer(Modifier.height(20.dp))
                Text(
                    text = stringResource(R.string.karaoke_remote_found_tvs),
                    style = MaterialTheme.typography.titleMedium,
                )
                if (foundTvs.isEmpty()) {
                    Text(
                        text = stringResource(R.string.karaoke_remote_searching),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                foundTvs.forEach { tv ->
                    TextButton(
                        onClick = {
                            selectedTv = tv
                            manualHost = tv.host
                            manualPort = tv.port
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = if (selectedTv?.host == tv.host && selectedTv?.port == tv.port) {
                                "● ${tv.name} — ${tv.host}"
                            } else {
                                "${tv.name} — ${tv.host}"
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = manualHost,
                    onValueChange = { manualHost = it.trim() },
                    label = { Text("TV address (from scan or list)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = codeInput,
                    onValueChange = { typed ->
                        codeInput = typed.filter(Char::isDigit).take(6)
                    },
                    label = { Text(stringResource(R.string.karaoke_remote_code_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth(),
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
                Button(
                    onClick = {
                        val host = manualHost
                        val port = manualPort
                        if (host.isNotBlank() && port != null && codeInput.length == 6) {
                            error = null
                            connecting = true
                            client.connect(host, port, codeInput)
                        }
                    },
                    enabled = manualHost.isNotBlank() && manualPort != null &&
                        codeInput.length == 6 && !connecting,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Text(
                        text = stringResource(
                            if (connecting) R.string.karaoke_remote_connecting
                            else R.string.karaoke_remote_connect,
                        ),
                    )
                }
            }
            return@Column
        }

        // ---- Connected: the remote itself ----
        val state = remoteState
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.karaoke_remote_connected),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { client.disconnect() }) {
                Text(stringResource(R.string.karaoke_remote_disconnect))
            }
        }
        Spacer(Modifier.height(8.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.karaoke_remote_now_playing),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = state?.title ?: stringResource(R.string.karaoke_remote_nothing),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                if (state?.artist != null) {
                    Text(
                        text = state.artist,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (state != null && state.durationMs > 0) {
                    Text(
                        text = "${formatMs(state.positionMs)} / ${formatMs(state.durationMs)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { client.send(RemoteCommand(type = "prev")) }) {
                        Icon(Icons.Rounded.SkipPrevious, contentDescription = null)
                    }
                    IconButton(onClick = { client.send(RemoteCommand(type = "toggle")) }) {
                        Icon(
                            if (state?.isPlaying == true) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = null,
                        )
                    }
                    IconButton(onClick = { client.send(RemoteCommand(type = "next")) }) {
                        Icon(Icons.Rounded.SkipNext, contentDescription = null)
                    }
                }
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                VocalSlider(client = client, remoteLevel = state?.vocalLevel ?: 1f)
            }
        }
        Spacer(Modifier.height(12.dp))
        MicSection(client = client)
        Spacer(Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.karaoke_remote_queue),
            style = MaterialTheme.typography.titleMedium,
        )
        val queue = state?.queue.orEmpty()
        if (queue.isEmpty()) {
            Text(
                text = stringResource(R.string.karaoke_remote_queue_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(max = 260.dp),
        ) {
            itemsIndexed(queue, key = { index, song -> "$index-${song.videoId}" }) { index, song ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = song.title,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (index == state?.queueIndex) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                        )
                        Text(
                            text = song.artist,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                    IconButton(
                        onClick = { client.send(RemoteCommand(type = "playIndex", index = index)) },
                    ) { Icon(Icons.Rounded.PlayArrow, contentDescription = null) }
                    IconButton(
                        onClick = {
                            client.send(RemoteCommand(type = "move", index = index, toIndex = index - 1))
                        },
                        enabled = index > 0,
                    ) { Icon(Icons.Rounded.ArrowUpward, contentDescription = null) }
                    IconButton(
                        onClick = {
                            client.send(RemoteCommand(type = "move", index = index, toIndex = index + 1))
                        },
                        enabled = index < queue.lastIndex,
                    ) { Icon(Icons.Rounded.ArrowDownward, contentDescription = null) }
                    IconButton(
                        onClick = { client.send(RemoteCommand(type = "remove", index = index)) },
                    ) { Icon(Icons.Rounded.Delete, contentDescription = null) }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        AddSongSection(client = client)
    }
}

@Composable
private fun VocalSlider(client: KaraokeRemoteClient, remoteLevel: Float) {
    var local by remember { mutableFloatStateOf(remoteLevel) }
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(remoteLevel) { if (!dragging) local = remoteLevel }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(R.string.karaoke_vocal_volume),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.width(110.dp),
        )
        Slider(
            value = local,
            onValueChange = {
                dragging = true
                local = it
            },
            onValueChangeFinished = {
                dragging = false
                client.send(RemoteCommand(type = "vocal", level = local))
            },
            valueRange = 0f..1f,
            modifier = Modifier.weight(1f),
        )
        Text(text = "${(local * 100).roundToInt()}%")
    }
}

@Composable
private fun MicSection(client: KaraokeRemoteClient) {
    val context = LocalContext.current
    val state by client.state.collectAsStateWithLifecycle()
    var micOn by remember { mutableStateOf(false) }
    var gain by remember { mutableFloatStateOf(0.8f) }

    fun startMic() {
        client.send(RemoteCommand(type = "micStart"))
        client.mic.start()
        micOn = true
    }
    fun stopMic() {
        client.mic.stop()
        client.send(RemoteCommand(type = "micStop"))
        micOn = false
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            startMic()
        } else {
            Toast.makeText(
                context,
                context.getString(R.string.karaoke_remote_mic_permission),
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (micOn) Icons.Rounded.Mic else Icons.Rounded.MicOff,
                    contentDescription = null,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(
                        if (micOn) R.string.karaoke_remote_mic_live else R.string.karaoke_remote_mic,
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Button(onClick = {
                    if (micOn) stopMic()
                    else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }) {
                    Text(if (micOn) "Stop" else "Start")
                }
            }
            if (micOn) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.karaoke_remote_mic_volume),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.width(110.dp),
                    )
                    Slider(
                        value = gain,
                        onValueChange = { gain = it },
                        onValueChangeFinished = {
                            client.send(RemoteCommand(type = "micGain", gain = gain))
                        },
                        valueRange = 0f..1f,
                        modifier = Modifier.weight(1f),
                    )
                    Text(text = "${(gain * 100).roundToInt()}%")
                }
                val muted = state?.micMuted == true
                TextButton(onClick = {
                    client.send(RemoteCommand(type = "micMute", muted = !muted))
                }) {
                    Text(if (muted) "Unmute mic" else "Mute mic")
                }
            }
        }
    }
}

@Composable
private fun AddSongSection(client: KaraokeRemoteClient) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<Song>>(emptyList()) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text(stringResource(R.string.karaoke_remote_search_hint)) },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        IconButton(
            onClick = {
                val asked = query.trim()
                if (asked.isEmpty() || searching) return@IconButton
                searching = true
                scope.launch {
                    val found = YtMusicRepository.search(asked, SearchFilter.SONGS).getOrNull()
                        .orEmpty()
                        .mapNotNull { hit ->
                            when (hit) {
                                is SearchResult.Track -> hit.song
                                is SearchResult.TopTrack -> hit.song
                                else -> null
                            }
                        }
                    results = found
                    searching = false
                }
            },
        ) { Icon(Icons.Rounded.Search, contentDescription = null) }
    }
    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp)) {
        items(results, key = { it.videoId }) { song ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                    )
                    Text(
                        text = song.artist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                OutlinedButton(onClick = {
                    client.send(
                        RemoteCommand(
                            type = "add",
                            videoId = song.videoId,
                            title = song.title,
                            artist = song.artist,
                        ),
                    )
                }) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Text(stringResource(R.string.karaoke_remote_add))
                }
            }
        }
    }
}

private fun formatMs(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}
