// Ported from Mochy Music (GPL-3.0), MainActivity.kt:1762 — the current
// song's cover colour as a composition local. Mochy's host provides it with
// the playing song's theme colour (MainActivity.kt:937); the Modern host
// (MochyApp) must do the same once the player lands — until then the default
// grey keeps every reader compiling and rendering.
package com.music.bitchord.ui.mochy

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import com.music.bitchord.ui.mochy.theme.DefaultThemeColor

val LocalArtworkColor: ProvidableCompositionLocal<Color> = compositionLocalOf { DefaultThemeColor }
