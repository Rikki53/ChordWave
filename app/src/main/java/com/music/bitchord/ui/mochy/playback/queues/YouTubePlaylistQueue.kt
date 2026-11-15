// Ported from Mochy Music (GPL-3.0): playback/queues/YouTubePlaylistQueue.kt
// (CW translation — round 1, 15 Nov 2026). Mochy's getInitialStatus falls
// back to YouTube.playlist() when no songs were handed in, and nextPage
// pages YouTube.playlistContinuation(); the ChordWave translation is
// YtMusicRepository.browseSongs() / moreSongs(). A queue built with
// initialSongs (every playlist-screen call site) resolves from those rows
// without a fetch.
package com.music.bitchord.ui.mochy.playback.queues

import androidx.media3.common.MediaItem
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.ui.mochy.extensions.toMediaItem
import com.music.bitchord.ui.mochy.extensions.toMochyQueueItem
import com.music.bitchord.ui.mochy.models.MediaMetadata
import com.music.bitchord.ui.mochy.models.innertube.SongItem
import com.music.bitchord.ui.mochy.viewmodels.browseIdForPlaylist
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

class YouTubePlaylistQueue(
    private val playlistId: String,
    private val playlistTitle: String? = null,
    private val initialSongs: List<SongItem> = emptyList(),
    private val initialContinuation: String? = null,
    private val startIndex: Int = 0,
    override val preloadItem: MediaMetadata? = null,
) : Queue {
    private var continuation: String? = initialContinuation

    override suspend fun getInitialStatus(): Queue.Status {
        if (initialSongs.isNotEmpty()) {
            val items = withContext(Dispatchers.Default) {
                initialSongs.map { it.toMediaItem() }
            }
            return Queue.Status(
                title = playlistTitle,
                items = items,
                mediaItemIndex = startIndex,
            )
        }
        // No rows handed in: fetch the playlist's first page. The browse
        // id is the playlist id with its VL prefix (a Mochy local id has
        // no server page at all). A failed or empty fetch resolves to an
        // empty status, which playQueue reports rather than playing
        // nothing in silence.
        val browseId = browseIdForPlaylist(playlistId)
        if (browseId == null) {
            Timber.w("YouTubePlaylistQueue: $playlistId names no server playlist")
            return Queue.Status(title = playlistTitle, items = emptyList(), mediaItemIndex = -1)
        }
        val page = YtMusicRepository.browseSongs(browseId).getOrNull()
        continuation = page?.continuation
        val songs = page?.songs.orEmpty()
        if (songs.isEmpty()) {
            Timber.w("YouTubePlaylistQueue: browse for $playlistId came back empty")
            return Queue.Status(title = playlistTitle, items = emptyList(), mediaItemIndex = -1)
        }
        val items = withContext(Dispatchers.Default) {
            songs.map { it.toMochyQueueItem() }
        }
        return Queue.Status(
            title = playlistTitle,
            items = items,
            mediaItemIndex = startIndex.coerceIn(0, items.lastIndex),
        )
    }

    override fun hasNextPage(): Boolean = continuation != null

    override suspend fun nextPage(): List<MediaItem> {
        val token = continuation ?: return emptyList()
        val page = YtMusicRepository.moreSongs(token).getOrNull()
        continuation = page?.continuation
        val songs = page?.songs.orEmpty()
        if (songs.isEmpty()) return emptyList()
        return withContext(Dispatchers.Default) {
            songs.map { it.toMochyQueueItem() }
        }
    }
}
