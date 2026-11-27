// Ported from Mochy Music (GPL-3.0): viewmodels/ArtistViewModel.kt,
// re-implemented over ChordWave sources: YtMusicRepository.artistLanding()
// supplies the page (Mappers6 maps it onto Mochy's ArtistPage shape),
// LibraryArtistSource the library row/songs/albums, setSubscribed() the
// channel follow, releaseLibraryState()/setSaved() the release card's save.
//
// Adaptations vs Mochy:
// - No saved-state handle: the host calls load(artistId) with the route
//   arg (coordinator decision — the route is available from
//   navigator.currentDetail). A repeated load() with the same id is a
//   no-op; a new id reseeds the page from ArtistPageStore and the header
//   from ArtistPageCache, so a revisited artist shows instantly while the
//   fresh copy loads. artistId is a val backed by the loaded arg ("" until
//   load() runs).
// - The page comes from artistLanding() (one request), not artistPage():
//   Mochy's page shows the landing shelves, and topSongs() fetches the
//   full song list on demand. artistPage() would page out every shelf
//   before the screen could draw.
// - No Apple Music editorial provider in CW: apple stays null forever, so
//   the header is the YouTube photo (banner = photo, no motion, no logo)
//   and pickRelease() runs without Apple's latest-release match. The
//   canvas-motion fallback (ArtistVideoCanvasProvider + ShowArtistVideoKey)
//   is dropped with it.
// - No local artist-like store in CW: toggleLike() flips the in-memory
//   library row only (a row is built from the page when the artist has
//   none). It does not survive a reload, and it does not touch YouTube —
//   Mochy's like was device-local too, apart from the channel follow,
//   which toggleSubscribe() still performs.
// - releaseSaved reads the release album's library state from YouTube
//   (releaseLibraryState) instead of the database; toggleReleaseSaved()
//   saves/unsaves its backing playlist the same way AlbumViewModel does.
// - libraryArtist/librarySongs/libraryAlbums come from
//   LibraryArtistSource (the signed-in account's library), re-filtered
//   whenever the HideExplicit/HideVideoSongs prefs change, the way
//   Mochy's flatMapLatest chains did.
// (phase2-detail NEEDS.md §(a) C4.)
package com.music.bitchord.ui.mochy.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.ui.mochy.constants.HideExplicitKey
import com.music.bitchord.ui.mochy.constants.HideVideoSongsKey
import com.music.bitchord.ui.mochy.constants.HideYoutubeShortsKey
import com.music.bitchord.ui.mochy.models.Album
import com.music.bitchord.ui.mochy.models.Artist
import com.music.bitchord.ui.mochy.models.ArtistEntity
import com.music.bitchord.ui.mochy.models.Song
import com.music.innertube.models.AlbumItem
import com.music.bitchord.ui.mochy.models.innertube.ArtistPage
import com.music.innertube.models.SongItem
import com.music.innertube.models.filterExplicit
import com.music.innertube.models.filterVideoSongs
import com.music.innertube.models.filterYoutubeShorts
import com.music.bitchord.ui.mochy.utils.ArtistHeader
import com.music.bitchord.ui.mochy.utils.ArtistPageCache
import com.music.bitchord.ui.mochy.utils.dataStore
import com.music.bitchord.ui.mochy.utils.get
import com.music.bitchord.ui.mochy.utils.resize
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

/**
 * the card under the artist's buttons: their newest release, or, for an artist with none listed,
 * their top song. [album] or [song] is what the card's play and save act on.
 */
data class ArtistRelease(
    val label: String,
    val title: String,
    val subtitle: String,
    val thumbnailUrl: String?,
    val dateLabel: String?,
    val album: AlbumItem? = null,
    val song: SongItem? = null,
)

/**
 * Apple's editorial record for an artist (banner motion, logo, bio, latest
 * release). CW has no Apple Music provider, so ArtistViewModel.apple is
 * always null — the class is ported so the screen's reads compile and a
 * later phase can fill it without touching the screens.
 */
data class AppleArtistEditorial(
    val appleId: String,
    val name: String,
    val motionVideoUrl: String?,
    val motionSquareUrl: String?,
    val previewFrameUrl: String?,
    val bannerUrl: String?,
    val logoUrl: String?,
    val logoAspect: Float?,
    val bio: String?,
    val latestRelease: AppleRelease?,
)

data class AppleRelease(
    val name: String,
    val releaseDate: String?,
    val artworkUrl: String?,
    val isSingle: Boolean,
    val trackCount: Int?,
)

@HiltViewModel
class ArtistViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private var loadedArtistId = ""

    /** The route's artist id; "" until load() runs. */
    val artistId: String get() = loadedArtistId

    // a page opened a moment ago comes straight back while the fresh copy loads
    private val _page = MutableStateFlow<ArtistPage?>(null)
    val page: StateFlow<ArtistPage?> = _page

    private val _header = MutableStateFlow<ArtistHeader?>(null)
    val header: StateFlow<ArtistHeader?> = _header

    // no Apple provider in CW: stays null (see the class doc above)
    private val _apple = MutableStateFlow<AppleArtistEditorial?>(null)
    val apple: StateFlow<AppleArtistEditorial?> = _apple

    private val _release = MutableStateFlow<ArtistRelease?>(null)
    val release: StateFlow<ArtistRelease?> = _release

    /** whether the newest release is in the library, for the card's + button */
    private val _releaseSaved = MutableStateFlow(false)
    val releaseSaved: StateFlow<Boolean> = _releaseSaved

    private val _subscribed = MutableStateFlow<Boolean?>(null)
    val subscribed: StateFlow<Boolean?> = _subscribed

    private val _loadFailed = MutableStateFlow(false)
    val loadFailed: StateFlow<Boolean> = _loadFailed

    private val _libraryArtist = MutableStateFlow<Artist?>(null)
    val libraryArtist: StateFlow<Artist?> = _libraryArtist

    private val _librarySongs = MutableStateFlow<List<Song>>(emptyList())
    val librarySongs: StateFlow<List<Song>> = _librarySongs

    private val _libraryAlbums = MutableStateFlow<List<Album>>(emptyList())
    val libraryAlbums: StateFlow<List<Album>> = _libraryAlbums

    /** the release album's backing playlist id, once releaseLibraryState has answered */
    private var releasePlaylistId: String? = null

    /** the library lists before the HideExplicit/HideVideoSongs filters */
    private var rawLibrarySongs: List<Song> = emptyList()
    private var rawLibraryAlbums: List<Album> = emptyList()

    /** The args the current page belongs to; null until load() is called. */
    @Volatile
    private var loadedKey: String? = null

    private var prefsJob: Job? = null
    private var releaseJob: Job? = null

    /**
     * Loads one artist page. The coordinator patches the ported screen to
     * call this with the route arg; a repeated load() with the same id is
     * a no-op.
     */
    fun load(artistId: String) {
        if (artistId == loadedKey) return
        loadedKey = artistId
        loadedArtistId = artistId
        _page.value = ArtistPageStore.page(artistId)
        _header.value = ArtistPageCache.header(artistId)
        _subscribed.value = _page.value?.subscribed
        _loadFailed.value = false
        _libraryArtist.value = null
        _librarySongs.value = emptyList()
        _libraryAlbums.value = emptyList()
        rawLibrarySongs = emptyList()
        rawLibraryAlbums = emptyList()
        releasePlaylistId = null
        updateHeader()
        updateRelease()
        // a filter toggle re-fetches the page (Mochy's init collector) and
        // re-filters the library lists; the first emission is the initial load
        prefsJob?.cancel()
        prefsJob = viewModelScope.launch {
            context.dataStore.data
                .map {
                    Triple(
                        it[HideExplicitKey] ?: false,
                        it[HideVideoSongsKey] ?: false,
                        it[HideYoutubeShortsKey] ?: true,
                    )
                }
                .distinctUntilChanged()
                .collect { (hideExplicit, hideVideoSongs, _) ->
                    fetchArtistsFromYTM()
                    deriveLibraryLists(hideExplicit, hideVideoSongs)
                }
        }
        viewModelScope.launch(Dispatchers.IO) { loadLibrary(artistId) }
    }

    fun fetchArtistsFromYTM() {
        val id = loadedArtistId
        if (id.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val hideExplicit = context.dataStore.get(HideExplicitKey, false)
            val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, false)
            val hideYoutubeShorts = context.dataStore.get(HideYoutubeShortsKey, true)
            YtMusicRepository.artistLanding(id)
                .onSuccess { cwPage ->
                    if (id != loadedKey) return@onSuccess
                    val mapped = cwPage.toMochyArtistPage(id)
                    val filteredSections = mapped.sections
                        .map { section ->
                            section.copy(
                                items = section.items
                                    .filterExplicit(hideExplicit)
                                    .filterVideoSongs(hideVideoSongs)
                                    .filterYoutubeShorts(hideYoutubeShorts),
                            )
                        }
                        .filter { section -> section.items.isNotEmpty() }
                    val filtered = mapped.copy(sections = filteredSections)
                    _page.value = filtered
                    _loadFailed.value = false
                    _subscribed.value = mapped.subscribed
                    ArtistPageStore.putPage(id, filtered)
                    updateHeader()
                    updateRelease()
                    // the page's name lets the library match by name too
                    // (for artists whose songs carry no channel id)
                    loadLibrary(id)
                }.onFailure {
                    if (id != loadedKey) return@onFailure
                    Timber.w(it, "Artist: landing failed for $id")
                    if (_page.value == null) _loadFailed.value = true
                }
        }
    }

    private suspend fun loadLibrary(artistId: String) {
        val nameHint = ArtistPageCache.header(artistId)?.name ?: _page.value?.artist?.title
        val page = LibraryArtistSource.libraryPage().getOrNull() ?: return
        if (artistId != loadedKey) return
        _libraryArtist.value = LibraryArtistSource.artistFor(page, artistId, nameHint)
        rawLibrarySongs = LibraryArtistSource.songsFor(page, artistId, nameHint)
        rawLibraryAlbums = LibraryArtistSource.albumsFor(page, artistId, nameHint)
        deriveLibraryLists(
            context.dataStore.get(HideExplicitKey, false),
            context.dataStore.get(HideVideoSongsKey, false),
        )
        updateHeader()
    }

    private fun deriveLibraryLists(hideExplicit: Boolean, hideVideoSongs: Boolean) {
        _librarySongs.value = rawLibrarySongs.filterExplicit(hideExplicit).filterVideoSongs(hideVideoSongs)
        _libraryAlbums.value = rawLibraryAlbums.filterExplicitAlbums(hideExplicit)
    }

    /** the round photo from YouTube Music; the banner is the photo (no Apple motion/logo in CW) */
    private fun updateHeader() {
        val id = loadedArtistId
        val name = _page.value?.artist?.title ?: _libraryArtist.value?.artist?.name ?: return
        val photo = _page.value?.artist?.thumbnail ?: _libraryArtist.value?.artist?.thumbnailUrl
        val header = ArtistHeader(
            id = id,
            name = name,
            avatarUrl = photo?.resize(544, 544),
            bannerUrl = photo?.resize(1200, 1200),
            motionUrl = null,
            logoUrl = null,
            logoAspect = null,
        )
        _header.value = header
        ArtistPageCache.putHeader(header)
    }

    private fun updateRelease() {
        val rel = _page.value?.let { pickRelease(it, _apple.value) }
        _release.value = rel
        releaseJob?.cancel()
        releaseJob = viewModelScope.launch(Dispatchers.IO) {
            val album = rel?.album
            if (album == null) {
                _releaseSaved.value = false
                releasePlaylistId = null
                return@launch
            }
            val state = YtMusicRepository.releaseLibraryState(album.browseId).getOrNull()
            releasePlaylistId = state?.playlistId
            _releaseSaved.value = state?.saved == true
        }
    }

    /** the library's heart: kept on this device, apart from following the channel on YouTube */
    fun toggleLike() {
        val artist = _libraryArtist.value
        if (artist != null) {
            _libraryArtist.value = artist.copy(artist = artist.artist.localToggleLike())
            return
        }
        val page = _page.value ?: return
        _libraryArtist.value = Artist(
            artist = ArtistEntity(
                id = page.artist.id,
                name = page.artist.title,
                channelId = page.artist.channelId,
                thumbnailUrl = page.artist.thumbnail,
            ).localToggleLike(),
            songCount = 0,
        )
    }

    /** follows the channel on the signed-in YouTube account; flips at once and backs out on failure */
    fun toggleSubscribe() {
        val channelId = _page.value?.artist?.channelId
            ?: loadedArtistId.takeIf { it.startsWith("UC") }
            ?: return
        val target = _subscribed.value != true
        _subscribed.value = target
        viewModelScope.launch(Dispatchers.IO) {
            YtMusicRepository.setSubscribed(channelId, target).onFailure {
                Timber.w(it, "Artist: subscribe failed for $channelId")
                _subscribed.value = !target
            }
        }
    }

    /** every top song, from the shelf's own page when there is one, else the five the shelf shows */
    suspend fun topSongs(): List<SongItem> {
        val section = _page.value?.sections?.firstOrNull { section ->
            (section.items.firstOrNull() as? SongItem)?.album != null
        } ?: return emptyList()
        val shown = section.items.filterIsInstance<SongItem>()
        val more = section.moreEndpoint ?: return shown
        val all = withContext(Dispatchers.IO) {
            withTimeoutOrNull(8_000) { YtMusicRepository.browseSongs(more.browseId).getOrNull() }
        }?.songs?.map { it.toSongItem() }
        return all?.takeIf { it.isNotEmpty() } ?: shown
    }

    /** the + on the release card: saves/unsaves the album's backing playlist on the account */
    fun toggleReleaseSaved() {
        val album = _release.value?.album ?: return
        val target = !_releaseSaved.value
        _releaseSaved.value = target
        viewModelScope.launch(Dispatchers.IO) {
            val playlistId = releasePlaylistId
                ?: YtMusicRepository.releaseLibraryState(album.browseId).getOrNull()
                    ?.also { releasePlaylistId = it.playlistId }
                    ?.playlistId
            if (playlistId == null) {
                _releaseSaved.value = !target
                return@launch
            }
            YtMusicRepository.setSaved(playlistId, target).onFailure {
                Timber.w(it, "Artist: setSaved failed for $playlistId")
                _releaseSaved.value = !target
            }
        }
    }

    private fun pickRelease(page: ArtistPage, apple: AppleArtistEditorial?): ArtistRelease? {
        // albums come first on the page, then singles & EPs; neither shelf says which it is in a
        // way that survives translation, so the order does
        val albumShelves = page.sections.filter { it.items.firstOrNull() is AlbumItem }
        val candidates = albumShelves.flatMapIndexed { shelf, section ->
            section.items.filterIsInstance<AlbumItem>().take(8).map { it to (shelf > 0) }
        }
        val latest = apple?.latestRelease
        val matched = latest?.let { rel ->
            val wanted = normalize(rel.name)
            candidates.firstOrNull { normalize(it.first.title) == wanted }
                ?: candidates.firstOrNull { normalize(it.first.title).startsWith(wanted) || wanted.startsWith(normalize(it.first.title)) }
        }
        val pick = matched
            ?: candidates.maxWithOrNull(compareBy<Pair<AlbumItem, Boolean>> { it.first.year ?: 0 }.thenByDescending { candidates.indexOf(it) })

        if (pick == null) {
            val top = page.sections
                .firstOrNull { (it.items.firstOrNull() as? SongItem)?.album != null }
                ?.items?.firstOrNull() as? SongItem
                ?: return null
            return ArtistRelease(
                label = "Highlight",
                title = top.title,
                subtitle = top.album?.name ?: "Top song",
                thumbnailUrl = top.thumbnail,
                dateLabel = null,
                song = top,
            )
        }

        val (album, fromSingles) = pick
        val date = if (matched != null) latest?.releaseDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } else null
        val isSingle = if (matched != null) latest?.isSingle == true else fromSingles
        val recent = when {
            date != null -> ChronoUnit.DAYS.between(date, LocalDate.now()) in 0..120
            else -> album.year != null && album.year!! >= LocalDate.now().year
        }
        val kind = if (isSingle) "Single" else "Album"
        return ArtistRelease(
            label = if (recent) "New release" else "Latest release",
            title = album.title,
            subtitle = listOfNotNull(
                kind,
                latest?.trackCount?.takeIf { matched != null && it > 1 }?.let { "$it songs" },
            ).joinToString(" · "),
            thumbnailUrl = album.thumbnail,
            dateLabel = date?.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()))
                ?: album.year?.toString(),
            album = album,
        )
    }

    private fun normalize(title: String): String =
        java.text.Normalizer.normalize(title.lowercase(Locale.ROOT), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace(Regex("[^\\p{L}\\p{N}]+"), "")
}
