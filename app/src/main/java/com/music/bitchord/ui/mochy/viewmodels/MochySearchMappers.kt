// MochySearchMappers — the Mochy engine's search results as the Modern
// search screens read them (two-engine architecture, Tahap 4).
//
// Riki's decision: Modern search uses Mochy search, Classic keeps its own.
// Since batch 77 the Modern screens read the engine's own innertube models
// (com.music.innertube.models) directly — the CW-side twin copies under
// ui.mochy.models.innertube were merged into the engine ones, so the only
// conversions left are the filter and the summary-page wrapper.

package com.music.bitchord.ui.mochy.viewmodels

import com.music.bitchord.ui.mochy.models.innertube.SearchFilter
import com.music.bitchord.ui.mochy.models.innertube.SearchSummary
import com.music.bitchord.ui.mochy.models.innertube.SearchSummaryPage
import com.music.innertube.YouTube
import com.music.innertube.pages.SearchSummaryPage as EngineSearchSummaryPage

/** The Modern filter as the engine's own filter (identical params strings). */
fun SearchFilter.toEngineFilter(): YouTube.SearchFilter = YouTube.SearchFilter(value)

fun EngineSearchSummaryPage.toModernSummaryPage(): SearchSummaryPage = SearchSummaryPage(
    summaries = summaries.map { section ->
        SearchSummary(
            title = section.title,
            items = section.items,
        )
    },
)
