package com.music.bitchord.data.push

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.music.bitchord.MainActivity
import com.music.bitchord.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * The receiving half of the Firebase admin system (Tahap 1): announcements
 * the admin sends to the `all_users` FCM topic land here as system
 * notifications. One channel, one topic, both engines — this lives in the
 * shared Application layer, so Modern and Classic behave identically.
 *
 * Nothing here asks for the notification permission: onboarding and
 * MainActivity already request POST_NOTIFICATIONS, and a second prompt
 * would only annoy. Without the permission the post is simply skipped.
 */
object PushNotifications {

    /** The channel every announcement posts to; named for the system settings list. */
    const val CHANNEL_ID = "announcements"

    /** The broadcast topic the admin sends to; every install subscribes. */
    const val TOPIC_ALL_USERS = "all_users"

    /**
     * Called once from [com.music.bitchord.BitChordApplication.onCreate].
     * The channel is created up front (not on first message) because a
     * notification message that arrives while the app is in the background
     * is posted by the system, not by [ChordWaveMessagingService] — the
     * manifest's default-channel meta-data points it here, and the channel
     * has to exist for the name and importance to be ours.
     *
     * The topic subscription is fire-and-forget off the main thread:
     * subscribeToTopic is an async Task, safe to repeat on every launch,
     * and a device without Play services just fails it quietly (batch
     * 46/54's lesson: no startup work may be able to take the app down).
     */
    fun init(context: Context) {
        ensureChannel(context)
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                // No FirebaseApp in builds without google-services.json
                // (the TV module compiles these sources but ships no
                // Firebase config) — skip quietly instead of throwing.
                if (FirebaseApp.getApps(context).isNotEmpty()) {
                    FirebaseMessaging.getInstance().subscribeToTopic(TOPIC_ALL_USERS)
                }
            }
        }
    }

    /** Idempotent: creating an existing channel is a no-op. */
    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Pengumuman ChordWave", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Pengumuman dan pemberitahuan resmi dari ChordWave"
            },
        )
    }

    /**
     * Posts one announcement. Tapping it opens the app (the in-app pop-up
     * card and notification icon are Tahap 5; for now the launcher
     * behaviour is the whole contract).
     */
    fun showAnnouncement(context: Context, title: String, body: String, messageId: String?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        ensureChannel(context)

        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        // One notification per message: the FCM message id keeps two
        // announcements from replacing each other in the shade.
        val notificationId = messageId?.hashCode() ?: (System.currentTimeMillis() and 0x7fffffff).toInt()
        val pending = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_logo)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()

        runCatching { NotificationManagerCompat.from(context).notify(notificationId, notification) }
    }
}
