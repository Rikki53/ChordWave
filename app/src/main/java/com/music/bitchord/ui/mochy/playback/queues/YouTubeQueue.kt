// Ported from Mochy Music (GPL-3.0): playback/queues/YouTubeQueue.kt
// (CW translation — round 1, 15 Nov 2026). Mochy's getInitialStatus /
// nextPage call its own YouTube.next() client (NextResult, NotRecommended).
// The ChordWave translation fetches the same queues through
// YtMusicRepository: watchQueue() for a playlist/mix endpoint, radio() for
// a lone song (the tapped song first, its station after it). Constructor
// and the radio() companion are verbatim.
package com.music.bitchord.ui.mochy.playback.queues

import androidx.media3.common.MediaItem
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.model.WatchTarget
import com.music.bitchord.ui.mochy.extensions.toMediaItem
import com.music.bitchord.ui.mochy.extensions.toMochyQueueItem
import com.music.bitchord.ui.mochy.models.MediaMetadata
import com.music.bitchord.ui.mochy.models.innertube.WatchEndpoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

class YouTubeQueue(
    private var endpoint: WatchEndpoint,
    override val preloadItem: MediaMetadata? = null,
) : Queue {
    private var continuation: String? = null

    /**
     * A lone tapped song: [getInitialStatus] leads with the preload song
     * itself and only fetches its radio behind it, so the connection can
     * start the song at once and append the radio when it lands (batch 48)
     * instead of holding the tap on a network round-trip. A playlist/mix
     * endpoint is not this: its queue order is the point, and the tapped
     * song sits wherever YouTube put it in that order.
     */
    val startsOnSeed: Boolean get() = endpoint.playlistId == null && preloadItem != null

    override suspend fun getInitialStatus(): Queue.Status {
        val videoId = endpoint.videoId ?: preloadItem?.id
        val playlistId = endpoint.playlistId
        if (playlistId != null) {
            // A playlist, a mix, an album or artist radio: YouTube's own
            // queue for the endpoint, in the order it gives it. The seed
            // track leads where it is in the list; otherwise the endpoint's
            // index does, and the list head is the fallback.
            val songs = YtMusicRepository.watchQueue(
                WatchTarget(
                    playlistId = playlistId,
                    videoId = videoId,
                    params = endpoint.params,
                ),
            ).getOrNull().orEmpty()
            if (songs.isEmpty()) {
                Timber.w("YouTubeQueue: watch queue for $playlistId came back empty")
                return Queue.Status(title = null, items = emptyList(), mediaItemIndex = -1)
            }
            val startIndex = endpoint.index?.takeIf { it in songs.indices }
                ?: videoId?.let { id -> songs.indexOfFirst { it.videoId == id }.takeIf { it >= 0 } }
                ?: 0
            val items = withContext(Dispatchers.Default) {
                songs.map { it.toMochyQueueItem() }
            }
            return Queue.Status(title = null, items = items, mediaItemIndex = startIndex)
        }
        if (videoId == null) {
            Timber.w("YouTubeQueue: no video id and no playlist to play")
            return Queue.Status(title = null, items = emptyList(), mediaItemIndex = -1)
        }
        // A lone song: the tapped song itself, then its radio. The radio
        // fetch failing must not lose the tap — the song still plays on
        // its own, and the service's autoplay appends a tail when it runs
        // low. A station started from a video stays videos (radio's
        // preferVideo), the way Mochy's next() kept it.
        val radio = YtMusicRepository.radio(
            videoId,
            preferVideo = preloadItem?.isVideoSong == true,
        ).getOrNull().orEmpty()
        val items = withContext(Dispatchers.Default) {
            buildList {
                val seed = preloadItem
                if (seed != null) {
                    add(seed.toMediaItem())
                    addAll(
                        radio.filterNot { it.videoId == videoId }
                            .map { it.toMochyQueueItem() },
                    )
                } else {
                    // No preload: radio() returns the seed first, so the
                    // list already starts on the tapped song.
                    addAll(radio.map { it.toMochyQueueItem() })
                }
            }
        }
        if (items.isEmpty()) {
            Timber.w("YouTubeQueue: nothing to play for $videoId (no preload, empty radio)")
            return Queue.Status(title = null, items = emptyList(), mediaItemIndex = -1)
        }
        return Queue.Status(title = null, items = items, mediaItemIndex = 0)
    }

    // No second page: neither the CW radio nor the watch queue has a
    // continuation, and the service's autoplay appends the tail once the
    // queue runs low — the same continuity Mochy's nextPage gave, from the
    // engine instead of the queue.
    override fun hasNextPage(): Boolean = continuation != null

    override suspend fun nextPage(): List<MediaItem> = emptyList()

    companion object {
        fun radio(song: MediaMetadata): YouTubeQueue {
            return YouTubeQueue(
                WatchEndpoint(videoId = song.id),
                song
            )
        }
    }
}
