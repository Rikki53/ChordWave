// Ported from Mochy Music (GPL-3.0): ui/component/SortHeader.kt
// SortHeader.kt
//
// REWRITTEN without the expressive API: Mochy built the split button from
// SplitButtonLayout (ExperimentalMaterial3ExpressiveApi, forbidden here).
// Same signature and behaviour — a primary-coloured button showing the
// current sort, a second button with the arrow (toggles the sort order,
// or opens the menu when there is no order to toggle), and a dropdown of
// every sort value with a radio icon on the current one — built from two
// ordinary Buttons with joined corners and a DropdownMenu. The
// PlaylistSongSortType.CUSTOM check is kept.

package com.music.bitchord.ui.mochy.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.bitchord.R
import com.music.bitchord.ui.mochy.constants.PlaylistSongSortType

@Composable
inline fun <reified T : Enum<T>> SortHeader(
    sortType: T,
    sortDescending: Boolean,
    crossinline onSortTypeChange: (T) -> Unit,
    crossinline onSortDescendingChange: (Boolean) -> Unit,
    crossinline sortTypeText: (T) -> Int,
    modifier: Modifier = Modifier,
    showDescending: Boolean? = true,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    val displayDescending = showDescending == true && sortType != PlaylistSongSortType.CUSTOM

    val buttonColors = ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
    )

    Row(modifier = modifier.padding(vertical = 8.dp)) {
        Button(
            onClick = { menuExpanded = !menuExpanded },
            colors = buttonColors,
            shape = RoundedCornerShape(
                topStart = 20.dp, bottomStart = 20.dp,
                topEnd = 4.dp, bottomEnd = 4.dp,
            ),
            modifier = Modifier.widthIn(min = 120.dp),
        ) {
            Text(
                text = stringResource(sortTypeText(sortType)),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelLarge,
            )
        }
        if (displayDescending) {
            val rotation: Float by animateFloatAsState(
                targetValue = if (sortDescending) 180f else 0f,
                label = "Trailing Icon Rotation",
            )
            Button(
                onClick = { onSortDescendingChange(!sortDescending) },
                colors = buttonColors,
                shape = RoundedCornerShape(
                    topStart = 4.dp, bottomStart = 4.dp,
                    topEnd = 20.dp, bottomEnd = 20.dp,
                ),
                modifier = Modifier
                    .padding(start = 2.dp)
                    .semantics {
                        stateDescription = if (sortDescending) "Descending" else "Ascending"
                        contentDescription = "Toggle sort order"
                    },
            ) {
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowDown,
                    modifier = Modifier
                        .size(18.dp)
                        .graphicsLayer {
                            this.rotationZ = rotation
                        },
                    contentDescription = null,
                )
            }
        } else {
            val rotation: Float by animateFloatAsState(
                targetValue = if (menuExpanded) 180f else 0f,
                label = "Trailing Icon Rotation",
            )
            Button(
                onClick = { menuExpanded = !menuExpanded },
                colors = buttonColors,
                shape = RoundedCornerShape(
                    topStart = 4.dp, bottomStart = 4.dp,
                    topEnd = 20.dp, bottomEnd = 20.dp,
                ),
                modifier = Modifier
                    .padding(start = 2.dp)
                    .semantics {
                        stateDescription = if (menuExpanded) "Expanded" else "Collapsed"
                        contentDescription = "Show sort options"
                    },
            ) {
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowDown,
                    modifier = Modifier
                        .size(18.dp)
                        .graphicsLayer {
                            this.rotationZ = rotation
                        },
                    contentDescription = null,
                )
            }
        }
    }

    DropdownMenu(
        expanded = menuExpanded,
        onDismissRequest = { menuExpanded = false },
        modifier = Modifier.widthIn(min = 172.dp),
    ) {
        enumValues<T>().forEach { type ->
            DropdownMenuItem(
                text = {
                    Text(
                        text = stringResource(sortTypeText(type)),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Normal,
                    )
                },
                trailingIcon = {
                    Icon(
                        painter = painterResource(
                            if (sortType == type) R.drawable.radio_button_checked
                            else R.drawable.radio_button_unchecked
                        ),
                        contentDescription = null,
                    )
                },
                onClick = {
                    onSortTypeChange(type)
                    menuExpanded = false
                },
            )
        }
    }
}
