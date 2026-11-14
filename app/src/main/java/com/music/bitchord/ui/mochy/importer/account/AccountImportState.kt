// Ported from Mochy Music (GPL-3.0): importer/account/AccountImportSession.kt
// (AccountImportState sealed interface) + importer/account/AccountModels.kt
// (RemotePlaylist data class). The AccountImportSession engine is NOT ported —
// the AccountImportViewModel adapter owns it (see NEEDS.md).
package com.music.bitchord.ui.mochy.importer.account

sealed interface AccountImportState {
    /** The service's sign-in page is showing. */
    data object SigningIn : AccountImportState
    /** YouTube Music uses the app's account, which isn't signed in yet. */
    data object NeedsYouTubeSignIn : AccountImportState
    data class Loading(val label: String) : AccountImportState
    data class Choosing(
        val accountName: String?,
        val playlists: List<RemotePlaylist>,
        val selected: Set<String>,
        /** A Re-sync that couldn't find this playlist by name: the user picks which one it is. */
        val pickingFor: String? = null,
    ) : AccountImportState
    data class Reading(val label: String, val done: Int, val total: Int) : AccountImportState
    data class Failed(val message: String, val signedOut: Boolean) : AccountImportState
}

data class RemotePlaylist(
    val id: String,
    val name: String,
    val trackCount: Int?,
    val subtitle: String? = null,
    /** Goes into Liked songs instead of becoming a playlist. */
    val isLiked: Boolean = false,
    /**
     * The account's whole song library rather than a playlist: goes into the Library's Songs (and
     * Artists, Albums) as songs, not into a playlist named after the service.
     */
    val isLibrary: Boolean = false,
    val coverUrl: String? = null,
)
