// Ported from Mochy Music (GPL-3.0): link/phone/PhoneLinkService.kt
// PhoneLinkService.kt
//
// No Hilt: the service takes the host from the DeviceLink holder, the
// same instance the sheets use.

package com.music.bitchord.ui.mochy.link.phone

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.music.bitchord.MainActivity
import com.music.bitchord.R
import com.music.bitchord.ui.mochy.link.DeviceLink
import com.music.bitchord.ui.mochy.link.isTelevision
import timber.log.Timber
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

// keeps the phone reachable for its paired devices while ChordWave is closed, with a quiet notification saying so
class PhoneLinkService : Service() {
    private val host: PhoneLinkHost get() = DeviceLink.host(this)

    // keeps the ongoing notification saying what's true now: which device is around
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main)
    private var watching = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ActionStop || host.devices.value.isEmpty()) {
            stopSelf()
            return START_NOT_STICKY
        }
        val started = runCatching {
            ServiceCompat.startForeground(
                this,
                NotificationId,
                notification(),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0,
            )
        }
        if (started.isFailure) {
            Timber.tag("Link").w(started.exceptionOrNull(), "couldn't stay in the background")
            stopSelf()
            return START_NOT_STICKY
        }
        host.refresh()
        watchState()
        return START_STICKY
    }

    private fun watchState() {
        if (watching) return
        watching = true
        scope.launch {
            // posted again only when its words change, so one swiped away on android 14+ stays away instead of coming back every 30 seconds
            kotlinx.coroutines.flow.combine(host.devices, host.live, ticks()) { _, _, _ -> status() }
                .distinctUntilChanged()
                .collect { NotificationManagerCompat.from(this@PhoneLinkService).runCatching { notify(NotificationId, notification(it)) } }
        }
    }

    // a device that goes quiet drops back to "linked" without anything else changing, so the line is checked now and then
    private fun ticks() = kotlinx.coroutines.flow.flow {
        while (true) {
            emit(Unit)
            kotlinx.coroutines.delay(30_000)
        }
    }

    // says what's really going on: a device around and in step, or only linked and waiting
    private fun status(): Pair<String, String> {
        val now = System.currentTimeMillis()
        val around = host.liveDevices(now)
        val names = host.devices.value.joinToString { it.name }
        return when {
            around.isNotEmpty() -> getString(R.string.tv_link_status_connected, around.joinToString { it.name }) to
                getString(if (around.any { it.kind.isTv }) R.string.tv_link_status_connected_detail else R.string.devices_status_connected_detail)
            else -> getString(R.string.devices_notification_title) to getString(R.string.devices_notification_text, names)
        }
    }

    private fun notification(status: Pair<String, String> = status()): android.app.Notification {
        val manager = NotificationManagerCompat.from(this)
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(ChannelId, NotificationManagerCompat.IMPORTANCE_MIN)
                .setName(getString(R.string.tv_link_channel))
                .setShowBadge(false)
                .build(),
        )
        val (title, text) = status
        val open = android.app.PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).setAction(ActionOpenTvLink),
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, ChannelId)
            .setSmallIcon(R.drawable.ic_notification_logo)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    companion object {
        private const val ChannelId = "tv_link"
        private const val NotificationId = 4_711
        private const val ActionStop = "com.music.bitchord.link.STOP"
        const val ActionOpenTvLink = "com.music.bitchord.action.TV_LINK"

        // android 12+ refuses this from the background, the link then only runs while ChordWave itself is up
        fun start(context: Context) {
            if (context.isTelevision()) return
            if (!PhoneLinkHost.hasPairedDevices(context) || !PhoneLinkHost.staysReachable(context)) return
            runCatching { ContextCompat.startForegroundService(context, Intent(context, PhoneLinkService::class.java)) }
                .onFailure { Timber.tag("Link").i("background link not started: ${it.message}") }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, PhoneLinkService::class.java)) }
        }
    }
}

// a phone that restarts comes back on for its devices without opening ChordWave first
class PhoneLinkBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        PhoneLinkService.start(context)
    }
}
