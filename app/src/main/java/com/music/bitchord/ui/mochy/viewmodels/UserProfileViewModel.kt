// UserProfileViewModel.kt
//
// What the profile page shows: listening totals, what you've been playing, your playlists and the
// artists you play most. Mochy reads these from its Room event table; ChordWave reads them from
// ListeningStats (the same buckets Replay uses) and OfflinePlaylists, so no second history store
// is needed.
//
// Ported from Mochy Music (GPL-3.0): viewmodels/UserProfileViewModel.kt —
// adapted to ChordWave's stores: recentSongs come from the buckets' per-track
// last-played stamps, topArtists from the buckets' artist totals, playlists
// and the liked count from OfflinePlaylists.

package com.music.bitchord.ui.mochy.viewmodels

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.bitchord.data.OfflinePlaylists
import com.music.bitchord.data.stats.ListeningStats
import com.music.bitchord.data.stats.NameEntry
import com.music.bitchord.data.stats.ReplayPeriod
import com.music.bitchord.data.stats.TrackEntry
import com.music.bitchord.ui.mochy.models.Artist
import com.music.bitchord.ui.mochy.models.ArtistEntity
import com.music.bitchord.ui.mochy.models.Playlist
import com.music.bitchord.ui.mochy.models.PlaylistEntity
import com.music.bitchord.ui.mochy.models.Song
import com.music.bitchord.ui.mochy.models.SongEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One "Total listens" card: how many different songs, and for how long, over [label]'s span. */
@Immutable
data class ListenTotal(val label: String, val songs: Int, val playTimeMs: Long) {
    companion object {
        /** Shown until the first count arrives, so the numbers roll up from zero instead of popping in. */
        val Placeholder = listOf(ListenTotal("ALL TIME", 0, 0L), ListenTotal("THIS MONTH", 0, 0L), ListenTotal("THIS WEEK", 0, 0L))
    }
}

@HiltViewModel
class UserProfileViewModel @Inject constructor() : ViewModel() {

    private val _listens = MutableStateFlow<List<ListenTotal>?>(null)
    val listens: StateFlow<List<ListenTotal>?> = _listens.asStateFlow()

    private val _recentSongs = MutableStateFlow<List<Song>?>(null)
    val recentSongs: StateFlow<List<Song>?> = _recentSongs.asStateFlow()

    private val _topArtists = MutableStateFlow<List<Artist>?>(null)
    val topArtists: StateFlow<List<Artist>?> = _topArtists.asStateFlow()

    /** The listener's own playlists, newest first (OfflinePlaylists appends on create). */
    val playlists: StateFlow<List<Playlist>?> = OfflinePlaylists.playlists
        .map { stored ->
            stored.filterNot { it.isLiked }
                .asReversed()
                .map { playlist ->
                    Playlist(
                        playlist = PlaylistEntity(
                            id = playlist.id,
                            name = playlist.title,
                            thumbnailUrl = playlist.coverUrl,
                        ),
                        songCount = playlist.songs.size,
                        songThumbnails = playlist.songs.map { it.thumbnailUrl },
                    )
                }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val likedCount: StateFlow<Int> = OfflinePlaylists.playlists
        .map { stored -> stored.firstOrNull { it.isLiked }?.songs?.size ?: 0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** All-time listening time in ms, for the profile's listening-time card. */
    val totalListeningMs: Long get() = _listens.value?.firstOrNull()?.playTimeMs ?: 0L

    init {
        refresh()
    }

    /**
     * Keeps every list above loaded for as long as the caller stays collecting, so the profile page
     * has them the moment it opens instead of filling in while it animates.
     */
    suspend fun keepWarm() {
        merge(recentSongs, playlists, likedCount, topArtists, listens).collect { }
    }

    /** Re-reads the totals from ListeningStats (the buckets only grow while music plays). */
    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            val allTime = ListeningStats.summary(ReplayPeriod.ALL_TIME)
            val month = ListeningStats.summary(ReplayPeriod.THIS_MONTH)
            val week = ListeningStats.summary(ReplayPeriod.THIS_WEEK)
            _listens.value = listOf(
                ListenTotal("ALL TIME", allTime.distinctSongs, allTime.totalMs),
                ListenTotal("THIS MONTH", month.distinctSongs, month.totalMs),
                ListenTotal("THIS WEEK", week.distinctSongs, week.totalMs),
            )

            val buckets = ListeningStats.exportAll()

            val tracks = LinkedHashMap<String, TrackEntry>()
            for (bucket in buckets) {
                for (track in bucket.tracks) {
                    val existing = tracks[track.id]
                    if (existing == null) tracks[track.id] = track.copy(days = track.days.toMutableMap())
                    else existing.absorb(track)
                }
            }
            _recentSongs.value = tracks.values
                .filter { it.last > 0L }
                .sortedByDescending { it.last }
                .take(20)
                .map { it.toSong() }

            val artists = LinkedHashMap<String, NameEntry>()
            for (bucket in buckets) {
                for (artist in bucket.artists) {
                    val key = artist.id ?: artist.name
                    if (key.isBlank()) continue
                    val existing = artists[key]
                    if (existing == null) artists[key] = artist.copy(days = artist.days.toMutableMap())
                    else existing.absorb(artist)
                }
            }
            _topArtists.value = artists.values
                .sortedByDescending { it.ms }
                .take(12)
                .map { entry ->
                    Artist(
                        artist = ArtistEntity(
                            id = entry.id ?: entry.name,
                            name = entry.name,
                            thumbnailUrl = entry.art,
                        ),
                        songCount = entry.plays,
                        timeListened = (entry.ms / 1000L).toInt(),
                    )
                }
        }
    }

    private fun TrackEntry.toSong(): Song = Song(
        song = SongEntity(
            id = id,
            title = title,
            thumbnailUrl = art,
            albumId = albumId,
            albumName = album,
            totalPlayTime = ms,
        ),
        artists = if (artist.isBlank()) {
            emptyList()
        } else {
            listOf(ArtistEntity(id = artistId ?: artist, name = artist))
        },
    )
}
