// ProfileScreen.kt
//
// The profile page. A dark header holds the profile ID card over a dot grid, with a giant
// CHORDWAVE column sliding up the right edge. The card tilts with the finger and the phone, and
// tapping it opens the profile page out of the card (ui/mochy/screens/profile). Below:
// listening time (into Stats), version, contributors, settings.
//
// Ported from Mochy Music (GPL-3.0): ui/screens/settings/ProfileScreen.kt —
// the collapsed page is Mochy's own layout, numbers and motion. NOT ported:
// the pull-down scratch-off monthly recap (Mochy reads per-play events for
// it; ChordWave's listening buckets are aggregates — see the batch log) and
// the landscape two-pane layout (the Modern host is phone-layout only).
// The Discord card wears ChordWave's robot character (batch 44) and opens the
// Discord presence settings — Mochy's card opens Mochy's own server, which
// ChordWave does not have. The giant
// header word is the app name, so it reads CHORDWAVE here.

package com.music.bitchord.ui.mochy.screens.profile

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.music.bitchord.BuildConfig
import com.music.bitchord.R
import com.music.bitchord.data.stats.ListeningStats
import com.music.bitchord.ui.MainViewModel
import com.music.bitchord.ui.mochy.constants.ShowDiscordPresence
import com.music.bitchord.ui.mochy.LocalAppContentObscured
import com.music.bitchord.ui.mochy.LocalPlayerAwareWindowInsets
import com.music.bitchord.ui.mochy.component.BlurDirection
import com.music.bitchord.ui.mochy.component.rememberResourcePainter
import com.music.bitchord.ui.mochy.component.FlatBarScrimAlpha
import com.music.bitchord.ui.mochy.component.GlassState
import com.music.bitchord.ui.mochy.component.LocalMenuState
import com.music.bitchord.ui.mochy.component.OdometerNumber
import com.music.bitchord.ui.mochy.component.ProgressiveGlassBackground
import com.music.bitchord.ui.mochy.component.RemoteFocus
import com.music.bitchord.ui.mochy.component.glassRoot
import com.music.bitchord.ui.mochy.component.rememberDeviceTilt
import com.music.bitchord.ui.mochy.component.rememberProgressiveBlur
import com.music.bitchord.ui.mochy.constants.ProfileCardNumberKey
import com.music.bitchord.ui.mochy.constants.ProfileJoinedEpochDayKey
import com.music.bitchord.ui.mochy.constants.ProfilePicUriKey
import com.music.bitchord.ui.mochy.constants.UsernameKey
import com.music.bitchord.ui.mochy.navigation.MochyNavigator
import com.music.bitchord.ui.mochy.screens.setup.onboarding.IdCardAspect
import com.music.bitchord.ui.mochy.screens.setup.onboarding.ProfileIdCard
import com.music.bitchord.ui.mochy.screens.update.UpdateHeadline
import com.music.bitchord.ui.mochy.screens.update.UpdateState
import com.music.bitchord.ui.mochy.screens.update.rememberUpdateState
import com.music.bitchord.ui.mochy.screens.update.showAboutSheet
import com.music.bitchord.ui.mochy.screens.update.showUpdateSheet
import com.music.bitchord.ui.mochy.theme.InterFontFamily
import com.music.bitchord.ui.mochy.utils.rememberPreference
import com.music.bitchord.ui.mochy.utils.stableSystemBars
import com.music.bitchord.ui.mochy.viewmodels.UserProfileViewModel
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

private object ProfileGreeting {
    private fun pick() = listOf(
        R.string.profile_greeting_hello_again,
        R.string.profile_greeting_hey,
        R.string.profile_greeting_welcome_back,
        R.string.profile_greeting_good_to_see_you,
    ).random()

    // a string id, so the greeting follows the app language
    private val greetingState = mutableIntStateOf(R.string.profile_greeting_hello_again)
    private var wasBackgrounded = false

    val currentRes: Int get() = greetingState.intValue

    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_STOP -> wasBackgrounded = true
                    Lifecycle.Event.ON_START -> if (wasBackgrounded) {
                        greetingState.intValue = pick()
                        wasBackgrounded = false
                    }
                    else -> Unit
                }
            },
        )
    }
}

private val PageCard = Color(0xFF0F0F0F)

@Composable
fun ProfileScreen(
    navigator: MochyNavigator,
    mainViewModel: MainViewModel = viewModel(),
) {
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val menuState = LocalMenuState.current
    val updateState by rememberUpdateState()
    val obscured = LocalAppContentObscured.current
    val windowHeight = LocalWindowInfo.current.containerSize.height.toFloat()
    val statusBarTop = WindowInsets.stableSystemBars.asPaddingValues().calculateTopPadding()
    val topPx = with(density) { statusBarTop.toPx() }
    val cornerPx = with(density) { 26.dp.toPx() }

    val profileViewModel: UserProfileViewModel = hiltViewModel()
    val account by mainViewModel.account.collectAsState()
    val listens by profileViewModel.listens.collectAsState()
    val totalListeningMs = listens?.firstOrNull()?.playTimeMs ?: 0L

    // the buckets only grow while music plays; re-read each time the page opens
    LaunchedEffect(Unit) { profileViewModel.refresh() }

    val (username) = rememberPreference(UsernameKey, "")
    val (profilePicUri) = rememberPreference(ProfilePicUriKey, "")
    val (cardNumberPref, setCardNumber) = rememberPreference(ProfileCardNumberKey, "")
    val (joinedEpochDay, setJoinedEpochDay) = rememberPreference(ProfileJoinedEpochDayKey, -1L)

    // the profile page that opens out of the card (screens/profile)
    val profile = rememberProfileMorphState()
    val cardPress = remember { MutableInteractionSource() }
    val cardPressed by cardPress.collectIsPressedAsState()

    // the onboarding writes the card's number and join date; an install from before it gets them once, here
    LaunchedEffect(cardNumberPref, joinedEpochDay) {
        if (cardNumberPref.isBlank()) setCardNumber((10_000_000_000L + Random.nextLong(89_999_999_999L)).toString())
        if (joinedEpochDay < 0L) setJoinedEpochDay((ListeningStats.firstPlayDate() ?: LocalDate.now()).toEpochDay())
    }
    val cardNumber = cardNumberPref.ifBlank { "00000000000" }
    val joinedDate = if (joinedEpochDay >= 0L) LocalDate.ofEpochDay(joinedEpochDay) else LocalDate.now()
    val joinedText = stringResource(
        R.string.profile_joined_at,
        joinedDate.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)),
    )
    val photo: Any? = profilePicUri.takeIf { it.isNotBlank() }?.let { if (it.contains("://")) it else "file://$it" }
    // signed in, the card wears the Google account (name + avatar); signed
    // out, it falls back to the local profile. The card number and join date
    // stay local either way — they belong to this device, not the account.
    val displayName = account?.name?.takeIf { it.isNotBlank() } ?: username
    val displayPhoto: Any? = account?.thumbnailUrl?.takeIf { it.isNotBlank() } ?: photo

    // ---- header state -------------------------------------------------------------------------
    val tiltX = remember { Animatable(0f) }
    val tiltY = remember { Animatable(0f) }
    var tiltTarget by remember { mutableStateOf(Offset.Zero) }
    val deviceTilt = rememberDeviceTilt()
    var rootOnScreen by remember { mutableStateOf(Offset.Zero) }

    // the player covering the page is a hold, not a stop, so opening it doesn't recompose the whole page
    val time = rememberProfileTime(running = true, held = { profile.holdsPage || obscured.value })
    val measurer = rememberTextMeasurer()
    val giant = remember {
        GiantTextState("CHORDWAVE", filledRows = 0..0).apply { color = { HeaderText } }
    }

    // the finger's tilt, eased so the card feels weighty rather than glued on
    LaunchedEffect(Unit) {
        snapshotFlow { tiltTarget }.collect { t ->
            val settle = t == Offset.Zero
            val spec = if (settle) spring<Float>(dampingRatio = 0.42f, stiffness = Spring.StiffnessLow) else spring(stiffness = Spring.StiffnessMediumLow)
            launch { tiltX.animateTo(t.y, spec) }
            launch { tiltY.animateTo(t.x, spec) }
        }
    }

    val scrollState = rememberScrollState()
    val glassState = remember { GlassState() }
    val bottomBarInset = LocalPlayerAwareWindowInsets.current.asPaddingValues().calculateBottomPadding()

    // the profile page, and over it the profile page when the card has been tapped
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .onGloballyPositioned { rootOnScreen = it.positionOnScreen() }
                // zooms towards you and fades as the profile page opens out of the card
                .zoomsAwayFor(profile),
        ) {
            val inset = with(density) { ProfilePageInset.toPx() }
            // upright on a tablet the id card stops at 380dp instead of stretching across the screen
            val maxCardW = with(density) { 380.dp.toPx() }
            val cardW = minOf(constraints.maxWidth - 2 * inset, maxCardW)
            val cardH = cardW / IdCardAspect
            val collapsedH = collapsedHeaderPx(topPx, cardH, density.density)
            val cardTopCollapsed = topPx + with(density) { CardTopDp.dp.toPx() }
            val collapseProgress by remember { derivedStateOf { (scrollState.value / with(density) { 120.dp.toPx() }).coerceIn(0f, 1f) } }

            // ---- the scrolling page ---------------------------------------------------------------
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .glassRoot(glassState, isActive = { collapseProgress > 0f }),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState),
                ) {
                    // header: the dark pane the card sits on
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .layout { measurable, constraints ->
                                val h = collapsedH.roundToInt()
                                val p = measurable.measure(Constraints.fixed(constraints.maxWidth, h))
                                layout(constraints.maxWidth, h) { p.place(0, 0) }
                            }
                            .pointerInput(Unit) {
                                // tilt follows the finger; nothing is consumed, so scrolling still works
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                    var acc = Offset.Zero
                                    while (true) {
                                        val event = awaitPointerEvent(PointerEventPass.Initial)
                                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                        if (!change.pressed) break
                                        acc += change.positionChange()
                                        tiltTarget = Offset(
                                            (acc.x * 0.09f).coerceIn(-26f, 26f),
                                            (-acc.y * 0.07f).coerceIn(-18f, 18f),
                                        )
                                    }
                                    tiltTarget = Offset.Zero
                                }
                            }
                            .clipBottomRounded { cornerPx }
                            .drawBehind { drawRect(HeaderGrey) }
                            .giantText(giant, time, measurer, centerY = { windowHeight / 2f - rootOnScreen.y })
                            .profileDots(color = { HeaderDots }),
                    ) {
                        // greeting (in the header; its scrolled-away twin lives in the top bar)
                        Text(
                            text = stringResource(R.string.profile_greeting_format, stringResource(ProfileGreeting.currentRes), displayName.ifBlank { stringResource(R.string.profile_greeting_fallback) }),
                            style = PageTitle,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .padding(start = ProfilePageInset, end = ProfilePageInset, top = statusBarTop + 80.dp)
                                .graphicsLayer {
                                    alpha = (1f - collapseProgress * 1.6f).coerceIn(0f, 1f)
                                },
                        )

                        // the card: tilts with the finger, drifts a little on its own
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .widthIn(max = 380.dp + ProfilePageInset * 2)
                                .fillMaxWidth()
                                .padding(horizontal = ProfilePageInset)
                                .aspectRatio(IdCardAspect)
                                .offset { androidx.compose.ui.unit.IntOffset(0, cardTopCollapsed.roundToInt()) }
                                .onPlaced { profile.cardSlot = it }
                                .graphicsLayer {
                                    // under the profile page the card holds still (no frames for nobody),
                                    // and it eases back into leaning as the page closes over it again
                                    val follow = profile.cardFollow.value
                                    var leanX = profile.heldTiltX
                                    var leanY = profile.heldTiltY
                                    if (follow > 0f) {
                                        val t = time.sway
                                        val liveY = tiltY.value + deviceTilt.rotationY + sin(t * 0.9f) * 3f
                                        val liveX = tiltX.value + deviceTilt.rotationX + cos(t * 0.7f) * 2f
                                        leanX = lerpF(leanX, liveX, follow)
                                        leanY = lerpF(leanY, liveY, follow)
                                    }
                                    rotationX = leanX
                                    rotationY = leanY
                                    // the profile page's flying copies lean the same way when they take off or land
                                    profile.cardTiltX = leanX
                                    profile.cardTiltY = leanY
                                    cameraDistance = 14f * this.density
                                }
                                .clickable(interactionSource = cardPress, indication = RemoteFocus) {
                                    haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                                    profile.open()
                                },
                        ) {
                            ProfileIdCard(
                                username = displayName,
                                photo = displayPhoto,
                                cardNumber = cardNumber,
                                joinedText = joinedText,
                                showIdCardOf = true,
                                anchors = profile.card,
                                partsAlpha = { if (profile.coversCard) 0f else 1f },
                                marqueeRunning = { !profile.holdsPage },
                                showEditHint = account == null,
                                pressed = { cardPressed },
                            )
                        }
                    }

                    // ---- everything under the header ----
                    Column(
                        modifier = Modifier.padding(horizontal = 20.dp),
                    ) {
                        Spacer(Modifier.height(24.dp))
                        ListeningTimeCard(totalListeningMs, onViewStats = { navigator.navigate("stats") })
                        Spacer(Modifier.height(16.dp))
                        val hasUpdate = updateState is UpdateState.Available
                        VersionCard(
                            updateHeadline = if (hasUpdate) UpdateHeadline else null,
                            onCardClick = { menuState.showUpdateSheet(updateState) },
                            onPillClick = {
                                if (hasUpdate) menuState.showUpdateSheet(updateState) else menuState.showAboutSheet()
                            },
                        )
                        Spacer(Modifier.height(16.dp))
                        DeveloperCard(onClick = { menuState.showAboutSheet() })
                        Spacer(Modifier.height(16.dp))
                        if (ShowDiscordPresence) DiscordCard(onClick = { navigator.navigate("integrations_settings/discord") })
                        Spacer(Modifier.height(28.dp))
                        Text(stringResource(R.string.profile_settings_title), style = PageTitle.copy(fontSize = 15.sp), modifier = Modifier.padding(start = 2.dp, bottom = 10.dp))
                        OpenSettingsRow(onClick = { navigator.navigate("mochy_settings") })
                        Spacer(Modifier.height(bottomBarInset + 24.dp))
                    }
                }
            }

            // ---- the glass top bar, a sibling of the glassRoot box (never inside it) ------------------
            if (collapseProgress > 0.005f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(statusBarTop + 84.dp)
                        .align(Alignment.TopCenter),
                ) {
                    ProgressiveGlassBackground(
                        state = glassState,
                        maxBlurRadius = { 55f * collapseProgress },
                        foundationColor = Color.Black.copy(alpha = 0.55f * collapseProgress),
                        direction = BlurDirection.BottomToTop,
                        steps = 3,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = (collapseProgress * 1.5f).coerceIn(0f, 1f) },
                    )
                    val progressiveBar = rememberProgressiveBlur()
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                if (progressiveBar) {
                                    Brush.verticalGradient(
                                        listOf(Color.Black, Color.Black.copy(alpha = 0.85f * collapseProgress), Color.Transparent),
                                    )
                                } else {
                                    androidx.compose.ui.graphics.SolidColor(Color.Black.copy(alpha = FlatBarScrimAlpha))
                                },
                            ),
                    )
                }
            }

            // ---- logo, and the greeting sliding into the bar beside it ----
            Icon(
                painter = painterResource(R.drawable.ic_chordwave_mark),
                contentDescription = "ChordWave",
                tint = Color.White,
                modifier = Modifier
                    .padding(start = ProfilePageInset, top = statusBarTop + TopBarLogoTop)
                    .size(width = 31.dp, height = 34.dp)
                    .graphicsLayer {
                        val k = 1f - collapseProgress * 0.18f
                        scaleX = k
                        scaleY = k
                        transformOrigin = TransformOrigin(0f, 0.5f)
                    },
            )
            if (collapseProgress > 0.01f) {
                Text(
                    text = stringResource(R.string.profile_greeting_format, stringResource(ProfileGreeting.currentRes), displayName.ifBlank { stringResource(R.string.profile_greeting_fallback) }),
                    style = PageTitle.copy(fontSize = 18.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(start = ProfilePageInset + 40.dp, end = ProfilePageInset, top = statusBarTop + 37.dp)
                        .graphicsLayer {
                            val p = ((collapseProgress - 0.35f) / 0.65f).coerceIn(0f, 1f)
                            alpha = easeOutCubic(p)
                            translationY = (1f - easeOutCubic(p)) * 12.dp.toPx()
                        },
                )
            }
        }

        ProfileMorphHost(
            state = profile,
            username = displayName,
            photo = displayPhoto,
            cardNumber = cardNumber,
            navigator = navigator,
            viewModel = profileViewModel,
        )
    }
}

private const val CardTopDp = 130f

/** The logo's top below the status bar: Settings and Library centre a 32dp mark in a 62dp block 18dp down. */
private val TopBarLogoTop = 32.dp

/** Inner padding every card on this page shares. */
private val CardPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp)

/** The collapsed header: inset, logo, greeting, the card, and room under it. */
private fun collapsedHeaderPx(top: Float, cardH: Float, density: Float): Float = top + CardTopDp * density + cardH + 49f * density

/** Clips to the bounds with only the bottom corners rounded, by a radius that can change every frame. */
private fun Modifier.clipBottomRounded(radius: () -> Float): Modifier = drawWithCache {
    val path = Path()
    onDrawWithContent {
        val r = radius()
        if (r <= 0.5f) {
            drawContent()
            return@onDrawWithContent
        }
        path.reset()
        path.addRoundRect(
            RoundRect(
                rect = Rect(0f, 0f, size.width, size.height),
                bottomLeft = CornerRadius(r),
                bottomRight = CornerRadius(r),
            ),
        )
        clipPath(path) { this@onDrawWithContent.drawContent() }
    }
}

private val PageTitle = TextStyle(
    fontFamily = InterFontFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 23.sp,
    letterSpacing = (-0.6).sp,
    color = Color.White,
)

/** A big figure's size in dp: it follows the card's width, not the font-size setting, so it never overflows. */
@Composable
internal fun figureSp(dp: Float): TextUnit = with(LocalDensity.current) { dp.dp.toSp() }

/** How much the cards' figures grow or shrink from the design's 360dp-wide phone, by the width they actually get. */
private fun widthScale(cardWidthDp: Float): Float = (cardWidthDp / 320f).coerceIn(0.72f, 1.2f)

@Composable
private fun ListeningTimeCard(totalMs: Long, onViewStats: () -> Unit) {
    val totalMinutes = (totalMs / 60_000L).coerceAtLeast(0L)
    val hours = totalMinutes / 60
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(PageCard)
            .clickable(onClick = onViewStats)
            .padding(CardPadding),
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            // long hour counts ("1234h") shrink the figures a step so the label keeps its room
            val k = widthScale(maxWidth.value + 40f) * if (hours >= 1000) 0.8f else if (hours >= 100) 0.9f else 1f
            Row(verticalAlignment = Alignment.CenterVertically) {
                TimeValue(hours.toString(), stringResource(R.string.profile_unit_hour_short), k)
                Box(
                    modifier = Modifier
                        .padding(horizontal = (14 * k).dp)
                        .width(1.dp)
                        .height((34 * k).dp)
                        .background(Color.White.copy(alpha = 0.4f)),
                )
                TimeValue((totalMinutes % 60).toString(), stringResource(R.string.profile_unit_minute_short), k)
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(R.string.profile_total_listening_time),
                    style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp, lineHeight = 18.sp, color = Color(0xFF8A8A8A), textAlign = TextAlign.End),
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF474747)))
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.profile_view_stats),
            style = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color(0xFF8A8A8A)),
        )
    }
}

@Composable
private fun TimeValue(value: String, unit: String, k: Float) {
    Row(verticalAlignment = Alignment.Bottom) {
        OdometerNumber(
            value = value,
            fontSize = figureSp(44f * k),
            fontWeight = FontWeight.Bold,
            fontFamily = InterFontFamily,
            color = Color.White,
            letterSpacing = (-2).sp,
        )
        Text(
            unit,
            style = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.Bold, fontSize = figureSp(18f * k), color = Color.White),
            modifier = Modifier.padding(start = 1.dp, bottom = (6 * k).dp),
        )
    }
}

@Composable
private fun VersionCard(
    updateHeadline: String?,
    onCardClick: () -> Unit,
    onPillClick: () -> Unit,
) {
    BoxWithConstraints {
        val k = widthScale(maxWidth.value)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(PageCard)
                .clickable(onClick = onCardClick)
                .padding(CardPadding),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.profile_version_about_app), style = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color(0xFF9A9A9A)))
                Text(
                    // "7.1.1 build#1093" -> "7.1.1"
                    BuildConfig.VERSION_NAME.substringBefore(' '),
                    style = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.Bold, fontSize = figureSp(44f * k), letterSpacing = (-2).sp, color = Color.White),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (updateHeadline != null) {
                    Text(updateHeadline, style = TextStyle(fontFamily = InterFontFamily, fontSize = 12.sp, color = Color(0xFF88FF76)), maxLines = 2)
                }
            }
            Spacer(Modifier.width(10.dp))
            VersionCapsule(update = updateHeadline != null, onAbout = onPillClick)
        }
    }
}

/**
 * The version card's capsule: "About app", or "Update" once one is out. The swap pops - the
 * capsule squashes and springs back past its size while the new word grows in on a bounce and
 * the width springs to fit it.
 */
@Composable
private fun VersionCapsule(update: Boolean, onAbout: () -> Unit) {
    val shown = if (update) stringResource(R.string.profile_capsule_update) else stringResource(R.string.profile_capsule_about)

    // the pop: a quick squash, then a spring that carries past full size and settles
    val pop = remember { Animatable(1f) }
    var popped by remember { mutableStateOf(false) }
    LaunchedEffect(shown) {
        // not on first showing - only when the label actually changes
        if (!popped) {
            popped = true
            return@LaunchedEffect
        }
        pop.animateTo(0.86f, tween(durationMillis = 90, easing = FastOutSlowInEasing))
        pop.animateTo(1f, spring(dampingRatio = 0.38f, stiffness = 520f))
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .graphicsLayer {
                scaleX = pop.value
                scaleY = pop.value
            }
            .clip(CircleShape)
            .background(Color(0xFF4D4D4D))
            .clickable(onClick = onAbout)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        AnimatedContent(
            targetState = shown,
            transitionSpec = {
                // the new word grows in on a bounce as the old one shrinks away
                val enter = scaleIn(
                    animationSpec = spring(dampingRatio = 0.45f, stiffness = 420f),
                    initialScale = 0.35f,
                ) + fadeIn(tween(durationMillis = 160))
                val exit = scaleOut(
                    animationSpec = tween(durationMillis = 140, easing = FastOutSlowInEasing),
                    targetScale = 0.35f,
                ) + fadeOut(tween(durationMillis = 120))
                // and the capsule's width springs to fit it, overshooting a touch
                (enter togetherWith exit) using SizeTransform(clip = false) { _, _ ->
                    spring(
                        dampingRatio = 0.6f,
                        stiffness = 380f,
                        visibilityThreshold = IntSize.VisibilityThreshold,
                    )
                }
            },
            contentAlignment = Alignment.Center,
            label = "versionCapsule",
        ) { label ->
            Text(
                label,
                style = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color.White),
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

/**
 * The dev-and-contributors card: the credited faces overlap, and pressed they spill to the
 * right. ChordWave has no DevSpace, so the faces are the same GitHub credits the About sheet
 * lists (the lead dev and the pinned Modern-interface inspiration) and the card opens About.
 */
@Composable
private fun DeveloperCard(onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val spread by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 420f),
        label = "devCardSpread",
    )
    val face = 30.dp
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(PageCard)
            .clickable(interactionSource = interaction, indication = RemoteFocus, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        // room for every face at rest, 40% of a face apart; pressed they spill to the right
        Box(Modifier.size(width = face + face * 0.4f * (DevFaces.size - 1), height = face)) {
            DevFaces.forEachIndexed { i, url ->
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(face)
                        .graphicsLayer { translationX = i * face.toPx() * (0.4f + 0.35f * spread) }
                        .clip(CircleShape)
                        .background(Color(0xFFD9D9D9))
                        .border(1.5.dp, PageCard, CircleShape),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            stringResource(R.string.profile_meet_dev_contributors),
            style = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.Bold, fontSize = 15.sp, letterSpacing = (-0.6).sp, color = Color(0xFF9A9A9A)),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

private val DevFaces = listOf(
    "https://github.com/rikki53.png?size=160",
    "https://github.com/realidkroo.png?size=160",
)

/**
 * Mochy's Discord card, with ChordWave's robot in the circle. Mochy's opens
 * Mochy's Discord server; ChordWave has none yet, so this opens the Discord
 * presence settings instead (batch 44).
 */
@Composable
private fun DiscordCard(onClick: () -> Unit) {
    val face = 30.dp
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(PageCard)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Image(
            painter = rememberResourcePainter(R.drawable.robot_discord),
            contentDescription = stringResource(R.string.profile_discord_cd),
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(face)
                .clip(CircleShape)
                .background(Color.White),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            stringResource(R.string.profile_discord_card),
            style = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.Bold, fontSize = 15.sp, letterSpacing = (-0.6).sp, color = Color.White),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun OpenSettingsRow(onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(PageCard)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Color(0xFFD9D9D9)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.settings), contentDescription = null, tint = Color(0xFF232323), modifier = Modifier.size(18.dp))
        }
        Text(stringResource(R.string.profile_open_settings), style = PageTitle.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold))
    }
}
