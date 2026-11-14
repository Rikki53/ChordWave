// Storage types for the Modern settings → storage screens (Phase 4D contract,
// staging/phase4-secondary/NEEDS.md §1a, plus the fields the staged call sites
// actually read — see NEEDS.md in this dir for the two corrections).
//
// Field names use `chordwave`, never `mochy`. None of these types exist in the
// worktree yet; this file is their single home.

package com.music.bitchord.ui.mochy.viewmodels

import com.music.bitchord.ui.mochy.models.Song

/** How much room ChordWave takes, in the four parts the Storage page shows, and every drive it sits on. */
data class StorageUsage(
    /** Songs streamed and kept for next time, cover pictures, and other throwaway files. */
    val cache: Long,
    /** Downloaded songs. */
    val downloads: Long,
    /** The app's databases. */
    val database: Long,
    /** The app itself and its settings, everything not counted above. */
    val appData: Long,
    /** Total bytes ChordWave uses: cache + downloads + database + appData. */
    val chordwave: Long,
    /** The phone first, then any memory card holding downloads or the cache. */
    val drives: List<DriveUsage>,
    /** The internal drive; phone.total is the phone's total storage. */
    val phone: DriveUsage,
)

/** One drive ChordWave keeps things on, for its own overall card. */
data class DriveUsage(
    val label: String,
    val free: Long,
    val total: Long,
    val isPhone: Boolean,
    /** What ChordWave has on this drive. */
    val chordwave: Long = 0L,
) {
    /** Everything on the drive that isn't ChordWave and isn't free. */
    val othersUsed: Long get() = (total - free - chordwave).coerceAtLeast(0L)
}

/** A finished download, with what it costs on the phone. */
data class DownloadedSong(
    val song: Song,
    val bytes: Long,
    /** When the download finished, ms since 1970; 0 when unknown. */
    val downloadedAt: Long,
)

/** The two big folders ChordWave could keep on another drive. */
enum class StoredKind { Downloads, SongCache }

/** The drives one kind of folder could go to, where it is now, and how much a move would copy. */
data class PlaceChoice(
    val current: StoragePlace,
    val places: List<StoragePlace>,
    val bytesToMove: Long,
)

/** A place ChordWave can save onto: the phone, its own folder on a memory card, or a folder the user picked. */
data class StoragePlace(
    /** Identity, compared with ==: the folder's path on the phone or a card. */
    val root: String,
    val label: String,
    val free: Long,
    val total: Long,
    val isPhone: Boolean,
    /** Picked with the folder picker, which needs all-files access unless it's one of ChordWave's own folders. */
    val isFolder: Boolean,
)

/** A storage move in progress. There is no Done: the UI treats "not Working/Failed" as idle. */
sealed interface MoveStep {
    data object Idle : MoveStep
    data class Working(val kind: StoredKind, val progress: Float) : MoveStep // progress 0..1
    data class Failed(val kind: StoredKind) : MoveStep
}

/** How an export sorts the songs, and what travels with them. */
data class ExportChoice(val layout: ExportLayout, val covers: Boolean, val lyrics: Boolean)

enum class ExportLayout { Albums, Playlists }

/** An export in progress. */
sealed interface ExportStep {
    data object Idle : ExportStep
    data class Working(val done: Int, val total: Int) : ExportStep // done is the 0-based completed count
    data class Done(val songs: Int, val skipped: Int) : ExportStep
    data class Failed(val message: String) : ExportStep
}
