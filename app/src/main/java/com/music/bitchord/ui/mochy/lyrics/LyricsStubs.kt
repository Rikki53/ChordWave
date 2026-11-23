// LyricsStubs.kt — lyrics-engine surface for the player-menu sheets.
//
// The Nov 2026 lyrics merge wired the L1/L2/L5 members for real, over
// ChordWave's own engine (MainViewModel + LyricsRepository, reached through
// the connection's LyricsProviderBridge — see MochyPlayerConnection):
// - rememberLyricsEntity / rememberLyricsOffsetMs read the connection.
// - rememberLyricsSources reads the connection's per-provider lookup states.
// - saveLyricsOffset persists a PER-SONG offset (AppSettings.
//   lyricsOffsetsBySong; songs without one use the global offset).
// - Saving edited/imported lyrics goes through the connection's saveLyrics
//   (a per-song override in AppSettings, applied by MainViewModel).
// - Translation goes through LyricsTranslationHelper, whose Google engine
//   is ChordWave's LyricsTranslation (no API key).
//
// Still stubs, exactly as the menus chunk left them: LyricsAutoSyncStub
// (L4 — auto-sync needs an audio-fingerprint engine; skipped in the merge)
// and rememberYouTubeCaptionLanguages (L5 — no YouTubeCaptionTranslation
// engine; the green caption ticks never show).

package com.music.bitchord.ui.mochy.lyrics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.music.bitchord.data.lyrics.LyricsSource
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.ui.mochy.playback.LocalPlayerConnection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import timber.log.Timber

/**
 * The playing song's lyrics row, from the connection's [MochyPlayerConnection.currentLyrics].
 * Falls back to a permanent-null flow when no connection is provided (the
 * sheets that call this already early-return without one, so the fallback
 * should never be reached).
 */
@Composable
fun rememberLyricsEntity(): StateFlow<LyricsEntity?> {
    val connection = LocalPlayerConnection.current
    val fallback = remember { MutableStateFlow<LyricsEntity?>(null) }
    return connection?.currentLyrics ?: fallback
}

/**
 * The lyric offset in ms for the song now playing, from the connection's
 * lyricsOffsetMs — the song's own offset when one was set, otherwise the
 * global AppSettings.lyricsOffsetMs.
 */
@Composable
fun rememberLyricsOffsetMs(): StateFlow<Int> {
    val connection = LocalPlayerConnection.current
    val fallback = remember { MutableStateFlow(0) }
    return connection?.lyricsOffsetMs ?: fallback
}

/**
 * Persists a lyric offset for [songId]: the song's own offset
 * ([AppSettings.setLyricsOffsetForSong], clamped to ±5_000 ms). An offset
 * of 0 removes the per-song entry, so the song falls back to the global
 * offset. Same write [MochyPlayerConnection.saveLyricsOffset] performs;
 * this top-level form exists because the sheet calls it from a
 * LaunchedEffect, not from a scope that can call the connection's suspend
 * function.
 */
fun saveLyricsOffset(songId: String, offsetMs: Int) {
    AppSettings.setLyricsOffsetForSong(songId, offsetMs)
}

/** The auto-offset listener's state, mirroring Mochy's LyricsAutoSync.State. */
sealed interface AutoSyncState {
    val songId: String

    data class Listening(override val songId: String, val progress: Float) : AutoSyncState
    data class Matching(override val songId: String) : AutoSyncState
    data class Synced(
        override val songId: String,
        val offsetMs: Int,
        val previousOffsetMs: Int,
        val sure: Boolean,
        val driftMs: Long?,
    ) : AutoSyncState

    data class Failed(override val songId: String, val message: String) : AutoSyncState
}

/** STUB for Mochy's LyricsAutoSync engine (listen-to-the-song offset finder). */
object LyricsAutoSyncStub {
    val state: StateFlow<AutoSyncState?> = MutableStateFlow(null)

    fun autoSyncLyrics(lines: List<LyricsEntry>, currentOffsetMs: Int) {
        // TODO(phase3-adapter): auto lyrics sync engine (NEEDS.md §(b) L4).
        Timber.w("LyricsStubs: auto lyrics sync not available (no engine yet)")
    }
}

/** One provider's check result, mirroring LyricsMenuViewModel.SourceState. */
sealed interface LyricsSourceState {
    data object Checking : LyricsSourceState
    data class Available(val candidate: ProviderCandidate) : LyricsSourceState
    data object Unavailable : LyricsSourceState
    data object Off : LyricsSourceState
}

/**
 * Per-provider lookup states for the current track, from the connection's
 * [MochyPlayerConnection.lyricsSources] — the provider picker's rows and
 * the song editor's source select. The automatic lookup that runs on track
 * change fills these in; there is no separate "check" call any more (the
 * old LyricsMenuStub.checkSources only logged).
 */
@Composable
fun rememberLyricsSources(): StateFlow<Map<LyricsSource, LyricsSourceState>> {
    val connection = LocalPlayerConnection.current
    val fallback = remember { MutableStateFlow<Map<LyricsSource, LyricsSourceState>>(emptyMap()) }
    return connection?.lyricsSources ?: fallback
}

/**
 * Languages the playing song's video has its own uploaded captions in.
 * Always empty until the engine exists (Mochy: YouTubeCaptionTranslation).
 */
@Composable
fun rememberYouTubeCaptionLanguages(): Set<String> = emptySet()
