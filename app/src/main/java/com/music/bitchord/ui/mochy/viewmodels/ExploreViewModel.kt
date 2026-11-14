// Ported from Mochy Music (GPL-3.0): viewmodels/ExploreViewModel.kt
// The Explore page for the Modern Search tab, re-implemented over ChordWave
// sources: YtMusicRepository.homeSupplement("FEmusic_explore") supplies the
// new-release albums shelf (Mappers4.newReleaseAlbums) and moodAndGenres()
// supplies the mood/genre buttons (Mappers4.toMoodAndGenresItems). Mochy's
// behaviour is kept: load once in init on IO; sort new-release albums so
// the user's most-played artists come first; apply filterExplicit with
// HideExplicitKey.
//
// Adaptations vs Mochy:
// - The artist ranking comes from the all-time ListeningStats summary
//   (artists ranked by played ms), matched by browseId then by name.
//   Mochy additionally put *favourited* (bookmarked) artists before
//   merely played ones — CW has no bookmarked-artists list, so the
//   favourite tier is dropped and the sort is purely by play time (see
//   NEEDS.md). Unknown artists sort last, keeping YouTube's order
//   (sortedBy is stable).
// - A failed explore fetch keeps the previous page (Mochy reported the
//   exception and kept null); a failed moods fetch only empties the
//   moodAndGenres field, which the screen does not read.

package com.music.bitchord.ui.mochy.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.stats.ListeningStats
import com.music.bitchord.data.stats.ReplayPeriod
import com.music.bitchord.ui.mochy.constants.HideExplicitKey
import com.music.bitchord.ui.mochy.models.innertube.AlbumItem
import com.music.bitchord.ui.mochy.models.innertube.ExplorePage
import com.music.bitchord.ui.mochy.models.innertube.filterExplicit
import com.music.bitchord.ui.mochy.utils.PreferencesCache
import com.music.bitchord.ui.mochy.utils.dataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

@HiltViewModel
class ExploreViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : ViewModel() {
    val explorePage = MutableStateFlow<ExplorePage?>(null)

    private val prefs get() = PreferencesCache.current(context.dataStore)
    private val hideExplicit get() = prefs[HideExplicitKey] ?: false

    init {
        viewModelScope.launch(Dispatchers.IO) {
            load()
        }
    }

    private suspend fun load() {
        val ranks = artistRanks()
        val albums = YtMusicRepository.homeSupplement("FEmusic_explore")
            .map { shelves -> shelves.newReleaseAlbums() }
            .onFailure { Timber.w(it, "Explore: FEmusic_explore failed, keeping previous page") }
            .getOrNull() ?: return
        val moods = YtMusicRepository.moodAndGenres()
            .map { sections -> sections.toMoodAndGenresItems() }
            .onFailure { Timber.w(it, "Explore: moodAndGenres failed, page keeps empty moods") }
            .getOrDefault(emptyList())
        explorePage.value = ExplorePage(
            newReleaseAlbums = albums
                .sortedBy { album -> albumRank(album, ranks) }
                .filterExplicit(hideExplicit),
            moodAndGenres = moods,
        )
    }

    /**
     * Artists ranked by all-time played ms: browseId -> rank, plus a
     * lowercased-name key for rows whose browseId the charts never
     * recorded. Empty when the stats store fails, which leaves the albums
     * in YouTube's order.
     */
    private suspend fun artistRanks(): Map<String, Int> {
        val summary = runCatching { ListeningStats.summary(ReplayPeriod.ALL_TIME) }.getOrNull()
            ?: return emptyMap()
        val ranks = mutableMapOf<String, Int>()
        summary.artists.sortedByDescending { it.ms }.forEachIndexed { index, entry ->
            entry.browseId?.let { ranks.putIfAbsent(it, index) }
            ranks.putIfAbsent(entry.title.lowercase(), index)
        }
        return ranks
    }

    /** The best (lowest) rank among an album's artists; unknown artists sort last. */
    private fun albumRank(album: AlbumItem, ranks: Map<String, Int>): Int =
        album.artists.orEmpty().minOfOrNull { artist ->
            artist.id?.let { ranks[it] } ?: ranks[artist.name.lowercase()]
        } ?: Int.MAX_VALUE
}
