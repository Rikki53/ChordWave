// LyricsSerializer.kt — ChordWave LyricLine list -> LRC text.
//
// The Mochy lyrics UI reads raw lyrics text (LyricsEntity.lyrics) and parses
// it itself with LyricsUtils.parseLyricsCached, so the adapter has to hand it
// text — ChordWave's parsed LyricLine list is serialised back to LRC here.
// The format is chosen to round-trip through the staged parser
// (phase3-player-menus lyrics/LyricsUtils.kt, verbatim from Mochy):
//
// - Line-synced lines: `[mm:ss.cc]text` — parsed by parseStandardLyrics.
// - Word-synced lines: `[mm:ss.cc]<mm:ss.cc>word1 <mm:ss.cc>word2 ...` —
//   parsed by parseRichSyncLyrics (any line carrying `<time>` tags makes the
//   whole song parse rich; lines without tags still parse, with null words).
// - A word the provider split into syllables is emitted syllable by syllable
//   (`<t>e<t>nough`), with no space between the pieces: the parser's
//   fixWordSpacing merges them back into the word and keeps the pieces in
//   LyricsEntry.syllables — exactly Mochy's model. Syllable tags are only
//   emitted when the syllables tile the word exactly; otherwise the word gets
//   a single tag, so a bad split can never garble the line.
// - Duets: when any line has LyricAlignment.End, every line carries an
//   `{agent:v1}` / `{agent:v2}` tag (Start -> v1, End -> v2). Single-voice
//   songs carry no agent tags at all.
// - A nested background line is emitted as its own line at its own stamp with
//   a `{bg}` prefix, immediately after the line it answers. Mochy renders
//   isBackground lines centred on their own clock, which is what the nested
//   line is.
//
// What does NOT survive the round-trip (all documented in NEEDS.md):
// - Word END times: the rich-sync parser derives each word's end from the
//   next word's start (or the next line's start, or +0.5s). CW's endMs is
//   dropped by the format, not by this file.
// - sungUntilMs: LRC has no line-end stamp. lineEndMs falls back to the next
//   line's start / the last word's end, as it does for any LRC source.
// - timingSource (CW's display-only translation line): ignored; Mochy has
//   its own translation layer.
// - Gap (instrumental) lines: blank lines carry no text, and the parser drops
//   bare-timestamp lines (LINE_REGEX needs text after the tag), so they are
//   skipped. The UI computes interludes from the time gaps between lines.
//
// Sanitisation is length-preserving (one char -> one char), so LyricLine's
// wordSpans — computed on the original text — stay valid on the emitted text:
// newlines become spaces (a newline would split the LRC line and the parser
// would drop the continuation), `<` / `>` become `‹` / `›` (a `<` would
// truncate the rich-sync word regex's `[^<]+` match). Backslashes are NOT
// escaped: parseLyrics unescapes `\\n` sequences, so a literal backslash-n in
// source lyrics would still break; that is vanishingly rare in lyrics and is
// accepted rather than papered over.
// Timestamps over 99 minutes (`%02d` prints 3 digits) would not match the
// parser's `\d\d` minute patterns; no song is that long, and it is accepted.

package com.music.bitchord.ui.mochy.lyrics

import com.music.bitchord.data.lyrics.LyricAlignment
import com.music.bitchord.data.lyrics.LyricLine
import com.music.bitchord.data.lyrics.LyricSyllable
import com.music.bitchord.data.lyrics.LyricWord
import java.util.Locale

/**
 * Serialises ChordWave's parsed lines to the LRC text the Mochy UI parses
 * back with [LyricsUtils.parseLyricsCached]. Empty string when there is
 * nothing to show (no lines, or only gap lines).
 */
fun serializeLyricLines(lines: List<LyricLine>): String {
    if (lines.isEmpty()) return ""
    val multiVoice = lines.any { it.alignment == LyricAlignment.End }
    return buildString {
        for (line in lines) {
            appendSerializedLine(line, multiVoice, isBackground = false)
            val background = line.background
            if (background != null) {
                // A background line's own background is always null (see
                // LyricLine's KDoc), so one level is the whole tree.
                appendSerializedLine(background, multiVoice, isBackground = true)
            }
        }
    }.trimEnd('\n')
}

/** One line (or its background vocal) as `[mm:ss.cc]content`, or nothing for a gap line. */
private fun StringBuilder.appendSerializedLine(
    line: LyricLine,
    multiVoice: Boolean,
    isBackground: Boolean,
) {
    val text = sanitizeLyricText(line.text)
    if (text.isBlank() && line.words.isEmpty()) return
    append('[')
    append(formatLyricTimestamp(line.timeMs))
    append(']')
    appendLineContent(line, text, multiVoice, isBackground)
    append('\n')
}

private fun StringBuilder.appendLineContent(
    line: LyricLine,
    text: String,
    multiVoice: Boolean,
    isBackground: Boolean,
) {
    // Order matters: the parser strips {agent:...} first and only then looks
    // for {bg} at the start of what is left.
    if (multiVoice) {
        append("{agent:")
        append(if (line.alignment == LyricAlignment.End) "v2" else "v1")
        append('}')
    }
    if (isBackground) append("{bg}")
    if (line.words.isEmpty()) {
        append(text)
        return
    }
    if (text.isBlank()) {
        // Words but no line text to hang the tags on: rebuild from the words.
        line.words.forEachIndexed { index, word ->
            if (index > 0) append(' ')
            appendWordWithTags(word, sanitizeLyricText(word.text))
        }
        return
    }
    appendRichSyncContent(line, text)
}

/**
 * The line's words as `<tag>` pieces laid over the line text: the gap between
 * two words (spaces, punctuation) is emitted verbatim, so stripping the tags
 * gives the line text back exactly.
 */
private fun StringBuilder.appendRichSyncContent(line: LyricLine, text: String) {
    val spans = line.wordSpans
    var cursor = 0
    for ((index, word) in line.words.withIndex()) {
        val span = spans.getOrNull(index) ?: IntRange.EMPTY
        val start = if (span.isEmpty()) -1 else span.first
        val end = if (span.isEmpty()) -1 else span.last + 1
        if (start < cursor || start < 0 || end > text.length || end <= start) {
            // This word cannot be located in the text (or sits before the
            // previous one): stop tagging here and emit the rest of the line
            // as plain text. The words already tagged keep their timing, and
            // the line still reads whole.
            if (cursor < text.length) append(text, cursor, text.length)
            return
        }
        if (start > cursor) append(text, cursor, start)
        appendWordWithTags(word, text.substring(start, end))
        cursor = end
    }
    if (cursor < text.length) append(text, cursor, text.length)
}

/**
 * One word with its timing tag(s): a tag per syllable where the provider
 * split the word and the syllables tile it exactly, else a single tag for
 * the whole word. The syllable pieces carry no space between them, which is
 * what tells the parser's fixWordSpacing to merge them back into one word
 * (and keep the pieces in LyricsEntry.syllables).
 */
private fun StringBuilder.appendWordWithTags(word: LyricWord, wordText: String) {
    val syllables = word.syllables
    if (syllables.isEmpty() || !syllablesTileWord(syllables, wordText)) {
        append('<')
        append(formatLyricTimestamp(word.startMs))
        append('>')
        append(wordText)
        return
    }
    for (syllable in syllables) {
        append('<')
        append(formatLyricTimestamp(syllable.startMs))
        append('>')
        append(wordText, syllable.startChar, syllable.endChar)
    }
}

/** Whether the syllables cover [wordText] end to end with no gaps or overlaps. */
private fun syllablesTileWord(
    syllables: List<LyricSyllable>,
    wordText: String,
): Boolean {
    var covered = 0
    for (syllable in syllables) {
        if (syllable.startChar != covered) return false
        if (syllable.endChar <= syllable.startChar) return false
        if (syllable.endChar > wordText.length) return false
        covered = syllable.endChar
    }
    return covered == wordText.length
}

/**
 * Length-preserving sanitisation: newlines become spaces, angle brackets
 * become single angle quotes. See the file header for why, and why the
 * length-preserving property matters (wordSpans).
 */
private fun sanitizeLyricText(text: String): String {
    if (text.none { it == '\n' || it == '\r' || it == '<' || it == '>' }) return text
    return buildString(text.length) {
        for (char in text) {
            when (char) {
                '\n', '\r' -> append(' ')
                '<' -> append('‹')
                '>' -> append('›')
                else -> append(char)
            }
        }
    }
}

/** `mm:ss.cc` with US digits (some locales localise %d digits). */
private fun formatLyricTimestamp(ms: Long): String {
    val safe = ms.coerceAtLeast(0L)
    val minutes = safe / 60_000L
    val seconds = (safe % 60_000L) / 1_000L
    val centis = (safe % 1_000L) / 10L
    return "%02d:%02d.%02d".format(Locale.US, minutes, seconds, centis)
}
