// Ported from Mochy Music (GPL-3.0): link/LinkSongs.kt
// LinkSongs.kt

package com.music.bitchord.ui.mochy.link

import com.music.bitchord.ui.mochy.models.MediaMetadata
import org.json.JSONArray
import org.json.JSONObject

// a song as one device hands it to another: enough to show it and to find it on youtube, no local paths
internal object LinkSongs {
    fun toJson(song: MediaMetadata): JSONObject = JSONObject()
        .put("id", song.id)
        .put("title", song.title)
        .put("duration", song.duration)
        .put("thumbnailUrl", song.thumbnailUrl)
        .put("explicit", song.explicit)
        .put("artists", JSONArray(song.artists.map { JSONObject().put("id", it.id).put("name", it.name) }))
        .apply { song.album?.let { put("album", JSONObject().put("id", it.id).put("title", it.title)) } }

    fun fromJson(json: JSONObject): MediaMetadata? {
        val id = json.optString("id").ifBlank { return null }
        val artists = json.optJSONArray("artists")
        return MediaMetadata(
            id = id,
            title = json.optString("title"),
            artists = (0 until (artists?.length() ?: 0)).mapNotNull { index ->
                val artist = artists?.optJSONObject(index) ?: return@mapNotNull null
                MediaMetadata.Artist(id = artist.optString("id").takeIf { it.isNotBlank() && it != "null" }, name = artist.optString("name"))
            },
            duration = json.optInt("duration", -1),
            thumbnailUrl = json.optString("thumbnailUrl").takeIf { it.isNotBlank() && it != "null" },
            album = json.optJSONObject("album")?.let { MediaMetadata.Album(it.optString("id"), it.optString("title")) },
            explicit = json.optBoolean("explicit"),
        )
    }

    fun listToJson(songs: List<MediaMetadata>) = JSONArray(songs.map(::toJson))

    fun listFromJson(array: JSONArray?): List<MediaMetadata> =
        (0 until (array?.length() ?: 0)).mapNotNull { array?.optJSONObject(it)?.let(::fromJson) }
}
