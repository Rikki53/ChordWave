// Batch 55b: Mochy's sorted library reads (DatabaseDao.artists/albums/
// playlists with a sort type), as app-side extensions because the sort
// enums live in the app module while the DAO lives in :mochyengine.
// CREATE_DATE uses Mochy's own rowId queries; the other orders sort the
// same rows in memory, ascending then reversed for descending, as Mochy
// does. The module pages are the only caller (CREATE_DATE, descending).

package com.music.bitchord.ui.mochy.models

import com.mochyy.mochymusic.db.DatabaseDao
import com.mochyy.mochymusic.db.entities.Album
import com.mochyy.mochymusic.db.entities.Artist
import com.mochyy.mochymusic.db.entities.Playlist
import com.music.bitchord.ui.mochy.constants.AlbumSortType
import com.music.bitchord.ui.mochy.constants.ArtistSortType
import com.music.bitchord.ui.mochy.constants.PlaylistSortType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.text.Collator
import java.util.Locale

fun DatabaseDao.artists(sortType: ArtistSortType, descending: Boolean): Flow<List<Artist>> =
    artistsByCreateDateAsc().map { rows ->
        val visible = rows.filter { it.artist.isYouTubeArtist || it.artist.isLocal }
        val sorted = when (sortType) {
            ArtistSortType.CREATE_DATE -> visible
            ArtistSortType.NAME -> visible.sortedBy { it.artist.name.lowercase() }
            ArtistSortType.SONG_COUNT -> visible.sortedBy { it.songCount }
            ArtistSortType.PLAY_TIME -> visible.sortedBy { it.timeListened ?: 0 }
        }
        if (descending) sorted.reversed() else sorted
    }

fun DatabaseDao.albums(sortType: AlbumSortType, descending: Boolean): Flow<List<Album>> =
    albumsByCreateDateAsc().map { rows ->
        val sorted = when (sortType) {
            AlbumSortType.CREATE_DATE -> rows
            AlbumSortType.NAME -> {
                val collator = Collator.getInstance(Locale.getDefault())
                collator.strength = Collator.PRIMARY
                rows.sortedWith(compareBy(collator) { it.album.title })
            }
            AlbumSortType.ARTIST -> rows.sortedBy { it.artists.firstOrNull()?.name?.lowercase().orEmpty() }
            AlbumSortType.YEAR -> rows.sortedBy { it.album.year ?: 0 }
            AlbumSortType.SONG_COUNT -> rows.sortedBy { it.album.songCount }
            AlbumSortType.LENGTH -> rows.sortedBy { it.album.duration }
        }
        if (descending) sorted.reversed() else sorted
    }

fun DatabaseDao.playlists(sortType: PlaylistSortType, descending: Boolean): Flow<List<Playlist>> =
    playlistsByCreateDateAsc().map { rows ->
        val sorted = when (sortType) {
            PlaylistSortType.CREATE_DATE -> rows
            PlaylistSortType.NAME -> {
                val collator = Collator.getInstance(Locale.getDefault())
                collator.strength = Collator.PRIMARY
                rows.sortedWith(compareBy(collator) { it.playlist.name })
            }
            PlaylistSortType.SONG_COUNT -> rows.sortedBy { it.songCount }
            PlaylistSortType.LAST_UPDATED -> rows.sortedBy { it.playlist.lastUpdateTime }
        }
        if (descending) sorted.reversed() else sorted
    }
