// Ported from Mochy Music (GPL-3.0): ui/screens/ExploreScreen.kt
// ExploreScreen.kt — the charts + new-releases explore page: trending and
// chart sections in snap-scrolling 4-row grids, new-release albums, and
// top music videos. Mochy keeps it in ui/screens/; the port lives in
// ui/mochy/screens/search/ with the other search screens.
//
// Port notes (see staging/phase2-search-results/NEEDS.md for the full list):
// - Navigation goes through MochyNavigator; Mochy's navigation controller
//   is gone. Route strings are Mochy's verbatim.
// - Scroll-to-top: Mochy flagged the page's back-stack entry
//   (savedStateHandle "scrollToTop"); here the screen collects the
//   navigator's scrollToTopRequests counter, same pattern as HomeScreen.
// - This screen reads ExploreViewModel + ChartsViewModel (NEEDS.md B4) —
//   NOT SuggestionsViewModel: no Mochy screen reads SuggestionsViewModel,
//   and the Apple-Music suggestions engine has no screen of its own.
//
// Fixes (feedback 24 Nov 2026, item 9):
// - Top inset: read off WindowInsets.statusBars, NOT
//   LocalPlayerAwareWindowInsets — MochyApp provides that local with
//   navigation bars + mini-player room only, so its top padding is 0
//   and the first title sat under the clock. Mochy's own provider
//   added the status bars + AppBarHeight (MainActivity:779); the port
//   dropped both.
// - Chart sections are no longer assumed to be songs: the "Top artists"
//   shelf carries artist rows only (parseArtistRow), so the old
//   filterIsInstance<SongItem> left its title over an empty
//   fixed-height grid. Sections now render their songs as a grid and
//   their cards (artists/albums/playlists) as a row, and a section
//   with nothing renderable is skipped entirely.
// - The two fetches are decoupled: charts and new releases render
//   independently, and both failing shows a retry instead of shimmer
//   forever. Loads refresh on revisit when the data is stale (the
//   view models own the staleness check).

package com.music.bitchord.ui.mochy.screens.search

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.music.bitchord.R
import com.music.bitchord.ui.mochy.LocalPlayerAwareWindowInsets
import com.music.bitchord.ui.mochy.component.LocalMenuState
import com.music.bitchord.ui.mochy.component.NavigationTitle
import com.music.bitchord.ui.mochy.component.YouTubeGridItem
import com.music.bitchord.ui.mochy.component.YouTubeListItem
import com.music.bitchord.ui.mochy.component.shimmer.GridItemPlaceHolder
import com.music.bitchord.ui.mochy.component.shimmer.ShimmerHost
import com.music.bitchord.ui.mochy.component.shimmer.TextPlaceholder
import com.music.bitchord.ui.mochy.constants.ListItemHeight
import com.music.bitchord.ui.mochy.models.innertube.AlbumItem
import com.music.bitchord.ui.mochy.models.innertube.ArtistItem
import com.music.bitchord.ui.mochy.models.innertube.ChartsPage
import com.music.bitchord.ui.mochy.models.innertube.PlaylistItem
import com.music.bitchord.ui.mochy.models.innertube.SongItem
import com.music.bitchord.ui.mochy.models.innertube.WatchEndpoint
import com.music.bitchord.ui.mochy.models.toMediaMetadata
import com.music.bitchord.ui.mochy.navigation.MochyNavigator
import com.music.bitchord.ui.mochy.playback.LocalPlayerConnection
import com.music.bitchord.ui.mochy.playback.queues.YouTubeQueue
import com.music.bitchord.ui.mochy.player.menu.YouTubeAlbumMenu
import com.music.bitchord.ui.mochy.player.menu.YouTubeSongMenu
import com.music.bitchord.ui.mochy.utils.SnapLayoutInfoProvider
import com.music.bitchord.ui.mochy.utils.listItemShape
import com.music.bitchord.ui.mochy.viewmodels.ChartsViewModel
import com.music.bitchord.ui.mochy.viewmodels.ExploreViewModel
import kotlinx.coroutines.CoroutineScope

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ExploreScreen(
    navigator: MochyNavigator,
    exploreViewModel: ExploreViewModel = hiltViewModel(),
    chartsViewModel: ChartsViewModel = hiltViewModel(),
) {
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    val explorePage by exploreViewModel.explorePage.collectAsState()
    val chartsPage by chartsViewModel.chartsPage.collectAsState()
    val isChartsLoading by chartsViewModel.isLoading.collectAsState()
    val chartsError by chartsViewModel.error.collectAsState()

    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    // The host composes only the current tab (MochyApp renders
    // ExploreScreen under `currentTab == 1`), so this re-runs on every
    // revisit. Both calls are cheap when the data is fresh: each view
    // model reloads only when its page is missing or stale.
    LaunchedEffect(Unit) {
        chartsViewModel.loadCharts()
        exploreViewModel.refresh()
    }

    // MochyNavigator.scrollToTopRequests; Explore scrolls its column to the top.
    LaunchedEffect(navigator) {
        var first = true
        navigator.scrollToTopRequests.collect {
            if (first) {
                first = false
                return@collect
            }
            scrollState.animateScrollTo(0)
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(
            modifier = Modifier.verticalScroll(scrollState),
        ) {
            // Status bars directly, not LocalPlayerAwareWindowInsets:
            // the host provides that local with navigation bars + the
            // mini-player room only, so its top padding is 0 and the
            // first title slid under the clock. (NewReleaseScreen reads
            // the same source for its top padding.)
            Spacer(
                Modifier.height(
                    WindowInsets.statusBars.asPaddingValues().calculateTopPadding(),
                ),
            )

            // The two fetches are independent: charts failing must not
            // hide working new releases, and vice versa. Shimmer only
            // while neither page exists yet; both failing shows a retry
            // instead of shimmering forever. A stale reload keeps the
            // old content on screen (isChartsLoading no longer blanks
            // the page) and swaps it when the new page lands.
            val hasAnyContent = chartsPage != null || explorePage != null
            val loadFailed = !hasAnyContent && !isChartsLoading && chartsError != null
            if (!hasAnyContent && !loadFailed) {
                ShimmerHost {
                    TextPlaceholder(
                        height = 36.dp,
                        modifier = Modifier
                            .padding(12.dp)
                            .fillMaxWidth(0.5f),
                    )
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        val horizontalLazyGridItemWidthFactor = if (maxWidth * 0.475f >= 320.dp) 0.475f else 0.9f
                        val horizontalLazyGridItemWidth = maxWidth * horizontalLazyGridItemWidthFactor

                        LazyHorizontalGrid(
                            rows = GridCells.Fixed(4),
                            contentPadding = PaddingValues(start = 4.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(ListItemHeight * 4),
                        ) {
                            items(4) {
                                Row(
                                    modifier = Modifier
                                        .width(horizontalLazyGridItemWidth)
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(ListItemHeight - 16.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(MaterialTheme.colorScheme.onSurface),
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(
                                        modifier = Modifier.fillMaxHeight(),
                                        verticalArrangement = Arrangement.Center,
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .height(16.dp)
                                                .width(120.dp)
                                                .background(MaterialTheme.colorScheme.onSurface),
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Box(
                                            modifier = Modifier
                                                .height(12.dp)
                                                .width(80.dp)
                                                .background(MaterialTheme.colorScheme.onSurface),
                                        )
                                    }
                                }
                            }
                        }
                    }

                    TextPlaceholder(
                        height = 36.dp,
                        modifier = Modifier
                            .padding(vertical = 12.dp, horizontal = 12.dp)
                            .width(250.dp),
                    )
                    Row {
                        repeat(2) {
                            GridItemPlaceHolder()
                        }
                    }

                    TextPlaceholder(
                        height = 36.dp,
                        modifier = Modifier
                            .padding(vertical = 12.dp, horizontal = 12.dp)
                            .width(250.dp),
                    )
                    Row {
                        repeat(2) {
                            GridItemPlaceHolder()
                        }
                    }

                    TextPlaceholder(
                        height = 36.dp,
                        modifier = Modifier
                            .padding(vertical = 12.dp, horizontal = 12.dp)
                            .width(250.dp),
                    )
                }
            } else if (loadFailed) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(horizontal = 32.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.explore_load_failed),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.secondary,
                            textAlign = TextAlign.Center,
                        )
                        Button(
                            onClick = {
                                chartsViewModel.loadCharts(forceRefresh = true)
                                exploreViewModel.refresh(force = true)
                            },
                        ) {
                            Text(text = stringResource(R.string.retry))
                        }
                    }
                }
            } else {
                chartsPage?.sections?.filter { it.title != "Top music videos" }?.forEach { section ->
                    key(section.title) {
                        ChartSection(
                            section = section,
                            navigator = navigator,
                            coroutineScope = coroutineScope,
                        )
                    }
                }

                explorePage?.newReleaseAlbums?.let { newReleaseAlbums ->
                    val distinctNewReleases = remember(newReleaseAlbums) {
                        newReleaseAlbums.distinctBy { it.id }
                    }
                    if (distinctNewReleases.isEmpty()) return@let
                    NavigationTitle(
                        title = stringResource(R.string.new_release_albums),
                        onClick = {
                            navigator.navigate("new_release")
                        },
                    )
                    LazyRow(
                        contentPadding = WindowInsets.systemBars
                            .only(WindowInsetsSides.Horizontal)
                            .asPaddingValues(),
                    ) {
                        items(
                            items = distinctNewReleases,
                            key = { it.id },
                        ) { album ->
                            val active = mediaMetadata?.album?.id == album.id
                            YouTubeGridItem(
                                item = album,
                                isActive = active,
                                isPlaying = active && isPlaying,
                                coroutineScope = coroutineScope,
                                modifier = Modifier
                                    .combinedClickable(
                                        onClick = {
                                            navigator.navigate("album/${album.id}")
                                        },
                                        onLongClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            menuState.show {
                                                YouTubeAlbumMenu(
                                                    albumItem = album,
                                                    navigator = navigator,
                                                    onDismiss = menuState::dismiss,
                                                )
                                            }
                                        },
                                    )
                                    .animateItem(),
                            )
                        }
                    }
                }

                chartsPage?.sections?.find { it.title == "Top music videos" }?.let { topVideosSection ->
                    val topVideos = remember(topVideosSection) {
                        topVideosSection.items.filterIsInstance<SongItem>().distinctBy { it.id }
                    }
                    if (topVideos.isEmpty()) return@let
                    NavigationTitle(
                        title = stringResource(R.string.top_music_videos),
                    )
                    LazyRow(
                        contentPadding = WindowInsets.systemBars
                            .only(WindowInsetsSides.Horizontal)
                            .asPaddingValues(),
                    ) {
                        items(
                            items = topVideos,
                            key = { it.id },
                        ) { video ->
                            val active = video.id == mediaMetadata?.id
                            YouTubeGridItem(
                                item = video,
                                isActive = active,
                                isPlaying = active && isPlaying,
                                coroutineScope = coroutineScope,
                                modifier = Modifier
                                    .combinedClickable(
                                        onClick = {
                                            if (video.id == mediaMetadata?.id) {
                                                playerConnection.togglePlayPause()
                                            } else {
                                                playerConnection.playQueue(
                                                    YouTubeQueue(
                                                        endpoint = WatchEndpoint(videoId = video.id),
                                                        preloadItem = video.toMediaMetadata(),
                                                    ),
                                                )
                                            }
                                        },
                                        onLongClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            menuState.show {
                                                YouTubeSongMenu(
                                                    song = video,
                                                    navigator = navigator,
                                                    onDismiss = menuState::dismiss,
                                                )
                                            }
                                        },
                                    )
                                    .animateItem(),
                            )
                        }
                    }
                }

            }

            Spacer(
                Modifier.height(
                    LocalPlayerAwareWindowInsets.current.asPaddingValues().calculateBottomPadding()
                )
            )
        }
    }
}

/**
 * One charts shelf: its songs as Mochy's snap-scrolling 4-row grid, its
 * cards (artists, albums, playlists) as a row of grid items. A charts
 * shelf is homogeneous in practice, but both halves are rendered when a
 * shelf mixes them — and a shelf with nothing renderable renders nothing
 * at all, not even its title: the old code rendered the title plus an
 * empty fixed-height grid for the artist-only "Top artists" shelf
 * ("Artis teratas"), which is a title over a black hole.
 *
 * Its own composable (rather than inline in the forEach) so the grid
 * state and snap provider keep their own remember slots per section.
 */
@Composable
private fun ChartSection(
    section: ChartsPage.ChartSection,
    navigator: MochyNavigator,
    coroutineScope: CoroutineScope,
) {
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    val sectionSongs = remember(section) {
        section.items.filterIsInstance<SongItem>().distinctBy { it.id }
    }
    val sectionCards = remember(section) {
        section.items.filter { it !is SongItem }.distinctBy { it.id }
    }
    if (sectionSongs.isEmpty() && sectionCards.isEmpty()) return

    NavigationTitle(
        title = when (section.title) {
            "Trending" -> stringResource(R.string.trending)
            else -> section.title.ifEmpty { stringResource(R.string.charts) }
        },
    )

    if (sectionSongs.isNotEmpty()) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth()
        ) {
            val horizontalLazyGridItemWidthFactor = if (maxWidth * 0.475f >= 320.dp) 0.475f else 0.9f
            val horizontalLazyGridItemWidth = maxWidth * horizontalLazyGridItemWidthFactor

            val lazyGridState = rememberLazyGridState()
            val snapLayoutInfoProvider = remember(lazyGridState) {
                SnapLayoutInfoProvider(
                    lazyGridState = lazyGridState,
                    positionInLayout = { layoutSize, itemSize ->
                        (layoutSize * horizontalLazyGridItemWidthFactor / 2f - itemSize / 2f)
                    },
                )
            }

            LazyHorizontalGrid(
                state = lazyGridState,
                rows = GridCells.Fixed(4),
                flingBehavior = rememberSnapFlingBehavior(snapLayoutInfoProvider),
                contentPadding = WindowInsets.systemBars
                    .only(WindowInsetsSides.Horizontal)
                    .asPaddingValues(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ListItemHeight * 4),
            ) {
                itemsIndexed(
                    items = sectionSongs,
                    key = { _, it -> it.id },
                ) { index, song ->
                    val active = song.id == mediaMetadata?.id
                    YouTubeListItem(
                        item = song,
                        isActive = active,
                        isPlaying = active && isPlaying,
                        isSwipeable = false,
                        shape = listItemShape(index % 4, 4),
                        trailingContent = {
                            IconButton(
                                onClick = {
                                    menuState.show {
                                        YouTubeSongMenu(
                                            song = song,
                                            navigator = navigator,
                                            onDismiss = menuState::dismiss,
                                        )
                                    }
                                },
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.more_vert),
                                    contentDescription = null,
                                )
                            }
                        },
                        modifier = Modifier
                            .width(horizontalLazyGridItemWidth)
                            .combinedClickable(
                                onClick = {
                                    if (song.id == mediaMetadata?.id) {
                                        playerConnection.togglePlayPause()
                                    } else {
                                        playerConnection.playQueue(
                                            YouTubeQueue(
                                                endpoint = WatchEndpoint(videoId = song.id),
                                                preloadItem = song.toMediaMetadata(),
                                            ),
                                        )
                                    }
                                },
                                onLongClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    menuState.show {
                                        YouTubeSongMenu(
                                            song = song,
                                            navigator = navigator,
                                            onDismiss = menuState::dismiss,
                                        )
                                    }
                                },
                            ),
                    )
                }
            }
        }
    }

    if (sectionCards.isNotEmpty()) {
        LazyRow(
            contentPadding = WindowInsets.systemBars
                .only(WindowInsetsSides.Horizontal)
                .asPaddingValues(),
        ) {
            items(
                items = sectionCards,
                key = { it.id },
            ) { card ->
                YouTubeGridItem(
                    item = card,
                    coroutineScope = coroutineScope,
                    modifier = Modifier
                        .combinedClickable(
                            onClick = {
                                when (card) {
                                    is ArtistItem -> navigator.navigate("artist/${card.id}")
                                    is AlbumItem -> navigator.navigate("album/${card.id}")
                                    is PlaylistItem -> navigator.navigate("online_playlist/${card.id}")
                                    // Filtered out above; the when must stay exhaustive.
                                    is SongItem -> Unit
                                }
                            },
                        )
                        .animateItem(),
                )
            }
        }
    }
}
