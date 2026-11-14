// Ported from Mochy Music (GPL-3.0): viewmodels/PlaylistsViewModel.kt
// PlaylistsViewModel.kt
//
// ADAPTATIONS: Mochy read its playlists from the database (sorted by the
// query) and synced saved playlists through SyncUtils. CW has no playlist
// database: the list is OfflinePlaylists (local-file playlists, mapped
// through the staged OfflinePlaylist.toMochyPlaylist()) plus the account's
// YouTube playlists from YtMusicRepository.libraryPlaylists(), sorted
// in-memory by AddToPlaylistSortTypeKey / AddToPlaylistSortDescendingKey.
// sync() re-fetches the online list. The online rows carry songCount 0
// (libraryPlaylists gives no count) and isEditable=false.

package com.music.bitchord.ui.mochy.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.bitchord.data.OfflinePlaylists
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.ui.mochy.constants.AddToPlaylistSortDescendingKey
import com.music.bitchord.ui.mochy.constants.AddToPlaylistSortTypeKey
import com.music.bitchord.ui.mochy.constants.PlaylistSortType
import com.music.bitchord.ui.mochy.extensions.toEnum
import com.music.bitchord.ui.mochy.models.Playlist
import com.music.bitchord.ui.mochy.models.PlaylistEntity
import com.music.bitchord.ui.mochy.utils.dataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDateTime
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber

@HiltViewModel
class PlaylistsViewModel
@Inject
constructor(
    @ApplicationContext context: Context,
) : ViewModel() {
    private val onlinePlaylists = MutableStateFlow<List<Playlist>>(emptyList())

    init {
        viewModelScope.launch { refreshOnline() }
    }

    val allPlaylists: StateFlow<List<Playlist>> =
        combine(
            OfflinePlaylists.playlists,
            onlinePlaylists,
            context.dataStore.data
                .map {
                    it[AddToPlaylistSortTypeKey].toEnum(PlaylistSortType.CREATE_DATE) to
                        (it[AddToPlaylistSortDescendingKey] ?: true)
                }
                .distinctUntilChanged(),
        ) { offline, online, (sortType, descending) ->
            val all = offline.map { it.toMochyPlaylist() } + online
            sortPlaylists(all, sortType, descending)
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    suspend fun sync() {
        refreshOnline()
    }

    private suspend fun refreshOnline() {
        val items = YtMusicRepository.libraryPlaylists()
            .onFailure { Timber.w("PlaylistsViewModel: libraryPlaylists failed: ${it.message}") }
            .getOrNull() ?: return
        onlinePlaylists.value = items.mapNotNull { item ->
            val browseId = item.browseId ?: return@mapNotNull null
            Playlist(
                playlist = PlaylistEntity(
                    id = browseId,
                    name = item.title,
                    browseId = browseId,
                    thumbnailUrl = item.thumbnailUrl,
                    isEditable = false,
                ),
                songCount = 0,
                songThumbnails = listOfNotNull(item.thumbnailUrl),
            )
        }
    }

    private fun sortPlaylists(
        list: List<Playlist>,
        sortType: PlaylistSortType,
        descending: Boolean,
    ): List<Playlist> {
        val sorted = when (sortType) {
            PlaylistSortType.CREATE_DATE ->
                list.sortedBy { it.playlist.createdAt ?: LocalDateTime.MIN }
            PlaylistSortType.NAME ->
                list.sortedBy { it.playlist.name.lowercase() }
            PlaylistSortType.SONG_COUNT ->
                list.sortedBy { it.songCount }
            PlaylistSortType.LAST_UPDATED ->
                list.sortedBy {
                    it.playlist.lastUpdateTime ?: it.playlist.createdAt ?: LocalDateTime.MIN
                }
        }
        return if (descending) sorted.reversed() else sorted
    }
}
