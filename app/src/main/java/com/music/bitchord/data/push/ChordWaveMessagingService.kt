package com.music.bitchord.data.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * FCM entry point for admin announcements (Firebase admin system,
 * Tahap 1). Registered in the manifest for MESSAGING_EVENT; the system
 * binds it for data messages and for notification messages that arrive
 * while the app is in the foreground. A notification message that
 * arrives in the background is posted by the system itself, onto the
 * channel the manifest's default-channel meta-data names — the same
 * channel [PushNotifications] creates at startup.
 */
class ChordWaveMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        // The admin sends notification + data; accept either spelling so a
        // data-only message still shows.
        val title = message.notification?.title ?: message.data["title"]
        val body = message.notification?.body ?: message.data["body"]
        if (title.isNullOrBlank() && body.isNullOrBlank()) return
        PushNotifications.showAnnouncement(
            context = this,
            title = title?.takeIf { it.isNotBlank() } ?: "ChordWave",
            body = body.orEmpty(),
            messageId = message.messageId,
        )
    }

    override fun onNewToken(token: String) {
        // Delivery is topic-based (all_users), so no per-device token is
        // stored or uploaded anywhere. Nothing to do.
    }
}
