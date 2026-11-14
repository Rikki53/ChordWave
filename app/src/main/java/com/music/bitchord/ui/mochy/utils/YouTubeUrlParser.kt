// Ported from Mochy Music (GPL-3.0): providers/innertube/.../utils/YouTubeUrlParser.kt
// Pure regex parser for pasted YouTube/Music links. Mochy's println debug
// lines ([LINK_PARSE_DEBUG]) are stripped — they spam logcat on every
// keystroke of a pasted link.

package com.music.bitchord.ui.mochy.utils

import com.music.bitchord.ui.mochy.models.innertube.WatchEndpoint

object YouTubeUrlParser {

    sealed class ParsedUrl {
        abstract val id: String

        data class Video(
            override val id: String,
        ) : ParsedUrl()

        data class Artist(
            override val id: String,
        ) : ParsedUrl()
    }

    private val VIDEO_URL_PATTERNS =
        listOf(
            Regex("""(?:https?://)?(?:www\.)?(?:music\.)?youtube\.com/watch\?.*v=([a-zA-Z0-9_-]{11})"""),
            Regex("""(?:https?://)?(?:www\.)?(?:music\.)?youtube\.com/watch\?v=([a-zA-Z0-9_-]{11})"""),
            Regex("""(?:https?://)?youtu\.be/([a-zA-Z0-9_-]{11})"""),
            Regex("""(?:https?://)?(?:www\.)?youtube\.com/shorts/([a-zA-Z0-9_-]{11})"""),
        )

    private val ARTIST_URL_PATTERNS =
        listOf(
            Regex("""(?:https?://)?(?:www\.)?music\.youtube\.com/channel/([a-zA-Z0-9_-]+)"""),
            Regex("""(?:https?://)?(?:www\.)?music\.youtube\.com/browse/(MPRE[a-zA-Z0-9_-]+)"""),
        )

    fun isYouTubeUrl(text: String): Boolean = parse(text) != null

    fun parse(url: String): ParsedUrl? {
        val trimmedUrl = url.trim()

        for (pattern in VIDEO_URL_PATTERNS) {
            pattern.find(trimmedUrl)?.let { matchResult ->
                matchResult.groupValues.getOrNull(1)?.let { videoId ->
                    return ParsedUrl.Video(videoId)
                }
            }
        }

        if (trimmedUrl.contains("music.youtube.com")) {
            for (pattern in ARTIST_URL_PATTERNS) {
                pattern.find(trimmedUrl)?.let { matchResult ->
                    matchResult.groupValues.getOrNull(1)?.let { artistId ->
                        return ParsedUrl.Artist(artistId)
                    }
                }
            }
        }

        return null
    }

    fun extractVideoId(url: String): String? = (parse(url) as? ParsedUrl.Video)?.id

    fun createWatchEndpoint(url: String): WatchEndpoint? =
        extractVideoId(url)?.let { videoId ->
            WatchEndpoint(videoId = videoId)
        }
}
