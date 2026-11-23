// CanvasOrder.kt — the order animated-cover sources are tried in.
//
// Ported from Mochy Music (GPL-3.0): constants/PreferenceKeys.kt (CanvasOrder).
// ChordWave's [CanvasRepository] used to know only two orders — Apple first,
// or Spotify first when [AppSettings.prioritizeSpotifyCanvas] was on. The
// six permutations here are the single source of truth now; the Spotify
// toggle in the Classic Spotify settings rewrites the order instead of
// living beside it (see [AppSettings.setPrioritizeSpotifyCanvas]).
//
// The community index is not in the order: it is the fallback, always tried
// last, after the three catalogue services.

package com.music.bitchord.data.canvas

/** One of the three catalogue services a canvas can come from. */
enum class CanvasSearchSource {
    APPLE,
    TIDAL,
    SPOTIFY,
}

/** The six orders the catalogue services can be tried in. */
enum class CanvasOrder(val sources: List<CanvasSearchSource>) {
    APPLE_TIDAL_SPOTIFY(listOf(CanvasSearchSource.APPLE, CanvasSearchSource.TIDAL, CanvasSearchSource.SPOTIFY)),
    APPLE_SPOTIFY_TIDAL(listOf(CanvasSearchSource.APPLE, CanvasSearchSource.SPOTIFY, CanvasSearchSource.TIDAL)),
    TIDAL_APPLE_SPOTIFY(listOf(CanvasSearchSource.TIDAL, CanvasSearchSource.APPLE, CanvasSearchSource.SPOTIFY)),
    TIDAL_SPOTIFY_APPLE(listOf(CanvasSearchSource.TIDAL, CanvasSearchSource.SPOTIFY, CanvasSearchSource.APPLE)),
    SPOTIFY_APPLE_TIDAL(listOf(CanvasSearchSource.SPOTIFY, CanvasSearchSource.APPLE, CanvasSearchSource.TIDAL)),
    SPOTIFY_TIDAL_APPLE(listOf(CanvasSearchSource.SPOTIFY, CanvasSearchSource.TIDAL, CanvasSearchSource.APPLE)),
}
