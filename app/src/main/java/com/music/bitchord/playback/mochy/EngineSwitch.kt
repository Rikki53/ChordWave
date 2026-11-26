// EngineSwitch — which playback engine is alive (two-engine architecture,
// RENCANA-DUA-MESIN.md, Tahap 4).
//
// Riki's decisions this enforces:
//  - UiMode is the engine switch: Modern runs the Mochy engine
//    (MochyMusicService), Classic runs the ChordWave engine
//    (PlaybackService). TV and Wear OS never see this file's Mochy half —
//    their manifests declare only PlaybackService.
//  - Only one player service lives at a time. Two MediaLibraryServices
//    would fight over the notification, the widget, Bluetooth and Android
//    Auto, so the inactive service's component is disabled outright:
//    nothing can bind it, and the system's media-browser discovery (Auto,
//    Assistant) finds exactly the active engine.
//  - Switching modes stops the song (the stop itself is driven by
//    MainActivity's switch sequence through [stopEngine]).

package com.music.bitchord.playback.mochy

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.UiMode
import com.music.bitchord.playback.PlaybackService
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import kotlin.coroutines.resume

/** The two playback engines, one per UI mode. */
enum class PlaybackEngine {
    CHORDWAVE,
    MOCHY,
}

object EngineSwitch {

    private const val TAG = "EngineSwitch"
    private const val PREFS_NAME = "bitchord_settings"
    private const val KEY_UI_MODE = "ui_mode"

    fun engineFor(mode: UiMode): PlaybackEngine =
        if (mode == UiMode.MODERN) PlaybackEngine.MOCHY else PlaybackEngine.CHORDWAVE

    /**
     * The engine the persisted mode names. Reads AppSettings when it is
     * initialised (the normal case — Application.onCreate runs first) and
     * falls back to the raw preference for the widget receiver's cold path.
     */
    fun activeEngine(context: Context): PlaybackEngine {
        val fromSettings = runCatching { AppSettings.uiMode.value }.getOrNull()
        if (fromSettings != null) return engineFor(fromSettings)
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_UI_MODE, null)
        return engineFor(runCatching { UiMode.valueOf(raw ?: "CLASSIC") }.getOrDefault(UiMode.CLASSIC))
    }

    fun component(context: Context, engine: PlaybackEngine): ComponentName = when (engine) {
        PlaybackEngine.CHORDWAVE -> ComponentName(context, PlaybackService::class.java)
        PlaybackEngine.MOCHY -> ComponentName(context, MochyMusicService::class.java)
    }

    fun token(context: Context, engine: PlaybackEngine): SessionToken =
        SessionToken(context, component(context, engine))

    /**
     * Enables the active engine's service component and disables the other,
     * so exactly one MediaLibraryService can exist. Idempotent and cheap;
     * called at startup and on every mode switch.
     */
    fun syncComponents(context: Context) {
        val active = activeEngine(context)
        val pm = context.packageManager
        for (engine in PlaybackEngine.entries) {
            val state = if (engine == active) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
            runCatching {
                pm.setComponentEnabledSetting(
                    component(context, engine),
                    state,
                    PackageManager.DONT_KILL_APP,
                )
            }.onFailure { Timber.tag(TAG).w(it, "syncComponents failed for $engine") }
        }
    }

    /**
     * Stops [engine]'s playback and its service: the song stops (Riki's
     * decision), the queue is cleared so a later bind cannot resurrect it,
     * and the service is stopped. Safe to call when nothing is playing.
     */
    suspend fun stopEngine(context: Context, engine: PlaybackEngine) {
        val app = context.applicationContext
        withTimeoutOrNull(STOP_TIMEOUT_MS) {
            val controller = connect(app, engine)
            if (controller != null) {
                runCatching {
                    controller.stop()
                    controller.clearMediaItems()
                }.onFailure { Timber.tag(TAG).w(it, "stopEngine: stop failed for $engine") }
                runCatching { controller.release() }
            }
        }
        runCatching {
            app.stopService(Intent(app, serviceClass(engine)))
        }.onFailure { Timber.tag(TAG).w(it, "stopEngine: stopService failed for $engine") }
    }

    private fun serviceClass(engine: PlaybackEngine): Class<*> = when (engine) {
        PlaybackEngine.CHORDWAVE -> PlaybackService::class.java
        PlaybackEngine.MOCHY -> MochyMusicService::class.java
    }

    private suspend fun connect(context: Context, engine: PlaybackEngine): MediaController? =
        suspendCancellableCoroutine { cont ->
            val future = MediaController.Builder(context, token(context, engine)).buildAsync()
            future.addListener(
                {
                    val controller = runCatching { future.get() }.getOrNull()
                    if (cont.isActive) cont.resume(controller)
                },
                ContextCompat.getMainExecutor(context),
            )
            cont.invokeOnCancellation { MediaController.releaseFuture(future) }
        }

    private const val STOP_TIMEOUT_MS = 4_000L
}
