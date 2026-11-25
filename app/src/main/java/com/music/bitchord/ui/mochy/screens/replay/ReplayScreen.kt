// Modern (Mochy-style) Replay page.
//
// The engine is untouched and reused as-is: ReplayState / rememberReplayState,
// ReplaySummary and every helper in ui/replay/ReplayModel.kt. This file only
// re-draws the page in the Modern visual language — dark-only (MochyTheme),
// Mochy list components (ChipsRow, ItemThumbnail, SongMenu, TopToaster),
// PopupSheetState sheets via LocalMenuState, navigation through
// MochyNavigator. Classic ui/replay/ReplayScreen.kt is kept and still hosts
// the Classic mode; nothing here edits it.
//
// Route: `replay` (host wiring in NEEDS.md §1). The same content also fills
// the Library's Replay tab (batch 39): [rememberReplayPageState] owns the
// state and [replayItems] emits the page into whichever LazyColumn hosts
// it — the route's own, or the Library scaffold's. Batch 39 also unified
// the look with the rest of Modern: flat Mochy cards on black (the artwork
// mesh wash is gone), Inter typography throughout, cascade reveals, and a
// collapsing glass header on the route like Home's and Stats'.
package com.music.bitchord.ui.mochy.screens.replay

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.music.bitchord.R
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.model.Song as CwSong
import com.music.bitchord.data.model.artworkAt
import com.music.bitchord.data.model.CARD_ART_PX
import com.music.bitchord.data.stats.ArtistFacts
import com.music.bitchord.data.stats.ReplayPeriod
import com.music.bitchord.data.stats.ReplaySummary
import com.music.bitchord.ui.MainViewModel
import com.music.bitchord.ui.mochy.LocalPlayerAwareWindowInsets
import com.music.bitchord.ui.mochy.component.BlurDirection
import com.music.bitchord.ui.mochy.component.BlurEffectCache
import com.music.bitchord.ui.mochy.component.BoneColor
import com.music.bitchord.ui.mochy.component.ChipsRow
import com.music.bitchord.ui.mochy.component.FlatBarScrimAlpha
import com.music.bitchord.ui.mochy.component.GlassState
import com.music.bitchord.ui.mochy.component.ItemThumbnail
import com.music.bitchord.ui.mochy.component.LocalMenuState
import com.music.bitchord.ui.mochy.component.ProgressiveGlassBackground
import com.music.bitchord.ui.mochy.component.RevealSeenState
import com.music.bitchord.ui.mochy.component.TopToaster
import com.music.bitchord.ui.mochy.component.glassRoot
import com.music.bitchord.ui.mochy.component.rememberProgressiveBlur
import com.music.bitchord.ui.mochy.component.rememberRevealSeenState
import com.music.bitchord.ui.mochy.component.revealOnAppear
import com.music.bitchord.ui.mochy.extensions.toMediaItem
import com.music.bitchord.ui.mochy.models.toMochySong
import com.music.bitchord.ui.mochy.navigation.MochyNavigator
import com.music.bitchord.ui.mochy.playback.LocalPlayerConnection
import com.music.bitchord.ui.mochy.playback.queues.ListQueue
import com.music.bitchord.ui.mochy.player.menu.SongMenu
import com.music.bitchord.ui.mochy.theme.InterFontFamily
import com.music.bitchord.ui.mochy.utils.stableSystemBars
import com.music.bitchord.ui.replay.InitialTile
import com.music.bitchord.ui.replay.RankBadge
import com.music.bitchord.ui.replay.ReplayHeroCard
import com.music.bitchord.ui.replay.ReplayRow
import com.music.bitchord.ui.replay.ReplayState
import com.music.bitchord.ui.replay.ReplayStoryPage
import com.music.bitchord.ui.replay.albumRows
import com.music.bitchord.ui.replay.artistRows
import com.music.bitchord.ui.replay.cards
import com.music.bitchord.ui.replay.formatDay
import com.music.bitchord.ui.replay.formatHour
import com.music.bitchord.ui.replay.formatListening
import com.music.bitchord.ui.replay.genreRows
import com.music.bitchord.ui.replay.localizedLabel
import com.music.bitchord.ui.replay.localizedTab
import com.music.bitchord.ui.replay.rememberReplayState
import com.music.bitchord.ui.replay.songRows
import com.music.bitchord.ui.theme.AccentMint
import kotlinx.coroutines.launch
import timber.log.Timber

// The Modern page colours, the same values the Stats page and Home draw with.
private val ReplayCard = Color(0xFF0F0F0F)
private val ReplayChipBg = Color(0xFF2A2A2A)
private val ReplayTile = Color(0xFF1A1A1A)
private val ReplayInkMuted = Color(0xFF7A7A7A)
private val ReplayPagePadding = 20.dp

private fun replayText(
    size: Float,
    weight: FontWeight = FontWeight.Bold,
    color: Color = Color.White,
    tracking: Float = -0.02f * size,
) = TextStyle(fontFamily = InterFontFamily, fontWeight = weight, fontSize = size.sp, letterSpacing = tracking.sp, color = color)

/**
 * Everything the Replay page needs, hoisted out of the screen so the route
 * and the Library tab draw one page from one state. Playback reads stay
 * lambdas, so a track change only recomposes the row that reads them.
 */
internal class ReplayPageState(
    val replayState: ReplayState,
    val setPeriod: (ReplayPeriod) -> Unit,
    val holder: String,
    val nowPlayingId: () -> String?,
    val isPlayingNow: () -> Boolean,
    val onCardClick: (ReplayStoryPage) -> Unit,
    val onPlayReplay: () -> Unit,
    val onShare: (ReplayStoryPage?) -> Unit,
    val playSong: (CwSong) -> Unit,
    val openSongMenu: (CwSong) -> Unit,
    val openArtist: (String?, String) -> Unit,
    val openAlbum: (String?, String, String?) -> Unit,
) {
    val summary: ReplaySummary? get() = replayState.summary
}

/**
 * The Replay's state, owned by whichever host shows it (the `replay` route
 * or the Library tab). Null while the player service isn't bound yet.
 *
 * Stories are a full-screen overlay owned here (not a route), the way
 * Classic hosts them as activity state: back closes the story first, then
 * the page.
 */
@Composable
internal fun rememberReplayPageState(
    navigator: MochyNavigator,
    mainViewModel: MainViewModel = viewModel(),
): ReplayPageState? {
    val context = LocalContext.current
    val (replayState, setPeriod) = rememberReplayState(active = true, pageOpen = true)
    val account by mainViewModel.account.collectAsState()
    val holder = account?.name.orEmpty()
    val playerConnection = LocalPlayerConnection.current ?: return null
    val menuState = LocalMenuState.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsState()

    var storyPage by remember { mutableStateOf<ReplayStoryPage?>(null) }
    val summary = replayState.summary

    /** Opens the share sheet for one story card, or the whole Replay when null. */
    fun openShare(page: ReplayStoryPage?) {
        val current = summary ?: return
        menuState.show {
            ReplayShareSheet(
                summary = current,
                holder = holder,
                memberSince = replayState.memberSince,
                page = page,
                onDismiss = menuState::dismiss,
            )
        }
    }

    /**
     * Plays a chart song the way Classic does: the song itself, then a
     * station off it ([YtMusicRepository.radio]) — through the Modern player
     * path ([com.music.bitchord.ui.mochy.playback.MochyPlayerConnection.playQueue]
     * with a [ListQueue] of [toMediaItem] items). A radio that comes back
     * empty still plays the song on its own rather than failing the tap;
     * that fallback is logged, not silent.
     */
    fun playSong(song: CwSong) {
        if (playerConnection.player == null) {
            Timber.w("Replay: player not bound; cannot play ${song.videoId}")
            TopToaster.error(context.getString(R.string.mochy_replay_playback_unavailable))
            return
        }
        scope.launch {
            val radio = YtMusicRepository.radio(song.videoId).getOrNull().orEmpty()
                .filter { it.videoId != song.videoId }
            if (radio.isEmpty()) {
                Timber.w("Replay: radio for ${song.videoId} came back empty; playing the song on its own")
            }
            val items = (listOf(song) + radio).map { it.toMochySong().toMediaItem() }
            playerConnection.playQueue(
                ListQueue(
                    title = context.getString(R.string.replay),
                    items = items,
                    startIndex = 0,
                ),
            )
        }
    }

    /** The song menu, Mochy-style, for a long-pressed chart song. */
    fun openSongMenu(song: CwSong) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        menuState.show {
            SongMenu(
                originalSong = song.toMochySong(),
                navigator = navigator,
                onDismiss = menuState::dismiss,
            )
        }
    }

    /**
     * Opens an artist page. The browse id is null for most rows — an artist
     * is counted by name — so a row without one falls back to a Modern
     * search for the name, which is the closest the Modern host has to
     * Classic's openByName (see NEEDS.md §3).
     */
    fun openArtist(browseId: String?, name: String) {
        if (browseId != null) {
            navigator.navigate("artist/$browseId")
        } else {
            navigator.navigate("search/${Uri.encode(name)}")
        }
    }

    /** Same fallback as [openArtist], for an album row. */
    fun openAlbum(browseId: String?, title: String, artist: String?) {
        if (browseId != null) {
            navigator.navigate("album/$browseId")
        } else {
            navigator.navigate("search/${Uri.encode(listOfNotNull(title, artist).joinToString(" "))}")
        }
    }

    val story = storyPage
    BackHandler(enabled = story != null) { storyPage = null }

    // Stories are drawn by the host above the app chrome (see
    // ReplayStoriesHost): this state only decides when they open, and
    // the host draws them. The clock pauses while the share sheet is up,
    // or the card being sent would have moved on by the time an app was
    // picked — the host reads that off the menu state itself.
    LaunchedEffect(storyPage, summary) {
        val start = storyPage
        if (start != null && summary != null) {
            ReplayStoriesOverlay.open(summary, start, onShare = { openShare(it) }, onClose = { storyPage = null })
        } else {
            ReplayStoriesOverlay.close()
        }
    }
    DisposableEffect(Unit) { onDispose { ReplayStoriesOverlay.close() } }

    return ReplayPageState(
        replayState = replayState,
        setPeriod = setPeriod,
        holder = holder,
        nowPlayingId = { mediaMetadata?.id },
        isPlayingNow = { isPlaying },
        onCardClick = { storyPage = it },
        onPlayReplay = { storyPage = ReplayStoryPage.INTRO },
        onShare = ::openShare,
        playSong = ::playSong,
        openSongMenu = ::openSongMenu,
        openArtist = ::openArtist,
        openAlbum = ::openAlbum,
    )
}

/**
 * The Replay page, Modern style: four headline cards, four charts, stories
 * and a shareable poster — the same numbers as Classic, drawn the Mochy way.
 *
 * The state is hoisted nowhere: [rememberReplayState] is called here with
 * `active = true`, because this screen is only composed while Replay is open
 * (Classic hoists it to MainActivity because the Library cards read the same
 * numbers; the Modern Library has no Replay cards yet — see NEEDS.md §4).
 * The period still survives rotation/back-stack trips via the rememberSaveable
 * inside [rememberReplayState].
 */
@Composable
fun ReplayScreen(
    navigator: MochyNavigator,
    landingPage: ReplayStoryPage = ReplayStoryPage.INTRO,
    mainViewModel: MainViewModel = viewModel(),
) {
    val state = rememberReplayPageState(navigator, mainViewModel) ?: return
    val listState = rememberLazyListState()
    val revealSeen = rememberRevealSeenState()
    val density = LocalDensity.current
    val glassState = remember { GlassState() }
    val topInset = WindowInsets.stableSystemBars.asPaddingValues().calculateTopPadding()
    val collapse by remember {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) {
                1f
            } else {
                (listState.firstVisibleItemScrollOffset / with(density) { 90.dp.toPx() }).coerceIn(0f, 1f)
            }
        }
    }

    val summary = state.summary
    // Library cards open this page at the chart they summarise. Once only
    // per opening — back from an artist/album opened off this page must not
    // throw away the scroll position. Same math as Classic (item order is
    // identical: heading, cards, play row, then the charts).
    var landed by rememberSaveable(landingPage, state.replayState.period) { mutableStateOf(false) }
    LaunchedEffect(landingPage, state.replayState.period, summary == null) {
        if (landed) return@LaunchedEffect
        val target = when {
            landingPage == ReplayStoryPage.INTRO || landingPage == ReplayStoryPage.MINUTES -> 0
            summary == null || summary.isEmpty -> 0
            else -> replayChartIndex(summary, landingPage)
        }
        listState.scrollToItem(target)
        if (summary != null) landed = true
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Box(Modifier.fillMaxSize().glassRoot(glassState, isActive = { collapse > 0f })) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = topInset + 82.dp,
                    bottom = LocalPlayerAwareWindowInsets.current
                        .only(WindowInsetsSides.Bottom)
                        .union(WindowInsets.ime)
                        .asPaddingValues()
                        .calculateBottomPadding(),
                ),
            ) {
                replayItems(state, revealSeen, embedded = false, collapse = { collapse })
            }
        }

        // glass bar: a sibling of the glassRoot box, never inside it
        val showGlass by remember { derivedStateOf { collapse > 0.01f } }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(topInset + 84.dp)
                .graphicsLayer { alpha = collapse },
        ) {
            if (showGlass) {
                ProgressiveGlassBackground(
                    state = glassState,
                    maxBlurRadius = { 50f * collapse },
                    foundationColor = Color.Black.copy(alpha = 0.5f),
                    direction = BlurDirection.BottomToTop,
                    steps = 3,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            val progressiveBar = rememberProgressiveBlur()
            Box(
                Modifier.fillMaxSize().background(
                    if (progressiveBar) {
                        Brush.verticalGradient(listOf(Color.Black, Color.Black.copy(alpha = 0.7f), Color.Transparent))
                    } else {
                        SolidColor(Color.Black.copy(alpha = FlatBarScrimAlpha))
                    },
                ),
            )
        }

        // top row: back, the logo turning into "Replay" once the title
        // scrolls away (one element morphing, like Home's and Stats'), share
        val titleGone by remember { derivedStateOf { collapse > 0.5f } }
        val morph by animateFloatAsState(
            targetValue = if (titleGone) 1f else 0f,
            animationSpec = tween(600, easing = FastOutSlowInEasing),
            label = "replayTopBar",
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = ReplayPagePadding, end = ReplayPagePadding, top = topInset + 32.dp),
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable { navigator.popBackStack() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.arrow_back_ios),
                    contentDescription = stringResource(R.string.back),
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
            Box(contentAlignment = Alignment.CenterStart) {
                Icon(
                    painter = painterResource(R.drawable.ic_chordwave_mark),
                    contentDescription = "ChordWave",
                    tint = Color.White,
                    modifier = Modifier
                        .size(width = 31.dp, height = 34.dp)
                        .graphicsLayer {
                            alpha = 1f - morph
                            val k = 1f - morph * 0.25f
                            scaleX = k
                            scaleY = k
                            transformOrigin = TransformOrigin(0f, 0.5f)
                            renderEffect = BlurEffectCache.element(morph * 15f)
                        },
                )
                Text(
                    text = stringResource(R.string.replay),
                    style = replayText(28f, tracking = -1f),
                    modifier = Modifier.graphicsLayer {
                        alpha = morph
                        renderEffect = BlurEffectCache.element((1f - morph) * 15f)
                    },
                )
            }
            Spacer(Modifier.weight(1f))
            if (summary != null && !summary.isEmpty) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.12f))
                        .clickable { state.onShare(null) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.share),
                        contentDescription = stringResource(R.string.share_my_replay),
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

/**
 * The Replay page as list items, for whichever LazyColumn hosts it: the
 * route's own (with the collapsing header above it) or the Library
 * scaffold's (under its own collapsing title). [embedded] swaps the big
 * page title for the tab's smaller one with the share button beside it;
 * [collapse] fades the route's big title out as it scrolls under the bar.
 */
internal fun LazyListScope.replayItems(
    state: ReplayPageState,
    revealSeen: RevealSeenState,
    embedded: Boolean,
    collapse: () -> Float = { 0f },
) {
    val summary = state.summary
    val context = LocalContext.current

    item("replay-heading") {
        Heading(
            state = state,
            embedded = embedded,
            collapse = collapse,
            modifier = Modifier.revealOnAppear("replay-heading", revealSeen, cascade = true),
        )
    }

    when {
        state.replayState.loading && summary == null -> item("replay-loading") {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(64.dp),
                Alignment.Center,
            ) {
                CircularProgressIndicator(color = Color.White.copy(alpha = 0.6f))
            }
        }
        summary == null || summary.isEmpty -> item("replay-empty") {
            EmptyReplay(state.replayState.period)
        }
        else -> {
            item("replay-cards") {
                MochyReplayCardRow(
                    cards = summary.cards(context),
                    holder = state.holder,
                    memberSince = state.replayState.memberSince,
                    onCardClick = state.onCardClick,
                    modifier = Modifier.revealOnAppear("replay-cards", revealSeen, cascade = true),
                )
            }
            item("replay-open") {
                ReplayActionRow(
                    label = stringResource(R.string.play_your_replay),
                    onClick = state.onPlayReplay,
                    modifier = Modifier.revealOnAppear("replay-open", revealSeen, cascade = true),
                )
            }

            chart(
                key = "songs",
                title = stringResource(R.string.top_songs),
                rows = summary.songRows(CHART_LENGTH),
                state = state,
                revealSeen = revealSeen,
                onClick = { index ->
                    summary.songs.getOrNull(index)?.let { state.playSong(it.song) }
                },
                onLongClick = { index ->
                    summary.songs.getOrNull(index)?.let { state.openSongMenu(it.song) }
                },
            )
            chart(
                key = "artists",
                title = stringResource(R.string.top_artists),
                rows = summary.artistRows(CHART_LENGTH),
                state = state,
                revealSeen = revealSeen,
                circular = true,
                onClick = { index ->
                    val artist = summary.artists.getOrNull(index) ?: return@chart
                    state.openArtist(artist.browseId, artist.title)
                },
            )
            chart(
                key = "albums",
                title = stringResource(R.string.top_albums),
                rows = summary.albumRows(CHART_LENGTH),
                state = state,
                revealSeen = revealSeen,
                onClick = { index ->
                    val album = summary.albums.getOrNull(index) ?: return@chart
                    state.openAlbum(album.browseId, album.title, album.subtitle)
                },
            )
            if (summary.genres.isNotEmpty()) {
                chart(
                    key = "genres",
                    title = stringResource(R.string.top_genres),
                    rows = summary.genreRows(CHART_LENGTH),
                    state = state,
                    revealSeen = revealSeen,
                    onClick = {},
                )
            } else if (ArtistFacts.genresAvailable) {
                item("replay-genres-pending") {
                    Text(
                        text = stringResource(R.string.genres_pending),
                        style = replayText(14f, FontWeight.Medium, Color.White.copy(alpha = 0.6f), 0f),
                        modifier = Modifier.padding(horizontal = ReplayPagePadding),
                    )
                }
            }

            item("replay-habits") {
                Habits(
                    summary,
                    modifier = Modifier.revealOnAppear("replay-habits", revealSeen, cascade = true),
                )
            }

            item("replay-bottom_spacer") { Spacer(Modifier.height(50.dp)) }
        }
    }
}

/** One chart: title, ranked rows, trailing gap. */
private fun LazyListScope.chart(
    key: String,
    title: String,
    rows: List<ReplayRow>,
    state: ReplayPageState,
    revealSeen: RevealSeenState,
    circular: Boolean = false,
    onClick: (Int) -> Unit,
    onLongClick: (Int) -> Unit = {},
) {
    if (rows.isEmpty()) return
    item("replay-$key-title") {
        SectionTitle(
            title,
            modifier = Modifier.revealOnAppear("replay-$key-title", revealSeen, cascade = true),
        )
    }
    items(rows, key = { "replay-$key-${it.key}" }) { row ->
        val active = key == "songs" && row.key == state.nowPlayingId()
        ReplayChartRow(
            row = row,
            circular = circular,
            isActive = active,
            isPlaying = active && state.isPlayingNow(),
            modifier = Modifier.revealOnAppear(
                key = "replay-$key-${row.key}",
                seenState = revealSeen,
                cascade = true,
            ),
            onClick = { onClick(row.rank - 1) },
            onLongClick = { onLongClick(row.rank - 1) },
        )
    }
    item("replay-$key-gap") { Spacer(Modifier.height(20.dp)) }
}

// ── Heading ─────────────────────────────────────────────────────────────────

@Composable
private fun Heading(
    state: ReplayPageState,
    embedded: Boolean,
    collapse: () -> Float,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val summary = state.summary
    val label = summary?.localizedLabel(context) ?: state.replayState.period.localizedTab(context)
    Column(
        modifier.padding(horizontal = ReplayPagePadding),
    ) {
        if (embedded) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.replay),
                    style = replayText(28f, tracking = -1f),
                    modifier = Modifier.weight(1f),
                )
                if (summary != null && !summary.isEmpty) {
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.12f))
                            .clickable { state.onShare(null) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.share),
                            contentDescription = stringResource(R.string.share_my_replay),
                            tint = Color.White,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        } else {
            Text(
                text = stringResource(R.string.replay),
                style = replayText(44f, tracking = -1.6f),
                modifier = Modifier.graphicsLayer { alpha = 1f - collapse() },
            )
        }
        Text(
            text = label,
            style = replayText(15f, FontWeight.Medium, Color.White.copy(alpha = 0.6f), 0f),
        )
        Spacer(Modifier.height(14.dp))
        // Mochy's own chip row, four equal stretches — weeks and months,
        // not a date range, because that is the granularity the listening
        // is actually kept at (see ListeningStats).
        ChipsRow(
            chips = ReplayPeriod.entries.map { it to it.localizedTab(context) },
            currentValue = state.replayState.period,
            onValueUpdate = state.setPeriod,
        )
        Spacer(Modifier.height(18.dp))
    }
}

// ── The cards ───────────────────────────────────────────────────────────────

/**
 * The wallet of headline cards, Modern style: the same four facts as
 * Classic's credit cards, drawn as flat Mochy cards (the Stats page's card
 * colour and radius) with the record's own cover on them. Tapping one
 * opens the story at the chart it summarises.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MochyReplayCardRow(
    cards: List<ReplayHeroCard>,
    holder: String,
    memberSince: String?,
    onCardClick: (ReplayStoryPage) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rowState = rememberLazyListState()
    LazyRow(
        modifier = modifier,
        state = rowState,
        contentPadding = PaddingValues(horizontal = ReplayPagePadding),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        flingBehavior = rememberSnapFlingBehavior(lazyListState = rowState),
    ) {
        items(cards, key = { it.label }) { card ->
            MochyReplayHeroCard(
                card = card,
                holder = holder,
                memberSince = memberSince,
                onClick = { onCardClick(card.page) },
                modifier = Modifier.width(300.dp),
            )
        }
    }
}

@Composable
private fun MochyReplayHeroCard(
    card: ReplayHeroCard,
    holder: String,
    memberSince: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(196.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(ReplayCard)
            .clickable(onClick = onClick),
    ) {
        // The cover itself, small and to the right, so the card is about a
        // record you can recognise rather than only a colour it lent.
        if (card.artworkUrl != null) {
            AsyncImage(
                model = card.artworkUrl.artworkAt(CARD_ART_PX),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .size(64.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(ReplayChipBg),
            )
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 16.dp),
        ) {
            Text(
                text = card.label.uppercase(),
                style = replayText(11f, FontWeight.Bold, Color.White.copy(alpha = 0.72f), 1.8f),
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = card.value,
                style = replayText(27f, tracking = -0.8f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (card.detail != null) {
                Text(
                    text = card.detail,
                    style = replayText(14f, FontWeight.Medium, Color.White.copy(alpha = 0.65f), 0f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = holder.ifBlank { stringResource(R.string.default_replay_holder) },
                    style = replayText(11f, FontWeight.Medium, Color.White.copy(alpha = 0.55f), 0f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (memberSince != null) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.member_since_value, memberSince),
                        style = replayText(11f, FontWeight.Medium, Color.White.copy(alpha = 0.55f), 0f),
                    )
                }
            }
        }
    }
}

/**
 * Lazy-list index of a chart title after the heading, card row and play row.
 * Each preceding chart contributes its title, visible rows and trailing gap.
 */
private fun replayChartIndex(summary: ReplaySummary, page: ReplayStoryPage): Int {
    val songs = summary.songRows(CHART_LENGTH)
    val artists = summary.artistRows(CHART_LENGTH)
    fun span(rows: List<ReplayRow>): Int = if (rows.isEmpty()) 0 else rows.size + 2

    return when (page) {
        ReplayStoryPage.SONGS -> 3
        ReplayStoryPage.ARTISTS -> 3 + span(songs)
        ReplayStoryPage.ALBUMS -> 3 + span(songs) + span(artists)
        else -> 0
    }
}

/** The "Play your Replay" row: opens the stories at the beginning. */
@Composable
private fun ReplayActionRow(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .padding(horizontal = ReplayPagePadding, vertical = 18.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(ReplayChipBg)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.PlayArrow,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            style = replayText(16f, FontWeight.SemiBold, tracking = -0.3f),
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.5f),
            modifier = Modifier.size(16.dp),
        )
    }
}

// ── Charts ──────────────────────────────────────────────────────────────────

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = replayText(20f, tracking = -0.6f),
        modifier = modifier.padding(
            start = ReplayPagePadding,
            end = ReplayPagePadding,
            top = 8.dp,
            bottom = 10.dp,
        ),
    )
}

/**
 * One ranked row.
 *
 * Deliberately not [com.music.bitchord.ui.mochy.component.detail.PlaylistTrackRow]:
 * that row carries a 36dp leading inset, which under a 28dp rank badge would
 * push every cover 64dp in. This one follows the same visual language —
 * [ItemThumbnail] cover with the playing indicator, white title, dim subtitle,
 * plays trailing — at the chart's own gutter. A row with no artwork (a genre)
 * gets the lettered [InitialTile], the same stand-in Classic uses.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ReplayChartRow(
    row: ReplayRow,
    circular: Boolean,
    isActive: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape: Shape = if (circular) CircleShape else RoundedCornerShape(9.dp)
    val context = LocalContext.current
    Row(
        modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = ReplayPagePadding, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RankBadge(row.rank, AccentMint)
        if (row.artworkUrl != null) {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(shape)
                    .background(BoneColor),
            ) {
                ItemThumbnail(
                    thumbnailUrl = row.artworkUrl,
                    isActive = isActive,
                    isPlaying = isPlaying,
                    shape = shape,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        } else {
            InitialTile(row.title, 48.dp, shape)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = row.title,
                style = replayText(16f, FontWeight.SemiBold, Color.White.copy(alpha = if (isActive) 1f else 0.94f), -0.3f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOfNotNull(row.subtitle, formatListening(context, row.ms))
                    .joinToString(" · "),
                style = replayText(14f, FontWeight.Medium, Color.White.copy(alpha = 0.55f), 0f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (row.plays > 0) {
            Text(
                text = row.plays.toString(),
                style = replayText(12f, FontWeight.Medium, Color.White.copy(alpha = 0.45f), 0f),
            )
        }
    }
}

// ── Tail ────────────────────────────────────────────────────────────────────

@Composable
private fun Habits(summary: ReplaySummary, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Column(modifier.padding(horizontal = ReplayPagePadding)) {
        Text(
            text = stringResource(R.string.listening_shape),
            style = replayText(20f, tracking = -0.6f),
            modifier = Modifier.padding(top = 8.dp, bottom = 10.dp),
        )
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile(stringResource(R.string.songs), summary.distinctSongs.toString(), Modifier.weight(1f))
            StatTile(stringResource(R.string.artists), summary.distinctArtists.toString(), Modifier.weight(1f))
            StatTile(stringResource(R.string.albums), summary.distinctAlbums.toString(), Modifier.weight(1f))
        }
        summary.peakHour?.let {
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.listen_most_around, formatHour(context, it)),
                style = replayText(14f, FontWeight.Medium, Color.White.copy(alpha = 0.6f), 0f),
            )
        }
        summary.busiestDay?.let {
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(
                    R.string.biggest_day_note,
                    formatDay(context, it),
                    formatListening(context, summary.busiestDayMs),
                ),
                style = replayText(14f, FontWeight.Medium, Color.White.copy(alpha = 0.6f), 0f),
            )
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(ReplayTile)
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = value,
            style = replayText(22f, tracking = -0.8f),
        )
        Text(
            text = label,
            style = replayText(11f, FontWeight.Medium, ReplayInkMuted, 0.5f),
        )
    }
}

@Composable
private fun EmptyReplay(period: ReplayPeriod) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 36.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painter = painterResource(R.drawable.history),
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.4f),
            modifier = Modifier.size(44.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.not_enough_listening),
            style = replayText(20f, tracking = -0.5f),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = when (period) {
                ReplayPeriod.THIS_WEEK -> stringResource(R.string.not_enough_this_week)
                ReplayPeriod.THIS_MONTH -> stringResource(R.string.not_enough_this_month)
                else -> stringResource(R.string.replay_empty_description)
            },
            style = replayText(14f, FontWeight.Medium, Color.White.copy(alpha = 0.6f), 0f),
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * How far down each chart the page goes.
 *
 * Five. Ten was Classic's first guess and it made the page a scroll of forty
 * rows whose second half nobody has an opinion about — a top five is a result,
 * and a top ten is a list.
 */
private const val CHART_LENGTH = 5
