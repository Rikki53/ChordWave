// Ported from Mochy Music (GPL-3.0): link/LinkControlRoutes.kt
// LinkControlRoutes.kt
//
// Adaptations: drives ChordWave's MochyPlayerConnection (its player is
// nullable until a controller binds); the remote-settings and player-style
// routes are not ported — a steered device keeps its own settings for now,
// and "sync" is accepted as a no-op until a ChordWave TV counterpart can
// pull a library. Liked comes from the metadata the player already holds.

package com.music.bitchord.ui.mochy.link

import android.os.Handler
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.Player
import com.music.bitchord.ui.mochy.extensions.metadata
import com.music.bitchord.ui.mochy.extensions.toMediaItem
import com.music.bitchord.ui.mochy.playback.MochyPlayerConnection
import com.music.bitchord.ui.mochy.playback.queues.ListQueue
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

// what a paired phone may ask of the device it controls: what's on, play this, line that up, skip
internal class LinkControlRoutes(
    private val onOpenPlayer: () -> Unit = {},
) {
    private val main = Handler(Looper.getMainLooper())

    // songs go straight to the player: a phone that is itself steering something mustn't pass them on again
    fun handle(request: LinkRequest, connection: MochyPlayerConnection): LinkResponse {
        val body = if (request.method == "POST") runCatching { JSONObject(request.bodyText()) }.getOrNull() ?: JSONObject() else JSONObject()
        return when (request.path) {
            "/now" -> LinkResponse.json(now(connection).toString())
            "/play" -> {
                val songs = LinkSongs.listFromJson(body.optJSONArray("songs"))
                if (songs.isEmpty()) return LinkResponse.status(400)
                val start = body.optInt("index").coerceIn(0, songs.lastIndex)
                onMain {
                    connection.playQueue(
                        ListQueue(
                            title = body.optString("title").ifBlank { null },
                            items = songs.map { it.toMediaItem() },
                            startIndex = start,
                            position = body.optLong("position"),
                        ),
                    )
                    onOpenPlayer()
                }
                ok()
            }
            "/next" -> {
                val songs = LinkSongs.listFromJson(body.optJSONArray("songs"))
                onMain { connection.playNext(songs.map { it.toMediaItem() }) }
                ok()
            }
            "/queue" -> {
                val songs = LinkSongs.listFromJson(body.optJSONArray("songs"))
                onMain { connection.addToQueue(songs.map { it.toMediaItem() }) }
                ok()
            }
            "/jump" -> {
                val index = body.optInt("index", -1)
                onMain {
                    val player = connection.player ?: return@onMain
                    if (index in 0 until player.mediaItemCount) {
                        player.seekToDefaultPosition(index)
                        if (player.playbackState == Player.STATE_IDLE || player.playbackState == Player.STATE_ENDED) player.prepare()
                        player.playWhenReady = true
                    }
                }
                ok()
            }
            "/remove" -> {
                val index = body.optInt("index", -1)
                onMain {
                    val player = connection.player ?: return@onMain
                    // the song playing stays, the same as on the queue page
                    if (index in 0 until player.mediaItemCount && index != player.currentMediaItemIndex) player.removeMediaItem(index)
                }
                ok()
            }
            "/control" -> control(connection, body)
            else -> LinkResponse.status(404)
        }
    }

    private fun control(connection: MochyPlayerConnection, body: JSONObject): LinkResponse {
        val position = body.optLong("position", -1L)
        when (body.optString("action")) {
            // a library sync needs the TV side of the link, which ChordWave does not have yet; accepted so the other side does not read it as a failure
            "sync" -> Unit
            else -> onMain {
                when (body.optString("action")) {
                    "toggle" -> connection.togglePlayPause()
                    "play" -> connection.play()
                    "pause" -> connection.pause()
                    "next" -> connection.seekToNext()
                    "previous" -> connection.seekToPrevious()
                    "seek" -> if (position >= 0) connection.seekTo(position)
                    "like" -> connection.toggleLike()
                    "open" -> onOpenPlayer()
                }
            }
        }
        return ok()
    }

    // what this device is playing and what comes after it, read on the main thread where the player lives
    private fun now(connection: MochyPlayerConnection): JSONObject {
        var json = JSONObject()
        onMainAndWait {
            val player = connection.player
            val song = connection.mediaMetadata.value
            json = JSONObject()
                .put("playing", connection.isPlaying.value)
                .put("position", player?.currentPosition?.coerceAtLeast(0L) ?: 0L)
                .put("duration", player?.duration?.coerceAtLeast(0L) ?: 0L)
                .put("index", player?.currentMediaItemIndex ?: -1)
                .put("liked", song?.liked == true)
                .put("upNext", player?.let(::upNext) ?: JSONArray())
                .apply { song?.let { put("song", LinkSongs.toJson(it)) } }
        }
        return json
    }

    // the songs still to play, in the order they will, shuffle included
    private fun upNext(player: Player): JSONArray {
        val out = JSONArray()
        val timeline = player.currentTimeline
        if (timeline.isEmpty) return out
        var index = player.currentMediaItemIndex
        while (out.length() < MaxUpNext) {
            index = timeline.getNextWindowIndex(index, Player.REPEAT_MODE_OFF, player.shuffleModeEnabled)
            if (index == C.INDEX_UNSET) break
            val song = player.getMediaItemAt(index).metadata ?: continue
            out.put(JSONObject().put("index", index).put("song", LinkSongs.toJson(song)))
        }
        return out
    }

    private fun ok() = LinkResponse.json("{}")

    private fun onMain(block: () -> Unit) {
        main.post(block)
    }

    private fun onMainAndWait(block: () -> Unit) {
        val done = CountDownLatch(1)
        main.post {
            try {
                block()
            } finally {
                done.countDown()
            }
        }
        done.await(2, TimeUnit.SECONDS)
    }

    private companion object {
        const val MaxUpNext = 25
    }
}
