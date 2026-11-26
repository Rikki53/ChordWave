// ModulePlaybackBridge — batch 55b: the modules system's bridge into the
// Modern playback engine. Mochy keeps this state inside its MusicService
// (forcedRoutes, failedProviders, playingFrom, trackQueryOf); ChordWave's
// resolver lives in MochyDataSourceFactory, so the state lives here and the
// service, the factory, the downloads and the provider switch all read it.
//
// The walk itself is SourceModules' (Mochy original): forced provider first,
// then the provider order with the failed ones passed over, YouTube in the
// order means "play it from YouTube now".

package com.music.bitchord.playback.mochy

import android.content.Context
import android.net.Uri
import com.mochyy.mochymusic.module.ResolveResult
import com.mochyy.mochymusic.module.SourceStream
import com.mochyy.mochymusic.module.TrackQuery
import com.music.bitchord.data.mochydata.MochyDatabaseHolder
import com.music.bitchord.data.mochydata.modules.SourceModules
import com.music.bitchord.ui.mochy.di.ModulesEntryPoint
import com.music.bitchord.ui.mochy.utils.youtubeIdOrNull
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

object ModulePlaybackBridge {

    /** who served a song, for the stream info sheet's provider line. */
    data class PlayingFrom(val providerId: String, val name: String, val qualityLabel: String? = null)

    // a provider the user picked for one song (the force-change sheet)
    private val forcedRoutes = ConcurrentHashMap<String, String>()

    // providers that already failed on a song; passed over on the retry
    private val failedProviders = ConcurrentHashMap<String, MutableSet<String>>()

    private val playingFrom = ConcurrentHashMap<String, PlayingFrom>()

    // the queue's own copy of a song that isn't saved yet (Mochy reads it off the player)
    private val queuedTracks = ConcurrentHashMap<String, TrackQuery>()

    fun sourceModules(context: Context): SourceModules =
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            ModulesEntryPoint::class.java,
        ).sourceModules()

    fun forceRoute(mediaId: String, providerId: String) {
        forcedRoutes[mediaId] = providerId
    }

    fun forcedRoute(mediaId: String): String? = forcedRoutes[mediaId]

    fun clearForcedRoute(mediaId: String) {
        forcedRoutes.remove(mediaId)
    }

    fun markFailed(mediaId: String, providerId: String) {
        failedProviders.getOrPut(mediaId) { ConcurrentHashMap.newKeySet() }.add(providerId)
        // the picked provider just failed: the usual order takes over again
        if (forcedRoutes[mediaId] == providerId) forcedRoutes.remove(mediaId)
    }

    fun failed(mediaId: String): Set<String> = failedProviders[mediaId]?.toSet() ?: emptySet()

    /** a new song starts: the last song's failures and forced pick no longer hold. */
    fun onMediaItemChanged(mediaId: String?) {
        failedProviders.keys.retainAll { it == mediaId }
        forcedRoutes.keys.retainAll { it == mediaId }
    }

    fun notePlayingFrom(mediaId: String, from: PlayingFrom) {
        playingFrom[mediaId] = from
    }

    fun playingFrom(mediaId: String?): PlayingFrom? = mediaId?.let { playingFrom[it] }

    /** the service hands over the queue's copy of each song as it starts. */
    fun noteQueuedTrack(mediaId: String, track: TrackQuery) {
        queuedTracks[mediaId] = track
        track.videoId?.let { queuedTracks[it] = track }
    }

    /** the song as a module looks for it: its saved row, or the queue's own copy for a song played straight from search that isn't saved yet (Mochy). */
    suspend fun trackQueryOf(context: Context, mediaId: String): TrackQuery? {
        runCatching {
            MochyDatabaseHolder.get(context).song(mediaId).first()
        }.getOrNull()?.let {
            return TrackQuery(
                title = it.song.title,
                artists = it.artists.map { a -> a.name },
                album = it.song.albumName ?: it.album?.title,
                durationMs = it.song.duration.takeIf { d -> d > 0 }?.times(1000L),
                videoId = youtubeIdOrNull(mediaId),
            )
        }
        return queuedTracks[mediaId]
    }

    /** a bitchord://source row's own metadata, as the row was built (Classic's URI shape). */
    fun trackQueryOfSourceUri(uri: Uri): TrackQuery? {
        val title = uri.getQueryParameter("n")?.takeIf { it.isNotBlank() } ?: return null
        return TrackQuery(
            title = title,
            artists = listOfNotNull(uri.getQueryParameter("a")?.takeIf { it.isNotBlank() }),
            album = uri.getQueryParameter("l")?.takeIf { it.isNotBlank() },
            durationMs = uri.getQueryParameter("d")?.toLongOrNull()?.takeIf { it > 0 }?.times(1000L),
            videoId = null,
        )
    }

    /**
     * Mochy's routeStream for a YouTube song: the forced provider, then the
     * walk. Returns the module stream to play, or null to hand the song to
     * YouTube (by its video id). Throws when no provider can serve it and
     * YouTube is off or already failed.
     */
    suspend fun resolveWatch(context: Context, mediaId: String): SourceStream? {
        val modules = sourceModules(context)
        forcedRoutes[mediaId]?.let { forced ->
            if (forced == SourceModules.YouTubeId) return null
            forcedStream(context, modules, mediaId, forced)?.let { return it }
            // the picked provider doesn't have it any more: the usual order takes over
            forcedRoutes.remove(mediaId)
        }
        val skip = failed(mediaId)
        // songs that only YouTube has (and the common case of YouTube first) need no lookup
        val first = modules.order.value.firstOrNull { it !in skip && modules.isUsable(it) }
        if (first == SourceModules.YouTubeId) return null
        val track = trackQueryOf(context, mediaId)
        return when (val route = modules.route(track, skip)) {
            is SourceModules.Route.Module -> {
                notePlayingFrom(
                    mediaId,
                    PlayingFrom(route.module.info.id, route.module.info.name, route.stream.qualityLabel),
                )
                route.stream
            }
            SourceModules.Route.YouTube -> null
            SourceModules.Route.Exhausted -> {
                val youTube = SourceModules.YouTubeId
                when {
                    // YouTube is in the order after providers that don't have it: it plays there
                    youTube !in skip && modules.isUsable(youTube) -> null
                    // the user turned YouTube off to hear only their other providers
                    !modules.isEnabled(youTube) ->
                        throw IOException("none of your providers have it, and YouTube Music is off")
                    else -> throw IOException("every provider failed on it")
                }
            }
        }
    }

    /** a bitchord://source row in Modern: the modules walk on the row's own metadata. */
    suspend fun resolveSourceUri(context: Context, uri: Uri): SourceStream? {
        val modules = sourceModules(context)
        val mediaId = uri.toString()
        forcedRoutes[mediaId]?.let { forced ->
            if (forced != SourceModules.YouTubeId) {
                forcedStream(context, modules, mediaId, forced, trackQueryOfSourceUri(uri))?.let { return it }
            }
            forcedRoutes.remove(mediaId)
        }
        val track = trackQueryOfSourceUri(uri) ?: return null
        return when (val route = modules.route(track, failed(mediaId))) {
            is SourceModules.Route.Module -> {
                notePlayingFrom(
                    mediaId,
                    PlayingFrom(route.module.info.id, route.module.info.name, route.stream.qualityLabel),
                )
                route.stream
            }
            // a source row has no video id for YouTube to play by
            else -> null
        }
    }

    /** one named provider's stream for [mediaId], or null when it doesn't have the song. */
    suspend fun forcedStream(
        context: Context,
        modules: SourceModules,
        mediaId: String,
        providerId: String,
        knownTrack: TrackQuery? = null,
    ): SourceStream? {
        val module = modules.module(providerId) ?: return null
        val track = knownTrack ?: trackQueryOf(context, mediaId) ?: return null
        val result = runCatching { module.resolve(track, modules.playbackPicks(providerId, module)) }
            .getOrNull() ?: return null
        val stream = (result as? ResolveResult.Found)?.stream ?: return null
        notePlayingFrom(mediaId, PlayingFrom(module.info.id, module.info.name, stream.qualityLabel))
        return stream
    }

    /** the download walk: a module's whole file, or null when YouTube's turn comes. */
    suspend fun downloadStream(context: Context, mediaId: String, track: TrackQuery?): SourceStream? {
        val modules = sourceModules(context)
        return when (val route = modules.downloadRoute(track, failed(mediaId))) {
            is SourceModules.Route.Module -> route.stream
            else -> null
        }
    }
}
