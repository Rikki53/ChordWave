package com.music.bitchord.karaoke.ai

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import kotlin.math.roundToInt

/**
 * PCM plumbing for AI karaoke creation: decode any audio the platform can
 * read into interleaved stereo float at 44 100 Hz, write WAV PCM16 stems,
 * hash files, and fetch the MDX model with size + SHA-256 verification.
 */
object AudioPcm {

    /**
     * Decode [uri] (file:// or content://) to interleaved stereo float at
     * [KaraokeAi.SAMPLE_RATE]. Mono is duplicated; channels past the first
     * two are dropped. A source at another rate is linearly resampled —
     * simple, and honest: the model's input is a spectrogram, and the
     * alternative (a polyphase filter) is a much larger surface for the
     * same audible result here.
     *
     * Throws when the source has no audio track or the codec fails; the
     * caller surfaces that as the job's error.
     */
    fun decodeStereo44100(context: Context, uri: Uri): FloatArray {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
            var track = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                if (f.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                    track = i; format = f; break
                }
            }
            require(track >= 0 && format != null) { "No audio track in source" }
            extractor.selectTrack(track)
            val mime = format.getString(MediaFormat.KEY_MIME)!!
            val srcRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val srcChannels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                .coerceAtLeast(1)
            val chunks = ArrayList<FloatArray>()
            val codec = MediaCodec.createDecoderByType(mime)
            try {
                codec.configure(format, null, null, 0)
                codec.start()
                var sawInputEos = false
                val info = MediaCodec.BufferInfo()
                while (true) {
                    if (!sawInputEos) {
                        val inIdx = codec.dequeueInputBuffer(10_000)
                        if (inIdx >= 0) {
                            val buf = codec.getInputBuffer(inIdx)!!
                            val n = extractor.readSampleData(buf, 0)
                            if (n < 0) {
                                codec.queueInputBuffer(
                                    inIdx, 0, 0, 0,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                                )
                                sawInputEos = true
                            } else {
                                codec.queueInputBuffer(inIdx, 0, n, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }
                    val outIdx = codec.dequeueOutputBuffer(info, 10_000)
                    when {
                        outIdx >= 0 -> {
                            val outFmt = codec.outputFormat
                            val encoding = if (outFmt.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                                outFmt.getInteger(MediaFormat.KEY_PCM_ENCODING)
                            } else {
                                AudioFormat.ENCODING_PCM_16BIT
                            }
                            val channels = if (outFmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                                outFmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                                    .coerceAtLeast(1)
                            } else {
                                srcChannels
                            }
                            val buf = codec.getOutputBuffer(outIdx)!!
                            buf.position(info.offset)
                            buf.limit(info.offset + info.size)
                            val bytes = ByteArray(info.size)
                            buf.get(bytes)
                            chunks += toStereoFloat(bytes, channels, encoding)
                            codec.releaseOutputBuffer(outIdx, false)
                            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                        }
                        outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> Unit
                    }
                }
            } finally {
                runCatching { codec.stop() }
                codec.release()
            }
            val frames = chunks.sumOf { it.size / 2 }
            val joined = FloatArray(frames * 2)
            var at = 0
            for (c in chunks) {
                c.copyInto(joined, at)
                at += c.size
            }
            return if (srcRate == KaraokeAi.SAMPLE_RATE) joined
            else resampleLinear(joined, srcRate, KaraokeAi.SAMPLE_RATE)
        } finally {
            extractor.release()
        }
    }

    /** One decoded buffer, any supported PCM encoding, as stereo float pairs. */
    private fun toStereoFloat(bytes: ByteArray, channels: Int, encoding: Int): FloatArray {
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val frames = when (encoding) {
            AudioFormat.ENCODING_PCM_FLOAT -> bytes.size / 4 / channels
            AudioFormat.ENCODING_PCM_24BIT_PACKED -> bytes.size / 3 / channels
            AudioFormat.ENCODING_PCM_32BIT -> bytes.size / 4 / channels
            else -> bytes.size / 2 / channels // PCM16 and PCM8 fallthrough-ish
        }
        val out = FloatArray(frames * 2)
        fun sample(frame: Int, ch: Int): Float {
            val c = minOf(ch, channels - 1)
            return when (encoding) {
                AudioFormat.ENCODING_PCM_FLOAT -> bb.getFloat((frame * channels + c) * 4)
                AudioFormat.ENCODING_PCM_32BIT ->
                    bb.getInt((frame * channels + c) * 4) / 2147483648f
                AudioFormat.ENCODING_PCM_24BIT_PACKED -> {
                    val o = (frame * channels + c) * 3
                    val v = (bytes[o].toInt() and 0xFF) or
                        ((bytes[o + 1].toInt() and 0xFF) shl 8) or
                        ((bytes[o + 2].toInt()) shl 16)
                    v / 8388608f
                }
                AudioFormat.ENCODING_PCM_8BIT ->
                    ((bytes[frame * channels + c].toInt() and 0xFF) - 128) / 128f
                else -> bb.getShort((frame * channels + c) * 2) / 32768f
            }
        }
        for (f in 0 until frames) {
            out[f * 2] = sample(f, 0)
            out[f * 2 + 1] = if (channels > 1) sample(f, 1) else out[f * 2]
        }
        return out
    }

    /** Linear-interpolation resample of interleaved stereo. */
    fun resampleLinear(input: FloatArray, fromRate: Int, toRate: Int): FloatArray {
        if (fromRate == toRate || input.isEmpty()) return input
        val inFrames = input.size / 2
        val outFrames = (inFrames.toDouble() * toRate / fromRate).roundToInt()
        val out = FloatArray(outFrames * 2)
        val step = fromRate.toDouble() / toRate
        for (i in 0 until outFrames) {
            val pos = i * step
            val i0 = pos.toInt().coerceAtMost(inFrames - 1)
            val i1 = minOf(i0 + 1, inFrames - 1)
            val frac = (pos - i0).toFloat()
            out[i * 2] = input[i0 * 2] + (input[i1 * 2] - input[i0 * 2]) * frac
            out[i * 2 + 1] = input[i0 * 2 + 1] + (input[i1 * 2 + 1] - input[i0 * 2 + 1]) * frac
        }
        return out
    }

    // ---- WAV out ------------------------------------------------------------

    /** Write interleaved stereo float as WAV PCM16, 44.1 kHz, canonical 44-byte header. */
    fun writeWav16(file: File, samples: FloatArray) {
        val frames = samples.size / 2
        val dataBytes = frames * 4
        FileOutputStream(file).buffered().use { out ->
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            header.put("RIFF".toByteArray(Charsets.US_ASCII))
            header.putInt(36 + dataBytes)
            header.put("WAVE".toByteArray(Charsets.US_ASCII))
            header.put("fmt ".toByteArray(Charsets.US_ASCII))
            header.putInt(16)
            header.putShort(1) // PCM
            header.putShort(2) // stereo
            header.putInt(KaraokeAi.SAMPLE_RATE)
            header.putInt(KaraokeAi.SAMPLE_RATE * 4)
            header.putShort(4)
            header.putShort(16)
            header.put("data".toByteArray(Charsets.US_ASCII))
            header.putInt(dataBytes)
            out.write(header.array())
            val chunk = ShortArray(8192)
            var i = 0
            while (i < samples.size) {
                val n = minOf(chunk.size, samples.size - i)
                for (j in 0 until n) {
                    val v = samples[i + j].coerceIn(-1f, 1f)
                    chunk[j] = (v * 32767f).roundToInt().toShort()
                }
                val bb = ByteBuffer.allocate(n * 2).order(ByteOrder.LITTLE_ENDIAN)
                bb.asShortBuffer().put(chunk, 0, n)
                out.write(bb.array())
                i += n
            }
        }
    }

    // ---- Model download -----------------------------------------------------

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                digest.update(buf, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Download the model to [dest] via a `.part` sibling, reporting the
     * honest transferred fraction; verify size and SHA-256 before the
     * rename, and delete the partial on any failure or cancellation.
     */
    fun downloadModel(
        dest: File,
        progress: (Float) -> Unit,
        cancelled: () -> Boolean,
    ) {
        val part = File(dest.parentFile, dest.name + ".part")
        part.delete()
        try {
            val conn = URL(KaraokeAi.MODEL_URL).openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = true
            conn.connectTimeout = 30_000
            conn.readTimeout = 60_000
            conn.setRequestProperty("User-Agent", "ChordWave-AIKaraoke")
            conn.connect()
            check(conn.responseCode in 200..299) { "Model download HTTP ${conn.responseCode}" }
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: KaraokeAi.MODEL_SIZE
            conn.inputStream.buffered().use { input ->
                FileOutputStream(part).buffered().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        if (cancelled()) throw InterruptedException("cancelled")
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        progress((done.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
            check(part.length() == KaraokeAi.MODEL_SIZE) {
                "Model size mismatch: ${part.length()} != ${KaraokeAi.MODEL_SIZE}"
            }
            val hash = sha256(part)
            check(hash == KaraokeAi.MODEL_SHA256) { "Model SHA-256 mismatch" }
            check(part.renameTo(dest)) { "Could not store the model file" }
        } catch (t: Throwable) {
            part.delete()
            throw t
        }
    }
}
