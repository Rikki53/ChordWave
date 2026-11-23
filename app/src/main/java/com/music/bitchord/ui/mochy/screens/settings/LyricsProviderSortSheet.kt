// Ported from Mochy Music (GPL-3.0): ui/screens/settings/LyricsProviderSortSheet.kt
// Adapted to ChordWave's engine:
// - Rows are LyricsSource (the engine's own enum), shown by source.label, seeded from
//   AppSettings.lyricsSourceOrder — not Mochy's string provider names.
// - Each row also carries an enable toggle: a source can be switched off here as well as
//   reordered, writing AppSettings.lyricsSources on Done. Mochy had no per-source switch
//   in this sheet; the toggle keeps the sheet as the one place lyrics sourcing is
//   arranged (the Classic Lyrics Settings screen has the same switches, but Modern has
//   no equivalent).
// - Done persists both the order and the enabled set itself; there is no onDone
//   callback, because AppSettings is the single home for both.
// - SheetTitle does not exist in ChordWave; the title is a plain Text like the
//   other settings sheets.

package com.music.bitchord.ui.mochy.screens.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.bitchord.R
import com.music.bitchord.data.lyrics.LyricsSource
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.ui.mochy.component.PopupSheetHandle
import com.music.bitchord.ui.mochy.component.PopupSheetState
import com.music.bitchord.ui.mochy.component.SheetPillButton
import com.music.bitchord.ui.mochy.player.menu.MenuRowSurface
import sh.calvin.reorderable.ReorderableCollectionItemScope
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

// the lyrics sources top to bottom in the order they're trusted, dragged by their lines; done saves the order and which are on
fun PopupSheetState.showLyricsProviderSortSheet() {
    // shared by the list and the pinned button, which compose apart
    val sorted = mutableStateOf(LyricsSource.ordered(AppSettings.lyricsSourceOrder.value))
    val enabled = mutableStateOf(AppSettings.lyricsSources.value)
    var sheet: PopupSheetHandle? = null
    sheet = showWithHandle(
        buttonBar = {
            SheetPillButton(
                text = stringResource(R.string.lyrics_sort_done),
                onClick = {
                    AppSettings.setLyricsSourceOrder(sorted.value)
                    AppSettings.setLyricsSources(enabled.value)
                    sheet?.dismiss()
                },
            )
        },
    ) {
        Text(
            text = stringResource(R.string.lyrics_sort_title),
            color = Color.White,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp),
        )
        Spacer(Modifier.height(16.dp))
        ProviderSortList(sorted, enabled, Modifier.weight(1f, fill = false))
    }
}

@Composable
private fun ProviderSortList(
    sorted: MutableState<List<LyricsSource>>,
    enabled: MutableState<Set<LyricsSource>>,
    modifier: Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        val current = sorted.value
        val fromIndex = current.indexOf(from.key)
        val toIndex = current.indexOf(to.key)
        if (fromIndex < 0 || toIndex < 0) return@rememberReorderableLazyListState
        sorted.value = current.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
        haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
    }
    LazyColumn(
        state = listState,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 12.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        items(sorted.value, key = { it }, contentType = { "provider" }) { source ->
            ReorderableItem(reorderState, key = source) { isDragging ->
                ProviderRow(
                    source = source,
                    on = source in enabled.value,
                    onToggle = {
                        enabled.value = if (source in enabled.value) {
                            enabled.value - source
                        } else {
                            enabled.value + source
                        }
                    },
                    isDragging = isDragging,
                    onPickUp = { haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate) },
                    onDrop = { haptic.performHapticFeedback(HapticFeedbackType.GestureEnd) },
                )
            }
        }
    }
}

// one source as the mock draws it: a check to switch it off, its name on a grey pill, the three lines to grab on the right
@Composable
private fun ReorderableCollectionItemScope.ProviderRow(
    source: LyricsSource,
    on: Boolean,
    onToggle: () -> Unit,
    isDragging: Boolean,
    onPickUp: () -> Unit,
    onDrop: () -> Unit,
) {
    val lift = animateFloatAsState(if (isDragging) 1f else 0f, spring(dampingRatio = 0.7f, stiffness = 600f), label = "lift")
    val toggleDescription = stringResource(
        if (on) R.string.lyrics_sort_turn_off else R.string.lyrics_sort_turn_on,
        source.label,
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 46.dp)
            .graphicsLayer {
                scaleX = 1f + 0.02f * lift.value
                scaleY = 1f + 0.02f * lift.value
            }
            .clip(RoundedCornerShape(14.dp))
            .background(if (isDragging) lerp(MenuRowSurface, Color.White, 0.1f) else MenuRowSurface)
            // holding anywhere on the row picks it up too, the lines are just the obvious place
            .longPressDraggableHandle(onDragStarted = { onPickUp() }, onDragStopped = { onDrop() })
            .padding(start = 8.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onToggle,
                )
                .semantics { contentDescription = toggleDescription },
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(if (on) Color.White else Color.Transparent)
                    .border(1.5.dp, Color.White.copy(alpha = if (on) 1f else 0.4f), CircleShape),
            ) {
                if (on) {
                    Icon(
                        painter = painterResource(R.drawable.check),
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
        Text(
            text = source.label,
            color = Color.White.copy(alpha = if (on) 1f else 0.45f),
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(46.dp)
                .draggableHandle(onDragStarted = { onPickUp() }, onDragStopped = { onDrop() }),
        ) {
            Icon(
                painter = painterResource(R.drawable.queue_drag_handle),
                contentDescription = null,
                tint = Color.White.copy(alpha = if (isDragging) 0.9f else 0.5f),
                modifier = Modifier.size(22.dp),
            )
        }
    }
}
