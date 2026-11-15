// customisation's launcher icons: the one in use, and behind a chevron every family's ready-made ones
//
// Ported from Mochy Music (GPL-3.0): ui/screens/gesture/CustomisationIcons.kt,
// reworked for ChordWave's own icon set (see NEEDS.md):
// - ChordWave's AppIcon is a flat enum of 23 activity-alias icons (root
//   package), with no families, custom colours or splash sync: the
//   families here are grouped by name prefix (Default, Monochrome,
//   Liquid Glass, Samsung Glass, Pixel Glass, Neon, Samsung Neon,
//   Pixel Neon), and the custom-colour picker is gone.
// - Art is the icon's preview foreground where it has one, else the icon
//   itself, drawn full-bleed in the tile (no background/foreground split).
package com.music.bitchord.ui.mochy.screens.settings

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.music.bitchord.AppIcon
import com.music.bitchord.R
import com.music.bitchord.ui.mochy.component.RemoteFocus
import com.music.bitchord.ui.mochy.component.pressBounce
import com.music.bitchord.ui.mochy.component.rememberResourcePainter
import com.music.bitchord.ui.mochy.screens.audiolab.LabColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private val TileSize = 66.dp
// close to a launcher's squircle, so the tile reads as the icon it will be
private val TileShape = RoundedCornerShape(TileSize * 0.27f)
private val TileRing = Color.White.copy(alpha = 0.12f)
private val TileRingPicked = Color.White.copy(alpha = 0.9f)

/** one row of the picker: a name and the icons grouped under it */
private class IconFamily(@StringRes val title: Int, val icons: List<AppIcon>)

private val IconFamilies = listOf(
    IconFamily(R.string.customise_icons_family_default, listOf(AppIcon.Default)),
    IconFamily(R.string.customise_icons_family_monochrome, listOf(AppIcon.Monochrome)),
    IconFamily(
        R.string.customise_icons_family_liquid_glass,
        listOf(AppIcon.LiquidGlass, AppIcon.LiquidGlassDark, AppIcon.LiquidGlassClear, AppIcon.LiquidGlassClearDark),
    ),
    IconFamily(
        R.string.customise_icons_family_samsung_glass,
        listOf(AppIcon.SamsungGlass, AppIcon.SamsungGlassDark, AppIcon.SamsungGlassClear, AppIcon.SamsungGlassClearDark),
    ),
    IconFamily(
        R.string.customise_icons_family_pixel_glass,
        listOf(AppIcon.PixelGlass, AppIcon.PixelGlassDark, AppIcon.PixelGlassClear, AppIcon.PixelGlassClearDark),
    ),
    IconFamily(
        R.string.customise_icons_family_neon,
        listOf(AppIcon.NeonSilver, AppIcon.NeonCyan, AppIcon.NeonViolet),
    ),
    IconFamily(
        R.string.customise_icons_family_samsung_neon,
        listOf(AppIcon.SamsungNeonSilver, AppIcon.SamsungNeonCyan, AppIcon.SamsungNeonViolet),
    ),
    IconFamily(
        R.string.customise_icons_family_pixel_neon,
        listOf(AppIcon.PixelNeonSilver, AppIcon.PixelNeonCyan, AppIcon.PixelNeonViolet),
    ),
)

private fun AppIcon.family(): IconFamily = IconFamilies.first { it.icons.contains(this) }

@Composable
internal fun IconsSection() {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var picked by remember { mutableStateOf(AppIcon.current(context)) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    val art = rememberIconArt()

    fun pick(icon: AppIcon) {
        if (icon == picked) return
        haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
        picked = icon
        // the package manager is a call to the system, kept off the main thread
        scope.launch(Dispatchers.IO) { AppIcon.apply(context.applicationContext, icon) }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(LabColors.Card)
            .padding(top = CardTextInset, bottom = 20.dp),
    ) {
        PickedIcon(icon = picked, art = art, expanded = expanded, onToggle = { expanded = !expanded })
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
            exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
        ) {
            Column {
                IconFamilies.forEach { family ->
                    FamilyIcons(
                        family = family,
                        picked = picked,
                        art = art,
                        onPick = ::pick,
                    )
                }
                Text(
                    text = stringResource(R.string.customise_icons_caption),
                    style = CaptionStyle,
                    color = Color.White.copy(alpha = 0.45f),
                    modifier = Modifier.padding(start = CardTextInset, end = CardTextInset, top = 16.dp),
                )
            }
        }
    }
}

// each piece of art loaded once and shared by every tile
@Composable
private fun rememberIconArt(): Map<AppIcon, Painter> {
    val ids = remember { AppIcon.entries.associateWith { it.previewRes ?: it.iconRes } }
    return ids.mapValues { rememberResourcePainter(it.value) }
}

// the icon in use with its name and family, and the chevron that opens every other choice
@Composable
private fun PickedIcon(icon: AppIcon, art: Map<AppIcon, Painter>, expanded: Boolean, onToggle: () -> Unit) {
    val turn by animateFloatAsState(if (expanded) 180f else 0f, tween(260), label = "iconsChevron")
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(interactionSource = interaction, indication = RemoteFocus, onClick = onToggle)
            .padding(horizontal = CardTextInset),
    ) {
        Text(
            text = stringResource(R.string.customise_icons),
            style = CardLabelStyle,
            color = Color.White.copy(alpha = 0.9f),
        )
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconArt(icon = icon, art = art, picked = true)
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(icon.labelRes),
                    style = CardLabelStyle,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(icon.family().title),
                    style = CaptionStyle,
                    color = Color.White.copy(alpha = 0.5f),
                )
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(34.dp)
                    .pressBounce(interaction, pressedScale = 0.9f)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.08f)),
            ) {
                Icon(
                    painter = painterResource(R.drawable.expand_more),
                    contentDescription = stringResource(if (expanded) R.string.customise_icons_show_less else R.string.customise_icons_show_all),
                    tint = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier
                        .size(22.dp)
                        .graphicsLayer { rotationZ = turn },
                )
            }
        }
    }
}

// one family: its ready-made icons swiped sideways
@Composable
private fun FamilyIcons(
    family: IconFamily,
    picked: AppIcon,
    art: Map<AppIcon, Painter>,
    onPick: (AppIcon) -> Unit,
) {
    Spacer(Modifier.height(18.dp))
    Text(
        text = stringResource(family.title),
        style = CaptionStyle,
        color = Color.White.copy(alpha = 0.5f),
        modifier = Modifier.padding(horizontal = CardTextInset),
    )
    Spacer(Modifier.height(10.dp))
    // swiped sideways: a family's tiles don't fit across a phone with their names
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(PaddingValues(horizontal = CardTextInset)),
    ) {
        family.icons.forEach { icon ->
            IconTile(icon = icon, art = art, picked = icon == picked, onClick = { onPick(icon) }) {
                TileLabel(text = stringResource(icon.labelRes), picked = icon == picked)
            }
        }
    }
}

// a tile you tap to wear that icon, with its name under it
@Composable
private fun IconTile(icon: AppIcon, art: Map<AppIcon, Painter>, picked: Boolean, onClick: () -> Unit, label: @Composable () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(TileSize + 10.dp),
    ) {
        IconArt(
            icon = icon,
            art = art,
            picked = picked,
            modifier = Modifier
                .pressBounce(interaction, pressedScale = 0.94f)
                .clickable(interactionSource = interaction, indication = RemoteFocus, onClick = onClick),
        )
        Spacer(Modifier.height(8.dp))
        label()
    }
}

/** the icon as the launcher will draw it: the preview foreground fills the tile */
@Composable
private fun IconArt(icon: AppIcon, art: Map<AppIcon, Painter>, picked: Boolean, modifier: Modifier = Modifier) {
    val ring by animateColorAsState(if (picked) TileRingPicked else TileRing, tween(220), label = "iconRing")
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(TileSize)
            .clip(TileShape)
            .border(if (picked) 2.dp else 1.dp, ring, TileShape),
    ) {
        Image(
            painter = art.getValue(icon),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(TileSize),
        )
    }
}

@Composable
private fun TileLabel(text: String, picked: Boolean) {
    Text(
        text = text,
        style = CaptionStyle,
        color = Color.White.copy(alpha = if (picked) 0.95f else 0.6f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}
