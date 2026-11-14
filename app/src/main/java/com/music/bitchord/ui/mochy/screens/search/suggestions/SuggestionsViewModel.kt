// Ported from Mochy Music (GPL-3.0): ui/screens/search/suggestions/SuggestionsViewModel.kt
// The Apple Music charts behind the Modern Search tab's suggestions
// (top songs / trending artists / top albums / top videos), re-implemented
// over ChordWave sources. The refresh() half is Mochy's verbatim over the
// ported AppleMusicScraper; the playback/navigation half searches CW
// (YtMusicRepository.search) instead of Mochy's engine.
//
// Adaptations vs Mochy:
// - Plain ViewModel with a no-arg constructor, NOT @HiltViewModel: the
//   consuming screen obtains it via viewModel(), and it takes no injected
//   deps (Mochy's was @HiltViewModel with an empty @Inject constructor).
// - playTrack/playVideo/navigateToArtist/navigateToAlbum take
//   MochyPlayerConnection / MochyNavigator instead of Mochy's player and
//   navigation types. The best-match logic is Mochy's
//   verbatim (exact title+artist -> title-contains -> artist-only -> first),
//   run over SongItems mapped from CW search rows. Playback goes through
//   YouTubeQueue(WatchEndpoint(videoId)) and MochyPlayerConnection.playQueue,
//   which is stubbed until Phase 3 — the taps are no-ops until then.
// - refresh() skips when the region is already loaded unless force (per the
//   phase2-search contract); Mochy re-fetched on every repeat refresh.
// - Log.e replaced with Timber, the adapter house style.

package com.music.bitchord.ui.mochy.screens.search.suggestions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.model.SearchFilter
import com.music.bitchord.ui.mochy.models.innertube.AlbumItem
import com.music.bitchord.ui.mochy.models.innertube.ArtistItem
import com.music.bitchord.ui.mochy.models.innertube.SongItem
import com.music.bitchord.ui.mochy.models.innertube.WatchEndpoint
import com.music.bitchord.ui.mochy.navigation.MochyNavigator
import com.music.bitchord.ui.mochy.playback.MochyPlayerConnection
import com.music.bitchord.ui.mochy.playback.queues.YouTubeQueue
import com.music.bitchord.ui.mochy.viewmodels.toYTItem
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

class SuggestionsViewModel : ViewModel() {
    private var currentLoadedRegion: String? = null

    private val _suggestionTracks = MutableStateFlow<List<SuggestionTrack>?>(null)
    val suggestionTracks: StateFlow<List<SuggestionTrack>?> = _suggestionTracks

    private val _suggestionArtists = MutableStateFlow<List<SuggestionArtist>?>(null)
    val suggestionArtists: StateFlow<List<SuggestionArtist>?> = _suggestionArtists

    private val _suggestionAlbums = MutableStateFlow<List<SuggestionAlbum>?>(null)
    val suggestionAlbums: StateFlow<List<SuggestionAlbum>?> = _suggestionAlbums

    private val _suggestionVideos = MutableStateFlow<List<SuggestionTrack>?>(null)
    val suggestionVideos: StateFlow<List<SuggestionTrack>?> = _suggestionVideos

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _isManualLoading = MutableStateFlow(false)
    val isManualLoading: StateFlow<Boolean> = _isManualLoading

    fun refresh(countryCode: String = "system", force: Boolean = false) {
        val resolvedCode = if (countryCode == "system") {
            Locale.getDefault().country.lowercase()
        } else {
            countryCode.lowercase()
        }

        if (!force && currentLoadedRegion == resolvedCode) return

        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            if (force) _isManualLoading.value = true

            if (currentLoadedRegion != resolvedCode || force) {
                _suggestionTracks.value = null
                _suggestionArtists.value = null
                _suggestionAlbums.value = null
                _suggestionVideos.value = null
            }

            try {
                coroutineScope {

                    launch {
                        try {
                            val tracks = AppleMusicScraper.fetchTopSongs(resolvedCode)
                            if (tracks.isNotEmpty()) {
                                _suggestionTracks.value = tracks
                                _suggestionArtists.value = AppleMusicScraper.getTrendingArtists(tracks)
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "SuggestionsViewModel: failed to fetch songs")
                        }
                    }

                    launch {
                        try {
                            val albums = AppleMusicScraper.fetchTopAlbums(resolvedCode)
                            if (albums.isNotEmpty()) {
                                _suggestionAlbums.value = albums
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "SuggestionsViewModel: failed to fetch albums")
                        }
                    }

                    launch {
                        try {
                            val videos = AppleMusicScraper.fetchTopVideos(resolvedCode)
                            if (videos.isNotEmpty()) {
                                _suggestionVideos.value = videos
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "SuggestionsViewModel: failed to fetch videos")
                        }
                    }
                }

                currentLoadedRegion = resolvedCode
            } catch (e: Exception) {
                Timber.e(e, "SuggestionsViewModel: failed to fetch suggestions")
            } finally {
                _isLoading.value = false
                _isManualLoading.value = false
            }
        }
    }

    fun playTrack(track: SuggestionTrack, playerConnection: MochyPlayerConnection?) {
        viewModelScope.launch(Dispatchers.IO) {
            val query = "${track.title} ${track.artist}"
            YtMusicRepository.search(query, SearchFilter.SONGS)
                .onSuccess { rows ->
                    val songs = rows.mapNotNull { it.toYTItem() }.filterIsInstance<SongItem>()

                    val bestMatch = songs.firstOrNull { s ->
                        s.title.equals(track.title, ignoreCase = true) &&
                            s.artists.any { a -> track.artist.contains(a.name, ignoreCase = true) }
                    } ?:

                    songs.firstOrNull { s ->
                        s.title.contains(track.title, ignoreCase = true) &&
                            s.artists.any { a -> track.artist.contains(a.name, ignoreCase = true) }
                    } ?:

                    songs.firstOrNull { s ->
                        s.artists.any { a -> track.artist.contains(a.name, ignoreCase = true) }
                    } ?:

                    songs.firstOrNull()

                    if (bestMatch != null) {
                        withContext(Dispatchers.Main) {
                            playerConnection?.playQueue(YouTubeQueue(WatchEndpoint(videoId = bestMatch.id)))
                        }
                    }
                }
                .onFailure { Timber.w(it, "Suggestions: search failed for \"$query\"") }
        }
    }

    fun navigateToArtist(artist: SuggestionArtist, navigator: MochyNavigator) {
        viewModelScope.launch(Dispatchers.IO) {
            YtMusicRepository.search(artist.name, SearchFilter.ARTISTS)
                .onSuccess { rows ->
                    val firstArtist =
                        rows.mapNotNull { it.toYTItem() }.filterIsInstance<ArtistItem>().firstOrNull()
                    if (firstArtist != null) {
                        withContext(Dispatchers.Main) {
                            navigator.navigate("artist/${firstArtist.id}")
                        }
                    }
                }
                .onFailure { Timber.w(it, "Suggestions: artist search failed for \"${artist.name}\"") }
        }
    }

    fun navigateToAlbum(album: SuggestionAlbum, navigator: MochyNavigator) {
        viewModelScope.launch(Dispatchers.IO) {
            val query = "${album.title} ${album.artist}"
            YtMusicRepository.search(query, SearchFilter.ALBUMS)
                .onSuccess { rows ->
                    val firstAlbum =
                        rows.mapNotNull { it.toYTItem() }.filterIsInstance<AlbumItem>().firstOrNull()
                    if (firstAlbum != null) {
                        withContext(Dispatchers.Main) {
                            navigator.navigate("album/${firstAlbum.id}")
                        }
                    }
                }
                .onFailure { Timber.w(it, "Suggestions: album search failed for \"$query\"") }
        }
    }

    fun playVideo(video: SuggestionTrack, playerConnection: MochyPlayerConnection?) {
        viewModelScope.launch(Dispatchers.IO) {
            val query = "${video.title} ${video.artist}"
            YtMusicRepository.search(query, SearchFilter.SONGS)
                .onSuccess { rows ->
                    val songs = rows.mapNotNull { it.toYTItem() }.filterIsInstance<SongItem>()

                    val bestMatch = songs.firstOrNull { s ->
                        s.title.equals(video.title, ignoreCase = true) &&
                            s.artists.any { a -> video.artist.contains(a.name, ignoreCase = true) }
                    } ?:
                    songs.firstOrNull { s ->
                        s.title.contains(video.title, ignoreCase = true) &&
                            s.artists.any { a -> video.artist.contains(a.name, ignoreCase = true) }
                    } ?:
                    songs.firstOrNull { s ->
                        s.artists.any { a -> video.artist.contains(a.name, ignoreCase = true) }
                    } ?:
                    songs.firstOrNull()

                    if (bestMatch != null) {
                        withContext(Dispatchers.Main) {
                            playerConnection?.playQueue(YouTubeQueue(WatchEndpoint(videoId = bestMatch.id)))
                        }
                    }
                }
                .onFailure { Timber.w(it, "Suggestions: search failed for \"$query\"") }
        }
    }
}
