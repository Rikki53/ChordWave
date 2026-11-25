// Ported from Mochy Music (GPL-3.0): ui/screens/donate/DonateParts.kt
// bits the support sheet and the donate sheet share: the mark, her sizing, and the pill buttons
// ChordWave adaptation: Mochy's coffee-girl art is replaced by ChordWave's own
// animated mint ribbon mark (batch 22), so the mark is square instead of tall.

package com.music.bitchord.ui.mochy.screens.donate

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import com.music.bitchord.ui.mochy.component.RemoteFocus
import com.music.bitchord.ui.mochy.component.pressBounce
import com.music.bitchord.ui.mochy.player.menu.MenuRowSurface
import com.music.bitchord.ui.mochy.screens.setup.welcome.AnimatedRibbonMark

// the ribbon mark is square, unlike the tall character art it replaces
internal const val CharacterAspect = 1f

// gap between the sheet's handle row and the top of the mark
internal val CharacterTop = 24.dp

internal val DonateSoftText = Color.White.copy(alpha = 0.72f)

/** about half the sheet wide like the mocks, but small enough on a short or sideways screen that the words still fit */
internal fun characterWidth(maxWidth: Dp, maxHeight: Dp): Dp =
    minOf(maxWidth * 0.46f, maxHeight * 0.44f / CharacterAspect).coerceIn(120.dp, 250.dp)

/** ChordWave's animated mint ribbon, on its own slow clock so it floats like the welcome page */
@Composable
internal fun DonateCharacter(width: Dp, modifier: Modifier = Modifier, overlapBelow: Dp = 0.dp) {
    val transition = rememberInfiniteTransition(label = "donateMark")
    val seconds by transition.animateFloat(
        initialValue = 0f,
        targetValue = 3600f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3_600_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "donateMarkSeconds",
    )
    val height = width * CharacterAspect
    Box(modifier.size(width, height - overlapBelow), contentAlignment = Alignment.TopCenter) {
        AnimatedRibbonMark(seconds = seconds, modifier = Modifier.size(width, height))
    }
}

/** the round grey buttons under both sheets, quiet ones are the way out */
@Composable
internal fun DonatePillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    quiet: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .pressBounce(interaction, pressedScale = 0.97f)
            .fillMaxWidth()
            .height(48.dp)
            .clip(CircleShape)
            .background(MenuRowSurface)
            .clickable(interactionSource = interaction, indication = RemoteFocus, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.4).sp,
            color = if (quiet) Color.White.copy(alpha = 0.38f) else Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
