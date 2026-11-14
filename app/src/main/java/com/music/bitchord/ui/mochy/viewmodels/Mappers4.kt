// Mappers4.kt — the extra mappings the search-result adapters need, kept here
// so the staged Mappers.kt / Mappers2.kt / Mappers3.kt stay untouched. One
// direction only: ChordWave -> Mochy, same as Mappers.kt.
//
//   SearchFilter (Mochy) -> CW SearchFilter   (filtered search + loadMore)
//   List<SearchResult>   -> SearchSummaryPage (summary view sections)
//   List<HomeShelf>      -> BrowseResult      (genre browse)
//   List<HomeShelf>      -> List<ChartSection> (charts page)
//   List<HomeShelf>      -> List<AlbumItem>   (explore new releases)
//   List<MoodGenreSection> -> List<MoodAndGenres.Item> (explore moods)
//
// The per-row mappings are reused, not redeclared: SearchResult.toYTItem /
// BrowseItem.toYTItem (Mappers3), CwSong.toSongItem (Mappers2),
// ShelfItem.toYTItem (ShelfItemMapper).

package com.music.bitchord.ui.mochy.viewmodels

import com.music.bitchord.data.model.BrowseType
import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.data.model.MoodGenreSection
import com.music.bitchord.data.model.SearchFilter as CwSearchFilter
import com.music.bitchord.data.model.SearchResult
import com.music.bitchord.ui.mochy.models.innertube.AlbumItem
import com.music.bitchord.ui.mochy.models.innertube.ArtistItem
import com.music.bitchord.ui.mochy.models.innertube.BrowseEndpoint
import com.music.bitchord.ui.mochy.models.innertube.BrowseResult
import com.music.bitchord.ui.mochy.models.innertube.ChartsPage
import com.music.bitchord.ui.mochy.models.innertube.MoodAndGenres
import com.music.bitchord.ui.mochy.models.innertube.PlaylistItem
import com.music.bitchord.ui.mochy.models.innertube.SearchFilter
import com.music.bitchord.ui.mochy.models.innertube.SearchSummary
import com.music.bitchord.ui.mochy.models.innertube.SearchSummaryPage
import com.music.bitchord.ui.mochy.models.innertube.SongItem

/**
 * A Mochy filter as the ChordWave filter whose search params run the same
 * tab. Mochy's song params differ by one flag from CW's SONGS params
 * ("...BRA..." vs "...ChA...") — the CW enum is what CW's parser was built
 * against, so the mapping is by constant identity, never by params string.
 * Featured and community playlists share CW's single PLAYLISTS filter, so
 * those two tabs show the same results (see NEEDS.md). An unknown value
 * falls back to the mixed search rather than failing.
 */
fun SearchFilter.toCwFilter(): CwSearchFilter =
    when (this) {
        SearchFilter.FILTER_SONG -> CwSearchFilter.SONGS
        SearchFilter.FILTER_VIDEO -> CwSearchFilter.VIDEOS
        SearchFilter.FILTER_ALBUM -> CwSearchFilter.ALBUMS
        SearchFilter.FILTER_ARTIST -> CwSearchFilter.ARTISTS
        SearchFilter.FILTER_FEATURED_PLAYLIST,
        SearchFilter.FILTER_COMMUNITY_PLAYLIST -> CwSearchFilter.PLAYLISTS
        else -> CwSearchFilter.ALL
    }

/**
 * An unfiltered search's flat rows as Mochy's summary sections. CW's parser
 * flattens YouTube's per-category shelves (InnertubeParser.parseSearchPage),
 * so the sections are rebuilt by row type, in response order within each
 * section; the promoted top track stays first in Songs. Empty sections are
 * dropped — the screen renders a header per section, and an empty one would
 * show a bare rule. The screen renames the first section "Exact matches"
 * whatever its title is, so the titles here only label sections 2+.
 */
fun List<SearchResult>.toSearchSummaryPage(): SearchSummaryPage {
    val songs = mutableListOf<SongItem>()
    val albums = mutableListOf<AlbumItem>()
    val artists = mutableListOf<ArtistItem>()
    val playlists = mutableListOf<PlaylistItem>()
    forEach { row ->
        when (row) {
            is SearchResult.TopTrack -> songs += row.song.toSongItem()
            is SearchResult.Track -> songs += row.song.toSongItem()
            is SearchResult.Browse ->
                when (row.item.type) {
                    BrowseType.ALBUM -> (row.item.toYTItem() as? AlbumItem)?.let { albums += it }
                    BrowseType.ARTIST -> (row.item.toYTItem() as? ArtistItem)?.let { artists += it }
                    BrowseType.PLAYLIST -> (row.item.toYTItem() as? PlaylistItem)?.let { playlists += it }
                    BrowseType.OTHER -> Unit
                }
        }
    }
    return SearchSummaryPage(
        listOf(
            SearchSummary("Songs", songs),
            SearchSummary("Albums", albums),
            SearchSummary("Artists", artists),
            SearchSummary("Playlists", playlists),
        ).filter { it.items.isNotEmpty() },
    )
}

/**
 * Browse shelves as a genre page. Shelves that map to nothing (cards CW's
 * ShelfItemMapper cannot type) are dropped — the screen filters empties
 * itself, but the community loader below also reads these items. The title
 * is the host's: CW's browse call returns shelves only, no page title.
 */
fun List<HomeShelf>.toBrowseResult(title: String?): BrowseResult =
    BrowseResult(
        title = title,
        items = mapNotNull { shelf ->
            val items = shelf.items
                .mapNotNull { it.toYTItem(shelf.layout) }
                .distinctBy { it.id }
            if (items.isEmpty()) null else BrowseResult.Item(title = shelf.title, items = items)
        },
    )

/** Browse shelves as chart sections, typed the way Mochy types them (YouTube.kt determineChartType). */
fun List<HomeShelf>.toChartSections(): List<ChartsPage.ChartSection> =
    mapNotNull { shelf ->
        val items = shelf.items
            .mapNotNull { it.toYTItem(shelf.layout) }
            .distinctBy { it.id }
        if (items.isEmpty()) null
        else ChartsPage.ChartSection(
            title = shelf.title,
            items = items,
            chartType = chartTypeFor(shelf.title),
        )
    }

/** Mochy's determineChartType, verbatim rule: Trending / Top / everything else is a genre chart. */
private fun chartTypeFor(title: String): ChartsPage.ChartType =
    when {
        title.contains("Trending", ignoreCase = true) -> ChartsPage.ChartType.TRENDING
        title.contains("Top", ignoreCase = true) -> ChartsPage.ChartType.TOP
        else -> ChartsPage.ChartType.GENRE
    }

/**
 * The new-release albums shelf off FEmusic_explore. Mochy picks the shelf
 * whose "more" button points at FEmusic_new_releases_albums; CW's HomeShelf
 * carries that as moreBrowseId, so the same rule applies, with a title
 * backstop ("New albums & singles") and a last-resort first shelf holding
 * album cards, in case YouTube renames or reorders the page.
 */
fun List<HomeShelf>.newReleaseAlbums(): List<AlbumItem> {
    val shelf = firstOrNull { it.moreBrowseId == "FEmusic_new_releases_albums" }
        ?: firstOrNull { it.title.contains("New albums", ignoreCase = true) }
        ?: firstOrNull { shelf -> shelf.items.any { it.browseId?.startsWith("MPRE") == true } }
        ?: return emptyList()
    return shelf.items
        .mapNotNull { it.toYTItem(shelf.layout) }
        .filterIsInstance<AlbumItem>()
        .distinctBy { it.id }
}

/**
 * Mood/genre sections flattened to the explore page's button list.
 * (Mochy's explore() reads the same buttons off the FEmusic_explore page;
 * CW's moodAndGenres() is the same data, already parsed.)
 */
fun List<MoodGenreSection>.toMoodAndGenresItems(): List<MoodAndGenres.Item> =
    flatMap { section -> section.items }.map { genre ->
        MoodAndGenres.Item(
            title = genre.title,
            stripeColor = genre.stripeColor ?: stripeFallback(genre.title),
            endpoint = BrowseEndpoint(browseId = genre.browseId, params = genre.params),
        )
    }

/**
 * YouTube's own stripe colour when the button carries one, else a stable
 * palette pick by title hash. Mirrors MoodAndGenresViewModel's private
 * fallback (that file must not be edited from here); the values are the
 * same, so a tile's stripe matches across the Search tab and Explore.
 */
private fun stripeFallback(title: String): Long =
    EXPLORE_STRIPE_PALETTE[Math.floorMod(title.hashCode(), EXPLORE_STRIPE_PALETTE.size)]

/** Muted ARGB stripes for buttons YouTube sent without a colour — same values as MoodAndGenresViewModel. */
private val EXPLORE_STRIPE_PALETTE = listOf(
    0xFFE57373L,
    0xFFBA68C8L,
    0xFF64B5F6L,
    0xFF4DB6ACL,
    0xFFAED581L,
    0xFFFFD54FL,
    0xFFFF8A65L,
    0xFF90A4AEL,
)
