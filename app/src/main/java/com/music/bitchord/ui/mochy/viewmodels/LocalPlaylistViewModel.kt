// Ported from Mochy Music (GPL-3.0): viewmodels/LocalPlaylistViewModel.kt
// Mochy's flow is kept: playlistSongs combines the stored songs with the
// sort prefs (PlaylistSongSortTypeKey / PlaylistSongSortDescendingKey /
// HideVideoSongsKey) — CUSTOM keeps stored order, CREATE_DATE sorts by map
// id, NAME/ARTIST sort with a Collator (PRIMARY strength), PLAY_TIME sorts
// by totalPlayTime, everything except CUSTOM reverses when descending.
//
// Adaptations vs Mochy — read the gap note first:
// ChordWave has NO local-playlist store for YouTube songs. OfflinePlaylists
// only holds local-file songs (everything is keyed by localUri; add()
// drops any song without one), and YtMusicRepository only covers online
// (browseId) playlists. So this adapter serves two kinds of playlist and
// is honest about what each can do (see NEEDS.md C2):
// - A local-file playlist (OfflinePlaylists: "liked" or "p<base36>"): the
//   store is the source of truth, watched live so adds from the Add-music
//   sheet land without a reload. Delete / remove / restore run on the
//   store; moveSong CANNOT persist (the store has no reorder API) and
//   logs instead; syncFromYouTube is a no-op; toggleSaved flips in memory
//   only (no store field holds it).
// - A server playlist (any other id that browses): the server page IS the
//   playlist — read via browseSongs + proactive paging, written via the
//   playlist-edit endpoints (reorderPlaylist / removeFromPlaylist /
//   addToPlaylist / deletePlaylist / setSaved). The local half Mochy kept
//   (entity + maps + songs in its database) does not exist here.
// - A Mochy local id ("LP" + 8 letters) names neither: the screen shows
//   an empty playlist until a YouTube-song store exists.
// - No saved-state handle: the route arg comes from load(playlistId), which
//   the coordinator calls from the screen. `playlistId` is therefore a
//   `var ... by mutableStateOf("")` set by load(), not Mochy's val. The VM
//   fetches nothing before the first load() call.
// - The eight REQUIRED ADDITIONS (relatedPlaylists, deletePlaylist,
//   moveSong, removeSong, restoreSong, toggleSaved, syncFromYouTube,
//   recordPlay, removeDownloads) are the screen's former database/YouTube
//   calls, routed here per the contract.
// - Mochy's init also synced a browsed playlist once and re-normalised
//   stored positions to 0..n-1; the sync half is syncFromYouTube() now
//   (menu-driven, not automatic — an automatic sync on every open would
//   re-fetch a playlist the user may be offline for), and positions are
//   re-numbered in memory after every mutation.
// - The cover-refresh half (PlaylistCovers.refreshIfStale) is dropped:
//   covers here come from the store / the server header, never generated.
// - recordPlay() is a no-op: ChordWave has no playlist-event store.

package com.music.bitchord.ui.mochy.viewmodels

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.bitchord.data.OfflinePlaylists
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.download.Downloads
import com.music.bitchord.ui.mochy.constants.HideVideoSongsKey
import com.music.bitchord.ui.mochy.constants.PlaylistSongSortDescendingKey
import com.music.bitchord.ui.mochy.constants.PlaylistSongSortType
import com.music.bitchord.ui.mochy.constants.PlaylistSongSortTypeKey
import com.music.bitchord.ui.mochy.extensions.toEnum
import com.music.bitchord.ui.mochy.models.Playlist
import com.music.bitchord.ui.mochy.models.PlaylistEntity
import com.music.bitchord.ui.mochy.models.PlaylistSong
import com.music.bitchord.ui.mochy.models.PlaylistSongMap
import com.music.bitchord.ui.mochy.models.toMochyPlaylist
import com.music.bitchord.ui.mochy.utils.dataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.Collator
import java.time.LocalDateTime
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber

@HiltViewModel
class LocalPlaylistViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : ViewModel() {
    // B1 calls for a String val; a var by mutableStateOf reads as a String
    // and lets load() update it when the host hands over a new playlist.
    var playlistId by mutableStateOf("")
        private set

    private val _playlist = MutableStateFlow<Playlist?>(null)
    val playlist: StateFlow<Playlist?> = _playlist.asStateFlow()

    /** Songs in stored order, before the sort prefs (Mochy's combine input). */
    private val _rawSongs = MutableStateFlow<List<PlaylistSong>>(emptyList())

    private val _relatedPlaylists = MutableStateFlow<List<Playlist>>(emptyList())
    val relatedPlaylists: StateFlow<List<Playlist>> = _relatedPlaylists.asStateFlow()

    /** The server page's continuation while an online playlist is still paging in; null when fully loaded or local. */
    private var onlineContinuation: String? = null

    /** Whether the open playlist is a local-file one (OfflinePlaylists) rather than a server page. */
    private var isOfflinePlaylist = false

    /** The server id the playlist-edit endpoints take (raw id, no VL). Null for local-file playlists. */
    private var serverPlaylistId: String? = null

    /** The id setSaved must be given: the one the page itself named. */
    private var libraryPlaylistId: String? = null

    /** The last entry removeSong dropped, so restoreSong can put it back — the map alone carries no title, art, or (for local files) uri. */
    private var lastRemoved: PlaylistSong? = null

    /** The id the current page belongs to; null until load() is called. */
    private var loadedId: String? = null
    private var loadJob: Job? = null
    private var pagingJob: Job? = null
    private var offlineWatchJob: Job? = null
    private var nextMapId = 0

    val playlistSongs: StateFlow<List<PlaylistSong>> =
        combine(
            _rawSongs,
            context.dataStore.data
                .map {
                    Triple(
                        it[PlaylistSongSortTypeKey].toEnum(PlaylistSongSortType.CUSTOM),
                        it[PlaylistSongSortDescendingKey] ?: true,
                        it[HideVideoSongsKey] ?: false,
                    )
                }.distinctUntilChanged(),
        ) { songs, (sortType, sortDescending, hideVideoSongs) ->
            val filteredSongs = if (hideVideoSongs) {
                songs.filter { !it.song.song.isVideo }
            } else {
                songs
            }
            when (sortType) {
                PlaylistSongSortType.CUSTOM -> filteredSongs
                PlaylistSongSortType.CREATE_DATE -> filteredSongs.sortedBy { it.map.id }
                PlaylistSongSortType.NAME -> {
                    val collator = Collator.getInstance(Locale.getDefault())
                    collator.strength = Collator.PRIMARY
                    filteredSongs.sortedWith(compareBy(collator) { it.song.song.title })
                }
                PlaylistSongSortType.ARTIST -> {
                    val collator = Collator.getInstance(Locale.getDefault())
                    collator.strength = Collator.PRIMARY
                    filteredSongs
                        .sortedWith(compareBy(collator) { song -> song.song.artists.joinToString("") { it.name } })
                        .groupBy { it.song.album?.title }
                        .flatMap { (_, songsByAlbum) ->
                            songsByAlbum.sortedBy {
                                it.song.artists.joinToString(
                                    "",
                                ) { it.name }
                            }
                        }
                }

                PlaylistSongSortType.PLAY_TIME -> filteredSongs.sortedBy { it.song.song.totalPlayTime }
            }.reversedIf(sortDescending && sortType != PlaylistSongSortType.CUSTOM)
        }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    /**
     * (Re)initialises the VM for a route playlist id. The coordinator
     * patches the ported screen to call this; the VM fetches nothing
     * before the first call.
     */
    fun load(playlistId: String) {
        val id = playlistId.trim()
        if (id.isEmpty()) return
        if (id == loadedId) return
        loadedId = id
        this.playlistId = id
        loadJob?.cancel()
        pagingJob?.cancel()
        offlineWatchJob?.cancel()
        _playlist.value = null
        _rawSongs.value = emptyList()
        _relatedPlaylists.value = emptyList()
        onlineContinuation = null
        isOfflinePlaylist = false
        serverPlaylistId = null
        libraryPlaylistId = null
        lastRemoved = null
        nextMapId = 0

        // A local-file playlist: the store is the source of truth, watched
        // live so adds from the Add-music sheet land without a reload.
        if (offlinePlaylistFor(id) != null) {
            isOfflinePlaylist = true
            offlineWatchJob = viewModelScope.launch {
                OfflinePlaylists.playlists.collect { playlists ->
                    val current = playlists.firstOrNull { it.id == id }
                    if (current == null) {
                        // Deleted elsewhere (the edit sheet, another tab).
                        _playlist.value = null
                        _rawSongs.value = emptyList()
                    } else {
                        _playlist.value = current.toMochyPlaylist()
                        _rawSongs.value = current.songs.mapIndexed { index, song ->
                            song.toPlaylistSong(id, index)
                        }.also { nextMapId = it.size }
                    }
                }
            }
            loadRelated(id)
            return
        }

        // Otherwise a server playlist, read off YouTube like the online
        // screen does — the local half of a synced playlist has no store
        // yet (NEEDS.md C2), so the server page IS the playlist here.
        loadJob = viewModelScope.launch(Dispatchers.IO) {
            val browseId = browseIdForPlaylist(id)
            if (browseId == null) {
                Timber.w("LocalPlaylist: $id names no local or server playlist")
                return@launch
            }
            serverPlaylistId = id
            YtMusicRepository.browseSongs(browseId)
                .onSuccess { page ->
                    if (id != loadedId) return@onSuccess
                    libraryPlaylistId = page.library?.playlistId
                    _playlist.value = Playlist(
                        playlist = PlaylistEntity(
                            id = id,
                            name = page.header?.title ?: id,
                            browseId = browseId,
                            bookmarkedAt = if (page.library?.saved == true) LocalDateTime.now() else null,
                            remoteSongCount = page.songs.size,
                            isEditable = page.owned ?: false,
                            thumbnailUrl = page.header?.thumbnailUrl,
                        ),
                        songCount = page.songs.size,
                        songThumbnails = page.songs.take(4).map { it.thumbnailUrl },
                    )
                    _rawSongs.value = page.songs.mapIndexed { index, song -> song.toPlaylistSong(id, index) }
                    nextMapId = page.songs.size
                    onlineContinuation = page.continuation
                    loadRelated(id)
                    // Page the rest in behind the first screenful, like the
                    // online screen's proactive loop.
                    pagingJob = viewModelScope.launch(Dispatchers.IO) {
                        var token = page.continuation
                        while (token != null && id == loadedId) {
                            val next = YtMusicRepository.moreSongs(token).getOrNull() ?: break
                            if (id != loadedId) break
                            val current = _rawSongs.value.toMutableList()
                            val known = current.mapNotNull { it.map.setVideoId }.toHashSet()
                            next.songs
                                .filter { it.setVideoId == null || known.add(it.setVideoId) }
                                .forEach { song -> current.add(song.toPlaylistSong(id, nextMapId++)) }
                            _rawSongs.value = current
                            refreshCounts()
                            token = next.continuation
                            onlineContinuation = token
                        }
                    }
                }
                .onFailure { Timber.w(it, "LocalPlaylist: failed to load $id") }
        }
    }

    /**
     * REQUIRED ADDITION — Mochy's related row: every playlist except this
     * one. Local-file playlists from the store plus the account's own
     * YouTube playlists; the screen still does the shuffled().take(5).
     */
    private fun loadRelated(currentId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val online = YtMusicRepository.userPlaylists().getOrNull().orEmpty()
                .map { it.toMochyPlaylist() }
            if (currentId != loadedId) return@launch
            val local = OfflinePlaylists.playlists.value.map { it.toMochyPlaylist() }
            _relatedPlaylists.value = (local + online)
                .filter { it.id != currentId }
                .distinctBy { it.id }
        }
    }

    /**
     * REQUIRED ADDITION. Mochy deleted the local rows and the server
     * playlist; here the store decides which half exists. A saved playlist
     * the account doesn't own is taken out of the library instead of
     * deleting someone else's list.
     */
    fun deletePlaylist() {
        val id = loadedId ?: return
        viewModelScope.launch(Dispatchers.IO) {
            if (isOfflinePlaylist) {
                OfflinePlaylists.delete(OfflinePlaylists.PREFIX + id)
                return@launch
            }
            val serverId = serverPlaylistId ?: return@launch
            if (id == "LM") {
                Timber.w("LocalPlaylist: Liked Music can't be deleted")
                return@launch
            }
            val editable = _playlist.value?.playlist?.isEditable == true
            val result = if (editable) {
                YtMusicRepository.deletePlaylist(serverId)
            } else {
                YtMusicRepository.setSaved(libraryPlaylistId ?: serverId, false)
            }
            result.onFailure { Timber.w(it, "LocalPlaylist: delete failed for $id") }
        }
    }

    /**
     * REQUIRED ADDITION. Drag-reorder commit. Online playlists reorder
     * through the server (the adapter computes the move itself from the
     * setVideoId lists, rather than Mochy's successor-based
     * moveSongPlaylist); local-file playlists CANNOT persist a drag —
     * OfflinePlaylists has no reorder API — so the rows move on screen
     * (the screen owns the drag state) and snap back on the next load.
     */
    fun moveSong(from: Int, to: Int) {
        if (from == to) return
        val songs = _rawSongs.value
        if (from !in songs.indices || to !in songs.indices) return
        val id = loadedId ?: return
        if (isOfflinePlaylist) {
            Timber.w("LocalPlaylist: moveSong is not persisted for local-file playlists (no store API)")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            // A reorder is sent as moves relative to neighbours, so it
            // needs the whole list — if the page is still filling in,
            // fetch it whole first (playlistEntries), exactly like the
            // Classic flow.
            pagingJob?.cancel()
            val full = if (onlineContinuation != null) {
                val browseId = browseIdForPlaylist(id) ?: return@launch
                YtMusicRepository.playlistEntries(browseId).getOrNull()?.mapIndexed { index, song ->
                    song.toPlaylistSong(id, index)
                } ?: return@launch
            } else {
                songs
            }
            val current = full.mapNotNull { it.map.setVideoId }
            if (current.size != full.size) {
                Timber.w("LocalPlaylist: moveSong needs a setVideoId on every entry")
                return@launch
            }
            val moved = full.toMutableList().apply { add(to, removeAt(from)) }
            val target = moved.map { it.map.setVideoId!! }
            YtMusicRepository.reorderPlaylist(serverPlaylistId ?: id, current, target)
                .onSuccess {
                    if (id != loadedId) return@onSuccess
                    onlineContinuation = null
                    _rawSongs.value = moved.renumbered()
                }
                .onFailure { Timber.w(it, "LocalPlaylist: moveSong failed for $id") }
        }
    }

    /**
     * REQUIRED ADDITION. Swipe-remove / delete. Mochy moved the entry to
     * Int.MAX_VALUE, deleted it, and told YouTube when synced; the
     * MAX_VALUE dance was a Room position trick with no meaning here, so
     * the entry is just dropped (and the server told, when synced).
     */
    fun removeSong(map: PlaylistSongMap) {
        val id = loadedId ?: return
        val entry = _rawSongs.value.firstOrNull { it.map.id == map.id } ?: return
        lastRemoved = entry
        if (isOfflinePlaylist) {
            OfflinePlaylists.remove(OfflinePlaylists.PREFIX + id, entry.song.toLocalCwSong())
            return // the store watcher republishes _rawSongs
        }
        val setVideoId = map.setVideoId
        if (setVideoId == null) {
            Timber.w("LocalPlaylist: removeSong needs a setVideoId (entry ${map.songId})")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            YtMusicRepository.removeFromPlaylist(serverPlaylistId ?: id, listOf(setVideoId to map.songId))
                .onSuccess {
                    if (id != loadedId) return@onSuccess
                    _rawSongs.value = _rawSongs.value.filterNot { it.map.id == map.id }.renumbered()
                    refreshCounts()
                }
                .onFailure { Timber.w(it, "LocalPlaylist: removeSong failed for $id") }
        }
    }

    /**
     * REQUIRED ADDITION. Undo for a swipe-remove. Mochy re-inserted the
     * map at Int.MAX_VALUE, moved it back to its position, and told
     * YouTube when synced; here the song goes back on the server first
     * (addToPlaylist appends it) and is then reordered to its old spot.
     * A local-file restore appends instead — the store has no positional
     * insert, so the original position is lost.
     */
    fun restoreSong(map: PlaylistSongMap) {
        val id = loadedId ?: return
        val removed = lastRemoved?.takeIf { it.map.id == map.id }
        if (isOfflinePlaylist) {
            val song = removed?.song ?: run {
                Timber.w("LocalPlaylist: restoreSong has no cached song for entry ${map.id}")
                return
            }
            OfflinePlaylists.add(listOf(id), listOf(song.toLocalCwSong()))
            lastRemoved = null
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val serverId = serverPlaylistId ?: id
            val added = YtMusicRepository.addToPlaylist(serverId, listOf(map.songId))
                .onFailure { Timber.w(it, "LocalPlaylist: restoreSong failed for $id") }
                .getOrNull() ?: return@launch
            if (id != loadedId) return@launch
            val newSetVideoId = added[map.songId]
            val restored = removed?.let { old ->
                PlaylistSong(
                    map = PlaylistSongMap(
                        id = nextMapId++,
                        playlistId = id,
                        songId = map.songId,
                        position = map.position,
                        setVideoId = newSetVideoId ?: map.setVideoId,
                    ),
                    song = old.song,
                )
            }
            if (restored == null) {
                // No cached copy (the VM reloaded since the remove): the
                // song is back on the server, so re-read the list.
                syncFromYouTube()
                return@launch
            }
            val before = _rawSongs.value
            val at = map.position.coerceIn(0, before.size)
            val after = before.toMutableList().apply { add(at, restored) }.renumbered()
            _rawSongs.value = after
            refreshCounts()
            if (newSetVideoId != null) {
                // The server's order right now is the old list plus the
                // new entry at the end; move it to the restored spot.
                val serverNow = before.mapNotNull { it.map.setVideoId } + newSetVideoId
                val target = after.mapNotNull { it.map.setVideoId }
                if (serverNow.size == before.size + 1 && target.size == after.size) {
                    YtMusicRepository.reorderPlaylist(serverId, serverNow, target)
                        .onFailure { Timber.w(it, "LocalPlaylist: restoreSong reorder failed for $id (song is back, at the end)") }
                }
            }
            lastRemoved = null
        }
    }

    /**
     * REQUIRED ADDITION. Header save button on a saved remote playlist.
     * Local-file playlists flip in memory only — no store field holds the
     * flag, so it resets on the next load.
     */
    fun toggleSaved() {
        val id = loadedId ?: return
        val current = _playlist.value ?: return
        if (isOfflinePlaylist) {
            _playlist.value = current.copy(playlist = current.playlist.localToggleLike())
            return
        }
        val saved = current.playlist.bookmarkedAt != null
        viewModelScope.launch(Dispatchers.IO) {
            YtMusicRepository.setSaved(libraryPlaylistId ?: serverPlaylistId ?: id, !saved)
                .onSuccess {
                    if (id != loadedId) return@onSuccess
                    _playlist.value = _playlist.value?.let { pl ->
                        pl.copy(playlist = pl.playlist.localToggleLike())
                    }
                }
                .onFailure { Timber.w(it, "LocalPlaylist: toggleSaved failed for $id") }
        }
    }

    /**
     * REQUIRED ADDITION. Menu "Sync": re-read the server page whole and
     * replace the listing. A no-op for local-file playlists (nothing to
     * sync from). The header is kept — only the songs are re-read.
     */
    fun syncFromYouTube() {
        val id = loadedId ?: return
        if (isOfflinePlaylist) {
            Timber.w("LocalPlaylist: syncFromYouTube is a no-op for local-file playlists")
            return
        }
        pagingJob?.cancel()
        loadJob?.cancel()
        loadJob = viewModelScope.launch(Dispatchers.IO) {
            val browseId = browseIdForPlaylist(id) ?: return@launch
            val entries = YtMusicRepository.playlistEntries(browseId).getOrNull()
                ?: YtMusicRepository.browseSongs(browseId).getOrNull()?.songs
                ?: run {
                    Timber.w("LocalPlaylist: syncFromYouTube failed for $id")
                    return@launch
                }
            if (id != loadedId) return@launch
            onlineContinuation = null
            _rawSongs.value = entries.mapIndexed { index, song -> song.toPlaylistSong(id, index) }
            nextMapId = entries.size
            refreshCounts()
        }
    }

    /**
     * REQUIRED ADDITION. Mochy inserted a PlaylistEvent row on every play
     * (the "recently played playlists" signal); ChordWave has no
     * playlist-event store, so this is a no-op until one exists.
     */
    fun recordPlay() {
        // No-op: no playlist-event store in ChordWave (NEEDS.md C2).
    }

    /**
     * REQUIRED ADDITION. Remove-downloads dialog: every song's download
     * goes. A non-editable playlist also loses its songs — for a
     * local-file list that means dropping them from the store; a saved
     * remote playlist has no local copy to clear, and the server's songs
     * are someone else's list, so they stay put.
     */
    fun removeDownloads() {
        val songs = _rawSongs.value
        if (songs.isEmpty()) return
        val id = loadedId ?: return
        viewModelScope.launch(Dispatchers.IO) {
            songs.forEach { entry ->
                Downloads.cancel(entry.song.id)
                Downloads.delete(context, entry.song.id)
            }
            if (_playlist.value?.playlist?.isEditable == false && isOfflinePlaylist) {
                songs.forEach { entry ->
                    OfflinePlaylists.remove(OfflinePlaylists.PREFIX + id, entry.song.toLocalCwSong())
                }
            }
        }
    }

    /** Keeps the header counts in step with the listing after a mutation. */
    private fun refreshCounts() {
        val size = _rawSongs.value.size
        _playlist.value = _playlist.value?.let { pl ->
            pl.copy(
                songCount = size,
                playlist = pl.playlist.copy(
                    remoteSongCount = if (isOfflinePlaylist) pl.playlist.remoteSongCount else size,
                ),
            )
        }
    }

    /** Positions 0..n-1 after a mutation; map ids stay put (the screen keys rows on them). */
    private fun List<PlaylistSong>.renumbered(): List<PlaylistSong> =
        mapIndexed { index, item -> item.copy(map = item.map.copy(position = index)) }
}
