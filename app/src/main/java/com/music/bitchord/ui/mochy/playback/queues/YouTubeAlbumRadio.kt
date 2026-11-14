// Ported from Mochy Music (GPL-3.0): playback/queues/YouTubeAlbumRadio.kt
// (THIN PORT — construction only).
//
// Same treatment as the YouTubeQueue thin port in this directory: the
// constructor and endpoint are verbatim so YouTubeAlbumMenu can build it;
// getInitialStatus()/nextPage() are Phase 3 (Mochy's bodies call
// YouTube.albumSongs()/YouTube.next(); the CW translation is
// YtMusicRepository.browseSongs() + watchQueue()) and throw until then.

package com.music.bitchord.ui.mochy.playback.queues

import androidx.media3.common.MediaItem
import com.music.bitchord.ui.mochy.models.MediaMetadata
import com.music.bitchord.ui.mochy.models.innertube.WatchEndpoint

class YouTubeAlbumRadio(
    private var playlistId: String,
) : Queue {
    override val preloadItem: MediaMetadata? = null

    private val endpoint: WatchEndpoint
        get() = WatchEndpoint(
            playlistId = playlistId
        )

    private var albumSongCount = 0
    private var continuation: String? = null
    private var firstTimeLoaded: Boolean = false

    override suspend fun getInitialStatus(): Queue.Status {
        // Phase 3: YtMusicRepository.browseSongs(playlistId) for the album's
        // tracks, then watchQueue(endpoint) for the radio tail.
        throw UnsupportedOperationException(
            "YouTubeAlbumRadio.getInitialStatus needs Phase 3 (YtMusicRepository queue resolution)"
        )
    }

    override fun hasNextPage(): Boolean = !firstTimeLoaded || continuation != null

    override suspend fun nextPage(): List<MediaItem> {
        // Phase 3: follow `continuation` via YtMusicRepository.
        throw UnsupportedOperationException(
            "YouTubeAlbumRadio.nextPage needs Phase 3 (YtMusicRepository queue resolution)"
        )
    }
}
