// Ported from Mochy Music (GPL-3.0): ui/utils/ShapeUtils.kt
// ShapeUtils.kt

package com.music.bitchord.ui.mochy.utils

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.ui.unit.dp

fun CornerBasedShape.top(): CornerBasedShape =
    copy(bottomStart = CornerSize(0.dp), bottomEnd = CornerSize(0.dp))
