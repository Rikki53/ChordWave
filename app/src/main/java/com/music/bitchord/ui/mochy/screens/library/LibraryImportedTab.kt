// Ported from Mochy Music (GPL-3.0): ui/screens/library/LibraryImportedTab.kt
// ADAPTATIONS: navigation param -> MochyNavigator (route strings verbatim);
// Mochy packages -> com.music.bitchord.ui.mochy.* staged equivalents. See NEEDS.md.
// LibraryImportedTab.kt
//
// The Library's Imported tab: the import banner, then what each service brought in, as songs
// (All music), as playlists and as albums, and Manage imports at the bottom. Songs YouTube sync keeps up to
// date count under YouTube here, apart from the songs liked in Mochy itself.

package com.music.bitchord.ui.mochy.screens.library

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.music.bitchord.R
import com.music.bitchord.ui.mochy.models.Playlist
import com.music.bitchord.ui.mochy.importer.ImportProgress
import com.music.bitchord.ui.mochy.importer.ImportSource
import com.music.bitchord.ui.mochy.screens.search.SearchHorizontalPadding
import com.music.bitchord.ui.mochy.viewmodels.ImportedAlbums
import com.music.bitchord.ui.mochy.viewmodels.ImportedSourceCard

/** What one service brought in, for its cards. */
@Immutable
class ImportedService(
    val source: ImportSource,
    val songCount: Int,
    val songCovers: List<String>,
    val playlists: List<Playlist>,
    val albums: ImportedAlbums,
) {
    val isEmpty: Boolean get() = songCount == 0 && playlists.isEmpty()
}

/**
 * The four services the mock lists, always, then any other one that has brought something in.
 * A service with nothing yet still gets its cards: they read 0 and open the import sheet.
 */
internal fun importedServices(
    songSources: List<ImportedSourceCard>,
    playlists: List<Playlist>,
    playlistSources: Map<String, ImportSource>,
    albums: Map<ImportSource, ImportedAlbums>,
): List<ImportedService> {
    val playlistsBySource = playlists.groupBy { playlistSources[it.id] }
    val songsBySource = songSources.associateBy { it.source }
    val extra = (songsBySource.keys + playlistsBySource.keys.filterNotNull())
        .filter { it.isListed && it !in ImportedTabServices }
        .sortedBy { it.ordinal }
    return (ImportedTabServices + extra).map { source ->
        val card = songsBySource[source]
        ImportedService(
            source = source,
            songCount = card?.songCount ?: 0,
            songCovers = card?.covers.orEmpty(),
            playlists = playlistsBySource[source].orEmpty(),
            albums = albums[source] ?: ImportedAlbums(count = 0, covers = emptyList()),
        )
    }
}

internal fun LazyListScope.libraryImportedTab(
    importProgress: ImportProgress,
    services: List<ImportedService>,
    onOpenImportSheet: () -> Unit,
    onOpenImportProgress: () -> Unit,
    onOpenSongs: (ImportSource) -> Unit,
    onOpenPlaylists: (ImportSource) -> Unit,
    onOpenAlbums: (ImportSource) -> Unit,
    onManage: () -> Unit,
) {
    item(key = "imported_banner") {
        LibraryImportBanner(
            progress = importProgress,
            onOpenSheet = onOpenImportSheet,
            onOpenProgress = onOpenImportProgress,
            modifier = Modifier.padding(horizontal = SearchHorizontalPadding),
        )
        Spacer(modifier = Modifier.height(22.dp))
    }

    item(key = "imported_songs") {
        LibraryCapsLabel("All music")
        Spacer(modifier = Modifier.height(10.dp))
        LibraryCardGrid(
            services.map { service ->
                LibraryCardEntry(
                    title = service.source.cardLabel,
                    count = service.songCount,
                    covers = service.songCovers,
                    onClick = { if (service.songCount == 0) onOpenImportSheet() else onOpenSongs(service.source) },
                    icon = { ImportSourceIcon(service.source, size = 18.dp) },
                )
            },
        )
        Spacer(modifier = Modifier.height(22.dp))
    }

    item(key = "imported_playlists") {
        LibraryCapsLabel("Playlists")
        Spacer(modifier = Modifier.height(10.dp))
        LibraryCardGrid(
            services.map { service ->
                LibraryCardEntry(
                    title = service.source.cardLabel,
                    count = service.playlists.size,
                    covers = service.playlists.take(2).flatMap { it.thumbnails },
                    onClick = { if (service.playlists.isEmpty()) onOpenImportSheet() else onOpenPlaylists(service.source) },
                    icon = { ImportSourceIcon(service.source, size = 18.dp) },
                )
            },
        )
    }

    item(key = "imported_albums") {
        Spacer(modifier = Modifier.height(22.dp))
        LibraryCapsLabel(stringResource(R.string.albums))
        Spacer(modifier = Modifier.height(10.dp))
        LibraryCardGrid(
            services.map { service ->
                LibraryCardEntry(
                    title = service.source.cardLabel,
                    count = service.albums.count,
                    covers = service.albums.covers,
                    onClick = { if (service.albums.count == 0) onOpenImportSheet() else onOpenAlbums(service.source) },
                    icon = { ImportSourceIcon(service.source, size = 18.dp) },
                )
            },
        )
    }

    item(key = "imported_manage") {
        Spacer(modifier = Modifier.height(22.dp))
        LibraryManagePill(text = "Manage imports", onClick = onManage)
    }
}
