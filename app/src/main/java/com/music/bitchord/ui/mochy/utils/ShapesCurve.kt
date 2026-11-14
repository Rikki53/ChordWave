// Ported from Mochy Music (GPL-3.0): utils/ShapesCurve.kt (grouped-shape helpers only)
// Grouped list-item shapes: the first row has rounded top corners, the
// last row rounded bottom corners, middle rows nearly square — so a stack
// of rows reads as one connected card. (ShapesCurve.kt also holds
// listItemColors(); not needed by the Search port.)

package com.music.bitchord.ui.mochy.utils

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

private const val ConnectedCornerRadius = 4
private const val EndCornerRadius = 16

fun leadingItemShape(): RoundedCornerShape = RoundedCornerShape(
    topStart = EndCornerRadius.dp,
    topEnd = EndCornerRadius.dp,
    bottomStart = ConnectedCornerRadius.dp,
    bottomEnd = ConnectedCornerRadius.dp
)

fun middleItemShape(): RoundedCornerShape = RoundedCornerShape(ConnectedCornerRadius.dp)

fun endItemShape(): RoundedCornerShape = RoundedCornerShape(
    topStart = ConnectedCornerRadius.dp,
    topEnd = ConnectedCornerRadius.dp,
    bottomStart = EndCornerRadius.dp,
    bottomEnd = EndCornerRadius.dp
)

fun getGroupedShape(index: Int, count: Int): Shape {
    return when {
        index == 0 -> leadingItemShape()
        index == count - 1 -> endItemShape()
        else -> middleItemShape()
    }
}
