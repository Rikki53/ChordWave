package com.music.bitchord.karaoke.ai

import com.music.bitchord.playback.audio.AudioBlock
import com.music.bitchord.playback.audio.FloatAudioProcessor
import java.io.File
import java.io.RandomAccessFile
import kotlin.math.abs

/**
 * AI karaoke mixing, the DSP-chain partner of [KaraokeProcessor] for
 * mode AI: the player is already playing the instrumental stem (see
 * PlaybackService's source swap), and this stage adds the vocal stem
 * back on top, scaled by the same "Vocal volume" slider:
 *
 * ```
 * out = in + (vocalLevel) * vocalSample
 * ```
 *
 * The vocal stem is streamed from its WAV file, never loaded whole. Both
 * stems start at sample 0 of the song, so alignment is a frame counter:
 * this processor counts the frames it has processed, and on flush (a
 * seek) — or whenever its count drifts from the player's own position by
 * more than [RESYNC_THRESHOLD_FRAMES] — it re-seeks the reader to the
 * position the player reports through [bindPositionProvider]. The
 * provider is given this processor's sample rate and answers in frames
 * at that rate, so a millisecond clock upstream stays rate-agnostic.
 *
 * Only 44.1 kHz stereo is mixed: that is the stem format by construction
 * and the rate the instrumental WAV negotiates. Anything else bypasses.
 */
class StemMixProcessor : FloatAudioProcessor {

    @Volatile
    var enabled: Boolean = false

    /** The shared vocal slider: 1 = full vocal back, 0 = instrumental only. */
    @Volatile
    var vocalLevel: Float = 1f

    private var sampleRate: Int = KaraokeAi.SAMPLE_RATE
    private var channelCount: Int = 2

    private val lock = Any()
    private var reader: WavReader? = null
    private var readerFile: File? = null
    private var countedFrames: Long = 0L
    private var resync: Boolean = true
    private var scratch: FloatArray = FloatArray(0)

    @Volatile
    private var positionProvider: ((Int) -> Long)? = null

    /** PlaybackService installs the player's position clock here. */
    fun bindPositionProvider(provider: (Int) -> Long) {
        positionProvider = provider
    }

    /** Point the reader at [file] (or close it, for null / a missing file). */
    fun setVocalFile(file: File?) {
        synchronized(lock) {
            if (readerFile == file && (file == null || reader != null)) return
            runCatching { reader?.close() }
            reader = null
            readerFile = file
            if (file != null && file.isFile) {
                reader = runCatching { WavReader(file) }.getOrNull()
            }
            countedFrames = 0L
            resync = true
        }
    }

    fun closeStem() = setVocalFile(null)

    override fun configure(sampleRate: Int, channelCount: Int) {
        if (sampleRate > 0) this.sampleRate = sampleRate
        this.channelCount = channelCount
    }

    override fun process(block: AudioBlock) {
        if (block.frameCount == 0) return
        synchronized(lock) {
            // Position reconciliation first: a seek flushes the chain, and
            // drift beyond the threshold means the counter and the player
            // disagree about where the song is.
            val provider = positionProvider
            if (provider != null) {
                val playerFrames = runCatching { provider(sampleRate) }.getOrNull()
                if (playerFrames != null &&
                    (resync || abs(countedFrames - playerFrames) > RESYNC_THRESHOLD_FRAMES)
                ) {
                    reader?.seekToFrame(playerFrames)
                    countedFrames = playerFrames
                    resync = false
                }
            }
            val r = reader
            val level = if (enabled) vocalLevel.coerceIn(0f, 1f) else 0f
            if (r == null || !enabled || sampleRate != KaraokeAi.SAMPLE_RATE ||
                block.channelCount < 2
            ) {
                countedFrames += block.frameCount
                return
            }
            if (r.framePosition != countedFrames) r.seekToFrame(countedFrames)
            if (level == 0f) {
                // Instrumental only: the reader stays parked; the counter
                // above is what a later slider lift re-seeks from.
                countedFrames += block.frameCount
                return
            }
            if (scratch.size < block.frameCount * 2) {
                scratch = FloatArray(block.frameCount * 2)
            }
            val read = r.readFrames(scratch, block.frameCount)
            val samples = block.samples
            val channels = block.channelCount
            var frame = 0
            while (frame < read) {
                val base = frame * channels
                samples[base] += level * scratch[frame * 2]
                samples[base + 1] += level * scratch[frame * 2 + 1]
                frame++
            }
            countedFrames += block.frameCount
        }
    }

    override fun flush() {
        synchronized(lock) { resync = true }
    }

    override fun reset() {
        synchronized(lock) {
            countedFrames = 0L
            resync = true
            runCatching { reader?.seekToFrame(0L) }
        }
    }

    companion object {
        const val RESYNC_THRESHOLD_FRAMES = 2048L
    }
}

/**
 * Minimal streaming WAV reader for the stems this app writes itself:
 * PCM16, stereo, 44.1 kHz, canonical header (chunk walk included, so a
 * header with extra chunks still parses). Samples come out as float.
 */
internal class WavReader(file: File) : AutoCloseable {

    private val raf = RandomAccessFile(file, "r")
    private val dataOffset: Long
    private val dataBytes: Long
    val totalFrames: Long
    var framePosition: Long = 0L
        private set

    init {
        val header = ByteArray(12)
        raf.readFully(header)
        require(
            header.copyOfRange(0, 4).toString(Charsets.US_ASCII) == "RIFF" &&
                header.copyOfRange(8, 12).toString(Charsets.US_ASCII) == "WAVE",
        ) { "Not a WAV file" }
        var offset = -1L
        var bytes = 0L
        var channels = 2
        var bits = 16
        var pos = 12L
        val len = raf.length()
        val chunkHeader = ByteArray(8)
        while (pos + 8 <= len) {
            raf.seek(pos)
            raf.readFully(chunkHeader)
            val id = chunkHeader.copyOfRange(0, 4).toString(Charsets.US_ASCII)
            val size = (chunkHeader[4].toLong() and 0xFF) or
                ((chunkHeader[5].toLong() and 0xFF) shl 8) or
                ((chunkHeader[6].toLong() and 0xFF) shl 16) or
                ((chunkHeader[7].toLong() and 0xFF) shl 24)
            when (id) {
                "fmt " -> {
                    val fmt = ByteArray(16)
                    raf.seek(pos + 8)
                    raf.readFully(fmt)
                    channels = (fmt[2].toInt() and 0xFF) or ((fmt[3].toInt() and 0xFF) shl 8)
                    bits = (fmt[14].toInt() and 0xFF) or ((fmt[15].toInt() and 0xFF) shl 8)
                }
                "data" -> {
                    offset = pos + 8
                    bytes = size
                    break
                }
            }
            pos += 8 + size + (size and 1)
        }
        require(offset >= 0) { "WAV has no data chunk" }
        require(channels == 2 && bits == 16) { "Stem WAV must be PCM16 stereo" }
        dataOffset = offset
        dataBytes = bytes
        totalFrames = bytes / 4
    }

    fun seekToFrame(frame: Long) {
        framePosition = frame.coerceIn(0L, totalFrames)
    }

    /** Read up to [frames] stereo frames into [out]; returns frames read. */
    fun readFrames(out: FloatArray, frames: Int): Int {
        val available = (totalFrames - framePosition).coerceAtLeast(0L)
        val want = minOf(frames.toLong(), available).toInt()
        if (want <= 0) return 0
        val bytes = ByteArray(want * 4)
        raf.seek(dataOffset + framePosition * 4)
        raf.readFully(bytes)
        var i = 0
        while (i < want * 2) {
            val lo = bytes[i * 2].toInt() and 0xFF
            val hi = bytes[i * 2 + 1].toInt()
            out[i] = ((hi shl 8) or lo).toShort() / 32768f
            i++
        }
        framePosition += want
        return want
    }

    override fun close() {
        runCatching { raf.close() }
    }
}
