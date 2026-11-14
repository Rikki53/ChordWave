// LocalMochyProviders.kt — the app-level CompositionLocals the ported Mochy
// UI reads. In Mochy these are declared in MainActivity.kt; here they live
// in one file and the Modern host (MochyApp) provides them.
// (LocalDatabase / LocalDownloadUtil are NOT here: they need the data-layer
// mapping, Phase 2b.)

package com.music.bitchord.ui.mochy

import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import com.music.bitchord.ui.mochy.component.GlassState
import com.music.bitchord.ui.mochy.constants.GridItemSize

/** grid cards: BIG = 137.dp, else 104.dp (HomeCards.homeCardSize) */
val LocalGridItemSize = compositionLocalOf { GridItemSize.BIG }

/** crop album art to a square (read by ItemThumbnail/PlaylistThumbnail; the
 *  value is unused in the ported bodies, same as Mochy — dead read kept) */
val LocalCropAlbumArt = compositionLocalOf { false }

/** true while another surface covers the app content: canvas video and the
 *  playing indicator pause while obscured */
val LocalAppContentObscured = staticCompositionLocalOf<State<Boolean>> {
    error("LocalAppContentObscured not provided")
}

/** bottom insets the tab screens pad for: navigation bars + the mini-player.
 *  Added for the Phase 2 Home screen (HomeScreen NEEDS.md A9). The host
 *  provides navigation-bars insets; the mini-player height joins in Phase 3
 *  when the player exists. */
val LocalPlayerAwareWindowInsets = staticCompositionLocalOf<androidx.compose.foundation.layout.WindowInsets> {
    androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0)
}

/** the shared glass-blur state the chrome surfaces (nav bar, mini-player)
 *  render through. Mochy MainActivity:1700, verbatim. The host provides a
 *  remembered GlassState; readers fall back to a fresh one when absent. */
val LocalGlassState = staticCompositionLocalOf<GlassState?> { null }
