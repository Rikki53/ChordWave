package com.music.bitchord.download

import java.util.Locale

/**
 * Which of the downloads already on this device still have to move out of
 * app-private storage, and how — the pure half of the one-time migration in
 * [Downloads.migrateLegacyDownloads], kept apart from it so the rules can be
 * tested on paths alone.
 *
 * The move exists because of a default that flipped. Before 1.4.1-dev1 a
 * download landed in this app's private `downloads` folder, where no file
 * manager and no other player can reach it; since then [DownloadStore] files
 * new downloads in Music/ChordWave by default. The files saved under the old
 * default did not move when the default did — this is what moves them, once.
 *
 * The rules that make it safe to run more than once, which is the whole
 * design:
 *
 *  - **Only the private folder is a candidate.** A recorded file qualifies
 *    by sitting under the app-private downloads directory, and by nothing
 *    else. The moment a file has been published its record points at a
 *    `content://` uri (or, before API 29, at a path in Music) — neither is
 *    under that directory, so a re-run finds nothing to do. The one-time
 *    flag in settings is only a shortcut past the scan; the truth lives
 *    here, in where each record points.
 *  - **A name already in Music is adopted, not published.** Publishing is
 *    the same [DownloadStore] path an exported download takes, and the
 *    media store answers a duplicate name by quietly minting `… (1)`. So
 *    the plan asks after each name first: one that is already filed is
 *    [Action.ADOPT_EXISTING], which closes the hole where a crash between
 *    publishing a file and recording that it was published would otherwise
 *    duplicate it on the next launch.
 *  - **Offline packages never move.** An HLS/DASH download is a directory
 *    holding a `playlist.m3u8` and its segments — a package only this app
 *    can play, private by design (see [OfflineHls]). It does not even live
 *    under the downloads folder, and it is refused here by name as well, so
 *    no future layout change can sweep one up by accident.
 *  - **What Android won't file stays private.** The media store's audio
 *    collection refuses some of what a download can be — WebM above all
 *    (see [Downloads]'s route notes). Those files have no [exportMimeType]
 *    and are simply not planned; leaving one behind is the correct outcome,
 *    not a failure.
 */
object LegacyDownloadMigration {

    /** One row of the download record, with its file path decoded out of its uri. */
    data class Entry(
        val videoId: String,
        val uriString: String,
        /** The decoded filesystem path when [uriString] is a `file://` uri, else null. */
        val filePath: String?,
    )

    /** What one surviving private file needs. */
    enum class Action {
        /** Copy its bytes into Music/ChordWave through [DownloadStore]. */
        PUBLISH,

        /** A copy is already in Music/ChordWave; point the record at that one instead. */
        ADOPT_EXISTING,
    }

    /** One file to move, with every id that names it. */
    data class Planned(
        val uriString: String,
        val filePath: String,
        val fileName: String,
        val mimeType: String,
        /** Every recorded id for this one file — a track and its catalogue replacement share one. */
        val videoIds: List<String>,
        val action: Action,
    )

    /** What one run of the migration did; see [Downloads.migrateLegacyDownloads]. */
    data class Outcome(val moved: Int, val failed: Int) {
        /** True when nothing that was planned was left behind. */
        val settled: Boolean get() = failed == 0
    }

    /** Whether [filePath] is one of the offline HLS/DASH packages rather than a file. */
    fun isOfflinePackage(filePath: String?): Boolean {
        if (filePath == null) return false
        return filePath.substringAfterLast('/') == "playlist.m3u8" ||
            "/offline-hls/" in filePath
    }

    /**
     * Whether [filePath] sits inside the app-private downloads directory
     * [privateDirPath]. A directory boundary, not a substring: a sibling
     * called `downloads-backup` is not this folder.
     */
    fun isPrivateDownload(filePath: String?, privateDirPath: String): Boolean {
        if (filePath == null) return false
        val dir = privateDirPath.trimEnd('/')
        if (dir.isEmpty()) return false
        return filePath.startsWith("$dir/")
    }

    /**
     * The MIME type the media store's audio collection is told for a file
     * ending in [extension], or null when it will not file one — WebM and
     * Opus above all, and anything this app never downloads as a plain file.
     * The values are the ones [Downloader.Container] and [DownloadStore]
     * already file downloads under.
     */
    fun exportMimeType(extension: String): String? =
        when (extension.lowercase(Locale.ROOT)) {
            "flac" -> "audio/flac"
            "wav" -> "audio/x-wav"
            "m4a", "mp4" -> "audio/mp4"
            "mp3" -> "audio/mpeg"
            "aac" -> "audio/aac"
            "ogg", "oga" -> "audio/ogg"
            else -> null
        }

    /**
     * The files to move, in record order, one per distinct file.
     *
     * @param alreadyExported whether a file of the given name is already
     *   filed in Music/ChordWave — asked per surviving name, never for one
     *   the other rules have already refused.
     */
    fun plan(
        entries: List<Entry>,
        privateDirPath: String,
        alreadyExported: (String) -> Boolean = { false },
    ): List<Planned> {
        // One file answers to two ids when a music video was swapped for its
        // catalogue track on the way down — group by the uri they share so
        // the file is published once and every id is repointed together.
        val byUri = LinkedHashMap<String, MutableList<Entry>>()
        for (entry in entries) byUri.getOrPut(entry.uriString) { mutableListOf() }.add(entry)
        return byUri.values.mapNotNull { group ->
            val first = group.first()
            val path = first.filePath ?: return@mapNotNull null
            if (!isPrivateDownload(path, privateDirPath)) return@mapNotNull null
            if (isOfflinePackage(path)) return@mapNotNull null
            val fileName = path.substringAfterLast('/')
            if (fileName.isEmpty()) return@mapNotNull null
            val mimeType = exportMimeType(fileName.substringAfterLast('.', ""))
                ?: return@mapNotNull null
            Planned(
                uriString = first.uriString,
                filePath = path,
                fileName = fileName,
                mimeType = mimeType,
                videoIds = group.map { it.videoId }.distinct(),
                action = if (alreadyExported(fileName)) {
                    Action.ADOPT_EXISTING
                } else {
                    Action.PUBLISH
                },
            )
        }
    }
}
