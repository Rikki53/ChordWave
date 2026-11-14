// ShelfItemMapper.kt — the one hard mapping in the HomeViewModel adapter:
// ChordWave's ShelfItem carries no per-item type discriminator (only
// title/subtitle/thumbnailUrl/videoId/browseId/isVideo), so the YTItem type
// has to be INFERRED (DATA-MAPPING §1 row 16, §6 Q3, coordinator decision §7.3).
//
// Rules (v1, verify against real FEmusic_home responses in Phase 6):
//   videoId present            -> SongItem (a playable row is a song)
//   browseId MPRE...           -> AlbumItem
//   browseId UC...             -> ArtistItem
//   browseId VL/PL/OLAK/RD...  -> PlaylistItem (VL prefix stripped from the id,
//                                 Mochy convention)
//   anything else (FEmusic_*, SE, ...) -> null (dropped)
// Tie-break: a card carrying BOTH a videoId and a browseId is a song, unless
// it sits in a CARDS-layout shelf, where the card identity (album/artist/
// playlist) wins. In practice YouTube sends one or the other, so the tie
// almost never fires — the layout is the hint the coordinator asked for.
//
// SongItem fields CW cannot fill stay null/default: duration, explicit
// (false), album, endpoints. musicVideoType is MUSIC_VIDEO_TYPE_OMV exactly
// when ShelfItem.isVideo, which is what ArtistVideos.feedShelfKind /
// shelves() key their video detection on — a null means "audio track".

package com.music.bitchord.ui.mochy.viewmodels

import com.music.bitchord.data.innertube.InnertubeParser
import com.music.bitchord.data.model.ShelfItem
import com.music.bitchord.data.model.ShelfLayout
import com.music.bitchord.ui.mochy.models.innertube.AlbumItem
import com.music.bitchord.ui.mochy.models.innertube.Artist as YtArtist
import com.music.bitchord.ui.mochy.models.innertube.ArtistItem
import com.music.bitchord.ui.mochy.models.innertube.SongItem
import com.music.bitchord.ui.mochy.models.innertube.WatchEndpoint.WatchEndpointMusicSupportedConfigs.WatchEndpointMusicConfig.Companion.MUSIC_VIDEO_TYPE_OMV
import com.music.bitchord.ui.mochy.models.innertube.YTItem

/**
 * The credited artists out of a shelf card's subtitle, which reads
 * "Song • Artist A, Artist B" rather than just the artist. Reuses CW's own
 * [InnertubeParser.artistFromSubtitle] (public on the shared module's parser;
 * it drops the "Song"/"Single"/"Album"/"Playlist" type word, tallies and
 * durations) and then splits the remaining credit on ", " and " & ". Ids are
 * unknowable from a bare subtitle, so every artist id is null.
 */
fun ShelfItem.artistCredit(): List<YtArtist> {
    if (subtitle.isBlank()) return emptyList()
    val credit = InnertubeParser.artistFromSubtitle(subtitle)
    if (credit.isBlank()) return emptyList()
    return credit.split(Regex("\\s*,\\s*|\\s+&\\s+"))
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .map { YtArtist(name = it, id = null) }
}

/** The single author credit of a playlist card's subtitle ("Playlist • YouTube Music"). */
fun ShelfItem.authorCredit(): YtArtist? = artistCredit().firstOrNull()

fun ShelfItem.toYTItem(layout: ShelfLayout = ShelfLayout.CARDS): YTItem? {
    val videoId = videoId
    val browseId = browseId

    // A card with both ids is a song row, unless it sits in a CARDS shelf,
    // where the card is the collection it points at.
    if (videoId != null && (browseId == null || layout != ShelfLayout.CARDS)) {
        return SongItem(
            id = videoId,
            title = title,
            artists = artistCredit(),
            album = null,
            duration = null,
            musicVideoType = MUSIC_VIDEO_TYPE_OMV.takeIf { isVideo },
            thumbnail = thumbnailUrl ?: "",
            explicit = false,
            endpoint = null,
        )
    }

    if (browseId == null) return null
    return when {
        browseId.startsWith("MPRE") -> AlbumItem(
            browseId = browseId,
            playlistId = "",
            title = title,
            artists = artistCredit().takeIf { it.isNotEmpty() },
            year = null,
            thumbnail = thumbnailUrl ?: "",
            explicit = false,
        )

        browseId.startsWith("UC") -> ArtistItem(
            id = browseId,
            title = title,
            thumbnail = thumbnailUrl,
            channelId = browseId,
            playEndpoint = null,
            shuffleEndpoint = null,
            radioEndpoint = null,
        )

        browseId.startsWith("VL") ||
            browseId.startsWith("PL") ||
            browseId.startsWith("OLAK") ||
            browseId.startsWith("RD") -> toPlaylistItem()

        // FEmusic_*, SE, and anything else we cannot type: drop it rather
        // than guess a type Home would render wrong.
        else -> null
    }
}
