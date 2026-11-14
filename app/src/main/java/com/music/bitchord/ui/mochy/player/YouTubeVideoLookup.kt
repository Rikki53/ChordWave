// YouTubeVideoLookup.kt

package com.music.bitchord.ui.mochy.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.model.Song as CwSong
import com.music.bitchord.ui.mochy.playback.LocalPlayerConnection
import com.music.bitchord.ui.mochy.player.video.matchSongToVideo
import com.music.bitchord.ui.mochy.player.video.parseDurationText
import com.music.bitchord.ui.mochy.player.video.resolveVideoStreams
import com.music.bitchord.ui.mochy.constants.YtVideoBackgroundLyricsSyncKey
import com.music.bitchord.ui.mochy.lyrics.LyricsEntity.Companion.LYRICS_NOT_FOUND
import com.music.bitchord.ui.mochy.songedit.SongEdits
import com.music.bitchord.ui.mochy.utils.VideoStreams
import com.music.bitchord.ui.mochy.utils.rememberPreference

data class OfficialMusicVideo(
    val videoId: String,
    val streams: VideoStreams,
)

// a song's video and where on it the song is right now; positionAt is null when the two simply run side by side
@Immutable
class SongVideo(
    val videoId: String,
    val streams: VideoStreams,
    val positionAt: ((songPositionMs: Long) -> Long)?,
)

object YouTubeVideoUrlCache {
    private const val maxSize = 64
    private val map = LinkedHashMap<String, OfficialMusicVideo?>(maxSize, 0.75f, true)

    // a miss stays a miss, but a found video whose links ran out has to be looked up again
    @Synchronized
    fun contains(mediaId: String): Boolean =
        map.containsKey(mediaId) && map[mediaId]?.streams?.isExpired != true

    @Synchronized
    fun get(mediaId: String): OfficialMusicVideo? = map[mediaId]

    @Synchronized
    fun put(mediaId: String, video: OfficialMusicVideo?) {
        if (mediaId.isBlank()) return
        map[mediaId] = video
        while (map.size > maxSize) {
            val it = map.entries.iterator()
            if (it.hasNext()) {
                it.next()
                it.remove()
            } else {
                break
            }
        }
    }
}

// The official music video for a song: ChordWave's own video pairing first
// (YtMusicRepository.resolveVideo — YouTube Music's Song/Video pair when it
// has one, else a search matched by TrackMatcher), then its streams.
suspend fun findOfficialMusicVideo(mediaId: String, title: String, artist: String): OfficialMusicVideo? {
    val video = findOfficialVideoSong(mediaId, title, artist) ?: return null
    val streams = resolveVideoStreams(video.videoId, durationMs = parseDurationText(video.durationText)).getOrNull()
        ?: return null
    return OfficialMusicVideo(video.videoId, streams)
}

// The video row itself, without resolving its streams — what the id-only lookup needs.
private suspend fun findOfficialVideoSong(mediaId: String, title: String, artist: String): CwSong? {
    if (mediaId.isBlank()) return null
    val song = CwSong(videoId = mediaId, title = title, artist = artist, thumbnailUrl = null, isVideo = false)
    return try {
        YtMusicRepository.resolveVideo(song)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Timber.w("YouTubeVideoLookup: video lookup failed for $mediaId: ${e.message}")
        null
    }
}

// a track that is a video itself plays its own stream, which lines up with the audio exactly
suspend fun ownMusicVideo(mediaId: String, durationMs: Long = 0L): OfficialMusicVideo? {
    if (mediaId.isBlank()) return null
    val streams = resolveVideoStreams(mediaId, durationMs = durationMs).getOrNull() ?: return null
    return OfficialMusicVideo(mediaId, streams)
}

// just the official video's id, for when its captions are wanted and not its picture; misses are kept too, so a song is searched once
suspend fun findOfficialMusicVideoId(mediaId: String, title: String, artist: String): String? {
    if (mediaId.isBlank()) return null
    if (OfficialVideoIdCache.contains(mediaId)) return OfficialVideoIdCache.get(mediaId)
    val videoId = findOfficialVideoSong(mediaId, title, artist)?.videoId
    OfficialVideoIdCache.put(mediaId, videoId)
    return videoId
}

private object OfficialVideoIdCache {
    private const val maxSize = 64
    private val map = LinkedHashMap<String, String?>(maxSize, 0.75f, true)

    @Synchronized
    fun contains(key: String): Boolean = map.containsKey(key)

    @Synchronized
    fun get(key: String): String? = map[key]

    @Synchronized
    fun put(key: String, videoId: String?) {
        map[key] = videoId
        if (map.size > maxSize) map.remove(map.keys.first())
    }
}

// a song's video once found, and whether it's still being looked for
@Immutable
class SongVideoLookup(val video: SongVideo?, val searching: Boolean)

// finds the video for a song (its own when it is a video, the official one otherwise) and lines the two up; null while looking or when there is none
@Composable
fun rememberSongVideo(
    mediaId: String,
    title: String,
    artist: String,
    isVideoSong: Boolean,
    enabled: Boolean,
    durationMs: Long = 0L,
): SongVideo? = rememberSongVideoLookup(mediaId, title, artist, isVideoSong, enabled, durationMs).video

@Composable
fun rememberSongVideoLookup(
    mediaId: String,
    title: String,
    artist: String,
    isVideoSong: Boolean,
    enabled: Boolean,
    durationMs: Long = 0L,
): SongVideoLookup {
    // a video picked in "select video" (or none) changes what's looked up, so it keys everything below
    val edits by SongEdits.all.collectAsState()
    val pick = edits[mediaId]?.let { if (it.noVideo) NoVideoPick else it.videoId }
    // the first song takes its video straight from the cache, so the cover's video turning into the full video mode never gaps; later songs settle first
    val firstId = remember { mediaId }
    var found by remember(mediaId, pick) {
        mutableStateOf(if (enabled && mediaId == firstId) cachedVideo(mediaId, isVideoSong) else null)
    }
    var searching by remember(mediaId, pick) { mutableStateOf(enabled && found == null) }
    val playerConnection = LocalPlayerConnection.current
    LaunchedEffect(mediaId, title, artist, enabled, isVideoSong, pick) {
        if (!enabled || mediaId.isBlank()) {
            found = null
            searching = false
            return@LaunchedEffect
        }
        if (found != null) {
            searching = false
            return@LaunchedEffect
        }
        searching = true
        // skipping through songs shouldn't look up, and then start a decoder for, every song passed on the way
        delay(LookupSettleMs)
        // The video's length is the song's when this lookup is for the song
        // playing now — the resolver has no cheaper way to learn it, and the
        // sync loop needs it to line the video up with the song.
        val knownDuration = durationMs.takeIf { it > 0L }
            ?: playerConnection?.let { connection ->
                if (connection.mediaMetadata.value?.id == mediaId) connection.progressState.value.duration else 0L
            } ?: 0L
        found = lookUpVideo(mediaId, title, artist, isVideoSong, knownDuration)
        searching = false
    }
    val video = found
    val positionAt = if (video != null) rememberVideoTimeline(mediaId, video.videoId) else null
    val songVideo = remember(video, positionAt) { video?.let { SongVideo(it.videoId, it.streams, positionAt) } }
    return remember(songVideo, searching) { SongVideoLookup(songVideo, searching) }
}

// how long a song has to stay before its video is looked up
private const val LookupSettleMs = 500L

private const val NoVideoPick = "none"

private fun videoCacheKey(mediaId: String, isVideoSong: Boolean): String {
    val picked = SongEdits.of(mediaId)?.videoId
    return when {
        picked != null -> "pick:$picked"
        isVideoSong -> "self:$mediaId"
        else -> mediaId
    }
}

private fun cachedVideo(mediaId: String, isVideoSong: Boolean): OfficialMusicVideo? {
    if (SongEdits.of(mediaId)?.noVideo == true) return null
    val key = videoCacheKey(mediaId, isVideoSong)
    return if (YouTubeVideoUrlCache.contains(key)) YouTubeVideoUrlCache.get(key) else null
}

// what the menus offer for a song's video: "wrong video?" once one was found, "find video" when the lookup came back empty or it was turned off
fun hasFoundVideo(mediaId: String, isVideoSong: Boolean): Boolean = cachedVideo(mediaId, isVideoSong) != null

// one lookup at a time: the mini player and the full video mode both ask for the playing song, and the second finds the first's answer cached
private val lookupLock = Mutex()

internal suspend fun lookUpVideo(
    mediaId: String,
    title: String,
    artist: String,
    isVideoSong: Boolean,
    durationMs: Long = 0L,
): OfficialMusicVideo? =
    lookupLock.withLock {
        val edit = SongEdits.of(mediaId)
        if (edit?.noVideo == true) return@withLock null
        val key = videoCacheKey(mediaId, isVideoSong)
        if (YouTubeVideoUrlCache.contains(key)) return@withLock YouTubeVideoUrlCache.get(key)
        val picked = edit?.videoId
        if (picked == null && !isVideoSong && (title.isBlank() || artist.isBlank())) return@withLock null
        val resolved = when {
            picked != null -> ownMusicVideo(picked, durationMs)
            isVideoSong -> ownMusicVideo(mediaId, durationMs)
            else -> findOfficialMusicVideo(mediaId, title, artist)
        }
        YouTubeVideoUrlCache.put(key, resolved)
        resolved
    }

// where a separately found video is for each moment of the song: a first guess from its captions, then the exact answer from listening to both
@Composable
private fun rememberVideoTimeline(mediaId: String, videoId: String): ((Long) -> Long)? {
    val playerConnection = LocalPlayerConnection.current ?: return null
    // the track's own video is already in step with it
    if (videoId == mediaId) return null
    val lyricsSyncEnabled by rememberPreference(YtVideoBackgroundLyricsSyncKey, defaultValue = true)
    val currentLyrics by playerConnection.currentLyrics.collectAsState(initial = null)

    var lyricTimeline by remember(mediaId) { mutableStateOf<LyricVideoTimeline?>(null) }
    LaunchedEffect(videoId, currentLyrics?.lyrics, lyricsSyncEnabled) {
        val lyricsRaw = currentLyrics?.lyrics?.takeIf { it.isNotBlank() && it != LYRICS_NOT_FOUND }
        if (!lyricsSyncEnabled || lyricsRaw == null) {
            lyricTimeline = null
            return@LaunchedEffect
        }
        val key = "$mediaId|$videoId|${lyricsRaw.hashCode()}"
        if (LyricVideoTimelineCache.contains(key)) {
            lyricTimeline = LyricVideoTimelineCache.get(key)
            return@LaunchedEffect
        }
        val timeline = try {
            buildLyricVideoTimeline(videoId, lyricsRaw)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        LyricVideoTimelineCache.put(key, timeline)
        lyricTimeline = timeline
    }

    var audioMap by remember(mediaId) { mutableStateOf<com.music.bitchord.ui.mochy.lyrics.SongVideoMap?>(null) }
    LaunchedEffect(mediaId, videoId, lyricsSyncEnabled) {
        if (!lyricsSyncEnabled) {
            audioMap = null
            return@LaunchedEffect
        }
        // YouTube's own pairing between this song and its video, when it
        // paired them itself — exact, with no listening. Null when the pair
        // came from a search match: the caption timeline above is the only
        // alignment then (measuring it by listening is not implemented —
        // see VideoEngine.matchSongToVideo).
        audioMap = try {
            matchSongToVideo(mediaId, videoId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w("YouTubeVideoLookup: song-video match failed for $mediaId: ${e.message}")
            null
        }
    }

    return remember(lyricTimeline, audioMap) {
        audioMap?.let { map -> { songMs: Long -> map.videoMsAt(songMs) } }
            ?: lyricTimeline?.let { timeline ->
                // the lyrics' own offset counts too: it's what puts the lyrics, and so the video, on the song
                { songMs: Long -> timeline.videoMsAt(songMs + playerConnection.lyricsOffsetMs.value) }
            }
    }
}
