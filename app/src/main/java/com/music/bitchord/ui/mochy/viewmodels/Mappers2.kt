// Mappers2.kt — the extra mappings the HomeViewModel adapter needs, kept
// here so the staged phase1-models Mappers.kt stays untouched. One direction
// only: ChordWave -> Mochy, same as Mappers.kt.
//
//   RankedEntry -> Artist / Album   (ListeningStats charts -> LocalItems)
//   ShelfItem   -> PlaylistItem     (account + community playlists)
//   ShelfItem   -> Artist           (library artists)
//   CwSong      -> SongItem         (radio / playlist entries -> YTItems)
//   TrackEntry  -> CwSong           (ListeningStats buckets -> catalogue rows)
//   OfflinePlaylist -> Playlist     (local-file playlists -> LocalItems)
//   LocalItem   -> YTItem           (speed dial / getRandomItem cards)
//   List<Song>.filterVideoSongs     (Mochy's db-song video filter)

package com.music.bitchord.ui.mochy.viewmodels

import com.music.bitchord.data.OfflinePlaylist
import com.music.bitchord.data.model.ShelfItem
import com.music.bitchord.data.model.durationMillis
import com.music.bitchord.data.model.Song as CwSong
import com.music.bitchord.data.stats.RankedEntry
import com.music.bitchord.data.stats.TrackEntry
import com.music.bitchord.ui.mochy.models.Album
import com.music.bitchord.ui.mochy.models.AlbumEntity
import com.music.bitchord.ui.mochy.models.Artist
import com.music.bitchord.ui.mochy.models.ArtistEntity
import com.music.bitchord.ui.mochy.models.LocalItem
import com.music.bitchord.ui.mochy.models.Playlist
import com.music.bitchord.ui.mochy.models.PlaylistEntity
import com.music.bitchord.ui.mochy.models.Song
import com.music.bitchord.ui.mochy.models.innertube.AlbumItem
import com.music.bitchord.ui.mochy.models.innertube.Artist as YtArtist
import com.music.bitchord.ui.mochy.models.innertube.PlaylistItem
import com.music.bitchord.ui.mochy.models.innertube.SongItem
import com.music.bitchord.ui.mochy.models.innertube.WatchEndpoint.WatchEndpointMusicSupportedConfigs.WatchEndpointMusicConfig.Companion.MUSIC_VIDEO_TYPE_OMV
import com.music.bitchord.ui.mochy.models.innertube.YTItem

/** A stats-chart artist as a Mochy library Artist. No browseId, no UC id: the name stands in. */
fun RankedEntry.toMochyArtist(): Artist =
    Artist(
        artist = ArtistEntity(
            id = browseId ?: title,
            name = title,
            thumbnailUrl = artworkUrl,
        ),
        songCount = 0,
    )

/**
 * A stats-chart album as a Mochy library Album. The chart's subtitle is the
 * artist's name (ListeningStats records albums with sub = song.artist), so it
 * lands on the album's artist list — the similar-recommendations loader reads
 * it back to find the album's artist page.
 */
fun RankedEntry.toMochyAlbum(): Album =
    Album(
        album = AlbumEntity(
            id = browseId ?: title,
            title = title,
            thumbnailUrl = artworkUrl,
            songCount = 0,
            duration = 0,
        ),
        artists = subtitle?.takeIf { it.isNotBlank() }?.let {
            listOf(ArtistEntity(id = it, name = it))
        }.orEmpty(),
    )

/**
 * A library/collection shelf card as a Mochy PlaylistItem. The id drops the
 * VL prefix (Mochy convention — PlaylistItem.shareLink rebuilds the watch
 * URL from the bare id); callers that need to browse the playlist re-add it.
 * Null when the card carries no browseId.
 */
fun ShelfItem.toPlaylistItem(): PlaylistItem? {
    val browseId = browseId ?: return null
    return PlaylistItem(
        id = browseId.removePrefix("VL"),
        title = title,
        author = authorCredit(),
        songCountText = null,
        thumbnail = thumbnailUrl,
        playEndpoint = null,
        shuffleEndpoint = null,
        radioEndpoint = null,
    )
}

/** A library Artists/Subscriptions shelf card as a Mochy library Artist. Null without a browseId. */
fun ShelfItem.toMochyArtist(): Artist? {
    val browseId = browseId ?: return null
    return Artist(
        artist = ArtistEntity(
            id = browseId,
            name = title,
            thumbnailUrl = thumbnailUrl,
        ),
        songCount = 0,
    )
}

/** A catalogue row as a YTItem song card (radio results, playlist entries). */
fun CwSong.toSongItem(): SongItem {
    val refs = if (artists.isNotEmpty()) artists else listOf(
        com.music.bitchord.data.model.ArtistRef(name = artist, browseId = artistId),
    )
    val seconds = (durationMillis() / 1_000).toInt()
    return SongItem(
        id = videoId,
        title = title,
        artists = refs.map { YtArtist(name = it.name, id = it.browseId) },
        album = albumId?.let {
            com.music.bitchord.ui.mochy.models.innertube.Album(name = albumName.orEmpty(), id = it)
        },
        duration = seconds.takeIf { it > 0 },
        musicVideoType = MUSIC_VIDEO_TYPE_OMV.takeIf { isVideo },
        thumbnail = thumbnailUrl ?: "",
        explicit = isExplicit ?: false,
        setVideoId = setVideoId,
    )
}

/**
 * One stats-bucket track as a catalogue row, so the staged CwSong mappers
 * (toMochySong / toMochyMetadata) can carry it the rest of the way. Duration
 * is unknown to the buckets, so it stays null (Mochy reads that as -1).
 */
fun TrackEntry.toCwSong(): CwSong =
    CwSong(
        videoId = id,
        title = title,
        artist = artist,
        thumbnailUrl = art,
        artistId = artistId,
        albumId = albumId,
        albumName = album,
    )

/**
 * A local-file playlist as a Mochy Playlist. The id is the store's own id
 * (incl. "liked" for the built-in liked-songs list), which is what
 * HomeViewModel.playlistSongs() resolves it by. Editable stays true:
 * HomeScreen's hero picks only offer editable, non-empty playlists.
 */
fun OfflinePlaylist.toMochyPlaylist(): Playlist =
    Playlist(
        playlist = PlaylistEntity(
            id = id,
            name = title,
            browseId = browseId,
            thumbnailUrl = songs.firstOrNull()?.thumbnailUrl,
        ),
        songCount = songs.size,
        songThumbnails = songs.take(4).map { it.thumbnailUrl },
    )

/**
 * A local item as the YTItem the Speed Dial row and getRandomItem() play —
 * Mochy's own speed-dial mapping (HomeViewModel L224-307), shared by both
 * call sites. Albums and playlists only ever come from keepListening; songs
 * from either list.
 */
fun LocalItem.toSpeedDialYTItem(): YTItem? =
    when (this) {
        is Song -> SongItem(
            id = id,
            title = title,
            artists = artists.map { YtArtist(name = it.name, id = it.id) },
            thumbnail = thumbnailUrl ?: "",
            explicit = false,
        )

        is Album -> AlbumItem(
            browseId = id,
            playlistId = album.playlistId ?: "",
            title = title,
            artists = artists.map { YtArtist(name = it.name, id = it.id) },
            year = album.year,
            thumbnail = thumbnailUrl ?: "",
        )

        is Playlist -> PlaylistItem(
            id = id,
            title = title,
            author = YtArtist(name = "Local Playlist", id = null),
            songCountText = "$songCount songs",
            thumbnail = thumbnails.firstOrNull() ?: "",
            playEndpoint = null,
            shuffleEndpoint = null,
            radioEndpoint = null,
        )

        else -> null
    }

/** Mochy's db-song video filter (extensions/filterVideoSongs): drops video uploads when asked. */
fun List<Song>.filterVideoSongs(hide: Boolean): List<Song> =
    if (hide) filterNot { it.song.isVideo } else this
