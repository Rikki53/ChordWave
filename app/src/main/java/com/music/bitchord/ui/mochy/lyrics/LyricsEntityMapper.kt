// LyricsEntityMapper.kt — ChordWave lyrics state -> the Mochy lyrics row.
//
// Two mappings, kept pure so they are testable without a player:
//
// 1. Provider names: ChordWave's LyricsSource enum -> the name the Mochy UI
//    shows in the source footer (LyricsProviderRegistry.getDisplayName, which
//    returns unknown names unchanged). Where CW's source has an exact
//    registry twin it maps to it; otherwise the source's own label is used,
//    so the footer always shows something truthful rather than a near-miss.
//    Deliberate non-matches: YOUTUBE_TRANSCRIPT stays "YouTube captions"
//    (mapping it to "YouTubeCaptions" would print "YouTube Auto Captions"
//    for captions that may be uploaded, not auto-generated), and
//    BETTER_LYRICS_PORTATO / PAXSENIX_SPOTIFY / PAXSENIX_MUSIXMATCH keep
//    their labels (they are distinct backends behind a shared brand).
// 2. The three-state row: the UI distinguishes "still loading" (null),
//    "looked, found nothing" (a row whose lyrics are LYRICS_NOT_FOUND) and
//    "found" (a row with LRC text). ChordWave's MainViewModel supplies
//    exactly those three states via lyrics + lyricsChecked — see
//    mapLyricsToEntity. This is the contract the staged UI was written
//    against (LyricsScreen.kt:565-577: null -> loading dots, non-null row
//    with no lines -> "No lyrics found"); collapsing "not found" into null
//    would leave the loading dots spinning for ever on a lyric-less song.

package com.music.bitchord.ui.mochy.lyrics

import com.music.bitchord.data.lyrics.LyricLine
import com.music.bitchord.data.lyrics.LyricsSource

/**
 * The provider name the Mochy UI shows for a ChordWave lyrics source: the
 * [LyricsProviderRegistry] name where one matches exactly, else the source's
 * own label.
 */
fun LyricsSource.toMochyProviderName(): String = when (this) {
    LyricsSource.LRC_RED -> "LrcRed"
    LyricsSource.BETTER_LYRICS -> "BetterLyrics"
    LyricsSource.PAXSENIX -> "Paxsenix"
    LyricsSource.SIMP_MUSIC -> "SimpMusic"
    LyricsSource.YOUTUBE_MUSIC -> "YouTubeMusic"
    LyricsSource.KUGOU -> "Kugou"
    LyricsSource.LRCLIB -> "LrcLib"
    else -> label
}

/**
 * The lyrics row for [songId] from ChordWave's lookup state.
 *
 * - [lines] null and [checked] false: the lookup is still running (or has
 *   not started) -> null, and the UI shows loading dots.
 * - [lines] null and [checked] true: the lookup finished with nothing ->
 *   a row holding [LyricsEntity.LYRICS_NOT_FOUND], and the UI shows
 *   "No lyrics found".
 * - [lines] non-null but serialising blank (only gap lines): same as
 *   nothing found — there is nothing to display.
 * - [lines] non-null with text: a row holding the serialised LRC.
 *
 * Provider: the mapped source name, or "Embedded" when [lines] exist but
 * [source] is null. That combination only arises from the embedded-file
 * path: MainViewModel.loadLyrics sets the source to null exactly when the
 * lyrics came out of the song file itself (LyricsRepository.Result.source
 * is non-null for every network source).
 */
fun mapLyricsToEntity(
    songId: String,
    lines: List<LyricLine>?,
    source: LyricsSource?,
    checked: Boolean,
): LyricsEntity? {
    if (lines == null) {
        if (!checked) return null
        return LyricsEntity(
            songId = songId,
            lyrics = LyricsEntity.LYRICS_NOT_FOUND,
            provider = source?.toMochyProviderName(),
        )
    }
    val raw = serializeLyricLines(lines)
    if (raw.isBlank()) {
        return LyricsEntity(
            songId = songId,
            lyrics = LyricsEntity.LYRICS_NOT_FOUND,
            provider = source?.toMochyProviderName(),
        )
    }
    return LyricsEntity(
        songId = songId,
        lyrics = raw,
        provider = source?.toMochyProviderName() ?: EmbeddedLyrics.Provider,
    )
}
