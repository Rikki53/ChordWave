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
// Sources system), downloads (files in Music/ChordWave, substituted at
// item-build time), Cast (CastPlayback/CastController, shared with the
// Classic engine), the widget snapshot, and the equaliser settings (applied
// through the platform Equalizer on this player's audio session).
//
// Not yet ported from Mochy's MusicService (later stages, honest list):
// crossfade/crossmix transitions, PoToken/SABR, loudness normalisation,
// sleep timer internals, Listen Together party sync, USB-DAC bit-perfect
// output. Classic keeps all of those; Modern gains them stage by stage.
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
import android.media.audiofx.Equalizer
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
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.exoplayer.ExoPlayer
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
import com.music.bitchord.data.model.LikeStatus
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.mochydata.MochyDatabaseHolder
import com.music.bitchord.data.sources.SourceResolver
import com.music.bitchord.data.stats.ListeningRecorder
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.download.Downloads
import com.music.bitchord.playback.ACTION_CLEAR_BOUNDED_CONTEXT
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
import com.music.bitchord.ui.mochy.constants.AudioQuality
import com.music.bitchord.ui.mochy.constants.AudioQualityKey
import com.music.bitchord.ui.mochy.utils.PreferencesCache
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
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import timber.log.Timber
import java.io.File
import java.time.LocalDateTime
import kotlin.math.min
import kotlin.math.roundToInt

@OptIn(UnstableApi::class)
class MochyMusicService : MediaLibraryService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val handler = Handler(Looper.getMainLooper())

    private var player: ExoPlayer? = null
    private var mediaSession: MediaLibrarySession? = null
    private var playerCache: SimpleCache? = null

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

        val exoPlayer = buildPlayer()
        player = exoPlayer
        exoPlayer.addListener(playerListener)
        applySettings(exoPlayer)
        applyEqualizer(exoPlayer)

        mediaSession = MediaLibrarySession.Builder(
            this,
            MochySessionPlayer(exoPlayer, speakers),
            LibraryCallback(),
        )
            .setId(SESSION_ID)
            .setSessionActivity(sessionActivity())
            .setSessionExtras(transportSlotReservations())
            .build()
        refreshCustomLayout()

        CastController.ensureStarted(this)
        CastController.attach(castPlayback)
        startSampler()
        Timber.tag(TAG).i("Mochy engine service created")
    }

    private fun buildPlayer(): ExoPlayer {
        val cache = SimpleCache(
            File(cacheDir, "mochy_player_cache"),
            LeastRecentlyUsedCacheEvictor(PLAYER_CACHE_BYTES),
            StandaloneDatabaseProvider(this),
        )
        playerCache = cache

        val resolvingFactory = ResolvingDataSource.Factory(
            MochyDataSourceFactory(this, cache),
        ) { dataSpec -> resolveDataSpec(dataSpec) }

        return ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(resolvingFactory))
            .setAudioAttributes(AUDIO_ATTRIBUTES, /* handleAudioFocus = */ true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .setMaxSeekToPreviousPositionMs(BACK_RESTARTS_AFTER_MS)
            .build()
    }

    private fun applySettings(exoPlayer: ExoPlayer) {
        exoPlayer.skipSilenceEnabled = AppSettings.skipSilence.value
        exoPlayer.repeatMode = AppSettings.repeatMode.value
    }

    /**
     * The resolver: bitchord://watch?v=… through the Mochy innertube,
     * bitchord://source through ChordWave's Sources, everything else
     * (files, content, radio streams) as it arrived.
     */
    private fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
        val uri = dataSpec.uri
        if (uri.authority == "source") {
            val stream = runBlocking {
                withTimeout(RESOLVE_TIMEOUT_MS) { SourceResolver.resolve(uri) }
            } ?: throw java.io.IOException("No enabled source could serve ${uri.getQueryParameter("n")}")
            return dataSpec.buildUpon()
                .setUri(Uri.parse(stream.url))
                .setHttpRequestHeaders(stream.headers)
                .build()
        }
        val videoId = uri.getQueryParameter("v") ?: return dataSpec
        val resolved = runBlocking {
            withTimeout(RESOLVE_TIMEOUT_MS) {
                MochyStreamResolver.resolve(videoId, currentAudioQuality(), isMetered())
            }
        }
        recordFormat(videoId, resolved)
        return dataSpec.buildUpon()
            .setUri(resolved.url.toUri())
            .setHttpRequestHeaders(resolved.headers)
            .build()
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

    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            flushPlayRecord()
            sampledMediaId = mediaItem?.mediaId
            sampledSong = mediaItem?.toSong()
            playedThisTrackMs = 0L
            countedMs = 0L
            lastSampleAt = System.currentTimeMillis()
            playCountedForTrack = false
            speakers.onLocalTransition(mediaItem, reason)
            publishWidgetState()
            refreshCustomLayout()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            publishWidgetState()
            if (playbackState == Player.STATE_ENDED) {
                flushPlayRecord()
                ListeningRecorder.onStopped()
                maybeAutoplayRefill()
            }
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            publishWidgetState()
            if (!playWhenReady) ListeningRecorder.onStopped()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            publishWidgetState()
            if (!isPlaying) ListeningRecorder.onStopped()
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            AppSettings.setRepeatMode(repeatMode)
            speakers.onLocalRepeatChanged(repeatMode)
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            publishWidgetState()
        }

        override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
            speakers.onLocalQueueChanged()
            publishWidgetState()
        }

        override fun onPlayerError(error: PlaybackException) {
            Timber.tag(TAG).w(error, "Playback error")
            ListeningRecorder.onStopped()
        }
    }

    /**
     * Infinite queue: when the queue runs out and autoplay is on, the
     * current song's radio follows — the same refill the Classic engine
     * does, so Modern's infinite-queue button keeps its meaning.
     */
    private fun maybeAutoplayRefill() {
        val exoPlayer = player ?: return
        if (!AppSettings.autoplay.value) return
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

    // ---- session / library ----------------------------------------------------

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        val exoPlayer = player
        if (exoPlayer == null || !exoPlayer.playWhenReady) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        flushPlayRecord()
        ListeningRecorder.onStopped()
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
        runCatching { playerCache?.release() }
        playerCache = null
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
                ACTION_TOGGLE_SHUFFLE -> {
                    player?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled }
                    refreshCustomLayout()
                }
                ACTION_TOGGLE_AUTOPLAY -> {
                    AppSettings.setAutoplay(!AppSettings.autoplay.value)
                    if (AppSettings.autoplay.value &&
                        player?.playbackState == Player.STATE_ENDED
                    ) {
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
        private const val TAG = "MochyMusicService"
        private const val SESSION_ID = "chordwave_mochy"
        private const val CHANNEL_ID = "chordwave_mochy_playback"
        private const val PLAYER_CACHE_BYTES = 512L * 1024 * 1024
        private const val RESOLVE_TIMEOUT_MS = 30_000L
        private const val BACK_RESTARTS_AFTER_MS = 3_000L
        private const val SAMPLE_INTERVAL_MS = 1_000L
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
private class MochySessionPlayer(
    player: Player,
    private val cast: RemoteSpeaker,
) : ForwardingPlayer(player), CastSink {

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
        if (cast.active) cast.play() else super.play()
    }

    override fun pause() {
        if (cast.active) cast.pause() else super.pause()
    }

    override fun seekTo(positionMs: Long) {
        if (cast.active) cast.seekTo(positionMs) else super.seekTo(positionMs)
    }

    override fun seekTo(mediaItemIndex: Int, positionMs: Long) {
        if (cast.active && mediaItemIndex == currentMediaItemIndex) {
            cast.seekTo(positionMs)
        } else {
            super.seekTo(mediaItemIndex, positionMs)
        }
    }

    override fun onRemotePlaybackChanged() {
        val playWhenReady = playWhenReady
        val state = playbackState
        val playing = isPlaying
        val events = Player.Events(
            androidx.media3.common.util.FlagSet.Builder()
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

/**
 * Cache for resolved streams, direct reads for everything already on the
 * device or already a stream: files and content URIs bypass the cache (a
 * second copy of a download helps nobody), and live radio streams are not
 * cached at all. Everything else — the resolved googlevideo URLs — goes
 * through the player cache keyed by media id, as in Mochy.
 */
@OptIn(UnstableApi::class)
private class MochyDataSourceFactory(
    context: Context,
    cache: SimpleCache,
) : DataSource.Factory {

    private val defaultFactory = DefaultDataSource.Factory(context)
    private val cacheFactory = CacheDataSource.Factory()
        .setCache(cache)
        .setUpstreamDataSourceFactory(defaultFactory)
        .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

    override fun createDataSource(): DataSource = object : DataSource {
        private var delegate: DataSource? = null

        override fun addTransferListener(transferListener: TransferListener) {
            delegate?.addTransferListener(transferListener)
        }

        override fun open(dataSpec: DataSpec): Long {
            val scheme = dataSpec.uri.scheme
            val direct = scheme == "file" || scheme == "content" ||
                dataSpec.key == null || dataSpec.key!!.startsWith("http")
            val chosen = if (direct) defaultFactory.createDataSource() else cacheFactory.createDataSource()
            delegate = chosen
            return chosen.open(dataSpec)
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
            delegate?.read(buffer, offset, length) ?: C.RESULT_END_OF_INPUT

        override fun getUri(): Uri? = delegate?.uri

        override fun getResponseHeaders(): Map<String, List<String>> =
            delegate?.responseHeaders ?: emptyMap()

        override fun close() {
            delegate?.close()
            delegate = null
        }
    }
}
