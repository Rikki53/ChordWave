// Ported from Mochy Music (GPL-3.0): viewmodels/AlbumViewModel.kt,
// re-implemented over ChordWave sources: YtMusicRepository.browseSongs()
// supplies the album (header, tracks, library state, description) and
// artistLanding() the main artist's other albums. Mochy's Room reads are
// gone — the screen never touches the database, and the like actions live
// here (toggleAlbumLike / toggleSongLike) instead of in the screen.
//
// Adaptations vs Mochy:
// - No saved-state handle: the host calls load(albumId) with the route arg
//   (coordinator decision — the route is available from
//   navigator.currentDetail). A repeated load() with the same id is a
//   no-op; retry() forces a fresh fetch. albumId is a val backed by the
//   loaded arg ("" until load() runs).
// - No local albums: CW has no local-album store to read one from, so a
//   non-YouTube album id fails to load (loadFailed) instead of skipping
//   the fetch the way Mochy's isLocal check did.
// - otherVersions / releasesForYou stay empty: CW's album response carries
//   neither shelf, and there is no other-versions endpoint to ask.
// - description comes from YouTube only: no Wikipedia / Apple Music
//   fallback (CW has no such provider).
// - format stays null: CW has no per-song format cache (see FormatEntity).
// - The artist-thumbnail repair Mochy did after a fetch (YouTube.artist +
//   ArtistImageResolver) is dropped with the database.
// - toggleSongLike also does the sync Mochy did through LocalSyncUtils:
//   the rating goes to YouTube via rate(), and LikeState records it so
//   every surface paints the new state at once.
// (phase2-detail NEEDS.md §(a) C1.)
package com.music.bitchord.ui.mochy.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.bitchord.data.LikeState
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.innertube.InnertubeParser
import com.music.bitchord.data.model.LikeStatus
import com.music.bitchord.data.model.durationMillis
import com.music.bitchord.data.model.Song as CwSong
import com.music.bitchord.ui.mochy.models.AlbumEntity
import com.music.bitchord.ui.mochy.models.AlbumWithSongs
import com.music.bitchord.ui.mochy.models.ArtistEntity
import com.music.bitchord.ui.mochy.models.FormatEntity
import com.music.bitchord.ui.mochy.models.Song
import com.music.bitchord.ui.mochy.models.innertube.AlbumItem
import com.music.bitchord.ui.mochy.models.toMochySong
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDateTime
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

@HiltViewModel
class AlbumViewModel @Inject constructor() : ViewModel() {
    private var loadedAlbumId = ""

    /** The route's album id; "" until load() runs. */
    val albumId: String get() = loadedAlbumId

    private val _albumWithSongs = MutableStateFlow<AlbumWithSongs?>(null)
    val albumWithSongs: StateFlow<AlbumWithSongs?> = _albumWithSongs

    val otherVersions = MutableStateFlow<List<AlbumItem>>(emptyList())
    val releasesForYou = MutableStateFlow<List<AlbumItem>>(emptyList())
    val description = MutableStateFlow<String?>(null)

    /** the main artist's other albums and singles, newest first, without this one */
    val moreByArtist = MutableStateFlow<List<AlbumItem>>(emptyList())

    /** YouTube couldn't be reached and nothing was saved to show instead: the page offers a retry */
    val loadFailed = MutableStateFlow(false)

    // No CW format cache: always null (the screen omits the format line).
    private val _format = MutableStateFlow<FormatEntity?>(null)
    val format: StateFlow<FormatEntity?> = _format

    /** The args the current page belongs to; null until load() is called. */
    @Volatile
    private var loadedKey: String? = null

    private var moreByArtistFor: String? = null

    /**
     * Loads one album. The coordinator patches the ported screen to call
     * this with the route arg; a repeated load() with the same id is a
     * no-op, and a new id resets every flow first, so a reused VM never
     * shows the previous album while loading.
     */
    fun load(albumId: String) {
        if (albumId == loadedKey) return
        loadedKey = albumId
        loadedAlbumId = albumId
        _albumWithSongs.value = null
        otherVersions.value = emptyList()
        releasesForYou.value = emptyList()
        moreByArtist.value = emptyList()
        description.value = null
        loadFailed.value = false
        moreByArtistFor = null
        viewModelScope.launch(Dispatchers.IO) { fetch(albumId) }
    }

    fun retry() {
        if (loadedAlbumId.isEmpty()) return
        loadFailed.value = false
        loadedKey = null
        load(loadedAlbumId)
    }

    private suspend fun fetch(albumId: String) {
        YtMusicRepository.browseSongs(albumId)
            .onSuccess { page ->
                if (albumId != loadedKey) return@onSuccess
                val songs = page.songs
                if (songs.isEmpty()) {
                    loadFailed.value = true
                    return@onSuccess
                }
                val header = page.header
                val artists = artistsOf(songs, header?.subtitle)
                val albumEntity = AlbumEntity(
                    id = albumId,
                    playlistId = page.library?.playlistId,
                    title = header?.title ?: albumId,
                    year = header?.subtitle?.let { AlbumYearRegex.find(it)?.value?.toIntOrNull() },
                    thumbnailUrl = header?.thumbnailUrl ?: songs.firstOrNull()?.thumbnailUrl,
                    songCount = songs.size,
                    duration = songs.sumOf { (it.durationMillis() / 1_000).toInt().coerceAtLeast(0) },
                    explicit = songs.any { it.isExplicit == true },
                    // the screen only null-checks this (isSaved); CW's
                    // library state says saved or not, never when
                    bookmarkedAt = if (page.library?.saved == true) LocalDateTime.now() else null,
                    description = page.description,
                )
                val mochySongs = songs.map { it.toMochySong().copy(album = albumEntity).withLikeAndDownloadState() }
                _albumWithSongs.value = AlbumWithSongs(albumEntity, artists, mochySongs)
                description.value = page.description
                loadFailed.value = false
                artists.firstOrNull()?.id?.let { loadMoreByArtist(it, albumId) }
            }
            .onFailure {
                if (albumId != loadedKey) return@onFailure
                Timber.w(it, "Album: browseSongs failed for $albumId")
                loadFailed.value = true
            }
    }

    /** The album's credited artists: the rows' own links, else the header's byline. */
    private fun artistsOf(songs: List<CwSong>, subtitle: String?): List<ArtistEntity> {
        songs.firstNotNullOfOrNull { it.artists.takeIf { refs -> refs.isNotEmpty() } }
            ?.let { refs -> return refs.map { ArtistEntity(id = it.browseId ?: it.name, name = it.name) } }
        val first = songs.firstOrNull() ?: return emptyList()
        val artistId = first.artistId
        if (artistId != null) return listOf(ArtistEntity(id = artistId, name = first.artist))
        val name = subtitle?.let { InnertubeParser.artistFromSubtitle(it) }?.takeIf { it.isNotBlank() }
            ?: first.artist
        return listOf(ArtistEntity(id = name, name = name))
    }

    private fun loadMoreByArtist(artistId: String, albumId: String) {
        if (moreByArtistFor == artistId) return
        moreByArtistFor = artistId
        viewModelScope.launch(Dispatchers.IO) {
            // the artist page the user may just have come from, else one
            // landing fetch (Mochy fetched the full page; the landing
            // shelves hold enough cards for a take-16 row)
            val page = ArtistPageStore.page(artistId)
                ?: YtMusicRepository.artistLanding(artistId).getOrNull()
                    ?.toMochyArtistPage(artistId)
                    ?.also { ArtistPageStore.putPage(artistId, it) }
                ?: return@launch
            if (albumId != loadedKey) return@launch
            moreByArtist.value = page.sections
                .filter { it.items.firstOrNull() is AlbumItem }
                .flatMap { section -> section.items.filterIsInstance<AlbumItem>() }
                .filter { it.browseId != albumId }
                .distinctBy { it.browseId }
                .sortedByDescending { it.year ?: 0 }
                .take(16)
        }
    }

    /**
     * The header's heart: flips at once, then saves/unsaves the backing
     * playlist on the signed-in account, backing out on failure. Without a
     * playlist id (signed out, or a page that offers no save button) the
     * flip is local-only — CW has no local album-like store to persist it
     * in, so it does not survive a reload.
     */
    fun toggleAlbumLike() {
        val current = _albumWithSongs.value ?: return
        val target = current.album.bookmarkedAt == null
        _albumWithSongs.value = current.copy(album = current.album.localToggleLike())
        val playlistId = current.album.playlistId ?: return
        viewModelScope.launch(Dispatchers.IO) {
            YtMusicRepository.setSaved(playlistId, target).onFailure {
                Timber.w(it, "Album: setSaved failed for $playlistId")
                val latest = _albumWithSongs.value ?: return@onFailure
                _albumWithSongs.value = latest.copy(album = latest.album.localToggleLike())
            }
        }
    }

    /**
     * A track row's heart: flips at once, records the rating in LikeState
     * (the sync Mochy did through its song-like sync — every surface paints
     * the new state immediately), then rates the track on the signed-in
     * account, backing out on failure.
     */
    fun toggleSongLike(song: Song) {
        val current = _albumWithSongs.value ?: return
        val target = !song.song.liked
        _albumWithSongs.value = current.copy(
            songs = current.songs.map {
                if (it.id == song.id) it.copy(song = it.song.localToggleLike()) else it
            },
        )
        LikeState.set(song.id, if (target) LikeStatus.LIKE else LikeStatus.INDIFFERENT)
        viewModelScope.launch(Dispatchers.IO) {
            YtMusicRepository.rate(song.id, if (target) LikeStatus.LIKE else LikeStatus.INDIFFERENT)
                .onFailure {
                    Timber.w(it, "Album: rate failed for ${song.id}")
                    LikeState.set(song.id, if (target) LikeStatus.INDIFFERENT else LikeStatus.LIKE)
                    val latest = _albumWithSongs.value ?: return@onFailure
                    _albumWithSongs.value = latest.copy(
                        songs = latest.songs.map {
                            if (it.id == song.id) it.copy(song = it.song.localToggleLike()) else it
                        },
                    )
                }
        }
    }
}
