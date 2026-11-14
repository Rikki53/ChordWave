// Mappers3.kt — the extra mappings the search adapters need, kept here so
// the staged phase1-models Mappers.kt and the phase2-adapter Mappers2.kt /
// ShelfItemMapper.kt stay untouched. One direction only: ChordWave -> Mochy,
// same as Mappers.kt.
//
//   SearchResult  -> YTItem       (typeahead rows + suggestion best-match)
//   BrowseItem    -> YTItem       (search rows: album / artist / playlist)
//   RankedEntry   -> ArtistItem / AlbumItem  (offline search preview)
//   OfflinePlaylist -> PlaylistItem          (offline search preview)
//
// The ShelfItem -> YTItem inference in ShelfItemMapper.kt is NOT reused
// here: SearchResult already carries its type (Track vs Browse + BrowseType),
// so no subtitle inference is needed. The subtitle-artist split below is
// the same rule ShelfItemMapper.artistCredit() uses, applied to a
// BrowseItem's subtitle instead of a shelf card's.

package com.music.bitchord.ui.mochy.viewmodels

import com.music.bitchord.data.OfflinePlaylist
import com.music.bitchord.data.innertube.InnertubeParser
import com.music.bitchord.data.model.BrowseItem
import com.music.bitchord.data.model.BrowseType
import com.music.bitchord.data.model.SearchResult
import com.music.bitchord.data.stats.RankedEntry
import com.music.bitchord.ui.mochy.models.innertube.AlbumItem
import com.music.bitchord.ui.mochy.models.innertube.Artist as YtArtist
import com.music.bitchord.ui.mochy.models.innertube.ArtistItem
import com.music.bitchord.ui.mochy.models.innertube.PlaylistItem
import com.music.bitchord.ui.mochy.models.innertube.YTItem

/** A search row as a YTItem card. The promoted top track maps like any track. */
fun SearchResult.toYTItem(): YTItem? =
    when (this) {
        is SearchResult.TopTrack -> song.toSongItem()
        is SearchResult.Track -> song.toSongItem()
        is SearchResult.Browse -> item.toYTItem()
    }

/**
 * A non-track search row as a YTItem card. Null for OTHER (podcast shows
 * and the like have no YTItem type). Playlist ids drop the VL prefix,
 * Mochy convention — callers that browse the playlist re-add it.
 */
fun BrowseItem.toYTItem(): YTItem? =
    when (type) {
        BrowseType.ALBUM -> AlbumItem(
            browseId = browseId,
            playlistId = "",
            title = title,
            artists = subtitleArtists().takeIf { it.isNotEmpty() },
            year = null,
            thumbnail = thumbnailUrl ?: "",
            explicit = false,
        )

        BrowseType.ARTIST -> ArtistItem(
            id = browseId,
            title = title,
            thumbnail = thumbnailUrl,
            channelId = browseId,
            playEndpoint = null,
            shuffleEndpoint = null,
            radioEndpoint = null,
        )

        BrowseType.PLAYLIST -> PlaylistItem(
            id = browseId.removePrefix("VL"),
            title = title,
            author = subtitleArtists().firstOrNull(),
            songCountText = null,
            thumbnail = thumbnailUrl,
            playEndpoint = null,
            shuffleEndpoint = null,
            radioEndpoint = null,
        )

        BrowseType.OTHER -> null
    }

/**
 * The credited artists out of a browse row's subtitle, which reads
 * "Album • Artist A, Artist B" rather than just the artist. Reuses CW's own
 * [InnertubeParser.artistFromSubtitle] (public on the shared module's parser;
 * it drops the type word, tallies and durations) and then splits the
 * remaining credit on ", " and " & ". Ids are unknowable from a bare
 * subtitle, so every artist id is null.
 */
private fun BrowseItem.subtitleArtists(): List<YtArtist> {
    if (subtitle.isBlank()) return emptyList()
    val credit = InnertubeParser.artistFromSubtitle(subtitle)
    if (credit.isBlank()) return emptyList()
    return credit.split(Regex("\\s*,\\s*|\\s+&\\s+"))
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .map { YtArtist(name = it, id = null) }
}

/** A stats-chart artist as a YTItem artist card (offline search preview). No browseId, no UC id: the name stands in. */
fun RankedEntry.toArtistItem(): ArtistItem =
    ArtistItem(
        id = browseId ?: title,
        title = title,
        thumbnail = artworkUrl,
        channelId = browseId,
        playEndpoint = null,
        shuffleEndpoint = null,
        radioEndpoint = null,
    )

/**
 * A stats-chart album as a YTItem album card (offline search preview). The
 * chart's subtitle is the artist's name (ListeningStats records albums with
 * sub = song.artist), so it lands on the album's artist list.
 */
fun RankedEntry.toAlbumItem(): AlbumItem =
    AlbumItem(
        browseId = browseId ?: title,
        playlistId = "",
        title = title,
        artists = subtitle?.takeIf { it.isNotBlank() }?.let { listOf(YtArtist(name = it, id = null)) },
        year = null,
        thumbnail = artworkUrl ?: "",
        explicit = false,
    )

/**
 * A local-file playlist as a YTItem playlist card (offline search preview).
 * The id is the store's own id ("liked" for the built-in list) — NOT the
 * "local:offline:" browse id, so a tap on it in the offline preview must
 * not be routed to "online_playlist/{id}" (see NEEDS.md).
 */
fun OfflinePlaylist.toPlaylistItem(): PlaylistItem =
    PlaylistItem(
        id = id,
        title = title,
        author = YtArtist(name = "Local Playlist", id = null),
        songCountText = "${songs.size} songs",
        thumbnail = songs.firstOrNull()?.thumbnailUrl,
        playEndpoint = null,
        shuffleEndpoint = null,
        radioEndpoint = null,
    )
