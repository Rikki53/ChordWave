// Ported from Mochy Music (GPL-3.0): ui/screens/audiolab/AudioLabKit.kt
// (the REMAINDER — LabSectionLabel, LabTextAction, LabChip/LabChips,
// LabGlyph, dimmedWhen, animatedDim, dpToPx). Phase 4 already ported the
// rest of that file; do NOT redeclare anything from AudioLabKit.kt
// (LabColors, LabCard, LabStage, LabToggleRow, LabSlider, LabPanel),
// AudioLabKitExtras.kt (LabCollapsible, rememberPicturesHeld,
// rememberLabTime, trackOnScreen, LevelFollower, rememberOnScreen,
// LabSelect) or LabControls.kt (LabSegmented, ListenToAudio). The
// coordinator may fold this file into AudioLabKit.kt at merge time.
package com.music.bitchord.ui.mochy.screens.audiolab

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.bitchord.ui.mochy.component.RemoteFocus
import com.music.bitchord.ui.mochy.theme.InterFontFamily

@Composable
fun LabSectionLabel(text: String, modifier: Modifier = Modifier, actions: @Composable RowScope.() -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = text.uppercase(),
            fontFamily = InterFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            letterSpacing = 1.4.sp,
            color = LabColors.Secondary,
            modifier = Modifier.weight(1f),
        )
        actions()
    }
}

/** [busy] spins the icon and holds off taps, so a press that waits on the network shows it's working */
@Composable
fun LabTextAction(text: String, iconRes: Int? = null, enabled: Boolean = true, busy: Boolean = false, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled && !busy, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (iconRes != null) {
            // the turn is read in the layer, so spinning never recomposes the row
            val turn = if (busy) rememberInfiniteTransition(label = "busy")
                .animateFloat(0f, 360f, infiniteRepeatable(tween(800, easing = LinearEasing)), label = "turn") else null
            Icon(
                painterResource(iconRes), null, tint = if (enabled) Color.White else LabColors.Faint,
                modifier = Modifier.size(16.dp).graphicsLayer { rotationZ = turn?.value ?: 0f },
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(text, fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
            color = if (enabled) Color.White else LabColors.Faint)
    }
}

data class LabChip(val key: String, val label: String, val dot: Color? = null)

/** a scrolling row of pills; the picked one is white */
@Composable
fun LabChips(
    chips: List<LabChip>,
    selectedKey: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    onLongPress: ((String) -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        chips.forEach { chip ->
            val selected = chip.key == selectedKey
            val bg by animateColorAsState(if (selected) Color.White else LabColors.Chip, tween(180), label = "chipBg")
            val fg by animateColorAsState(if (selected) Color.Black else Color.White, tween(180), label = "chipFg")
            val interaction = remember { MutableInteractionSource() }
            Row(
                modifier = Modifier
                    .height(38.dp)
                    .clip(RoundedCornerShape(19.dp))
                    .background(bg)
                    .then(
                        if (onLongPress != null) Modifier.combinedClickableCompat(
                            interaction, onClick = { onSelect(chip.key) }, onLongClick = { onLongPress(chip.key) },
                        ) else Modifier.clickable(interaction, null) { onSelect(chip.key) }
                    )
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (chip.dot != null) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(chip.dot))
                    Spacer(Modifier.width(8.dp))
                }
                Text(chip.label, fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = fg, maxLines = 1)
            }
        }
        trailing()
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun Modifier.combinedClickableCompat(
    interaction: MutableInteractionSource,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
): Modifier = this.then(
    Modifier.combinedClickable(interactionSource = interaction, indication = RemoteFocus, onClick = onClick, onLongClick = onLongClick)
)

/** the 32dp light circle with a dark glyph that mochy's settings rows lead with */
@Composable
fun LabGlyph(iconRes: Int, modifier: Modifier = Modifier, size: Dp = 32.dp) {
    Box(
        modifier = modifier.size(size).clip(CircleShape).background(Color(0xFFE5E5EA)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(iconRes), null, tint = Color.Black, modifier = Modifier.size(size * 0.55f))
    }
}

/** dims a section while what it belongs to is off, without removing it */
fun Modifier.dimmedWhen(off: Boolean, alpha: Float): Modifier = graphicsLayer { this.alpha = if (off) alpha else 1f }

@Composable
fun animatedDim(off: Boolean): Float =
    animateFloatAsState(if (off) 0.45f else 1f, tween(220), label = "dim").value

@Composable
fun dpToPx(dp: Dp): Float = with(LocalDensity.current) { dp.toPx() }
