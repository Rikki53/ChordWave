package com.music.bitchord.karaoke

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The karaoke remote's wire format — one shared vocabulary for the TV that
 * serves and the phone that drives, compiled into both from this one file.
 *
 * Transport is a plain WebSocket on the local network (see
 * KaraokeRemoteServer / KaraokeRemoteClient): text frames are the JSON
 * messages below, binary frames are the phone-microphone's PCM (48 kHz mono
 * 16-bit) — the mic is the only binary citizen, and only an authenticated
 * connection's frames are ever played.
 *
 * The session code the TV shows is the whole credential: six digits, minted
 * per remote session, checked once in an `auth` command before anything
 * else from that connection is honoured. There is deliberately nothing
 * stronger here — this is a living-room protocol between two devices on
 * the same Wi-Fi, and the server only exists while the TV's remote dialog
 * is on screen.
 */

const val KARAOKE_REMOTE_SERVICE_TYPE = "_chordwave-karaoke._tcp."
const val KARAOKE_REMOTE_QR_PREFIX = "chordwave://karaoke-remote"
const val KARAOKE_REMOTE_CODE_LENGTH = 6

/** A queue entry as the wire carries it: identity plus what a list shows. */
@Serializable
data class RemoteSong(
    val videoId: String,
    val title: String,
    val artist: String,
)

/** The TV's playback picture, pushed whole whenever anything changes. */
@Serializable
data class RemoteState(
    val type: String = "state",
    val title: String? = null,
    val artist: String? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val queueIndex: Int = -1,
    val queue: List<RemoteSong> = emptyList(),
    /** The karaoke vocal slider as the TV has it, 0..1 (1 = full vocal). */
    val vocalLevel: Float = 1f,
    val karaokeOn: Boolean = false,
    /** Phone-microphone gain as the TV applies it, 0..1, and its mute. */
    val micGain: Float = 0.8f,
    val micMuted: Boolean = false,
    val micLive: Boolean = false,
)

/** One instruction from phone to TV; only the fields its [type] needs. */
@Serializable
data class RemoteCommand(
    val type: String,
    /** Session code; only read on the `auth` command. */
    val token: String? = null,
    val videoId: String? = null,
    val title: String? = null,
    val artist: String? = null,
    val index: Int? = null,
    val toIndex: Int? = null,
    val level: Float? = null,
    val gain: Float? = null,
    val muted: Boolean? = null,
)

val karaokeRemoteJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

/** What the TV's QR code carries: where to knock, and the code to knock with. */
fun karaokeRemoteQrPayload(host: String, port: Int, code: String): String =
    "$KARAOKE_REMOTE_QR_PREFIX?host=$host&port=$port&code=$code"

/** Reads a scanned payload back; null when it is not a karaoke remote code. */
fun parseKaraokeRemoteQr(raw: String): Triple<String, Int, String>? {
    if (!raw.startsWith(KARAOKE_REMOTE_QR_PREFIX)) return null
    val params = raw.substringAfter('?', "")
        .split('&')
        .mapNotNull { part ->
            val eq = part.indexOf('=')
            if (eq <= 0) null else part.substring(0, eq) to part.substring(eq + 1)
        }
        .toMap()
    val host = params["host"]?.takeIf { it.isNotBlank() } ?: return null
    val port = params["port"]?.toIntOrNull() ?: return null
    val code = params["code"]?.filter(Char::isDigit)
        ?.takeIf { it.length == KARAOKE_REMOTE_CODE_LENGTH } ?: return null
    return Triple(host, port, code)
}
