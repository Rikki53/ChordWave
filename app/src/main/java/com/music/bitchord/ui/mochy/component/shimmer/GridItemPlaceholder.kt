// Ported from Mochy Music (GPL-3.0): ui/component/shimmer/GridItemPlaceholder.kt
// GridItemPlaceholder.kt — a grid card's shimmer stand-in: a square thumb
// plus two text bars, sized by the grid-size preference. Ported verbatim.
// (Mochy's spelling GridItemPlaceHolder is kept, so call sites match.)
// GridItemsSizeKey is not staged yet — NEEDS.md B5.

package com.music.bitchord.ui.mochy.component.shimmer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.music.bitchord.ui.mochy.constants.GridItemSize
import com.music.bitchord.ui.mochy.constants.GridItemsSizeKey
import com.music.bitchord.ui.mochy.constants.GridThumbnailHeight
import com.music.bitchord.ui.mochy.constants.SmallGridThumbnailHeight
import com.music.bitchord.ui.mochy.constants.ThumbnailCornerRadius
import com.music.bitchord.ui.mochy.utils.rememberEnumPreference

@Composable
fun GridItemPlaceHolder(
    modifier: Modifier = Modifier,
    thumbnailShape: Shape = RoundedCornerShape(ThumbnailCornerRadius),
    fillMaxWidth: Boolean = false,
) {
    val gridItemSize by rememberEnumPreference(GridItemsSizeKey, GridItemSize.BIG)
    val gridHeight = if (gridItemSize == GridItemSize.BIG) GridThumbnailHeight else SmallGridThumbnailHeight

    Column(
        modifier =
        if (fillMaxWidth) {
            modifier
                .padding(12.dp)
                .fillMaxWidth()
        } else {
            modifier
                .padding(12.dp)
                .width(gridHeight)
        },
    ) {
        Spacer(
            modifier =
            if (fillMaxWidth) {
                Modifier.fillMaxWidth()
            } else {
                Modifier.height(gridHeight)
            }.aspectRatio(1f)
                .clip(thumbnailShape)
                .background(MaterialTheme.colorScheme.onSurface),
        )

        Spacer(modifier = Modifier.height(6.dp))

        TextPlaceholder()

        TextPlaceholder()
    }
}
