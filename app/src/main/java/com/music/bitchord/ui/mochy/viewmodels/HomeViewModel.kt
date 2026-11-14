// HomeViewModel adapter — coordinator spec: DATA-MAPPING.md (NOT a Mochy port:
// Mochy's HVM reads Room + its YouTube client; this one reads CW's
// YtMusicRepository + ListeningStats).
//
// Scope: DATA-MAPPING §5 Phases A + B + C (items 1-17). Phase D is deferred:
// homeCategories and allTimeHits stay empty lists, starterSongs stays empty,
// attachPlayer records the connection and follows mediaMetadata/isPlaying but
// skip/play-through signals are not counted (onListeningBreak is stale-only),
// and speedDialItems is built from keepListening + stats quick picks with no
// pin store.
//
// Every member HomeScreen reads (phase2-home-screen NEEDS.md §(a) A1) is
// exposed here with the declared name and type; the checklist in this dir's
// NEEDS.md ticks them off one by one.

package com.music.bitchord.ui.mochy.viewmodels

import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.bitchord.auth.SignedIn
import com.music.bitchord.data.OfflinePlaylists
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.model.ArtistPage
import com.music.bitchord.data.model.HomeFeed
import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.data.model.LibraryPage
import com.music.bitchord.data.stats.ListeningStats
import com.music.bitchord.data.stats.RankedEntry
import com.music.bitchord.data.stats.ReplayPeriod
import com.music.bitchord.data.stats.ReplaySummary
import com.music.bitchord.data.stats.StoredBucket
import com.music.bitchord.data.stats.TrackEntry
import com.music.bitchord.ui.mochy.constants.DisableAiFilterKey
import com.music.bitchord.ui.mochy.constants.HideExplicitKey
import com.music.bitchord.ui.mochy.constants.HideVideoSongsKey
import com.music.bitchord.ui.mochy.constants.HideYoutubeShortsKey
import com.music.bitchord.ui.mochy.models.Album
import com.music.bitchord.ui.mochy.models.Artist
import com.music.bitchord.ui.mochy.models.ArtistGroup
import com.music.bitchord.ui.mochy.models.CommunityPlaylistItem
import com.music.bitchord.ui.mochy.models.DailyDiscoverItem
import com.music.bitchord.ui.mochy.models.FeedVideoKind
import com.music.bitchord.ui.mochy.models.HomeCategory
import com.music.bitchord.ui.mochy.models.HomeVideo
import com.music.bitchord.ui.mochy.models.LocalItem
import com.music.bitchord.ui.mochy.models.MediaMetadata
import com.music.bitchord.ui.mochy.models.Playlist
import com.music.bitchord.ui.mochy.models.SimilarRecommendation
import com.music.bitchord.ui.mochy.models.Song
import com.music.bitchord.ui.mochy.models.StarterSong
import com.music.bitchord.ui.mochy.models.toMochyMetadata
import com.music.bitchord.ui.mochy.models.toMochySong
import com.music.bitchord.ui.mochy.models.innertube.AlbumItem
import com.music.bitchord.ui.mochy.models.innertube.ArtistItem
import com.music.bitchord.ui.mochy.models.innertube.BrowseEndpoint
import com.music.bitchord.ui.mochy.models.innertube.HomePage
import com.music.bitchord.ui.mochy.models.innertube.PlaylistItem
import com.music.bitchord.ui.mochy.models.innertube.SongItem
import com.music.bitchord.ui.mochy.models.innertube.YTItem
import com.music.bitchord.ui.mochy.models.innertube.filterAiGenerated
import com.music.bitchord.ui.mochy.models.innertube.filterExplicit
import com.music.bitchord.ui.mochy.models.innertube.filterVideoSongs
import com.music.bitchord.ui.mochy.models.innertube.filterYoutubeShorts
import com.music.bitchord.ui.mochy.models.innertube.looksAiGenerated
import com.music.bitchord.ui.mochy.playback.MochyPlayerConnection
import com.music.bitchord.ui.mochy.utils.ArtistVideos
import com.music.bitchord.ui.mochy.utils.PreferencesCache
import com.music.bitchord.ui.mochy.utils.VideoShelf
import com.music.bitchord.ui.mochy.utils.dataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : ViewModel() {

    // --- Filter flags: read live from the Mochy-mode prefs ------------------
    // Mochy's own defaults (HideExplicit=false, HideVideoSongs=false,
    // HideYoutubeShorts=true, DisableAiFilter=false → AI filter ON). Read
    // through PreferencesCache on every access, so a Settings toggle
    // (Phase 4) takes effect on the next load() without an app restart.
    // (Coordinator-wired 15 Nov — the adapter shipped these as hardcoded
    // vals; the keys have now landed in PreferenceKeys.kt.)
    private val prefs get() = PreferencesCache.current(context.dataStore)
    private val hideExplicit get() = prefs[HideExplicitKey] ?: false
    private val hideVideoSongs get() = prefs[HideVideoSongsKey] ?: false
    private val hideYoutubeShorts get() = prefs[HideYoutubeShortsKey] ?: true
    private val hideAi get() = !(prefs[DisableAiFilterKey] ?: false)

    val isRefreshing = MutableStateFlow(false)
    val isLoading = MutableStateFlow(false)
    val isRandomizing = MutableStateFlow(false)

    val quickPicks = MutableStateFlow<List<LocalItem>?>(null)
    val dailyDiscover = MutableStateFlow<List<DailyDiscoverItem>?>(null)
    val forgottenFavorites = MutableStateFlow<List<Song>?>(null)
    val keepListening = MutableStateFlow<List<LocalItem>?>(null)
    val similarRecommendations = MutableStateFlow<List<SimilarRecommendation>?>(null)
    val accountPlaylists = MutableStateFlow<List<PlaylistItem>?>(null)
    val localPlaylists = MutableStateFlow<List<Playlist>?>(null)

    /**
     * A new install's hero: one song from each artist picked in onboarding, until there's history.
     * Null while it's still being asked for (the hero keeps its bones), empty when there's nothing
     * to ask. CW has no onboarding artist picker, so v1 always lands on empty (DATA-MAPPING §1 row 1).
     */
    val starterSongs = MutableStateFlow<List<StarterSong>?>(null)

    /** Library artists, for the hero before there's any history. Signed-in only; empty otherwise. */
    private val _libraryArtists = MutableStateFlow<List<Artist>>(emptyList())
    val libraryArtists: StateFlow<List<Artist>> = _libraryArtists

    val homePage = MutableStateFlow<HomePage?>(null)
    val communityPlaylists = MutableStateFlow<List<CommunityPlaylistItem>?>(null)

    val recentlyPlayed = MutableStateFlow<List<LocalItem>?>(null)
    val mostPlayedSongsForHome = MutableStateFlow<List<Song>?>(null)
    val recentHistorySongs = MutableStateFlow<List<Song>?>(null)
    val artistListItems = MutableStateFlow<List<ArtistGroup>?>(null)

    /** Phase D (no CW charts endpoint): stays empty, and HomeScreen hides the row. */
    val allTimeHits = MutableStateFlow<List<YTItem>?>(emptyList())

    private val liveShows = MutableStateFlow<List<HomeVideo>?>(null)
    private val musicVideos = MutableStateFlow<List<HomeVideo>?>(null)

    /** Phase D (HomeRecommender port): stays empty, and HomeScreen interleaves zero categories. */
    val homeCategories = MutableStateFlow<List<HomeCategory>?>(emptyList())

    /** YouTube's own all-video shelves in the feed, by section index */
    val feedVideoKinds: StateFlow<Map<Int, FeedVideoKind>> = homePage
        .map { page ->
            page?.sections.orEmpty()
                .mapIndexedNotNull { index, section ->
                    ArtistVideos.feedShelfKind(section.title, section.items)?.let { index to it }
                }
                .toMap()
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    // the feed's other all-video shelves, already turned into banners
    val feedVideoRows: StateFlow<Map<Int, List<HomeVideo>>> = homePage
        .map { page ->
            page?.sections.orEmpty()
                .mapIndexedNotNull { index, section ->
                    if (ArtistVideos.feedShelfKind(section.title, section.items) != FeedVideoKind.Other) return@mapIndexedNotNull null
                    index to section.items.filterIsInstance<SongItem>().map(ArtistVideos::feedVideo)
                }
                .toMap()
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    // YouTube's "Live performances" and "Music videos for you" fold into Home's rows of the same
    // name, so each shows once
    val liveRow: StateFlow<List<HomeVideo>?> = combine(liveShows, homePage, feedVideoKinds) { own, page, kinds ->
        ArtistVideos.merge(own, feedVideos(page, kinds, FeedVideoKind.Live))
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val musicVideoRow: StateFlow<List<HomeVideo>?> = combine(musicVideos, homePage, feedVideoKinds) { own, page, kinds ->
        ArtistVideos.merge(own, feedVideos(page, kinds, FeedVideoKind.MusicVideos))
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private fun feedVideos(page: HomePage?, kinds: Map<Int, FeedVideoKind>, kind: FeedVideoKind): List<HomeVideo> =
        page?.sections.orEmpty().flatMapIndexed { index, section ->
            if (kinds[index] == kind) section.items.filterIsInstance<SongItem>().map(ArtistVideos::feedVideo) else emptyList()
        }

    // one fetch per artist page per load, shared by every row that reads one
    private val artistPages = ConcurrentHashMap<String, Deferred<ArtistPage?>>()

    private suspend fun artistPage(id: String): ArtistPage? {
        val page = artistPages.computeIfAbsent(id) {
            viewModelScope.async(Dispatchers.IO) { YtMusicRepository.artistPage(id).getOrNull() }
        }.await()
        // a failed fetch shouldn't stick for the rest of the load
        if (page == null) artistPages.remove(id)
        return page
    }

    // one library fetch per load, shared by dailyDiscover (liked seeds) and libraryArtists
    private val libraryMutex = Mutex()
    private var libraryResult: Result<LibraryPage>? = null

    private suspend fun libraryPage(): Result<LibraryPage> = libraryMutex.withLock {
        libraryResult ?: YtMusicRepository.library().also { libraryResult = it }
    }

    /** A stats artist's page id: the chart's browseId, else a name lookup (§7.4). */
    private suspend fun resolveArtistId(entry: RankedEntry): String? =
        entry.browseId ?: YtMusicRepository.findArtistPageId(entry.title).getOrNull()

    val allLocalItems = MutableStateFlow<List<LocalItem>>(emptyList())
    val allYtItems = MutableStateFlow<List<YTItem>>(emptyList())

    private val _lastPlayedSong = MutableStateFlow<MediaMetadata?>(null)
    val lastPlayedSong: StateFlow<MediaMetadata?> = _lastPlayedSong

    // No pin store in v1 (Phase D item 22): the dial fills from keepListening
    // then quickPicks only, capped at 27 like Mochy's.
    val speedDialItems: StateFlow<List<YTItem>> =
        combine(keepListening, quickPicks) { keep, quick ->
            val filled = mutableListOf<YTItem>()
            val targetSize = 27
            keep?.let { items ->
                val needed = targetSize - filled.size
                val available = items.filter { item -> filled.none { it.id == item.id } }
                    .mapNotNull { it.toSpeedDialYTItem() }
                filled.addAll(available.take(needed))
            }
            if (filled.size < targetSize) {
                quick?.let { items ->
                    val needed = targetSize - filled.size
                    val available = items.filter { item -> filled.none { it.id == item.id } }
                        .mapNotNull { it.toSpeedDialYTItem() }
                    filled.addAll(available.take(needed))
                }
            }
            filled.take(targetSize)
        }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    suspend fun getRandomItem(): YTItem? {
        try {
            isRandomizing.value = true
            delay(1000)

            val userSongs = mutableListOf<YTItem>()
            val otherSources = mutableListOf<YTItem>()

            quickPicks.value?.forEach { item ->
                when (val mapped = item.toSpeedDialYTItem()) {
                    is SongItem -> userSongs.add(mapped)
                    is PlaylistItem -> otherSources.add(mapped)
                    else -> {}
                }
            }
            keepListening.value?.forEach { item ->
                when (val mapped = item.toSpeedDialYTItem()) {
                    is SongItem -> userSongs.add(mapped)
                    is AlbumItem -> otherSources.add(mapped)
                    else -> {}
                }
            }
            otherSources.addAll(allYtItems.value)

            val item = if (userSongs.isNotEmpty() && (otherSources.isEmpty() || kotlin.random.Random.nextFloat() < 0.8f)) {
                userSongs.distinctBy { it.id }.shuffled().firstOrNull()
            } else {
                otherSources.distinctBy { it.id }.shuffled().firstOrNull()
            } ?: userSongs.firstOrNull() ?: otherSources.firstOrNull()

            return item
        } finally {
            isRandomizing.value = false
        }
    }

    val accountName = MutableStateFlow("Guest")
    val accountImageUrl = MutableStateFlow<String?>(null)

    // --- Per-load stats snapshots (local phase fills, network phase reuses) ---
    private var monthSummary: ReplaySummary? = null
    private var recentTracks: List<TrackEntry> = emptyList()

    // --- Daily Discover -----------------------------------------------------

    private suspend fun getDailyDiscover() {
        val likedSongs = if (SignedIn.state(context).value) {
            libraryPage().getOrNull()?.likedSongs.orEmpty().map { it.toMochySong() }
        } else emptyList()
        val summary = monthSummary
        val mostPlayed = summary?.songs.orEmpty().map { it.song.toMochySong() }
        val recent = recentTracks.map { it.toCwSong().toMochySong() }
        if (recent.isEmpty() && likedSongs.isEmpty()) return

        // seeds from three directions, so the cards don't all orbit one song: something you liked,
        // something you play all the time, something you played just now. one seed per artist.
        val seeds = buildList<Song> {
            fun artistKey(song: Song) = song.artists.firstOrNull()?.let { it.id ?: it.name }
            fun takeFrom(songs: List<Song>, count: Int) {
                songs.shuffled()
                    .filter { song ->
                        none { it.id == song.id } && none { artistKey(it) == artistKey(song) }
                    }
                    .take(count)
                    .forEach { add(it) }
            }
            takeFrom(likedSongs, 2)
            takeFrom(mostPlayed.take(20), 2)
            takeFrom(recent.take(15), 2)
            if (size < 5) takeFrom(recent, 5 - size)
        }

        // anything you've already heard isn't much of a discovery
        val heard = recent.mapTo(HashSet()) { it.id }
        val items = java.util.Collections.synchronizedList(mutableListOf<DailyDiscoverItem>())

        coroutineScope {
            seeds.map { seed ->
                launch(Dispatchers.IO) {
                    // CW has no next+related pair: radio() is the closest call — its
                    // first item is the seed itself, so it gets filtered out.
                    val candidates = YtMusicRepository.radio(seed.id).getOrNull().orEmpty()
                        .filter { it.videoId != seed.id }
                        .map { it.toSongItem() }
                        .filter { item ->
                            !(hideVideoSongs && item.isVideoSong) &&
                                !(hideExplicit && item.explicit) &&
                                !(hideAi && item.looksAiGenerated())
                        }
                    val recommendation = candidates.filter { it.id !in heard }.shuffled().firstOrNull()
                        ?: candidates.shuffled().firstOrNull()
                    if (recommendation != null) {
                        // relatedEndpoint has no CW equivalent; no UI file consumes it.
                        items.add(DailyDiscoverItem(seed = seed, recommendation = recommendation, relatedEndpoint = null))
                    }
                }
            }.forEach { it.join() }
        }

        // an empty result means the requests failed, not that there is nothing to show. one card
        // per artist, so a single act can't take over the hero.
        val found = items.toList()
            .distinctBy { it.recommendation.id }
            .distinctBy { (it.recommendation as? SongItem)?.artists?.firstOrNull()?.name ?: it.recommendation.id }
        if (found.isNotEmpty()) dailyDiscover.value = found.shuffled()
    }

    // --- Quick picks (stats-based; CW's repo quickPicks() is a home-shelf feed, not most-played) ---

    private fun getQuickPicks() {
        quickPicks.value = monthSummary?.songs.orEmpty()
            .map { it.song.toMochySong() }
            .filterVideoSongs(hideVideoSongs)
            .distinctBy { it.id }
            .take(20)
    }

    // --- Community playlists (artist-seed path only; the song-seed path needs a related endpoint) ---

    private suspend fun getCommunityPlaylists() {
        val artistSeeds = monthSummary?.artists.orEmpty()
            .take(10)
            .shuffled().take(3)
        if (artistSeeds.isEmpty()) return

        val candidatePlaylists = java.util.Collections.synchronizedList(mutableListOf<PlaylistItem>())

        coroutineScope {
            artistSeeds.map { seed ->
                launch(Dispatchers.IO) {
                    val pageId = resolveArtistId(seed) ?: return@launch
                    artistPage(pageId)?.let { page ->
                        page.sections.forEach { section ->
                            section.items.mapNotNull { it.toYTItem(section.layout) }
                                .filterIsInstance<PlaylistItem>()
                                .forEach { playlist ->
                                    if (playlist.author?.name != "YouTube Music" &&
                                        playlist.author?.name != "YouTube" &&
                                        playlist.author?.name != "Playlist" &&
                                        playlist.author?.name != seed.title &&
                                        !playlist.id.startsWith("RD") &&
                                        !playlist.id.startsWith("OLAK")
                                    ) {
                                        candidatePlaylists.add(playlist)
                                    }
                                }
                        }
                    }
                }
            }.forEach { it.join() }
        }

        val uniqueCandidates = candidatePlaylists.distinctBy { it.id }.shuffled().take(5)
        val playlists = java.util.Collections.synchronizedList(mutableListOf<CommunityPlaylistItem>())

        coroutineScope {
            uniqueCandidates.map { playlist ->
                launch(Dispatchers.IO) {
                    // PlaylistItem.id drops the VL prefix; browsing needs it back.
                    val entries = YtMusicRepository.playlistEntries("VL${playlist.id}").getOrNull().orEmpty()
                    val songs = entries.take(10).map { it.toSongItem() }
                    if (songs.isNotEmpty()) {
                        val updatedPlaylist = playlist.copy(songCountText = "${entries.size} songs")
                        playlists.add(CommunityPlaylistItem(updatedPlaylist, songs))
                    }
                }
            }.forEach { it.join() }
        }

        // a failed fetch used to overwrite the cached section with nothing,
        // which is how "From the community" vanished until the next lucky refresh
        val kept = playlists.mapNotNull { item ->
            if (hideAi && item.playlist.looksAiGenerated()) return@mapNotNull null
            item.copy(songs = item.songs.filterAiGenerated(hideAi)).takeIf { it.songs.isNotEmpty() }
        }
        if (kept.isNotEmpty()) communityPlaylists.value = kept.shuffled()
    }

    // --- Local phase --------------------------------------------------------

    private fun loadRecentlyPlayed() {
        // Songs only in v1: CW has no recently-played albums/playlists source.
        // (Mochy interleaves songs/albums/playlists and prepends a synthetic
        // "liked" playlist when it was played in the last 7d — CW tracks
        // neither, so both are dropped; see NEEDS.md §(c).)
        recentlyPlayed.value = recentTracks.take(15).map { it.toCwSong().toMochySong() }
    }

    private fun loadMostPlayedForHome() {
        mostPlayedSongsForHome.value = monthSummary?.songs.orEmpty()
            .map { it.song.toMochySong() }
            .filterVideoSongs(hideVideoSongs)
            .distinctBy { it.id }
            .take(20)
    }

    private fun loadRecentHistory() {
        // CW gives distinct tracks by recency; Mochy's event list (repeats
        // possible) cannot be reproduced from aggregates.
        recentHistorySongs.value = recentTracks.take(15).map { it.toCwSong().toMochySong() }
    }

    /**
     * Mochy's forgotten-favorites rule, reproduced off the day buckets
     * (DatabaseDao.kt:665): old = ms on days more than 30 days ago, new = ms
     * on days within the last 30, keep where 0.2 * old > new. The SQL joins
     * against songs that HAVE recent plays, so a track with no plays in the
     * last 30 days does not qualify either — new must be > 0. Day granularity
     * (not Mochy's exact 30x24h timestamp) is the closest the buckets allow.
     */
    private fun loadForgottenFavorites(allTracks: List<TrackEntry>) {
        val cutoff = LocalDate.now().minusDays(30)
        forgottenFavorites.value = allTracks.mapNotNull { track ->
            var oldMs = 0L
            var newMs = 0L
            track.days.forEach { (dateStr, stats) ->
                val date = runCatching { LocalDate.parse(dateStr) }.getOrNull() ?: return@forEach
                if (date < cutoff) oldMs += stats.ms else newMs += stats.ms
            }
            if (newMs > 0 && 0.2 * oldMs > newMs) track to oldMs else null
        }
            .sortedByDescending { it.second }
            .take(100)
            .map { it.first.toCwSong().toMochySong() }
            .filterVideoSongs(hideVideoSongs)
            .shuffled()
            .take(20)
    }

    private fun loadKeepListening() {
        val summary = monthSummary
        // Mochy's windows are rolling 14d; CW's summary is the calendar month
        // (THIS_WEEK would be Mon-today). The offsets/shuffles are Mochy's:
        // songs ranked 6-20 shuffled take 10, albums ranked 3-10 take 5,
        // artists take 5.
        val songs = summary?.songs.orEmpty()
            .drop(5).take(15)
            .map { it.song.toMochySong() }
            .filterVideoSongs(hideVideoSongs)
            .shuffled().take(10)
        val albums = summary?.albums.orEmpty()
            .drop(2).take(8)
            .filter { it.artworkUrl != null }
            .map { it.toMochyAlbum() }
            .shuffled().take(5)
        val artists = summary?.artists.orEmpty()
            .filter { it.browseId?.startsWith("UC") == true && it.artworkUrl != null }
            .map { it.toMochyArtist() }
            .shuffled().take(5)
        // No playlist part in v1: CW has no local playlists of YouTube songs
        // and no playlist-contains-song lookup. No "liked" prepend either:
        // CW does not track when the liked list was last played.
        keepListening.value = (songs + albums + artists).shuffled()
    }

    private suspend fun loadLocalDataPhase() {
        monthSummary = ListeningStats.summary(ReplayPeriod.THIS_MONTH)
        val buckets: List<StoredBucket> = ListeningStats.exportAll()
        val merged = LinkedHashMap<String, TrackEntry>()
        buckets.forEach { bucket ->
            bucket.tracks.forEach { entry ->
                val existing = merged[entry.id]
                if (existing == null) {
                    merged[entry.id] = entry.copy(days = entry.days.toMutableMap())
                } else {
                    existing.absorb(entry)
                }
            }
        }
        val allTracks = merged.values.toList()
        recentTracks = allTracks.filter { it.last > 0 }.sortedByDescending { it.last }

        getQuickPicks()
        loadRecentlyPlayed()
        loadMostPlayedForHome()
        loadRecentHistory()
        loadForgottenFavorites(allTracks)
        loadKeepListening()

        localPlaylists.value = OfflinePlaylists.playlists.value.map { it.toMochyPlaylist() }
        _lastPlayedSong.value = recentTracks.firstOrNull()?.toCwSong()?.toMochyMetadata()

        // No onboarding artist picker in CW: the hero's starter songs stay empty.
        starterSongs.value = emptyList()

        allLocalItems.value = (quickPicks.value.orEmpty() + forgottenFavorites.value.orEmpty() + keepListening.value.orEmpty())
            .filter { it is Song || it is Album || it is Playlist }
    }

    // --- Similar recommendations + artist list ------------------------------

    private suspend fun loadSimilarRecommendations() {
        val summary = monthSummary ?: return

        coroutineScope {
            val artistDeferreds = summary.artists
                .take(15)
                .shuffled().take(4)
                .map { entry ->
                    async(Dispatchers.IO) {
                        val items = mutableListOf<YTItem>()
                        val pageId = resolveArtistId(entry) ?: return@async null
                        artistPage(pageId)?.let { page ->
                            page.sections.takeLast(3).forEach { section ->
                                section.items.mapNotNull { it.toYTItem(section.layout) }.let { items += it }
                            }
                        }
                        SimilarRecommendation(
                            title = entry.toMochyArtist(),
                            items = items
                                .distinctBy { item -> item.id }
                                .filterExplicit(hideExplicit)
                                .filterVideoSongs(hideVideoSongs)
                                .shuffled()
                                .take(12)
                                .ifEmpty { return@async null }
                        )
                    }
                }

            val songDeferreds = summary.songs
                .filter { it.song.albumId != null }
                .shuffled().take(3)
                .map { ranked ->
                    async(Dispatchers.IO) {
                        val song = ranked.song.toMochySong()
                        // CW radio() returns songs only — no albums/artists/
                        // playlists split the way Mochy's related page has.
                        val items = YtMusicRepository.radio(ranked.song.videoId).getOrNull().orEmpty()
                            .filter { it.videoId != ranked.song.videoId }
                            .map { it.toSongItem() }
                            .shuffled().take(10)
                        SimilarRecommendation(
                            title = song,
                            items = items
                                .distinctBy { it.id }
                                .filterExplicit(hideExplicit)
                                .filterVideoSongs(hideVideoSongs)
                                .shuffled()
                                .ifEmpty { return@async null }
                        )
                    }
                }

            val albumDeferreds = summary.albums
                .filter { it.artworkUrl != null }
                .shuffled().take(2)
                .map { entry ->
                    async(Dispatchers.IO) {
                        val items = mutableListOf<YTItem>()
                        // No otherVersions equivalent: the album's own tracks
                        // stand in for Mochy's alternate versions.
                        entry.browseId?.let { browseId ->
                            YtMusicRepository.browseSongs(browseId).getOrNull()?.songs.orEmpty()
                                .map { it.toSongItem() }
                                .let { items += it }
                        }
                        // The chart's album subtitle is the artist's name.
                        entry.subtitle?.takeIf { it.isNotBlank() }?.let { artistName ->
                            YtMusicRepository.findArtistPageId(artistName).getOrNull()?.let { artistId ->
                                artistPage(artistId)?.let { page ->
                                    page.sections.lastOrNull()?.items
                                        ?.mapNotNull { it.toYTItem() }
                                        ?.let { items += it }
                                }
                            }
                        }
                        SimilarRecommendation(
                            title = entry.toMochyAlbum(),
                            items = items
                                .distinctBy { it.id }
                                .filterExplicit(hideExplicit)
                                .filterVideoSongs(hideVideoSongs)
                                .shuffled()
                                .take(10)
                                .ifEmpty { return@async null }
                        )
                    }
                }

            val results = (artistDeferreds + songDeferreds + albumDeferreds).awaitAll()
            val nonNullResults = results.filterNotNull()
            // nothing came back: keep what the artist list already shows
            if (nonNullResults.isEmpty()) return@coroutineScope
            similarRecommendations.value = nonNullResults
                .mapNotNull { row -> row.copy(items = row.items.filterAiGenerated(hideAi)).takeIf { it.items.isNotEmpty() } }
                .ifEmpty { return@coroutineScope }
                .shuffled()

            data class GroupAccumulator(
                var artistId: String?,
                var artistName: String,
                var artistThumbnailUrl: String?,
                val items: MutableList<YTItem> = mutableListOf(),
            )

            val groups = linkedMapOf<String, GroupAccumulator>()
            fun addToGroup(key: String, name: String, thumbnailUrl: String?, id: String?, item: YTItem?) {
                val group = groups.getOrPut(key) { GroupAccumulator(id, name, thumbnailUrl) }
                if (thumbnailUrl != null && group.artistThumbnailUrl == null) group.artistThumbnailUrl = thumbnailUrl
                if (item != null && group.items.none { it.id == item.id }) group.items.add(item)
            }

            nonNullResults.forEach { rec ->
                val seed = rec.title
                rec.items.forEach { item ->
                    when {
                        item is ArtistItem -> addToGroup(item.id, item.title, item.thumbnail, item.id, null)
                        seed is Artist -> addToGroup(seed.id, seed.title, seed.thumbnailUrl, seed.id, item)
                        else -> {
                            val itemArtist = when (item) {
                                is SongItem -> item.artists.firstOrNull()
                                is AlbumItem -> item.artists?.firstOrNull()
                                else -> null
                            }
                            if (itemArtist?.name != null) {
                                addToGroup(itemArtist.id ?: itemArtist.name, itemArtist.name, null, itemArtist.id, item)
                            }
                        }
                    }
                }
            }

            artistListItems.value = groups.values
                .filter { it.items.isNotEmpty() }
                .map {
                    ArtistGroup(
                        artistName = it.artistName,
                        artistId = it.artistId,
                        artistThumbnailUrl = it.artistThumbnailUrl,
                        items = it.items.take(5)
                    )
                }
                .shuffled()
                .take(15)
        }
    }

    // --- Artist videos (Live Shows / Music Videos for You, own halves) ------

    /**
     * "Live Shows" and "Music Videos for You": the Videos and Live performances shelves off the pages
     * of the artists you've played most this month, a few each, dealt out one artist at a time
     */
    private suspend fun loadArtistVideos() {
        // the same switch that keeps video versions out of every other list
        if (hideVideoSongs) {
            liveShows.value = null
            musicVideos.value = null
            return
        }

        val artists = monthSummary?.artists.orEmpty()
            .sortedByDescending { it.ms }
            .take(6)
        if (artists.isEmpty()) return

        val perArtist = coroutineScope {
            artists.map { entry ->
                async(Dispatchers.IO) {
                    val pageId = resolveArtistId(entry)
                    val page = pageId?.let { YtMusicRepository.artistLanding(it).getOrNull() }
                    val shelves = ArtistVideos.shelves(
                        page?.sections.orEmpty().map { shelf ->
                            VideoShelf(shelf.title, shelf.items.mapNotNull { it.toYTItem(shelf.layout) })
                        }
                    )
                    // a few of each artist's six most popular, so a refresh doesn't show the same three
                    fun pick(videos: List<SongItem>) = videos.filterExplicit(hideExplicit)
                        .take(6).shuffled().take(3)
                        .map { ArtistVideos.homeVideo(it, entry.title) }
                    pick(shelves.liveShows) to pick(shelves.musicVideos)
                }
            }.awaitAll()
        }

        val live = ArtistVideos.interleave(perArtist.map { it.first }, limit = 12).distinctBy { it.item.id }
        val liveIds = live.mapTo(HashSet()) { it.item.id }
        val official = ArtistVideos.interleave(
            perArtist.map { (_, videos) -> videos.filterNot { it.item.id in liveIds } },
            limit = 12,
        ).distinctBy { it.item.id }

        // nothing back (offline mid-load, a hiccup) keeps what's already on screen
        if (live.isNotEmpty()) liveShows.value = live
        if (official.isNotEmpty()) musicVideos.value = official
    }

    // --- Home feed ----------------------------------------------------------

    private fun isCommunityOrTrendingSection(title: String): Boolean {
        val titleLower = title.lowercase()
        return "trending" in titleLower || "community" in titleLower
    }

    /** One feed shelf as a HomePage section, filtered the way Mochy filters its feed. */
    private fun toFilteredSection(shelf: HomeShelf): HomePage.Section? {
        if (isCommunityOrTrendingSection(shelf.title)) return null
        val filteredItems = shelf.items
            .mapNotNull { it.toYTItem(shelf.layout) }
            .filterExplicit(hideExplicit)
            .filterVideoSongs(hideVideoSongs)
            .filterYoutubeShorts(hideYoutubeShorts)
            .filterAiGenerated(hideAi)
            .distinctBy { it.id }
        if (filteredItems.isEmpty()) return null
        return HomePage.Section(
            title = shelf.title,
            label = shelf.subtitle.takeIf { it.isNotBlank() },
            thumbnail = null,
            endpoint = shelf.moreBrowseId?.let { BrowseEndpoint(browseId = it, params = shelf.moreParams) },
            items = filteredItems,
        )
    }

    private fun HomeFeed.toHomePage(): HomePage =
        HomePage(
            chips = null, // CW's home feed has no chips; HomeScreen never reads them.
            sections = shelves.mapNotNull { toFilteredSection(it) },
            continuation = continuation,
        )

    private suspend fun loadHomeFeed() {
        // No supplements merge, no recents-prepend (§7.7): Mochy fidelity,
        // the raw FEmusic_home feed. No songVersionsOf in v1 (per-song
        // resolveAudio is too expensive across a whole feed).
        YtMusicRepository.home()
            .onSuccess { feed -> homePage.value = feed.toHomePage() }
            .onFailure { Timber.w(it, "Home: home() failed, keeping previous feed") }
    }

    // --- Account ------------------------------------------------------------

    private suspend fun loadAccount() {
        YtMusicRepository.account()
            .onSuccess {
                accountName.value = it.name
                accountImageUrl.value = it.thumbnailUrl
            }
            .onFailure { Timber.w(it, "Home: account() failed") }
    }

    private suspend fun loadAccountPlaylists() {
        if (!SignedIn.state(context).value) return
        YtMusicRepository.libraryPlaylists()
            .onSuccess { items ->
                accountPlaylists.value = items.mapNotNull { it.toPlaylistItem() }
                    .distinctBy { it.id }
                    .filterNot { it.id == "SE" }
                    .filterYoutubeShorts(hideYoutubeShorts)
                    .distinctBy { it.id }
            }
            .onFailure { Timber.w(it, "Home: libraryPlaylists() failed, keeping previous") }
    }

    private suspend fun loadLibraryArtists() {
        if (!SignedIn.state(context).value) {
            _libraryArtists.value = emptyList()
            return
        }
        libraryPage().onSuccess { page ->
            _libraryArtists.value = page.shelves
                .filter { it.title == "Artists" || it.title == "Subscriptions" }
                .flatMap { it.items }
                .filter { it.browseId?.startsWith("UC") == true }
                .mapNotNull { it.toMochyArtist() }
                .distinctBy { it.id }
                .take(5)
        }.onFailure { Timber.w(it, "Home: library() failed for libraryArtists") }
    }

    // --- Load orchestration -------------------------------------------------

    private suspend fun loadNetworkDataPhase() {
        // each source on its own: one failing request must not cancel the others
        // (that took whole sections down with it), and an unexpected throw
        // shouldn't reach the crash handler
        supervisorScope {
            launchSafely { loadHomeFeed() }
            launchSafely { getDailyDiscover() }
            launchSafely { getCommunityPlaylists() }
            launchSafely { loadSimilarRecommendations() }
            launchSafely { loadArtistVideos() }
            launchSafely { loadAccountPlaylists() }
            launchSafely { loadLibraryArtists() }
        }

        allYtItems.value = similarRecommendations.value?.flatMap { it.items }.orEmpty() +
            homePage.value?.sections?.flatMap { it.items }.orEmpty()
    }

    private fun CoroutineScope.launchSafely(block: suspend () -> Unit) = launch(Dispatchers.IO) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Home: loader failed")
        }
    }

    private val loadMutex = Mutex()

    @Volatile
    private var reloadRequested = false

    /**
     * init, pull-to-refresh and the network-back reload can all ask at once. overlapping runs used to
     * race each other's writes, so a request that lands mid-load now just queues one more pass.
     */
    private suspend fun load() {
        if (!loadMutex.tryLock()) {
            reloadRequested = true
            return
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

    private suspend fun loadOnce() {
        isLoading.value = true
        try {
            artistPages.clear()
            libraryResult = null
            loadLocalDataPhase()
        } finally {
            isLoading.value = false
        }
        loadNetworkDataPhase()
        lastLoadAt = SystemClock.elapsedRealtime()
    }

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore

    fun loadMoreYouTubeItems(continuation: String?) {
        if (continuation == null || _isLoadingMore.value) return
        viewModelScope.launch(Dispatchers.IO) {
            _isLoadingMore.value = true
            try {
                val feed = YtMusicRepository.moreHome(continuation).getOrNull() ?: return@launch
                val current = homePage.value
                val existingTitles = current?.sections.orEmpty().mapTo(HashSet()) { it.title.lowercase() }
                val added = feed.shelves.mapNotNull { toFilteredSection(it) }
                    .filter { it.title.lowercase() !in existingTitles }
                // A page with nothing new signals the feed has looped back on
                // itself rather than run dry with a token still attached —
                // treat it the same as exhausted so scrolling can't spin here.
                val nextContinuation = feed.continuation.takeIf { added.isNotEmpty() }
                homePage.value = HomePage(
                    chips = current?.chips,
                    sections = current?.sections.orEmpty() + added,
                    continuation = nextContinuation,
                )
            } finally {
                _isLoadingMore.value = false
            }
        }
    }

    @Volatile
    private var lastLoadAt = 0L

    /**
     * Home has no pull to refresh; it refreshes itself when your listening says it's worth it. this
     * runs when the player is closed and when Home comes back into view. After half an hour away
     * the whole page reloads, never more than once a minute, and never on top of a load already
     * running. The taste trigger (2 skips / 4 plays-through) is Phase D — it needs the
     * skip/play signals attachPlayer does not count yet.
     */
    fun onListeningBreak() {
        val sinceLoad = SystemClock.elapsedRealtime() - lastLoadAt
        if (lastLoadAt == 0L || sinceLoad < 60_000L || loadMutex.isLocked) return
        if (sinceLoad <= 30 * 60_000L) return
        viewModelScope.launch(Dispatchers.IO) { load() }
    }

    private var listening: Job? = null
    private var listeningTo: MochyPlayerConnection? = null

    /**
     * Follows the player the way Mochy's attachPlayer does (current track +
     * played time per track), but the skip / play-through counting is Phase D
     * (DATA-MAPPING §5 item 19): there is no ListeningSignals port yet, so the
     * collector tracks the state and the one-line hook where the signals will
     * plug in is marked below. onListeningBreak stays time-based until then.
     */
    fun attachPlayer(connection: MochyPlayerConnection) {
        if (listeningTo === connection) return
        listeningTo = connection
        listening?.cancel()
        listening = viewModelScope.launch {
            var current: MediaMetadata? = null
            var playedMs = 0L
            var playingSince = -1L
            combine(connection.mediaMetadata, connection.isPlaying) { metadata, playing -> metadata to playing }
                .collect { (metadata, playing) ->
                    val now = SystemClock.elapsedRealtime()
                    if (playingSince >= 0) {
                        playedMs += now - playingSince
                        playingSince = -1L
                    }
                    if (metadata?.id != current?.id) {
                        // Phase D hook: signals.onTrackLeft(current, playedMs)
                        current = metadata
                        playedMs = 0L
                    }
                    if (playing && metadata != null) playingSince = now
                }
        }
    }

    /**
     * The one database touch HomeScreen used to make itself
     * (DATA-MAPPING §1 row 21), as an adapter method: the songs of a
     * playlist, IN PLAYLIST ORDER — the caller does not sort.
     *
     * Local ids (OfflinePlaylists, incl. "liked") resolve against the local
     * store; anything else is treated as a YouTube playlist/album browse id
     * (VL/PL/OLAK/MPRE) and read off the server. playlistEntries pages the
     * whole list; if it fails (e.g. a playlist too long to page), the first
     * browseSongs page stands in.
     */
    suspend fun playlistSongs(playlistId: String): List<MediaMetadata> {
        OfflinePlaylists.get(OfflinePlaylists.PREFIX + playlistId)?.let { playlist ->
            return playlist.songs.map { it.toMochyMetadata() }
        }
        val browseId = when {
            playlistId.startsWith("VL") || playlistId.startsWith("MPRE") -> playlistId
            playlistId.startsWith("PL") || playlistId.startsWith("OLAK") -> "VL$playlistId"
            else -> return emptyList()
        }
        return YtMusicRepository.playlistEntries(browseId).getOrNull()?.map { it.toMochyMetadata() }
            ?: YtMusicRepository.browseSongs(browseId).getOrNull()?.songs?.map { it.toMochyMetadata() }
            ?: emptyList()
    }

    fun refresh() {
        if (isRefreshing.value) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                isRefreshing.value = true
                load()
            } finally {
                isRefreshing.value = false
            }
        }
    }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            load()
        }

        // distinct, so any other state write no longer re-fetches the account.
        // the first load() fetches the account playlists itself; a later
        // sign-in has to do it here.
        viewModelScope.launch(Dispatchers.IO) {
            var isFirstSignedIn = true
            SignedIn.state(context).collect { signedIn ->
                val signedInLater = !isFirstSignedIn
                isFirstSignedIn = false
                if (signedIn) {
                    loadAccount()
                    if (signedInLater) loadAccountPlaylists()
                } else {
                    accountName.value = "Guest"
                    accountImageUrl.value = null
                    accountPlaylists.value = null
                }
            }
        }
    }
}
