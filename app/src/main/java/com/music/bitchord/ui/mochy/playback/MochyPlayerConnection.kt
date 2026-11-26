// MochyPlayerConnection — coordinator-written (14 Nov 2026), Phase 1.5 skeleton.
// NOT a port of Mochy's playback/PlayerConnection.kt (841 lines, bound to
// Mochy's MusicService binder, Room database, remote control and crossmix —
// none of which exist in ChordWave). Instead: the same UI-facing shape Mochy's
// screens read (StateFlows + methods), backed by ChordWave's MediaController
// (SessionToken to PlaybackService) and CW's PlayerState machinery.
//
// REAL in this skeleton: transport state flows (isPlaying, playbackState,
// repeat/shuffle, canSkip), current track (mediaMetadata/currentSong via CW
// MediaItem.toSong() + the ui.mochy.models mappers), queue flows
// (queueWindows/queueItems/queueSnapshot), progress ticker, mute via volume,
// and the simple transport methods (play/pause/toggle/seek/next/previous).
// The Phase 3 adapter round wired the rest (see below): queue methods,
// like/library, radio, infinite queue, lyrics, stream facts.
// SKIPPED (no CW equivalent, out of scope): MusicService-bound flows
// (waitingForNetworkConnection, streamFailure — the flow exists as a
// permanent-null stub so StreamFailureUi compiles), cast, remote
// control, SongEdits.
//
// Phase 3 adapter round (15 Nov 2026, staging/phase3-adapter-connection):
// adds the player-connection members the ported Phase 3 player UI reads —
// player, playbackParameters, uiState, streamFailure, lyricsOffsetMs,
// isCrossmixing, studioRequests/requestStudio, streamFacts, musicVolume/
// setMusicVolume. See NEEDS.md in that staging dir for the contract of
// each member (what is real, what is a stub, and why).
//
// Phase 3 adapter round, lyrics (15 Nov 2026, staging/phase3-adapter-lyrics):
// adds currentLyrics (the current song's lyrics row, serialised from
// MainViewModel.lyrics), the lyrics loading trigger (attachLyrics + the
// mediaMetadata watcher — Modern mode never runs the Classic UI's
// loadLyrics effect, so the connection has to trigger the load itself),
// and saveLyricsOffset. The Nov 2026 lyrics merge made the offset
// per-song (AppSettings.lyricsOffsetsBySong, falling back to the global
// value) and added the provider bridge (LyricsProviderBridge) behind the
// provider picker + editor. See NEEDS.md in that staging dir for the
// contracts, the three-state null/LYRICS_NOT_FOUND semantics, and the
// host wiring the coordinator must add in MochyApp.
//
// Phase 3 adapter round, queue/like (15 Nov 2026, coordinator, same file):
// wires the queue methods onto the controller (playQueue/playNext/
// addToQueue/playQueueItem/moveQueueItem/playQueueItemNext/
// removeQueueItems), the like/library bridge (toggleLike/toggleLibrary
// via MenuActions' toggleSongLike/sendLibraryFeedback + a LikeState
// overlay on the published metadata), startRadioSeamlessly (append
// YtMusicRepository.radio results), and setInfiniteQueueEnabled (Mochy
// pref + CW autoplay together). stopRecommendingCurrent stays a no-op:
// CW has no recommendation engine to stop.
//
// The Modern host (MochyApp) creates one instance, binds it to
// rememberMediaController() inside a DisposableEffect, provides it via
// LocalPlayerConnection, and calls dispose() when the host leaves.
//
// Two-engine Tahap 4 (26 Nov 2026): rememberMediaController() now binds
// the active engine's service, so in Modern this connection talks directly
// to the Mochy engine (MochyMusicService — Mochy innertube resolution,
// Mochy player cache, Room playback records) instead of adapting the
// ChordWave engine. The UI-facing shape is unchanged; the queue methods
// below keep their batch-48 behaviour (a tapped song is set and played at
// once, the radio tail follows) on the new engine.
//
// Round 1 fix (15 Nov 2026): playQueue used to return silently when a
// queue resolved to nothing (YouTubeQueue was still a stub, so every Home
// song tap did exactly that), and the Modern item builders put bare video
// ids in as URIs, which the service resolver cannot answer. playQueue now
// shows the tapped song + a loading state at once, starts with Classic's
// sequence (clear bounded context, set, prepare, play), and reports a
// failed start on playFailure instead of dropping it; the builders (see
// extensions/MediaItemExt) delegate to CW's own toMediaItem(). Engine
// errors still travel on `error` — the host debounces and shows those.
package com.music.bitchord.ui.mochy.playback

import android.content.Context
import android.os.Bundle
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import com.music.bitchord.R
import com.music.bitchord.data.LikeState
import com.music.bitchord.data.OfflinePlaylists
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.lyrics.LyricLine
import com.music.bitchord.data.lyrics.LyricsRepository
import com.music.bitchord.data.lyrics.LyricsSource
import com.music.bitchord.ui.LyricsProviderState
import com.music.bitchord.data.model.Song as CwSong
import com.music.bitchord.data.model.LikeStatus
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.playback.ACTION_CLEAR_BOUNDED_CONTEXT
import com.music.bitchord.playback.fromAutoplay
import com.music.bitchord.playback.toSong
import com.music.bitchord.ui.mochy.constants.InfiniteQueueKey
import com.music.bitchord.ui.mochy.di.PlayerIntegrationsEntryPoint
import dagger.hilt.android.EntryPointAccessors
import com.music.bitchord.ui.mochy.extensions.toMediaItem
import com.music.bitchord.ui.mochy.link.LinkPlayer
import com.music.bitchord.ui.mochy.lyrics.LyricsEntity
import com.music.bitchord.ui.mochy.lyrics.LyricsSourceState
import com.music.bitchord.ui.mochy.lyrics.LyricsUtils
import com.music.bitchord.ui.mochy.lyrics.ProviderCandidate
import com.music.bitchord.ui.mochy.lyrics.mapLyricsToEntity
import com.music.bitchord.ui.mochy.lyrics.serializeLyricLines
import com.music.bitchord.ui.mochy.lyrics.toMochyProviderName
import com.music.bitchord.ui.mochy.models.MediaMetadata
import com.music.bitchord.ui.mochy.models.Song
import com.music.bitchord.ui.mochy.models.toMochyMetadata
import com.music.bitchord.ui.mochy.models.toMochySong
import com.music.bitchord.ui.mochy.playback.queues.Queue
import com.music.bitchord.ui.mochy.playback.queues.YouTubeQueue
import com.music.bitchord.ui.mochy.player.PlayerUiState
import com.music.bitchord.ui.mochy.player.menu.sendLibraryFeedback
import com.music.bitchord.ui.mochy.player.menu.toggleSongLike
import com.music.bitchord.ui.mochy.player.models.ProgressState
import com.music.bitchord.ui.mochy.player.models.QueueItemData
import com.music.bitchord.ui.mochy.player.models.QueueState
import com.music.bitchord.ui.mochy.utils.PreferencesCache
import com.music.bitchord.ui.mochy.utils.dataStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.LocalDateTime

val LocalPlayerConnection = staticCompositionLocalOf<MochyPlayerConnection?> {
    error("No MochyPlayerConnection provided")
}

/**
 * A stream that started failing: the player is retrying until [timeoutMs]
 * after [startedAt] (both [android.os.SystemClock.elapsedRealtime]).
 * Mochy shape (playback/MusicService.kt:303). ChordWave has no
 * stream-failure signal, so [MochyPlayerConnection.streamFailure] stays
 * null and the failure UI never shows — see its KDoc.
 */
data class StreamFailure(val startedAt: Long, val timeoutMs: Long)

/**
 * Everything ChordWave's lyrics lookup needs for one song: exactly
 * [com.music.bitchord.ui.MainViewModel.loadLyrics]'s parameters, bundled so
 * the host can attach the loader as one lambda (see
 * [MochyPlayerConnection.attachLyrics]). The connection fills it from the
 * current [MediaMetadata] + the player's duration; the host's lambda just
 * forwards it to the ViewModel.
 */
data class LyricsLoadRequest(
    val videoId: String,
    val title: String,
    val artist: String,
    val durationMs: Long,
    val album: String?,
    val localUri: String?,
)

/**
 * The lyrics provider picker + editor, as the host attaches it. Like
 * [MochyPlayerConnection.attachLyrics]'s loader, this keeps MainViewModel
 * out of the connection: the host (MochyApp) implements it over the
 * activity-scoped ViewModel, and the connection only calls through it.
 */
interface LyricsProviderBridge {
    /** Per-provider lookup state for the current track (see [com.music.bitchord.ui.LyricsProviderState]). */
    val providerStates: StateFlow<Map<LyricsSource, LyricsProviderState>>

    /** Applies [source]'s lyrics for the current track (fetching it first when untouched). */
    fun selectProvider(source: LyricsSource)

    /** The completed result for [source] from the current track's lookup, if it answered. */
    fun providerResult(source: LyricsSource): LyricsRepository.Result?

    /** Saves LRC the listener wrote for [songId] as that song's override. */
    fun saveLyrics(songId: String, lrc: String)

    /** Drops the saved override for [songId], re-running the provider lookup when it is playing. */
    fun clearLyrics(songId: String)

    /** Whether [songId] has a saved override. */
    fun hasLyricsOverride(songId: String): Boolean
}

class MochyPlayerConnection(context: Context) {

    val context: Context = context.applicationContext

    val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // ---- bound controller (set by the host via bind()) ----

    private var controller: MediaController? = null

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = sync(player)
        override fun onPlayerErrorChanged(error: PlaybackException?) {
            this@MochyPlayerConnection.error.value = error
        }
    }

    fun bind(controller: MediaController?) {
        val old = this.controller
        if (old === controller) return
        old?.removeListener(listener)
        this.controller = controller
        controller?.let {
            it.addListener(listener)
            // A fresh controller starts at full volume; restore the stored
            // music volume / mute state so a rebind is inaudible.
            it.volume = if (isMuted.value) 0f else musicVolume.value
            sync(it)
        } ?: run {
            playbackState.value = Player.STATE_IDLE
            isPlaying.value = false
            mediaMetadata.value = null
            currentSong.value = null
            queueWindows.value = emptyList()
            currentMediaItemIndex.value = -1
            currentWindowIndex.value = -1
            // Nothing is playing: drop the lyrics row too (it is gated on
            // this id, so clearing it takes currentLyrics back to null).
            requestedLyricsVideoId.value = null
            publishQueueSnapshot()
        }
    }

    fun dispose() {
        controller?.removeListener(listener)
        controller = null
        scope.cancel()
    }

    // ---- state flows (Mochy's public shape) ----

    val playbackState = MutableStateFlow(Player.STATE_IDLE)
    val isPlaying = MutableStateFlow(false)

    // Mochy semantics: playing and not buffering (per-card play indicators).
    // Added 15 Nov 00:50 for the HomeScreen port (it read isPlaying until
    // this flow existed).
    val isEffectivelyPlaying: StateFlow<Boolean> =
        kotlinx.coroutines.flow.combine(isPlaying, playbackState) { playing, state ->
            playing && state != Player.STATE_BUFFERING
        }.stateIn(scope, SharingStarted.Lazily, false)

    val mediaMetadata = MutableStateFlow<MediaMetadata?>(null)
    val currentSong = MutableStateFlow<Song?>(null)

    val queueTitle = MutableStateFlow<String?>(null)
    val queueWindows = MutableStateFlow<List<Timeline.Window>>(emptyList())
    val currentMediaItemIndex = MutableStateFlow(-1)
    val currentWindowIndex = MutableStateFlow(-1)

    val queueItems: StateFlow<List<QueueItemData>> = queueWindows.map { windows ->
        windows.map(::toQueueItem)
    }.distinctUntilChanged().stateIn(scope, SharingStarted.Lazily, emptyList())

    val queueSnapshot = MutableStateFlow(QueueState())
    private var snapshotWindows: List<Timeline.Window>? = null
    private var snapshotItems: List<QueueItemData> = emptyList()

    // the host turns this off while the app is hidden; the progress poll rests
    // then instead of recomposing the player for nobody
    val isUiVisible = MutableStateFlow(true)

    val progressState: StateFlow<ProgressState> =
        callbackFlow {
            while (true) {
                isUiVisible.first { it }
                try {
                    val p = controller
                    if (p != null) {
                        val position = p.currentPosition.coerceAtLeast(0L)
                        val duration = p.duration.coerceAtLeast(0L)
                        trySend(
                            ProgressState(
                                position = position,
                                duration = duration,
                                percentage = if (duration > 0L) position.toFloat() / duration else 0f,
                            )
                        )
                    }
                } catch (_: Exception) {
                }
                delay(200L)
            }
        }.distinctUntilChanged().stateIn(
            scope,
            SharingStarted.Lazily,
            ProgressState(),
        )

    val shuffleModeEnabled = MutableStateFlow(false)
    val repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val canSkipPrevious = MutableStateFlow(true)
    val canSkipNext = MutableStateFlow(true)

    val error = MutableStateFlow<PlaybackException?>(null)
    val isMuted = MutableStateFlow(false)

    private val _playFailure = MutableSharedFlow<String>(extraBufferCapacity = 1)

    /**
     * One-shot, user-facing messages for a queue that failed to START
     * (its fetch threw, or it resolved to nothing to play). The host shows
     * each one once; without this a tap that loads nothing reads as a dead
     * button. Engine errors mid-playback travel on [error] instead — a
     * failed stream is the service's to report, a queue that never reached
     * it is this connection's.
     */
    val playFailure: SharedFlow<String> = _playFailure.asSharedFlow()

    // ---- Phase 3 adapter members (the ported player UI reads these) ----

    /**
     * The bound MediaController as a [Player], null until [bind] supplies
     * one. Every call site is nullable-safe (core B1, covers B2).
     */
    val player: Player?
        get() = controller

    /**
     * Playback speed/pitch, forwarded to the bound controller;
     * [PlaybackParameters.DEFAULT] while unbound. The ported HoldSpeed
     * (hold-to-speed ×2 / ×0.5) sets speed through this (core B2).
     */
    var playbackParameters: PlaybackParameters
        get() = controller?.playbackParameters ?: PlaybackParameters.DEFAULT
        set(value) {
            controller?.playbackParameters = value
        }

    /**
     * The combined UI state the player screens read (trackInfo,
     * transportState, queueState, progressState). Lazy so construction
     * order against the flows above can never matter (core B3, covers B1).
     */
    val uiState: PlayerUiState by lazy { PlayerUiState(this) }

    /**
     * STUB — stays null. ChordWave has no stream-failure signal (no
     * retry/timeout event reaches this layer), so there is nothing real
     * to emit. The flow exists so StreamFailureUi compiles; its helpers
     * return -1f/null with no failure, so the red failure arc simply
     * never shows (core B6, covers B3).
     * TODO(phase3-adapter): emit a real StreamFailure if CW ever
     * surfaces a stream retry/timeout.
     */
    val streamFailure = MutableStateFlow<StreamFailure?>(null)

    /**
     * The user's lyrics offset in ms for the song now playing: the song's
     * own offset when one was set ([AppSettings.lyricsOffsetsBySong]),
     * otherwise ChordWave's global setting ([AppSettings.lyricsOffsetMs]).
     * Positive values delay synced lyrics (core B11, styles §(b).4,
     * menus L1).
     */
    val lyricsOffsetMs: StateFlow<Int> = combine(
        mediaMetadata,
        AppSettings.lyricsOffsetsBySong,
        AppSettings.lyricsOffsetMs,
    ) { metadata, perSong, global ->
        perSong[metadata?.id] ?: global
    }.stateIn(
        scope,
        SharingStarted.Lazily,
        AppSettings.lyricsOffsetsBySong.value[mediaMetadata.value?.id]
            ?: AppSettings.lyricsOffsetMs.value,
    )

    /**
     * Whether Automix/a crossfade is mixing into the next track right
     * now, straight from the engine's own signal ([AppSettings.smartMixInProgress],
     * written by PlaybackService/CrossfadeController). MixingNote reads
     * this to decide whether to show the "mixing" note.
     */
    val isCrossmixing: StateFlow<Boolean> = AppSettings.smartMixInProgress

    private val _studioRequests = MutableStateFlow(0L)

    /**
     * Incremented by [requestStudio] to ask BottomSheetPlayer to open
     * the player studio ("Customize player" in settings). BottomSheetPlayer
     * is the only reader; nothing in staging writes it yet — [requestStudio]
     * is the API for the settings caller (core B13).
     */
    val studioRequests: StateFlow<Long> = _studioRequests

    /**
     * The music volume (0..1) behind the DeviceOutputSheet slider
     * (menus D2). Independent of [isMuted]: mute silences the controller
     * without touching this, so unmuting restores the volume.
     */
    val musicVolume = MutableStateFlow(1f)

    // ---- lyrics (Phase 3 adapter — lyrics) ----
    //
    // When ChordWave loads lyrics, and why this section exists at all:
    // MainViewModel.loadLyrics runs AUTOMATICALLY on track change — but only
    // from a LaunchedEffect in the CLASSIC UI (MainActivity.kt, keyed on
    // player.song?.videoId + duration + the lyric settings). In Modern mode
    // MainActivity returns early with MochyApp() and that effect never runs,
    // so nothing would ever load lyrics for the Modern player. The
    // connection therefore triggers the load itself, through a loader lambda
    // the host attaches (see attachLyrics); it reuses CW's loadLyrics path
    // verbatim and invents no fetcher. PlaybackService also loads lyrics on
    // track change (loadLyricsForCurrentTrack), but into its own private
    // serviceLyrics for the subtitle ticker — that path does not publish to
    // MainViewModel.lyrics and cannot feed this flow.

    // The host's MainViewModel flows, forwarded here so currentLyrics is one
    // combine. The connection never touches MainViewModel directly: the host
    // attaches the flows and the loader (MochyApp has the ViewModel).
    private val attachedLyricsLines = MutableStateFlow<List<LyricLine>?>(null)
    private val attachedLyricsSource = MutableStateFlow<LyricsSource?>(null)
    private val attachedLyricsChecked = MutableStateFlow(false)
    private val attachedLyricsProviderStates =
        MutableStateFlow<Map<LyricsSource, LyricsProviderState>>(emptyMap())
    private val lyricsLoaderFlow = MutableStateFlow<((LyricsLoadRequest) -> Unit)?>(null)
    private var lyricsProviderBridge: LyricsProviderBridge? = null

    /**
     * The song the trigger last asked the loader for. currentLyrics is gated
     * on this, NOT on mediaMetadata: the trigger clears the attached lyrics
     * synchronously before setting it, so the row can never show the old
     * song's lines under the new song's id — not even for one frame. It is
     * cleared again when the controller unbinds (bind(null)).
     */
    private val requestedLyricsVideoId = MutableStateFlow<String?>(null)

    private var lyricsForwardJobs: List<Job> = emptyList()
    private var warnedNoLyricsLoader = false

    /**
     * The current song's lyrics row (lyrics B1, core B5, menus L1).
     *
     * Three states, matching what the staged UI was written against
     * (LyricsScreen.kt:565-577):
     * - null: the lookup is still running (or nothing is attached yet) —
     *   the page shows loading dots, the card shows "No lyrics".
     * - a row whose lyrics are [LyricsEntity.LYRICS_NOT_FOUND]: the lookup
     *   finished and found nothing — the page shows "No lyrics found".
     * - a row with LRC text: the serialised lines
     *   ([com.music.bitchord.ui.mochy.lyrics.serializeLyricLines]),
     *   parsed back by the UI with LyricsUtils.parseLyricsCached.
     *
     * The mapping itself lives in [mapLyricsToEntity] (pure, testable);
     * this flow only combines the attached CW state. It stays null until
     * [attachLyrics] supplies the flows AND the trigger has asked the
     * loader for a song — without a loader, lyrics cannot be fresh, so
     * nothing is shown rather than something stale.
     */
    val currentLyrics: StateFlow<LyricsEntity?> = combine(
        attachedLyricsLines,
        attachedLyricsSource,
        attachedLyricsChecked,
        requestedLyricsVideoId,
    ) { lines, source, checked, requestedId ->
        val songId = requestedId ?: return@combine null
        mapLyricsToEntity(songId, lines, source, checked)
    }.stateIn(scope, SharingStarted.Lazily, null)

    /**
     * Per-provider lookup state for the current track, forwarded from the
     * attached [LyricsProviderBridge] — the provider picker's rows.
     */
    val lyricsProviderStates: StateFlow<Map<LyricsSource, LyricsProviderState>> =
        attachedLyricsProviderStates

    /** The source the current lyrics came from (null for embedded-file or edited lyrics). */
    val currentLyricsSource: StateFlow<LyricsSource?> = attachedLyricsSource

    /**
     * The provider picker's rows: every offered source with what the current
     * track's lookup knows about it — checking, found (with the candidate),
     * not found, or switched off in Settings. The candidate carries the
     * serialised lines, so the picker can tell word- from line-synced.
     * A source the lookup never asked (a race loser, or one added after the
     * track loaded) reads Checking — tapping it fetches it, via
     * [selectLyricsProvider].
     */
    val lyricsSources: StateFlow<Map<LyricsSource, LyricsSourceState>> = combine(
        attachedLyricsProviderStates,
        AppSettings.lyricsSources,
        AppSettings.lyricsSourceOrder,
    ) { states, enabled, order ->
        val ordered = LyricsSource.ordered(order)
        ordered.associateWith { source ->
            when {
                source !in enabled -> LyricsSourceState.Off
                else -> when (states[source]) {
                    LyricsProviderState.FOUND -> {
                        val result = lyricsProviderBridge?.providerResult(source)
                        if (result == null) {
                            LyricsSourceState.Checking
                        } else {
                            LyricsSourceState.Available(
                                ProviderCandidate(
                                    providerName = source.toMochyProviderName(),
                                    lyrics = serializeLyricLines(result.lines),
                                    order = ordered.indexOf(source),
                                    sync = syncKindOf(result.lines),
                                    script = LyricsUtils.dominantScript(
                                        result.lines.joinToString(" ") { it.text },
                                    ),
                                    source = source,
                                ),
                            )
                        }
                    }
                    LyricsProviderState.NOT_FOUND -> LyricsSourceState.Unavailable
                    else -> LyricsSourceState.Checking
                }
            }
        }
    }.stateIn(scope, SharingStarted.Lazily, emptyMap())

    /** Applies [source]'s lyrics for the current track (fetching it first when untouched). */
    fun selectLyricsProvider(source: LyricsSource) {
        lyricsProviderBridge?.selectProvider(source)
    }

    /** The completed result for [source] from the current track's lookup, if it answered. */
    fun lyricsProviderResult(source: LyricsSource): LyricsRepository.Result? =
        lyricsProviderBridge?.providerResult(source)

    /** Saves LRC the listener wrote for [songId] as that song's override. */
    fun saveLyrics(songId: String, lrc: String) {
        lyricsProviderBridge?.saveLyrics(songId, lrc)
    }

    /** Drops the saved override for [songId], re-running the provider lookup when it is playing. */
    fun clearLyrics(songId: String) {
        lyricsProviderBridge?.clearLyrics(songId)
    }

    /** Whether [songId] has a saved override. */
    fun hasLyricsOverride(songId: String): Boolean =
        lyricsProviderBridge?.hasLyricsOverride(songId) ?: false

    /** Word-, line- or plain-synced, from the lines a provider answered with. */
    private fun syncKindOf(lines: List<LyricLine>): LyricsUtils.SyncKind = when {
        lines.any { it.isWordSynced } -> LyricsUtils.SyncKind.WORD
        lines.any { it.timeMs > 0L } -> LyricsUtils.SyncKind.LINE
        lines.any { it.text.isNotBlank() } -> LyricsUtils.SyncKind.PLAIN
        else -> LyricsUtils.SyncKind.NONE
    }

    /**
     * Attaches ChordWave's lyrics state and loader. The host (MochyApp)
     * calls this once, with the activity-scoped MainViewModel:
     *
     *     connection.attachLyrics(
     *         lyrics = viewModel.lyrics,
     *         source = viewModel.lyricsSource,
     *         checked = viewModel.lyricsChecked,
     *         loader = { request ->
     *             viewModel.loadLyrics(
     *                 request.videoId, request.title, request.artist,
     *                 request.durationMs, request.album, request.localUri,
     *             )
     *         },
     *         providerBridge = ..., // the provider picker + editor over the same ViewModel
     *     )
     *
     * Re-attaching cancels the previous forwarding jobs and replaces the
     * loader. Passing a null loader disables the trigger (currentLyrics
     * stays null and one warning is logged when a song starts).
     */
    fun attachLyrics(
        lyrics: StateFlow<List<LyricLine>?>,
        source: StateFlow<LyricsSource?>,
        checked: StateFlow<Boolean>,
        loader: ((LyricsLoadRequest) -> Unit)?,
        providerBridge: LyricsProviderBridge? = null,
    ) {
        lyricsForwardJobs.forEach { it.cancel() }
        lyricsForwardJobs = listOf(
            scope.launch { lyrics.collect { attachedLyricsLines.value = it } },
            scope.launch { source.collect { attachedLyricsSource.value = it } },
            scope.launch { checked.collect { attachedLyricsChecked.value = it } },
        ) + listOfNotNull(
            providerBridge?.let { bridge ->
                scope.launch { bridge.providerStates.collect { attachedLyricsProviderStates.value = it } }
            },
        )
        lyricsProviderBridge = providerBridge
        lyricsLoaderFlow.value = loader
        if (loader != null) warnedNoLyricsLoader = false
    }

    /**
     * Persists a lyric offset for [songId] (menus L1).
     *
     * Per-song since the lyrics merge: this writes the song's own offset
     * ([AppSettings.setLyricsOffsetForSong], clamped to ±5_000 ms); songs
     * without one keep using the global [AppSettings.lyricsOffsetMs]. The
     * top-level [com.music.bitchord.ui.mochy.lyrics.saveLyricsOffset] the
     * sheets call performs this same write.
     */
    suspend fun saveLyricsOffset(songId: String, offsetMs: Int) {
        AppSettings.setLyricsOffsetForSong(songId, offsetMs)
    }

    // ---- lyrics loading trigger (core B8) ----
    //
    // Mirrors the Classic UI's LaunchedEffect keys: the song, whether a
    // duration is known yet, and the four lyric settings — so turning a
    // source on/off, reordering, or changing the syllable preference
    // re-triggers the lookup for the song already playing, and a duration
    // that lands late re-triggers the lookup loadLyrics turned away for
    // lack of one. loadLyrics dedupes by its own key, so a re-trigger that
    // changes nothing is a cheap no-op.

    /**
     * The four lyrics settings as one value, so the lyrics trigger combine
     * stays at four flows (see the note in [init]).
     */
    private data class LyricsSettings(
        val synced: Boolean,
        val sources: Set<LyricsSource>,
        val order: List<LyricsSource>,
        val prioritizeSyllables: Boolean,
    )

    private data class LyricsTriggerKey(
        val videoId: String?,
        val durationKnown: Boolean,
        val syncedLyrics: Boolean,
        val sources: Set<LyricsSource>,
        val order: List<LyricsSource>,
        val prioritizeSyllableSync: Boolean,
        val loaderAttached: Boolean,
    )

    init {
        // The trigger watches seven sources, but a seven-flow combine does
        // not resolve here — the compiler falls back to the vararg form and
        // the lambda's parameter types go unknown — so the four lyrics
        // settings combine first (the same four-flow shape PlaybackService
        // uses for these settings) and the outer combine takes four flows.
        scope.launch {
            combine(
                mediaMetadata,
                progressState,
                combine(
                    AppSettings.syncedLyrics,
                    AppSettings.lyricsSources,
                    AppSettings.lyricsSourceOrder,
                    AppSettings.prioritizeSyllableSync,
                ) { synced, sources, order, prioritizeSyllables ->
                    LyricsSettings(synced, sources, order, prioritizeSyllables)
                },
                lyricsLoaderFlow,
            ) { metadata, progress, settings, loader ->
                LyricsTriggerKey(
                    videoId = metadata?.id,
                    durationKnown = (metadata?.duration ?: 0) > 0 || progress.duration > 0L,
                    syncedLyrics = settings.synced,
                    sources = settings.sources,
                    order = settings.order,
                    prioritizeSyllableSync = settings.prioritizeSyllables,
                    loaderAttached = loader != null,
                )
            }
                .distinctUntilChanged()
                .collect { key -> triggerLyricsLoad(key) }
        }

        // A like toggled from a menu (or seeded late from the server)
        // updates the player heart without waiting for the next track
        // change. Songs never touched keep the value published above.
        scope.launch {
            LikeState.overrides.collect { overrides ->
                val current = mediaMetadata.value ?: return@collect
                if (current.id !in overrides) return@collect
                val liked = overrides[current.id] == LikeStatus.LIKE
                if (current.liked != liked) mediaMetadata.value = current.copy(liked = liked)
            }
        }

        // A device file liked (or unliked) anywhere — Classic, the widget, a
        // list menu — flips the player heart the same way.
        scope.launch {
            OfflinePlaylists.liked.collect { likedUris ->
                val current = mediaMetadata.value ?: return@collect
                val uri = current.localUri ?: return@collect
                val liked = uri in likedUris
                if (current.liked != liked) mediaMetadata.value = current.copy(liked = liked)
            }
        }
    }

    private fun triggerLyricsLoad(key: LyricsTriggerKey) {
        val videoId = key.videoId ?: return
        val loader = lyricsLoaderFlow.value
        if (loader == null) {
            if (!warnedNoLyricsLoader) {
                warnedNoLyricsLoader = true
                Timber.w(
                    "MochyPlayerConnection: no lyrics loader attached — " +
                        "the host must call attachLyrics (MochyApp) or no lyrics will load"
                )
            }
            return
        }
        val metadata = mediaMetadata.value
        // Clear synchronously BEFORE requesting: currentLyrics is gated on
        // requestedLyricsVideoId, so from this line on the row is null
        // (loading) until the new song's lines arrive — the old song's
        // lyrics can never be shown under the new id.
        attachedLyricsLines.value = null
        attachedLyricsSource.value = null
        attachedLyricsChecked.value = false
        requestedLyricsVideoId.value = videoId
        // Duration: the catalogue value first. The polled player duration
        // can still be the PREVIOUS song's at trigger time (the poller runs
        // every 200 ms; the trigger fires on the track-change event), and a
        // wrong duration mis-matches the lyrics lookup. The catalogue
        // duration changes atomically with the song; the polled one is only
        // the fallback when the catalogue has none (the durationKnown key
        // re-fires the trigger when it lands).
        val catalogueMs = (metadata?.duration ?: 0) * 1_000L
        val durationMs = if (catalogueMs > 0L) {
            catalogueMs
        } else {
            progressState.value.duration.coerceAtLeast(0L)
        }
        loader(
            LyricsLoadRequest(
                videoId = videoId,
                title = metadata?.title ?: "",
                artist = metadata?.artists?.joinToString(", ") { it.name } ?: "",
                durationMs = durationMs,
                album = metadata?.album?.title,
                localUri = metadata?.localUri,
            )
        )
    }

    // ---- sync from the controller (mirrors CW rememberPlayerState's sync) ----

    private fun sync(player: Player) {
        playbackState.value = player.playbackState
        isPlaying.value = player.isPlaying
        repeatMode.value = player.repeatMode
        shuffleModeEnabled.value = player.shuffleModeEnabled
        canSkipPrevious.value = player.hasPreviousMediaItem()
        canSkipNext.value = player.hasNextMediaItem()
        currentMediaItemIndex.value = player.currentMediaItemIndex
        currentWindowIndex.value = player.currentMediaItemIndex

        val cwSong: CwSong? = player.currentMediaItem?.let {
            try {
                it.toSong()
            } catch (_: Exception) {
                null
            }
        }
        // The like flag is overlaid from LikeState: toMochyMetadata() never
        // sets it (CW keeps likes in LikeState, seeded from the server at
        // playback / library load), so without this the player heart would
        // always read empty. The mapping is guarded like toSong() above: a
        // row this cannot map must not take the queue sync below it down
        // with it — the transport state above is already published, and a
        // half-synced queue is worse than a stale song row.
        try {
            mediaMetadata.value = cwSong?.toMochyMetadata()?.let {
                it.copy(liked = LikeState.overrides.value[it.id] == LikeStatus.LIKE || (it.localUri != null && it.localUri in OfflinePlaylists.liked.value))
            }
            currentSong.value = cwSong?.toMochySong()
        } catch (_: Exception) {
            mediaMetadata.value = null
            currentSong.value = null
        }

        val timeline = player.currentTimeline
        val windows = (0 until timeline.windowCount).map { i ->
            timeline.getWindow(i, Timeline.Window())
        }
        queueWindows.value = windows
        publishQueueSnapshot()
    }

    private fun toQueueItem(window: Timeline.Window): QueueItemData {
        val mediaItem = window.mediaItem
        return QueueItemData(
            uid = window.uid.hashCode(),
            mediaId = mediaItem.mediaId,
            title = mediaItem.mediaMetadata.title?.toString() ?: "",
            artist = mediaItem.mediaMetadata.artist?.toString() ?: "",
            artworkUri = mediaItem.mediaMetadata.artworkUri,
            duration = window.durationMs,
            isAutoplay = mediaItem.fromAutoplay,
        )
    }

    private fun publishQueueSnapshot() {
        val windows = queueWindows.value
        // A song change moves the index but not the list: only remap when the
        // windows changed.
        if (windows !== snapshotWindows) {
            snapshotItems = windows.map(::toQueueItem)
            snapshotWindows = windows
        }
        queueSnapshot.value = QueueState(
            items = snapshotItems,
            currentIndex = currentWindowIndex.value,
            title = queueTitle.value.orEmpty(),
        )
    }

    // ---- transport methods (REAL — delegate to the controller) ----

    fun togglePlayPause() {
        val p = controller ?: return
        if (p.isPlaying) p.pause() else p.play()
    }

    fun play() {
        controller?.play()
    }

    fun pause() {
        controller?.pause()
    }

    fun seekTo(position: Long) {
        controller?.seekTo(position)
    }

    fun seekToNext() {
        controller?.seekToNextMediaItem()
    }

    fun seekToPrevious() {
        controller?.seekToPreviousMediaItem()
    }

    fun skipToPreviousSong() {
        val p = controller ?: return
        if (p.currentPosition > 3000 || !p.hasPreviousMediaItem()) {
            p.seekTo(0)
        } else {
            p.seekToPreviousMediaItem()
        }
    }

    fun toggleRepeatMode() {
        val p = controller ?: return
        p.repeatMode = when (p.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun toggleShuffleMode() {
        val p = controller ?: return
        p.shuffleModeEnabled = !p.shuffleModeEnabled
    }

    fun toggleMute() = setMuted(!isMuted.value)

    fun setMuted(muted: Boolean) {
        isMuted.value = muted
        // Unmuting restores the stored music volume, not full volume.
        controller?.volume = if (muted) 0f else musicVolume.value
    }

    /**
     * Music volume (menus D2): clamps to 0..1, stores it, and applies it
     * to the controller unless muted (mute keeps the controller silent
     * and the stored volume is applied on unmute / rebind).
     */
    fun setMusicVolume(volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        musicVolume.value = clamped
        if (!isMuted.value) controller?.volume = clamped
    }

    /** Ask BottomSheetPlayer to open the player studio (core B13). */
    fun requestStudio() {
        _studioRequests.value += 1
    }

    /**
     * What [mediaId] is actually playing as, for the format badge
     * (core B4, styles §(b).2).
     *
     * v1: a one-shot snapshot from the bound controller's selected audio
     * track — real codec/sampleRate while the song is the one playing,
     * null otherwise (other queue rows, unbound, or no track selected
     * yet). Callers re-collect when the mediaId changes
     * (`remember(mediaId)`), so a song change re-snapshots; the flow
     * does NOT re-emit when track selection resolves later, so a badge
     * can stay blank until the next recomposition that re-collects.
     * TODO(phase3-adapter): make it reactive (re-emit on
     * onTracksChanged) if the badge is ever seen stuck blank.
     */
    fun streamFacts(mediaId: String): Flow<StreamFacts?> = flow {
        val p = controller
        val format = if (p != null && p.currentMediaItem?.mediaId == mediaId) {
            try {
                StreamFacts.selectedAudio(p.currentTracks)
            } catch (_: Exception) {
                null
            }
        } else {
            null
        }
        if (format == null) {
            emit(null)
        } else {
            emit(
                StreamFacts(
                    codec = StreamFacts.codecOf(format.sampleMimeType, format.codecs),
                    sampleRate = format.sampleRate.takeIf { it > 0 },
                )
            )
        }
    }

    // ---- queue methods (REAL — delegate to the controller) ----
    //
    // The controller talks to CW's own PlaybackService session, whose
    // MediaSource factory resolves the `bitchord://watch?v=…` URIs the
    // Modern builders put on YouTube items — the same path the Classic UI
    // plays through.

    /** Queue uids are Timeline.Window uid hashCodes (see toQueueItem). */
    private fun indexOfUid(uid: Int): Int =
        queueWindows.value.indexOfFirst { it.uid.hashCode() == uid }

    // The queue start in flight, if any. A second tap cancels the first
    // fetch rather than racing it: two setMediaItems landing in either
    // order is how a tap plays the song from the tap before it.
    private var playQueueJob: Job? = null

    fun playQueue(queue: Queue) {
        // while a paired device is being steered, queues picked here play there instead (batch 19)
        if (LinkPlayer.queueInterceptor?.invoke(queue) == true) return
        val p = controller ?: return
        playQueueJob?.cancel()
        playQueueJob = scope.launch {
            // Show the tapped song at once: the queue fetch is network, and
            // without this the tap reads as dead until the items land. The
            // preload row is the song itself (title, artist, artwork), and
            // BUFFERING is what the slider draws its Loading state from;
            // the controller's own events correct both the moment the
            // items are set, and the failure branches below re-sync them
            // if the fetch never lands.
            queue.preloadItem?.let { preload ->
                mediaMetadata.value = preload
                playbackState.value = Player.STATE_BUFFERING
            }
            // Classic's start sequence (PlayerConnection.playSongs): drop
            // any bounded context the service is holding for an album or
            // playlist started from the Classic UI, so it cannot trim or
            // refill this queue behind the player's back. Best-effort — a
            // command that fails must not lose the tap.
            suspend fun clearBoundedContext() {
                runCatching {
                    p.sendCustomCommand(
                        SessionCommand(ACTION_CLEAR_BOUNDED_CONTEXT, Bundle.EMPTY),
                        Bundle.EMPTY,
                    ).await()
                }.onFailure { Timber.w(it, "MochyPlayerConnection.playQueue: clear bounded context failed") }
            }
            // A lone tapped song starts NOW (batch 48): its queue is the
            // song plus a radio fetched behind it, and holding the tap on
            // that fetch put a whole network round-trip in front of every
            // play — the stream resolve could not even begin until the
            // radio landed. Classic never waits: it sets the song and
            // lets the tail follow. Same here: the seed plays at once
            // (its resolve runs while the radio is still in flight) and
            // the radio is appended when it arrives. If the fetch fails,
            // the seed keeps playing and the service's autoplay appends
            // its own tail, exactly the fallback YouTubeQueue documents.
            val seed = (queue as? YouTubeQueue)?.takeIf { it.startsOnSeed }?.preloadItem
            if (seed != null) {
                clearBoundedContext()
                p.setMediaItems(listOf(seed.toMediaItem()), 0, 0L)
                p.prepare()
                p.play()
                val status = try {
                    queue.getInitialStatus()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.w(e, "MochyPlayerConnection.playQueue: radio tail failed to load; seed keeps playing")
                    return@launch
                }
                queueTitle.value = status.title
                // The radio behind the seed, minus the seed itself. Only
                // appended while the seed is still the track in hand: if
                // the listener already moved on, this tail belongs to a
                // tap that no longer stands. (A newer tap cancels this
                // job outright; this guard covers moves within the tap,
                // like autoplay having taken over at the seed's end.)
                val tail = status.items.filterNot { it.mediaId == seed.id }
                if (tail.isNotEmpty() && p.currentMediaItem?.mediaId == seed.id) {
                    p.addMediaItems(tail)
                }
                return@launch
            }
            val status = try {
                queue.getInitialStatus()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "MochyPlayerConnection.playQueue: queue failed to load")
                resyncFromController()
                _playFailure.emit(context.getString(R.string.couldnt_load_queue))
                return@launch
            }
            if (status.items.isEmpty()) {
                Timber.w("MochyPlayerConnection.playQueue: queue resolved to nothing to play")
                resyncFromController()
                _playFailure.emit(context.getString(R.string.couldnt_load_queue))
                return@launch
            }
            queueTitle.value = status.title
            clearBoundedContext()
            p.setMediaItems(
                status.items,
                status.mediaItemIndex.coerceIn(0, status.items.lastIndex),
                status.position,
            )
            p.prepare()
            p.play()
        }
    }

    /**
     * Puts the published state back to what the controller actually holds.
     * The failure branches of [playQueue] call this after clearing the
     * optimistic preload: without it a failed tap would leave the tapped
     * song on screen, loading forever, over whatever was really playing.
     */
    private fun resyncFromController() {
        val p = controller
        if (p != null) {
            sync(p)
        } else {
            playbackState.value = Player.STATE_IDLE
            isPlaying.value = false
            mediaMetadata.value = null
            currentSong.value = null
        }
    }

    fun playNext(item: MediaItem) = playNext(listOf(item))

    fun playNext(items: List<MediaItem>) {
        val p = controller ?: return
        if (items.isEmpty()) return
        if (LinkPlayer.nextInterceptor?.invoke(items) == true) return
        p.addMediaItems((p.currentMediaItemIndex + 1).coerceAtLeast(0), items)
    }

    fun addToQueue(item: MediaItem) = addToQueue(listOf(item))

    fun addToQueue(items: List<MediaItem>) {
        val p = controller ?: return
        if (items.isEmpty()) return
        if (LinkPlayer.queueEndInterceptor?.invoke(items) == true) return
        p.addMediaItems(items)
    }

    fun playQueueItem(uid: Int) {
        val p = controller ?: return
        val index = indexOfUid(uid)
        if (index < 0) return
        p.seekToDefaultPosition(index)
        p.play()
    }

    /**
     * Drops [uid] directly after [afterUid] (QueueScreen's drag commit
     * passes the preceding item's uid). moveMediaItem's target is the
     * item's FINAL index: moving down, removing the item first pulls the
     * anchor up one, so the target is the anchor's index; moving up, the
     * anchor stays put and the target is one past it.
     */
    fun moveQueueItem(uid: Int, afterUid: Int) {
        val p = controller ?: return
        val from = indexOfUid(uid)
        val anchor = indexOfUid(afterUid)
        if (from < 0 || anchor < 0 || from == anchor) return
        val to = if (from < anchor) anchor else anchor + 1
        p.moveMediaItem(from, to.coerceIn(0, p.mediaItemCount - 1))
    }

    fun playQueueItemNext(uid: Int) {
        val p = controller ?: return
        val from = indexOfUid(uid)
        val current = p.currentMediaItemIndex
        if (from < 0 || current < 0 || from == current) return
        // Removing an item above the current song pulls it down one.
        val to = if (from < current) current else current + 1
        p.moveMediaItem(from, to.coerceIn(0, p.mediaItemCount - 1))
    }

    fun removeQueueItems(uids: Collection<Int>) {
        val p = controller ?: return
        uids.mapNotNull { indexOfUid(it).takeIf { index -> index >= 0 } }
            .sortedDescending()
            .forEach { p.removeMediaItem(it) }
    }

    /**
     * No-op: ChordWave has no recommendation engine to stop. Mochy's
     * infinite queue is CW's autoplay ([setInfiniteQueueEnabled]); the
     * per-song "stop recommending" has no equivalent to switch off.
     */
    fun stopRecommendingCurrent() {
    }

    fun toggleLike() {
        val meta = mediaMetadata.value ?: return
        if (meta.localUri != null) {
            val cwSong = try {
                player?.currentMediaItem?.toSong()
            } catch (_: Exception) {
                null
            }
            if (cwSong != null) {
                val nowLiked = OfflinePlaylists.toggleLike(cwSong)
                mediaMetadata.value = meta.copy(liked = nowLiked)
                return
            }
        }
        scope.toggleSongLike(meta.id, !meta.liked)
        mediaMetadata.value = meta.copy(liked = !meta.liked)
        // a like here becomes a love on Last.fm/Libre.fm, when the user asked for that
        runCatching {
            EntryPointAccessors.fromApplication(context, PlayerIntegrationsEntryPoint::class.java)
                .scrobbler()
                .love(meta.artists.firstOrNull()?.name.orEmpty(), meta.title, !meta.liked)
        }
    }

    fun toggleLibrary() {
        val meta = mediaMetadata.value ?: return
        val adding = meta.inLibrary == null
        scope.sendLibraryFeedback(meta.id, adding, meta.libraryAddToken, meta.libraryRemoveToken)
        // Local hint only: the next track change republishes inLibrary as
        // null (CW has no local library flag — the server state is the
        // state). Within this song the button flips and stays flipped.
        mediaMetadata.value = meta.copy(inLibrary = if (adding) LocalDateTime.now() else null)
    }

    fun startRadioSeamlessly() {
        val p = controller ?: return
        val videoId = mediaMetadata.value?.id ?: return
        scope.launch {
            val songs = YtMusicRepository.radio(videoId).getOrNull() ?: return@launch
            val existing = (0 until p.mediaItemCount).map { p.getMediaItemAt(it).mediaId }.toSet()
            val items = songs
                .filter { it.videoId != videoId && it.videoId !in existing }
                .map { it.toMochyMetadata().toMediaItem() }
            if (items.isNotEmpty()) p.addMediaItems(items)
        }
    }

    fun setInfiniteQueueEnabled(enabled: Boolean) {
        // Batch 53: the Mochy engine reads InfiniteQueueKey itself now
        // (Mochy's MusicService does the same), so the button writes
        // only the Mochy pref — Classic's autoplay is its own setting.
        PreferencesCache.set(context.dataStore, InfiniteQueueKey, enabled)
    }
}

/** Batch 55b: Mochy's Player.toggleRepeatMode (extensions/PlayerExt.kt), for module pages' repeat buttons. */
fun androidx.media3.common.Player.toggleRepeatMode() {
    repeatMode =
        when (repeatMode) {
            androidx.media3.common.Player.REPEAT_MODE_OFF -> androidx.media3.common.Player.REPEAT_MODE_ALL
            androidx.media3.common.Player.REPEAT_MODE_ALL -> androidx.media3.common.Player.REPEAT_MODE_ONE
            androidx.media3.common.Player.REPEAT_MODE_ONE -> androidx.media3.common.Player.REPEAT_MODE_OFF
            else -> throw IllegalStateException()
        }
}
