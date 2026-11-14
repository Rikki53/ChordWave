// StreamDetails.kt — data-only port of the StreamDetails data class in
// Mochy's playback/StreamInspector.kt (GPL-3.0). The StreamInspector engine
// (an AnalyticsListener that files decoder/sink events per song) is NOT
// ported — see NEEDS.md §(b).

package com.music.bitchord.ui.mochy.playback

data class StreamDetails(
    val mediaId: String? = null,
    val containerMime: String? = null,
    val sampleMime: String? = null,
    val codecs: String? = null,
    /** bits per second, as the stream says */
    val bitrate: Int? = null,
    val sampleRate: Int? = null,
    val channels: Int? = null,
    /** what a lossless file was mastered at (16, 24); lossy streams have none */
    val sourceBitDepth: Int? = null,
    val decoderName: String? = null,
    val decoderInitMs: Long? = null,
    /** the pcm the decoder hands over, a C.ENCODING_PCM_* */
    val decodedEncoding: Int? = null,
    val decodedSampleRate: Int? = null,
    val decodedChannels: Int? = null,
    /** what the effects and the audio track were given: float when hi-res is passing through untouched */
    val outputEncoding: Int? = null,
    val outputSampleRate: Int? = null,
    val outputChannelConfig: Int? = null,
    val outputBufferBytes: Int? = null,
    val offload: Boolean = false,
    val tunneling: Boolean = false,
    val underruns: Int = 0,
    val fileSizeBytes: Long? = null,
    /** the app's own resampler, when it's converting this song */
    val resampledFrom: Int? = null,
    val resampledTo: Int? = null,
    val resamplerTaps: Int? = null,
    val resamplerPassbandHz: Double? = null,
    /** brought down to 16-bit with tpdf dither, rather than cut */
    val dithered: Boolean = false,
)
