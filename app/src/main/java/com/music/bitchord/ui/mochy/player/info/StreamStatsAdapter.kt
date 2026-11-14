// StreamStatsAdapter.kt — ChordWave adapter (NOT a Mochy port): the
// stream-stats inputs the StreamInfoSheet cards read, from what ChordWave
// can actually supply. Mochy read all of this off its MusicService (the
// stream inspector, audio outputs, the audio lab, cast, audio pass);
// ChordWave's measurements live in NerdStats (published by PlaybackService
// from the decoder's own Format) and its providers in SourceRegistry.
//
// WIRED (real values):
// - StreamFacts (codec + sample rate) and the measurable half of
//   StreamDetails (sample mime, bitrate, sample rate, channels, source bit
//   depth) from NerdStats.current — the playing stream's measured snapshot.
// - Provider attribution (PlayingFrom) from the snapshot's sourceName plus
//   Downloads: a downloaded song plays from its file ("Downloads"), a
//   device file is "On this device", anything else is the source that
//   served it (YouTube Music, or the addon's own display name + the quality
//   it claimed).
// - Provider list for ForceProviderSheet from SourceRegistry
//   (activeForPlayback), with each addon's quality options; YouTube's id
//   is mapped to "youtube" (its config id is a per-install UUID, and the
//   sheet keys its YouTube quality chips off the "youtube" id).
// - The active output device from AudioRouting (the same resolver the
//   playback service uses), with its sample rates / bit depths from
//   AudioDeviceInfo.
// - Equalizer band count (AppSettings) and float-output permission
//   (outputPcmMode) for the effects snapshot.
//
// STUBBED HONESTLY (null / default, the cards hide those lines):
// - Decoder name, decoded/output PCM encodings + rates, buffer bytes,
//   offload/tunneling, underruns, file size, resampler fields, dither:
//   ChordWave measures none of these (no stream inspector).
// - FormatEntity (the YouTube format record: itag, content length,
//   loudness): formats are resolved at play time and not kept; only the
//   claimed StreamFormat survives, and it feeds the provider quality line.
// - USB DAC mixer, cast device, audio-pass snapshot: no CW equivalent.
// - Speed/pitch: need the connection's playbackParameters (player-core
//   NEEDS.md §(b) B2, unlanded).
// - Provider switching (ProviderSwitchHost.force): NO ChordWave API
//   re-resolves the playing song from another provider mid-playback. The
//   mechanism exists one layer down — SourceResolver pins a provider with
//   the ?s=<configId> query on a bitchord://source URI — but stopping the
//   song, rebuilding its media item with the pinned URI and reloading at
//   the same position is playback-service work. Until it lands, force()
//   reports the switch as Failed rather than pretending it happened.
//
// The sheet itself (StreamInfoSheet.kt, another chunk's file) is NOT edited
// here: NEEDS.md §(merge) gives the exact lines that swap its null inputs
// for these calls.

package com.music.bitchord.ui.mochy.player.info

import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import com.music.bitchord.data.NerdStats
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.OutputPcmMode
import com.music.bitchord.data.sources.SourceKind
import com.music.bitchord.data.sources.SourceRegistry
import com.music.bitchord.data.sources.StreamFormat
import com.music.bitchord.download.Downloads
import com.music.bitchord.playback.AudioRouting
import com.music.bitchord.ui.mochy.models.MediaMetadata
import com.music.bitchord.ui.mochy.playback.PlayingFrom
import com.music.bitchord.ui.mochy.playback.ProviderSwitchState
import com.music.bitchord.ui.mochy.playback.StreamDetails
import com.music.bitchord.ui.mochy.playback.StreamFacts
import com.music.bitchord.ui.mochy.playback.YouTubeProviderId
import com.music.bitchord.ui.mochy.playback.audio.AudioOutputs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

object StreamStatsAdapter {

    /** The playing stream's measured facts, or null before the player has reported any. */
    fun streamFacts(snapshot: NerdStats.Snapshot?): StreamFacts? {
        val snap = snapshot ?: return null
        val codec = StreamFacts.codecOf(snap.mimeType)
        if (codec == null && snap.sampleRateHz == null) return null
        return StreamFacts(
            codec = codec,
            sampleRate = snap.sampleRateHz,
            fromModule = isModuleSource(snap.sourceName),
        )
    }

    /** The measurable half of the stream details; the inspector-only fields stay null. */
    fun streamDetails(snapshot: NerdStats.Snapshot?): StreamDetails {
        val snap = snapshot ?: return StreamDetails()
        return StreamDetails(
            mediaId = snap.mediaId,
            sampleMime = snap.mimeType,
            bitrate = snap.bitrateKbps?.times(1_000),
            sampleRate = snap.sampleRateHz,
            channels = snap.channels,
            sourceBitDepth = snap.bitDepth,
        )
    }

    /** Where [song] is playing from right now, with the quality its provider claimed. */
    fun playingFrom(song: MediaMetadata, snapshot: NerdStats.Snapshot?): PlayingFrom {
        // A downloaded song plays from the file on disk, whatever the stream says.
        if (Downloads.verifiedSavedUri(song.id) != null) {
            return PlayingFrom(PlayingFrom.Downloads, "Downloads")
        }
        if (song.localUri != null || song.id.startsWith("LOCAL_")) {
            return PlayingFrom(PlayingFrom.Device, "On this device")
        }
        // PlaybackService.currentSourceName's own words: "YouTube" for a
        // YouTube video id, "Local Storage" for a local file, an addon's
        // display name, or "Unknown".
        val name = snapshot?.sourceName
        if (name.isNullOrBlank() || name == "Unknown" || name == "YouTube") {
            return PlayingFrom(YouTubeProviderId, "YouTube Music")
        }
        if (name == "Local Storage") {
            return PlayingFrom(PlayingFrom.Device, "On this device")
        }
        val config = SourceRegistry.configs.value.firstOrNull { it.displayName == name }
        return PlayingFrom(
            providerId = config?.id ?: name,
            name = name,
            quality = qualityLabel(snapshot?.claimed),
        )
    }

    /** The providers the force-change sheet can offer, with their quality options. */
    fun providerEntries(): List<ForceProviderEntry> {
        val configs = SourceRegistry.configs.value.associateBy { it.id }
        return SourceRegistry.activeForPlayback().map { source ->
            val config = configs[source.configId]
            ForceProviderEntry(
                // The sheet keys its YouTube quality chips off the "youtube"
                // id; the YouTube config's own id is a per-install UUID.
                id = if (source.kind == SourceKind.YOUTUBE) YouTubeProviderId else source.configId,
                name = source.displayName,
                options = config?.qualityOptions.orEmpty().map { StreamOption(it.value, it.label, "") },
                selectedOption = config?.pickedQuality,
            )
        }
    }

    /** The device the audio is reaching, or null when it cannot be told. */
    fun activeOutputDevice(context: Context): AudioOutputs.Device? {
        val manager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return null
        val device = AudioRouting.outputs(manager).firstOrNull { it.isActive } ?: return null
        // AudioOutputs has no OTHER kind; an unrepresentable route stays null
        // rather than being mislabelled as the phone.
        val kind = when (device.kind) {
            AudioRouting.Kind.PHONE -> AudioOutputs.Kind.PHONE
            AudioRouting.Kind.WIRED -> AudioOutputs.Kind.WIRED
            AudioRouting.Kind.USB -> AudioOutputs.Kind.USB
            AudioRouting.Kind.BLUETOOTH -> AudioOutputs.Kind.BLUETOOTH
            AudioRouting.Kind.HDMI -> AudioOutputs.Kind.HDMI
            AudioRouting.Kind.OTHER -> return null
        }
        val info = AudioRouting.infoFor(manager, device.id)
        return AudioOutputs.Device(
            id = device.id,
            type = device.type,
            name = device.name,
            kind = kind,
            isActive = true,
            isChosen = AudioRouting.selectedId.value == device.id,
            maxSampleRate = info?.sampleRates?.maxOrNull(),
            bitDepths = info?.encodings.orEmpty().mapNotNull { encoding ->
                when (encoding) {
                    AudioFormat.ENCODING_PCM_16BIT -> 16
                    AudioFormat.ENCODING_PCM_24BIT_PACKED -> 24
                    AudioFormat.ENCODING_PCM_32BIT, AudioFormat.ENCODING_PCM_FLOAT -> 32
                    else -> null
                }
            }.distinct(),
        )
    }

    /** The equalizer's band count while it is on, null while it is off. */
    fun equalizerBands(): Int? =
        if (AppSettings.equalizerEnabled.value) AppSettings.equalizerBands.value.size else null

    /** Whether float PCM output is allowed (the 32-bit float output mode). */
    fun floatAllowed(): Boolean = AppSettings.outputPcmMode.value == OutputPcmMode.FLOAT_32

    /** "FLAC 24-bit · 96 kHz" from what a source said it would serve, or null when it said nothing. */
    fun qualityLabel(format: StreamFormat?): String? {
        val claimed = format ?: return null
        val parts = listOfNotNull(
            claimed.codec?.uppercase(),
            claimed.bitDepth?.let { "$it-bit" },
            claimed.sampleRateHz?.let { "${it / 1_000} kHz" },
        )
        return parts.joinToString(" · ").takeIf { it.isNotEmpty() }
    }

    private fun isModuleSource(sourceName: String?): Boolean =
        !sourceName.isNullOrBlank() && sourceName != "YouTube" &&
            sourceName != "Local Storage" && sourceName != "Unknown"
}

/** The playing stream's measured snapshot, as state (null until the player reports one). */
@Composable
fun rememberNerdSnapshot(): State<NerdStats.Snapshot?> = NerdStats.current.collectAsState()

/**
 * The force-change sheet's switch state. force() reports Failed: no
 * ChordWave API re-resolves the playing song from another provider
 * mid-playback (see the file header). The sheet shows the failure instead
 * of hanging on Working, and the log says why.
 */
class ProviderSwitchHost {
    private val _switchState = MutableStateFlow<ProviderSwitchState>(ProviderSwitchState.Idle)
    val switchState: StateFlow<ProviderSwitchState> = _switchState.asStateFlow()

    fun force(providerId: String, optionId: String?) {
        // TODO(phase3-adapter): provider switching needs playback-service
        // support — stop the song, rebuild its media item with a
        // source-pinned URI (SourceResolver, ?s=<configId>) and reload at
        // the same position. Until then the switch fails honestly.
        Timber.w("ProviderSwitchHost: forcing provider $providerId is not supported yet (Phase 3 adapter)")
        _switchState.value = ProviderSwitchState.Failed(providerId)
    }

    fun reset() {
        _switchState.value = ProviderSwitchState.Idle
    }
}
