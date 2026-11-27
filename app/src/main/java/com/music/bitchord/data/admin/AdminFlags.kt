package com.music.bitchord.data.admin

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import com.music.bitchord.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * The admin kill-switch / maintenance flags (Sistem Admin Firebase,
 * Tahap 2), read from Firebase Remote Config and observed by both
 * engines through one [StateFlow].
 *
 * FAIL-OPEN is the whole contract: the in-code defaults below
 * (online on, maintenance off) are what the app runs on until a fetch
 * actually lands, and a failed fetch changes nothing — the last
 * activated values (Remote Config's own cache) or the defaults stay
 * in force. A dead network must never lock the app.
 *
 * Parameters (created by the admin in Firebase Console → Remote
 * Config; until they exist the defaults simply apply):
 *  - `online_enabled` (boolean, default true) — the kill-switch.
 *  - `maintenance_active` (boolean, default false) — total block.
 *  - `maintenance_message` (string, default "") — shown on the
 *    maintenance screen; blank falls back to a built-in text.
 *
 * Fetching is async and applied on the main thread (batch 46/54's
 * lesson: no main-thread-only API from a background thread, and no
 * startup work may be able to take the app down). The minimum fetch
 * interval is 0 on the dev flavor so the admin can test toggles from
 * the console quickly, and one hour on prod.
 */
object AdminFlags {

    const val KEY_ONLINE_ENABLED = "online_enabled"
    const val KEY_MAINTENANCE_ACTIVE = "maintenance_active"
    const val KEY_MAINTENANCE_MESSAGE = "maintenance_message"

    /** One immutable snapshot of the three admin flags. */
    data class Flags(
        val onlineEnabled: Boolean = true,
        val maintenanceActive: Boolean = false,
        val maintenanceMessage: String = "",
    )

    private val _flags = MutableStateFlow(Flags())
    val flags: StateFlow<Flags> = _flags.asStateFlow()

    /** Synchronous read for resolver threads: is online playback allowed right now? */
    val onlineEnabled: Boolean get() = _flags.value.onlineEnabled

    /** Synchronous read: is the total maintenance block up? */
    val maintenanceActive: Boolean get() = _flags.value.maintenanceActive

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var started = false

    /**
     * Called once from [com.music.bitchord.BitChordApplication.onCreate].
     * Sets the in-code defaults, publishes whatever Remote Config has
     * cached from a previous run, then fetches fresh values in the
     * background. Builds without a FirebaseApp (the TV module ships
     * no google-services.json) skip quietly, like PushNotifications.
     */
    fun init(context: Context) {
        if (started) return
        started = true
        val app = context.applicationContext
        scope.launch {
            runCatching {
                if (FirebaseApp.getApps(app).isEmpty()) return@launch
                val remoteConfig = FirebaseRemoteConfig.getInstance()
                remoteConfig.setConfigSettingsAsync(
                    FirebaseRemoteConfigSettings.Builder()
                        .setMinimumFetchIntervalInSeconds(
                            if (BuildConfig.FLAVOR == "dev") 0 else 3600,
                        )
                        .build(),
                ).awaitTask()
                remoteConfig.setDefaultsAsync(
                    mapOf(
                        KEY_ONLINE_ENABLED to true,
                        KEY_MAINTENANCE_ACTIVE to false,
                        KEY_MAINTENANCE_MESSAGE to "",
                    ),
                ).awaitTask()
                // Whatever a previous run activated is already the
                // best known truth; publish it before the fetch so a
                // kill-switch set yesterday applies from first frame.
                publish(remoteConfig)
                fetchAndPublish(remoteConfig)
            }
        }
    }

    /**
     * Ask Remote Config for fresh values (called when the app comes
     * to the foreground). The SDK's minimum fetch interval decides
     * whether this is a real network fetch or a no-op; failures are
     * silent and leave the current flags untouched (fail-open).
     */
    fun refresh(context: Context) {
        if (!started) {
            init(context)
            return
        }
        val app = context.applicationContext
        scope.launch {
            runCatching {
                if (FirebaseApp.getApps(app).isEmpty()) return@launch
                fetchAndPublish(FirebaseRemoteConfig.getInstance())
            }
        }
    }

    private suspend fun fetchAndPublish(remoteConfig: FirebaseRemoteConfig) {
        runCatching { remoteConfig.fetchAndActivate().awaitTask() }
            .onSuccess { publish(remoteConfig) }
        // On failure: nothing. The last published flags (cache or
        // defaults) stay in force — fail-open.
    }

    /** Reads the activated values and applies them on the main thread. */
    private fun publish(remoteConfig: FirebaseRemoteConfig) {
        val next = Flags(
            onlineEnabled = remoteConfig.getBoolean(KEY_ONLINE_ENABLED),
            maintenanceActive = remoteConfig.getBoolean(KEY_MAINTENANCE_ACTIVE),
            maintenanceMessage = remoteConfig.getString(KEY_MAINTENANCE_MESSAGE),
        )
        mainHandler.post { _flags.value = next }
    }

    /**
     * Awaits a Play services [Task] without the
     * kotlinx-coroutines-play-services artifact (not on this app's
     * classpath): the completion listener resumes the coroutine.
     */
    private suspend fun <T> Task<T>.awaitTask(): T = suspendCancellableCoroutine { cont ->
        addOnCompleteListener { task ->
            if (!cont.isActive) return@addOnCompleteListener
            val error = task.exception
            if (task.isSuccessful) {
                @Suppress("UNCHECKED_CAST")
                cont.resume(task.result as T)
            } else if (error != null) {
                cont.resumeWithException(error)
            } else {
                cont.resumeWithException(IllegalStateException("Firebase task failed"))
            }
        }
    }
}
