// The sources list's rows, in Mochy's modules-page idiom: one rounded card
// per source (Mochy's ModuleCard: 26dp corners, #131314, a drag handle drawn
// as three lines beside the card, swipe left to remove), a dashed slot to
// add another, and a big grey section title.
//
// The drag mechanics are the classic screen's ReorderableAddons, not
// Mochy's ModuleList: Mochy's list sits on fixed-height slots, and a
// source row's height varies — a status line wraps to as many lines as its
// message needs, so an addon with an error stands taller than its
// neighbours. The classic mechanics measure each row's own height, so a
// tall row still drags correctly. What changed is only the wrapper: a card
// plus its gap instead of a row plus its hairline.

package com.music.bitchord.ui.mochy.screens.sources

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.music.bitchord.R
import com.music.bitchord.data.settings.AudioQuality
import com.music.bitchord.data.sources.SourceConfig
import com.music.bitchord.data.sources.SourceHealth
import com.music.bitchord.data.sources.SourceKind
import com.music.bitchord.playback.audio.LosslessOutput
import com.music.bitchord.ui.mochy.component.AppSwitch
import com.music.bitchord.ui.mochy.component.SwipeAction
import com.music.bitchord.ui.mochy.component.SwipeActionsBox
import com.music.bitchord.ui.mochy.component.SwipeRemoveColor
import com.music.bitchord.ui.mochy.screens.audiolab.LabColors
import com.music.bitchord.ui.mochy.theme.InterFontFamily

/** The gap between source cards, and the trailing gap each reorderable wrapper measures. */
val SourceCardGap = 12.dp

/** Mochy's module-row colour, shared by the cards and the dashed slot. */
private val SourceCardColor = Color(0xFF131314)

/** A card being dragged reads a step lighter, as Mochy's lifted module card does. */
private val SourceCardLiftedColor = Color(0xFF222225)

private val SourceCardShape = RoundedCornerShape(26.dp)

private val DragHandleWidth = 44.dp

/**
 * How far past a neighbour the finger has to carry a row before the two trade
 * places, as a share of the height of the row being passed.
 *
 * Deliberately more than half, for the reason spelled out in
 * [LyricsSourcesDialog][com.music.bitchord.ui.components.LyricsSourcesDialog]:
 * at exactly half, a row that has just swapped lands precisely on the boundary
 * of swapping back, so the shake in any real finger flips it back and forth for
 * as long as it is held near a crossing.
 */
private const val SWAP_THRESHOLD = 0.6f

/** A section's big grey title, Mochy's ModuleSectionTitle, with an optional small line under it. */
@Composable
fun SourcesSectionTitle(title: String, subtitle: String? = null) {
    Column(Modifier.padding(start = 2.dp, bottom = 14.dp)) {
        Text(
            title, fontFamily = InterFontFamily, fontWeight = FontWeight.Bold, fontSize = 24.sp,
            letterSpacing = (-0.6).sp, color = Color(0xFFB4B4B8),
        )
        subtitle?.let {
            Text(
                it, fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                color = LabColors.Secondary,
            )
        }
    }
}

/**
 * The addon rows, draggable by their handles to set which is asked first.
 *
 * The mechanics are the classic screen's: the gesture keeps only how far the
 * finger has travelled and which slot it started on, and both where the row is
 * drawn and which slot it occupies are derived from those two numbers, so they
 * cannot drift apart however many swaps happen along the way.
 *
 * Two things differ from the classic screen. The list is on a vertically
 * scrolling page rather than in a dialog, which is why the drag lives on the
 * handle alone — [detectDragGestures] there consumes the vertical drag before
 * the scroll container sees it, while a drag anywhere on the row would make
 * the list impossible to scroll past. And the order is only written back when
 * the gesture ends: [SourceRegistry.reorderAddons] persists to encrypted prefs
 * and rebuilds the source instances, which is not work to do on every frame of
 * a drag.
 *
 * No handle is drawn for a single addon. There is nothing to reorder, and a
 * grip that cannot move anything is a control that lies. A swipe left still
 * removes it — the swipe is on the card, not the handle.
 */
@Composable
fun ReorderableAddonList(
    addons: List<SourceConfig>,
    onReorder: (List<SourceConfig>) -> Unit,
    onRemove: (SourceConfig) -> Unit,
    card: @Composable (position: Int, config: SourceConfig, isDragging: Boolean) -> Unit,
) {
    if (addons.isEmpty()) return
    if (addons.size == 1) {
        val only = addons.first()
        val removeLabel = stringResource(R.string.remove)
        SwipeActionsBox(
            end = { SwipeAction(R.drawable.delete, SwipeRemoveColor, removeLabel) { onRemove(only) } },
            slab = false,
        ) {
            card(1, only, false)
        }
        Spacer(Modifier.height(SourceCardGap))
        return
    }

    var liveOrder by remember(addons) { mutableStateOf(addons) }
    var dragged by remember { mutableStateOf<String?>(null) }

    /** Distance the finger has covered since this gesture began, in pixels. */
    var totalDrag by remember { mutableFloatStateOf(0f) }

    /** Which slot of [liveOrder] it began on. */
    var startIndex by remember { mutableIntStateOf(0) }

    // Each wrapper's height — the card row plus its trailing gap, measured off
    // the wrapper holding both — by addon id. Per row rather than one shared
    // pitch, because a row's status line wraps to as many lines as its message
    // needs, so an addon with an error stands taller than its neighbours.
    // Frozen for the duration of a gesture so a relayout mid-drag cannot move
    // the boundaries the drag is being measured against underneath it.
    val heights = remember { mutableStateMapOf<String, Float>() }
    var lockedHeights by remember { mutableStateOf<Map<String, Float>>(emptyMap()) }

    /** How far the dragged row's slot has moved since the gesture began: the heights of the rows it has passed. */
    var slotShift by remember { mutableFloatStateOf(0f) }

    val removeLabel = stringResource(R.string.remove)

    liveOrder.forEachIndexed { index, config ->
        // Keyed on the config's id so this composable — gesture and all —
        // follows that addon from slot to slot. Matched by position instead,
        // the first swap would change the key under the finger, restart the
        // `pointerInput` coroutine mid-gesture, and stall the drag one swap
        // after it started.
        key(config.id) {
            val isDragging = config.id == dragged
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .zIndex(if (isDragging) 1f else 0f)
                    .onSizeChanged { heights[config.id] = it.height.toFloat() }
                    .graphicsLayer {
                        // Read in the draw phase, so a drag moves the row
                        // without recomposing the list at all. The row sits
                        // wherever the finger has carried it from where it was
                        // picked up, less whatever the swaps have already moved
                        // its slot — so a swap relocates the slot and shortens
                        // this by exactly as much, and the row does not budge.
                        translationY = if (isDragging) totalDrag - slotShift else 0f
                    },
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    SwipeActionsBox(
                        modifier = Modifier.weight(1f),
                        end = {
                            SwipeAction(R.drawable.delete, SwipeRemoveColor, removeLabel) {
                                onRemove(config)
                            }
                        },
                        enabled = !isDragging,
                        slab = false,
                    ) {
                        card(index + 1, config, isDragging)
                    }
                    SourceDragHandle(
                        Modifier
                            .width(DragHandleWidth)
                            .fillMaxHeight()
                            // A constant key on purpose: the row is pinned by
                            // [key] above, so nothing about a reorder should
                            // restart this coroutine.
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = {
                                        dragged = config.id
                                        totalDrag = 0f
                                        slotShift = 0f
                                        startIndex = liveOrder.indexOfFirst { it.id == config.id }
                                        lockedHeights = heights.toMap()
                                    },
                                    onDrag = { change, delta ->
                                        change.consume()
                                        val height = { id: String -> lockedHeights[id] ?: 0f }
                                        var at = liveOrder.indexOfFirst { it.id == config.id }
                                        if (at < 0 || lockedHeights.isEmpty()) return@detectDragGestures

                                        // Held past either end the row stops
                                        // there under the finger, rather than
                                        // running off the list and having to be
                                        // dragged all the way back. The ends are
                                        // the rows above and below the slot it
                                        // started in, taken from the current
                                        // order with the swaps undone.
                                        val others = liveOrder.filterNot { it.id == config.id }
                                        totalDrag = (totalDrag + delta.y).coerceIn(
                                            -others.take(startIndex).sumOf { height(it.id).toDouble() }.toFloat(),
                                            others.drop(startIndex).sumOf { height(it.id).toDouble() }.toFloat(),
                                        )

                                        // A loop, not an `if`: one pointer event
                                        // can cover several rows when the finger
                                        // is quick, and settling one row per
                                        // event would leave the list trailing.
                                        // A swap with a neighbour moves the slot
                                        // by that neighbour's height, so the
                                        // threshold is a share of the row being
                                        // passed, whatever height either row is.
                                        while (true) {
                                            val offset = totalDrag - slotShift
                                            val below = liveOrder.getOrNull(at + 1)?.let { height(it.id) }
                                            val above = liveOrder.getOrNull(at - 1)?.let { height(it.id) }
                                            if (below != null && below > 0f && offset > below * SWAP_THRESHOLD) {
                                                liveOrder = liveOrder.toMutableList()
                                                    .apply { add(at + 1, removeAt(at)) }
                                                slotShift += below
                                                at++
                                            } else if (above != null && above > 0f && offset < -above * SWAP_THRESHOLD) {
                                                liveOrder = liveOrder.toMutableList()
                                                    .apply { add(at - 1, removeAt(at)) }
                                                slotShift -= above
                                                at--
                                            } else {
                                                break
                                            }
                                        }
                                    },
                                    onDragEnd = {
                                        dragged = null
                                        totalDrag = 0f
                                        slotShift = 0f
                                        onReorder(liveOrder)
                                    },
                                    onDragCancel = {
                                        dragged = null
                                        totalDrag = 0f
                                        slotShift = 0f
                                        liveOrder = addons
                                    },
                                )
                            },
                    )
                }
                // After the row, not before it, so every wrapper is exactly one
                // row plus one gap — the height the drag measures it by.
                Spacer(Modifier.height(SourceCardGap))
            }
        }
    }
}

// The mock's three lines beside each card, Mochy's ModuleList DragHandle.
@Composable
private fun SourceDragHandle(modifier: Modifier) {
    Canvas(modifier) {
        val w = 22.dp.toPx()
        val x0 = size.width - w - 2.dp.toPx()
        val gap = 7.dp.toPx()
        val c = Color.White.copy(alpha = 0.55f)
        for (i in -1..1) {
            val y = size.height / 2 + i * gap
            drawLine(
                color = c,
                start = androidx.compose.ui.geometry.Offset(x0, y),
                end = androidx.compose.ui.geometry.Offset(x0 + w, y),
                strokeWidth = 2.dp.toPx(),
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
            )
        }
    }
}

/**
 * One source as the modules page draws it: its position in the order, its
 * icon, its name, how it is doing, and the switch — or "Always on" where no
 * switch would be honest.
 */
@Composable
fun SourceRowCard(
    position: Int,
    config: SourceConfig,
    health: SourceHealth?,
    onClick: (() -> Unit)?,
    onChooseQuality: () -> Unit,
    /** Explains the "Outdated addon" line, which only an addon with [needsUpdate] shows. */
    onExplainOutdated: () -> Unit,
    /** Null for a source that cannot be switched off, which gets a label instead. */
    onToggle: ((Boolean) -> Unit)?,
    /** On, but skipped by the ceiling the current connection is set to. */
    skippedByQuality: Boolean = false,
    /** On, but its addon needs a lossless output and this is what is connected instead. Null when not waiting. */
    awaitingOutput: LosslessOutput.State? = null,
    /** An addon that is out of date (no `bitchordAuth` in its manifest). Still used; flagged on a line of its own. */
    needsUpdate: Boolean = false,
    /** Which of the two ceilings [ceiling] is, so the row can name it. */
    onMetered: Boolean = false,
    ceiling: AudioQuality = AudioQuality.LOSSLESS,
    /** Held in a drag: the card reads a step lighter. */
    lifted: Boolean = false,
) {
    // Dimmed for the same reason an off source is: it is not in the walk. The
    // switch stays where the user left it, so the row reads "on, but not
    // today" rather than "off".
    val dimmed = !config.enabled || skippedByQuality || awaitingOutput != null
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SourceCardShape)
            .background(if (lifted) SourceCardLiftedColor else SourceCardColor)
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$position",
            fontFamily = InterFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            color = Color.White.copy(alpha = 0.45f),
            modifier = Modifier
                .width(18.dp)
                .alpha(if (dimmed) 0.4f else 1f),
        )
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.08f))
                .alpha(if (dimmed) 0.4f else 1f),
        ) {
            Icon(
                painter = painterResource(
                    when (config.kind) {
                        SourceKind.ADDON -> R.drawable.puzzle
                        SourceKind.CUSTOM_MODULE -> R.drawable.puzzle
                        SourceKind.MODULE -> R.drawable.puzzle
                        SourceKind.JIOSAAVN -> R.drawable.graphic_eq
                        SourceKind.YOUTUBE -> R.drawable.play
                    },
                ),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(
            Modifier
                .weight(1f)
                .alpha(if (dimmed) 0.4f else 1f),
        ) {
            Text(
                text = config.displayName,
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                letterSpacing = (-0.3).sp,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // Read off the stored manifest, so it can still be set while the
            // addon is down or waiting on a lossless output. One line whatever
            // the label's length, so it adds the same height to every addon;
            // the full label is in the list it opens.
            val quality = config.activeQuality
            if (config.kind == SourceKind.ADDON && config.isComplete && quality != null) {
                Row(
                    modifier = Modifier
                        .clickable(
                            onClickLabel = stringResource(R.string.audio_quality),
                            onClick = onChooseQuality,
                        )
                        .padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.tune),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = quality.label,
                        fontFamily = InterFontFamily,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(2.dp))
                    Icon(
                        painter = painterResource(R.drawable.ic_chevron_right),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(10.dp),
                    )
                }
            }
            Text(
                // The ceiling outranks the health line: a source that isn't
                // going to be asked at all is not usefully described by
                // whether its server answered a probe.
                text = when {
                    // Outranks the ceiling: this one is not reached on *any*
                    // connection until the output changes.
                    awaitingOutput != null -> when {
                        awaitingOutput.bluetoothCodecUnknown ->
                            stringResource(R.string.source_needs_lossless_bt_unknown)
                        awaitingOutput.bluetoothCodec != null ->
                            stringResource(R.string.source_needs_lossless_bt_codec, awaitingOutput.bluetoothCodec)
                        else -> stringResource(R.string.source_needs_lossless_output)
                    }
                    skippedByQuality -> stringResource(
                        R.string.source_skipped_by_quality,
                        stringResource(if (onMetered) R.string.mobile_data else R.string.wifi),
                        ceiling.localizedLabel(),
                    )
                    else -> config.statusLine(health)
                },
                fontFamily = InterFontFamily,
                fontSize = 13.sp,
                color = when {
                    // A warning, not a fault: the addon is fine, the output is
                    // what has to change — but it is something to act on.
                    awaitingOutput != null -> MaterialTheme.colorScheme.error
                    // Only a rejection is coloured, and only when it is what
                    // the line actually says. A server that is merely down
                    // will be up again without anyone doing anything, and
                    // painting that red trains people to ignore the colour by
                    // the time it means something.
                    skippedByQuality -> Color.White.copy(alpha = 0.6f)
                    health is SourceHealth.Rejected -> MaterialTheme.colorScheme.error
                    else -> Color.White.copy(alpha = 0.6f)
                },
                // No line limit: an error is only useful read in full, so the
                // row grows to fit it. [ReorderableAddonList] measures each row's
                // own height, so a tall row still drags correctly.
            )
            // The addon's own `allowDownloads`, on a line of its own and both
            // ways round, so whether downloads come from it is answered here
            // rather than found out from where a downloaded file came from.
            // Read off the stored manifest, so it holds while the server is
            // down too.
            if (config.kind == SourceKind.ADDON && config.isComplete) {
                Row(
                    modifier = Modifier.padding(top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.download),
                        contentDescription = null,
                        tint = Color.White.copy(alpha = if (config.allowDownloads) 0.6f else 0.35f),
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = stringResource(
                            if (config.allowDownloads) R.string.source_downloads_on else R.string.source_downloads_off,
                        ),
                        fontFamily = InterFontFamily,
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.6f),
                        maxLines = 1,
                    )
                }
            }
            // A caution, not a block: the addon still works, but its developer
            // should update it. Short here, with the full explanation a tap
            // away, so it costs the row one line.
            if (needsUpdate) {
                Row(
                    modifier = Modifier
                        .clickable(
                            onClickLabel = stringResource(R.string.source_outdated),
                            onClick = onExplainOutdated,
                        )
                        .padding(top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.warning),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.source_outdated),
                        fontFamily = InterFontFamily,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 1,
                    )
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        if (onToggle == null) {
            Text(
                text = stringResource(R.string.always_on),
                fontFamily = InterFontFamily,
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.6f),
            )
        } else {
            AppSwitch(
                checked = config.enabled,
                onCheckedChange = onToggle,
            )
        }
    }
}

@Composable
private fun AudioQuality.localizedLabel(): String = stringResource(
    when (this) {
        AudioQuality.LOW -> R.string.low
        AudioQuality.MEDIUM -> R.string.medium
        AudioQuality.HIGH -> R.string.high
        AudioQuality.LOSSLESS -> R.string.lossless
    },
)

/** The second line of a row: what this source is, or what is wrong with it. */
@Composable
private fun SourceConfig.statusLine(health: SourceHealth?): String = when {
    !isComplete -> stringResource(R.string.source_setup_required)
    kind == SourceKind.JIOSAAVN -> stringResource(R.string.jiosaavn_mismatch_warning)
    // Downloads are a line of their own under this one — see [SourceRowCard].
    health is SourceHealth.Ok -> listOfNotNull(
        health.detail,
        kind.labels.take(3).joinToString(" · "),
    ).joinToString(" · ")
    health is SourceHealth.Rejected -> health.reason
    health is SourceHealth.Unreachable -> stringResource(R.string.source_unreachable, health.reason)
    kind.needsServer -> stringResource(R.string.checking)
    else -> kind.labels.take(3).joinToString(" · ")
}

/**
 * An empty slot drawn in dashes with a square plus in it, Mochy's DashedSlot:
 * there's room for another source here. Compact once one is already listed.
 */
@Composable
fun DashedSlot(
    title: String,
    detail: String,
    compact: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(SourceCardColor)
            .drawBehind {
                val inset = 1.dp.toPx()
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.32f),
                    topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                    size = androidx.compose.ui.geometry.Size(size.width - inset * 2, size.height - inset * 2),
                    cornerRadius = CornerRadius(23.dp.toPx()),
                    style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14.dp.toPx(), 10.dp.toPx()))),
                )
            }
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = if (compact) 16.dp else 22.dp),
    ) {
        if (compact) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PlusSquare(size = 40.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, fontFamily = InterFontFamily, fontSize = 16.sp, color = Color.White.copy(alpha = 0.85f))
                    Text(detail, fontFamily = InterFontFamily, fontSize = 13.sp, color = Color.White.copy(alpha = 0.55f))
                }
            }
        } else {
            Text(
                title, fontFamily = InterFontFamily, fontSize = 19.sp, letterSpacing = (-0.3).sp,
                color = Color.White.copy(alpha = 0.88f),
            )
            Spacer(Modifier.height(6.dp))
            Text(detail, fontFamily = InterFontFamily, fontSize = 13.sp, color = Color.White.copy(alpha = 0.55f))
            Spacer(Modifier.height(14.dp))
            PlusSquare(size = 52.dp)
        }
    }
}

// The mock's outlined square with a thin plus.
@Composable
private fun PlusSquare(size: Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.2f))
            .drawBehind {
                val stroke = 1.2.dp.toPx()
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.75f),
                    topLeft = androidx.compose.ui.geometry.Offset(stroke / 2, stroke / 2),
                    size = androidx.compose.ui.geometry.Size(this.size.width - stroke, this.size.height - stroke),
                    cornerRadius = CornerRadius(size.toPx() * 0.2f),
                    style = Stroke(width = stroke),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.add),
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.85f),
            modifier = Modifier.size(size * 0.45f),
        )
    }
}

/**
 * Asks for Nearby devices so the Bluetooth codec can be read — the only way an
 * LDAC, LHDC or SSC UHQ headset qualifies for an addon's `checkValidLossless`. Draws
 * nothing where the permission does not exist (before Android 12) or is
 * already granted, since asking again would do nothing.
 */
@Composable
fun BluetoothCodecPermissionCard() {
    val context = LocalContext.current
    val permission = "android.permission.BLUETOOTH_CONNECT"
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    fun isGranted() =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    var granted by remember { mutableStateOf(isGranted()) }
    var denied by remember { mutableStateOf(false) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        denied = !ok
        if (ok) LosslessOutput.refresh()
    }
    // Back from system Settings, where it may have been switched on.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        val now = isGranted()
        if (now && !granted) LosslessOutput.refresh()
        granted = now
        if (now) denied = false
    }
    // Asked as soon as the screen shows the shut-out it would fix, and again
    // on every later visit, since this row is composed afresh each time. After
    // a second refusal Android answers without a dialog, which lands in
    // [denied] and the Settings prompt below.
    LaunchedEffect(Unit) {
        if (!isGranted()) ask.launch(permission)
    }
    if (granted) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SourceCardShape)
            .background(SourceCardColor)
            .clickable {
                if (denied) {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            .setData(Uri.fromParts("package", context.packageName, null)),
                    )
                } else {
                    ask.launch(permission)
                }
            }
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.08f)),
        ) {
            Icon(
                painter = painterResource(R.drawable.bluetooth),
                contentDescription = null,
                tint = if (denied) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(
                    if (denied) R.string.lossless_output_permission_denied else R.string.lossless_output_permission,
                ),
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                letterSpacing = (-0.3).sp,
                color = if (denied) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(
                    if (denied) R.string.lossless_output_permission_denied_detail else R.string.lossless_output_permission_detail,
                ),
                fontFamily = InterFontFamily,
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.6f),
            )
        }
    }
}
