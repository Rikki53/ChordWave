// Ported from Mochy Music (GPL-3.0): ui/component/Items.kt (extract)
// ItemsThumbnails.kt — thumbnail composables extracted from Items.kt (1,534 lines).
// The rest of Items.kt (list/grid item rows, LocalSongs/Artists/Albums grids, object Icon,
// overlay buttons, LocalThumbnail) was NOT ported: its dependency closure (SwipeActions 647
// lines, SongSwipeBox, db entities, LocalPlayerConnection, …) is not manageable yet.
// See NEEDS.md for the full list of skipped symbols.

package com.music.bitchord.ui.mochy.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandIn
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.util.fastForEachIndexed
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import com.music.bitchord.R
import com.music.bitchord.ui.mochy.constants.ThumbnailCornerRadius
import com.music.bitchord.ui.mochy.utils.resize
import com.music.bitchord.ui.mochy.LocalCropAlbumArt

const val ActiveBoxAlpha = 0.6f

@Composable
fun ItemThumbnail(
    thumbnailUrl: String?,
    isActive: Boolean,
    isPlaying: Boolean,
    shape: Shape,
    modifier: Modifier = Modifier,
    albumIndex: Int? = null,
    isSelected: Boolean = false,
    thumbnailRatio: Float = 1f
) = ItemThumbnail(thumbnailUrl, isActive, { isPlaying }, shape, modifier, albumIndex, isSelected, thumbnailRatio)

// isPlaying is read only inside the indicator, so a play or pause leaves the cover image alone
@Composable
fun ItemThumbnail(
    thumbnailUrl: String?,
    isActive: Boolean,
    isPlaying: () -> Boolean,
    shape: Shape,
    modifier: Modifier = Modifier,
    albumIndex: Int? = null,
    isSelected: Boolean = false,
    thumbnailRatio: Float = 1f
) {
    val cropAlbumArt = LocalCropAlbumArt.current

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxSize()
            .aspectRatio(thumbnailRatio)
            .clip(shape)
    ) {
        if (albumIndex == null) {
            // the box above already clips to the shape, a second clip here was just one more layer per cover
            AsyncImage(
                model = thumbnailUrl?.resize(544, 544),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        if (albumIndex != null) {
            AnimatedVisibility(
                visible = !isActive,
                enter = fadeIn() + expandIn(expandFrom = Alignment.Center),
                exit = shrinkOut(shrinkTowards = Alignment.Center) + fadeOut()
            ) {
                Text(
                    text = albumIndex.toString(),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }

        if (isSelected) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(1f)
                    .clip(shape)
                    .background(Color.Black.copy(alpha = 0.5f))
            ) {
                Icon(
                    painter = painterResource(R.drawable.done),
                    contentDescription = null
                )
            }
        }

        PlayingIndicatorBox(
            isActive = isActive,
            playWhenReady = isPlaying,
            color = if (albumIndex != null) MaterialTheme.colorScheme.onBackground else Color.White,
            dimColor = if (albumIndex != null) Color.Transparent else Color.Black,
            shape = shape,
            modifier = Modifier.fillMaxSize()
        )
    }
}


@Composable
fun PlaylistThumbnail(
    thumbnails: List<String>,
    size: Dp,
    placeHolder: @Composable () -> Unit,
    shape: Shape,
    cacheKey: String? = null,
    modifier: Modifier = Modifier,
) {
    val cropAlbumArt = LocalCropAlbumArt.current

    when (thumbnails.size) {
        0 -> Box(
            contentAlignment = Alignment.Center,
            modifier = modifier
                .size(size)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainer)
        ) {
            placeHolder()
        }
        1 -> AsyncImage(
            model = thumbnails[0].resize(544, 544),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            placeholder = painterResource(R.drawable.queue_music),
            error = painterResource(R.drawable.queue_music),
            modifier = modifier
                .size(size)
                .clip(shape)
        )
        else -> Box(
            modifier = modifier
                .size(size)
                .clip(shape)
        ) {
            listOf(
                Alignment.TopStart,
                Alignment.TopEnd,
                Alignment.BottomStart,
                Alignment.BottomEnd
            ).fastForEachIndexed { index, alignment ->
                AsyncImage(
                    model = thumbnails.getOrNull(index)?.resize(544, 544),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    placeholder = painterResource(R.drawable.queue_music),
                    error = painterResource(R.drawable.queue_music),
                    modifier = Modifier
                        .align(alignment)
                        .size(size / 2)
                )
            }
        }
    }
}


@Composable
fun LikedSongsThumbnail(
    size: Dp,
    shape: Shape = RoundedCornerShape(ThumbnailCornerRadius),
    modifier: Modifier = Modifier,
) {
    LikedCover(
        modifier = modifier
            .size(size)
            .clip(shape),
    )
}
