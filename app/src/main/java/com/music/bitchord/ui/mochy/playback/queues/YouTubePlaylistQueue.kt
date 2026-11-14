// Ported from Mochy Music (GPL-3.0): playback/queues/YouTubePlaylistQueue.kt
// (SHAPE + initial-songs branch only — the network branches are STUBBED).
// Mochy's getInitialStatus falls back to YouTube.playlist() when no songs
// were handed in, and nextPage pages YouTube.playlistContinuation(); both
// are engine calls. Phase 3 replaces the stubs with the CW translation
// (YtMusicRepository.browseSongs / moreSongs + CW Song -> MediaItem); until
// then a queue built with initialSongs (every playlist-screen call site)
// resolves fully, and an empty one resolves to an empty status — the same
// stub pattern as the staged YouTubeQueue. Constructor, hasNextPage and the
// retry constants' removal are noted in NEEDS.md. MochyPlayerConnection.
// playQueue is itself a stub, so nothing plays yet either way.
package com.music.bitchord.ui.mochy.playback.queues

import androidx.media3.common.MediaItem
import com.music.bitchord.ui.mochy.extensions.toMediaItem
import com.music.bitchord.ui.mochy.models.MediaMetadata
import com.music.bitchord.ui.mochy.models.innertube.SongItem
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
            return Queue.Status(
                title = playlistTitle,
                items = initialSongs.map { it.toMediaItem() },
                mediaItemIndex = startIndex,
            )
        }
        // TODO(Phase 3): fetch via CW YtMusicRepository.browseSongs(playlistId)
        // and map CW Song -> MediaItem (tagged with toMochyMetadata()); keep
        // Mochy's songsContinuation for the next pages.
        Timber.w("YouTubePlaylistQueue.getInitialStatus is not wired yet (Phase 3)")
        return Queue.Status(title = playlistTitle, items = emptyList(), mediaItemIndex = -1)
    }

    override fun hasNextPage(): Boolean = continuation != null

    // TODO(Phase 3): same CW translation as getInitialStatus, paged via
    // YtMusicRepository.moreSongs(continuation); keep Mochy's retry +
    // give-up-after-3-failed-pages behaviour.
    override suspend fun nextPage(): List<MediaItem> {
        Timber.w("YouTubePlaylistQueue.nextPage is not wired yet (Phase 3)")
        return emptyList()
    }
}
