// Ported from Mochy Music (GPL-3.0): ui/player/styles/SkinAudio.kt.
// the stream and phone words a module player's text can show: codec, rate, bitrate, bits, size, lossy or lossless, the output, the volume and how busy the app is (docs/wiki, text blocks)
//
// STUBBED (engine, see NEEDS.md §b): the stream words ({codec}, {khz}, {kbps},
// {bits}, {size}, {quality}) and the output device ({device}) need the engine
// contracts (service inspector, streamFacts, AudioOutputs) that do not exist
// in this clone, so those placeholders stay "–" until the adapter round. The
// live words ({volume}, {cpu}) are pure Android and work as in Mochy.

package com.music.bitchord.ui.mochy.player.styles

import android.content.Context
import android.media.AudioManager
import android.os.Process
import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalContext
import com.music.bitchord.ui.mochy.LocalAppContentObscured
import com.music.bitchord.ui.mochy.modules.skin.SkinAudioField
import com.music.bitchord.ui.mochy.playback.LocalPlayerConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import timber.log.Timber

/** the placeholders' words by lowercase name, changing only when one of them does */
@Stable
internal class SkinAudio(initial: Map<String, String> = emptyMap()) {
    var fields by mutableStateOf(initial)
        internal set

    // a value the app doesn't know yet shows as a dash, so the layout never jumps around an empty gap
    fun fill(text: String): String = SkinAudioField.replace(text) { fields[it.groupValues[1].lowercase()] ?: Unknown }

    val isLossless: Boolean get() = fields["codec"] in Lossless
}

/** null unless the layout shows a stream placeholder; the volume and cpu only tick while the player can be seen */
@OptIn(ExperimentalCoroutinesApi::class)
@Composable
internal fun rememberSkinAudio(needed: Boolean, preview: Boolean): SkinAudio? {
    if (!needed) return null
    // the style picker's card is still, so it shows a believable made-up stream
    if (preview) return remember { SkinAudio(PreviewFields) }
    val audio = remember { SkinAudio() }
    val connection = LocalPlayerConnection.current ?: return audio
    val android = LocalContext.current
    val showing = rememberStyleShowing()
    val obscured = LocalAppContentObscured.current
    LaunchedEffect(connection) {
        // TODO(adapter): stream words + device need the engine contracts in NEEDS.md §(b)
        // (service inspector, streamFacts, AudioOutputs). Until they land, {codec}/{khz}/
        // {kbps}/{bits}/{size}/{quality}/{device} stay "–"; only {volume}/{cpu} tick.
        Timber.tag("SkinAudio").w("Stream/device words stubbed: no engine inspector yet; only volume/cpu tick (see NEEDS.md §b).")
        val stream: Flow<Map<String, String>> = flowOf(emptyMap())
        val live = snapshotFlow { showing() && !obscured.value }
            .distinctUntilChanged()
            .flatMapLatest { shown -> if (shown) liveFlow(android) else emptyFlow() }
            .onStart { emit(emptyMap()) }
        combine(stream, live) { words, now -> words + now }
            .collect { audio.fields = it }
    }
    return audio
}

// the phone's media volume and the app's share of the cpu, once a second, off the main thread since the volume is a binder call
private fun liveFlow(android: Context): Flow<Map<String, String>> = flow {
    val manager = android.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
    var lastCpu = Process.getElapsedCpuTime()
    var lastWall = SystemClock.elapsedRealtime()
    var cpu = 0
    while (true) {
        val max = manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val volume = manager.getStreamVolume(AudioManager.STREAM_MUSIC) * 100 / max
        emit(mapOf("volume" to volume.toString(), "cpu" to cpu.toString()))
        delay(LiveTickMs)
        val nowCpu = Process.getElapsedCpuTime()
        val nowWall = SystemClock.elapsedRealtime()
        cpu = ((nowCpu - lastCpu) * 100 / ((nowWall - lastWall).coerceAtLeast(1) * cores)).toInt().coerceIn(0, 100)
        lastCpu = nowCpu
        lastWall = nowWall
    }
}.distinctUntilChanged().flowOn(Dispatchers.Default)

private const val Unknown = "–"
private val Lossless = setOf("FLAC", "ALAC", "PCM")
private const val LiveTickMs = 1_000L
private val PreviewFields = mapOf(
    "codec" to "MP3", "khz" to "44.1", "kbps" to "320", "bits" to "16", "size" to "7.4",
    "quality" to "Lossy", "device" to "Speaker", "volume" to "60", "cpu" to "3",
)
