// ProfileScreen.kt
//
// The profile page: greeting, ID card, listening time (into Stats),
// version & about, contributors, and the way into Settings.
//
// Ported from Mochy Music (GPL-3.0): ui/screens/settings/ProfileScreen.kt —
// SIMPLIFIED on purpose (see the merge log): no morph animation into a
// UserProfilePage, no pull-down/scratch-off recap header, no giant MOCHY
// column. The card opens the edit sheet on tap instead of morphing.
// ListeningTimeCard's odometer figures are plain text (OdometerNumber was
// never ported to CW). The Discord card is NOT ported — that server is
// Mochy's own. "View Stats" goes to the Stats page (batch 39); Replay has
// its own tab in the Library.

package com.music.bitchord.ui.mochy.screens.profile

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.music.bitchord.BuildConfig
import com.music.bitchord.R
import com.music.bitchord.ui.MainViewModel
import com.music.bitchord.ui.mochy.LocalPlayerAwareWindowInsets
import com.music.bitchord.ui.mochy.component.LocalMenuState
import com.music.bitchord.ui.mochy.component.RemoteFocus
import com.music.bitchord.ui.mochy.constants.ProfileCardNumberKey
import com.music.bitchord.ui.mochy.constants.ProfileJoinedEpochDayKey
import com.music.bitchord.ui.mochy.constants.ProfilePicUriKey
import com.music.bitchord.ui.mochy.constants.UsernameKey
import com.music.bitchord.ui.mochy.navigation.MochyNavigator
import com.music.bitchord.ui.mochy.screens.settings.importsync.YouTubeSyncRoute
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
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.random.Random

private val PageCard = Color(0xFF0F0F0F)

/** Inner padding every card on this page shares. */
private val CardPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp)

private val PageTitle = TextStyle(
    fontFamily = InterFontFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 23.sp,
    letterSpacing = (-0.6).sp,
    color = Color.White,
)

@Composable
fun ProfileScreen(
    navigator: MochyNavigator,
    mainViewModel: MainViewModel = viewModel(),
) {
    val context = LocalContext.current
    val menuState = LocalMenuState.current
    val updateState by rememberUpdateState()
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

    // the onboarding writes the card's number and join date; an install from
    // before it gets them once, here. Join date falls back to the install
    // date (the first day this app was on the phone).
    LaunchedEffect(cardNumberPref, joinedEpochDay) {
        if (cardNumberPref.isBlank()) setCardNumber((10_000_000_000L + Random.nextLong(89_999_999_999L)).toString())
        if (joinedEpochDay < 0L) {
            val installMs = runCatching {
                context.packageManager.getPackageInfo(context.packageName, 0).firstInstallTime
            }.getOrDefault(System.currentTimeMillis())
            setJoinedEpochDay(Instant.ofEpochMilli(installMs).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay())
        }
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
    val greetingRes = remember {
        listOf(
            R.string.profile_greeting_hello_again,
            R.string.profile_greeting_hey,
            R.string.profile_greeting_welcome_back,
            R.string.profile_greeting_good_to_see_you,
        ).random()
    }

    val cardPress = remember { MutableInteractionSource() }
    val cardPressed by cardPress.collectIsPressedAsState()
    val statusBarTop = WindowInsets.stableSystemBars.asPaddingValues().calculateTopPadding()
    val bottomBarInset = LocalPlayerAwareWindowInsets.current.asPaddingValues().calculateBottomPadding()
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(statusBarTop + 32.dp))
        Icon(
            painter = painterResource(R.drawable.ic_chordwave_mark),
            contentDescription = "ChordWave",
            tint = Color.White,
            modifier = Modifier.size(width = 31.dp, height = 34.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(
                R.string.profile_greeting_format,
                stringResource(greetingRes),
                displayName.ifBlank { stringResource(R.string.profile_greeting_fallback) },
            ),
            style = PageTitle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(20.dp))
        // the card: signed out, tapping it opens the editor (Mochy morphs into a full
        // profile page here; the simplified page edits in place instead); signed in,
        // the card shows the Google account and tapping it opens the account screen
        Box(
            modifier = Modifier
                .widthIn(max = 380.dp)
                .fillMaxWidth()
                .align(Alignment.CenterHorizontally)
                .clickable(
                    interactionSource = cardPress,
                    indication = RemoteFocus,
                    onClick = {
                        if (account != null) navigator.navigate(YouTubeSyncRoute)
                        else menuState.showEditProfileSheet(username, photo)
                    },
                ),
        ) {
            ProfileIdCard(
                username = displayName,
                photo = displayPhoto,
                cardNumber = cardNumber,
                joinedText = joinedText,
                showIdCardOf = true,
                showEditHint = account == null,
                pressed = { cardPressed },
            )
        }
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
        ContributorsCard(onClick = { menuState.showAboutSheet() })
        Spacer(Modifier.height(28.dp))
        Text(
            stringResource(R.string.profile_settings_title),
            style = PageTitle.copy(fontSize = 15.sp),
            modifier = Modifier.padding(start = 2.dp, bottom = 10.dp),
        )
        OpenSettingsRow(onClick = { navigator.navigate("mochy_settings") })
        Spacer(Modifier.height(bottomBarInset + 24.dp))
    }
}

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
    // Mochy rolls these figures with OdometerNumber; CW never ported it, so
    // the simplified card shows plain figures.
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            value,
            style = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.Bold, fontSize = figureSp(44f * k), letterSpacing = (-2).sp, color = Color.White),
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
 * Mochy's DeveloperCard shows the devs' faces (DevSpace) — CW has no
 * DevSpace, so the simplified card shows a group glyph and opens the About
 * sheet, where the dev and contributors are listed.
 */
@Composable
private fun ContributorsCard(onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(PageCard)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(Color(0xFFD9D9D9)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.group), contentDescription = null, tint = Color(0xFF232323), modifier = Modifier.size(18.dp))
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

@Composable
private fun OpenSettingsRow(onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(14.dp),
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
