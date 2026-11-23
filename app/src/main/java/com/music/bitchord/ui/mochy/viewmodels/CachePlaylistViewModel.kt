// Ported from Mochy Music (GPL-3.0): viewmodels/CachePlaylistViewModel.kt
// Mochy polled its player cache every second — player-cache keys minus
// download-cache keys, resolved to songs, keeping only fully-cached ones,
// sorted by dateDownload desc, filtered by HideExplicitKey /
// HideVideoSongsKey (the screen sorts and filters again itself, so the VM
// only has to supply the list).
//
// ChordWave's player cache IS exposed: AudioCache.cachedSongs() (the same
// source the Library adapter's Cached shelf polls). This VM polls it every
// 2s like the Library shelf does, keeps only complete copies, and maps each
// CachedSong.song via toMochySong() with the session like-state overlaid.
// The screen reads NO route arg (the {playlist} segment in its route is
// unused by Mochy too), so this VM has no load() — it starts polling in
// init, like LibraryHomeViewModel's cached shelf.
//
// Coordinator fix (15 Nov): the first adapter draft returned an empty list
// claiming CW has no player-cache API; AudioCache.cachedSongs() exists
// (playback/AudioCache.kt) and the Library adapter already uses it.

package com.music.bitchord.ui.mochy.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.bitchord.data.LikeState
import com.music.bitchord.data.model.LikeStatus
import com.music.bitchord.playback.AudioCache
import com.music.bitchord.ui.mochy.models.Song
import com.music.bitchord.ui.mochy.models.toMochySong
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

@HiltViewModel
class CachePlaylistViewModel @Inject constructor() : ViewModel() {

    private val _cachedSongs = MutableStateFlow<List<Song>>(emptyList())
    val cachedSongs: StateFlow<List<Song>> = _cachedSongs.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                val songs = runCatching { AudioCache.cachedSongs() }.getOrNull().orEmpty()
                    .filter { it.complete }
                    .map { cached ->
                        val song = cached.song.toMochySong()
                        val liked = LikeState.overrides.value[cached.song.videoId] == LikeStatus.LIKE
                        if (liked) song.copy(song = song.song.copy(liked = true)) else song
                    }
                _cachedSongs.value = songs
                kotlinx.coroutines.delay(2_000)
            }
        }
    }

    /**
     * Removes the song from ChordWave's player cache. Called from the
     * song menu's "Remove from cache" row on the Cache screen.
     */
    fun removeSongFromCache(songId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val removed = runCatching { AudioCache.remove(songId) }.getOrNull() ?: 0
            if (removed == 0) Timber.w("CachePlaylist: removeSongFromCache($songId) removed nothing")
        }
    }
}
