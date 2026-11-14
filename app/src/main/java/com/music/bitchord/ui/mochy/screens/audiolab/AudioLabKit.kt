// Ported from Mochy Music (GPL-3.0), ui/screens/audiolab/AudioLabKit.kt —
// EXTRACT, not the whole file: only the kit pieces the player share UI uses
// (LabColors, LabCard, LabStage, LabToggleRow, LabSlider, LabPanel). The full
// AudioLab screen is Phase 5 scope; when it lands, this file folds into it.
package com.music.bitchord.ui.mochy.screens.audiolab

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.bitchord.ui.mochy.component.AppSwitch
import com.music.bitchord.ui.mochy.component.DefaultPlayerSeekBarSlider
import com.music.bitchord.ui.mochy.component.damageBoundary
import com.music.bitchord.ui.mochy.constants.HideSettingsDescriptionsKey
import com.music.bitchord.ui.mochy.theme.InterFontFamily
import com.music.bitchord.ui.mochy.utils.rememberPreference

object LabColors {
    val Card = Color(0xFF1C1C1E)
    val Stage = Color(0xFF0E0E10)
    val StageLine = Color(0xFF2E2E32)
    val Row = Color(0xFF26262A)
    val RowSelected = Color(0xFF34343A)
    val Chip = Color(0xFF232327)
    val Primary = Color.White
    val Secondary = Color(0xFF9A9AA0)
    val Faint = Color(0xFF5C5C63)
    val Ink = Color(0xFF8A8A92)

    /** the equalizer's node colours, soft enough to sit on mochy's greys and apart enough to tell */
    val Nodes = listOf(
        Color(0xFFF28BA8), Color(0xFFF2A46B), Color(0xFFF2D46B), Color(0xFF8FD98B),
        Color(0xFF6BD9C4), Color(0xFF6BBAF2), Color(0xFF8C9EF2), Color(0xFFB98CF2),
        Color(0xFFE08CF2), Color(0xFFF26B6B),
    )

    fun node(index: Int) = Nodes[index.mod(Nodes.size)]
}

@Composable
fun LabCard(
    modifier: Modifier = Modifier,
    padding: Dp = 14.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(LabColors.Card)
            .padding(padding),
        content = content,
    )
}

/** outlined dark box for a live picture, bounded so its animation repaints only itself and not the page and glass over it */
@Composable
fun LabStage(
    modifier: Modifier = Modifier,
    height: Dp = 132.dp,
    content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(18.dp))
            .background(LabColors.Stage)
            .border(1.dp, LabColors.StageLine, RoundedCornerShape(18.dp))
            .damageBoundary(),
    ) {
        content()
    }
}

@Composable
fun LabToggleRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingDot: Boolean = false,
) {
    val (hideDescriptions) = rememberPreference(
        HideSettingsDescriptionsKey,
        defaultValue = true,
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(horizontal = 6.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingDot) {
            val dot by animateColorAsState(if (checked) Color.White else Color(0xFFD9D9DE), tween(200), label = "dot")
            Box(
                Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(dot)
            )
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                color = if (enabled) LabColors.Primary else LabColors.Faint,
            )
            if (subtitle != null && !hideDescriptions) {
                Text(
                    text = subtitle,
                    fontFamily = InterFontFamily,
                    fontSize = 12.sp,
                    lineHeight = 15.sp,
                    color = if (enabled) LabColors.Secondary else LabColors.Faint,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        AppSwitch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

/** a slider with its name on the left and its value on the right, like the Audio FX design */
@Composable
fun LabSlider(
    label: String,
    valueText: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    steps: Int = 0,
) {
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = if (enabled) LabColors.Primary else LabColors.Faint,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = valueText,
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = if (enabled) LabColors.Primary else LabColors.Faint,
            )
        }
        DefaultPlayerSeekBarSlider(
            value = value.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            enabled = enabled,
        )
    }
}

/** the inner darker panel the sliders sit in */
@Composable
fun LabPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(LabColors.Row)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        content = content,
    )
}
