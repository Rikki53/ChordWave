// Ported from Mochy Music (GPL-3.0): extensions/MediaItemExt.kt (the
// toMediaItem builders + autoplay flags only).
//
// The staged phase1-player MediaItemExt.kt already carries the `metadata`
// extension and reserves this file's name for Phase 3, so the builders live
// here under a different filename in the SAME package
// (com.music.bitchord.ui.mochy.extensions) — same package, no clash, and
// Phase 3 can fold them into MediaItemExt.kt when it lands the player
// adapter. Callers import com.music.bitchord.ui.mochy.extensions.toMediaItem
// either way.
//
// The MediaMetadata.toMediaItem() overload lives in the already-pushed
// phase1-player MediaItemExt.kt (same package); this file only adds the
// Song / SongItem overloads + the autoplay flags. (Coordinator note: an
// earlier draft of this file duplicated that overload — removed at
// integration, it would not compile twice in one package.)

package com.music.bitchord.ui.mochy.extensions

import android.os.Bundle
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata.MEDIA_TYPE_MUSIC
import com.music.bitchord.ui.mochy.models.Song
import com.music.bitchord.ui.mochy.models.innertube.SongItem
import com.music.bitchord.ui.mochy.models.toMediaMetadata

fun Song.toMediaItem() = MediaItem.Builder()
    .setMediaId(song.id)
    .setUri(if (song.isLocal && song.localUri != null) song.localUri else song.id)
    .setCustomCacheKey(song.id)
    .setTag(toMediaMetadata())
    .setMediaMetadata(
        androidx.media3.common.MediaMetadata.Builder()
            .setTitle(song.title)
            .setSubtitle(artists.joinToString { it.name })
            .setArtist(artists.joinToString { it.name })
            .setArtworkUri(song.thumbnailUrl?.toUri())
            .setAlbumTitle(song.albumName)
            .setAlbumArtist(artists.firstOrNull()?.name)
            .setDisplayTitle(song.title)
            .setMediaType(MEDIA_TYPE_MUSIC)
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .setExtras(Bundle().apply {
                putString("artwork_uri", song.thumbnailUrl)
            })
            .build()
    )
    .build()

// built through the metadata so a youtube result picks up the song's hand edits too
fun SongItem.toMediaItem() = toMediaMetadata().toMediaItem()

/**
 * Flags a song the infinite queue lined up on its own (the queue's "Autoplay" section), as
 * opposed to one the user queued. Kept in the media metadata extras so it travels with the item
 * through the player's timeline.
 */
private const val AutoplayExtra = "mochy.autoplay"

val MediaItem.isAutoplay: Boolean
    get() = mediaMetadata.extras?.getBoolean(AutoplayExtra, false) == true

fun MediaItem.withAutoplay(autoplay: Boolean): MediaItem {
    if (isAutoplay == autoplay) return this
    val extras = Bundle(mediaMetadata.extras ?: Bundle()).apply { putBoolean(AutoplayExtra, autoplay) }
    return buildUpon()
        .setMediaMetadata(mediaMetadata.buildUpon().setExtras(extras).build())
        .build()
}
