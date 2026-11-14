// Ported from Mochy Music (GPL-3.0):
// ui/screens/settings/importsync/YouTubeLoginScreen.kt
//
// REWIRED TO CHORDWAVE'S AUTH — this is the one screen in the set whose
// engine is replaced, not ported, because CW already has a complete,
// multi-account YouTube sign-in and a second cookie store would corrupt it:
// - Mochy's own WebView + SAPISID check + DataStore save (InnerTubeCookieKey,
//   VisitorDataKey, DataSyncIdKey, Account*Key) are GONE. No cookie is read,
//   stored, or written anywhere in this file.
// - The WebView is CW's own auth.YtMusicLoginScreen (SIGN_IN mode) and the
//   captured session is saved by MainViewModel.onWebSession — the exact path
//   the Classic host uses (MainActivity web-session overlay). That path
//   validates the cookie/profile pair, persists it in the encrypted AuthStore
//   (multi-account registry), adopts the visitorData/dataSyncId/pageId scope
//   into Innertube, and reloads home/library/account/playlists.
// - Deliberately NOT auto-captured on reaching music.youtube.com: CW found
//   that race creates a fake "Personal" profile on multi-channel accounts
//   (see YtMusicLoginScreen.kt). The user confirms with "Use this profile"
//   once the page is ready, exactly like Classic.
// - onSignedIn takes no account info: Mochy's AccountInfo type has no CW
//   equivalent, and the signed-in name is already in MainViewModel.account
//   after onWebSession reloads it. The toast is the generic "Signed in to
//   YouTube".
// - No loading bar: CW's YtMusicLoginScreen exposes no page-loading signal
//   (only onPageReady). Mochy's onUrlChange/onLoadingChange params are
//   dropped for the same reason; see NEEDS.md.
// - Back exits the screen: CW's composable does not expose the WebView, so
//   there is no in-page back navigation — same as the Classic host.
package com.music.bitchord.ui.mochy.screens.settings.importsync

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.music.bitchord.R
import com.music.bitchord.auth.WebSessionMode
import com.music.bitchord.auth.YtMusicLoginScreen
import com.music.bitchord.ui.MainViewModel
import com.music.bitchord.ui.mochy.LocalPlayerAwareWindowInsets
import com.music.bitchord.ui.mochy.navigation.MochyNavigator
import com.music.bitchord.ui.mochy.utils.stableStatusBarsPadding

/** the full-screen sign-in page used from the sync settings */
@Composable
fun YouTubeLoginScreen(
    navigator: MochyNavigator,
    mainViewModel: MainViewModel = viewModel(),
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .stableStatusBarsPadding()
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Bottom)),
    ) {
        WebLoginHeader(
            title = "Sign in to YouTube",
            subtitle = "Google's own sign-in page. ChordWave never sees your password.",
            onBack = { navigator.popBackStack() },
        )
        YouTubeLoginWebContent(
            onSignedIn = {
                Toast.makeText(context, "Signed in to YouTube", Toast.LENGTH_SHORT).show()
                navigator.popBackStack()
            },
            onExit = { navigator.popBackStack() },
            mainViewModel = mainViewModel,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
    }
}

/**
 * Google sign-in in a webview without chrome. The session is captured and
 * saved through ChordWave's own sign-in path (auth.YtMusicLoginScreen +
 * MainViewModel.onWebSession); nothing here touches cookies or storage.
 */
@Composable
fun YouTubeLoginWebContent(
    onSignedIn: () -> Unit,
    onExit: () -> Unit,
    mainViewModel: MainViewModel = viewModel(),
    modifier: Modifier = Modifier,
) {
    var pageReady by remember { mutableStateOf(false) }
    var captureRequest by remember { mutableIntStateOf(0) }
    var confirmingProfile by remember { mutableStateOf(false) }
    var captureFailed by remember { mutableStateOf(false) }

    BackHandler(onBack = onExit)

    Box(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (pageReady) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = if (captureFailed) {
                            stringResource(R.string.profile_unavailable)
                        } else {
                            stringResource(R.string.switch_profile_hint)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.6f),
                        maxLines = 2,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    TextButton(
                        onClick = {
                            captureFailed = false
                            confirmingProfile = true
                            captureRequest++
                        },
                        enabled = !confirmingProfile,
                    ) {
                        Text(
                            if (confirmingProfile) stringResource(R.string.checking)
                            else stringResource(R.string.use_this_profile),
                            color = Color.White,
                        )
                    }
                }
            }
            YtMusicLoginScreen(
                mode = WebSessionMode.SIGN_IN,
                captureRequest = captureRequest,
                onPageReady = { pageReady = it },
                onCaptureUnavailable = {
                    confirmingProfile = false
                    captureFailed = true
                },
                onCaptured = { session ->
                    mainViewModel.onWebSession(session, WebSessionMode.SIGN_IN) { accepted ->
                        confirmingProfile = false
                        if (accepted) {
                            onSignedIn()
                        } else {
                            captureFailed = true
                        }
                    }
                },
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
        }
    }
}
