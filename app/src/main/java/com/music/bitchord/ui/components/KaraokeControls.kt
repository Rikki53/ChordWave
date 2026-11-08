package com.music.bitchord.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.SettingsRemote
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.R
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.KaraokeMode
import com.music.bitchord.karaoke.ai.KaraokeAi
import com.music.bitchord.karaoke.ai.KaraokeAiService
import com.music.bitchord.playback.rememberMediaController
import com.music.bitchord.playback.rememberPlayerState
import com.music.bitchord.ui.screens.RowDivider
import com.music.bitchord.ui.screens.SettingsGroup
import com.music.bitchord.ui.screens.SettingsRow
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * The karaoke block: a mic switch and the vocal slider, in the settings
 * idiom, for whichever audio page hosts it.
 *
 * The switch and slider write straight to [AppSettings]; PlaybackService
 * observes those flows and hands them to the DSP chain, so this block is
 * presentation only. [onOpenRemote] is the TV karaoke remote's door — null
 * on surfaces where driving a TV makes no sense.
 *
 * Misi 2 adds the mode picker under the same switch: Instan is the
 * centre-cancel processor as before; AI drives the on-device MDX stem
 * flow ([KaraokeAiService]) for the song currently selected in the
 * player, with its creation progress, stem status and cache controls
 * shown inline here.
 */
@Composable
fun KaraokeSettingsGroup(
    modifier: Modifier = Modifier,
    topSpacing: androidx.compose.ui.unit.Dp = 26.dp,
    onOpenRemote: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val enabled by AppSettings.karaokeEnabled.collectAsStateWithLifecycle()
    val vocalLevel by AppSettings.karaokeVocalLevel.collectAsStateWithLifecycle()
    val mode by AppSettings.karaokeMode.collectAsStateWithLifecycle()

    SettingsGroup(
        footer = stringResource(R.string.karaoke_footer),
        topSpacing = topSpacing,
    ) {
        SettingsRow(
            icon = Icons.Rounded.Mic,
            title = stringResource(R.string.karaoke),
            subtitle = stringResource(R.string.karaoke_subtitle),
            trailing = {
                IosSwitch(
                    checked = enabled,
                    onCheckedChange = { AppSettings.setKaraokeEnabled(it) },
                )
            },
            onClick = { AppSettings.setKaraokeEnabled(!enabled) },
        )
        if (enabled) {
            RowDivider()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.karaoke_vocal_volume),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "${(vocalLevel * 100).roundToInt()}%",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Slider(
                    value = vocalLevel,
                    onValueChange = { AppSettings.setKaraokeVocalLevel(it) },
                    valueRange = 0f..1f,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(2.dp))
                PillSegmentedControl(
                    options = listOf(
                        stringResource(R.string.karaoke_mode_instant),
                        stringResource(R.string.karaoke_mode_ai),
                    ),
                    selectedIndex = KaraokeMode.entries.indexOf(mode),
                    onSelect = { AppSettings.setKaraokeMode(KaraokeMode.entries[it]) },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (mode == KaraokeMode.AI) {
                    Spacer(Modifier.height(10.dp))
                    KaraokeAiSection()
                }
            }
        }
        if (onOpenRemote != null) {
            RowDivider()
            SettingsRow(
                icon = Icons.Rounded.SettingsRemote,
                title = stringResource(R.string.karaoke_remote_title),
                subtitle = stringResource(R.string.karaoke_remote_subtitle),
                onClick = onOpenRemote,
            )
        }
    }
}

/** The mode-AI half of the karaoke block: create / progress / stem cache. */
@Composable
private fun KaraokeAiSection() {
    val context = LocalContext.current
    val controller = rememberMediaController()
    val player = rememberPlayerState(controller)
    val song = player.song
    val job by KaraokeAi.job.collectAsStateWithLifecycle()

    // Stem existence is a disk fact, not a flow: re-check it on a slow
    // tick while this section is on screen (creation finishing, a delete).
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(song?.videoId, job?.done) {
        while (true) {
            tick++
            delay(1000)
        }
    }
    @Suppress("UNUSED_EXPRESSION") tick
    val hasStem = song != null && KaraokeAi.hasStem(context, song.videoId)
    val stemBytes = if (song != null) KaraokeAi.stemBytes(context, song.videoId) else 0L
    val totalBytes = KaraokeAi.totalBytes(context)
    val running = job != null && !job!!.done && job!!.error == null && !job!!.cancelled
    val jobForSong = job?.takeIf { it.songId == song?.videoId }

    Column(modifier = Modifier.fillMaxWidth()) {
        when {
            running -> {
                Text(
                    text = buildString {
                        append(job!!.phase)
                        if (job!!.fraction >= 0f) {
                            append(" ${(job!!.fraction * 100).roundToInt()}%")
                        }
                        job!!.title.takeIf { it.isNotBlank() }?.let { append(" — $it") }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                if (job!!.fraction >= 0f) {
                    LinearProgressIndicator(
                        progress = { job!!.fraction.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                TextButton(onClick = { KaraokeAiService.cancel(context) }) {
                    Text(stringResource(R.string.karaoke_ai_cancel))
                }
            }
            song == null -> {
                Text(
                    text = stringResource(R.string.karaoke_ai_no_song),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            hasStem -> {
                Text(
                    text = "${stringResource(R.string.karaoke_ai_ready)} · ${formatBytes(stemBytes)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = {
                    KaraokeAi.deleteStem(context, song.videoId)
                    // Nudge PlaybackService (it re-applies on a finished job)
                    // so a stem deleted under the playing song restores its
                    // original source instead of playing a missing file.
                    KaraokeAi.job.value = KaraokeAi.JobState(
                        songId = song.videoId, title = song.title,
                        phase = "Stem dihapus", fraction = 1f, done = true,
                    )
                }) {
                    Text(stringResource(R.string.karaoke_ai_delete))
                }
            }
            else -> {
                if (jobForSong?.cancelled == true) {
                    Text(
                        text = "Pembuatan dibatalkan.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (jobForSong?.error != null) {
                    Text(
                        text = "Gagal: ${jobForSong.error}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.height(4.dp))
                } else if (jobForSong?.cancelled != true) {
                    Text(
                        text = stringResource(R.string.karaoke_ai_not_ready),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                }
                Button(
                    onClick = { KaraokeAiService.start(context, song) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.karaoke_ai_create))
                }
            }
        }
        if (totalBytes > 0L && !running) {
            Spacer(Modifier.height(2.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Cache karaoke AI: ${formatBytes(totalBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = {
                    val currentId = song?.videoId
                    KaraokeAi.clearAll(context)
                    if (currentId != null) {
                        KaraokeAi.job.value = KaraokeAi.JobState(
                            songId = currentId, title = song?.title.orEmpty(),
                            phase = "Stem dihapus", fraction = 1f, done = true,
                        )
                    }
                }) {
                    Text(stringResource(R.string.karaoke_ai_clear_all))
                }
            }
        }
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_000_000L -> "%.1f MB".format(bytes / 1_000_000.0)
    bytes >= 1_000L -> "%.0f KB".format(bytes / 1_000.0)
    else -> "$bytes B"
}
