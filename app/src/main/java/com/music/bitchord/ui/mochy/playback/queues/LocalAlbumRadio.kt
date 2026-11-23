// Ported from Mochy Music (GPL-3.0): playback/queues/LocalAlbumRadio.kt
// Mochy's getInitialStatus is pure (the album's songs as MediaItems) and
// ports verbatim. There is no second page: like YouTubeAlbumRadio, the
// service's autoplay appends the radio tail once the queue runs low
// (PlaybackService.loadAutoplayForCurrentTrack), so nextPage stays empty
// and hasNextPage() is false — the queue ends with the album and the
// service continues it. A local album has no YouTube playlist to fetch a
// radio continuation from anyway.
// (phase2-detail NEEDS.md §(a) C8.)
package com.music.bitchord.ui.mochy.playback.queues

import androidx.media3.common.MediaItem
import com.music.bitchord.ui.mochy.extensions.toMediaItem
import com.music.bitchord.ui.mochy.models.AlbumWithSongs
import com.music.bitchord.ui.mochy.models.MediaMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LocalAlbumRadio(
    private val albumWithSongs: AlbumWithSongs,
    private val startIndex: Int = 0,
) : Queue {
    override val preloadItem: MediaMetadata? = null

    override suspend fun getInitialStatus(): Queue.Status = withContext(Dispatchers.IO) {
        Queue.Status(
            title = albumWithSongs.album.title,
            items = albumWithSongs.songs.map { it.toMediaItem() },
            mediaItemIndex = startIndex,
        )
    }

    override fun hasNextPage(): Boolean = false

    // No second page by design: the service's autoplay appends the radio
    // tail once the queue runs low (see the header).
    override suspend fun nextPage(): List<MediaItem> = emptyList()
}
