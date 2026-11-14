// Ported from Mochy Music (GPL-3.0): extensions/MediaItemExt.kt (PARTIAL —
// only the `metadata` extension, which Queue.kt's filter helpers need).
// Mochy stores its MediaMetadata as the MediaItem tag; the Modern player
// adapter (Phase 3) builds Mochy-mode MediaItems the same way, so this reads
// back whatever the adapter tagged. CW's own MediaItems carry no such tag
// and read as null. The rest of Mochy's MediaItemExt (toMediaItem builders,
// SongEdits integration) belongs to Phase 3.
package com.music.bitchord.ui.mochy.extensions

import androidx.media3.common.MediaItem
import com.music.bitchord.ui.mochy.models.MediaMetadata

val MediaItem.metadata: MediaMetadata?
    get() = localConfiguration?.tag as? MediaMetadata
