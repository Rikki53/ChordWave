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
        // data-only message still shows. The announcement data keys
        // (announcement_id/title/body, or id/title/body) feed the
        // in-app card and the bell (Tahap 5).
        val title = message.notification?.title
            ?: message.data["announcement_title"] ?: message.data["title"]
        val body = message.notification?.body
            ?: message.data["announcement_body"] ?: message.data["body"]
        if (title.isNullOrBlank() && body.isNullOrBlank()) return
        val announcementId = message.data["announcement_id"]?.takeIf { it.isNotBlank() }
            ?: message.data["id"]?.takeIf { it.isNotBlank() }
            ?: message.messageId
        val announcement = com.music.bitchord.data.admin.AnnouncementStore.Announcement(
            id = announcementId ?: "push-${(title.orEmpty() + body.orEmpty()).hashCode()}",
            title = title?.takeIf { it.isNotBlank() } ?: "ChordWave",
            body = body.orEmpty(),
        )
        // Save first so the bell lights up even if the notification
        // itself is swiped away; the tap then opens this card.
        com.music.bitchord.data.admin.AnnouncementStore.savePush(this, announcement)
        PushNotifications.showAnnouncement(
            context = this,
            title = announcement.title,
            body = announcement.body,
            messageId = message.messageId,
            announcementId = announcement.id,
        )
    }

    override fun onNewToken(token: String) {
        // Delivery is topic-based (all_users), so no per-device token is
        // stored or uploaded anywhere. Nothing to do.
    }
}
