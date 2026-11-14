// Ported from Mochy Music (GPL-3.0): ui/player/menu/DeviceMenuRows.kt
// STUB — Mochy's remote-device system (devices link, remote control, "play
// on / play next on / control" rows for paired TVs) is OUT OF SCOPE for the
// Modern UI clone (see NEEDS.md §(c) D1). In Mochy these rows only rendered
// when a paired, remotely-controllable device was nearby; with no devices
// engine they render nothing, which is exactly the graceful degradation the
// brief asks for. PlayerActionMenu keeps calling this, so the rows come back
// the moment a devices engine exists — this file is the place to restore them.
//
// The Mochy original (79 lines) is preserved in the inspect tree at
// ui/player/menu/DeviceMenuRows.kt; it needs rememberDevicesLink(),
// LinkSongs, showRemoteControl and the tv_menu_* strings, none of which are
// staged (the tv_menu_* strings are deliberately NOT in
// mochy_player_menus_strings.xml).

package com.music.bitchord.ui.mochy.player.menu

import androidx.compose.runtime.Composable

// the player menu's device rows: move what's playing to a paired device that's around, line the song up there, or steer it from here
@Composable
internal fun DeviceMenuRows(onDismiss: () -> Unit) {
    // TODO(phase3-adapter): remote devices are out of scope — no rows until a
    // devices engine exists (NEEDS.md §(c) D1). Renders nothing by design.
}
