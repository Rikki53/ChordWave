// ChordWaveIpodLibrary.kt — ChordWave adapter (NOT a Mochy port): the
// IpodLibrarySource the iPod player style reads, backed by ChordWave's own
// library instead of Mochy's Room database.
//
// Sources, in the order they win a song's identity (a download's local file
// beats the same song's history row):
// - Downloads (Downloads.getDownloadedSongs): the offline files, newest first
// - Imported device files (LocalImportStore.songs) + the files the offline
//   playlists reference (OfflinePlaylists) — the same union the Library's
//   Local shelf builds (LibraryHomeViewModel.localSource)
// - Listening history (ListeningStats.summary(ALL_TIME)): every song ever
//   played on this device, so the iPod's Songs/Artists/Albums pages are the
//   whole library, not just what is on disk
//
// Page shapes mirror Mochy's IpodLibraryViewModel exactly (rows, subtitles,
// empty texts, 120px row art, 400px covers, 24 slideshow covers), so the
// iPod UI reads as Mochy's did. Two deliberate differences, both from the
// sources rather than the UI:
// - Artist pages list the artist's songs, as Mochy's buildList does (the
//   bedside-ipod NEEDS.md table said "albums-then-songs"; the Mochy source
//   is songs only).
// - Album tracks never need Mochy's YouTube fetch fallback: every album here
//   is grouped from the library songs themselves, so its tracks are always
//   stored. A stale album key just shows "No songs yet".
//
// Playback still depends on the connection: IpodController plays through
// MochyPlayerConnection.playQueue, which is stubbed until the queue
// translation lands (queue-info NEEDS.md §(b) B1). The pages show real data
// now; tapping a song to play waits on that contract.
//
// Id scheme (opaque to the UI, read back by load()):
// - Playlists: offline playlists by browseId ("local:offline:<id>"),
//   downloaded collections as "dl:<collectionId>"
// - Albums: the YouTube album id when the songs have one, else
//   "tag:<lowercased album name>" for imported files (LibraryShelf's rule)
// - Artists: the artist entity id (channel browse id, or the name fallback)

package com.music.bitchord.ui.mochy.player.ipod

import android.content.Context
import android.os.SystemClock
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.media3.common.MediaItem
import com.music.bitchord.data.LikeState
import com.music.bitchord.data.OfflinePlaylists
import com.music.bitchord.data.model.LikeStatus
import com.music.bitchord.data.stats.ListeningStats
import com.music.bitchord.data.stats.ReplayPeriod
import com.music.bitchord.download.Downloads
import com.music.bitchord.ui.mochy.extensions.toMediaItem
import com.music.bitchord.ui.mochy.importer.LocalImportStore
import com.music.bitchord.ui.mochy.models.Song
import com.music.bitchord.ui.mochy.utils.resize
import com.music.bitchord.ui.mochy.viewmodels.toDownloadedMochySong
import com.music.bitchord.ui.mochy.viewmodels.toLocalFileMochySong
import com.music.bitchord.ui.mochy.viewmodels.toTopMochySong
import java.text.Collator
import java.time.LocalDateTime
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** The iPod's library, provided by the Modern host (MochyApp). */
val LocalIpodLibrarySource = staticCompositionLocalOf<IpodLibrarySource?> { null }

class ChordWaveIpodLibrary(
    context: Context,
    private val scope: CoroutineScope,
) : IpodLibrarySource {

    private val appContext = context.applicationContext

    private val cache = ConcurrentHashMap<IpodPage, IpodList>().apply {
        put(IpodPage.Main, IpodMainList)
        put(IpodPage.Music, IpodMusicList)
    }

    @Volatile
    override var cachedCovers: List<String>? = null
        private set

    @Volatile
    override var cachedCoverFlow: List<IpodAlbum>? = null
        private set

    private val snapshotMutex = Mutex()
    private var snapshot: LibrarySnapshot? = null
    private var snapshotAt = 0L

    @Volatile
    private var warmed = false

    // ---- IpodLibrarySource ----

    override fun cached(page: IpodPage): IpodList? = cache[page]

    override suspend fun load(page: IpodPage): IpodList = withContext(Dispatchers.IO) {
        ensureWarmed()
        cache[page]?.let { return@withContext it }
        buildList(page, loadSnapshot()).also { cache[page] = it }
    }

    override fun refreshAsync(page: IpodPage) {
        if (page == IpodPage.Main || page == IpodPage.Music ||
            page == IpodPage.NowPlaying || page == IpodPage.CoverFlow
        ) {
            return
        }
        scope.launch(Dispatchers.IO) {
            runCatching {
                cache[page] = buildList(page, loadSnapshot(force = true))
            }
        }
    }

    // album covers for the slideshow beside the main menu, newest first
    override suspend fun covers(): List<String> = withContext(Dispatchers.IO) {
        ensureWarmed()
        cachedCovers?.let { return@withContext it }
        loadSnapshot().albums
            .sortedByDescending { it.newest }
            .mapNotNull { it.thumbnailUrl?.resize(CoverPx, CoverPx) }
            .distinct()
            .take(MaxCovers)
            .also { cachedCovers = it }
    }

    // every album, by name, for cover flow
    override suspend fun coverFlow(): List<IpodAlbum> = withContext(Dispatchers.IO) {
        ensureWarmed()
        cachedCoverFlow?.let { return@withContext it }
        loadSnapshot().albums
            .map {
                IpodAlbum(
                    it.key,
                    it.title,
                    it.artists.orEmpty(),
                    it.thumbnailUrl?.resize(CoverPx, CoverPx),
                )
            }
            .also { cachedCoverFlow = it }
    }

    // the whole library in a random order, ready to hand to the player
    override suspend fun shuffledLibrary(): List<MediaItem> = withContext(Dispatchers.Default) {
        ensureWarmed()
        loadSnapshot().songs.shuffled().map { it.toMediaItem() }
    }

    // ---- warming ----

    // Warms the menus and playlists off the main thread the first time the
    // iPod is used — Mochy warmed in its ViewModel's init, which ran when
    // the style opened; this runs on first access instead, so nothing is
    // read at app start for a style that may never open.
    private fun ensureWarmed() {
        if (warmed) return
        warmed = true
        scope.launch(Dispatchers.IO) {
            runCatching { covers() }
            runCatching { coverFlow() }
            val playlists = runCatching { load(IpodPage.Playlists) }.getOrNull()
            runCatching { load(IpodPage.Artists) }
            runCatching { load(IpodPage.Albums) }
            runCatching { load(IpodPage.LikedSongs) }
            runCatching { load(IpodPage.Songs) }
            playlists?.rows?.forEach { row ->
                val open = row.pick as? IpodPick.Open ?: return@forEach
                runCatching { load(open.page) }
            }
        }
    }

    // ---- the library snapshot ----

    private suspend fun loadSnapshot(force: Boolean = false): LibrarySnapshot =
        snapshotMutex.withLock {
            val now = SystemClock.uptimeMillis()
            val current = snapshot
            if (!force && current != null && now - snapshotAt < SnapshotTtlMs) return current
            buildSnapshot().also {
                snapshot = it
                snapshotAt = SystemClock.uptimeMillis()
            }
        }

    private suspend fun buildSnapshot(): LibrarySnapshot = withContext(Dispatchers.IO) {
        val downloads = runCatching { Downloads.getDownloadedSongs(appContext) }
            .getOrNull().orEmpty()
            .map { it.toDownloadedMochySong() }
        val imported = LocalImportStore.songs.value
        val playlistFiles = OfflinePlaylists.playlists.value
            .flatMap { it.songs }
            .distinctBy { it.localUri }
            .map { it.toLocalFileMochySong() }
        val history = runCatching { ListeningStats.summary(ReplayPeriod.ALL_TIME) }
            .getOrNull()?.songs.orEmpty()
            .map { it.toTopMochySong() }
        val likedUris = OfflinePlaylists.liked.value
        val overrides = LikeState.overrides.value
        // Downloads first: a downloaded song keeps its file (localUri,
        // download date) over the same song's history row.
        val union = (downloads + imported + playlistFiles + history)
            .distinctBy { it.id }
            .map { song ->
                val localUri = song.song.localUri
                val liked = overrides[song.id] == LikeStatus.LIKE ||
                    (localUri != null && localUri in likedUris)
                if (liked != song.song.liked) {
                    song.copy(song = song.song.copy(liked = liked))
                } else {
                    song
                }
            }
            .sortedWith(compareBy(CollatorInstance) { it.title })
        LibrarySnapshot(
            songs = union,
            liked = union.filter { it.song.liked },
            albums = groupAlbums(union),
            artists = groupArtists(union),
            playlists = playlistRefs(),
        )
    }

    // the albums the songs belong to, built from the songs themselves rather
    // than an album table: a downloaded or cached song that was never added
    // to the library has no album in the Library's list, and an imported
    // file only has its album as a tag (LibraryShelf's rule).
    private fun groupAlbums(songs: List<Song>): List<ShelfAlbum> =
        songs
            .groupBy { song ->
                song.album?.id
                    ?: song.song.albumId
                    ?: song.song.albumName?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
                        ?.let { "tag:$it" }
            }
            .mapNotNull { (key, group) ->
                if (key == null) return@mapNotNull null
                val first = group.first()
                val title = first.album?.title?.takeIf { it.isNotBlank() }
                    ?: first.song.albumName?.trim()?.takeIf { it.isNotEmpty() }
                    ?: return@mapNotNull null
                ShelfAlbum(
                    key = key,
                    albumId = first.album?.id ?: first.song.albumId,
                    title = title,
                    artists = group.flatMap { it.artists }.map { it.name }
                        .distinct().joinToString().ifBlank { null },
                    thumbnailUrl = first.album?.thumbnailUrl
                        ?: group.firstNotNullOfOrNull { it.song.thumbnailUrl },
                    songs = group,
                    newest = group.mapNotNull { it.song.dateDownload ?: it.song.date }.maxOrNull(),
                )
            }
            .sortedWith(compareBy(CollatorInstance) { it.title })

    private fun groupArtists(songs: List<Song>): List<ShelfArtist> =
        songs
            .flatMap { song -> song.artists.map { artist -> artist to song } }
            .filter { (artist, _) -> artist.name.isNotBlank() }
            .groupBy({ it.first.id }, { it.second })
            .map { (id, artistSongs) ->
                val first = artistSongs.first()
                ShelfArtist(
                    id = id,
                    name = first.artists.firstOrNull { it.id == id }?.name ?: id,
                    thumbnailUrl = artistSongs.firstNotNullOfOrNull { it.song.thumbnailUrl },
                    songCount = artistSongs.size,
                )
            }
            .sortedWith(compareBy(CollatorInstance) { it.name })

    private fun playlistRefs(): List<IpodPlaylistRef> {
        val offline = OfflinePlaylists.playlists.value.map { playlist ->
            IpodPlaylistRef(
                id = playlist.browseId,
                title = playlist.title,
                songCount = playlist.songs.size,
                art = playlist.songs.firstNotNullOfOrNull { it.thumbnailUrl },
            )
        }
        val collections = runCatching { Downloads.savedReleases() }
            .getOrNull().orEmpty()
            .map { collection ->
                IpodPlaylistRef(
                    id = DownloadCollectionPrefix + collection.id,
                    title = collection.title,
                    songCount = collection.videoIds.size,
                    art = collection.thumbnailUrl,
                )
            }
        return offline + collections
    }

    // ---- pages ----

    private suspend fun buildList(page: IpodPage, snap: LibrarySnapshot): IpodList =
        when (page) {
            IpodPage.Main -> IpodMainList
            IpodPage.Music -> IpodMusicList
            IpodPage.NowPlaying, IpodPage.CoverFlow -> IpodList.Loading
            IpodPage.Playlists -> withArt(
                snap.playlists.map {
                    IpodRow(
                        key = it.id,
                        title = it.title,
                        pick = IpodPick.Open(IpodPage.Playlist(it.id, it.title)),
                        subtitle = songCount(it.songCount),
                        art = it.art?.resize(RowPx, RowPx),
                    )
                },
                "No playlists yet",
            )
            IpodPage.Artists -> withArt(
                snap.artists.map {
                    IpodRow(
                        key = it.id,
                        title = it.name,
                        pick = IpodPick.Open(IpodPage.Artist(it.id, it.name)),
                        subtitle = songCount(it.songCount),
                        art = it.thumbnailUrl?.resize(RowPx, RowPx),
                        roundArt = true,
                    )
                },
                "No artists yet",
            )
            IpodPage.Albums -> withArt(
                snap.albums.map {
                    IpodRow(
                        key = it.key,
                        title = it.title,
                        pick = IpodPick.Open(IpodPage.Album(it.key, it.title)),
                        subtitle = it.artists,
                        art = it.thumbnailUrl?.resize(RowPx, RowPx),
                    )
                },
                "No albums yet",
            )
            IpodPage.Songs -> songs(snap.songs, art = true)
            IpodPage.LikedSongs -> songs(snap.liked, art = true)
            is IpodPage.Playlist -> songs(playlistSongs(page.id), art = true)
            is IpodPage.Artist -> songs(snap.songsForArtist(page.id), art = true)
            // an album's own tracks all share its cover, so they're a plain
            // track list like on the classic
            is IpodPage.Album -> albumTracks(snap, page.id)
        }

    private suspend fun playlistSongs(id: String): List<Song> = withContext(Dispatchers.IO) {
        if (id.startsWith(DownloadCollectionPrefix)) {
            val collectionId = id.removePrefix(DownloadCollectionPrefix)
            runCatching { Downloads.getCollectionSongs(appContext, collectionId) }
                .getOrNull().orEmpty()
                .map { it.toDownloadedMochySong() }
        } else {
            OfflinePlaylists.get(id)?.songs.orEmpty().map { it.toLocalFileMochySong() }
        }
    }

    private fun albumTracks(snap: LibrarySnapshot, key: String): IpodList {
        val album = snap.album(key) ?: return songs(emptyList(), art = false)
        return songs(album.songs, art = false)
    }

    private fun withArt(rows: List<IpodRow>, emptyText: String) =
        if (rows.isEmpty()) IpodList(listOf(IpodRow("empty", emptyText, null))) else IpodList(rows, art = true)

    private fun songs(songs: List<Song>, art: Boolean): IpodList {
        if (songs.isEmpty()) return IpodList(listOf(IpodRow("empty", "No songs yet", null)))
        val rows = songs.mapIndexed { index, song ->
            IpodRow(
                key = "${song.id}#$index",
                title = song.song.title,
                pick = IpodPick.Play(index),
                subtitle = if (art) song.artists.joinToString { it.name }.ifBlank { null } else null,
                art = if (art) song.song.thumbnailUrl?.resize(RowPx, RowPx) else null,
            )
        }
        return IpodList(rows, songs, art = art)
    }

    private fun songCount(count: Int) = if (count == 1) "1 song" else "$count songs"

    private class LibrarySnapshot(
        val songs: List<Song>,
        val liked: List<Song>,
        val albums: List<ShelfAlbum>,
        val artists: List<ShelfArtist>,
        val playlists: List<IpodPlaylistRef>,
    ) {
        fun songsForArtist(artistId: String): List<Song> =
            songs.filter { song -> song.artists.any { it.id == artistId } }

        fun album(key: String): ShelfAlbum? = albums.firstOrNull { it.key == key }
    }

    private class ShelfAlbum(
        val key: String,
        val albumId: String?,
        val title: String,
        val artists: String?,
        val thumbnailUrl: String?,
        val songs: List<Song>,
        val newest: LocalDateTime?,
    )

    private class ShelfArtist(
        val id: String,
        val name: String,
        val thumbnailUrl: String?,
        val songCount: Int,
    )

    private class IpodPlaylistRef(
        val id: String,
        val title: String,
        val songCount: Int,
        val art: String?,
    )

    private companion object {
        const val CoverPx = 400
        const val RowPx = 120
        const val MaxCovers = 24

        /** "dl:" marks a downloaded collection's id, apart from offline playlist browseIds. */
        const val DownloadCollectionPrefix = "dl:"

        /** How long a built snapshot is reused before a load rebuilds it (refreshAsync forces). */
        const val SnapshotTtlMs = 60_000L

        // Mochy's NAME sort: locale-aware, accents ignored.
        val CollatorInstance: Collator =
            Collator.getInstance(Locale.getDefault()).apply { strength = Collator.PRIMARY }
    }
}
