// RadioScreen — the Modern radio page (route "radio"): internet stations
// from Radio Browser, restyled from the Classic radio/RadioScreen.kt in
// Mochy's visual language. The engine is untouched (RadioBrowserApi +
// RadioFavorites, via ui.mochy.viewmodels.RadioViewModel); only the
// presentation is new.
//
// Layout: Search's collapsing glass header (batch 25) — the big bold
// title ("Radio") slides up and fades out as the list scrolls, the
// search field rises to sit beside the profile avatar, and focusing
// the field (or showing search results) holds it collapsed, exactly
// like the Search tab (SearchGlassTopBar + rememberCollapseProgress
// from screens/search). No back arrow, Radio is a top-level tab.
// Below it a LazyColumn. Sections, in order: "Di Sekitar Kamu" (nearest Indonesian
// stations, only when location is granted and stations with coordinates
// sit within 150 km), Favourites, Indonesia, Top stations as swipeable
// Mochy grid cards, Pop and Dangdut rows, then Top worldwide (the
// most-clicked ranking, not a repeat of Top stations) as Mochy track rows
// with a heart and a menu per station. A submitted search swaps the whole
// body for its results; clearing the field goes back to browsing.
//
// Location is coarse-only and opt-in: while permission is neither
// granted nor asked for, a small card above the sections offers to turn
// it on; a "no" dismisses the card for good (a second "no" stops Android
// showing the dialog at all, so re-asking would be a dead button) and
// the page works fully without it. Lists refresh when the tab is
// returned to, at most once per 10 minutes (RadioViewModel.refreshIfStale).
//
// Playback: a tap on the station already on air toggles play/pause (the
// Modern convention everywhere else); any other station plays through
// MochyPlayerConnection.playStation — the Classic playStation path
// (RadioPlayback.kt). Long-press (or the row's menu button) opens the
// station sheet (RadioStationMenu.kt).
//
// Animation (batch 24): rebuilt to match Mochy's reveal, the same one
// Home uses — every section, card row and station row fades and rises
// into place the first time it shows, cascading 60ms apart
// (component/RevealOnAppear.kt). Batch 25 adds the Search tab's
// collapsing header on top of that (900ms tween, same easing).

package com.music.bitchord.ui.mochy.screens.radio

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.music.bitchord.R
import com.music.bitchord.radio.RadioStation
import com.music.bitchord.ui.mochy.LocalPlayerAwareWindowInsets
import com.music.bitchord.ui.mochy.component.GlassState
import com.music.bitchord.ui.mochy.component.LocalMenuState
import com.music.bitchord.ui.mochy.component.NavigationTitle
import com.music.bitchord.ui.mochy.component.RevealSeenState
import com.music.bitchord.ui.mochy.component.glassRoot
import com.music.bitchord.ui.mochy.component.rememberRevealSeenState
import com.music.bitchord.ui.mochy.component.revealOnAppear
import com.music.bitchord.ui.mochy.component.TopToaster
import com.music.bitchord.ui.mochy.component.detail.PlaylistTrackBones
import com.music.bitchord.ui.mochy.component.detail.PlaylistTrackRow
import com.music.bitchord.ui.mochy.navigation.MochyNavigator
import com.music.bitchord.ui.mochy.playback.LocalPlayerConnection
import com.music.bitchord.ui.mochy.screens.profile.rememberProfilePhotoUrl
import com.music.bitchord.ui.mochy.screens.search.SearchAvatar
import com.music.bitchord.ui.mochy.screens.search.SearchCollapseDurationMs
import com.music.bitchord.ui.mochy.screens.search.SearchCollapseEasing
import com.music.bitchord.ui.mochy.screens.search.SearchField
import com.music.bitchord.ui.mochy.screens.search.SearchGlassTopBar
import com.music.bitchord.ui.mochy.screens.search.rememberCollapseProgress
import com.music.bitchord.ui.mochy.screens.search.searchTopBarHeight
import com.music.bitchord.ui.mochy.viewmodels.RadioViewModel
import kotlinx.coroutines.launch

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
    val topClicked by viewModel.topClicked.collectAsState()
    val pop by viewModel.pop.collectAsState()
    val dangdut by viewModel.dangdut.collectAsState()
    val nearby by viewModel.nearby.collectAsState()
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

    // The nearby section's subtitles ("Bandung · 68 km") need each
    // station's distance back; look them up by uuid at render time.
    val nearbyByUuid = remember(nearby) { nearby.associateBy { it.station.uuid } }

    var field by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue())
    }
    val browseListState = rememberLazyListState()
    val resultsListState = rememberLazyListState()
    // Tap the active tab again to scroll to the top (same pattern as Home).
    LaunchedEffect(navigator) {
        var first = true
        navigator.scrollToTopRequests.collect {
            if (first) {
                first = false
                return@collect
            }
            browseListState.animateScrollToItem(0)
            resultsListState.animateScrollToItem(0)
        }
    }
    // Mochy's reveal: sections and rows rise in once, cascading (batch 24)
    val revealSeen = rememberRevealSeenState()
    val bottomPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues()
        .calculateBottomPadding()

    // Search's collapsing header (batch 25): scrolling the browse list
    // slides the big title away and lifts the search field beside the
    // profile avatar; focusing the field — or showing search results —
    // holds it collapsed, the same two halves Search and Library have.
    val glassState = remember { GlassState() }
    val collapse = rememberCollapseProgress(browseListState)
    var searchActive by remember { mutableStateOf(false) }
    val activeProgress = animateFloatAsState(
        targetValue = if (searchActive || results != null) 1f else 0f,
        animationSpec = tween(
            durationMillis = SearchCollapseDurationMs,
            easing = SearchCollapseEasing,
        ),
        label = "radioSearchActive",
    )
    val progressProvider = remember(collapse, activeProgress) {
        { maxOf(collapse.value, activeProgress.value) }
    }
    val topBarHeight = searchTopBarHeight(withTitle = true)
    val topBarHeightCollapsed = searchTopBarHeight(withTitle = false)

    // Coarse location, for the "Di Sekitar Kamu" section only. The
    // permission state is re-read on every resume, so granting it in
    // Settings while away still fills the section on the way back.
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    // The prompt card goes away after the first ask: a second "no" stops
    // Android showing the dialog at all, so re-asking would be a dead
    // button — and the page works fully without location.
    var locationPrompted by rememberSaveable { mutableStateOf(false) }

    val locationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasLocationPermission = granted
        locationPrompted = true
        if (granted) {
            viewModel.refreshNearbyFromDevice()
        } else {
            TopToaster.show(
                text = context.getString(R.string.mochy_radio_location_denied),
                iconRes = R.drawable.explore_outlined,
                actionLabel = "Settings",
                onAction = {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                },
            )
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        hasLocationPermission =
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        if (hasLocationPermission) viewModel.refreshNearbyFromDevice()
        viewModel.refreshIfStale()
    }

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

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // Only the content below is recorded for the header's glass, so
        // the bar never draws itself (same arrangement as Search).
        Box(
            modifier = Modifier
                .fillMaxSize()
                .glassRoot(glassState, isActive = { progressProvider() > 0.01f }),
        ) {
            val shownResults = results
            when {
                loading && indonesia.isEmpty() && top.isEmpty() && shownResults == null -> {
                    // First load: Mochy shimmer rows, not a spinner.
                    Box(Modifier.fillMaxSize().padding(top = topBarHeight)) {
                        PlaylistTrackBones(rows = 8)
                    }
                }

                shownResults != null -> {
                    if (shownResults.isEmpty()) {
                        RadioEmptyState(text = stringResource(R.string.radio_no_results), topPadding = topBarHeightCollapsed)
                    } else {
                        LazyColumn(
                            state = resultsListState,
                            contentPadding = PaddingValues(top = topBarHeightCollapsed, bottom = bottomPadding),
                        ) {
                            radioItem("results_title", revealSeen) {
                                NavigationTitle(
                                    title = stringResource(R.string.mochy_radio_search_results),
                                )
                            }
                            items(shownResults, key = { it.uuid }) { station ->
                                StationRowItem(
                                    modifier = Modifier.revealOnAppear("result_${station.uuid}", revealSeen, cascade = true),
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
                        Modifier.fillMaxSize().padding(top = topBarHeight).padding(32.dp),
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
                        contentPadding = PaddingValues(top = topBarHeight, bottom = bottomPadding),
                    ) {
                        if (!hasLocationPermission && !locationPrompted) {
                            radioItem("location_prompt", revealSeen) {
                                LocationPromptCard(
                                    onClick = {
                                        locationPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                                    },
                                )
                            }
                        }
                        if (nearby.isNotEmpty()) {
                            radioItem("nearby_title", revealSeen) {
                                NavigationTitle(
                                    title = stringResource(R.string.mochy_radio_nearby),
                                )
                            }
                            radioItem("nearby_row", revealSeen) {
                                StationCardsRow(
                                    rowKey = "nearby",
                                    revealSeen = revealSeen,
                                    stations = nearby.map { it.station },
                                    isCurrent = { it.streamUrl == currentStationUrl },
                                    isPlaying = isPlaying,
                                    isFavorite = ::isFavorite,
                                    onStationClick = ::onStationClick,
                                    onStationLongClick = ::showStationMenu,
                                    onToggleFavorite = viewModel.favorites::toggle,
                                    subtitle = { station ->
                                        nearbyByUuid[station.uuid]?.let(::nearbyMetaOf)
                                            ?: metaOf(station)
                                    },
                                )
                            }
                        }
                        if (favorites.isNotEmpty()) {
                            radioItem("fav_title", revealSeen) {
                                NavigationTitle(
                                    title = stringResource(R.string.radio_favourites),
                                )
                            }
                            radioItem("fav_row", revealSeen) {
                                StationCardsRow(
                                    rowKey = "fav",
                                    revealSeen = revealSeen,
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
                            radioItem("id_title", revealSeen) {
                                NavigationTitle(
                                    title = stringResource(R.string.radio_indonesia),
                                )
                            }
                            radioItem("id_row", revealSeen) {
                                StationCardsRow(
                                    rowKey = "id",
                                    revealSeen = revealSeen,
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
                            radioItem("hero_title", revealSeen) {
                                NavigationTitle(
                                    title = stringResource(R.string.mochy_radio_top_stations),
                                )
                            }
                            radioItem("hero_row", revealSeen) {
                                StationCardsRow(
                                    rowKey = "hero",
                                    revealSeen = revealSeen,
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
                        if (pop.isNotEmpty()) {
                            radioItem("pop_title", revealSeen) {
                                NavigationTitle(
                                    title = stringResource(R.string.mochy_radio_pop),
                                )
                            }
                            radioItem("pop_row", revealSeen) {
                                StationCardsRow(
                                    rowKey = "pop",
                                    revealSeen = revealSeen,
                                    stations = pop,
                                    isCurrent = { it.streamUrl == currentStationUrl },
                                    isPlaying = isPlaying,
                                    isFavorite = ::isFavorite,
                                    onStationClick = ::onStationClick,
                                    onStationLongClick = ::showStationMenu,
                                    onToggleFavorite = viewModel.favorites::toggle,
                                )
                            }
                        }
                        if (dangdut.isNotEmpty()) {
                            radioItem("dangdut_title", revealSeen) {
                                NavigationTitle(
                                    title = stringResource(R.string.mochy_radio_dangdut),
                                )
                            }
                            radioItem("dangdut_row", revealSeen) {
                                StationCardsRow(
                                    rowKey = "dangdut",
                                    revealSeen = revealSeen,
                                    stations = dangdut,
                                    isCurrent = { it.streamUrl == currentStationUrl },
                                    isPlaying = isPlaying,
                                    isFavorite = ::isFavorite,
                                    onStationClick = ::onStationClick,
                                    onStationLongClick = ::showStationMenu,
                                    onToggleFavorite = viewModel.favorites::toggle,
                                )
                            }
                        }
                        if (topClicked.isNotEmpty()) {
                            radioItem("top_title", revealSeen) {
                                NavigationTitle(
                                    title = stringResource(R.string.radio_top_worldwide),
                                )
                            }
                            items(topClicked, key = { it.uuid }) { station ->
                                StationRowItem(
                                    modifier = Modifier.revealOnAppear("station_${station.uuid}", revealSeen, cascade = true),
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

        // Search's collapsing glass header: the big "Radio" title slides
        // up and fades out on scroll while the search field rises to sit
        // beside the profile avatar. No back arrow — Radio is a
        // top-level tab, like Home and Library.
        SearchGlassTopBar(
            glassState = glassState,
            progressProvider = progressProvider,
            pureBlack = true,
            title = stringResource(R.string.radio_title),
            trailing = {
                SearchAvatar(
                    imageUrl = rememberProfilePhotoUrl(),
                    onClick = { navigator.navigateToTab("profile") },
                )
            },
        ) {
            SearchField(
                value = field,
                onValueChange = { newValue ->
                    field = newValue
                    if (newValue.text.isBlank()) viewModel.clearSearch()
                },
                onSearch = { viewModel.submitSearch(it) },
                placeholder = stringResource(R.string.radio_search_hint),
                focused = searchActive,
                onFocusChanged = { focused -> searchActive = focused },
            )
        }
    }
}

/** a Radio section that fades and slides up the first time it shows, like Home's rows */
private fun LazyListScope.radioItem(
    key: String,
    revealSeen: RevealSeenState,
    content: @Composable () -> Unit,
) {
    item(key = key) {
        Box(modifier = Modifier.revealOnAppear(key, revealSeen, cascade = true)) {
            content()
        }
    }
}

/** Small "turn on location" card above the sections, shown only while
 * location permission is neither granted nor asked for. */
@Composable
private fun LocationPromptCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.explore_outlined),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(22.dp),
        )
        Text(
            text = stringResource(R.string.mochy_radio_enable_location),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
            modifier = Modifier.weight(1f),
        )
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
private fun RadioEmptyState(text: String, topPadding: Dp = 0.dp) {
    Column(
        Modifier.fillMaxSize().padding(top = topPadding).padding(32.dp),
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
