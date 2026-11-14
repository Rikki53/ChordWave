// Ported from Mochy Music (GPL-3.0): ui/player/menu/DeviceOutputSheet.kt
// SHELL PORT — degrades gracefully without Mochy's audio-output + cast
// engines (see NEEDS.md §(b) D2, §(c) D2):
// - KEPT + WORKING: the two volume cards (device volume via AudioManager;
//   music volume as local state — see below), the "Automatic" row, and the
//   "Pair a new device" row (opens Bluetooth settings).
// - MUSIC VOLUME: Mochy wrote playerConnection.player.volume (ExoPlayer).
//   CW's MochyPlayerConnection exposes no volume API, so the slider moves
//   locally and apply logs a Timber warning + TODO instead of faking a
//   volume change. Contract in NEEDS.md §(b) D2.
// - DROPPED (engine): the per-device list (AudioOutputs.devices —
//   service.audioOutputs + setPreferredAudioDevice), the cast section
//   (castConnectionHandler: discovery, targets, connect/disconnect), the
//   USB passthrough detail, and the larp-mode row relabel
//   (rememberLarpMode / larpDacDetail — a Mochy in-joke, not a CW feature).
//   Their helpers (deviceLabel / deviceDetail / Kind.icon) went with them.
//   With no devices, "Automatic" is always the selected row.
// The sheet opens, scrolls, and never crashes without the engines.

package com.music.bitchord.ui.mochy.player.menu

import android.media.AudioManager
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoMode
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.SettingsBluetooth
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.bitchord.R
import com.music.bitchord.ui.mochy.component.RemoteFocus
import com.music.bitchord.ui.mochy.component.SheetIcon
import timber.log.Timber

/** "change playback": both volumes, then every output; picking one moves the song there, cast receivers play it themselves */
@Composable
fun DeviceOutputSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    // Mochy reached the player volume through LocalPlayerConnection; CW's
    // connection exposes no volume API, so this sheet no longer reads it.
    val audioManager = remember {
        context.getSystemService(android.content.Context.AUDIO_SERVICE) as AudioManager
    }
    val maxVolume = remember {
        audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    }

    val deviceVolume = remember {
        mutableFloatStateOf(
            audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume
        )
    }
    val musicVolume = remember {
        mutableFloatStateOf(1f)
    }

    // STUB (NEEDS.md §(b) D2): no audio-output engine, no cast engine.
    // Mochy read service.audioOutputs.devices + castConnectionHandler here;
    // with neither, the device list is empty and nothing is casting, so
    // "Automatic" is selected and the cast section stays hidden.
    val anyChosen = false
    val isCasting = false

    MenuSheetSurface(onDismiss = onDismiss, halfDetent = 0.66f, fullDetent = 0.92f) { _ ->
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 10.dp, bottom = 28.dp)
        ) {
            SectionLabel("Change playback")

            VolumeCard(
                icon = R.drawable.volume_up,
                title = android.os.Build.MODEL ?: "this device",
                value = deviceVolume.floatValue,
                onValueChange = { next ->
                    deviceVolume.floatValue = next
                    audioManager.setStreamVolume(
                        AudioManager.STREAM_MUSIC,
                        (next * maxVolume).toInt(),
                        0,
                    )
                },
            )

            Spacer(modifier = Modifier.height(10.dp))

            VolumeCard(
                icon = R.drawable.music_note,
                title = "Music volume",
                value = musicVolume.floatValue,
                onValueChange = { next ->
                    musicVolume.floatValue = next
                    // TODO(phase3-adapter): no player-volume API on
                    // MochyPlayerConnection yet (NEEDS.md §(b) D2).
                    Timber.w("DeviceOutputSheet: music volume %.2f ignored — no player volume API yet", next)
                },
            )

            Spacer(modifier = Modifier.height(22.dp))
            SectionLabel("Play on")

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DeviceRow(
                    label = "Automatic",
                    detail = "Wherever Android sends it",
                    icon = Icons.Rounded.AutoMode,
                    selected = !anyChosen && !isCasting,
                    onClick = {
                        // TODO(phase3-adapter): setPreferredAudioDevice(null)
                        // once the audio-output engine exists (NEEDS.md §(b) D2).
                        Timber.w("DeviceOutputSheet: no audio-output engine; Automatic is already in effect")
                    },
                )
                // Mochy listed service.audioOutputs.devices here, then the
                // cast targets under "Other devices on your network".
                // Both engines are out of scope; the sections stay hidden
                // until they exist rather than showing an empty list.
            }

            Spacer(modifier = Modifier.height(8.dp))
            DeviceRow(
                label = "Pair a new device",
                detail = "Opens Bluetooth settings",
                icon = Icons.Rounded.SettingsBluetooth,
                onClick = {
                    runCatching {
                        context.startActivity(
                            android.content.Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)
                                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                },
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        color = Color(0xFF8A8A8A),
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 10.dp),
    )
}

@Composable
private fun VolumeCard(
    icon: Int,
    title: String,
    value: Float,
    onValueChange: (Float) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MenuRowSurface)
            .padding(horizontal = 14.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SheetIcon(icon, tint = Color.White)
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = title,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.volume_down),
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(16.dp),
            )
            LineSlider(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
            )
            Icon(
                painter = painterResource(R.drawable.volume_up),
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun DeviceRow(
    label: String,
    detail: String?,
    icon: ImageVector,
    onClick: () -> Unit,
    selected: Boolean = false,
    playing: Boolean = false,
) {
    val checkAlpha by animateFloatAsState(if (selected) 1f else 0f, label = "deviceCheck")
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected || playing) Color.White.copy(alpha = 0.10f) else MenuRowSurface)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = RemoteFocus,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = Color.White,
                fontSize = 14.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (detail != null) {
                // a dac's line (kind, rates, bit depth, passthrough) runs long: it wraps instead of losing its end
                Text(
                    text = detail,
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                )
            }
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(22.dp)
                .graphicsLayer { alpha = checkAlpha }
                .clip(CircleShape)
                .background(Color.White),
        ) {
            Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.Black, modifier = Modifier.size(15.dp))
        }
    }
}
