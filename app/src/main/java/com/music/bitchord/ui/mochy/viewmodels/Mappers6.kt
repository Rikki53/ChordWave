// Mappers6.kt — the extra mappings the detail-page adapters need, kept here
// so the staged Mappers.kt / Mappers2.kt / Mappers3.kt / Mappers4.kt stay
// untouched. One direction only: ChordWave -> Mochy, same as Mappers.kt.
//
//   CW ArtistPage   -> Mochy ArtistPage  (artist page + top-songs section)
//   HomeShelf       -> ArtistSection     (one carousel shelf)
//   WatchTarget     -> WatchEndpoint     (artist Mix / Shuffle buttons)
//   ShelfItem       -> Album             (library Albums shelf)
//   Song            -> Song              (like/download state overlay)
//   List<Song>      -> filterExplicit    (Mochy's db-song explicit filter)
//   List<Album>     -> filterExplicitAlbums
//
// The per-row mappings are reused, not redeclared: CwSong.toSongItem
// (Mappers2), ShelfItem.toYTItem / artistCredit (ShelfItemMapper).

package com.music.bitchord.ui.mochy.viewmodels

import com.music.bitchord.data.LikeState
import com.music.bitchord.data.innertube.InnertubeParser
import com.music.bitchord.data.model.ArtistPage as CwArtistPage
import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.data.model.LikeStatus
import com.music.bitchord.data.model.ShelfItem
import com.music.bitchord.data.model.WatchTarget
import com.music.bitchord.download.Downloads
import com.music.bitchord.ui.mochy.models.Album
import com.music.bitchord.ui.mochy.models.AlbumEntity
import com.music.bitchord.ui.mochy.models.ArtistEntity
import com.music.bitchord.ui.mochy.models.Song
import com.music.innertube.models.ArtistItem
import com.music.bitchord.ui.mochy.models.innertube.ArtistPage
import com.music.bitchord.ui.mochy.models.innertube.ArtistSection
import com.music.innertube.models.BrowseEndpoint
import com.music.innertube.models.WatchEndpoint

/** A 4-digit year inside a page subtitle ("Album • Artist • 2024"). */
internal val AlbumYearRegex = Regex("""\b(19|20)\d{2}\b""")

/** A watch target as the endpoint a queue starts from, fields verbatim. */
fun WatchTarget.toWatchEndpoint(): WatchEndpoint =
    WatchEndpoint(videoId = videoId, playlistId = playlistId, params = params)

/**
 * One carousel shelf as an artist-page section. Null when nothing on it
 * maps (cards CW's ShelfItemMapper cannot type) — the screen renders a
 * header per section, and an empty one would show a bare rule. The shelf's
 * "more" button becomes the section's moreEndpoint.
 */
fun HomeShelf.toArtistSection(): ArtistSection? {
    val items = items.mapNotNull { it.toYTItem(layout) }.distinctBy { it.id }
    if (items.isEmpty()) return null
    return ArtistSection(
        title = title,
        items = items,
        moreEndpoint = moreBrowseId?.let { BrowseEndpoint(browseId = it, params = moreParams) },
    )
}

/**
 * CW's parsed artist landing page as Mochy's ArtistPage. CW keeps the top
 * songs apart from the shelves, so they become a leading "Top songs"
 * section (Mochy's ArtistShelves finds it by its first item being a
 * SongItem with an album — toSongItem sets the album whenever the row
 * carries an album id, which top-song rows do). The section title is
 * fixed English: the screen hardcodes "Top songs" as the shelf heading
 * anyway, and CW's parser does not carry the localised title.
 * descriptionRuns stays null — CW parses the bio as a plain String.
 */
fun CwArtistPage.toMochyArtistPage(artistId: String): ArtistPage {
    val topSongsSection = songs.takeIf { it.isNotEmpty() }?.let { topSongs ->
        ArtistSection(
            title = "Top songs",
            items = topSongs.map { it.toSongItem() }.distinctBy { it.id },
            moreEndpoint = moreSongsBrowseId?.let { BrowseEndpoint(browseId = it) },
        )
    }
    return ArtistPage(
        artist = ArtistItem(
            id = artistId,
            title = name ?: artistId,
            thumbnail = thumbnailUrl,
            channelId = artistId.takeIf { it.startsWith("UC") },
            playEndpoint = null,
            shuffleEndpoint = shuffle?.toWatchEndpoint(),
            radioEndpoint = radio?.toWatchEndpoint(),
        ),
        sections = listOfNotNull(topSongsSection) + sections.mapNotNull { it.toArtistSection() },
        description = description,
        subscriberCountText = subscriberCountText,
        monthlyListenerCount = monthlyListenerCount,
        subscribed = subscription?.subscribed,
    )
}

/**
 * A library Albums shelf card as a Mochy library Album. Null without a
 * browseId. The year comes off the subtitle ("Album • Artist • 2024");
 * the artist credit becomes the album's artist list.
 */
fun ShelfItem.toMochyAlbum(): Album? {
    val browseId = browseId ?: return null
    return Album(
        album = AlbumEntity(
            id = browseId,
            title = title,
            year = AlbumYearRegex.find(subtitle)?.value?.toIntOrNull(),
            thumbnailUrl = thumbnailUrl,
            songCount = 0,
            duration = 0,
        ),
        artists = artistCredit().map { ArtistEntity(id = it.id ?: it.name, name = it.name) },
    )
}

/**
 * A mapped library song with this session's like state and download state
 * overlaid. LikeState's override wins over whatever the row said (an
 * explicit INDIFFERENT/DISLIKE clears a liked flag, so a toggle paints at
 * once); a downloaded copy marks the song downloaded with its saved uri.
 */
fun Song.withLikeAndDownloadState(): Song {
    val liked = when (LikeState.overrides.value[id]) {
        LikeStatus.LIKE -> true
        LikeStatus.INDIFFERENT, LikeStatus.DISLIKE -> false
        null -> song.liked
    }
    val uri = Downloads.saved.value[id]
    return copy(
        song = song.copy(
            liked = liked,
            isDownloaded = uri != null,
            localUri = uri ?: song.localUri,
        ),
    )
}

/** Mochy's db-song explicit filter (extensions/filterExplicit): drops explicit songs when asked. */
fun List<Song>.filterExplicit(hide: Boolean): List<Song> =
    if (hide) filterNot { it.song.explicit } else this

/** Mochy's db-album explicit filter (extensions/filterExplicitAlbums). */
fun List<Album>.filterExplicitAlbums(hide: Boolean): List<Album> =
    if (hide) filterNot { it.album.explicit } else this
