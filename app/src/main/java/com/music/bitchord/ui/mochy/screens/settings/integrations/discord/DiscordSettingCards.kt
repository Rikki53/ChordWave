// the pieces the discord page is built from, drawn to the user's mock: dark pill-shaped cards, pickers, a switch and a slider
// Ported from Mochy Music (GPL-3.0). The image-source card and custom-picture row are gone:
// ChordWave has no custom-presence-image engine, so DiscordScreen shows one disabled notice
// card instead. The button card is rewritten for CW's two fixed targets (YouTube Music song
// URL, ChordWave GitHub), and the placeholder card lists CW's variables.

package com.music.bitchord.ui.mochy.screens.settings.integrations.discord

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.bitchord.R
import com.music.bitchord.ui.mochy.component.AppSwitch
import com.music.bitchord.ui.mochy.theme.InterFontFamily
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** the mock's colours, read straight off it */
internal object DiscordColors {
    val Card = Color(0xFF1F1F1F)
    val ToggleCard = Color(0xFF232323)
    val Field = Color(0xFF171717)
    val FieldSelected = Color(0xFF2B2B2B)
    val Profile = Color(0xFF0D0D0D)
    val Activity = Color(0xFF1E1E1E)
    val Cover = Color(0xFF0C0C0C)
    val Banner = Color(0xFF3C3C3C)
    val Primary = Color.White
    val Secondary = Color.White.copy(alpha = 0.55f)
    val Hint = Color.White.copy(alpha = 0.32f)
    val Menu = Color(0xFF1E1F23)
    val Blurple = Color(0xFF5865F2)
}

private val CardShape = RoundedCornerShape(20.dp)
private val FieldHeight = 38.dp

/** how long the dropdown takes to unfold and fold */
private const val UnfoldMs = 280

@Composable
internal fun DiscordSectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = Color.White.copy(alpha = 0.5f),
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier.padding(start = 4.dp, top = 22.dp, bottom = 10.dp),
    )
}

@Composable
internal fun DiscordCard(
    modifier: Modifier = Modifier,
    color: Color = DiscordColors.Card,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(color)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        content = content,
    )
}

@Composable
internal fun DiscordCardTitle(text: String) {
    Text(text, color = DiscordColors.Primary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
}

/** a card that turns one behaviour on or off, with a line saying what it does */
@Composable
internal fun DiscordSwitchCard(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    /** an extra honest line, used by the controls ChordWave has no engine for */
    note: String? = null,
) {
    DiscordCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                Modifier
                    .weight(1f)
                    .padding(end = 12.dp),
            ) {
                DiscordCardTitle(title)
                Text(
                    text = description,
                    color = DiscordColors.Secondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 2.dp),
                )
                note?.let {
                    Text(
                        text = it,
                        color = DiscordColors.Secondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
            AppSwitch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
        }
    }
}

/** the small grey "string" / "action" labels inside the button cards */
@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        color = DiscordColors.Secondary,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(top = 8.dp, bottom = 6.dp),
    )
}

/** a pill text box, hint shows while it's empty so the user knows what empty means */
@Composable
internal fun DiscordTextField(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        enabled = enabled,
        textStyle = TextStyle(
            fontFamily = InterFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
            color = if (enabled) DiscordColors.Primary else DiscordColors.Primary.copy(alpha = 0.4f),
        ),
        cursorBrush = SolidColor(Color.White),
        modifier = modifier.fillMaxWidth(),
        decorationBox = { inner ->
            Box(
                contentAlignment = Alignment.CenterStart,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(FieldHeight)
                    .clip(CircleShape)
                    .background(DiscordColors.Field)
                    .padding(horizontal = 16.dp),
            ) {
                if (value.isEmpty()) {
                    Text(hint, color = DiscordColors.Hint, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                }
                inner()
            }
        },
    )
}

/** a text line of the status: title and its box */
@Composable
internal fun DiscordTextCard(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    hint: String = "Hidden",
    enabled: Boolean = true,
) {
    DiscordCard {
        DiscordCardTitle(title)
        Spacer(Modifier.height(10.dp))
        DiscordTextField(value = value, onValueChange = onValueChange, hint = hint, enabled = enabled)
    }
}

/** a pill that unfolds into its own list at the same width and row height, so it reads as one shape opening up and not a menu popping up elsewhere */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun <T> DiscordDropdown(
    selected: T,
    options: List<T>,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    optionIcon: ((T) -> Int)? = null,
) {
    var open by remember { mutableStateOf(false) }
    val chevronTurn by animateFloatAsState(if (open) 180f else 0f, tween(UnfoldMs), label = "chevron")
    val bringIntoView = remember { BringIntoViewRequester() }
    LaunchedEffect(open) {
        if (open) {
            // once it has finished growing, scroll just enough for the whole list to show
            delay(UnfoldMs.toLong())
            bringIntoView.bringIntoView()
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .bringIntoViewRequester(bringIntoView)
            .clip(RoundedCornerShape(FieldHeight / 2))
            .background(DiscordColors.Field),
    ) {
        DropdownRow(
            text = label(selected),
            icon = optionIcon?.invoke(selected),
            color = if (open) DiscordColors.Primary else DiscordColors.Secondary,
            onClick = { open = !open },
        ) {
            Icon(
                painter = painterResource(R.drawable.expand_more),
                contentDescription = if (open) "Close" else "Open",
                tint = Color.White,
                modifier = Modifier
                    .size(22.dp)
                    .graphicsLayer { rotationZ = chevronTurn },
            )
        }
        AnimatedVisibility(
            visible = open,
            enter = expandVertically(spring(dampingRatio = 0.86f, stiffness = 520f), expandFrom = Alignment.Top) +
                fadeIn(tween(180, delayMillis = 60)),
            exit = shrinkVertically(tween(UnfoldMs), shrinkTowards = Alignment.Top) + fadeOut(tween(140)),
        ) {
            // every choice, the current one included, so picking doesn't reshuffle the list mid-fold
            Column(Modifier.padding(bottom = 4.dp)) {
                options.forEach { option ->
                    DropdownRow(
                        text = label(option),
                        icon = optionIcon?.invoke(option),
                        color = DiscordColors.Primary,
                        highlighted = option == selected,
                        onClick = {
                            open = false
                            if (option != selected) onSelect(option)
                        },
                    )
                }
            }
        }
    }
}

/** one row of the dropdown: centred text in the pill's own size, the chosen one on a lighter pill */
@Composable
private fun DropdownRow(
    text: String,
    icon: Int?,
    color: Color,
    onClick: () -> Unit,
    highlighted: Boolean = false,
    trailing: (@Composable BoxScope.() -> Unit)? = null,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(FieldHeight)
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .clip(CircleShape)
            .background(if (highlighted) DiscordColors.FieldSelected else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 24.dp)) {
            if (icon != null) {
                Image(
                    painter = painterResource(icon),
                    contentDescription = null,
                    modifier = Modifier
                        .size(18.dp)
                        .clip(RoundedCornerShape(5.dp)),
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = text,
                color = color,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        trailing?.let { Box(Modifier.align(Alignment.CenterEnd), content = it) }
    }
}

/**
 * One of the two buttons under the status. ChordWave's targets are FIXED — button 1 opens the
 * song on YouTube Music, button 2 opens ChordWave on GitHub — so there is no target picker,
 * only the label and whether the button shows at all.
 */
@Composable
internal fun DiscordButtonCard(
    title: String,
    text: String,
    onTextChange: (String) -> Unit,
    visible: Boolean,
    onVisibleChange: (Boolean) -> Unit,
    targetDescription: String,
) {
    DiscordCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DiscordCardTitle(title)
            Spacer(Modifier.weight(1f))
            AppSwitch(checked = visible, onCheckedChange = onVisibleChange)
        }
        FieldLabel("Label")
        DiscordTextField(
            value = text,
            onValueChange = onTextChange,
            hint = "No button",
        )
        FieldLabel("Opens")
        Text(
            text = targetDescription,
            color = DiscordColors.Secondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

/** two choices side by side, the chosen one sits on a lighter pill */
@Composable
internal fun <T> DiscordSegmented(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .height(36.dp)
            .clip(CircleShape)
            .background(DiscordColors.Field)
            .padding(4.dp),
    ) {
        options.forEachIndexed { index, option ->
            if (index > 0) {
                Box(
                    Modifier
                        .width(1.dp)
                        .height(12.dp)
                        .background(Color.White.copy(alpha = if (option == selected || options[index - 1] == selected) 0f else 0.25f)),
                )
            }
            val isSelected = option == selected
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .width(110.dp)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(if (isSelected) DiscordColors.FieldSelected else Color.Transparent)
                    .clickable { onSelect(option) },
            ) {
                Text(
                    text = label(option),
                    color = if (isSelected) DiscordColors.Primary else DiscordColors.Secondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

/** a thin track with the value riding above the end of the fill, tap or drag anywhere and it snaps to one of values */
@Composable
internal fun DiscordStepSlider(
    value: Int,
    values: List<Int>,
    label: (Int) -> String,
    onValueChange: (Int) -> Unit,
    enabled: Boolean = true,
) {
    val latest by rememberUpdatedState(onValueChange)
    val enabledNow by rememberUpdatedState(enabled)
    val density = LocalDensity.current
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp),
    ) {
        val widthPx = constraints.maxWidth.toFloat()
        val lastIndex = values.lastIndex.coerceAtLeast(1)
        fun valueAt(x: Float): Int {
            val fraction = (x / widthPx).coerceIn(0f, 1f)
            return values[(fraction * lastIndex).roundToInt().coerceIn(0, values.lastIndex)]
        }
        val fraction = values.indexOf(value).coerceAtLeast(0).toFloat() / lastIndex
        val mono = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = DiscordColors.Secondary)

        Box(
            Modifier
                .fillMaxWidth()
                .height(40.dp)
                .pointerInput(widthPx, values) { detectTapGestures { if (enabledNow) latest(valueAt(it.x)) } }
                .pointerInput(widthPx, values) { detectHorizontalDragGestures { change, _ -> if (enabledNow) latest(valueAt(change.position.x)) } }
                .drawBehind {
                    val y = size.height - 6.dp.toPx()
                    val thickness = 3.dp.toPx()
                    val radius = CornerRadius(thickness / 2)
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.18f),
                        topLeft = Offset(0f, y - thickness / 2),
                        size = Size(size.width, thickness),
                        cornerRadius = radius,
                    )
                    drawRoundRect(
                        color = Color.White.copy(alpha = if (enabled) 1f else 0.35f),
                        topLeft = Offset(0f, y - thickness / 2),
                        size = Size(size.width * fraction.coerceAtLeast(0.02f), thickness),
                        cornerRadius = radius,
                    )
                },
        )
        // the value follows the end of the fill, but never runs off either edge or into the last value
        val labelWidth = with(density) { SliderLabelWidth.toPx() }
        val labelX = (widthPx * fraction - labelWidth / 2).coerceIn(0f, (widthPx - labelWidth * 2).coerceAtLeast(0f))
        Text(
            text = label(value),
            style = mono.copy(color = if (enabled) DiscordColors.Primary else DiscordColors.Secondary),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .offset { IntOffset(labelX.roundToInt(), 0) }
                .width(SliderLabelWidth),
        )
        Text(
            text = label(values.last()),
            style = mono,
            modifier = Modifier.align(Alignment.TopEnd),
        )
    }
}

private val SliderLabelWidth = 52.dp

/** the bold one-liner card, like "empty it to disable" */
@Composable
internal fun DiscordNoticeCard(text: String) {
    DiscordCard {
        Text(text, color = DiscordColors.Primary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** what each placeholder means, with a worked example. CW's variables come from DiscordRPC.resolveVariables. */
@Composable
internal fun DiscordPlaceholderCard() {
    val body = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        color = Color.White.copy(alpha = 0.85f),
    )
    val placeholders = listOf(
        "{song_name}" to "the song's title",
        "{artist_name}" to "the song's artist",
        "{album_name}" to "the song's album",
    )
    DiscordCard {
        Text("Placeholder list", color = DiscordColors.Primary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        placeholders.forEach { (placeholder, meaning) ->
            Text("$placeholder = $meaning", style = body)
        }
        Spacer(Modifier.height(12.dp))
        Text("For example, this activity name", style = body)
        Text("{song_name} by {artist_name} on {album_name}", style = body.copy(color = DiscordColors.Primary, fontWeight = FontWeight.Medium))
        Spacer(Modifier.height(12.dp))
        Text("shows up as", style = body)
        Text(
            "Listening to Fake Plastic Trees by Radiohead on The Bends",
            style = body.copy(color = DiscordColors.Primary, fontWeight = FontWeight.Medium),
        )
        Spacer(Modifier.height(12.dp))
        Text("They work in the activity name and the button labels.", style = body)
    }
}
