package com.music.bitchord.data.admin

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Local half of the admin announcement / update system (Sistem Admin
 * Firebase, Tahap 5): what the user has already dismissed or read,
 * and the last announcement that arrived by FCM push. Remote Config
 * (see [AdminFlags]) carries the standing announcement and the
 * update prompt; this store carries everything that must survive on
 * the device between launches:
 *
 *  - the announcement id the user dismissed on the old in-app banner
 *    (the banner was removed in batch 73; the stored id is kept so
 *    an announcement dismissed back then does not light the bell);
 *  - the announcement id the user has read (the bell badge goes out);
 *  - the update versionCode the user declined in normal (non-force)
 *    mode, so the prompt does not nag on every launch;
 *  - the latest push announcement (title/body/id from the FCM data
 *    payload), so the bell lights up and tapping the system
 *    notification — or the bell — opens its card.
 *
 * FAIL-OPEN: with no Remote Config values and no push ever received,
 * everything here is empty and the UI shows no prompt and an unlit
 * bell.
 */
object AnnouncementStore {

    /** Intent extras [com.music.bitchord.data.push.PushNotifications] stamps on the tap intent. */
    const val EXTRA_ANNOUNCEMENT_ID = "chordwave.announcement_id"
    const val EXTRA_ANNOUNCEMENT_TITLE = "chordwave.announcement_title"
    const val EXTRA_ANNOUNCEMENT_BODY = "chordwave.announcement_body"

    private const val PREFS_NAME = "admin_announcements"
    private const val KEY_DISMISSED_ID = "dismissed_announcement_id"
    private const val KEY_READ_ID = "read_announcement_id"
    private const val KEY_DISMISSED_UPDATE_CODE = "dismissed_update_version_code"
    private const val KEY_PUSH_ID = "push_announcement_id"
    private const val KEY_PUSH_TITLE = "push_announcement_title"
    private const val KEY_PUSH_BODY = "push_announcement_body"

    /** One announcement, from Remote Config or from a push payload. */
    data class Announcement(
        val id: String,
        val title: String,
        val body: String,
        /** Epoch seconds; 0 means no expiry (push announcements never expire). */
        val expiresEpochSec: Long = 0L,
    ) {
        fun isActive(nowEpochSec: Long): Boolean =
            id.isNotBlank() &&
                (title.isNotBlank() || body.isNotBlank()) &&
                (expiresEpochSec <= 0L || nowEpochSec < expiresEpochSec)
    }

    /** Everything persisted locally; one immutable snapshot. */
    data class LocalState(
        val dismissedId: String = "",
        val readId: String = "",
        val dismissedUpdateCode: Long = 0L,
        val push: Announcement? = null,
    )

    private val _state = MutableStateFlow(LocalState())
    val state: StateFlow<LocalState> = _state.asStateFlow()

    private val _cardRequests = MutableStateFlow(0)

    /** How many times something asked for the announcement card to open. The overlay watches this. */
    val cardRequests: StateFlow<Int> = _cardRequests.asStateFlow()

    private val _pendingCard = MutableStateFlow<Announcement?>(null)

    /** The announcement a notification tap wants shown; null means "show whatever is current". */
    val pendingCard: StateFlow<Announcement?> = _pendingCard.asStateFlow()

    @Volatile
    private var prefs: SharedPreferences? = null

    /** Idempotent; called from BitChordApplication (and defensively from the FCM service). */
    fun init(context: Context) {
        if (prefs != null) return
        val store = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs = store
        val pushId = store.getString(KEY_PUSH_ID, "").orEmpty()
        val push = if (pushId.isNotBlank()) {
            Announcement(
                id = pushId,
                title = store.getString(KEY_PUSH_TITLE, "").orEmpty(),
                body = store.getString(KEY_PUSH_BODY, "").orEmpty(),
            )
        } else {
            null
        }
        _state.value = LocalState(
            dismissedId = store.getString(KEY_DISMISSED_ID, "").orEmpty(),
            readId = store.getString(KEY_READ_ID, "").orEmpty(),
            dismissedUpdateCode = store.getLong(KEY_DISMISSED_UPDATE_CODE, 0L),
            push = push,
        )
    }

    /** Records an announcement that arrived by FCM (data payload). Lights the bell until read. */
    fun savePush(context: Context, announcement: Announcement) {
        init(context)
        if (announcement.id.isBlank()) return
        prefs?.edit()
            ?.putString(KEY_PUSH_ID, announcement.id)
            ?.putString(KEY_PUSH_TITLE, announcement.title)
            ?.putString(KEY_PUSH_BODY, announcement.body)
            ?.apply()
        _state.value = _state.value.copy(push = announcement)
    }

    /** The card was opened and read: the badge goes out for this id. */
    fun markRead(id: String) {
        if (id.isBlank()) return
        prefs?.edit()?.putString(KEY_READ_ID, id)?.apply()
        _state.value = _state.value.copy(readId = id)
    }

    /** Normal-mode update prompt declined: do not nag again for this versionCode. */
    fun markUpdateDismissed(versionCode: Long) {
        if (versionCode <= 0L) return
        prefs?.edit()?.putLong(KEY_DISMISSED_UPDATE_CODE, versionCode)?.apply()
        _state.value = _state.value.copy(dismissedUpdateCode = versionCode)
    }

    /** Bell tap (announcement = null) or notification tap (announcement set): open the card. */
    fun requestCard(announcement: Announcement?) {
        _pendingCard.value = announcement
        _cardRequests.value += 1
    }

    /** The card closed; forget which announcement the tap asked for. */
    fun clearPendingCard() {
        _pendingCard.value = null
    }

    /**
     * Reads a launch / notification-tap intent. Our own tap intent
     * carries [EXTRA_ANNOUNCEMENT_ID] etc.; a background FCM
     * notification+data message is posted by the system and its tap
     * delivers the data payload as plain extras, so the raw FCM keys
     * (`announcement_id`/`id`, `announcement_title`/`title`,
     * `announcement_body`/`body`) are accepted too. Consumed extras
     * are removed so a configuration change cannot re-open the card.
     * Returns true when the intent carried an announcement.
     */
    fun consumeIntent(context: Context, intent: Intent?): Boolean {
        if (intent == null) return false
        val id = intent.getStringExtra(EXTRA_ANNOUNCEMENT_ID)
            ?: intent.getStringExtra("announcement_id")
            ?: intent.getStringExtra("id")
        val title = intent.getStringExtra(EXTRA_ANNOUNCEMENT_TITLE)
            ?: intent.getStringExtra("announcement_title")
            ?: intent.getStringExtra("title")
        val body = intent.getStringExtra(EXTRA_ANNOUNCEMENT_BODY)
            ?: intent.getStringExtra("announcement_body")
            ?: intent.getStringExtra("body")
        if (id.isNullOrBlank() && title.isNullOrBlank() && body.isNullOrBlank()) return false
        val announcement = Announcement(
            id = id?.takeIf { it.isNotBlank() } ?: "push-${(title.orEmpty() + body.orEmpty()).hashCode()}",
            title = title.orEmpty(),
            body = body.orEmpty(),
        )
        savePush(context, announcement)
        requestCard(announcement)
        intent.removeExtra(EXTRA_ANNOUNCEMENT_ID)
        intent.removeExtra(EXTRA_ANNOUNCEMENT_TITLE)
        intent.removeExtra(EXTRA_ANNOUNCEMENT_BODY)
        intent.removeExtra("announcement_id")
        intent.removeExtra("announcement_title")
        intent.removeExtra("announcement_body")
        return true
    }
}

/** The standing Remote Config announcement, or null when none is active. */
fun AdminFlags.Flags.announcementOrNull(
    nowEpochSec: Long = System.currentTimeMillis() / 1000,
): AnnouncementStore.Announcement? {
    val announcement = AnnouncementStore.Announcement(
        id = announcementId,
        title = announcementTitle,
        body = announcementBody,
        expiresEpochSec = announcementExpiresEpochSec,
    )
    return announcement.takeIf { it.isActive(nowEpochSec) }
}

/**
 * What the card should show: the announcement a notification tap
 * asked for, else the latest push, else the standing Remote Config
 * announcement. Null means the polite empty state.
 */
fun currentAnnouncement(
    flags: AdminFlags.Flags,
    local: AnnouncementStore.LocalState,
    pending: AnnouncementStore.Announcement?,
): AnnouncementStore.Announcement? =
    pending ?: local.push ?: flags.announcementOrNull()

/** The bell badge: an active announcement exists that the user has not read or dismissed. */
fun hasUnreadAnnouncement(
    flags: AdminFlags.Flags,
    local: AnnouncementStore.LocalState,
): Boolean {
    val rc = flags.announcementOrNull()
    if (rc != null && rc.id != local.readId && rc.id != local.dismissedId) return true
    val push = local.push
    if (push != null && push.id != local.readId && push.id != local.dismissedId) return true
    return false
}
