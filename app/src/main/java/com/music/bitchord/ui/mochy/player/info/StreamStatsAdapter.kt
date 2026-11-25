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
// - Provider switching (ProviderSwitchHost.force): WIRED (batch 37) —
//   Mochy's ProviderSwitch engine (stop, resolve, reload at the same
//   spot) ported in ui/mochy/playback/ProviderSwitch.kt, resolving
//   through SourceResolver.forcedTrack and reloading with a
//   source-pinned bitchord://source URI (or the song's own watch URI
//   for YouTube).
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
import com.music.bitchord.data.sources.SourceResolver
import com.music.bitchord.data.sources.StreamFormat
import com.music.bitchord.data.sources.TrackMatcher
import com.music.bitchord.download.Downloads
import com.music.bitchord.playback.AudioRouting
import com.music.bitchord.ui.mochy.models.MediaMetadata
import com.music.bitchord.ui.mochy.playback.ForcedRoute
import com.music.bitchord.ui.mochy.playback.MochyPlayerConnection
import com.music.bitchord.ui.mochy.playback.PlayingFrom
import com.music.bitchord.ui.mochy.playback.ProviderSwitch
import com.music.bitchord.ui.mochy.playback.ProviderSwitchState
import com.music.bitchord.ui.mochy.playback.StreamDetails
import com.music.bitchord.ui.mochy.playback.StreamFacts
import com.music.bitchord.ui.mochy.playback.YouTubeProviderId
import com.music.bitchord.ui.mochy.playback.audio.AudioOutputs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow

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
            bitDepths = info?.encodings?.toList().orEmpty().mapNotNull { encoding ->
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
 * The force-change sheet's switch: Mochy's ProviderSwitch engine
 * (ui/mochy/playback/ProviderSwitch.kt) with this host over the player
 * connection (batch 37). Resolving goes through SourceResolver — a
 * source-pinned bitchord://source URI for addons, the song's own
 * bitchord://watch URI for YouTube — and the reload replaces the
 * current media item at the held position, so the song carries on
 * where it was, from the picked provider.
 */
class ProviderSwitchHost(private val connection: MochyPlayerConnection) : ProviderSwitch.Host {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val switch = ProviderSwitch({ scope }, this)
    val switchState: StateFlow<ProviderSwitchState> = switch.state

    private var heldPositionMs = 0L

    fun force(providerId: String, optionId: String?) {
        // a picked quality applies before the switch resolves: YouTube's
        // is written by the sheet itself; an addon's goes to its config,
        // which is what its client reads when the stream is asked for
        if (optionId != null && providerId != YouTubeProviderId) {
            SourceRegistry.configs.value.firstOrNull { it.id == providerId }?.let { config ->
                if (config.pickedQuality != optionId) SourceRegistry.update(config.copy(pickedQuality = optionId))
            }
        }
        switch.force(providerId)
    }

    fun reset() = switch.acknowledge()

    override fun currentMediaId(): String? = connection.mediaMetadata.value?.id

    override fun holdPlayback(): Boolean {
        val player = connection.player ?: return false
        heldPositionMs = player.currentPosition.coerceAtLeast(0L)
        val wasPlaying = player.isPlaying
        player.pause()
        return wasPlaying
    }

    override fun resumePlayback() {
        connection.player?.play()
    }

    override suspend fun resolve(providerId: String, mediaId: String): ForcedRoute? {
        val song = connection.mediaMetadata.value ?: return null
        if (song.id != mediaId) return null
        // a device file has no other provider to move to
        if (song.localUri != null || mediaId.startsWith("LOCAL_")) return null
        val target = TrackMatcher.Target(
            title = song.title,
            artist = song.artists.joinToString { it.name },
            durationSec = song.duration.takeIf { it > 0 },
            album = song.album?.title,
            isExplicit = song.explicit,
            isVideo = song.isVideoSong,
        )
        return if (providerId == YouTubeProviderId) {
            // already a YouTube song: its own id is the route
            if (SourceRegistry.parseTrackKey(mediaId) == null) {
                ForcedRoute(providerId, "bitchord://watch?v=$mediaId")
            } else {
                val youTube = SourceRegistry.activeForPlayback().firstOrNull { it.kind == SourceKind.YOUTUBE }
                    ?: return null
                val trackId = SourceResolver.forcedTrack(youTube.configId, target) ?: return null
                ForcedRoute(providerId, "bitchord://watch?v=$trackId")
            }
        } else {
            val trackId = SourceResolver.forcedTrack(providerId, target) ?: return null
            ForcedRoute(providerId, SourceRegistry.trackUri(providerId, trackId))
        }
    }

    override fun switchTo(mediaId: String, route: ForcedRoute, play: Boolean) {
        val player = connection.player ?: return
        val index = player.currentMediaItemIndex
        val current = player.currentMediaItem ?: return
        if (current.mediaId != mediaId) return
        val reloaded = current.buildUpon().setUri(route.playbackUri).build()
        player.replaceMediaItem(index, reloaded)
        player.seekTo(index, heldPositionMs)
        player.prepare()
        player.playWhenReady = play
    }
}
