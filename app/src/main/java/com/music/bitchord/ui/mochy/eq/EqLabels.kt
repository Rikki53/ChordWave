// Frequency / gain labels, ported from Mochy Music (GPL-3.0):
// viewmodels/AudioLabViewModel.kt (frequencyLabel / gainLabel /
// filterTypeLabel, verbatim). Shared by the graph and bands editors.
package com.music.bitchord.ui.mochy.eq

import com.music.bitchord.ui.mochy.eq.data.FilterType
import kotlin.math.abs

/** how a node's frequency reads on a chip: 31Hz, 120Hz, 1.0k, 16k */
fun frequencyLabel(hz: Double): String = when {
    hz >= 10_000 -> "${(hz / 1000).toInt()}k"
    hz >= 1000 -> String.format("%.1fk", hz / 1000)
    else -> "${hz.toInt()}Hz"
}

fun gainLabel(db: Double): String = (if (db > 0.04) "+" else if (db < -0.04) "−" else "") + String.format("%.1f dB", abs(db))

fun filterTypeLabel(type: FilterType): String = when (type) {
    FilterType.PK -> "Peak"
    FilterType.LSC -> "Low shelf"
    FilterType.HSC -> "High shelf"
    FilterType.LPQ -> "Low pass"
    FilterType.HPQ -> "High pass"
}
