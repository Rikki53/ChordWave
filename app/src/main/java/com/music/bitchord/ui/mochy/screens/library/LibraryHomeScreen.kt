// Ported from Mochy Music (GPL-3.0): ui/screens/library/LibraryHomeScreen.kt
// ADAPTATIONS: navigation param -> MochyNavigator (route strings verbatim);
// Mochy packages -> com.music.bitchord.ui.mochy.* staged equivalents. See NEEDS.md.
// LibraryHomeScreen.kt
//
// The Library tab's landing page: collapsing title + search, then All / Local / Cached / History /
// Genres / Imported. All has the import banner, Pinned, the four category cards, Recently
// added and Liked; the others are in LibraryLocalTab.kt, LibraryCachedTab.kt, LibraryMoreTabs.kt and
// LibraryImportedTab.kt. While songs download or files come in from the phone, a progress card sits
// at the top of All and Local.

package com.music.bitchord.ui.mochy.screens.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.music.bitchord.ui.mochy.navigation.MochyNavigator
import com.music.bitchord.download.SavedCollection
import com.music.bitchord.ui.mochy.player.menu.rememberResyncAll
import com.music.bitchord.ui.mochy.LocalPlayerAwareWindowInsets
import com.music.bitchord.R
import com.music.bitchord.ui.mochy.playback.LocalPlayerConnection
import com.music.bitchord.ui.mochy.constants.HideLibraryImportBannerKey
import com.music.bitchord.ui.mochy.screens.profile.rememberProfilePhotoUrl
import com.music.bitchord.ui.mochy.models.Song
import com.music.bitchord.ui.mochy.extensions.toMediaItem
import com.music.bitchord.ui.mochy.models.TransferBatch
import com.music.bitchord.ui.mochy.playback.queues.ListQueue
import com.music.bitchord.ui.mochy.component.GlassState
import com.music.bitchord.ui.mochy.component.LikedCover
import com.music.bitchord.ui.mochy.component.CreatePlaylistDialog
import com.music.bitchord.ui.mochy.component.LocalMenuState
import com.music.bitchord.ui.mochy.component.rememberRevealSeenState
import com.music.bitchord.ui.mochy.player.menu.PlaylistMenu
import com.music.bitchord.ui.mochy.player.menu.SongMenu
import com.music.bitchord.ui.mochy.screens.search.SearchAvatar
import com.music.bitchord.ui.mochy.screens.search.SearchColors
import com.music.bitchord.ui.mochy.screens.search.rememberCollapseProgress
import com.music.bitchord.ui.mochy.utils.rememberPreference
import com.music.bitchord.ui.mochy.viewmodels.HistoryViewModel
import com.music.bitchord.ui.mochy.viewmodels.LibraryHomeViewModel
import com.music.bitchord.ui.mochy.viewmodels.LibraryImportViewModel
import com.music.bitchord.ui.mochy.viewmodels.LocalGenresViewModel
import com.music.bitchord.ui.mochy.models.innertube.SongItem
import com.music.bitchord.ui.mochy.models.innertube.YTItem
import com.music.bitchord.ui.mochy.component.coverTransitionKey
import com.music.bitchord.ui.mochy.playback.MochyPlayerConnection

@Composable
fun LibraryHomeScreen(
    navigator: MochyNavigator,
    modifier: Modifier = Modifier,
    pureBlack: Boolean = false,
) {
    val viewModel: LibraryHomeViewModel = hiltViewModel()
    val importViewModel: LibraryImportViewModel = hiltViewModel()
    val playerConnection = LocalPlayerConnection.current
    val menuState = LocalMenuState.current

    val pinnedItems by viewModel.pinnedItems.collectAsState()
    val songs by viewModel.songs.collectAsState()
    val artists by viewModel.artists.collectAsState()
    val albums by viewModel.albums.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val likedSongs by viewModel.likedSongs.collectAsState()
    val downloadedSongs by viewModel.downloadedSongs.collectAsState()
    val downloadedReleases by viewModel.downloadedReleases.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val loadFailed by viewModel.loadFailed.collectAsState()
    val signedIn by viewModel.signedIn.collectAsState()
    val importedSources by viewModel.importedSources.collectAsState()
    val playlistSources by viewModel.playlistSources.collectAsState()
    val importedAlbums by viewModel.importedAlbums.collectAsState()
    val importProgress by viewModel.importProgress.collectAsState()
    val downloads by importViewModel.downloads.collectAsState()
    val localImports by importViewModel.localImports.collectAsState()

    // A failed load retries when the tab is opened again — the one path
    // that recovers a cold start whose first fetch never made it.
    LaunchedEffect(Unit) { viewModel.refreshIfNeeded() }

    // The open tab lives in the ViewModel, not here: this screen leaves composition while a
    // card's page is open, and a rememberSaveable tab would reset to All on return (batch 11).
    val tab by viewModel.selectedTab.collectAsState()
    // only the open tab's shelf is collected; null while it is first read
    val shelfData = tab.shelf?.let { collectShelfTabData(viewModel, it) }
    val localActions = rememberLocalImportActions(importViewModel, onPermissionGranted = viewModel::load)

    // the History and Genres tabs only load once they're opened: one reads every play, the other every local song's genre
    val historyViewModel: HistoryViewModel? = if (tab == LibraryTab.History) hiltViewModel<HistoryViewModel>() else null
    val historyEvents = historyViewModel?.events?.collectAsState()?.value
    val historyLoaded = historyViewModel?.loaded?.collectAsState()?.value ?: false
    val genres = if (tab == LibraryTab.Genres) {
        hiltViewModel<LocalGenresViewModel>().genres.collectAsState().value
    } else {
        null
    }
    LaunchedEffect(historyViewModel) { historyViewModel?.load() }

    // the Replay tab's state, only while it's open (batch 39): the same state
    // the `replay` route draws, so the tab and the page never disagree
    val replayPageState = if (tab == LibraryTab.Replay) rememberLibraryReplayState(navigator) else null

    // read inside the Cached and History tabs' rows only, so playback changes don't recompose this screen
    val nowPlaying = playerConnection?.mediaMetadata?.collectAsState()
    val isPlaying = playerConnection?.isEffectivelyPlaying?.collectAsState()

    val (hideImportBanner, onHideImportBannerChange) = rememberPreference(HideLibraryImportBannerKey, defaultValue = false)

    var query by remember { mutableStateOf(TextFieldValue()) }
    val revealSeen = rememberRevealSeenState()
    val coroutineScope = rememberCoroutineScope()
    var creatingPlaylist by remember { mutableStateOf(false) }
    val results = remember(query.text, songs, artists, albums, playlists, tab, shelfData) {
        val q = query.text.trim()
        when {
            q.isBlank() -> emptyList()
            tab.shelf != null -> shelfData?.let { searchShelf(q, it.songs, it.artists, it.albums, it.playlists) }.orEmpty()
            else -> searchLibrary(q, songs, artists, albums, playlists)
        }
    }
    val services = remember(importedSources, playlists, playlistSources, importedAlbums) {
        importedServices(importedSources, playlists, playlistSources, importedAlbums)
    }

    val listState = rememberLazyListState()
    // Tap the active tab again to scroll to the top (same pattern as Home).
    LaunchedEffect(navigator) {
        var first = true
        navigator.scrollToTopRequests.collect {
            if (first) {
                first = false
                return@collect
            }
            listState.animateScrollToItem(0)
        }
    }
    val glassState = remember { GlassState() }
    val collapse = rememberCollapseProgress(listState)
    val bottomInset = LocalPlayerAwareWindowInsets.current.asPaddingValues().calculateBottomPadding()

    // The import sheet (Phase 4): showLibraryImportSheet() opens the
    // onboarding import pages (ProviderListPage etc.) + the importsync
    // WebView login flow.
    val openImportSheet = menuState::showLibraryImportSheet
    val openImportProgress = { navigator.navigate("import_progress") }
    val resyncAll = rememberResyncAll(navigator)
    val openTransfer = { kind: TransferKind -> menuState.showTransferSheet(kind, importViewModel) }

    // A downloaded release plays its own tracks, in the order the page
    // listed them — the release record is what says which songs are its.
    // A release whose tracks were all deleted has no rows to begin with
    // (downloadedReleases only keeps releases with tracks on disk).
    val onPlayRelease: (SavedCollection) -> Unit = { release ->
        val ordered = release.videoIds.mapNotNull { id -> downloadedSongs.firstOrNull { it.id == id } }
        if (ordered.isNotEmpty()) {
            playerConnection?.playQueue(
                ListQueue(title = release.title, items = ordered.map { it.toMediaItem() }),
            )
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LibraryScaffold(
            title = stringResource(R.string.filter_library),
            subtitle = null,
            searchPlaceholder = when (tab) {
                LibraryTab.Local -> stringResource(R.string.library_local_search)
                LibraryTab.Cached -> stringResource(R.string.library_cached_search)
                else -> stringResource(R.string.library_all_search)
            },
            query = query,
            onQueryChange = { query = it },
            listState = listState,
            glassState = glassState,
            collapseProvider = { collapse.value },
            pureBlack = pureBlack,
            trailing = {
                SearchAvatar(
                    imageUrl = rememberProfilePhotoUrl(),
                    onClick = { navigator.navigateToTab("profile") },
                )
            },
            bottomInset = bottomInset,
        ) {
            // No file access yet: say so at the top of the page, on every tab,
            // instead of only under the Local tab's import card. Tapping asks
            // for access; granting re-scans and reloads the library, so the
            // downloaded songs show up in Local (batch 3 item 4).
            if (!localActions.hasStorageAccess) {
                item(key = "storage_permission") {
                    LibraryStoragePermissionBanner(onRequest = localActions::scanPhone)
                }
            }

            item(key = "tabs") {
                LibraryTabChips(
                    selected = tab,
                    onSelect = viewModel::selectTab,
                    modifier = Modifier.padding(horizontal = LibraryHorizontalPadding),
                )
                Spacer(modifier = Modifier.height(14.dp))
            }

            if (query.text.isNotBlank()) {
                librarySearchResults(
                    results = results,
                    query = query.text,
                    onOpen = { item ->
                        if (shelfData != null) {
                            openShelfResult(item, shelfData, navigator, playerConnection)
                        } else {
                            openLibraryItem(item, navigator, playerConnection, songs)
                        }
                    },
                )
                return@LibraryScaffold
            }

            when (tab) {
                LibraryTab.All -> {
                    libraryProgressCards(downloads, localImports, openTransfer)
                    libraryAllTab(
                        pinnedItems = pinnedItems,
                        songs = songs,
                        artists = artists.size,
                        artistCovers = artists.take(4).map { it.artist.thumbnailUrl },
                        albums = albums.size,
                        albumCovers = albums.take(4).map { it.album.thumbnailUrl },
                        playlists = playlists.size,
                        playlistCovers = playlists.take(2).flatMap { it.thumbnails },
                        likedSongs = likedSongs,
                        downloadedSongs = downloadedSongs,
                        downloadedReleases = downloadedReleases,
                        isLoading = isLoading,
                        loadFailed = loadFailed,
                        signedIn = signedIn,
                        importBanner = if (!hideImportBanner || importProgress.isRunning) {
                            {
                                LibraryImportBanner(
                                    progress = importProgress,
                                    onOpenSheet = openImportSheet,
                                    onOpenProgress = openImportProgress,
                                    onDismiss = { onHideImportBannerChange(true) },
                                    modifier = Modifier.padding(horizontal = LibraryHorizontalPadding),
                                )
                            }
                        } else {
                            null
                        },
                        onOpen = { item -> openLibraryItem(item, navigator, playerConnection, songs) },
                        onUnpin = viewModel::unpin,
                        onRetry = viewModel::load,
                        onSignIn = { navigator.navigate("youtube_login") },
                        onPlayRelease = onPlayRelease,
                        onOpenReplayTab = { viewModel.selectTab(LibraryTab.Replay) },
                        navigator = navigator,
                    )
                }

                LibraryTab.Local -> {
                    val local = shelfData ?: return@LibraryScaffold
                    libraryProgressCards(downloads, localImports, openTransfer)
                    libraryLocalTab(
                        data = local,
                        hasStorageAccess = localActions.hasStorageAccess,
                        onImport = localActions::importFromPhone,
                        onScan = localActions::scanPhone,
                        onManage = {
                            menuState.showManageLocalSheet(
                                viewModel = importViewModel,
                                onRescan = localActions::rescan,
                                onAddFolder = localActions::pickFolder,
                                onRemoveFromList = { menuState.showRemoveLocalSheet(local.files, importViewModel) },
                            )
                        },
                        navigator = navigator,
                    )
                }

                LibraryTab.Cached -> {
                    val cached = shelfData ?: return@LibraryScaffold
                    libraryCachedTab(
                        data = cached,
                        nowPlayingId = { nowPlaying?.value?.id },
                        isPlaying = { isPlaying?.value == true },
                        onPlay = { song -> playShelf(cached, cached.songs.indexOf(song), playerConnection) },
                        onMenu = { song ->
                            menuState.show {
                                SongMenu(originalSong = song, navigator = navigator, onDismiss = menuState::dismiss)
                            }
                        },
                        onManage = {
                            menuState.showManageCachedSheet(
                                cachedSongs = cached.songs,
                                onClearCache = viewModel::clearCache,
                                onRemove = viewModel::removeFromCache,
                            )
                        },
                        navigator = navigator,
                    )
                }

                LibraryTab.History -> libraryHistoryTab(historyEvents, historyLoaded, nowPlaying, isPlaying, playerConnection, navigator)

                LibraryTab.Replay -> libraryReplayTab(replayPageState, revealSeen)

                LibraryTab.Genres -> libraryGenresTab(genres, navigator)

                LibraryTab.Playlists -> libraryPlaylistsTab(
                    playlists = playlists,
                    likedSongs = likedSongs,
                    navigator = navigator,
                    onNewPlaylist = { creatingPlaylist = true },
                    onPlaylistMenu = { playlist ->
                        menuState.show {
                            PlaylistMenu(
                                playlist = playlist,
                                coroutineScope = coroutineScope,
                                onDismiss = menuState::dismiss,
                                navigator = navigator,
                            )
                        }
                    },
                    revealSeen = revealSeen,
                )

                LibraryTab.Imported -> libraryImportedTab(
                    importProgress = importProgress,
                    services = services,
                    onOpenImportSheet = openImportSheet,
                    onOpenImportProgress = openImportProgress,
                    onOpenSongs = { navigator.navigate("library/imported/${it.key}") },
                    onOpenPlaylists = { navigator.navigate("library/imported/${it.key}/playlists") },
                    onOpenAlbums = { navigator.navigate("library/imported/${it.key}/albums") },
                    onManage = {
                        menuState.showManageImportsSheet(
                            sources = services.filterNot { it.isEmpty }.map { it.source },
                            viewModel = importViewModel,
                            onResyncAll = resyncAll,
                        )
                    },
                )
            }
        }
    }

    if (creatingPlaylist) {
        CreatePlaylistDialog(
            onDismiss = { creatingPlaylist = false },
            onPlaylistCreated = { playlistId -> navigator.navigate("local_playlist/$playlistId") },
        )
    }
}

/** The download and local import cards; each only shows while its job runs. */
private fun LazyListScope.libraryProgressCards(
    downloads: TransferBatch,
    localImports: TransferBatch,
    onOpen: (TransferKind) -> Unit,
) {
    item(key = "progress_cards") {
        Column(modifier = Modifier.padding(horizontal = LibraryHorizontalPadding)) {
            LibraryProgressCard(TransferKind.Downloads.title, downloads, onClick = { onOpen(TransferKind.Downloads) })
            LibraryProgressCard(TransferKind.LocalImport.title, localImports, onClick = { onOpen(TransferKind.LocalImport) })
        }
    }
}

private fun LazyListScope.libraryAllTab(
    pinnedItems: List<YTItem>,
    songs: List<Song>,
    artists: Int,
    artistCovers: List<String?>,
    albums: Int,
    albumCovers: List<String?>,
    playlists: Int,
    playlistCovers: List<String?>,
    likedSongs: List<Song>,
    downloadedSongs: List<Song>,
    downloadedReleases: List<SavedCollection>,
    isLoading: Boolean,
    loadFailed: Boolean,
    signedIn: Boolean,
    importBanner: (@Composable () -> Unit)?,
    onOpen: (YTItem) -> Unit,
    onUnpin: (String) -> Unit,
    onRetry: () -> Unit,
    onSignIn: () -> Unit,
    onPlayRelease: (SavedCollection) -> Unit,
    onOpenReplayTab: () -> Unit,
    navigator: MochyNavigator,
) {
    // Even in a library with nothing in it - that is when it matters most. Opens the import sheet,
    // and is the import's progress bar while one runs.
    if (importBanner != null) {
        item(key = "import_banner") {
            importBanner()
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // Library data (songs, liked songs, playlists, albums, artists — the
    // account's when signed in, this phone's own while signed out) vs the
    // downloads shelf: the two arrive on different paths, so the tab's
    // state is decided by both, not by either alone.
    val hasLibraryData = songs.isNotEmpty() || likedSongs.isNotEmpty() || albums > 0 || artists > 0 || playlists > 0 || pinnedItems.isNotEmpty()
    val hasLocalData = downloadedSongs.isNotEmpty() || downloadedReleases.isNotEmpty()

    // Nothing at all yet: one honest state, not four zero-count cards.
    // Loading, failed, signed-out and genuinely-empty are four different
    // things, and the tab used to show the same "import your music" line
    // for all of them — including while the first fetch was still running
    // and after it had failed, with no way to retry.
    if (!hasLibraryData && !hasLocalData) {
        item(key = "library_state") {
            when {
                isLoading -> LibraryLoadingState()
                loadFailed -> LibraryErrorState(onRetry = onRetry)
                !signedIn -> LibrarySignedOutState(onSignIn = onSignIn)
                else -> LibraryEmptyState(text = stringResource(R.string.library_empty_import))
            }
        }
        return
    }

    // Pinned disappears entirely when nothing is pinned rather than showing an empty 3x3 of
    // placeholders, which on a fresh install would be the first thing on the screen.
    if (pinnedItems.isNotEmpty()) {
        item(key = "pinned_header") {
            LibrarySectionTitle(stringResource(R.string.library_pinned))
            Spacer(modifier = Modifier.height(12.dp))
            LibraryRule()
            Spacer(modifier = Modifier.height(16.dp))
        }
        item(key = "pinned_grid") {
            // Chunked rows rather than a nested lazy grid: a scrolling grid inside a scrolling
            // column has no bounded height, and nine items never needs its own scroller.
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = LibraryHorizontalPadding),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                pinnedItems.take(9).chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { item ->
                            LibraryPinnedTile(
                                thumbnailUrl = item.thumbnail,
                                sharedElementKey = coverTransitionKey(item),
                                onClick = { onOpen(item) },
                                onLongClick = { onUnpin(item.id) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        // Keeps the last row's tiles the same size as a full row's.
                        repeat(3 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                    }
                }
            }
            Spacer(modifier = Modifier.height(18.dp))
            LibraryRule()
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // The four category cards only once the account library has landed:
    // while it is still loading, a spinner holds their place so four
    // 0-count cards never flash — and a failed or signed-out load says so
    // here instead, above the downloads that did arrive.
    if (hasLibraryData) {
        item(key = "categories") {
            LibraryCardGrid(
                listOf(
                    LibraryCardEntry(stringResource(R.string.songs), songs.size, songs.take(4).map { it.song.thumbnailUrl }, {
                        navigator.navigate("library/songs")
                    }),
                    LibraryCardEntry(stringResource(R.string.playlists), playlists, playlistCovers, { navigator.navigate("library/playlists") }),
                    LibraryCardEntry(stringResource(R.string.albums), albums, albumCovers, { navigator.navigate("library/albums") }),
                    LibraryCardEntry(stringResource(R.string.artists), artists, artistCovers, { navigator.navigate("library/artists") }, roundCovers = true),
                ),
            )
            Spacer(modifier = Modifier.height(10.dp))
        }
    } else if (isLoading) {
        item(key = "categories_loading") { LibraryLoadingState() }
    } else if (loadFailed) {
        item(key = "categories_failed") { LibraryErrorState(onRetry = onRetry) }
    } else if (!signedIn) {
        item(key = "categories_signed_out") { LibrarySignedOutState(onSignIn = onSignIn) }
    }

    if (songs.isNotEmpty()) {
        item(key = "recently_added") {
            LibraryWideCard(
                title = stringResource(R.string.library_recently_added),
                count = songs.size,
                covers = songs.take(4).map { it.song.thumbnailUrl },
                onClick = { navigator.navigate("library/added") },
                modifier = Modifier.padding(horizontal = LibraryHorizontalPadding),
            )
            Spacer(modifier = Modifier.height(10.dp))
        }
    }

    if (likedSongs.isNotEmpty()) {
        item(key = "liked_songs") {
            LibraryWideCard(
                title = stringResource(R.string.liked),
                count = likedSongs.size,
                covers = likedSongs.take(4).map { it.song.thumbnailUrl },
                onClick = { navigator.navigate("auto_playlist/liked") },
                sharedElementKey = "playlist-liked",
                art = { LikedCover(it) },
                modifier = Modifier.padding(horizontal = LibraryHorizontalPadding),
            )
            Spacer(modifier = Modifier.height(10.dp))
        }
    }

    // Downloads: every downloaded track in one card (Classic's Downloads
    // folder), then the releases downloaded whole (Classic's "On device"
    // shelf) — each playing its own tracks. Downloads live on this phone,
    // so they show with or without a signed-in account.
    if (downloadedSongs.isNotEmpty()) {
        item(key = "downloads") {
            LibraryWideCard(
                title = stringResource(R.string.downloads),
                count = downloadedSongs.size,
                covers = downloadedSongs.take(4).map { it.song.thumbnailUrl },
                onClick = { navigator.navigate("auto_playlist/downloaded") },
                sharedElementKey = "playlist-downloaded",
                modifier = Modifier.padding(horizontal = LibraryHorizontalPadding),
            )
            Spacer(modifier = Modifier.height(10.dp))
        }
    }

    if (downloadedReleases.isNotEmpty()) {
        item(key = "downloaded_releases") {
            Spacer(modifier = Modifier.height(16.dp))
            LibrarySectionTitle(stringResource(R.string.library_downloaded))
            Spacer(modifier = Modifier.height(8.dp))
            downloadedReleases.forEach { release ->
                LibraryListRow(
                    title = release.title,
                    // The credit the release was downloaded with (Classic's
                    // rule); a playlist with none still says what it is.
                    subtitle = release.subtitle.takeIf { it.isNotBlank() }
                        ?: if (release.playlist) stringResource(R.string.downloaded_playlist) else null,
                    thumbnailUrl = release.thumbnailUrl,
                    onClick = { onPlayRelease(release) },
                    onLongClick = {},
                )
            }
        }
    }

    // ChordWave-only pages with no shelf of their own: your year in
    // music, and live radio. Replay has its own tab now (batch 39), so its
    // row switches to it; a Home tile for Radio is the nicer home
    // (Phase 6 polish); the row here keeps it reachable meanwhile.
    item(key = "more_pages") {
        Spacer(modifier = Modifier.height(16.dp))
        LibraryRule()
        Spacer(modifier = Modifier.height(10.dp))
        LibraryListRow(
            title = stringResource(R.string.replay),
            subtitle = stringResource(R.string.replay_subtitle),
            thumbnailUrl = null,
            onClick = onOpenReplayTab,
            onLongClick = {},
        )
        LibraryListRow(
            title = stringResource(R.string.radio_title),
            subtitle = null,
            thumbnailUrl = null,
            onClick = { navigator.navigate("radio") },
            onLongClick = {},
        )
    }
}

/**
 * A song found on the Local or Cached tab plays among that tab's songs, from what is on this phone,
 * rather than as a radio that needs a connection.
 */
private fun openShelfResult(
    item: YTItem,
    shelf: ShelfTabData,
    navigator: MochyNavigator,
    playerConnection: MochyPlayerConnection?,
) {
    val index = if (item is SongItem) shelf.songs.indexOfFirst { it.id == item.id } else -1
    if (index < 0) {
        openLibraryItem(item, navigator, playerConnection, shelf.songs)
        return
    }
    playShelf(shelf, index, playerConnection)
}

/** The shelf's songs as one queue, in the order the tab shows them, starting at [startIndex]. */
private fun playShelf(
    shelf: ShelfTabData,
    startIndex: Int,
    playerConnection: MochyPlayerConnection?,
) {
    playerConnection?.playQueue(
        ListQueue(
            title = shelf.kind.label,
            items = shelf.songs.map { it.toMediaItem() },
            startIndex = startIndex.coerceAtLeast(0),
        ),
    )
}

/**
 * The file-access ask, at the top of the Library until access is granted.
 * Same card language as the Local tab's own ask, so it reads as one page.
 */
@Composable
private fun LibraryStoragePermissionBanner(onRequest: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = LibraryHorizontalPadding)
            .clip(RoundedCornerShape(20.dp))
            .background(SearchColors.Tile)
            .clickable(role = Role.Button, onClick = onRequest)
            .padding(horizontal = 18.dp, vertical = 18.dp),
    ) {
        Text(
            text = "Let ChordWave read your storage to find all your songs",
            color = SearchColors.Primary,
            fontSize = 20.sp,
            lineHeight = 24.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Tap to allow access",
            color = SearchColors.Secondary,
            fontSize = 13.sp,
        )
    }
}
