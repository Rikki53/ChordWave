// Ported from Mochy Music (GPL-3.0): extensions/MediaItemExt.kt (PARTIAL —
// the `metadata` extension, which Queue.kt's filter helpers need, plus
// MediaMetadata.toMediaItem(), which the Home hero needs). Mochy stores its
// MediaMetadata as the MediaItem tag; the Modern player adapter (Phase 3)
// builds Mochy-mode MediaItems the same way, so this reads back whatever
// the adapter tagged. CW's own MediaItems carry no such tag and read as
// null. SongEdits integration landed in Phase 3 (SongEdits is now ported,
// in-memory for the session).
package com.music.bitchord.ui.mochy.extensions

import androidx.media3.common.MediaItem
import com.music.bitchord.playback.toMediaItem
import com.music.bitchord.ui.mochy.component.toCwSong
import com.music.bitchord.ui.mochy.models.MediaMetadata
import com.music.bitchord.ui.mochy.songedit.SongEdits

val MediaItem.metadata: MediaMetadata?
    get() = localConfiguration?.tag as? MediaMetadata

fun MediaMetadata.toMediaItem(): MediaItem = SongEdits.applyTo(this).buildMediaItem()

/**
 * The item ChordWave's engine actually plays: CW's own `Song.toMediaItem`
 * (the `bitchord://watch?v=…` URI the service's stream resolver answers, the
 * title/artist/runtime match tail, the download substitution and the full
 * metadata + extras round-trip), tagged with this Mochy metadata so the
 * queue filters ([com.music.bitchord.ui.mochy.playback.queues.filterExplicit],
 * `filterVideoSongs`) and the swipe actions keep reading it.
 *
 * The old builder put the bare video id in as the URI. The resolver only
 * answers URIs carrying a `v` query parameter (PlaybackService's
 * streamResolver returns anything else unresolved), so an uncached track
 * built that way errored on every attempt and only a cache hit played —
 * until the cached bytes ran out, after which the same error left the
 * player stuck with nothing on screen saying why. (Round 1, 15 Nov 2026.)
 */
private fun MediaMetadata.buildMediaItem(): MediaItem {
    val metadata = this
    return toCwSong()
        .toMediaItem()
        .buildUpon()
        .setTag(metadata)
        .setCustomCacheKey(id)
        .build()
}
