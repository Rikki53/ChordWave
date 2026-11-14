// Minimal stand-in for Mochy's innertube Run (a styled text run), carried
// only so ArtistPage.descriptionRuns has a type. No ported screen reads
// descriptionRuns, and CW's parser hands the artist bio over as a plain
// String, so the field is always null from the CW adapter. Replace this
// with the full Run port if a later phase needs styled description runs.
// (phase2-detail NEEDS.md §(a) C4.)
package com.music.bitchord.ui.mochy.models.innertube

import kotlinx.serialization.Serializable

@Serializable
data class Run(
    val text: String,
)
