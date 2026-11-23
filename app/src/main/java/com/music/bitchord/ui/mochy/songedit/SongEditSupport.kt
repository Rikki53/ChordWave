// SongEditSupport.kt — small types + helpers the song edit sheets need,
// gathered from Mochy (GPL-3.0) into one staged file:
// - EditedArtist: db/entities/SongEdit.kt (verbatim, incl. JSON codec)
// - CoverPick / SongEditDraft: songedit/SongEditSaver.kt (verbatim)
// - CoverOption: songedit/CoverSources.kt (verbatim)
// - PickedLyrics / LyricsFileReader: songedit/LyricsFileReader.kt (verbatim)
// - CoverSources: YouTube Music cover lookup (Mochy also had Apple Music,
//   Deezer and modules — see the note above the object)
// - SongEdit/SongEdits: coordinator's store (models/SongEdit.kt +
//   songedit/SongEdits.kt, phase3-coordinator) — the minimal stub that was
//   here was deleted at merge; see the note above CoverSources.
// - generateEditedArtistId: stands in for ArtistEntity.generateArtistId()
//   (Mochy db entity; CW has no artist id generator — NEEDS.md §(b) E5)

package com.music.bitchord.ui.mochy.songedit

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.Immutable
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.model.SearchFilter
import com.music.bitchord.data.model.SearchResult
import com.music.bitchord.ui.mochy.lyrics.LyricsUtils
import java.io.ByteArrayOutputStream
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import timber.log.Timber

@Immutable
@Serializable
data class EditedArtist(val id: String, val name: String, val thumbnail: String? = null) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        private val serializer = ListSerializer(serializer())

        fun encode(artists: List<EditedArtist>): String = json.encodeToString(serializer, artists)

        // a broken row reads as no edit rather than taking the song's artists away
        fun decode(text: String): List<EditedArtist>? =
            runCatching { json.decodeFromString(serializer, text) }.getOrNull()?.takeIf { it.isNotEmpty() }
    }
}

// the cover picked in the carousel: a provider's link, or a picture from the phone that still has to be copied in
sealed interface CoverPick {
    data class Link(val url: String) : CoverPick
    data class Picture(val uri: Uri) : CoverPick
}

// one cover the edit sheet can offer, with the name of where it came from
@Immutable
data class CoverOption(val source: String, val url: String)

// everything the edit sheet hands over when ok is pressed; a null means that part wasn't touched
data class SongEditDraft(
    val title: String,
    val cover: CoverPick?,
    val artists: List<EditedArtist>,
    val album: String,
    val year: Int?,
    val lyricsOffsetMs: Int,
    val speed: Float?,
    val eqPresetId: String?,
    val customLyrics: String?,
)

/**
 * A song's hand-made edits, as the sheets read them. MINIMAL stand-in for
 * Mochy's db.entities.SongEdit (a Room entity): only the video choice is
 * read here. The adapter round replaces this with the real store —
 * NEEDS.md §(b) E4.
 */
// SongEdit + SongEdits live in the coordinator's store
// (ui.mochy.models.SongEdit + ui.mochy.songedit.SongEdits, phase3-coordinator):
// the full Mochy entity (title/artists/album/year/cover/videoId/noVideo) with
// a persisted put()/remove() store (SharedPreferences JSON). The minimal stub
// that was here (videoId + noVideo only, always empty) was deleted at merge —
// call sites only read videoId/noVideo, which the full entity carries.

// Mochy's CoverSources looked a song's cover up on YouTube Music, Apple
// Music, Deezer and switched-on modules. CW wires YouTube Music only:
// Apple's CW API returns canvas videos, not artwork; Deezer has no CW
// engine; modules are out of scope. YouTube Music is the source that
// matters — its thumbnails are the album art.
object CoverSources {
    // a few is enough to catch the single and the album versions without a wall of the same picture
    private const val PerSource = 3

    fun search(title: String, artist: String, album: String?, durationMs: Long?): Flow<CoverOption> = channelFlow {
        if (title.isBlank()) return@channelFlow
        val match = Match(title, artist)
        val songs = safely {
            YtMusicRepository.search("$title $artist".trim(), SearchFilter.SONGS).getOrNull().orEmpty()
                .mapNotNull { result ->
                    when (result) {
                        is SearchResult.Track -> result.song
                        is SearchResult.TopTrack -> result.song
                        else -> null
                    }
                }
                .filterNot { it.isVideo }
        }.orEmpty()
        songs
            .sortedByDescending { match.score(it.title, it.artist) }
            .mapNotNull { it.thumbnailUrl?.takeIf(String::isNotBlank) }
            .distinct()
            .take(PerSource)
            .forEach { send(CoverOption("YouTube Music", it)) }
    }.flowOn(Dispatchers.IO)

    // a source that breaks (blocked, changed, offline) is left out instead of taking the others down
    private suspend fun <T> safely(block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Timber.tag("CoverSources").w(e, "A cover source failed")
        null
    }

    // how close a result is to the song: same title and artist first, then either one
    private class Match(title: String, artist: String) {
        private val title = normalizeTitle(title)
        private val artist = normalizeArtist(artist)

        fun score(candidateTitle: String, candidateArtist: String): Int {
            val titleHit = close(normalizeTitle(candidateTitle), title)
            val artistHit = close(normalizeArtist(candidateArtist), artist)
            return (if (titleHit) 2 else 0) + (if (artistHit) 1 else 0)
        }

        private fun close(a: String, b: String) =
            a.isNotEmpty() && b.isNotEmpty() && (a.contains(b, ignoreCase = true) || b.contains(a, ignoreCase = true))

        // bracketed tags and featured artists off the title; the first artist only
        private fun normalizeTitle(raw: String) = raw
            .replace(Regex("\\s*\\[[^]]*]"), "")
            .replace(Regex("\\s*\\((?:feat\\.?|ft\\.?|featuring|with)\\b[^)]*\\)", RegexOption.IGNORE_CASE), "")
            .trim()

        private fun normalizeArtist(raw: String) = raw
            .split(
                Regex("(?:\\s*,\\s*|\\s*&\\s*|\\s+×\\s+|\\s+x\\s+|\\bfeat\\.?\\b|\\bft\\.?\\b|\\bfeaturing\\b|\\bwith\\b)", RegexOption.IGNORE_CASE),
                limit = 2,
            )
            .first().trim()
    }
}

// Mochy made typed-in artists with ArtistEntity.generateArtistId() (a db
// entity helper). CW has no artist table; a UUID-based id keeps the
// typed artist unique inside the draft. NEEDS.md §(b) E5.
fun generateEditedArtistId(): String = "local-artist-" + UUID.randomUUID().toString()

// a lyrics file someone picked, already turned into the text the lyrics table keeps
@Immutable
data class PickedLyrics(val fileName: String, val text: String, val lines: Int)

object LyricsFileReader {
    // a lyrics file is a few kilobytes; anything past this is the wrong file
    private const val MaxBytes = 1_000_000

    private val TtmlLine = Regex("<p\\b([^>]*)\\bbegin=\"([^\"]+)\"([^>]*)>(.*?)</p>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val Tag = Regex("<[^>]+>")
    private val AnyTag = Regex("<(/?)([a-zA-Z][\\w:.-]*)\\b([^>]*?)(/?)>")
    private val BeginAttr = Regex("\\bbegin=\"([^\"]+)\"")
    private val AgentAttr = Regex("ttm:agent=\"([^\"]+)\"")
    private val BackgroundRole = Regex("ttm:role=\"x-bg\"")
    private val SrtTime = Regex("(\\d+):(\\d+):(\\d+)[,.](\\d+)\\s*-->")

    // lrc and plain text stay as they are, ttml and srt become lrc lines; null when nothing in it reads as lyrics
    suspend fun read(context: Context, uri: Uri): PickedLyrics? = withContext(Dispatchers.IO) {
        try {
            val raw = readText(context, uri) ?: return@withContext null
            val lyrics = when {
                raw.contains("<tt", ignoreCase = true) -> fromTtml(raw)
                SrtTime.containsMatchIn(raw) -> fromSrt(raw)
                else -> raw.trim()
            }
            if (LyricsUtils.syncKind(lyrics) == LyricsUtils.SyncKind.NONE) return@withContext null
            PickedLyrics(fileName(context, uri), lyrics, LyricsUtils.parseLyrics(lyrics).size)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    private fun readText(context: Context, uri: Uri): String? {
        val input = context.contentResolver.openInputStream(uri) ?: return null
        val out = ByteArrayOutputStream()
        input.use {
            val buffer = ByteArray(8 * 1024)
            while (true) {
                val read = it.read(buffer)
                if (read < 0) break
                if (out.size() + read > MaxBytes) return null
                out.write(buffer, 0, read)
            }
        }
        return out.toString(Charsets.UTF_8.name()).removePrefix("﻿")
    }

    private fun fileName(context: Context, uri: Uri): String =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        } ?: uri.lastPathSegment.orEmpty()

    // each timed <p> becomes an lrc line; timed <span>s inside it keep their word timing as a rich sync line, the singer's agent and a background part come along too
    private fun fromTtml(raw: String): String = TtmlLine.findAll(raw).flatMap { match ->
        val ms = ttmlTime(match.groupValues[2]) ?: return@flatMap emptySequence()
        val attrs = match.groupValues[1] + match.groupValues[3]
        val agent = AgentAttr.find(attrs)?.groupValues?.get(1)?.let { "{agent:$it}" }.orEmpty()
        val inner = match.groupValues[4]
        val words = timedWords(inner)
        if (words.isEmpty()) {
            val text = inner
                .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), " ")
                .replace(Tag, "")
                .replace(Regex("\\s+"), " ")
                .trim()
            return@flatMap if (text.isEmpty()) emptySequence() else sequenceOf("${lrcTime(ms)}$agent$text")
        }
        val lead = words.filter { !it.background }
        val backing = words.filter { it.background }
        sequenceOf(
            lead.takeIf { it.isNotEmpty() }?.let { "${lrcTime(ms)}$agent${richSync(it)}" },
            // the background part sings as its own line, from its first word
            backing.takeIf { it.isNotEmpty() }?.let { "${lrcTime(it.first().beginMs)}$agent{bg}${richSync(it)}" },
        ).filterNotNull()
    }.joinToString("\n")

    private class TtmlWord(val text: String, val beginMs: Long, val background: Boolean)

    private fun richSync(words: List<TtmlWord>) = words.joinToString(" ") { "<${wordTime(it.beginMs)}>${it.text}" }

    // the words of one <p>, each from the innermost <span> with a begin time round it; a span marked x-bg (and everything inside it) is the background part
    private fun timedWords(inner: String): List<TtmlWord> {
        class Open(val beginMs: Long?, val background: Boolean, val text: StringBuilder = StringBuilder())
        val words = ArrayList<TtmlWord>()
        val open = ArrayList<Open>()
        var last = 0
        fun text(piece: String) {
            if (piece.isEmpty()) return
            open.lastOrNull { it.beginMs != null }?.text?.append(piece)
        }
        for (tag in AnyTag.findAll(inner)) {
            text(inner.substring(last, tag.range.first))
            last = tag.range.last + 1
            val closing = tag.groupValues[1] == "/"
            val name = tag.groupValues[2].lowercase()
            val selfClosing = tag.groupValues[4] == "/"
            when {
                name == "br" -> text(" ")
                name != "span" -> Unit
                closing -> {
                    val done = open.removeLastOrNull() ?: continue
                    val word = done.text.toString().replace(Regex("\\s+"), " ").trim()
                    if (done.beginMs != null && word.isNotEmpty()) words += TtmlWord(word, done.beginMs, done.background)
                }
                else -> {
                    val attrs = tag.groupValues[3]
                    val background = BackgroundRole.containsMatchIn(attrs) || open.lastOrNull()?.background == true
                    val span = Open(BeginAttr.find(attrs)?.groupValues?.get(1)?.let(::ttmlTime), background)
                    if (!selfClosing) open += span
                }
            }
        }
        text(inner.substring(last))
        return words
    }

    private fun wordTime(ms: Long): String =
        String.format(Locale.US, "%02d:%02d.%03d", ms / 60_000, (ms / 1000) % 60, ms % 1000)

    // ttml writes time as 1:02:03.5, 02:03.5, 123.5s or 123500ms
    private fun ttmlTime(value: String): Long? {
        val v = value.trim()
        return when {
            v.endsWith("ms") -> v.removeSuffix("ms").toDoubleOrNull()?.toLong()
            v.endsWith("s") -> v.removeSuffix("s").toDoubleOrNull()?.times(1000)?.toLong()
            else -> {
                val parts = v.split(':').map { it.toDoubleOrNull() ?: return null }
                parts.fold(0.0) { total, part -> total * 60 + part }.times(1000).toLong()
            }
        }
    }

    private fun fromSrt(raw: String): String = raw.replace("\r", "").split(Regex("\n\\s*\n")).mapNotNull { block ->
        val lines = block.lines().filter { it.isNotBlank() }
        val timeIndex = lines.indexOfFirst { SrtTime.containsMatchIn(it) }
        if (timeIndex < 0) return@mapNotNull null
        val (h, m, s, frac) = SrtTime.find(lines[timeIndex])!!.destructured
        val ms = ((h.toLong() * 60 + m.toLong()) * 60 + s.toLong()) * 1000 + frac.padEnd(3, '0').take(3).toLong()
        val text = lines.drop(timeIndex + 1).joinToString(" ").replace(Tag, "").trim()
        if (text.isEmpty()) null else "${lrcTime(ms)}$text"
    }.joinToString("\n")

    private fun lrcTime(ms: Long): String =
        String.format(Locale.US, "[%02d:%02d.%02d]", ms / 60_000, (ms / 1000) % 60, (ms % 1000) / 10)
}
