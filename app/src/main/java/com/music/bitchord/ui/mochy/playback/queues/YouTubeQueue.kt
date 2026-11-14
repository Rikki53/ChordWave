// Ported from Mochy Music (GPL-3.0): playback/queues/YouTubeQueue.kt
// (SHAPE ONLY — the fetch methods are STUBBED). Mochy's getInitialStatus /
// nextPage call its own YouTube.next() client (NextResult, NotRecommended),
// which is engine, not UI. Phase 3 replaces the stubs with the CW
// translation (YtMusicRepository.radio() + watchQueue); until then queues
// from Home taps resolve to an empty status and MochyPlayerConnection.
// playQueue is itself a stub, so nothing plays yet — that is the
// skeleton's documented state, not a port gap. Constructor, radio()
// companion, isMix() and radioEndpoint() are verbatim.
package com.music.bitchord.ui.mochy.playback.queues

import androidx.media3.common.MediaItem
import com.music.bitchord.ui.mochy.models.MediaMetadata
import com.music.bitchord.ui.mochy.models.innertube.WatchEndpoint
import timber.log.Timber

class YouTubeQueue(
    private var endpoint: WatchEndpoint,
    override val preloadItem: MediaMetadata? = null,
) : Queue {
    private var continuation: String? = null

    // TODO(Phase 3): fetch via CW YtMusicRepository.radio(endpoint.videoId)
    // / watchQueue() and map CW Song -> MediaItem (tagged with
    // toMochyMetadata()). Keep Mochy's retry + dead-end-radio behavior.
    override suspend fun getInitialStatus(): Queue.Status {
        Timber.w("YouTubeQueue.getInitialStatus is not wired yet (Phase 3)")
        return Queue.Status(title = null, items = emptyList(), mediaItemIndex = -1)
    }

    override fun hasNextPage(): Boolean = continuation != null

    // TODO(Phase 3): same CW translation as getInitialStatus, paged.
    override suspend fun nextPage(): List<MediaItem> {
        Timber.w("YouTubeQueue.nextPage is not wired yet (Phase 3)")
        return emptyList()
    }

    // a song on its own or a radio is youtube picking for you, curated (RDCLAK) and normal playlists and albums play as they are
    private fun isMix(): Boolean {
        val playlistId = endpoint.playlistId ?: return true
        return playlistId.startsWith("RD") && !playlistId.startsWith("RDCLAK")
    }

    private fun radioEndpoint(): WatchEndpoint? {
        val videoId = endpoint.videoId ?: preloadItem?.id ?: return null
        if (endpoint.playlistId != null) return null
        return WatchEndpoint(videoId = videoId, playlistId = "RDAMVM$videoId")
    }

    companion object {
        fun radio(song: MediaMetadata): YouTubeQueue {
            return YouTubeQueue(
                WatchEndpoint(videoId = song.id),
                song
            )
        }
    }
}
