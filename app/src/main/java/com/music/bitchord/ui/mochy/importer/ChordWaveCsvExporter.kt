// ChordWave CSV export: liked songs and owned playlists as a csv the
// import reads back exactly (rows carry the youtube id) and other tools
// can read (TuneMyMusic column names). The format is Mochy's —
// writeMochyCsv/CsvExportRow come from the Phase 4C CsvImport.kt, same
// package — so old "Mochy backup" files still restore; only the
// user-visible label changed (4E decision D7).
//
// CW ADAPTATION: Mochy's exporter read its Room database. ChordWave has
// no local library database, so this reads the signed-in YouTube account
// instead: the full Liked Music list + every playlist the account owns.
// Saved (not owned) playlists are already on YouTube, so they are not
// exported. Exporting needs a signed-in account.
package com.music.bitchord.ui.mochy.importer

import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.innertube.Innertube
import com.music.bitchord.data.model.Song as CwSong
import com.music.bitchord.data.model.durationMillis
import com.music.bitchord.ui.mochy.viewmodels.fetchAllBrowseSongs
import com.music.bitchord.ui.mochy.viewmodels.fetchPlaylistSongs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ChordWaveCsvExporter {

    data class Summary(val likedSongs: Int, val playlists: Int, val playlistSongs: Int)

    suspend fun build(): Pair<String, Summary> = withContext(Dispatchers.IO) {
        if (Innertube.cookie == null) {
            throw IllegalStateException("Sign in to YouTube first — there's nothing to export without an account.")
        }
        val rows = mutableListOf<CsvExportRow>()

        // newest first, which is the order an import re-creates
        val liked = fetchAllBrowseSongs(YtMusicRepository.LIKED_MUSIC).asReversed()
        liked.forEach { rows += it.toRow(playlistName = null) }

        val playlists = YtMusicRepository.userPlaylists().getOrDefault(emptyList())
        val usedNames = HashSet<String>()
        var playlistSongs = 0
        playlists.forEach { playlist ->
            // two playlists with one name would merge on import; keep them apart
            var name = playlist.title.ifBlank { "Untitled playlist" }
            var suffix = 2
            while (!usedNames.add(name)) name = "${playlist.title} ($suffix)".also { suffix++ }

            val songs = fetchPlaylistSongs(playlist.playlistId)
            if (songs.isEmpty()) {
                rows += CsvExportRow(name, null, null, null, null, null, null)
            } else {
                songs.forEach { rows += it.toRow(playlistName = name) }
                playlistSongs += songs.size
            }
        }

        writeMochyCsv(rows) to Summary(liked.size, playlists.size, playlistSongs)
    }

    private fun CwSong.toRow(playlistName: String?) = CsvExportRow(
        playlistName = playlistName,
        title = title,
        artist = artist,
        album = albumName,
        durationMs = durationMillis().takeIf { it > 0 },
        videoId = videoId,
        thumbnailUrl = thumbnailUrl?.takeIf { it.startsWith("https://") },
    )
}
