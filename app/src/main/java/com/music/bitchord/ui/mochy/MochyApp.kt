package com.music.bitchord.ui.mochy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.music.bitchord.R
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.UiMode
import com.music.bitchord.playback.rememberMediaController
import com.music.bitchord.ui.mochy.constants.GridItemSize
import com.music.bitchord.ui.mochy.playback.LocalPlayerConnection
import com.music.bitchord.ui.mochy.playback.MochyPlayerConnection
import com.music.bitchord.ui.mochy.theme.MochyTheme
import com.music.bitchord.ui.mochy.utils.DataSaver
import com.music.bitchord.ui.mochy.utils.PreferencesCache

/**
 * The Modern UI host: the Mochy-style interface, branched to from
 * MainActivity when [UiMode.MODERN] is active. The classic UI
 * (BitChordApp) is untouched and stays available through the Settings
 * toggle; this host grows screen by screen as the clone lands.
 *
 * Phase 1.5: provides the player connection + the app-level
 * CompositionLocals the ported UI reads, and starts the Mochy-mode
 * preferences cache + data saver. Phase 2 replaces the placeholder with
 * the ported Home / Search / Library tabs.
 */
@Composable
fun MochyApp(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Mochy-mode prefs (DataStore "settings") + data saver, once per host
    // lifetime. CW's own settings stay in AppSettings (SharedPreferences).
    LaunchedEffect(Unit) {
        PreferencesCache.start(context, scope)
        DataSaver.attach(context)
    }

    // Player connection: bound to CW's MediaController, provided to the
    // whole Modern tree. Disposed when the host leaves composition.
    val controller = rememberMediaController()
    val connection = remember { MochyPlayerConnection(context) }
    DisposableEffect(connection, controller) {
        connection.bind(controller)
        onDispose { connection.bind(null) }
    }
    DisposableEffect(connection) {
        onDispose { connection.dispose() }
    }

    val detailAccentColor = remember { mutableStateOf<androidx.compose.ui.graphics.Color?>(null) }
    val appContentObscured = remember { mutableStateOf(false) }

    MochyTheme {
        CompositionLocalProvider(
            LocalPlayerConnection provides connection,
            LocalGridItemSize provides GridItemSize.BIG,
            LocalCropAlbumArt provides false,
            LocalAppContentObscured provides appContentObscured,
            LocalDetailAccentColor provides detailAccentColor,
        ) {
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = stringResource(R.string.ui_mode_modern),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.ui_mode_modern_coming_soon),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(24.dp))
                Button(onClick = { AppSettings.setUiMode(UiMode.CLASSIC) }) {
                    Text(text = stringResource(R.string.ui_mode_back_to_classic))
                }
            }
        }
    }
}
