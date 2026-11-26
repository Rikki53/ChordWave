// Batch 55b: a lyrics module as one of the lyrics sources, from Mochy's
// lyrics/ModuleLyricsProvider.kt. The module registry only hands these out
// while the module is on; LyricsProviderRegistry keeps them by name.

package com.music.bitchord.ui.mochy.lyrics

import android.content.Context
import com.mochyy.mochymusic.module.LyricsModule
import com.mochyy.mochymusic.module.TrackQuery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ModuleLyricsProvider(private val module: LyricsModule) {
    val name = "module:" + module.info.id

    val displayName: String get() = module.info.name

    fun isEnabled(context: Context): Boolean = true

    suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): Result<String> = runCatching {
        val query = TrackQuery(title, listOf(artist), album, duration.takeIf { it > 0 }?.times(1000L))
        withContext(Dispatchers.IO) { module.lyrics(query) } ?: error(module.info.name + " has no lyrics for " + title)
    }
}
