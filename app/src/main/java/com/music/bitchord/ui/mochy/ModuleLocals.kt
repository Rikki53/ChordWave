// Batch 55b: the composition locals Mochy's module pages read, in ChordWave's
// shape. Mochy provides LocalDatabase (its MusicDatabase) and
// LocalDownloadUtil (its DownloadUtil) from its app root; here the database
// is the shared Room MusicDatabase in :mochyengine (MochyDatabaseHolder) and
// the download util is MochyDownloadUtil, the Modern engine's downloader.

package com.music.bitchord.ui.mochy

import androidx.compose.runtime.staticCompositionLocalOf
import com.mochyy.mochymusic.db.MusicDatabase
import com.music.bitchord.playback.mochy.MochyDownloadUtil

/** the shared library database, for module pages that list songs, playlists, albums and artists */
val LocalDatabase = staticCompositionLocalOf<MusicDatabase> {
    error("LocalDatabase not provided")
}

/** the Modern engine's downloader, for module pages that show download state */
val LocalDownloadUtil = staticCompositionLocalOf { MochyDownloadUtil }
