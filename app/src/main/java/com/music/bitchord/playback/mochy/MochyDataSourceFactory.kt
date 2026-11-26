// MochyDataSourceFactory — the Mochy engine's data-source chain, shared
// by the player and the lyric share (two-engine Tahap 5).
//
// This is Mochy's MusicService.createDataSourceFactory in ChordWave's
// shape:
//  - reads go download cache -> player cache -> network (Mochy's
//    createCacheDataSource), so a downloaded song plays from its kept
//    bytes and a streamed one from the player cache, and only what is
//    in neither goes over the network; network reads are written to
//    the player cache only, never into the download cache (the outer
//    factory's write sink is null, as in Mochy);
//  - files already on the device (file/content URIs — downloads the
//    item builder substituted, local songs) bypass both caches: a
//    second copy of a file helps nobody (Mochy's LocalBypassDataSource;
//    Tahap 4's factory did the same for its single cache);
//  - the resolver turns the item's URI into a stream: bitchord://watch
//    through the Mochy innertube (MochyStreamResolver), bitchord://source
//    through ChordWave's Sources (the module system the Mochy service
//    already resolves source tracks through), everything else as it
//    arrived.
//
// The lyric share reads through [shareFactory] — Mochy's
// service.shareDataSourceFactory(), which is createDataSourceFactory()
// itself. That sameness is the whole point: the preview and the export
// hit the same caches and the same resolver as playback, so the audio
// in an exported lyric video is the recording the lyrics were synced
// to. Reading through a separate resolver instead (the pre-Tahap-5
// SongDataSourceFactory, on the Classic innertube) could export a
// different recording of the same song — batch 50's failure.

package com.music.bitchord.playback.mochy

import android.content.Context
import android.net.ConnectivityManager
import android.net.Uri
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.music.bitchord.data.Http
import com.music.bitchord.ui.mochy.constants.AudioQuality
import com.music.bitchord.ui.mochy.constants.AudioQualityKey
import com.music.bitchord.ui.mochy.utils.PreferencesCache
import com.music.bitchord.ui.mochy.utils.dataStore
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.IOException

@OptIn(UnstableApi::class)
object MochyDataSourceFactory {

    private const val RESOLVE_TIMEOUT_MS = 30_000L

    /**
     * The player's factory. [onResolved] hears about each YouTube
     * resolve (the service records the format row from it, as Mochy's
     * service does); the share passes none.
     */
    fun create(
        context: Context,
        onResolved: ((videoId: String, resolved: MochyResolvedStream) -> Unit)? = null,
    ): DataSource.Factory {
        // The resolver's PoToken minter needs an application context;
        // hand it over here too so share/download resolves that never
        // touch the service can still mint (Tahap 6).
        MochyStreamResolver.init(context)
        val app = context.applicationContext
        val upstream = LocalBypassFactory(
            DefaultDataSource.Factory(app),
            cacheFactory(app),
        )
        return ResolvingDataSource.Factory(upstream) { dataSpec ->
            resolveDataSpec(app, dataSpec, onResolved)
        }
    }

    /** The share's factory: the player's own (Mochy's shareDataSourceFactory). */
    fun shareFactory(context: Context): DataSource.Factory = create(context)

    /** Mochy's createCacheDataSource: downloads first, then the player cache, then the network. */
    private fun cacheFactory(context: Context): DataSource.Factory =
        CacheDataSource.Factory()
            .setCache(MochyCaches.downloadCache(context))
            .setUpstreamDataSourceFactory(
                CacheDataSource.Factory()
                    .setCache(MochyCaches.playerCache(context))
                    .setUpstreamDataSourceFactory(
                        DefaultDataSource.Factory(context, OkHttpDataSource.Factory(Http.client)),
                    )
                    .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR),
            )
            .setCacheWriteDataSinkFactory(null)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

    /**
     * The resolver: bitchord://watch?v=… through the modules walk first
     * (batch 55b — a module earlier in the provider order serves it, else
     * the Mochy innertube), bitchord://source through the modules walk on
     * the row's own metadata, everything else (files, content, radio
     * streams) as it arrived. Moved here from MochyMusicService (Tahap 4)
     * so the player and the share resolve identically.
     */
    private fun resolveDataSpec(
        context: Context,
        dataSpec: DataSpec,
        onResolved: ((String, MochyResolvedStream) -> Unit)?,
    ): DataSpec {
        val uri = dataSpec.uri
        if (uri.authority == "source") {
            val stream = runBlocking {
                withTimeout(RESOLVE_TIMEOUT_MS) { ModulePlaybackBridge.resolveSourceUri(context, uri) }
            } ?: throw IOException("No provider could serve ${uri.getQueryParameter("n")}")
            return dataSpec.buildUpon()
                .setUri(Uri.parse(stream.url))
                .setHttpRequestHeaders(stream.headers)
                .build()
        }
        val videoId = uri.getQueryParameter("v") ?: return dataSpec
        // the modules walk (Mochy's routeStream): a module earlier in the
        // provider order serves the song; null hands it to YouTube below
        val moduleStream = runBlocking {
            withTimeout(RESOLVE_TIMEOUT_MS) {
                ModulePlaybackBridge.resolveWatch(context, dataSpec.key ?: videoId)
            }
        }
        if (moduleStream != null) {
            return dataSpec.buildUpon()
                .setUri(Uri.parse(moduleStream.url))
                .setHttpRequestHeaders(moduleStream.headers)
                .build()
        }
        val resolved = runBlocking {
            withTimeout(RESOLVE_TIMEOUT_MS) {
                MochyStreamResolver.resolve(videoId, currentAudioQuality(context), isMetered(context))
            }
        }
        onResolved?.invoke(videoId, resolved)
        return dataSpec.buildUpon()
            .setUri(resolved.url.toUri())
            .setHttpRequestHeaders(resolved.headers)
            .build()
    }

    private fun currentAudioQuality(context: Context): AudioQuality {
        val raw = runCatching {
            PreferencesCache.current(context.dataStore)[AudioQualityKey]
        }.getOrNull()
        return runCatching { AudioQuality.valueOf(raw ?: AudioQuality.AUTO.name) }
            .getOrDefault(AudioQuality.AUTO)
    }

    private fun isMetered(context: Context): Boolean = runCatching {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        cm?.isActiveNetworkMetered == true
    }.getOrDefault(false)

    /**
     * Songs already on the phone are read straight from their file; only
     * what comes over the network goes through the caches (Mochy's
     * LocalBypassDataSource). A live radio stream (an http key, no media
     * id to cache under) also reads direct, as Tahap 4's factory had it.
     */
    private class LocalBypassFactory(
        private val directFactory: DataSource.Factory,
        private val cacheFactory: DataSource.Factory,
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource = object : DataSource {
            private var delegate: DataSource? = null

            override fun addTransferListener(transferListener: TransferListener) {
                delegate?.addTransferListener(transferListener)
            }

            override fun open(dataSpec: DataSpec): Long {
                val scheme = dataSpec.uri.scheme
                val direct = scheme == "file" || scheme == "content" ||
                    dataSpec.key == null || dataSpec.key!!.startsWith("http")
                val chosen = if (direct) directFactory.createDataSource() else cacheFactory.createDataSource()
                delegate = chosen
                return chosen.open(dataSpec)
            }

            override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
                delegate?.read(buffer, offset, length) ?: C.RESULT_END_OF_INPUT

            override fun getUri(): Uri? = delegate?.uri

            override fun getResponseHeaders(): Map<String, List<String>> =
                delegate?.responseHeaders ?: emptyMap()

            override fun close() {
                delegate?.close()
                delegate = null
            }
        }
    }
}
