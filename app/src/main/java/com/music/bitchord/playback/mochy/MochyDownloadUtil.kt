// MochyDownloadUtil — the Mochy engine's download engine (two-engine
// Tahap 5), ported from Mochy's playback/DownloadUtil.kt.
//
// What is Mochy's here:
//  - media3 DownloadManager over the download cache, at most three at
//    once, fed straight from the network — never through the player's
//    cache, whose bytes are often another format than the download
//    asks for; gluing the two together made songs that played for a
//    few seconds, then went silent;
//  - the resolver: a source module's whole file first (here ChordWave's
//    the modules system, through SourceModules.downloadRoute (batch 55b)
//    the Mochy service already resolves source tracks through), then
//    YouTube through the Mochy innertube (MochyStreamResolver), with
//    the itag guard that fails a resumed download whose stream changed
//    format part way rather than splicing two files into one;
//  - Room records: the FormatEntity and the song row at resolve time,
//    isDownloaded/dateDownload on completion, the real length once
//    known, and the song's lyrics saved with it so they are there
//    offline;
//  - offline mode holds downloads where they are (pause on, resume
//    off) instead of failing them.
//
// What stays ChordWave's, deliberately:
//  - the door and the progress: Downloads.enqueue keeps the
//    mobile-data gate and DownloadSession keeps the progress UI
//    (Riki's decision: ChordWave download progress is kept); this util
//    mirrors its states into Downloads.active/DownloadSession, so
//    every Modern surface — menus, the batch-52 "Queued" label, the
//    library progress card — reads them exactly as before;
//  - the folder: on completion the song is exported to Music/ChordWave
//    (MochyDownloadExporter) and registered in Downloads.saved, so
//    both engines' downloads live in one folder both modes can play
//    (Riki's decision #3). Mochy exports only on request; here the
//    export is part of finishing.
//  - the quality setting: the Mochy DataStore's DownloadQualityKey
//    drives this engine (batch 53, as in Mochy's DownloadUtil: HIGH /
//    MEDIUM / LOW map onto the resolver's same-named rungs; source
//    downloads translate the rung into ChordWave's tiers, since
//    Sources are ChordWave's feature). Classic keeps its own
//    AppSettings.downloadQuality.
//
// Deviations from Mochy, recorded honestly: manifest (HLS/DASH) source
// streams are not downloaded here — DownloadManager fetches whole
// files, as Mochy's does; a manifest candidate is passed over for the
// next source or YouTube (Classic keeps its offline-package path).
// Auto-downloads for liked/playlists stay with PlaylistSync, which
// routes here when the Mochy engine is active.

package com.music.bitchord.playback.mochy

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import com.mochyy.mochymusic.db.entities.FormatEntity
import com.mochyy.mochymusic.db.entities.LyricsEntity
import com.mochyy.mochymusic.db.entities.SongEntity
import com.music.bitchord.data.DebugLog
import com.music.bitchord.data.Http
import com.music.bitchord.data.lyrics.LyricsRepository
import com.music.bitchord.data.mochydata.MochyDatabaseHolder
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.DownloadQuality
import com.music.bitchord.data.sources.SourceRegistry
import com.mochyy.mochymusic.module.TrackQuery
import com.music.bitchord.ui.mochy.utils.youtubeIdOrNull
import com.music.bitchord.download.DownloadSession
import com.music.bitchord.download.Downloads
import com.music.bitchord.ui.mochy.constants.AudioQuality
import com.music.bitchord.ui.mochy.constants.DownloadQualityKey
import com.music.bitchord.ui.mochy.constants.MochyDownloadQuality
import com.music.bitchord.ui.mochy.constants.OfflineModeKey
import com.music.bitchord.ui.mochy.utils.PreferencesCache
import com.music.bitchord.ui.mochy.lyrics.serializeLyricLines
import com.music.bitchord.ui.mochy.utils.dataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor

@OptIn(UnstableApi::class)
object MochyDownloadUtil {

    private const val TAG = "MochyDownload"

    /** The Classic path's source-lookup bound; a module search can run this long. */
    private const val SOURCE_LOOKUP_MS = 85_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var manager: DownloadManager? = null

    @Volatile
    private var notificationHelper: DownloadNotificationHelper? = null

    private val _downloads = MutableStateFlow<Map<String, Download>>(emptyMap())

    /** Every download the manager knows, by song id (Mochy's downloads flow). */
    val downloads: StateFlow<Map<String, Download>> = _downloads.asStateFlow()

    /** one song's download as it changes (Mochy's DownloadUtil.getDownload). */
    fun getDownload(songId: String): kotlinx.coroutines.flow.Flow<Download?> =
        downloads.map { it[songId] }.distinctUntilChanged()

    /** Each song's stream url and when it stops working (ms since 1970); read on the download threads. */
    private val songUrlCache = ConcurrentHashMap<String, Pair<String, Long>>()

    /** Per-track stream request headers, so a cached url is replayed with its client's Origin. */
    private val songStreamHeaders = ConcurrentHashMap<String, Map<String, String>>()

    /** The YouTube format each download started in, so a resumed one can't carry on in another. */
    private val downloadItags = ConcurrentHashMap<String, Int>()

    /** A source's file for each song being downloaded from one, looked up once. */
    private class SourceDownload(val url: String, val headers: Map<String, String>, val badge: String?)

    private val sourceDownloads = ConcurrentHashMap<String, SourceDownload>()

    /** The row each download was asked for as, for the export and the progress list. */
    private val askedSongs = ConcurrentHashMap<String, Song>()

    /** The display badge each download resolved with, for the export's record. */
    private val badges = ConcurrentHashMap<String, String>()

    /**
     * Builds the manager and loads what it already knows. Call once,
     * on the main thread (BitChordApplication does, beside
     * Downloads.init): the manager may only be read on the thread it
     * was made on, which is why the progress follower reads it there.
     */
    fun init(context: Context) {
        if (manager != null) return
        val app = context.applicationContext
        val downloadManager = DownloadManager(
            app,
            MochyCaches.databaseProvider(app),
            MochyCaches.downloadCache(app),
            downloadDataSourceFactory(app),
            Executor(Runnable::run),
        ).apply {
            maxParallelDownloads = 3
            addListener(downloadListener)
        }
        manager = downloadManager
        notificationHelper = DownloadNotificationHelper(app, MochyExoDownloadService.CHANNEL_ID)

        val result = mutableMapOf<String, Download>()
        downloadManager.downloadIndex.getDownloads().use { cursor ->
            while (cursor.moveToNext()) {
                result[cursor.download.request.id] = cursor.download
            }
        }
        _downloads.value = result
        // Downloads that outlived the process show again in the Modern
        // surfaces: mirror what is still under way (their rows come
        // back from Room, since the session list died with the process).
        result.values.filter { it.isUnderWay }.forEach { download ->
            mirrorUnderWay(app, download)
        }
        scope.launch { followProgress() }
        scope.launch { followOfflineMode(app) }
    }

    fun downloadManager(): DownloadManager? = manager

    fun downloadNotificationHelper(): DownloadNotificationHelper? = notificationHelper

    /** Whether the Mochy engine holds a download (any state) for [songId]. */
    fun hasDownload(songId: String): Boolean = _downloads.value.containsKey(songId)

    /**
     * Queues [song] on the Mochy engine. The mobile-data gate and the
     * progress row are Downloads.enqueue's, already done by the time
     * this is called; a track already saved, queued or running is left
     * alone rather than doubled.
     */
    fun enqueue(context: Context, song: Song) {
        val app = context.applicationContext
        val id = song.videoId
        // An imported file has no online copy: looking it up by name
        // would download some other recording under its id (Mochy).
        // A track already carrying its file is on the device; the
        // caller offers removal for those, not another download.
        if (id.startsWith("LOCAL_") || song.localUri != null) return
        val known = _downloads.value[id]
        if (known != null && known.state != Download.STATE_FAILED) return
        askedSongs[id] = song
        val request = DownloadRequest.Builder(id, id.toUri())
            .setCustomCacheKey(id)
            .setData(song.title.toByteArray())
            .build()
        // Android won't start the service from the background, so the
        // manager takes it straight then (Mochy's startAutoDownload).
        runCatching {
            DownloadService.sendAddDownload(app, MochyExoDownloadService::class.java, request, false)
        }.onFailure {
            mainHandler.post { runCatching { manager?.addDownload(request) } }
        }
    }

    /**
     * Calls off an under-way download (Mochy's remove). A finished
     * download is not touched: cancelling is about what is still
     * coming, and deleting what arrived is Downloads.delete's job.
     */
    fun cancel(videoId: String) {
        val known = _downloads.value[videoId] ?: return
        if (known.state == Download.STATE_COMPLETED) return
        remove(videoId)
    }

    /** Removes the cache download and its Room flag, whatever its state (Downloads.delete's other half). */
    fun removeDownload(videoId: String) {
        if (_downloads.value.containsKey(videoId)) remove(videoId)
    }

    private fun remove(videoId: String) {
        val app = appContext ?: return
        runCatching {
            DownloadService.sendRemoveDownload(app, MochyExoDownloadService::class.java, videoId, false)
        }.onFailure {
            mainHandler.post { runCatching { manager?.removeDownload(videoId) } }
        }
    }

    @Volatile
    private var appContext: Context? = null

    // ---- the download data source ------------------------------------------
    // Straight from the network, never through the player's cache. The
    // player caches the stream it played, which is often another format
    // than the download asks for. A download that took the cached start
    // from there and the rest from its own stream was two files glued
    // together (Mochy).

    private fun downloadDataSourceFactory(context: Context): ResolvingDataSource.Factory {
        appContext = context
        return ResolvingDataSource.Factory(
            OkHttpDataSource.Factory(Http.client),
        ) { dataSpec ->
            val mediaId = dataSpec.key ?: error("No media id")
            if (mediaId.startsWith("LOCAL_")) {
                throw IOException("$mediaId is a file on this phone, there's nothing to download")
            }

            sourceDownload(context, mediaId)?.let { found ->
                return@Factory dataSpec.buildUpon()
                    .setUri(found.url.toUri())
                    .setHttpRequestHeaders(dataSpec.httpRequestHeaders + found.headers)
                    .build()
            }

            // Only while it still works.
            songUrlCache[mediaId]?.takeIf { it.second > System.currentTimeMillis() }?.let {
                return@Factory dataSpec.buildUpon()
                    .setUri(it.first.toUri())
                    .setHttpRequestHeaders(dataSpec.httpRequestHeaders + songStreamHeaders[mediaId].orEmpty())
                    .build()
            }

            // Batch 53: the rung comes from the Mochy DataStore
            // (DownloadQualityKey), as in Mochy's DownloadUtil — Modern's
            // quality screen writes it there. AppSettings.exportDownloads
            // stays: exporting finished downloads into Music/ChordWave
            // is ChordWave's own feature (Tahap 5), and its AAC pin has
            // no Mochy counterpart.
            val quality = downloadQuality(context)
            val resolved = runBlocking(Dispatchers.IO) {
                MochyStreamResolver.resolveForDownload(
                    mediaId,
                    quality.toAudioQuality(),
                    requireM4a = AppSettings.exportDownloads.value,
                )
            }
            val format = resolved.format

            // Picking up part way with a fresh url that gives another
            // format would glue two files together. Fail instead: the
            // failed download is wiped (see forgetStream), so trying
            // again starts the whole song over in one format (Mochy).
            val startedItag = downloadItags.put(mediaId, format.itag)
            if (dataSpec.position > 0 && startedItag != null && startedItag != format.itag) {
                throw IOException("Stream format changed part way through the download of $mediaId")
            }

            runBlocking(Dispatchers.IO) {
                val db = MochyDatabaseHolder.get(context)
                db.upsertFormat(
                    FormatEntity(
                        id = mediaId,
                        itag = format.itag,
                        mimeType = format.mimeType.split(";")[0],
                        codecs = format.mimeType.substringAfter("codecs=", "").removeSurrounding("\""),
                        bitrate = format.bitrate,
                        sampleRate = format.audioSampleRate,
                        // Some formats don't say; the real size is saved
                        // when the download finishes (Mochy).
                        contentLength = format.contentLength ?: 0L,
                        loudnessDb = resolved.audioConfig?.loudnessDb,
                        perceptualLoudnessDb = resolved.audioConfig?.perceptualLoudnessDb,
                        playbackUrl = resolved.playbackTrackingUrl,
                    ),
                )
                ensureSongRow(context, mediaId)
            }

            val streamUrl = resolved.url.let {
                "${it}&range=0-${format.contentLength ?: 10000000}"
            }
            songUrlCache[mediaId] = streamUrl to resolved.expiresAtMs
            songStreamHeaders[mediaId] = resolved.headers
            badges[mediaId] = com.music.bitchord.download.youtubeDownloadBadge(
                extension = if (format.mimeType.contains("mp4")) "m4a" else "webm",
                kbps = format.bitrate / 1000,
            ) ?: ""
            dataSpec.buildUpon()
                .setUri(streamUrl.toUri())
                .setHttpRequestHeaders(dataSpec.httpRequestHeaders + resolved.headers)
                .build()
        }
    }

    /**
     * A provider's whole file for [mediaId], or null when YouTube's turn
     * comes — Mochy's moduleDownload, through SourceModules.downloadRoute
     * (batch 55b). The walk itself only keeps progressive, DRM-free files
     * this phone can decode. A row picked from a source is fetched from a
     * provider as the row it is, and never from YouTube: its id is not a
     * video id.
     */
    private fun sourceDownload(context: Context, mediaId: String): SourceDownload? {
        sourceDownloads[mediaId]?.let { return it }
        val song = askedSongs[mediaId] ?: runBlocking(Dispatchers.IO) { songFromRoom(context, mediaId) }
            ?: return null

        val row = SourceRegistry.parseTrackKey(mediaId)
        val stream = runBlocking(Dispatchers.IO) {
            val track = ModulePlaybackBridge.trackQueryOf(context, mediaId) ?: TrackQuery(
                title = song.title,
                artists = song.artists.map { it.name }.ifEmpty { listOf(song.artist) },
                album = song.albumName,
                videoId = youtubeIdOrNull(mediaId),
            )
            withTimeoutOrNull(SOURCE_LOOKUP_MS) {
                ModulePlaybackBridge.downloadStream(context, mediaId, track)
            }
        }
        if (stream == null) {
            if (row != null) {
                throw IOException("Couldn't download this song from its providers — check that they're still connected")
            }
            return null
        }

        val (mimeType, codecs) = savedFormatOf(stream.codec)
        runBlocking(Dispatchers.IO) {
            val db = MochyDatabaseHolder.get(context)
            db.upsertFormat(
                FormatEntity(
                    id = mediaId,
                    itag = -1,
                    mimeType = mimeType,
                    codecs = codecs,
                    bitrate = 0,
                    sampleRate = stream.sampleRateHz,
                    // Filled in when the download finishes (Mochy).
                    contentLength = 0L,
                    loudnessDb = null,
                    perceptualLoudnessDb = null,
                    playbackUrl = null,
                ),
            )
            ensureSongRow(context, mediaId)
        }
        val badge = stream.qualityLabel.takeIf { it.isNotBlank() }
        if (badge != null) badges[mediaId] = badge
        return SourceDownload(stream.url, stream.headers, badge).also { sourceDownloads[mediaId] = it }
    }

    // The saved file as the format badges read it, from the codec the source named (Mochy).
    private fun savedFormatOf(codec: String?): Pair<String, String> = when (codec?.uppercase()) {
        "FLAC" -> "audio/flac" to "flac"
        "ALAC" -> "audio/mp4" to "alac"
        "AAC", "HE-AAC" -> "audio/mp4" to "aac"
        "MP3" -> "audio/mpeg" to "mp3"
        "OPUS" -> "audio/ogg" to "opus"
        "E-AC-3", "DOLBY ATMOS" -> "audio/mp4" to "ec-3"
        else -> "audio/*" to codec?.lowercase().orEmpty()
    }

    /** The song row a download needs, created or dated (Mochy's resolve-time upsert). */
    private suspend fun ensureSongRow(context: Context, mediaId: String) {
        val db = MochyDatabaseHolder.get(context)
        val existing = db.getSongById(mediaId)?.song
        val now = LocalDateTime.now()
        when {
            existing == null -> {
                val asked = askedSongs[mediaId]
                db.upsertSong(
                    SongEntity(
                        id = mediaId,
                        title = asked?.title ?: "Unknown",
                        thumbnailUrl = asked?.thumbnailUrl,
                        albumName = asked?.albumName,
                        dateDownload = now,
                        isDownloaded = false,
                    ),
                )
            }
            existing.dateDownload == null -> db.upsertSong(existing.copy(dateDownload = now))
        }
    }

    private suspend fun songFromRoom(context: Context, mediaId: String): Song? {
        val room = MochyDatabaseHolder.get(context).getSongById(mediaId) ?: return null
        return Song(
            videoId = room.song.id,
            title = room.song.title,
            artist = room.artists.joinToString(", ") { it.name },
            thumbnailUrl = room.song.thumbnailUrl,
            albumName = room.song.albumName,
        ).also { askedSongs[mediaId] = it }
    }

    // ---- manager listener ----------------------------------------------------

    private val downloadListener = object : DownloadManager.Listener {
        override fun onDownloadChanged(
            downloadManager: DownloadManager,
            download: Download,
            finalException: Exception?,
        ) {
            val id = download.request.id
            if (download.state == Download.STATE_REMOVING) {
                _downloads.update { it - id }
            } else {
                _downloads.update { map -> map.toMutableMap().apply { set(id, download) } }
            }
            val app = appContext ?: return
            scope.launch {
                when (download.state) {
                    Download.STATE_COMPLETED -> onCompleted(app, download)
                    Download.STATE_FAILED -> {
                        runCatching {
                            MochyDatabaseHolder.get(app).updateDownloadedInfo(id, false, null)
                        }
                        forgetStream(id)
                        // Kept in release builds, unlike Timber: the reason is the only clue (Mochy).
                        android.util.Log.w(TAG, "Download of $id failed", finalException)
                        Downloads.mirrorMochyFailed(id, friendlyFailure(finalException))
                    }
                    Download.STATE_STOPPED -> {
                        runCatching {
                            MochyDatabaseHolder.get(app).updateDownloadedInfo(id, false, null)
                        }
                        Downloads.mirrorMochyQueued(id)
                    }
                    Download.STATE_REMOVING -> {
                        runCatching {
                            MochyDatabaseHolder.get(app).updateDownloadedInfo(id, false, null)
                        }
                        forgetStream(id)
                        Downloads.mirrorMochyCleared(id)
                    }
                    else -> Unit
                }
            }
        }
    }

    private suspend fun onCompleted(context: Context, download: Download) {
        val id = download.request.id
        val db = MochyDatabaseHolder.get(context)
        runCatching { db.updateDownloadedInfo(id, true, LocalDateTime.now()) }
        recordDownloadedLength(context, download)
        sourceDownloads.remove(id)
        downloadItags.remove(id)
        val song = askedSongs[id] ?: songFromRoom(context, id)
        val exported = song?.let {
            MochyDownloadExporter.exportSong(context, it, badges[id]?.takeIf { b -> b.isNotEmpty() })
        }
        if (song != null && exported != null) {
            Downloads.mirrorMochyDone(song, exported.uri, exported.downloadFormat, exported.artworkUri)
        } else {
            // The cache holds the song even when the folder could not
            // take it: done, but with no saved record to play from in
            // Classic. The Mochy chain reads the download cache.
            Downloads.mirrorMochyDoneCacheOnly(id)
        }
        askedSongs.remove(id)
        badges.remove(id)
        saveLyrics(context, id)
    }

    /**
     * The downloaded file's real size into the song's format record.
     * Playback checks the download holds the whole song against that
     * size, and a FLAC is several times the size YouTube's stream was
     * recorded at (Mochy).
     */
    private suspend fun recordDownloadedLength(context: Context, download: Download) {
        val length = download.contentLength.takeIf { it > 0 } ?: return
        val db = MochyDatabaseHolder.get(context)
        val format = db.format(download.request.id).first() ?: return
        if (format.contentLength != length) db.upsertFormat(format.copy(contentLength = length))
    }

    // A failed download retries with a fresh stream, and drops its bytes so two formats never get glued together (Mochy).
    private fun forgetStream(mediaId: String) {
        songUrlCache.remove(mediaId)
        songStreamHeaders.remove(mediaId)
        downloadItags.remove(mediaId)
        sourceDownloads.remove(mediaId)
        MochyStreamResolver.forget(mediaId)
        val app = appContext ?: return
        runCatching { MochyCaches.downloadCache(app).removeResource(mediaId) }
    }

    /** One lyrics lookup at a time: an album finishing would otherwise ask every provider at once per song (Mochy). */
    private val lyricsLock = Mutex()

    /**
     * A downloaded song's lyrics, saved with it so they're there
     * offline (Mochy). A song that already has lyrics keeps them; one
     * saved as "not found" is asked again.
     */
    private suspend fun saveLyrics(context: Context, songId: String) {
        lyricsLock.withLock {
            val db = MochyDatabaseHolder.get(context)
            val stored = db.lyrics(songId).first()
            if (stored != null && stored.lyrics != LyricsEntity.LYRICS_NOT_FOUND) return
            val room = db.getSongById(songId) ?: return
            val found = runCatching {
                LyricsRepository.lyrics(
                    videoId = songId,
                    title = room.song.title,
                    artist = room.artists.joinToString(", ") { it.name },
                    durationMs = room.song.duration.takeIf { it > 0 }?.times(1000L) ?: 0L,
                    album = room.song.albumName ?: room.album?.title,
                    sources = AppSettings.lyricsSources.value,
                    order = AppSettings.lyricsSourceOrder.value,
                    prioritizeSyllableSync = AppSettings.prioritizeSyllableSync.value,
                )
            }.getOrNull() ?: return
            db.upsertLyrics(
                LyricsEntity(
                    id = songId,
                    lyrics = serializeLyricLines(found.lines),
                    provider = found.source.name,
                ),
            )
        }
    }

    // ---- progress + offline mode --------------------------------------------

    private val Download.isUnderWay: Boolean
        get() = state == Download.STATE_QUEUED || state == Download.STATE_DOWNLOADING || state == Download.STATE_RESTARTING

    /**
     * Mirrors progress into Downloads.active/DownloadSession while
     * anything downloads (Mochy's followBatches, twice a second; the
     * manager only reports starts and finishes, never how far along).
     * Nothing is polled while idle.
     */
    private suspend fun followProgress() {
        while (true) {
            _downloads.first { map -> map.values.any { it.isUnderWay } }
            while (true) {
                // The manager may only be read on the thread it was made on.
                val running = withContext(Dispatchers.Main) {
                    manager?.currentDownloads?.filter { it.isUnderWay }.orEmpty()
                }
                running.forEach { download ->
                    val fraction = (download.percentDownloaded / 100f).coerceIn(0f, 1f)
                    if (download.state == Download.STATE_DOWNLOADING) {
                        Downloads.mirrorMochyRunning(download.request.id, fraction)
                    } else {
                        Downloads.mirrorMochyQueued(download.request.id)
                    }
                }
                if (running.isEmpty()) break
                delay(500)
            }
            delay(1_000)
        }
    }

    private fun mirrorUnderWay(context: Context, download: Download) {
        val id = download.request.id
        Downloads.mirrorMochyQueued(id)
        scope.launch {
            val song = songFromRoom(context, id) ?: return@launch
            DownloadSession.queued(song)
            if (download.state == Download.STATE_DOWNLOADING) {
                Downloads.mirrorMochyRunning(id, (download.percentDownloaded / 100f).coerceIn(0f, 1f))
            }
        }
    }

    // Offline mode holds downloads where they are rather than failing
    // them: they carry on when it's turned off. Only then — resuming
    // whenever offline mode reads off would undo downloads paused for
    // any other reason (Mochy).
    private suspend fun followOfflineMode(context: Context) {
        var wasOffline = false
        context.dataStore.data
            .map { it[OfflineModeKey] ?: false }
            .distinctUntilChanged()
            .collect { offline ->
                withContext(Dispatchers.Main) {
                    if (offline) {
                        manager?.pauseDownloads()
                    } else if (wasOffline) {
                        manager?.resumeDownloads()
                    }
                }
                wasOffline = offline
            }
    }

    private fun friendlyFailure(error: Exception?): String {
        val message = error?.message?.takeIf { it.isNotBlank() }
        return message ?: "Download failed"
    }

    /** The stored download rung (Mochy's DownloadQualityKey, HIGH by default). */
    private fun downloadQuality(context: Context): MochyDownloadQuality =
        runCatching {
            MochyDownloadQuality.valueOf(
                PreferencesCache.current(context.dataStore)[DownloadQualityKey]
                    ?: MochyDownloadQuality.HIGH.name,
            )
        }.getOrDefault(MochyDownloadQuality.HIGH)

    /** The download rung as the resolver's quality (Mochy's mapping). */
    private fun MochyDownloadQuality.toAudioQuality(): AudioQuality = when (this) {
        MochyDownloadQuality.HIGH -> AudioQuality.HIGH
        MochyDownloadQuality.MEDIUM -> AudioQuality.MEDIUM
        MochyDownloadQuality.LOW -> AudioQuality.LOW
    }

}
