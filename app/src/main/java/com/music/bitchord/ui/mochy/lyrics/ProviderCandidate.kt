// Ported from Mochy Music (GPL-3.0): lyrics/LyricsHelper.kt (ProviderCandidate)
// Verbatim data class; only the package changed. The fetching that produces
// these candidates (LyricsHelper / LyricsMenuViewModel) is engine — see
// NEEDS.md §(b) L2.

package com.music.bitchord.ui.mochy.lyrics

data class ProviderCandidate(
    val providerName: String,
    val lyrics: String,
    val order: Int,
    val sync: LyricsUtils.SyncKind,
    val script: LyricsUtils.Script,
    /** For Latin-script lyrics, the language when it can be told; see [LyricsUtils.latinLanguage]. */
    val language: String? = null,
) {
    /** Carries real left/right voice tags, which is what lays a duet out on both sides. */
    val hasDuet: Boolean get() = LyricsUtils.hasDistinctVoices(lyrics)
}
