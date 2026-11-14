// Ported from Mochy Music (GPL-3.0): lyrics/LyricsProviderRegistry.kt (attribution half)
// STATIC DATA ONLY: the open-source project behind a source (for the lyrics page's
// "Star on GitHub" button) and the attribution website a source asks to be linked to.
//
// These live as extensions on the LyricsProviderRegistry object staged by
// phase3-player-menus (which carries getDisplayName / getDefaultProviderOrder), so the
// footer can call LyricsProviderRegistry.getProject(provider) exactly as in Mochy without
// a second registry object. If the coordinator merges the two files, move these three
// declarations into the object as members and drop the extension imports at the call site.

package com.music.bitchord.ui.mochy.lyrics

data class LyricsProject(val name: String, val repoUrl: String)

/**
 * The open-source project behind a source, for the lyrics page's "Star on GitHub" button.
 * Only sources whose code is public: KuGou, NetEase, QQ Music, Deezer and YouTube are the
 * companies' own services, and Paxsenix hasn't published its server. SimpMusic's lyrics
 * server has no public repo of its own, so its star goes to the SimpMusic app.
 */
fun LyricsProviderRegistry.getProject(name: String): LyricsProject? = when (name) {
    "YouLyPlus"    -> LyricsProject("LyricsPlus", "https://github.com/ibratabian17/LyricsPlus")
    "BetterLyrics" -> LyricsProject("Better Lyrics", "https://github.com/better-lyrics/better-lyrics")
    "LrcLib"       -> LyricsProject("LRCLIB", "https://github.com/tranxuanthang/lrclib")
    "SimpMusic"    -> LyricsProject("SimpMusic", "https://github.com/maxrave-dev/SimpMusic")
    else           -> null
}

// lrc.red lets anyone use its lyrics as long as the app links back to it wherever they show.
// (Mochy read this from com.music.directlyrics.LrcRed.BaseUrl; inlined here — verify the URL
// still resolves at merge time.)
fun LyricsProviderRegistry.getWebsite(name: String): String? = when (name) {
    "LrcRed" -> "https://lrc.red"
    else     -> null
}

/**
 * Provider name a user-edited lyrics row is saved under, so the edit is recognisable later.
 * Verbatim value of Mochy LyricsMenuViewModel.USER_EDITED ("UserEdited"); the coordinator's
 * lyrics-menu ViewModel must save edits under this same string.
 */
const val UserEditedProvider = "UserEdited"
