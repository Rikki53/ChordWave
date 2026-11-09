package com.music.bitchord.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.sharedui.resources.*
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/**
 * Karaoke's quick door on the lyrics panel, in the shape Apple Music's Sing
 * made familiar: a mic floated on the right edge of the words, which opens
 * into a vertical vocal-level slider when tapped.
 *
 * Presentation only. Every value here is the karaoke state in AppSettings,
 * reached through [PlayerSettings] — the same flows the Equalizer's karaoke
 * block reads and writes, so the two surfaces share one truth and can never
 * disagree: move the level here and the Equalizer's slider has already
 * moved, and the other way round. PlaybackService observes those flows and
 * drives the DSP chain, so switching karaoke on from the lyrics lights
 * exactly the chain the Equalizer's switch does.
 *
 * The rail stands down on its own: a few seconds after the last touch, when
 * the lyrics start scrolling, or when karaoke is switched off elsewhere.
 */
@Composable
internal fun KaraokeMicRail(
    lyricsScrolling: Boolean,
    modifier: Modifier = Modifier,
) {
    val enabled by PlayerSettings.karaokeEnabled.collectAsStateWithLifecycle()
    val vocalLevel by PlayerSettings.karaokeVocalLevel.collectAsStateWithLifecycle()
    val ytMode by PlayerSettings.karaokeYtMode.collectAsStateWithLifecycle()
    val chrome = LocalPlayerChrome.current

    var expanded by remember { mutableStateOf(false) }
    var lastTouchMs by remember { mutableLongStateOf(0L) }

    LaunchedEffect(enabled) { if (!enabled) expanded = false }
    LaunchedEffect(lyricsScrolling) { if (lyricsScrolling) expanded = false }
    LaunchedEffect(expanded, lastTouchMs) {
        if (expanded) {
            delay(4_000)
            expanded = false
        }
    }

    // A fixed-height stage with the mic pinned to its foot: opening the
    // slider must not shove the button out from under the thumb that
    // tapped it, so the level grows into space the rail already holds.
    // The empty stage consumes nothing — only the mic and, while open,
    // the slider take touches.
    Column(
        modifier = modifier.height(256.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom,
    ) {
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(tween(160)) +
                expandVertically(tween(220), expandFrom = Alignment.Bottom),
            exit = fadeOut(tween(120)) +
                shrinkVertically(tween(180), shrinkTowards = Alignment.Bottom),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${(vocalLevel * 100).roundToInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = chromeWhite(0.92f),
                )
                Spacer(Modifier.height(8.dp))
                VerticalVocalSlider(
                    level = vocalLevel,
                    enabled = !ytMode,
                    onLevel = { level ->
                        PlayerSettings.setKaraokeVocalLevel(level)
                        lastTouchMs = System.currentTimeMillis()
                    },
                )
                Spacer(Modifier.height(10.dp))
            }
        }
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .additiveChrome(chrome)
                .background(Color.White.copy(alpha = if (enabled) 0.34f else 0.18f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    if (!enabled) {
                        // The first tap is the switch: karaoke comes on in
                        // whichever mode was last selected (the Equalizer's
                        // picker owns that choice), and the slider opens
                        // with the level right there under the thumb.
                        PlayerSettings.setKaraokeEnabled(true)
                        expanded = true
                    } else {
                        expanded = !expanded
                    }
                    lastTouchMs = System.currentTimeMillis()
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Mic,
                contentDescription = stringResource(Res.string.karaoke_mic),
                tint = chromeWhite(if (enabled) 1f else 0.52f),
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

/**
 * The vocal level as a vertical capsule: the fill rises from the foot and
 * the thumb rides on top of it. Drawn directly, like [ThinSlider] —
 * Material's Slider only comes horizontal and insists on its own track.
 *
 * In Karaoke YT mode there is no vocal stem to fade — the track itself is
 * the karaoke take — so the capsule stands greyed and untouchable, exactly
 * as the Equalizer's slider steps aside there; touches fall through to the
 * lyrics under it.
 */
@Composable
private fun VerticalVocalSlider(
    level: Float,
    enabled: Boolean,
    onLevel: (Float) -> Unit,
) {
    val chrome = LocalPlayerChrome.current
    val currentOnLevel by rememberUpdatedState(onLevel)
    BoxWithConstraints(
        modifier = Modifier
            .width(40.dp)
            .height(168.dp)
            .alpha(if (enabled) 1f else 0.35f)
            .then(
                if (enabled) {
                    Modifier.pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            currentOnLevel(
                                (1f - down.position.y / size.height).coerceIn(0f, 1f),
                            )
                            down.consume()
                            do {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull() ?: break
                                if (change.positionChanged()) {
                                    currentOnLevel(
                                        (1f - change.position.y / size.height)
                                            .coerceIn(0f, 1f),
                                    )
                                    change.consume()
                                }
                            } while (event.changes.any { it.pressed })
                        }
                    }
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        // Track.
        Box(
            modifier = Modifier
                .width(30.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(50))
                .additiveChrome(chrome)
                .background(Color.White.copy(alpha = 0.18f)),
        )
        // Fill, from the foot up to the level.
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .width(30.dp)
                .height(maxHeight * level.coerceIn(0f, 1f))
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.85f)),
        )
        // Thumb riding the fill's top.
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = -((maxHeight - 30.dp) * level.coerceIn(0f, 1f)))
                .size(30.dp)
                .clip(CircleShape)
                .background(Color.White),
        )
    }
}
