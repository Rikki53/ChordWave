// Ported from Mochy Music (GPL-3.0): ui/screens/library/LibraryRecentlyAddedScreen.kt
// ADAPTATIONS: navController -> MochyNavigator (navigateUp -> popBackStack,
// backToMain -> clearDetails); the account load state holds the loading bones
// while the first fetch runs; loadFailed has no slot on this page — the All
// tab surfaces it with a retry. See NEEDS.md.
// LibraryRecentlyAddedScreen.kt
//
// Songs only - every library song, or with a shelf the Local or Cached tab's - newest first, under
// the same headings History uses (today, yesterday, this week, last week, then month by month), and
// each row says when it came in. albums, artists and playlists carry no comparable timestamp.

package com.music.bitchord.ui.mochy.screens.library

import android.content.res.Resources
import android.text.format.DateUtils
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.R
import com.music.bitchord.ui.mochy.LocalPlayerAwareWindowInsets
import com.music.bitchord.ui.mochy.component.LocalMenuState
import com.music.bitchord.ui.mochy.component.SongSwipeBox
import com.music.bitchord.ui.mochy.component.detail.PlaylistTrackBones
import com.music.bitchord.ui.mochy.component.detail.PlaylistTrackRow
import com.music.bitchord.ui.mochy.component.librarySwipeAction
import com.music.bitchord.ui.mochy.component.queueSwipeAction
import com.music.bitchord.ui.mochy.extensions.toMediaItem
import com.music.bitchord.ui.mochy.models.Song
import com.music.bitchord.ui.mochy.navigation.MochyNavigator
import com.music.bitchord.ui.mochy.playback.LocalPlayerConnection
import com.music.bitchord.ui.mochy.playback.queues.ListQueue
import com.music.bitchord.ui.mochy.player.menu.SongMenu
import com.music.bitchord.ui.mochy.screens.playlist.GeneratedGroupHeader
import com.music.bitchord.ui.mochy.screens.playlist.GeneratedNoResults
import com.music.bitchord.ui.mochy.screens.playlist.GeneratedPlaylistPage
import com.music.bitchord.ui.mochy.utils.listeningTime
import com.music.bitchord.ui.mochy.viewmodels.LibraryHomeViewModel
import com.music.bitchord.ui.mochy.viewmodels.LibraryShelfKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// a heading, or a song with its line already written ("artist • added 3 days ago")
private sealed interface AddedRow {
    val key: String

    @Immutable
    class Heading(override val key: String, val text: String) : AddedRow

    @Immutable
    class Track(val song: Song, val subtitle: String) : AddedRow {
        override val key = "s_${song.id}"
    }
}

private fun addedAt(song: Song): LocalDateTime? = song.song.inLibrary ?: song.song.dateDownload

// the heading a day falls under, the same words History uses
private fun headingFor(date: LocalDate?, today: LocalDate, resources: Resources): String {
    if (date == null) return resources.getString(R.string.lib_added_earlier)
    val thisMonday = today.with(DayOfWeek.MONDAY)
    return when {
        date == today -> resources.getString(R.string.today)
        date == today.minusDays(1) -> resources.getString(R.string.yesterday)
        date >= thisMonday -> resources.getString(R.string.this_week)
        date >= thisMonday.minusDays(7) -> resources.getString(R.string.last_week)
        else -> date.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()))
    }
}

private fun addedRows(songs: List<Song>, query: String, resources: Resources): List<AddedRow> {
    val q = query.trim()
    val visible = if (q.isEmpty()) {
        songs
    } else {
        songs.filter { song -> song.song.title.contains(q, true) || song.artists.any { it.name.contains(q, true) } }
    }
    val today = LocalDate.now()
    val now = System.currentTimeMillis()
    val zone = ZoneId.systemDefault()
    val rows = ArrayList<AddedRow>(visible.size + 8)
    var lastHeading: String? = null
    visible.forEach { song ->
        val at = addedAt(song)
        val heading = headingFor(at?.toLocalDate(), today, resources)
        if (heading != lastHeading) {
            rows += AddedRow.Heading("h_${rows.size}_$heading", heading)
            lastHeading = heading
        }
        val whenAdded = at?.let {
            val millis = it.atZone(zone).toInstant().toEpochMilli()
            resources.getString(
                R.string.lib_added_when,
                DateUtils.getRelativeTimeSpanString(millis, now, DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE),
            )
        }
        val subtitle = listOfNotNull(song.artists.joinToString { it.name }.ifBlank { null }, whenAdded).joinToString(" • ")
        rows += AddedRow.Track(song, subtitle)
    }
    return rows
}

@Composable
fun LibraryRecentlyAddedScreen(
    navigator: MochyNavigator,
    modifier: Modifier = Modifier,
    pureBlack: Boolean = false,
    shelf: LibraryShelfKind? = null,
) {
    val viewModel: LibraryHomeViewModel = hiltViewModel()
    // with the lifecycle: the Cached shelf looks at the cache every couple of seconds while watched
    val songs by (shelf?.let(viewModel::shelf)?.songs ?: viewModel.songs).collectAsStateWithLifecycle()
    // the account load state only drives the library page — a shelf's
    // songs come from its own source, not from the account fetch.
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val playerConnection = LocalPlayerConnection.current ?: return
    val menuState = LocalMenuState.current
    val resources = LocalContext.current.resources
    val title = stringResource(R.string.library_recently_added)
    val queueTitle = shelf?.label ?: title
    val nowPlaying = playerConnection.mediaMetadata.collectAsState()
    val isPlaying = playerConnection.isEffectivelyPlaying.collectAsState()
    val currentQueue by playerConnection.queueTitle.collectAsState()

    var query by remember { mutableStateOf(TextFieldValue()) }
    val listState = rememberLazyListState()
    val rows by produceState<List<AddedRow>?>(null, songs, query.text) {
        value = withContext(Dispatchers.Default) { addedRows(songs, query.text, resources) }
    }
    val totalSeconds = remember(songs) { songs.sumOf { it.song.duration.coerceAtLeast(0) } }
    val isThisQueue = currentQueue == queueTitle

    fun play(start: Song?, shuffle: Boolean) {
        if (songs.isEmpty()) return
        val ordered = if (shuffle) songs.shuffled() else songs
        playerConnection.playQueue(
            ListQueue(
                title = queueTitle,
                items = ordered.map { it.toMediaItem() },
                startIndex = start?.let { ordered.indexOf(it).coerceAtLeast(0) } ?: 0,
            ),
        )
    }

    GeneratedPlaylistPage(
        title = title,
        coverUrl = songs.firstOrNull { !it.song.thumbnailUrl.isNullOrEmpty() }?.song?.thumbnailUrl,
        stats = stringResource(
            R.string.lib_stats_in_library_time,
            pluralStringResource(R.plurals.n_song, songs.size, songs.size),
            listeningTime(totalSeconds),
        ),
        lazyListState = listState,
        contentPadding = LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Bottom)
            .union(WindowInsets.ime).asPaddingValues(),
        isPlaying = isThisQueue && isPlaying.value,
        onPlayClick = { if (isThisQueue) playerConnection.togglePlayPause() else play(null, shuffle = false) },
        onShuffleClick = { play(null, shuffle = true) },
        query = query,
        onQueryChange = { query = it },
        searchPlaceholder = stringResource(R.string.lib_recent_search),
        onBackClick = { navigator.popBackStack() },
        onBackLongClick = { navigator.clearDetails() },
    ) {
        if (shelf == null && isLoading) {
            item(key = "loading", contentType = "loading") { PlaylistTrackBones() }
            return@GeneratedPlaylistPage
        }
        val shown = rows
        if (shown == null) {
            item(key = "loading", contentType = "loading") { PlaylistTrackBones() }
            return@GeneratedPlaylistPage
        }
        if (shown.isEmpty()) {
            item(key = "empty", contentType = "empty") {
                if (query.text.isNotBlank()) GeneratedNoResults(query.text) else LibraryEmptyState()
            }
        }
        items(shown, key = { it.key }, contentType = { if (it is AddedRow.Heading) "heading" else "track" }) { row ->
            when (row) {
                is AddedRow.Heading -> GeneratedGroupHeader(row.text)
                is AddedRow.Track -> SongSwipeBox(
                    library = { librarySwipeAction(row.song.song) },
                    queue = { queueSwipeAction { row.song.toMediaItem() } },
                ) {
                    val active = row.song.id == nowPlaying.value?.id
                    PlaylistTrackRow(
                        thumbnailUrl = row.song.song.thumbnailUrl,
                        songId = row.song.id,
                        title = row.song.song.title,
                        subtitle = row.subtitle,
                        isActive = active,
                        isPlaying = active && isPlaying.value,
                        onClick = { if (active) playerConnection.togglePlayPause() else play(row.song, shuffle = false) },
                        onLongClick = {
                            menuState.show { SongMenu(originalSong = row.song, navigator = navigator, onDismiss = menuState::dismiss) }
                        },
                        onMenuClick = {
                            menuState.show { SongMenu(originalSong = row.song, navigator = navigator, onDismiss = menuState::dismiss) }
                        },
                    )
                }
            }
        }
        item(key = "bottom_spacer", contentType = "spacer") { Spacer(Modifier.height(50.dp)) }
    }
}
