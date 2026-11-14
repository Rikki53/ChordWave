// settings → integrations → discord: log in, then shape the status friends see with a live preview
// Ported from Mochy Music (GPL-3.0). No ViewModel: the page reads CW's AppSettings discord*
// flows and KizzyRPC directly, the way the classic DiscordScreen does. The differences from
// Mochy are all honest ones:
// - Login opens the classic in-app WebView login (DiscordLoginScreen) at the host-owned
//   DiscordLoginRoute — CW signs in with a token, not Mochy's browser OAuth redirect.
// - No Live/Connecting states: CW's socket has no connection state to show, so the status line
//   only says whether presence is on and signed in.
// - Refresh interval, clear-on-pause and the four text lines have no CW engine, so they stay
//   visible but disabled, each saying what ChordWave does instead.
// - Image customisation is one notice card: CW shows the track artwork, or its own mark.

package com.music.bitchord.ui.mochy.screens.settings.integrations.discord

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.bitchord.R
import com.music.bitchord.data.NerdStats
import com.music.bitchord.data.discord.SuperProperties
import com.music.bitchord.data.discord.discordAudioQualityLine
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.ui.mochy.LocalPlayerAwareWindowInsets
import com.music.bitchord.ui.mochy.component.SubSettingsScaffold
import com.music.bitchord.ui.mochy.component.toCwSong
import com.music.bitchord.ui.mochy.navigation.MochyNavigator
import com.music.bitchord.ui.mochy.playback.LocalPlayerConnection
import com.music.bitchord.ui.mochy.screens.settings.integrations.IntegrationPageSubtitle
import com.music.bitchord.ui.mochy.utils.stableStatusBars
import com.music.bitchord.ui.screens.DiscordActivityKind
import com.music.bitchord.ui.screens.DiscordPresenceStatus
import com.my.kizzy.rpc.KizzyRPC
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

const val DiscordRoute = "integrations_settings/discord"

/**
 * Host contract: the Modern host renders the classic DiscordLoginScreen at this route and, when
 * it captures a token, calls AppSettings.setDiscordToken(token) and pops back. See NEEDS.md.
 */
const val DiscordLoginRoute = "integrations_settings/discord/login"

/** where SubSettingsScaffold's header ends; the preview counts as gone once it's under it */
private val HeaderHeight = 118.dp

@Composable
fun DiscordScreen(navigator: MochyNavigator) {
    val token by AppSettings.discordToken.collectAsState()
    val username by AppSettings.discordUsername.collectAsState()
    val name by AppSettings.discordName.collectAsState()
    val avatar by AppSettings.discordAvatar.collectAsState()
    val rpcEnabled by AppSettings.discordRpcEnabled.collectAsState()
    val useDetails by AppSettings.discordUseDetails.collectAsState()
    val showAudioQuality by AppSettings.discordShowAudioQuality.collectAsState()
    val status by AppSettings.discordStatus.collectAsState()
    val activityType by AppSettings.discordActivityType.collectAsState()
    val activityName by AppSettings.discordActivityName.collectAsState()
    val button1Text by AppSettings.discordButton1Text.collectAsState()
    val button1Visible by AppSettings.discordButton1Visible.collectAsState()
    val button2Text by AppSettings.discordButton2Text.collectAsState()
    val button2Visible by AppSettings.discordButton2Visible.collectAsState()

    val signedIn = token.isNotBlank()
    val appName = stringResource(R.string.app_name).removeSuffix(" Dev")

    // refreshes the cached profile whenever the connected account changes. A failure is left
    // to stand rather than clearing the cache: the usual reason for one is being offline, and
    // blanking the name every time the phone loses signal would read as having been signed out.
    LaunchedEffect(token) {
        if (token.isEmpty()) {
            AppSettings.setDiscordAccount("", "", null)
            return@LaunchedEffect
        }
        withContext(Dispatchers.IO) {
            KizzyRPC.getUserInfo(
                token,
                SuperProperties.userAgent,
                SuperProperties.superPropertiesBase64,
            ).onSuccess {
                AppSettings.setDiscordAccount(it.username, it.name, it.avatar)
            }
        }
    }

    val nerdStats by NerdStats.current.collectAsState()
    val qualityLine = discordAudioQualityLine(nerdStats)
    // nothing playing: show a made-up song so the preview still means something
    val sample = remember { CwNowPlaying.sample() }
    val track = liveNowPlaying(qualityLine) ?: sample
    val settings = CwPresenceSettings(
        enabled = rpcEnabled,
        activityType = activityType,
        activityName = activityName,
        useDetails = useDetails,
        showAudioQuality = showAudioQuality,
        button1Text = button1Text,
        button1Visible = button1Visible,
        button2Text = button2Text,
        button2Visible = button2Visible,
    )
    val profile = CwDiscordProfile(
        displayName = name,
        username = username,
        avatarUrl = avatar.ifBlank { null },
    ).takeIf { signedIn }

    val density = LocalDensity.current
    val statusBarTop = WindowInsets.stableStatusBars.asPaddingValues().calculateTopPadding()
    val headerBottomPx = with(density) { (statusBarTop + HeaderHeight).toPx() }
    var previewBottomPx by remember { mutableFloatStateOf(Float.MAX_VALUE) }
    val previewHidden by remember(headerBottomPx) { derivedStateOf { previewBottomPx < headerBottomPx } }
    val bottomInset = LocalPlayerAwareWindowInsets.current.asPaddingValues().calculateBottomPadding()

    Box(Modifier.fillMaxSize()) {
        SubSettingsScaffold(
            title = "Discord",
            onBack = { navigator.popBackStack() },
            backLabel = stringResource(R.string.integrations),
        ) {
            IntegrationPageSubtitle("Rich presence on your profile")
            DiscordToggleCard(
                checked = rpcEnabled,
                onCheckedChange = AppSettings::setDiscordRpcEnabled,
                status = statusLine(signedIn, rpcEnabled),
                enabled = signedIn,
            )
            Spacer(Modifier.height(12.dp))
            DiscordProfileCard(
                profile = profile,
                track = track,
                settings = settings,
                appName = appName,
                modifier = Modifier.onGloballyPositioned { previewBottomPx = it.boundsInWindow().bottom },
            )
            Spacer(Modifier.height(12.dp))
            AccountButton(
                signedIn = signedIn,
                onLogin = { navigator.navigate(DiscordLoginRoute) },
                onLogOut = AppSettings::clearDiscordAccount,
            )
            CustomizeSection(
                useDetails = useDetails,
                showAudioQuality = showAudioQuality,
                status = status,
                activityType = activityType,
                activityName = activityName,
                button1Text = button1Text,
                button1Visible = button1Visible,
                button2Text = button2Text,
                button2Visible = button2Visible,
            )
            // room for the floating preview over the last card
            Spacer(Modifier.height(220.dp))
        }

        DiscordFloatingPreview(
            visible = previewHidden,
            track = track,
            settings = settings,
            appName = appName,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 20.dp)
                .padding(bottom = bottomInset + 12.dp),
        )
    }
}

/** the live track as the preview needs it, or null when the player has nothing on */
@Composable
private fun liveNowPlaying(qualityLine: String?): CwNowPlaying? {
    val connection = LocalPlayerConnection.current ?: return null
    val song by connection.currentSong.collectAsState()
    val progress by connection.progressState.collectAsState()
    val playing by connection.isPlaying.collectAsState()
    val currentSong = song ?: return null
    return CwNowPlaying(
        // The connection's song is a Mochy-model Song; the preview
        // renders a CW Song (title/artist/artwork only).
        song = currentSong.toCwSong(),
        positionMs = progress.position,
        durationMs = progress.duration,
        isPlaying = playing,
        // the listener's own rate, not the player's instantaneous one — a crossfade stretch
        // must not get stamped into the title, the same reason PlaybackService reads
        // AppSettings.playbackSpeed instead of the player here
        speed = AppSettings.playbackSpeed.value,
        qualityLine = qualityLine,
    )
}

private fun statusLine(signedIn: Boolean, enabled: Boolean): String = when {
    !signedIn -> "Log in below to turn it on"
    !enabled -> "Off, so your profile shows nothing from ChordWave"
    else -> "Shows up when you play something"
}

@Composable
private fun AccountButton(
    signedIn: Boolean,
    onLogin: () -> Unit,
    onLogOut: () -> Unit,
) {
    val (text, action) = if (signedIn) {
        "Log out from Discord" to onLogOut
    } else {
        "Log in with Discord" to onLogin
    }
    val inviting = !signedIn
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(CircleShape)
            .background(if (inviting) DiscordColors.Blurple else DiscordColors.Profile)
            .clickable { action() },
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
    if (inviting) {
        LoginNote(stringResource(R.string.discord_login_note), Modifier.padding(top = 8.dp))
        // CW signs in with the account's own token, not OAuth, so say up front what that means
        LoginNote(stringResource(R.string.discord_permissions_note), Modifier.padding(top = 4.dp, bottom = 8.dp))
    }
}

@Composable
private fun LoginNote(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = DiscordColors.Secondary,
        fontSize = 12.sp,
        textAlign = TextAlign.Center,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    )
}

@Composable
private fun CustomizeSection(
    useDetails: Boolean,
    showAudioQuality: Boolean,
    status: String,
    activityType: String,
    activityName: String,
    button1Text: String,
    button1Visible: Boolean,
    button2Text: String,
    button2Visible: Boolean,
) {
    val gap = Modifier.height(12.dp)

    DiscordSectionTitle("CUSTOMIZE")
    DiscordPlaceholderCard()

    DiscordSectionTitle("Options")
    DiscordCard {
        DiscordCardTitle("Refresh interval")
        Spacer(Modifier.height(4.dp))
        DiscordStepSlider(
            value = 15,
            values = (5..60).toList(),
            label = { "${it}s" },
            onValueChange = {},
            enabled = false,
        )
        Text(
            text = stringResource(R.string.discord_presence_footer),
            color = DiscordColors.Secondary,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            modifier = Modifier.padding(start = 4.dp, top = 10.dp),
        )
    }
    Spacer(gap)
    DiscordSwitchCard(
        title = stringResource(R.string.discord_use_details),
        description = stringResource(R.string.discord_use_details_desc),
        checked = useDetails,
        onCheckedChange = AppSettings::setDiscordUseDetails,
    )
    Spacer(gap)
    DiscordSwitchCard(
        title = stringResource(R.string.discord_show_audio_quality),
        description = stringResource(R.string.discord_show_audio_quality_subtitle),
        checked = showAudioQuality,
        onCheckedChange = AppSettings::setDiscordShowAudioQuality,
    )

    DiscordSectionTitle(stringResource(R.string.discord_behaviour))
    DiscordSwitchCard(
        title = stringResource(R.string.discord_clear_on_pause),
        description = stringResource(R.string.discord_clear_on_pause_desc),
        checked = false,
        onCheckedChange = {},
        enabled = false,
        note = stringResource(R.string.discord_clear_on_pause_na),
    )

    DiscordSectionTitle("Activity")
    DiscordCard {
        DiscordCardTitle("Select the activity type")
        Spacer(Modifier.height(10.dp))
        // four kinds do not fit the segmented row's fixed slots, so this one is a dropdown
        DiscordDropdown(
            selected = DiscordActivityKind.entries.find { it.value == activityType } ?: DiscordActivityKind.LISTENING,
            options = DiscordActivityKind.entries,
            label = { it.label },
            onSelect = { kind -> AppSettings.setDiscordActivityType(kind.value) },
        )
    }
    Spacer(gap)
    DiscordTextCard("Activity name", activityName, AppSettings::setDiscordActivityName, hint = "ChordWave")
    Spacer(gap)
    DiscordCard {
        DiscordCardTitle("Status")
        Spacer(Modifier.height(10.dp))
        DiscordDropdown(
            selected = DiscordPresenceStatus.entries.find { it.value == status } ?: DiscordPresenceStatus.ONLINE,
            options = DiscordPresenceStatus.entries,
            label = { it.label },
            onSelect = { presence -> AppSettings.setDiscordStatus(presence.value) },
        )
    }

    DiscordSectionTitle("Strings customisation")
    DiscordNoticeCard(stringResource(R.string.discord_strings_note))
    Spacer(gap)
    DiscordTextCard("Details string", "", {}, hint = "Song title", enabled = false)
    Spacer(gap)
    DiscordTextCard("State string", "", {}, hint = "Artist", enabled = false)
    Spacer(gap)
    DiscordTextCard("Large image hover text", "", {}, hint = "Audio quality line", enabled = false)
    Spacer(gap)
    DiscordTextCard("Small image hover text", "", {}, hint = "Not sent", enabled = false)

    DiscordSectionTitle("Image customisation")
    DiscordNoticeCard(stringResource(R.string.discord_images_note))

    DiscordSectionTitle("Buttons")
    DiscordButtonCard(
        title = "1st button",
        text = button1Text,
        onTextChange = AppSettings::setDiscordButton1Text,
        visible = button1Visible,
        onVisibleChange = AppSettings::setDiscordButton1Visible,
        targetDescription = stringResource(R.string.discord_button1_target),
    )
    Spacer(gap)
    DiscordButtonCard(
        title = "2nd button",
        text = button2Text,
        onTextChange = AppSettings::setDiscordButton2Text,
        visible = button2Visible,
        onVisibleChange = AppSettings::setDiscordButton2Visible,
        targetDescription = stringResource(R.string.discord_button2_target),
    )
    Text(
        text = "Discord only shows the buttons to other people, never on your own profile.",
        color = DiscordColors.Secondary,
        fontSize = 12.sp,
        modifier = Modifier.padding(start = 4.dp, top = 10.dp),
    )
}
