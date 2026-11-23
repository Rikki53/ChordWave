// Ported from Mochy Music (GPL-3.0): lyrics/LyricsProviderRegistry.kt (attribution half)
// STATIC DATA ONLY: the attribution website a source asks to be linked to.
//
// These live as extensions on the LyricsProviderRegistry object staged by
// phase3-player-menus (which carries getDisplayName / getDefaultProviderOrder), so the
// footer can call LyricsProviderRegistry.getWebsite(provider) exactly as in Mochy without
// a second registry object. If the coordinator merges the two files, move these two
// declarations into the object as members and drop the extension imports at the call site.
//
// NOTE: Mochy's getProject / "Star on GitHub" button was removed in the round-1 brand
// audit (15 Nov 2026): the lyrics footer is ChordWave-only and no longer links out to
// other projects' repos.

package com.music.bitchord.ui.mochy.lyrics

// lrc.red lets anyone use its lyrics as long as the app links back to it wherever they show.
// (Mochy read this from com.music.directlyrics.LrcRed.BaseUrl; inlined here — verify the URL
// still resolves at merge time.)
// Unison (the BetterLyrics aggregator) asks for the same: its project page, not the fetch
// mirror (unison.boidu.dev) the engine calls.
fun LyricsProviderRegistry.getWebsite(name: String): String? = when (name) {
    "LrcRed" -> "https://lrc.red"
    "Unison" -> "https://unison.betterlyrics.org"
    else     -> null
}

/**
 * Provider name a user-edited lyrics row is saved under, so the edit is recognisable later.
 * Verbatim value of Mochy LyricsMenuViewModel.USER_EDITED ("UserEdited"); the coordinator's
 * lyrics-menu ViewModel must save edits under this same string.
 */
const val UserEditedProvider = "UserEdited"
