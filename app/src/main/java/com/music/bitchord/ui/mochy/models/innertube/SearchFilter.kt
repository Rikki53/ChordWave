// Ported from Mochy Music (GPL-3.0): providers/innertube/.../YouTube.kt:1531
// Mochy's engine SearchFilter value class, constants verbatim. Top-level here
// (in Mochy it is nested in the YouTube object). The adapter maps these to
// ChordWave's own SearchFilter enum (Mappers4.toCwFilter) — the two types
// share a name but live in different packages, and the CW one is always
// imported aliased (CwSearchFilter) where both are in scope.
package com.music.bitchord.ui.mochy.models.innertube

@JvmInline
value class SearchFilter(val value: String) {
    companion object {
        val FILTER_SONG = SearchFilter("EgWKAQIIAWoKEAkQBRAKEAMQBA%3D%3D")
        val FILTER_VIDEO = SearchFilter("EgWKAQIQAWoKEAkQChAFEAMQBA%3D%3D")
        val FILTER_ALBUM = SearchFilter("EgWKAQIYAWoKEAkQChAFEAMQBA%3D%3D")
        val FILTER_ARTIST = SearchFilter("EgWKAQIgAWoKEAkQChAFEAMQBA%3D%3D")
        val FILTER_FEATURED_PLAYLIST = SearchFilter("EgeKAQQoADgBagwQDhAKEAMQBRAJEAQ%3D")
        val FILTER_COMMUNITY_PLAYLIST = SearchFilter("EgeKAQQoAEABagoQAxAEEAoQCRAF")
    }
}
