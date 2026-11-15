// Ported from Mochy Music (GPL-3.0): playback/queues/YouTubeAlbumRadio.kt
// (CW translation — round 1, 15 Nov 2026). Mochy's bodies call
// YouTube.albumSongs() / YouTube.next(); the ChordWave translation is
// YtMusicRepository.watchQueue() on the album's playlist, which returns the
// album's tracks in order as a watch queue. There is no second page:
// watchQueue returns the whole album, and the service's autoplay appends
// the radio tail once it runs low.

package com.music.bitchord.ui.mochy.playback.queues

import androidx.media3.common.MediaItem
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.model.WatchTarget
import com.music.bitchord.ui.mochy.extensions.toMochyQueueItem
import com.music.bitchord.ui.mochy.models.MediaMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

class YouTubeAlbumRadio(
    private var playlistId: String,
) : Queue {
    override val preloadItem: MediaMetadata? = null

    private var continuation: String? = null
    private var firstTimeLoaded: Boolean = false

    override suspend fun getInitialStatus(): Queue.Status {
        val songs = YtMusicRepository.watchQueue(
            WatchTarget(playlistId = playlistId),
        ).getOrNull().orEmpty()
        firstTimeLoaded = true
        if (songs.isEmpty()) {
            Timber.w("YouTubeAlbumRadio: watch queue for $playlistId came back empty")
            return Queue.Status(title = null, items = emptyList(), mediaItemIndex = -1)
        }
        val items = withContext(Dispatchers.Default) {
            songs.map { it.toMochyQueueItem() }
        }
        return Queue.Status(title = null, items = items, mediaItemIndex = 0)
    }

    override fun hasNextPage(): Boolean = !firstTimeLoaded || continuation != null

    override suspend fun nextPage(): List<MediaItem> = emptyList()
}
