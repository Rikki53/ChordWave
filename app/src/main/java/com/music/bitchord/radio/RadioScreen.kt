package com.music.bitchord.radio

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import com.music.bitchord.R
import com.music.bitchord.ui.components.SearchField
import com.music.bitchord.ui.icons.SfIcons
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * The Radio tab: stations from Radio Browser, presented the way Apple
 * Music's radio page presents them — a hero carousel of the most-voted
 * stations up top, swipeable rows of big artwork tiles, and a two-column
 * tile grid — instead of a plain list of rows. Stations without artwork
 * get a tile of their initials over a gradient drawn from the app's own
 * colour scheme, so the page follows whatever theme is active. Tapping a
 * station hands its stream to the ordinary player — see
 * [RadioStation.asSong].
 */
class RadioViewModel(application: Application) : AndroidViewModel(application) {
    val favorites = RadioFavorites(application)

    private val _indonesia = MutableStateFlow<List<RadioStation>>(emptyList())
    val indonesia: StateFlow<List<RadioStation>> = _indonesia

    private val _top = MutableStateFlow<List<RadioStation>>(emptyList())
    val top: StateFlow<List<RadioStation>> = _top

    private val _results = MutableStateFlow<List<RadioStation>?>(null)
    val results: StateFlow<List<RadioStation>?> = _results

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading

    private val _failed = MutableStateFlow(false)
    val failed: StateFlow<Boolean> = _failed

    var query by mutableStateOf("")
        private set

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _loading.value = true
            _failed.value = false
            try {
                _indonesia.value = RadioBrowserApi.stationsForCountry("Indonesia")
                _top.value = RadioBrowserApi.topStations()
            } catch (_: Throwable) {
                _failed.value = true
            } finally {
                _loading.value = false
            }
        }
    }

    fun onQueryChange(value: String) {
        query = value
        if (value.isBlank()) _results.value = null
    }

    fun submitSearch() {
        val term = query.trim()
        if (term.isEmpty()) return
        viewModelScope.launch {
            _loading.value = true
            try {
                _results.value = RadioBrowserApi.search(term)
            } catch (_: Throwable) {
                _results.value = emptyList()
            } finally {
                _loading.value = false
            }
        }
    }
}

@Composable
fun RadioScreen(
    viewModel: RadioViewModel,
    currentStationUrl: String?,
    isPlaying: Boolean,
    onPlayStation: (RadioStation) -> Unit,
    modifier: Modifier = Modifier,
) {
    val indonesia by viewModel.indonesia.collectAsState()
    val top by viewModel.top.collectAsState()
    val results by viewModel.results.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val failed by viewModel.failed.collectAsState()
    val favorites by viewModel.favorites.stations.collectAsState()

    fun fav(station: RadioStation) = favorites.any { it.uuid == station.uuid }

    Column(modifier = modifier.fillMaxSize()) {
        Text(
            text = stringResource(R.string.radio_title),
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp),
        )
        Spacer(Modifier.height(12.dp))
        SearchField(
            query = viewModel.query,
            onQueryChange = viewModel::onQueryChange,
            onSubmit = viewModel::submitSearch,
            placeholder = stringResource(R.string.radio_search_hint),
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(8.dp))

        val shown = results
        when {
            loading && indonesia.isEmpty() && shown == null -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            shown != null -> StationGrid(
                stations = shown,
                isFavorite = ::fav,
                currentStationUrl = currentStationUrl,
                isPlaying = isPlaying,
                onPlayStation = onPlayStation,
                onToggleFavorite = viewModel.favorites::toggle,
                emptyText = stringResource(R.string.radio_no_results),
            )

            failed && indonesia.isEmpty() && top.isEmpty() -> Column(
                Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = stringResource(R.string.radio_load_failed),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.radio_retry),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { viewModel.refresh() }.padding(8.dp),
                )
            }

            else -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 190.dp),
            ) {
                if (top.isNotEmpty()) {
                    item {
                        HeroCarousel(
                            stations = top.take(8),
                            currentStationUrl = currentStationUrl,
                            isPlaying = isPlaying,
                            onPlayStation = onPlayStation,
                        )
                    }
                }
                if (favorites.isNotEmpty()) {
                    item { SectionTitle(stringResource(R.string.radio_favourites)) }
                    item {
                        StationRow(
                            stations = favorites,
                            isFavorite = { true },
                            currentStationUrl = currentStationUrl,
                            onPlayStation = onPlayStation,
                            onToggleFavorite = viewModel.favorites::toggle,
                        )
                    }
                }
                if (indonesia.isNotEmpty()) {
                    item { SectionTitle(stringResource(R.string.radio_indonesia)) }
                    item {
                        StationRow(
                            stations = indonesia,
                            isFavorite = ::fav,
                            currentStationUrl = currentStationUrl,
                            onPlayStation = onPlayStation,
                            onToggleFavorite = viewModel.favorites::toggle,
                        )
                    }
                }
                if (top.isNotEmpty()) {
                    item { SectionTitle(stringResource(R.string.radio_top_worldwide)) }
                    items(top.chunked(2), key = { "top:" + it.first().uuid }) { pair ->
                        GridRow(
                            pair = pair,
                            isFavorite = ::fav,
                            currentStationUrl = currentStationUrl,
                            onPlayStation = onPlayStation,
                            onToggleFavorite = viewModel.favorites::toggle,
                        )
                    }
                }
            }
        }
    }
}

/** Search results, as the same two-column tile grid the browse page ends in. */
@Composable
private fun StationGrid(
    stations: List<RadioStation>,
    isFavorite: (RadioStation) -> Boolean,
    currentStationUrl: String?,
    isPlaying: Boolean,
    onPlayStation: (RadioStation) -> Unit,
    onToggleFavorite: (RadioStation) -> Unit,
    emptyText: String,
) {
    if (stations.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text(
                text = emptyText,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 190.dp)) {
        items(stations.chunked(2), key = { "search:" + it.first().uuid }) { pair ->
            GridRow(
                pair = pair,
                isFavorite = isFavorite,
                currentStationUrl = currentStationUrl,
                onPlayStation = onPlayStation,
                onToggleFavorite = onToggleFavorite,
            )
        }
    }
}

@Composable
private fun GridRow(
    pair: List<RadioStation>,
    isFavorite: (RadioStation) -> Boolean,
    currentStationUrl: String?,
    onPlayStation: (RadioStation) -> Unit,
    onToggleFavorite: (RadioStation) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        pair.forEach { station ->
            StationGridCard(
                station = station,
                isFavorite = isFavorite(station),
                isCurrent = station.streamUrl == currentStationUrl,
                onClick = { onPlayStation(station) },
                onToggleFavorite = { onToggleFavorite(station) },
                modifier = Modifier.weight(1f),
            )
        }
        if (pair.size == 1) Spacer(Modifier.weight(1f))
    }
}

/** One swipeable row of square station tiles under a section title. */
@Composable
private fun StationRow(
    stations: List<RadioStation>,
    isFavorite: (RadioStation) -> Boolean,
    currentStationUrl: String?,
    onPlayStation: (RadioStation) -> Unit,
    onToggleFavorite: (RadioStation) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items(stations, key = { it.uuid }) { station ->
            StationTile(
                station = station,
                isFavorite = isFavorite(station),
                isCurrent = station.streamUrl == currentStationUrl,
                onClick = { onPlayStation(station) },
                onToggleFavorite = { onToggleFavorite(station) },
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 12.dp),
    )
}

/* ------------------------------------------------------------------ */
/* Tiles                                                               */
/* ------------------------------------------------------------------ */

/** Gradient pairs drawn from the active colour scheme, soft and bold. */
private data class TilePalette(val from: Color, val to: Color, val content: Color)

@Composable
private fun softPalettes(): List<TilePalette> {
    val scheme = MaterialTheme.colorScheme
    return listOf(
        TilePalette(scheme.primaryContainer, scheme.secondaryContainer, scheme.onPrimaryContainer),
        TilePalette(scheme.secondaryContainer, scheme.tertiaryContainer, scheme.onSecondaryContainer),
        TilePalette(scheme.tertiaryContainer, scheme.primaryContainer, scheme.onTertiaryContainer),
        TilePalette(scheme.surfaceVariant, scheme.primaryContainer, scheme.onSurfaceVariant),
        TilePalette(scheme.secondaryContainer, scheme.primaryContainer, scheme.onSecondaryContainer),
        TilePalette(scheme.primaryContainer, scheme.tertiaryContainer, scheme.onPrimaryContainer),
    )
}

@Composable
private fun boldPalettes(): List<TilePalette> {
    val scheme = MaterialTheme.colorScheme
    return listOf(
        TilePalette(scheme.primary, scheme.tertiary, Color.White),
        TilePalette(scheme.secondary, scheme.primary, Color.White),
        TilePalette(scheme.tertiary, scheme.secondary, Color.White),
        TilePalette(scheme.primary, scheme.secondary, Color.White),
    )
}

@Composable
private fun paletteFor(station: RadioStation, bold: Boolean): TilePalette {
    val palettes = if (bold) boldPalettes() else softPalettes()
    return palettes[abs(station.uuid.hashCode()) % palettes.size]
}

/** "Prambors FM" → "PF", "RRI Pro 1" → "RP", "Bens Radio" → "BR". */
private fun initialsOf(name: String): String {
    val words = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    val letters = words.take(2).mapNotNull { word -> word.firstOrNull { it.isLetterOrDigit() } }
    val joined = letters.joinToString("").uppercase()
    return joined.ifEmpty { name.take(2).uppercase() }
}

private fun metaOf(station: RadioStation): String = listOfNotNull(
    station.country?.takeIf { it.isNotBlank() },
    station.tags?.split(',')?.firstOrNull()?.trim()?.takeIf { it.isNotBlank() },
    station.bitrate.takeIf { it > 0 }?.let { "$it kbps" },
).joinToString(" · ")

/**
 * The station's artwork: its favicon when it has one that loads, otherwise
 * its initials over a theme-drawn gradient. The initials sit underneath
 * either way, so a broken or missing icon never leaves a hole.
 */
@Composable
private fun StationArtwork(
    station: RadioStation,
    corner: Dp,
    modifier: Modifier = Modifier,
    bold: Boolean = false,
) {
    val palette = paletteFor(station, bold)
    var failed by remember(station.uuid) { mutableStateOf(false) }
    val favicon = station.favicon?.takeIf { it.isNotBlank() }
    Box(
        modifier
            .clip(RoundedCornerShape(corner))
            .background(Brush.linearGradient(listOf(palette.from, palette.to))),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initialsOf(station.name),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = palette.content,
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

/** Small round star chip overlaid on a tile's corner. */
@Composable
private fun FavoriteChip(isFavorite: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.88f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (isFavorite) SfIcons.StarFill else SfIcons.Star,
            contentDescription = stringResource(R.string.radio_favourite),
            tint = if (isFavorite) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(17.dp),
        )
    }
}

@Composable
private fun StationTile(
    station: RadioStation,
    isFavorite: Boolean,
    isCurrent: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    Column(Modifier.width(126.dp).clickable(onClick = onClick)) {
        Box {
            StationArtwork(
                station = station,
                corner = 18.dp,
                modifier = Modifier
                    .size(126.dp)
                    .then(
                        if (isCurrent) Modifier.border(
                            2.5.dp,
                            MaterialTheme.colorScheme.primary,
                            RoundedCornerShape(18.dp),
                        ) else Modifier,
                    ),
            )
            Box(Modifier.size(126.dp).padding(7.dp), contentAlignment = Alignment.TopEnd) {
                FavoriteChip(isFavorite, onToggleFavorite)
            }
        }
        Spacer(Modifier.height(7.dp))
        Text(
            text = station.name,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = if (isCurrent) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        val meta = metaOf(station)
        if (meta.isNotBlank()) {
            Text(
                text = meta,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun StationGridCard(
    station: RadioStation,
    isFavorite: Boolean,
    isCurrent: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.clickable(onClick = onClick)) {
        Box {
            StationArtwork(
                station = station,
                corner = 18.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .then(
                        if (isCurrent) Modifier.border(
                            2.5.dp,
                            MaterialTheme.colorScheme.primary,
                            RoundedCornerShape(18.dp),
                        ) else Modifier,
                    ),
            )
            Box(
                Modifier.fillMaxWidth().aspectRatio(1f).padding(7.dp),
                contentAlignment = Alignment.TopEnd,
            ) {
                FavoriteChip(isFavorite, onToggleFavorite)
            }
        }
        Spacer(Modifier.height(7.dp))
        Text(
            text = station.name,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = if (isCurrent) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        val meta = metaOf(station)
        if (meta.isNotBlank()) {
            Text(
                text = meta,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/* ------------------------------------------------------------------ */
/* Hero carousel                                                       */
/* ------------------------------------------------------------------ */

@Composable
private fun HeroCarousel(
    stations: List<RadioStation>,
    currentStationUrl: String?,
    isPlaying: Boolean,
    onPlayStation: (RadioStation) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.padding(top = 6.dp),
    ) {
        items(stations, key = { "hero:" + it.uuid }) { station ->
            HeroCard(
                station = station,
                isCurrent = station.streamUrl == currentStationUrl,
                isPlaying = isPlaying,
                onClick = { onPlayStation(station) },
            )
        }
    }
}

@Composable
private fun HeroCard(
    station: RadioStation,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
) {
    val palette = paletteFor(station, bold = true)
    Box(
        Modifier
            .width(292.dp)
            .height(178.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.linearGradient(listOf(palette.from, palette.to)))
            .clickable(onClick = onClick),
    ) {
        // Oversized initials as the card's artwork when there is room for
        // them to read as a design rather than a fallback.
        Text(
            text = initialsOf(station.name),
            style = MaterialTheme.typography.displayLarge,
            fontWeight = FontWeight.Black,
            color = Color.White.copy(alpha = 0.16f),
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 2.dp, end = 14.dp),
        )
        // Scrim so the name stays readable over any gradient.
        Box(
            Modifier
                .fillMaxWidth()
                .height(96.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.42f)),
                    ),
                ),
        )
        val favicon = station.favicon?.takeIf { it.isNotBlank() }
        if (favicon != null) {
            var failed by remember(station.uuid) { mutableStateOf(false) }
            if (!failed) {
                Box(
                    Modifier
                        .padding(14.dp)
                        .size(52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White),
                    contentAlignment = Alignment.Center,
                ) {
                    AsyncImage(
                        model = favicon,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        onError = { failed = true },
                        modifier = Modifier.fillMaxSize().padding(5.dp),
                    )
                }
            }
        }
        Column(
            Modifier.align(Alignment.BottomStart).padding(start = 16.dp, end = 76.dp, bottom = 14.dp),
        ) {
            if (isCurrent) {
                Text(
                    text = stringResource(
                        if (isPlaying) R.string.radio_on_air else R.string.radio_paused,
                    ).uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.85f),
                )
            }
            Text(
                text = station.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val meta = metaOf(station)
            if (meta.isNotBlank()) {
                Text(
                    text = meta,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.82f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(14.dp)
                .size(46.dp)
                .clip(CircleShape)
                .background(Color.White),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = SfIcons.Play,
                contentDescription = null,
                tint = palette.from,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}
