// Mapping between ChordWave's legacy library data and the Mochy Room
// database (the shared data source of the two-engine architecture,
// RENCANA-DUA-MESIN.md Tahap 2). Pure functions only: the migration
// (MochyDataMigration) and the Modern library read path
// (MochyLibraryRepository) both go through here, and the unit tests cover
// this file without a device.
//
// Identity rules (deterministic, so re-running the migration lands on the
// same rows):
// - A song keeps its ChordWave videoId; device files keep the content uri
//   OfflinePlaylists already uses as their identity.
// - An artist keeps its channel/browse id when the row states one;
//   otherwise it gets a stable "LA" id derived from the name (Mochy's
//   generateArtistId is random, which would duplicate artists on a re-run).
// - An album keeps its browse id, or a stable id derived from its name.
// - An offline playlist keeps a stable id derived from its ChordWave id;
//   the built-in Liked list becomes Mochy's LP_LIKED.

package com.music.bitchord.data.mochydata

import com.music.bitchord.data.OfflinePlaylist
import com.music.bitchord.data.OfflinePlaylists
import com.music.bitchord.data.model.SearchHistoryEntity
import com.music.bitchord.data.model.durationMillis
import com.music.bitchord.data.stats.TrackEntry
import com.music.bitchord.data.model.Song as CwSong
import com.mochyy.mochymusic.db.entities.AlbumArtistMap
import com.mochyy.mochymusic.db.entities.AlbumEntity as RoomAlbumEntity
import com.mochyy.mochymusic.db.entities.ArtistEntity as RoomArtistEntity
import com.mochyy.mochymusic.db.entities.PlayCountEntity
import com.mochyy.mochymusic.db.entities.PlaylistEntity as RoomPlaylistEntity
import com.mochyy.mochymusic.db.entities.PlaylistSongMap
import com.mochyy.mochymusic.db.entities.SearchHistory as RoomSearchHistory
import com.mochyy.mochymusic.db.entities.Song as RoomSong
import com.mochyy.mochymusic.db.entities.SongAlbumMap
import com.mochyy.mochymusic.db.entities.SongArtistMap
import com.mochyy.mochymusic.db.entities.SongEntity as RoomSongEntity
import com.music.bitchord.ui.mochy.models.AlbumEntity as UiAlbumEntity
import com.music.bitchord.ui.mochy.models.ArtistEntity as UiArtistEntity
import com.music.bitchord.ui.mochy.models.Playlist as UiPlaylist
import com.music.bitchord.ui.mochy.models.PlaylistEntity as UiPlaylistEntity
import com.music.bitchord.ui.mochy.models.Song as UiSong
import com.music.bitchord.ui.mochy.models.SongEntity as UiSongEntity
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/** Stable 8-letter suffix from a name, in Mochy's generated-id shape. */
internal fun stableSuffix(seed: String): String {
    val letters = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"
    val digest = MessageDigest.getInstance("SHA-256").digest(seed.trim().lowercase().toByteArray())
    return (0 until 8).map { letters[(digest[it].toInt() and 0xFF) % letters.length] }.joinToString("")
}

fun artistIdFor(name: String, browseId: String?): String =
    browseId?.takeIf { it.isNotBlank() } ?: ("LA" + stableSuffix(name))

fun albumIdFor(name: String?, browseId: String?): String? =
    browseId?.takeIf { it.isNotBlank() } ?: name?.takeIf { it.isNotBlank() }?.let { "MPRE" + stableSuffix(it) }

fun playlistIdFor(offlineId: String): String =
    if (offlineId == OfflinePlaylists.LIKED_ID) {
        RoomPlaylistEntity.LIKED_PLAYLIST_ID
    } else {
        "LPCW" + offlineId.filter { it.isLetterOrDigit() }.ifEmpty { stableSuffix(offlineId) }
    }

internal fun Long.toLocalDateTime(): LocalDateTime =
    LocalDateTime.ofInstant(Instant.ofEpochMilli(this), ZoneId.systemDefault())

/** One ChordWave song as the rows the Room database holds for it. */
data class MappedSong(
    val song: RoomSongEntity,
    val artists: List<RoomArtistEntity>,
    val songArtistMaps: List<SongArtistMap>,
    val album: RoomAlbumEntity?,
    val songAlbumMap: SongAlbumMap?,
    val albumArtistMaps: List<AlbumArtistMap>,
)

fun CwSong.toRoomSong(
    isLocal: Boolean = localUri != null,
    isDownloaded: Boolean = false,
    liked: Boolean = false,
    inLibraryAt: LocalDateTime? = null,
    downloadedAt: LocalDateTime? = localDateAddedSeconds?.let { (it * 1_000).toLocalDateTime() },
): MappedSong {
    val refs = if (artists.isNotEmpty()) {
        artists
    } else {
        listOf(com.music.bitchord.data.model.ArtistRef(name = artist, browseId = artistId))
    }.filter { it.name.isNotBlank() }
    val roomArtists = refs.map { ref ->
        RoomArtistEntity(
            id = artistIdFor(ref.name, ref.browseId),
            name = ref.name,
            isLocal = isLocal,
        )
    }
    val resolvedAlbumId = albumIdFor(albumName, albumId)
    val seconds = (durationMillis() / 1_000).toInt()
    val entity = RoomSongEntity(
        id = videoId,
        title = title,
        duration = if (seconds > 0) seconds else -1,
        thumbnailUrl = thumbnailUrl,
        albumId = resolvedAlbumId,
        albumName = albumName,
        explicit = isExplicit ?: false,
        liked = liked,
        likedDate = if (liked) inLibraryAt else null,
        inLibrary = inLibraryAt,
        dateDownload = if (isDownloaded) downloadedAt else null,
        isLocal = isLocal,
        isDownloaded = isDownloaded,
        isVideo = isVideo,
        localUri = localUri,
        date = localDateAddedSeconds?.let { (it * 1_000).toLocalDateTime() },
        dateModified = localDateModifiedSeconds?.let { (it * 1_000).toLocalDateTime() },
    )
    val album = resolvedAlbumId?.let { id ->
        RoomAlbumEntity(
            id = id,
            title = albumName.orEmpty(),
            thumbnailUrl = thumbnailUrl,
            songCount = 0,
            duration = 0,
            isLocal = isLocal,
        )
    }
    return MappedSong(
        song = entity,
        artists = roomArtists,
        songArtistMaps = roomArtists.mapIndexed { index, artist ->
            SongArtistMap(songId = videoId, artistId = artist.id, position = index)
        },
        album = album,
        songAlbumMap = resolvedAlbumId?.let { SongAlbumMap(songId = videoId, albumId = it, index = 0) },
        albumArtistMaps = resolvedAlbumId?.let { albumId ->
            roomArtists.mapIndexed { index, artist ->
                AlbumArtistMap(albumId = albumId, artistId = artist.id, order = index)
            }
        } ?: emptyList(),
    )
}

/** A legacy monthly-bucket track as Room rows: the song plus its play count for that month. */
fun TrackEntry.toRoomRows(year: Int, month: Int): Pair<MappedSong, PlayCountEntity> {
    val cw = CwSong(
        videoId = id,
        title = title,
        artist = artist,
        thumbnailUrl = art,
        artistId = artistId,
        albumId = albumId,
        albumName = album,
    )
    return cw.toRoomSong(isLocal = false) to PlayCountEntity(song = id, year = year, month = month, count = plays)
}

/** A ChordWave offline playlist as its Room playlist row plus ordered song maps. */
fun OfflinePlaylist.toRoomPlaylist(): RoomPlaylistEntity = RoomPlaylistEntity(
    id = playlistIdFor(id),
    name = title,
    browseId = browseId,
    thumbnailUrl = coverUrl,
    isLocal = true,
    description = description,
)

fun OfflinePlaylist.toRoomSongMaps(): List<PlaylistSongMap> =
    songs.mapIndexed { index, song ->
        PlaylistSongMap(playlistId = playlistIdFor(id), songId = song.videoId, position = index)
    }

/**
 * A tapped search entity as a Mochy search-history row. Mochy stores the
 * query text; a raw term ChordWave stored as "q:<term>" unwraps to the
 * term, anything else keeps its display title.
 */
fun SearchHistoryEntity.toRoomSearchHistory(): RoomSearchHistory =
    RoomSearchHistory(query = if (id.startsWith("q:")) id.removePrefix("q:") else title)

// --- Room -> Modern UI models ------------------------------------------------
// The Modern UI already speaks Mochy's shapes (ui.mochy.models are the
// Room-free copies of these entities), so these are field-for-field copies.

fun RoomSongEntity.toUiSongEntity(): UiSongEntity = UiSongEntity(
    id = id,
    title = title,
    duration = duration,
    thumbnailUrl = thumbnailUrl,
    albumId = albumId,
    albumName = albumName,
    genre = genre,
    explicit = explicit,
    year = year,
    date = date,
    dateModified = dateModified,
    liked = liked,
    likedDate = likedDate,
    totalPlayTime = totalPlayTime,
    inLibrary = inLibrary,
    dateDownload = dateDownload,
    isLocal = isLocal,
    libraryAddToken = libraryAddToken,
    libraryRemoveToken = libraryRemoveToken,
    lyricsOffset = lyricsOffset,
    romanizeLyrics = romanizeLyrics,
    isDownloaded = isDownloaded,
    isUploaded = isUploaded,
    isVideo = isVideo,
    localUri = localUri,
    addedByLike = addedByLike,
)

fun RoomArtistEntity.toUiArtistEntity(): UiArtistEntity = UiArtistEntity(
    id = id,
    name = name,
    thumbnailUrl = thumbnailUrl,
    channelId = channelId,
    lastUpdateTime = lastUpdateTime,
    bookmarkedAt = bookmarkedAt,
    isLocal = isLocal,
)

fun RoomAlbumEntity.toUiAlbumEntity(): UiAlbumEntity = UiAlbumEntity(
    id = id,
    playlistId = playlistId,
    title = title,
    year = year,
    thumbnailUrl = thumbnailUrl,
    themeColor = themeColor,
    songCount = songCount,
    duration = duration,
    explicit = explicit,
    lastUpdateTime = lastUpdateTime,
    bookmarkedAt = bookmarkedAt,
    likedDate = likedDate,
    inLibrary = inLibrary,
    description = description,
    isLocal = isLocal,
    isUploaded = isUploaded,
)

fun RoomSong.toUiSong(): UiSong = UiSong(
    song = song.toUiSongEntity(),
    artists = artists.map { it.toUiArtistEntity() },
    album = album?.toUiAlbumEntity(),
)

fun com.mochyy.mochymusic.db.entities.Playlist.toUiPlaylist(): UiPlaylist = UiPlaylist(
    playlist = UiPlaylistEntity(
        id = playlist.id,
        name = playlist.name,
        browseId = playlist.browseId,
        createdAt = playlist.createdAt,
        lastUpdateTime = playlist.lastUpdateTime,
        isEditable = playlist.isEditable,
        bookmarkedAt = playlist.bookmarkedAt,
        remoteSongCount = playlist.remoteSongCount,
        playEndpointParams = playlist.playEndpointParams,
        thumbnailUrl = playlist.thumbnailUrl,
        shuffleEndpointParams = playlist.shuffleEndpointParams,
        radioEndpointParams = playlist.radioEndpointParams,
        isLocal = playlist.isLocal,
        isAutoSync = playlist.isAutoSync,
        description = playlist.description,
    ),
    songCount = songCount,
    songThumbnails = songThumbnails,
)

/** Epoch millis as the Room converters store a [LocalDateTime] (UTC). */
internal fun LocalDateTime.toRoomMillis(): Long = atZone(ZoneOffset.UTC).toInstant().toEpochMilli()
