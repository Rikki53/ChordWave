// Batch 55b: from Mochy's ui/component/ModuleNavBar.kt — a skin colour away
// from the player, where there's no song to take tones from.

package com.music.bitchord.ui.mochy.component

import androidx.compose.ui.graphics.Color
import com.music.bitchord.ui.mochy.modules.skin.SkinColor
import com.music.bitchord.ui.mochy.modules.skin.skinColor

/** a skin colour away from the player, where there's no song to take tones from: the player's tones read as white shades and the cover as the accent */
internal fun SkinColor.onChrome(accent: Color, values: Map<String, String>): Color = when (this) {
    is SkinColor.Fixed -> color
    SkinColor.Accent, SkinColor.Cover, SkinColor.Played -> accent
    SkinColor.Remaining -> accent.copy(alpha = 0.6f)
    SkinColor.InkSoft -> Color.White.copy(alpha = 0.6f)
    SkinColor.Chip -> Color.White.copy(alpha = 0.12f)
    SkinColor.Ink -> Color.White
    // a setting can't point at another setting, so this never loops
    is SkinColor.Setting -> skinColor(values[key])?.takeIf { it !is SkinColor.Setting }?.onChrome(accent, values) ?: fallback.onChrome(accent, values)
}
