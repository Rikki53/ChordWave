// ForceProviderSheet.kt — ported from Mochy Music (GPL-3.0): ui/player/info/ForceProviderSheet.kt
//
// Adaptation from Mochy (see NEEDS.md): Mochy reads its providers and the
// switch state off MusicService (service.sourceModules, service.providerSwitch
// — the modules system, out of scope). Here the providers, the switch state
// and the force callback arrive as params, so the sheet UI is complete and
// the adapter round only has to supply the data:
// - providers: the usable providers, each with its quality options
// - switchState: what a forced switch is doing (Idle by default)
// - onForceProvider: called with (providerId, optionId?); the default logs
//   a warning and does nothing.
// The YouTube quality preference is still read and written here, as Mochy
// does. With no providers yet, one muted line says so instead of leaving
// an empty sheet.

package com.music.bitchord.ui.mochy.player.info

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.bitchord.ui.mochy.component.RemoteFocus
import com.music.bitchord.ui.mochy.constants.AudioQuality
import com.music.bitchord.ui.mochy.constants.AudioQualityKey
import com.music.bitchord.ui.mochy.playback.ProviderSwitchMaxTries
import com.music.bitchord.ui.mochy.playback.ProviderSwitchState
import com.music.bitchord.ui.mochy.playback.YouTubeProviderId
import com.music.bitchord.ui.mochy.player.menu.LocalMenuSheetClose
import com.music.bitchord.ui.mochy.player.menu.MenuRowSurface
import com.music.bitchord.ui.mochy.player.menu.MenuSheetSurface
import com.music.bitchord.ui.mochy.utils.rememberPreference
import kotlinx.coroutines.delay
import timber.log.Timber

private val YouTubeQualities = listOf(AudioQuality.AUTO, AudioQuality.HIGH, AudioQuality.MEDIUM, AudioQuality.LOW)

/** one quality (or other) option a provider offers, shown as a chip (Mochy's module StreamOption) */
data class StreamOption(val id: String, val label: String, val description: String)

/** one provider the sheet can force the song to, with the options its row shows */
data class ForceProviderEntry(
    val id: String,
    val name: String,
    val options: List<StreamOption> = emptyList(),
    val selectedOption: String? = null,
)

/** "force change": plays this song from another of the providers that are switched on, at the quality picked */
@Composable
fun ForceProviderSheet(
    currentProviderId: String?,
    onDismiss: () -> Unit,
    providers: List<ForceProviderEntry> = emptyList(),
    switchState: ProviderSwitchState = ProviderSwitchState.Idle,
    onForceProvider: (providerId: String, optionId: String?) -> Unit = { _, _ ->
        // fallback only: StreamInfoSheet passes the real switch host (batch 37)
        Timber.w("ForceProviderSheet: no switch host wired")
    },
) {
    val (youTubeQuality, setYouTubeQuality) = rememberPreference(AudioQualityKey, AudioQuality.AUTO.name)

    MenuSheetSurface(onDismiss = onDismiss, wrapHeight = true) { _ ->
        val close = LocalMenuSheetClose.current
        // once it plays from the new provider there's nothing left to choose
        LaunchedEffect(switchState) {
            if (switchState is ProviderSwitchState.Done) {
                delay(700)
                close()
            }
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth().navigationBarsPadding(),
        ) {
            InfoColumn(modifier = Modifier.widthIn(max = 600.dp)) {
                InfoSheetTitle("Play from")
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "The song stops while it's fetched from the one you pick.",
                    color = InfoMuted,
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(16.dp))
                SwitchStatus(switchState, nameOf = { id -> providers.firstOrNull { it.id == id }?.name ?: id })
                if (providers.isEmpty()) {
                    Text(
                        text = "No other providers are available yet.",
                        color = InfoMuted,
                        fontSize = 13.sp,
                    )
                }
                providers.forEach { entry ->
                    ProviderChoiceRow(
                        providerId = entry.id,
                        name = entry.name,
                        isCurrent = entry.id == currentProviderId,
                        busy = switchState is ProviderSwitchState.Working,
                        options = when {
                            entry.id == YouTubeProviderId -> YouTubeQualities.map { StreamOption(it.name, it.name.lowercase().replaceFirstChar(Char::uppercase), "") }
                            else -> entry.options
                        },
                        selectedOption = if (entry.id == YouTubeProviderId) youTubeQuality else entry.selectedOption,
                        onPick = { option ->
                            if (option != null && entry.id == YouTubeProviderId) setYouTubeQuality(option)
                            onForceProvider(entry.id, option)
                        },
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun SwitchStatus(state: ProviderSwitchState, nameOf: (String) -> String) {
    AnimatedVisibility(
        visible = state !is ProviderSwitchState.Idle,
        enter = fadeIn(tween(200)) + expandVertically(tween(260)),
        exit = fadeOut(tween(160)) + shrinkVertically(tween(220)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 14.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(MenuRowSurface)
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            when (state) {
                is ProviderSwitchState.Working -> {
                    Text("Fetching from ${nameOf(state.providerId)}…", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text("Try ${state.attempt} of ${state.of}, up to a minute", color = InfoMuted, fontSize = 12.sp)
                    Spacer(Modifier.height(10.dp))
                    val progress by animateFloatAsState(state.attempt / state.of.toFloat(), label = "switchTries")
                    Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f))) {
                        Box(Modifier.fillMaxWidth(progress).fillMaxHeight().clip(CircleShape).background(Color.White))
                    }
                }
                is ProviderSwitchState.Failed -> {
                    Text("Source unavailable", color = Color(0xFFFF7A7A), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "${nameOf(state.providerId)} didn't answer after $ProviderSwitchMaxTries tries, so the song kept playing where it was. Try another one.",
                        color = InfoMuted,
                        fontSize = 12.sp,
                    )
                }
                is ProviderSwitchState.Done -> {
                    Text("Now playing from ${nameOf(state.providerId)}", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                ProviderSwitchState.Idle -> Unit
            }
        }
    }
}

@Composable
private fun ProviderChoiceRow(
    providerId: String,
    name: String,
    isCurrent: Boolean,
    busy: Boolean,
    options: List<StreamOption>,
    selectedOption: String?,
    /** null when the row itself was tapped, keeping the quality as it is */
    onPick: (String?) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MenuRowSurface)
            .clickable(
                enabled = !busy,
                interactionSource = remember { MutableInteractionSource() },
                indication = RemoteFocus,
            ) { onPick(null) }
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProviderLogo(providerId, name, 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = name, color = Color.White, fontSize = 15.sp, lineHeight = 19.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    text = if (isCurrent) "Playing from here now" else "Tap to play from here",
                    color = InfoMuted,
                    fontSize = 12.sp,
                )
            }
        }
        if (options.size > 1) {
            Spacer(Modifier.height(10.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            ) {
                options.forEach { option ->
                    QualityChip(
                        label = option.label,
                        selected = option.id == selectedOption,
                        enabled = !busy,
                        onClick = { onPick(option.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun QualityChip(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .height(28.dp)
            .clip(shape)
            .then(if (selected) Modifier.background(Color.White) else Modifier.border(1.dp, Color.White.copy(alpha = 0.25f), shape))
            .clickable(enabled = enabled, interactionSource = remember { MutableInteractionSource() }, indication = RemoteFocus, onClick = onClick)
            .padding(horizontal = 12.dp),
    ) {
        Text(text = label, color = if (selected) Color.Black else Color.White.copy(alpha = 0.8f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}
