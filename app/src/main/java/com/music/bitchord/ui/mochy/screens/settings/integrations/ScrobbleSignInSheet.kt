// the sheet for signing in to last.fm or libre.fm, where the sign-in form lives
// Ported from Mochy Music (GPL-3.0). No ViewModel: the form calls LastFM.getMobileSession
// directly (the same mobile-session endpoint CW's classic settings use), then stores the
// session in AppSettings. If sign-in fails after LastFM was reconfigured for this site, the
// runtime client is restored to the stored AppSettings config so a failed attempt cannot
// break the existing account.

package com.music.bitchord.ui.mochy.screens.settings.integrations

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.music.bitchord.R
import com.music.bitchord.data.scrobbling.LastFM
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.ui.mochy.component.PopupSheetHandleHeight
import com.music.bitchord.ui.mochy.component.SheetLabel
import com.music.bitchord.ui.mochy.component.SheetPillButton
import com.music.bitchord.ui.mochy.component.SheetTextField
import com.music.bitchord.ui.mochy.screens.search.SearchColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Restores the runtime LastFM client to whatever AppSettings currently stores, undoing a
 * reconfigure that a failed sign-in attempt may have left behind.
 */
private fun restoreRuntimeConfig() {
    val storedEndpoint = AppSettings.lastfmEndpoint.value.ifBlank { LastFM.DEFAULT_API_ENDPOINT }
    LastFM.configure(
        endpoint = storedEndpoint,
        apiKey = AppSettings.lastfmApiKey.value,
        secret = AppSettings.lastfmSecret.value,
        sessionKey = AppSettings.lastfmSessionKey.value.ifBlank { null },
    )
}

/** the sign-in sheet's form, laid out over the sheet's own scrim so it scrolls like one */
@Composable
fun ScrobbleSignInContent(
    site: ScrobbleSite,
    onSignedIn: (username: String) -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // CW's SheetTextField takes a plain String, not Mochy's TextFieldValue
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var problem by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun submit() {
        if (busy) return
        val user = username.trim()
        val pass = password
        if (user.isEmpty() || pass.isEmpty()) return
        busy = true
        problem = null
        scope.launch {
            try {
                LastFM.configure(
                    endpoint = site.endpoint,
                    apiKey = AppSettings.lastfmApiKey.value.trim(),
                    secret = AppSettings.lastfmSecret.value.trim(),
                    sessionKey = null,
                )
                LastFM.getMobileSession(user, pass)
                    .onSuccess { auth ->
                        AppSettings.setLastfmSessionKey(auth.session.key)
                        AppSettings.setLastfmUsername(auth.session.name)
                        AppSettings.setLastfmEndpoint(site.storedEndpoint)
                        AppSettings.setLastfmEnabled(true)
                        AppSettings.setLastfmScrobbleEnabled(true)
                        LastFM.sessionKey = auth.session.key
                        onSignedIn(auth.session.name)
                    }
                    .onFailure { error ->
                        Timber.w(error, "Scrobble sign-in failed for ${site.label}")
                        restoreRuntimeConfig()
                        problem = when (error) {
                            is LastFM.LastFmException ->
                                if (error.code == 4) {
                                    context.getString(R.string.scrobble_sign_in_wrong)
                                } else {
                                    context.getString(R.string.scrobble_sign_in_busy)
                                }

                            else -> context.getString(R.string.scrobble_sign_in_offline)
                        }
                    }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Timber.w(error, "Scrobble sign-in failed for ${site.label}")
                restoreRuntimeConfig()
                problem = context.getString(R.string.scrobble_sign_in_offline)
            } finally {
                busy = false
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .padding(top = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.Top,
        ) {
            // the account name from the settings page, again at the top of its own sheet
            Text(
                text = stringResource(R.string.scrobble_sign_in_title, site.label),
                color = Color.White,
                style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
            )
            Text(
                text = stringResource(R.string.scrobble_sign_in_body, site.label),
                color = SearchColors.Secondary,
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 6.dp),
            )

            // fields sit further down so the keyboard has room to rise under them
            Spacer(Modifier.height(40.dp))
            SheetLabel(stringResource(R.string.username))
            SheetTextField(
                value = username,
                onValueChange = { username = it },
                placeholder = stringResource(R.string.scrobble_username_hint, site.label),
            )
            Spacer(Modifier.height(12.dp))
            SheetLabel(stringResource(R.string.password))
            SheetTextField(
                value = password,
                onValueChange = { password = it },
                placeholder = stringResource(R.string.scrobble_password_hint),
                visualTransformation = PasswordVisualTransformation(),
            )

            // a wrong password or a flaky connection, in red under the fields
            problem?.let { error ->
                Text(
                    text = error,
                    color = Color.Red,
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }

            Spacer(Modifier.weight(1f))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SheetPillButton(
                    text = stringResource(if (busy) R.string.scrobble_signing_in else R.string.scrobble_sign_in_button),
                    enabled = !busy && username.isNotBlank() && password.isNotBlank(),
                    onClick = ::submit,
                )
                // a long way down, near the bottom edge, under the sign-in button
                Text(
                    text = stringResource(R.string.scrobble_sign_up, site.label),
                    color = SearchColors.Secondary,
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .clickable { uriHandler.openUri(site.signUpUrl) }
                        .padding(bottom = 4.dp),
                )
            }
            // room for the sheet's own handle under the buttons
            Spacer(Modifier.height(PopupSheetHandleHeight))
        }
    }
}
