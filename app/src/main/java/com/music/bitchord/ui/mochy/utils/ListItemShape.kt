// Ported from Mochy Music (GPL-3.0): utils/Utils.kt (listItemShape only)
// listItemShape — the grouped list-item shape: a lone row is rounded all
// round, the first row only on top, the last only on the bottom, middle
// rows square, so a stack reads as one connected card with smooth
// (squircle) corners. Mochy's reportException (same file) is NOT ported
// here — nothing in this dir needs it.
//
// NOTE FOR THE COORDINATOR: this lives in this dir (utils/ListItemShape.kt),
// not in phase2-search's ShapesCurve.kt — do not add a second copy there.

package com.music.bitchord.ui.mochy.utils

import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

fun listItemShape(index: Int, count: Int, radius: Dp = 24.dp): Shape {
    val smoothness = 60
    return when {
        count == 1 -> AbsoluteSmoothCornerShape(
            cornerRadiusTL = radius, smoothnessAsPercentTL = smoothness,
            cornerRadiusTR = radius, smoothnessAsPercentTR = smoothness,
            cornerRadiusBL = radius, smoothnessAsPercentBL = smoothness,
            cornerRadiusBR = radius, smoothnessAsPercentBR = smoothness
        )
        index == 0 -> AbsoluteSmoothCornerShape(
            cornerRadiusTL = radius, smoothnessAsPercentTL = smoothness,
            cornerRadiusTR = radius, smoothnessAsPercentTR = smoothness,
            cornerRadiusBL = 0.dp, smoothnessAsPercentBL = 0,
            cornerRadiusBR = 0.dp, smoothnessAsPercentBR = 0
        )
        index == count - 1 -> AbsoluteSmoothCornerShape(
            cornerRadiusTL = 0.dp, smoothnessAsPercentTL = 0,
            cornerRadiusTR = 0.dp, smoothnessAsPercentTR = 0,
            cornerRadiusBL = radius, smoothnessAsPercentBL = smoothness,
            cornerRadiusBR = radius, smoothnessAsPercentBR = smoothness
        )
        else -> RectangleShape
    }
}
