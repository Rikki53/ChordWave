// YouTube Music account import, rebuilt on ChordWave's YtMusicRepository
// (Mochy's YouTubeMusicAccount.kt read the same library through its own
// innertube client). Uses the app's own YouTube sign-in; songs keep their
// exact ids, so nothing is searched.
package com.music.bitchord.ui.mochy.importer.account

import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.ui.mochy.importer.ImportedTrack
import com.music.bitchord.ui.mochy.importer.toImportedTrack
import com.music.bitchord.ui.mochy.viewmodels.fetchAllBrowseSongs
import com.music.bitchord.ui.mochy.viewmodels.fetchPlaylistSongs
import com.music.bitchord.ui.mochy.viewmodels.toSongItem

private const val LIKED_ID = "LM"

/** Uses the app's own YouTube sign-in; songs keep their exact ids, so nothing is searched. */
class YouTubeMusicLibrary(
    override val accountName: String?,
    override val accountPhotoUrl: String? = null,
) : AccountLibrary {

    override suspend fun playlists(): List<RemotePlaylist> {
        val items = YtMusicRepository.libraryPlaylists().getOrElse {
            throw AccountImportException("Couldn't read your YouTube Music playlists. Check that you're signed in.")
        }
        val result = mutableListOf(RemotePlaylist(LIKED_ID, "Liked music", null, isLiked = true))
        items.mapNotNull { item ->
            val browseId = item.browseId ?: return@mapNotNull null
            // LM is listed above; SE is the "Episodes for later" podcast list
            if (browseId in setOf("VLLM", "LM", "VLSE", "SE")) return@mapNotNull null
            RemotePlaylist(
                id = browseId,
                name = item.title,
                trackCount = null,
                subtitle = item.subtitle.ifEmpty { null },
                coverUrl = item.thumbnailUrl,
            )
        }.forEach { result += it }
        return result
    }

    override suspend fun tracks(playlist: RemotePlaylist): List<ImportedTrack> {
        val songs = if (playlist.isLiked) {
            fetchAllBrowseSongs(YtMusicRepository.LIKED_MUSIC)
        } else {
            YtMusicRepository.playlistEntries(playlist.id).getOrNull()
                ?: fetchPlaylistSongs(playlist.id)
        }
        return songs.map { it.toSongItem().toImportedTrack() }
    }
}
