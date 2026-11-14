// Mappers7.kt — the extra mappings the playlist adapters (C1–C6) need,
// kept here so the staged Mappers.kt / Mappers2.kt / Mappers3.kt /
// Mappers4.kt stay untouched. One direction only: ChordWave -> Mochy,
// same as Mappers.kt.
//
//   List<Song>.sortedBySongSort   (Mochy's song-list sort, in memory)
//   CwSong -> downloaded / local-file / liked Mochy Songs (dates + flags
//                                  the plain toMochySong() leaves blank)
//   RankedSong -> Song            (stats songs with real total play time)
//   CwSong -> PlaylistSong        (one playlist entry, in stored order)
//   Song / MediaMetadata -> CwSong, localUri-carrying (the staged
//                                  CwDownloads.toCwSong drops it, and
//                                  OfflinePlaylists keys on it)
//
// NOTE (coordinator dedup, 15 Nov): List<Song>.filterExplicit lived here
// AND in Mappers6 with the same signature — duplicate top-level
// declarations in one package break the build. Single home is Mappers6
// (byte-identical body). Same package, so all callers resolve unchanged.
//
// SongSortType is a C9 item (coordinator adds it to PreferenceKeys.kt;
// the playlist screens already import it).

package com.music.bitchord.ui.mochy.viewmodels

import com.music.bitchord.data.model.Song as CwSong
import com.music.bitchord.data.stats.RankedSong
import com.music.bitchord.ui.mochy.constants.SongSortType
import com.music.bitchord.ui.mochy.models.MediaMetadata
import com.music.bitchord.ui.mochy.models.PlaylistSong
import com.music.bitchord.ui.mochy.models.PlaylistSongMap
import com.music.bitchord.ui.mochy.models.Song
import com.music.bitchord.ui.mochy.models.toMochySong
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/** Mochy's ListExt.reversed(Boolean), renamed so it can't be confused with stdlib reversed(). */
fun <T> List<T>.reversedIf(reversed: Boolean): List<T> = if (reversed) asReversed() else this

/**
 * Mochy's song-list sort (DatabaseDao: CREATE_DATE is the library-added
 * date, NAME the title, ARTIST the byline, PLAY_TIME the total play time),
 * applied in memory. [createDate] picks the date CREATE_DATE sorts on:
 * liked lists pass likedDate, downloaded lists dateDownload, everything
 * else the library date. The sort is stable, so rows with no date keep the
 * order they arrived in — which for the Liked feed is newest first.
 */
fun List<Song>.sortedBySongSort(
    sortType: SongSortType,
    descending: Boolean,
    createDate: (Song) -> LocalDateTime? = { it.song.inLibrary ?: it.song.date ?: it.song.dateDownload },
): List<Song> {
    val sorted = when (sortType) {
        SongSortType.CREATE_DATE -> sortedBy { createDate(it) ?: LocalDateTime.MIN }
        SongSortType.NAME -> sortedBy { it.song.title }
        SongSortType.ARTIST -> sortedBy { song -> song.artists.joinToString(separator = "") { it.name } }
        SongSortType.PLAY_TIME -> sortedBy { it.song.totalPlayTime }
    }
    return sorted.reversedIf(descending)
}

/** A CW row's added date as a LocalDateTime, or null when the row never said. */
private fun CwSong.addedDate(): LocalDateTime? =
    localDateAddedSeconds?.takeIf { it > 0 }?.let {
        Instant.ofEpochSecond(it).atZone(ZoneId.systemDefault()).toLocalDateTime()
    }

/** A downloaded track as a Mochy library Song: downloaded, with its download date. */
fun CwSong.toDownloadedMochySong(): Song {
    val base = toMochySong()
    return base.copy(song = base.song.copy(isDownloaded = true, dateDownload = addedDate()))
}

/**
 * An on-device file as a Mochy library Song: local, with its added date,
 * local artists (so the shelf's artist list, which only keeps YouTube or
 * local artists, keeps them) and a local album tag. Merged from Mappers5
 * (flags) + Mappers7 (date) — single home after the 15 Nov dedup. The id
 * is the file's content uri, the same identity LocalMediaRepository gives
 * device files.
 */
fun CwSong.toLocalFileMochySong(): Song {
    val base = toMochySong()
    val album = base.album?.let { it.copy(album = it.album.copy(isLocal = true)) }
    return base.copy(
        song = base.song.copy(isLocal = true, date = addedDate()),
        artists = base.artists.map { it.copy(isLocal = true) },
        album = album,
    )
}

/**
 * A liked track as a Mochy library Song. The liked date is unknowable from
 * a browse row, so it stays null — callers that sort liked songs by date
 * rely on the feed's own order (newest first) plus a stable sort.
 */
fun CwSong.toLikedMochySong(): Song {
    val base = toMochySong()
    return base.copy(song = base.song.copy(liked = true))
}

/**
 * A stats-chart song as a Mochy library Song, carrying its total play time
 * so PLAY_TIME sorts and the Top page read real numbers.
 */
fun RankedSong.toTopMochySong(): Song {
    val base = song.toMochySong()
    return base.copy(song = base.song.copy(totalPlayTime = ms))
}

/**
 * A catalogue row as one entry of a playlist, in stored order. The map id
 * is the position: Mochy's DB row ids don't exist here, so the adapter
 * re-numbers positions on every mutation and keeps ids stable once
 * assigned (see LocalPlaylistViewModel).
 */
fun CwSong.toPlaylistSong(playlistId: String, position: Int): PlaylistSong =
    PlaylistSong(
        map = PlaylistSongMap(
            id = position,
            playlistId = playlistId,
            songId = videoId,
            position = position,
            setVideoId = setVideoId,
        ),
        song = toMochySong(),
    )

/**
 * A Mochy library song as the CW Song OfflinePlaylists needs — like the
 * staged Song.toCwSong, but carrying the localUri the store keys on.
 */
fun Song.toLocalCwSong(): CwSong = CwSong(
    videoId = song.id,
    title = song.title,
    artist = artists.joinToString { it.name },
    thumbnailUrl = song.thumbnailUrl,
    albumId = song.albumId,
    albumName = song.albumName,
    isVideo = song.isVideo,
    localUri = song.localUri,
)

/** Player metadata as the CW Song OfflinePlaylists needs — same localUri rule as Song.toLocalCwSong. */
fun MediaMetadata.toLocalCwSong(): CwSong = CwSong(
    videoId = id,
    title = title,
    artist = artists.joinToString { it.name },
    thumbnailUrl = thumbnailUrl,
    albumId = album?.id,
    albumName = album?.title,
    isVideo = isVideoSong,
    localUri = localUri,
)
