// Ported from Mochy Music (GPL-3.0): importer/account/AccountModels.kt
// (DESKTOP_USER_AGENT + WEB_PLAYER_QUERY_HEADER only). The rest of
// AccountModels.kt (AccountLibrary, CapturedCredentials, AccountConnector,
// WebPlayerQuery) is the account-import session machinery — the
// AccountImportViewModel adapter owns it (see NEEDS.md).
//
// WEB_PLAYER_QUERY_HEADER's value ("mochy-web-player-query") is kept verbatim:
// it is a page-side pseudo-header name in the sign-in WebView bridge, never
// shown to the user, and the adapter's Spotify connector matches on it.
package com.music.bitchord.ui.mochy.importer.account

/**
 * Desktop Chrome: Tidal's web player refuses phones, and Spotify's phone site has no library. Also
 * sent with the requests that read Spotify's library the way its web player does.
 */
internal const val DESKTOP_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"

/**
 * The pseudo header under which the sign-in page reports a query Spotify's web player made, as JSON
 * {name, hash, variables, url, post}, so the app can send the same query for the rest of a list.
 */
const val WEB_PLAYER_QUERY_HEADER = "mochy-web-player-query"
