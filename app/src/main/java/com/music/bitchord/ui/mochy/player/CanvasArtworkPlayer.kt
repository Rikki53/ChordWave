// STUB — CanvasArtworkPlayer (detail NEEDS.md C12, Phase 5 stretch).
// Mochy: ui/player/CanvasArtworkPlayer.kt — the artist banner's motion loop
// (plays the Apple canvas video when available, falls back to static art).
// The full port needs the canvas provider modules + CanvasArtworkPlayer's
// ExoPlayer wiring; until Phase 5 this renders nothing — the banner already
// shows the still photo + stillUrl artwork behind it, so the screen reads
// correctly without motion. Signature matches Mochy's call site in
// ArtistDesign.kt (primaryUrl/fallbackUrl/isPlaying/modifier).
package com.music.bitchord.ui.mochy.player

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun CanvasArtworkPlayer(
    primaryUrl: String?,
    fallbackUrl: String?,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    // Phase 5: canvas motion loop (Monochrome/Apple providers + player).
    // Static artwork behind this Box already covers the banner.
    Box(modifier = modifier)
}
