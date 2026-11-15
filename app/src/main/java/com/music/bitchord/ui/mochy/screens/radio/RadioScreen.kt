// RadioScreen — the Modern radio page (route "radio"): internet stations
// from Radio Browser, restyled from the Classic radio/RadioScreen.kt in
// Mochy's visual language. The engine is untouched (RadioBrowserApi +
// RadioFavorites, via ui.mochy.viewmodels.RadioViewModel); only the
// presentation is new.
//
// Layout: a fixed header (back + "Radio" + Mochy search field) over a
// LazyColumn — Top stations as swipeable Mochy grid cards, then
// Favourites and Indonesia rows, then Top worldwide as Mochy track rows
// with a heart and a menu per station. A submitted search swaps the whole
// body for its results; clearing the field goes back to browsing.
//
// Playback: a tap on the station already on air toggles play/pause (the
// Modern convention everywhere else); any other station plays through
// MochyPlayerConnection.playStation — the Classic playStation path
// (RadioPlayback.kt). Long-press (or the row's menu button) opens the
// station sheet (RadioStationMenu.kt).

package com.music.bitchord.ui.mochy.screens.radio

import android.widget.Toast
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
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.music.bitchord.R
import com.music.bitchord.radio.RadioStation
import com.music.bitchord.ui.mochy.LocalPlayerAwareWindowInsets
import com.music.bitchord.ui.mochy.component.LocalMenuState
import com.music.bitchord.ui.mochy.component.NavigationTitle
import com.music.bitchord.ui.mochy.component.RemoteFocus
import com.music.bitchord.ui.mochy.component.detail.PlaylistTrackBones
import com.music.bitchord.ui.mochy.component.detail.PlaylistTrackRow
import com.music.bitchord.ui.mochy.navigation.MochyNavigator
import com.music.bitchord.ui.mochy.playback.LocalPlayerConnection
import com.music.bitchord.ui.mochy.screens.search.SearchField
import com.music.bitchord.ui.mochy.viewmodels.RadioViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RadioScreen(
    navigator: MochyNavigator,
    viewModel: RadioViewModel = hiltViewModel(),
) {
    val connection = LocalPlayerConnection.current ?: return
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val indonesia by viewModel.indonesia.collectAsState()
    val top by viewModel.top.collectAsState()
    val results by viewModel.results.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val failed by viewModel.failed.collectAsState()
    val favorites by viewModel.favorites.stations.collectAsState()
    val mediaMetadata by connection.mediaMetadata.collectAsState()
    val isPlaying by connection.isEffectivelyPlaying.collectAsState()

    // A station plays as its stream URL: RadioStation.asSong puts the URL
    // where the video id sits, MediaItem.mediaId is that id, and
    // MediaMetadata.id is the video id — so the current station is the one
    // whose URL matches the playing item's id. A normal song's id is a
    // video id, never a URL, so it can never collide.
    val currentStationUrl = mediaMetadata?.id

    var field by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue())
    }
    val browseListState = rememberLazyListState()
    val resultsListState = rememberLazyListState()
    val bottomPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues()
        .calculateBottomPadding()

    fun isFavorite(station: RadioStation) = favorites.any { it.uuid == station.uuid }

    fun onStationClick(station: RadioStation) {
        if (station.streamUrl == currentStationUrl) {
            connection.togglePlayPause()
            return
        }
        scope.launch {
            when (connection.playStation(station)) {
                PlayStationResult.PLAYED -> Unit
                PlayStationResult.LOCKED_BY_HOST -> Toast.makeText(
                    context,
                    R.string.listen_together_host_only_notice,
                    Toast.LENGTH_SHORT,
                ).show()

                PlayStationResult.NO_CONTROLLER -> Toast.makeText(
                    context,
                    R.string.mochy_radio_couldnt_play,
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    fun showStationMenu(station: RadioStation) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        menuState.show {
            RadioStationMenu(
                station = station,
                isCurrent = station.streamUrl == currentStationUrl,
                isPlaying = isPlaying,
                isFavorite = isFavorite(station),
                onPlayPause = { onStationClick(station) },
                onToggleFavorite = { viewModel.favorites.toggle(station) },
                onDismiss = menuState::dismiss,
            )
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // Header: GenreScreen's circle back button + the title.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.10f))
                    .combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = RemoteFocus,
                        onClick = { navigator.popBackStack() },
                        onLongClick = { navigator.clearDetails() },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.arrow_back),
                    contentDescription = stringResource(R.string.back),
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
            Text(
                text = stringResource(R.string.radio_title),
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                ),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        SearchField(
            value = field,
            onValueChange = { newValue ->
                field = newValue
                if (newValue.text.isBlank()) viewModel.clearSearch()
            },
            onSearch = { viewModel.submitSearch(it) },
            placeholder = stringResource(R.string.radio_search_hint),
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
        )

        val shownResults = results
        when {
            loading && indonesia.isEmpty() && top.isEmpty() && shownResults == null -> {
                // First load: Mochy shimmer rows, not a spinner.
                PlaylistTrackBones(rows = 8)
            }

            shownResults != null -> {
                if (shownResults.isEmpty()) {
                    RadioEmptyState(text = stringResource(R.string.radio_no_results))
                } else {
                    LazyColumn(
                        state = resultsListState,
                        contentPadding = PaddingValues(bottom = bottomPadding),
                    ) {
                        item(key = "results_title") {
                            NavigationTitle(
                                title = stringResource(R.string.mochy_radio_search_results),
                            )
                        }
                        items(shownResults, key = { it.uuid }) { station ->
                            StationRowItem(
                                station = station,
                                isCurrent = station.streamUrl == currentStationUrl,
                                isPlaying = isPlaying,
                                isFavorite = isFavorite(station),
                                onClick = { onStationClick(station) },
                                onLongClick = { showStationMenu(station) },
                                onMenuClick = { showStationMenu(station) },
                                onToggleFavorite = { viewModel.favorites.toggle(station) },
                            )
                        }
                    }
                }
            }

            failed && indonesia.isEmpty() && top.isEmpty() -> {
                Column(
                    Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.radio),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(48.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.radio_load_failed),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.radio_retry),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { viewModel.refresh() }.padding(8.dp),
                    )
                }
            }

            else -> {
                LazyColumn(
                    state = browseListState,
                    contentPadding = PaddingValues(bottom = bottomPadding),
                ) {
                    if (top.isNotEmpty()) {
                        item(key = "hero_title") {
                            NavigationTitle(
                                title = stringResource(R.string.mochy_radio_top_stations),
                            )
                        }
                        item(key = "hero_row") {
                            StationCardsRow(
                                stations = top.take(8),
                                isCurrent = { it.streamUrl == currentStationUrl },
                                isPlaying = isPlaying,
                                isFavorite = ::isFavorite,
                                onStationClick = ::onStationClick,
                                onStationLongClick = ::showStationMenu,
                                onToggleFavorite = viewModel.favorites::toggle,
                            )
                        }
                    }
                    if (favorites.isNotEmpty()) {
                        item(key = "fav_title") {
                            NavigationTitle(
                                title = stringResource(R.string.radio_favourites),
                            )
                        }
                        item(key = "fav_row") {
                            StationCardsRow(
                                stations = favorites,
                                isCurrent = { it.streamUrl == currentStationUrl },
                                isPlaying = isPlaying,
                                isFavorite = { true },
                                onStationClick = ::onStationClick,
                                onStationLongClick = ::showStationMenu,
                                onToggleFavorite = viewModel.favorites::toggle,
                            )
                        }
                    }
                    if (indonesia.isNotEmpty()) {
                        item(key = "id_title") {
                            NavigationTitle(
                                title = stringResource(R.string.radio_indonesia),
                            )
                        }
                        item(key = "id_row") {
                            StationCardsRow(
                                stations = indonesia,
                                isCurrent = { it.streamUrl == currentStationUrl },
                                isPlaying = isPlaying,
                                isFavorite = ::isFavorite,
                                onStationClick = ::onStationClick,
                                onStationLongClick = ::showStationMenu,
                                onToggleFavorite = viewModel.favorites::toggle,
                            )
                        }
                    }
                    if (top.isNotEmpty()) {
                        item(key = "top_title") {
                            NavigationTitle(
                                title = stringResource(R.string.radio_top_worldwide),
                            )
                        }
                        items(top, key = { it.uuid }) { station ->
                            StationRowItem(
                                station = station,
                                isCurrent = station.streamUrl == currentStationUrl,
                                isPlaying = isPlaying,
                                isFavorite = isFavorite(station),
                                onClick = { onStationClick(station) },
                                onLongClick = { showStationMenu(station) },
                                onMenuClick = { showStationMenu(station) },
                                onToggleFavorite = { viewModel.favorites.toggle(station) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** One station as a Mochy track row: favicon thumb, heart, menu button. */
@Composable
private fun StationRowItem(
    station: RadioStation,
    isCurrent: Boolean,
    isPlaying: Boolean,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMenuClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PlaylistTrackRow(
        // songId stays null: a station is a stream, never a download or an
        // imported file, so the row's on-device marks have nothing to show.
        thumbnailUrl = station.favicon?.takeIf { it.isNotBlank() },
        title = station.name,
        subtitle = metaOf(station),
        isActive = isCurrent,
        isPlaying = isCurrent && isPlaying,
        onClick = onClick,
        onLongClick = onLongClick,
        onMenuClick = onMenuClick,
        modifier = modifier,
        liked = isFavorite,
        onLikeClick = onToggleFavorite,
    )
}

/** Centered icon + text for an empty result list. */
@Composable
private fun RadioEmptyState(text: String) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.radio),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
