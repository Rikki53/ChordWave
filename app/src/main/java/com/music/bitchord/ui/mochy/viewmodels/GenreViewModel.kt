// Ported from Mochy Music (GPL-3.0): viewmodels/GenreViewModel.kt
// Genre/mood browse for the Modern Search tab, re-implemented over
// ChordWave sources: YtMusicRepository.moodGenreShelves() supplies the
// playlist shelves behind one mood/genre category (Mappers4.toBrowseResult
// turns them into a BrowseResult), and the community row resolves each
// playlist's first songs through playlistEntries(). Mochy's behaviour is
// kept: browse once; on failure set BrowseResult(title = null, items =
// emptyList()); community = the first 3 distinct PlaylistItems, each
// resolved to its first 6 songs concurrently on IO, skipping empty ones;
// params "null" (the literal string) is treated as absent.
//
// Adaptations vs Mochy:
// - No saved-state handle: the host calls load(browseId, params) with the
//   route args (coordinator decision — the route is available from
//   navigator.currentDetail). A repeated load() with the same args is a
//   no-op; a new browseId resets result/communityPlaylists to null first,
//   so a reused VM never shows the previous genre while loading.
// - The page title is NOT in CW's browse response (shelves only), so
//   load() takes an optional title — the host passes the mood tile's title
//   when it has it; without one the header stays blank. The parameter has
//   a default, so the B3 call shape load(browseId, params) still works.
// - songCountText comes from the entry count ("N songs", HomeViewModel's
//   rule) — CW's playlistEntries returns songs, not a playlist header.
// - On browse failure communityPlaylists is set to emptyList() as well as
//   result (Mochy left it null; the screen never renders the community row
//   when there are no sections, so this only makes the state explicit).

package com.music.bitchord.ui.mochy.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.ui.mochy.models.CommunityPlaylistItem
import com.music.bitchord.ui.mochy.models.innertube.BrowseResult
import com.music.innertube.models.PlaylistItem
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

@HiltViewModel
class GenreViewModel @Inject constructor() : ViewModel() {
    val result = MutableStateFlow<BrowseResult?>(null)

    val communityPlaylists = MutableStateFlow<List<CommunityPlaylistItem>?>(null)

    /** The args the current page belongs to; null until load() is called. */
    @Volatile
    private var loadedKey: String? = null

    /**
     * Browses one mood/genre category. The coordinator patches the ported
     * screen to call this with the route args; [title] is the mood tile's
     * title when the host knows it (CW's browse response carries no page
     * title of its own).
     */
    fun load(browseId: String, params: String?, title: String? = null) {
        val key = "$browseId:${params.orEmpty()}:${title.orEmpty()}"
        if (key == loadedKey) return
        loadedKey = key
        result.value = null
        communityPlaylists.value = null
        viewModelScope.launch(Dispatchers.IO) {
            val effectiveParams = params?.takeIf { it != "null" }
            YtMusicRepository.moodGenreShelves(browseId, effectiveParams)
                .onSuccess { shelves ->
                    if (key != loadedKey) return@onSuccess
                    val page = shelves.toBrowseResult(title)
                    result.value = page
                    loadCommunity(page)
                }
                .onFailure {
                    if (key != loadedKey) return@onFailure
                    Timber.w(it, "Genre: browse failed for $browseId")
                    result.value = BrowseResult(title = null, items = emptyList())
                    communityPlaylists.value = emptyList()
                }
        }
    }

    private suspend fun loadCommunity(page: BrowseResult) {
        val candidates = page.items
            .asSequence()
            .flatMap { it.items.asSequence() }
            .filterIsInstance<PlaylistItem>()
            .distinctBy { it.id }
            .take(3)
            .toList()

        if (candidates.isEmpty()) {
            communityPlaylists.value = emptyList()
            return
        }

        val resolved = java.util.Collections.synchronizedList(mutableListOf<CommunityPlaylistItem>())
        coroutineScope {
            candidates.map { playlist ->
                launch(Dispatchers.IO) {
                    // PlaylistItem.id drops the VL prefix; browsing needs it back.
                    val entries = YtMusicRepository.playlistEntries("VL${playlist.id}").getOrNull().orEmpty()
                    val songs = entries.take(6).map { it.toSongItem() }
                    if (songs.isNotEmpty()) {
                        resolved.add(
                            CommunityPlaylistItem(
                                playlist.copy(songCountText = "${entries.size} songs"),
                                songs,
                            )
                        )
                    }
                }
            }.forEach { it.join() }
        }
        communityPlaylists.value = resolved.toList()
    }
}
