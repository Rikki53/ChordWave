// Ported from Mochy Music (GPL-3.0): lyrics/LyricsUtils.kt
// FAITHFUL SUBSET — the lyrics parsing/grading surface the player menus
// and song edit sheets need, extracted verbatim (line ranges noted):
//   object header + timing regexes (14-25)
//   decodeHtmlEntities (332-354)
//   parseLyrics / parseLyricsCached / warmParsedLyrics / fixWordSpacing (388-544)
//   parseRichSyncLyrics / parseRichSyncWords / parseStandardLyrics /
//   parseWordTimestamps / parseLine (545-760)
//   isUnsynced / parsePlainLyrics / SyncKind / syncKind / Script /
//   dominantScript (805-913)
//   hasVoiceTags / hasDistinctVoices / wordCharRanges (995-1032)
// NOT ported: isSyllableSplit (lyrics screen / LyricsHelper only).
// MERGED BY COORDINATOR (15 Nov): the line-index helpers are now here,
// verbatim from Mochy :762-800 (LineLeadInMs/findCurrentLineIndex/
// lineEndMs/lyricsEndMs/UntimedLineDurationMs), and the romanisation
// surface moved in from the lyrics chunk's stub (stubs returning
// input/null/false, except readingLine which is verbatim) so
// LyricsReadings.kt compiles. The lyrics chunk's LyricsUtils stub file
// was deleted — this file is the single home of LyricsUtils.

package com.music.bitchord.ui.mochy.lyrics

import android.text.format.DateUtils
import timber.log.Timber

@Suppress("RegExpRedundantEscape")
object LyricsUtils {
    val LINE_REGEX = "((\\[\\d\\d:\\d\\d\\.\\d{2,3}\\] ?)+)(.+)".toRegex()
    val TIME_REGEX = "\\[(\\d\\d):(\\d\\d)\\.(\\d{2,3})\\]".toRegex()

    private val RICH_SYNC_LINE_REGEX = "\\[(\\d{1,2}):(\\d{2})\\.(\\d{2,3})\\](.+)".toRegex()
    private val RICH_SYNC_WORD_REGEX = "<(\\d{1,2}):(\\d{2})\\.(\\d{2,3})>\\s*([^<]+)".toRegex()
    private val RICH_SYNC_TAG_REGEX = "<\\d{1,2}:\\d{2}\\.\\d{2,3}>".toRegex()

    private val AGENT_REGEX = "\\{agent:([^}]+)\\}".toRegex()
    private val BACKGROUND_REGEX = "^\\{bg\\}".toRegex()


    private val HEX_ENTITY_REGEX = "&#x([0-9a-fA-F]+);".toRegex()
    private val DEC_ENTITY_REGEX = "&#(\\d+);".toRegex()

    private fun decodeHtmlEntities(text: String): String =
        text
            .replace(HEX_ENTITY_REGEX) { match ->
                match.groupValues[1].toIntOrNull(16)
                    ?.takeIf { it in 0..0x10FFFF }
                    ?.let { String(Character.toChars(it)) }
                    ?: match.value
            }
            .replace(DEC_ENTITY_REGEX) { match ->
                match.groupValues[1].toIntOrNull()
                    ?.takeIf { it in 0..0x10FFFF }
                    ?.let { String(Character.toChars(it)) }
                    ?: match.value
            }
            .replace("&apos;", "'")
            .replace("&quot;", "\"")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")

    fun parseLyrics(lyrics: String): List<LyricsEntry> {

        val unescapedLyrics = lyrics
            .trim()
            .removePrefix("\"")
            .removeSuffix("\"")
            .replace("\\\\", "\\")
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")

        val decodedLyrics = decodeHtmlEntities(unescapedLyrics)

        val lines = decodedLyrics.lines()
            .filter { it.isNotBlank() && !it.trim().startsWith("[offset:") }

        val isRichSync = lines.any { line ->
            RICH_SYNC_LINE_REGEX.matches(line.trim()) &&
            RICH_SYNC_WORD_REGEX.containsMatchIn(line)
        }

        val parsed = if (isRichSync) {
            parseRichSyncLyrics(lines)
        } else {
            parseStandardLyrics(lines)
        }
        return fixWordSpacing(parsed)
    }

    // the last few songs' parsed lines, never handed out themselves, since each caller fills its own reading flows
    private val parsedCache = object : LinkedHashMap<String, List<LyricsEntry>>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<LyricsEntry>>?) = size > 4
    }

    // same as parseLyrics, but the card, the page and the styles stop re-parsing one song on the main thread
    fun parseLyricsCached(lyrics: String): List<LyricsEntry> {
        val template = synchronized(parsedCache) { parsedCache[lyrics] }
            ?: parseLyrics(lyrics).also { parsed -> synchronized(parsedCache) { parsedCache[lyrics] = parsed } }
        return template.map { entry ->
            entry.copy(
                romanizedTextFlow = MutableStateFlow(null),
                rubyFlow = MutableStateFlow(null),
                translatedTextFlow = MutableStateFlow(null),
                romanizedWordsFlow = MutableStateFlow(null),
            )
        }
    }

    // parse in the background as soon as a song's lyrics land, so opening the player finds them ready
    fun warmParsedLyrics(lyrics: String?) {
        if (lyrics.isNullOrBlank() || lyrics == LyricsEntity.LYRICS_NOT_FOUND) return
        if (synchronized(parsedCache) { parsedCache.containsKey(lyrics) }) return
        val parsed = runCatching { parseLyrics(lyrics) }.getOrNull() ?: return
        synchronized(parsedCache) { parsedCache[lyrics] = parsed }
    }

    /**
     * Repairs the two ways a line's word timings and its text come apart, so every lyrics style
     * shows the same words:
     *
     *  - **Syllables timed as words** ("a" "gain", "lo" "ve"). The line text is right, but the
     *    styles that lay out one timed word after another with a space between showed "let down a
     *    gain you will not.. I thin k I lo ve yo u". Pieces with no space between them in the
     *    line are one word, and are merged into it. The pieces themselves stay in
     *    [LyricsEntry.syllables] for the main style, which sweeps syllable by syllable.
     *  - **No spaces at all** ("<t>I<t>love<t>you" reads "Iloveyou"). When nearly every
     *    multi-word line of a song has no space in it, the source dropped them: its timed pieces
     *    are the words, and the text is rebuilt with spaces between them.
     *
     * Neither touches Chinese, Japanese or Thai, which are written without spaces.
     */
    private fun fixWordSpacing(entries: List<LyricsEntry>): List<LyricsEntry> {
        if (entries.none { (it.words?.size ?: 0) > 1 }) return entries
        val spacesDropped = spacesDropped(entries)
        return entries.map { entry ->
            val words = entry.words?.takeIf { it.size > 1 } ?: return@map entry
            val text = if (spacesDropped && isGluedLine(entry.text, words)) spacedText(words) else entry.text
            val merged = mergeWordPieces(text, words)
            if (text == entry.text && merged == words) {
                entry
            } else {
                entry.copy(text = text, words = merged, syllables = words.takeIf { it.size != merged.size })
            }
        }
    }

    /** Scripts written without spaces between words: their timed pieces are never merged. */
    private val UnspacedScripts = setOf(Script.HAN, Script.KANA, Script.THAI)

    /** Two or more timed pieces on a line in a spaced script with no space anywhere in it. */
    private fun isGluedLine(text: String, words: List<WordTimestamp>): Boolean =
        words.count { it.text.isNotBlank() } >= 2 &&
            text.none(Char::isWhitespace) &&
            dominantScript(text).let { it !in UnspacedScripts && it != Script.UNKNOWN }

    /**
     * Whether the source left out the spaces between words: at least three multi-piece lines in a
     * spaced script, four in five of them with no space at all, and those split into three or more
     * pieces on average. Lyrics timed in syllables still have spaces between their words on most
     * lines, so they never get this far, and neither do a few glued "oh-oh"s.
     */
    private fun spacesDropped(entries: List<LyricsEntry>): Boolean {
        val lines = entries.filter { entry ->
            val words = entry.words ?: return@filter false
            words.count { it.text.isNotBlank() } >= 2 &&
                dominantScript(entry.text).let { it !in UnspacedScripts && it != Script.UNKNOWN }
        }
        if (lines.size < 3) return false
        val glued = lines.filter { it.text.none(Char::isWhitespace) }
        if (glued.size * 5 < lines.size * 4) return false
        val pieces = glued.sumOf { line -> line.words!!.count { it.text.isNotBlank() } }
        return pieces >= glued.size * 3
    }

    private const val NoSpaceBefore = ",.!?;:)]}…"
    private const val NoSpaceAfter = "([{"
    private val ContractionPiece = Regex("^['’](s|t|re|ll|m|ve|d)$", RegexOption.IGNORE_CASE)

    /** Timed words as a line of text: a space between each, but not before punctuation. */
    private fun spacedText(words: List<WordTimestamp>): String = buildString {
        words.map { it.text.trim() }.filter { it.isNotEmpty() }.forEach { piece ->
            val attached = piece.first() in NoSpaceBefore || ContractionPiece.matches(piece)
            if (isNotEmpty() && !attached && last() !in NoSpaceAfter) append(' ')
            append(piece)
        }
    }

    /**
     * [words] as whole words of [text]: consecutive pieces with nothing between them in the text
     * become one word, timed from the first piece's start to the last one's end. Blank pieces (a
     * timed space) drop out. A piece that can't be found in the text is left as it is.
     */
    private fun mergeWordPieces(text: String, words: List<WordTimestamp>): List<WordTimestamp> {
        val pieces = words.filter { it.text.isNotBlank() }
        val ranges = wordCharRanges(text, pieces)
        val merged = ArrayList<WordTimestamp>(pieces.size)
        var lastEnd = -1
        pieces.forEachIndexed { index, piece ->
            val range = ranges[index]
            val previous = merged.lastOrNull()
            val glued = !range.isEmpty() && range.first == lastEnd &&
                scriptOf(text[lastEnd - 1]) !in UnspacedScripts &&
                scriptOf(text[range.first]) !in UnspacedScripts
            if (previous != null && glued) {
                merged[merged.lastIndex] = WordTimestamp(
                    text = previous.text + piece.text.trim(),
                    startTime = previous.startTime,
                    endTime = maxOf(previous.endTime, piece.endTime),
                )
            } else {
                merged += WordTimestamp(piece.text.trim(), piece.startTime, piece.endTime)
            }
            lastEnd = if (range.isEmpty()) -1 else range.last + 1
        }
        return merged
    }


    private fun parseRichSyncLyrics(lines: List<String>): List<LyricsEntry> {
        val result = mutableListOf<LyricsEntry>()

        lines.forEachIndexed { index, line ->
            val matchResult = RICH_SYNC_LINE_REGEX.matchEntire(line.trim())
            if (matchResult != null) {
                val minutes = matchResult.groupValues[1].toLongOrNull() ?: 0L
                val seconds = matchResult.groupValues[2].toLongOrNull() ?: 0L
                val centiseconds = matchResult.groupValues[3].toLongOrNull() ?: 0L

                val millisPart = if (matchResult.groupValues[3].length == 3) centiseconds else centiseconds * 10
                val lineTimeMs = minutes * DateUtils.MINUTE_IN_MILLIS + seconds * DateUtils.SECOND_IN_MILLIS + millisPart

                var content = matchResult.groupValues[4].trimStart()

                val agentMatch = AGENT_REGEX.find(content)
                val agent = agentMatch?.groupValues?.get(1)
                if (agentMatch != null) {
                    content = content.replaceFirst(AGENT_REGEX, "")
                }

                val isBackground = BACKGROUND_REGEX.containsMatchIn(content)
                if (isBackground) {
                    content = content.replaceFirst(BACKGROUND_REGEX, "")
                }

                val wordTimings = parseRichSyncWords(content, index, lines)

                // Strip the word tags and nothing else. The old pattern also ate the whitespace
                // after each tag, and some sources carry the gap between words as its own timed
                // piece ("<t>I<t> <t>love<t> <t>you") or put it after the tag ("<t> love"), so the
                // spaces went with the tags and the line read "Iloveyou".
                val plainText = repairRichSyncPlainText(
                    content.replace(RICH_SYNC_TAG_REGEX, "").trim()
                )

                if (plainText.isNotBlank()) {
                    result.add(LyricsEntry(lineTimeMs, plainText, wordTimings, agent = agent, isBackground = isBackground))
                }
            }
        }

        return result.sorted()
    }

    /**
     * Undoes the old syllable-split cache format ("wa nt  yo u": syllables single-spaced, words
     * double-spaced) and otherwise only normalises whitespace.
     *
     * The rejoin used to fire on ANY double space in a line, and it glues together every run of
     * short words, so one stray double space turned "I love you  baby" into "Iloveyou baby". It
     * now needs the line to actually look syllable-split: most double-spaced groups have to be
     * made of several pieces. Real text with an odd double space has mostly single-word groups.
     */
    internal fun repairRichSyncPlainText(text: String): String {
        val compacted = text.replace(Regex("[\\t\\u00A0]+"), " ").trim()
        if (compacted.isBlank()) return compacted
        if (!Regex(" {2,}").containsMatchIn(compacted)) {
            return compacted.replace(Regex(" +"), " ")
        }

        val groups = compacted.split(Regex(" {2,}")).filter { it.isNotBlank() }
        val multiPiece = groups.count { it.trim().contains(' ') }
        if (multiPiece * 2 <= groups.size) {
            return compacted.replace(Regex(" +"), " ")
        }

        return groups
            .joinToString(" ") { wordGroup ->
                val parts = wordGroup.trim().split(Regex(" +")).filter { it.isNotBlank() }
                if (parts.size > 1 && parts.all { part -> part.length <= 4 && part.any(Char::isLetter) }) {
                    parts.joinToString("")
                } else {
                    wordGroup.trim().replace(Regex(" +"), " ")
                }
            }
    }

    private fun parseRichSyncWords(content: String, currentIndex: Int, allLines: List<String>): List<WordTimestamp>? {
        val wordMatches = RICH_SYNC_WORD_REGEX.findAll(content).toList()

        if (wordMatches.isEmpty()) return null

        val wordTimings = mutableListOf<WordTimestamp>()

        wordMatches.forEachIndexed { index, match ->
            val minutes = match.groupValues[1].toLongOrNull() ?: 0L
            val seconds = match.groupValues[2].toLongOrNull() ?: 0L
            val fraction = match.groupValues[3].toLongOrNull() ?: 0L

            val fractionPart = if (match.groupValues[3].length == 3) fraction / 1000.0 else fraction / 100.0
            val startTimeSeconds = minutes * 60.0 + seconds + fractionPart

            val wordText = match.groupValues[4].trim()

            val endTimeSeconds = if (index < wordMatches.size - 1) {
                val nextMatch = wordMatches[index + 1]
                val nextMinutes = nextMatch.groupValues[1].toLongOrNull() ?: 0L
                val nextSeconds = nextMatch.groupValues[2].toLongOrNull() ?: 0L
                val nextFraction = nextMatch.groupValues[3].toLongOrNull() ?: 0L
                val nextFractionPart = if (nextMatch.groupValues[3].length == 3) nextFraction / 1000.0 else nextFraction / 100.0
                nextMinutes * 60.0 + nextSeconds + nextFractionPart
            } else {

                val nextLineTime = getNextLineStartTime(currentIndex, allLines)
                nextLineTime ?: (startTimeSeconds + 0.5)
            }

            if (wordText.isNotBlank()) {
                wordTimings.add(WordTimestamp(wordText, startTimeSeconds, endTimeSeconds))
            }
        }

        return if (wordTimings.isNotEmpty()) wordTimings else null
    }

    private fun getNextLineStartTime(currentIndex: Int, allLines: List<String>): Double? {
        if (currentIndex + 1 >= allLines.size) return null

        val nextLine = allLines[currentIndex + 1].trim()
        val matchResult = RICH_SYNC_LINE_REGEX.matchEntire(nextLine) ?: return null

        val minutes = matchResult.groupValues[1].toLongOrNull() ?: return null
        val seconds = matchResult.groupValues[2].toLongOrNull() ?: return null
        val fraction = matchResult.groupValues[3].toLongOrNull() ?: 0L

        val fractionPart = if (matchResult.groupValues[3].length == 3) fraction / 1000.0 else fraction / 100.0
        return minutes * 60.0 + seconds + fractionPart
    }

    private fun parseStandardLyrics(lines: List<String>): List<LyricsEntry> {
        val result = mutableListOf<LyricsEntry>()

        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            if (!line.trim().startsWith("<") || !line.trim().endsWith(">")) {
                val entries = parseLine(line, null)
                if (entries != null) {
                    val wordTimestamps = if (i + 1 < lines.size) {
                        val nextLine = lines[i + 1]
                        if (nextLine.trim().startsWith("<") && nextLine.trim().endsWith(">")) {
                            parseWordTimestamps(nextLine.trim().removeSurrounding("<", ">"))
                        } else null
                    } else null

                    if (wordTimestamps != null) {
                        result.addAll(entries.map { entry ->
                            LyricsEntry(entry.time, entry.text, wordTimestamps, agent = entry.agent, isBackground = entry.isBackground)
                        })
                    } else {
                        result.addAll(entries)
                    }
                }
            }
            i++
        }
        return result.sorted()
    }

    private fun parseWordTimestamps(data: String): List<WordTimestamp>? {
        if (data.isBlank()) return null
        return try {
            data.split("|").mapNotNull { wordData ->
                val parts = wordData.split(":")
                if (parts.size == 3) {
                    WordTimestamp(
                        text = parts[0],
                        startTime = parts[1].toDouble(),
                        endTime = parts[2].toDouble()
                    )
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun parseLine(line: String, words: List<WordTimestamp>? = null): List<LyricsEntry>? {
        if (line.isEmpty()) {
            return null
        }
        val matchResult = LINE_REGEX.matchEntire(line.trim()) ?: return null
        val times = matchResult.groupValues[1]
        var text = matchResult.groupValues[3]
        val timeMatchResults = TIME_REGEX.findAll(times)

        val agentMatch = AGENT_REGEX.find(text)
        val agent = agentMatch?.groupValues?.get(1)
        if (agentMatch != null) {
            text = text.replaceFirst(AGENT_REGEX, "")
        }

        val isBackground = BACKGROUND_REGEX.containsMatchIn(text)
        if (isBackground) {
            text = text.replaceFirst(BACKGROUND_REGEX, "")
        }

        return timeMatchResults
            .map { timeMatchResult ->
                val min = timeMatchResult.groupValues[1].toLong()
                val sec = timeMatchResult.groupValues[2].toLong()
                val milString = timeMatchResult.groupValues[3]
                var mil = milString.toLong()
                if (milString.length == 2) {
                    mil *= 10
                }
                val time = min * DateUtils.MINUTE_IN_MILLIS + sec * DateUtils.SECOND_IN_MILLIS + mil
                LyricsEntry(time, text, words, agent = agent, isBackground = isBackground)
            }.toList()
    }

    // ---------------------------------------------------------------------------------------
    // Provider result grading
    //
    // These exist because provider selection used to accept a result on
    // `contains("<") && contains(">") && contains(":")`, which is true of any LRC file with an
    // emoticon in it and false of several genuinely word-synced formats. Grading a result means
    // parsing it once and looking at what actually came out.
    // ---------------------------------------------------------------------------------------

    /**
     * Whether parsed [lines] carry no usable timing: several lines, no word timings, and every
     * line on the same timestamp. Some providers hand back unsynced lyrics as LRC with each line
     * stamped [00:00.00]; played as synced, the whole song counted as "already sung" a few seconds
     * in and every line sat blurred and dimmed.
     */
    fun isUnsynced(lines: List<LyricsEntry>): Boolean {
        if (lines.size < 2) return false
        if (lines.any { !it.words.isNullOrEmpty() }) return false
        val first = lines.first().time
        return lines.all { it.time == first }
    }

    private val LRC_TIME_TAG_REGEX = "\\[\\d{1,2}:\\d{2}(?:[.:]\\d{1,3})?\\]".toRegex()
    private val LRC_META_LINE_REGEX = "^\\[[a-zA-Z#]+:.*\\]$".toRegex()

    /**
     * Plain lyrics with no timing at all, one entry per non-blank line, all at time 0. Used for
     * display only, where [parseLyrics] would drop every line for lacking a timestamp.
     */
    fun parsePlainLyrics(lyrics: String): List<LyricsEntry> =
        decodeHtmlEntities(lyrics.trim().replace("\\n", "\n").replace("\\r", "\r"))
            .lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !LRC_META_LINE_REGEX.matches(it) }
            .map { it.replace(LRC_TIME_TAG_REGEX, "").replace(RICH_SYNC_TAG_REGEX, "").trim() }
            .filter { it.isNotEmpty() }
            .map { LyricsEntry(0L, it) }

    /** How finely a lyrics payload is timed. Ordered worst to best; compared by ordinal. */
    enum class SyncKind { NONE, PLAIN, LINE, WORD }

    fun syncKind(rawLyrics: String): SyncKind {
        if (rawLyrics.isBlank() || rawLyrics == "LYRICS_NOT_FOUND") return SyncKind.NONE
        val entries = try {
            parseLyrics(rawLyrics)
        } catch (_: Exception) {
            return SyncKind.NONE
        }
        if (entries.isEmpty()) {
            // Parsed to nothing but has text in it — unsynced plain lyrics.
            return if (rawLyrics.any(Char::isLetter)) SyncKind.PLAIN else SyncKind.NONE
        }
        // Every line on the same stamp (usually [00:00.00]) is plain text wearing LRC syntax.
        // Grading it as line-synced let it beat genuinely timed lyrics.
        if (isUnsynced(entries)) return SyncKind.PLAIN
        // One word timing on one line is noise; a genuinely word-synced payload has them
        // throughout. Requiring half the lines keeps a stray <00:12.34> from promoting a
        // line-synced file.
        // counted on the pieces as sent: a one-word line sung in two syllables is still word-timed
        val wordSynced = entries.count { ((it.syllables ?: it.words)?.size ?: 0) > 1 }
        if (wordSynced * 2 >= entries.size && wordSynced > 0) return SyncKind.WORD
        return SyncKind.LINE
    }

    /**
     * Writing systems we care about telling apart when deciding whether a provider handed back
     * the song or handed back a translation of it.
     */
    enum class Script { LATIN, HAN, KANA, HANGUL, CYRILLIC, ARABIC, DEVANAGARI, THAI, GREEK, HEBREW, UNKNOWN }

    private fun scriptOf(ch: Char): Script? {
        if (!ch.isLetter()) return null
        return when (ch.code) {
            in 0x0041..0x024F -> Script.LATIN
            in 0x0370..0x03FF -> Script.GREEK
            in 0x0400..0x052F -> Script.CYRILLIC
            in 0x0590..0x05FF -> Script.HEBREW
            in 0x0600..0x06FF, in 0x0750..0x077F -> Script.ARABIC
            in 0x0900..0x097F -> Script.DEVANAGARI
            in 0x0E00..0x0E7F -> Script.THAI
            in 0x3040..0x30FF -> Script.KANA
            in 0xAC00..0xD7AF, in 0x1100..0x11FF -> Script.HANGUL
            in 0x3400..0x4DBF, in 0x4E00..0x9FFF, in 0xF900..0xFAFF -> Script.HAN
            else -> null
        }
    }

    /**
     * The script most of [text] is written in.
     *
     * Kana wins over Han whenever any kana is present: Japanese is mostly kanji by character count
     * but the presence of kana is decisive, and calling a Japanese song "Chinese" sends it to the
     * pinyin romanizer, which is exactly the "why is this in Chinese" symptom.
     */
    fun dominantScript(text: String): Script {
        val counts = HashMap<Script, Int>()
        var total = 0
        for (ch in text) {
            val s = scriptOf(ch) ?: continue
            counts[s] = (counts[s] ?: 0) + 1
            total++
        }
        if (total == 0) return Script.UNKNOWN
        if ((counts[Script.KANA] ?: 0) > 0) return Script.KANA
        if ((counts[Script.HANGUL] ?: 0) * 4 >= total) return Script.HANGUL
        return counts.maxByOrNull { it.value }?.key ?: Script.UNKNOWN
    }

    // the script the readings are for: kana settles it; otherwise the commonest script that isn't latin, as long as a tenth of the letters are in it, so a korean song with english verses still reads as korean instead of losing its readings

    /** Whether a payload carries `{agent:…}` voice tags — i.e. can drive left/right duet layout. */
    fun hasVoiceTags(rawLyrics: String): Boolean = rawLyrics.contains("{agent:")

    /**
     * Whether a payload would actually lay out on both sides: two or more voices among its lead
     * lines, counting untagged lines as the lead voice. Same rule as the lyrics screen. A provider
     * that tags every line {agent:v1} has voice tags but nothing to put on the right.
     */
    fun hasDistinctVoices(rawLyrics: String): Boolean {
        if (!hasVoiceTags(rawLyrics)) return false
        val lines = try {
            parseLyrics(rawLyrics)
        } catch (_: Exception) {
            return false
        }
        return lines.filter { !it.isBackground }.map { it.agent ?: "v1" }.distinct().size > 1
    }

    /**
     * Character range each timed word occupies inside [text].
     *
     * Word timings arrive as bare strings with no offsets, so the position has to be recovered by
     * walking the line. Mirrors the same walk the lyrics screen's glow line does
     * to build its karaoke mask, so the romanisation lines up with the sweep rather than drifting
     * out of step with it. A word that cannot be located yields an empty range and is skipped.
     */
    fun wordCharRanges(text: String, words: List<WordTimestamp>): List<IntRange> {
        var searchFrom = 0
        return words.map { word ->
            val trimmed = word.text.trim()
            if (trimmed.isEmpty()) return@map IntRange.EMPTY
            val start = text.indexOf(trimmed, searchFrom)
            if (start < 0) return@map IntRange.EMPTY
            searchFrom = start + trimmed.length
            start until searchFrom
        }
    }

    // ---- Line-index helpers (verbatim from Mochy LyricsUtils.kt:762-800) ----
    // Added at coordinator merge: the lyrics chunk's stub returned -1/0L for
    // these, which would have broken the active-line highlight everywhere.
    // Pure functions over the parsed lines — no engine involved.

    /**
     * How early a line lights up before its first word is sung, in ms. Small
     * enough to feel instant, large enough that the highlight never lags the
     * voice on a slow tick; it is a named constant rather than a literal
     * buried in the loop.
     */
    const val LineLeadInMs = 300L

    /**
     * Index of the line that should read as ACTIVE at [position], or -1 before the first line.
     *
     * Returns [lines].size — one past the end — once playback has run past the final line's own
     * end, so callers can tell "the last line is being sung" apart from "the lyrics are over".
     * Without that distinction the last line stayed lit through the entire outro.
     */
    fun findCurrentLineIndex(
        lines: List<LyricsEntry>,
        position: Long,
    ): Int {
        if (lines.isEmpty()) return -1
        if (position >= lyricsEndMs(lines)) return lines.size
        for (index in lines.indices) {
            if (lines[index].time >= position + LineLeadInMs) {
                return index - 1
            }
        }
        return lines.lastIndex
    }

    /**
     * When a line stops being sung: the end of its last word if the line is word-synced, otherwise
     * the start of the next line, otherwise a plain-LRC guess.
     */
    fun lineEndMs(lines: List<LyricsEntry>, index: Int): Long {
        val entry = lines.getOrNull(index) ?: return 0L
        entry.words?.lastOrNull()?.let { return (it.endTime * 1000).toLong() }
        lines.getOrNull(index + 1)?.let { return it.time }
        return entry.time + UntimedLineDurationMs
    }

    /** Where the lyrics as a whole finish. */
    fun lyricsEndMs(lines: List<LyricsEntry>): Long {
        if (lines.isEmpty()) return 0L
        return lineEndMs(lines, lines.lastIndex)
    }

    /** Fallback duration for a line with no word timings and no successor. */
    private const val UntimedLineDurationMs = 5_000L

    // ---- Romanisation (readings / ruby) — STUBBED, from the lyrics chunk ----
    // Moved here at coordinator merge (the lyrics chunk's LyricsUtils stub is
    // deleted): LyricsReadings.kt calls these, and the real Mochy romanisers
    // (kana/hangul/devanagari maps) are a lyrics-engine item, not a UI port.
    // readingLine is the one real function — pure, verbatim from Mochy.

    // High-frequency lookups warn once, not per call.
    private val warned = mutableSetOf<String>()

    private fun warnOnce(name: String) {
        if (warned.add(name)) Timber.w("LyricsUtils: $name not available (no lyrics engine yet)")
    }

    /** Which script [text] is written in, for picking a romaniser. TODO(lyrics-engine). */
    fun romanizableScript(text: String): Script {
        warnOnce("romanizableScript")
        return Script.UNKNOWN
    }

    fun isUkrainian(text: String): Boolean = false
    fun isSerbian(text: String): Boolean = false
    fun isBulgarian(text: String): Boolean = false
    fun isBelarusian(text: String): Boolean = false
    fun isKyrgyz(text: String): Boolean = false
    fun isMacedonian(text: String): Boolean = false
    fun isPunjabi(text: String): Boolean = false

    /** Romanise [text]; returns the input unchanged until the engine lands. TODO(lyrics-engine). */
    suspend fun romanizeJapanese(text: String): String {
        warnOnce("romanizeJapanese")
        return text
    }

    suspend fun romanizeKorean(text: String): String {
        warnOnce("romanizeKorean")
        return text
    }

    suspend fun romanizeChinese(text: String): String {
        warnOnce("romanizeChinese")
        return text
    }

    suspend fun romanizeCyrillic(text: String): String? {
        warnOnce("romanizeCyrillic")
        return null
    }

    suspend fun romanizeHindi(text: String): String {
        warnOnce("romanizeHindi")
        return text
    }

    suspend fun romanizePunjabi(text: String): String {
        warnOnce("romanizePunjabi")
        return text
    }

    /** Per-word readings over [text], furigana style. TODO(lyrics-engine). */
    suspend fun rubyFor(text: String, script: Script): List<RubyToken>? {
        warnOnce("rubyFor")
        return null
    }

    /**
     * The whole line's reading, from its ruby tokens. Pure function, ported verbatim from
     * Mochy — it needs no engine, only the tokens [rubyFor] would have produced.
     */
    fun readingLine(text: String, ruby: List<RubyToken>): String = buildString {
        var at = 0
        ruby.sortedBy { it.start }.forEach { token ->
            if (token.start > at) append(text, at, token.start.coerceAtMost(text.length))
            if (isNotEmpty() && !last().isWhitespace()) append(' ')
            append(token.text)
            at = maxOf(at, token.end)
        }
        if (at < text.length) append(text, at, text.length)
    }.trim()

    /** The line's timed pieces said in romanisation. TODO(lyrics-engine). */
    suspend fun romanizedWords(
        text: String,
        pieces: List<WordTimestamp>,
        ruby: List<RubyToken>?,
        romanizeWord: suspend (String) -> String?,
    ): List<WordTimestamp>? {
        warnOnce("romanizedWords")
        return null
    }

}