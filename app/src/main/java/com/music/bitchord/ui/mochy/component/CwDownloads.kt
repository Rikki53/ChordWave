// CwDownloads.kt — coordinator-side bridge (not a Mochy port).
//
// Mochy's items and menus read download state from LocalDownloadUtil
// (media3 DownloadManager state per song id). ChordWave has no such util:
// downloads live in com.music.bitchord.download.Downloads
// (saved: StateFlow<Map<videoId, uri>>, active: StateFlow<Map<videoId,
// DownloadState>>). These helpers translate the CW flows back into the
// media3 Download.STATE_* ints the ported Mochy UI already speaks
// (SheetDownloadCard, Icon.Download), so no ported call site has to learn
// CW's download model. Downloading/queued both map to STATE_DOWNLOADING —
// the Mochy UI draws a spinner for either — and a failed download reads as
// "nothing", the same as never downloaded.
//
// Also here: Mochy -> CW Song builders, because Downloads.enqueue() takes
// a CW Song. Only the four required fields plus the album/video flags are
// filled; Downloads resolves the stream itself at enqueue time.

package com.music.bitchord.ui.mochy.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.media3.exoplayer.offline.Download
import com.music.bitchord.data.model.Song as CwSong
import com.music.bitchord.download.DownloadState
import com.music.bitchord.download.Downloads
import com.music.bitchord.ui.mochy.models.MediaMetadata
import com.music.bitchord.ui.mochy.models.Song
import com.music.bitchord.ui.mochy.models.innertube.SongItem

private fun stateOf(id: String, saved: Map<String, String>, active: Map<String, DownloadState>): Int? =
    when {
        id in saved -> Download.STATE_COMPLETED
        active[id] is DownloadState.Running ||
            active[id] == DownloadState.Queued ||
            active[id] == DownloadState.WaitingForWifi -> Download.STATE_DOWNLOADING
        else -> null
    }

/** media3 download state for one track, or null when it was never downloaded. */
@Composable
fun downloadStateFor(songId: String): Int? {
    val saved by Downloads.saved.collectAsState()
    val active by Downloads.active.collectAsState()
    return stateOf(songId, saved, active)
}

/**
 * Aggregate media3 state for a whole listing, following Mochy's album /
 * playlist rule: COMPLETED when every track is saved, DOWNLOADING when
 * every track is at least queued, STOPPED otherwise (including an empty
 * listing, which has nothing to download).
 */
@Composable
fun downloadStateForAll(songIds: List<String>): Int {
    val saved by Downloads.saved.collectAsState()
    val active by Downloads.active.collectAsState()
    if (songIds.isEmpty()) return Download.STATE_STOPPED
    return when {
        songIds.all { stateOf(it, saved, active) == Download.STATE_COMPLETED } ->
            Download.STATE_COMPLETED
        songIds.all { stateOf(it, saved, active) != null } ->
            Download.STATE_DOWNLOADING
        else -> Download.STATE_STOPPED
    }
}

/** A Mochy library song as the CW Song Downloads.enqueue() needs. */
fun Song.toCwSong(): CwSong = CwSong(
    videoId = song.id,
    title = song.title,
    artist = artists.joinToString { it.name },
    thumbnailUrl = song.thumbnailUrl,
    albumId = song.albumId,
    albumName = song.albumName,
    isVideo = song.isVideo,
)

/** A YouTube search/feed song as the CW Song Downloads.enqueue() needs. */
fun SongItem.toCwSong(): CwSong = CwSong(
    videoId = id,
    title = title,
    artist = artists.joinToString { it.name },
    thumbnailUrl = thumbnail,
    albumId = album?.id,
    albumName = album?.name,
    isVideo = isVideoSong,
)

/** Player metadata as the CW Song Downloads.enqueue() needs. */
fun MediaMetadata.toCwSong(): CwSong = CwSong(
    videoId = id,
    title = title,
    artist = artists.joinToString { it.name },
    thumbnailUrl = thumbnailUrl,
    albumId = album?.id,
    albumName = album?.title,
    isVideo = isVideoSong,
)
