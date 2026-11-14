// Ported from Mochy Music (GPL-3.0): importer/account/AccountModels.kt
// (AccountService enum only). The rest of AccountModels.kt (RemotePlaylist,
// AccountLibrary, CapturedCredentials, AccountConnector) is the account-import
// session machinery — Phase 4, with the onboarding import pages.
// LibraryImport.kt needs the enum for its import-source icons + service() map.
package com.music.bitchord.ui.mochy.importer.account

enum class AccountService(
    val route: String,
    val label: String,
    /** Where sign-in starts; null for YouTube Music, which uses the app's own account. */
    val loginUrl: String?,
    /** Sites whose cookies and storage belong to this service, cleared by "Use a different account". */
    val webOrigins: List<String>,
    /** The web player that sends the session after sign-in; only it is watched, never the login pages. */
    val playerOrigins: Set<String> = emptySet(),
    /** Tidal's web player turns phones away, so it's opened as a desktop site. */
    val desktopSite: Boolean = false,
) {
    SPOTIFY(
        route = "spotify",
        label = "Spotify",
        loginUrl = "https://accounts.spotify.com/login?continue=https%3A%2F%2Fopen.spotify.com%2F",
        webOrigins = listOf("https://accounts.spotify.com", "https://open.spotify.com", "https://www.spotify.com"),
        playerOrigins = setOf("https://open.spotify.com"),
    ),
    APPLE_MUSIC(
        route = "apple_music",
        label = "Apple Music",
        loginUrl = "https://music.apple.com/us/library/playlists",
        webOrigins = listOf("https://music.apple.com", "https://idmsa.apple.com", "https://authorize.music.apple.com", "https://www.apple.com"),
        playerOrigins = setOf("https://music.apple.com"),
    ),
    TIDAL(
        route = "tidal",
        label = "Tidal",
        loginUrl = "https://listen.tidal.com/login",
        webOrigins = listOf("https://listen.tidal.com", "https://login.tidal.com", "https://tidal.com"),
        playerOrigins = setOf("https://listen.tidal.com"),
        desktopSite = true,
    ),
    YOUTUBE_MUSIC(
        route = "youtube_music",
        label = "YouTube Music",
        loginUrl = null,
        webOrigins = emptyList(),
    );

    companion object {
        fun fromRoute(route: String?): AccountService? = entries.firstOrNull { it.route == route }
    }
}
