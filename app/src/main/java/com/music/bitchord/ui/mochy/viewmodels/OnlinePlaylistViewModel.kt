// Ported from Mochy Music (GPL-3.0): viewmodels/OnlinePlaylistViewModel.kt
// One browseSongs fetch, then a proactive background loop follows the
// continuation until it runs out — Mochy's init + loader, verbatim in
// shape. Songs are .distinctBy { it.id } and passed through the staged
// filterVideoSongs with HideVideoSongsKey (default false).
//
// Adaptations vs Mochy:
// - No saved-state handle: the route arg comes from load(playlistId), which
//   the coordinator calls from the screen (screens parse
//   navigator.currentDetail). `playlistId` is therefore a `var ... by
//   mutableStateOf("")` set by load(), not Mochy's val — the screen only
//   ever reads it as a String. The VM fetches nothing before the first
//   load() call. Re-calling load() with the same id is a no-op once the
//   page is loaded (or still loading); re-calling after a failure retries.
// - No MusicDatabase: `dbPlaylist` is the page's own library state dressed
//   as a Playlist (bookmarkedAt set exactly when YouTube says the release
//   is saved), because ChordWave has no local playlist table — see
//   NEEDS.md C2. Null when the page offers no save button (signed out, or
//   an unsaveable release); the header then reads "not saved".
// - `relatedItems` is the page's suggested songs (SongPage.suggested —
//   tracks YouTube offers to round the playlist out), mapped to SongItems.
//   Mochy's related section also held playlists; ChordWave's parser
//   exposes no related-playlists shelf on this page.
// - The PlaylistItem header is built from SongPage.header: the author is
//   parsed out of the header subtitle (see toPlaylistItem), isEditable is
//   the page's owned flag, and the play/shuffle/radio endpoints are null —
//   playback goes through YouTubePlaylistQueue, never the endpoints.
// - toggleSaved() is a REQUIRED ADDITION (not in Mochy's VM): the header
//   save button. Mochy imported the playlist into its database on first
//   save; only the online half runs here — setSaved on the id the page
//   named — and the local flag follows it.
// - loadMoreSongs() / retry() / error are kept for parity with Mochy; the
//   screen never calls them (the proactive loop pages).

package com.music.bitchord.ui.mochy.viewmodels

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.innertube.InnertubeParser
import com.music.bitchord.ui.mochy.constants.HideVideoSongsKey
import com.music.bitchord.ui.mochy.models.Playlist
import com.music.bitchord.ui.mochy.models.PlaylistEntity
import com.music.bitchord.ui.mochy.models.innertube.Artist as YtArtist
import com.music.bitchord.ui.mochy.models.innertube.PlaylistItem
import com.music.bitchord.ui.mochy.models.innertube.SongItem
import com.music.bitchord.ui.mochy.models.innertube.YTItem
import com.music.bitchord.ui.mochy.models.innertube.filterVideoSongs
import com.music.bitchord.ui.mochy.utils.PreferencesCache
import com.music.bitchord.ui.mochy.utils.dataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDateTime
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber

@HiltViewModel
class OnlinePlaylistViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : ViewModel() {
    // B1 calls for a String val; a var by mutableStateOf reads as a String
    // and lets load() update it when the host hands over a new playlist.
    var playlistId by mutableStateOf("")
        private set

    val playlist = MutableStateFlow<PlaylistItem?>(null)
    val playlistSongs = MutableStateFlow<List<SongItem>>(emptyList())
    val relatedItems = MutableStateFlow<List<YTItem>>(emptyList())

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private val _dbPlaylist = MutableStateFlow<Playlist?>(null)
    val dbPlaylist: StateFlow<Playlist?> = _dbPlaylist.asStateFlow()

    var continuation: String? = null
        private set

    private var proactiveLoadJob: Job? = null

    /** The id the current page belongs to; null until load() is called. */
    private var loadedId: String? = null

    /** The id setSaved must be given: the one the page itself named. */
    private var libraryPlaylistId: String? = null

    // Read live on every fetch, like HomeViewModel's flags, so a Settings
    // toggle takes effect without an app restart.
    private val hideVideoSongs: Boolean
        get() = PreferencesCache.current(context.dataStore)[HideVideoSongsKey] ?: false

    /**
     * (Re)initialises the VM for a route playlist id. The coordinator
     * patches the ported screen to call this; the VM fetches nothing
     * before the first call.
     */
    fun load(playlistId: String) {
        val id = playlistId.trim()
        if (id.isEmpty()) return
        if (id == loadedId && (playlist.value != null || _isLoading.value)) return
        loadedId = id
        this.playlistId = id
        fetchInitialPlaylistData()
    }

    private fun fetchInitialPlaylistData() {
        val id = loadedId ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            _error.value = null
            continuation = null
            proactiveLoadJob?.cancel()
            playlist.value = null
            playlistSongs.value = emptyList()
            relatedItems.value = emptyList()
            _dbPlaylist.value = null
            libraryPlaylistId = null

            val browseId = browseIdForPlaylist(id)
            if (browseId == null) {
                _error.value = "Not an online playlist"
                _isLoading.value = false
                return@launch
            }

            YtMusicRepository.browseSongs(browseId)
                .onSuccess { page ->
                    if (id != loadedId) return@onSuccess
                    playlist.value = page.toPlaylistItem(id)
                    playlistSongs.value = applySongFilters(page.songs.map { it.toSongItem() })
                    relatedItems.value = page.suggested.map { it.toSongItem() }.distinctBy { it.id }
                    continuation = page.continuation
                    libraryPlaylistId = page.library?.playlistId
                    _dbPlaylist.value = page.toDbPlaylist(id)
                    _isLoading.value = false
                    if (continuation != null) {
                        startProactiveBackgroundLoading()
                    }
                }.onFailure { throwable ->
                    if (id != loadedId) return@onFailure
                    _error.value = throwable.message ?: "Failed to load playlist"
                    _isLoading.value = false
                    Timber.w(throwable, "OnlinePlaylist: failed to load $id")
                }
        }
    }

    private fun startProactiveBackgroundLoading() {
        proactiveLoadJob?.cancel()
        proactiveLoadJob = viewModelScope.launch(Dispatchers.IO) {
            val id = loadedId ?: return@launch
            var currentProactiveToken = continuation
            while (currentProactiveToken != null && isActive) {
                if (_isLoadingMore.value) {
                    break
                }

                YtMusicRepository.moreSongs(currentProactiveToken)
                    .onSuccess { page ->
                        if (id != loadedId) return@onSuccess
                        val currentSongs = playlistSongs.value.toMutableList()
                        currentSongs.addAll(page.songs.map { it.toSongItem() })
                        playlistSongs.value = applySongFilters(currentSongs)
                        currentProactiveToken = page.continuation
                        this@OnlinePlaylistViewModel.continuation = currentProactiveToken
                    }.onFailure { throwable ->
                        Timber.w(throwable, "OnlinePlaylist: proactive paging failed for $id")
                        currentProactiveToken = null
                    }
            }
        }
    }

    fun loadMoreSongs() {
        if (_isLoadingMore.value) return

        val tokenForManualLoad = continuation ?: return

        proactiveLoadJob?.cancel()
        _isLoadingMore.value = true

        viewModelScope.launch(Dispatchers.IO) {
            val id = loadedId
            YtMusicRepository.moreSongs(tokenForManualLoad)
                .onSuccess { page ->
                    if (id != loadedId) return@onSuccess
                    val currentSongs = playlistSongs.value.toMutableList()
                    currentSongs.addAll(page.songs.map { it.toSongItem() })
                    playlistSongs.value = applySongFilters(currentSongs)
                    continuation = page.continuation
                }.onFailure { throwable ->
                    Timber.w(throwable, "OnlinePlaylist: loadMore failed")
                }
            _isLoadingMore.value = false

            if (continuation != null && isActive) {
                startProactiveBackgroundLoading()
            }
        }
    }

    fun retry() {
        if (loadedId == null) return
        proactiveLoadJob?.cancel()
        fetchInitialPlaylistData()
    }

    /**
     * REQUIRED ADDITION (not in Mochy's VM): the header save button.
     * Mochy imported the playlist into its database on first save; only
     * the online half runs here — setSaved on the id the page named —
     * and the local flag follows it.
     */
    fun toggleSaved() {
        val id = loadedId ?: return
        val currentlySaved = _dbPlaylist.value?.playlist?.bookmarkedAt != null
        viewModelScope.launch(Dispatchers.IO) {
            YtMusicRepository.setSaved(libraryPlaylistId ?: id, !currentlySaved)
                .onSuccess {
                    if (id != loadedId) return@onSuccess
                    val db = _dbPlaylist.value
                    _dbPlaylist.value = if (db != null) {
                        db.copy(playlist = db.playlist.localToggleLike())
                    } else if (!currentlySaved) {
                        // The page offered no save button, so no record
                        // existed — one is minted now that YouTube says
                        // the release is saved.
                        Playlist(
                            playlist = PlaylistEntity(
                                id = id,
                                name = playlist.value?.title ?: id,
                                browseId = browseIdForPlaylist(id),
                                bookmarkedAt = LocalDateTime.now(),
                                isEditable = playlist.value?.isEditable ?: false,
                                thumbnailUrl = playlist.value?.thumbnail,
                            ),
                            songCount = playlistSongs.value.size,
                            songThumbnails = playlistSongs.value.take(4).map { it.thumbnail },
                        )
                    } else {
                        null
                    }
                }
                .onFailure { Timber.w(it, "OnlinePlaylist: toggleSaved failed for $id") }
        }
    }

    private fun applySongFilters(songs: List<SongItem>): List<SongItem> =
        songs
            .distinctBy { it.id }
            .filterVideoSongs(hideVideoSongs)

    /**
     * The page header as the PlaylistItem the screen reads. The author is
     * parsed out of the header subtitle ("Playlist • Author • N songs") —
     * InnertubeParser.artistFromSubtitle drops the type word, tallies and
     * durations; when the subtitle carries no such credit the author stays
     * null and the header shows no creator row.
     */
    private fun YtMusicRepository.SongPage.toPlaylistItem(id: String): PlaylistItem {
        val subtitle = header?.subtitle.orEmpty()
        val authorName = subtitle
            .takeIf { " • " in it }
            ?.let { InnertubeParser.artistFromSubtitle(it) }
            ?.takeIf { it.isNotBlank() && it != subtitle && it.length <= 80 }
        return PlaylistItem(
            id = id,
            title = header?.title ?: id,
            author = authorName?.let { YtArtist(name = it, id = null) },
            songCountText = null,
            thumbnail = header?.thumbnailUrl,
            playEndpoint = null,
            shuffleEndpoint = null,
            radioEndpoint = null,
            isEditable = owned ?: false,
        )
    }

    /**
     * The saved-copy record the header's save button reads. ChordWave has
     * no local playlist table (see NEEDS.md C2), so this is the page's own
     * library state dressed as a Playlist: bookmarkedAt is set exactly
     * when YouTube says the release is saved. Null when the page offers no
     * save button (signed out, or an unsaveable release) — the header then
     * reads "not saved".
     */
    private fun YtMusicRepository.SongPage.toDbPlaylist(id: String): Playlist? {
        val library = library ?: return null
        return Playlist(
            playlist = PlaylistEntity(
                id = id,
                name = header?.title ?: id,
                browseId = browseIdForPlaylist(id),
                bookmarkedAt = if (library.saved) LocalDateTime.now() else null,
                isEditable = owned ?: false,
                thumbnailUrl = header?.thumbnailUrl,
            ),
            songCount = songs.size,
            songThumbnails = songs.take(4).map { it.thumbnailUrl },
        )
    }

    override fun onCleared() {
        super.onCleared()
        proactiveLoadJob?.cancel()
    }
}
