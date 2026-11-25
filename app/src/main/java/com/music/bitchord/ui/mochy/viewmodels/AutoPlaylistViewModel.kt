// Ported from Mochy Music (GPL-3.0): viewmodels/AutoPlaylistViewModel.kt
// Mochy's flow is kept: content combines the sort prefs (SongSortTypeKey /
// SongSortDescendingKey / LikedSongSortTypeKey / LikedSongSortDescendingKey)
// with HideExplicitKey / HideVideoSongsKey and switches on the playlist
// key — liked, downloaded, uploaded, local, local_liked, cached,
// cached_liked, songs, recently_added. The screen sorts nothing itself; the
// sort lives here. Import-order sections stay empty until ChordWave gains
// an importer (the screen then renders one plain list, exactly as Mochy
// does when nothing is imported).
//
// CW sources per page (see NEEDS.md C3):
// - liked: Liked Music paged whole (browseSongs VLLM + moreSongs) plus the
//   liked local files. The server list is cached per refresh tick, so a
//   sort toggle re-sorts instead of re-paging YouTube.
// - downloaded: Downloads.getDownloadedSongs.
// - uploaded: EMPTY — ChordWave has no uploaded-songs endpoint.
// - local: downloads + imported files + every local-file playlist's songs —
//   the same catalogue as the Local shelf, so the Songs card's count and its
//   page agree (batch 11: the import store's files were missing here).
// - local_liked: the on-device songs that are liked.
// - cached / cached_liked: the player cache's whole songs via
//   AudioCache.cachedSongs() — the same source as the Cached shelf (batch 11:
//   both pages used to read empty).
// - songs: played songs (ListeningStats, with real play time) + downloads
//   + on-device files, merged by id.
// - recently_added: the same catalogue, CREATE_DATE desc (Mochy's rule).
// A track that was never played, downloaded, imported, or filed in a
// playlist is invisible to the local pages — same limitation as the
// local-search catalogue.
// - No saved-state handle: the route arg comes from load(playlist), which
//   the coordinator calls from the screen. `playlist` is therefore a
//   `var ... by mutableStateOf("")` set by load(), not Mochy's val. The VM
//   fetches nothing before the first load() call (content stays null).
// - syncLikedSongs() just re-fetches: opening the page already pulls
//   Liked Music fresh, and the fetch seeds LikeState from the complete
//   list, so YtMusicRepository.library()'s extra feeds buy nothing here.
//   syncUploadedSongs() is a no-op (no endpoint); refresh() only ever
//   re-fetches Liked (the screen only offers pull-to-refresh there and on
//   Uploaded).

package com.music.bitchord.ui.mochy.viewmodels

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.bitchord.data.LikeState
import com.music.bitchord.data.model.LikeStatus
import com.music.bitchord.data.OfflinePlaylist
import com.music.bitchord.data.OfflinePlaylists
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.stats.ListeningStats
import com.music.bitchord.data.stats.ReplayPeriod
import com.music.bitchord.download.Downloads
import com.music.bitchord.playback.AudioCache
import com.music.bitchord.ui.mochy.constants.HideExplicitKey
import com.music.bitchord.ui.mochy.constants.HideVideoSongsKey
import com.music.bitchord.ui.mochy.constants.LikedSongSortDescendingKey
import com.music.bitchord.ui.mochy.constants.LikedSongSortType
import com.music.bitchord.ui.mochy.constants.LikedSongSortTypeKey
import com.music.bitchord.ui.mochy.constants.SongSortDescendingKey
import com.music.bitchord.ui.mochy.constants.SongSortType
import com.music.bitchord.ui.mochy.constants.SongSortTypeKey
import com.music.bitchord.ui.mochy.extensions.toEnum
import com.music.bitchord.ui.mochy.importer.ImportSource
import com.music.bitchord.ui.mochy.importer.LocalImportStore
import com.music.bitchord.ui.mochy.models.Song
import com.music.bitchord.ui.mochy.models.toMochySong
import com.music.bitchord.ui.mochy.utils.dataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber

/** Liked songs that came from one place; [source] null is the ones liked here in Mochy. */
data class LikedSourceSection(val source: ImportSource?, val songs: List<Song>)

data class AutoPlaylistSongs(
    val songs: List<Song>,
    /** Only for Liked songs in import order, when some were imported: [songs], split by service. */
    val sections: List<LikedSourceSection> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AutoPlaylistViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : ViewModel() {
    // B1 calls for a String val; a var by mutableStateOf reads as a String
    // and lets load() update it when the host hands over a new playlist.
    var playlist by mutableStateOf("")
        private set

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    /** The playlist key the current page belongs to; null until load() is called. */
    private val loadedId = MutableStateFlow<String?>(null)

    /** Bumped by refresh()/sync so the network-backed lists re-fetch; sort/pref changes re-sort the cached lists instead. */
    private val refreshTick = MutableStateFlow(0)

    /** Liked Music, fetched whole once per refresh tick (paging VLLM is the only way to get all of it). */
    private var likedCache: List<Song>? = null
    private var likedCacheTick = -1

    private data class ListPrefs(
        val sortType: SongSortType,
        val descending: Boolean,
        val likedSortType: LikedSongSortType,
        val likedDescending: Boolean,
        val hideExplicit: Boolean,
        val hideVideoSongs: Boolean,
    )

    private data class Inputs(
        val prefs: ListPrefs,
        val id: String?,
        val tick: Int,
        val playlists: List<OfflinePlaylist>,
        /** The imported files, so the Local pages count them like the shelf does. */
        val imported: List<Song>,
        /** Live like overrides, so a like toggled elsewhere re-sorts the page. */
        val likedOverrides: Map<String, LikeStatus>,
    )

    /** null until the first load */
    val content: StateFlow<AutoPlaylistSongs?> =
        combine(
            context.dataStore.data
                .map {
                    ListPrefs(
                        sortType = it[SongSortTypeKey].toEnum(SongSortType.CREATE_DATE),
                        descending = it[SongSortDescendingKey] ?: true,
                        likedSortType = it[LikedSongSortTypeKey].toEnum(LikedSongSortType.CREATE_DATE),
                        likedDescending = it[LikedSongSortDescendingKey] ?: true,
                        hideExplicit = it[HideExplicitKey] ?: false,
                        hideVideoSongs = it[HideVideoSongsKey] ?: false,
                    )
                }
                .distinctUntilChanged(),
            loadedId,
            refreshTick,
            OfflinePlaylists.playlists,
            Downloads.saved,
            LocalImportStore.songs,
            LikeState.overrides,
        ) { prefs, id, tick, playlists, _, imported, likedOverrides ->
            Inputs(prefs, id, tick, playlists, imported, likedOverrides)
        }
            .flatMapLatest { inputs ->
                val id = inputs.id ?: return@flatMapLatest flowOf<AutoPlaylistSongs?>(null)
                flow { emit(loadContent(inputs)) }
            }
            .flowOn(Dispatchers.IO)
            // Stops a moment after the page stops watching: the Cached lists
            // look at the player's cache every couple of seconds in Mochy,
            // which shouldn't go on from the background. The last list is
            // kept, so coming back shows it at once.
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * (Re)initialises the VM for a route playlist key ("liked",
     * "uploaded", "downloaded", "local", "local_liked", "cached",
     * "cached_liked", "songs", "recently_added"). The coordinator patches
     * the ported screen to call this; the VM fetches nothing before the
     * first call.
     */
    fun load(playlist: String) {
        val key = playlist.trim()
        if (key.isEmpty() || key == loadedId.value) return
        loadedId.value = key
        this.playlist = key
    }

    private suspend fun loadContent(inputs: Inputs): AutoPlaylistSongs {
        val id = inputs.id ?: return AutoPlaylistSongs(emptyList())
        val prefs = inputs.prefs
        val visible: (List<Song>) -> List<Song> = {
            it.filterExplicit(prefs.hideExplicit).filterVideoSongs(prefs.hideVideoSongs)
        }
        return when (id) {
            "liked" -> {
                val songs = likedSongs(inputs.tick)
                if (prefs.likedSortType == LikedSongSortType.IMPORT_ORDER) {
                    // No importer exists in ChordWave yet, so there are no
                    // source sections — one plain list, exactly as Mochy
                    // renders when nothing was imported.
                    AutoPlaylistSongs(
                        visible(songs.sortedBySongSort(SongSortType.CREATE_DATE, prefs.likedDescending) { it.song.likedDate }),
                    )
                } else {
                    AutoPlaylistSongs(
                        visible(songs.sortedBySongSort(prefs.likedSortType.songSortType, prefs.likedDescending) { it.song.likedDate }),
                    )
                }
            }

            "downloaded" -> AutoPlaylistSongs(
                visible(downloadedSongs().sortedBySongSort(prefs.sortType, prefs.descending) { it.song.dateDownload }),
            )

            // No uploaded-songs endpoint in ChordWave — the screen's empty
            // state says so honestly until one exists.
            "uploaded" -> AutoPlaylistSongs(emptyList())

            // Local is everything on this device: downloads, imported files and the
            // files the offline playlists reference — the same catalogue as the
            // Local shelf, so the card's count and this page agree.
            "local" -> AutoPlaylistSongs(
                visible(
                    onDeviceSongs(inputs.playlists, inputs.imported, inputs.likedOverrides)
                        .sortedBySongSort(prefs.sortType, prefs.descending),
                ),
            )

            // the Local page's Liked songs: only the liked songs that are on this device.
            // Sorted like any song list - the page shows the song sort for it, not Liked's.
            "local_liked" -> AutoPlaylistSongs(
                visible(
                    onDeviceSongs(inputs.playlists, inputs.imported, inputs.likedOverrides)
                        .filter { it.song.liked }
                        .sortedBySongSort(prefs.sortType, prefs.descending),
                ),
            )

            // The Cached page and its liked songs: what the player kept whole from
            // streaming, read from the same AudioCache the Cached shelf reads.
            "cached" -> AutoPlaylistSongs(
                visible(cachedSongs(inputs.likedOverrides).sortedBySongSort(prefs.sortType, prefs.descending)),
            )
            "cached_liked" -> AutoPlaylistSongs(
                visible(
                    cachedSongs(inputs.likedOverrides).filter { it.song.liked }
                        .sortedBySongSort(prefs.sortType, prefs.descending),
                ),
            )

            "songs" -> AutoPlaylistSongs(
                visible(allSongs(inputs.playlists).sortedBySongSort(prefs.sortType, prefs.descending)),
            )

            "recently_added" -> AutoPlaylistSongs(
                visible(allSongs(inputs.playlists).sortedBySongSort(SongSortType.CREATE_DATE, true)),
            )

            else -> AutoPlaylistSongs(emptyList())
        }
    }

    /**
     * Liked Music, paged whole, plus the liked local files. The server
     * list is cached per refresh tick: re-sorting must not re-page
     * YouTube. YouTube returns Liked Music newest first, and the stable
     * CREATE_DATE sort keeps that order (browse rows carry no liked date).
     */
    private suspend fun likedSongs(tick: Int): List<Song> {
        val cached = likedCache.takeIf { likedCacheTick == tick }
        val server = cached ?: run {
            val fetched = fetchAllBrowseSongs(YtMusicRepository.LIKED_MUSIC)
            if (fetched.isEmpty()) {
                Timber.w("AutoPlaylist: liked fetch came back empty (signed out?)")
            }
            LikeState.seedLiked(fetched.mapTo(HashSet()) { it.videoId })
            fetched.map { it.toLikedMochySong() }.also {
                likedCache = it
                likedCacheTick = tick
            }
        }
        val localLiked = OfflinePlaylists.playlists.value
            .firstOrNull { it.isLiked }?.songs.orEmpty()
            .map { it.toLocalFileMochySong().let { song -> song.copy(song = song.song.copy(liked = true)) } }
        return (server + localLiked).distinctBy { it.id }
    }

    private suspend fun downloadedSongs(): List<Song> =
        Downloads.getDownloadedSongs(context).map { it.toDownloadedMochySong() }

    /**
     * Local is everything on this device: downloads, imported files and the files the
     * offline playlists reference — the same three sources as the Local shelf, so the
     * Songs card's count and this page list the same songs. Liked is recomputed the
     * shelf's way (like overrides, then the liked playlist's local uris), so the
     * imported files carry their likes here too.
     */
    private suspend fun onDeviceSongs(
        playlists: List<OfflinePlaylist>,
        imported: List<Song>,
        likedOverrides: Map<String, LikeStatus>,
    ): List<Song> {
        val downloaded = downloadedSongs()
        val likedUris = playlists.firstOrNull { it.isLiked }?.songs
            ?.mapNotNull { it.localUri }?.toHashSet().orEmpty()
        val files = playlists.flatMap { it.songs }
            .map { file ->
                val song = file.toLocalFileMochySong()
                if (file.localUri in likedUris) song.copy(song = song.song.copy(liked = true)) else song
            }
        return (downloaded + imported + files)
            .distinctBy { it.id }
            .map { song ->
                val liked = likedOverrides[song.id] == LikeStatus.LIKE ||
                    (song.song.localUri != null && song.song.localUri in likedUris)
                if (liked != song.song.liked) song.copy(song = song.song.copy(liked = liked)) else song
            }
    }

    /**
     * The Cached page's songs: what the player kept whole from streaming, most
     * recently played first — the same AudioCache read as the Cached shelf.
     * Only whole songs count, like the shelf; a half-cached track plays from
     * the network, not from here.
     */
    private suspend fun cachedSongs(likedOverrides: Map<String, LikeStatus>): List<Song> =
        runCatching { AudioCache.cachedSongs() }.getOrNull().orEmpty()
            .filter { it.complete }
            .map { cached ->
                val song = cached.song.toMochySong()
                val liked = likedOverrides[cached.song.videoId] == LikeStatus.LIKE
                if (liked) song.copy(song = song.song.copy(liked = true)) else song
            }

    /**
     * Every song ChordWave knows locally: played songs (stats, with real
     * play time), downloads and on-device files. A track that was never
     * played, downloaded, or filed in a playlist is invisible here — same
     * limitation as the local-search catalogue.
     */
    private suspend fun allSongs(playlists: List<OfflinePlaylist>): List<Song> {
        val byId = LinkedHashMap<String, Song>()
        runCatching { ListeningStats.summary(ReplayPeriod.ALL_TIME) }
            .onSuccess { summary -> summary.songs.forEach { byId[it.song.videoId] = it.toTopMochySong() } }
            .onFailure { Timber.w(it, "AutoPlaylist: stats unavailable for \"songs\"") }
        downloadedSongs().forEach { dl ->
            val old = byId[dl.id]
            byId[dl.id] = old?.copy(
                song = old.song.copy(
                    isDownloaded = true,
                    dateDownload = dl.song.dateDownload,
                    localUri = dl.song.localUri,
                ),
            ) ?: dl
        }
        playlists.flatMap { it.songs }.forEach { file ->
            val song = file.toLocalFileMochySong()
            val old = byId[song.id]
            byId[song.id] = old?.copy(
                song = old.song.copy(
                    isLocal = true,
                    date = song.song.date ?: old.song.date,
                    localUri = song.song.localUri,
                ),
            ) ?: song
        }
        return byId.values.toList()
    }

    fun syncLikedSongs() {
        viewModelScope.launch(Dispatchers.IO) {
            // Opening the page already fetches Liked Music fresh (and the
            // fetch seeds LikeState from the complete list), so only
            // re-fetch when a list is already showing — otherwise this
            // would cancel the first fetch and start it over.
            if (likedCache != null) refreshTick.value++
        }
    }

    fun syncUploadedSongs() {
        // No uploaded-songs endpoint in ChordWave — nothing to sync.
        Timber.w("AutoPlaylist: uploaded sync is a no-op (no CW endpoint)")
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            _isRefreshing.value = true
            try {
                when (loadedId.value) {
                    "liked" -> refreshTick.value++
                    // "uploaded" has no endpoint — the flag still clears so
                    // the pull-to-refresh indicator doesn't stick.
                    else -> Unit
                }
            } finally {
                _isRefreshing.value = false
            }
        }
    }
}

/** Import order lists by the date liked, then splits that by service. */
private val LikedSongSortType.songSortType: SongSortType
    get() = when (this) {
        LikedSongSortType.CREATE_DATE, LikedSongSortType.IMPORT_ORDER -> SongSortType.CREATE_DATE
        LikedSongSortType.NAME -> SongSortType.NAME
        LikedSongSortType.ARTIST -> SongSortType.ARTIST
        LikedSongSortType.PLAY_TIME -> SongSortType.PLAY_TIME
    }
