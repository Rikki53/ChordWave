// RadioStationMenu — the station sheet: long-press (or the row's menu
// button) on any station opens it through LocalMenuState, the way
// SongMenu opens for a song. Three rows, all real: Play / Pause, the
// favourite toggle, and Share (the station name + its stream URL as plain
// text, Mochy's shareText).
//
// The favourite flag is local rememberSaveable state seeded from the
// passed-in value (SongMenu's liked pattern): the sheet's heart flips at
// once, while the toggle itself still writes through RadioFavorites — the
// screen's flow picks the change up underneath.

package com.music.bitchord.ui.mochy.screens.radio

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.music.bitchord.R
import com.music.bitchord.radio.RadioStation
import com.music.bitchord.ui.mochy.component.Material3MenuGroup
import com.music.bitchord.ui.mochy.component.Material3MenuItemData
import com.music.bitchord.ui.mochy.component.SheetHeader
import com.music.bitchord.ui.mochy.component.SheetHeaderButton
import com.music.bitchord.ui.mochy.component.SheetHeaderGap
import com.music.bitchord.ui.mochy.player.menu.shareText

@Composable
fun RadioStationMenu(
    station: RadioStation,
    isCurrent: Boolean,
    isPlaying: Boolean,
    isFavorite: Boolean,
    onPlayPause: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var favorite by rememberSaveable { mutableStateOf(isFavorite) }
    val showingPause = isCurrent && isPlaying

    SheetHeader(
        thumbnailUrl = station.favicon?.takeIf { it.isNotBlank() },
        title = station.name,
        subtitle = metaOf(station).ifBlank { null },
        trailing = {
            SheetHeaderButton(
                icon = if (favorite) R.drawable.favorite else R.drawable.favorite_border,
                tint = if (favorite) MaterialTheme.colorScheme.error else Color.White,
                onClick = {
                    favorite = !favorite
                    onToggleFavorite()
                },
            )
        },
    )

    Spacer(Modifier.height(SheetHeaderGap))

    LazyColumn(
        contentPadding = PaddingValues(
            bottom = 8.dp + WindowInsets.systemBars.asPaddingValues().calculateBottomPadding(),
        ),
    ) {
        item {
            Material3MenuGroup(
                items = listOf(
                    Material3MenuItemData(
                        title = {
                            Text(
                                text = stringResource(
                                    if (showingPause) R.string.pause else R.string.play,
                                ),
                            )
                        },
                        icon = {
                            Icon(
                                painter = painterResource(
                                    if (showingPause) R.drawable.pause else R.drawable.play,
                                ),
                                contentDescription = null,
                            )
                        },
                        onClick = {
                            onDismiss()
                            onPlayPause()
                        },
                    ),
                    Material3MenuItemData(
                        title = {
                            Text(
                                text = stringResource(
                                    if (favorite) R.string.mochy_radio_remove_favourite
                                    else R.string.mochy_radio_add_favourite,
                                ),
                            )
                        },
                        icon = {
                            Icon(
                                painter = painterResource(
                                    if (favorite) R.drawable.favorite
                                    else R.drawable.favorite_border,
                                ),
                                contentDescription = null,
                            )
                        },
                        onClick = {
                            favorite = !favorite
                            onToggleFavorite()
                        },
                    ),
                    Material3MenuItemData(
                        title = { Text(text = stringResource(R.string.share)) },
                        icon = {
                            Icon(
                                painter = painterResource(R.drawable.share),
                                contentDescription = null,
                            )
                        },
                        onClick = {
                            onDismiss()
                            context.shareText("${station.name}\n${station.streamUrl}")
                        },
                    ),
                ),
            )
        }
    }
}
