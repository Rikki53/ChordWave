// NewReleaseScreen.kt
//
// The full New Release albums list behind Explore's "New release albums"
// title. New in ChordWave — Mochy itself never had a destination for this
// route (its title navigates to "new_release" too, but nothing handles it),
// so this screen is written from scratch over the albums Explore already
// loaded (ExploreViewModel, activity-scoped, so no second fetch).

package com.music.bitchord.ui.mochy.screens.search

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.music.bitchord.R
import com.music.bitchord.ui.mochy.LocalPlayerAwareWindowInsets
import com.music.bitchord.ui.mochy.component.LocalMenuState
import com.music.bitchord.ui.mochy.component.YouTubeGridItem
import com.music.bitchord.ui.mochy.navigation.MochyNavigator
import com.music.bitchord.ui.mochy.player.menu.YouTubeAlbumMenu
import com.music.bitchord.ui.mochy.theme.InterFontFamily
import com.music.bitchord.ui.mochy.viewmodels.ExploreViewModel

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NewReleaseScreen(
    navigator: MochyNavigator,
    exploreViewModel: ExploreViewModel = hiltViewModel(),
) {
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val explorePage by exploreViewModel.explorePage.collectAsState()
    val albums = remember(explorePage?.newReleaseAlbums) {
        explorePage?.newReleaseAlbums?.distinctBy { it.id }.orEmpty()
    }
    val bottomInset = LocalPlayerAwareWindowInsets.current.asPaddingValues().calculateBottomPadding()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 8.dp,
                    bottom = 8.dp,
                )
                .padding(horizontal = 8.dp),
        ) {
            IconButton(onClick = { navigator.popBackStack() }) {
                Icon(
                    painter = painterResource(R.drawable.arrow_back_ios),
                    contentDescription = stringResource(R.string.back),
                    tint = Color.White,
                    modifier = Modifier.size(22.dp),
                )
            }
            Text(
                text = stringResource(R.string.new_release_albums),
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = Color.White,
                modifier = Modifier.padding(start = 4.dp),
            )
        }

        when {
            explorePage == null -> {
                // Explore is still fetching (or failed and has nothing yet)
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = Color.White)
                }
            }
            albums.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.new_release_empty),
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp),
                    )
                }
            }
            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 8.dp,
                        bottom = bottomInset + 16.dp,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(items = albums, key = { it.id }) { album ->
                        YouTubeGridItem(
                            item = album,
                            fillMaxWidth = true,
                            coroutineScope = coroutineScope,
                            modifier = Modifier
                                .combinedClickable(
                                    onClick = { navigator.navigate("album/${album.id}") },
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
        }
    }
}
