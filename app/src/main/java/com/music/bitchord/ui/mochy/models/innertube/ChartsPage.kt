// Ported from Mochy Music (GPL-3.0): providers/innertube/.../pages/ChartsPage.kt
// Data classes + enum only, verbatim. The adapter fills sections from
// ChordWave's FEmusic_charts shelves (Mappers4.toChartSections).
package com.music.bitchord.ui.mochy.models.innertube

data class ChartsPage(
    val sections: List<ChartSection>,
    val continuation: String?
) {
    data class ChartSection(
        val title: String,
        val items: List<YTItem>,
        val chartType: ChartType
    )

    enum class ChartType {
        TRENDING, TOP, GENRE, NEW_RELEASES
    }
}
