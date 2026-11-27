// the rows switched-on modules add to the player's menu, kept where the menu can read them without a view model

package com.music.bitchord.ui.mochy.modules.skin

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** one module's row, with the skin that resolves its icon and the module whose page it may open */
@Immutable
class ModuleMenuAction(val moduleId: String, val skin: ModuleSkin, val action: SkinAction)

object ModuleMenu {
    // a flow, not compose snapshot state: publish() runs on the registry refresh path (view model
    // init, module switches), outside any composition snapshot, where reading snapshot state can
    // throw when the state's record was created in a snapshot that never applied
    private val _actions = MutableStateFlow<List<ModuleMenuAction>>(emptyList())
    val actions: StateFlow<List<ModuleMenuAction>> = _actions.asStateFlow()

    // set on every refresh of the switches; the same list again changes nothing, so the menu doesn't redraw for it
    internal fun publish(skins: List<ModuleSkin>) {
        val next = skins.flatMap { skin -> skin.actions.map { ModuleMenuAction(skin.moduleId, skin, it) } }
        if (next.map { it.moduleId to it.action } != _actions.value.map { it.moduleId to it.action }) _actions.value = next
    }
}
