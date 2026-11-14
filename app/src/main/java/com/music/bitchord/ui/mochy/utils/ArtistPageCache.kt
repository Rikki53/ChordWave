// Ported from Mochy Music (GPL-3.0): utils/ArtistPageCache.kt (TRIMMED —
// ArtistHeader + the picks/header maps only). The page map is dropped: it
// holds Mochy's innertube ArtistPage, which is not ported (CW's ArtistPage
// is a different type; the detail-page phase can add a CW-typed page cache
// if the artist screen needs it). Home only uses putPicks/picks.
package com.music.bitchord.ui.mochy.utils

import com.music.bitchord.ui.mochy.models.ArtistGroup
import java.util.concurrent.ConcurrentHashMap

/**
 * what an artist's page shows at the top: the round photo from YouTube Music, and the banner,
 * motion and name logo from Apple Music when Apple has them. image urls are ready to load.
 */
data class ArtistHeader(
    val id: String,
    val name: String,
    /** YouTube Music's photo; crop it square for the round avatar */
    val avatarUrl: String?,
    /** a still for the banner: Apple's motion frame or banner, else the YouTube photo */
    val bannerUrl: String?,
    /** Apple's looping banner video (HLS), when there is one */
    val motionUrl: String?,
    /** the artist's name as a white logo on transparent, when Apple has one */
    val logoUrl: String?,
    /** the logo's width / height */
    val logoAspect: Float?,
)

/**
 * the artist pages opened this run, so the pages under an artist (top songs, albums, videos,
 * similar artists) open on the same banner and shelves at once instead of fetching them again.
 */
object ArtistPageCache {
    private val headers = ConcurrentHashMap<String, ArtistHeader>()
    private val picks = ConcurrentHashMap<String, ArtistGroup>()

    fun header(artistId: String): ArtistHeader? = headers[artistId]

    fun putHeader(header: ArtistHeader) {
        headers[header.id] = header
    }

    /** what a Home "Similar to" box showed for this artist, so its page leads with the same picks */
    fun picks(artistId: String): ArtistGroup? = picks[artistId]

    fun putPicks(artistId: String, group: ArtistGroup) {
        picks[artistId] = group
    }
}
