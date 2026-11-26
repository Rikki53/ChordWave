// settings > player > spotify canvas, same cards as the discord page, signing in reuses spotify import's own sign-in page
// Ported from Mochy Music (GPL-3.0). ChordWave's canvas engine is its own
// (data/canvas/SpotifyCanvas + SpotifyToken, driven by the sp_dc cookie in
// AppSettings), so the sign-in here harvests that cookie from the login page
// instead of Mochy's SpotifyCanvasProvider cookie store. The on/off switch
// writes Mochy's SpotifyCanvasKey and mirrors into AppSettings'
// disableSpotifyCanvas so the ChordWave canvas chain honours it.

package com.music.bitchord.ui.mochy.screens.settings.integrations.spotify

import android.webkit.CookieManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.bitchord.R
import com.music.bitchord.data.canvas.SpotifyToken
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.ui.mochy.constants.SpotifyCanvasKey
import com.music.bitchord.ui.mochy.importer.account.AccountService
import com.music.bitchord.ui.mochy.component.SubSettingsScaffold
import com.music.bitchord.ui.mochy.screens.settings.importsync.CaptureWebView
import com.music.bitchord.ui.mochy.screens.settings.importsync.clearWebSession
import com.music.bitchord.ui.mochy.screens.settings.integrations.IntegrationPageSubtitle
import com.music.bitchord.ui.mochy.screens.settings.integrations.discord.DiscordCard
import com.music.bitchord.ui.mochy.screens.settings.integrations.discord.DiscordCardTitle
import com.music.bitchord.ui.mochy.screens.settings.integrations.discord.DiscordColors
import com.music.bitchord.ui.mochy.screens.settings.integrations.discord.DiscordSectionTitle
import com.music.bitchord.ui.mochy.screens.settings.integrations.discord.DiscordSwitchCard
import com.music.bitchord.ui.mochy.utils.rememberPreference
import kotlinx.coroutines.launch

const val SpotifyRoute = "integrations_settings/spotify"

/** the sp_dc session cookie the login page sets, if the sign-in went through */
private fun spotifySessionCookie(): String? =
    CookieManager.getInstance().getCookie("https://open.spotify.com/")
        ?.split(';')
        ?.map { it.trim() }
        ?.firstOrNull { it.startsWith("sp_dc=") }
        ?.removePrefix("sp_dc=")
        ?.takeIf { it.isNotBlank() }

@Composable
fun SpotifyScreen(navigator: com.music.bitchord.ui.mochy.navigation.MochyNavigator) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // the canvas lookup reads this same key, so no viewmodel needed here
    val (enabled, setEnabled) = rememberPreference(SpotifyCanvasKey, defaultValue = false)
    var signingIn by remember { mutableStateOf(false) }
    val spdc by AppSettings.spotifySpdcToken.collectAsState()
    val signedIn = spdc.isNotBlank()

    fun warmUp() {
        SpotifyToken.init(context)
        scope.launch { SpotifyToken.accessToken() }
    }

    if (signingIn) {
        val spotify = AccountService.SPOTIFY
        BackHandler { signingIn = false }
        CaptureWebView(
            title = stringResource(R.string.spotify_canvas_sign_in),
            subtitle = stringResource(R.string.spotify_canvas_sign_in_subtitle),
            startUrl = spotify.loginUrl.orEmpty(),
            playerOrigins = spotify.playerOrigins,
            desktopSite = spotify.desktopSite,
            onHeader = { _, _, _ -> },
            onTick = {
                // spotify sets its sign-in cookie once the login is through, that's all we wait for
                spotifySessionCookie()?.let { cookie ->
                    AppSettings.setSpotifySpdcToken(cookie)
                    signingIn = false
                    warmUp()
                }
            },
            onExit = { signingIn = false },
        )
        return
    }

    SubSettingsScaffold(
        title = stringResource(R.string.spotify_canvas),
        onBack = { navigator.popBackStack() },
    ) {
        IntegrationPageSubtitle(stringResource(R.string.spotify_canvas_subtitle))

        DiscordCard {
            DiscordCardTitle(stringResource(R.string.spotify_canvas_what_title))
            Text(
                text = stringResource(R.string.spotify_canvas_what),
                color = DiscordColors.Secondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 19.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        DiscordSwitchCard(
            title = stringResource(R.string.spotify_canvas_use),
            description = stringResource(R.string.spotify_canvas_use_desc),
            checked = enabled,
            onCheckedChange = { on ->
                setEnabled(on)
                // ChordWave's canvas chain reads this instead of the key above
                AppSettings.setDisableSpotifyCanvas(!on)
                if (on) warmUp()
            },
        )

        DiscordSectionTitle(stringResource(R.string.spotify_canvas_account))
        DiscordCard {
            DiscordCardTitle(stringResource(if (signedIn) R.string.spotify_canvas_signed_in else R.string.spotify_canvas_signed_out))
        }
        Spacer(Modifier.height(12.dp))
        AccountButton(
            signedIn = signedIn,
            onSignIn = { signingIn = true },
            onSignOut = {
                clearWebSession(AccountService.SPOTIFY.webOrigins)
                AppSettings.setSpotifySpdcToken("")
                SpotifyToken.invalidate()
            },
        )
        Text(
            text = stringResource(R.string.spotify_canvas_note),
            color = DiscordColors.Secondary,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp, start = 8.dp, end = 8.dp),
        )
        // room under the last card for the mini player
        Spacer(Modifier.height(140.dp))
    }
}

@Composable
private fun AccountButton(signedIn: Boolean, onSignIn: () -> Unit, onSignOut: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(CircleShape)
            .background(if (signedIn) DiscordColors.Card else Color.White)
            .clickable(onClick = if (signedIn) onSignOut else onSignIn),
    ) {
        Text(
            text = stringResource(if (signedIn) R.string.spotify_canvas_sign_out else R.string.spotify_canvas_sign_in),
            color = if (signedIn) Color.White else Color.Black,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
