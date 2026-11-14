// ActiveGlass.kt — only the ActiveGlass class from Mochy's
// modules/skin/ActiveSkin.kt. The full skin system (ActiveSkin object,
// SkinParser, module loading) is a Phase 3 decision; ModuleGlass only
// needs this type, and with no skins installed the value is always null.

package com.music.bitchord.ui.mochy.modules.skin

import androidx.compose.runtime.Immutable

/** a look's glass, compiled and ready for the bars: the shader is shared, each bar builds its own effect from it */
@Immutable
class ActiveGlass(val moduleId: String, val shader: Any, val blurDp: Float, val marginDp: Float, val uniforms: Map<String, Float>)
