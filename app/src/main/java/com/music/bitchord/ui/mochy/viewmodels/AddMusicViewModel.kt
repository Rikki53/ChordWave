// NEW — no Mochy ViewModel. Mochy's sheet read the database directly and called
// YouTube.* directly; this adapter is the contract in phase2-playlist
// NEEDS.md C6, re-implemented over ChordWave sources. The suggestion logic
// (seeds, radio first, artist fallback, interleave, name-key dedup) is
// Mochy's AddMusicSources.kt, quoted in the contract; the announce toast
// is Mochy's announceAddedToPlaylist, minus the database.
//
// Behaviour notes:
// - playlistSongIds: the ids already in the playlist (ticked-and-dimmed
//   rows). Local-file playlists answer from the store; server playlists
//   are paged whole (playlistEntries, falling back to the first browse
//   page — see PlaylistSources).
// - librarySongs: the library feed (liked + added songs) plus downloads
//   plus every local-file playlist's songs, deduped, sorted by title.
//   The online half is fetched once and cached — the sheet re-collects on
//   every tab switch.
// - loadSuggestions: seeds = the playlist's first song + up to 2 random
//   others; for each seed, songs like it — radio first, then the artist's
//   own songs. Mochy's middle step (the watch page's "related" endpoint)
//   has no ChordWave equivalent and is skipped. An imported file's
//   YouTube copy is found by a title+artist search first. Interleaved
//   round-robin, dropped when already in the playlist (by id AND by
//   title|artist lowercase key), filtered explicit/videos, take 40.
// - searchOnlineSongs: searchPage(query, SONGS) -> SongItems, filtered
//   explicit/videos, distinctBy id. Null on failure (the sheet shows the
//   connection note).
// - addPicks: skip ids already in the playlist; append the rest in ticked
//   order; announce via TopToaster. A server playlist takes YouTube picks
//   through addToPlaylist. A local-file playlist can only take picks that
//   are themselves files (the store drops anything without a localUri) —
//   YouTube picks have nowhere to go until a YouTube-song playlist store
//   exists (NEEDS.md C2), so they are skipped with a warning, and when
//   NOTHING could be added the call throws (the sheet catches, toasts,
//   unlocks). A Mochy local id ("LP…") has neither a store nor a server
//   page and throws the same way.

package com.music.bitchord.ui.mochy.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import com.music.bitchord.R
import com.music.bitchord.data.OfflinePlaylists
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.model.SearchFilter as CwSearchFilter
import com.music.bitchord.download.Downloads
import com.music.bitchord.ui.mochy.component.TopToaster
import com.music.bitchord.ui.mochy.component.isImportedFile
import com.music.bitchord.ui.mochy.constants.HideExplicitKey
import com.music.bitchord.ui.mochy.constants.HideVideoSongsKey
import com.music.bitchord.ui.mochy.models.MediaMetadata
import com.music.bitchord.ui.mochy.models.Song
import com.music.innertube.models.SongItem
import com.music.innertube.models.filterExplicit
import com.music.innertube.models.filterVideoSongs
import com.music.bitchord.ui.mochy.models.toMochySong
import com.music.bitchord.ui.mochy.utils.PreferencesCache
import com.music.bitchord.ui.mochy.utils.dataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import timber.log.Timber

@HiltViewModel
class AddMusicViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : ViewModel() {

    // Read live on every fetch, like HomeViewModel's flags, so a Settings
    // toggle takes effect without an app restart.
    private val hideExplicit: Boolean
        get() = PreferencesCache.current(context.dataStore)[HideExplicitKey] ?: false
    private val hideVideoSongs: Boolean
        get() = PreferencesCache.current(context.dataStore)[HideVideoSongsKey] ?: false

    /** The library feed's online half, fetched once — the sheet re-collects on every tab switch. */
    private var libraryOnlineCache: List<Song>? = null

    fun playlistSongIds(playlistId: String): Flow<Set<String>> = flow {
        emit(fetchPlaylistSongs(playlistId).map { it.videoId }.toSet())
    }.flowOn(Dispatchers.IO)

    val librarySongs: Flow<List<Song>> = flow {
        emit(loadLibrarySongs())
    }.flowOn(Dispatchers.IO)

    private suspend fun loadLibrarySongs(): List<Song> {
        val online = libraryOnlineCache ?: run {
            val page = YtMusicRepository.library().getOrNull()
            if (page == null) Timber.w("AddMusic: library fetch failed (signed out?) — local songs only")
            val songs = (page?.likedSongs.orEmpty() + page?.librarySongs.orEmpty())
                .map { it.toMochySong() }
            libraryOnlineCache = songs
            songs
        }
        val downloaded = Downloads.getDownloadedSongs(context).map { it.toDownloadedMochySong() }
        val files = OfflinePlaylists.playlists.value.flatMap { it.songs }.map { it.toLocalFileMochySong() }
        return (online + downloaded + files)
            .distinctBy { it.id }
            .sortedBy { it.song.title }
    }

    /**
     * Songs like the ones already in the playlist: songs like a few of
     * them, taking turns between the lists so one song's neighbours don't
     * fill the top. Empty when the playlist is, or when YouTube can't be
     * reached.
     */
    suspend fun loadSuggestions(playlistId: String): List<SongItem> = coroutineScope {
        val songs = playlistSeedSongs(playlistId)
        if (songs.isEmpty()) return@coroutineScope emptyList()
        val haveIds = songs.mapTo(HashSet()) { it.id }
        // A song imported from the phone and its YouTube copy have different ids, so match by name too.
        val haveNames = songs.mapTo(HashSet()) { nameKey(it.song.title, it.artists.firstOrNull()?.name) }

        // The first song sets the playlist's mood; a couple of random others keep the mix from
        // leaning on one artist, and make each opening a little different.
        val seeds = listOf(songs.first()) + songs.drop(1).shuffled().take(SuggestionSeeds - 1)
        val lists = seeds.map { seed -> async { songsLike(seed) } }.awaitAll()

        interleave(lists)
            .filter { it.id !in haveIds && nameKey(it.title, it.artists.firstOrNull()?.name) !in haveNames }
            .distinctBy { it.id }
            .filterExplicit(hideExplicit)
            .filterVideoSongs(hideVideoSongs)
            .take(MaxSuggestions)
    }

    /**
     * The playlist's songs as suggestion seeds. The first browse page is
     * enough — seeds only need the first song plus two random others — so
     * an online playlist is NOT paged whole here (playlistSongIds already
     * pays that cost for the ticked state).
     */
    private suspend fun playlistSeedSongs(playlistId: String): List<Song> {
        offlinePlaylistFor(playlistId)?.let { return it.songs.map { song -> song.toMochySong() } }
        val browseId = browseIdForPlaylist(playlistId) ?: return emptyList()
        return YtMusicRepository.browseSongs(browseId).getOrNull()?.songs
            ?.map { it.toMochySong() }.orEmpty()
    }

    /**
     * Songs like [seed], in the order the player's endless queue tries
     * them. The song's radio comes first because it's the one a
     * signed-out session reliably gets: YouTube leaves out the "related"
     * list without an account. The artist's own songs are the last
     * resort. Mochy's middle step — the watch page's "related" endpoint
     * — has no ChordWave equivalent and is skipped (NEEDS.md C6).
     */
    private suspend fun songsLike(seed: Song): List<SongItem> {
        val videoId = if (isImportedFile(seed.id) || seed.song.localUri != null) {
            findOnYouTube(seed) ?: return emptyList()
        } else {
            seed.id
        }

        YtMusicRepository.radio(videoId).getOrNull()
            ?.map { it.toSongItem() }
            ?.filter { it.id != videoId }
            ?.takeIf { it.isNotEmpty() }
            ?.let { return it }

        val artistId = seed.artists.firstOrNull { it.isYouTubeArtist }?.id ?: return emptyList()
        return YtMusicRepository.artistPage(artistId).getOrNull()?.songs
            ?.map { it.toSongItem() }.orEmpty()
    }

    /** A song imported from the phone has no YouTube id: find its YouTube copy by title and artist. */
    private suspend fun findOnYouTube(song: Song): String? {
        val query = listOfNotNull(song.song.title, song.artists.firstOrNull()?.name).joinToString(" ")
        return searchOnlineSongs(query)?.firstOrNull()?.id
    }

    suspend fun searchOnlineSongs(query: String): List<SongItem>? {
        val page = YtMusicRepository.searchPage(query, CwSearchFilter.SONGS).getOrNull()
            ?: return null
        return page.rows.mapNotNull { it.toYTItem() }
            .filterIsInstance<SongItem>()
            .filterExplicit(hideExplicit)
            .filterVideoSongs(hideVideoSongs)
            .distinctBy { it.id }
    }

    /**
     * Saves any picked song the playlist doesn't have yet, in the order
     * they were ticked, and announces them. Throws on failure — the sheet
     * catches, toasts, and unlocks.
     */
    suspend fun addPicks(playlistId: String, chosen: List<MediaMetadata>) {
        val offline = offlinePlaylistFor(playlistId)
        val already = fetchPlaylistSongs(playlistId).map { it.videoId }.toSet()
        val fresh = chosen.filter { it.id !in already }
        if (fresh.isEmpty()) return

        if (offline != null) {
            // The store only takes local files (add() drops anything
            // without a localUri): a YouTube pick has nowhere to go until
            // a YouTube-song playlist store exists (NEEDS.md C2).
            val addable = fresh.filter { it.localUri != null }
            val skipped = fresh.size - addable.size
            if (skipped > 0) {
                Timber.w("AddMusic: $skipped YouTube pick(s) can't go into local playlist $playlistId (no store for them)")
            }
            if (addable.isEmpty()) {
                error("Those songs can't be added to a local playlist yet")
            }
            OfflinePlaylists.add(listOf(offline.id), addable.map { it.toLocalCwSong() })
            announceAdded(offline.title, addable, browseId = null, playlistId = playlistId)
            return
        }

        // Only server playlists can take YouTube picks; a Mochy local id
        // (LP…) has no store and no server page.
        val browseId = browseIdForPlaylist(playlistId)
            ?: error("That playlist can't take new songs yet")
        YtMusicRepository.addToPlaylist(playlistId, fresh.map { it.id })
            .onFailure { Timber.w(it, "AddMusic: addToPlaylist failed for $playlistId") }
            .getOrThrow()
        announceAdded(playlistTitle(playlistId), fresh, browseId = browseId, playlistId = playlistId)
    }

    /**
     * Mochy's announceAddedToPlaylist, minus the database: "Added <song>
     * to <playlist>" (one) or "Added N songs to <playlist>" (many), with
     * an Undo that pulls the songs back out of a local playlist. A synced
     * playlist gets no Undo — Mochy didn't offer one there either.
     */
    private fun announceAdded(
        playlistName: String,
        added: List<MediaMetadata>,
        browseId: String?,
        playlistId: String,
    ) {
        if (added.isEmpty()) return
        val first = added.first()
        TopToaster.show(
            text = if (added.size == 1) "Added ${first.title} to $playlistName" else "Added ${added.size} songs to $playlistName",
            thumbnail = first.thumbnailUrl,
            iconRes = R.drawable.playlist_add,
            actionLabel = if (browseId == null) "Undo" else null,
            onAction = if (browseId == null) {
                {
                    offlinePlaylistFor(playlistId)?.let { playlist ->
                        added.forEach { pick ->
                            OfflinePlaylists.remove(playlist.browseId, pick.toLocalCwSong())
                        }
                    }
                }
            } else {
                null
            },
        )
    }

    /** The playlist's name for the toast: the header title, or the id when the page won't say. */
    private suspend fun playlistTitle(playlistId: String): String {
        val browseId = browseIdForPlaylist(playlistId) ?: return playlistId
        return YtMusicRepository.browseSongs(browseId).getOrNull()?.header?.title ?: playlistId
    }

    companion object {
        /** How many of the playlist's songs the suggestions are drawn from. */
        private const val SuggestionSeeds = 3

        /** Past this many, suggestions drift too far from the playlist to be worth showing. */
        private const val MaxSuggestions = 40
    }
}

private fun nameKey(title: String, artist: String?): String =
    title.trim().lowercase() + "|" + artist.orEmpty().trim().lowercase()

/** First of each list, then the second of each, and so on. */
private fun <T> interleave(lists: List<List<T>>): List<T> {
    val longest = lists.maxOfOrNull { it.size } ?: 0
    val out = ArrayList<T>(lists.sumOf { it.size })
    for (i in 0 until longest) {
        for (list in lists) list.getOrNull(i)?.let(out::add)
    }
    return out
}
