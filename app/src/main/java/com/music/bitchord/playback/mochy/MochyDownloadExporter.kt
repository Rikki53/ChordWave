// MochyDownloadExporter — one downloaded song out of the download cache
// into the shared Music/ChordWave folder (two-engine Tahap 5).
//
// Mochy keeps downloads as cache spans and exports them to plain files
// only when asked (its DownloadExporter, into a folder the user picks).
// ChordWave's decision is different: both engines' downloads live in
// one folder, Music/ChordWave, readable by both modes — so a Mochy
// download is exported here the moment it completes, through the same
// DownloadStore destination (and the same tag embedding) the Classic
// download path uses. The cache copy stays: it is what the Mochy
// player chain reads first, and deleting the download removes both.
//
// The span checks and the container sniffing are Mochy's (audioOf /
// extensionOf in its DownloadExporter): a download whose spans do not
// join into the whole file is not exported short. The webm->opus remux
// and the playlist/album folder layouts of Mochy's exporter are not
// ported: downloads headed for Music are pinned to AAC at resolve time
// (see MochyStreamResolver.resolveForDownload), and ChordWave's folder
// is flat by design.

package com.music.bitchord.playback.mochy

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.ContentMetadata
import com.music.bitchord.data.DebugLog
import com.music.bitchord.data.model.Song
import com.music.bitchord.download.DownloadStore
import com.music.bitchord.download.LyricsTag
import com.music.bitchord.download.MediaTagger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(UnstableApi::class)
object MochyDownloadExporter {

    private const val TAG = "MochyDownload"

    /** What an export produced: where the file landed, and the record's display bits. */
    class Exported(
        val uri: Uri,
        val downloadFormat: String?,
        val artworkUri: String?,
    )

    /**
     * Copies [song]'s completed download out of the download cache into
     * Music/ChordWave (or the private download folder when exports are
     * off — DownloadStore decides, exactly as for a Classic download).
     * Returns null when the cache does not hold the whole song or the
     * store refuses the file; the download then lives in the cache
     * alone, which the Mochy engine still plays.
     */
    suspend fun exportSong(context: Context, song: Song, badge: String?): Exported? =
        withContext(Dispatchers.IO) {
            val app = context.applicationContext
            val audio = audioOf(app, song.videoId) ?: run {
                DebugLog.w(TAG, "export of ${song.videoId}: cache does not hold the whole song")
                return@withContext null
            }
            val name = DownloadStore.fileNameFor(song, audio.extension)
            DownloadStore.existing(app, name)?.let { existing ->
                DebugLog.d(TAG, "$name is already in Music; adopting it")
                return@withContext Exported(existing, badge, null)
            }
            val lyrics = if (MediaTagger.carriesTags(audio.extension)) {
                runCatching { LyricsTag.forTrack(song) }.getOrNull()
            } else {
                null
            }
            val artwork = if (MediaTagger.carriesTags(audio.extension)) {
                runCatching { MediaTagger.artworkFor(app, song) }.getOrNull()
            } else {
                null
            }
            val pending = runCatching { DownloadStore.begin(app, name, mimeFor(audio.extension)) }
                .getOrElse {
                    DebugLog.w(TAG, "export of ${song.videoId} refused: ${it.message}")
                    return@withContext null
                }
            try {
                pending.openStream().use { sink ->
                    val buffer = ByteArray(256 * 1024)
                    audio.spans.forEach { span ->
                        span.inputStream().use { input ->
                            while (true) {
                                val read = input.read(buffer)
                                if (read < 0) break
                                sink.write(buffer, 0, read)
                            }
                        }
                    }
                }
                // Publish only after metadata is part of the file, as the
                // Classic transfer does.
                MediaTagger.embed(app, pending.tagUri, song, audio.extension, lyrics, artwork)
                val artworkUri = MediaTagger.persistArtwork(app, song.thumbnailUrl, artwork)
                val uri = pending.commit()
                DebugLog.d(TAG, "exported $name to $uri")
                Exported(uri, badge, artworkUri)
            } catch (t: Throwable) {
                runCatching { pending.abort() }
                DebugLog.w(TAG, "export of ${song.videoId} failed: ${t.message}")
                null
            }
        }

    /** The MIME type the store is told for [extension] (the Classic path's table, by container). */
    private fun mimeFor(extension: String): String = when (extension) {
        "flac" -> "audio/flac"
        "mp3" -> "audio/mpeg"
        "m4a" -> "audio/mp4"
        "opus" -> "audio/opus"
        "ogg" -> "audio/ogg"
        "wav" -> "audio/x-wav"
        "webm" -> "audio/webm"
        else -> "audio/mp4"
    }

    /** A downloaded song's bytes, straight from the pieces in the download cache (Mochy's CachedAudio). */
    private class CachedAudio(val spans: List<File>, val extension: String)

    // Null when the download isn't whole, so a broken song is skipped rather than written out short (Mochy).
    private fun audioOf(context: Context, mediaId: String): CachedAudio? {
        val cache = MochyCaches.downloadCache(context)
        val length = ContentMetadata.getContentLength(cache.getContentMetadata(mediaId))
        val spans = cache.getCachedSpans(mediaId).filter { it.isCached }.sortedBy { it.position }
        var expected = 0L
        for (span in spans) {
            if (span.position != expected) return null
            expected += span.length
        }
        if (spans.isEmpty() || (length > 0 && expected != length)) return null
        val files = spans.mapNotNull { it.file }
        if (files.size != spans.size) return null
        return CachedAudio(files, extensionOf(files.first()))
    }

    // Read off the file's first bytes rather than the saved format, since a module download can carry any container (Mochy).
    private fun extensionOf(first: File): String {
        val head = ByteArray(64)
        val read = runCatching { first.inputStream().use { it.read(head) } }.getOrDefault(0)
        if (read < 4) return "m4a"
        fun at(offset: Int, text: String) = text.indices.all { head[offset + it] == text[it].code.toByte() }
        return when {
            at(0, "fLaC") -> "flac"
            // ogg holding opus is an .opus file, anything else in ogg stays .ogg
            at(0, "OggS") -> if (read >= 36 && (28..read - 8).any { at(it, "OpusHead") }) "opus" else "ogg"
            at(0, "ID3") || (head[0] == 0xFF.toByte() && (head[1].toInt() and 0xE0) == 0xE0) -> "mp3"
            at(0, "RIFF") -> "wav"
            head[0] == 0x1A.toByte() && head[1] == 0x45.toByte() && head[2] == 0xDF.toByte() && head[3] == 0xA3.toByte() -> "webm"
            read >= 8 && at(4, "ftyp") -> "m4a"
            else -> "m4a"
        }
    }
}
