// SongDataSourceFactory.kt — ONE shared DataSource.Factory that resolves a
// song's stream for MediaItems built by MediaMetadata.toMediaItem()
// (mediaId == song id; URI is localUri ?: id). Used by the share preview
// player (E1) and the share export (E2) — the same factory both places.
//
// It follows ChordWave's own player (PlaybackService): a ResolvingDataSource
// whose resolver turns the item's id into a stream URL via
// StreamResolver.resolve, wrapped in ChunkedDataSource (1 MB bounded ranges)
// over the app's single OkHttp client, with DefaultDataSource routing local
// file/content URIs to the platform sources before the resolver ever sees
// them. The resolver runs on ExoPlayer's loader thread and blocks there with
// runBlocking, exactly as PlaybackService does; StreamResolver memoises
// resolves, so asking twice for one song costs nothing past the first time.

package com.music.bitchord.ui.mochy.playback

import android.content.Context
import android.net.Uri
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.music.bitchord.data.Http
import com.music.bitchord.data.innertube.StreamResolver
import com.music.bitchord.playback.ChunkedDataSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.IOException

object SongDataSourceFactory {

    /** Same bound as PlaybackService's resolve: the NewPipe failsafe alone can take 45–90 s. */
    private const val ResolveTimeoutMs = 120_000L

    /** Same as PlaybackService's STREAM_CHUNK_BYTES. */
    private const val ChunkBytes = 1L * 1024 * 1024

    private val YouTubeId = Regex("[A-Za-z0-9_-]{11}")

    @Volatile
    private var shared: DataSource.Factory? = null

    /** The shared factory, built once for the app (factories are stateless; the HTTP client is shared). */
    fun create(context: Context): DataSource.Factory {
        shared?.let { return it }
        return synchronized(this) {
            shared ?: buildFactory(context.applicationContext).also { shared = it }
        }
    }

    private fun buildFactory(appContext: Context): DataSource.Factory {
        // Upstream first, resolver second — the same order PlaybackService builds it in.
        val resolving = ResolvingDataSource.Factory(
            ChunkedDataSource.Factory(OkHttpDataSource.Factory(Http.client), ChunkBytes),
            Resolver,
        )
        return DefaultDataSource.Factory(appContext, resolving)
    }

    private val Resolver = ResolvingDataSource.Resolver { dataSpec ->
        val videoId = videoIdOf(dataSpec.uri) ?: return@Resolver dataSpec
        val url = try {
            runBlocking {
                withTimeout(ResolveTimeoutMs) { StreamResolver.resolve(videoId) }
            }
        } catch (e: CancellationException) {
            throw IOException("Stream resolution timed out for $videoId", e)
        } catch (e: IOException) {
            throw e
        } catch (e: Exception) {
            throw IOException("Could not resolve a stream for $videoId: ${e.message}", e)
        }
        dataSpec.buildUpon()
            .setUri(Uri.parse(url))
            .setHttpRequestHeaders(StreamResolver.mediaHeadersFor(url))
            .build()
    }

    /**
     * The video id a URI stands for, or null when the URI is already a
     * playable address and must pass through untouched (a local file, or a
     * direct http(s) URL).
     */
    private fun videoIdOf(uri: Uri): String? {
        val scheme = uri.scheme
        if (scheme == null) {
            // A bare id: MediaMetadata.toMediaItem() sets the URI to the song
            // id itself when the song has no local file.
            val raw = uri.toString()
            if (raw.matches(YouTubeId)) return raw
            // A scheme-less path is a local file without a scheme, which this
            // factory cannot open — local songs must carry a content:// or
            // file:// URI. Fail loudly rather than fetching garbage.
            throw IOException("Cannot resolve a stream for scheme-less URI: $raw")
        }
        if (scheme == "content" || scheme == "file" || scheme == "android.resource" || scheme == "asset") return null
        if (scheme != "http" && scheme != "https") return null
        val host = uri.host.orEmpty()
        if (host.endsWith("youtube.com") || host == "youtu.be" || host.endsWith(".youtu.be")) {
            val id = uri.getQueryParameter("v") ?: uri.lastPathSegment
            if (!id.isNullOrBlank()) return id
        }
        return null
    }
}
