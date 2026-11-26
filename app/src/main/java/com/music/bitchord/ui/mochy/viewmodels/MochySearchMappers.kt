// MochySearchMappers — the Mochy engine's search results as the Modern
// search screens read them (two-engine architecture, Tahap 4).
//
// Riki's decision: Modern search uses Mochy search, Classic keeps its own.
// The Modern screens were ported from Mochy and read the CW-side copies of
// Mochy's innertube models (ui.mochy.models.innertube); the engine in
// :mochyengine returns the originals (com.music.innertube.models). The two
// families are field-for-field twins, so this is a straight copy — no
// ChordWave repository in between any more.

package com.music.bitchord.ui.mochy.viewmodels

import com.music.bitchord.ui.mochy.models.innertube.Album
import com.music.bitchord.ui.mochy.models.innertube.AlbumItem
import com.music.bitchord.ui.mochy.models.innertube.Artist
import com.music.bitchord.ui.mochy.models.innertube.ArtistItem
import com.music.bitchord.ui.mochy.models.innertube.PlaylistItem
import com.music.bitchord.ui.mochy.models.innertube.SearchFilter
import com.music.bitchord.ui.mochy.models.innertube.SearchSummary
import com.music.bitchord.ui.mochy.models.innertube.SearchSummaryPage
import com.music.bitchord.ui.mochy.models.innertube.SongItem
import com.music.bitchord.ui.mochy.models.innertube.WatchEndpoint
import com.music.bitchord.ui.mochy.models.innertube.YTItem
import com.music.innertube.YouTube
import com.music.innertube.models.Album as EngineAlbum
import com.music.innertube.models.AlbumItem as EngineAlbumItem
import com.music.innertube.models.Artist as EngineArtist
import com.music.innertube.models.ArtistItem as EngineArtistItem
import com.music.innertube.models.PlaylistItem as EnginePlaylistItem
import com.music.innertube.models.SongItem as EngineSongItem
import com.music.innertube.models.WatchEndpoint as EngineWatchEndpoint
import com.music.innertube.models.YTItem as EngineYTItem
import com.music.innertube.pages.SearchSummaryPage as EngineSearchSummaryPage

/** The Modern filter as the engine's own filter (identical params strings). */
fun SearchFilter.toEngineFilter(): YouTube.SearchFilter = YouTube.SearchFilter(value)

fun EngineWatchEndpoint?.toModernEndpoint(): WatchEndpoint? = this?.let { endpoint ->
    WatchEndpoint(
        videoId = endpoint.videoId,
        playlistId = endpoint.playlistId,
        playlistSetVideoId = endpoint.playlistSetVideoId,
        params = endpoint.params,
        index = endpoint.index,
        watchEndpointMusicSupportedConfigs = endpoint.watchEndpointMusicSupportedConfigs?.let { configs ->
            WatchEndpoint.WatchEndpointMusicSupportedConfigs(
                watchEndpointMusicConfig = WatchEndpoint.WatchEndpointMusicSupportedConfigs.WatchEndpointMusicConfig(
                    musicVideoType = configs.watchEndpointMusicConfig.musicVideoType,
                ),
            )
        },
    )
}

private fun EngineArtist.toModernArtist(): Artist = Artist(name = name, id = id)

private fun EngineAlbum?.toModernAlbum(): Album? = this?.let { Album(name = it.name, id = it.id) }

fun EngineYTItem.toModernItem(): YTItem = when (this) {
    is EngineSongItem -> SongItem(
        id = id,
        title = title,
        artists = artists.map { it.toModernArtist() },
        album = album.toModernAlbum(),
        duration = duration,
        musicVideoType = musicVideoType,
        chartPosition = chartPosition,
        chartChange = chartChange,
        thumbnail = thumbnail,
        explicit = explicit,
        endpoint = endpoint.toModernEndpoint(),
        setVideoId = setVideoId,
        libraryAddToken = libraryAddToken,
        libraryRemoveToken = libraryRemoveToken,
        historyRemoveToken = historyRemoveToken,
    )
    is EngineAlbumItem -> AlbumItem(
        browseId = browseId,
        playlistId = playlistId,
        id = id,
        title = title,
        artists = artists?.map { it.toModernArtist() },
        year = year,
        thumbnail = thumbnail,
        explicit = explicit,
        description = description,
    )
    is EnginePlaylistItem -> PlaylistItem(
        id = id,
        title = title,
        author = author?.toModernArtist(),
        songCountText = songCountText,
        thumbnail = thumbnail,
        playEndpoint = playEndpoint.toModernEndpoint(),
        shuffleEndpoint = shuffleEndpoint.toModernEndpoint(),
        radioEndpoint = radioEndpoint.toModernEndpoint(),
        isEditable = isEditable,
    )
    is EngineArtistItem -> ArtistItem(
        id = id,
        title = title,
        thumbnail = thumbnail,
        channelId = channelId,
        playEndpoint = playEndpoint.toModernEndpoint(),
        shuffleEndpoint = shuffleEndpoint.toModernEndpoint(),
        radioEndpoint = radioEndpoint.toModernEndpoint(),
    )
}

fun EngineSearchSummaryPage.toModernSummaryPage(): SearchSummaryPage = SearchSummaryPage(
    summaries = summaries.map { section ->
        SearchSummary(
            title = section.title,
            items = section.items.map { it.toModernItem() },
        )
    },
)
