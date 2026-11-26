package com.music.bitchord.ui.mochy

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
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
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.music.bitchord.R
import com.music.bitchord.data.lyrics.LyricsSource
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.playback.rememberMediaController
import com.music.bitchord.ui.MainViewModel
import com.music.bitchord.ui.mochy.component.AppNavigationBar
import com.music.bitchord.ui.mochy.component.BottomSheetState
import com.music.bitchord.ui.mochy.component.LocalPlayerBottomSheetState
import com.music.bitchord.ui.mochy.component.rememberBottomSheetState
import com.music.bitchord.ui.mochy.component.GlassState
import com.music.bitchord.ui.mochy.component.LocalNavAnimatedContentScope
import com.music.bitchord.ui.mochy.component.LocalSharedTransitionScope
import com.music.bitchord.ui.mochy.component.LocalMenuState
import com.music.bitchord.ui.mochy.component.PopupSheetHost
import com.music.bitchord.ui.mochy.component.PopupSheetState
import com.music.bitchord.ui.mochy.component.owningTab
import com.music.bitchord.ui.mochy.component.onTabTapped
import com.music.bitchord.ui.mochy.constants.GridItemSize
import com.music.bitchord.ui.mochy.constants.KeepScreenOn
import com.music.bitchord.ui.mochy.constants.MiniPlayerHeight
import com.music.bitchord.ui.mochy.constants.NavigationBarHeight
import com.music.bitchord.ui.mochy.constants.PureBlackKey
import com.music.bitchord.ui.mochy.importer.ImportSource
import com.music.bitchord.ui.mochy.navigation.MochyNavigator
import com.music.bitchord.ui.mochy.playback.LocalPlayerConnection
import com.music.bitchord.ui.mochy.utils.AppMotion
import com.music.bitchord.ui.mochy.link.DeviceLink
import com.music.bitchord.ui.mochy.link.LinkPlayer
import com.music.bitchord.ui.mochy.screens.devices.ControllingPill
import com.music.bitchord.ui.mochy.screens.devices.DeviceIntegrationOpener
import com.music.bitchord.ui.mochy.playback.LyricsProviderBridge
import com.music.bitchord.ui.mochy.playback.MochyPlayerConnection
import com.music.bitchord.ui.mochy.player.BottomSheetPlayer
import com.music.bitchord.ui.mochy.player.ipod.ChordWaveIpodLibrary
import com.music.bitchord.ui.mochy.player.ipod.LocalIpodLibrarySource
import com.music.bitchord.ui.mochy.player.share.song.SongShareHost
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
import com.music.bitchord.ui.mochy.screens.library.LibraryGenreScreen
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
import com.music.bitchord.ui.mochy.theme.DefaultThemeColor
import com.music.bitchord.ui.mochy.theme.MochyTheme
import com.music.bitchord.ui.mochy.theme.rememberCoverThemeColor
import com.music.bitchord.ui.mochy.utils.DataSaver
import com.music.bitchord.ui.mochy.utils.PreferencesCache
import com.music.bitchord.ui.mochy.utils.isLandscapeLayout
import com.music.bitchord.ui.mochy.utils.rememberPreference
import com.music.bitchord.ui.mochy.viewmodels.HomeViewModel
import com.music.bitchord.ui.mochy.viewmodels.LibraryShelfKind
import androidx.datastore.preferences.core.edit
import com.music.bitchord.auth.DiscordLoginScreen
import com.music.bitchord.ui.mochy.component.LocalZoomOutOverlayState
import com.music.bitchord.ui.mochy.constants.ProfilePicUriKey
import com.music.bitchord.ui.mochy.constants.SetupCompletedKey
import com.music.bitchord.ui.mochy.constants.UsernameKey
import com.music.bitchord.ui.mochy.importer.YouTubeSignOut
import com.music.bitchord.ui.mochy.importer.account.AccountService
import com.music.bitchord.ui.mochy.launch.LaunchGate
import com.music.bitchord.ui.mochy.launch.LaunchIntroOverlay
import com.music.bitchord.ui.mochy.launch.LaunchIntroState
import com.music.bitchord.ui.mochy.launch.LocalLaunchIntro
import com.music.bitchord.ui.mochy.screens.settings.AppBehaviourSettingsScreen
import com.music.bitchord.ui.mochy.screens.settings.CipherSettingsScreen
import com.music.bitchord.ui.mochy.screens.settings.CrossmixSettingsScreen
import com.music.bitchord.ui.mochy.screens.settings.ExperimentalSettingsScreen
import com.music.bitchord.ui.mochy.screens.settings.MochySettingsScreen
import com.music.bitchord.ui.mochy.screens.settings.NowPlayingSettingsScreen
import com.music.bitchord.ui.mochy.screens.settings.PlaybackSettingsScreen
import com.music.bitchord.ui.mochy.screens.settings.PlaybackDiagnosticsScreen
import com.music.bitchord.ui.mochy.screens.settings.ProxySettingsScreen
import com.music.bitchord.ui.mochy.screens.settings.importsync.AccountImportScreen
import com.music.bitchord.ui.mochy.screens.settings.importsync.DriveBackupScreen
import com.music.bitchord.ui.mochy.screens.settings.importsync.DriveDevicesScreen
import com.music.bitchord.ui.mochy.screens.settings.importsync.ImportProgressScreen
import com.music.bitchord.ui.mochy.screens.settings.importsync.ImportProvidersScreen
import com.music.bitchord.ui.mochy.screens.settings.importsync.ImportSyncScreen
import com.music.bitchord.ui.mochy.screens.settings.importsync.TuneMyMusicImportScreen
import com.music.bitchord.ui.mochy.screens.settings.importsync.YouTubeLoginScreen
import com.music.bitchord.ui.mochy.screens.settings.importsync.YouTubeSyncScreen
import com.music.bitchord.ui.mochy.screens.settings.integrations.IntegrationsScreen
import com.music.bitchord.ui.mochy.screens.settings.integrations.ScrobbleServiceScreen
import com.music.bitchord.data.mochydata.scrobble.ScrobbleSite
import com.music.bitchord.ui.mochy.screens.settings.integrations.backup.BackupScreen
import com.music.bitchord.ui.mochy.screens.settings.integrations.spotify.SpotifyScreen
import com.music.bitchord.ui.mochy.screens.settings.integrations.discord.DiscordScreen
import com.music.bitchord.ui.mochy.screens.settings.storage.StorageScreen
import com.music.bitchord.ui.mochy.screens.audiolab.AudioFxScreen
import com.music.bitchord.ui.mochy.screens.audiolab.AudioLabScreen
import com.music.bitchord.ui.mochy.screens.audiolab.AudioPassScreen
import com.music.bitchord.ui.mochy.screens.audiolab.AudioQualityScreen
import com.music.bitchord.ui.mochy.screens.equalizer.EqualizerScreen
import com.music.bitchord.ui.mochy.screens.karaoke.KaraokeRemoteScreen
import com.music.bitchord.ui.mochy.screens.listen.ListenTogetherScreen
import com.music.bitchord.ui.mochy.screens.profile.ProfileScreen
import com.music.bitchord.ui.mochy.screens.radio.RadioScreen
import com.music.bitchord.ui.mochy.screens.replay.ReplayScreen
import com.music.bitchord.ui.mochy.screens.stats.StatsScreen
import com.music.bitchord.ui.mochy.screens.replay.ReplayStoriesHost
import com.music.bitchord.ui.mochy.screens.search.NewReleaseScreen
import com.music.bitchord.ui.mochy.screens.settings.CustomisationScreen
import com.music.bitchord.ui.mochy.screens.sources.SourcesScreen
import com.music.bitchord.ui.mochy.utils.rememberYouTubeSignedIn
import com.music.bitchord.data.listentogether.JamInviteLink
import com.music.bitchord.ui.mochy.screens.setup.ProfilePhotoStore
import com.music.bitchord.ui.mochy.screens.setup.SetupRerun
import com.music.bitchord.ui.mochy.screens.setup.SetupWizardContainer
import com.music.bitchord.ui.mochy.screens.update.UpdateDeepLink
import com.music.bitchord.ui.mochy.screens.update.UpdateNotificationOpener
import com.music.bitchord.ui.mochy.screens.update.UpdateState
import com.music.bitchord.ui.mochy.screens.update.rememberUpdateState
import com.music.bitchord.ui.mochy.screens.update.showUpdateSheet
import com.music.bitchord.ui.mochy.utils.dataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * The Modern UI host: the Mochy-style interface, branched to from
 * MainActivity when [UiMode.MODERN] is active. The classic UI
 * (BitChordApp) is untouched and stays available through the Settings
 * toggle; this host grows screen by screen as the clone lands.
 *
 * Phase 2: the real tab host — MochyNavigator state drives the five
 * Classic-parity tabs (Home, Explore, Library, Search, Radio), the
 * ported AppNavigationBar, the menu sheet host, and the full detail
 * routing (album/artist/playlist/library/history/browse). Settings is
 * not a tab: it opens as a detail from the profile avatar.
 *
 * Phase 3 (adapter-host): the player joins. The host creates the
 * [BottomSheetState] (Mochy's bounds: dismissed 0, collapsed at the nav bar
 * + mini player, expanded full height), provides it via
 * LocalPlayerBottomSheetState (Home's null-checks still hold, but the value
 * is now non-null), provides LocalArtworkColor from the playing song's cover
 * colour, applies the KeepScreenOn preference to the window, composes the
 * BottomSheetPlayer (mini player + full sheet) and the SongShareHost once
 * above it, and hands the iPod library source to the iPod player style.
 * The bottom insets the tab screens pad for now follow the sheet: nav bar
 * height + the mini player while a song is loaded, nav bar alone when the
 * sheet is dismissed.
 */
@Composable
fun MochyApp(modifier: Modifier = Modifier, launchIntroRequested: Boolean = false) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // The launch intro (batch 38): on a fresh launcher launch the splash
    // mark flies into Home's logo; anything else skips the gate at once —
    // otherwise the setup wizard would wait out the full 8 s awaitIntro
    // timeout on first launch.
    val launchIntro = remember { LaunchIntroState(plays = launchIntroRequested) }

    // Mochy-mode prefs (DataStore "settings") + data saver, once per host
    // lifetime. CW's own settings stay in AppSettings (SharedPreferences).
    LaunchedEffect(Unit) {
        if (!launchIntro.plays) LaunchGate.skip()
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
        // the device link steers this player while ChordWave is on screen (batch 19)
        LinkPlayer.connection = connection
        onDispose {
            LinkPlayer.release(connection)
            connection.dispose()
        }
    }

    // Lyrics pipeline: Modern mode never runs BitChordApp's lyrics-loading
    // effect (MainActivity returns early with MochyApp), so the host
    // attaches MainViewModel's lyrics flows + loader to the connection —
    // the connection itself never touches the ViewModel, it only takes
    // flows and a loader lambda (see attachLyrics).
    val mainViewModel: MainViewModel = viewModel()
    LaunchedEffect(connection, mainViewModel) {
        connection.attachLyrics(
            lyrics = mainViewModel.lyrics,
            source = mainViewModel.lyricsSource,
            checked = mainViewModel.lyricsChecked,
            loader = { request ->
                mainViewModel.loadLyrics(
                    request.videoId, request.title, request.artist,
                    request.durationMs, request.album, request.localUri,
                )
            },
            // The provider picker + editor over the same ViewModel, so the
            // connection never touches MainViewModel directly (see
            // LyricsProviderBridge).
            providerBridge = object : LyricsProviderBridge {
                override val providerStates = mainViewModel.lyricsProviderStates
                override fun selectProvider(source: LyricsSource) =
                    mainViewModel.selectLyricsProvider(source)
                override fun providerResult(source: LyricsSource) =
                    mainViewModel.lyricsProviderResult(source)
                override fun saveLyrics(songId: String, lrc: String) =
                    mainViewModel.saveLyricsOverride(songId, lrc)
                override fun clearLyrics(songId: String) =
                    mainViewModel.clearLyricsOverride(songId)
                override fun hasLyricsOverride(songId: String): Boolean =
                    AppSettings.lyricsOverride(songId) != null
            },
        )
        // The import/sync engine signs out through the activity-scoped
        // MainViewModel, which Hilt cannot inject — installed once here,
        // like SpotifyLibrary.auth. Until wired, YouTubeSignOut falls
        // back to clearing the same session state directly.
        YouTubeSignOut.delegate = mainViewModel::signOut
        YouTubeSignOut.refreshLibrary = {
            mainViewModel.loadLibrary()
            mainViewModel.loadPlaylists()
        }
    }

    val navigator = remember { MochyNavigator() }
    val glassState = remember { GlassState() }
    val menuState = remember { PopupSheetState() }
    val snackbarHostState = remember { SnackbarHostState() }
    val detailAccentColor = remember { mutableStateOf<androidx.compose.ui.graphics.Color?>(null) }
    val appContentObscured = remember { mutableStateOf(false) }

    // Update notification taps (UpdateDeepLink relay): the opener below
    // shows the version sheet each time the count goes up.
    val updateRequests by UpdateDeepLink.requests.collectAsState()
    val deviceRequests by DeviceLink.openRequests.collectAsState()

    // A Listen Together invite arriving while Modern is active opens the
    // party page (Classic precedent: MainActivity navigates to its own
    // page on the same signal). The screen runs the consent + preview +
    // confirm flow itself and calls JamInviteLink.handled() once it
    // starts, so this fires once per invite; navigate() also dedups a
    // same-top route. Signed-out users land on the page's sign-in row.
    val pendingInvite by JamInviteLink.pending.collectAsState()
    val youTubeSignedIn = rememberYouTubeSignedIn()
    LaunchedEffect(pendingInvite, youTubeSignedIn) {
        if (pendingInvite != null && youTubeSignedIn) {
            navigator.navigate("listen_together")
        }
    }

    // The setup wizard: a fresh Modern install sees it (both flags false);
    // a Classic user switching to Modern does not (onboardingComplete is
    // already true). "Repeat initial setup" in Experimental settings asks
    // for it again through SetupRerun.
    val setupCompleted by rememberPreference(SetupCompletedKey, defaultValue = false)
    val onboardingComplete by AppSettings.onboardingComplete.collectAsState()
    var forceShowSetup by remember { mutableStateOf(false) }
    val setupRerunRequests by SetupRerun.requests.collectAsState()
    LaunchedEffect(setupRerunRequests) {
        if (setupRerunRequests > 0) forceShowSetup = true
    }

    // The iPod player's library: downloads + device files + offline
    // playlists + listening history, warmed lazily on first use (see
    // ChordWaveIpodLibrary). Provided for the iPod style; the styles chunk's
    // AlternatePlayerStyle passes it to IpodPlayerStyle at merge time.
    val ipodLibrary = remember { ChordWaveIpodLibrary(context, scope) }

    // The connection's progress poll rests while the app is hidden instead
    // of recomposing the player for nobody.
    val deviceHost = remember { DeviceLink.host(context) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, connection) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    connection.isUiVisible.value = true
                    deviceHost.appShown()
                }
                Lifecycle.Event.ON_STOP -> {
                    connection.isUiVisible.value = false
                    deviceHost.appHidden()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    MochyTheme {
        KeepScreenOnEffect()
        // The artwork colour lives in its own provider so a song change
        // recomposes only it and its readers, not the whole host.
        MochyArtworkColorHost(connection) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val bottomInset = WindowInsets.navigationBars.asPaddingValues()
                    .calculateBottomPadding()
                // The player sheet, Mochy's bounds: dismissed at 0, collapsed
                // with the mini player level above the tab bar, expanded to
                // the full window. Starts dismissed; MochyContent brings it
                // up when a song loads.
                val playerSheetState = rememberBottomSheetState(
                    dismissedBound = 0.dp,
                    collapsedBound = bottomInset + NavigationBarHeight + MiniPlayerHeight,
                    expandedBound = maxHeight,
                )
                val sheetDismissed = playerSheetState.isDismissed
                // Bottom insets the tab screens pad for: the floating nav bar
                // (80dp) over the gesture bar, plus the mini player (64dp)
                // while a song is loaded. Mochy's formula, without the wide
                // bar (the host is phone-layout only for now).
                // WindowInsets.navigationBars is a @Composable getter, so it
                // is read here in the composable body, not inside remember.
                val navBars = WindowInsets.navigationBars
                val playerAwareInsets = remember(bottomInset, sheetDismissed, navBars) {
                    val bottom = bottomInset + NavigationBarHeight +
                        if (sheetDismissed) 0.dp else MiniPlayerHeight
                    navBars.union(WindowInsets(bottom = bottom))
                }
                CompositionLocalProvider(
                    LocalLaunchIntro provides launchIntro,
                    LocalPlayerConnection provides connection,
                    LocalPlayerBottomSheetState provides playerSheetState,
                    LocalIpodLibrarySource provides ipodLibrary,
                    LocalGridItemSize provides GridItemSize.BIG,
                    LocalCropAlbumArt provides false,
                    LocalAppContentObscured provides appContentObscured,
                    LocalDetailAccentColor provides detailAccentColor,
                    LocalGlassState provides glassState,
                    LocalMenuState provides menuState,
                    // The overlay sheets (language, video quality, Larp
                    // mode, scrobble sign-in, update) share the hosted
                    // menu state: PopupSheetHost only renders the states
                    // it is given, and the local's default is detached.
                    LocalZoomOutOverlayState provides menuState,
                    LocalPlayerAwareWindowInsets provides playerAwareInsets,
                ) {
                    // Menus (and later info pages) render through this host; the
                    // app zooms out and dims behind the active sheet.
                    PopupSheetHost(states = listOf(menuState)) {
                        UpdateNotificationOpener(requests = updateRequests)
                        DeviceIntegrationOpener(requests = deviceRequests)
                        Box(Modifier.fillMaxSize()) {
                            ControllingPill(Modifier.align(Alignment.TopStart).statusBarsPadding().padding(start = 16.dp, top = 8.dp))
                        }
                        SetupWizardContainer(
                            isVisible = (!setupCompleted && !onboardingComplete) || forceShowSetup,
                            onSetupCompleted = { username, uri ->
                                scope.launch(Dispatchers.IO) {
                                    val savedUriStr = uri?.let { ProfilePhotoStore.copyPhoto(context, it) }.orEmpty()
                                    context.dataStore.edit { prefs ->
                                        prefs[SetupCompletedKey] = true
                                        prefs[UsernameKey] = username
                                        if (savedUriStr.isNotEmpty()) prefs[ProfilePicUriKey] = savedUriStr
                                    }
                                }
                                // Both flags: the wizard's own marker, and
                                // the first-run gate — without the second,
                                // a Modern user switching to Classic would
                                // be thrown into classic onboarding.
                                AppSettings.setOnboardingComplete()
                                forceShowSetup = false
                            },
                        ) {
                            MochyContent(
                                navigator = navigator,
                                snackbarHostState = snackbarHostState,
                                connection = connection,
                                playerSheetState = playerSheetState,
                                modifier = modifier,
                            )
                        }

                        // The launch intro over everything (batch 38): the
                        // splash mark holds while the essentials come up,
                        // then flies into Home's logo.
                        LaunchIntroOverlay(launchIntro, essentialsReady = controller != null)
                    }
                }
            }
        }
    }
}

/**
 * "Keep screen on" (the player's menu, or a chevron button): the phone
 * doesn't sleep while ChordWave is on screen. A window flag only holds
 * while the app is in front, which is the point. [android.view.View.keepScreenOn]
 * sets FLAG_KEEP_SCREEN_ON on the window under the hood — Mochy's
 * MainActivity does exactly this.
 */
@Composable
private fun KeepScreenOnEffect() {
    val keepScreenOn by rememberPreference(KeepScreenOn, defaultValue = false)
    val rootView = LocalView.current
    DisposableEffect(keepScreenOn) {
        rootView.keepScreenOn = keepScreenOn
        onDispose { rootView.keepScreenOn = false }
    }
}

/**
 * Provides [LocalArtworkColor] with the playing song's cover colour: the
 * bottom-sampled, darkened theme colour of its artwork
 * ([rememberCoverThemeColor]), falling back to [DefaultThemeColor] while
 * there is no song or no artwork yet.
 */
@Composable
private fun MochyArtworkColorHost(
    connection: MochyPlayerConnection,
    content: @Composable () -> Unit,
) {
    val metadata by connection.mediaMetadata.collectAsState()
    val coverColor by rememberCoverThemeColor(metadata?.thumbnailUrl)
    CompositionLocalProvider(LocalArtworkColor provides (coverColor ?: DefaultThemeColor)) {
        content()
    }
}

/** How long an engine error must stand before the host shows it — long
 *  enough for the service's retries (two recoveries, 350 ms apart) to clear
 *  a hiccup, short enough that a stuck track is reported promptly. */
private const val PlaybackErrorDebounceMs = 2_000L

/** The page on screen, for the page transition: the tab it belongs to
 *  (a detail page carries the tab it was opened from — the answer
 *  Mochy's TabOwnership gave its NavHost), its route, and how deep the
 *  detail stack is. A shrinking depth is a pop. */
private data class MochyPage(val tab: Int, val route: String, val depth: Int)

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun MochyContent(
    navigator: MochyNavigator,
    snackbarHostState: SnackbarHostState,
    connection: MochyPlayerConnection,
    playerSheetState: BottomSheetState,
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

    // The sheet follows the player, Mochy's MainActivity logic: a song
    // loading brings the mini player up, the queue emptying sends it away.
    // state.dismiss() is programmatic — it does not run the sheet's
    // onDismiss (only a finger flinging the mini player away does that, and
    // that is what stops playback), so this cannot stop a song by itself.
    val currentMetadata by connection.mediaMetadata.collectAsState()
    LaunchedEffect(currentMetadata) {
        if (currentMetadata == null) {
            if (!playerSheetState.isDismissed) playerSheetState.dismiss()
        } else if (playerSheetState.isDismissed) {
            playerSheetState.collapseSoft()
        }
    }

    // Playback failures surface here, over the tab bar, through the same
    // SnackbarHost the screens use. Engine errors are debounced: the
    // service retries a failed stream (up to two recoveries, 350 ms apart),
    // and each retry clears the error first — so a hiccup it recovers from
    // never reaches the snackbar, and only an error still standing after
    // the delay is a track that is actually stuck. Queue starts that fail
    // outright (fetch threw, nothing to play) arrive on playFailure
    // already worded, and show at once.
    val hostContext = LocalContext.current
    LaunchedEffect(connection, snackbarHostState) {
        launch {
            connection.error.collectLatest { err ->
                if (err == null) return@collectLatest
                delay(PlaybackErrorDebounceMs)
                if (connection.error.value === err) {
                    snackbarHostState.showSnackbar(hostContext.getString(R.string.couldnt_play_song))
                }
            }
        }
        launch {
            connection.playFailure.collect { message ->
                snackbarHostState.showSnackbar(message)
            }
        }
    }

    val selectedTabRoute = owningTab(currentTab, detailStack, Screens.MainScreens)
    val bottomInset = WindowInsets.navigationBars.asPaddingValues()
        .calculateBottomPadding()
    val density = LocalDensity.current
    // How far the tab bar slides down (and fades out) as the player opens:
    // its own height, Mochy's formula.
    val navBarTotalPx = with(density) { (NavigationBarHeight + bottomInset).toPx() }

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
                // Page transitions exactly as Mochy's NavHost (batch 40):
                // each page slides by the tab it belongs to, not by its own
                // route — an album open in the Library moves like the
                // Library when you switch tabs. Slide width/8 + fade,
                // tween(200); the pop variants run when the detail stack
                // shrinks (back). CW's host has no NavHost, so the page
                // key carries the owning tab + stack depth and
                // AnimatedContent runs the same specs. The shared-element
                // scopes Mochy provides around its NavHost are provided
                // here too, so container transforms see the transition.
                val page = MochyPage(
                    tab = currentTab,
                    route = currentDetail ?: Screens.MainScreens[currentTab].route,
                    depth = detailStack.size,
                )
                SharedTransitionLayout(Modifier.fillMaxSize()) {
                    CompositionLocalProvider(LocalSharedTransitionScope provides this) {
                        AnimatedContent(
                            targetState = page,
                            transitionSpec = {
                                val back = targetState.depth < initialState.depth
                                val enter = if (!AppMotion.enabled) EnterTransition.None else if (back) {
                                    if (initialState.tab != -1 && initialState.tab < targetState.tab)
                                        slideInHorizontally { it / 8 } + fadeIn(tween(200))
                                    else
                                        slideInHorizontally { -it / 8 } + fadeIn(tween(200))
                                } else {
                                    if (targetState.tab == -1 || targetState.tab >= initialState.tab)
                                        slideInHorizontally { it / 8 } + fadeIn(tween(200))
                                    else
                                        slideInHorizontally { -it / 8 } + fadeIn(tween(200))
                                }
                                val exit = if (!AppMotion.enabled) ExitTransition.None else if (back) {
                                    if (initialState.tab != -1 && initialState.tab < targetState.tab)
                                        slideOutHorizontally { -it / 8 } + fadeOut(tween(200))
                                    else
                                        slideOutHorizontally { it / 8 } + fadeOut(tween(200))
                                } else {
                                    if (targetState.tab == -1 || targetState.tab >= initialState.tab)
                                        slideOutHorizontally { -it / 8 } + fadeOut(tween(200))
                                    else
                                        slideOutHorizontally { it / 8 } + fadeOut(tween(200))
                                }
                                enter togetherWith exit
                            },
                            modifier = Modifier.fillMaxSize(),
                            label = "mochy_page",
                        ) { shown ->
                            CompositionLocalProvider(LocalNavAnimatedContentScope provides this) {
                                when {
                                    shown.depth > 0 -> DetailContent(
                                        route = shown.route,
                                        navigator = navigator,
                                        pureBlack = pureBlack,
                                        onBack = { navigator.popBackStack() },
                                    )
                                    shown.tab == 0 -> HomeScreen(
                                        navigator = navigator,
                                        snackbarHostState = snackbarHostState,
                                    )
                                    // Classic's set and order (round 2, item 7): Home,
                                    // Explore, Library, Search, Radio. Settings is not a
                                    // tab — it opens as a detail from the profile avatar.
                                    shown.tab == 1 -> ExploreScreen(navigator = navigator)
                                    shown.tab == 2 -> LibraryHomeScreen(
                                        navigator = navigator,
                                        pureBlack = pureBlack,
                                    )
                                    shown.tab == 3 -> SearchScreen(
                                        navigator = navigator,
                                        pureBlack = pureBlack,
                                    )
                                    shown.tab == 4 -> RadioScreen(navigator = navigator)
                                    // No tab at that index (should not happen): Home, the
                                    // first tab, rather than a blank page.
                                    else -> HomeScreen(
                                        navigator = navigator,
                                        snackbarHostState = snackbarHostState,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                // Above the tab bar, and above the mini player while it is up.
                .padding(
                    bottom = bottomInset + NavigationBarHeight +
                        (if (playerSheetState.isDismissed) 0.dp else MiniPlayerHeight) + 8.dp,
                ),
        )

        // The player sheet: the mini player collapsed, the full player open.
        // Swipe-skip on the mini player is the player's own: BottomSheetPlayer
        // hands the sheet its SongCarousel swipe, which skips through the
        // connection (seekToNext/skipToPreviousSong) — the host wires no
        // onSwipeSkip of its own.
        BottomSheetPlayer(
            state = playerSheetState,
            navigator = navigator,
            pureBlack = pureBlack,
        )

        // "Share song" from any menu opens here, over the app and the player.
        SongShareHost()

        AppNavigationBar(
            navigationItems = Screens.MainScreens,
            currentRoute = currentRoute,
            selectedTabRoute = selectedTabRoute,
            onItemClick = { screen, isSelected ->
                navigator.onTabTapped(screen, isSelected)
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .graphicsLayer {
                    // Slides down and fades as the player opens, Mochy's
                    // formula: gone once the sheet is 60% up.
                    val progress = playerSheetState.progress.coerceIn(0f, 1f)
                    translationY = navBarTotalPx * progress
                    alpha = (1f - progress / 0.6f).coerceIn(0f, 1f)
                },
            // Phase 2 Search: a second quick tap on Search goes straight
            // into typing once the Search screen lands.
            onSearchDoubleTap = navigator::requestSearchFocus,
        )

        // Replay stories over everything — the mini player and the tab
        // bar included: a story covers the window, and inside the Replay
        // page it ran under them (its share button sat behind the mini
        // player). Classic mounts its stories at the activity for the
        // same reason.
        ReplayStoriesHost()
    }
}

/** A pushed detail route: the ported detail screens, falling back to the
 *  placeholder for routes whose screen hasn't landed yet (customisation
 *  and audio_pass until their Phase 5 ports; unknown routes stay
 *  diagnosable on-device). */
@OptIn(ExperimentalMaterial3Api::class)
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
        // Explore is a tab now (round 2, item 7 — Classic parity), so
        // navigate("explore") switches to it instead of pushing; this
        // branch stays as the safety net for a stacked "explore" route.
        route == "explore" -> ExploreScreen(navigator = navigator)
        route.startsWith("local_genre/") -> {
            val name = Uri.decode(route.removePrefix("local_genre/"))
            LibraryGenreScreen(
                navigator = navigator,
                genreName = name,
            )
        }
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
                // The shelf rides along: "library/local/songs" lists the Local tab's
                // songs, not the account library's (batch 11 — the shelf was parsed
                // here but never handed to the screen).
                "songs" -> LibrarySongsScreen(
                    navigator = navigator,
                    pureBlack = pureBlack,
                    shelf = shelf,
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
        // ---- Profile (simplified port of Mochy's ProfileScreen) ----
        // Opened from the avatar in Home/Search/Library/Artist; the profile
        // itself links on to Settings (the Mochy flow).
        route == "profile" -> ProfileScreen(navigator = navigator)
        // New Release albums (Explore's title navigates here; Mochy never
        // had a destination for it — this screen is ChordWave's own).
        route == "new_release" -> NewReleaseScreen(navigator = navigator)
        // ---- Settings (Phase 4; verbatim Mochy route strings) ----
        route == "mochy_settings" -> MochySettingsScreen(navigator = navigator)
        route == "general_settings" -> AppBehaviourSettingsScreen(navigator = navigator)
        route == "playback_settings" -> PlaybackSettingsScreen(navigator = navigator)
        route == "now_playing_settings" -> NowPlayingSettingsScreen(navigator = navigator)
        route == "cipher_settings" -> CipherSettingsScreen(navigator = navigator)
        route == "crossmix_settings" -> CrossmixSettingsScreen(navigator = navigator)
        route == "experimental_settings" -> ExperimentalSettingsScreen(navigator = navigator)
        route == "playback_diagnostics" -> PlaybackDiagnosticsScreen(navigator = navigator)
        route == "proxy_settings" -> ProxySettingsScreen(navigator = navigator)
        route == "storage_settings" -> StorageScreen(navigator = navigator)
        route == "integrations_settings" -> IntegrationsScreen(navigator = navigator)
        route == "integrations_settings/discord" -> DiscordScreen(navigator = navigator)
        route.startsWith("integrations_settings/scrobble/") -> {
            val site = ScrobbleSite.entries.firstOrNull {
                it.name == route.removePrefix("integrations_settings/scrobble/")
                    .substringBefore("?").uppercase()
            } ?: ScrobbleSite.LASTFM
            ScrobbleServiceScreen(navigator = navigator, site = site)
        }
        route == "integrations_settings/backup" -> BackupScreen(navigator = navigator)
        route == "integrations_settings/spotify" -> SpotifyScreen(navigator = navigator)
        route == "import_sync" -> ImportSyncScreen(navigator = navigator)
        route == "import_providers" -> ImportProvidersScreen(navigator = navigator)
        route == "import_tunemymusic" -> TuneMyMusicImportScreen(navigator = navigator)
        route == "import_progress" -> ImportProgressScreen(navigator = navigator)
        route == "youtube_sync" -> YouTubeSyncScreen(navigator = navigator)
        route == "youtube_login" -> YouTubeLoginScreen(navigator = navigator)
        route == "drive_backup" -> DriveBackupScreen(navigator = navigator)
        route == "drive_devices" -> DriveDevicesScreen(navigator = navigator)
        // ---- ChordWave-only restyles (Phase 5) ----
        route == "listen_together" -> ListenTogetherScreen(navigator = navigator)
        route == "sources" -> SourcesScreen(navigator = navigator)
        // Radio is a tab now (round 2, item 7 — Classic parity), so
        // navigate("radio") switches to it instead of pushing; this branch
        // stays as the safety net for a stacked "radio" route.
        route == "radio" -> RadioScreen(navigator = navigator)
        route == "replay" -> ReplayScreen(navigator = navigator)
        // Mochy's Stats page (batch 39): monthly/yearly listening stats.
        // Reached from Profile's "View Stats"; Replay lives in the Library.
        route == "stats" -> StatsScreen(navigator = navigator)
        route == "karaoke_remote" -> KaraokeRemoteScreen(navigator = navigator)
        route == "audio_lab" -> AudioLabScreen(navigator = navigator)
        route == "audio_fx" -> AudioFxScreen(navigator = navigator)
        route == "audio_quality" -> AudioQualityScreen(navigator = navigator)
        route == "audio_pass" -> AudioPassScreen(navigator = navigator)
        route == "customisation_settings" -> CustomisationScreen(navigator = navigator)
        // The equalizer takes a tab query: the Settings AutoEQ entry
        // lands straight on the AutoEq tab (2); every other caller
        // opens the graph (0). No other route starts with "equalizer".
        route.startsWith("equalizer") -> {
            val tab = if ("tab=autoeq" in route) 2 else 0
            EqualizerScreen(navigator = navigator, initialTab = tab)
        }
        route.startsWith("account_import/") -> {
            // account_import/{service}?resync={id|all}&then={svc,svc}
            val rest = route.removePrefix("account_import/")
            val service = AccountService.fromRoute(rest.substringBefore("?"))
                ?: AccountService.SPOTIFY
            val query = rest.substringAfter("?", "")
            val resync = query.substringAfter("resync=", "")
                .substringBefore("&")
                .takeIf { it.isNotEmpty() }?.let { Uri.decode(it) }
            val then = query.substringAfter("then=", "")
                .substringBefore("&")
                .takeIf { it.isNotEmpty() }
                ?.split(",")
                ?.mapNotNull { AccountService.fromRoute(Uri.decode(it)) }
                .orEmpty()
            AccountImportScreen(
                navigator = navigator,
                service = service,
                resync = resync,
                then = then,
            )
        }
        // The update sheet is a PopupSheet, not a page: the route opens it
        // once the launch-time check settles, then pops itself so back
        // never lands on an empty route. (The notification path uses
        // UpdateNotificationOpener instead; this covers deep links.)
        route == "settings/update" -> {
            val menuState = LocalMenuState.current
            val updateState by rememberUpdateState()
            var opened by remember { mutableStateOf(false) }
            LaunchedEffect(updateState) {
                if (!opened && updateState !is UpdateState.Checking) {
                    opened = true
                    menuState.showUpdateSheet(updateState)
                    navigator.popBackStack()
                }
            }
            MochySettingsScreen(navigator = navigator)
        }
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
