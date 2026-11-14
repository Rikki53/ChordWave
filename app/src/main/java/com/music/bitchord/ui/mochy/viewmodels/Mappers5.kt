// Mappers5.kt — the extra mappings the Library adapters need, kept here so
// the staged Mappers.kt / Mappers2.kt / Mappers3.kt / Mappers4.kt stay
// untouched. One direction only: ChordWave -> Mochy, same as Mappers.kt.
//
//   ShelfItem -> Playlist  (library shelves -> LocalItems)
//
// NOTE (coordinator dedup, 15 Nov): ShelfItem.toMochyAlbum,
// CwSong.toDownloadedMochySong and CwSong.toLocalFileMochySong lived here
// AND in Mappers6/Mappers7 with the same signatures — duplicate top-level
// declarations in one package break the build. Single homes now:
// toMochyAlbum in Mappers6 (superset: year + artists), toDownloadedMochySong
// and toLocalFileMochySong in Mappers7 (toLocalFile merged: added date +
// local artists/album flags). Same package, so all callers resolve unchanged.

package com.music.bitchord.ui.mochy.viewmodels

import com.music.bitchord.data.model.ShelfItem
import com.music.bitchord.ui.mochy.models.Playlist
import com.music.bitchord.ui.mochy.models.PlaylistEntity

/**
 * A library Playlists-shelf card as a Mochy Playlist. Null when the card
 * carries no browseId. The id IS the browseId (VL...), which is what
 * AppSettings.pinnedPlaylists holds and what the detail host browses by.
 */
fun ShelfItem.toMochyPlaylist(): Playlist? {
    val browseId = browseId ?: return null
    return Playlist(
        playlist = PlaylistEntity(
            id = browseId,
            name = title,
            browseId = browseId,
            thumbnailUrl = thumbnailUrl,
        ),
        songCount = 0,
        songThumbnails = emptyList(),
    )
}
