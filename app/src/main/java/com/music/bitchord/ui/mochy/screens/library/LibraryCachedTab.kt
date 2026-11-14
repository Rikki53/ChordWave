// Ported from Mochy Music (GPL-3.0): ui/screens/library/LibraryCachedTab.kt
// ADAPTATIONS: navigation param -> MochyNavigator (route strings verbatim);
// Mochy packages -> com.music.bitchord.ui.mochy.* staged equivalents. See NEEDS.md.
// LibraryCachedTab.kt
//
// The Library's Cached tab: songs the player kept whole while streaming them, so they play again
// without a connection. The same cards as Local, counting only what is cached, then Manage cache,
// then every cached song in one list, most recently played first.

package com.music.bitchord.ui.mochy.screens.library

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.music.bitchord.ui.mochy.navigation.MochyNavigator
import com.music.bitchord.ui.mochy.models.Song
import com.music.bitchord.ui.mochy.extensions.toMediaItem
import com.music.bitchord.ui.mochy.component.SongSwipeBox
import com.music.bitchord.ui.mochy.component.librarySwipeAction
import com.music.bitchord.ui.mochy.component.queueSwipeAction

/**
 * [nowPlayingId] and [isPlaying] are read inside each row, so a song starting or pausing only
 * redraws the rows, not the whole tab.
 */
internal fun LazyListScope.libraryCachedTab(
    data: ShelfTabData,
    nowPlayingId: () -> String?,
    isPlaying: () -> Boolean,
    onPlay: (Song) -> Unit,
    onMenu: (Song) -> Unit,
    onManage: () -> Unit,
    navigator: MochyNavigator,
) {
    if (data.songs.isEmpty()) {
        item(key = "cached_empty") {
            LibraryEmptyState(text = "Nothing cached yet. Songs you play show up here.")
        }
    } else {
        libraryShelfCards(data, navigator, spaceAbove = 0.dp)
    }

    // above the list rather than under it: with hundreds of songs cached it would be a long way down
    item(key = "cached_manage") {
        Spacer(modifier = Modifier.height(12.dp))
        LibraryManagePill(text = "Manage cache", onClick = onManage)
    }

    if (data.songs.isEmpty()) return

    item(key = "cached_songs_header") {
        Spacer(modifier = Modifier.height(24.dp))
        LibraryCapsLabel("All cached music")
        Spacer(modifier = Modifier.height(8.dp))
    }

    items(data.songs, key = { "cached_song_${it.id}" }, contentType = { "cached_song" }) { song ->
        // left queues the song, right adds it to the library or takes it out, as on Songs
        SongSwipeBox(
            slab = false,
            library = { librarySwipeAction(song.song) },
            queue = { queueSwipeAction { song.toMediaItem() } },
        ) {
            val active = nowPlayingId() == song.id
            LibraryListRow(
                title = song.song.title,
                subtitle = song.artists.joinToString { it.name }.ifBlank { null },
                thumbnailUrl = song.song.thumbnailUrl,
                isActive = active,
                isPlaying = active && isPlaying(),
                songId = song.id,
                onClick = { onPlay(song) },
                onLongClick = { onMenu(song) },
            )
        }
    }
}
