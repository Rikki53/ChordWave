// Ported from Mochy Music (GPL-3.0): ui/screens/audiolab/AudioLabKit.kt
// (LabSegmented verbatim; ListenToAudio stubbed — see
// below). The worktree's AudioLabKit.kt extract only carries LabColors,
// LabCard, LabStage, LabToggleRow, LabSlider and LabPanel; the crossmix
// settings screen also needs these two (LabCollapsible lives in
// AudioLabKitExtras.kt — same package, do not redeclare it). When the full AudioLab port
// lands (Phase 5), fold this file into AudioLabKit.kt and delete it.
//
// NOTE: animateColorAsState lives in androidx.compose.animation in
// Compose 1.10 (NOT .core) — hard lesson from the Phase 3 build.

package com.music.bitchord.ui.mochy.screens.audiolab

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.bitchord.ui.mochy.theme.InterFontFamily

/** a track with a white pill that slides to the picked option */
@Composable
fun LabSegmented(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 48.dp,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(LabColors.Card)
            .border(1.dp, LabColors.StageLine, RoundedCornerShape(height / 2))
            .padding(4.dp),
    ) {
        val segment = maxWidth / options.size
        val offset by animateDpAsState(segment * selectedIndex, spring(dampingRatio = 0.82f, stiffness = 520f), label = "segment")
        Box(
            Modifier
                .offset(x = offset)
                .width(segment)
                .fillMaxHeight()
                .clip(RoundedCornerShape(height / 2))
                .background(Color.White)
        )
        Row(Modifier.fillMaxWidth().fillMaxHeight()) {
            options.forEachIndexed { index, label ->
                val color by animateColorAsState(if (index == selectedIndex) Color.Black else Color.White, tween(160), label = "segText")
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(height / 2))
                        .clickable(remember { MutableInteractionSource() }, null) { onSelect(index) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = color,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/**
 * STUB (no-op): in Mochy this asks the audio tap to fill while the page
 * shows a live picture, so the transition pictures glow with the music.
 * ChordWave has no audio tap, so the pictures render static. Kept as a
 * named no-op (rather than dropping the call) so a future tap can wire
 * in here without touching the screens.
 */
@Composable
fun ListenToAudio() {
}
