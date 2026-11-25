package com.music.bitchord.ui.tv.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.music.bitchord.R
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.TvSettings
import com.music.bitchord.ui.MainViewModel
import com.music.bitchord.ui.tv.TvAppMark
import com.music.bitchord.ui.tv.audio.TvSpatialAudioEngine
import com.music.bitchord.ui.tv.components.TvButton
import com.music.bitchord.ui.tv.components.TvListRow
import com.music.bitchord.ui.tv.components.tvClick
import com.music.bitchord.ui.tv.components.tvInitialFocus
import com.music.bitchord.ui.tv.components.tvLift
import com.music.bitchord.ui.tv.dialogs.TvAccountDialog
import com.music.bitchord.ui.tv.personalization.AppThemeOption
import com.music.bitchord.ui.tv.theme.TvDarkPalette
import com.music.bitchord.ui.tv.theme.TvGlass
import com.music.bitchord.ui.tv.theme.TvMidnightPalette
import com.music.bitchord.ui.tv.theme.TvOledPalette
import com.music.bitchord.ui.tv.theme.TvType
import kotlinx.coroutines.delay

private enum class SetupStep { Welcome, SignIn, Appearance, Preferences, Done }

/**
 * First-run setup, in the manner of Apple TV's: one decision per screen, a
 * centred title and explanation, and the choices stacked at the bottom. Back
 * steps back; nothing is saved until the last screen.
 */
@Composable
fun TvSetupScreen(
    viewModel: MainViewModel,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var step by remember { mutableStateOf(SetupStep.Welcome) }
    var theme by remember { mutableStateOf(AppThemeOption.fromId(TvSettings.tvTheme.value)) }

    val finish = {
        TvSettings.setTvPersonalization(
            nickname = TvSettings.tvNickname.value,
            themeId = theme.id,
            version = 1,
        )
        onComplete()
    }

    BackHandler(enabled = step != SetupStep.Welcome) {
        step = SetupStep.entries[step.ordinal - 1]
    }

    AnimatedContent(
        targetState = step,
        transitionSpec = {
            val forward = targetState.ordinal > initialState.ordinal
            (slideInHorizontally(tween(320)) { if (forward) it / 6 else -it / 6 } + fadeIn(tween(320)))
                .togetherWith(slideOutHorizontally(tween(240)) { if (forward) -it / 6 else it / 6 } + fadeOut(tween(200)))
        },
        label = "setupStep",
        modifier = modifier.fillMaxSize(),
    ) { current ->
        when (current) {
            SetupStep.Welcome -> TvSetupPage(
                title = "Welcome to ChordWave",
                message = "Your music, made for the big screen.",
                hero = { TvRobotMascot() },
            ) {
                TvSetupButton("Continue", initialFocus = true) { step = SetupStep.SignIn }
                TvSetupButton("Set Up Later") { finish() }
            }
            SetupStep.SignIn -> TvSignInStep(
                viewModel = viewModel,
                onContinue = { step = SetupStep.Appearance },
            )
            SetupStep.Appearance -> TvSetupPage(
                title = "Choose a Look",
                message = "You can change this later in Settings.",
                hero = {
                    Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                        AppThemeOption.entries.forEach { option ->
                            TvThemeSwatch(
                                option = option,
                                selected = option == theme,
                                initialFocus = option == theme,
                                onClick = { theme = option },
                            )
                        }
                    }
                },
            ) {
                TvSetupButton("Continue") {
                    TvSettings.setTvTheme(theme.id)
                    step = SetupStep.Preferences
                }
            }
            SetupStep.Preferences -> TvPreferencesStep(onContinue = { step = SetupStep.Done })
            SetupStep.Done -> TvSetupPage(
                title = "You're All Set",
                message = "Find music in Home and Explore. Everything you save is in Library.",
                hero = {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(77.dp),
                    )
                },
            ) {
                TvSetupButton("Start Listening", initialFocus = true) { finish() }
            }
        }
    }
}

/** The common frame of every setup screen. */
@Composable
private fun TvSetupPage(
    title: String,
    message: String,
    hero: @Composable () -> Unit,
    buttons: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 38.dp, vertical = 35.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.weight(1f))
        hero()
        Spacer(modifier = Modifier.height(29.dp))
        Text(text = title, style = TvType.LargeTitle, color = TvGlass.TextPrimary, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            style = TvType.Body.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.W400),
            color = TvGlass.TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 448.dp),
        )
        Spacer(modifier = Modifier.weight(1f))
        Column(
            modifier = Modifier.width(304.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = buttons,
        )
    }
}

@Composable
private fun TvSetupButton(text: String, initialFocus: Boolean = false, onClick: () -> Unit) {
    TvButton(
        text = text,
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .tvInitialFocus(initialFocus),
    )
}

@Composable
private fun TvSignInStep(viewModel: MainViewModel, onContinue: () -> Unit) {
    val signedIn by viewModel.signedIn.collectAsState()
    val account by viewModel.account.collectAsState()
    var signingIn by remember { mutableStateOf(false) }

    // Signing in from here moves on by itself once it lands.
    LaunchedEffect(signedIn) {
        if (signedIn && signingIn) {
            signingIn = false
            onContinue()
        }
    }

    TvSetupPage(
        title = if (signedIn) "Signed In" else "Sign In to YouTube Music",
        message = if (signedIn) {
            "ChordWave will use ${account?.name ?: "your account"}'s library and recommendations."
        } else {
            "Bring your library, playlists and recommendations. You can also do this later in Settings."
        },
        hero = { TvAppMark(size = 77.dp) },
    ) {
        if (signedIn) {
            TvSetupButton("Continue", initialFocus = true, onClick = onContinue)
        } else {
            TvSetupButton("Sign In", initialFocus = true) { signingIn = true }
            TvSetupButton("Not Now", onClick = onContinue)
        }
    }

    if (signingIn && !signedIn) {
        // Phone (QR) sign-in is held back from setup for now; Settings still offers it.
        TvAccountDialog(viewModel = viewModel, onDismiss = { signingIn = false }, allowPhoneSignIn = false)
    }
}

@Composable
private fun TvPreferencesStep(onContinue: () -> Unit) {
    val lyrics by AppSettings.syncedLyrics.collectAsState()
    val canvas by AppSettings.animatedCanvas.collectAsState()
    val automix by AppSettings.smartFadeEnabled.collectAsState()
    val spatial by TvSettings.spatialAudioEnabled.collectAsState()

    TvSetupPage(
        title = "Listening",
        message = "Select a row to turn it on or off.",
        hero = {
            Column(
                modifier = Modifier.width(416.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                TvListRow(
                    title = "Synced Lyrics",
                    value = if (lyrics) "On" else "Off",
                    modifier = Modifier.tvInitialFocus(),
                    onClick = { AppSettings.setSyncedLyrics(!lyrics) },
                )
                TvListRow(
                    title = "Motion Artwork",
                    value = if (canvas) "On" else "Off",
                    onClick = { AppSettings.setAnimatedCanvas(!canvas) },
                )
                TvListRow(
                    title = "Automix",
                    value = if (automix) "On" else "Off",
                    onClick = { AppSettings.setSmartFadeEnabled(!automix) },
                )
                TvListRow(
                    title = "Spatial Audio",
                    value = if (spatial) "On" else "Off",
                    onClick = {
                        TvSettings.setSpatialAudioEnabled(!spatial)
                        TvSpatialAudioEngine.setEnabled(!spatial)
                    },
                )
            }
        },
    ) {
        TvSetupButton("Continue", onClick = onContinue)
    }
}

/** A small picture of the theme's canvas with two placeholder cards, its name under it. */
@Composable
private fun TvThemeSwatch(
    option: AppThemeOption,
    selected: Boolean,
    initialFocus: Boolean,
    onClick: () -> Unit,
) {
    val palette = when (option) {
        AppThemeOption.MIDNIGHT -> TvMidnightPalette
        AppThemeOption.PURE_BLACK -> TvOledPalette
        AppThemeOption.DYNAMIC_ARTWORK -> TvDarkPalette
    }
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .width(168.dp)
                .aspectRatio(16f / 10f)
                .tvInitialFocus(initialFocus)
                .tvLift(interaction, RoundedCornerShape(11.dp), focusedScale = 1.08f, elevation = 18.dp)
                .tvClick(interaction, onClick = onClick)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            if (option == AppThemeOption.DYNAMIC_ARTWORK) Color(0xFF3A2A44) else palette.canvasTop,
                            palette.canvasBottom,
                        ),
                    ),
                )
                .padding(13.dp),
        ) {
            Row(
                modifier = Modifier.align(Alignment.BottomStart),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                repeat(3) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(Color.White.copy(alpha = 0.16f)),
                    )
                }
            }
            if (selected) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = "Selected",
                    tint = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(19.dp)
                        .clip(CircleShape),
                )
            }
        }
        Spacer(modifier = Modifier.height(11.dp))
        Text(
            text = option.title,
            style = TvType.CardTitle,
            color = if (focused || selected) TvGlass.TextPrimary else TvGlass.TextSecondary,
        )
    }
}


private val MascotGlow = Color(0xFF8FD0FF)

/**
 * ChordWave's robot on the TV welcome (batch 44), after Mochy's TvMascot: she
 * swings in with a little overshoot, then floats and sways while her phone
 * glows. Mochy's welcome puts her at the right edge; this setup keeps its
 * centred column, so she stands in the hero slot instead.
 */
@Composable
private fun TvRobotMascot(modifier: Modifier = Modifier) {
    val arrive = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(120)
        arrive.animateTo(1f, spring(dampingRatio = 0.58f, stiffness = 110f))
    }
    val idle = rememberInfiniteTransition(label = "mascotIdle")
    val bob by idle.animateFloat(-1f, 1f, infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "bob")
    val sway by idle.animateFloat(-1f, 1f, infiniteRepeatable(tween(3700, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "sway")
    val glow by idle.animateFloat(0.55f, 1f, infiniteRepeatable(tween(1900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "glow")
    Image(
        painter = painterResource(R.drawable.robot_tv_mascot),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        alignment = Alignment.BottomCenter,
        modifier = modifier
            .height(190.dp)
            .graphicsLayer {
                val p = arrive.value
                // she pivots from her feet, so the sway reads as a lean and not a wobble
                transformOrigin = TransformOrigin(0.5f, 1f)
                translationY = (1f - p) * 36.dp.toPx() + bob * 5.dp.toPx()
                rotationZ = (1f - p) * 8f + sway * 1.3f
                alpha = p.coerceIn(0f, 1f)
            }
            // the screen she's holding up gives off a soft light, read while drawing so it never recomposes
            .drawBehind {
                val center = Offset(size.width - size.height * 0.2f, size.height * 0.36f)
                val radius = size.height * 0.26f
                drawCircle(
                    Brush.radialGradient(listOf(MascotGlow.copy(alpha = 0.3f * glow), Color.Transparent), center, radius),
                    radius = radius,
                    center = center,
                )
            },
    )
}
