package com.music.bitchord.karaoke

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import java.net.URI
import java.util.concurrent.ConcurrentLinkedQueue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake

/** A TV found on the network, before anyone has typed its session code. */
data class FoundTv(val name: String, val host: String, val port: Int)

/**
 * NSD discovery for karaoke TVs. Resolution is queued — the platform only
 * settles one resolve at a time on older releases, and firing them in
 * parallel loses TVs silently. The multicast lock is held for the whole
 * search: without it, several chipsets never deliver the advertisements.
 */
class KaraokeTvDiscovery(context: Context) {
    private val appContext = context.applicationContext
    private val nsd = appContext.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val wifi = appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    private val _found = MutableStateFlow<List<FoundTv>>(emptyList())
    val found: StateFlow<List<FoundTv>> = _found.asStateFlow()

    private var multicastLock: WifiManager.MulticastLock? = null
    private var discovering = false
    private val resolveQueue = ConcurrentLinkedQueue<NsdServiceInfo>()
    @Volatile private var resolving = false

    private val listener = object : NsdManager.DiscoveryListener {
        override fun onServiceFound(service: NsdServiceInfo) {
            resolveQueue.add(service)
            resolveNext()
        }

        override fun onServiceLost(service: NsdServiceInfo) {
            val host = service.host?.hostAddress
            _found.value = _found.value.filterNot {
                it.name == service.serviceName || (host != null && it.host == host)
            }
        }

        override fun onDiscoveryStarted(serviceType: String) { discovering = true }
        override fun onDiscoveryStopped(serviceType: String) { discovering = false }
        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) { discovering = false }
        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) { discovering = false }
    }

    private fun resolveNext() {
        if (resolving) return
        val next = resolveQueue.poll() ?: return
        resolving = true
        @Suppress("DEPRECATION")
        nsd.resolveService(next, object : NsdManager.ResolveListener {
            override fun onServiceResolved(resolved: NsdServiceInfo) {
                resolving = false
                val host = resolved.host?.hostAddress
                if (host != null) {
                    val tv = FoundTv(resolved.serviceName, host, resolved.port)
                    _found.value = (_found.value.filterNot { it.host == host && it.port == resolved.port } + tv)
                        .sortedBy { it.name }
                }
                resolveNext()
            }

            override fun onResolveFailed(service: NsdServiceInfo, errorCode: Int) {
                resolving = false
                resolveNext()
            }
        })
    }

    fun start() {
        if (discovering) return
        if (multicastLock == null) {
            multicastLock = wifi.createMulticastLock("chordwave-karaoke-discover").apply {
                setReferenceCounted(false)
            }
        }
        if (multicastLock?.isHeld != true) multicastLock?.acquire()
        runCatching {
            nsd.discoverServices(KARAOKE_REMOTE_SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        }
    }

    fun stop() {
        if (discovering) runCatching { nsd.stopServiceDiscovery(listener) }
        discovering = false
        if (multicastLock?.isHeld == true) multicastLock?.release()
    }
}

/**
 * The phone half of the karaoke remote: one WebSocket to the TV, the last
 * state the TV pushed, and the microphone streamer that rides the same
 * connection as binary frames.
 */
class KaraokeRemoteClient {
    private val _state = MutableStateFlow<RemoteState?>(null)
    val state: StateFlow<RemoteState?> = _state.asStateFlow()

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    /** Set before [connect] returns failure detail: "auth" when the TV refused the code. */
    @Volatile var failureReason: String? = null
        private set

    private var socket: WebSocketClient? = null
    @Volatile private var authed = false

    val mic = MicStreamer { frame -> if (authed) socket?.send(frame) }

    fun connect(host: String, port: Int, code: String) {
        disconnect()
        failureReason = null
        val client = object : WebSocketClient(URI("ws://$host:$port")) {
            override fun onOpen(handshake: ServerHandshake?) {
                send(karaokeRemoteJson.encodeToString(RemoteCommand(type = "auth", token = code)))
            }

            override fun onMessage(message: String) {
                val parsed = runCatching {
                    karaokeRemoteJson.decodeFromString<RemoteState>(message)
                }.getOrNull() ?: return
                if (parsed.type == "auth-ok") {
                    authed = true
                    _connected.value = true
                } else if (parsed.type == "state") {
                    _state.value = parsed
                }
            }

            override fun onMessage(bytes: java.nio.ByteBuffer?) = Unit

            override fun onClose(codeInt: Int, reason: String?, remote: Boolean) {
                if (!authed) failureReason = "auth"
                authed = false
                _connected.value = false
                mic.stop()
            }

            override fun onError(ex: Exception?) {
                if (!authed && failureReason == null) failureReason = "connect"
            }
        }
        client.isTcpNoDelay = true
        socket = client
        client.connect()
    }

    fun send(command: RemoteCommand) {
        if (!authed) return
        runCatching { socket?.send(karaokeRemoteJson.encodeToString(command)) }
    }

    fun disconnect() {
        mic.stop()
        authed = false
        _connected.value = false
        _state.value = null
        runCatching { socket?.close() }
        socket = null
    }
}

/**
 * The phone's microphone, streamed live: 48 kHz mono PCM16 in small frames,
 * echo-cancelled and noise-suppressed where the hardware offers it. The
 * effects matter here more than anywhere — the TV's own speakers are
 * playing the song back into the room this phone is sitting in.
 *
 * Frames go to [sink] as raw little-endian PCM; the TV applies gain and
 * writes them to its low-latency track. Nothing is recorded or stored.
 */
class MicStreamer(private val sink: (ByteArray) -> Unit) {
    @Volatile private var running = false
    private var thread: Thread? = null
    private var record: AudioRecord? = null

    val isRunning: Boolean get() = running

    fun start() {
        if (running) return
        running = true
        thread = Thread({
            val sampleRate = 48_000
            val minBuffer = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
            // ~10 ms frames: small enough that the sing-along feels live,
            // large enough not to drown the socket in packets.
            val frameBytes = 960 * 2
            val rec = try {
                AudioRecord(
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    maxOf(minBuffer, frameBytes * 4),
                )
            } catch (e: SecurityException) {
                running = false
                return@Thread
            }
            if (rec.state != AudioRecord.STATE_INITIALIZED) {
                rec.release()
                running = false
                return@Thread
            }
            record = rec
            val echo = if (AcousticEchoCanceler.isAvailable()) {
                runCatching { AcousticEchoCanceler.create(rec.audioSessionId) }.getOrNull()
            } else null
            echo?.enabled = true
            val suppressor = if (NoiseSuppressor.isAvailable()) {
                runCatching { NoiseSuppressor.create(rec.audioSessionId) }.getOrNull()
            } else null
            suppressor?.enabled = true
            rec.startRecording()
            val buffer = ByteArray(frameBytes)
            while (running) {
                val read = rec.read(buffer, 0, buffer.size)
                if (read > 0) sink(if (read == buffer.size) buffer.copyOf() else buffer.copyOf(read))
            }
            runCatching { rec.stop() }
            echo?.release()
            suppressor?.release()
            rec.release()
            record = null
        }, "karaoke-mic").apply { start() }
    }

    fun stop() {
        running = false
        thread = null
    }
}
