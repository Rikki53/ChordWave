// LibraryArtistSource.kt — the one place the detail VMs read "what of this
// artist is in the library". Mochy answers that from its Room database
// (artist / artistSongsPreview / artistAlbumsPreview); ChordWave has no
// local library store, so the answer comes from the signed-in account's
// YtMusicRepository.library(): Liked Music + library songs for the tracks,
// the Artists/Subscriptions shelves for the artist row, the Albums shelf
// plus the songs' own album ids for the albums.
//
// The library fetch is memoized for 5 minutes and shared by ArtistViewModel,
// ArtistAlbumsViewModel and ArtistSongsViewModel, so opening an artist page
// and its two subpages costs one library fetch, not three. A failed fetch
// is memoized too — a signed-out account fails fast every time, and
// re-fetching on every subpage would just repeat the failure. The memo is
// deliberately short-lived: a like/unlike elsewhere shows up on the next
// artist page opened after it expires.
//
// Matching: a song belongs to the artist when its lead artist id or any
// credited artist id equals the page id; when the id is not a channel id
// (a name-as-id from the stats charts, or a local artist), the artist NAME
// matches instead (case-insensitive). Name matching can over-match two
// artists sharing a name — the honest answer for an id-less artist, and
// the same rule HomeViewModel's resolveArtistId documents.
package com.music.bitchord.ui.mochy.viewmodels

import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.innertube.InnertubeParser
import com.music.bitchord.data.model.LibraryPage
import com.music.bitchord.data.model.Song as CwSong
import com.music.bitchord.ui.mochy.models.Album
import com.music.bitchord.ui.mochy.models.AlbumEntity
import com.music.bitchord.ui.mochy.models.Artist
import com.music.bitchord.ui.mochy.models.ArtistEntity
import com.music.bitchord.ui.mochy.models.Song
import com.music.bitchord.ui.mochy.models.toMochySong
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal object LibraryArtistSource {
    private val mutex = Mutex()
    private var cached: Result<LibraryPage>? = null
    private var cachedAt = 0L

    /** How long one library() result is shared between the detail VMs. */
    private const val CACHE_MS = 5 * 60 * 1000L

    suspend fun libraryPage(): Result<LibraryPage> = mutex.withLock {
        val hit = cached
        if (hit != null && System.currentTimeMillis() - cachedAt < CACHE_MS) return hit
        val fresh = YtMusicRepository.library()
        cached = fresh
        cachedAt = System.currentTimeMillis()
        fresh
    }

    fun invalidate() {
        cached = null
    }

    /** Liked Music + library songs, deduplicated: Liked tracks also appear in the library feed. */
    private fun allLibrarySongs(page: LibraryPage): List<CwSong> =
        (page.likedSongs + page.librarySongs).distinctBy { it.videoId }

    private fun matchesArtist(song: CwSong, artistId: String, nameHint: String?): Boolean {
        if (song.artistId == artistId) return true
        if (song.artists.any { it.browseId == artistId }) return true
        if (nameHint != null) {
            if (song.artist.equals(nameHint, ignoreCase = true)) return true
            if (song.artists.any { it.name.equals(nameHint, ignoreCase = true) }) return true
        }
        return false
    }

    /**
     * The artist's library row: the Artists/Subscriptions shelf card when
     * the account saved the artist, else a row built from their library
     * songs (name + first cover), else null — the screens fall back to the
     * cached banner name when there is no library row.
     */
    fun artistFor(page: LibraryPage, artistId: String, nameHint: String?): Artist? {
        page.shelves
            .filter { it.title == "Artists" || it.title == "Subscriptions" }
            .flatMap { it.items }
            .firstOrNull { it.browseId == artistId }
            ?.toMochyArtist()
            ?.let { return it }
        val matching = allLibrarySongs(page).filter { matchesArtist(it, artistId, nameHint) }
        if (matching.isEmpty()) return null
        val entity = matching.firstNotNullOfOrNull { song ->
            song.artists.firstOrNull { it.browseId == artistId }
                ?.let { ArtistEntity(id = artistId, name = it.name) }
        } ?: ArtistEntity(id = artistId, name = nameHint ?: matching.first().artist)
        return Artist(
            artist = entity.copy(thumbnailUrl = matching.firstNotNullOfOrNull { it.thumbnailUrl }),
            songCount = matching.size,
        )
    }

    /**
     * The artist's library songs (Mochy's artistSongsPreview). Liked
     * Music's tracks read as liked; every row gets this session's like and
     * download state overlaid. Unsorted — the callers sort/filter.
     */
    fun songsFor(page: LibraryPage, artistId: String, nameHint: String?): List<Song> {
        val likedIds = page.likedSongs.mapTo(HashSet()) { it.videoId }
        return allLibrarySongs(page)
            .filter { matchesArtist(it, artistId, nameHint) }
            .map { song ->
                song.toMochySong()
                    .let { if (song.videoId in likedIds) it.copy(song = it.song.copy(liked = true)) else it }
                    .withLikeAndDownloadState()
            }
    }

    /**
     * The artist's library albums (Mochy's artistAlbumsPreview): albums
     * grouped out of their library songs, plus Albums-shelf cards whose
     * subtitle credits the artist (a saved album with no library songs of
     * its own still shows). Newest first, then by title.
     */
    fun albumsFor(page: LibraryPage, artistId: String, nameHint: String?): List<Album> {
        val songs = songsFor(page, artistId, nameHint)
        val fromSongs = songs
            .groupBy { it.song.albumId }
            .filterKeys { !it.isNullOrBlank() }
            .map { (albumId, albumSongs) ->
                val first = albumSongs.first()
                Album(
                    album = AlbumEntity(
                        id = albumId!!,
                        title = first.song.albumName.orEmpty(),
                        thumbnailUrl = first.song.thumbnailUrl,
                        songCount = albumSongs.size,
                        duration = albumSongs.sumOf { it.song.duration.coerceAtLeast(0) },
                    ),
                    artists = first.artists,
                )
            }
        val fromShelves = page.shelves
            .filter { it.title == "Albums" }
            .flatMap { it.items }
            .filter { item ->
                nameHint != null &&
                    InnertubeParser.artistFromSubtitle(item.subtitle).equals(nameHint, ignoreCase = true)
            }
            .mapNotNull { it.toMochyAlbum() }
        return (fromSongs + fromShelves)
            .distinctBy { it.id }
            .sortedWith(compareByDescending<Album> { it.album.year ?: 0 }.thenBy { it.album.title })
    }
}
