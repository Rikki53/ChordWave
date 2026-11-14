// Ported from Mochy Music (GPL-3.0): lyrics/LyricsProviderRegistry.kt
// STATIC DATA ONLY: the provider names, the default order and the display
// names (verbatim). The live registry (provider instances, module providers,
// fetching) is engine and is NOT ported — see NEEDS.md §(b) L2.

package com.music.bitchord.ui.mochy.lyrics

object LyricsProviderRegistry {
    /** The providers the picker lists, in default preference order. */
    val providerNames: List<String>
        get() = getDefaultProviderOrder()

    // NetEase, QQ Music and KuGou near the end: they are Chinese services, and LyricsHelper only
    // believes them first-hand for Chinese songs. NetEase leads them because it often has word
    // timing. YouTube's auto captions last of all - they are speech recognition.
    fun getDefaultProviderOrder(): List<String> = listOf(
        "BetterLyrics",
        "YouLyPlus",
        "Paxsenix",
        "LrcRed",
        "SimpMusic",
        "LrcLib",
        "Deezer",
        "YouTubeSubtitle",
        "YouTubeMusic",
        "NetEase",
        "QQMusic",
        "Kugou",
        "YouTubeCaptions",
    )

    fun getDisplayName(name: String): String = when (name) {
        "YouLyPlus"       -> "YouLyPlus"
        "Paxsenix"        -> "PaxSenix"
        "BetterLyrics"    -> "Apple Music"
        "SimpMusic"       -> "SimpMusic"
        "LrcLib"          -> "LrcLib"
        "Kugou"           -> "KuGou"
        "NetEase"         -> "NetEase"
        "QQMusic"         -> "QQ Music"
        "Deezer"          -> "Deezer"
        "LrcRed"          -> "lrc.red"
        "YouTubeSubtitle" -> "YouTube Subtitle"
        "YouTubeMusic"    -> "YouTube Music"
        "YouTubeCaptions" -> "YouTube Auto Captions"
        else              -> name
    }
}
