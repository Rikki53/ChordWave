// Ported from Mochy Music (GPL-3.0): ui/screens/HomeScreen.kt
// HomeScreen.kt — the Modern (Mochy-clone) Home tab: hero carousel, Listen Now /
// Hop In compact tops, and the interleaved recommendation + YouTube feed rows.
//
// Port notes (see staging/phase2-home-screen/NEEDS.md for the full list):
// - Navigation goes through MochyNavigator, not NavController; route strings are
//   Mochy's verbatim.
// - The ViewModel is the ui.mochy.viewmodels.HomeViewModel adapter (DATA-MAPPING.md
//   §1); the one direct database touch (hero playlist-pick tap) calls the adapter's
//   suspend playlistSongs(playlistId) instead.
// - The launch intro is stubbed (coordinator decision): the Modern host plays no
//   intro, Home shows content at once. launchRise / launchMarkTarget /
//   rememberLaunchHold / ReportLaunchHomeComposed are private no-ops below.
// - HomeScreen itself makes no canvas calls (the canvas block lives in the staged
//   HeroCarousel, where the coordinator applies the canvasEnabled = false guard),
//   so there is nothing to stub here for rule 4.
// - NetworkReload is Mochy's ui/screens/NetworkReload.kt inlined as a private
//   composable (same 3-second offline rule), so no new file is needed for it.

package com.music.bitchord.ui.mochy.screens

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.LazyGridPrefetchStrategy
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListPrefetchStrategy
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import com.music.bitchord.R
import com.music.bitchord.data.TopPicks
import com.music.bitchord.data.model.TopPick
import com.music.bitchord.data.model.TopPickKind
import com.music.bitchord.data.model.TopPickMood
import com.music.bitchord.ui.mochy.LocalPlayerAwareWindowInsets
import com.music.bitchord.ui.mochy.component.ArtistListCard
import com.music.bitchord.ui.mochy.component.BlurDirection
import com.music.bitchord.ui.mochy.component.BlurEffectCache
import com.music.bitchord.ui.mochy.component.BonesHost
import com.music.bitchord.ui.mochy.component.containerTransformSource
import com.music.bitchord.ui.mochy.component.coverTintBackground
import com.music.bitchord.ui.mochy.component.FlatBarScrimAlpha
import com.music.bitchord.ui.mochy.component.FindYourMoodContent
import com.music.bitchord.ui.mochy.component.GlassState
import com.music.bitchord.ui.mochy.component.glassRoot
import com.music.bitchord.ui.mochy.component.HeroCarousel
import com.music.bitchord.ui.mochy.component.HeroCarouselBones
import com.music.bitchord.ui.mochy.component.heroHeight
import com.music.bitchord.ui.mochy.component.HomeCardCornerRadius
import com.music.bitchord.ui.mochy.component.HomeCardHighlight
import com.music.bitchord.ui.mochy.component.HomeCardSpacing
import com.music.bitchord.ui.mochy.component.HomeCompactTopBones
import com.music.bitchord.ui.mochy.component.HomeContentInset
import com.music.bitchord.ui.mochy.component.HomeCoverCard
import com.music.bitchord.ui.mochy.component.HomeHopInCard
import com.music.bitchord.ui.mochy.component.HomeSectionBones
import com.music.bitchord.ui.mochy.component.HomeSectionTitle
import com.music.bitchord.ui.mochy.component.HomeTopGlow
import com.music.bitchord.ui.mochy.component.HomeTrackRow
import com.music.bitchord.ui.mochy.component.HomeVideoCard
import com.music.bitchord.ui.mochy.component.homeRowHeight
import com.music.bitchord.ui.mochy.component.LikedSongsThumbnail
import com.music.bitchord.ui.mochy.component.LocalMenuState
import com.music.bitchord.ui.mochy.component.LocalPlayerBottomSheetState
import com.music.bitchord.ui.mochy.component.PlaylistPickItem
import com.music.bitchord.ui.mochy.component.PlaylistThumbnail
import com.music.bitchord.ui.mochy.component.ProgressiveGlassBackground
import com.music.bitchord.ui.mochy.component.rememberProgressiveBlur
import com.music.bitchord.ui.mochy.component.rememberRevealSeenState
import com.music.bitchord.ui.mochy.component.RevealSeenState
import com.music.bitchord.ui.mochy.component.revealOnAppear
import com.music.bitchord.ui.mochy.component.StarterItem
import com.music.bitchord.ui.mochy.component.StarterSongItem
import com.music.bitchord.ui.mochy.component.TopPickCard
import com.music.bitchord.ui.mochy.component.TopToaster
import com.music.bitchord.ui.mochy.constants.HomeStyle
import com.music.bitchord.ui.mochy.constants.HomeStyleKey
import com.music.bitchord.ui.mochy.constants.ProfilePicUriKey
import com.music.bitchord.ui.mochy.constants.ThumbnailCornerRadius
import com.music.bitchord.ui.mochy.extensions.toMediaItem
import com.music.bitchord.ui.mochy.models.Album
import com.music.bitchord.ui.mochy.models.Artist
import com.music.bitchord.ui.mochy.models.ArtistGroup
import com.music.bitchord.ui.mochy.models.CommunityPlaylistItem
import com.music.bitchord.ui.mochy.models.FeedVideoKind
import com.music.bitchord.ui.mochy.models.HomeCategory
import com.music.bitchord.ui.mochy.models.HomeVideo
import com.music.bitchord.ui.mochy.models.LocalItem
import com.music.bitchord.ui.mochy.models.Playlist
import com.music.bitchord.ui.mochy.models.Song
import com.music.bitchord.ui.mochy.models.toMediaMetadata
import com.music.bitchord.ui.mochy.models.toMochySong
import com.music.bitchord.ui.mochy.models.innertube.AlbumItem
import com.music.bitchord.ui.mochy.models.innertube.ArtistItem
import com.music.bitchord.ui.mochy.models.innertube.PlaylistItem
import com.music.bitchord.ui.mochy.models.innertube.SongItem
import com.music.bitchord.ui.mochy.models.innertube.WatchEndpoint
import com.music.bitchord.ui.mochy.models.innertube.YTItem
import com.music.bitchord.ui.mochy.navigation.MochyNavigator
import com.music.bitchord.ui.mochy.playback.LocalPlayerConnection
import com.music.bitchord.ui.mochy.playback.queues.ListQueue
import com.music.bitchord.ui.mochy.playback.queues.YouTubeQueue
import com.music.bitchord.ui.mochy.player.menu.AlbumMenu
import com.music.bitchord.ui.mochy.player.menu.ArtistMenu
import com.music.bitchord.ui.mochy.player.menu.SongMenu
import com.music.bitchord.ui.mochy.player.menu.YouTubeAlbumMenu
import com.music.bitchord.ui.mochy.player.menu.YouTubeArtistMenu
import com.music.bitchord.ui.mochy.player.menu.YouTubePlaylistMenu
import com.music.bitchord.ui.mochy.player.menu.YouTubeSongMenu
import com.music.bitchord.ui.mochy.theme.InterFontFamily
import com.music.bitchord.ui.mochy.utils.ArtistPageCache
import com.music.bitchord.ui.mochy.utils.joinByBullet
import com.music.bitchord.ui.mochy.utils.rememberEnumPreference
import com.music.bitchord.ui.mochy.utils.rememberPreference
import com.music.bitchord.ui.mochy.utils.resize
import com.music.bitchord.ui.mochy.utils.SnapLayoutInfoProvider
import com.music.bitchord.ui.mochy.utils.stableSystemBars
import com.music.bitchord.ui.mochy.viewmodels.HomeViewModel
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ---- Launch intro stubs (coordinator decision) ----
// The Modern host plays no intro; Home shows content at once. These keep
// Mochy's call shape so the body below stays a verbatim port, but do nothing:
// launchRise / launchMarkTarget return the modifier untouched, the hold is
// never on, and there is nothing to report. LaunchGate.skip() is called by
// the host, not by HomeScreen.

@Suppress("UNUSED_PARAMETER")
@Composable
private fun Modifier.launchRise(order: Int, distance: Dp = 56.dp): Modifier = this

@Composable
private fun Modifier.launchMarkTarget(): Modifier = this

@Composable
private fun rememberLaunchHold(): Boolean = false

@Composable
private fun ReportLaunchHomeComposed() {
}

sealed class HomeSection(val id: String) {
    data object RecentlyPlayed : HomeSection("recently_played")
    data object MostPlayed : HomeSection("most_played")
    data object History : HomeSection("history")
    data object DontForgetTheseSongs : HomeSection("dont_forget_these_songs")
    data object AccountPlaylists : HomeSection("account_playlists")
    data object SpeedDial : HomeSection("speed_dial")
    data object FromTheCommunity : HomeSection("from_the_community")
    data object ArtistList : HomeSection("artist_list")
    data object AllTimeHits : HomeSection("all_time_hits")
    data object LiveShows : HomeSection("live_shows")
    data object MusicVideos : HomeSection("music_videos")
    // Round 2 item 6: Classic Home parity — the track playing right now, the
    // Top Picks row, and the Find Your Mood shelf. (The name TopPicks is
    // HomeSection-qualified everywhere; the TopPicks builder object is the
    // com.music.bitchord.data import.)
    data object NowPlaying : HomeSection("now_playing")
    data object TopPicks : HomeSection("top_picks")
    data object FindYourMood : HomeSection("find_your_mood")
    data class Category(val categoryId: String) : HomeSection("category_$categoryId")

    data class HomePageSection(val index: Int, val title: String) : HomeSection("home_page_section_${index}_$title")
}

/** a Home row that fades and slides up the first time it scrolls into view */
private fun LazyListScope.homeItem(
    key: String,
    revealSeen: RevealSeenState,
    contentType: String,
    content: @Composable () -> Unit,
) {
    item(key = key, contentType = contentType) {
        Box(modifier = Modifier.revealOnAppear(key, revealSeen, cascade = true)) {
            content()
        }
    }
}

// a row scrolling into view prefetches 2 cards by default, but 3 to 4 are showing, so the rest composed in the frame that revealed them
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun rememberCardRowState(): LazyListState =
    rememberLazyListState(prefetchStrategy = remember { LazyListPrefetchStrategy(nestedPrefetchItemCount = 4) })

// same for the song grids, which show about two columns of three rows
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun rememberTrackGridState(): LazyGridState =
    rememberLazyGridState(prefetchStrategy = remember { LazyGridPrefetchStrategy(nestedPrefetchItemCount = 6) })

/** a horizontally scrolling row of cards, lined up with the section title. cards cascade in once. */
@Composable
private fun <T> HomeCardRow(
    rowKey: String,
    items: List<T>,
    revealSeen: RevealSeenState,
    key: (T) -> Any,
    content: @Composable (T) -> Unit,
) {
    LazyRow(
        state = rememberCardRowState(),
        contentPadding = PaddingValues(horizontal = HomeContentInset),
        horizontalArrangement = Arrangement.spacedBy(HomeCardSpacing),
    ) {
        itemsIndexed(items = items, key = { _, item -> key(item) }) { index, item ->
            Box(
                modifier = Modifier.revealOnAppear(
                    key = "$rowKey/${key(item)}",
                    seenState = revealSeen,
                    delayMillis = min(index, 4) * 60,
                )
            ) {
                content(item)
            }
        }
    }
}

/** the paged "list" and "Rank" sections: columns of three song rows */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun <T> HomeTrackGrid(
    items: List<T>,
    state: LazyGridState,
    rowSpacing: Dp,
    columnWidth: Dp,
    key: (Int, T) -> Any,
    content: @Composable (index: Int, item: T) -> Unit,
) {
    val rows = min(3, items.size)
    val snapLayoutInfoProvider = remember(state) {
        SnapLayoutInfoProvider(lazyGridState = state, positionInLayout = { _, _ -> 0f })
    }
    LazyHorizontalGrid(
        state = state,
        rows = GridCells.Fixed(rows),
        flingBehavior = rememberSnapFlingBehavior(snapLayoutInfoProvider),
        contentPadding = PaddingValues(horizontal = HomeContentInset),
        horizontalArrangement = Arrangement.spacedBy(39.dp),
        verticalArrangement = Arrangement.spacedBy(rowSpacing),
        modifier = Modifier
            .fillMaxWidth()
            .height(homeRowHeight() * rows + rowSpacing * (rows - 1)),
    ) {
        itemsIndexed(items = items, key = key) { index, item ->
            Box(modifier = Modifier.width(columnWidth)) {
                content(index, item)
            }
        }
    }
}

/** the listen now and hop in tops: room for the top bar, then the section over a glow of its own covers' colours */
@Composable
private fun HomeCompactTop(
    glowCoverUrls: List<String>,
    glowVisibility: () -> Float,
    content: @Composable ColumnScope.() -> Unit,
) {
    val topInset = WindowInsets.stableSystemBars.asPaddingValues().calculateTopPadding()
    Box {
        // outside the launch rise, so the glow never slides down off the top edge while the intro plays
        HomeTopGlow(
            coverUrls = glowCoverUrls,
            depth = topInset + 150.dp,
            visibility = glowVisibility,
            modifier = Modifier.matchParentSize(),
        )
        Column(modifier = Modifier.launchRise(order = 1)) {
            Spacer(Modifier.height(topInset + 78.dp))
            content()
            Spacer(Modifier.height(34.dp))
        }
    }
}

@Composable
fun CommunityPlaylistCard(
    item: CommunityPlaylistItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tintCovers = remember(item.songs) { item.songs.take(3).mapNotNull { it.thumbnail } }

    Card(
        modifier = modifier
            .width(276.dp)
            .height(344.dp)
            .containerTransformSource("playlist-${item.playlist.id}", coverUrl = item.playlist.thumbnail)
            .clip(RoundedCornerShape(HomeCardCornerRadius)),
        shape = RoundedCornerShape(HomeCardCornerRadius),
        onClick = onClick
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .coverTintBackground(tintCovers, seed = item.playlist.id)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            rotationZ = 35f
                            scaleX = 1.3f
                            scaleY = 1.3f
                            translationX = 40.dp.toPx()
                            translationY = -30.dp.toPx()
                        }
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxSize()
                            .wrapContentSize(Alignment.Center)
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item.songs.take(3).forEach { song ->
                                AsyncImage(
                                    model = song.thumbnail.resize(256, 256),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(84.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item.songs.drop(3).take(2).forEach { song ->
                                AsyncImage(
                                    model = song.thumbnail.resize(256, 256),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(84.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                )
                            }
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.7f),
                                Color.Black.copy(alpha = 0.9f)
                            )
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 24.dp, bottom = 28.dp, end = 16.dp, top = 16.dp)
            ) {
                Text(
                    text = item.playlist.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.playlist.songCountText ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.75f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun AllTimeHitsCard(
    item: YTItem,
    modifier: Modifier = Modifier,
) {
    val artistName = when (item) {
        is SongItem -> item.artists.joinToString(", ") { it.name }
        is AlbumItem -> item.artists?.joinToString(", ") { it.name } ?: ""
        is ArtistItem -> item.title
        is PlaylistItem -> item.author?.name ?: ""
    }

    Box(
        modifier = modifier
            .height(160.dp)
            .clip(RoundedCornerShape(HomeCardCornerRadius))
    ) {
        AsyncImage(
            model = item.thumbnail?.resize(320, 320),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f))
                    )
                )
        )
        Text(
            text = artistName,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp)
        )
    }
}

private val HomeMarkWidth = 31.dp
private val HomeMarkHeight = 34.dp
private val HomeTopBarRise = 16.dp

/**
 * Home before the player service is up: the top bar, hero and bones the real Home shows while it
 * loads, in exactly the same places, so the swap to the real thing can't be seen. On a launch the
 * intro lands on this logo when it's the first one laid out.
 */
@Composable
private fun HomeShell() {
    val homeStyle by rememberEnumPreference(HomeStyleKey, defaultValue = HomeStyle.MODERN)
    val profilePicUri by rememberPreference(ProfilePicUriKey, "")
    val profileImageUrl = profilePicUri
        .takeIf { it.isNotBlank() }
        ?.let { if (it.contains("://")) it else "file://$it" }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (homeStyle == HomeStyle.MODERN) {
                HeroCarouselBones(modifier = Modifier.launchRise(order = 1))
            } else {
                Spacer(Modifier.height(WindowInsets.stableSystemBars.asPaddingValues().calculateTopPadding() + 82.dp))
                BonesHost(modifier = Modifier.launchRise(order = 1)) {
                    HomeCompactTopBones(isHopIn = homeStyle == HomeStyle.HOP_IN)
                }
                Spacer(Modifier.height(34.dp))
            }
            BonesHost(modifier = Modifier.launchRise(order = 2)) {
                repeat(2) {
                    HomeSectionBones()
                    Spacer(modifier = Modifier.height(34.dp))
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    top = WindowInsets.stableSystemBars.asPaddingValues().calculateTopPadding() + 14.dp,
                    bottom = 16.dp,
                    start = HomeContentInset,
                    end = 30.dp
                ),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_chordwave_mark),
                contentDescription = "ChordWave",
                tint = Color.White,
                modifier = Modifier
                    .launchMarkTarget()
                    .size(HomeMarkWidth, HomeMarkHeight)
            )
            Box(
                modifier = Modifier
                    .launchRise(order = 0, distance = HomeTopBarRise)
                    .height(44.dp),
                contentAlignment = Alignment.Center
            ) {
                if (profileImageUrl != null) {
                    AsyncImage(
                        model = profileImageUrl,
                        placeholder = painterResource(R.drawable.person),
                        error = painterResource(R.drawable.person),
                        contentDescription = stringResource(R.string.profile_label),
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.person),
                            contentDescription = stringResource(R.string.profile_label),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navigator: MochyNavigator,
    snackbarHostState: SnackbarHostState,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val menuState = LocalMenuState.current
    val playerBottomSheetState = LocalPlayerBottomSheetState.current
    // the player service comes up a beat after the first frame; Home's skeleton doesn't wait for it
    val playerConnection = LocalPlayerConnection.current ?: run {
        HomeShell()
        return
    }
    val haptic = LocalHapticFeedback.current

    // read only where they're used - inside each card, track row and the hero - never out here: a
    // play/pause or a new song used to recompose the whole of Home, every row and card on screen,
    // which landed as a hitch whenever the track changed mid-scroll
    // Mochy reads isEffectivelyPlaying here (playing and not buffering).
    val isPlayingState = playerConnection.isEffectivelyPlaying.collectAsState()
    val mediaMetadataState = playerConnection.mediaMetadata.collectAsState()
    // handed to the cards as a lambda so only the active cover's indicator reads it
    val isPlayingNow: () -> Boolean = remember(isPlayingState) { { isPlayingState.value } }

    val forgottenFavorites by viewModel.forgottenFavorites.collectAsState()
    val keepListening by viewModel.keepListening.collectAsState()
    val accountPlaylists by viewModel.accountPlaylists.collectAsState()
    val localPlaylists by viewModel.localPlaylists.collectAsState()
    val libraryArtists by viewModel.libraryArtists.collectAsState()
    // playlists the user made (or imported), put forward in the hero; Play loads and queues them
    val heroScope = rememberCoroutineScope()
    val playlistPicks = remember(localPlaylists) {
        localPlaylists.orEmpty()
            .filter { it.playlist.isEditable && it.songCount > 0 && it.id != "liked" }
            .shuffled()
            .take(2)
            .map { playlist ->
                PlaylistPickItem(
                    id = playlist.id,
                    name = playlist.playlist.name,
                    thumbnailUrl = playlist.playlist.thumbnailUrl ?: playlist.thumbnails.firstOrNull(),
                ) {
                    heroScope.launch(Dispatchers.IO) {
                        // REQUIRED EDIT (DATA-MAPPING.md §1 row 21): Mochy read the database
                        // directly here (database.playlistSongs(playlist.id).first(), sorted by
                        // map position). The adapter's playlistSongs() returns the songs in
                        // playlist order, so there is nothing left to sort.
                        val songs = viewModel.playlistSongs(playlist.id)
                            .map { it.toMediaItem() }
                        if (songs.isEmpty()) return@launch
                        withContext(Dispatchers.Main) {
                            playerConnection.playQueue(ListQueue(title = playlist.playlist.name, items = songs))
                        }
                    }
                }
            }
    }
    // a new install's hero: what was just imported, or else a song from each artist picked in
    // onboarding (the artists themselves if their songs couldn't be fetched)
    val starterSongs by viewModel.starterSongs.collectAsState()
    val starterItems = remember(localPlaylists, libraryArtists, starterSongs) {
        val imported = localPlaylists.orEmpty()
            .filter { it.songCount > 0 && it.id != "liked" }
            .take(5)
            .map {
                StarterItem(
                    kind = StarterItem.Kind.Playlist,
                    id = it.id,
                    title = it.playlist.name,
                    detail = "${it.songCount} songs",
                    thumbnailUrl = it.thumbnails.firstOrNull() ?: it.playlist.thumbnailUrl,
                )
            }
        val songs = starterSongs
        imported.ifEmpty {
            when {
                // still being asked for; the hero holds its bones meanwhile
                songs == null -> emptyList()
                songs.isNotEmpty() -> songs.map {
                    StarterSongItem(it.song, it.artistName, it.artistThumbnail)
                }
                else -> libraryArtists.map {
                    StarterItem(
                        kind = StarterItem.Kind.Artist,
                        id = it.id,
                        title = it.artist.name,
                        detail = "",
                        thumbnailUrl = it.artist.thumbnailUrl,
                    )
                }
            }
        }
    }
    val homePage by viewModel.homePage.collectAsState()
    val dailyDiscover by viewModel.dailyDiscover.collectAsState()
    val localSongs by viewModel.localSongs.collectAsState()
    val similarRecommendations by viewModel.similarRecommendations.collectAsState()
    val communityPlaylists by viewModel.communityPlaylists.collectAsState()
    val recentlyPlayed by viewModel.recentlyPlayed.collectAsState()
    val mostPlayedSongsForHome by viewModel.mostPlayedSongsForHome.collectAsState()
    val recentHistorySongs by viewModel.recentHistorySongs.collectAsState()
    val artistListItems by viewModel.artistListItems.collectAsState()
    val allTimeHits by viewModel.allTimeHits.collectAsState()
    val liveRow by viewModel.liveRow.collectAsState()
    val musicVideoRow by viewModel.musicVideoRow.collectAsState()
    val feedVideoKinds by viewModel.feedVideoKinds.collectAsState()
    val feedVideoRows by viewModel.feedVideoRows.collectAsState()
    val homeCategories by viewModel.homeCategories.collectAsState()

    // Round 2 item 6: Classic's Top Picks row (null = never built, empty =
    // nothing to pick from) + the Find Your Mood expand state. The mood
    // shelf is static — it never waits on data, so it always shows.
    val topPicks by viewModel.topPicks.collectAsState()
    val topPicksLoading by viewModel.topPicksLoading.collectAsState()
    var moodsExpanded by remember { mutableStateOf(false) }

    val speedDialItems by viewModel.speedDialItems.collectAsState()
    val lastPlayedSong by viewModel.lastPlayedSong.collectAsState()

    val isLoading: Boolean by viewModel.isLoading.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    // keepListening stays null until the first local load lands, which also covers the moment
    // before load() has flipped isLoading on
    // right after a launch Home shows its bones through the intro and reveals its content after
    val launchHold = rememberLaunchHold()
    ReportLaunchHomeComposed()
    val isFirstLoad = isLoading || keepListening == null || launchHold
    val revealSeen = rememberRevealSeenState()

    // the gacha's reel: songs you play most, favourites you've drifted from, recommendations and
    // recent plays, all mixed together (only ones with a cover, so the reel never shows a blank)
    val gachaPool = remember(mostPlayedSongsForHome, forgottenFavorites, dailyDiscover, recentHistorySongs, keepListening, localSongs, recentlyPlayed) {
        buildList {
            mostPlayedSongsForHome?.forEach { add(it.toMediaMetadata()) }
            forgottenFavorites?.forEach { add(it.toMediaMetadata()) }
            dailyDiscover?.forEach { discover -> (discover.recommendation as? SongItem)?.let { add(it.toMediaMetadata()) } }
            recentHistorySongs?.forEach { add(it.toMediaMetadata()) }
            keepListening?.filterIsInstance<Song>()?.forEach { add(it.toMediaMetadata()) }
            // a thin library: the reel also eats the phone's own files (unplayed
            // local songs never reach the stats lists) + everything recently played
            localSongs?.forEach { add(it.toMediaMetadata()) }
            recentlyPlayed?.filterIsInstance<Song>()?.forEach { add(it.toMediaMetadata()) }
        }
            .filter { !it.thumbnailUrl.isNullOrEmpty() }
            .distinctBy { it.id }
    }

    val startListeningGridState = rememberTrackGridState()
    val quickPicksLazyGridState = rememberTrackGridState()
    val forgottenFavoritesLazyGridState = rememberTrackGridState()
    val homeStyle by rememberEnumPreference(HomeStyleKey, defaultValue = HomeStyle.MODERN)

    val startListeningSongs = remember(keepListening, recentHistorySongs, mostPlayedSongsForHome, forgottenFavorites) {
        buildList<Song> {
            keepListening?.filterIsInstance<Song>()?.forEach { add(it) }
            recentHistorySongs?.forEach { add(it) }
            mostPlayedSongsForHome?.forEach { add(it) }
            forgottenFavorites?.forEach { add(it) }
        }
            .distinctBy { it.id }
            .take(18)
    }

    val hopInLocalItems = remember(recentlyPlayed, keepListening, localPlaylists, mostPlayedSongsForHome) {
        buildList<LocalItem> {
            recentlyPlayed?.forEach { add(it) }
            keepListening?.forEach { add(it) }
            localPlaylists?.filter { it.songCount > 0 }?.forEach { add(it) }
            mostPlayedSongsForHome?.forEach { add(it) }
        }
            .distinctBy { "${it.javaClass.simpleName}_${it.id}" }
            .take(6)
    }

    val accountName by viewModel.accountName.collectAsState()
    val accountImageUrl by viewModel.accountImageUrl.collectAsState()
    val profilePicUri by rememberPreference(ProfilePicUriKey, "")

    val localProfileImageUrl = profilePicUri
        .takeIf { it.isNotBlank() }
        ?.let { if (it.contains("://")) it else "file://$it" }
    val profileImageUrl = localProfileImageUrl ?: accountImageUrl
    val context = LocalContext.current
    val profileImageRequest = remember(context, profileImageUrl) {
        profileImageUrl?.let { imageUrl ->
            ImageRequest.Builder(context)
                .data(imageUrl)
                .diskCachePolicy(CachePolicy.ENABLED)
                .diskCacheKey(imageUrl)
                .build()
        }
    }


    val scope = rememberCoroutineScope()

    val lazylistState = rememberLazyListState()

    val carouselHeightDp = heroHeight()
    val collapseDistancePx = with(LocalDensity.current) {
        if (homeStyle == HomeStyle.MODERN) carouselHeightDp.toPx() else 140.dp.toPx()
    }

    val firstItemScrollOffset by remember(collapseDistancePx) {
        derivedStateOf {
            if (lazylistState.firstVisibleItemIndex == 0) {
                lazylistState.firstVisibleItemScrollOffset.toFloat()
            } else {
                collapseDistancePx
            }
        }
    }
    val heroScrollProgress by remember(collapseDistancePx) {
        derivedStateOf {
            (firstItemScrollOffset / collapseDistancePx).coerceIn(0f, 1f)
        }
    }

    val heroScrollProgressProvider = remember { { heroScrollProgress } }
    // the compact tops' cover glow fades out as the list scrolls up under the top bar
    val topGlowVisibility = remember { { 1f - heroScrollProgress } }
    // the glass layer only switches on or off once, so reading the raw progress would redraw the list on every scroll frame
    val heroGlassActive = remember { derivedStateOf { heroScrollProgress > 0f } }

    // DROPPED (no NavController equivalent): Mochy scrolled to the top when its tab was
    // tapped, via the NavBackStackEntry's savedStateHandle ("scrollToTop" flag set by
    // onTabTapped). MochyNavigator carries no per-tab saved state; the Modern host owns
    // tab taps, so the host must drive scroll-to-top itself (NEEDS.md §(d)).
    // WIRED (coordinator, 15 Nov): the nav bar's re-tap on Home bumps
    // MochyNavigator.scrollToTopRequests; Home animates its list to the top.
    LaunchedEffect(navigator) {
        var first = true
        navigator.scrollToTopRequests.collect {
            if (first) {
                first = false
                return@collect
            }
            lazylistState.animateScrollToItem(0)
        }
    }

    LaunchedEffect(Unit) {
        // keyed on the page token as well as the scroll position: a short feed never scrolls, so
        // when the fresh token replaced the stale cached one nothing asked for the next page and
        // Home stopped after a section or two
        snapshotFlow {
            val layoutInfo = lazylistState.layoutInfo
            val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            homePage?.continuation?.takeIf { lastVisibleIndex >= layoutInfo.totalItemsCount - 3 }
        }
            .distinctUntilChanged()
            .collect { continuation ->
                if (continuation != null) viewModel.loadMoreYouTubeItems(continuation)
            }
    }

    NetworkReload(
        onReload = viewModel::refresh
    )

    // no pull to refresh: Home refreshes itself from what you play. the view model follows the
    // player for skips, and decides whether a refresh is due when the player is closed or Home
    // comes back into view
    LaunchedEffect(playerConnection) { viewModel.attachPlayer(playerConnection) }
    LaunchedEffect(playerBottomSheetState) {
        val sheet = playerBottomSheetState ?: return@LaunchedEffect
        snapshotFlow { sheet.isExpanded }
            .distinctUntilChanged()
            .drop(1)
            .collect { expanded -> if (!expanded) viewModel.onListeningBreak() }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onListeningBreak() }

    val playSong: (Song) -> Unit = { song ->
        if (song.id == mediaMetadataState.value?.id) {
            playerConnection.togglePlayPause()
        } else {
            playerConnection.playQueue(YouTubeQueue.radio(song.toMediaMetadata()))
        }
    }
    // Round 2 item 6: the Top Picks + mood play paths, mirroring Classic's
    // openTopPick/playMood in MainActivity. A release card opens the album;
    // a station or mood card fetches its queue (TopPicks.queue) and plays it
    // as a list. An empty queue is an honest toast, never a silent tap.
    val topPickCouldntPlay = stringResource(R.string.top_pick_couldnt_play)
    val playStationQueue: (TopPick, String) -> Unit = { pick, queueTitle ->
        scope.launch(Dispatchers.IO) {
            val songs = TopPicks.queue(pick).getOrNull().orEmpty()
            if (songs.isEmpty()) {
                withContext(Dispatchers.Main) { TopToaster.error(topPickCouldntPlay) }
                return@launch
            }
            val items = songs.map { it.toMochySong().toMediaItem() }
            withContext(Dispatchers.Main) {
                playerConnection.playQueue(ListQueue(title = queueTitle, items = items))
            }
        }
    }
    val playTopPick: (TopPick, String) -> Unit = { pick, title ->
        val browseId = pick.browseId
        if (pick.kind == TopPickKind.LATEST_RELEASE && browseId != null) {
            navigator.navigate("album/$browseId")
        } else {
            playStationQueue(pick, title)
        }
    }
    val playMood: (TopPickMood, String) -> Unit = { mood, title ->
        val hints = topPicks?.firstOrNull { it.kind == TopPickKind.MOOD }?.hints
        val pick = if (hints != null) TopPicks.moodStation(mood, hints) else TopPicks.moodStation(mood)
        playStationQueue(pick, title)
    }
    val showSongMenu: (Song) -> Unit = { song ->
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        menuState.show {
            SongMenu(originalSong = song, navigator = navigator, onDismiss = menuState::dismiss)
        }
    }
    val openLocalPlaylist: (String) -> Unit = { id ->
        if (id == "liked") navigator.navigate("auto_playlist/liked") else navigator.navigate("local_playlist/$id")
    }

    // every square card on Home that comes from the library
    val localItemCard: @Composable (LocalItem) -> Unit = { localItem ->
        val (onClick, onLongClick) = when (localItem) {
            is Song -> Pair<() -> Unit, () -> Unit>({ playSong(localItem) }, { showSongMenu(localItem) })
            is Album -> Pair<() -> Unit, () -> Unit>(
                { navigator.navigate("album/${localItem.id}") },
                {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    menuState.show {
                        AlbumMenu(originalAlbum = localItem, navigator = navigator, onDismiss = menuState::dismiss)
                    }
                }
            )
            is Artist -> Pair<() -> Unit, () -> Unit>(
                { navigator.navigate("artist/${localItem.id}") },
                {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    menuState.show {
                        ArtistMenu(originalArtist = localItem, coroutineScope = scope, onDismiss = menuState::dismiss)
                    }
                }
            )
            is Playlist -> Pair<() -> Unit, () -> Unit>(
                { openLocalPlaylist(localItem.id) },
                { haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
            )
        }
        val playlist = localItem as? Playlist
        HomeCoverCard(
            title = localItem.title,
            subtitle = when (localItem) {
                is Song -> localItem.artists.joinToString(", ") { it.name }
                is Album -> localItem.artists.joinToString(", ") { it.name }
                is Playlist -> pluralStringResource(R.plurals.n_song, localItem.songCount, localItem.songCount)
                is Artist -> null
            },
            // a library playlist's own thumbnailUrl is always null; its covers live in thumbnails
            thumbnailUrl = playlist?.thumbnails?.firstOrNull() ?: localItem.thumbnailUrl,
            circular = localItem is Artist,
            isActive = when (localItem) {
                is Song -> localItem.id == mediaMetadataState.value?.id
                is Album -> localItem.id == mediaMetadataState.value?.album?.id
                else -> false
            },
            isPlaying = isPlayingNow,
            sharedElementKey = when (localItem) {
                is Album -> "album-${localItem.id}"
                is Playlist -> "playlist-${localItem.id}"
                else -> null
            },
            cover = when {
                playlist == null -> null
                playlist.id == "liked" -> { size ->
                    LikedSongsThumbnail(size = size, shape = RoundedCornerShape(HomeCardCornerRadius))
                }
                else -> { size ->
                    PlaylistThumbnail(
                        thumbnails = playlist.thumbnails,
                        size = size,
                        shape = RoundedCornerShape(HomeCardCornerRadius),
                        placeHolder = {
                            Icon(
                                painter = painterResource(R.drawable.queue_music),
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.6f),
                                modifier = Modifier.size(size / 3)
                            )
                        },
                    )
                }
            },
            modifier = Modifier.combinedClickable(
                interactionSource = null,
                indication = HomeCardHighlight,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        )
    }

    // the same card for anything that came from YouTube
    val ytItemCard: @Composable (YTItem) -> Unit = { item ->
        HomeCoverCard(
            title = item.title,
            subtitle = when (item) {
                is SongItem -> item.artists.joinToString(", ") { it.name }
                is AlbumItem -> joinByBullet(item.artists?.joinToString(", ") { it.name }, item.year?.toString())
                is ArtistItem -> null
                is PlaylistItem -> joinByBullet(item.author?.name, item.songCountText)
            },
            thumbnailUrl = item.thumbnail,
            circular = item is ArtistItem,
            isActive = item.id in listOf(mediaMetadataState.value?.album?.id, mediaMetadataState.value?.id),
            isPlaying = isPlayingNow,
            sharedElementKey = when (item) {
                is AlbumItem -> "album-${item.id}"
                is PlaylistItem -> "playlist-${item.id}"
                else -> null
            },
            modifier = Modifier.combinedClickable(
                interactionSource = null,
                indication = HomeCardHighlight,
                onClick = {
                    when (item) {
                        is SongItem -> playerConnection.playQueue(
                            YouTubeQueue(item.endpoint ?: WatchEndpoint(videoId = item.id), item.toMediaMetadata())
                        )
                        is AlbumItem -> navigator.navigate("album/${item.id}")
                        is ArtistItem -> navigator.navigate("artist/${item.id}")
                        is PlaylistItem -> when {
                            item.id == "liked" -> navigator.navigate("auto_playlist/liked")
                            item.author?.name == "Local Playlist" -> navigator.navigate("local_playlist/${item.id}")
                            else -> navigator.navigate("online_playlist/${item.id}")
                        }
                    }
                },
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    menuState.show {
                        when (item) {
                            is SongItem -> YouTubeSongMenu(
                                song = item,
                                navigator = navigator,
                                onDismiss = menuState::dismiss
                            )
                            is AlbumItem -> YouTubeAlbumMenu(
                                albumItem = item,
                                navigator = navigator,
                                onDismiss = menuState::dismiss
                            )
                            is ArtistItem -> YouTubeArtistMenu(
                                artist = item,
                                onDismiss = menuState::dismiss
                            )
                            is PlaylistItem -> YouTubePlaylistMenu(
                                playlist = item,
                                coroutineScope = scope,
                                onDismiss = menuState::dismiss,
                                onImportedPlaylist = { playlistId ->
                                    navigator.navigate("local_playlist/$playlistId")
                                }
                            )
                        }
                    }
                }
            ),
        )
    }

    // the wide banner for every video row: Live Shows, Music Videos and YouTube's other video shelves.
    // a video plays as a video; the player shows it unless that's switched off in General settings
    val videoCard: @Composable (HomeVideo, Dp) -> Unit = { video, width ->
        HomeVideoCard(
            title = video.title,
            subtitle = video.subtitle,
            videoId = video.item.id,
            fallbackThumbnailUrl = video.item.thumbnail,
            width = width,
            isActive = video.item.id == mediaMetadataState.value?.id,
            isPlaying = isPlayingNow,
            modifier = Modifier.combinedClickable(
                interactionSource = null,
                indication = HomeCardHighlight,
                onClick = {
                    if (video.item.id == mediaMetadataState.value?.id) {
                        playerConnection.togglePlayPause()
                    } else {
                        playerConnection.playQueue(
                            YouTubeQueue(
                                video.item.endpoint ?: WatchEndpoint(videoId = video.item.id),
                                video.item.toMediaMetadata()
                            )
                        )
                    }
                },
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    menuState.show {
                        YouTubeSongMenu(
                            song = video.item,
                            navigator = navigator,
                            onDismiss = menuState::dismiss
                        )
                    }
                },
            ),
        )
    }

    // each row's card is written once up here and passed straight in: a fresh lambda at the call site
    // makes every composed card recompose whenever the feed list updates, e.g. while loading more
    val communityCard: @Composable (CommunityPlaylistItem) -> Unit = { item ->
        CommunityPlaylistCard(
            item = item,
            onClick = { navigator.navigate("online_playlist/${item.playlist.id}") }
        )
    }

    val artistGroupCard: @Composable (ArtistGroup) -> Unit = { group ->
        ArtistListCard(
            group = group,
            onClick = {
                val artistId = group.artistId
                if (artistId != null) {
                    ArtistPageCache.putPicks(artistId, group)
                    navigator.navigate("artist/$artistId/similar")
                } else {
                    // no browse id (the seed artist came from a bare name):
                    // search for the artist instead of leaving the tap dead
                    navigator.navigate("search/${Uri.encode(group.artistName)}")
                }
            },
            onItemClick = { item ->
                when (item) {
                    is SongItem -> playerConnection.playQueue(
                        YouTubeQueue(
                            item.endpoint ?: WatchEndpoint(videoId = item.id),
                            item.toMediaMetadata()
                        )
                    )
                    is AlbumItem -> navigator.navigate("album/${item.id}")
                    is PlaylistItem -> navigator.navigate("online_playlist/${item.id}")
                    else -> {}
                }
            }
        )
    }

    val hitsCard: @Composable (YTItem) -> Unit = { item ->
        AllTimeHitsCard(
            item = item,
            modifier = Modifier
                .width(160.dp)
                .combinedClickable(
                    onClick = {
                        when (item) {
                            is SongItem -> playerConnection.playQueue(
                                YouTubeQueue(
                                    item.endpoint ?: WatchEndpoint(videoId = item.id),
                                    item.toMediaMetadata()
                                )
                            )
                            is AlbumItem -> navigator.navigate("album/${item.id}")
                            is ArtistItem -> navigator.navigate("artist/${item.id}")
                            is PlaylistItem -> navigator.navigate("online_playlist/${item.id}")
                        }
                    },
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                )
        )
    }

    // the song rows of the paged grids: Most Played numbers them, History and Don't Forget don't
    val rankedTrackRow: @Composable (Int, Song) -> Unit = { index, song ->
        HomeTrackRow(
            thumbnailUrl = song.song.thumbnailUrl,
            songId = song.id,
            title = song.song.title,
            subtitle = song.artists.joinToString(", ") { it.name },
            isActive = song.id == mediaMetadataState.value?.id,
            isPlaying = isPlayingNow,
            rank = index + 1,
            onClick = { playSong(song) },
            onLongClick = { showSongMenu(song) },
            onMoreClick = { showSongMenu(song) },
        )
    }

    val trackRow: @Composable (Int, Song) -> Unit = { _, song ->
        HomeTrackRow(
            thumbnailUrl = song.song.thumbnailUrl,
            songId = song.id,
            title = song.song.title,
            subtitle = song.artists.joinToString(", ") { it.name },
            isActive = song.id == mediaMetadataState.value?.id,
            isPlaying = isPlayingNow,
            onClick = { playSong(song) },
            onLongClick = { showSongMenu(song) },
            onMoreClick = { showSongMenu(song) },
        )
    }

    val homeSections by remember {
        derivedStateOf {
            val list = mutableListOf<HomeSection>()

            // the recommendation rows are dealt out between Home's own rows, the way Apple Music
            // and Spotify mix "because you like" shelves in with your history
            val categories = homeCategories.orEmpty()
            var nextCategory = 0
            fun addCategories(count: Int = 1) = repeat(count) {
                categories.getOrNull(nextCategory)?.let {
                    list.add(HomeSection.Category(it.id))
                    nextCategory++
                }
            }

            // Round 2 item 6: Classic's order — Top Picks first, Recents the
            // lead shelf, Find Your Mood right after it — with the track
            // playing right now ahead of all three. Top Picks shows its
            // bones while the row is still being built.
            if (mediaMetadataState.value != null) list.add(HomeSection.NowPlaying)
            if (topPicks?.isNotEmpty() == true || (topPicks == null && topPicksLoading)) {
                list.add(HomeSection.TopPicks)
            }
            if (recentlyPlayed?.isNotEmpty() == true) list.add(HomeSection.RecentlyPlayed)
            list.add(HomeSection.FindYourMood)
            if (mostPlayedSongsForHome?.isNotEmpty() == true) list.add(HomeSection.MostPlayed)
            addCategories()
            if (communityPlaylists?.isNotEmpty() == true) list.add(HomeSection.FromTheCommunity)
            if (speedDialItems.isNotEmpty()) list.add(HomeSection.SpeedDial)
            if (recentHistorySongs?.isNotEmpty() == true) list.add(HomeSection.History)
            addCategories()
            // the two video banners sit apart, so the big frames break the feed up instead of piling up
            if (liveRow?.isNotEmpty() == true) list.add(HomeSection.LiveShows)
            addCategories()
            if (artistListItems?.isNotEmpty() == true) list.add(HomeSection.ArtistList)
            addCategories()
            if (accountPlaylists?.isNotEmpty() == true || localPlaylists?.isNotEmpty() == true) list.add(HomeSection.AccountPlaylists)
            addCategories()
            if (forgottenFavorites?.isNotEmpty() == true) list.add(HomeSection.DontForgetTheseSongs)
            addCategories()
            if (musicVideoRow?.isNotEmpty() == true) list.add(HomeSection.MusicVideos)
            addCategories(categories.size)

            // YouTube's live and music video shelves already sit inside the rows above
            val homePageSections = homePage?.sections.orEmpty()
                .mapIndexed { index, section -> index to section }
                .filter { (index, _) ->
                    feedVideoKinds[index] != FeedVideoKind.Live && feedVideoKinds[index] != FeedVideoKind.MusicVideos
                }
            homePageSections.firstOrNull()?.let { (index, section) -> list.add(HomeSection.HomePageSection(index, section.title)) }
            if (allTimeHits?.isNotEmpty() == true) list.add(HomeSection.AllTimeHits)
            homePageSections.drop(1).forEach { (index, section) ->
                list.add(HomeSection.HomePageSection(index, section.title))
            }

            list
        }
    }

    LaunchedEffect(mostPlayedSongsForHome) {
        quickPicksLazyGridState.scrollToItem(0)
    }

    LaunchedEffect(forgottenFavorites) {
        forgottenFavoritesLazyGridState.scrollToItem(0)
    }

    val baseTypography = MaterialTheme.typography
    val homeTypography = remember(baseTypography) {
        val tightenSp = 0.6f
        fun tighten(style: TextStyle) =
            style.copy(letterSpacing = (style.letterSpacing.value - tightenSp).sp)
        baseTypography.copy(
            titleLarge = tighten(baseTypography.titleLarge),
            titleMedium = tighten(baseTypography.titleMedium),
            titleSmall = tighten(baseTypography.titleSmall),
            bodyLarge = tighten(baseTypography.bodyLarge),
            bodyMedium = tighten(baseTypography.bodyMedium),
            bodySmall = tighten(baseTypography.bodySmall),
            labelLarge = tighten(baseTypography.labelLarge),
            labelMedium = tighten(baseTypography.labelMedium),
            labelSmall = tighten(baseTypography.labelSmall),
        )
    }

    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme,
        shapes = MaterialTheme.shapes,
        typography = homeTypography,
    ) {
    Box(modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopStart
        ) {
            // a song column is 304 of 401 in the Figma, so the next one peeks in from the edge
            val trackColumnWidth = if (maxWidth * 0.475f >= 320.dp) maxWidth * 0.475f else maxWidth * 0.76f
            // a video banner is 314 of 402, so the next one peeks in the same way
            val videoCardWidth = minOf(maxWidth * 0.78f, 340.dp)
            // a Top Picks card is 60% of the page, capped at 280dp — Classic's rule
            val topPickCardWidth = minOf(maxWidth * 0.60f, 280.dp)
            val videoRowCard: @Composable (HomeVideo) -> Unit = { video -> videoCard(video, videoCardWidth) }
            val hopInColumns = if (maxWidth >= 600.dp) 3 else 2

            val hopInCard: @Composable (LocalItem, Modifier) -> Unit = { localItem, cardModifier ->
                val (onClick, onLongClick) = when (localItem) {
                    is Song -> Pair<() -> Unit, () -> Unit>({ playSong(localItem) }, { showSongMenu(localItem) })
                    is Album -> Pair<() -> Unit, () -> Unit>(
                        { navigator.navigate("album/${localItem.id}") },
                        {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            menuState.show {
                                AlbumMenu(originalAlbum = localItem, navigator = navigator, onDismiss = menuState::dismiss)
                            }
                        }
                    )
                    is Artist -> Pair<() -> Unit, () -> Unit>(
                        { navigator.navigate("artist/${localItem.id}") },
                        {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            menuState.show {
                                ArtistMenu(originalArtist = localItem, coroutineScope = scope, onDismiss = menuState::dismiss)
                            }
                        }
                    )
                    is Playlist -> Pair<() -> Unit, () -> Unit>(
                        { openLocalPlaylist(localItem.id) },
                        { haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
                    )
                }
                val playlist = localItem as? Playlist
                HomeHopInCard(
                    title = localItem.title,
                    subtitle = when (localItem) {
                        is Song -> localItem.artists.joinToString(", ") { it.name }
                        is Album -> localItem.artists.joinToString(", ") { it.name }
                        is Playlist -> pluralStringResource(R.plurals.n_song, localItem.songCount, localItem.songCount)
                        is Artist -> null
                    },
                    thumbnailUrl = playlist?.thumbnails?.firstOrNull() ?: localItem.thumbnailUrl,
                    isActive = when (localItem) {
                        is Song -> localItem.id == mediaMetadataState.value?.id
                        is Album -> localItem.id == mediaMetadataState.value?.album?.id
                        else -> false
                    },
                    isPlaying = isPlayingNow,
                    onClick = onClick,
                    onLongClick = onLongClick,
                    sharedElementKey = when (localItem) {
                        is Album -> "album-${localItem.id}"
                        is Playlist -> "playlist-${localItem.id}"
                        else -> null
                    },
                    cover = when {
                        playlist == null -> null
                        playlist.id == "liked" -> { size, shape ->
                            LikedSongsThumbnail(size = size, shape = shape)
                        }
                        else -> { size, shape ->
                            PlaylistThumbnail(
                                thumbnails = playlist.thumbnails,
                                size = size,
                                shape = shape,
                                placeHolder = {
                                    Icon(
                                        painter = painterResource(R.drawable.queue_music),
                                        contentDescription = null,
                                        tint = Color.White.copy(alpha = 0.6f),
                                        modifier = Modifier.size(size / 3)
                                    )
                                },
                            )
                        }
                    },
                    modifier = cardModifier,
                )
            }

            val homeGlassState = remember { GlassState() }

            Box(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                val backgroundColor = Color.Black
                // DROPPED: Mochy wraps this LazyColumn in a CompositionLocalProvider
                // setting LocalGridItemPadding to 0.dp. Nothing staged reads that local
                // (the Items.kt rows that consume it are not used by HomeScreen), so
                // providing it here would be a no-op; the parallel Items task owns the
                // local (NEEDS.md §(a)).
                LazyColumn(
                    modifier = Modifier.glassRoot(
                        homeGlassState,
                        isActive = { heroGlassActive.value }
                    ),
                    state = lazylistState,
                    contentPadding = PaddingValues(
                        bottom = LocalPlayerAwareWindowInsets.current.asPaddingValues().calculateBottomPadding()
                    )
                ) {
                when (homeStyle) {
                    HomeStyle.MODERN -> {
                        item(key = "hero", contentType = "hero") {
                            HeroCarousel(
                                keepListening = keepListening,
                                lastPlayedSong = mediaMetadataState.value ?: lastPlayedSong,
                                dailyDiscover = dailyDiscover,
                                playerConnection = playerConnection,
                                navigator = navigator,
                                heroScrollProgressProvider = heroScrollProgressProvider,
                                isLoading = isFirstLoad || starterSongs == null,
                                hold = launchHold,
                                gachaPool = gachaPool,
                                starterItems = starterItems,
                                playlistPicks = playlistPicks,
                                similarRecommendations = similarRecommendations,
                                onRepeat = mostPlayedSongsForHome,
                                forgottenFavorites = forgottenFavorites,
                                modifier = Modifier.launchRise(order = 1),
                            )
                        }
                    }
                    HomeStyle.LISTEN_NOW -> {
                        item(key = "hero_listen_now", contentType = "hero_compact") {
                            // no glow under the launch bones; it fades in with the songs
                            val glowCoverUrls = remember(startListeningSongs, launchHold) {
                                if (launchHold) emptyList() else startListeningSongs.mapNotNull { it.thumbnailUrl }.take(2)
                            }
                            HomeCompactTop(glowCoverUrls, topGlowVisibility) {
                                if (launchHold || (isFirstLoad && startListeningSongs.isEmpty())) {
                                    BonesHost {
                                        HomeCompactTopBones(isHopIn = false)
                                    }
                                } else if (startListeningSongs.isNotEmpty()) {
                                    HomeSectionTitle(title = stringResource(R.string.home_start_listening))
                                    HomeTrackGrid(
                                        items = startListeningSongs,
                                        state = startListeningGridState,
                                        rowSpacing = 6.dp,
                                        columnWidth = trackColumnWidth,
                                        key = { _, song -> "listen_now_${song.id}" },
                                        content = trackRow,
                                    )
                                }
                            }
                        }
                    }
                    HomeStyle.HOP_IN -> {
                        item(key = "hero_hop_in", contentType = "hero_compact") {
                            // a library playlist's own thumbnailUrl is always null; its covers live in thumbnails
                            val glowCoverUrls = remember(hopInLocalItems, launchHold) {
                                if (launchHold) emptyList()
                                else hopInLocalItems.mapNotNull { (it as? Playlist)?.thumbnails?.firstOrNull() ?: it.thumbnailUrl }.take(2)
                            }
                            HomeCompactTop(glowCoverUrls, topGlowVisibility) {
                                if (launchHold || (isFirstLoad && hopInLocalItems.isEmpty())) {
                                    BonesHost {
                                        HomeCompactTopBones(isHopIn = true)
                                    }
                                } else if (hopInLocalItems.isNotEmpty()) {
                                    HomeSectionTitle(title = stringResource(R.string.home_hop_in))
                                    val visibleItems = hopInLocalItems.take(hopInColumns * 2)
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = HomeContentInset),
                                    ) {
                                        visibleItems.chunked(hopInColumns).forEach { rowItems ->
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                                modifier = Modifier.fillMaxWidth(),
                                            ) {
                                                rowItems.forEach { item ->
                                                    hopInCard(item, Modifier.weight(1f))
                                                }
                                                repeat(hopInColumns - rowItems.size) {
                                                    Spacer(Modifier.weight(1f))
                                                }
                                            }
                                        }
                                    }
                                }
                                Spacer(Modifier.height(34.dp))
                            }
                        }
                    }
                }

                // nothing but bones while the launch intro plays; the rows reveal once it lands
                if (!launchHold) homeSections.forEach { section ->
                    when (section) {
                        HomeSection.NowPlaying -> {
                            val nowPlaying = mediaMetadataState.value
                            if (nowPlaying != null) {
                                homeItem("now_playing_title", revealSeen, "title") {
                                    HomeSectionTitle(
                                        title = stringResource(R.string.now_playing),
                                        onClick = { playerBottomSheetState?.expandSoft() },
                                    )
                                }
                                homeItem("now_playing_row", revealSeen, "trackRow") {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = HomeContentInset),
                                    ) {
                                        HomeTrackRow(
                                            thumbnailUrl = nowPlaying.thumbnailUrl,
                                            title = nowPlaying.title,
                                            subtitle = nowPlaying.artists.joinToString(", ") { it.name },
                                            isActive = true,
                                            isPlaying = isPlayingNow,
                                            onClick = { playerBottomSheetState?.expandSoft() },
                                            onLongClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                playerConnection.togglePlayPause()
                                            },
                                            onMoreClick = null,
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                    }
                                }
                            }
                        }
                        HomeSection.TopPicks -> {
                            val picks = topPicks
                            homeItem("top_picks_title", revealSeen, "title") {
                                HomeSectionTitle(title = stringResource(R.string.top_picks_for_you))
                            }
                            if (!picks.isNullOrEmpty()) {
                                homeItem("top_picks_list", revealSeen, "cardRow") {
                                    HomeCardRow("top_picks", picks, revealSeen, key = { "${it.kind}:${it.title}" }) { pick ->
                                        TopPickCard(
                                            pick = pick,
                                            // "Guest" is the adapter's signed-out
                                            // default, not a name: a guest gets
                                            // "Your Station", not "Guest's Station"
                                            listenerName = accountName.takeUnless { it == "Guest" },
                                            onClick = playTopPick,
                                            onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
                                            modifier = Modifier.width(topPickCardWidth),
                                        )
                                    }
                                }
                            } else {
                                homeItem("top_picks_bones", revealSeen, "bones") {
                                    HomeSectionBones()
                                }
                            }
                        }
                        HomeSection.FindYourMood -> {
                            homeItem("find_your_mood_title", revealSeen, "title") {
                                HomeSectionTitle(
                                    title = stringResource(R.string.find_your_mood),
                                    onClick = { moodsExpanded = !moodsExpanded },
                                )
                            }
                            homeItem("find_your_mood_list", revealSeen, "cardRow") {
                                FindYourMoodContent(
                                    expanded = moodsExpanded,
                                    onMoodClick = playMood,
                                )
                            }
                        }
                        HomeSection.SpeedDial -> {
                            speedDialItems.takeIf { it.isNotEmpty() }?.let { items ->
                                homeItem("speed_dial_title", revealSeen, "title") {
                                    HomeSectionTitle(
                                        title = stringResource(R.string.speed_dial),
                                        onClick = { navigator.navigate("section_detail/speed_dial") }
                                    )
                                }
                                homeItem("speed_dial_list", revealSeen, "cardRow") {
                                    HomeCardRow("speed_dial", items, revealSeen, key = { it.id }, content = ytItemCard)
                                }
                            }
                        }
                        HomeSection.RecentlyPlayed -> {
                            recentlyPlayed?.takeIf { it.isNotEmpty() }?.let { items ->
                                homeItem("recently_played_title", revealSeen, "title") {
                                    HomeSectionTitle(
                                        title = stringResource(R.string.recently_played),
                                        onClick = { navigator.navigate("section_detail/recently_played") }
                                    )
                                }
                                homeItem("recently_played_list", revealSeen, "cardRow") {
                                    HomeCardRow("recently_played", items, revealSeen, key = { it.id }, content = localItemCard)
                                }
                            }
                        }
                        HomeSection.MostPlayed -> {
                            mostPlayedSongsForHome?.takeIf { it.isNotEmpty() }?.let { mostPlayed ->
                                homeItem("most_played_title", revealSeen, "title") {
                                    HomeSectionTitle(
                                        title = stringResource(R.string.home_most_played),
                                        onClick = { navigator.navigate("section_detail/most_played") },
                                    )
                                }
                                homeItem("most_played_list", revealSeen, "trackGrid") {
                                    HomeTrackGrid(
                                        items = mostPlayed,
                                        state = quickPicksLazyGridState,
                                        rowSpacing = 6.dp,
                                        columnWidth = trackColumnWidth,
                                        key = { _, song -> song.id },
                                        content = rankedTrackRow,
                                    )
                                }
                            }
                        }
                        HomeSection.History -> {
                            recentHistorySongs?.takeIf { it.isNotEmpty() }?.let { history ->
                                homeItem("history_title", revealSeen, "title") {
                                    HomeSectionTitle(
                                        title = stringResource(R.string.home_history),
                                        onClick = { navigator.navigate("history") }
                                    )
                                }
                                homeItem("history_list", revealSeen, "trackGrid") {
                                    HomeTrackGrid(
                                        items = history,
                                        state = rememberTrackGridState(),
                                        rowSpacing = 6.dp,
                                        columnWidth = trackColumnWidth,
                                        key = { index, song -> "history_${index}_${song.id}" },
                                        content = trackRow,
                                    )
                                }
                            }
                        }
                        HomeSection.FromTheCommunity -> {
                            communityPlaylists?.takeIf { it.isNotEmpty() }?.let { playlists ->
                                homeItem("community_playlists_title", revealSeen, "title") {
                                    HomeSectionTitle(
                                        title = stringResource(R.string.from_the_community),
                                        onClick = { navigator.navigate("section_detail/from_the_community") }
                                    )
                                }
                                homeItem("community_playlists_content", revealSeen, "cardRow") {
                                    HomeCardRow("community_playlists", playlists, revealSeen, key = { it.playlist.id }, content = communityCard)
                                }
                            }
                        }
                        HomeSection.ArtistList -> {
                            artistListItems?.takeIf { it.isNotEmpty() }?.let { items ->
                                homeItem("artist_list_title", revealSeen, "title") {
                                    // just a title: each box below is its own artist's page
                                    HomeSectionTitle(title = stringResource(R.string.artist_list))
                                }
                                homeItem("artist_list_content", revealSeen, "cardRow") {
                                    HomeCardRow("artist_list", items, revealSeen, key = { "${it.artistId}_${it.artistName}" }, content = artistGroupCard)
                                }
                            }
                        }
                        HomeSection.AllTimeHits -> {
                            allTimeHits?.takeIf { it.isNotEmpty() }?.let { hits ->
                                homeItem("all_time_hits_title", revealSeen, "title") {
                                    HomeSectionTitle(
                                        title = stringResource(R.string.all_time_hits),
                                        onClick = { navigator.navigate("section_detail/all_time_hits") }
                                    )
                                }
                                homeItem("all_time_hits_list", revealSeen, "cardRow") {
                                    HomeCardRow("all_time_hits", hits, revealSeen, key = { it.id }, content = hitsCard)
                                }
                            }
                        }
                        HomeSection.LiveShows, HomeSection.MusicVideos -> {
                            val isLive = section == HomeSection.LiveShows
                            (if (isLive) liveRow else musicVideoRow)?.takeIf { it.isNotEmpty() }?.let { videos ->
                                homeItem("${section.id}_title", revealSeen, "title") {
                                    HomeSectionTitle(
                                        title = stringResource(if (isLive) R.string.live_shows else R.string.music_videos_for_you),
                                        onClick = { navigator.navigate("section_detail/${section.id}") }
                                    )
                                }
                                homeItem("${section.id}_list", revealSeen, "cardRow") {
                                    HomeCardRow(section.id, videos, revealSeen, key = { it.item.id }, content = videoRowCard)
                                }
                            }
                        }
                        is HomeSection.Category -> {
                            homeCategories?.firstOrNull { it.id == section.categoryId }?.let { category ->
                                homeItem("${section.id}_title", revealSeen, "title") {
                                    val title = when (category.kind) {
                                        HomeCategory.Kind.NewFromArtists -> stringResource(R.string.home_new_from_your_artists)
                                        else -> category.title
                                    }
                                    HomeSectionTitle(
                                        label = when (category.kind) {
                                            HomeCategory.Kind.BecauseYouLike -> stringResource(R.string.home_because_you_like)
                                            HomeCategory.Kind.MoreLikeArtist -> stringResource(R.string.home_more_like)
                                            HomeCategory.Kind.YourGenre -> stringResource(R.string.home_because_you_listen_to)
                                            HomeCategory.Kind.NewGenre -> stringResource(R.string.home_try_something_new)
                                            HomeCategory.Kind.NewFromArtists -> null
                                        },
                                        title = title,
                                        thumbnail = category.thumbnailUrl?.let { thumbnailUrl ->
                                            {
                                                AsyncImage(
                                                    model = thumbnailUrl.resize(90, 90),
                                                    contentDescription = null,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier
                                                        .size(30.dp)
                                                        .clip(
                                                            if (category.kind == HomeCategory.Kind.MoreLikeArtist) {
                                                                CircleShape
                                                            } else {
                                                                RoundedCornerShape(ThumbnailCornerRadius)
                                                            }
                                                        )
                                                )
                                            }
                                        },
                                        onClick = category.endpoint?.let { endpoint ->
                                            {
                                                if (category.kind == HomeCategory.Kind.MoreLikeArtist) {
                                                    navigator.navigate("artist/${endpoint.browseId}")
                                                } else {
                                                    navigator.navigate("genre/${endpoint.browseId}?params=${endpoint.params}")
                                                }
                                            }
                                        },
                                    )
                                }
                                homeItem("${section.id}_list", revealSeen, "cardRow") {
                                    HomeCardRow(section.id, category.items, revealSeen, key = { it.id }, content = ytItemCard)
                                }
                            }
                        }
                        HomeSection.AccountPlaylists -> {
                            if (!localPlaylists.isNullOrEmpty() || !accountPlaylists.isNullOrEmpty()) {
                                homeItem("account_playlists_title", revealSeen, "title") {
                                    Box {
                                        HomeSectionTitle(
                                            label = stringResource(R.string.home_your_playlists),
                                            title = accountName,
                                            thumbnail = {
                                                if (profileImageUrl != null) {
                                                    AsyncImage(
                                                        model = profileImageRequest,
                                                        placeholder = painterResource(id = R.drawable.person),
                                                        error = painterResource(id = R.drawable.person),
                                                        contentDescription = null,
                                                        contentScale = ContentScale.Crop,
                                                        modifier = Modifier
                                                            .size(30.dp)
                                                            .clip(CircleShape)
                                                    )
                                                } else {
                                                    Icon(
                                                        painter = painterResource(id = R.drawable.person),
                                                        contentDescription = null,
                                                        tint = Color.White.copy(alpha = 0.69f),
                                                        modifier = Modifier.size(30.dp)
                                                    )
                                                }
                                            },
                                        )

                                    }
                                }

                                homeItem("account_playlists_list", revealSeen, "cardRow") {
                                    LazyRow(
                                        state = rememberCardRowState(),
                                        contentPadding = PaddingValues(horizontal = HomeContentInset),
                                        horizontalArrangement = Arrangement.spacedBy(HomeCardSpacing)
                                    ) {
                                        localPlaylists?.let { items ->
                                            items(items = items, key = { it.id }) { item ->
                                                localItemCard(item)
                                            }
                                        }
                                        accountPlaylists?.let { items ->
                                            items(items = items, key = { it.id }) { item ->
                                                ytItemCard(item)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        HomeSection.DontForgetTheseSongs -> {
                            forgottenFavorites?.takeIf { it.isNotEmpty() }?.let { forgottenFavorites ->
                                homeItem("forgotten_favorites_title", revealSeen, "title") {
                                    HomeSectionTitle(
                                        title = stringResource(R.string.dont_forget_these_songs),
                                        onClick = { navigator.navigate("section_detail/forgotten_favorites") },
                                    )
                                }
                                homeItem("forgotten_favorites_list", revealSeen, "trackGrid") {
                                    HomeTrackGrid(
                                        items = forgottenFavorites,
                                        state = forgottenFavoritesLazyGridState,
                                        rowSpacing = 6.dp,
                                        columnWidth = trackColumnWidth,
                                        key = { _, song -> song.id },
                                        content = trackRow,
                                    )
                                }
                            }
                        }
                        is HomeSection.HomePageSection -> {
                            val sectionData = homePage?.sections?.getOrNull(section.index)
                            sectionData?.let {
                                homeItem("home_section_title_${section.id}", revealSeen, "title") {
                                    HomeSectionTitle(
                                        title = sectionData.title,
                                        label = sectionData.label,
                                        thumbnail = sectionData.thumbnail?.let { thumbnailUrl ->
                                            {
                                                val shape = if (sectionData.endpoint?.isArtistEndpoint == true) {
                                                    CircleShape
                                                } else {
                                                    RoundedCornerShape(ThumbnailCornerRadius)
                                                }
                                                AsyncImage(
                                                    model = thumbnailUrl,
                                                    contentDescription = null,
                                                    modifier = Modifier
                                                        .size(30.dp)
                                                        .clip(shape)
                                                )
                                            }
                                        },
                                        onClick = sectionData.endpoint?.let { endpoint ->
                                            {
                                                when {
                                                    endpoint.browseId == "FEmusic_moods_and_genres" ->
                                                        // there is no mood_and_genres page (Mochy
                                                        // never had one either): the moods &
                                                        // genres grid lives on the Search tab
                                                        navigator.navigateToTab("search")
                                                    endpoint.params != null ->
                                                        navigator.navigate("youtube_browse/${endpoint.browseId}?params=${endpoint.params}")
                                                    else ->
                                                        navigator.navigate("browse/${endpoint.browseId}")
                                                }
                                            }
                                        },
                                    )
                                }

                                homeItem("home_section_list_${section.id}", revealSeen, "cardRow") {
                                    val videos = feedVideoRows[section.index]
                                    if (videos != null) {
                                        HomeCardRow(
                                            "home_section_${section.id}",
                                            videos,
                                            revealSeen,
                                            key = { it.item.id },
                                            content = videoRowCard,
                                        )
                                    } else {
                                        HomeCardRow("home_section_${section.id}", sectionData.items, revealSeen, key = { it.id }, content = ytItemCard)
                                    }
                                }
                            }
                        }
                    }

                    item(key = "section_spacer_${section.id}", contentType = "spacer") {
                        Spacer(modifier = Modifier.height(34.dp))
                    }
                }

                // only while something is really on its way; a page token alone used to leave
                // these bones up forever when the next page couldn't load
                if (launchHold || (isFirstLoad && speedDialItems.isEmpty() && keepListening.isNullOrEmpty()) || isLoadingMore) {
                    item(key = "loading_bones", contentType = "bones") {
                        BonesHost(
                            modifier = Modifier
                                .animateItem(
                                    fadeInSpec = tween(220),
                                    placementSpec = null,
                                    fadeOutSpec = tween(220),
                                )
                                .launchRise(order = 2)
                        ) {
                            repeat(2) {
                                HomeSectionBones()
                                Spacer(modifier = Modifier.height(34.dp))
                            }
                        }
                    }
                }

                item(key = "bottom_spacer", contentType = "spacer") {
                    Spacer(modifier = Modifier.height(30.dp))
                }
            }

            Box(
                modifier = Modifier.fillMaxWidth()
            ) {

                val showHeroOverlay by remember { derivedStateOf { heroScrollProgressProvider() > 0.01f } }
                if (showHeroOverlay) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .graphicsLayer { alpha = heroScrollProgressProvider().coerceIn(0f, 1f) }
                    ) {
                        ProgressiveGlassBackground(
                            state = homeGlassState,
                            maxBlurRadius = {
                                val sheetProgress = playerBottomSheetState?.progress ?: 0f
                                50f * heroScrollProgressProvider().coerceIn(0f, 1f) * (1f - sheetProgress.coerceIn(0f, 1f))
                            },
                            foundationColor = backgroundColor,
                            direction = BlurDirection.BottomToTop,

                            steps = 3,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    val progressiveBar = rememberProgressiveBlur()
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .drawWithCache {
                                // every stop is black, so the fade is built once and only its alpha follows the scroll
                                val fade = if (progressiveBar) {
                                    Brush.verticalGradient(
                                        0f to Color.Black.copy(alpha = 0.9f),
                                        0.3f to Color.Black.copy(alpha = 0.6f),
                                        0.6f to Color.Black.copy(alpha = 0.3f),
                                        1f to Color.Transparent
                                    )
                                } else {
                                    androidx.compose.ui.graphics.SolidColor(Color.Black.copy(alpha = FlatBarScrimAlpha))
                                }
                                onDrawBehind {
                                    drawRect(brush = fade, alpha = heroScrollProgressProvider().coerceIn(0f, 1f))
                                }
                            }
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            top = WindowInsets.stableSystemBars.asPaddingValues().calculateTopPadding() + 14.dp,
                            bottom = 16.dp,
                            start = HomeContentInset,
                            end = 30.dp
                        ),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val isScrolled by remember { derivedStateOf { heroScrollProgress > 0.5f } }
                    val topBarProgress by animateFloatAsState(
                        targetValue = if (isScrolled) 1f else 0f,
                        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
                        label = "topBarProgress"
                    )

                    // the brand mark over the hero, swapped for a bold "Home" once the hero scrolls away.
                    // it's also where the launch intro lands the splash mark
                    Box(contentAlignment = Alignment.CenterStart) {
                        Icon(
                            painter = painterResource(R.drawable.ic_chordwave_mark),
                            contentDescription = "ChordWave",
                            tint = Color.White,
                            modifier = Modifier
                                .launchMarkTarget()
                                .size(HomeMarkWidth, HomeMarkHeight)
                                .graphicsLayer {
                                    alpha = 1f - topBarProgress
                                    val scale = 1f - topBarProgress * 0.25f
                                    scaleX = scale
                                    scaleY = scale
                                    transformOrigin = TransformOrigin(0f, 0.5f)
                                    renderEffect = BlurEffectCache.element(topBarProgress * 15f)
                                }
                        )

                        Text(
                            text = stringResource(R.string.home),
                            style = TextStyle(
                                fontFamily = InterFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 32.sp,
                                letterSpacing = (-0.06).em,
                            ),
                            color = Color.White,
                            maxLines = 1,
                            modifier = Modifier.graphicsLayer {
                                alpha = topBarProgress
                                renderEffect = BlurEffectCache.element((1f - topBarProgress) * 15f)
                            }
                        )
                    }

                    Box(
                        modifier = Modifier
                            .launchRise(order = 0, distance = HomeTopBarRise)
                            .graphicsLayer {
                                val scale = 1f - (topBarProgress * 0.1f)
                                scaleX = scale
                                scaleY = scale
                                transformOrigin = TransformOrigin(1f, 0.5f)
                            }
                            .height(44.dp)
                            .clip(RoundedCornerShape(50))
                            .clickable { navigator.navigateToTab("profile") },
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
            val avatarBoundsModifier = Modifier
                            if (profileImageUrl != null) {
                                AsyncImage(
                                    model = profileImageRequest,
                                    placeholder = painterResource(R.drawable.person),
                                    error = painterResource(R.drawable.person),
                                    contentDescription = stringResource(R.string.profile_label),
                                    modifier = avatarBoundsModifier
                                        .size(36.dp)
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(
                                    modifier = avatarBoundsModifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.person),
                                        contentDescription = stringResource(R.string.profile_label),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }


        }
    }
    }
}

private const val MinOfflineDurationMs = 3000L

/**
 * Mochy's ui/screens/NetworkReload.kt (plus its NetworkConnectivityObserver) inlined as a
 * private helper: when the network comes back after being down for at least 3 seconds, Home
 * reloads. Kept in this file because no other screen needs it yet; split it out into its own
 * ui.mochy.screens file if one does.
 */
@Composable
private fun NetworkReload(
    onReload: () -> Unit
) {
    val context = LocalContext.current
    LaunchedEffect(context) {
        val connectivityManager =
            context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkStatusChannel = Channel<Boolean>(Channel.CONFLATED)
        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                networkStatusChannel.trySend(true)
            }

            override fun onLost(network: Network) {
                networkStatusChannel.trySend(false)
            }
        }
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
            .build()
        try {
            connectivityManager.registerNetworkCallback(request, networkCallback)
        } catch (_: Exception) {
            networkStatusChannel.trySend(true)
        }
        val activeNetwork = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
        networkStatusChannel.trySend(
            capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        )
        try {
            var offlineSinceMs: Long? = null
            networkStatusChannel.receiveAsFlow().collect { isConnected ->
                if (isConnected) {
                    val offlineSince = offlineSinceMs
                    if (offlineSince != null && System.currentTimeMillis() - offlineSince >= MinOfflineDurationMs) {
                        onReload()
                    }
                    offlineSinceMs = null
                } else if (offlineSinceMs == null) {
                    offlineSinceMs = System.currentTimeMillis()
                }
            }
        } finally {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        }
    }
}
