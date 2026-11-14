// Ported from Mochy Music (GPL-3.0): ui/screens/search/SearchScreen.kt
// SearchScreen.kt — the Modern (Mochy-clone) Search tab: the mood/genre
// browse, the glass top bar with the search field, and the active search
// pane (history + suggestions + top results).
//
// Port notes (see staging/phase2-search/NEEDS.md for the full list):
// - Navigation goes through MochyNavigator; Mochy's navigation controller
//   is gone. Route strings are Mochy's verbatim.
// - The search-field focus request (Mochy's requestSearchFocus extension,
//   driven by the Search tab's double tap) is the navigator's
//   searchFocusRequests counter now (NEEDS.md A7): the host bumps it from the
//   nav bar's onSearchDoubleTap hook, and this screen collects it.
// - Search history writes go through OnlineSearchSuggestionViewModel
//   (addHistory/deleteHistory) — the screen never touches the database.
// - The ViewModels are bare contracts (NEEDS.md §(a)); a later adapter task
//   builds them over ChordWave's YtMusicRepository/SearchHistory.

package com.music.bitchord.ui.mochy.screens.search

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.music.bitchord.R
import com.music.bitchord.ui.mochy.LocalPlayerAwareWindowInsets
import com.music.bitchord.ui.mochy.component.GlassState
import com.music.bitchord.ui.mochy.component.RemoteFocus
import com.music.bitchord.ui.mochy.component.coverTransitionKey
import com.music.bitchord.ui.mochy.component.glassRoot
import com.music.bitchord.ui.mochy.constants.OfflineModeKey
import com.music.bitchord.ui.mochy.constants.PauseSearchHistoryKey
import com.music.bitchord.ui.mochy.constants.ProfilePicUriKey
import com.music.bitchord.ui.mochy.models.SearchHistory
import com.music.bitchord.ui.mochy.models.innertube.AlbumItem
import com.music.bitchord.ui.mochy.models.innertube.ArtistItem
import com.music.bitchord.ui.mochy.models.innertube.PlaylistItem
import com.music.bitchord.ui.mochy.models.innertube.SongItem
import com.music.bitchord.ui.mochy.models.innertube.WatchEndpoint
import com.music.bitchord.ui.mochy.models.innertube.YTItem
import com.music.bitchord.ui.mochy.models.toMediaMetadata
import com.music.bitchord.ui.mochy.navigation.MochyNavigator
import com.music.bitchord.ui.mochy.playback.LocalPlayerConnection
import com.music.bitchord.ui.mochy.playback.queues.YouTubeQueue
import com.music.bitchord.ui.mochy.utils.YouTubeUrlParser
import com.music.bitchord.ui.mochy.utils.isLandscapeLayout
import com.music.bitchord.ui.mochy.utils.rememberPreference
import com.music.bitchord.ui.mochy.utils.stableStatusBars
import com.music.bitchord.ui.mochy.viewmodels.MoodAndGenresViewModel
import com.music.bitchord.ui.mochy.viewmodels.OnlineSearchSuggestionViewModel
import com.music.bitchord.ui.mochy.viewmodels.SearchSuggestionViewState

@Composable
fun SearchScreen(
    navigator: MochyNavigator,
    pureBlack: Boolean,
) {
    // on its side the field and its history stay in the pane at the left (SearchSidePane), so this side shows the categories
    if (isLandscapeLayout()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(SearchColors.page(pureBlack))
        ) {
            SearchBrowseContent(
                listState = rememberLazyListState(),
                navigator = navigator,
                columns = 3,
                topPadding = WindowInsets.stableStatusBars.asPaddingValues().calculateTopPadding() + SearchTopClearance,
            )
        }
        return
    }
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val playerConnection = LocalPlayerConnection.current
    val pauseSearchHistory by rememberPreference(PauseSearchHistoryKey, defaultValue = false)
    val offlineMode by rememberPreference(OfflineModeKey, defaultValue = false)
    val profilePicStr by rememberPreference(ProfilePicUriKey, defaultValue = "")

    var query by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue())
    }
    var active by rememberSaveable { mutableStateOf(false) }

    val suggestionViewModel: OnlineSearchSuggestionViewModel = hiltViewModel()
    val suggestionState by suggestionViewModel.viewState.collectAsState()

    LaunchedEffect(active, query.text) {
        if (active) suggestionViewModel.query.value = query.text
    }

    val browseListState = rememberLazyListState()
    val activeListState = rememberLazyListState()
    val glassState = remember { GlassState() }

    val collapse = rememberCollapseProgress(browseListState)

    val activeProgress = animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = tween(
            durationMillis = SearchCollapseDurationMs,
            easing = SearchCollapseEasing,
        ),
        label = "searchActive",
    )

    val progressProvider = remember(collapse, activeProgress) {
        { maxOf(collapse.value, activeProgress.value) }
    }

    val commit: (String) -> Unit = remember(navigator, pauseSearchHistory, offlineMode, suggestionViewModel) {
        { raw ->
            val text = raw.trim()
            if (offlineMode && text.isNotEmpty()) {
                // offline: the library results above are the whole search; stay on them
                focusManager.clearFocus()
            } else if (text.isNotEmpty()) {
                focusManager.clearFocus()
                active = false
                when (val parsed = YouTubeUrlParser.parse(text)) {
                    is YouTubeUrlParser.ParsedUrl.Video ->
                        playerConnection?.playQueue(YouTubeQueue(WatchEndpoint(videoId = parsed.id)))

                    is YouTubeUrlParser.ParsedUrl.Artist ->
                        navigator.navigate("artist/${parsed.id}")

                    null ->
                        navigator.navigate(searchRoute(text))
                }
                if (!pauseSearchHistory) {
                    suggestionViewModel.addHistory(text)
                }
            }
        }
    }

    BackHandler(enabled = active) {
        active = false
        focusManager.clearFocus()
        keyboardController?.hide()
    }

    LaunchedEffect(active) {
        if (active) {
            runCatching { focusRequester.requestFocus() }
        } else {
            keyboardController?.hide()
        }
    }

    // two taps on the Search tab ask for the field. Mochy flagged the Search
    // page's own back-stack entry (Mochy's requestSearchFocus, keyed on
    // SearchFocusKey); here the host bumps the navigator's searchFocusRequests
    // counter from the nav bar's onSearchDoubleTap hook (NEEDS.md A7), so
    // other pages opening don't recompose the search page. A counter, not a
    // boolean, so two quick double-taps are two requests.
    val focusRequests by navigator.searchFocusRequests.collectAsState()
    LaunchedEffect(focusRequests) {
        if (focusRequests == 0) return@LaunchedEffect
        if (active) {
            // already open: just bring the keyboard back
            runCatching { focusRequester.requestFocus() }
            keyboardController?.show()
        } else {
            active = true
        }
    }

    DisposableEffect(Unit) {
        onDispose { focusManager.clearFocus() }
    }

    val showBrowse by remember { derivedStateOf { activeProgress.value < 0.995f } }
    val showActive by remember { derivedStateOf { activeProgress.value > 0.005f } }

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
            if (showBrowse) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = 1f - activeProgress.value }
                ) {
                    SearchBrowseContent(
                        listState = browseListState,
                        navigator = navigator,
                    )
                }
            }
            if (showActive) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = activeProgress.value }
                ) {
                    SearchActiveContent(
                        listState = activeListState,
                        query = query.text,
                        state = suggestionState,
                        onFill = { text ->
                            query = TextFieldValue(text, TextRange(text.length))
                        },
                        onSubmit = commit,
                        onDeleteHistory = { entry ->
                            suggestionViewModel.deleteHistory(entry)
                        },
                        navigator = navigator,
                    )
                }
            }
        }

        SearchGlassTopBar(
            glassState = glassState,
            progressProvider = progressProvider,
            pureBlack = pureBlack,
            title = stringResource(R.string.search),
            trailing = {
                SearchAvatar(imageUrl = profilePicStr.ifBlank { null }, onClick = { navigator.navigateToTab("settings") })
            },
        ) {
            SearchField(
                value = query,
                onValueChange = { query = it },
                onSearch = commit,
                placeholder = stringResource(R.string.search_tracks_placeholder),
                focused = active,
                leading = if (active) {
                    {
                        Icon(
                            painter = painterResource(R.drawable.arrow_back),
                            contentDescription = null,
                            tint = SearchColors.Secondary,
                            modifier = Modifier
                                .size(20.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = RemoteFocus,
                                ) {
                                    active = false
                                    focusManager.clearFocus()
                                },
                        )
                    }
                } else {
                    null
                },
                trailing = if (query.text.isNotEmpty()) {
                    {
                        Icon(
                            painter = painterResource(R.drawable.close),
                            contentDescription = null,
                            tint = SearchColors.Secondary,
                            modifier = Modifier
                                .size(20.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = RemoteFocus,
                                ) { query = TextFieldValue("") },
                        )
                    }
                } else {
                    null
                },
                focusRequester = focusRequester,
                onFocusChanged = { focused -> if (focused) active = true },
                readOnlyClick = if (!active) {
                    { active = true }
                } else {
                    null
                },
            )
        }
    }
}

@Composable
private fun SearchBrowseContent(
    listState: LazyListState,
    navigator: MochyNavigator,
    viewModel: MoodAndGenresViewModel = hiltViewModel(),
    // tiles in a row: two upright, three in the wider pane on its side
    columns: Int = 2,
    topPadding: Dp = searchTopBarHeight(withTitle = true),
) {
    val sections by viewModel.moodAndGenres.collectAsState()
    val bottomInset = LocalPlayerAwareWindowInsets.current
        .asPaddingValues()
        .calculateBottomPadding()

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = topPadding,
            bottom = bottomInset + 24.dp,
        ),
    ) {
        val list = sections
        if (list == null) {
            item(key = "loading") {
                Box(
                    modifier = Modifier.fillMaxWidth().height(220.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    SearchLoadingDots()
                }
            }
        } else {
            list.forEachIndexed { sectionIndex, section ->
                item(key = "header_${sectionIndex}_${section.title}") {
                    SearchSectionHeader(
                        title = section.title,
                        ruleAbove = false,
                        ruleBelow = true,
                    )
                }
                val rows = section.items.chunked(columns)
                itemsIndexed(
                    items = rows,
                    key = { rowIndex, _ -> "row_${sectionIndex}_$rowIndex" },
                    contentType = { _, _ -> "moodRow" },
                ) { _, row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = SearchHorizontalPadding, vertical = 5.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        row.forEach { item ->

                            LaunchedEffect(item.endpoint.browseId, item.endpoint.params) {
                                viewModel.requestCovers(item.endpoint.browseId, item.endpoint.params)
                            }
                            MoodTile(
                                title = item.title,
                                stripeColor = item.stripeColor,
                                covers = viewModel.covers[
                                    coverKey(item.endpoint.browseId, item.endpoint.params)
                                ].orEmpty(),
                                onClick = {
                                    navigator.navigate(
                                        "genre/${item.endpoint.browseId}?params=${item.endpoint.params}"
                                    )
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        repeat(columns - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                    }
                }
                item(key = "gap_${sectionIndex}") { Spacer(modifier = Modifier.height(22.dp)) }
            }
        }
    }
}

@Composable
internal fun SearchActiveContent(
    listState: LazyListState,
    query: String,
    state: SearchSuggestionViewState,
    onFill: (String) -> Unit,
    onSubmit: (String) -> Unit,
    onDeleteHistory: (SearchHistory) -> Unit,
    navigator: MochyNavigator,
    topPadding: Dp = searchTopBarHeight(withTitle = false),
) {
    val playerConnection = LocalPlayerConnection.current
    val offlineMode by rememberPreference(OfflineModeKey, defaultValue = false)
    val bottomInset = LocalPlayerAwareWindowInsets.current
        .asPaddingValues()
        .calculateBottomPadding()

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = topPadding,
            bottom = bottomInset + 24.dp,
        ),
    ) {
        if (query.isEmpty()) {
            if (state.history.isNotEmpty()) {
                item(key = "history_header") {
                    SearchSectionHeader(title = stringResource(R.string.search_history))
                }
                items(
                    items = state.history,
                    key = { "history_${it.query}" },
                    contentType = { "history" },
                ) { entry ->
                    SearchSimpleRow(
                        text = entry.query,
                        leadingIcon = R.drawable.history,
                        onClick = { onSubmit(entry.query) },
                        trailing = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RowGlyph(R.drawable.close) { onDeleteHistory(entry) }
                                Spacer(modifier = Modifier.width(4.dp))
                                RowGlyph(R.drawable.arrow_top_left) { onFill(entry.query) }
                            }
                        },
                    )
                }
            }
        } else {
            if (state.suggestions.isNotEmpty()) {
                item(key = "suggestion_header") {
                    SearchSectionHeader(title = stringResource(R.string.suggestions))
                }
                items(
                    items = state.suggestions,
                    key = { "suggestion_$it" },
                    contentType = { "suggestion" },
                ) { suggestion ->
                    SearchSimpleRow(
                        text = suggestion,
                        pill = true,
                        onClick = { onSubmit(suggestion) },
                        trailing = { RowGlyph(R.drawable.arrow_top_left) { onFill(suggestion) } },
                    )
                }
            }

            if (state.items.isNotEmpty()) {
                item(key = "top_result_header") {
                    Spacer(modifier = Modifier.height(10.dp))
                    SearchSectionHeader(title = stringResource(R.string.search_top_result))
                }
                items(
                    items = state.items,
                    key = { "item_${it.id}" },
                    contentType = { "ytItem" },
                ) { item ->
                    SuggestionResultRow(
                        item = item,
                        onClick = {
                            when (item) {
                                is SongItem -> playerConnection?.playQueue(
                                    YouTubeQueue.radio(item.toMediaMetadata())
                                )

                                is AlbumItem -> navigator.navigate("album/${item.id}")
                                is ArtistItem -> navigator.navigate("artist/${item.id}")
                                is PlaylistItem -> navigator.navigate("online_playlist/${item.id}")
                            }
                        },
                    )
                }
            }

            if (state.suggestions.isEmpty() && state.items.isEmpty()) {
                item(key = "suggest_loading") {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(180.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        // offline there's nothing on its way: the library just has no match
                        if (offlineMode) {
                            Text(
                                text = stringResource(R.string.search_offline_no_matches),
                                color = SearchColors.Secondary,
                                fontSize = 13.sp,
                            )
                        } else {
                            SearchLoadingDots()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RowGlyph(icon: Int, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = SearchColors.Secondary,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun SuggestionResultRow(
    item: YTItem,
    onClick: () -> Unit,
) {
    val subtitle = when (item) {
        is SongItem -> item.artists.joinToString { it.name }
        is AlbumItem -> "Album" + (item.artists?.takeIf { it.isNotEmpty() }
            ?.let { " • " + it.joinToString { a -> a.name } } ?: "")

        is ArtistItem -> "Artist"
        is PlaylistItem -> "Playlist" + (item.author?.name?.let { " • $it" } ?: "")
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = SearchHorizontalPadding, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SearchArtwork(
            url = item.thumbnail,
            size = 44.dp,
            circle = item is ArtistItem,
            sharedElementKey = coverTransitionKey(item),
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                ),
                color = SearchColors.Primary,
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
    }
}
