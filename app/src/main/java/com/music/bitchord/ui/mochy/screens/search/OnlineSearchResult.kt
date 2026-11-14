// Ported from Mochy Music (GPL-3.0): ui/screens/search/OnlineSearchResult.kt
// OnlineSearchResult.kt — the online results page: a top-result card plus
// exact-match sections, or one filtered kind of result with paging.
//
// Port notes (see staging/phase2-search-results/NEEDS.md for the full list):
// - Navigation goes through MochyNavigator; Mochy's navigation controller
//   is gone. Route strings are Mochy's verbatim. Back is popBackStack().
// - A new search replaces this page: Mochy's navigate + popUpTo(inclusive)
//   is popBackStack() + navigate() here — equivalent while this page is on
//   top, which it always is when its own field submits.
// - History writes go through OnlineSearchViewModel.addHistory (NEEDS.md
//   B1, a required addition) — the screen never touches the database.
// - SearchFilter is Mochy's engine value class; the CW copy is a NEEDS.md
//   B1 item (same package as the staged YTItem family).

package com.music.bitchord.ui.mochy.screens.search

import android.graphics.Shader
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.music.bitchord.R
import com.music.bitchord.ui.mochy.LocalPlayerAwareWindowInsets
import com.music.bitchord.ui.mochy.component.BlurEffectCache
import com.music.bitchord.ui.mochy.component.GlassState
import com.music.bitchord.ui.mochy.component.LocalMenuState
import com.music.bitchord.ui.mochy.component.RemoteFocus
import com.music.bitchord.ui.mochy.component.RoundedHighlight
import com.music.bitchord.ui.mochy.component.SongSwipeBox
import com.music.bitchord.ui.mochy.component.coverTransitionKey
import com.music.bitchord.ui.mochy.component.glassRoot
import com.music.bitchord.ui.mochy.component.librarySwipeAction
import com.music.bitchord.ui.mochy.component.queueSwipeAction
import com.music.bitchord.ui.mochy.constants.PauseSearchHistoryKey
import com.music.bitchord.ui.mochy.constants.ProfilePicUriKey
import com.music.bitchord.ui.mochy.extensions.toMediaItem
import com.music.bitchord.ui.mochy.models.innertube.AlbumItem
import com.music.bitchord.ui.mochy.models.innertube.ArtistItem
import com.music.bitchord.ui.mochy.models.innertube.PlaylistItem
import com.music.bitchord.ui.mochy.models.innertube.SearchFilter.Companion.FILTER_ALBUM
import com.music.bitchord.ui.mochy.models.innertube.SearchFilter.Companion.FILTER_ARTIST
import com.music.bitchord.ui.mochy.models.innertube.SearchFilter.Companion.FILTER_COMMUNITY_PLAYLIST
import com.music.bitchord.ui.mochy.models.innertube.SearchFilter.Companion.FILTER_FEATURED_PLAYLIST
import com.music.bitchord.ui.mochy.models.innertube.SearchFilter.Companion.FILTER_SONG
import com.music.bitchord.ui.mochy.models.innertube.SearchFilter.Companion.FILTER_VIDEO
import com.music.bitchord.ui.mochy.models.innertube.SongItem
import com.music.bitchord.ui.mochy.models.innertube.YTItem
import com.music.bitchord.ui.mochy.models.toMediaMetadata
import com.music.bitchord.ui.mochy.navigation.MochyNavigator
import com.music.bitchord.ui.mochy.playback.LocalPlayerConnection
import com.music.bitchord.ui.mochy.playback.queues.YouTubeQueue
import com.music.bitchord.ui.mochy.player.menu.YouTubeAlbumMenu
import com.music.bitchord.ui.mochy.player.menu.YouTubeArtistMenu
import com.music.bitchord.ui.mochy.player.menu.YouTubePlaylistMenu
import com.music.bitchord.ui.mochy.player.menu.YouTubeSongMenu
import com.music.bitchord.ui.mochy.utils.isLandscapeLayout
import com.music.bitchord.ui.mochy.utils.rememberPreference
import com.music.bitchord.ui.mochy.utils.resize
import com.music.bitchord.ui.mochy.utils.stableStatusBars
import com.music.bitchord.ui.mochy.viewmodels.OnlineSearchViewModel

private val SearchCategories = listOf(
    "All" to null,
    "Music" to FILTER_SONG,
    "Artist" to FILTER_ARTIST,
    "Video" to FILTER_VIDEO,
    "Album" to FILTER_ALBUM,
    "Playlist" to FILTER_COMMUNITY_PLAYLIST,
    "Featured" to FILTER_FEATURED_PLAYLIST,
)

@Composable
fun OnlineSearchResult(
    navigator: MochyNavigator,
    // Coordinator patch (route-args rule): the host parses the query out
    // of the "search/{query}" route and passes it here; the screen feeds
    // it to viewModel.load() (the VM has no SavedStateHandle to read).
    // Named initialQuery so it never shadows the TextFieldValue below.
    initialQuery: String,
    viewModel: OnlineSearchViewModel = hiltViewModel(),
    pureBlack: Boolean = false,
) {
    val menuState = LocalMenuState.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val haptic = LocalHapticFeedback.current
    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()
    val pauseSearchHistory by rememberPreference(PauseSearchHistoryKey, defaultValue = false)
    val profilePicStr by rememberPreference(ProfilePicUriKey, defaultValue = "")

    // (Re)loads the page for this query; a repeat call with the same query
    // only fills whatever fetch is still missing.
    LaunchedEffect(initialQuery) { viewModel.load(initialQuery) }

    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    val searchFilter by viewModel.filter.collectAsState()
    val summary = viewModel.summaryPage
    val itemsPage by remember(searchFilter) {
        derivedStateOf { searchFilter?.value?.let { viewModel.viewStateMap[it] } }
    }

    var query by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(initialQuery, TextRange(initialQuery.length)))
    }

    val listState = rememberLazyListState()
    val glassState = remember { GlassState() }
    val collapse = rememberCollapseProgress(listState)
    val progressProvider = remember(collapse) { { collapse.value } }

    val selectedIndex = remember(searchFilter) {
        SearchCategories.indexOfFirst { it.second?.value == searchFilter?.value }.coerceAtLeast(0)
    }

    LaunchedEffect(searchFilter) { listState.scrollToItem(0) }

    LaunchedEffect(listState, itemsPage) {
        snapshotFlow {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= info.totalItemsCount - 4
        }.collect { nearEnd ->
            if (nearEnd && itemsPage?.continuation != null) viewModel.loadMore()
        }
    }

    BackHandler { navigator.popBackStack() }

    val submit: (String) -> Unit = remember(navigator, pauseSearchHistory) {
        { raw ->
            val text = raw.trim()
            if (text.isNotEmpty() && text != viewModel.query) {
                focusManager.clearFocus()
                navigator.popBackStack()
                navigator.navigate(searchRoute(text))
                if (!pauseSearchHistory) {
                    viewModel.addHistory(text)
                }
            }
        }
    }

    val recentContext = LocalContext.current
    val onItemClick: (YTItem) -> Unit = { item ->
        // kept for the search page's recent searches
        RecentSearches.add(recentContext, item)
        when (item) {
            is SongItem -> {
                if (item.id == mediaMetadata?.id) {
                    playerConnection.togglePlayPause()
                } else {
                    playerConnection.playQueue(YouTubeQueue.radio(item.toMediaMetadata()))
                }
            }

            is AlbumItem -> navigator.navigate("album/${item.id}")
            is ArtistItem -> navigator.navigate("artist/${item.id}")
            is PlaylistItem -> navigator.navigate("online_playlist/${item.id}")
        }
    }

    val onItemLongClick: (YTItem) -> Unit = { item ->
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        menuState.show {
            when (item) {
                is SongItem -> YouTubeSongMenu(item, navigator, menuState::dismiss)
                is AlbumItem -> YouTubeAlbumMenu(item, navigator, menuState::dismiss)
                is ArtistItem -> YouTubeArtistMenu(item, menuState::dismiss)
                is PlaylistItem -> YouTubePlaylistMenu(
                    playlist = item,
                    coroutineScope = coroutineScope,
                    onDismiss = menuState::dismiss,
                )
            }
        }
    }

    val bottomInset = LocalPlayerAwareWindowInsets.current.asPaddingValues().calculateBottomPadding()
    // on its side the field is in the pane at the left (SearchSidePane): only the kinds of result sit over the list
    val landscape = isLandscapeLayout()
    val statusTop = WindowInsets.stableStatusBars.asPaddingValues().calculateTopPadding()

    val top = remember(summary, viewModel.query) {
        summary?.let { pickTopResult(it.summaries.flatMap { section -> section.items }, viewModel.query) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SearchColors.page(pureBlack))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                // recorded while scrolling too, so the bar's blur never drops out mid-scroll
                .glassRoot(glassState, isActive = { progressProvider() > 0.01f })
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = if (landscape) statusTop + SearchTopClearance + 34.dp + 18.dp else searchTopBarHeight(withTitle = true, extra = 46.dp),
                    bottom = bottomInset + 24.dp,
                ),
            ) {
                if (searchFilter == null) {

                    val page = summary
                    if (page == null) {
                        item(key = "loading") {
                            Box(
                                modifier = Modifier.fillMaxWidth().height(220.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                SearchLoadingDots()
                            }
                        }
                    } else {
                        if (top != null) {
                            item(key = "top_header") {
                                SearchSectionHeader(title = "Top Results", ruleAbove = false)
                            }
                            item(key = "top_card") {
                                val active = mediaMetadata?.id == top.id
                                TopResultCard(
                                    item = top,
                                    isActive = active,
                                    isPlaying = active && isPlaying,
                                    onClick = { onItemClick(top) },
                                    onLongClick = { onItemLongClick(top) },
                                )
                                Spacer(modifier = Modifier.height(22.dp))
                            }
                        }

                        var renderedSection = false
                        page.summaries.forEachIndexed { index, section ->
                            val rows = section.items.filter { it.id != top?.id }
                            if (rows.isNotEmpty()) {
                                // a row's own rule already closes the previous section
                                val ruleAbove = !renderedSection
                                renderedSection = true
                                item(key = "summary_header_${index}_${section.title}") {
                                    SearchSectionHeader(
                                        title = if (index == 0) "Exact matches" else section.title,
                                        ruleAbove = ruleAbove,
                                    )
                                }
                                items(
                                    items = rows,
                                    key = { "summary_${index}_${it.id}" },
                                    contentType = { "resultRow" },
                                ) { item ->
                                    ResultRow(
                                        item = item,
                                        isActive = mediaMetadata?.id == item.id,
                                        onClick = { onItemClick(item) },
                                        onLongClick = { onItemLongClick(item) },
                                        onMenu = { onItemLongClick(item) },
                                    )
                                }
                                item(key = "summary_gap_$index") {
                                    Spacer(modifier = Modifier.height(18.dp))
                                }
                            }
                        }
                    }
                } else {
                    val page = itemsPage
                    if (page == null) {
                        item(key = "filter_loading") {
                            Box(
                                modifier = Modifier.fillMaxWidth().height(220.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                SearchLoadingDots()
                            }
                        }
                    } else if (page.items.isEmpty()) {
                        item(key = "filter_empty") {
                            Box(
                                modifier = Modifier.fillMaxWidth().height(220.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "No results",
                                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                                    color = SearchColors.Secondary,
                                )
                            }
                        }
                    } else {
                        items(
                            items = page.items,
                            key = { it.id },
                            contentType = { "resultRow" },
                        ) { item ->
                            ResultRow(
                                item = item,
                                isActive = mediaMetadata?.id == item.id,
                                onClick = { onItemClick(item) },
                                onLongClick = { onItemLongClick(item) },
                                onMenu = { onItemLongClick(item) },
                            )
                        }
                    }
                }
            }
        }

        if (landscape) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            0f to SearchColors.page(pureBlack),
                            0.7f to SearchColors.page(pureBlack).copy(alpha = 0.9f),
                            1f to Color.Transparent,
                        )
                    )
                    .padding(top = statusTop + SearchTopClearance - 4.dp, bottom = 14.dp),
            ) {
                SearchCategoryRow(
                    categories = SearchCategories.map { it.first },
                    selectedIndex = selectedIndex,
                    onSelect = { index -> viewModel.filter.value = SearchCategories[index].second },
                )
            }
        } else SearchGlassTopBar(
            glassState = glassState,
            progressProvider = progressProvider,
            pureBlack = pureBlack,
            title = stringResource(R.string.search),
            trailing = {
                SearchAvatar(imageUrl = profilePicStr.ifBlank { null }, onClick = { navigator.navigateToTab("settings") })
            },
            below = {
                SearchCategoryRow(
                    categories = SearchCategories.map { it.first },
                    selectedIndex = selectedIndex,
                    onSelect = { index -> viewModel.filter.value = SearchCategories[index].second },
                )
            },
        ) {
            SearchField(
                value = query,
                onValueChange = { query = it },
                onSearch = submit,
                placeholder = "Search for any tracks, albums, lyrics...",
                leading = {
                    Icon(
                        painter = painterResource(R.drawable.arrow_back),
                        contentDescription = null,
                        tint = SearchColors.Secondary,
                        modifier = Modifier
                            .size(20.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = RemoteFocus,
                            ) { navigator.popBackStack() },
                    )
                },
            )
        }
    }
}

// youtube's own first pick, unless another result matches the typed words clearly better; taking the first song anywhere showed songs nobody searched for
private fun pickTopResult(items: List<YTItem>, query: String): YTItem? {
    if (items.isEmpty()) return null
    val words = searchWords(query)
    if (words.isEmpty()) return items.first()
    val typed = words.joinToString(" ")

    items.filterIsInstance<ArtistItem>()
        .firstOrNull { searchWords(it.title).joinToString(" ") == typed }
        ?.let { return it }

    // the highest score wins, and on a tie the one youtube put first
    var best: YTItem? = null
    var bestScore = 0
    items.forEach { item ->
        val score = matchScore(item, words, typed)
        if (score > bestScore) {
            best = item
            bestScore = score
        }
    }
    return best ?: items.first()
}

// how well an item's title and credits cover the typed words: all of them beats some, the exact title beats both, and a music video gives way to the song
private fun matchScore(item: YTItem, words: List<String>, typed: String): Int {
    val title = searchWords(item.title)
    val credits = when (item) {
        is SongItem -> item.artists.flatMap { searchWords(it.name) }
        is AlbumItem -> item.artists.orEmpty().flatMap { searchWords(it.name) }
        is PlaylistItem -> item.author?.name?.let(::searchWords).orEmpty()
        else -> emptyList()
    }
    val known = (title + credits).toHashSet()
    val found = words.count { it in known }
    if (found == 0) return 0
    var score = found * 100 / words.size
    if (title.joinToString(" ") == typed) score += 100 else if (found == words.size) score += 40
    if (item is SongItem && item.isVideoSong) score -= 5
    return score.coerceAtLeast(1)
}

// lowercase words without accents, so "Beyoncé" and "beyonce" match
private fun searchWords(text: String): List<String> =
    AccentMarks.replace(java.text.Normalizer.normalize(text.lowercase(), java.text.Normalizer.Form.NFD), "")
        .split(WordBreaks)
        .filter { it.isNotEmpty() }

private val AccentMarks = Regex("\\p{M}+")
private val WordBreaks = Regex("[^\\p{L}\\p{N}]+")

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TopResultCard(
    item: YTItem,
    isActive: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val kind = when (item) {
        is SongItem -> if (item.isVideoSong) "Video" else "Song"
        is AlbumItem -> "Album"
        is ArtistItem -> "Artist"
        is PlaylistItem -> "Playlist"
    }
    val detail = when (item) {
        is SongItem -> listOfNotNull(
            kind,
            item.artists.joinToString { it.name }.takeIf { it.isNotBlank() }?.let { "by $it" },
            item.duration?.let { formatDuration(it) },
        ).joinToString("  •  ")

        is AlbumItem -> listOfNotNull(
            kind,
            item.artists?.joinToString { it.name }?.takeIf { it.isNotBlank() }?.let { "by $it" },
            item.year?.toString(),
        ).joinToString("  •  ")

        is ArtistItem -> kind
        is PlaylistItem -> listOfNotNull(kind, item.author?.name?.let { "by $it" }, item.songCountText)
            .joinToString("  •  ")
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SearchHorizontalPadding)
            .clip(RoundedCornerShape(16.dp))
            .background(SearchColors.Tile)
            .searchCardBorder(16.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {

        if (item.thumbnail != null) {
            AsyncImage(
                model = item.thumbnail?.resize(256, 256),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        alpha = 0.55f
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            renderEffect = BlurEffectCache.get(
                                70f,
                                Shader.TileMode.CLAMP
                            )
                        }
                    },
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.35f),
                            0.55f to Color.Black.copy(alpha = 0.62f),
                            1f to Color.Black.copy(alpha = 0.88f),
                        )
                    )
            )
        }

        Column(modifier = Modifier.padding(16.dp)) {
        SearchArtwork(
            url = item.thumbnail,
            size = 108.dp,
            circle = item is ArtistItem,
            corner = 8.dp,
            sharedElementKey = coverTransitionKey(item),
        )
        Spacer(modifier = Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = SearchColors.Primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = SearchColors.Secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (item !is ArtistItem) {
                Spacer(modifier = Modifier.width(12.dp))
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .clickable(onClick = onClick),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(
                            if (isActive && isPlaying) R.drawable.ic_untitled_pause else R.drawable.play
                        ),
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
        }
    }
}

// no play state here: rows only tint the active title, so a play or pause leaves every row alone
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ResultRow(
    item: YTItem,
    isActive: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMenu: () -> Unit,
) {
    val subtitle = when (item) {
        is SongItem -> listOfNotNull(
            item.artists.joinToString { it.name }.takeIf { it.isNotBlank() },
            item.duration?.let { formatDuration(it) },
        ).joinToString("  •  ")

        is AlbumItem -> listOfNotNull(
            "Album",
            item.artists?.joinToString { it.name }?.takeIf { it.isNotBlank() },
        ).joinToString("  •  ")

        is ArtistItem -> "Artist"
        is PlaylistItem -> listOfNotNull("Playlist", item.author?.name).joinToString("  •  ")
    }

    val row: @Composable () -> Unit = {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    interactionSource = null,
                    indication = ResultRowHighlight,
                    onClick = onClick,
                    onLongClick = onLongClick,
                )
                .padding(horizontal = SearchHorizontalPadding, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SearchArtwork(
                url = item.thumbnail,
                size = 46.dp,
                circle = item is ArtistItem,
                sharedElementKey = coverTransitionKey(item),
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),

                    color = if (isActive) Color(0xFF7FD1FF) else SearchColors.Primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = SearchColors.Secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            SearchOverflowDots(onClick = onMenu)
        }
    }

    Column {
        // songs slide to queue or save to the library; Settings > Gesture sets which side does which
        if (item is SongItem) {
            SongSwipeBox(
                library = { librarySwipeAction(item) },
                queue = { queueSwipeAction { item.toMediaItem() } },
            ) {
                row()
            }
        } else {
            row()
        }
        SearchRule()
    }
}

// pressing a result lights a pill from one side of the screen to the other
private val ResultRowHighlight = RoundedHighlight(cornerRadius = 100.dp, spillX = (-10).dp)

private fun formatDuration(seconds: Int): String {
    val minutes = seconds / 60
    val remainder = seconds % 60
    return "$minutes:${remainder.toString().padStart(2, '0')}"
}
