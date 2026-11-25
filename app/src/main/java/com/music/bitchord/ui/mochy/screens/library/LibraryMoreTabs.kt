// Ported from Mochy Music (GPL-3.0): ui/screens/library/LibraryMoreTabs.kt
// ADAPTATIONS: navigation param -> MochyNavigator (route strings verbatim);
// Mochy packages -> com.music.bitchord.ui.mochy.* staged equivalents. History
// groups by CW's DateAgo (HistoryViewModel has no per-day LocalDate groups),
// so the group headings come from HistoryScreen's rule, not historyDayHeading.
// Genres covers tagged local files only (no artist-genre lookup in CW).
// LibraryMoreTabs.kt
//
// The Library's History and Genres tabs: the last few days of what you played, and the genres of
// your tagged local files, each with a way into its full page.

package com.music.bitchord.ui.mochy.screens.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.music.bitchord.R
import com.music.bitchord.ui.mochy.extensions.toMediaItem
import com.music.bitchord.ui.mochy.models.MediaMetadata
import com.music.bitchord.ui.mochy.navigation.MochyNavigator
import com.music.bitchord.ui.mochy.playback.MochyPlayerConnection
import com.music.bitchord.ui.mochy.playback.queues.ListQueue
import com.music.bitchord.ui.mochy.screens.search.SearchLoadingDots
import com.music.bitchord.ui.mochy.viewmodels.DateAgo
import com.music.bitchord.ui.mochy.viewmodels.EventWithSong
import com.music.bitchord.ui.mochy.viewmodels.LocalGenre
import com.music.bitchord.ui.mochy.viewmodels.localGenreRoute
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val HistoryGroups = 3
private const val HistoryPerGroup = 8

/** the last few days of plays, a handful each, under a card that opens all of History */
fun LazyListScope.libraryHistoryTab(
    events: Map<DateAgo, List<EventWithSong>>?,
    loaded: Boolean,
    nowPlaying: State<MediaMetadata?>?,
    isPlaying: State<Boolean>?,
    player: MochyPlayerConnection?,
    navigator: MochyNavigator,
) {
    if (!loaded) {
        item(key = "history_loading") {
            Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) { SearchLoadingDots() }
        }
        return
    }
    if (events.isNullOrEmpty()) {
        item(key = "history_empty") { LibraryEmptyState(text = stringResource(R.string.lib_history_empty)) }
        return
    }
    val groups = events.entries.take(HistoryGroups).map { (ago, plays) -> ago to plays.take(HistoryPerGroup) }
    // a tap plays what this tab shows, from that song on, like History does with all of it
    val shown = groups.flatMap { (_, plays) -> plays.map { it.song } }
    item(key = "history_all") {
        LibraryWideCard(
            title = stringResource(R.string.lib_history_all),
            count = events.values.sumOf { it.size },
            covers = shown.take(4).map { it.song.thumbnailUrl },
            onClick = { navigator.navigate("history") },
            modifier = Modifier.padding(horizontal = LibraryHorizontalPadding),
        )
        Spacer(Modifier.height(8.dp))
    }
    groups.forEach { (ago, plays) ->
        item(key = "history_day_${ago.hashCode()}", contentType = "historyDay") {
            Spacer(Modifier.height(14.dp))
            LibrarySectionTitle(historyGroupHeading(ago))
            Spacer(Modifier.height(6.dp))
        }
        items(plays, key = { "history_${it.event.id}" }, contentType = { "historyRow" }) { play ->
            val song = play.song
            val active = song.id == nowPlaying?.value?.id
            LibraryListRow(
                title = song.song.title,
                subtitle = song.artists.joinToString { it.name }.ifBlank { null },
                thumbnailUrl = song.song.thumbnailUrl,
                isActive = active,
                isPlaying = active && isPlaying?.value == true,
                songId = song.id,
                onClick = {
                    if (active) {
                        player?.togglePlayPause()
                    } else {
                        player?.playQueue(
                            ListQueue(items = shown.map { it.toMediaItem() }, startIndex = shown.indexOf(song).coerceAtLeast(0)),
                        )
                    }
                },
                onLongClick = { navigator.navigate("history") },
            )
        }
    }
}

/** the heading over a history group, the same rule HistoryScreen uses */
@Composable
private fun historyGroupHeading(ago: DateAgo): String = when (ago) {
    DateAgo.Today -> stringResource(R.string.today)
    DateAgo.Yesterday -> stringResource(R.string.yesterday)
    DateAgo.ThisWeek -> stringResource(R.string.this_week)
    DateAgo.LastWeek -> stringResource(R.string.last_week)
    is DateAgo.Other -> ago.date.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()))
}

/** the genres of your tagged local files, one row each like a playlist, each opening its own page */
fun LazyListScope.libraryGenresTab(genres: List<LocalGenre>?, navigator: MochyNavigator) {
    if (genres == null) {
        item(key = "genres_loading") {
            Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) { SearchLoadingDots() }
        }
        return
    }
    if (genres.isEmpty()) {
        item(key = "genres_empty") { LibraryEmptyState(text = stringResource(R.string.lib_genres_local_empty)) }
        return
    }
    item(key = "genres_top") { Spacer(Modifier.height(4.dp)) }
    items(genres, key = { "genre_${it.name.lowercase()}" }, contentType = { "genreRow" }) { genre ->
        val open = { navigator.navigate(localGenreRoute(genre.name)) }
        LibraryListRow(
            title = genre.name,
            subtitle = pluralStringResource(R.plurals.n_song, genre.songs.size, genre.songs.size),
            thumbnailUrl = genre.songs.firstNotNullOfOrNull { it.song.thumbnailUrl?.takeIf(String::isNotEmpty) },
            onClick = open,
            onLongClick = open,
        )
    }
}
