// Ported from Mochy Music (GPL-3.0): extensions/MediaItemExt.kt (PARTIAL —
// the `metadata` extension, which Queue.kt's filter helpers need, plus
// MediaMetadata.toMediaItem(), which the Home hero needs). Mochy stores its
// MediaMetadata as the MediaItem tag; the Modern player adapter (Phase 3)
// builds Mochy-mode MediaItems the same way, so this reads back whatever
// the adapter tagged. CW's own MediaItems carry no such tag and read as
// null. The Song.toMediaItem() builder + SongEdits integration belong to
// Phase 3 (SongEdits has no CW equivalent; toMediaItem() here skips it).
package com.music.bitchord.ui.mochy.extensions

import android.os.Bundle
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata.MEDIA_TYPE_MUSIC
import com.music.bitchord.ui.mochy.models.MediaMetadata

val MediaItem.metadata: MediaMetadata?
    get() = localConfiguration?.tag as? MediaMetadata

fun MediaMetadata.toMediaItem(): MediaItem = buildMediaItem()

private fun MediaMetadata.buildMediaItem() = MediaItem.Builder()
    .setMediaId(id)
    .setUri(localUri ?: id)
    .setCustomCacheKey(id)
    .setTag(this)
    .setMediaMetadata(
        androidx.media3.common.MediaMetadata.Builder()
            .setTitle(title)
            .setSubtitle(artists.joinToString { it.name })
            .setArtist(artists.joinToString { it.name })
            .setArtworkUri(thumbnailUrl?.toUri())
            .setAlbumTitle(album?.title)
            .setAlbumArtist(artists.firstOrNull()?.name)
            .setDisplayTitle(title)
            .setMediaType(MEDIA_TYPE_MUSIC)
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .setExtras(Bundle().apply {
                thumbnailUrl?.let { putString("artwork_uri", it) }
            })
            .build()
    )
    .build()
