// The Modern UI's read path into the Mochy Room database (Tahap 2 "Basis
// data"): read-only for now — writes still go to the ChordWave legacy
// stores, and every flow here is only used once the one-time migration has
// completed ([ready]). Before that, or if the migration failed, callers
// keep reading the legacy stores exactly as before, so the library can
// never read as empty while the phone is full of music.

package com.music.bitchord.data.mochydata

import android.content.Context
import com.music.bitchord.ui.mochy.models.Playlist as UiPlaylist
import com.music.bitchord.ui.mochy.models.Song as UiSong
import com.mochyy.mochymusic.db.entities.PlaylistEntity as RoomPlaylistEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class MochyLibraryRepository(context: Context) {

    private val db = MochyDatabaseHolder.get(context)

    /** True once the legacy -> Room migration has completed. */
    val ready: Flow<Boolean> = MochyDataMigration.status.map { it == MochyDataMigration.Status.DONE }

    /** The user's migrated playlists (the built-in Liked list excluded, as in the legacy path). */
    val offlinePlaylists: Flow<List<UiPlaylist>> = db.playlists().map { playlists ->
        playlists
            .filterNot { it.playlist.id == RoomPlaylistEntity.LIKED_PLAYLIST_ID }
            .map { it.toUiPlaylist() }
    }

    /** Playlist id -> the song ids in it, for the library shelves. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val playlistSongIds: Flow<Map<String, Set<String>>> = db.playlists().flatMapLatest { playlists ->
        val user = playlists.filterNot { it.playlist.id == RoomPlaylistEntity.LIKED_PLAYLIST_ID }
        if (user.isEmpty()) {
            flowOf(emptyMap())
        } else {
            combine(user.map { playlist -> db.playlistSongs(playlist.playlist.id) }) { lists ->
                user.mapIndexed { index, playlist ->
                    playlist.playlist.id to lists[index].mapTo(HashSet()) { it.song.id }
                }.toMap()
            }
        }
    }

    /** Every song that sits in a migrated playlist (incl. Liked), for the Local shelf. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val playlistSongs: Flow<List<UiSong>> = db.playlists().flatMapLatest { playlists ->
        if (playlists.isEmpty()) {
            flowOf(emptyList())
        } else {
            combine(playlists.map { playlist -> db.playlistSongs(playlist.playlist.id) }) { lists ->
                lists.flatMap { list -> list.map { it.song.toUiSong() } }
                    .distinctBy { it.id }
            }
        }
    }

    /** Songs marked downloaded in Room (their files live in Music/ChordWave). */
    val downloadedSongs: Flow<List<UiSong>> = db.downloadedSongs().map { songs ->
        songs.map { it.toUiSong() }
    }

    /** Local uris of liked songs, for drawing hearts without a scan. */
    val likedLocalUris: Flow<Set<String>> = db.likedSongs().map { songs ->
        songs.mapNotNullTo(HashSet()) { it.song.localUri }
    }

    /** All library songs in Room (in-library rows), newest first. */
    val librarySongs: Flow<List<UiSong>> = db.songs().map { songs -> songs.map { it.toUiSong() } }
}
