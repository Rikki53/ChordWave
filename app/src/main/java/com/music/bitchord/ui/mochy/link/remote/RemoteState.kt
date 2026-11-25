// Ported from Mochy Music (GPL-3.0): link/remote/RemoteState.kt
// RemoteState.kt
//
// The remote-settings model is not ported — a steered device keeps its
// own settings for now.

package com.music.bitchord.ui.mochy.link.remote

import androidx.compose.runtime.Immutable
import com.music.bitchord.ui.mochy.link.DeviceKind
import com.music.bitchord.ui.mochy.link.LinkSongs
import com.music.bitchord.ui.mochy.models.MediaMetadata
import org.json.JSONObject

// the device a phone is steering, as the pill and the remote show it
@Immutable
data class RemoteTarget(val id: String, val name: String, val kind: DeviceKind)

// one song still to play on the steered device, with its place in that device's queue
@Immutable
data class RemoteQueueItem(val index: Int, val song: MediaMetadata)

// what the steered device said it plays, and when it said it, so the seek bar can run on between asks
@Immutable
data class RemoteNow(
    val song: MediaMetadata?,
    val playing: Boolean,
    val position: Long,
    val duration: Long,
    val liked: Boolean,
    val upNext: List<RemoteQueueItem>,
    val readAt: Long,
) {
    // where the song is now, carried on from the last answer while it plays
    fun positionAt(now: Long): Long {
        if (!playing) return position
        val moved = position + (now - readAt)
        return if (duration > 0) moved.coerceAtMost(duration) else moved
    }

    companion object {
        fun fromJson(json: JSONObject, readAt: Long): RemoteNow {
            val queue = json.optJSONArray("upNext")
            return RemoteNow(
                song = json.optJSONObject("song")?.let(LinkSongs::fromJson),
                playing = json.optBoolean("playing"),
                position = json.optLong("position"),
                duration = json.optLong("duration"),
                liked = json.optBoolean("liked"),
                upNext = (0 until (queue?.length() ?: 0)).mapNotNull { i ->
                    val item = queue?.optJSONObject(i) ?: return@mapNotNull null
                    val song = item.optJSONObject("song")?.let(LinkSongs::fromJson) ?: return@mapNotNull null
                    RemoteQueueItem(item.optInt("index"), song)
                },
                readAt = readAt,
            )
        }
    }
}
