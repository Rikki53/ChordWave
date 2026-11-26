// Ported from Mochy Music (GPL-3.0): ui/player/share/song/SongShare.kt
// SongShare.kt

package com.music.bitchord.ui.mochy.player.share.song

import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.music.bitchord.ui.mochy.extensions.toMediaItem
import com.music.bitchord.ui.mochy.playback.LocalPlayerConnection
import com.music.bitchord.ui.mochy.playback.SongDataSourceFactory
import com.music.bitchord.ui.mochy.constants.ElementBlurKey
import com.music.bitchord.ui.mochy.constants.LyricsHighBloomKey
import com.music.bitchord.ui.mochy.constants.LyricsMotionStyle
import com.music.bitchord.ui.mochy.constants.LyricsMotionStyleKey
import com.music.bitchord.ui.mochy.lyrics.LyricsEntry
import com.music.bitchord.ui.mochy.lyrics.LyricsUtils
import com.music.bitchord.ui.mochy.models.MediaMetadata
import com.music.bitchord.ui.mochy.component.PopupSheetHost
import com.music.bitchord.ui.mochy.component.PopupSheetState
import com.music.bitchord.ui.mochy.player.share.LyricsShareSession
import com.music.bitchord.ui.mochy.player.share.ShareBackground
import com.music.bitchord.ui.mochy.player.share.ShareFlight
import com.music.bitchord.ui.mochy.player.share.showLinePicker
import com.music.bitchord.ui.mochy.utils.rememberEnumPreference
import com.music.bitchord.ui.mochy.utils.rememberPreference
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// "share song" from any menu: the menu calls open, and the host in MainActivity shows the share over everything, the player included
object SongShare {
    internal var song by mutableStateOf<MediaMetadata?>(null)
        private set

    fun open(song: MediaMetadata) {
        this.song = song
    }

    internal fun close() {
        song = null
    }
}

@Composable
fun SongShareHost() {
    val song = SongShare.song ?: return
    key(song.id) { SongShareDialog(song, onDismiss = SongShare::close) }
}

// holding a lyric line: the same share, opening on the line picker with the held line flying in, and no link page
@Composable
internal fun LyricsSongShare(song: MediaMetadata, pick: LyricsShareSession, lyrics: List<LyricsEntry>, onDismiss: () -> Unit) {
    SongShareDialog(song, onDismiss, pick = pick, heldLyrics = lyrics)
}

@Composable
private fun SongShareDialog(
    song: MediaMetadata,
    onDismiss: () -> Unit,
    pick: LyricsShareSession? = null,
    heldLyrics: List<LyricsEntry>? = null,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            // the sheets handle back themselves, one sheet at a time
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        ),
    ) {
        EdgeToEdgeDialogWindow()
        val context = LocalContext.current
        val playerConnection = LocalPlayerConnection.current
        val session = remember {
            SongShareSession(song).also { started ->
                if (pick != null && heldLyrics != null) {
                    started.kind = SongShareKind.Lyrics
                    started.pick = pick
                    started.lyrics = Fetched.Found(heldLyrics)
                }
                started.readingChoice = startingReadingChoice(context)
            }
        }
        val sheets = remember { PopupSheetState() }

        ReadLyricsLook(session)
        ShareLyricReadings(session)

        LaunchedEffect(Unit) {
            if (pick != null) {
                // the held line's own picker comes first; closing it plays the opening backwards, back into the lyrics
                pick.onLinesPicked = { popup -> session.choseLines(popup) }
                val picker = sheets.showLinePicker(pick)
                snapshotFlow { picker.isClosing }.first { it }
                pick.choreography.playBackward()
            } else {
                val first = sheets.showKindPage(session)
                snapshotFlow { first.isClosing }.first { it }
            }
            snapshotFlow { sheets.isVisible }.first { !it }
            onDismiss()
        }
        LaunchedEffect(session) {
            launch { loadShareArt(context, session) }
            launch { loadAnimatedCover(session) }
            launch { findMusicVideo(session) }
            if (heldLyrics == null) {
                loadShareLyrics(context, session)
            } else {
                loadLyricsOffset(session)
            }
            // the app's own playback, read off the connection's flows: the position
            // only counts when the song playing is this one
            val playing = playerConnection?.let { connection ->
                if (connection.mediaMetadata.value?.id == song.id) connection.progressState.value.position else null
            }
            pickStartingMoment(session, playing)
        }
        // the line picker is tinted with the cover's colours too
        LaunchedEffect(session) { snapshotFlow { session.palette }.collect { session.pick?.palette = it } }
        // your own picture is read again whenever a different one is picked
        LaunchedEffect(session) {
            snapshotFlow { ((session.background as? SongBackground.Still)?.background as? ShareBackground.Picture)?.uri }
                .collect { uri -> if (uri != null) loadPictureArt(context, session, uri) }
        }
        // lining the music video up means listening to both; it only happens once the music video is picked (full canvas or card only)
        LaunchedEffect(session) {
            snapshotFlow { session.activeVideoSource == ShareVideoSource.MusicVideo }.first { it }
            matchMusicVideo(session)
        }
        // if the user picks a YouTube video from search that happens to match the song's audio, line it up automatically too
        LaunchedEffect(session) {
            var matchedId: String? = null
            snapshotFlow { session.pickedYouTubeVideo.found?.videoId }.collectLatest { videoId ->
                if (videoId != null && videoId != matchedId) {
                    matchedId = videoId
                    matchPickedYouTubeVideo(session, videoId)
                }
            }
        }
        MovesThePlayhead(session)
        SongPreviewAudio(session)

        Box(Modifier.fillMaxSize()) {
            PopupSheetHost(states = listOf(sheets)) {}
            if (pick != null) ShareFlight(pick)
            // never drawn by the window: export frames are drawn from here by hand, at full size
            AndroidView(
                factory = { FrameLayout(it).apply { visibility = View.INVISIBLE }.also { host -> session.offscreenHost = host } },
                modifier = Modifier.size(1.dp),
            )
        }
    }
}

// one clock for every picture in the share, and only while a video picture is up; a still share draws once and rests
@Composable
private fun MovesThePlayhead(session: SongShareSession) {
    LaunchedEffect(session) {
        snapshotFlow { session.isVideo && session.previewWanted }.collectLatest { moving ->
            if (!moving) {
                session.playhead.longValue = session.windowStartMs
                return@collectLatest
            }
            while (true) withFrameMillis { session.playhead.longValue = session.previewPositionMs() }
        }
    }
}

// the lyrics page's look, read here once so the card's lyrics and the exports use the same one
@Composable
private fun ReadLyricsLook(session: SongShareSession) {
    val (motionStyle) = rememberEnumPreference(LyricsMotionStyleKey, defaultValue = LyricsMotionStyle.MOTION)
    val (highBloom) = rememberPreference(LyricsHighBloomKey, defaultValue = true)
    val (lineBlur) = rememberPreference(ElementBlurKey, defaultValue = true)
    LaunchedEffect(motionStyle, highBloom, lineBlur) {
        session.lyricsLook = ShareLyricsLook(motionStyle, highBloom, lineBlur)
    }
}

// the share's own player for the song; it stays quiet until a video share plays, and only then pauses the app's music, which carries on once the share closes
@Composable
private fun SongPreviewAudio(session: SongShareSession) {
    val context = LocalContext.current
    val playerConnection = LocalPlayerConnection.current
    WatchesTheApp(session)
    // Whether the app's music was playing when the preview first paused it:
    // null until the preview has paused it once, so the share never starts
    // music that was already stopped.
    val pausedApp = remember(session) { mutableStateOf<Boolean?>(null) }
    val currentConnection by rememberUpdatedState(playerConnection)
    DisposableEffect(session) {
        val preview = SongPreviewPlayer(context, SongDataSourceFactory.create(context))
        session.preview = preview
        onDispose {
            session.preview = null
            preview.release()
            if (pausedApp.value == true) currentConnection?.play()
        }
    }
    LaunchedEffect(session) {
        snapshotFlow { Triple(session.previewPlays, session.windowStartMs, session.windowLengthMs) }
            .collectLatest { (plays, startMs, lengthMs) ->
                val preview = session.preview ?: return@collectLatest
                if (!plays) {
                    preview.setPlaying(false)
                    return@collectLatest
                }
                if (pausedApp.value == null) {
                    pausedApp.value = playerConnection?.isPlaying?.value == true
                    playerConnection?.pause()
                }
                preview.load(session.song.toMediaItem(), startMs, lengthMs, play = true)
            }
    }
}

// the share's song stops the moment you leave ChordWave, say for instagram, so it never loops on under the app you post from
@Composable
private fun WatchesTheApp(session: SongShareSession) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, session) {
        val observer = LifecycleEventObserver { _, _ -> session.onScreen = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
}

// the share starts on what's playing when it's this song, otherwise on the first sung line
private fun pickStartingMoment(session: SongShareSession, playingAtMs: Long?) {
    if (session.windowTouched || session.pick != null) return
    val lines = session.lyrics.found.orEmpty()
    val start = when {
        playingAtMs != null && lines.isNotEmpty() -> {
            val current = LyricsUtils.findCurrentLineIndex(lines, playingAtMs).coerceIn(0, lines.lastIndex)
            lines[current].time - session.lyricsOffsetMs
        }
        playingAtMs != null -> playingAtMs
        else -> (lines.firstOrNull { it.time > 0L }?.time ?: 0L) - session.lyricsOffsetMs
    }
    session.startAt(start)
}

// lines picked: the window moves to them, and the styles come next
internal fun SongShareSession.choseLines(popup: PopupSheetState) {
    fitWindowToPick()
    popup.showStylePage(this)
}

// the dialog's window drawn edge to edge, see-through and without its own dim or animation, so its coordinates are the app's and the held line flies from exactly where it sits
@Composable
private fun EdgeToEdgeDialogWindow() {
    val window = (LocalView.current.parent as? DialogWindowProvider)?.window ?: return
    SideEffect {
        window.run {
            setWindowAnimations(0)
            setDimAmount(0f)
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
            @Suppress("DEPRECATION")
            clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS or WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)
            addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN)
            @Suppress("DEPRECATION")
            statusBarColor = android.graphics.Color.TRANSPARENT
            @Suppress("DEPRECATION")
            navigationBarColor = android.graphics.Color.TRANSPARENT
            if (Build.VERSION.SDK_INT >= 28) {
                attributes = attributes.also {
                    it.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }
            if (Build.VERSION.SDK_INT >= 29) isNavigationBarContrastEnforced = false
            if (Build.VERSION.SDK_INT >= 30) {
                attributes = attributes.also { it.fitInsetsTypes = 0 }
            }
        }
    }
}
