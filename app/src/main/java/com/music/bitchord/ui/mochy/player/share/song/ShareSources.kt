// Ported from Mochy Music (GPL-3.0): ui/player/share/song/ShareSources.kt
// ShareSources.kt

package com.music.bitchord.ui.mochy.player.share.song

import android.content.Context
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.transformations
import coil3.toBitmap
import com.music.bitchord.ui.mochy.player.lookUpVideo
import com.music.bitchord.ui.mochy.player.share.coverPalette
import com.music.bitchord.ui.mochy.player.video.matchSongToVideo
import com.music.bitchord.ui.mochy.player.video.resolveVideoStreams
import com.music.bitchord.ui.mochy.utils.SoftBlurTransformation
import com.music.bitchord.ui.mochy.utils.resize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

// the cover's colours, the cover itself sharp for the card and blurred for the clouds
internal suspend fun loadShareArt(context: Context, session: SongShareSession) {
    val url = session.song.thumbnailUrl
    session.palette = coverPalette(context, url)
    val cover = loadBitmap(context, url?.resize(1080, 1080), size = 1080)
    val coverBlur = loadBitmap(context, url?.resize(544, 544), size = 192, blur = SoftBlurTransformation())
    session.art = session.art.copy(cover = cover, coverBlur = coverBlur)
}

// your own picture, big enough for a 4k export, and a soft blurred copy
internal suspend fun loadPictureArt(context: Context, session: SongShareSession, uri: Uri) {
    if (session.art.pictureUri == uri && session.art.picture != null) return
    val sharp = loadBitmap(context, uri, size = 2160)
    val blurred = loadBitmap(context, uri, size = 220, blur = SoftBlurTransformation(width = 160, radius = 3))
    session.art = session.art.copy(pictureUri = uri, picture = sharp, pictureBlur = blurred)
}

private suspend fun loadBitmap(context: Context, data: Any?, size: Int, blur: SoftBlurTransformation? = null): ImageBitmap? {
    if (data == null) return null
    return withContext(Dispatchers.IO) {
        runCatching {
            // kept in memory rather than on the gpu, so the software frames on old phones can draw it too
            val request = ImageRequest.Builder(context)
                .data(data)
                .size(size)
                .allowHardware(false)
                .apply { if (blur != null) transformations(blur) }
                .build()
            context.imageLoader.execute(request).image?.toBitmap()?.asImageBitmap()
        }.getOrNull()
    }
}

// the song's lyrics from the library, or fetched and kept there the same way the lyrics page does
//
// STUB (phase3-adapter, NEEDS.md §(b) L1): Mochy read the stored lyrics row
// from its database and otherwise fetched them through its LyricsHelper engine
// and stored the result. ChordWave has neither a lyrics store nor a lyrics
// fetcher wired yet, so a share opened from a menu reports no lyrics and the
// Lyrics tile stays disabled. A share opened by holding a line on the lyrics
// page is unaffected: it is handed its lyrics directly (see SongShareDialog).
internal suspend fun loadShareLyrics(session: SongShareSession) {
    // TODO(phase3-adapter): load the stored lyrics for session.song.id, fetch
    // through the lyrics engine when missing, parse with
    // LyricsUtils.parseLyricsCached, and set session.lyrics to
    // Fetched.Found(lines) — see NEEDS.md §(b) L1.
    Timber.w("SongShare: no lyrics store yet; lyrics share unavailable from menus for %s", session.song.id)
    loadLyricsOffset(session)
    session.lyrics = Fetched.Missing
}

// how far this song's lyrics were nudged on the lyrics page, so the share lines them up the same way
//
// STUB (phase3-adapter, NEEDS.md §(b) L2): Mochy read the song row's
// lyricsOffset from its database. Until the adapter wires it the offset is 0,
// which is also what an un-nudged song has, so nothing visibly changes.
internal suspend fun loadLyricsOffset(session: SongShareSession) {
    // TODO(phase3-adapter): read the stored lyrics offset for session.song.id
    // (NEEDS.md §(b) L2).
    Timber.w("SongShare: no lyrics offset store yet; using 0")
    session.lyricsOffsetMs = 0L
}

// the album's looping cover from the same places the player finds it
//
// STUB (phase3-adapter, NEEDS.md §(b) C1): Mochy looked the animated cover up
// through its canvas providers (Monochrome API, then Apple Music). Canvas
// fetch/playback is out of scope for this port, so the moving-cover background
// is never offered; the background pager simply omits its page.
internal suspend fun loadAnimatedCover(session: SongShareSession) {
    // TODO(phase3-adapter): resolve the animated cover URL for the song (see
    // NEEDS.md §(b) C1) and set session.animatedCover to Fetched.Found(url).
    Timber.w("SongShare: animated covers not available yet (no canvas engine)")
    session.animatedCover = Fetched.Missing
}

// the song's music video, its own picture when the song is a video; lined up as-is until matchMusicVideo has listened to both
internal suspend fun findMusicVideo(session: SongShareSession) {
    val song = session.song
    val found = runCatching { lookUpVideo(song.id, song.title, session.share.artist, song.isVideoSong) }.getOrNull()
    session.musicVideo = if (found == null) {
        Fetched.Missing
    } else {
        Fetched.Found(ShareMusicVideo(found.videoId, found.streams) { it })
    }
}

// lines the music video up with the song by listening to both; heavy, so it only runs once the music video is picked
//
// WIRED (phase3-adapter, NEEDS.md §(b) V2): the alignment comes from
// YouTube's own pairing when it paired this song and video itself (see
// VideoEngine.matchSongToVideo). When it did not, the video stays lined up
// as-is (videoMsAt = identity, set by findMusicVideo) — also what Mochy
// showed until the match finished.
internal suspend fun matchMusicVideo(session: SongShareSession) {
    val video = session.musicVideo.found ?: return
    val map = matchSongToVideo(session.song.id, video.videoId) ?: return
    session.musicVideo = Fetched.Found(ShareMusicVideo(video.videoId, video.streams, map::videoMsAt))
}

// links run out after a few hours; an export fetches fresh ones first if they have
//
// WIRED (phase3-adapter, NEEDS.md §(b) V3): re-resolves the video's streams
// when they have expired. When the re-resolve fails the expired video is
// used as-is — Mochy's own fallback when the re-resolve failed — so an
// export over a stale music video may fail at the encoder.
internal suspend fun freshMusicVideo(video: ShareMusicVideo): ShareMusicVideo {
    if (!video.streams.isExpired) return video
    val fresh = resolveVideoStreams(video.videoId, durationMs = video.streams.durationMs).getOrNull()
    if (fresh == null) {
        Timber.w("SongShare: music video links expired and could not be refreshed; using them as-is")
        return video
    }
    return ShareMusicVideo(video.videoId, fresh, video.videoMsAt)
}
