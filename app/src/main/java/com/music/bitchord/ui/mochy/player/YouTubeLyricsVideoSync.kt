// Ported from Mochy Music (GPL-3.0): ui/player/YouTubeLyricsVideoSync.kt
// STUB — the caption engine (VideoCaptionSource, CaptionAligner) is NOT ported (see
// NEEDS.md §(b)). buildLyricVideoTimeline always returns null, so the video mode falls
// back to unaligned playback; the timeline class and its cache keep their Mochy shapes so
// the video chunk's call sites compile unchanged.
//
// The timeline is backed by a SongVideoMap (pure model, staged in ui/mochy/lyrics): when
// the coordinator wires a real aligner, its only job is to produce a SongVideoMap for a
// (videoId, lyrics) pair and construct the timeline from it.

package com.music.bitchord.ui.mochy.player

import com.music.bitchord.ui.mochy.lyrics.SongVideoMap
import timber.log.Timber

// where on a separately found music video the playing song is, from lining the song's lyrics up with the video's captions
class LyricVideoTimeline internal constructor(private val map: SongVideoMap) {
    fun videoMsAt(songLyricMs: Long): Long = map.videoMsAt(songLyricMs)
}

// null when the video has no captions, or too few of the lyrics could be found in them to trust.
// TODO(lyrics-engine): always null until the caption aligner lands.
suspend fun buildLyricVideoTimeline(videoId: String, songLyricsRaw: String): LyricVideoTimeline? {
    Timber.w("buildLyricVideoTimeline is not wired yet (Phase 3 lyrics engine)")
    return null
}

object LyricVideoTimelineCache {
    private const val maxSize = 32
    private val map = LinkedHashMap<String, LyricVideoTimeline?>(maxSize, 0.75f, true)

    @Synchronized
    fun contains(key: String): Boolean = map.containsKey(key)

    @Synchronized
    fun get(key: String): LyricVideoTimeline? = map[key]

    @Synchronized
    fun put(key: String, timeline: LyricVideoTimeline?) {
        map[key] = timeline
        if (map.size > maxSize) map.remove(map.keys.first())
    }
}
