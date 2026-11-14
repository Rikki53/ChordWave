// the last.fm or libre.fm page: every song that counts is scrobbled
// Ported from Mochy Music (GPL-3.0). No ViewModel: the page reads CW's AppSettings Last.fm
// flows and LastFM directly. One honest difference from Mochy: CW keeps a SINGLE scrobble
// account (one session key, one endpoint), so signing in here replaces an account on the
// other service, and there is no offline pending queue — the "plays waiting" row is gone.
// "Send likes" has no CW engine, so its row stays visible but disabled with a note.

package com.music.bitchord.ui.mochy.screens.settings.integrations

import android.widget.Toast
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.music.bitchord.R
import com.music.bitchord.data.scrobbling.LastFM
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.ui.mochy.component.AppSwitch
import com.music.bitchord.ui.mochy.component.LocalZoomOutOverlayState
import com.music.bitchord.ui.mochy.component.PopupSheetHandle
import com.music.bitchord.ui.mochy.component.SettingsGroup
import com.music.bitchord.ui.mochy.component.SettingsGroupStyle
import com.music.bitchord.ui.mochy.component.SettingsItem
import com.music.bitchord.ui.mochy.component.SubSettingsScaffold
import com.music.bitchord.ui.mochy.navigation.MochyNavigator

const val ScrobbleServiceRoute = "integrations_settings/scrobble/{site}"

fun scrobbleServiceRoute(site: ScrobbleSite) = "settings/integrations/scrobble/${site.name}"

/**
 * The services ChordWave scrobbles to. Both speak Last.fm's API, so one client serves both.
 * CW stores a blank endpoint for Last.fm (the default) and the Libre.fm URL for Libre.fm;
 * [storedEndpoint] is what goes into AppSettings, [endpoint] is the URL LastFM is configured with.
 */
enum class ScrobbleSite(
    val label: String,
    val endpoint: String,
    val storedEndpoint: String,
    private val profilePage: String,
    /** where someone without an account makes one */
    val signUpUrl: String,
) {
    LASTFM(
        label = "Last.fm",
        endpoint = LastFM.DEFAULT_API_ENDPOINT,
        storedEndpoint = "",
        profilePage = "https://www.last.fm/user/",
        signUpUrl = "https://www.last.fm/join",
    ),
    LIBREFM(
        label = "Libre.fm",
        endpoint = LastFM.LIBREFM_API_ENDPOINT,
        storedEndpoint = LastFM.LIBREFM_API_ENDPOINT,
        profilePage = "https://libre.fm/user/",
        signUpUrl = "https://libre.fm/register.php",
    );

    fun profileUrl(username: String) = profilePage + username
}

@Composable
fun ScrobbleServiceScreen(
    navigator: MochyNavigator,
    site: ScrobbleSite,
) {
    val context = LocalContext.current
    val overlay = LocalZoomOutOverlayState.current
    val sessionKey by AppSettings.lastfmSessionKey.collectAsState()
    val username by AppSettings.lastfmUsername.collectAsState()
    val endpoint by AppSettings.lastfmEndpoint.collectAsState()
    val apiKey by AppSettings.lastfmApiKey.collectAsState()
    val secret by AppSettings.lastfmSecret.collectAsState()
    val scrobbleEnabled by AppSettings.lastfmScrobbleEnabled.collectAsState()
    val nowPlaying by AppSettings.lastfmNowPlaying.collectAsState()

    val currentEndpoint = endpoint.ifBlank { LastFM.DEFAULT_API_ENDPOINT }
    val signedInHere = sessionKey.isNotBlank() && currentEndpoint == site.endpoint
    val signedInElsewhere = sessionKey.isNotBlank() && !signedInHere
    val otherSite = if (site == ScrobbleSite.LASTFM) ScrobbleSite.LIBREFM else ScrobbleSite.LASTFM
    val configured = apiKey.isNotBlank() && secret.isNotBlank()

    fun signIn() {
        var sheet: PopupSheetHandle? = null
        sheet = overlay.showWithHandle {
            ScrobbleSignInContent(
                site = site,
                onSignedIn = { name ->
                    sheet?.dismiss()
                    Toast.makeText(context, context.getString(R.string.scrobble_signed_in, site.label, name), Toast.LENGTH_LONG).show()
                },
            )
        }
    }

    fun signOut() {
        AppSettings.setLastfmSessionKey("")
        AppSettings.setLastfmUsername("")
        AppSettings.setLastfmScrobbleEnabled(false)
        AppSettings.setLastfmEnabled(false)
        LastFM.sessionKey = null
    }

    SubSettingsScaffold(
        title = site.label,
        onBack = { navigator.popBackStack() },
        backLabel = stringResource(R.string.integrations),
    ) {
        IntegrationPageSubtitle(stringResource(R.string.scrobble_page_subtitle))
        ScrobbleAccountGroup(
            site = site,
            username = username.takeIf { signedInHere },
            signedInElsewhereTo = otherSite.takeIf { signedInElsewhere },
            configured = configured,
            onSignIn = ::signIn,
            onSignOut = ::signOut,
        )
        Spacer(Modifier.height(16.dp))

        // shared by both services, so it reads the same on either page
        val nowPlayingTitle = stringResource(R.string.scrobble_now_playing)
        val sendLikesTitle = stringResource(R.string.scrobble_send_likes)
        SettingsGroup(
            title = stringResource(R.string.scrobble_while_listening),
            style = SettingsGroupStyle.Grouped,
            items = listOf(
                SettingsItem(
                    title = { Text(nowPlayingTitle) },
                    highlightKey = "Now Playing",
                    searchText = nowPlayingTitle,
                    descriptionText = stringResource(R.string.scrobble_now_playing_desc),
                    icon = painterResource(R.drawable.music_note),
                    iconShape = CircleShape,
                    enabled = signedInHere && scrobbleEnabled,
                    onClick = { AppSettings.setLastfmNowPlaying(!nowPlaying) },
                    trailingContent = { AppSwitch(checked = nowPlaying, onCheckedChange = AppSettings::setLastfmNowPlaying) },
                ),
                SettingsItem(
                    title = { Text(sendLikesTitle) },
                    highlightKey = "Send likes",
                    searchText = sendLikesTitle,
                    descriptionText = stringResource(R.string.scrobble_send_likes_na),
                    icon = painterResource(R.drawable.favorite),
                    iconShape = CircleShape,
                    enabled = false,
                    onClick = null,
                ),
            ),
        )

        Spacer(Modifier.height(140.dp))
    }
}

/** sign in, or once signed in: scrobbling on or paused, the profile, and sign out */
@Composable
private fun ScrobbleAccountGroup(
    site: ScrobbleSite,
    username: String?,
    signedInElsewhereTo: ScrobbleSite?,
    configured: Boolean,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
) {
    val scrobbleEnabled by AppSettings.lastfmScrobbleEnabled.collectAsState()
    val uriHandler = LocalUriHandler.current
    val items = if (username == null) {
        listOfNotNull(
            signedInElsewhereTo?.let { other ->
                SettingsItem(
                    title = { Text(stringResource(R.string.scrobble_signed_in_to_other, other.label)) },
                    descriptionText = stringResource(R.string.scrobble_sign_in_replaces, site.label, other.label),
                    icon = painterResource(R.drawable.music_history),
                    iconShape = CircleShape,
                    onClick = null,
                )
            },
            SettingsItem(
                title = { Text(stringResource(R.string.scrobble_sign_in_title, site.label)) },
                highlightKey = site.label,
                descriptionText = stringResource(
                    if (configured) R.string.scrobble_sign_in_desc else R.string.scrobble_not_set_up,
                ),
                icon = painterResource(R.drawable.login),
                iconShape = CircleShape,
                enabled = configured,
                onClick = onSignIn,
            ),
        )
    } else {
        listOf(
            SettingsItem(
                title = { Text(stringResource(R.string.scrobble_to, site.label)) },
                highlightKey = site.label,
                descriptionText = stringResource(
                    if (scrobbleEnabled) R.string.scrobble_signed_in_as else R.string.scrobble_paused_signed_in_as,
                    username,
                ),
                icon = painterResource(R.drawable.music_history),
                iconShape = CircleShape,
                onClick = {
                    if (scrobbleEnabled) {
                        AppSettings.setLastfmScrobbleEnabled(false)
                    } else {
                        AppSettings.setLastfmEnabled(true)
                        AppSettings.setLastfmScrobbleEnabled(true)
                    }
                },
                trailingContent = {
                    AppSwitch(
                        checked = scrobbleEnabled,
                        onCheckedChange = { on ->
                            if (on) {
                                AppSettings.setLastfmEnabled(true)
                                AppSettings.setLastfmScrobbleEnabled(true)
                            } else {
                                AppSettings.setLastfmScrobbleEnabled(false)
                            }
                        },
                    )
                },
            ),
            SettingsItem(
                title = { Text(stringResource(R.string.scrobble_open_profile)) },
                descriptionText = stringResource(R.string.scrobble_open_profile_desc, site.label),
                icon = painterResource(R.drawable.web_link),
                iconShape = CircleShape,
                onClick = { uriHandler.openUri(site.profileUrl(username)) },
            ),
            SettingsItem(
                title = { Text(stringResource(R.string.scrobble_sign_out)) },
                descriptionText = stringResource(R.string.scrobble_sign_out_desc, site.label),
                icon = painterResource(R.drawable.logout),
                iconShape = CircleShape,
                onClick = onSignOut,
            ),
        )
    }
    SettingsGroup(title = stringResource(R.string.scrobble_account), style = SettingsGroupStyle.Grouped, items = items)
}
