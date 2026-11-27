// LibraryHomeViewModel adapter — phase2-library NEEDS.md §(a) C1 (NOT a
// Mochy port: Mochy's HVM reads Room + MusicImportService + CachedSongs;
// this one reads CW's YtMusicRepository + OfflinePlaylists + Downloads +
// AudioCache + the local-import store in ui.mochy.importer).
//
// Every member the ported Library screens read is exposed here with the
// declared name and type; the checklist in this dir's NEEDS.md ticks them
// off one by one.
//
// Loading: this VM loads in init (like HomeViewModel) and exposes load() for
// a manual refresh — the Library screens never call load(), and NEEDS.md
// names no load args, so "inert until load()" would leave the tab empty
// forever. Signed-out state clears the account-backed flows; signing in
// later reloads them. A failed load is surfaced, not swallowed: [isLoading]
// and [loadFailed] let the screens tell loading, failed, signed-out and
// genuinely-empty apart, and [refreshIfNeeded] retries a failed load when
// the tab is opened again.

package com.music.bitchord.ui.mochy.viewmodels

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import com.music.bitchord.auth.SignedIn
import com.music.bitchord.data.LikeState
import com.music.bitchord.data.OfflinePlaylist
import com.music.bitchord.data.OfflinePlaylists
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.model.BrowseItem
import com.music.bitchord.data.model.BrowseType
import com.music.bitchord.data.model.LibraryPage
import com.music.bitchord.data.model.SearchFilter
import com.music.bitchord.data.model.SearchResult
import com.music.bitchord.data.model.LikeStatus
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.mochydata.MochyLibraryRepository
import com.music.bitchord.download.Downloads
import com.music.bitchord.download.SavedCollection
import com.music.bitchord.playback.AudioCache
import com.music.bitchord.ui.mochy.importer.ImportProgress
import com.music.bitchord.ui.mochy.importer.ImportSource
import com.music.bitchord.ui.mochy.importer.LocalImportStore
import com.music.bitchord.ui.mochy.models.Album
import com.music.bitchord.ui.mochy.models.AlbumEntity
import com.music.bitchord.ui.mochy.models.Artist
import com.music.bitchord.ui.mochy.models.ArtistEntity
import com.music.bitchord.ui.mochy.models.Playlist
import com.music.bitchord.ui.mochy.models.Song
import com.music.innertube.models.YTItem
import com.music.bitchord.ui.mochy.models.toMochySong
import com.music.bitchord.ui.mochy.screens.library.LibraryTab
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.util.Collections

/**
 * Backs the Library home screen: the four category counts/covers, the pinned grid, and the
 * Recently added / Liked preview cards.
 *
 * Deliberately its own view model rather than reusing HomeViewModel's speed-dial items — that
 * flow pads pinned items out to 27 with "keep listening" filler for the Home feed's speed-dial
 * carousel, which is exactly wrong here: the Library screen's Pinned section is only supposed to
 * show what was actually pinned, and hides entirely when that's empty.
 */
@HiltViewModel
class LibraryHomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : ViewModel() {

    /**
     * The Room (Mochy engine) library, read-only this stage: once the
     * one-time legacy -> Room migration has completed, the offline
     * playlists, their songs and the downloads below read from Room;
     * until then (or if the migration failed) everything keeps reading
     * the legacy ChordWave stores exactly as before.
     */
    private val mochyLibrary = MochyLibraryRepository(context)

    // --- Library (account) --------------------------------------------------

    /** Whether a YouTube account is signed in — the All tab's empty state depends on it. */
    val signedIn: StateFlow<Boolean> = SignedIn.state(context)

    private val _isLoading = MutableStateFlow(false)
    /**
     * True while an account load is in flight. The screens show a spinner
     * instead of the empty state while this is true, so a slow first load
     * never flashes "nothing here yet".
     */
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _loadFailed = MutableStateFlow(false)
    /**
     * True when the last account load failed AND left no account data on
     * screen. A failed reload over data already shown doesn't set it — the
     * old lists stay, and the next tab visit retries quietly.
     */
    val loadFailed: StateFlow<Boolean> = _loadFailed.asStateFlow()

    private val _songs = MutableStateFlow<List<Song>>(emptyList())

    private val _likedSongs = MutableStateFlow<List<Song>>(emptyList())

    private val _artistsBase = MutableStateFlow<List<Artist>>(emptyList())

    /** Artist pictures fetched after the list loads, by artist id. */
    private val _artistThumbs = MutableStateFlow<Map<String, String>>(emptyMap())


    private val _albums = MutableStateFlow<List<Album>>(emptyList())

    private val _accountPlaylists = MutableStateFlow<List<Playlist>>(emptyList())
    private val _offlinePlaylists = MutableStateFlow<List<Playlist>>(emptyList())

    /** Account playlists, then the user's local-file playlists. */
    val playlists: StateFlow<List<Playlist>> = combine(_accountPlaylists, _offlinePlaylists) { account, offline ->
        account + offline
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Playlist id -> the song ids in it, for the shelves. Offline playlists only: CW knows an
     * account playlist's contents only by fetching its page, which a shelf must not do. */
    private val _playlistSongIds = MutableStateFlow<Map<String, Set<String>>>(emptyMap())

    /**
     * Pinned playlists only: CW's pin store ([AppSettings.pinnedPlaylists])
     * holds playlist browseIds, not arbitrary items. A pinned playlist that
     * hasn't loaded into [playlists] yet doesn't show until it does.
     */
    val pinnedItems: StateFlow<List<YTItem>> = combine(AppSettings.pinnedPlaylists, playlists) { pinned, lists ->
        pinned.mapNotNull { browseId ->
            lists.firstOrNull { it.playlist.browseId == browseId }?.toSpeedDialYTItem()
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // --- Imports (no engine yet: all empty until the import engine lands) ---

    /** The import under way, if any - the banner at the top of the Library turns into its progress bar. */
    val importProgress: StateFlow<ImportProgress> = MutableStateFlow(ImportProgress())

    /** One card per service that has brought music in. Empty: CW has no import store yet. */
    val importedSources: StateFlow<List<ImportedSourceCard>> = MutableStateFlow(emptyList())

    /** How many albums each service's songs come from. Empty: CW has no import store yet. */
    val importedAlbums: StateFlow<Map<ImportSource, ImportedAlbums>> = MutableStateFlow(emptyMap())

    /** The service each imported playlist came from, by playlist id. Empty: CW has no import store yet. */
    val playlistSources: StateFlow<Map<String, ImportSource>> = MutableStateFlow(emptyMap())

    // --- Library tab --------------------------------------------------------

    /**
     * The Library tab on screen (All / Local / Cached / ...). Kept here, not in
     * the screen's rememberSaveable: the host renders the detail pages INSTEAD
     * of the tab screen, so the screen leaves composition when a card opens
     * and a rememberSaveable tab would reset to All on every return (batch 11).
     * The ViewModel is activity-scoped, so the tab survives card pages and
     * tab switches within a session; a fresh launch still starts on All.
     */
    private val _selectedTab = MutableStateFlow(LibraryTab.All)
    val selectedTab: StateFlow<LibraryTab> = _selectedTab.asStateFlow()

    fun selectTab(tab: LibraryTab) {
        _selectedTab.value = tab
    }

    // --- Shelves ------------------------------------------------------------

    private val _downloadedSongs = MutableStateFlow<List<Song>>(emptyList())

    /** Every downloaded track, newest first — the Downloads card's count and covers. */
    val downloadedSongs: StateFlow<List<Song>> = _downloadedSongs.asStateFlow()

    /**
     * Whole albums/playlists downloaded, still with tracks on disk —
     * Classic's "On device" shelf (MainViewModel's downloadedReleases, same
     * call). Recomputed whenever the collection record or the saved files
     * change, so deleting the last track of a release drops its row.
     */
    val downloadedReleases: StateFlow<List<SavedCollection>> =
        combine(Downloads.collections, Downloads.saved) { _, _ -> Downloads.savedReleases() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Everything that plays from this device: songs downloaded for offline, audio files imported
     * from storage, and files the offline playlists reference (a file in a CW playlist is
     * imported in Mochy's sense: it is in the library). Newest first; undated last.
     */
    /**
     * Songs sitting in the user's playlists: from Room once the migration
     * has landed, from [OfflinePlaylists] until then.
     */
    private val playlistSongsSource: Flow<List<Song>> = combine(
        OfflinePlaylists.playlists,
        mochyLibrary.ready,
        mochyLibrary.playlistSongs,
    ) { offline, roomReady, roomSongs ->
        if (roomReady && roomSongs.isNotEmpty()) {
            roomSongs
        } else {
            offline
                .flatMap { it.songs }
                .distinctBy { it.localUri }
                .map { it.toLocalFileMochySong() }
        }
    }

    private val localSource: Flow<List<Song>> = combine(
        _downloadedSongs,
        LocalImportStore.songs,
        playlistSongsSource,
        LikeState.overrides,
        OfflinePlaylists.liked,
    ) { downloaded, imported, fromPlaylists, overrides, likedUris ->
        (downloaded + imported + fromPlaylists)
            .distinctBy { it.id }
            .map { song ->
                val liked = overrides[song.id] == LikeStatus.LIKE ||
                    (song.song.localUri != null && song.song.localUri in likedUris)
                if (liked != song.song.liked) song.copy(song = song.song.copy(liked = liked)) else song
            }
            .sortedByDescending { it.song.dateDownload ?: it.song.inLibrary }
    }

    /**
     * Songs the player kept whole from streaming, most recently played first.
     * Re-read every couple of seconds while watched (AudioCache has no change
     * flow); only whole songs count, like Mochy's cached shelf.
     */
    @OptIn(UnstableApi::class)
    private fun cachedSource(): Flow<List<Song>> = flow {
        while (true) {
            val songs = runCatching { AudioCache.cachedSongs() }.getOrNull().orEmpty()
                .filter { it.complete }
                .map { cached ->
                    val song = cached.song.toMochySong()
                    val liked = LikeState.overrides.value[cached.song.videoId] == LikeStatus.LIKE
                    if (liked) song.copy(song = song.song.copy(liked = true)) else song
                }
            emit(songs)
            kotlinx.coroutines.delay(2_000)
        }
    }.flowOn(Dispatchers.IO)

    val local = LibraryShelf(
        source = localSource,
        libraryPlaylists = playlists,
        playlistSongIds = _playlistSongIds,
        scope = viewModelScope,
        artistThumbnails = _artistThumbs,
    )

    /**
     * Songs the player kept whole from streaming, most recently played first. Their artists are
     * often not in the library, so this shelf fetches their pictures itself - only while the
     * Cached tab is open, since watching the cache means looking at it every couple of seconds.
     */
    val cached = LibraryShelf(
        source = cachedSource(),
        libraryPlaylists = playlists,
        playlistSongIds = _playlistSongIds,
        scope = viewModelScope,
        artistThumbnails = _artistThumbs,
        refreshArtists = ::refreshArtistImages,
    )

    // The library the All tab and its pages read: the account's lists when
    // the account has any, the Local shelf's when it has none — signed out,
    // or an account whose library is empty. Mochy's library lived in a
    // local database and showed the same either way; here the phone's own
    // songs, likes, artists and albums stand in, so the Library never reads
    // as empty while the phone is full of music (batch 18).

    /** Liked + added library songs, in feed order (CW rows carry no add date to sort by); the Local shelf's songs while the account has none. */
    val songs: StateFlow<List<Song>> = combine(_songs, local.songs) { account, onDevice ->
        account.ifEmpty { onDevice }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The account's liked songs, or the liked songs on this phone while the account has none. */
    val likedSongs: StateFlow<List<Song>> = combine(_likedSongs, local.liked) { account, onDevice ->
        account.ifEmpty { onDevice }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Shelf artists first (in shelf order), then song-derived artists by song count; the Local shelf's artists while the account has none. */
    val artists: StateFlow<List<Artist>> = combine(_artistsBase, local.artists, _artistThumbs) { base, onDevice, thumbs ->
        val picked = base.ifEmpty { onDevice }
        if (thumbs.isEmpty()) {
            picked
        } else {
            picked.map { artist ->
                thumbs[artist.id]?.let { artist.copy(artist = artist.artist.copy(thumbnailUrl = it)) } ?: artist
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Saved albums first, then the albums the library's songs come from, distinct by id; the Local shelf's albums while the account has none. */
    val albums: StateFlow<List<Album>> = combine(_albums, local.albums) { account, onDevice ->
        account.ifEmpty { onDevice.mapNotNull { it.toLibraryAlbum() } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun shelf(kind: LibraryShelfKind): LibraryShelf = when (kind) {
        LibraryShelfKind.Local -> local
        LibraryShelfKind.Cached -> cached
    }

    // These MUST be declared above the init block: init calls load(), whose
    // Dispatchers.IO coroutine can run on a worker thread while this
    // constructor is still running — a field declared below init would still
    // be null there (crashed twice on device as NPE in tryLock, 15 Nov 2026).
    private val loadMutex = Mutex()
    private val libraryMutex = Mutex()
    private val artistRefreshAttempted = Collections.synchronizedSet(HashSet<String>())

    @Volatile
    private var reloadRequested = false

    init {
        LocalImportStore.init(context)
        // Pictures resolved for local artists in earlier sessions (batch 33)
        // show at once; the refresh below only fetches what's still missing.
        _artistThumbs.value = AppSettings.localArtistThumbs.value
        load()

        // The first load() fetches the account itself; a later sign-in (or
        // sign-out) has to reload here.
        viewModelScope.launch(Dispatchers.IO) {
            var isFirstSignedIn = true
            SignedIn.state(context).collect {
                if (isFirstSignedIn) {
                    isFirstSignedIn = false
                    return@collect
                }
                load()
            }
        }

        viewModelScope.launch {
            combine(
                OfflinePlaylists.playlists,
                mochyLibrary.ready,
                mochyLibrary.offlinePlaylists,
                mochyLibrary.playlistSongIds,
            ) { legacy, roomReady, roomPlaylists, roomIds ->
                Triple(legacy, roomReady && roomPlaylists.isNotEmpty(), roomPlaylists to roomIds)
            }.collect { (legacy, useRoom, room) ->
                if (useRoom) {
                    _offlinePlaylists.value = room.first
                    _playlistSongIds.value = room.second
                } else {
                    rebuildOfflinePlaylists(legacy)
                }
            }
        }

        // A download finishing lands in the Local shelf; debounced so a whole
        // album completing doesn't re-read the folder once per track.
        viewModelScope.launch(Dispatchers.IO) {
            Downloads.saved.debounce(300).collect { refreshDownloadedSongs() }
        }

        // Local artists are included: a downloaded song that never went into the library has
        // artists the Library's list doesn't know about, and the Local tab shows them too.
        viewModelScope.launch(Dispatchers.IO) {
            combine(artists, local.artists) { library, localArtists ->
                (library + localArtists).distinctBy { it.id }
            }.collect { refreshArtistImages(it) }
        }
    }

    // --- Load orchestration -------------------------------------------------

    /** Reloads the library (account + local). Safe to call any time; a load already running queues one more pass. */
    fun load() {
        viewModelScope.launch(Dispatchers.IO) {
            if (!loadMutex.tryLock()) {
                reloadRequested = true
                return@launch
            }
            try {
                do {
                    reloadRequested = false
                    loadOnce()
                } while (reloadRequested)
            } finally {
                loadMutex.unlock()
            }
        }
    }

    /**
     * Reloads only when the last account load failed — called when the
     * Library tab is opened, so one failed cold start doesn't leave the tab
     * empty until the app is killed. A healthy load is never repeated here.
     */
    fun refreshIfNeeded() {
        if (_loadFailed.value) load()
    }

    private suspend fun loadOnce() {
        _isLoading.value = true
        try {
            libraryResult = null
            rebuildOfflinePlaylists(OfflinePlaylists.playlists.value)
            refreshDownloadedSongs()
            if (!SignedIn.state(context).value) {
                clearAccountData()
                _loadFailed.value = false
                return
            }
            _loadFailed.value = false
            val libraryLoaded = supervisorScope {
                val libraryJob = async(Dispatchers.IO) { runLoader(::loadLibrary) }
                val playlistsJob = async(Dispatchers.IO) { runLoader(::loadAccountPlaylists) }
                val loaded = libraryJob.await()
                playlistsJob.await()
                loaded
            }
            // Failed only matters when it leaves the tab with nothing to
            // show: a failed reload over data already on screen keeps the
            // old lists, and the next tab visit retries quietly.
            _loadFailed.value = !libraryLoaded &&
                _songs.value.isEmpty() && _accountPlaylists.value.isEmpty()
        } finally {
            _isLoading.value = false
        }
        // Artist pictures fill in after the lists are already on screen —
        // up to 25 artist pages, one per artist, must never hold the
        // loading spinner up.
        refreshArtistImages(_artistsBase.value)
    }

    /** Runs one account loader, logging (not throwing) on failure so one feed failing doesn't take the other down. */
    private suspend fun runLoader(block: suspend () -> Boolean): Boolean {
        return try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Library: loader failed")
            false
        }
    }

    // One library fetch per load, shared by the songs/albums/artists loaders.
    private var libraryResult: Result<LibraryPage>? = null

    private suspend fun libraryPage(): Result<LibraryPage> = libraryMutex.withLock {
        libraryResult ?: YtMusicRepository.library().also { libraryResult = it }
    }

    private fun clearAccountData() {
        _songs.value = emptyList()
        _likedSongs.value = emptyList()
        _artistsBase.value = emptyList()
        _albums.value = emptyList()
        _accountPlaylists.value = emptyList()
    }

    private suspend fun loadLibrary(): Boolean {
        val page = libraryPage().getOrNull() ?: run {
            Timber.w("Library: library() failed, keeping previous")
            return false
        }
        val liked = page.likedSongs.map { cw ->
            cw.toMochySong().let { song -> song.copy(song = song.song.copy(liked = true)) }
        }
        val added = page.librarySongs.map { cw ->
            val song = cw.toMochySong()
            if (LikeState.overrides.value[cw.videoId] == LikeStatus.LIKE) {
                song.copy(song = song.song.copy(liked = true))
            } else {
                song
            }
        }
        _likedSongs.value = liked
        _songs.value = liked + added

        // Saved albums first, then the albums the library's songs come from
        // (liked songs' albums included — Mochy's AlbumsFromLikedSongsKey has
        // no CW equivalent, so the inclusive behaviour is the default).
        val savedAlbums = page.shelves
            .filter { it.title == "Albums" }
            .flatMap { it.items }
            .mapNotNull { it.toMochyAlbum() }
        val allSongs = liked + added
        val songAlbums = allSongs
            .filter { it.song.albumId != null && !it.song.albumName.isNullOrBlank() }
            .groupBy { it.song.albumId!! }
            .map { (albumId, group) ->
                val first = group.first()
                Album(
                    album = AlbumEntity(
                        id = albumId,
                        title = first.song.albumName.orEmpty(),
                        thumbnailUrl = first.song.thumbnailUrl,
                        songCount = group.size,
                        duration = 0,
                    ),
                    artists = first.artists,
                )
            }
        _albums.value = (savedAlbums + songAlbums).distinctBy { it.id }

        // Shelf artists first, then artists the library's songs credit that
        // have a channel page (a name-only credit has nowhere to open).
        val counts = allSongs.flatMap { it.artists }.groupingBy { it.id }.eachCount()
        val shelfArtists = page.shelves
            .filter { it.title == "Artists" || it.title == "Subscriptions" }
            .flatMap { it.items }
            .filter { it.browseId?.startsWith("UC") == true }
            .mapNotNull { it.toMochyArtist() }
            .distinctBy { it.id }
            .map { artist -> artist.copy(songCount = counts[artist.id] ?: 0) }
        val songArtists = allSongs
            .flatMap { it.artists }
            .filter { it.id.startsWith("UC") }
            .distinctBy { it.id }
            .map { entity -> Artist(artist = entity, songCount = counts[entity.id] ?: 0) }
            .sortedByDescending { it.songCount }
        _artistsBase.value = (shelfArtists + songArtists).distinctBy { it.id }

        // Liked Music past the first page still reads as liked (Classic
        // parity: MainViewModel.syncLikedMusic) — ids only, so syncing a
        // long list stays cheap. Guarded per page: a sign-out mid-sync
        // must stop rather than seed the old account's likes into the
        // new session (sign-out clears LikeState).
        page.likedContinuation?.let { token ->
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    YtMusicRepository.syncLikedMusic(token) { next ->
                        if (!SignedIn.state(context).value) null
                        else YtMusicRepository.moreSongs(next).getOrNull()
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.w(e, "Library: liked sync failed")
                }
            }
        }
        return true
    }

    private suspend fun loadAccountPlaylists(): Boolean {
        return YtMusicRepository.libraryPlaylists()
            .onSuccess { items ->
                _accountPlaylists.value = items
                    .filterNot { it.browseId == YtMusicRepository.LIKED_MUSIC }
                    .mapNotNull { it.toMochyPlaylist() }
                    .distinctBy { it.id }
            }
            .onFailure { Timber.w(it, "Library: libraryPlaylists() failed, keeping previous") }
            .isSuccess
    }

    private fun rebuildOfflinePlaylists(all: List<OfflinePlaylist>) {
        // The built-in Liked list is not a user playlist: its songs surface
        // through the Local shelf's Liked card instead.
        val user = all.filterNot { it.isLiked }
        _offlinePlaylists.value = user.map { it.toMochyPlaylist() }
        _playlistSongIds.value = user.associate { playlist ->
            playlist.id to playlist.songs.flatMapTo(HashSet()) { listOfNotNull(it.videoId, it.localUri) }
        }
    }

    private suspend fun refreshDownloadedSongs() {
        // Room first once the migration has landed (the files themselves
        // stay in Music/ChordWave either way); the legacy scan is the
        // fallback until then and whenever Room has nothing to say.
        val fromRoom = if (mochyLibrary.ready.first()) {
            runCatching { mochyLibrary.downloadedSongs.first() }.getOrNull().orEmpty()
        } else {
            emptyList()
        }
        if (fromRoom.isNotEmpty()) {
            _downloadedSongs.value = fromRoom
            return
        }
        val downloaded = runCatching { Downloads.getDownloadedSongs(context) }.getOrNull().orEmpty()
        _downloadedSongs.value = downloaded.map { it.toDownloadedMochySong() }
    }

    /**
     * Artist rows have no picture until something goes and fetches one: a
     * catalogue row carries no artist thumbnail, and nothing resolves it as a
     * side effect of reading the library. Without this every artist in the
     * library rendered as a placeholder icon forever.
     *
     * Refreshes anything missing a picture, one artist page per artist, at
     * most 25 per pass; each artist is attempted once per ViewModel lifetime
     * so the watchers that re-fire on every list change don't re-fetch.
     */
    private suspend fun refreshArtistImages(list: List<Artist>) {
        val missing = list.map { it.artist }
            .filter { it.thumbnailUrl == null && it.id.startsWith("UC") && artistRefreshAttempted.add(it.id) }
            .take(25)
        for (artist in missing) {
            val thumb = runCatching {
                YtMusicRepository.artistLanding(artist.id).getOrNull()?.thumbnailUrl
            }.getOrNull()
            if (thumb != null) {
                _artistThumbs.update { it + (artist.id to thumb) }
            }
        }
        // Local artists (batch 33): their id is a name, not a channel, so the
        // pass above never sees them and they rendered as grey circles
        // forever. Resolve a picture by exact-name search instead, cached
        // across restarts so the search runs once per artist.
        val localMissing = list.map { it.artist }
            .filter { it.thumbnailUrl == null && !it.id.startsWith("UC") && artistRefreshAttempted.add(it.id) }
            .take(25)
        for (artist in localMissing) {
            val thumb = runCatching { resolveLocalArtistThumb(artist) }.getOrNull()
            if (thumb != null) {
                _artistThumbs.update { it + (artist.id to thumb) }
                AppSettings.rememberLocalArtistThumb(artist.id, thumb)
            }
        }
    }

    /**
     * A YouTube Music picture for an artist known only by a file tag.
     * Deliberately strict (batch 33): the search result's name must equal
     * the tag (case and punctuation aside) — a near miss stays a grey
     * circle rather than wearing another artist's face. A tag naming
     * several artists ("Calum Scott, Darren Espanto") is tried whole first,
     * then by its first name. When several channels share the exact name,
     * one of the artist's own local songs breaks the tie; if it can't, no
     * picture is better than a wrong one.
     */
    private suspend fun resolveLocalArtistThumb(artist: ArtistEntity): String? {
        for (name in candidateArtistNames(artist.name)) {
            val results = YtMusicRepository.search(name, SearchFilter.ARTISTS).getOrNull().orEmpty()
            val matches = results.filterIsInstance<SearchResult.Browse>()
                .map { it.item }
                .filter {
                    it.type == BrowseType.ARTIST &&
                        normalizeArtistName(it.title) == normalizeArtistName(name)
                }
            val picked = when {
                matches.size == 1 -> matches.first()
                matches.size > 1 -> disambiguateArtistBySong(artist, name, matches)
                else -> null
            } ?: continue
            return picked.thumbnailUrl
                ?: YtMusicRepository.artistLanding(picked.browseId).getOrNull()?.thumbnailUrl
        }
        return null
    }

    /** Which of several same-named channels actually sings one of this artist's local songs. */
    private suspend fun disambiguateArtistBySong(
        artist: ArtistEntity,
        name: String,
        matches: List<BrowseItem>,
    ): BrowseItem? {
        val title = local.songs.value
            .firstOrNull { song -> song.artists.any { it.id == artist.id } }
            ?.song?.title ?: return null
        val tracks = YtMusicRepository.search("$name $title", SearchFilter.SONGS).getOrNull().orEmpty()
        val channelIds = tracks.flatMap { result ->
            when (result) {
                is SearchResult.Track -> listOfNotNull(result.song.artistId) + result.song.artists.mapNotNull { it.browseId }
                is SearchResult.TopTrack -> listOfNotNull(result.song.artistId) + result.song.artists.mapNotNull { it.browseId }
                is SearchResult.Browse -> emptyList()
            }
        }.toSet()
        val confirmed = matches.filter { it.browseId in channelIds }
        return confirmed.singleOrNull()
    }

    // --- Cache management ---------------------------------------------------

    /** Deletes these songs from the cache; they stream again the next time they play. */
    @OptIn(UnstableApi::class)
    fun removeFromCache(songIds: Collection<String>) {
        if (songIds.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            songIds.forEach { runCatching { AudioCache.remove(it) } }
        }
    }

    /** Clears the whole playback cache, including partly-cached songs. Downloads are never touched. */
    @OptIn(UnstableApi::class)
    fun clearCache() {
        AudioCache.clear()
    }

    // --- Pins ---------------------------------------------------------------

    fun unpin(id: String) {
        val browseId = when {
            id in AppSettings.pinnedPlaylists.value -> id
            else -> playlists.value.firstOrNull { it.id == id }?.playlist?.browseId
                ?: ("VL$id").takeIf { it in AppSettings.pinnedPlaylists.value }
        } ?: run {
            Timber.w("Library: unpin($id) — no pinned playlist matches")
            return
        }
        AppSettings.togglePinnedPlaylist(browseId)
    }

    /**
     * Pins a playlist to the Library's front row. CW pins playlists only, so
     * anything that isn't a playlist is logged and ignored; Mochy's pin rows
     * already log the same Phase 3 gap for songs and artists.
     */
    fun pin(item: YTItem) {
        val playlist = playlists.value.firstOrNull { it.id == item.id }
            ?: playlists.value.firstOrNull { it.playlist.browseId == item.id }
        val browseId = playlist?.playlist?.browseId
        if (browseId == null) {
            Timber.w("Library: pin(${item.id}) — not a known playlist, CW pins playlists only")
            return
        }
        if (!AppSettings.togglePinnedPlaylist(browseId)) {
            Timber.w("Library: pin($browseId) refused — already at ${AppSettings.MAX_PINNED_PLAYLISTS} pins")
        }
    }
}

// a service's albums card on the imported tab
@Immutable
data class ImportedAlbums(
    val count: Int,
    val covers: List<String?>,
)

/** What a service's card in the Library shows. */
@Immutable
data class ImportedSourceCard(
    val source: ImportSource,
    val songCount: Int,
    val covers: List<String>,
)

// A shelf album as a library album: only when it has an album page to open
// (a tag-only album of imported files has none — it stays on its shelf,
// where opening it plays its songs), the same rule the account loader uses.
private fun ShelfAlbum.toLibraryAlbum(): Album? {
    val id = albumId ?: return null
    return Album(
        album = AlbumEntity(
            id = id,
            title = title,
            thumbnailUrl = thumbnailUrl,
            songCount = songs.size,
            duration = 0,
        ),
        artists = songs.flatMap { it.artists }.distinctBy { it.id },
    )
}

/** Names to try for a file-tag artist: the whole tag, then its first credited name. */
private fun candidateArtistNames(name: String): List<String> {
    val trimmed = name.trim()
    if (trimmed.isEmpty()) return emptyList()
    val first = trimmed
        .split(", ", " & ", " feat. ", " feat ", " ft. ", " ft ", " featuring ")
        .firstOrNull()?.trim().orEmpty()
    return listOf(trimmed, first).filter { it.isNotEmpty() }.distinct()
}

/** Case, spacing and punctuation aside, is this the same name? */
private fun normalizeArtistName(name: String): String =
    name.lowercase()
        .filter { it.isLetterOrDigit() || it.isWhitespace() }
        .replace(Regex("\\s+"), " ")
        .trim()
