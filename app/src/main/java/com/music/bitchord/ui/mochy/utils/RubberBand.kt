// Ported from Mochy Music (GPL-3.0): ui/utils/RubberBand.kt
package com.music.bitchord.ui.mochy.utils

import kotlin.math.abs
import kotlin.math.sign

// the iphone's rubber band: the further past the end, the less each move adds, never more than reach
internal fun rubberBand(over: Float, reach: Float): Float {
    if (over == 0f) return 0f
    val d = abs(over)
    return sign(over) * (1f - 1f / (d * 0.55f / reach + 1f)) * reach
}
