// RadioComponents — the station tiles and rows of the Modern radio page.
//
// Mochy has no Radio Browser screen (its only "radio" is YouTube song /
// album radio queues), so these are a ChordWave-original design in
// Mochy's visual language: dark glass, Mochy's GridItem cards and
// PlaylistTrackRow rows, the Mochy playing indicator on the cover of
// whatever is on air. The Classic tile ideas that survive are the
// initials-over-gradient fallback for stations without artwork and the
// country · tag · bitrate meta line.

package com.music.bitchord.ui.mochy.screens.radio

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.music.bitchord.R
import com.music.bitchord.radio.RadioStation
import com.music.bitchord.ui.mochy.component.GridItem
import com.music.bitchord.ui.mochy.component.PlayingIndicatorBox
import com.music.bitchord.ui.mochy.constants.GridThumbnailCornerRadius
import kotlin.math.abs

/** "Prambors FM" → "PF", "RRI Pro 1" → "RP", "Bens Radio" → "BR". */
internal fun initialsOf(name: String): String {
    val words = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    val letters = words.take(2).mapNotNull { word -> word.firstOrNull { it.isLetterOrDigit() } }
    val joined = letters.joinToString("").uppercase()
    return joined.ifEmpty { name.take(2).uppercase() }
}

/** "Indonesia · Pop · 128 kbps" — country, first tag, bitrate, whatever exists. */
internal fun metaOf(station: RadioStation): String = listOfNotNull(
    station.country?.takeIf { it.isNotBlank() },
    station.tags?.split(',')?.firstOrNull()?.trim()?.takeIf { it.isNotBlank() },
    station.bitrate.takeIf { it > 0 }?.let { "$it kbps" },
).joinToString(" · ")

/**
 * Muted dark gradient pairs for stations without artwork. The Classic
 * tiles drew theirs from the active colour scheme, but MochyTheme's
 * scheme is greys on black — drawn from it, every tile would be the same
 * grey. These fixed jewel tones keep the page dark while telling the
 * stations apart.
 */
private data class TilePalette(val from: Color, val to: Color)

private val TilePalettes = listOf(
    TilePalette(Color(0xFF3B2F5E), Color(0xFF1E3A5F)),
    TilePalette(Color(0xFF5E2F3B), Color(0xFF3B2F5E)),
    TilePalette(Color(0xFF1F4E4A), Color(0xFF1E3A5F)),
    TilePalette(Color(0xFF4E3B1F), Color(0xFF5E2F2F)),
    TilePalette(Color(0xFF2F4E2A), Color(0xFF1F4E4A)),
    TilePalette(Color(0xFF333333), Color(0xFF1A1A1A)),
)

private fun paletteFor(station: RadioStation): TilePalette =
    TilePalettes[abs(station.uuid.hashCode()) % TilePalettes.size]

/**
 * The station's artwork: its favicon when it has one that loads,
 * otherwise its initials over a dark gradient. The initials sit
 * underneath either way, so a broken or missing icon never leaves a hole.
 */
@Composable
fun StationArtwork(
    station: RadioStation,
    modifier: Modifier = Modifier,
) {
    val palette = paletteFor(station)
    var failed by remember(station.uuid) { mutableStateOf(false) }
    val favicon = station.favicon?.takeIf { it.isNotBlank() }
    Box(
        modifier
            .clip(RoundedCornerShape(GridThumbnailCornerRadius))
            .background(Brush.linearGradient(listOf(palette.from, palette.to))),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initialsOf(station.name),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = 0.9f),
        )
        if (favicon != null && !failed) {
            AsyncImage(
                model = favicon,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onError = { failed = true },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** Small round heart chip overlaid on a tile's corner. */
@Composable
private fun FavoriteChip(isFavorite: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(
                if (isFavorite) R.drawable.favorite else R.drawable.favorite_border,
            ),
            contentDescription = stringResource(R.string.radio_favourite),
            tint = if (isFavorite) MaterialTheme.colorScheme.error else Color.White,
            modifier = Modifier.size(17.dp),
        )
    }
}

/** One station as a Mochy grid card: artwork, name, meta, heart chip. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StationCard(
    station: RadioStation,
    isCurrent: Boolean,
    isPlaying: Boolean,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GridItem(
        modifier = modifier.combinedClickable(
            onClick = onClick,
            onLongClick = onLongClick,
        ),
        title = station.name,
        subtitle = metaOf(station),
        thumbnailContent = {
            StationArtwork(station = station, modifier = Modifier.fillMaxSize())
            PlayingIndicatorBox(
                modifier = Modifier.fillMaxSize(),
                isActive = isCurrent,
                playWhenReady = isPlaying,
                shape = RoundedCornerShape(GridThumbnailCornerRadius),
            )
            Box(
                Modifier.fillMaxSize().padding(8.dp),
                contentAlignment = Alignment.TopEnd,
            ) {
                FavoriteChip(isFavorite = isFavorite, onClick = onToggleFavorite)
            }
        },
    )
}

/** A swipeable row of station cards, Mochy Explore's LazyRow shape. */
@Composable
fun StationCardsRow(
    stations: List<RadioStation>,
    isCurrent: (RadioStation) -> Boolean,
    isPlaying: Boolean,
    isFavorite: (RadioStation) -> Boolean,
    onStationClick: (RadioStation) -> Unit,
    onStationLongClick: (RadioStation) -> Unit,
    onToggleFavorite: (RadioStation) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier,
        contentPadding = WindowInsets.systemBars
            .only(WindowInsetsSides.Horizontal)
            .asPaddingValues(),
    ) {
        items(stations, key = { it.uuid }) { station ->
            StationCard(
                station = station,
                isCurrent = isCurrent(station),
                isPlaying = isPlaying,
                isFavorite = isFavorite(station),
                onClick = { onStationClick(station) },
                onLongClick = { onStationLongClick(station) },
                onToggleFavorite = { onToggleFavorite(station) },
            )
        }
    }
}
