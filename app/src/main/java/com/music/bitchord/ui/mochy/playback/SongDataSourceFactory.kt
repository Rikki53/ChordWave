// SongDataSourceFactory.kt — ONE shared DataSource.Factory that resolves a
// song's stream for MediaItems built by MediaMetadata.toMediaItem()
// (mediaId == song id; URI is a local file, bitchord://watch?v=<id>,
// bitchord://source?… or a bare id). Used by the share preview
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
import com.music.bitchord.data.sources.SourceResolver
import com.music.bitchord.playback.ChunkedDataSource
import com.music.bitchord.playback.DIRECT_YOUTUBE_PARAMETER
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
        val uri = dataSpec.uri
        // A source-backed track (bitchord://source?…) is resolved by whichever
        // source can serve it, exactly as PlaybackService does; it carries no
        // `v` parameter and would otherwise fall straight through unresolved.
        // The Direct Source gate is deliberately not awaited: an export is the
        // listener asking for this track now, not read-ahead, and the gate
        // only opens for a track that is playing or next.
        if (uri.authority == "source") {
            val directYouTube = uri.getQueryParameter(DIRECT_YOUTUBE_PARAMETER) == "1"
            val stream = try {
                runBlocking {
                    withTimeout(ResolveTimeoutMs) {
                        if (directYouTube) {
                            SourceResolver.youtubeFallback(SourceResolver.targetIn(uri))
                        } else {
                            SourceResolver.resolve(uri)
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw IOException("Stream resolution timed out for $uri", e)
            } catch (e: IOException) {
                throw e
            } catch (e: Exception) {
                throw IOException("Could not resolve a stream for $uri: ${e.message}", e)
            } ?: throw IOException("No enabled source could serve $uri")
            return@Resolver dataSpec.buildUpon()
                .setUri(Uri.parse(stream.url))
                .setHttpRequestHeaders(
                    if (directYouTube) StreamResolver.mediaHeadersFor(stream.url) else stream.headers,
                )
                .build()
        }
        val videoId = videoIdOf(uri) ?: return@Resolver dataSpec
        // a device song that lost its file URI has nothing to resolve: fail
        // at once instead of blocking the loader on a 120 s YouTube lookup
        // for an id that was never a video id (batch 36)
        if (videoId.startsWith("LOCAL_")) throw IOException("Local song $videoId has no file URI to play from")
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
        // the app's own playback URIs (bitchord://watch?v=<id>, built by
        // Song.toMediaItem): the id rides in the `v` parameter whatever the
        // scheme, the same way PlaybackService reads it. Source tracks
        // (bitchord://source?…) are resolved in the resolver above and
        // never reach here.
        if (scheme == "bitchord") return uri.getQueryParameter("v")
        if (scheme != "http" && scheme != "https") return null
        val host = uri.host.orEmpty()
        if (host.endsWith("youtube.com") || host == "youtu.be" || host.endsWith(".youtu.be")) {
            val id = uri.getQueryParameter("v") ?: uri.lastPathSegment
            if (!id.isNullOrBlank()) return id
        }
        return null
    }
}
