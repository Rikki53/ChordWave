// Ported from Mochy Music (GPL-3.0): ui/component/WideBar.kt (MiniPillFrame extract)
// NOTE: in Mochy these live in WideBar.kt, which was NOT ported: it needs LocalPlayerConnection,
// the Screens nav graph, a repeat-mode extension and repeat/shuffle resources that ChordWave has
// no equivalent for. Extracted verbatim so BottomSheet's pill transition compiles; when WideBar
// is ported, delete this file and use WideBar.kt's definitions instead.

package com.music.bitchord.ui.mochy.component

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// where the mini player's pill sits across the screen, and whether the wide bar's one glass already lies under it
@Immutable
class MiniPillFrame(
    val start: Dp,
    val end: Dp,
    // the wide bar draws the glass, the outline and the shadow under the mini player and the tabs alike
    val merged: Boolean,
    // the wide mini player has room for a previous button beside play and next
    val withPrevious: Boolean,
)

// the phone's mini player: the full width less 24dp each side, in its own glass
val PhoneMiniPill = MiniPillFrame(start = 24.dp, end = 24.dp, merged = false, withPrevious = false)

val LocalMiniPillFrame = staticCompositionLocalOf { PhoneMiniPill }
