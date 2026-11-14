// Ported from Mochy Music (GPL-3.0): playback/queues/LocalAlbumRadio.kt
// (SHAPE ONLY — nextPage is STUBBED). Mochy's getInitialStatus is pure
// (the album's songs as MediaItems) and ports verbatim; its nextPage
// continues with YouTube.next on the album's playlist, which is engine
// work — stubbed like the staged YouTubeQueue until Phase 3 wires the CW
// translation (YtMusicRepository.watchQueue / radio). hasNextPage() is
// false until then, so the queue ends with the album instead of promising
// a continuation it cannot fetch.
// (phase2-detail NEEDS.md §(a) C8.)
package com.music.bitchord.ui.mochy.playback.queues

import androidx.media3.common.MediaItem
import com.music.bitchord.ui.mochy.extensions.toMediaItem
import com.music.bitchord.ui.mochy.models.AlbumWithSongs
import com.music.bitchord.ui.mochy.models.MediaMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

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

    // TODO(Phase 3): fetch the album's radio continuation via CW
    // YtMusicRepository (watchQueue on the album's playlist, skipping the
    // album's own tracks like Mochy did) and map CW Song -> MediaItem.
    override suspend fun nextPage(): List<MediaItem> {
        Timber.w("LocalAlbumRadio.nextPage is not wired yet (Phase 3)")
        return emptyList()
    }
}
