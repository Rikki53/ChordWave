// StreamInfoSheet.kt — ported from Mochy Music (GPL-3.0): ui/player/info/StreamInfoSheet.kt
//
// Adaptations from Mochy (see NEEDS.md §(b)):
// - Mochy reads everything off MusicService (playingFrom, the stream
//   inspector, audio outputs, the audio lab, cast, audio pass). ChordWave
//   has no MusicService, so rememberStatCards gathers what the connection
//   and the settings can actually say today and leaves the rest null:
//   the cards only show lines with a real value, so nothing is guessed.
//   The provider is derived from the metadata (a local file vs YouTube
//   Music) until real provider attribution lands.
// - The "Star on GitHub" pill is dropped: it needs the source modules
//   (out of scope). The larp-mode branches are dropped too (LarpMode is
//   not ported; see NEEDS.md §(c)).

package com.music.bitchord.ui.mochy.player.info

import android.content.Context
import android.media.AudioManager
import android.media.MediaCodecList
import android.os.Build
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.music.bitchord.ui.mochy.constants.AudioNormalizationKey
import com.music.bitchord.ui.mochy.constants.CrossmixMode
import com.music.bitchord.ui.mochy.constants.CrossmixModeKey
import com.music.bitchord.ui.mochy.constants.DefaultTransitionMode
import com.music.bitchord.ui.mochy.constants.HiResOutputKey
import com.music.bitchord.data.NerdStats
import com.music.bitchord.ui.mochy.constants.MusicHapticsEnabledKey
import com.music.bitchord.ui.mochy.constants.SkipSilenceKey
import com.music.bitchord.ui.mochy.models.FormatEntity
import com.music.bitchord.ui.mochy.playback.LocalPlayerConnection
import com.music.bitchord.ui.mochy.playback.PlayingFrom
import com.music.bitchord.ui.mochy.playback.StreamFacts
import com.music.bitchord.ui.mochy.playback.YouTubeProviderId
import com.music.bitchord.ui.mochy.player.menu.LocalMenuSheetStack
import com.music.bitchord.ui.mochy.player.menu.MenuSheetSurface
import com.music.bitchord.ui.mochy.player.menu.SongHeader
import com.music.bitchord.ui.mochy.player.menu.rememberMenuSheetStack
import com.music.bitchord.ui.mochy.player.info.ProviderSwitchHost
import com.music.bitchord.ui.mochy.player.info.StreamStatsAdapter
import com.music.bitchord.ui.mochy.player.info.rememberNerdSnapshot
import com.music.bitchord.ui.mochy.utils.rememberPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/** "stream info": where the playing song comes from, and its whole road to the speaker */
@Composable
fun StreamInfoSheet(onDismiss: () -> Unit) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val metadata by playerConnection.mediaMetadata.collectAsState()
    val song = metadata ?: return
    var showForce by remember { mutableStateOf(false) }
    val stack = LocalMenuSheetStack.current ?: rememberMenuSheetStack()
    val snapshot by rememberNerdSnapshot()
    val switchHost = remember { ProviderSwitchHost(playerConnection) }
    val switchState by switchHost.switchState.collectAsState()

    val from = remember(song.id, song.localUri, snapshot) { StreamStatsAdapter.playingFrom(song, snapshot) }
    val cards = rememberStatCards(song.id, song.duration * 1000L, from, snapshot)

    CompositionLocalProvider(LocalMenuSheetStack provides stack) {
        MenuSheetSurface(onDismiss = onDismiss, halfDetent = 0.72f, fullDetent = 0.94f) { _ ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding(),
            ) {
                InfoColumn(modifier = Modifier.widthIn(max = 600.dp)) {
                    InfoSheetTitle("Stream Info")
                    Spacer(Modifier.height(16.dp))
                    SongHeader(song)
                    InfoDivider()
                    InfoSectionTitle("Provider")
                    Spacer(Modifier.height(12.dp))
                    ProviderRow(from, onForce = { showForce = true })
                    NerdsDivider()
                    InfoSectionTitle("Format & about")
                    Spacer(Modifier.height(14.dp))
                    cards.forEachIndexed { index, card ->
                        if (index > 0) CardConnector()
                        StatCardView(card)
                    }
                }
            }
        }

        if (showForce) {
            ForceProviderSheet(
                currentProviderId = from.providerId,
                onDismiss = { showForce = false; switchHost.reset() },
                providers = remember { StreamStatsAdapter.providerEntries(LocalContext.current) },
                switchState = switchState,
                onForceProvider = switchHost::force,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProviderRow(from: PlayingFrom?, onForce: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        if (from == null) {
            LoadingBar(width = 64.dp, height = 64.dp)
            Spacer(Modifier.width(14.dp))
            Column {
                LoadingBar(width = 120.dp)
                Spacer(Modifier.height(10.dp))
                LoadingBar(width = 160.dp, height = 24.dp)
            }
            return@Row
        }
        ProviderLogo(from.providerId, from.name, 64.dp)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = from.name,
                color = Color.White,
                fontSize = 18.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.02).em,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            from.quality?.let {
                Text(text = it, color = InfoMuted, fontSize = 12.sp, lineHeight = 16.sp)
            }
            Spacer(Modifier.height(8.dp))
            // side by side when they fit, the second one on its own line when they don't
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // a file on the phone has nowhere else to come from
                if (from.providerId != PlayingFrom.Device) {
                    InfoPill(text = "Force change", filled = true, onClick = onForce)
                }
            }
        }
    }
}

@Composable
private fun StatCardView(card: StatCard) {
    // lines arrive as the decoder and track open; the card grows into them instead of jumping
    InfoCard(modifier = Modifier.animateContentSize(spring(stiffness = 500f))) {
        Text(
            text = card.title,
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.02).em,
        )
        Spacer(Modifier.height(8.dp))
        card.lines.forEach { (label, value) -> StatLine(label, value) }
    }
}

/** everything the cards read, gathered from the connection and the settings */
@Composable
private fun rememberStatCards(
    mediaId: String,
    durationMs: Long,
    from: PlayingFrom?,
    snapshot: NerdStats.Snapshot?,
): List<StatCard> {
    val playerConnection = LocalPlayerConnection.current ?: return emptyList()
    val context = LocalContext.current
    val isMuted by playerConnection.isMuted.collectAsState()
    // Stream inputs come from StreamStatsAdapter (phase3-adapter-host),
    // which reads the decoder's NerdStats snapshot: codec/sample rate/
    // bitrate/channels/bit depth + provider attribution. The YouTube
    // format record, mixer, cast and audio-pass snapshots have no CW
    // source and stay null — those lines hide instead of being guessed.
    val details = StreamStatsAdapter.streamDetails(snapshot).copy(mediaId = mediaId)
    val facts = StreamStatsAdapter.streamFacts(snapshot)
    val format: FormatEntity? = null
    val device = remember(snapshot) { StreamStatsAdapter.activeOutputDevice(context) }
    val (normalize) = rememberPreference(AudioNormalizationKey, true)
    val (skipSilence) = rememberPreference(SkipSilenceKey, false)
    val (haptics) = rememberPreference(MusicHapticsEnabledKey, false)
    val (hiRes) = rememberPreference(HiResOutputKey, false)
    val (crossmixMode) = rememberPreference(CrossmixModeKey, DefaultTransitionMode.name)
    val systemMixerRate = remember {
        (context.getSystemService(Context.AUDIO_SERVICE) as AudioManager).getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)?.toIntOrNull()
    }
    val hardware by produceState<Boolean?>(initialValue = null, details.decoderName) {
        value = details.decoderName?.let { name -> withContext(Dispatchers.Default) { isHardwareDecoder(name) } }
    }

    val transitions = when (runCatching { CrossmixMode.valueOf(crossmixMode) }.getOrNull()) {
        CrossmixMode.CROSSMIX -> "Crossmix"
        CrossmixMode.AUTO_CROSSFADE, CrossmixMode.MANUAL_CROSSFADE -> "Crossfade"
        else -> null
    }
    val input = StatsInput(
        mediaId = mediaId,
        details = details,
        facts = facts,
        // youtube's record only describes youtube's stream
        youtubeFormat = format?.takeIf { from?.providerId == YouTubeProviderId },
        from = from,
        durationMs = durationMs,
        playerVolume = if (isMuted) 0f else 1f,
        decoderHardware = hardware,
        device = device,
        mixer = null,
        systemMixerRate = systemMixerRate,
        castingTo = null,
        effects = EffectsSnapshot(
            normalize = normalize,
            skipSilence = skipSilence,
            haptics = haptics,
            transitions = transitions,
            hiResWanted = hiRes,
            equalizerBands = StreamStatsAdapter.equalizerBands(),
            floatAllowed = StreamStatsAdapter.floatAllowed(),
        ),
        passthrough = rememberPassthroughStat(device = device, audioPass = null),
    )
    return remember(input) { buildStatCards(input) }
}

private val hardwareDecoders = ConcurrentHashMap<String, Boolean>()

// android 10+ says outright; before that a google or c2.android software decoder gives itself away by name
private fun isHardwareDecoder(name: String): Boolean = hardwareDecoders.getOrPut(name) {
    val info = runCatching { MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos.firstOrNull { it.name == name } }.getOrNull()
    if (info != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        info.isHardwareAccelerated
    } else {
        val lower = name.lowercase()
        !(lower.startsWith("omx.google.") || lower.startsWith("c2.android.") || lower.contains(".sw."))
    }
}
