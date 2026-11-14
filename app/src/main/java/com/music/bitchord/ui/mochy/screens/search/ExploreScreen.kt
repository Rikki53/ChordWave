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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
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

    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    LaunchedEffect(Unit) {
        if (chartsPage == null) {
            chartsViewModel.loadCharts()
        }
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
            Spacer(
                Modifier.height(
                    LocalPlayerAwareWindowInsets.current.asPaddingValues().calculateTopPadding(),
                ),
            )

            if (isChartsLoading || chartsPage == null || explorePage == null) {
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
            } else {
                chartsPage?.sections?.filter { it.title != "Top music videos" }?.forEach { section ->
                    NavigationTitle(
                        title = when (section.title) {
                            "Trending" -> stringResource(R.string.trending)
                            else -> section.title.ifEmpty { stringResource(R.string.charts) }
                        },
                    )
                    BoxWithConstraints(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val horizontalLazyGridItemWidthFactor = if (maxWidth * 0.475f >= 320.dp) 0.475f else 0.9f
                        val horizontalLazyGridItemWidth = maxWidth * horizontalLazyGridItemWidthFactor

                        val sectionSongs = remember(section) {
                            section.items.filterIsInstance<SongItem>().distinctBy { it.id }
                        }
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

                explorePage?.newReleaseAlbums?.let { newReleaseAlbums ->
                    val distinctNewReleases = remember(newReleaseAlbums) {
                        newReleaseAlbums.distinctBy { it.id }
                    }
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
