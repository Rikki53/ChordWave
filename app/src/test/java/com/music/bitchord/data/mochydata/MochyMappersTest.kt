package com.music.bitchord.data.mochydata

import com.music.bitchord.data.OfflinePlaylist
import com.music.bitchord.data.OfflinePlaylists
import com.music.bitchord.data.model.ArtistRef
import com.music.bitchord.data.model.EntityType
import com.music.bitchord.data.model.SearchHistoryEntity
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.stats.TrackEntry
import com.mochyy.mochymusic.db.entities.PlaylistEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MochyMappersTest {

    private fun song(
        videoId: String = "abc123",
        artist: String = "Drake",
        artistId: String? = "UCdrake",
        artists: List<ArtistRef> = emptyList(),
        albumId: String? = "MPREalbum",
        albumName: String? = "Scorpion",
        localUri: String? = null,
    ) = Song(
        videoId = videoId,
        title = "God's Plan",
        artist = artist,
        thumbnailUrl = "https://example.invalid/cover.jpg",
        durationText = "3:18",
        artistId = artistId,
        artists = artists,
        albumId = albumId,
        albumName = albumName,
        localUri = localUri,
    )

    @Test
    fun `song keeps its video id and stated artist and album ids`() {
        val mapped = song().toRoomSong()
        assertEquals("abc123", mapped.song.id)
        assertEquals("God's Plan", mapped.song.title)
        assertEquals(198, mapped.song.duration)
        assertEquals("MPREalbum", mapped.song.albumId)
        assertEquals(listOf("UCdrake"), mapped.artists.map { it.id })
        assertEquals("abc123", mapped.songArtistMaps.single().songId)
        assertEquals("MPREalbum", mapped.songAlbumMap?.albumId)
    }

    @Test
    fun `unstated artist and album ids are stable across runs`() {
        val a = song(artistId = null, albumId = null).toRoomSong()
        val b = song(artistId = null, albumId = null).toRoomSong()
        assertEquals(a.artists.single().id, b.artists.single().id)
        assertTrue(a.artists.single().id.startsWith("LA"))
        assertEquals(a.song.albumId, b.song.albumId)
        assertTrue(a.song.albumId!!.startsWith("MPRE"))
        // A different name is a different artist.
        val c = song(artist = "Kendrick", artistId = null).toRoomSong()
        assertNotEquals(a.artists.single().id, c.artists.single().id)
    }

    @Test
    fun `per-artist credits keep their order`() {
        val mapped = song(
            artists = listOf(
                ArtistRef("First", "UC1"),
                ArtistRef("Second", "UC2"),
            ),
        ).toRoomSong()
        assertEquals(listOf("UC1", "UC2"), mapped.artists.map { it.id })
        assertEquals(listOf(0, 1), mapped.songArtistMaps.map { it.position })
    }

    @Test
    fun `local file song is local and keeps its uri`() {
        val mapped = song(localUri = "content://media/42").toRoomSong(isLocal = true)
        assertTrue(mapped.song.isLocal)
        assertEquals("content://media/42", mapped.song.localUri)
        assertTrue(mapped.artists.single().isLocal)
    }

    @Test
    fun `offline playlist ids are stable and liked becomes LP_LIKED`() {
        val liked = OfflinePlaylist(OfflinePlaylists.LIKED_ID, "Liked songs", listOf(song()))
        assertEquals(PlaylistEntity.LIKED_PLAYLIST_ID, liked.toRoomPlaylist().id)
        val mine = OfflinePlaylist("pxyz", "Mine", listOf(song(videoId = "v1"), song(videoId = "v2")))
        assertEquals(mine.toRoomPlaylist().id, playlistIdFor("pxyz"))
        assertEquals(listOf("v1", "v2"), mine.toRoomSongMaps().map { it.songId })
        assertEquals(listOf(0, 1), mine.toRoomSongMaps().map { it.position })
    }

    @Test
    fun `listening bucket track becomes a song and a monthly play count`() {
        val track = TrackEntry(
            id = "vid9",
            title = "HUMBLE.",
            artist = "Kendrick Lamar",
            album = "DAMN.",
            albumId = "MPREdamn",
            artistId = "UCkendrick",
            art = "https://example.invalid/damn.jpg",
            ms = 420_000,
            plays = 3,
            last = 1_700_000_000_000,
        )
        val (mapped, count) = track.toRoomRows(2026, 11)
        assertEquals("vid9", mapped.song.id)
        assertEquals("UCkendrick", mapped.artists.single().id)
        assertEquals("vid9", count.song)
        assertEquals(2026, count.year)
        assertEquals(11, count.month)
        assertEquals(3, count.count)
    }

    @Test
    fun `search history unwraps raw terms and keeps entity titles`() {
        val raw = SearchHistoryEntity("q:lofi beats", "lofi beats", "", null, EntityType.TRACK)
        assertEquals("lofi beats", raw.toRoomSearchHistory().query)
        val entity = SearchHistoryEntity("UCdrake", "Drake", "Artist", null, EntityType.ARTIST)
        assertEquals("Drake", entity.toRoomSearchHistory().query)
    }

    @Test
    fun `room song maps back to the ui song field for field`() {
        val mapped = song().toRoomSong(liked = true, isDownloaded = true)
        val ui = mapped.song.toUiSongEntity()
        assertEquals(mapped.song.id, ui.id)
        assertEquals(mapped.song.title, ui.title)
        assertEquals(mapped.song.duration, ui.duration)
        assertEquals(true, ui.liked)
        assertEquals(true, ui.isDownloaded)
        assertEquals(mapped.song.albumId, ui.albumId)
    }
}
