// Ported from Mochy Music (GPL-3.0): db/DatabaseDao.kt — reduced to the
// library core the two-engine architecture needs first (Tahap 2 "Basis
// data"): library reads for the Modern UI and the writes the one-time
// ChordWave -> Room migration performs. Query names and shapes follow
// Mochy's DAO where they overlap (songs, likedSongs, playlistSongs, ...);
// the stats/recommendation queries of the full 2,200-line Mochy DAO arrive
// with the engine stages that use them.

package com.mochyy.mochymusic.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.mochyy.mochymusic.db.entities.Album
import com.mochyy.mochymusic.db.entities.AlbumArtistMap
import com.mochyy.mochymusic.db.entities.AlbumEntity
import com.mochyy.mochymusic.db.entities.ArtistEntity
import com.mochyy.mochymusic.db.entities.Event
import com.mochyy.mochymusic.db.entities.FormatEntity
import com.mochyy.mochymusic.db.entities.LyricsEntity
import com.mochyy.mochymusic.db.entities.PlayCountEntity
import com.mochyy.mochymusic.db.entities.Playlist
import com.mochyy.mochymusic.db.entities.PlaylistEntity
import com.mochyy.mochymusic.db.entities.PlaylistSong
import com.mochyy.mochymusic.db.entities.PlaylistSongMap
import com.mochyy.mochymusic.db.entities.SearchHistory
import com.mochyy.mochymusic.db.entities.Song
import com.mochyy.mochymusic.db.entities.SongAlbumMap
import com.mochyy.mochymusic.db.entities.SongArtistMap
import com.mochyy.mochymusic.db.entities.SongEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DatabaseDao {

    // --- Library reads -------------------------------------------------------

    @Transaction
    @Query("SELECT * FROM song WHERE inLibrary IS NOT NULL ORDER BY inLibrary DESC")
    fun songs(): Flow<List<Song>>

    @Transaction
    @Query("SELECT * FROM song ORDER BY title")
    fun allSongs(): Flow<List<Song>>

    @Transaction
    @Query("SELECT * FROM song WHERE liked = 1 ORDER BY likedDate DESC")
    fun likedSongs(): Flow<List<Song>>

    @Query("SELECT COUNT(*) FROM song WHERE liked = 1")
    fun likedSongsCount(): Flow<Int>

    @Transaction
    @Query("SELECT * FROM song WHERE isDownloaded = 1 ORDER BY dateDownload DESC")
    fun downloadedSongs(): Flow<List<Song>>

    @Transaction
    @Query("SELECT * FROM song WHERE isLocal = 1 ORDER BY date DESC")
    fun localSongs(): Flow<List<Song>>

    @Transaction
    @Query("SELECT * FROM song WHERE id = :songId")
    fun song(songId: String?): Flow<Song?>

    @Transaction
    @Query("SELECT * FROM song WHERE id = :songId")
    suspend fun getSongById(songId: String): Song?

    @Query("SELECT * FROM artist ORDER BY name")
    fun artists(): Flow<List<ArtistEntity>>

    @Transaction
    @Query("SELECT * FROM album ORDER BY title")
    fun albums(): Flow<List<Album>>

    @Transaction
    @Query(
        "SELECT *, (SELECT COUNT(*) FROM playlist_song_map WHERE playlistId = playlist.id) AS songCount " +
            "FROM playlist ORDER BY createdAt",
    )
    fun playlists(): Flow<List<Playlist>>

    @Transaction
    @Query("SELECT * FROM playlist_song_map WHERE playlistId = :playlistId ORDER BY position")
    fun playlistSongs(playlistId: String): Flow<List<PlaylistSong>>

    @Query("SELECT * FROM search_history ORDER BY id DESC")
    fun searchHistory(): Flow<List<SearchHistory>>

    @Query("SELECT * FROM lyrics WHERE id = :id")
    fun lyrics(id: String?): Flow<LyricsEntity?>

    @Query("SELECT * FROM format WHERE id = :id")
    fun format(id: String?): Flow<FormatEntity?>

    @Query("SELECT * FROM playCount WHERE song = :songId ORDER BY year, month")
    fun playCounts(songId: String): Flow<List<PlayCountEntity>>

    @Query("SELECT COUNT(*) FROM song")
    suspend fun songCount(): Int

    @Query("SELECT COUNT(*) FROM playlist")
    suspend fun playlistCount(): Int

    // --- Writes --------------------------------------------------------------
    // The migration inserts with IGNORE so re-running it can never duplicate
    // or clobber rows a later stage may have written; targeted updates then
    // set the flags (liked/downloaded/in-library) the migration knows about.

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSongs(songs: List<SongEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertArtists(artists: List<ArtistEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAlbums(albums: List<AlbumEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPlaylists(playlists: List<PlaylistEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSongArtistMaps(maps: List<SongArtistMap>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSongAlbumMaps(maps: List<SongAlbumMap>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAlbumArtistMaps(maps: List<AlbumArtistMap>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPlaylistSongMaps(maps: List<PlaylistSongMap>)

    /** Rebuilds one playlist's membership (the migration re-runs safely). */
    @Query("DELETE FROM playlist_song_map WHERE playlistId = :playlistId")
    suspend fun deletePlaylistSongs(playlistId: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSearchHistory(entries: List<SearchHistory>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlayCounts(counts: List<PlayCountEntity>)

    @Insert
    suspend fun insertEvents(events: List<Event>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLyrics(lyrics: LyricsEntity)

    /** Migration: mark songs liked without touching the rest of the row. */
    @Query("UPDATE song SET liked = 1, likedDate = COALESCE(likedDate, :at) WHERE id IN (:ids)")
    suspend fun markLiked(ids: List<String>, at: Long)

    /** Migration: mark songs downloaded (files already in Music/ChordWave). */
    @Query("UPDATE song SET isDownloaded = 1, dateDownload = COALESCE(dateDownload, :at) WHERE id IN (:ids)")
    suspend fun markDownloaded(ids: List<String>, at: Long)

    /** Migration: songs that were in the library on the ChordWave side. */
    @Query("UPDATE song SET inLibrary = COALESCE(inLibrary, :at) WHERE id IN (:ids)")
    suspend fun markInLibrary(ids: List<String>, at: Long)

    /** Migration: total play time summed from the legacy monthly buckets. */
    @Query("UPDATE song SET totalPlayTime = :totalPlayTime WHERE id = :id")
    suspend fun setTotalPlayTime(id: String, totalPlayTime: Long)
}
