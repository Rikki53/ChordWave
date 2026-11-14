// STUB — coordinator-written, 14 Nov 2026, EXTENDED 15 Nov 2026 for Phase 3.
// Mochy's modules.skin.ActiveSkin is the active look/skin module's runtime
// state (part of the modules system, which is OUT OF SCOPE for the UI clone).
// The player UI reads current/playerFont/shaders/values/glass from it; with
// no module loader nothing is ever published, so every member stays at its
// empty value and the MODULE player style + module menu rows never appear.
// The members are plain vals (not mutableState): nothing ever changes them,
// so no recomposition is ever needed. If the skin system is ever ported,
// this stub becomes the real ActiveSkin object with publish().
package com.music.bitchord.ui.mochy.modules.skin

import androidx.compose.ui.text.font.FontFamily

/** a compiled skin shader program: the AGSL shader plus the uniforms it declares */
class SkinProgram(val shader: Any, val declared: Set<String>)

object ActiveSkin {
    /** the active look module's skin, if one is on (never in this clone) */
    val current: ModuleSkin? = null

    /** the active look module's glass for the bottom bar (null = default FROSTED path) */
    val glass: ActiveGlass? = null

    /** why the glass failed to load, if it did (never in this clone) */
    val glassProblem: Pair<String, String>? = null

    /** the skin's custom settings by key (empty in this clone) */
    val values: Map<String, String> = emptyMap()

    /** why a player shader failed to compile, if one did (never in this clone) */
    val shaderProblem: Pair<String, String>? = null

    /** the skin's font for the player (null = default font) */
    val playerFont: FontFamily? = null

    /** the skin's compiled player shaders by file name (empty in this clone) */
    val shaders: Map<String, SkinProgram> = emptyMap()
}
