// LyricsStubs.kt — lyrics-engine surface for the player-menu sheets.
//
// Copied from phase3-player-menus and REWIRED (15 Nov 2026,
// phase3-adapter-lyrics): the L1 members are real now — rememberLyricsEntity
// and rememberLyricsOffsetMs read the connection, and saveLyricsOffset
// persists through AppSettings (the same global write
// MochyPlayerConnection.saveLyricsOffset performs). Everything else is still
// a stub, exactly as the menus chunk left it: LyricsMenuStub (L2),
// LyricsAutoSyncStub (L4), LyricsTranslationStub (L5),
// rememberYouTubeCaptionLanguages (L5). Contracts: NEEDS.md §(b) L1–L5 in
// phase3-player-menus, and NEEDS.md in phase3-adapter-lyrics.
//
// DEVIATION (v1, ChordWave has no per-song offset store): the offset is
// GLOBAL. saveLyricsOffset ignores songId and writes AppSettings'
// single lyricsOffsetMs; every song shares it. See NEEDS.md.
//
// COORDINATOR NOTE — LyricsOffsetSheet's save gate: the sheet only calls
// saveLyricsOffset ONCE per sheet (its `warned` flag gates the call, not
// just the log line — the menus chunk added it so the stub would not spam
// logcat on a slider drag). Now that the save is real, that gate means only
// the FIRST offset change in a sheet is persisted. Remove the gate in
// LyricsOffsetSheet (keep the LaunchedEffect(offset) + the
// `offset == startOffsetFlow.value` guard) or the slider will look broken.
// Also: the sheet's MaxOffsetMs is 10_000 but AppSettings clamps to ±5_000,
// so values past ±5s snap back on reopen — decide whether to clamp the
// sheet to ±5_000.

package com.music.bitchord.ui.mochy.lyrics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.ui.mochy.models.MediaMetadata
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
 * The lyric offset in ms, from the connection's lyricsOffsetMs — which is
 * ChordWave's single global AppSettings.lyricsOffsetMs, not a per-song
 * value (see the file header).
 */
@Composable
fun rememberLyricsOffsetMs(): StateFlow<Int> {
    val connection = LocalPlayerConnection.current
    val fallback = remember { MutableStateFlow(0) }
    return connection?.lyricsOffsetMs ?: fallback
}

/**
 * Persists a lyric offset for [songId]. WIRED (v1): writes ChordWave's
 * single GLOBAL offset via [AppSettings.setLyricsOffsetMs] — [songId] is
 * accepted for the contract's sake and ignored, because ChordWave has no
 * per-song offset store. The value is clamped to ±5_000 ms by AppSettings.
 * Same write [MochyPlayerConnection.saveLyricsOffset] performs; this
 * top-level form exists because the sheet calls it from a LaunchedEffect,
 * not from a scope that can call the connection's suspend function.
 */
fun saveLyricsOffset(songId: String, offsetMs: Int) {
    AppSettings.setLyricsOffsetMs(offsetMs)
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
 * STUB for Mochy's LyricsMenuViewModel. Sources stay empty (every provider
 * reads "Checking…") until the adapter wires the real provider checks.
 */
object LyricsMenuStub {
    val sources: StateFlow<Map<String, LyricsSourceState>> =
        MutableStateFlow(emptyMap())

    fun checkSources(metadata: MediaMetadata) {
        // TODO(phase3-adapter): lyrics provider checks (NEEDS.md §(b) L2).
        Timber.w("LyricsStubs: lyrics source check not available (no engine yet)")
    }

    fun saveLyrics(songId: String, lyrics: String) {
        // TODO(phase3-adapter): save edited/picked lyrics (NEEDS.md §(b) L2).
        Timber.w("LyricsStubs: saving lyrics for %s not available (no lyrics store yet)", songId)
    }

    fun useSource(metadata: MediaMetadata, candidate: ProviderCandidate) {
        // TODO(phase3-adapter): apply a provider's lyrics (NEEDS.md §(b) L2).
        Timber.w("LyricsStubs: switching lyrics source not available (no engine yet)")
    }
}

/** Translation status, mirroring LyricsTranslationHelper.TranslationStatus. */
sealed interface TranslationStatus {
    data object Idle : TranslationStatus
    data object Translating : TranslationStatus
    data class Error(val message: String) : TranslationStatus
}

/** STUB for Mochy's LyricsTranslationHelper. */
object LyricsTranslationStub {
    val status: StateFlow<TranslationStatus> = MutableStateFlow(TranslationStatus.Idle)
    val translationOn: StateFlow<Boolean> = MutableStateFlow(false)

    fun triggerManualTranslation() {
        // TODO(phase3-adapter): lyrics translation engine (NEEDS.md §(b) L5).
        Timber.w("LyricsStubs: lyrics translation not available (no engine yet)")
    }

    fun triggerClearTranslations() {
        // TODO(phase3-adapter): lyrics translation engine (NEEDS.md §(b) L5).
        Timber.w("LyricsStubs: clearing lyrics translation not available (no engine yet)")
    }
}

/**
 * Languages the playing song's video has its own uploaded captions in.
 * Always empty until the engine exists (Mochy: YouTubeCaptionTranslation).
 */
@Composable
fun rememberYouTubeCaptionLanguages(): Set<String> = emptySet()
