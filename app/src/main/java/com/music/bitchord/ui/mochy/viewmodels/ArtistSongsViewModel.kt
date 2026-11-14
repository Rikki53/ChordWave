// Ported from Mochy Music (GPL-3.0):
// viewmodels/LibraryViewModels.kt (ArtistSongsViewModel), re-implemented
// over ChordWave sources: LibraryArtistSource reads the artist's library
// songs from the signed-in account's library (Mochy read its Room
// database, sorted in SQL). The sort/filter prefs are observed the way
// Mochy observes them — a dataStore.data collector re-sorts and
// re-filters the cached songs whenever the user changes the sort control
// or a filter toggle.
//
// Adaptations vs Mochy:
// - No saved-state handle: the host calls load(artistId) with the route
//   arg (coordinator decision — the route is available from
//   navigator.currentDetail). A repeated load() with the same id is a
//   no-op; a new id resets artist/songs first, so a reused VM never
//   shows the previous artist's songs while loading.
// - CREATE_DATE keeps library order: CW's song rows carry no
//   added-to-library date, so there is nothing to sort by (descending
//   still reverses, matching Mochy's newest-first default loosely).
// - PLAY_TIME sorts on ListeningStats' all-time milliseconds per track
//   (the buckets' totals, joined by video id); a track never played
//   locally sorts as 0.
// - Depends on the C11 preference declarations (ArtistSongSortTypeKey,
//   ArtistSongSortDescendingKey, ArtistSongSortType in
//   ui.mochy.constants), which the coordinator lands — see NEEDS.md.
// (phase2-detail NEEDS.md §(a) C7.)
package com.music.bitchord.ui.mochy.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.bitchord.data.stats.ListeningStats
import com.music.bitchord.ui.mochy.constants.ArtistSongSortDescendingKey
import com.music.bitchord.ui.mochy.constants.ArtistSongSortType
import com.music.bitchord.ui.mochy.constants.ArtistSongSortTypeKey
import com.music.bitchord.ui.mochy.constants.HideExplicitKey
import com.music.bitchord.ui.mochy.constants.HideVideoSongsKey
import com.music.bitchord.ui.mochy.extensions.toEnum
import com.music.bitchord.ui.mochy.models.Artist
import com.music.bitchord.ui.mochy.models.Song
import com.music.bitchord.ui.mochy.utils.ArtistPageCache
import com.music.bitchord.ui.mochy.utils.PreferencesCache
import com.music.bitchord.ui.mochy.utils.dataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@HiltViewModel
class ArtistSongsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val _artist = MutableStateFlow<Artist?>(null)
    val artist: StateFlow<Artist?> = _artist

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs

    /** the artist's library songs before sorting and filtering */
    @Volatile
    private var rawSongs: List<Song> = emptyList()

    private var prefsJob: Job? = null

    /** The args the current page belongs to; null until load() is called. */
    @Volatile
    private var loadedKey: String? = null

    /**
     * Loads the artist's library songs. The coordinator patches the
     * ported screen to call this with the route arg.
     */
    fun load(artistId: String) {
        if (artistId == loadedKey) return
        loadedKey = artistId
        _artist.value = null
        _songs.value = emptyList()
        rawSongs = emptyList()
        prefsJob?.cancel()
        prefsJob = viewModelScope.launch {
            context.dataStore.data
                .map {
                    Triple(
                        it[ArtistSongSortTypeKey].toEnum(ArtistSongSortType.CREATE_DATE) to
                            (it[ArtistSongSortDescendingKey] ?: true),
                        it[HideExplicitKey] ?: false,
                        it[HideVideoSongsKey] ?: false,
                    )
                }
                .distinctUntilChanged()
                .collect { (sort, hideExplicit, hideVideoSongs) ->
                    val (sortType, descending) = sort
                    publish(sortType, descending, hideExplicit, hideVideoSongs)
                }
        }
        viewModelScope.launch(Dispatchers.IO) {
            val nameHint = ArtistPageCache.header(artistId)?.name
            // all-time milliseconds per track, for the PLAY_TIME sort
            val playTimes = ListeningStats.exportAll()
                .flatMap { it.tracks }
                .groupBy { it.id }
                .mapValues { (_, entries) -> entries.sumOf { it.ms } }
            val page = LibraryArtistSource.libraryPage().getOrNull() ?: return@launch
            if (artistId != loadedKey) return@launch
            _artist.value = LibraryArtistSource.artistFor(page, artistId, nameHint)
            rawSongs = LibraryArtistSource.songsFor(page, artistId, nameHint).map { song ->
                val ms = playTimes[song.id] ?: 0L
                if (ms == 0L) song else song.copy(song = song.song.copy(totalPlayTime = ms))
            }
            // the prefs collector already ran once on empty rawSongs;
            // publish again now the songs are here
            val prefs = PreferencesCache.current(context.dataStore)
            publish(
                prefs[ArtistSongSortTypeKey].toEnum(ArtistSongSortType.CREATE_DATE),
                prefs[ArtistSongSortDescendingKey] ?: true,
                prefs[HideExplicitKey] ?: false,
                prefs[HideVideoSongsKey] ?: false,
            )
        }
    }

    private fun publish(
        sortType: ArtistSongSortType,
        descending: Boolean,
        hideExplicit: Boolean,
        hideVideoSongs: Boolean,
    ) {
        val filtered = rawSongs.filterExplicit(hideExplicit).filterVideoSongs(hideVideoSongs)
        val sorted = when (sortType) {
            ArtistSongSortType.NAME -> filtered.sortedBy { it.song.title.lowercase() }
            ArtistSongSortType.PLAY_TIME -> filtered.sortedBy { it.song.totalPlayTime }
            // no added-date on CW rows: library order stands
            ArtistSongSortType.CREATE_DATE -> filtered
        }
        _songs.value = if (descending) sorted.reversed() else sorted
    }
}
