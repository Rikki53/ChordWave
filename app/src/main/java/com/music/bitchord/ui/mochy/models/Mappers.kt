// Mappers from ChordWave's data.model types (shared module) to the ported
// Mochy models in this package. One direction only: ChordWave -> Mochy.
// Mochy fields with no ChordWave equivalent get a sensible default and are
// listed in NEEDS.md §(a).
//
// ChordWave's Song/Album/Artist/Playlist are catalogue rows, not library
// records, so the mapped Mochy objects carry no like/library/play-count
// state — that lives in ChordWave's LikeState / player state, not on the
// row, and a later phase (HomeViewModel adapter) owns bridging it.

package com.music.bitchord.ui.mochy.models

import com.music.bitchord.data.model.ArtistRef
import com.music.bitchord.data.model.BrowseItem
import com.music.bitchord.data.model.DetailPage
import com.music.bitchord.data.model.UiState
import com.music.bitchord.data.model.UserPlaylist
import com.music.bitchord.data.model.durationMillis
import com.music.bitchord.data.model.Song as CwSong

/** ChordWave duration ("M:SS" text) as Mochy seconds; -1 when unstated (Mochy's unknown). */
private fun CwSong.durationSeconds(): Int {
    val seconds = (durationMillis() / 1_000).toInt()
    return if (seconds > 0) seconds else -1
}

/**
 * Every credited artist on the row. Falls back to the single byline string
 * ([CwSong.artist] / [CwSong.artistId]) when the row states no per-artist
 * links, which is most home cards.
 */
private fun CwSong.artistRefs(): List<ArtistRef> =
    if (artists.isNotEmpty()) artists
    else listOf(ArtistRef(name = artist, browseId = artistId))

/** An ArtistRef has no thumbnail of its own; the id falls back to the name when no channel is linked. */
private fun ArtistRef.toMochyArtistEntity(): ArtistEntity =
    ArtistEntity(id = browseId ?: name, name = name)

/** A ChordWave track as a Mochy library Song. */
fun CwSong.toMochySong(): Song {
    val entity = SongEntity(
        id = videoId,
        title = title,
        duration = durationSeconds(),
        thumbnailUrl = thumbnailUrl,
        albumId = albumId,
        albumName = albumName,
        explicit = isExplicit ?: false,
        isVideo = isVideo,
        localUri = localUri,
    )
    val albumEntity = albumId?.let {
        AlbumEntity(
            id = it,
            title = albumName.orEmpty(),
            songCount = 0,
            duration = 0,
        )
    }
    return Song(
        song = entity,
        artists = artistRefs().map { it.toMochyArtistEntity() },
        album = albumEntity,
    )
}

/** A ChordWave track straight to the player payload, without going through [Song]. */
fun CwSong.toMochyMetadata(): MediaMetadata =
    MediaMetadata(
        id = videoId,
        title = title,
        artists = artistRefs().map { MediaMetadata.Artist(id = it.browseId, name = it.name) },
        duration = durationSeconds(),
        thumbnailUrl = thumbnailUrl,
        album = albumId?.let { MediaMetadata.Album(id = it, title = albumName.orEmpty()) },
        setVideoId = setVideoId,
        musicVideoType = if (isVideo) "MUSIC_VIDEO_TYPE_OMV" else null,
        explicit = isExplicit ?: false,
        localUri = localUri,
    )

/**
 * A search/browse row as a Mochy Album. Callers should check
 * [BrowseItem.type] == ALBUM first; this maps the fields unconditionally.
 */
fun BrowseItem.toMochyAlbum(): Album =
    Album(
        album = AlbumEntity(
            id = browseId,
            title = title,
            thumbnailUrl = thumbnailUrl,
            songCount = 0,
            duration = 0,
        ),
    )

/**
 * A search/browse row as a Mochy Artist. Callers should check
 * [BrowseItem.type] == ARTIST first; this maps the fields unconditionally.
 */
fun BrowseItem.toMochyArtist(): Artist =
    Artist(
        artist = ArtistEntity(id = browseId, name = title, thumbnailUrl = thumbnailUrl),
        songCount = 0,
    )

/**
 * A search/browse row as a Mochy Playlist. Callers should check
 * [BrowseItem.type] == PLAYLIST first; this maps the fields unconditionally.
 */
fun BrowseItem.toMochyPlaylist(): Playlist =
    Playlist(
        playlist = PlaylistEntity(
            id = browseId,
            name = title,
            browseId = browseId,
            thumbnailUrl = thumbnailUrl,
        ),
        songCount = 0,
        songThumbnails = emptyList(),
    )

/** One of the account's own playlists as a Mochy Playlist. */
fun UserPlaylist.toMochyPlaylist(): Playlist =
    Playlist(
        playlist = PlaylistEntity(
            id = playlistId,
            name = title,
            browseId = browseId,
            thumbnailUrl = thumbnailUrl,
        ),
        songCount = 0,
        songThumbnails = emptyList(),
    )

/** A browsed album page as a Mochy Album (song count from the loaded listing). */
fun DetailPage.toMochyAlbum(): Album =
    Album(
        album = AlbumEntity(
            id = browseId,
            playlistId = library?.playlistId,
            title = title,
            thumbnailUrl = thumbnailUrl,
            songCount = (songs as? UiState.Success)?.data?.size ?: 0,
            duration = 0,
            description = description,
        ),
    )

/** A browsed playlist page as a Mochy Playlist (thumbnails from the first loaded tracks). */
fun DetailPage.toMochyPlaylist(): Playlist {
    val loadedSongs = (songs as? UiState.Success)?.data.orEmpty()
    return Playlist(
        playlist = PlaylistEntity(
            id = browseId,
            name = title,
            browseId = browseId,
            thumbnailUrl = thumbnailUrl,
            description = description,
        ),
        songCount = loadedSongs.size,
        songThumbnails = loadedSongs.take(4).map { it.thumbnailUrl },
    )
}
