package com.music.bitchord.ui.mochy

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
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
import com.music.bitchord.ui.mochy.constants.PureBlackKey
import com.music.bitchord.ui.mochy.importer.ImportSource
import com.music.bitchord.ui.mochy.navigation.MochyNavigator
import com.music.bitchord.ui.mochy.playback.LocalPlayerConnection
import com.music.bitchord.ui.mochy.playback.MochyPlayerConnection
import com.music.bitchord.ui.mochy.screens.AlbumScreen
import com.music.bitchord.ui.mochy.screens.BrowseScreen
import com.music.bitchord.ui.mochy.screens.HistoryScreen
import com.music.bitchord.ui.mochy.screens.HomeScreen
import com.music.bitchord.ui.mochy.screens.Screens
import com.music.bitchord.ui.mochy.screens.SectionDetailScreen
import com.music.bitchord.ui.mochy.screens.YouTubeBrowseScreen
import com.music.bitchord.ui.mochy.screens.artist.ArtistAlbumsScreen
import com.music.bitchord.ui.mochy.screens.artist.ArtistItemsScreen
import com.music.bitchord.ui.mochy.screens.artist.ArtistScreen
import com.music.bitchord.ui.mochy.screens.artist.ArtistSimilarScreen
import com.music.bitchord.ui.mochy.screens.artist.ArtistSongsScreen
import com.music.bitchord.ui.mochy.screens.library.LibraryAlbumsScreen
import com.music.bitchord.ui.mochy.screens.library.LibraryArtistsScreen
import com.music.bitchord.ui.mochy.screens.library.LibraryHomeScreen
import com.music.bitchord.ui.mochy.screens.library.LibraryImportedScreen
import com.music.bitchord.ui.mochy.screens.library.LibraryPlaylistsScreen
import com.music.bitchord.ui.mochy.screens.library.LibraryRecentlyAddedScreen
import com.music.bitchord.ui.mochy.screens.library.LibrarySongsScreen
import com.music.bitchord.ui.mochy.screens.playlist.AutoPlaylistScreen
import com.music.bitchord.ui.mochy.screens.playlist.CachePlaylistScreen
import com.music.bitchord.ui.mochy.screens.playlist.LocalPlaylistScreen
import com.music.bitchord.ui.mochy.screens.playlist.OnlinePlaylistScreen
import com.music.bitchord.ui.mochy.screens.playlist.TopPlaylistScreen
import com.music.bitchord.ui.mochy.screens.search.ExploreScreen
import com.music.bitchord.ui.mochy.screens.search.GenreScreen
import com.music.bitchord.ui.mochy.screens.search.OnlineSearchResult
import com.music.bitchord.ui.mochy.screens.search.SearchScreen
import com.music.bitchord.ui.mochy.screens.search.SearchSidePane
import com.music.bitchord.ui.mochy.screens.search.showsSearchPane
import com.music.bitchord.ui.mochy.theme.MochyTheme
import com.music.bitchord.ui.mochy.utils.DataSaver
import com.music.bitchord.ui.mochy.utils.PreferencesCache
import com.music.bitchord.ui.mochy.utils.isLandscapeLayout
import com.music.bitchord.ui.mochy.utils.rememberPreference
import com.music.bitchord.ui.mochy.viewmodels.HomeViewModel
import com.music.bitchord.ui.mochy.viewmodels.LibraryShelfKind

/**
 * The Modern UI host: the Mochy-style interface, branched to from
 * MainActivity when [UiMode.MODERN] is active. The classic UI
 * (BitChordApp) is untouched and stays available through the Settings
 * toggle; this host grows screen by screen as the clone lands.
 *
 * Phase 2: the real tab host — MochyNavigator state drives Home, Search
 * and Library (all ported) + the Settings placeholder, the ported
 * AppNavigationBar, the menu sheet host, and the full detail routing
 * (album/artist/playlist/library/history/browse). The mini-player
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
    // Search's page background (pure black vs dark grey) — Mochy's host
    // reads this once and passes it down (default false).
    val pureBlack by rememberPreference(PureBlackKey, defaultValue = false)
    // Landscape search pane state (Mochy's MainActivity holds this): the
    // pane's field text. The pane owns typing; committing navigates to
    // search/{query}, which the results screen reads from its route.
    val searchPaneQuery = rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(""))
    }
    val currentRoute = currentDetail ?: Screens.MainScreens[currentTab].route

    BackHandler(enabled = detailStack.isNotEmpty()) {
        navigator.popBackStack()
    }

    val selectedTabRoute = owningTab(currentTab, detailStack, Screens.MainScreens)

    Box(modifier = modifier.fillMaxSize()) {
        Row(Modifier.fillMaxSize()) {
            // On its side the search field + history stay at the left
            // while the categories or results change beside it (Mochy
            // MainActivity's Row). The pane sizes itself.
            if (isLandscapeLayout() && showsSearchPane(currentRoute)) {
                SearchSidePane(
                    navigator = navigator,
                    currentRoute = currentRoute,
                    query = searchPaneQuery,
                    onQueryChange = { searchPaneQuery.value = it },
                    pureBlack = pureBlack,
                )
            }
            Box(Modifier.weight(1f).fillMaxHeight()) {
                when {
                    currentDetail != null -> DetailContent(
                        route = currentDetail,
                        navigator = navigator,
                        pureBlack = pureBlack,
                        onBack = { navigator.popBackStack() },
                    )
                    currentTab == 0 -> HomeScreen(
                        navigator = navigator,
                        snackbarHostState = snackbarHostState,
                    )
                    currentTab == 1 -> SearchScreen(
                        navigator = navigator,
                        pureBlack = pureBlack,
                    )
                    currentTab == 2 -> LibraryHomeScreen(
                        navigator = navigator,
                        pureBlack = pureBlack,
                    )
                    else -> SettingsPlaceholder(
                        onBackToClassic = { AppSettings.setUiMode(UiMode.CLASSIC) },
                    )
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 104.dp),
        )

        AppNavigationBar(
            navigationItems = Screens.MainScreens,
            currentRoute = currentRoute,
            selectedTabRoute = selectedTabRoute,
            onItemClick = { screen, isSelected ->
                navigator.onTabTapped(screen, isSelected)
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding(),
            // Phase 2 Search: a second quick tap on Search goes straight
            // into typing once the Search screen lands.
            onSearchDoubleTap = navigator::requestSearchFocus,
        )
    }
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

/** A pushed detail route: the ported detail screens, falling back to the
 *  placeholder for routes whose screen hasn't landed yet (import_progress
 *  until the settings port; unknown routes stay diagnosable on-device). */
@Composable
private fun DetailContent(
    route: String,
    navigator: MochyNavigator,
    pureBlack: Boolean,
    onBack: () -> Unit,
) {
    // One scroll behaviour per detail (only one detail is composed at a
    // time); the detail screens take it as a param, some only to satisfy
    // Mochy's signature (UNUSED_PARAMETER).
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    when {
        route.startsWith("search/") -> OnlineSearchResult(
            navigator = navigator,
            initialQuery = Uri.decode(route.removePrefix("search/")),
            pureBlack = pureBlack,
        )
        route.startsWith("genre/") -> {
            val rest = route.removePrefix("genre/")
            val browseId = rest.substringBefore("?")
            val query = rest.substringAfter("?", "")
            // New format carries the title (a ChordWave addition — CW's
            // browse response has no page title, so the tile passes it);
            // an old-format route without title still parses, header blank.
            val title = query.substringAfter("title=", "").substringBefore("&params=")
                .takeIf { it.isNotEmpty() }?.let { Uri.decode(it) }
            val params = when {
                query.contains("&params=") -> query.substringAfter("&params=", "")
                query.startsWith("params=") -> query.removePrefix("params=")
                else -> ""
            }.takeIf { it.isNotEmpty() && it != "null" }
            GenreScreen(
                navigator = navigator,
                browseId = browseId,
                params = params,
                title = title,
                pureBlack = pureBlack,
            )
        }
        // No entry point navigates here yet (Mochy has an Explore tab;
        // Modern has none) — the route is wired so a future entry (a Home
        // section or Search tile) only needs to navigate to "explore".
        route == "explore" -> ExploreScreen(navigator = navigator)
        route.startsWith("library/imported/") -> {
            val rest = route.removePrefix("library/imported/")
            val source = ImportSource.fromKey(rest.substringBefore("/"))
            when (rest.substringAfter("/", "")) {
                "albums" -> LibraryAlbumsScreen(
                    navigator = navigator,
                    pureBlack = pureBlack,
                    source = source,
                )
                "playlists" -> LibraryPlaylistsScreen(
                    navigator = navigator,
                    pureBlack = pureBlack,
                    source = source,
                )
                "" -> LibraryImportedScreen(
                    navigator = navigator,
                    pureBlack = pureBlack,
                    importSource = source,
                )
                else -> DetailPlaceholder(route = route, onBack = onBack)
            }
        }
        route.startsWith("library/") -> {
            val segments = route.removePrefix("library/").split("/")
            // library/{local|cached}/{page} vs library/{page}
            val shelf = segments.firstOrNull()?.let { key ->
                LibraryShelfKind.entries.firstOrNull { it.key == key }
            }
            val page = if (shelf != null) segments.getOrNull(1) else segments.firstOrNull()
            when (page) {
                "songs" -> LibrarySongsScreen(
                    navigator = navigator,
                    pureBlack = pureBlack,
                )
                "playlists" -> LibraryPlaylistsScreen(
                    navigator = navigator,
                    pureBlack = pureBlack,
                    shelf = shelf,
                )
                "albums" -> LibraryAlbumsScreen(
                    navigator = navigator,
                    pureBlack = pureBlack,
                    shelf = shelf,
                )
                "artists" -> LibraryArtistsScreen(
                    navigator = navigator,
                    pureBlack = pureBlack,
                    shelf = shelf,
                )
                "added" -> LibraryRecentlyAddedScreen(
                    navigator = navigator,
                    pureBlack = pureBlack,
                    shelf = shelf,
                )
                else -> DetailPlaceholder(route = route, onBack = onBack)
            }
        }
        route.startsWith("album/") -> AlbumScreen(
            navigator = navigator,
            scrollBehavior = scrollBehavior,
            albumId = Uri.decode(route.removePrefix("album/")),
        )
        route.startsWith("artist/") -> {
            val rest = route.removePrefix("artist/")
            val artistId = rest.substringBefore("/")
            when (val sub = rest.substringAfter("/", "")) {
                "" -> ArtistScreen(
                    navigator = navigator,
                    scrollBehavior = scrollBehavior,
                    artistId = artistId,
                )
                "songs" -> ArtistSongsScreen(
                    navigator = navigator,
                    scrollBehavior = scrollBehavior,
                    artistId = artistId,
                )
                "albums" -> ArtistAlbumsScreen(
                    navigator = navigator,
                    scrollBehavior = scrollBehavior,
                    artistId = artistId,
                )
                "similar" -> ArtistSimilarScreen(
                    navigator = navigator,
                    artistId = artistId,
                )
                else -> if (sub.startsWith("items")) {
                    // Mochy's route literally contains a second '?':
                    // artist/{id}/items?browseId={bid}?params={params}
                    // — and may pass the string "null" for a missing param.
                    val query = sub.substringAfter("?", "")
                    val browseId = query.substringAfter("browseId=", "")
                        .substringBefore("?params=")
                        .takeIf { it.isNotEmpty() && it != "null" }
                    val params = query.substringAfter("?params=", "")
                        .takeIf { it.isNotEmpty() && it != "null" }
                    ArtistItemsScreen(
                        navigator = navigator,
                        scrollBehavior = scrollBehavior,
                        artistId = artistId,
                        browseId = browseId,
                        params = params,
                    )
                } else {
                    DetailPlaceholder(route = route, onBack = onBack)
                }
            }
        }
        route.startsWith("online_playlist/") -> OnlinePlaylistScreen(
            navigator = navigator,
            scrollBehavior = scrollBehavior,
            playlistId = Uri.decode(route.removePrefix("online_playlist/")),
        )
        route.startsWith("local_playlist/") -> LocalPlaylistScreen(
            navigator = navigator,
            scrollBehavior = scrollBehavior,
            playlistId = Uri.decode(route.removePrefix("local_playlist/")),
        )
        route.startsWith("auto_playlist/") -> AutoPlaylistScreen(
            navigator = navigator,
            scrollBehavior = scrollBehavior,
            playlist = Uri.decode(route.removePrefix("auto_playlist/")),
        )
        // No entry point navigates here yet; the route is wired so a
        // future entry only needs to navigate to it. The VM polls the
        // player cache on its own (no route arg is read).
        route.startsWith("cache_playlist/") -> CachePlaylistScreen(
            navigator = navigator,
            scrollBehavior = scrollBehavior,
        )
        // No entry point navigates here yet (same as cache_playlist).
        route.startsWith("top_playlist/") -> TopPlaylistScreen(
            navigator = navigator,
            scrollBehavior = scrollBehavior,
            top = Uri.decode(route.removePrefix("top_playlist/")),
        )
        // No entry point navigates here yet (same as explore).
        route == "history" -> HistoryScreen(navigator = navigator)
        route.startsWith("section_detail/") -> {
            // The section screen reads the Home tab's VM (the same
            // Activity-scoped instance the Home tab composes with).
            val homeViewModel: HomeViewModel = hiltViewModel()
            SectionDetailScreen(
                navigator = navigator,
                sectionId = Uri.decode(route.removePrefix("section_detail/")),
                homeViewModel = homeViewModel,
            )
        }
        route.startsWith("browse/") -> BrowseScreen(
            navigator = navigator,
            scrollBehavior = scrollBehavior,
            browseId = Uri.decode(route.removePrefix("browse/")),
        )
        route.startsWith("youtube_browse/") -> {
            val rest = route.removePrefix("youtube_browse/")
            val browseId = rest.substringBefore("?")
            val params = rest.substringAfter("?params=", "")
                .takeIf { it.isNotEmpty() && it != "null" }
            YouTubeBrowseScreen(
                navigator = navigator,
                browseId = browseId,
                params = params,
            )
        }
        // import_progress leads to the settings import-sync screen (not
        // ported yet); the Library banner navigates here. Placeholder
        // until the settings port lands.
        route == "import_progress" -> DetailPlaceholder(route = route, onBack = onBack)
        else -> DetailPlaceholder(route = route, onBack = onBack)
    }
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
