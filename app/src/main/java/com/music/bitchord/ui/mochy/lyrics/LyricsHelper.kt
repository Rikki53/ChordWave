// Ported from Mochy Music (GPL-3.0): lyrics/LyricsHelper.kt
// CONSTANTS ONLY. LyricsHelper is the lyrics ENGINE (provider selection, fetching, caching)
// and is NOT ported — see NEEDS.md §(b). Only the provider-name sentinel the UI compares
// against survives.

package com.music.bitchord.ui.mochy.lyrics

object LyricsHelper {
    /** Provider name recorded when lyrics could not be fetched for lack of a connection. */
    const val NoConnectionProvider = "NoConnection"
}
