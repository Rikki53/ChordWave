// KaraokeRemoteConnected.kt — Phase 5 worker 5E (Karaoke restyle).
//
// The connected half of the Modern TV karaoke remote: the now-playing
// card with transport + vocal slider, the phone-as-microphone section,
// the TV queue, and the add-a-song search. Companion to
// KaraokeRemoteScreen.kt; every command still goes through the classic
// KaraokeRemoteClient / RemoteCommand wire vocabulary, unchanged.

package com.music.bitchord.ui.mochy.screens.karaoke

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.R
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.model.SearchFilter
import com.music.bitchord.data.model.SearchResult
import com.music.bitchord.data.model.Song
import com.music.bitchord.karaoke.KaraokeRemoteClient
import com.music.bitchord.karaoke.RemoteCommand
import com.music.bitchord.karaoke.RemoteState
import com.music.bitchord.ui.mochy.component.RemoteFocus
import com.music.bitchord.ui.mochy.component.SheetSearchField
import com.music.bitchord.ui.mochy.component.TopToaster
import com.music.bitchord.ui.mochy.screens.search.SearchColors
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val CardShape = RoundedCornerShape(20.dp)

@Composable
internal fun KaraokeRemoteConnectedContent(
    client: KaraokeRemoteClient,
    state: RemoteState?,
    onDisconnect: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(R.string.karaoke_remote_connected),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = stringResource(R.string.karaoke_remote_disconnect),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = RemoteFocus,
                onClick = onDisconnect,
            ),
        )
    }
    Spacer(Modifier.height(12.dp))

    KaraokeNowPlayingCard(client = client, state = state)
    Spacer(Modifier.height(12.dp))
    KaraokeMicCard(client = client)
    Spacer(Modifier.height(16.dp))

    Text(
        text = stringResource(R.string.karaoke_remote_queue),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = Color.White,
    )
    Spacer(Modifier.height(8.dp))
    val queue = state?.queue.orEmpty()
    if (queue.isEmpty()) {
        Text(
            text = stringResource(R.string.karaoke_remote_queue_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        // Bounded height: this page itself scrolls (SubSettingsScaffold),
        // so the queue scrolls inside its own box rather than stretching it.
        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp)) {
            itemsIndexed(queue, key = { index, song -> "$index-${song.videoId}" }) { index, song ->
                KaraokeQueueRow(
                    title = song.title,
                    artist = song.artist,
                    isCurrent = index == state?.queueIndex,
                    onPlay = { client.send(RemoteCommand(type = "playIndex", index = index)) },
                    onMoveUp = {
                        client.send(RemoteCommand(type = "move", index = index, toIndex = index - 1))
                    },
                    moveUpEnabled = index > 0,
                    onMoveDown = {
                        client.send(RemoteCommand(type = "move", index = index, toIndex = index + 1))
                    },
                    moveDownEnabled = index < queue.lastIndex,
                    onRemove = { client.send(RemoteCommand(type = "remove", index = index)) },
                )
            }
        }
    }
    Spacer(Modifier.height(16.dp))
    KaraokeAddSongSection(client = client)
}

@Composable
private fun KaraokeNowPlayingCard(client: KaraokeRemoteClient, state: RemoteState?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = SearchColors.Field),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.karaoke_remote_now_playing),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = state?.title ?: stringResource(R.string.karaoke_remote_nothing),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (state?.artist != null) {
                Text(
                    text = state.artist,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (state != null && state.durationMs > 0) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${formatKaraokeMs(state.positionMs)} / ${formatKaraokeMs(state.durationMs)}",
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
                    Icon(
                        painter = painterResource(R.drawable.skip_previous),
                        contentDescription = null,
                        tint = Color.White,
                    )
                }
                IconButton(onClick = { client.send(RemoteCommand(type = "toggle")) }) {
                    Icon(
                        painter = painterResource(
                            if (state?.isPlaying == true) R.drawable.pause else R.drawable.play,
                        ),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(32.dp),
                    )
                }
                IconButton(onClick = { client.send(RemoteCommand(type = "next")) }) {
                    Icon(
                        painter = painterResource(R.drawable.skip_next),
                        contentDescription = null,
                        tint = Color.White,
                    )
                }
            }
            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
            Spacer(Modifier.height(8.dp))
            KaraokeVocalSlider(client = client, remoteLevel = state?.vocalLevel ?: 1f)
        }
    }
}

/**
 * The TV's vocal slider: a value-based Slider (never SliderState) in
 * Mochy white, sending only when the drag settles — the classic behaviour,
 * so a drag doesn't spam the socket with a command per frame.
 */
@Composable
private fun KaraokeVocalSlider(client: KaraokeRemoteClient, remoteLevel: Float) {
    var local by remember { mutableFloatStateOf(remoteLevel) }
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(remoteLevel) { if (!dragging) local = remoteLevel }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(R.string.karaoke_vocal_volume),
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White,
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
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.White,
                inactiveTrackColor = Color.White.copy(alpha = 0.22f),
            ),
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "${(local * 100).roundToInt()}%",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun KaraokeMicCard(client: KaraokeRemoteClient) {
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
            // Mochy toast, not an android.widget.Toast: the refusal is
            // shown, and the manual path (tap Start again) stays open.
            TopToaster.error(context.getString(R.string.karaoke_remote_mic_permission))
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = SearchColors.Field),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(
                            if (micOn) MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
                            else Color.White.copy(alpha = 0.08f),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.mic),
                        contentDescription = null,
                        tint = if (micOn) MaterialTheme.colorScheme.error else Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = stringResource(
                        if (micOn) R.string.karaoke_remote_mic_live
                        else R.string.karaoke_remote_mic,
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    modifier = Modifier.weight(1f),
                )
                KaraokeSmallPill(
                    text = stringResource(
                        if (micOn) R.string.mochy_karaoke_mic_stop
                        else R.string.mochy_karaoke_mic_start,
                    ),
                    onClick = {
                        if (micOn) stopMic()
                        else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    },
                )
            }
            if (micOn) {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.karaoke_remote_mic_volume),
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White,
                        modifier = Modifier.width(110.dp),
                    )
                    Slider(
                        value = gain,
                        onValueChange = { gain = it },
                        onValueChangeFinished = {
                            client.send(RemoteCommand(type = "micGain", gain = gain))
                        },
                        valueRange = 0f..1f,
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color.White,
                            inactiveTrackColor = Color.White.copy(alpha = 0.22f),
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "${(gain * 100).roundToInt()}%",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(4.dp))
                val muted = state?.micMuted == true
                Text(
                    text = stringResource(
                        if (muted) R.string.mochy_karaoke_mic_unmute
                        else R.string.mochy_karaoke_mic_mute,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = RemoteFocus,
                        onClick = {
                            client.send(RemoteCommand(type = "micMute", muted = !muted))
                        },
                    ),
                )
            }
        }
    }
}

@Composable
private fun KaraokeQueueRow(
    title: String,
    artist: String,
    isCurrent: Boolean,
    onPlay: () -> Unit,
    onMoveUp: () -> Unit,
    moveUpEnabled: Boolean,
    onMoveDown: () -> Unit,
    moveDownEnabled: Boolean,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isCurrent) Color.White.copy(alpha = 0.06f) else Color.Transparent,
            )
            .padding(start = 12.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = artist,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onPlay, modifier = Modifier.size(36.dp)) {
            Icon(
                Icons.Rounded.PlayArrow,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.8f),
            )
        }
        IconButton(onClick = onMoveUp, enabled = moveUpEnabled, modifier = Modifier.size(36.dp)) {
            Icon(
                Icons.Rounded.ArrowUpward,
                contentDescription = null,
                tint = Color.White.copy(alpha = if (moveUpEnabled) 0.8f else 0.25f),
            )
        }
        IconButton(onClick = onMoveDown, enabled = moveDownEnabled, modifier = Modifier.size(36.dp)) {
            Icon(
                Icons.Rounded.ArrowDownward,
                contentDescription = null,
                tint = Color.White.copy(alpha = if (moveDownEnabled) 0.8f else 0.25f),
            )
        }
        IconButton(onClick = onRemove, modifier = Modifier.size(36.dp)) {
            Icon(
                Icons.Rounded.Delete,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.8f),
            )
        }
    }
}

@Composable
private fun KaraokeAddSongSection(client: KaraokeRemoteClient) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var searched by remember { mutableStateOf(false) }
    var searchFailed by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<Song>>(emptyList()) }

    fun runSearch() {
        val asked = query.trim()
        if (asked.isEmpty() || searching) return
        searching = true
        searchFailed = false
        scope.launch {
            val found = YtMusicRepository.search(asked, SearchFilter.SONGS)
            results = found.getOrNull().orEmpty()
                .mapNotNull { hit ->
                    when (hit) {
                        is SearchResult.Track -> hit.song
                        is SearchResult.TopTrack -> hit.song
                        else -> null
                    }
                }
            searchFailed = found.isFailure
            searched = true
            searching = false
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.weight(1f)) {
            SheetSearchField(
                value = query,
                onValueChange = { query = it },
                placeholder = stringResource(R.string.karaoke_remote_search_hint),
            )
        }
        Spacer(Modifier.width(8.dp))
        IconButton(onClick = ::runSearch) {
            Icon(
                Icons.Rounded.Search,
                contentDescription = null,
                tint = Color.White,
            )
        }
    }
    when {
        searching -> {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.mochy_karaoke_searching),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        searchFailed -> {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.mochy_karaoke_search_failed),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        searched && results.isEmpty() -> {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.mochy_karaoke_no_results),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (results.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp)) {
            items(results, key = { it.videoId }) { song ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = song.title,
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = song.artist,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    KaraokeSmallPill(
                        text = stringResource(R.string.karaoke_remote_add),
                        icon = Icons.Rounded.Add,
                        onClick = {
                            client.send(
                                RemoteCommand(
                                    type = "add",
                                    videoId = song.videoId,
                                    title = song.title,
                                    artist = song.artist,
                                ),
                            )
                        },
                    )
                }
            }
        }
    }
}

/** A compact pill for the small actions (mic Start/Stop, queue Add). */
@Composable
private fun KaraokeSmallPill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(Color.White.copy(alpha = 0.12f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = RemoteFocus,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(4.dp))
            }
            Text(
                text = text,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
            )
        }
    }
}
