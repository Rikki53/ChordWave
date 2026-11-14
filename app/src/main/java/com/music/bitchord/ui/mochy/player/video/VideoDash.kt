// Ported from Mochy Music (GPL-3.0): ui/player/video/VideoDash.kt
// RESTORED (Phase 3 adapter) — the DASH manifest builder, reconstructed from
// the contract in NEEDS.md §(b) B1: one AdaptationSet per codec family,
// SegmentBase with init/index ranges, PT%.3fS durations. No Mochy source was
// available to diff against; the output is a standard ISOBMFF on-demand MPD
// (urn:mpeg:dash:profile:isoff-on-demand:2011), the shape ExoPlayer's
// DashMediaSource expects for ranged googlevideo URLs.
//
// NOTE: today's resolver (InnerTubeX) reports no init/index ranges, so its
// tracks are not chunked and the session plays them progressive — this
// builder returns null for them. It activates if a future resolver supplies
// ranged tracks.

package com.music.bitchord.ui.mochy.player.video

import com.music.bitchord.ui.mochy.utils.VideoTrack
import com.music.bitchord.ui.mochy.utils.chunked
import java.util.Locale

// a dash manifest written from the sizes youtube handed us, so exoplayer can fetch small pieces and change size on its own
internal object VideoDash {
    fun manifest(family: List<VideoTrack>, durationMs: Long): String? {
        val chunked = family.filter { it.chunked }
        if (chunked.isEmpty() || durationMs <= 0L) return null
        val duration = "PT%.3fS".format(Locale.US, durationMs / 1000.0)
        val sets = chunked
            .groupBy { it.mimeType to codecFamily(it.codecs) }
            .entries.joinToString("\n") { (key, tracks) ->
                val mimeType = key.first
                val representations = tracks.joinToString("\n") { track ->
                    val init = track.initRange
                    val index = track.indexRange
                    if (init == null || index == null) return@joinToString ""
                    buildString {
                        // A raw string cannot end in a quote ("""" closes it
                        // early), so each attribute's closing quote goes on
                        // with the bracket at the end.
                        append("""<Representation id="${track.itag}" bandwidth="${track.bitrate}" width="${track.width}" height="${track.height}""")
                        if (track.fps > 0) append(""" frameRate="${track.fps}""")
                        if (track.codecs.isNotEmpty()) append(""" codecs="${xmlEscape(track.codecs)}""")
                        append("\">")
                        append("""<BaseURL>${xmlEscape(track.url)}</BaseURL>""")
                        append("""<SegmentBase indexRange="${index.first}-${index.last}">""")
                        append("""<Initialization range="${init.first}-${init.last}"/>""")
                        append("</SegmentBase></Representation>")
                    }
                }
                """<AdaptationSet mimeType="${xmlEscape(mimeType)}" segmentAlignment="true" startWithSAP="1">""" +
                    "\n" + representations + "\n</AdaptationSet>"
            }
        return buildString {
            append("""<MPD xmlns="urn:mpeg:dash:schema:mpd:2011" type="static" mediaPresentationDuration="$duration" minBufferTime="PT1.5S" profiles="urn:mpeg:dash:profile:isoff-on-demand:2011">""")
            append("\n<Period duration=\"$duration\">\n")
            append(sets)
            append("\n</Period>\n</MPD>")
        }
    }

    // "avc1.640028" -> "avc1", "vp09.00.10.08" -> "vp09", "av01.0.08M.08" -> "av01"
    private fun codecFamily(codecs: String): String = codecs.substringBefore(".").ifEmpty { codecs }

    private fun xmlEscape(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}
