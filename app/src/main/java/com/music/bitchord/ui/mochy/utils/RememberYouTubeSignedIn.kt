// Ported from Mochy Music (GPL-3.0): ui/screens/settings/importsync/ImportSyncComponents.kt
// (rememberYouTubeSignedIn only).
// Mochy read the InnerTube cookie out of DataStore and looked for SAPISID;
// ChordWave keeps the same answer in one process-wide flow (auth.SignedIn,
// owned by MainViewModel, seeded from AuthStore), so the port reads that
// instead of the cookie. Same meaning: a YouTube account is signed in.
// The artist page uses it to decide whether to show the Subscribe pill.

package com.music.bitchord.ui.mochy.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import com.music.bitchord.auth.SignedIn

@Composable
fun rememberYouTubeSignedIn(): Boolean {
    val context = LocalContext.current
    val signedIn by SignedIn.state(context).collectAsState()
    return signedIn
}
