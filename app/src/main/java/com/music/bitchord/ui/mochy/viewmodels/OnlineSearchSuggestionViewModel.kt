// Ported from Mochy Music (GPL-3.0): viewmodels/OnlineSearchSuggestionViewModel.kt
// Search suggestions + history for the Modern Search tab, re-implemented over
// ChordWave sources: history lives in CW's SearchHistory store (query terms
// filed as SearchHistoryEntity rows with id "q:<term>"), suggestions come
// from YtMusicRepository.searchSuggestions, top results from
// searchTypeahead, and a pasted YouTube link resolves through trackLinks /
// artistPage. Mochy's init logic is kept: empty query -> full history,
// offline -> local library preview, online -> suggestions + parsed-URL item,
// history filtered out of suggestions, suggestions .distinct() (a repeated
// suggestion gave two rows the same key and crashed the app).
//
// Adaptations vs Mochy:
// - No MusicDatabase: history is CW's SearchHistory store, observed through
//   its `recent` StateFlow and combined with the query (Mochy read the DB
//   inside flatMapLatest; the combine re-runs the same computation whenever
//   the stored list changes, e.g. after addHistory/deleteHistory).
// - Mochy's searchSuggestions returned text queries AND recommended items;
//   CW's returns text only, so the top-result items come from searchTypeahead
//   (first 6, distinct by id).
// - No debounce: Mochy's init has none either — flatMapLatest cancels the
//   previous query's work on each keystroke.
// - addHistory/deleteHistory are REQUIRED ADDITIONS (not in Mochy's VM):
//   the ported SearchScreen never touches the store itself.
// - History ids are index-based (0-based position, most recent first): CW's
//   store has no stable numeric ids. The screen keys rows by query, so the
//   id is never used for identity — see NEEDS.md.

package com.music.bitchord.ui.mochy.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.model.EntityType
import com.music.bitchord.data.model.SearchHistoryEntity
import com.music.bitchord.data.settings.SearchHistory as CwSearchHistory
import com.music.bitchord.data.stats.ListeningStats
import com.music.bitchord.data.stats.ReplayPeriod
import com.music.bitchord.ui.mochy.constants.HideExplicitKey
import com.music.bitchord.ui.mochy.constants.HideVideoSongsKey
import com.music.bitchord.ui.mochy.constants.OfflineModeKey
import com.music.bitchord.ui.mochy.models.SearchHistory
import com.music.bitchord.ui.mochy.models.innertube.ArtistItem
import com.music.bitchord.ui.mochy.models.innertube.YTItem
import com.music.bitchord.ui.mochy.models.innertube.filterExplicit
import com.music.bitchord.ui.mochy.models.innertube.filterVideoSongs
import com.music.bitchord.ui.mochy.utils.PreferencesCache
import com.music.bitchord.ui.mochy.utils.YouTubeUrlParser
import com.music.bitchord.ui.mochy.utils.dataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import timber.log.Timber

/** CW files a raw search term as a TRACK entity with this id prefix (see SearchHistoryEntity.toSong). */
private const val QUERY_ID_PREFIX = "q:"

/** How many top-result cards the typeahead contributes (Mochy's recommendedItems had no CW cap to copy). */
private const val TopResultLimit = 6

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class OnlineSearchSuggestionViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : ViewModel() {
    val query = MutableStateFlow("")
    private val _viewState = MutableStateFlow(SearchSuggestionViewState())
    val viewState = _viewState.asStateFlow()

    // Read live on every query, like HomeViewModel's flags, so a Settings
    // toggle takes effect without an app restart.
    private val prefs get() = PreferencesCache.current(context.dataStore)
    private val offlineMode get() = prefs[OfflineModeKey] ?: false
    private val hideExplicit get() = prefs[HideExplicitKey] ?: false
    private val hideVideoSongs get() = prefs[HideVideoSongsKey] ?: false

    init {
        viewModelScope.launch(Dispatchers.IO) {
            query
                .combine(CwSearchHistory.recent) { text, entities ->
                    text to entities.toMochyHistory()
                }
                .flatMapLatest { (text, history) ->
                    flow { emit(buildState(text, history)) }
                }
                .collect { _viewState.value = it }
        }
    }

    private suspend fun buildState(
        text: String,
        history: List<SearchHistory>,
    ): SearchSuggestionViewState {
        if (text.isEmpty()) {
            return SearchSuggestionViewState(history = history)
        }
        // Mochy's database.searchHistory(query) is a LIKE match, capped at 3.
        val matchingHistory = history
            .filter { it.query.contains(text, ignoreCase = true) }
            .take(3)

        if (offlineMode) {
            // offline: the library is all there is to search, shown where
            // YouTube's top results would be
            return SearchSuggestionViewState(
                history = matchingHistory,
                items = offlinePreview(text),
            )
        }

        val parsedUrl = YouTubeUrlParser.parse(text)
        val parsedItem = parsedUrl?.let { fetchParsedUrlItem(it) }

        // A pasted link replaces the suggestions entirely, as in Mochy.
        val suggestions = if (parsedUrl == null) {
            YtMusicRepository.searchSuggestions(text).getOrNull().orEmpty()
                .filter { suggestion -> matchingHistory.none { it.query == suggestion } }
                // a suggestion repeated twice gave two rows the same key and closed the app
                .distinct()
        } else {
            emptyList()
        }
        val typeahead = if (parsedUrl == null) {
            YtMusicRepository.searchTypeahead(text).getOrNull()?.rows.orEmpty()
                .mapNotNull { it.toYTItem() }
                .distinctBy { it.id }
                .filter { it.id != parsedItem?.id }
                .filterExplicit(hideExplicit)
                .filterVideoSongs(hideVideoSongs)
                .take(TopResultLimit)
        } else {
            emptyList()
        }
        return SearchSuggestionViewState(
            history = matchingHistory,
            suggestions = suggestions,
            items = listOfNotNull(parsedItem) + typeahead,
            isFromLink = parsedUrl != null,
        )
    }

    /**
     * The offline preview: 8 songs / 8 artists / 8 albums from the
     * on-device library, matching Mochy's OfflinePreviewSize. CW has no
     * local library search, so the played catalogue stands in: the all-time
     * ListeningStats charts (cached by the stats store, so this is cheap per
     * keystroke). Tracks never played are not in the charts and cannot match
     * — see NEEDS.md. Local playlists are DELIBERATELY excluded (coordinator
     * decision, 15 Nov): SearchScreen routes every PlaylistItem tap to
     * "online_playlist/{id}", and a local store id ("liked", …) would open a
     * bogus online-playlist route. They return when local playlist pages
     * land (Mappers3's OfflinePlaylist.toPlaylistItem is kept for that).
     */
    private suspend fun offlinePreview(text: String): List<YTItem> {
        val summary = runCatching { ListeningStats.summary(ReplayPeriod.ALL_TIME) }.getOrNull()
        val songs = summary?.songs.orEmpty()
            .filter { it.song.title.contains(text, ignoreCase = true) || it.song.artist.contains(text, ignoreCase = true) }
            .take(OfflinePreviewSize)
            .map { it.song.toSongItem() }
        val artists = summary?.artists.orEmpty()
            .filter { it.title.contains(text, ignoreCase = true) }
            .take(OfflinePreviewSize)
            .map { it.toArtistItem() }
        val albums = summary?.albums.orEmpty()
            .filter { it.title.contains(text, ignoreCase = true) }
            .take(OfflinePreviewSize)
            .map { it.toAlbumItem() }
        return songs + artists + albums
    }

    private suspend fun fetchParsedUrlItem(parsedUrl: YouTubeUrlParser.ParsedUrl): YTItem? {
        return try {
            when (parsedUrl) {
                is YouTubeUrlParser.ParsedUrl.Video ->
                    // CW has no "song by videoId" call; trackLinks reads the
                    // track's own watch-queue entry, which names it.
                    YtMusicRepository.trackLinks(parsedUrl.id).getOrNull()?.toSongItem()

                is YouTubeUrlParser.ParsedUrl.Artist ->
                    YtMusicRepository.artistPage(parsedUrl.id).getOrNull()?.let { page ->
                        ArtistItem(
                            id = parsedUrl.id,
                            title = page.name ?: parsedUrl.id,
                            thumbnail = page.thumbnailUrl,
                            channelId = parsedUrl.id,
                            playEndpoint = null,
                            shuffleEndpoint = null,
                            radioEndpoint = null,
                        )
                    }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Search: parsed-URL fetch failed for $parsedUrl")
            null
        }
    }

    /** Records a committed query. The screen already guards PauseSearchHistoryKey. */
    fun addHistory(query: String) {
        val text = query.trim()
        if (text.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                CwSearchHistory.record(
                    SearchHistoryEntity(
                        id = "$QUERY_ID_PREFIX$text",
                        title = text,
                        subtitle = "",
                        artworkUrl = null,
                        entityType = EntityType.TRACK,
                    )
                )
            }.onFailure { Timber.w(it, "Search: addHistory failed") }
        }
    }

    fun deleteHistory(entry: SearchHistory) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { CwSearchHistory.remove("$QUERY_ID_PREFIX${entry.query}") }
                .onFailure { Timber.w(it, "Search: deleteHistory failed") }
        }
    }
}

data class SearchSuggestionViewState(
    val history: List<SearchHistory> = emptyList(),
    val suggestions: List<String> = emptyList(),
    val items: List<YTItem> = emptyList(),
    val isFromLink: Boolean = false,
)

/** how many of each kind offline search shows */
private const val OfflinePreviewSize = 8

/**
 * CW's stored recents as Mochy history rows. Only the raw query terms
 * (id "q:<term>") qualify — tapped entities (tracks/albums/artists) are CW
 * classic-UI recents with artwork and navigation of their own, not query
 * strings, so they stay out of the Modern search field. Ids are the
 * 0-based position in the most-recent-first list; CW's store has no stable
 * numeric ids (see NEEDS.md).
 */
private fun List<SearchHistoryEntity>.toMochyHistory(): List<SearchHistory> =
    asSequence()
        .filter { it.id.startsWith(QUERY_ID_PREFIX) }
        .mapIndexed { index, entity -> SearchHistory(id = index.toLong(), query = entity.title) }
        .toList()
