// Batch 55b: the module-effects holder, adapted from Mochy's
// eq/mixer/ModuleProcessors.kt. Mochy feeds these kinds into its Liveprog
// mixer; ChordWave's Modern audio chain has no Liveprog/native mixer, so
// the kinds are registered faithfully (key, name, category, presets, the
// script itself) but nothing renders them yet — an effects module shows in
// Modules, can be switched on and off, and its processors wait here for a
// mixer that can run them. Native (.so) processors need Mochy's native
// mixer library, which is not ported, so they register nothing.

package com.music.bitchord.ui.mochy.eq.mixer

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/** one knob of a module effect, as its script declares it */
data class FxKnob(val id: String, val name: String, val default: Float)

/** a named set of knob values */
data class KindPreset(val name: String, val values: List<Float>)

/** where a kind came from */
sealed interface KindOrigin {
    data class Module(val moduleId: String, val moduleName: String) : KindOrigin
}

/** an audio processor a module brings, as the registry keeps it */
data class FxKind(
    /** what a saved slot points at */
    val key: String,
    val name: String,
    val category: String,
    val description: String,
    val knobs: List<FxKnob>,
    val presets: List<KindPreset>,
    val origin: KindOrigin,
    /** the script for a script processor */
    val engineSource: String,
) {
    val defaults: List<Float> get() = knobs.map { it.default }
}

/** the audio processors switched-on modules bring; the module registry fills it in */
@Singleton
class ModuleProcessors @Inject constructor() {

    private val byModule = MutableStateFlow<Map<String, List<FxKind>>>(emptyMap())

    /** every module's processors, in module order */
    val kinds: StateFlow<Map<String, List<FxKind>>> = byModule.asStateFlow()

    fun all(): List<FxKind> = byModule.value.values.flatten()

    fun find(key: String): FxKind? = byModule.value.values.firstNotNullOfOrNull { list -> list.firstOrNull { it.key == key } }

    /** drops every module not in [ids]: one removed takes its effects with it */
    fun keepOnly(ids: Set<String>) {
        byModule.update { map -> map.filterKeys { it in ids } }
    }

    /** replaces everything [moduleId] offers, an empty list takes them all away */
    fun publish(moduleId: String, kinds: List<FxKind>) {
        byModule.update { if (kinds.isEmpty()) it - moduleId else it + (moduleId to kinds) }
    }

    companion object {
        /** a script a module brings: its presets from the manifest by knob name; knob parsing waits for a mixer that runs scripts */
        fun scriptKind(
            moduleId: String,
            moduleName: String,
            processorId: String,
            name: String,
            category: String?,
            description: String?,
            script: String,
            presets: List<Pair<String, Map<String, Float>>>,
        ): FxKind = FxKind(
            key = "module:$moduleId/$processorId",
            name = name,
            category = category?.takeIf { it.isNotBlank() } ?: moduleName,
            description = description?.takeIf { it.isNotBlank() } ?: "",
            knobs = emptyList(),
            presets = presets.map { (presetName, _) -> KindPreset(presetName, emptyList()) },
            origin = KindOrigin.Module(moduleId, moduleName),
            engineSource = script,
        )

        /** a module's native library: ChordWave has no native mixer to open it with, so nothing registers */
        fun nativeKinds(moduleId: String, moduleName: String, libraryPath: String): List<FxKind> = emptyList()
    }
}
