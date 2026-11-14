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
// STUBBED (Phase 3): playQueue/playNext/addToQueue/playQueueItem/
// moveQueueItem/removeQueueItems (need the Queue -> CW play translation),
// toggleLike/toggleLibrary (need the CW LikeState bridge), radio/infinite
// queue, lyrics, stream facts, PlayerUiState.
// SKIPPED (no CW equivalent, out of scope): MusicService-bound flows
// (waitingForNetworkConnection, streamFailure, crossmix), cast, remote
// control, SongEdits.
//
// The Modern host (MochyApp) creates one instance, binds it to
// rememberMediaController() inside a DisposableEffect, provides it via
// LocalPlayerConnection, and calls dispose() when the host leaves.
package com.music.bitchord.ui.mochy.playback

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.session.MediaController
import com.music.bitchord.data.model.Song as CwSong
import com.music.bitchord.playback.fromAutoplay
import com.music.bitchord.playback.toSong
import com.music.bitchord.ui.mochy.models.MediaMetadata
import com.music.bitchord.ui.mochy.models.Song
import com.music.bitchord.ui.mochy.models.toMochyMetadata
import com.music.bitchord.ui.mochy.models.toMochySong
import com.music.bitchord.ui.mochy.player.models.ProgressState
import com.music.bitchord.ui.mochy.player.models.QueueItemData
import com.music.bitchord.ui.mochy.player.models.QueueState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import timber.log.Timber

val LocalPlayerConnection = staticCompositionLocalOf<MochyPlayerConnection?> {
    error("No MochyPlayerConnection provided")
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
            sync(it)
        } ?: run {
            playbackState.value = Player.STATE_IDLE
            isPlaying.value = false
            mediaMetadata.value = null
            currentSong.value = null
            queueWindows.value = emptyList()
            currentMediaItemIndex.value = -1
            currentWindowIndex.value = -1
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
        mediaMetadata.value = cwSong?.toMochyMetadata()
        currentSong.value = cwSong?.toMochySong()

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
        controller?.volume = if (muted) 0f else 1f
    }

    // ---- queue methods (STUBBED — Phase 3 wires the Queue -> CW translation) ----

    fun playQueue(queue: com.music.bitchord.ui.mochy.playback.queues.Queue) {
        Timber.w("MochyPlayerConnection.playQueue is not wired yet (Phase 3)")
    }

    fun playNext(item: MediaItem) = playNext(listOf(item))

    fun playNext(items: List<MediaItem>) {
        Timber.w("MochyPlayerConnection.playNext is not wired yet (Phase 3)")
    }

    fun addToQueue(item: MediaItem) = addToQueue(listOf(item))

    fun addToQueue(items: List<MediaItem>) {
        Timber.w("MochyPlayerConnection.addToQueue is not wired yet (Phase 3)")
    }

    fun playQueueItem(uid: Int) {
        Timber.w("MochyPlayerConnection.playQueueItem is not wired yet (Phase 3)")
    }

    fun moveQueueItem(uid: Int, afterUid: Int) {
        Timber.w("MochyPlayerConnection.moveQueueItem is not wired yet (Phase 3)")
    }

    fun playQueueItemNext(uid: Int) {
        Timber.w("MochyPlayerConnection.playQueueItemNext is not wired yet (Phase 3)")
    }

    fun removeQueueItems(uids: Collection<Int>) {
        Timber.w("MochyPlayerConnection.removeQueueItems is not wired yet (Phase 3)")
    }

    fun stopRecommendingCurrent() {
        Timber.w("MochyPlayerConnection.stopRecommendingCurrent is not wired yet (Phase 3)")
    }

    fun toggleLike() {
        Timber.w("MochyPlayerConnection.toggleLike is not wired yet (Phase 3, CW LikeState bridge)")
    }

    fun toggleLibrary() {
        Timber.w("MochyPlayerConnection.toggleLibrary is not wired yet (Phase 3, CW LikeState bridge)")
    }

    fun startRadioSeamlessly() {
        Timber.w("MochyPlayerConnection.startRadioSeamlessly is not wired yet (Phase 3)")
    }

    fun setInfiniteQueueEnabled(enabled: Boolean) {
        Timber.w("MochyPlayerConnection.setInfiniteQueueEnabled is not wired yet (Phase 3)")
    }
}
