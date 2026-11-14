// Ported from Mochy Music (GPL-3.0): ui/screens/audiolab/AudioLabKit.kt
// (EXTRACT #2 — the pieces the settings transition pages use: LabCollapsible,
// the picture clock helpers, LevelFollower, LabSelect). The worktree's
// AudioLabKit.kt already holds LabColors/LabCard/LabStage/LabToggleRow/
// LabSlider/LabPanel; when the full AudioLab screen lands in Phase 5 this
// file folds into it. Shared with the crossmix worker: the coordinator pushes
// ONE copy; other workers' duplicates must be dropped.
//
// Deviations from Mochy (both documented, neither changes the look):
// - LevelFollower: ChordWave has no AudioTap (Mochy's live audio-level tap),
//   so the level decays to silence and the pictures breathe at their resting
//   amplitude. The clock (rememberLabTime) still drives all motion.
// - LabSelect is ALSO ported (internal) in ui/mochy/menu/songedit/LabSelect.kt
//   by the Phase 3 menus chunk; this is the public audiolab copy Mochy's
//   settings pages import. Different packages, no declaration clash; the
//   coordinator may unify them later.

package com.music.bitchord.ui.mochy.screens.audiolab

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.bitchord.R
import com.music.bitchord.ui.mochy.LocalAppContentObscured
import com.music.bitchord.ui.mochy.component.LocalNavAnimatedContentScope
import com.music.bitchord.ui.mochy.theme.InterFontFamily
import kotlinx.coroutines.flow.first

/** options that fold away while their effect is off */
@Composable
fun LabCollapsible(visible: Boolean, content: @Composable ColumnScope.() -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow)) + fadeIn(tween(220, 60)),
        exit = shrinkVertically(spring(dampingRatio = 1f, stiffness = Spring.StiffnessMedium)) + fadeOut(tween(140)),
    ) {
        Column(Modifier.fillMaxWidth()) { content() }
    }
}

/** whether pictures should move: not while the player covers the app, the page is opening or closing, or it is scrolled out of view */
@Composable
fun rememberPicturesHeld(onScreen: State<Boolean>): State<Boolean> {
    val obscured = LocalAppContentObscured.current
    val navTransition = LocalNavAnimatedContentScope.current?.transition
    return remember(obscured, navTransition, onScreen) {
        derivedStateOf {
            !onScreen.value || obscured.value || (navTransition != null &&
                (navTransition.currentState != EnterExitState.Visible || navTransition.targetState != EnterExitState.Visible))
        }
    }
}

/** seconds, advancing every frame while the picture is free to move, or checking back a few times a second when moving says no */
@Composable
fun rememberLabTime(held: State<Boolean>, moving: () -> Boolean = { true }): State<Float> {
    val time = remember { mutableFloatStateOf(0f) }
    val currentMoving by rememberUpdatedState(moving)
    LaunchedEffect(held) {
        var last = 0L
        while (true) {
            if (held.value) {
                snapshotFlow { held.value }.first { !it }
                last = 0L
            }
            if (!currentMoving()) {
                kotlinx.coroutines.delay(250)
                last = 0L
                continue
            }
            withFrameNanos { now ->
                if (last != 0L) time.floatValue += ((now - last) / 1e9f).coerceAtMost(0.05f)
                last = now
            }
        }
    }
    return time
}

/** true while any part of this is inside the window; written only when it flips */
@Composable
fun Modifier.trackOnScreen(state: MutableState<Boolean>): Modifier {
    val view = LocalView.current
    return onGloballyPositioned { coords ->
        val bounds = coords.boundsInWindow()
        val visible = bounds.height > 0f && bounds.bottom > 0f && bounds.top < view.height
        if (visible != state.value) state.value = visible
    }
}

/**
 * A smoothed music level 0..1 for pictures to breathe with, updated from the frame clock.
 *
 * CHORDWAVE STUB: Mochy reads the live level from its AudioTap (an EQ audio tap
 * ChordWave has no equivalent of), so the level rests at silence here and the
 * pictures breathe at their resting amplitude. The clock still drives all
 * motion, so nothing looks frozen. (No Timber call: this runs once per frame.)
 */
class LevelFollower {
    var level = 0f
        private set
    var punch = 0f
        private set

    /** call once per frame with the seconds since the last one */
    fun update(dt: Float) {
        // No live audio level in ChordWave: ease back to silence.
        level = (level - dt * 0.5f).coerceAtLeast(0f)
        punch = (punch - dt * 3f).coerceAtLeast(0f)
    }
}

@Composable
fun rememberOnScreen(): MutableState<Boolean> = remember { mutableStateOf(true) }

/** a discord style select: the choice on the left and a chevron on the right, and opening it unrolls the list right under it as one box with the picked row lit and checked; the first option is the "none" one and reads muted */
@Composable
fun LabSelect(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    mutedIndex: Int = 0,
    maxListHeight: Dp = 264.dp,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    val chevron by animateFloatAsState(if (open) 180f else 0f, spring(dampingRatio = 0.8f, stiffness = 420f), label = "chevron")
    val edge by animateColorAsState(if (open) LabSelectEdgeOpen else LabSelectEdge, tween(180), label = "selectEdge")
    val listScroll = rememberScrollState()
    val rowHeight = with(LocalDensity.current) { LabSelectRowHeight.roundToPx() }
    val bringIntoView = remember { BringIntoViewRequester() }

    // back closes the list before it leaves the page
    BackHandler(enabled = open) { open = false }
    LaunchedEffect(open) {
        if (!open) return@LaunchedEffect
        // open with the picked row in sight, then make room for the list once it has unrolled
        listScroll.scrollTo(((selectedIndex - 2) * rowHeight).coerceAtLeast(0))
        kotlinx.coroutines.delay(LabSelectUnrollMs.toLong())
        bringIntoView.bringIntoView()
    }

    Column(
        modifier
            .fillMaxWidth()
            .bringIntoViewRequester(bringIntoView)
            .clip(LabSelectShape)
            .background(LabSelectColor)
            .border(1.dp, edge, LabSelectShape),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .clickable { open = !open }
                .padding(start = 16.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = options.getOrNull(selectedIndex).orEmpty(),
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                color = if (selectedIndex == mutedIndex) LabColors.Secondary else LabColors.Primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Icon(
                painter = painterResource(R.drawable.expand_more),
                contentDescription = contentDescription,
                tint = if (open) Color.White else LabColors.Secondary,
                modifier = Modifier.size(22.dp).graphicsLayer { rotationZ = chevron },
            )
        }
        AnimatedVisibility(
            visible = open,
            enter = expandVertically(tween(LabSelectUnrollMs, easing = FastOutSlowInEasing), expandFrom = Alignment.Top) + fadeIn(tween(160, 40)),
            exit = shrinkVertically(tween(200, easing = FastOutSlowInEasing), shrinkTowards = Alignment.Top) + fadeOut(tween(120)),
        ) {
            Column {
                Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp).height(1.dp).background(LabSelectEdge))
                Column(
                    Modifier
                        .heightIn(max = maxListHeight)
                        .verticalScroll(listScroll)
                        .padding(6.dp),
                ) {
                    options.forEachIndexed { index, label ->
                        LabSelectRow(
                            label = label,
                            picked = index == selectedIndex,
                            onClick = {
                                onSelect(index)
                                open = false
                            },
                        )
                    }
                }
            }
        }
    }
}

// one option in the open list: the picked one sits on a lighter row with a filled check at its end
@Composable
private fun LabSelectRow(label: String, picked: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(LabSelectRowHeight)
            .clip(RoundedCornerShape(10.dp))
            .background(if (picked) LabColors.Row else Color.Transparent)
            .selectable(selected = picked, role = Role.RadioButton, onClick = onClick)
            .padding(start = 10.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontFamily = InterFontFamily,
            fontWeight = if (picked) FontWeight.SemiBold else FontWeight.Medium,
            fontSize = 15.sp,
            color = if (picked) LabColors.Primary else LabColors.Secondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (picked) {
            Box(Modifier.size(20.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.check), contentDescription = null, tint = Color.Black, modifier = Modifier.size(14.dp))
            }
        }
    }
}

private const val LabSelectUnrollMs = 240
private val LabSelectRowHeight = 44.dp
private val LabSelectShape = RoundedCornerShape(16.dp)
private val LabSelectColor = Color(0xFF141416)
private val LabSelectEdge = Color(0xFF2A2A2F)
private val LabSelectEdgeOpen = Color(0xFF3C3C42)
