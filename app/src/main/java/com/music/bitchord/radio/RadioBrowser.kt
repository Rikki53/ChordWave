package com.music.bitchord.radio

import android.content.Context
import com.music.bitchord.data.model.Song
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Internet radio, backed by the community-run Radio Browser directory
 * (radio-browser.info): tens of thousands of stations, no account, no key.
 *
 * A station rides the ordinary playback pipeline rather than a player of its
 * own: [RadioStation.asSong] puts the stream URL where [Song.videoId] sits,
 * and `Song.toMediaItem` passes an http(s) id straight through as the item's
 * URI, so the queue, the notification, the widgets and the lock screen all
 * treat a station exactly like any other song whose file happens to be a
 * never-ending stream.
 */
@Serializable
data class RadioStation(
    val uuid: String,
    val name: String,
    val streamUrl: String,
    val favicon: String? = null,
    val country: String? = null,
    val tags: String? = null,
    val bitrate: Int = 0,
    val votes: Int = 0,
) {
    fun asSong(): Song = Song(
        videoId = streamUrl,
        title = name,
        artist = listOfNotNull(
            country?.takeIf { it.isNotBlank() },
            tags?.split(',')?.firstOrNull()?.trim()?.takeIf { it.isNotBlank() },
        ).joinToString(" · ").ifBlank { "Internet radio" },
        thumbnailUrl = favicon?.takeIf { it.isNotBlank() },
    )
}

@Serializable
private data class RadioStationDto(
    val stationuuid: String,
    val name: String,
    val url: String = "",
    val url_resolved: String = "",
    val favicon: String? = null,
    val country: String? = null,
    val tags: String? = null,
    val bitrate: Int = 0,
    val votes: Int = 0,
)

private fun RadioStationDto.toStation(): RadioStation? {
    val stream = url_resolved.ifBlank { url }
    if (stream.isBlank() || name.isBlank()) return null
    return RadioStation(
        uuid = stationuuid,
        name = name.trim(),
        streamUrl = stream,
        favicon = favicon,
        country = country,
        tags = tags,
        bitrate = bitrate,
        votes = votes,
    )
}

object RadioBrowserApi {
    // Radio Browser is mirrored; try each host in turn before giving up.
    private val hosts = listOf(
        "https://de1.api.radio-browser.info",
        "https://nl1.api.radio-browser.info",
        "https://at1.api.radio-browser.info",
    )

    private const val USER_AGENT = "ChordWave/1.0.0 (https://github.com/Rikki53/ChordWave)"

    private val json = Json { ignoreUnknownKeys = true }

    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) { json(json) }
    }

    private suspend fun fetch(path: String): List<RadioStation> {
        var failure: Throwable? = null
        for (host in hosts) {
            try {
                val rows: List<RadioStationDto> = client.get("$host$path") {
                    header(HttpHeaders.UserAgent, USER_AGENT)
                }.body()
                return rows.mapNotNull { it.toStation() }
            } catch (t: Throwable) {
                failure = t
            }
        }
        throw failure ?: IllegalStateException("Radio Browser unreachable")
    }

    suspend fun topStations(limit: Int = 40): List<RadioStation> =
        fetch("/json/stations/topvote/$limit?hidebroken=true")

    suspend fun stationsForCountry(country: String, limit: Int = 60): List<RadioStation> =
        fetch("/json/stations/bycountry/$country?order=votes&reverse=true&hidebroken=true&limit=$limit")

    suspend fun search(query: String, limit: Int = 60): List<RadioStation> =
        fetch("/json/stations/search?name=$query&order=votes&reverse=true&hidebroken=true&limit=$limit")

    /** Radio Browser asks apps to register a play, which feeds its rankings. */
    suspend fun registerPlay(station: RadioStation) {
        try {
            client.get("${hosts.first()}/json/url/${station.uuid}") {
                header(HttpHeaders.UserAgent, USER_AGENT)
            }
        } catch (_: Throwable) {
            // A lost click count is not worth surfacing.
        }
    }
}

/** Favourite stations, kept in a small preferences file of their own. */
class RadioFavorites(context: Context) {
    private val prefs = context.getSharedPreferences("radio_favorites", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    private val _stations = MutableStateFlow(load())
    val stations: StateFlow<List<RadioStation>> = _stations

    private fun load(): List<RadioStation> = try {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        json.decodeFromString<List<RadioStation>>(raw)
    } catch (_: Throwable) {
        emptyList()
    }

    fun contains(station: RadioStation): Boolean = _stations.value.any { it.uuid == station.uuid }

    fun toggle(station: RadioStation) {
        val next = if (contains(station)) {
            _stations.value.filterNot { it.uuid == station.uuid }
        } else {
            listOf(station) + _stations.value
        }
        _stations.value = next
        prefs.edit().putString(KEY, json.encodeToString(next)).apply()
    }

    private companion object {
        const val KEY = "stations"
    }
}
