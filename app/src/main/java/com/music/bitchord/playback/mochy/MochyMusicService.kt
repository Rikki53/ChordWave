// MochyMusicService — the Mochy engine's player service (two-engine
// architecture, RENCANA-DUA-MESIN.md, Tahap 4).
//
// The Modern UI runs on this service; the Classic UI keeps running on
// PlaybackService. EngineSwitch enables exactly one of the two components,
// so there is never a second MediaLibraryService alive to fight over the
// notification, widget, Bluetooth or Android Auto.
//
// What is Mochy's here (the engine, not the skin):
//  - stream resolution through the ported Mochy innertube
//    (MochyStreamResolver: Mochy's client order, format ranking, deciphering);
//  - the player cache: a SimpleCache keyed by media id, fed by a
//    ResolvingDataSource — the shape of Mochy's createDataSourceFactory;
//  - playback records in the shared Room MusicDatabase (FormatEntity per
//    resolve, Event + PlayCount + totalPlayTime per counted play), beside
//    the Classic bucket the same plays feed through ListeningRecorder, so
//    history and statistics stay one (Riki's decision);
//  - search/library for Android Auto from the Mochy innertube + Room.
//
// What stays ChordWave's, deliberately: the MediaItem shape the Modern
// builders produce (bitchord://watch URIs + extras, so the queue, lyrics,
// share and menus keep working unchanged), module/source tracks
// (bitchord://source resolves through SourceResolver, ChordWave's own
// Sources system), Cast (CastPlayback/CastController, shared with the
// Classic engine), the widget snapshot, and the equaliser settings (applied
// through the platform Equalizer on this player's audio session).
//
// Tahap 5: downloads run on the Mochy engine too (MochyDownloadUtil's
// DownloadManager over the download cache, exported to the shared
// Music/ChordWave folder), the player's chain reads download cache ->
// player cache -> network through MochyDataSourceFactory, and the lyric
// share reads through the same factory (shareDataSourceFactory below),
// so an exported lyric video carries the recording the lyrics belong
// to. The caches themselves are process singletons (MochyCaches, Mochy's
// DI places) shared by player, downloads and share.
//
// Tahap 6 (the last stage) completes the engine: the sleep timer runs
// here (the shared SleepTimer the Modern sheet already sets, faded out
// through SleepFade like Classic), loudness normalisation levels each
// track from the Mochy resolver's own figures (the shared
// LoudnessProcessor in the DSP chain), the USB-DAC bit-perfect output
// routes in front of the normal sink (BitPerfectRoutingSink, the same
// chain Classic builds), Listen Together party sync binds this player
// (PartySync, user intent told apart from party corrections by the
// session player below), PoToken minting lives in MochyStreamResolver,
// and transport fades/effects are Mochy's own TransportTransitions +
// CrossmixTransitionAudioProcessor, driven by the Modern transport-fade
// settings. Still NOT here (recorded honestly): the automatic two-player
// crossmix (Mochy's CrossmixDirector + analysis) and SABR — Classic
// keeps both; a track-to-track overlap in Modern remains a plain
// gapless handoff, and skips/pauses fade the Mochy way.
//
// Placement note: the plan puts Mochy code in :mochyengine, and the
// providers + Room database live there. This service sits in :app because
// it is ChordWave integration code (Cast, widget, stats, settings) around
// that engine — the same place Mochy keeps its own MusicService (its :app).
// The TV manifest declares only PlaybackService, so TV stays Classic-only.

package com.music.bitchord.playback.mochy

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.audiofx.Equalizer
import android.os.SystemClock
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.DeviceInfo
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.SilenceSkippingAudioProcessor
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaConstants
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.mochyy.mochymusic.db.entities.Event
import com.mochyy.mochymusic.db.entities.FormatEntity
import com.mochyy.mochymusic.db.entities.PlayCountEntity
import com.mochyy.mochymusic.db.entities.SongEntity
import com.music.bitchord.MainActivity
import com.music.bitchord.R
import com.music.bitchord.auth.SignedIn
import com.music.bitchord.data.LikeState
import com.music.bitchord.data.OfflinePlaylists
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.model.ArtistRef
import com.music.bitchord.data.model.LikeStatus
import com.music.bitchord.data.model.PlaybackSourceType
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.mochydata.MochyDatabaseHolder
import com.music.bitchord.data.sources.SourceResolver
import com.music.bitchord.data.stats.ListeningRecorder
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.OutputPcmMode
import com.music.bitchord.download.Downloads
import com.music.bitchord.data.listentogether.ListenTogether
import com.music.bitchord.playback.ACTION_CLEAR_BOUNDED_CONTEXT
import com.music.bitchord.playback.ACTION_QUEUE_DRAG
import com.music.bitchord.playback.AudioOutputPolicy
import com.music.bitchord.playback.AudioOutputStatus
import com.music.bitchord.playback.EXTRA_QUEUE_DRAG_ACTIVE
import com.music.bitchord.playback.LoudnessProcessor
import com.music.bitchord.playback.PartySync
import com.music.bitchord.playback.SleepTimer
import com.music.bitchord.playback.audio.DspChain
import com.music.bitchord.playback.audio.PrecisionAudioSink
import com.music.bitchord.playback.audio.bitperfect.BitPerfectAudioSink
import com.music.bitchord.playback.audio.bitperfect.BitPerfectOutput
import com.music.bitchord.playback.audio.bitperfect.BitPerfectRoutingSink
import com.music.bitchord.playback.mochy.audio.CrossmixClockSink
import com.music.bitchord.playback.mochy.audio.CrossmixTransitionAudioProcessor
import com.music.bitchord.playback.ACTION_TOGGLE_AUTOPLAY
import com.music.bitchord.playback.ACTION_TOGGLE_FAVORITE
import com.music.bitchord.playback.ACTION_TOGGLE_SHUFFLE
import com.music.bitchord.playback.cast.CastController
import com.music.bitchord.playback.cast.CastPlayback
import com.music.bitchord.playback.cast.CastSink
import com.music.bitchord.playback.cast.CastStream
import com.music.bitchord.playback.cast.RemoteSpeaker
import com.music.bitchord.playback.cast.Speakers
import com.music.bitchord.playback.toMediaItem
import com.music.bitchord.playback.toSong
import androidx.datastore.preferences.core.edit
import com.music.bitchord.data.mochydata.discord.DiscordPresence
import com.music.bitchord.data.mochydata.discord.NowPlaying
import com.music.bitchord.data.mochydata.scrobble.Scrobbler
import com.music.bitchord.data.mochydata.scrobble.ScrobbleTimer
import com.music.bitchord.ui.mochy.constants.AudioNormalizationKey
import com.music.bitchord.ui.mochy.constants.AudioQuality
import com.music.bitchord.ui.mochy.constants.AudioQualityKey
import com.music.bitchord.ui.mochy.constants.DontSkipOnFailureKey
import com.music.bitchord.ui.mochy.constants.InfiniteQueueKey
import com.music.bitchord.ui.mochy.constants.MixWithOtherAppsKey
import com.music.bitchord.ui.mochy.constants.PauseOnMute
import com.music.bitchord.ui.mochy.constants.PersistentShuffleAcrossQueuesKey
import com.music.bitchord.ui.mochy.constants.PersistentQueueKey
import com.music.bitchord.ui.mochy.constants.ScrobbleDelayPercentKey
import com.music.bitchord.ui.mochy.constants.ScrobbleDelaySecondsKey
import com.music.bitchord.ui.mochy.constants.ScrobbleMinSongDurationKey
import com.music.bitchord.ui.mochy.constants.HiResOutputKey
import com.music.bitchord.ui.mochy.constants.RepeatModeKey
import com.music.bitchord.ui.mochy.constants.ResumeOnBluetoothConnectKey
import com.music.bitchord.ui.mochy.constants.SkipSilenceKey
import com.music.bitchord.ui.mochy.constants.StopPlaybackOnTaskRemovedKey
import com.music.bitchord.ui.mochy.constants.StreamFailureTimeoutKey
import com.music.bitchord.ui.mochy.constants.TransportEffect
import com.music.bitchord.ui.mochy.constants.TransportEffectKey
import com.music.bitchord.ui.mochy.constants.TransportFadeDefaultSeconds
import com.music.bitchord.ui.mochy.constants.TransportFadeEnabledKey
import com.music.bitchord.ui.mochy.constants.TransportFadeMaxSeconds
import com.music.bitchord.ui.mochy.constants.TransportFadeMinSeconds
import com.music.bitchord.ui.mochy.constants.TransportFadeSecondsKey
import com.music.bitchord.ui.mochy.utils.PreferencesCache
import com.music.bitchord.ui.mochy.di.PlayerIntegrationsEntryPoint
import com.music.bitchord.ui.mochy.models.MediaMetadata
import com.music.bitchord.ui.mochy.utils.dataStore
import dagger.hilt.android.EntryPointAccessors
import com.music.bitchord.ui.mochy.utils.dataStore
import com.music.bitchord.wear.WearBridge
import com.music.bitchord.widget.MediaWidget
import com.music.bitchord.widget.MediaWidgetSnapshot
import com.music.innertube.YouTube
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import timber.log.Timber
import java.io.File
import java.time.LocalDateTime
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

@OptIn(UnstableApi::class)
class MochyMusicService : MediaLibraryService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val handler = Handler(Looper.getMainLooper())

    private var player: ExoPlayer? = null
    private var mediaSession: MediaLibrarySession? = null

    private val castPlayback = CastPlayback(
        scope = scope,
        localPlayer = { player },
        resolve = ::resolveForCast,
        say = { message -> Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show() },
    )
    private val speakers = Speakers(listOf(castPlayback))

    private var equalizer: Equalizer? = null
    private var favoriteActionJob: Job? = null
    private var autoplayJob: Job? = null

    // Tahap 6: the shared DSP chain's loudness stage (this player's own,
    // like Classic's per-player stage) and Mochy's crossmix renderer,
    // idle (a straight copy) except while a transport fade runs.
    private val loudnessProcessor = LoudnessProcessor().apply { gainFor = ::loudnessGainFor }
    private val crossmixProcessor = CrossmixTransitionAudioProcessor()
    private val dspChain = DspChain(loudness = loudnessProcessor)

    /** The volume the listener set; transport fades scale it, never replace it. */
    @Volatile
    private var userVolume = 1f

    /** Mochy's transport fades/effects for pause, play and skips. */
    private val transport: TransportTransitions by lazy { TransportTransitions({ scope }, TransportHost()) }

    /** Listen Together sync for this engine; null until onCreate starts it. */
    private var partySync: PartySync? = null

    // Batch 53: the Mochy DataStore settings this engine runs on (Mochy's
    // MusicService reads the same keys from the same store). Observed
    // live in observePlaybackPrefs; read on the audio thread where noted.
    @Volatile
    private var normalizeAudio = true

    // Queue persistence (batch 53): Mochy keeps the queue on disk so it
    // survives process death; the saved key/position let timed saves skip
    // unchanged queues the way Mochy's saveQueueToDisk does.
    private var savedQueueKey: Int = 0
    private var savedQueuePosition: Long = 0L

    /** Stream-failure retry/skip job (Mochy's failStream), cancelled on recovery. */
    private var failureJob: Job? = null

    // Pause-on-mute bookkeeping (Mochy's onDeviceVolumeChanged).
    private var wasPlayingBeforeVolumeMute = false
    private var isPausedByVolumeMute = false

    /** Resume-on-Bluetooth (Mochy's audio-device callback). */
    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            super.onAudioDevicesAdded(addedDevices)
            val hasBluetooth = addedDevices?.any {
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                    it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
            } == true
            if (hasBluetooth &&
                PreferencesCache.current(applicationContext.dataStore)[ResumeOnBluetoothConnectKey] == true
            ) {
                val exoPlayer = player ?: return
                if (exoPlayer.playbackState == Player.STATE_READY && !exoPlayer.isPlaying) {
                    exoPlayer.play()
                }
            }
        }
    }

    // Listening sampler state (mirrors ListeningRecorder's rule).
    private var sampledMediaId: String? = null
    private var sampledSong: Song? = null
    private var playedThisTrackMs = 0L
    private var countedMs = 0L
    private var lastSampleAt = 0L
    private var playCountedForTrack = false

    override fun onCreate() {
        super.onCreate()
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setChannelId(CHANNEL_ID)
                .setChannelName(R.string.playback_channel_name)
                .build()
                .apply { setSmallIcon(R.drawable.ic_notification_logo) },
        )

        MochyStreamResolver.init(this)
        val exoPlayer = buildPlayer()
        player = exoPlayer
        exoPlayer.addListener(playerListener)
        applySettings(exoPlayer)
        observePlaybackPrefs(exoPlayer)
        applyEqualizer(exoPlayer)
        runCatching {
            getSystemService(AudioManager::class.java)
                ?.registerAudioDeviceCallback(audioDeviceCallback, handler)
        }
        partySync = PartySync(scope) { player }.also { it.start() }
        watchSleepTimer()
        observeTransportPrefs()
        observeScrobblePrefs()
        // the Discord status reconnects as soon as the network is back
        runCatching {
            getSystemService(ConnectivityManager::class.java)
                ?.registerDefaultNetworkCallback(discordNetworkCallback)
        }

        mediaSession = MediaLibrarySession.Builder(
            this,
            MochySessionPlayer(
                exoPlayer,
                speakers,
                transport,
                partySync,
                onUserVolume = { userVolume = it },
                persistentShuffle = {
                    PreferencesCache.current(applicationContext.dataStore)[PersistentShuffleAcrossQueuesKey] ?: true
                },
            ),
            LibraryCallback(),
        )
            .setId(SESSION_ID)
            .setSessionActivity(sessionActivity())
            .setSessionExtras(transportSlotReservations())
            .build()
        refreshCustomLayout()

        CastController.ensureStarted(this)
        CastController.attach(castPlayback)
        restoreQueueFromDisk(exoPlayer)
        startSampler()
        Timber.tag(TAG).i("Mochy engine service created")
    }

    private fun buildPlayer(): ExoPlayer {
        return ExoPlayer.Builder(this)
            .setRenderersFactory(mochyRenderersFactory())
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(
                    MochyDataSourceFactory.create(this) { videoId, resolved ->
                        recordFormat(videoId, resolved)
                    },
                ),
            )
            .setAudioAttributes(AUDIO_ATTRIBUTES, /* handleAudioFocus = */ true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .setMaxSeekToPreviousPositionMs(BACK_RESTARTS_AFTER_MS)
            .build()
    }

    private fun applySettings(exoPlayer: ExoPlayer) {
        // Batch 53: initial values come from the Mochy DataStore, as in
        // Mochy's MusicService (buildPlayer reads SkipSilenceKey and
        // RepeatModeKey the same way) — not from ChordWave's AppSettings,
        // which only Classic's settings screen writes.
        val prefs = runCatching { PreferencesCache.current(applicationContext.dataStore) }.getOrNull()
        exoPlayer.skipSilenceEnabled = prefs?.get(SkipSilenceKey) ?: false
        exoPlayer.repeatMode = prefs?.get(RepeatModeKey) ?: Player.REPEAT_MODE_OFF
        normalizeAudio = prefs?.get(AudioNormalizationKey) ?: true
        if (prefs?.get(MixWithOtherAppsKey) == true) {
            exoPlayer.setAudioAttributes(AUDIO_ATTRIBUTES, /* handleAudioFocus = */ false)
        }
    }

    /**
     * Live Mochy playback settings (batch 53): the same DataStore
     * observations Mochy's MusicService makes — skip silence, mix with
     * other apps (audio-focus handling), and loudness normalisation.
     * Modern's settings screens write these keys; before this, the
     * engine read ChordWave's AppSettings instead and the settings did
     * nothing.
     */
    private fun observePlaybackPrefs(exoPlayer: ExoPlayer) {
        scope.launch {
            applicationContext.dataStore.data
                .map { prefs ->
                    Triple(
                        prefs[SkipSilenceKey] ?: false,
                        prefs[MixWithOtherAppsKey] ?: false,
                        prefs[AudioNormalizationKey] ?: true,
                    )
                }
                .distinctUntilChanged()
                .collect { (skipSilence, mix, normalize) ->
                    exoPlayer.skipSilenceEnabled = skipSilence
                    exoPlayer.setAudioAttributes(AUDIO_ATTRIBUTES, /* handleAudioFocus = */ !mix)
                    normalizeAudio = normalize
                    setupLoudness(exoPlayer.currentMediaItem?.mediaId)
                }
        }
    }

    /** Mochy's infinite queue (the Modern autoplay button's own pref). */
    private fun infiniteQueueEnabled(): Boolean =
        runCatching {
            PreferencesCache.current(applicationContext.dataStore)[InfiniteQueueKey] ?: true
        }.getOrDefault(true)

    private fun setInfiniteQueueEnabled(enabled: Boolean) {
        scope.launch(Dispatchers.IO) {
            runCatching {
                applicationContext.dataStore.edit { it[InfiniteQueueKey] = enabled }
            }
        }
    }

    private fun recordFormat(videoId: String, resolved: MochyResolvedStream) {
        val format = resolved.format
        scope.launch(Dispatchers.IO) {
            runCatching {
                MochyDatabaseHolder.get(this@MochyMusicService).upsertFormat(
                    FormatEntity(
                        id = videoId,
                        itag = format.itag,
                        mimeType = format.mimeType.split(";")[0],
                        codecs = format.mimeType.substringAfter("codecs=", "").removeSurrounding("\""),
                        bitrate = format.bitrate,
                        sampleRate = format.audioSampleRate,
                        contentLength = format.contentLength ?: 0L,
                        loudnessDb = resolved.audioConfig?.loudnessDb,
                        perceptualLoudnessDb = resolved.audioConfig?.perceptualLoudnessDb,
                        playbackUrl = resolved.playbackTrackingUrl,
                    ),
                )
            }.onFailure { Timber.tag(TAG).w(it, "Format record failed for $videoId") }
        }
    }

    /**
     * The song share's preview and export read the song through this, so
     * they hit the same caches and the same resolver playback does —
     * Mochy's MusicService.shareDataSourceFactory, which is its
     * createDataSourceFactory itself.
     */
    fun shareDataSourceFactory(): DataSource.Factory = MochyDataSourceFactory.shareFactory(this)

    private fun currentAudioQuality(): AudioQuality {
        val raw = runCatching {
            PreferencesCache.current(applicationContext.dataStore)[AudioQualityKey]
        }.getOrNull()
        return runCatching { AudioQuality.valueOf(raw ?: AudioQuality.AUTO.name) }
            .getOrDefault(AudioQuality.AUTO)
    }

    private fun isMetered(): Boolean = runCatching {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        cm?.isActiveNetworkMetered == true
    }.getOrDefault(false)

    /** The stream a Cast receiver can open for [item], or null when there is none it can. */
    private suspend fun resolveForCast(item: MediaItem): CastStream? = withContext(Dispatchers.IO) {
        val uri = item.localConfiguration?.uri ?: return@withContext null
        when {
            uri.authority == "source" -> {
                val stream = runCatching {
                    withTimeout(RESOLVE_TIMEOUT_MS) { SourceResolver.resolve(uri) }
                }.getOrNull() ?: return@withContext null
                CastStream(stream.url, CastPlayback.mimeTypeOf(Uri.parse(stream.url)))
            }
            uri.getQueryParameter("v") != null -> {
                val videoId = uri.getQueryParameter("v")!!
                val resolved = runCatching {
                    withTimeout(RESOLVE_TIMEOUT_MS) {
                        MochyStreamResolver.resolve(videoId, currentAudioQuality(), isMetered())
                    }
                }.getOrNull() ?: return@withContext null
                CastStream(resolved.url, CastPlayback.mimeTypeOf(Uri.parse(resolved.url)))
            }
            uri.scheme == "http" || uri.scheme == "https" ->
                CastStream(uri.toString(), CastPlayback.mimeTypeOf(uri))
            uri.scheme == "content" || uri.scheme == "file" -> {
                // A downloaded track: the receiver cannot reach the file, so
                // resolve the remote stream for the same video id.
                val videoId = item.mediaId.takeIf { it.isNotBlank() && Downloads.saved.value.containsKey(it) }
                    ?: return@withContext null
                val resolved = runCatching {
                    withTimeout(RESOLVE_TIMEOUT_MS) {
                        MochyStreamResolver.resolve(videoId, currentAudioQuality(), isMetered())
                    }
                }.getOrNull() ?: return@withContext null
                CastStream(resolved.url, CastPlayback.mimeTypeOf(Uri.parse(resolved.url)))
            }
            else -> null
        }
    }

    private fun sessionActivity(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java)
            .setAction(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun transportSlotReservations() = Bundle().apply {
        putBoolean(MediaConstants.EXTRAS_KEY_SLOT_RESERVATION_SEEK_TO_PREV, true)
        putBoolean(MediaConstants.EXTRAS_KEY_SLOT_RESERVATION_SEEK_TO_NEXT, true)
    }

    // ---- player events ----------------------------------------------------

    // Batch 55a (Integrations, Mochy original): the Modern engine feeds the
    // Discord presence and the scrobbler, exactly where Mochy's MusicService
    // does. Both are Hilt singletons reached through the entry point, since
    // this service isn't built by Hilt.
    private val integrations: PlayerIntegrationsEntryPoint by lazy {
        EntryPointAccessors.fromApplication(applicationContext, PlayerIntegrationsEntryPoint::class.java)
    }
    private val discordPresence: DiscordPresence by lazy { integrations.discordPresence() }
    private val scrobbler: Scrobbler by lazy { integrations.scrobbler() }
    private val scrobbleTimer by lazy { ScrobbleTimer({ scope }, scrobbler) }
    private val discordNetworkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: android.net.Network) {
            runCatching { discordPresence.nudge() }
        }
    }

    /** what the player is on, in the shape the scrobble timer reads */
    private fun currentMediaMetadata(): MediaMetadata? {
        val item = player?.currentMediaItem ?: return null
        val song = item.toSong()
        val durationSec = ((player?.duration ?: 0L).takeIf { it > 0 }?.div(1000))?.toInt() ?: 0
        return MediaMetadata(
            id = song.videoId,
            title = song.title,
            artists = song.artists.map { MediaMetadata.Artist(it.browseId, it.name) }
                .ifEmpty { listOf(MediaMetadata.Artist(song.artistId, song.artist)) },
            duration = durationSec,
            thumbnailUrl = song.thumbnailUrl,
            album = song.albumId?.let { MediaMetadata.Album(it, song.albumName.orEmpty()) },
            localUri = song.localUri,
        )
    }

    /** Tells the Discord status what's playing; it ignores this when Discord isn't signed in or is off. */
    private fun publishPresence() {
        val p = player
        val item = p?.currentMediaItem
        if (p == null || item == null || p.playbackState == Player.STATE_IDLE || p.playbackState == Player.STATE_ENDED) {
            discordPresence.update(null)
            return
        }
        val song = item.toSong()
        discordPresence.update(
            NowPlaying(
                mediaId = song.videoId,
                title = song.title,
                artist = song.artist,
                album = song.albumName,
                artworkUrl = song.thumbnailUrl,
                // ChordWave's MediaItem carries no artist picture; Mochy
                // resolves one from its library, which has no counterpart here.
                artistArtworkUrl = null,
                durationMs = p.duration.takeIf { it > 0 } ?: 0L,
                positionMs = p.currentPosition,
                readAtMs = System.currentTimeMillis(),
                speed = p.playbackParameters.speed,
                // buffering with play pressed still counts as playing, so the status doesn't flicker
                isPlaying = p.playWhenReady,
                quality = song.sourceQuality,
                isYouTube = song.localUri == null,
            ),
        )
    }

    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            // "Sleep after this song": the queue moving on by itself is
            // the moment the track the user meant has finished. REPEAT
            // counts too, or the timer would never fire with repeat-one on.
            if ((reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO ||
                    reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) &&
                SleepTimer.afterTrack.value
            ) {
                player?.pause()
                SleepTimer.cancel()
            }
            // The queue moving on by itself is the user's intent as far
            // as the party is concerned — see PartySync.
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                partySync?.onLocalIntent()
            }
            clearStreamFailure()
            setupLoudness(mediaItem?.mediaId)
            flushPlayRecord()
            sampledMediaId = mediaItem?.mediaId
            sampledSong = mediaItem?.toSong()
            playedThisTrackMs = 0L
            countedMs = 0L
            lastSampleAt = System.currentTimeMillis()
            playCountedForTrack = false
            speakers.onLocalTransition(mediaItem, reason)
            scrobbleTimer.onSongStart(currentMediaMetadata(), player?.duration ?: 0L)
            publishPresence()
            publishWidgetState()
            refreshCustomLayout()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            publishWidgetState()
            if (playbackState == Player.STATE_ENDED) {
                flushPlayRecord()
                ListeningRecorder.onStopped()
                scrobbleTimer.onSongStop()
                publishPresence()
                maybeAutoplayRefill()
            }
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            partySync?.onPlayWhenReadyChanged(playWhenReady, reason)
            publishWidgetState()
            if (!playWhenReady) ListeningRecorder.onStopped()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            publishWidgetState()
            if (!isPlaying) ListeningRecorder.onStopped()
            scrobbleTimer.onPlayingChanged(isPlaying, currentMediaMetadata(), player?.duration ?: 0L)
            publishPresence()
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            // Batch 53: persisted to the Mochy DataStore (RepeatModeKey),
            // as in Mochy's MusicService — each engine keeps its own.
            scope.launch(Dispatchers.IO) {
                runCatching {
                    applicationContext.dataStore.edit { it[RepeatModeKey] = repeatMode }
                }
            }
            speakers.onLocalRepeatChanged(repeatMode)
        }

        override fun onDeviceVolumeChanged(volume: Int, muted: Boolean) {
            super.onDeviceVolumeChanged(volume, muted)
            // Pause on mute (Mochy's onDeviceVolumeChanged): muting the
            // device pauses, unmuting resumes only what the mute paused.
            val pauseOnMute = runCatching {
                PreferencesCache.current(applicationContext.dataStore)[PauseOnMute] ?: false
            }.getOrDefault(false)
            val exoPlayer = player ?: return
            if ((volume == 0 || muted) && pauseOnMute) {
                if (exoPlayer.isPlaying) {
                    wasPlayingBeforeVolumeMute = true
                    isPausedByVolumeMute = true
                    exoPlayer.pause()
                }
            } else if (volume > 0 && !muted && pauseOnMute) {
                if (wasPlayingBeforeVolumeMute && !exoPlayer.isPlaying && !speakers.active) {
                    wasPlayingBeforeVolumeMute = false
                    isPausedByVolumeMute = false
                    exoPlayer.play()
                }
            }
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            publishWidgetState()
        }

        override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
            speakers.onLocalQueueChanged()
            publishWidgetState()
            saveQueueToDisk()
        }

        override fun onPlayerError(error: PlaybackException) {
            Timber.tag(TAG).w(error, "Playback error")
            ListeningRecorder.onStopped()
            failStream()
        }
    }

    /**
     * Queue persistence, Mochy's PersistentQueueKey behavior (batch 53):
     * the queue (songs, index, position) is kept in a JSON file so it
     * survives process death, and restored paused on the next start.
     * Mochy serializes its PersistQueue with Java serialization; here the
     * same fields ride JSON because our Song/MediaItem pair already
     * round-trips through toSong()/toMediaItem().
     */
    private fun queueKey(exoPlayer: ExoPlayer): Int {
        var key = exoPlayer.mediaItemCount
        for (i in 0 until exoPlayer.mediaItemCount) {
            key = 31 * key + exoPlayer.getMediaItemAt(i).mediaId.hashCode()
        }
        return key
    }

    private fun saveQueueToDisk(onlyChanges: Boolean = false) {
        val exoPlayer = player ?: return
        if (exoPlayer.mediaItemCount == 0) return
        val key = queueKey(exoPlayer)
        val position = exoPlayer.currentPosition.coerceAtLeast(0L)
        val queueChanged = key != savedQueueKey
        if (onlyChanges && !queueChanged &&
            kotlin.math.abs(position - savedQueuePosition) < POSITION_SAVE_STEP_MS
        ) {
            return
        }
        savedQueueKey = key
        savedQueuePosition = position
        val songs = (0 until exoPlayer.mediaItemCount).map { exoPlayer.getMediaItemAt(it).toSong() }
        val index = exoPlayer.currentMediaItemIndex
        scope.launch(Dispatchers.IO) {
            runCatching {
                val root = JSONObject()
                root.put("index", index)
                root.put("position", position)
                val array = JSONArray()
                songs.forEach { song -> array.put(song.toQueueJson()) }
                root.put("songs", array)
                val file = File(filesDir, PERSISTENT_QUEUE_FILE)
                val tmp = File(filesDir, "$PERSISTENT_QUEUE_FILE.tmp")
                tmp.writeText(root.toString())
                tmp.renameTo(file)
            }.onFailure { Timber.tag(TAG).w(it, "Failed to save queue") }
        }
    }

    private fun restoreQueueFromDisk(exoPlayer: ExoPlayer) {
        val prefs = runCatching { PreferencesCache.current(applicationContext.dataStore) }.getOrNull()
        if (prefs?.get(PersistentQueueKey) == false) return
        if (exoPlayer.mediaItemCount > 0) return
        val file = File(filesDir, PERSISTENT_QUEUE_FILE)
        if (!file.exists()) return
        runCatching {
            val root = JSONObject(file.readText())
            val array = root.optJSONArray("songs") ?: return
            val songs = (0 until array.length()).mapNotNull { i ->
                array.optJSONObject(i)?.toQueueSong()
            }
            if (songs.isEmpty()) return
            val index = root.optInt("index", 0).coerceIn(0, songs.lastIndex)
            val position = root.optLong("position", 0L).coerceAtLeast(0L)
            exoPlayer.setMediaItems(songs.map { it.toMediaItem() }, index, position)
            exoPlayer.prepare()
            savedQueueKey = queueKey(exoPlayer)
            savedQueuePosition = position
            Timber.tag(TAG).i("Restored persisted queue (${songs.size} songs)")
        }.onFailure {
            Timber.tag(TAG).w(it, "Failed to restore persisted queue, clearing data")
            runCatching { file.delete() }
        }
    }

    private fun Song.toQueueJson(): JSONObject = JSONObject().apply {
        put("videoId", videoId)
        put("title", title)
        put("artist", artist)
        put("thumbnailUrl", thumbnailUrl ?: JSONObject.NULL)
        put("durationText", durationText ?: JSONObject.NULL)
        put("artistId", artistId ?: JSONObject.NULL)
        put("albumId", albumId ?: JSONObject.NULL)
        put("albumName", albumName ?: JSONObject.NULL)
        put("isExplicit", isExplicit ?: JSONObject.NULL)
        put("isVideo", isVideo)
        put("isVideoOrigin", isVideoOrigin)
        put("setVideoId", setVideoId ?: JSONObject.NULL)
        put("sourceQuality", sourceQuality ?: JSONObject.NULL)
        put("sourceHiRes", sourceHiRes)
        put("radioName", radioName ?: JSONObject.NULL)
        put("playbackSource", playbackSource ?: JSONObject.NULL)
        put("playbackSourceType", playbackSourceType?.name ?: JSONObject.NULL)
        put("playbackSourceId", playbackSourceId ?: JSONObject.NULL)
        put("localUri", localUri ?: JSONObject.NULL)
        put("localPath", localPath ?: JSONObject.NULL)
        val artistsArray = JSONArray()
        artists.forEach { ref ->
            artistsArray.put(
                JSONObject().apply {
                    put("name", ref.name)
                    put("browseId", ref.browseId ?: JSONObject.NULL)
                },
            )
        }
        put("artists", artistsArray)
    }

    private fun JSONObject.toQueueSong(): Song? {
        val videoId = optString("videoId").takeIf { it.isNotEmpty() } ?: return null
        val artistsArray = optJSONArray("artists")
        val refs = if (artistsArray == null) {
            emptyList()
        } else {
            (0 until artistsArray.length()).mapNotNull { i ->
                val obj = artistsArray.optJSONObject(i) ?: return@mapNotNull null
                val name = obj.optString("name").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                ArtistRef(name = name, browseId = obj.optString("browseId").takeIf { it.isNotEmpty() })
            }
        }
        return Song(
            videoId = videoId,
            title = optString("title"),
            artist = optString("artist"),
            thumbnailUrl = optString("thumbnailUrl").takeIf { it.isNotEmpty() },
            durationText = optString("durationText").takeIf { it.isNotEmpty() },
            artistId = optString("artistId").takeIf { it.isNotEmpty() },
            artists = refs,
            albumId = optString("albumId").takeIf { it.isNotEmpty() },
            albumName = optString("albumName").takeIf { it.isNotEmpty() },
            isExplicit = if (has("isExplicit") && !isNull("isExplicit")) optBoolean("isExplicit") else null,
            isVideo = optBoolean("isVideo"),
            isVideoOrigin = optBoolean("isVideoOrigin"),
            setVideoId = optString("setVideoId").takeIf { it.isNotEmpty() },
            sourceQuality = optString("sourceQuality").takeIf { it.isNotEmpty() },
            sourceHiRes = optBoolean("sourceHiRes"),
            radioName = optString("radioName").takeIf { it.isNotEmpty() },
            playbackSource = optString("playbackSource").takeIf { it.isNotEmpty() },
            playbackSourceType = optString("playbackSourceType")
                .takeIf { it.isNotEmpty() }
                ?.let { runCatching { PlaybackSourceType.valueOf(it) }.getOrNull() },
            playbackSourceId = optString("playbackSourceId").takeIf { it.isNotEmpty() },
            localUri = optString("localUri").takeIf { it.isNotEmpty() },
            localPath = optString("localPath").takeIf { it.isNotEmpty() },
        )
    }

    /**
     * A failed stream the player has given up on (Mochy's failStream,
     * batch 53): pause, then either retry the same stream every few
     * seconds (DontSkipOnFailureKey — staying on it means trying again
     * until it plays) or skip to the next track after
     * StreamFailureTimeoutKey seconds.
     */
    private fun failStream() {
        val exoPlayer = player ?: return
        exoPlayer.pause()
        clearStreamFailure()
        val prefs = runCatching { PreferencesCache.current(applicationContext.dataStore) }.getOrNull()
        if (prefs?.get(DontSkipOnFailureKey) == true) {
            failureJob = scope.launch {
                delay(STREAM_RETRY_INTERVAL_MS)
                Timber.tag(TAG).d("Retrying the failed stream")
                player?.prepare()
                player?.play()
            }
            return
        }
        if (exoPlayer.nextMediaItemIndex == C.INDEX_UNSET) return
        val timeoutMs = (prefs?.get(StreamFailureTimeoutKey) ?: 6).coerceIn(5, 20) * 1000L
        failureJob = scope.launch {
            delay(timeoutMs)
            Timber.tag(TAG).d("Stream failure timed out, skipping to next track")
            player?.seekToNextMediaItem()
            player?.prepare()
            player?.play()
        }
    }

    private fun clearStreamFailure() {
        failureJob?.cancel()
        failureJob = null
    }

    /**
     * Infinite queue: when the queue runs out and autoplay is on, the
     * current song's radio follows — the same refill the Classic engine
     * does, so Modern's infinite-queue button keeps its meaning.
     */
    private fun maybeAutoplayRefill() {
        val exoPlayer = player ?: return
        // Batch 53: Mochy's infinite queue governs the refill, as in
        // Mochy's MusicService (InfiniteQueueKey, on by default).
        if (!infiniteQueueEnabled()) return
        val seedId = exoPlayer.currentMediaItem?.mediaId ?: return
        if (seedId.startsWith("http") || seedId.startsWith("content") || seedId.startsWith("file")) return
        autoplayJob?.cancel()
        autoplayJob = scope.launch {
            val songs = withContext(Dispatchers.IO) {
                YtMusicRepository.radio(seedId).getOrNull()
            } ?: return@launch
            val existing = (0 until exoPlayer.mediaItemCount)
                .map { exoPlayer.getMediaItemAt(it).mediaId }.toSet()
            val items = songs
                .filter { it.videoId != seedId && it.videoId !in existing }
                .map { it.toMediaItem() }
            if (items.isNotEmpty() && exoPlayer.currentMediaItem?.mediaId == seedId) {
                exoPlayer.addMediaItems(items)
                exoPlayer.prepare()
                exoPlayer.play()
            }
        }
    }

    // ---- listening records --------------------------------------------------
    // One sampler feeds both histories: the Classic bucket through
    // ListeningRecorder (Stats/Replay read it) and the shared Room database
    // (Event + PlayCount + totalPlayTime, Mochy's own records). A play is
    // counted once per track at the same half-or-four-minutes rule, floor
    // thirty seconds, so the two histories never disagree about what a play
    // is.

    private fun startSampler() {
        handler.post(object : Runnable {
            override fun run() {
                sampleTick()
                handler.postDelayed(this, SAMPLE_INTERVAL_MS)
            }
        })
    }

    private fun sampleTick() {
        val exoPlayer = player ?: return
        saveQueueToDisk(onlyChanges = true)
        if (!exoPlayer.isPlaying) return
        val song = sampledSong ?: exoPlayer.currentMediaItem?.toSong()?.also { sampledSong = it } ?: return
        if (song.videoId != sampledMediaId) {
            sampledMediaId = song.videoId
            playedThisTrackMs = 0L
            countedMs = 0L
            lastSampleAt = System.currentTimeMillis()
            playCountedForTrack = false
            return
        }
        ListeningRecorder.onSample(song, exoPlayer.duration)
        val now = System.currentTimeMillis()
        val step = (now - lastSampleAt).coerceIn(0L, MAX_SAMPLE_STEP_MS)
        lastSampleAt = now
        if (step <= 0L) return
        playedThisTrackMs += step
        if (!playCountedForTrack) {
            val length = exoPlayer.duration.takeIf { it > 0 } ?: 0L
            val threshold = if (length > 0) {
                min(length / 2, PLAY_CEILING_MS).coerceAtLeast(PLAY_FLOOR_MS)
            } else {
                PLAY_FLOOR_MS
            }
            if (playedThisTrackMs >= threshold) {
                playCountedForTrack = true
                countedMs = playedThisTrackMs
                recordPlay(song, playedThisTrackMs)
            }
        }
    }

    /** A track left before its play was counted still earned its minutes. */
    private fun flushPlayRecord() {
        val song = sampledSong ?: return
        if (playedThisTrackMs > 0L && !playCountedForTrack) {
            recordPlay(song, playedThisTrackMs, counted = false)
        } else if (playedThisTrackMs > countedMs) {
            // Already counted: only the minutes since the count are new.
            recordPlayTimeOnly(song, playedThisTrackMs - countedMs)
        }
        playedThisTrackMs = 0L
        countedMs = 0L
    }

    private fun recordPlay(song: Song, playedMs: Long, counted: Boolean = true) {
        scope.launch(Dispatchers.IO) {
            runCatching {
                val db = MochyDatabaseHolder.get(this@MochyMusicService)
                db.insertSongs(
                    listOf(
                        SongEntity(
                            id = song.videoId,
                            title = song.title,
                            thumbnailUrl = song.thumbnailUrl,
                            albumName = song.albumName,
                        ),
                    ),
                )
                if (counted) {
                    db.insertEvents(
                        listOf(Event(songId = song.videoId, timestamp = LocalDateTime.now(), playTime = playedMs)),
                    )
                    val now = LocalDateTime.now()
                    val existing = db.getPlayCount(song.videoId, now.year, now.monthValue)
                    db.insertPlayCounts(
                        listOf(
                            PlayCountEntity(
                                song = song.videoId,
                                year = now.year,
                                month = now.monthValue,
                                count = (existing?.count ?: 0) + 1,
                            ),
                        ),
                    )
                }
                db.addTotalPlayTime(song.videoId, playedMs)
            }.onFailure { Timber.tag(TAG).w(it, "Play record failed for ${song.videoId}") }
        }
    }

    private fun recordPlayTimeOnly(song: Song, playedMs: Long) {
        scope.launch(Dispatchers.IO) {
            runCatching {
                MochyDatabaseHolder.get(this@MochyMusicService).addTotalPlayTime(song.videoId, playedMs)
            }.onFailure { Timber.tag(TAG).w(it, "Play time record failed for ${song.videoId}") }
        }
    }

    // ---- widget / watch -----------------------------------------------------

    private fun canLike(item: MediaItem?): Boolean =
        item != null && (item.toSong().localUri != null || SignedIn.state(this).value)

    private fun isLiked(item: MediaItem?): Boolean {
        val song = item?.toSong() ?: return false
        return if (song.localUri != null) {
            OfflinePlaylists.isLiked(song)
        } else {
            LikeState.overrides.value[song.videoId] == LikeStatus.LIKE
        }
    }

    private fun publishWidgetState(playing: Boolean? = null) {
        val exoPlayer = player ?: return
        val item = exoPlayer.currentMediaItem ?: return
        val song = item.toSong()
        val liked = (if (song.localUri != null) OfflinePlaylists.isLiked(song) else null)
            ?: LikeState.overrides.value[song.videoId]?.let { it == LikeStatus.LIKE }
            ?: MediaWidgetSnapshot.load(this).takeIf { it.mediaId == song.videoId }?.isLiked
            ?: false
        val reported: Player = mediaSession?.player ?: exoPlayer
        val snapshot = MediaWidgetSnapshot(
            mediaId = song.videoId,
            title = song.title,
            artist = song.artist,
            artworkUrl = song.thumbnailUrl,
            isPlaying = playing ?: reported.playWhenReady,
            hasPrevious = exoPlayer.hasPreviousMediaItem(),
            hasNext = exoPlayer.hasNextMediaItem(),
            isLiked = liked,
            canLike = canLike(item),
            shuffleEnabled = exoPlayer.shuffleModeEnabled,
        )
        MediaWidgetSnapshot.save(this, snapshot)
        MediaWidget.refresh(this)
        WearBridge.publish(this, snapshot, reported, playing)
    }

    // ---- notification buttons -------------------------------------------------

    private fun refreshCustomLayout() {
        val session = mediaSession ?: return
        val liked = isLiked(player?.currentMediaItem)
        val shuffleEnabled = player?.shuffleModeEnabled == true
        val buttons = listOfNotNull(
            if (!canLike(player?.currentMediaItem)) null else CommandButton.Builder(
                if (liked) CommandButton.ICON_STAR_FILLED else CommandButton.ICON_STAR_UNFILLED,
            )
                .setCustomIconResId(
                    if (liked) R.drawable.ic_notification_star_filled else R.drawable.ic_notification_star,
                )
                .setSessionCommand(SessionCommand(ACTION_TOGGLE_FAVORITE, Bundle.EMPTY))
                .setDisplayName("Favorite")
                .build(),
            CommandButton.Builder(
                if (shuffleEnabled) CommandButton.ICON_SHUFFLE_ON else CommandButton.ICON_SHUFFLE_OFF,
            )
                .setCustomIconResId(
                    if (shuffleEnabled) R.drawable.ic_notification_shuffle else R.drawable.ic_notification_shuffle_off,
                )
                .setSessionCommand(SessionCommand(ACTION_TOGGLE_SHUFFLE, Bundle.EMPTY))
                .setDisplayName(if (shuffleEnabled) "Shuffle off" else "Shuffle on")
                .build(),
        )
        session.setCustomLayout(buttons)
        session.getMediaNotificationControllerInfo()?.let { session.setCustomLayout(it, buttons) }
    }

    private fun toggleFavorite(videoId: String) {
        favoriteActionJob?.cancel()
        val previous = LikeState.overrides.value[videoId] ?: LikeStatus.INDIFFERENT
        val target = if (previous == LikeStatus.LIKE) LikeStatus.INDIFFERENT else LikeStatus.LIKE
        LikeState.set(videoId, target)
        // a like here becomes a love on Last.fm/Libre.fm, when the user asked for that
        player?.currentMediaItem?.takeIf { it.mediaId == videoId }?.toSong()?.let { song ->
            scrobbler.love(song.artist, song.title, target == LikeStatus.LIKE)
        }
        refreshCustomLayout()
        publishWidgetState()
        favoriteActionJob = scope.launch {
            YtMusicRepository.rate(videoId, target)
                .onFailure {
                    LikeState.set(videoId, previous)
                    refreshCustomLayout()
                    publishWidgetState()
                    Timber.tag(TAG).w(it, "Favorite toggle failed for $videoId")
                }
        }
    }

    // ---- equaliser ------------------------------------------------------------
    // The Modern equaliser screen edits AppSettings' bands; the Classic
    // engine applies them through its DSP chain. Here the platform
    // Equalizer on this player's audio session applies the same gains
    // (seven stored bands mapped onto the device's bands by frequency), so
    // the screen keeps working on the Mochy engine.

    private fun applyEqualizer(exoPlayer: ExoPlayer) {
        runCatching {
            equalizer?.release()
            val eq = Equalizer(0, exoPlayer.audioSessionId)
            equalizer = eq
            updateEqualizer(eq)
        }.onFailure { Timber.tag(TAG).w(it, "Equalizer unavailable") }
        scope.launch {
            AppSettings.equalizerEnabled.collect { equalizer?.let(::updateEqualizer) }
        }
        scope.launch {
            AppSettings.equalizerBands.collect { equalizer?.let(::updateEqualizer) }
        }
    }

    private fun updateEqualizer(eq: Equalizer) {
        runCatching {
            eq.enabled = AppSettings.equalizerEnabled.value
            if (!eq.enabled) return
            val gains = AppSettings.equalizerBands.value
            if (gains.isEmpty()) return
            val range = eq.bandLevelRange
            val bandCount = eq.numberOfBands.toInt()
            for (band in 0 until bandCount) {
                val freqHz = eq.getCenterFreq(band.toShort()) / 1000
                val gainDb = gainForFrequency(gains, freqHz)
                val level = (gainDb * 100).roundToInt()
                    .coerceIn(range[0].toInt(), range[1].toInt())
                eq.setBandLevel(band.toShort(), level.toShort())
            }
        }.onFailure { Timber.tag(TAG).w(it, "Equalizer update failed") }
    }

    /** The stored seven gains (60 Hz … 14 kHz) sampled at [freqHz] by nearest band. */
    private fun gainForFrequency(gains: List<Float>, freqHz: Int): Float {
        val freqs = listOf(60, 150, 400, 1000, 2400, 6000, 14000)
        val index = freqs.indices.minByOrNull { kotlin.math.abs(freqs[it] - freqHz) } ?: 0
        return gains.getOrElse(index) { 0f }
    }

    // ---- audio chain (Tahap 6) ------------------------------------------------
    // The same sink stack Classic builds, for one player: Media3's sink
    // carrying Mochy's crossmix renderer + silence skipping + Sonic, the
    // precision DSP chain in front of it (loudness normalisation last, the
    // other stages idle at their defaults — the equaliser screen still
    // works through the platform Equalizer above), the bit-perfect USB
    // output in front of that (a track it can carry skips everything),
    // and Mochy's clock sink outermost so the crossmix renderer always
    // knows where in its song each buffer sits.

    private fun mochyRenderersFactory(): DefaultRenderersFactory = object : DefaultRenderersFactory(this) {
        init {
            // Do not force PCM_FLOAT onto an OEM speaker mixer merely
            // because the preference asks for it — the same policy (and
            // the same reason) as Classic's factory.
            setEnableAudioFloatOutput(shouldUseFloatOutput())
        }

        override fun buildAudioSink(
            context: Context,
            enableFloatOutput: Boolean,
            enableAudioTrackPlaybackParams: Boolean,
        ): AudioSink {
            val defaultSink = DefaultAudioSink.Builder(context)
                .setEnableFloatOutput(enableFloatOutput)
                // Speed handled by the AudioTrack itself on a float-capable
                // route, rather than by Sonic — see Classic's factory.
                .setEnableAudioTrackPlaybackParams(
                    enableAudioTrackPlaybackParams || enableFloatOutput,
                )
                .setAudioProcessorChain(
                    DefaultAudioSink.DefaultAudioProcessorChain(
                        arrayOf<AudioProcessor>(crossmixProcessor),
                        SilenceSkippingAudioProcessor(
                            MIN_SILENCE_US,
                            SilenceSkippingAudioProcessor.DEFAULT_SILENCE_RETENTION_RATIO,
                            SilenceSkippingAudioProcessor.DEFAULT_MAX_SILENCE_TO_KEEP_DURATION_US,
                            SilenceSkippingAudioProcessor.DEFAULT_MIN_VOLUME_TO_KEEP_PERCENTAGE,
                            SilenceSkippingAudioProcessor.DEFAULT_SILENCE_THRESHOLD_LEVEL,
                        ),
                        SonicAudioProcessor(),
                    ),
                )
                .build()
            val precision = PrecisionAudioSink(
                delegate = defaultSink,
                dspChain = dspChain,
                enableFloatOutput = enableFloatOutput,
            )
            // In front of everything above: a track the bit-perfect output
            // can carry skips the DSP chain and Media3's sink altogether.
            val routing = BitPerfectRoutingSink(precision, BitPerfectAudioSink(BitPerfectOutput.forContext(context)))
            return CrossmixClockSink(routing, crossmixProcessor)
        }
    }

    /** Whether Media3 may open a PCM-float AudioTrack on the current route. */
    private fun shouldUseFloatOutput(): Boolean {
        val advertisesFloat = runCatching {
            val manager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            manager?.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                ?.any { it.encodings.contains(AudioFormat.ENCODING_PCM_FLOAT) } == true
        }.getOrDefault(false)
        // Batch 53: the Modern hi-res toggle (HiResOutputKey, DataStore)
        // drives the float output, not Classic's outputPcmMode.
        val hiRes = runCatching {
            PreferencesCache.current(applicationContext.dataStore)[HiResOutputKey]
        }.getOrNull() ?: false
        return AudioOutputPolicy.shouldUseFloatOutput(
            requestedMode = if (hiRes) OutputPcmMode.FLOAT_32 else OutputPcmMode.PCM_16,
            routeKind = AudioOutputStatus.current.value.routeKind,
            advertisesPcmFloat = advertisesFloat,
        )
    }

    // ---- loudness normalisation (Tahap 6) --------------------------------------
    // The linear gain LoudnessProcessor applies: YouTube's own
    // normalisation figure, from the Mochy resolver's resolved streams
    // (MochyStreamResolver.loudnessDbFor), bounded the way the platform
    // effect Classic replaced was, or exactly 1 when nothing is known yet
    // or the setting is off. Called on the audio thread, so it only reads
    // things that are safe there: StateFlow values and concurrent maps.

    private fun loudnessGainFor(mediaId: String): Float = loudnessGainMb(mediaId)
        ?.let { 10.0.pow(it / 2000.0).toFloat() }
        ?: 1f

    private fun loudnessGainMb(mediaId: String?): Int? {
        // Batch 53: gated by Mochy's AudioNormalizationKey (observed in
        // observePlaybackPrefs), as in Mochy's MusicService — ChordWave's
        // loudness settings and speaker exception belong to Classic.
        if (!normalizeAudio) return null
        val id = mediaId?.takeIf { it.isNotBlank() } ?: return null
        val loudnessDb = MochyStreamResolver.loudnessDbFor(id) ?: return null
        return (-loudnessDb * 100.0).roundToInt().coerceIn(MIN_LOUDNESS_GAIN_MB, MAX_LOUDNESS_GAIN_MB)
    }

    /** Points the loudness stage at [mediaId] and the track queued after it. */
    private fun setupLoudness(mediaId: String?) {
        val exoPlayer = player ?: return
        loudnessProcessor.track(mediaId, nextMediaIdOf(exoPlayer))
        publishLoudnessFor(mediaId)
    }

    /** The track [exoPlayer] will move onto gaplessly, if any. */
    private fun nextMediaIdOf(exoPlayer: ExoPlayer): String? {
        val next = exoPlayer.nextMediaItemIndex
        if (next == C.INDEX_UNSET || next !in 0 until exoPlayer.mediaItemCount) return null
        return exoPlayer.getMediaItemAt(next).mediaId
    }

    private fun publishLoudnessFor(mediaId: String?) {
        val gainMb = loudnessGainMb(mediaId)
        val lufs = mediaId?.takeIf { it.isNotBlank() }?.let(MochyStreamResolver::loudnessDbFor)
        if (gainMb == null || lufs == null) {
            AudioOutputStatus.publishLoudness(gainDb = null, lufs = null)
        } else {
            AudioOutputStatus.publishLoudness(gainDb = gainMb / 100f, lufs = lufs.toFloat())
        }
    }

    // ---- sleep timer (Tahap 6; Mochy semantics in batch 53) -------------------
    // The Modern sleep-timer sheet sets the shared SleepTimer; this is
    // the watcher that makes it fire on the Mochy engine. Mochy's own
    // SleepTimer simply pauses when the time runs out (its scheduleJob
    // delays, pauses, clears) — no volume fade; the fade is Classic's
    // SleepFade and stays Classic's. "After this song" is handled in
    // the player listener's transition callback, as in Mochy.

    private fun watchSleepTimer() {
        scope.launch {
            SleepTimer.deadline.collectLatest { deadline ->
                if (deadline == null) return@collectLatest
                runTimedSleep(deadline)
            }
        }
    }

    private suspend fun runTimedSleep(deadline: Long) {
        while (true) {
            val remaining = deadline - SystemClock.elapsedRealtime()
            if (remaining <= 0) break
            delay(minOf(remaining, 1_000L))
        }
        player?.pause()
        SleepTimer.cancel()
        Timber.tag(TAG).d("sleep timer: paused")
    }

    // ---- transport fades (Tahap 6) ------------------------------------------------
    // Mochy's TransportTransitions, fed by the Modern transport-fade
    // settings (the same keys the settings screen writes) and hosted on
    // this service's single player. The automatic two-player crossmix is
    // not ported (see the header), so blendSkip/blendJump stay false and
    // skips fade on this player, which is Mochy's own fallback path.

    // the scrobble timer's thresholds, kept in step with the settings store
    private fun observeScrobblePrefs() {
        scope.launch {
            applicationContext.dataStore.data
                .map { prefs ->
                    Triple(
                        prefs[ScrobbleDelayPercentKey] ?: ScrobbleTimer.DEFAULT_PLAYED_FRACTION,
                        prefs[ScrobbleMinSongDurationKey] ?: ScrobbleTimer.DEFAULT_MIN_SONG_SECONDS,
                        prefs[ScrobbleDelaySecondsKey] ?: ScrobbleTimer.DEFAULT_MAX_WAIT_SECONDS,
                    )
                }
                .distinctUntilChanged()
                .collect { (playedFraction, minSongSeconds, maxWaitSeconds) ->
                    scrobbleTimer.playedFraction = playedFraction
                    scrobbleTimer.minSongSeconds = minSongSeconds
                    scrobbleTimer.maxWaitSeconds = maxWaitSeconds
                }
        }
    }

    private fun observeTransportPrefs() {
        scope.launch {
            applicationContext.dataStore.data
                .map { prefs ->
                    Triple(
                        prefs[TransportFadeEnabledKey] ?: true,
                        prefs[TransportFadeSecondsKey] ?: TransportFadeDefaultSeconds,
                        prefs[TransportEffectKey],
                    )
                }
                .distinctUntilChanged()
                .collect { (enabled, seconds, effectRaw) ->
                    transport.enabled = enabled
                    transport.lengthMs = seconds.coerceIn(TransportFadeMinSeconds, TransportFadeMaxSeconds) * 1_000f
                    transport.effect = runCatching { TransportEffect.valueOf(effectRaw ?: TransportEffect.OFF.name) }
                        .getOrDefault(TransportEffect.OFF)
                }
        }
    }

    private inner class TransportHost : TransportTransitions.Host {
        override val player: ExoPlayer get() = requireNotNull(this@MochyMusicService.player)

        override fun processorFor(player: ExoPlayer): CrossmixTransitionAudioProcessor = crossmixProcessor

        override fun targetVolume(): Float = userVolume

        // Not while casting or into an untouched DAC, which drive the
        // volume themselves (Mochy's own rule).
        override fun canFade(): Boolean =
            !speakers.active && !BitPerfectOutput.status.value.active

        // No crossmix analysis on this engine: effects repeat at the
        // default tempo, as Mochy does for an unanalysed track.
        override fun beatMsFor(mediaId: String?): Double? = null

        // Finds a picked song's stream while the old one dips, so it
        // starts the moment the old one is gone.
        override suspend fun getReady(mediaId: String) {
            runCatching { MochyStreamResolver.resolve(mediaId, currentAudioQuality(), isMetered()) }
        }
    }

    // ---- session / library ----------------------------------------------------

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Mochy's rule (batch 53): swiping the app away only stops
        // playback when StopPlaybackOnTaskRemovedKey says so (off by
        // default) — the service otherwise keeps its session.
        val stopOnRemoved = runCatching {
            PreferencesCache.current(applicationContext.dataStore)[StopPlaybackOnTaskRemovedKey] ?: false
        }.getOrDefault(false)
        if (stopOnRemoved) {
            player?.stop()
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        runCatching {
            getSystemService(AudioManager::class.java)
                ?.unregisterAudioDeviceCallback(audioDeviceCallback)
        }
        clearStreamFailure()
        saveQueueToDisk()
        partySync?.stop()
        partySync = null
        runCatching { transport.cancel() }
        flushPlayRecord()
        ListeningRecorder.onStopped()
        runCatching {
            getSystemService(ConnectivityManager::class.java)
                ?.unregisterNetworkCallback(discordNetworkCallback)
        }
        runCatching { scrobbleTimer.onSongStop() }
        runCatching { discordPresence.update(null) }
        handler.removeCallbacksAndMessages(null)
        CastController.detach(castPlayback)
        castPlayback.release()
        runCatching { equalizer?.release() }
        equalizer = null
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        player = null
        // The caches are process singletons (MochyCaches), shared with
        // the download engine and the share: nothing to release here.
        MediaWidgetSnapshot.save(this, MediaWidgetSnapshot.EMPTY)
        MediaWidget.refresh(this)
        scope.cancel()
        super.onDestroy()
    }

    private inner class LibraryCallback : MediaLibrarySession.Callback {

        private val favoriteCommand = SessionCommand(ACTION_TOGGLE_FAVORITE, Bundle.EMPTY)
        private val clearBoundedContextCommand = SessionCommand(ACTION_CLEAR_BOUNDED_CONTEXT, Bundle.EMPTY)
        private val autoplayCommand = SessionCommand(ACTION_TOGGLE_AUTOPLAY, Bundle.EMPTY)
        private val shuffleCommand = SessionCommand(ACTION_TOGGLE_SHUFFLE, Bundle.EMPTY)
        private val queueDragCommand = SessionCommand(ACTION_QUEUE_DRAG, Bundle.EMPTY)

        private val searchCache = mutableMapOf<String, List<MediaItem>>()

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS
                .buildUpon()
                .add(favoriteCommand)
                .add(clearBoundedContextCommand)
                .add(autoplayCommand)
                .add(shuffleCommand)
                .add(queueDragCommand)
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailablePlayerCommands(MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS)
                .setAvailableSessionCommands(sessionCommands)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                // The Mochy engine holds no bounded contexts: its queue is
                // the plain player queue, so clearing one is already done.
                ACTION_CLEAR_BOUNDED_CONTEXT -> Unit
                ACTION_QUEUE_DRAG -> {
                    if (args.getBoolean(EXTRA_QUEUE_DRAG_ACTIVE, false)) {
                        partySync?.beginQueueDrag()
                    } else {
                        partySync?.endQueueDrag()
                    }
                }
                ACTION_TOGGLE_SHUFFLE -> {
                    player?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled }
                    refreshCustomLayout()
                }
                ACTION_TOGGLE_AUTOPLAY -> {
                    val enabled = !infiniteQueueEnabled()
                    setInfiniteQueueEnabled(enabled)
                    if (enabled && player?.playbackState == Player.STATE_ENDED) {
                        maybeAutoplayRefill()
                    }
                }
                ACTION_TOGGLE_FAVORITE -> session.player.currentMediaItem
                    ?.takeIf(::canLike)
                    ?.let { item ->
                        val song = item.toSong()
                        if (song.localUri != null) {
                            OfflinePlaylists.toggleLike(song)
                            publishWidgetState()
                            refreshCustomLayout()
                        } else {
                            toggleFavorite(item.mediaId)
                        }
                    }
                else -> return Futures.immediateFuture(
                    SessionResult(SessionError.ERROR_NOT_SUPPORTED),
                )
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: MediaLibraryService.LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val rootItem = MediaItem.Builder()
                .setMediaId(MEDIA_ROOT_ID)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(getString(R.string.app_name))
                        .setIsBrowsable(true)
                        .setIsPlayable(false)
                        .setFolderType(MediaMetadata.FOLDER_TYPE_MIXED)
                        .build(),
                )
                .build()
            return Futures.immediateFuture(LibraryResult.ofItem(rootItem, params))
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: MediaLibraryService.LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = scope.future(Dispatchers.IO) {
            val db = MochyDatabaseHolder.get(this@MochyMusicService)
            val children: List<MediaItem> = when {
                parentId == MEDIA_ROOT_ID -> listOf(
                    folderItem(MEDIA_LIKED_ID, getString(R.string.auto_liked)),
                    folderItem(MEDIA_PLAYLISTS_ID, getString(R.string.playlists)),
                    folderItem(MEDIA_SONGS_ID, getString(R.string.songs)),
                )
                parentId == MEDIA_LIKED_ID ->
                    db.likedSongs().firstOrNull().orEmpty().map { it.toPlayableItem() }
                parentId == MEDIA_SONGS_ID ->
                    db.songs().firstOrNull().orEmpty().map { it.toPlayableItem() }
                parentId == MEDIA_PLAYLISTS_ID ->
                    db.playlists().firstOrNull().orEmpty().map { playlist ->
                        MediaItem.Builder()
                            .setMediaId(MEDIA_PLAYLIST_PREFIX + playlist.id)
                            .setMediaMetadata(
                                MediaMetadata.Builder()
                                    .setTitle(playlist.title)
                                    .setArtworkUri(playlist.thumbnailUrl?.toUri())
                                    .setIsBrowsable(true)
                                    .setIsPlayable(false)
                                    .setFolderType(MediaMetadata.FOLDER_TYPE_PLAYLISTS)
                                    .build(),
                            )
                            .build()
                    }
                parentId.startsWith(MEDIA_PLAYLIST_PREFIX) ->
                    db.playlistSongs(parentId.removePrefix(MEDIA_PLAYLIST_PREFIX))
                        .firstOrNull().orEmpty().map { it.song.toPlayableItem() }
                else -> emptyList()
            }
            LibraryResult.ofItemList(ImmutableList.copyOf(children), params)
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String,
        ): ListenableFuture<LibraryResult<MediaItem>> = scope.future(Dispatchers.IO) {
            val db = MochyDatabaseHolder.get(this@MochyMusicService)
            val song = db.getSongById(mediaId)
            val item = if (song != null) {
                Song(
                    videoId = song.song.id,
                    title = song.song.title,
                    artist = song.artists.joinToString(", ") { it.name },
                    thumbnailUrl = song.song.thumbnailUrl,
                    albumName = song.song.albumName,
                ).toMediaItem()
            } else {
                Song(videoId = mediaId, title = mediaId, artist = "", thumbnailUrl = null).toMediaItem()
            }
            LibraryResult.ofItem(item, null)
        }

        override fun onSearch(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            params: MediaLibraryService.LibraryParams?,
        ): ListenableFuture<LibraryResult<Void>> = scope.future(Dispatchers.IO) {
            val result = YouTube.search(query, YouTube.SearchFilter.FILTER_SONG).getOrNull()
            val items = result?.items.orEmpty().filterIsInstance<com.music.innertube.models.SongItem>().map { songItem ->
                Song(
                    videoId = songItem.id,
                    title = songItem.title,
                    artist = songItem.artists.joinToString(", ") { it.name },
                    thumbnailUrl = songItem.thumbnail,
                    albumName = songItem.album?.name,
                ).toMediaItem()
            }
            searchCache[query] = items
            session.notifySearchResultChanged(browser, query, items.size, params)
            LibraryResult.ofVoid()
        }

        override fun onGetSearchResult(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            page: Int,
            pageSize: Int,
            params: MediaLibraryService.LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val items = searchCache[query].orEmpty()
            return Futures.immediateFuture(
                LibraryResult.ofItemList(ImmutableList.copyOf(items), params),
            )
        }

        override fun onAddMediaItems(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>,
        ): ListenableFuture<List<MediaItem>> = scope.future(Dispatchers.IO) {
            mediaItems.map { item ->
                if (item.localConfiguration != null) {
                    item
                } else {
                    // A browse/search item without a URI: rebuild it as a
                    // playable watch item for its media id.
                    Song(
                        videoId = item.mediaId,
                        title = item.mediaMetadata.title?.toString().orEmpty(),
                        artist = item.mediaMetadata.artist?.toString().orEmpty(),
                        thumbnailUrl = item.mediaMetadata.artworkUri?.toString(),
                    ).toMediaItem()
                }
            }
        }

        private fun folderItem(id: String, title: String): MediaItem = MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .setFolderType(MediaMetadata.FOLDER_TYPE_MIXED)
                    .build(),
            )
            .build()

        private fun com.mochyy.mochymusic.db.entities.Song.toPlayableItem(): MediaItem = Song(
            videoId = song.id,
            title = song.title,
            artist = artists.joinToString(", ") { it.name },
            thumbnailUrl = song.thumbnailUrl,
            albumName = song.albumName,
        ).toMediaItem()
    }

    companion object {
        // The loudness gain bounds, Classic's (PlaybackService): -15 dB .. +3 dB.
        const val MIN_LOUDNESS_GAIN_MB = -1500
        const val MAX_LOUDNESS_GAIN_MB = 300

        // Silence skipping's minimum, Classic's (PlaybackService).
        const val MIN_SILENCE_US = 1_000_000L

        private const val TAG = "MochyMusicService"
        private const val SESSION_ID = "chordwave_mochy"
        private const val CHANNEL_ID = "chordwave_mochy_playback"
        private const val RESOLVE_TIMEOUT_MS = 30_000L
        private const val BACK_RESTARTS_AFTER_MS = 3_000L
        private const val SAMPLE_INTERVAL_MS = 1_000L
        private const val STREAM_RETRY_INTERVAL_MS = 5_000L
        private const val PERSISTENT_QUEUE_FILE = "mochy_persist_queue.json"
        private const val POSITION_SAVE_STEP_MS = 5_000L
        private const val MAX_SAMPLE_STEP_MS = 5_000L
        private const val PLAY_FLOOR_MS = 30_000L
        private const val PLAY_CEILING_MS = 240_000L

        private const val MEDIA_ROOT_ID = "mochy_root"
        private const val MEDIA_LIKED_ID = "mochy_liked"
        private const val MEDIA_PLAYLISTS_ID = "mochy_playlists"
        private const val MEDIA_SONGS_ID = "mochy_songs"
        private const val MEDIA_PLAYLIST_PREFIX = "mochy_playlist:"

        private val AUDIO_ATTRIBUTES = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
    }
}

/**
 * The session's player: the ExoPlayer, with a Cast receiver's clock
 * reported in its place while the receiver is the speaker — the same
 * contract the Classic engine's SessionPlayer keeps, minus the parts that
 * belong to that engine alone (crossfade, party, DAC).
 */
@OptIn(UnstableApi::class)
/**
 * The session player: the door every user action passes through (the app,
 * the notification, a headset button, Android Auto), while the service's
 * own moves — and PartySync's corrections — go straight to the ExoPlayer
 * underneath. That asymmetry is how the party tells a listener's action
 * from its own echo (see PartySync), and it is where Mochy's transport
 * fades hook in: play, pause and skips here get the same fades as the
 * app's buttons, exactly as Mochy's TransportFadingPlayer does it.
 */
private class MochySessionPlayer(
    player: Player,
    private val cast: RemoteSpeaker,
    private val transport: TransportTransitions,
    private val partySync: PartySync?,
    private val onUserVolume: (Float) -> Unit,
    private val persistentShuffle: () -> Boolean = { true },
) : ForwardingPlayer(player), CastSink {

    private fun locked(): Boolean = ListenTogether.state.value.controlsLocked

    private val listeners = java.util.concurrent.CopyOnWriteArraySet<Player.Listener>()
    private var reportedPlayWhenReady = false
    private var reportedPlaybackState = Player.STATE_IDLE
    private var reportedIsPlaying = false

    init {
        cast.sink = this
    }

    override fun addListener(listener: Player.Listener) {
        listeners += listener
        super.addListener(listener)
    }

    override fun removeListener(listener: Player.Listener) {
        listeners -= listener
        super.removeListener(listener)
    }

    override fun getPlayWhenReady() = if (cast.active) cast.playWhenReady else super.getPlayWhenReady()
    override fun isPlaying() = if (cast.active) cast.isPlaying else super.isPlaying()
    override fun getPlaybackState() = if (cast.active) cast.playbackState else super.getPlaybackState()
    override fun getCurrentPosition() = if (cast.active) cast.positionMs else super.getCurrentPosition()
    override fun getContentPosition() = if (cast.active) cast.positionMs else super.getContentPosition()
    override fun getBufferedPosition() = if (cast.active) cast.positionMs else super.getBufferedPosition()
    override fun getContentBufferedPosition() = if (cast.active) cast.positionMs else super.getContentBufferedPosition()
    override fun getDuration(): Long =
        if (cast.active && cast.durationMs != C.TIME_UNSET) cast.durationMs else super.getDuration()
    override fun getContentDuration(): Long =
        if (cast.active && cast.durationMs != C.TIME_UNSET) cast.durationMs else super.getContentDuration()

    override fun getDeviceInfo(): DeviceInfo =
        if (cast.active) CAST_DEVICE else super.getDeviceInfo()
    override fun getDeviceVolume(): Int =
        if (cast.active) cast.deviceVolume else super.getDeviceVolume()
    override fun isDeviceMuted(): Boolean = if (cast.active) false else super.isDeviceMuted()
    override fun setDeviceVolume(volume: Int, flags: Int) {
        if (cast.active) cast.setVolume(volume / 100f) else super.setDeviceVolume(volume, flags)
    }

    @Suppress("DEPRECATION")
    override fun setDeviceVolume(volume: Int) {
        if (cast.active) cast.setVolume(volume / 100f) else super.setDeviceVolume(volume)
    }

    override fun play() {
        if (cast.active) {
            cast.play()
            return
        }
        // Locked, this plays only here — the party carries on untouched
        // and this device rejoins it wherever it has got to.
        if (partySync?.onLockedTransport(true) == true) return
        partySync?.onLocalIntent()
        // Held back only when a party will schedule the start for
        // everyone at once — see PartySync.shouldDeferPlay.
        if (partySync?.shouldDeferPlay() == true) return
        transport.play()
    }

    override fun pause() {
        if (cast.active) {
            cast.pause()
            return
        }
        if (partySync?.onLockedTransport(false) == true) return
        partySync?.onLocalIntent()
        transport.pause()
    }

    override fun setPlayWhenReady(playWhenReady: Boolean) {
        if (cast.active) {
            if (playWhenReady) cast.play() else cast.pause()
            return
        }
        if (partySync?.onLockedTransport(playWhenReady) == true) return
        partySync?.onLocalIntent()
        if (playWhenReady && partySync?.shouldDeferPlay() == true) return
        if (playWhenReady) transport.play() else transport.pause()
    }

    override fun seekToNext() {
        if (locked()) return
        partySync?.onLocalIntent()
        transport.skipNext()
    }

    override fun seekToNextMediaItem() {
        if (locked()) return
        partySync?.onLocalIntent()
        transport.skipNext()
    }

    override fun seekToPrevious() {
        if (locked()) return
        partySync?.onLocalIntent()
        transport.skipPrevious()
    }

    override fun seekToPreviousMediaItem() {
        if (locked()) return
        partySync?.onLocalIntent()
        transport.skipPrevious()
    }

    override fun seekTo(positionMs: Long) {
        if (cast.active) {
            cast.seekTo(positionMs)
            return
        }
        if (locked()) return
        partySync?.onLocalIntent()
        transport.cancel()
        super.seekTo(positionMs)
    }

    override fun seekTo(mediaItemIndex: Int, positionMs: Long) {
        if (cast.active && mediaItemIndex == currentMediaItemIndex) {
            cast.seekTo(positionMs)
            return
        }
        if (locked()) return
        partySync?.onLocalIntent()
        // A song picked from the queue fades in like one picked in the
        // app; a seek within the queue at a position is a plain seek.
        if (mediaItemIndex != currentMediaItemIndex && positionMs == 0L) {
            transport.pickFromQueue { mediaItemIndex }
        } else {
            transport.cancel()
            super.seekTo(mediaItemIndex, positionMs)
        }
    }

    override fun seekToDefaultPosition(mediaItemIndex: Int) {
        if (locked()) return
        partySync?.onLocalIntent()
        if (mediaItemIndex == currentMediaItemIndex) {
            transport.cancel()
            super.seekToDefaultPosition(mediaItemIndex)
        } else {
            transport.pickFromQueue { mediaItemIndex }
        }
    }

    // Not blocked when locked: stopping is this device going quiet, which
    // is the listener's own business.
    override fun stop() {
        transport.cancel()
        if (!locked()) partySync?.onLocalIntent()
        super.stop()
    }

    override fun setVolume(volume: Float) {
        // The listener's level is the fades' target; while a fade runs it
        // owns the player's volume and picks the new level up as its base.
        onUserVolume(volume)
        if (!transport.isFading) super.setVolume(volume)
    }

    // Mochy's playQueue rule: a new queue keeps shuffling only when
    // PersistentShuffleAcrossQueuesKey allows it (on by default).
    private fun applyPersistentShuffle() {
        if (!persistentShuffle()) {
            runCatching { shuffleModeEnabled = false }
        }
    }

    override fun setMediaItems(mediaItems: List<MediaItem>) {
        transport.cancel()
        super.setMediaItems(mediaItems)
        applyPersistentShuffle()
        partySync?.onLocalIntent()
    }

    override fun setMediaItems(mediaItems: List<MediaItem>, resetPosition: Boolean) {
        transport.cancel()
        super.setMediaItems(mediaItems, resetPosition)
        applyPersistentShuffle()
        partySync?.onLocalIntent()
    }

    override fun setMediaItems(mediaItems: List<MediaItem>, startIndex: Int, startPositionMs: Long) {
        transport.cancel()
        super.setMediaItems(mediaItems, startIndex, startPositionMs)
        applyPersistentShuffle()
        partySync?.onLocalIntent()
    }

    override fun addMediaItems(mediaItems: List<MediaItem>) {
        super.addMediaItems(mediaItems)
        partySync?.onLocalIntent()
    }

    override fun addMediaItems(index: Int, mediaItems: List<MediaItem>) {
        super.addMediaItems(index, mediaItems)
        partySync?.onLocalIntent()
    }

    override fun moveMediaItem(currentIndex: Int, newIndex: Int) {
        super.moveMediaItem(currentIndex, newIndex)
        partySync?.onLocalIntent()
    }

    override fun removeMediaItem(index: Int) {
        super.removeMediaItem(index)
        partySync?.onLocalIntent()
    }

    override fun onRemotePlaybackChanged() {
        val playWhenReady = playWhenReady
        val state = playbackState
        val playing = isPlaying
        val events = Player.Events(
            androidx.media3.common.FlagSet.Builder()
                .apply {
                    if (playWhenReady != reportedPlayWhenReady) add(Player.EVENT_PLAY_WHEN_READY_CHANGED)
                    if (state != reportedPlaybackState) add(Player.EVENT_PLAYBACK_STATE_CHANGED)
                    if (playing != reportedIsPlaying) add(Player.EVENT_IS_PLAYING_CHANGED)
                }
                .build(),
        )
        reportedPlayWhenReady = playWhenReady
        reportedPlaybackState = state
        reportedIsPlaying = playing
        listeners.forEach { listener ->
            if (events.contains(Player.EVENT_PLAY_WHEN_READY_CHANGED)) {
                listener.onPlayWhenReadyChanged(playWhenReady, Player.PLAY_WHEN_READY_CHANGE_REASON_REMOTE)
            }
            if (events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED)) {
                listener.onPlaybackStateChanged(state)
            }
            if (events.contains(Player.EVENT_IS_PLAYING_CHANGED)) {
                listener.onIsPlayingChanged(playing)
            }
            listener.onEvents(this, events)
        }
    }

    override fun onRemoteRouteChanged() {
        val info = deviceInfo
        listeners.forEach { it.onDeviceInfoChanged(info) }
        onRemotePlaybackChanged()
    }

    override fun onRemoteVolumeChanged() {
        val volume = deviceVolume
        listeners.forEach { it.onDeviceVolumeChanged(volume, isDeviceMuted) }
    }

    private companion object {
        val CAST_DEVICE: DeviceInfo = DeviceInfo.Builder(DeviceInfo.PLAYBACK_TYPE_REMOTE)
            .setMinVolume(0)
            .setMaxVolume(100)
            .build()
    }
}
