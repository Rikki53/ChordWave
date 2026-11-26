// MochyStreamResolver — the Mochy engine's stream resolution for ChordWave's
// two-engine architecture (RENCANA-DUA-MESIN.md, Tahap 4).
//
// This is Mochy's YTPlayerUtils.playerResponseForPlayback reduced to the part
// that decides which URL the player opens, running on the Mochy innertube
// already ported into :mochyengine (com.music.innertube.YouTube):
//  - the same client order (VISIONOS first — the only client Mochy measured
//    serving a complete track without a PoToken — then the web clients, then
//    the rest), walked until one answers with a playable response;
//  - the same format ranking (YouTube's AUDIO_QUALITY_* label outranks raw
//    bitrate; LOW/MEDIUM land on their bitrate ceilings, HIGH takes the best;
//    the original non-dubbed track wins when both are offered);
//  - the same deciphering (the innertube NewPipe bridge deobfuscates the
//    signature and the throttling parameter), with a full NewPipe extraction
//    as the last resort;
//  - the same per-client stream headers googlevideo checks against the
//    url's own c= parameter.
//
// Tahap 6: PoToken minting for the web clients (useWebPoTokens) through the
// shared BotGuard minter the Classic engine uses (PoTokenGenerator +
// PoTokenWebView), minted lazily on the first client that needs one — the
// VISIONOS-first walk still serves most tracks untokened, exactly as Mochy
// measured. What is still NOT here (recorded honestly): the SABR/EJS
// n-transform solver, bot-check resting, studio-version substitution and
// playback tracking. A track only SABR can serve still fails here; the
// Classic engine's resolver is untouched and remains one mode-switch away.
//
// Resolved URLs are remembered until they expire (streamingData's own
// expiresInSeconds), the way Mochy's MusicService keeps its songUrlCache.

package com.music.bitchord.playback.mochy

import android.content.Context
import android.webkit.CookieManager
import com.music.bitchord.data.innertube.potoken.PoTokenGenerator
import com.music.bitchord.data.innertube.potoken.PoTokenWebView
import com.music.bitchord.ui.mochy.constants.AudioQuality
import com.music.bitchord.ui.mochy.utils.DataSaver
import com.music.innertube.NewPipeExtractor
import com.music.innertube.YouTube
import com.music.innertube.models.YouTubeClient
import com.music.innertube.models.response.PlayerResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap

/** A stream the Mochy engine can open: the URL, what it holds, and the headers it wants. */
data class MochyResolvedStream(
    val url: String,
    val format: PlayerResponse.StreamingData.Format,
    val headers: Map<String, String>,
    val expiresAtMs: Long,
    val audioConfig: PlayerResponse.PlayerConfig.AudioConfig?,
    val playbackTrackingUrl: String?,
)

object MochyStreamResolver {

    private const val TAG = "MochyStreamResolver"

    /** Mochy's main client: VISIONOS, the one measured to serve a whole track untokened. */
    private val MAIN_CLIENT: YouTubeClient = YouTubeClient.VISIONOS

    // Mochy's fallback order, verbatim: VISIONOS, the PoToken-capable web
    // clients, then the remaining clients; IOS/IPADOS/ANDROID_VR last
    // because their clean urls stop dead at the ~786 KB untokened window.
    private val STREAM_FALLBACK_CLIENTS: Array<YouTubeClient> = arrayOf(
        YouTubeClient.VISIONOS,
        YouTubeClient.WEB_REMIX,
        YouTubeClient.WEB,
        YouTubeClient.WEB_CREATOR,
        YouTubeClient.IPADOS,
        YouTubeClient.ANDROID_VR_1_43_32,
        YouTubeClient.ANDROID_VR_1_61_48,
        YouTubeClient.ANDROID_VR_1_65_10,
        YouTubeClient.ANDROID_VR_NO_AUTH,
        YouTubeClient.TVHTML5_SIMPLY_EMBEDDED_PLAYER,
        YouTubeClient.TVHTML5,
        YouTubeClient.ANDROID_CREATOR,
        YouTubeClient.MOBILE,
    )

    private val urlCache = ConcurrentHashMap<String, MochyResolvedStream>()

    // BotGuard PoTokens for the web clients, minted in the same hidden
    // WebView the Classic engine's InnerTubeX path uses. Needs an
    // application context (the WebView's), handed over by [init] from the
    // data-source factory / service; without one, resolves simply go
    // untokened, as they did before Tahap 6.
    private var appContext: Context? = null

    private val poTokenGenerator: PoTokenGenerator? by lazy {
        val app = appContext ?: return@lazy null
        val webViewSupported by lazy { runCatching { CookieManager.getInstance() }.isSuccess }
        PoTokenGenerator(
            createMinter = { PoTokenWebView.getNewPoTokenGenerator(app) },
            available = { webViewSupported },
        )
    }

    /** Hands the resolver the application context its PoToken minter needs. */
    fun init(context: Context) {
        appContext = context.applicationContext
    }

    /**
     * The player-request PoToken for [videoId] on a web client, or null when
     * the client needs none, no session id is known yet, or minting failed
     * (the walk then asks untokened, which is where it started).
     */
    private suspend fun poTokenFor(videoId: String, client: YouTubeClient): String? {
        if (!client.useWebPoTokens) return null
        val sessionId = YouTube.dataSyncId ?: YouTube.visitorData ?: return null
        return runCatching {
            poTokenGenerator?.getWebClientPoToken(videoId, sessionId)?.playerRequestPoToken
        }.onFailure { Timber.tag(TAG).w(it, "PoToken generation failed for $videoId") }.getOrNull()
    }

    /**
     * YouTube's loudness figure for a track this resolver has already
     * resolved, for the Mochy engine's loudness normalisation — read on the
     * audio thread, so it only touches the concurrent caches. Null while
     * nothing is known; the gain then glides in once a resolve lands, the
     * same lazy read the Classic engine's LoudnessProcessor makes.
     */
    fun loudnessDbFor(videoId: String): Double? =
        (urlCache[videoId] ?: downloadUrlCache[videoId])?.audioConfig?.loudnessDb

    // Downloads remember their URLs apart from playback's (Tahap 5): a
    // download asks for its own rung (the download-quality setting, AAC
    // when the file is headed for Music/ChordWave), which is often not
    // the rung playback picked, and Mochy keeps the two caches apart for
    // the same reason (MusicService.songUrlCache vs DownloadUtil's).
    private val downloadUrlCache = ConcurrentHashMap<String, MochyResolvedStream>()

    /** Drops remembered URLs (a quality change makes the remembered pick wrong). */
    fun forget(videoId: String? = null) {
        if (videoId == null) {
            urlCache.clear()
            downloadUrlCache.clear()
        } else {
            urlCache.remove(videoId)
            downloadUrlCache.remove(videoId)
        }
    }

    /**
     * Turns a videoId into a playable stream, walking Mochy's clients in
     * order. [quality] is the Modern audio-quality preference; [metered]
     * decides what AUTO means, exactly as in Mochy.
     */
    suspend fun resolve(
        videoId: String,
        quality: AudioQuality,
        metered: Boolean,
    ): MochyResolvedStream = withContext(Dispatchers.IO) {
        urlCache[videoId]?.takeIf { it.expiresAtMs > System.currentTimeMillis() }?.let { return@withContext it }

        var lastError: Throwable? = null
        var mintedToken: String? = null
        var mintTried = false
        val clients = (listOf(MAIN_CLIENT) + STREAM_FALLBACK_CLIENTS.toList()).distinct()
        for (client in clients) {
            try {
                val signatureTimestamp = if (client.useSignatureTimestamp) {
                    NewPipeExtractor.getSignatureTimestamp(videoId).getOrNull()
                } else {
                    null
                }
                if (client.useWebPoTokens && !mintTried) {
                    mintTried = true
                    mintedToken = poTokenFor(videoId, client)
                }
                val response = YouTube.player(videoId, null, client, signatureTimestamp, mintedToken).getOrThrow()
                if (response.playabilityStatus.status != "OK") {
                    Timber.tag(TAG).d("Client ${client.clientName} refused $videoId: ${response.playabilityStatus.status}")
                    continue
                }
                val candidates = selectAudioFormatCandidates(response, quality, metered)
                for (format in candidates) {
                    val url = NewPipeExtractor.getStreamUrl(format, videoId) ?: continue
                    val resolved = MochyResolvedStream(
                        url = url,
                        format = format,
                        headers = client.streamHeaders(),
                        expiresAtMs = System.currentTimeMillis() +
                            (response.streamingData?.expiresInSeconds ?: 3600) * 1000L,
                        audioConfig = response.playerConfig?.audioConfig,
                        playbackTrackingUrl = response.playbackTracking?.videostatsPlaybackUrl?.baseUrl,
                    )
                    urlCache[videoId] = resolved
                    Timber.tag(TAG).d("Resolved $videoId via ${client.clientName}: ${format.mimeType}@${format.bitrate}")
                    return@withContext resolved
                }
            } catch (e: Exception) {
                lastError = e
                Timber.tag(TAG).d(e, "Client ${client.clientName} failed for $videoId")
            }
        }

        // Last resort: a full NewPipe extraction of the watch page.
        val extracted = NewPipeExtractor.newPipePlayer(videoId)
        if (extracted.isNotEmpty()) {
            val (itag, url) = extracted.maxByOrNull { it.first }!!
            val resolved = MochyResolvedStream(
                url = url,
                format = PlayerResponse.StreamingData.Format(
                    itag = itag,
                    url = url,
                    mimeType = "audio/webm",
                    bitrate = 0,
                    width = null,
                    height = null,
                    contentLength = null,
                    quality = "",
                    fps = null,
                    qualityLabel = null,
                    averageBitrate = null,
                    audioQuality = null,
                    approxDurationMs = null,
                    audioSampleRate = null,
                    audioChannels = null,
                    loudnessDb = null,
                    lastModified = null,
                    signatureCipher = null,
                    cipher = null,
                    audioTrack = null,
                ),
                headers = MAIN_CLIENT.streamHeaders(),
                expiresAtMs = System.currentTimeMillis() + 3600_000L,
                audioConfig = null,
                playbackTrackingUrl = null,
            )
            urlCache[videoId] = resolved
            return@withContext resolved
        }

        throw IllegalStateException("No Mochy client could resolve $videoId", lastError)
    }

    /**
     * The download engine's resolve (Tahap 5): the same client walk as
     * [resolve], but the format is picked for keeping — [quality] is the
     * download rung, and when [requireM4a] the pick is pinned to
     * AAC-in-MP4, because the file is headed for Music/ChordWave through
     * the media store, which refuses audio/webm on affected Android
     * versions (the same pin the Classic download path applies). Falls
     * back to the best format when no AAC rung exists rather than
     * failing a download YouTube could have served.
     */
    suspend fun resolveForDownload(
        videoId: String,
        quality: AudioQuality,
        requireM4a: Boolean,
    ): MochyResolvedStream = withContext(Dispatchers.IO) {
        downloadUrlCache[videoId]?.takeIf { it.expiresAtMs > System.currentTimeMillis() }?.let { return@withContext it }

        var lastError: Throwable? = null
        var mintedToken: String? = null
        var mintTried = false
        val clients = (listOf(MAIN_CLIENT) + STREAM_FALLBACK_CLIENTS.toList()).distinct()
        for (client in clients) {
            try {
                val signatureTimestamp = if (client.useSignatureTimestamp) {
                    NewPipeExtractor.getSignatureTimestamp(videoId).getOrNull()
                } else {
                    null
                }
                if (client.useWebPoTokens && !mintTried) {
                    mintTried = true
                    mintedToken = poTokenFor(videoId, client)
                }
                val response = YouTube.player(videoId, null, client, signatureTimestamp, mintedToken).getOrThrow()
                if (response.playabilityStatus.status != "OK") {
                    Timber.tag(TAG).d("Download: client ${client.clientName} refused $videoId: ${response.playabilityStatus.status}")
                    continue
                }
                val candidates = selectAudioFormatCandidates(response, quality, networkMetered = false)
                val ordered = if (requireM4a) {
                    candidates.filter { isM4a(it) } + candidates.filterNot { isM4a(it) }
                } else {
                    candidates
                }
                for (format in ordered) {
                    val url = NewPipeExtractor.getStreamUrl(format, videoId) ?: continue
                    val resolved = MochyResolvedStream(
                        url = url,
                        format = format,
                        headers = client.streamHeaders(),
                        expiresAtMs = System.currentTimeMillis() +
                            (response.streamingData?.expiresInSeconds ?: 3600) * 1000L,
                        audioConfig = response.playerConfig?.audioConfig,
                        playbackTrackingUrl = response.playbackTracking?.videostatsPlaybackUrl?.baseUrl,
                    )
                    downloadUrlCache[videoId] = resolved
                    Timber.tag(TAG).d("Download resolved $videoId via ${client.clientName}: ${format.mimeType}@${format.bitrate}")
                    return@withContext resolved
                }
            } catch (e: Exception) {
                lastError = e
                Timber.tag(TAG).d(e, "Download: client ${client.clientName} failed for $videoId")
            }
        }
        throw IllegalStateException("No Mochy client could resolve $videoId for download", lastError)
    }

    /** AAC-in-MP4: the container the media store keeps for Music/ChordWave. */
    private fun isM4a(format: PlayerResponse.StreamingData.Format): Boolean {
        val mime = format.mimeType.lowercase()
        return mime.startsWith("audio/mp4") || mime.contains("mp4a")
    }

    /**
     * Every usable audio format ordered best-first for [audioQuality] —
     * Mochy's selectAudioFormatCandidates, so a format whose URL cannot be
     * deciphered falls back to the next best instead of losing the client.
     */
    internal fun selectAudioFormatCandidates(
        playerResponse: PlayerResponse,
        audioQuality: AudioQuality,
        networkMetered: Boolean,
    ): List<PlayerResponse.StreamingData.Format> {
        val audioFormats = playerResponse.streamingData?.adaptiveFormats
            ?.filter { it.isAudio && it.bitrate > 0 }
            ?.filter { !it.url.isNullOrEmpty() || !it.signatureCipher.isNullOrEmpty() || !it.cipher.isNullOrEmpty() }
            .orEmpty()
        if (audioFormats.isEmpty()) return emptyList()

        // Prefer the original (non auto-dubbed) track when both are offered.
        val originalFormats = audioFormats.filter { it.isOriginal }
        val usableFormats = originalFormats.ifEmpty { audioFormats }

        val effectiveQuality = when {
            // Data saver keeps YouTube on its lightest stream, whatever was picked.
            DataSaver.isActive(networkMetered) -> AudioQuality.LOW
            audioQuality == AudioQuality.AUTO -> if (networkMetered) AudioQuality.MEDIUM else AudioQuality.HIGH
            // Lossless/Atmos tiers belong to source modules; on YouTube take its best.
            audioQuality == AudioQuality.LOSSLESS ||
                audioQuality == AudioQuality.HI_RES_LOSSLESS ||
                audioQuality == AudioQuality.DOLBY_ATMOS -> AudioQuality.HIGH
            else -> audioQuality
        }

        // YouTube's ladder is roughly ~50k, ~70k, ~128k AAC, ~160k Opus; the
        // MEDIUM ceiling sits just under the Opus rung so it lands on 128k.
        val targetBitrateBps = when (effectiveQuality) {
            AudioQuality.LOW -> 70_000
            AudioQuality.MEDIUM -> 135_000
            else -> null
        }

        fun qualityRank(format: PlayerResponse.StreamingData.Format): Int = when (format.audioQuality) {
            "AUDIO_QUALITY_HIGH" -> 3
            "AUDIO_QUALITY_MEDIUM" -> 2
            "AUDIO_QUALITY_LOW" -> 1
            else -> 0
        }

        val preferHigher = compareByDescending<PlayerResponse.StreamingData.Format> { qualityRank(it) }
            .thenByDescending { it.audioChannels ?: 2 }
            .thenByDescending { it.bitrate }
            .thenByDescending { codecRank(extractCodec(it.mimeType)) }
            .thenByDescending { !it.url.isNullOrEmpty() }
            .thenByDescending { it.audioSampleRate ?: 0 }

        val preferLowerAboveTarget = compareByDescending<PlayerResponse.StreamingData.Format> { !it.url.isNullOrEmpty() }
            .thenBy { it.bitrate }
            .thenByDescending { codecRank(extractCodec(it.mimeType)) }
            .thenByDescending { it.audioSampleRate ?: 0 }

        return if (targetBitrateBps == null) {
            usableFormats.sortedWith(preferHigher)
        } else {
            usableFormats.filter { it.bitrate <= targetBitrateBps }.sortedWith(preferHigher) +
                usableFormats.filter { it.bitrate > targetBitrateBps }.sortedWith(preferLowerAboveTarget)
        }
    }

    private fun extractCodec(mimeType: String): String? =
        Regex("""codecs="([^"]+)"""").find(mimeType)
            ?.groupValues?.getOrNull(1)
            ?.split(",")?.firstOrNull()?.trim()

    private fun codecRank(codec: String?): Int = when {
        codec.isNullOrBlank() -> 0
        codec.contains("opus", ignoreCase = true) -> 3
        codec.contains("mp4a", ignoreCase = true) -> 2
        else -> 1
    }

    /**
     * Headers googlevideo expects alongside a stream url, checked against
     * the client named in the url's own c= parameter (Mochy's streamHeaders).
     */
    internal fun YouTubeClient.streamHeaders(): Map<String, String> =
        buildMap {
            put("User-Agent", userAgent)
            put("Accept", "*/*")
            put("Accept-Language", "en-US,en;q=0.9")
            when (clientName) {
                "WEB_REMIX" -> {
                    put("Referer", "https://music.youtube.com/")
                    put("Origin", "https://music.youtube.com")
                }
                "WEB_CREATOR" -> {
                    put("Referer", "https://studio.youtube.com/")
                    put("Origin", "https://studio.youtube.com")
                }
                else -> {
                    put("Referer", "https://www.youtube.com/")
                    put("Origin", "https://www.youtube.com")
                }
            }
        }
}
