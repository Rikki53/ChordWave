// LyricsEntity.kt — MINIMAL staged stand-in for Mochy's db.entities.LyricsEntity
// (a Room entity). The player-menu sheets only read two fields of the current
// song's lyrics row: the raw lyrics text and which provider supplied it.
// The adapter round fills this from ChordWave's real lyrics store — see
// NEEDS.md §(b) L1. Do NOT grow this into a Room entity here.

package com.music.bitchord.ui.mochy.lyrics

/**
 * The current song's lyrics, as the lyrics sheets see them.
 *
 * @param songId the song these lyrics belong to.
 * @param lyrics the raw lyrics text (LRC / rich-sync / plain), or
 *   [LYRICS_NOT_FOUND] when no provider had any.
 * @param provider the provider name that supplied [lyrics] (a
 *   [LyricsProviderRegistry] name, e.g. "LrcLib"), or null when unknown.
 * @param translatedLyrics the stored translation of [lyrics], one line per
 *   line, or "" when untranslated (Mochy db/entities/LyricsEntity.kt).
 * @param translationLanguage the language [translatedLyrics] is in, or "".
 * @param translationMode how [translatedLyrics] was produced, or "".
 */
data class LyricsEntity(
    val songId: String,
    val lyrics: String,
    val provider: String? = null,
    val translatedLyrics: String = "",
    val translationLanguage: String = "",
    val translationMode: String = "",
) {
    companion object {
        // Verbatim from Mochy's db/entities/LyricsEntity.kt.
        const val LYRICS_NOT_FOUND = "LYRICS_NOT_FOUND"
    }
}
