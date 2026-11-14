// Ported from Mochy Music (GPL-3.0): ui/player/share/song/ShareReadings.kt
// ShareReadings.kt

package com.music.bitchord.ui.mochy.player.share.song

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalContext
import com.music.bitchord.ui.mochy.constants.LyricsRomanizeAsMainKey
import com.music.bitchord.ui.mochy.constants.TranslateLanguageKey
import com.music.bitchord.ui.mochy.constants.TranslateSourceLanguageKey
import com.music.bitchord.ui.mochy.lyrics.LyricsEntry
import com.music.bitchord.ui.mochy.lyrics.LyricsTranslationHelper
import com.music.bitchord.ui.mochy.player.LineReading
import com.music.bitchord.ui.mochy.player.readSong
import com.music.bitchord.ui.mochy.player.romanizeSwitchFor
import com.music.bitchord.ui.mochy.utils.dataStore
import com.music.bitchord.ui.mochy.utils.get
import com.music.bitchord.ui.mochy.utils.rememberPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext
import timber.log.Timber

// the share's own reading switches; they start the way the app has them and only ever change this share
@Immutable
internal data class ShareReadingChoice(val romanize: Boolean = true, val asMain: Boolean = false, val translate: Boolean = false)

// a lyric line as the share draws it: the line that sings, and what sits under it
@Immutable
internal class ShareLine(val entry: LyricsEntry, val under: List<String>)

// romanisation as main and translation as the lyrics page has them right now; romanisation waits for the song's language
internal fun startingReadingChoice(context: Context) = ShareReadingChoice(
    romanize = true,
    asMain = context.dataStore.get(LyricsRomanizeAsMainKey, false),
    translate = LyricsTranslationHelper.translationOn.value,
)

// the card's line: the original with its reading and translation under it, or the romanisation singing with the original under it
internal fun SongShareSession.cardLine(entry: LyricsEntry): ShareLine {
    val choice = readingChoice
    val reading = lineReadings[entry]?.takeIf { choice.romanize }
    val readingText = reading?.text
    val translation = lineTranslations[entry]?.takeIf { choice.translate }
    if (choice.asMain && readingText != null) {
        // the romanised pieces carry the timing, so the line still sings word by word in a video
        return ShareLine(entry.copy(text = readingText, words = reading?.words, syllables = null), listOfNotNull(entry.text.trim(), translation))
    }
    return ShareLine(entry, listOfNotNull(readingText, translation))
}

// the cinematic words stay the original, since each of them can be styled, so the reading always goes under them
internal fun SongShareSession.cinematicUnder(entry: LyricsEntry): List<String> = listOfNotNull(
    lineReadings[entry]?.text?.takeIf { readingChoice.romanize },
    lineTranslations[entry]?.takeIf { readingChoice.translate },
)

// works out the share's own readings and translation for its lines, so the lyrics page's switches and lines are left alone
@Composable
internal fun ShareLyricReadings(session: SongShareSession) {
    val lines = session.lyrics.found ?: return
    val context = LocalContext.current
    LaunchedEffect(lines) {
        val switch = withContext(Dispatchers.Default) { romanizeSwitchFor(lines) }
        // romanised to start with when the lyrics page romanises this language
        if (!session.readingChoiceTouched) {
            session.readingChoice = session.readingChoice.copy(romanize = switch != null && context.dataStore.get(switch, true))
        }
        val found = HashMap<LyricsEntry, LineReading>()
        readSong(lines, allowed = { true }) { index, reading ->
            if (reading?.text != null) found[lines[index]] = reading
        }
        session.lineReadings = found
    }
    val (target) = rememberPreference(TranslateLanguageKey, defaultValue = "en")
    val (source) = rememberPreference(TranslateSourceLanguageKey, defaultValue = "auto")
    LaunchedEffect(lines, target, source) {
        session.lineTranslations = emptyMap()
        // only fetched once the switch is on, and kept after, so switching it off and on again is instant
        snapshotFlow { session.readingChoice.translate }.collectLatest { on ->
            if (on && session.lineTranslations.isEmpty()) {
                session.lineTranslations = translateForShare(lines, session.song.id, target.ifBlank { "en" }, source)
            }
        }
    }
}

// the lines' translation: the one the lyrics page already shows, the one saved with the song, or a fresh one just for the share
//
// PARTIAL (phase3-adapter, NEEDS.md §(b) L3/L4): the first branch is real —
// a translation the lyrics page already shows is reused. The stored
// translation needs the lyrics store (L3) and a fresh translation needs the
// Google translate engine (L4); both are stubs below, so until the adapter
// wires them a share whose lyrics page shows no translation gets none.
private suspend fun translateForShare(
    lines: List<LyricsEntry>,
    songId: String,
    target: String,
    source: String,
): Map<LyricsEntry, String> {
    val shown = lines.mapNotNull { entry -> entry.translatedTextFlow.value?.takeIf { it.isNotBlank() }?.let { entry to it } }.toMap()
    if (shown.isNotEmpty()) return shown
    val sung = lines.filter { it.text.isNotBlank() }
    if (sung.isEmpty()) return emptyMap()
    storedTranslation(sung, songId, target, source)?.let { return it }
    // google needs no account, and nothing is saved, so the share never changes what the lyrics page has stored
    return freshTranslation(sung, target, source)
}

// a fresh translation just for the share, through Google Translate.
//
// STUB (phase3-adapter, NEEDS.md §(b) L4): Mochy called
// GoogleTranslateEngine.translate(texts, target, source) and dropped lines
// that came back identical to the original (already in the target language).
// No translate engine is wired yet, so this returns nothing.
private suspend fun freshTranslation(
    sung: List<LyricsEntry>,
    target: String,
    source: String,
): Map<LyricsEntry, String> {
    // TODO(phase3-adapter): translate sung.map { it.text } to `target` from
    // `source` (NEEDS.md §(b) L4) and map the results back onto `sung`,
    // dropping lines that come back identical to the original.
    Timber.w("SongShare: no translate engine yet; share translation unavailable (target=%s)", target)
    return emptyMap()
}

// the translation saved with the song, when it's in the language asked for; one row per sung line
//
// STUB (phase3-adapter, NEEDS.md §(b) L3): Mochy read the stored lyrics row,
// checked its translationLanguage against the target language's tags
// (LyricsTranslationHelper.languageTag(source, target) and
// YouTubeCaptionTranslation.storedLanguageTag(target)), and mapped its
// translatedLyrics lines onto the sung lines. No lyrics store is wired yet.
private suspend fun storedTranslation(
    sung: List<LyricsEntry>,
    songId: String,
    target: String,
    source: String,
): Map<LyricsEntry, String>? {
    // TODO(phase3-adapter): read the stored translation for songId (NEEDS.md
    // §(b) L3).
    Timber.w("SongShare: no lyrics store yet; no stored translation for %s", songId)
    return null
}
