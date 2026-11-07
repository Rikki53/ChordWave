package com.music.bitchord.radio

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
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

/**
 * The Radio tab: stations from Radio Browser, Indonesia first, the world's
 * most-voted below, and everything a search away. Tapping a station hands its
 * stream to the ordinary player — see [RadioStation.asSong].
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

            shown != null -> StationList(
                stations = shown,
                favorites = favorites,
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
                if (favorites.isNotEmpty()) {
                    item {
                        SectionTitle(stringResource(R.string.radio_favourites))
                    }
                    items(favorites, key = { "fav:${it.uuid}" }) { station ->
                        StationRow(
                            station = station,
                            isFavorite = true,
                            isCurrent = station.streamUrl == currentStationUrl,
                            isPlaying = isPlaying,
                            onClick = { onPlayStation(station) },
                            onToggleFavorite = { viewModel.favorites.toggle(station) },
                        )
                    }
                }
                item { SectionTitle(stringResource(R.string.radio_indonesia)) }
                items(indonesia, key = { "id:${it.uuid}" }) { station ->
                    StationRow(
                        station = station,
                        isFavorite = viewModel.favorites.contains(station),
                        isCurrent = station.streamUrl == currentStationUrl,
                        isPlaying = isPlaying,
                        onClick = { onPlayStation(station) },
                        onToggleFavorite = { viewModel.favorites.toggle(station) },
                    )
                }
                item { SectionTitle(stringResource(R.string.radio_top_worldwide)) }
                items(top, key = { "top:${it.uuid}" }) { station ->
                    StationRow(
                        station = station,
                        isFavorite = viewModel.favorites.contains(station),
                        isCurrent = station.streamUrl == currentStationUrl,
                        isPlaying = isPlaying,
                        onClick = { onPlayStation(station) },
                        onToggleFavorite = { viewModel.favorites.toggle(station) },
                    )
                }
            }
        }
    }
}

@Composable
private fun StationList(
    stations: List<RadioStation>,
    favorites: List<RadioStation>,
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
        items(stations, key = { "search:${it.uuid}" }) { station ->
            StationRow(
                station = station,
                isFavorite = favorites.any { it.uuid == station.uuid },
                isCurrent = station.streamUrl == currentStationUrl,
                isPlaying = isPlaying,
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
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp),
    )
}

@Composable
private fun StationRow(
    station: RadioStation,
    isFavorite: Boolean,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = station.favicon,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(12.dp)),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = station.name,
                style = MaterialTheme.typography.titleMedium,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val subtitle = listOfNotNull(
                station.country?.takeIf { it.isNotBlank() },
                station.tags?.split(',')?.firstOrNull()?.trim()?.takeIf { it.isNotBlank() },
                station.bitrate.takeIf { it > 0 }?.let { "$it kbps" },
                if (isCurrent) stringResource(if (isPlaying) R.string.radio_on_air else R.string.radio_paused) else null,
            ).joinToString(" · ")
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        IconButton(onClick = onToggleFavorite) {
            Icon(
                imageVector = if (isFavorite) SfIcons.StarFill else SfIcons.Star,
                contentDescription = stringResource(R.string.radio_favourite),
                tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
