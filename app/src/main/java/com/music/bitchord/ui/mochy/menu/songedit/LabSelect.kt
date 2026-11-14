// Ported from Mochy Music (GPL-3.0): ui/screens/audiolab/AudioLabKit.kt
// (LabSelect + LabSelectRow + its private constants, verbatim).
//
// NOTE ON PLACEMENT: in Mochy this lives in the AudioLab screens package;
// the song edit sheet is its only caller in this chunk. It is staged here
// (internal, songedit package) so this chunk compiles standalone. If the
// AudioLab chunk ports AudioLabKit.kt with its own LabSelect, the
// coordinator should keep ONE copy (prefer the audiolab one, in
// ui.mochy.screens.audiolab) and delete this file. NEEDS.md §(a) A4.
// LabColors is reduced to the three values LabSelect reads; the full
// palette belongs to AudioLabKit.

package com.music.bitchord.ui.mochy.menu.songedit

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.bitchord.R
import com.music.bitchord.ui.mochy.theme.InterFontFamily

/** The three LabColors values LabSelect reads (full palette: AudioLabKit). */
private object LabSelectColors {
    val Row = Color(0xFF26262A)
    val Primary = Color.White
    val Secondary = Color(0xFF9A9AA0)
}

/** a discord style select: the choice on the left and a chevron on the right, and opening it unrolls the list right under it as one box with the picked row lit and checked; the first option is the "none" one and reads muted */
@Composable
internal fun LabSelect(
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
                color = if (selectedIndex == mutedIndex) LabSelectColors.Secondary else LabSelectColors.Primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Icon(
                painter = painterResource(R.drawable.expand_more),
                contentDescription = contentDescription,
                tint = if (open) Color.White else LabSelectColors.Secondary,
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
            .background(if (picked) LabSelectColors.Row else Color.Transparent)
            .selectable(selected = picked, role = Role.RadioButton, onClick = onClick)
            .padding(start = 10.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontFamily = InterFontFamily,
            fontWeight = if (picked) FontWeight.SemiBold else FontWeight.Medium,
            fontSize = 15.sp,
            color = if (picked) LabSelectColors.Primary else LabSelectColors.Secondary,
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
