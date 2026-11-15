// Ported from Mochy Music (GPL-3.0): eq/AudioLabStore.kt (AutoEqCorrection only).
//
// In Mochy the correction sits UNDER the user's own curve (both run in the
// processor). ChordWave's engine has one curve, so here the correction
// BECOMES the manual curve when applied (see EqualizerViewModel): the
// previous bands are remembered and restored when the correction is
// switched off or removed. The fitter's preamp is stored but not applied —
// ChordWave sets headroom automatically (EqCurve.preampDb).
package com.music.bitchord.ui.mochy.eq.autoeq

import com.music.bitchord.ui.mochy.eq.data.ParametricEQBand
import kotlinx.serialization.Serializable

/** a headphone correction picked from the autoeq database, with the filters it came to */
@Serializable
data class AutoEqCorrection(
    val model: String,
    val source: String,
    val rig: String? = null,
    val path: String,
    val form: HeadphoneForm = HeadphoneForm.OverEar,
    val tuning: AutoEqTuning = AutoEqTuning(),
    val filters: List<ParametricEQBand>,
    val preamp: Double,
)

internal fun AutoEqCorrection.toEntry() = AutoEqEntry(model, source, rig, path, form)
