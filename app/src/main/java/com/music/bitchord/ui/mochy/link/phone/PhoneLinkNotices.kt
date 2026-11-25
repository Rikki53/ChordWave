// Ported from Mochy Music (GPL-3.0): link/phone/PhoneLinkNotices.kt
// PhoneLinkNotices.kt
//
// Only the "connected" notice is ported: the "in step" one belongs to
// the library mirror, which waits for a ChordWave TV counterpart.

package com.music.bitchord.ui.mochy.link.phone

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.music.bitchord.MainActivity
import com.music.bitchord.R
import com.music.bitchord.ui.mochy.component.TopToaster

// the phone's side of "your tv and phone are in step": a banner while ChordWave is open, a quiet notification otherwise
internal object PhoneLinkNotices {
    private const val ChannelId = "tv_link_events"
    private const val NoticeId = 4_712
    private val main = Handler(Looper.getMainLooper())

    fun connected(context: Context, tvName: String) =
        tell(context, context.getString(R.string.tv_link_notice_connected, tvName), context.getString(R.string.tv_link_notice_connected_detail))

    private fun tell(context: Context, title: String, detail: String) {
        main.post {
            val visible = ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            if (visible) {
                TopToaster.show(title, iconRes = R.drawable.cast_connected)
            } else {
                notify(context, title, detail)
            }
        }
    }

    private fun notify(context: Context, title: String, detail: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val manager = NotificationManagerCompat.from(context)
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(ChannelId, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName(context.getString(R.string.tv_link_events_channel))
                .build(),
        )
        val open = android.app.PendingIntent.getActivity(
            context,
            1,
            Intent(context, MainActivity::class.java).setAction(PhoneLinkService.ActionOpenTvLink),
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
        )
        // one notice that's replaced each time, so a tv syncing all evening leaves one line, not a pile
        manager.notify(
            NoticeId,
            NotificationCompat.Builder(context, ChannelId)
                .setSmallIcon(R.drawable.ic_notification_logo)
                .setContentTitle(title)
                .setContentText(detail)
                .setContentIntent(open)
                .setAutoCancel(true)
                .setSilent(true)
                .setTimeoutAfter(NoticeLifetimeMs)
                .build(),
        )
    }

    // gone on its own after a while, it's news only for a bit
    private const val NoticeLifetimeMs = 30 * 60_000L
}
