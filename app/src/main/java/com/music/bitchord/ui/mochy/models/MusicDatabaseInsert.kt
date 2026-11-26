// Batch 55b: Mochy's DatabaseDao.insert(MediaMetadata) in ChordWave's shape.
// The engine's DAO can't see the app's MediaMetadata, so the insert lives
// here: the song row, its artists and their maps, all IGNORE so a song
// already saved stays as it is.

package com.music.bitchord.ui.mochy.models

import com.mochyy.mochymusic.db.MusicDatabase
import com.mochyy.mochymusic.db.entities.ArtistEntity
import com.mochyy.mochymusic.db.entities.SongArtistMap
import com.mochyy.mochymusic.db.entities.SongEntity as DbSongEntity

suspend fun MusicDatabase.insertSongMetadata(metadata: MediaMetadata) {
    val e = metadata.toSongEntity()
    insertSongs(
        listOf(
            DbSongEntity(
                id = e.id,
                title = e.title,
                duration = e.duration,
                thumbnailUrl = e.thumbnailUrl,
                albumId = e.albumId,
                albumName = e.albumName,
                explicit = e.explicit,
                liked = e.liked,
                likedDate = e.likedDate,
                inLibrary = e.inLibrary,
                libraryAddToken = e.libraryAddToken,
                libraryRemoveToken = e.libraryRemoveToken,
                isVideo = e.isVideo,
                localUri = e.localUri,
            )
        )
    )
    metadata.artists.forEachIndexed { index, artist ->
        val artistId = artist.id ?: artistByName(artist.name)?.id ?: ArtistEntity.generateArtistId()
        insertArtists(
            listOf(
                ArtistEntity(
                    id = artistId,
                    name = artist.name,
                    channelId = artist.id,
                )
            )
        )
        insertSongArtistMaps(
            listOf(
                SongArtistMap(
                    songId = metadata.id,
                    artistId = artistId,
                    position = index,
                )
            )
        )
    }
}
