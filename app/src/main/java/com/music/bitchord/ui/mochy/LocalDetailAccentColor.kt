// Ported from Mochy Music (GPL-3.0): MainActivity.kt (LocalDetailAccentColor,
// line 1764). The detail-screen accent color (set by the open detail page,
// read by GlassKit's rememberChromeAccentWash). The Modern host (MochyApp)
// provides it; the default is a null-holding state so unprovided reads are
// safe.
package com.music.bitchord.ui.mochy

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

val LocalDetailAccentColor = staticCompositionLocalOf<MutableState<Color?>> {
    mutableStateOf(null)
}
