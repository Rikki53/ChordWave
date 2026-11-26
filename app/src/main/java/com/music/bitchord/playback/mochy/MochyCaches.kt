// MochyCaches — the Mochy engine's two SimpleCaches and their database
// provider, opened once for the process (two-engine Tahap 5).
//
// In Mochy these are Hilt singletons (di/AppModule: @PlayerCache in
// filesDir/"exoplayer" with an LRU evictor sized by MaxSongCacheSizeKey,
// @DownloadCache in filesDir/"download" with NoOpCacheEvictor — downloads
// are never evicted). ChordWave's Mochy service is not a Hilt entry point,
// so the same singletons live here, opened lazily and never released:
// a SimpleCache folder can be opened by only one instance per process,
// and the player, the download engine and the lyric share must all read
// and write the same two caches or they would each hold a different
// recording of the same song.
//
// Tahap 4 kept the player cache in cacheDir/mochy_player_cache, owned by
// the service. It moves to Mochy's place (filesDir/exoplayer) here: the
// cache is keyed by media id and simply starts cold once; nothing else
// reads the old folder.

package com.music.bitchord.playback.mochy

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.DatabaseProvider
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

@OptIn(UnstableApi::class)
object MochyCaches {

    /** Mochy's default song-cache size (MaxSongCacheSizeKey's 1024 MB). */
    private const val PLAYER_CACHE_BYTES = 1024L * 1024 * 1024

    @Volatile
    private var provider: DatabaseProvider? = null

    @Volatile
    private var player: SimpleCache? = null

    @Volatile
    private var downloads: SimpleCache? = null

    fun databaseProvider(context: Context): DatabaseProvider =
        provider ?: synchronized(this) {
            provider ?: StandaloneDatabaseProvider(context.applicationContext).also { provider = it }
        }

    /** The player cache: streamed bytes, LRU-evicted (Mochy's @PlayerCache). */
    fun playerCache(context: Context): SimpleCache =
        player ?: synchronized(this) {
            player ?: SimpleCache(
                File(context.applicationContext.filesDir, "exoplayer"),
                LeastRecentlyUsedCacheEvictor(PLAYER_CACHE_BYTES),
                databaseProvider(context),
            ).also { player = it }
        }

    /** The download cache: kept songs, never evicted (Mochy's @DownloadCache). */
    fun downloadCache(context: Context): SimpleCache =
        downloads ?: synchronized(this) {
            downloads ?: SimpleCache(
                File(context.applicationContext.filesDir, "download"),
                NoOpCacheEvictor(),
                databaseProvider(context),
            ).also { downloads = it }
        }
}
