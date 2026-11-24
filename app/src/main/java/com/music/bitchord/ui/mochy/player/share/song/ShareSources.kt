// Ported from Mochy Music (GPL-3.0): ui/player/share/song/ShareSources.kt
// ShareSources.kt

package com.music.bitchord.ui.mochy.player.share.song

import android.content.Context
import android.net.ConnectivityManager
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.getSystemService
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.transformations
import coil3.toBitmap
import com.music.bitchord.data.canvas.CanvasRepository
import com.music.bitchord.data.canvas.CanvasSource
import com.music.bitchord.data.lyrics.EmbeddedLyrics
import com.music.bitchord.data.lyrics.LyricLine
import com.music.bitchord.data.lyrics.LyricsRepository
import com.music.bitchord.data.model.Song as CwSong
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.ui.mochy.constants.CanvasThumbnailAnimationKey
import com.music.bitchord.ui.mochy.constants.CanvasWifiOnlyKey
import com.music.bitchord.ui.mochy.lyrics.LyricsUtils
import com.music.bitchord.ui.mochy.lyrics.serializeLyricLines
import com.music.bitchord.ui.mochy.player.lookUpVideo
import com.music.bitchord.ui.mochy.player.share.coverPalette
import com.music.bitchord.ui.mochy.player.video.matchSongToVideo
import com.music.bitchord.ui.mochy.player.video.resolveVideoStreams
import com.music.bitchord.ui.mochy.utils.DataSaver
import com.music.bitchord.ui.mochy.utils.PreferencesCache
import com.music.bitchord.ui.mochy.utils.SoftBlurTransformation
import com.music.bitchord.ui.mochy.utils.dataStore
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
// The same order MainViewModel.loadLyrics uses: the user's own override
// first, then the file's embedded lyrics (synced ones win outright, plain
// ones stand in until a provider answers with timing), then the providers
// in the user's order. Lines go through serializeLyricLines +
// parseLyricsCached — the same path the player connection takes.
internal suspend fun loadShareLyrics(context: Context, session: SongShareSession) {
    val song = session.song
    val artist = song.artists.joinToString { it.name }
    val override = AppSettings.lyricsOverride(song.id)
    val localUri = song.localUri
    var embeddedPlain: List<LyricLine>? = null
    val embedded = if (override == null && localUri != null) {
        EmbeddedLyrics.forUri(context, localUri)?.also { lines ->
            if (!EmbeddedLyrics.isSynced(lines)) embeddedPlain = lines
        }
    } else {
        null
    }
    val found = if (override != null || (embedded != null && EmbeddedLyrics.isSynced(embedded))) {
        null // decided below without contacting the providers
    } else {
        LyricsRepository.lyrics(
            videoId = song.id,
            title = song.title,
            artist = artist,
            durationMs = song.duration * 1000L,
            album = song.album?.title,
            sources = AppSettings.lyricsSources.value,
            order = AppSettings.lyricsSourceOrder.value,
            prioritizeSyllableSync = AppSettings.prioritizeSyllableSync.value,
        )
    }
    val raw = override
        ?: embedded?.takeIf { EmbeddedLyrics.isSynced(it) }?.let { serializeLyricLines(it) }
        ?: found?.lines?.takeIf { embeddedPlain == null || EmbeddedLyrics.isSynced(it) }?.let { serializeLyricLines(it) }
        ?: embeddedPlain?.let { serializeLyricLines(it) }
    loadLyricsOffset(session)
    val lines = raw?.let { LyricsUtils.parseLyricsCached(it) }.orEmpty()
    session.lyrics = if (lines.isNotEmpty()) Fetched.Found(lines) else Fetched.Missing
}

// how far this song's lyrics were nudged on the lyrics page, so the share lines them up the same way
//
// The per-song offset when one was set, else the global offset — the same
// effective value the player connection reads.
internal suspend fun loadLyricsOffset(session: SongShareSession) {
    session.lyricsOffsetMs = (AppSettings.lyricsOffsetForSong(session.song.id)
        ?: AppSettings.lyricsOffsetMs.value).toLong()
}

// the album's looping cover from the same places the player finds it
//
// CanvasRepository, behind the same guards the player uses: the canvas
// switch, Wi-Fi-only on a metered network, and Data Saver.
internal suspend fun loadAnimatedCover(context: Context, session: SongShareSession) {
    val prefs = PreferencesCache.current(context.dataStore)
    val canvasEnabled = prefs[CanvasThumbnailAnimationKey] ?: true
    val canvasWifiOnly = prefs[CanvasWifiOnlyKey] ?: true
    val song = session.song
    val artist = song.artists.joinToString { it.name }
    if (!canvasEnabled || song.id.isBlank() || song.title.isBlank() || artist.isBlank()) {
        session.animatedCover = Fetched.Missing
        return
    }
    val connectivityManager = context.getSystemService<ConnectivityManager>()
    if (canvasWifiOnly && connectivityManager?.isActiveNetworkMetered == true) {
        session.animatedCover = Fetched.Missing
        return
    }
    if (DataSaver.isActive(context)) {
        session.animatedCover = Fetched.Missing
        return
    }
    // Same guard as the player: no Spotify clip when Spotify Canvas is off.
    val artwork = CanvasRepository.canvasFor(
        CwSong(
            videoId = song.id,
            title = song.title,
            artist = artist,
            thumbnailUrl = song.thumbnailUrl,
            albumName = song.album?.title,
        )
    )?.takeUnless { AppSettings.disableSpotifyCanvas.value && it.source == CanvasSource.SPOTIFY }
    session.animatedCover = artwork?.url?.let { Fetched.Found(it) } ?: Fetched.Missing
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
