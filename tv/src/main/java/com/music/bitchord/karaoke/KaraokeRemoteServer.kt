package com.music.bitchord.karaoke

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.media3.session.MediaController
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.playback.PlayerState
import com.music.bitchord.playback.toMediaItem
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.security.SecureRandom
import kotlinx.serialization.encodeToString
import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer

/**
 * The TV half of the karaoke remote: a WebSocket server on the LAN, an NSD
 * advertisement, and the phone-as-microphone playback track.
 *
 * One instance exists exactly as long as the TV's remote dialog is on
 * screen: [start] mints a fresh six-digit session code, opens the server
 * and advertises it; [stop] tears all of it down. Connections must present
 * the code in an `auth` command before any other frame — text or binary —
 * is honoured, and the state pushed to phones is read live from the player
 * through [stateProvider], never cached here.
 *
 * Playback commands are applied on the main thread against
 * [controllerProvider]'s MediaController — the same controller the TV's
 * own UI drives, so a remote "next" is indistinguishable from a remote
 * control's.
 */
class KaraokeRemoteServer(
    context: Context,
    private val stateProvider: () -> PlayerState,
    private val controllerProvider: () -> MediaController?,
) {
    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())

    /** The session code, shown on the TV; also the whole credential. */
    val code: String = (SecureRandom().nextInt(900_000) + 100_000).toString()

    @Volatile var port: Int = 0
        private set

    private var server: WsServer? = null
    private var nsd: NsdManager? = null
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private var ticking = false

    val mic = MicPlayer()

    fun localIpAddress(): String? {
        val wifi = appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        @Suppress("DEPRECATION")
        val ip = wifi.connectionInfo?.ipAddress ?: return null
        if (ip == 0) return null
        return InetSocketAddress(ip.toLong().let {
            java.net.InetAddress.getByAddress(
                byteArrayOf(
                    (it and 0xff).toByte(),
                    (it shr 8 and 0xff).toByte(),
                    (it shr 16 and 0xff).toByte(),
                    (it shr 24 and 0xff).toByte(),
                ),
            )
        }, 0).address?.hostAddress
    }

    fun start() {
        if (server != null) return
        // Multicast first: NSD advertisements on several chipsets never
        // leave the device without the lock held.
        val wifi = appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        multicastLock = wifi.createMulticastLock("chordwave-karaoke-serve").apply {
            setReferenceCounted(false)
            acquire()
        }
        var lastError: Exception? = null
        for (attempt in 0 until 6) {
            val candidate = 49_152 + SecureRandom().nextInt(4_000)
            try {
                val ws = WsServer(InetSocketAddress(candidate))
                ws.isReuseAddr = true
                ws.start()
                server = ws
                port = candidate
                lastError = null
                break
            } catch (e: Exception) {
                lastError = e
            }
        }
        if (server == null) {
            multicastLock?.let { if (it.isHeld) it.release() }
            throw IllegalStateException("Could not open a port for the karaoke remote", lastError)
        }
        nsd = appContext.getSystemService(Context.NSD_SERVICE) as NsdManager
        val info = NsdServiceInfo().apply {
            serviceName = "ChordWave Karaoke"
            serviceType = KARAOKE_REMOTE_SERVICE_TYPE
            setPort(port)
        }
        registrationListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) = Unit
            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) = Unit
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
        }
        runCatching { nsd?.registerService(info, NsdManager.PROTOCOL_DNS_SD, registrationListener) }
        ticking = true
        main.post(tick)
    }

    fun stop() {
        ticking = false
        main.removeCallbacks(tick)
        registrationListener?.let { listener -> runCatching { nsd?.unregisterService(listener) } }
        registrationListener = null
        nsd = null
        runCatching { server?.stop(200) }
        server = null
        mic.stop()
        multicastLock?.let { if (it.isHeld) it.release() }
        multicastLock = null
    }

    /** Push the current picture to everyone connected, right now. */
    fun broadcastState() {
        val ws = server ?: return
        val payload = karaokeRemoteJson.encodeToString(buildState())
        for (conn in ws.connections) {
            if (conn.getAttachment<Boolean>() == true && conn.isOpen) {
                runCatching { conn.send(payload) }
            }
        }
    }

    private val tick = object : Runnable {
        override fun run() {
            if (!ticking) return
            broadcastState()
            main.postDelayed(this, 1_000)
        }
    }

    private fun buildState(): RemoteState {
        val player = stateProvider()
        val current = player.song
        return RemoteState(
            title = current?.title,
            artist = current?.artist,
            isPlaying = player.isPlaying,
            positionMs = player.position.positionMs,
            durationMs = player.durationMs,
            queueIndex = player.queueIndex,
            queue = player.queue.map { RemoteSong(it.videoId, it.title, it.artist) },
            vocalLevel = AppSettings.karaokeVocalLevel.value,
            karaokeOn = AppSettings.karaokeEnabled.value,
            micGain = mic.gain,
            micMuted = mic.muted,
            micLive = mic.live,
        )
    }

    private fun applyCommand(command: RemoteCommand) {
        val controller = controllerProvider()
        when (command.type) {
            "play" -> controller?.play()
            "pause" -> controller?.pause()
            "toggle" -> controller?.let { if (it.isPlaying) it.pause() else it.play() }
            "next" -> controller?.seekToNextMediaItem()
            "prev" -> controller?.seekToPrevious()
            "playIndex" -> command.index?.let { index ->
                controller?.seekToDefaultPosition(index)
                controller?.play()
            }
            "add" -> {
                val videoId = command.videoId ?: return
                val song = com.music.bitchord.data.model.Song(
                    videoId = videoId,
                    title = command.title ?: "",
                    artist = command.artist ?: "",
                    thumbnailUrl = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
                )
                // Appended, karaoke-style: a requested song joins the end of
                // the line. The service enriches it when its turn comes.
                controller?.addMediaItem(song.toMediaItem())
            }
            "remove" -> command.index?.let { index ->
                val count = controller?.mediaItemCount ?: 0
                if (index in 0 until count) controller?.removeMediaItem(index)
            }
            "move" -> {
                val from = command.index ?: return
                val to = command.toIndex ?: return
                val count = controller?.mediaItemCount ?: 0
                if (from in 0 until count && to in 0 until count) {
                    controller?.moveMediaItem(from, to)
                }
            }
            "vocal" -> command.level?.let { level ->
                AppSettings.setKaraokeEnabled(true)
                AppSettings.setKaraokeVocalLevel(level.coerceIn(0f, 1f))
            }
            "micGain" -> command.gain?.let { mic.gain = it.coerceIn(0f, 1f) }
            "micMute" -> command.muted?.let { mic.muted = it }
            "micStart" -> mic.live = true
            "micStop" -> {
                mic.live = false
                mic.stop()
            }
        }
        broadcastState()
    }

    private inner class WsServer(address: InetSocketAddress) : WebSocketServer(address) {
        override fun onOpen(conn: WebSocket, handshake: ClientHandshake?) {
            conn.setAttachment(false)
        }

        override fun onClose(conn: WebSocket, codeInt: Int, reason: String?, remote: Boolean) = Unit

        override fun onMessage(conn: WebSocket, message: String) {
            val command = runCatching {
                karaokeRemoteJson.decodeFromString<RemoteCommand>(message)
            }.getOrNull() ?: return
            if (conn.getAttachment<Boolean>() != true) {
                if (command.type == "auth" && command.token == code) {
                    conn.setAttachment(true)
                    runCatching {
                        conn.send(karaokeRemoteJson.encodeToString(RemoteState(type = "auth-ok")))
                    }
                    main.post { broadcastState() }
                } else {
                    conn.close(1008, "auth")
                }
                return
            }
            main.post { applyCommand(command) }
        }

        override fun onMessage(conn: WebSocket, bytes: ByteBuffer) {
            if (conn.getAttachment<Boolean>() != true || !mic.live) return
            val pcm = ByteArray(bytes.remaining()).also { bytes.get(it) }
            mic.write(pcm)
        }

        override fun onError(conn: WebSocket?, ex: Exception?) {
            ex?.printStackTrace()
        }

        override fun onStart() = Unit
    }
}

/**
 * The singing half of the TV: a low-latency AudioTrack that plays the
 * phone microphone's PCM alongside whatever ExoPlayer is doing. Both write
 * to the same output device through the ordinary media mix — no audio
 * focus is taken, so the music is never ducked or stopped by the mic.
 *
 * Gain is applied per frame on the socket's own reader thread; the track
 * is created on the first live frame and released when the stream stops,
 * so an idle remote costs the TV nothing.
 */
class MicPlayer {
    @Volatile var gain: Float = 0.8f
    @Volatile var muted: Boolean = false
    @Volatile var live: Boolean = false

    private var track: AudioTrack? = null

    @Synchronized
    fun write(pcm: ByteArray) {
        if (!live) return
        val current = track ?: createTrack()?.also { track = it } ?: return
        val scale = if (muted) 0f else gain
        // Scale in place on a copy: the socket reuses its buffers.
        val out = ShortArray(pcm.size / 2)
        val buffer = ByteBuffer.wrap(pcm).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        for (i in out.indices) {
            val sample = buffer.getShort(i * 2).toInt()
            out[i] = (sample * scale).toInt().coerceIn(-32_768, 32_767).toShort()
        }
        runCatching { current.write(out, 0, out.size, AudioTrack.WRITE_BLOCKING) }
    }

    private fun createTrack(): AudioTrack? {
        val sampleRate = 48_000
        val minBuffer = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuffer <= 0) return null
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        val format = AudioFormat.Builder()
            .setSampleRate(sampleRate)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .build()
        val created = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AudioTrack.Builder()
                .setAudioAttributes(attributes)
                .setAudioFormat(format)
                .setBufferSizeInBytes(maxOf(minBuffer, 9_600))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                .build()
        } else {
            @Suppress("DEPRECATION")
            AudioTrack(
                attributes,
                format,
                maxOf(minBuffer, 9_600),
                AudioTrack.MODE_STREAM,
                AudioManager.AUDIO_SESSION_ID_GENERATE,
            )
        }
        if (created.state != AudioTrack.STATE_INITIALIZED) {
            created.release()
            return null
        }
        created.play()
        return created
    }

    @Synchronized
    fun stop() {
        track?.let {
            runCatching { it.pause() }
            runCatching { it.flush() }
            runCatching { it.release() }
        }
        track = null
    }
}
