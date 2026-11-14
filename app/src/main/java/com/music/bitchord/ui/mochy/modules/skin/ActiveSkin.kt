// STUB — coordinator-written, 14 Nov 2026. Mochy's modules.skin.ActiveSkin is
// the active look/skin module's runtime state (part of the modules system,
// which is OUT OF SCOPE for the UI clone). FrostedGlass reads ActiveSkin.glass
// to decide whether a look module provides its own glass shader; it null-checks
// before use, so a null glass keeps the default FROSTED path. If the skin
// system is ever ported (Phase 3 decision, still pending), this stub becomes
// the real ActiveSkin object and `glass` gets set by the module loader.
package com.music.bitchord.ui.mochy.modules.skin

object ActiveSkin {
    val glass: ActiveGlass? = null
}
