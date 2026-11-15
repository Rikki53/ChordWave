// The shared party queue, on the Listen Together page.
//
// NEW vs Classic: the Classic screen never showed the queue — it lived only
// in the player. Here it is a section of the party page: what is playing now,
// what is coming next, and the controls any member may use on it — tap a
// song to play it for everyone, remove one, clear the lot. Adding songs to
// the party queue still happens from the player (PartySync owns that path);
// see NEEDS.md.
//
// All controls honour State.controlsLocked: in a host-only party a listener
// sees the queue but cannot drive it, and the server enforces the same rule,
// so a control sent anyway would come back refused rather than obeyed.

package com.music.bitchord.ui.mochy.screens.listen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.music.bitchord.R
import com.music.bitchord.data.listentogether.ListenTogether
import com.music.bitchord.data.listentogether.PartyTrack
import com.music.bitchord.ui.mochy.component.TopToaster
import com.music.bitchord.ui.mochy.utils.resize

@Composable
fun PartyQueueSection(state: ListenTogether.State, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val queue = state.queue
    val items = queue.items
    val currentIndex = queue.index
    val upcoming = if (currentIndex >= 0) {
        (items.size - currentIndex - 1).coerceAtLeast(0)
    } else {
        items.size
    }
    val locked = state.controlsLocked

    Column(modifier = modifier.fillMaxWidth()) {
        SectionTitle(stringResource(R.string.mochy_listen_together_queue))
        MochyCard {
            if (items.isEmpty()) {
                Text(
                    text = stringResource(R.string.mochy_listen_together_queue_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.mochy_listen_together_queue_up_next, upcoming),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    if (!locked) {
                        TextButton(onClick = {
                            ListenTogether.queueClear()
                            TopToaster.show(context.getString(R.string.mochy_listen_together_queue_cleared))
                        }) {
                            Text(
                                text = stringResource(R.string.mochy_listen_together_queue_clear),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                CardDivider()
                // Capped and scrollable like the activity log: a party queue
                // can run long, and the page underneath still has to be
                // reachable without scrolling past fifty songs.
                val queueScroll = rememberScrollState()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .verticalScroll(queueScroll),
                ) {
                    items.forEachIndexed { index, track ->
                        if (index > 0) CardDivider()
                        PartyQueueRow(
                            track = track,
                            isCurrent = index == currentIndex,
                            canControl = !locked,
                            onPlay = { ListenTogether.setTrack(track) },
                            onRemove = { ListenTogether.queueRemove(track.videoId) },
                        )
                    }
                }
            }
        }
        GroupFooter(
            if (locked) {
                stringResource(R.string.listen_together_host_only_notice)
            } else {
                stringResource(R.string.mochy_listen_together_queue_hint)
            },
        )
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun PartyQueueRow(
    track: PartyTrack,
    isCurrent: Boolean,
    canControl: Boolean,
    onPlay: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (canControl) Modifier.clickable(onClick = onPlay) else Modifier)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val thumb = track.thumbnailUrl
        if (thumb != null) {
            AsyncImage(
                model = thumb.resize(120, 120),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
        } else {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.music_note),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = track.title.ifBlank { stringResource(R.string.listen_together_nothing_playing) },
                style = MaterialTheme.typography.bodyLarge,
                color = if (isCurrent) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isCurrent) {
                    Text(
                        text = stringResource(R.string.mochy_listen_together_queue_now_playing),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (track.artist.isNotBlank() || track.durationMs != null) {
                        Text(
                            text = " · ",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    text = buildString {
                        if (track.artist.isNotBlank()) append(track.artist)
                        track.durationMs?.let {
                            if (isNotEmpty()) append(" · ")
                            append(formatElapsed(it))
                        }
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (track.fromAutoplay) {
                Text(
                    text = stringResource(R.string.mochy_listen_together_queue_autoplay),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
        }
        if (canControl) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clickable(onClick = onRemove),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.close),
                    contentDescription = stringResource(R.string.remove),
                    tint = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
