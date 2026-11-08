package com.music.bitchord.karaoke.ai

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import java.security.MessageDigest

/**
 * On-device AI karaoke stems (Misi 2): where the model and the per-song
 * stems live, how a song id becomes a file name, and the shared job state
 * the creation service writes and the karaoke UI reads.
 *
 * Stems live in a dedicated app cache folder (`cacheDir/karaoke-ai`) as
 * `<id>_inst.wav` + `<id>_vocal.wav`, WAV PCM16 44.1 kHz stereo, exactly
 * what [StemMixProcessor] streams and what PlaybackService swaps in as
 * the playback source. The 67 MB model is NOT bundled: it is downloaded
 * once into internal storage (`filesDir/karaoke-ai`) and verified against
 * [MODEL_SHA256]/[MODEL_SIZE] before it is ever used.
 */
object KaraokeAi {

    const val MODEL_URL =
        "https://huggingface.co/seanghay/uvr_models/resolve/main/Kim_Vocal_2.onnx"
    const val MODEL_NAME = "kim_vocal_2.onnx"
    const val MODEL_SHA256 =
        "ce74ef3b6a6024ce44211a07be9cf8bc6d87728cc852a68ab34eb8e58cde9c8b"
    const val MODEL_SIZE = 66_759_214L
    const val SAMPLE_RATE = 44_100

    /** One creation job's public state; null when nothing has ever run. */
    data class JobState(
        val songId: String,
        val title: String,
        /** Human phase: model download / source / decode / separation / done. */
        val phase: String,
        /** Fraction within [phase], 0f..1f; -1f while indeterminate. */
        val fraction: Float,
        val error: String? = null,
        val done: Boolean = false,
        val cancelled: Boolean = false,
    )

    val job = MutableStateFlow<JobState?>(null)

    /** Set by the service's cancel action; polled by the blocking workers. */
    @Volatile
    var cancelRequested: Boolean = false

    /**
     * File-name-safe stem key: catalogue ids (YouTube etc.) pass through
     * unchanged; anything else (a local track's `content://` id) is hashed,
     * so one song always maps to one stem pair and never to a path escape.
     */
    fun safeId(raw: String): String =
        if (raw.matches(Regex("[A-Za-z0-9_-]{1,80}"))) raw
        else MessageDigest.getInstance("SHA-256")
            .digest(raw.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
            .take(32)

    fun stemsDir(context: Context): File =
        File(context.cacheDir, "karaoke-ai").apply { mkdirs() }

    fun instFile(context: Context, songId: String): File =
        File(stemsDir(context), "${safeId(songId)}_inst.wav")

    fun vocalFile(context: Context, songId: String): File =
        File(stemsDir(context), "${safeId(songId)}_vocal.wav")

    fun hasStem(context: Context, songId: String): Boolean {
        val i = instFile(context, songId)
        val v = vocalFile(context, songId)
        return i.isFile && v.isFile && i.length() > 44 && v.length() > 44
    }

    fun stemBytes(context: Context, songId: String): Long =
        listOf(instFile(context, songId), vocalFile(context, songId))
            .sumOf { if (it.isFile) it.length() else 0L }

    fun totalBytes(context: Context): Long =
        stemsDir(context).listFiles()?.sumOf { it.length() } ?: 0L

    fun deleteStem(context: Context, songId: String) {
        instFile(context, songId).delete()
        vocalFile(context, songId).delete()
    }

    fun clearAll(context: Context) {
        stemsDir(context).listFiles()?.forEach { it.delete() }
    }

    fun modelDir(context: Context): File =
        File(context.filesDir, "karaoke-ai").apply { mkdirs() }

    fun modelFile(context: Context): File = File(modelDir(context), MODEL_NAME)

    /** Length is the cheap gate; the hash is verified at download time. */
    fun modelReady(context: Context): Boolean {
        val f = modelFile(context)
        return f.isFile && f.length() == MODEL_SIZE
    }
}
