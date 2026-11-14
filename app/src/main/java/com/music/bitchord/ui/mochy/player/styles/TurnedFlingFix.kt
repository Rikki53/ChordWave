// Ported from Mochy Music (GPL-3.0): ui/player/styles/TurnedFlingFix.kt.
//
// DEVIATION (BOM 2024.12.01 / Compose 1.7.6): Mochy toggles
// androidx.compose.ui.input.pointer.util.VelocityTrackerAddPointsFix, which was added in
// Compose 1.8 and does not exist in 1.7.6 (verified against the ui-android 1.7.6 artifact).
// In 1.7.6 the velocity tracker already reads touches the old way, so there is no fix to
// disable and this composable is intentionally a no-op. If the app ever moves to a BOM with
// Compose 1.8+, restore Mochy's TurnedFrames logic (toggle the flag off while any turned
// frame is up, back on when the last one leaves).
package com.music.bitchord.ui.mochy.player.styles

import androidx.compose.runtime.Composable

// compose reads a fling's speed off the raw screen touches, which don't turn with the video mode's sideways frame, so its lists flung the wrong way; the older way reads the turned touches
@Composable
fun TurnedFlingFix(active: Boolean = true) {
    // No-op on Compose 1.7.6 (see header). Kept so callers compile unchanged.
}
