// DeviceLink.kt
//
// The link's singletons without Hilt: the host, the remote and the
// pairing door are created once from the application context and shared
// by the sheets, the player menu and the background service. Also the
// notification tap's way in — MainActivity hands the intent here, and
// the Modern host opens the device sheet when the counter moves.

package com.music.bitchord.ui.mochy.link

import android.content.Context
import android.content.Intent
import com.music.bitchord.ui.mochy.link.phone.PhoneLinkHost
import com.music.bitchord.ui.mochy.link.phone.PhoneLinkService
import com.music.bitchord.ui.mochy.link.phone.PhonePairing
import com.music.bitchord.ui.mochy.link.remote.RemoteControl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object DeviceLink {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile private var hostInstance: PhoneLinkHost? = null
    @Volatile private var remoteInstance: RemoteControl? = null
    @Volatile private var pairingInstance: PhonePairing? = null

    fun host(context: Context): PhoneLinkHost =
        hostInstance ?: synchronized(this) {
            hostInstance ?: PhoneLinkHost(context.applicationContext).also { hostInstance = it }
        }

    fun remote(context: Context): RemoteControl =
        remoteInstance ?: synchronized(this) {
            remoteInstance ?: RemoteControl(context.applicationContext, host(context), scope).also { remoteInstance = it }
        }

    fun pairing(context: Context): PhonePairing =
        pairingInstance ?: synchronized(this) {
            pairingInstance ?: PhonePairing(context.applicationContext, host(context)).also { pairingInstance = it }
        }

    // the host without creating it, for callers that must stay cheap (the player connection's queue hooks)
    fun hostOrNull(): PhoneLinkHost? = hostInstance

    private val _openRequests = MutableStateFlow(0)

    // goes up each time a link notification is tapped; the Modern host opens the device sheet on it
    val openRequests: StateFlow<Int> = _openRequests.asStateFlow()

    fun consume(intent: Intent?) {
        if (intent?.action == PhoneLinkService.ActionOpenTvLink) _openRequests.update { it + 1 }
    }
}
