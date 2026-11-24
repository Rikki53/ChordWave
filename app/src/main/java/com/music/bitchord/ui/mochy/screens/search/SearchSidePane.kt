// Ported from Mochy Music (GPL-3.0): ui/screens/search/SearchSidePane.kt
// SearchSidePane.kt — the landscape search pane: the field and its history
// at the left, the categories or the results beside it. SearchScreen's
// landscape branch only renders the browse content; the host composes this
// pane next to it (and next to OnlineSearchResult).
//
// Port notes (see staging/phase2-search-results/NEEDS.md for the full list):
// - Navigation goes through MochyNavigator; Mochy's navigation controller
//   is gone. Route strings are Mochy's verbatim.
// - A search replaces the one beside it: Mochy's navigate + popUpTo(Search)
//   + launchSingleTop is clearDetails() + navigate() here — the navigator
//   already no-ops when the route is on top (launchSingleTop), and the
//   Search tab is the stack bottom, so clearing the details is the popUpTo.
// - The double-tap focus request (Mochy's back-stack entry + SearchFocusKey)
//   is the navigator's searchFocusRequests counter (phase2-search NEEDS.md
//   A7), same pattern as SearchScreen.
// - History writes go through OnlineSearchSuggestionViewModel
//   (addHistory/deleteHistory) — the screen never touches the database.

package com.music.bitchord.ui.mochy.screens.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.music.bitchord.R
import com.music.bitchord.ui.mochy.component.GlassState
import com.music.bitchord.ui.mochy.component.RemoteFocus
import com.music.bitchord.ui.mochy.component.glassRoot
import com.music.bitchord.ui.mochy.constants.OfflineModeKey
import com.music.bitchord.ui.mochy.constants.PauseSearchHistoryKey
import com.music.bitchord.ui.mochy.constants.ProfilePicUriKey
import com.music.bitchord.ui.mochy.models.innertube.WatchEndpoint
import com.music.bitchord.ui.mochy.navigation.MochyNavigator
import com.music.bitchord.ui.mochy.playback.LocalPlayerConnection
import com.music.bitchord.ui.mochy.playback.queues.YouTubeQueue
import com.music.bitchord.ui.mochy.screens.Screens
import com.music.bitchord.ui.mochy.utils.YouTubeUrlParser
import com.music.bitchord.ui.mochy.utils.rememberPreference
import com.music.bitchord.ui.mochy.viewmodels.OnlineSearchSuggestionViewModel

// the search pages on its side: the field and its history at the left, the categories or the results beside it
fun showsSearchPane(currentRoute: String?): Boolean =
    currentRoute == Screens.Search.route || currentRoute?.startsWith("search/") == true

// the pane's width for a window this wide: a phone's column, never more than a third and a bit of a tablet
fun searchPaneWidth(windowWidth: Dp): Dp = (windowWidth * 0.4f).coerceIn(300.dp, 420.dp)

// the field, then what's been searched before or, while typing, the suggestions and the top matches
@Composable
fun SearchSidePane(
    navigator: MochyNavigator,
    currentRoute: String?,
    // read in here, so typing recomposes the pane and not the app around it
    query: State<TextFieldValue>,
    onQueryChange: (TextFieldValue) -> Unit,
    pureBlack: Boolean,
    modifier: Modifier = Modifier,
) {
    val query by query
    val playerConnection = LocalPlayerConnection.current
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    val pauseSearchHistory by rememberPreference(PauseSearchHistoryKey, defaultValue = false)
    val offlineMode by rememberPreference(OfflineModeKey, defaultValue = false)
    val profilePicStr by rememberPreference(ProfilePicUriKey, defaultValue = "")

    val suggestionViewModel: OnlineSearchSuggestionViewModel = hiltViewModel()
    val suggestionState by suggestionViewModel.viewState.collectAsState()
    val libraryOnly by suggestionViewModel.libraryOnly.collectAsState()
    LaunchedEffect(query.text) { suggestionViewModel.query.value = query.text }

    val listState = rememberLazyListState()
    val glassState = remember { GlassState() }
    val collapse = rememberCollapseProgress(listState)

    // a search replaces the one beside it instead of stacking, so back goes to the categories
    val commit: (String) -> Unit = { raw ->
        val text = raw.trim()
        if (text.isNotEmpty()) {
            focusManager.clearFocus()
            // offline the library matches under the field are the whole search
            if (!offlineMode) {
                when (val parsed = YouTubeUrlParser.parse(text)) {
                    is YouTubeUrlParser.ParsedUrl.Video ->
                        playerConnection?.playQueue(YouTubeQueue(WatchEndpoint(videoId = parsed.id)))
                    is YouTubeUrlParser.ParsedUrl.Artist ->
                        navigator.navigate("artist/${parsed.id}")
                    null -> {
                        navigator.clearDetails()
                        navigator.navigate(searchRoute(text))
                    }
                }
                if (!pauseSearchHistory) {
                    suggestionViewModel.addHistory(text)
                }
            }
        }
    }

    // two taps on the Search tab put the cursor in this field, keyboard up
    val focusRequests by navigator.searchFocusRequests.collectAsState()
    LaunchedEffect(focusRequests) {
        if (focusRequests == 0) return@LaunchedEffect
        runCatching { focusRequester.requestFocus() }
        keyboard?.show()
    }

    Box(modifier = modifier.width(searchPaneWidth(LocalConfiguration.current.screenWidthDp.dp)).fillMaxHeight()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(SearchColors.page(pureBlack))
                .glassRoot(glassState, isActive = { collapse.value > 0.01f }),
        ) {
            SearchActiveContent(
                listState = listState,
                query = query.text,
                state = suggestionState,
                onFill = { text -> onQueryChange(TextFieldValue(text, TextRange(text.length))) },
                onSubmit = commit,
                onDeleteHistory = { entry ->
                    suggestionViewModel.deleteHistory(entry)
                },
                navigator = navigator,
                topPadding = searchTopBarHeight(withTitle = true),
                libraryOnly = libraryOnly,
                onLibraryOnlyChange = suggestionViewModel::setLibraryOnly,
            )
        }

        SearchGlassTopBar(
            glassState = glassState,
            progressProvider = { collapse.value },
            pureBlack = pureBlack,
            title = stringResource(R.string.search),
            trailing = {
                SearchAvatar(imageUrl = profilePicStr.ifBlank { null }, onClick = { navigator.navigateToTab("profile") })
            },
        ) {
            SearchField(
                value = query,
                onValueChange = onQueryChange,
                onSearch = commit,
                placeholder = stringResource(R.string.search_tracks_placeholder),
                focused = true,
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
                                ) { onQueryChange(TextFieldValue("")) },
                        )
                    }
                } else {
                    null
                },
                focusRequester = focusRequester,
            )
        }

        // a hairline between the pane and the page beside it, like the settings list's
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(0.8.dp)
                .fillMaxHeight()
                .background(Color.White.copy(alpha = 0.1f)),
        )
    }
}
