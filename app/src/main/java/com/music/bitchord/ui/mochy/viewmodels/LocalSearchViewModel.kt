// Ported from Mochy Music (GPL-3.0): viewmodels/LocalSearchViewModel.kt
// Local library search for the Modern Search tab, re-implemented over
// ChordWave sources. Mochy's flow is kept: empty query -> empty result;
// ALL -> 3-item previews per kind (PREVIEW_SIZE = 3); a specific filter ->
// the full list for that kind; songs filtered by HideVideoSongsKey; the
// result is grouped by kind into LocalSearchResult.map. LocalFilter and
// LocalSearchResult live in this file, as in Mochy.
//
// Adaptations vs Mochy:
// - No MusicDatabase: ChordWave has no local library search at all (the
//   Classic library tab browses, it never text-searches), so the searchable
//   catalogue is assembled from CW's local-state sources (DATA-MAPPING §4):
//   the all-time ListeningStats charts (played songs / artists / albums —
//   the same source the A1 offline preview uses, and cheap per keystroke
//   because the stats store caches the summary) plus the local-file
//   playlists (OfflinePlaylists: their songs, and the artists/albums those
//   songs name). A track that was never played and sits in no local
//   playlist cannot match — same limitation as the A1 offline preview.
// - Downloads are NOT searched: Downloads.saved is videoId -> uri with no
//   public title list (savedMetadata is internal), so a downloaded song
//   matches only once it has been played (stats) or added to a playlist.
// - Playlists are the local-file playlists only (incl. the liked list).
//   The signed-in account's YouTube playlists are server data, not local
//   library, and Mochy's local search never crossed the network either.
// - The playlists flow is combined in, so creating/renaming a local
//   playlist re-runs the search without retyping.

package com.music.bitchord.ui.mochy.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.bitchord.data.OfflinePlaylist
import com.music.bitchord.data.OfflinePlaylists
import com.music.bitchord.data.stats.ListeningStats
import com.music.bitchord.data.stats.ReplayPeriod
import com.music.bitchord.ui.mochy.constants.HideVideoSongsKey
import com.music.bitchord.ui.mochy.models.Album
import com.music.bitchord.ui.mochy.models.AlbumEntity
import com.music.bitchord.ui.mochy.models.Artist
import com.music.bitchord.ui.mochy.models.ArtistEntity
import com.music.bitchord.ui.mochy.models.LocalItem
import com.music.bitchord.ui.mochy.models.Playlist
import com.music.bitchord.ui.mochy.models.Song
import com.music.bitchord.ui.mochy.models.toMochySong
import com.music.bitchord.ui.mochy.utils.dataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import timber.log.Timber

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LocalSearchViewModel @Inject constructor(
    @ApplicationContext context: Context,
) : ViewModel() {
    val query = MutableStateFlow("")
    val filter = MutableStateFlow(LocalFilter.ALL)

    val result: StateFlow<LocalSearchResult> =
        combine(
            query,
            filter,
            context.dataStore.data.map { it[HideVideoSongsKey] ?: false }.distinctUntilChanged(),
            OfflinePlaylists.playlists,
        ) { query, filter, hideVideoSongs, playlists ->
            SearchInputs(query, filter, hideVideoSongs, playlists)
        }.flatMapLatest { (query, filter, hideVideoSongs, playlists) ->
            if (query.isEmpty()) {
                flowOf(LocalSearchResult("", filter, emptyMap()))
            } else {
                flow { emit(searchLocal(query, filter, hideVideoSongs, playlists)) }
            }
        }.stateIn(
            viewModelScope,
            SharingStarted.Lazily,
            LocalSearchResult("", filter.value, emptyMap()),
        )

    /**
     * The local search itself. Songs match on title or artist (the A1
     * offline preview's rule); artists, albums and playlists on title.
     * Stats songs and offline-playlist songs are merged and deduped by
     * videoId, so a played local file shows once, not twice.
     */
    private suspend fun searchLocal(
        text: String,
        filter: LocalFilter,
        hideVideoSongs: Boolean,
        playlists: List<OfflinePlaylist>,
    ): LocalSearchResult {
        val summary = runCatching { ListeningStats.summary(ReplayPeriod.ALL_TIME) }.getOrNull()
        if (summary == null) {
            Timber.w("LocalSearch: ListeningStats.summary(ALL_TIME) failed; searching offline playlists only")
        }
        val offlineSongs = playlists.flatMap { it.songs }

        val songs = (
            summary?.songs.orEmpty().map { it.song } + offlineSongs
        ).distinctBy { it.videoId }
            .filter { it.title.contains(text, ignoreCase = true) || it.artist.contains(text, ignoreCase = true) }
            .map { it.toMochySong() }
            .let { found -> if (hideVideoSongs) found.filterNot { it.song.isVideo } else found }

        val artists = (
            summary?.artists.orEmpty()
                .filter { it.title.contains(text, ignoreCase = true) }
                .map { it.toMochyArtist() } +
            offlineSongs
                .map { it.artist to it.artistId }
                .distinctBy { it.first.lowercase() }
                .filter { (name, _) -> name.contains(text, ignoreCase = true) }
                .map { (name, id) -> Artist(artist = ArtistEntity(id = id ?: name, name = name), songCount = 0) }
        ).distinctBy { it.title.lowercase() }

        val albums = (
            summary?.albums.orEmpty()
                .filter { it.title.contains(text, ignoreCase = true) }
                .map { it.toMochyAlbum() } +
            offlineSongs
                .mapNotNull { song ->
                    val albumId = song.albumId ?: return@mapNotNull null
                    val albumName = song.albumName?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    Album(
                        album = AlbumEntity(id = albumId, title = albumName, songCount = 0, duration = 0),
                        artists = listOf(ArtistEntity(id = song.artistId ?: song.artist, name = song.artist)),
                    )
                }
                .filter { it.title.contains(text, ignoreCase = true) }
        ).distinctBy { it.id.lowercase() }

        val localPlaylists = playlists
            .filter { it.title.contains(text, ignoreCase = true) }
            .map { it.toMochyPlaylist() }

        val list: List<LocalItem> = when (filter) {
            LocalFilter.ALL ->
                songs.take(PREVIEW_SIZE) + albums.take(PREVIEW_SIZE) +
                    artists.take(PREVIEW_SIZE) + localPlaylists.take(PREVIEW_SIZE)
            LocalFilter.SONG -> songs
            LocalFilter.ALBUM -> albums
            LocalFilter.ARTIST -> artists
            LocalFilter.PLAYLIST -> localPlaylists
        }
        return LocalSearchResult(
            query = text,
            filter = filter,
            map = list.groupBy {
                when (it) {
                    is Song -> LocalFilter.SONG
                    is Album -> LocalFilter.ALBUM
                    is Artist -> LocalFilter.ARTIST
                    is Playlist -> LocalFilter.PLAYLIST
                }
            },
        )
    }

    companion object {
        const val PREVIEW_SIZE = 3
    }
}

/** One search run's inputs, so the combine stays a single value downstream. */
private data class SearchInputs(
    val query: String,
    val filter: LocalFilter,
    val hideVideoSongs: Boolean,
    val playlists: List<OfflinePlaylist>,
)

enum class LocalFilter {
    ALL,
    SONG,
    ALBUM,
    ARTIST,
    PLAYLIST,
}

data class LocalSearchResult(
    val query: String,
    val filter: LocalFilter,
    val map: Map<LocalFilter, List<LocalItem>>,
)
