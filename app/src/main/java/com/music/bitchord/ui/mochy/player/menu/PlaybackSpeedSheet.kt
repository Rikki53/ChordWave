// Ported from Mochy Music (GPL-3.0): ui/player/menu/PlaybackSpeedSheet.kt
// UI verbatim. Engine split (see NEEDS.md §(b) P1):
// - SPEED (global) is WIRED: ChordWave already has a global playback speed
//   (AppSettings.playbackSpeed, persisted via setPlaybackSpeed); the Speed
//   slider reads/writes it, and the service applies it to the player.
// - PITCH has no CW engine: the Pitch slider moves locally and apply() logs
//   a Timber warning + TODO instead of faking a pitch change. (Mochy drove
//   pitch through its AudioLabStore + SongTuning, which are not ported.)
// - "Save for this song only" has no CW store (no per-song tuning): the row
//   is rendered DISABLED so it cannot promise what it can't do, and the
//   contract is recorded in NEEDS.md §(b) P1. Mochy's toggle also restored
//   the global speed when switched on; with no per-song store there is
//   nothing to restore from.
// - The AudioLabEntryPoint / Hilt wiring Mochy used to reach its store is
//   gone with the engine; this file takes no ViewModel and no entry point.

package com.music.bitchord.ui.mochy.player.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.bitchord.R
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.ui.mochy.component.RemoteFocus
import com.music.bitchord.ui.mochy.component.detail.PlaylistSelectTick
import com.music.bitchord.ui.mochy.playback.LocalPlayerConnection
import timber.log.Timber

private val CardSurface = MenuRowSurface

@Composable
fun PlaybackSpeedSheet(onDismiss: () -> Unit) {
    val connection = LocalPlayerConnection.current ?: return
    // ChordWave's global playback speed: the service applies it to the player.
    val globalSpeed by AppSettings.playbackSpeed.collectAsState()
    val metadata by connection.mediaMetadata.collectAsState()
    val songId = metadata?.id

    // No per-song tuning store in CW, so there is never a per-song override:
    // the sliders always show the global values, and "this song only" stays off.
    val onlyThisSong = false
    val speed = remember(songId) { mutableFloatStateOf(globalSpeed) }
    val pitch = remember(songId) { mutableFloatStateOf(1f) }

    fun apply() {
        val s = speed.floatValue.coerceIn(0.25f, 3f)
        AppSettings.setPlaybackSpeed(s)
        val p = pitch.floatValue.coerceIn(0.25f, 3f)
        if (p != 1f) {
            // TODO(phase3-adapter): pitch has no CW engine (NEEDS.md §(b) P1).
            Timber.w("PlaybackSpeedSheet: pitch %.2f ignored — no pitch engine in ChordWave yet", p)
        }
    }

    MenuSheetSurface(onDismiss = onDismiss, halfDetent = 0.64f, fullDetent = 0.64f) { _ ->
        val close = LocalMenuSheetClose.current
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                // the full video mode's card is only as tall as the phone is wide, so the sliders scroll there
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = 10.dp, bottom = 28.dp)
        ) {
            Spacer(modifier = Modifier.height(22.dp))
            Text(
                text = "Playback speed and tempo",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(18.dp))

            ValueSlider(
                label = "Speed",
                value = speed.floatValue,
                onValueChange = { speed.floatValue = it; apply() },
            )
            Spacer(modifier = Modifier.height(14.dp))
            ValueSlider(
                label = "Pitch",
                value = pitch.floatValue,
                onValueChange = { pitch.floatValue = it; apply() },
            )

            Spacer(modifier = Modifier.height(18.dp))

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(CardSurface)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = RemoteFocus,
                        onClick = {
                            speed.floatValue = 1f
                            pitch.floatValue = 1f
                            apply()
                        },
                    )
                    .padding(vertical = 14.dp)
            ) {
                Text(
                    text = "RESET TO NORMAL",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            OnlyThisSongRow(
                checked = onlyThisSong,
                songTitle = metadata?.title,
                // Disabled: no per-song tuning store in CW (NEEDS.md §(b) P1).
                // Mochy enabled this whenever a song was playing.
                enabled = false,
                onToggle = {
                    // TODO(phase3-adapter): per-song speed/pitch store (NEEDS.md §(b) P1).
                    Timber.w("PlaybackSpeedSheet: per-song tuning not available (no store yet)")
                },
            )
            Spacer(modifier = Modifier.height(10.dp))
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = RemoteFocus,
                        onClick = close,
                    )
                    .padding(vertical = 14.dp)
            ) {
                Text(
                    text = stringResource(android.R.string.ok),
                    color = Color.Black,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

// the tick over ok: on, the speed and pitch above stay with this song and every other song plays as usual
@Composable
private fun OnlyThisSongRow(checked: Boolean, songTitle: String?, enabled: Boolean, onToggle: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(CardSurface)
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = RemoteFocus,
                onClick = onToggle,
            )
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.speed_only_this_song),
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            if (!songTitle.isNullOrBlank()) {
                Text(
                    text = stringResource(R.string.speed_only_this_song_detail, songTitle),
                    color = Color(0xFFB3B3B3),
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        PlaylistSelectTick(checked = checked)
    }
}

@Composable
private fun ValueSlider(label: String, value: Float, onValueChange: (Float) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(CardSurface)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = String.format("%.2f×", value),
                color = Color(0xFFB3B3B3),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        LineSlider(

            value = ((value - SpeedMin) / (SpeedMax - SpeedMin)).coerceIn(0f, 1f),
            onValueChange = { fraction ->
                val raw = SpeedMin + fraction * (SpeedMax - SpeedMin)
                onValueChange(kotlin.math.round(raw / 0.05f) * 0.05f)
            },
        )
    }
}

private const val SpeedMin = 0.25f
private const val SpeedMax = 2f
