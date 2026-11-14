// Ported from Mochy Music (GPL-3.0): utils/VideoStreams.kt (data types only).
// The pure data classes the video UI reads. The innertube parsing that FILLS
// them (pickVideoFamily / pickSharpFamily / HardwareDecoders / the
// PlayerResponse.Format helpers) belongs to the video engine and is NOT
// ported — see NEEDS.md §(b). The engine constructs VideoStreams; the UI
// only reads sizes / durationMs / isExpired / tracksFor / trackFor.

package com.music.bitchord.ui.mochy.utils

// one youtube video's pictures, each track the same picture at another size, with what the requests have to carry
data class VideoStreams(
    val videoId: String,
    // smallest first
    val tracks: List<VideoTrack>,
    val headers: Map<String, String>,
    val expiresAtMs: Long,
    val durationMs: Long,
    // the same picture past what tracks reach (1440p, 4k), which youtube only serves as vp9 or av1; empty when this phone can't decode it in hardware
    val sharpTracks: List<VideoTrack> = emptyList(),
    // every short side the video is offered at — from the resolver when it
    // knows more sizes than it has tracks for (InnerTubeX reports heights
    // but a single URL); empty means "the tracks are all there is". The
    // quality menu reads sizes, so it can offer every size even though only
    // one track is loaded; picking one re-resolves at it (see
    // YouTubeVideoSession.setQuality).
    val availableSizes: List<Int> = emptyList(),
) {
    // a minute early, so a video that is just starting doesn't run into a dead link halfway
    val isExpired: Boolean get() = System.currentTimeMillis() > expiresAtMs - 60_000L

    // every size there is, biggest first
    val sizes: List<Int>
        get() = if (availableSizes.isNotEmpty()) availableSizes.distinct().sortedDescending()
        else (tracks + sharpTracks).map { it.shortSide }.distinct().sortedDescending()

    // h264 for anything it has, the sharper family only once a size past it is picked
    fun tracksFor(shortSide: Int): List<VideoTrack> =
        if (sharpTracks.isNotEmpty() && shortSide > (tracks.lastOrNull()?.shortSide ?: 0)) sharpTracks else tracks

    // the largest track whose short side fits in shortSide, or the smallest when none does
    fun trackFor(shortSide: Int): VideoTrack? {
        val family = tracksFor(shortSide)
        return family.lastOrNull { it.shortSide <= shortSide } ?: family.firstOrNull()
    }
}

// every size can be fetched in small pieces, so the player can switch between them as the connection changes
val List<VideoTrack>.chunked: Boolean get() = isNotEmpty() && all { it.chunked }

data class VideoTrack(
    val itag: Int,
    val url: String,
    val mimeType: String,
    val codecs: String,
    val width: Int,
    val height: Int,
    val fps: Int,
    val bitrate: Int,
    val initRange: LongRange?,
    val indexRange: LongRange?,
    val contentLength: Long?,
) {
    // what "720p" means for a landscape and a portrait video alike
    val shortSide: Int get() = minOf(width, height)

    val chunked: Boolean get() = initRange != null && indexRange != null
}
