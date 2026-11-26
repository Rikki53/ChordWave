// EngineSwitch — which playback engine is alive (two-engine architecture,
// RENCANA-DUA-MESIN.md, Tahap 4; switch hardened in batch 53).
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
//    MainActivity's switch sequence through [switchTo]).
//
// Batch 53 — why the switch used to force-close the app in both
// directions, and what changed:
//  - The UI bound its MediaController by reading the mode preference
//    directly. The preference flips the instant the user taps, while the
//    old host is still composing and the new engine's component is still
//    disabled — so the old host transiently bound to a disabled service
//    and released its live controller mid-recomposition. Controllers now
//    bind to [boundEngine], which only changes at the safe point of a
//    switch (old service stopped and destroyed, components synced), when
//    no host is composing.
//  - The old service was stopped with stopService (asynchronous) and its
//    component disabled immediately, while the UI controller's unbind
//    could still be pending (Media3 delays unbind until pending commands
//    drain). Two MediaLibraryServices could briefly coexist and a running
//    foreground service's component was disabled under it. [switchTo]
//    now waits until the old service is actually destroyed before
//    touching component states.
//  - stopEngine connected a fresh controller even when the old service
//    was not running (resurrecting it just to kill it), and a synchronous
//    throw from buildAsync would have propagated out of the switch
//    coroutine and crashed the app. The connect is now skipped when the
//    service is not running and every step is guarded: a failed switch
//    step can never take the process down.

package com.music.bitchord.playback.mochy

import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.UiMode
import com.music.bitchord.playback.PlaybackService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    // The engine UI controllers bind to. Unlike the mode preference, this
    // only changes at the safe point of a switch (see [switchTo] and
    // [syncComponents]), never the instant the preference flips — a host
    // still composing during the switch keeps its own engine until it
    // leaves, and the incoming host only ever binds an enabled service.
    private val _boundEngine = MutableStateFlow(PlaybackEngine.CHORDWAVE)
    private var boundEngineReady = false

    fun boundEngine(context: Context): StateFlow<PlaybackEngine> {
        if (!boundEngineReady) {
            _boundEngine.value = activeEngine(context)
            boundEngineReady = true
        }
        return _boundEngine.asStateFlow()
    }

    fun component(context: Context, engine: PlaybackEngine): ComponentName = when (engine) {
        PlaybackEngine.CHORDWAVE -> ComponentName(context, PlaybackService::class.java)
        PlaybackEngine.MOCHY -> ComponentName(context, MochyMusicService::class.java)
    }

    fun token(context: Context, engine: PlaybackEngine): SessionToken =
        SessionToken(context, component(context, engine))

    /**
     * Enables the active engine's service component and disables the other,
     * so exactly one MediaLibraryService can exist, and points
     * [boundEngine] at the active engine. Idempotent and cheap; called at
     * startup and at the safe point of every mode switch (after the old
     * service is destroyed — see [switchTo]).
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
        _boundEngine.value = active
        boundEngineReady = true
    }

    /**
     * The full mode-switch sequence, safe in both directions and never
     * throwing: stop the outgoing engine (song stops, per Riki's
     * decision), wait until its service is actually destroyed, then sync
     * component states and flip [boundEngine]. The caller covers this
     * with the short splash and only then composes the other host.
     */
    suspend fun switchTo(context: Context, target: PlaybackEngine) {
        val app = context.applicationContext
        val current = if (boundEngineReady) _boundEngine.value else activeEngine(app)
        if (current != target) {
            stopEngine(app, current)
            awaitServiceDestroyed(app, current)
        }
        if (target == PlaybackEngine.MOCHY) {
            // Batch 53 (cold start): the Mochy DownloadManager is only
            // built when Modern is (or becomes) the active engine —
            // BitChordApplication skips it on a Classic cold start.
            // switchTo is called from the main thread (MainActivity's
            // switch LaunchedEffect), where the manager is read from;
            // init() is idempotent.
            runCatching { MochyDownloadUtil.init(app) }
                .onFailure { Timber.tag(TAG).w(it, "switchTo: download engine init failed") }
        }
        syncComponents(app)
    }

    /**
     * Stops [engine]'s playback and its service: the song stops (Riki's
     * decision), the queue is cleared so a later bind cannot resurrect it,
     * and the service is stopped. Safe to call when nothing is playing —
     * and when the service is not even running: connecting then would
     * resurrect it just to kill it, so the connect is skipped.
     */
    suspend fun stopEngine(context: Context, engine: PlaybackEngine) {
        val app = context.applicationContext
        if (isServiceRunning(app, engine)) {
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
        }
        runCatching {
            app.stopService(Intent(app, serviceClass(engine)))
        }.onFailure { Timber.tag(TAG).w(it, "stopEngine: stopService failed for $engine") }
    }

    /**
     * Whether [engine]'s service is currently running. getRunningServices
     * only ever reports the caller's own services on modern Android,
     * which is exactly the two services asked about here.
     */
    @Suppress("DEPRECATION")
    fun isServiceRunning(context: Context, engine: PlaybackEngine): Boolean = runCatching {
        val manager = context.getSystemService<ActivityManager>() ?: return false
        val className = serviceClass(engine).name
        manager.getRunningServices(Int.MAX_VALUE).any { it.service.className == className }
    }.getOrDefault(false)

    /** Waits (bounded) until [engine]'s service has actually been destroyed. */
    private suspend fun awaitServiceDestroyed(context: Context, engine: PlaybackEngine) {
        var waitedMs = 0L
        while (waitedMs < DESTROY_TIMEOUT_MS && isServiceRunning(context, engine)) {
            delay(DESTROY_POLL_MS)
            waitedMs += DESTROY_POLL_MS
        }
        if (isServiceRunning(context, engine)) {
            Timber.tag(TAG).w("awaitServiceDestroyed: $engine still running after ${DESTROY_TIMEOUT_MS}ms")
        }
    }

    private fun serviceClass(engine: PlaybackEngine): Class<*> = when (engine) {
        PlaybackEngine.CHORDWAVE -> PlaybackService::class.java
        PlaybackEngine.MOCHY -> MochyMusicService::class.java
    }

    private suspend fun connect(context: Context, engine: PlaybackEngine): MediaController? =
        try {
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
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Timber.tag(TAG).w(e, "connect failed for $engine")
            null
        }

    private const val STOP_TIMEOUT_MS = 4_000L
    private const val DESTROY_TIMEOUT_MS = 3_000L
    private const val DESTROY_POLL_MS = 100L
}
