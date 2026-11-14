// Spotify account import, rebuilt on ChordWave's own signed-in Spotify
// library (data.spotify.SpotifyLibrary) instead of Mochy's 514-line
// SpotifyAccount connector.
//
// WHY: CW already reads a signed-in Spotify library — playlists() +
// tracks() over the web player's GraphQL, with the bearer minted from
// the sp_dc session cookie (SpotifyToken harvests it in an offscreen
// WebView). The cookie is the credential here, not the bearer tokens
// the sign-in page sends: the session captures sp_dc from the WebView's
// cookie jar (AccountImportSession.onHeader) and stores it where CW
// already keeps it (AppSettings.spotifySpdcToken — the same store the
// Classic Spotify library reads). No new token store is invented, and
// no bearer token is ever persisted: SpotifyToken mints a fresh one
// from the cookie on demand and holds it in memory only.
//
// LIMIT: accountName is null — CW's library query names no account, so
// the list page shows Mochy's "Signed in to Spotify" fallback.
package com.music.bitchord.ui.mochy.importer.account

import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.spotify.SpotifyLibrary
import com.music.bitchord.ui.mochy.importer.ImportedTrack

/** Turns the stored sp_dc cookie into a library; null while no cookie is stored yet. */
object SpotifyConnector : AccountConnector {
    override suspend fun connect(credentials: CapturedCredentials): AccountLibrary? {
        // The captured bearer tokens are not used: CW's library reader
        // mints its own bearer from the sp_dc cookie.
        if (AppSettings.spotifySpdcToken.value.isBlank()) return null
        return SpotifyLibraryImpl()
    }
}

private class SpotifyLibraryImpl : AccountLibrary {
    override val accountName: String? = null

    override suspend fun playlists(): List<RemotePlaylist> = try {
        SpotifyLibrary.playlists().map { playlist ->
            RemotePlaylist(
                id = playlist.id,
                name = playlist.name,
                trackCount = null,
                subtitle = playlist.owner,
                isLiked = playlist.id == SpotifyLibrary.LIKED_ID,
                coverUrl = playlist.imageUrl,
            )
        }
    } catch (e: Exception) {
        throw e.toAccountImportException("Couldn't read your Spotify library.")
    }

    override suspend fun tracks(playlist: RemotePlaylist): List<ImportedTrack> = try {
        SpotifyLibrary.tracks(playlist.id).map { track ->
            ImportedTrack(
                title = track.title,
                artist = track.artist,
                album = track.album,
                durationMs = track.durationMs.toLong(),
                thumbnailUrl = track.imageUrl,
                sourceId = "spotify:track:${track.id}",
            )
        }
    } catch (e: Exception) {
        throw e.toAccountImportException("Couldn't read \"${playlist.name}\" on Spotify.")
    }

    private fun Exception.toAccountImportException(what: String): AccountImportException {
        val message = this.message.orEmpty()
        val signedOut = message.contains("expired", ignoreCase = true) || message.contains("(401)")
        return if (signedOut) {
            AccountImportException("Spotify signed you out. Sign in again.", signedOut = true)
        } else {
            AccountImportException(what)
        }
    }
}
