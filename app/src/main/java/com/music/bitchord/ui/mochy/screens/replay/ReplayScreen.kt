// Modern (Mochy-style) restyle of ChordWave's Replay page.
//
// The engine is untouched and reused as-is: ReplayState / rememberReplayState,
// ReplaySummary and every helper in ui/replay/ReplayModel.kt. This file only
// re-draws the page in the Modern visual language — dark-only (MochyTheme),
// Mochy list components (ChipsRow, ItemThumbnail, SongMenu, TopToaster),
// PopupSheetState sheets via LocalMenuState, navigation through
// MochyNavigator. Classic ui/replay/ReplayScreen.kt is kept and still hosts
// the Classic mode; nothing here edits it.
//
// Route: `replay` (host wiring in NEEDS.md §1).
package com.music.bitchord.ui.mochy.screens.replay

import android.net.Uri
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
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
import com.music.bitchord.ui.player.MeshGradientBackground
import com.music.bitchord.ui.player.rememberArtworkColors
import com.music.bitchord.ui.mochy.LocalPlayerAwareWindowInsets
import com.music.bitchord.ui.mochy.component.BoneColor
import com.music.bitchord.ui.mochy.component.ChipsRow
import com.music.bitchord.ui.mochy.component.ItemThumbnail
import com.music.bitchord.ui.mochy.component.LocalMenuState
import com.music.bitchord.ui.mochy.component.TopToaster
import com.music.bitchord.ui.mochy.component.rememberRevealSeenState
import com.music.bitchord.ui.mochy.component.revealOnAppear
import com.music.bitchord.ui.mochy.extensions.toMediaItem
import com.music.bitchord.ui.mochy.models.toMochySong
import com.music.bitchord.ui.mochy.navigation.MochyNavigator
import com.music.bitchord.ui.mochy.playback.LocalPlayerConnection
import com.music.bitchord.ui.mochy.playback.queues.ListQueue
import com.music.bitchord.ui.mochy.player.menu.SongMenu
import com.music.bitchord.ui.replay.InitialTile
import com.music.bitchord.ui.replay.RankBadge
import com.music.bitchord.ui.replay.ReplayHeroCard
import com.music.bitchord.ui.replay.ReplayRow
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
import kotlinx.coroutines.launch
import timber.log.Timber

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
 *
 * Stories are a full-screen overlay owned by this screen (not a route), the
 * way Classic hosts them as activity state: back closes the story first,
 * then the page.
 */
@Composable
fun ReplayScreen(
    navigator: MochyNavigator,
    landingPage: ReplayStoryPage = ReplayStoryPage.INTRO,
    mainViewModel: MainViewModel = viewModel(),
) {
    val context = LocalContext.current
    val (replayState, setPeriod) = rememberReplayState(active = true, pageOpen = true)
    val account by mainViewModel.account.collectAsState()
    val holder = account?.name.orEmpty()
    val playerConnection = LocalPlayerConnection.current ?: return
    val menuState = LocalMenuState.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsState()
    val listState = rememberLazyListState()
    val revealSeen = rememberRevealSeenState()

    var storyPage by remember { mutableStateOf<ReplayStoryPage?>(null) }

    val summary = replayState.summary
    val leadArtwork = summary?.songs?.firstOrNull()?.song?.thumbnailUrl
    val palette = rememberArtworkColors(leadArtwork)

    // Library cards open this page at the chart they summarise. Once only
    // per opening — back from an artist/album opened off this page must not
    // throw away the scroll position. Same math as Classic (item order is
    // identical: heading, cards, play row, then the charts).
    var landed by rememberSaveable(landingPage, replayState.period) { mutableStateOf(false) }
    LaunchedEffect(landingPage, replayState.period, summary == null) {
        if (landed) return@LaunchedEffect
        val target = when {
            landingPage == ReplayStoryPage.INTRO || landingPage == ReplayStoryPage.MINUTES -> 0
            summary == null || summary.isEmpty -> 0
            else -> replayChartIndex(summary, landingPage)
        }
        listState.scrollToItem(target)
        if (summary != null) landed = true
    }

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
     * path ([MochyPlayerConnection.playQueue] with a [ListQueue] of
     * [toMediaItem] items). A radio that comes back empty still plays the
     * song on its own rather than failing the tap; that fallback is logged,
     * not silent.
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
    // ReplayStoriesHost): this screen only decides when they open, and
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

    Box(Modifier.fillMaxSize()) {
        MeshGradientBackground(palette = palette, trackKey = leadArtwork, animated = false)
        // Most of the mesh goes back under ink: a page of ranked lists
        // needs far more separation than the player it was built for.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(alpha = 0.30f),
                            Color.Black.copy(alpha = 0.72f),
                            Color.Black.copy(alpha = 0.88f),
                        ),
                    ),
                ),
        )

        val topSongs = stringResource(R.string.top_songs)
        val topArtists = stringResource(R.string.top_artists)
        val topAlbums = stringResource(R.string.top_albums)
        val topGenres = stringResource(R.string.top_genres)

        // One chart: title, ranked rows, trailing gap. Local so it captures
        // the player state and the reveal state without a dozen parameters.
        fun LazyListScope.chart(
            key: String,
            title: String,
            rows: List<ReplayRow>,
            circular: Boolean = false,
            onClick: (Int) -> Unit,
            onLongClick: (Int) -> Unit = {},
        ) {
            if (rows.isEmpty()) return
            item("$key-title") { SectionTitle(title) }
            items(rows, key = { "$key-${it.key}" }) { row ->
                val active = key == "songs" && row.key == mediaMetadata?.id
                ReplayChartRow(
                    row = row,
                    circular = circular,
                    isActive = active,
                    isPlaying = active && isPlaying,
                    modifier = Modifier.revealOnAppear(
                        key = "replay-$key-${row.key}",
                        seenState = revealSeen,
                    ),
                    onClick = { onClick(row.rank - 1) },
                    onLongClick = { onLongClick(row.rank - 1) },
                )
            }
            item("$key-gap") { Spacer(Modifier.height(20.dp)) }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = LocalPlayerAwareWindowInsets.current
                .only(WindowInsetsSides.Bottom)
                .union(WindowInsets.ime)
                .asPaddingValues(),
        ) {
            item("heading") {
                Heading(
                    statePeriod = replayState.period,
                    label = summary?.localizedLabel(context)
                        ?: replayState.period.localizedTab(context),
                    onPeriodChange = setPeriod,
                    showShare = summary != null && !summary.isEmpty,
                    onBack = { navigator.popBackStack() },
                    onShare = { openShare(null) },
                )
            }

            when {
                replayState.loading && summary == null -> item("loading") {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(64.dp),
                        Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = Color.White.copy(alpha = 0.6f))
                    }
                }
                summary == null || summary.isEmpty -> item("empty") {
                    EmptyReplay(replayState.period)
                }
                else -> {
                    item("cards") {
                        MochyReplayCardRow(
                            cards = summary.cards(context),
                            holder = holder,
                            memberSince = replayState.memberSince,
                            onCardClick = { storyPage = it },
                        )
                    }
                    item("open") {
                        ReplayActionRow(
                            label = stringResource(R.string.play_your_replay),
                            onClick = { storyPage = ReplayStoryPage.INTRO },
                        )
                    }

                    chart(
                        key = "songs",
                        title = topSongs,
                        rows = summary.songRows(CHART_LENGTH),
                        onClick = { index ->
                            summary.songs.getOrNull(index)?.let { playSong(it.song) }
                        },
                        onLongClick = { index ->
                            summary.songs.getOrNull(index)?.let { openSongMenu(it.song) }
                        },
                    )
                    chart(
                        key = "artists",
                        title = topArtists,
                        rows = summary.artistRows(CHART_LENGTH),
                        circular = true,
                        onClick = { index ->
                            val artist = summary.artists.getOrNull(index) ?: return@chart
                            openArtist(artist.browseId, artist.title)
                        },
                    )
                    chart(
                        key = "albums",
                        title = topAlbums,
                        rows = summary.albumRows(CHART_LENGTH),
                        onClick = { index ->
                            val album = summary.albums.getOrNull(index) ?: return@chart
                            openAlbum(album.browseId, album.title, album.subtitle)
                        },
                    )
                    if (summary.genres.isNotEmpty()) {
                        chart(
                            key = "genres",
                            title = topGenres,
                            rows = summary.genreRows(CHART_LENGTH),
                            onClick = {},
                        )
                    } else if (ArtistFacts.genresAvailable) {
                        item("genres-pending") {
                            Text(
                                text = stringResource(R.string.genres_pending),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.6f),
                                modifier = Modifier.padding(horizontal = 20.dp),
                            )
                        }
                    }

                    item("habits") { Habits(summary) }

                    item("bottom_spacer") { Spacer(Modifier.height(50.dp)) }
                }
            }
        }

    }
}

// ── Heading ─────────────────────────────────────────────────────────────────

@Composable
private fun Heading(
    statePeriod: ReplayPeriod,
    label: String,
    onPeriodChange: (ReplayPeriod) -> Unit,
    showShare: Boolean,
    onBack: () -> Unit,
    onShare: () -> Unit,
) {
    val context = LocalContext.current
    Column(
        Modifier
            .statusBarsPadding()
            .padding(horizontal = 20.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.arrow_back_ios),
                    contentDescription = stringResource(R.string.back),
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            if (showShare) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.12f))
                        .clickable(onClick = onShare),
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
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.replay),
            style = MaterialTheme.typography.displayLarge,
            fontWeight = FontWeight.W800,
            color = Color.White,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White.copy(alpha = 0.6f),
        )
        Spacer(Modifier.height(14.dp))
        // Mochy's own chip row, four equal stretches — weeks and months,
        // not a date range, because that is the granularity the listening
        // is actually kept at (see ListeningStats).
        ChipsRow(
            chips = ReplayPeriod.entries.map { it to it.localizedTab(context) },
            currentValue = statePeriod,
            onValueUpdate = onPeriodChange,
        )
        Spacer(Modifier.height(18.dp))
    }
}

// ── The cards ───────────────────────────────────────────────────────────────

/**
 * The wallet of headline cards, Modern style: the same four facts as
 * Classic's credit cards, drawn as Mochy glass cards washed in each card's
 * own artwork mesh — no embossed mono type, no card anatomy. Tapping one
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
        contentPadding = PaddingValues(horizontal = 20.dp),
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
    val palette = rememberArtworkColors(card.artworkUrl)
    Box(
        modifier = modifier
            .height(196.dp)
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick),
    ) {
        MeshGradientBackground(
            palette = palette,
            trackKey = card.artworkUrl ?: card.label,
            blurRadius = 34.dp,
            animated = false,
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to Color.Black.copy(alpha = 0.18f),
                        0.55f to Color.Black.copy(alpha = 0.30f),
                        1.0f to Color.Black.copy(alpha = 0.55f),
                    ),
                ),
        )
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
                    .clip(RoundedCornerShape(12.dp)),
            )
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 16.dp),
        ) {
            Text(
                text = card.label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.W700,
                letterSpacing = 1.8.sp,
                color = Color.White.copy(alpha = 0.72f),
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = card.value,
                style = MaterialTheme.typography.headlineMedium,
                fontSize = 27.sp,
                lineHeight = 30.sp,
                fontWeight = FontWeight.W800,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (card.detail != null) {
                Text(
                    text = card.detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.65f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = holder.ifBlank { stringResource(R.string.default_replay_holder) },
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.55f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (memberSince != null) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.member_since_value, memberSince),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.55f),
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
private fun ReplayActionRow(label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .padding(horizontal = 20.dp, vertical = 18.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.12f))
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
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
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
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.W800,
        color = Color.White,
        modifier = Modifier.padding(
            start = 20.dp,
            end = 20.dp,
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
            .padding(horizontal = 20.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RankBadge(row.rank, MaterialTheme.colorScheme.primary)
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
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.W600,
                color = Color.White.copy(alpha = if (isActive) 1f else 0.94f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOfNotNull(row.subtitle, formatListening(context, row.ms))
                    .joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.55f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (row.plays > 0) {
            Text(
                text = row.plays.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.45f),
            )
        }
    }
}

// ── Tail ────────────────────────────────────────────────────────────────────

@Composable
private fun Habits(summary: ReplaySummary) {
    val context = LocalContext.current
    Column(Modifier.padding(horizontal = 20.dp)) {
        Text(
            text = stringResource(R.string.listening_shape),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.W800,
            color = Color.White,
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
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.6f),
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
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.6f),
            )
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.09f))
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.W800,
            color = Color.White,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.55f),
            letterSpacing = 1.sp,
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
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = when (period) {
                ReplayPeriod.THIS_WEEK -> stringResource(R.string.not_enough_this_week)
                ReplayPeriod.THIS_MONTH -> stringResource(R.string.not_enough_this_month)
                else -> stringResource(R.string.replay_empty_description)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.6f),
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
