package com.music.bitchord.ui.mochy

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.music.bitchord.ui.mochy.component.AppNavigationBar
import com.music.bitchord.ui.mochy.component.GlassState
import com.music.bitchord.ui.mochy.component.LocalMenuState
import com.music.bitchord.ui.mochy.component.PopupSheetHost
import com.music.bitchord.ui.mochy.component.PopupSheetState
import com.music.bitchord.ui.mochy.component.owningTab
import com.music.bitchord.ui.mochy.component.onTabTapped
import com.music.bitchord.ui.mochy.constants.GridItemSize
import com.music.bitchord.ui.mochy.navigation.MochyNavigator
import com.music.bitchord.ui.mochy.playback.LocalPlayerConnection
import com.music.bitchord.ui.mochy.playback.MochyPlayerConnection
import com.music.bitchord.ui.mochy.screens.HomeScreen
import com.music.bitchord.ui.mochy.screens.Screens
import com.music.bitchord.ui.mochy.theme.MochyTheme
import com.music.bitchord.ui.mochy.utils.DataSaver
import com.music.bitchord.ui.mochy.utils.PreferencesCache

/**
 * The Modern UI host: the Mochy-style interface, branched to from
 * MainActivity when [UiMode.MODERN] is active. The classic UI
 * (BitChordApp) is untouched and stays available through the Settings
 * toggle; this host grows screen by screen as the clone lands.
 *
 * Phase 2: the real tab host — MochyNavigator state drives Home (ported)
 * + Search/Library/Settings placeholders, the ported AppNavigationBar,
 * the menu sheet host, and detail-route placeholders. The mini-player
 * joins in Phase 3 (LocalPlayerBottomSheetState stays at its null
 * default until then; Home null-checks it).
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

    val navigator = remember { MochyNavigator() }
    val glassState = remember { GlassState() }
    val menuState = remember { PopupSheetState() }
    val snackbarHostState = remember { SnackbarHostState() }
    val detailAccentColor = remember { mutableStateOf<androidx.compose.ui.graphics.Color?>(null) }
    val appContentObscured = remember { mutableStateOf(false) }

    MochyTheme {
        CompositionLocalProvider(
            LocalPlayerConnection provides connection,
            LocalGridItemSize provides GridItemSize.BIG,
            LocalCropAlbumArt provides false,
            LocalAppContentObscured provides appContentObscured,
            LocalDetailAccentColor provides detailAccentColor,
            LocalGlassState provides glassState,
            LocalMenuState provides menuState,
            // Bottom insets the tab screens pad for: the floating nav bar
            // (64dp + 2x8dp padding = 80dp) over the gesture bar (~16dp).
            // A flat 96dp floor until the mini-player exists (Phase 3),
            // when its height joins here. Tune on-device in Phase 6.
            LocalPlayerAwareWindowInsets provides
                WindowInsets.navigationBars.union(WindowInsets(bottom = 96.dp)),
        ) {
            // Menus (and later info pages) render through this host; the
            // app zooms out and dims behind the active sheet.
            PopupSheetHost(states = listOf(menuState)) {
                MochyContent(
                    navigator = navigator,
                    snackbarHostState = snackbarHostState,
                    modifier = modifier,
                )
            }
        }
    }
}

@Composable
private fun MochyContent(
    navigator: MochyNavigator,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    val currentTab by navigator.currentTab.collectAsState()
    val detailStack by navigator.detailStack.collectAsState()
    val currentDetail = detailStack.lastOrNull()

    BackHandler(enabled = detailStack.isNotEmpty()) {
        navigator.popBackStack()
    }

    val selectedTabRoute = owningTab(currentTab, detailStack, Screens.MainScreens)

    Box(modifier = modifier.fillMaxSize()) {
        when {
            currentDetail != null -> DetailPlaceholder(
                route = currentDetail,
                onBack = { navigator.popBackStack() },
            )
            currentTab == 0 -> HomeScreen(
                navigator = navigator,
                snackbarHostState = snackbarHostState,
            )
            currentTab == 1 -> TabPlaceholder(titleId = Screens.Search.titleId)
            currentTab == 2 -> TabPlaceholder(titleId = Screens.Library.titleId)
            else -> SettingsPlaceholder(
                onBackToClassic = { AppSettings.setUiMode(UiMode.CLASSIC) },
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 104.dp),
        )

        AppNavigationBar(
            navigationItems = Screens.MainScreens,
            currentRoute = currentDetail ?: Screens.MainScreens[currentTab].route,
            selectedTabRoute = selectedTabRoute,
            onItemClick = { screen, isSelected ->
                navigator.onTabTapped(screen, isSelected)
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding(),
            // Phase 2 Search: a second quick tap on Search goes straight
            // into typing once the Search screen lands.
            onSearchDoubleTap = null,
        )
    }
}

/** A tab whose screen is not ported yet (Search, Library). */
@Composable
private fun TabPlaceholder(titleId: Int) {
    PlaceholderColumn(title = stringResource(titleId))
}

/** The Settings tab until the Settings screen lands — keeps the escape
 *  hatch to Classic reachable from Modern mode. */
@Composable
private fun SettingsPlaceholder(onBackToClassic: () -> Unit) {
    PlaceholderColumn(
        title = stringResource(R.string.settings),
        onBackToClassic = onBackToClassic,
    )
}

/** A detail route whose screen is not ported yet (album/artist/playlist/
 *  section_detail/...). Shows the route so a tap that lands here is
 *  diagnosable on-device, and offers the way back. */
@Composable
private fun DetailPlaceholder(route: String, onBack: () -> Unit) {
    PlaceholderColumn(
        title = route,
        onBack = onBack,
    )
}

@Composable
private fun PlaceholderColumn(
    title: String,
    onBack: (() -> Unit)? = null,
    onBackToClassic: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = title,
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
        if (onBack != null) {
            Spacer(Modifier.height(24.dp))
            Button(onClick = onBack) {
                Text(text = stringResource(R.string.back))
            }
        }
        if (onBackToClassic != null) {
            Spacer(Modifier.height(24.dp))
            Button(onClick = onBackToClassic) {
                Text(text = stringResource(R.string.ui_mode_back_to_classic))
            }
        }
    }
}
