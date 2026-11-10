package com.music.bitchord.download

import com.music.bitchord.data.lyrics.WORD_LYRICS_FIELD
import java.io.ByteArrayOutputStream

/**
 * ID3v2 tags for the MP3s a source hands over as-is.
 *
 * The fourth container [MediaTagger] knows, and the one the other three
 * taggers' absence hurt most: an addon serving `mp3` is serving the most
 * portable audio file there is, and until this existed it landed with no
 * title but its filename, no cover, and no lyrics — the one download a user
 * could copy anywhere and have it arrive knowing nothing about itself.
 *
 * What is written is a fresh ID3v2.4 tag in front of the audio, replacing
 * whatever v2 tag the file arrived with:
 *
 *  - `TIT2`, `TPE1`, `TALB` — the names every player sorts by.
 *  - `APIC` — the cover, as a front-cover picture.
 *  - `USLT` — the plain LRC text, in the field every other player reads.
 *  - `SYLT` — the same lines as synchronised lyrics, parsed back out of the
 *    LRC timestamps, for the players that light lines up from the frame
 *    rather than from a text blob they have to re-parse.
 *  - `TXXX/[WORD_LYRICS_FIELD]` — this app's own enhanced form with its word
 *    timings, under the same name the FLAC and MP4 writers use, so
 *    [com.music.bitchord.data.lyrics.EmbeddedLyrics] reads all three back
 *    the same way.
 *
 * ID3 is the forgiving container of the four: the tag is a prefix and
 * nothing in the audio addresses itself by an absolute offset, so replacing
 * it cannot desync the frames behind it — the Xing/LAME header in the first
 * frame counts from the first frame, not from the start of the file. The
 * one thing that has to be got right is finding where the old tag ends,
 * which its header states outright.
 *
 * Like the other taggers: never throws, and returns its input unchanged —
 * the same array reference — for anything it does not recognise as MP3.
 */
object Mp3Tagger {

    private const val PADDING = 1024

    fun tag(
        bytes: ByteArray,
        title: String,
        artist: String,
        album: String?,
        lyrics: String?,
        cover: ByteArray?,
        coverMime: String,
        /** The A2 form, under a name of this app's own — see [WORD_LYRICS_FIELD]. */
        wordLyrics: String? = null,
    ): ByteArray = runCatching {
        val audioStart = audioStart(bytes) ?: return@runCatching bytes
        val frames = ByteArrayOutputStream()
        textFrame(frames, "TIT2", title)
        textFrame(frames, "TPE1", artist)
        if (!album.isNullOrBlank()) textFrame(frames, "TALB", album)
        if (cover != null && cover.isNotEmpty()) apicFrame(frames, cover, coverMime)
        if (!lyrics.isNullOrBlank()) {
            usltFrame(frames, lyrics)
            syltFrame(frames, lyrics)
        }
        if (!wordLyrics.isNullOrBlank()) txxxFrame(frames, WORD_LYRICS_FIELD, wordLyrics)
        if (frames.size() == 0) return@runCatching bytes

        val body = frames.toByteArray()
        val total = body.size + PADDING
        val out = ByteArrayOutputStream(10 + total + (bytes.size - audioStart))
        out.write(byteArrayOf('I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte()))
        out.write(4) // version 2.4
        out.write(0) // revision
        out.write(0) // flags: no unsynchronisation, no extended header, no footer
        out.write(synchsafe(total))
        out.write(body)
        out.write(ByteArray(PADDING))
        out.write(bytes, audioStart, bytes.size - audioStart)
        out.toByteArray()
    }.getOrDefault(bytes)

    /**
     * Where the MPEG audio begins: past an existing ID3v2 tag when there is
     * one, at 0 when the file starts with a frame sync, and nowhere — null —
     * when neither is true and this is not an MP3 at all.
     */
    private fun audioStart(bytes: ByteArray): Int? {
        if (bytes.size >= 10 &&
            bytes[0] == 'I'.code.toByte() && bytes[1] == 'D'.code.toByte() && bytes[2] == '3'.code.toByte()
        ) {
            val major = bytes[3].toInt()
            if (major !in 2..4) return null
            val size = unsynchsafe(bytes, 6) ?: return null
            var start = 10 + size
            // A v2.4 footer is a second 10-byte header after the frames.
            if (major == 4 && bytes[5].toInt() and 0x10 != 0) start += 10
            if (start >= bytes.size) return null
            return start
        }
        return if (isFrameSync(bytes, 0)) 0 else null
    }

    private fun isFrameSync(bytes: ByteArray, at: Int): Boolean =
        bytes.size >= at + 2 &&
            bytes[at] == 0xFF.toByte() &&
            bytes[at + 1].toInt() and 0xE0 == 0xE0

    // ---- Frames -------------------------------------------------------------

    private fun frame(out: ByteArrayOutputStream, id: String, body: ByteArray) {
        out.write(id.toByteArray(Charsets.US_ASCII))
        out.write(synchsafe(body.size))
        out.write(0)
        out.write(0)
        out.write(body)
    }

    private fun textFrame(out: ByteArrayOutputStream, id: String, value: String) {
        if (value.isBlank()) return
        val body = ByteArrayOutputStream()
        body.write(3) // UTF-8
        body.write(value.toByteArray(Charsets.UTF_8))
        frame(out, id, body.toByteArray())
    }

    private fun apicFrame(out: ByteArrayOutputStream, cover: ByteArray, mime: String) {
        val body = ByteArrayOutputStream()
        body.write(3) // UTF-8 description
        body.write(mime.toByteArray(Charsets.US_ASCII))
        body.write(0)
        body.write(3) // picture type: cover (front)
        body.write(0) // empty description
        body.write(cover)
        frame(out, "APIC", body.toByteArray())
    }

    private fun usltFrame(out: ByteArrayOutputStream, lyrics: String) {
        val body = ByteArrayOutputStream()
        body.write(3) // UTF-8
        body.write("eng".toByteArray(Charsets.US_ASCII))
        body.write(0) // empty descriptor
        body.write(lyrics.toByteArray(Charsets.UTF_8))
        frame(out, "USLT", body.toByteArray())
    }

    private fun txxxFrame(out: ByteArrayOutputStream, description: String, value: String) {
        val body = ByteArrayOutputStream()
        body.write(3) // UTF-8
        body.write(description.toByteArray(Charsets.UTF_8))
        body.write(0)
        body.write(value.toByteArray(Charsets.UTF_8))
        frame(out, "TXXX", body.toByteArray())
    }

    /**
     * The LRC's lines as a `SYLT` frame, or nothing when the text carries no
     * timestamps worth the name — an unsynchronised blob in a synchronised
     * frame would be a lie a strict player trips over, and `USLT` already
     * holds the words themselves.
     */
    private fun syltFrame(out: ByteArrayOutputStream, lrc: String) {
        val entries = parseLrc(lrc)
        if (entries.size < 2) return
        val body = ByteArrayOutputStream()
        body.write(3) // UTF-8
        body.write("eng".toByteArray(Charsets.US_ASCII))
        body.write(2) // timestamp format: absolute time, in milliseconds
        body.write(1) // content type: lyrics
        body.write(0) // empty descriptor
        for ((millis, text) in entries) {
            body.write(text.toByteArray(Charsets.UTF_8))
            body.write(0)
            body.write(byteArrayOf(
                (millis ushr 24).toByte(),
                (millis ushr 16).toByte(),
                (millis ushr 8).toByte(),
                millis.toByte(),
            ))
        }
        frame(out, "SYLT", body.toByteArray())
    }

    // ---- LRC ----------------------------------------------------------------

    private val TIMESTAMP = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?]""")

    /**
     * (milliseconds, line) pairs out of an LRC document, in time order. A
     * line carrying several timestamps is repeated at each of them, which is
     * how the format says a chorus comes back; metadata tags (`[ti:…]`) and
     * lines with no words are skipped.
     */
    private fun parseLrc(lrc: String): List<Pair<Int, String>> {
        val out = ArrayList<Pair<Int, String>>()
        for (line in lrc.lineSequence()) {
            val stamps = TIMESTAMP.findAll(line).toList()
            if (stamps.isEmpty()) continue
            val text = TIMESTAMP.replace(line, "").trim()
            if (text.isEmpty()) continue
            for (stamp in stamps) {
                val minutes = stamp.groupValues[1].toInt()
                val seconds = stamp.groupValues[2].toInt()
                val fraction = stamp.groupValues[3]
                val millis = when (fraction.length) {
                    0 -> 0
                    1 -> fraction.toInt() * 100
                    2 -> fraction.toInt() * 10
                    else -> fraction.take(3).toInt()
                }
                out += ((minutes * 60 + seconds) * 1000 + millis) to text
            }
        }
        return out.sortedBy { it.first }
    }

    // ---- Synchsafe integers ---------------------------------------------------

    /** The 4-byte synchsafe form of [value]: seven usable bits per byte. */
    private fun synchsafe(value: Int): ByteArray = byteArrayOf(
        ((value ushr 21) and 0x7F).toByte(),
        ((value ushr 14) and 0x7F).toByte(),
        ((value ushr 7) and 0x7F).toByte(),
        (value and 0x7F).toByte(),
    )

    private fun unsynchsafe(bytes: ByteArray, at: Int): Int? {
        if (at + 4 > bytes.size) return null
        var value = 0
        for (i in 0 until 4) {
            val b = bytes[at + i].toInt()
            if (b and 0x80 != 0) return null
            value = (value shl 7) or (b and 0x7F)
        }
        return value
    }
}
