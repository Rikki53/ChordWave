package com.music.bitchord.ui.admin

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.BuildConfig
import com.music.bitchord.R
import com.music.bitchord.data.admin.AdminFlags
import com.music.bitchord.data.admin.AnnouncementStore
import com.music.bitchord.data.admin.announcementOrNull
import com.music.bitchord.data.admin.currentAnnouncement
import com.music.bitchord.data.admin.hasUnreadAnnouncement
import com.music.bitchord.ui.mochy.component.DefaultDialog
import com.music.bitchord.ui.mochy.theme.MochyTheme

/**
 * The visible half of the admin announcement / update system
 * (Sistem Admin Firebase, Tahap 5), hosted by MainActivity above
 * both engines so Modern and Classic behave identically:
 *
 *  - a dismissible banner for the standing Remote Config
 *    announcement (hidden once dismissed for that announcement id,
 *    and after its expiry);
 *  - the announcement card/pop-up — opened by tapping the banner,
 *    the bell, or a system notification (the tap intent lands in
 *    [AnnouncementStore.consumeIntent] and asks for the card here);
 *  - the update prompt when Remote Config advertises a newer
 *    versionCode — dismissible in normal mode (and not shown again
 *    for the same versionCode), blocking in force mode.
 *
 * FAIL-OPEN: with the in-code defaults (empty announcement, latest
 * versionCode 0) nothing here renders at all.
 *
 * Two faces, one logic (the parity rule): Modern wears Mochy's
 * [DefaultDialog] under [MochyTheme] like [MaintenanceScreen],
 * Classic wears Material dialogs under the caller's theme. The
 * banner card follows the same split.
 */
@Composable
fun AdminAnnouncementOverlay(modern: Boolean) {
    val flags by AdminFlags.flags.collectAsStateWithLifecycle()
    val local by AnnouncementStore.state.collectAsStateWithLifecycle()
    val cardRequests by AnnouncementStore.cardRequests.collectAsStateWithLifecycle()
    val pending by AnnouncementStore.pendingCard.collectAsStateWithLifecycle()

    var cardOpen by remember { mutableStateOf(false) }
    var lastSeenRequest by remember { mutableIntStateOf(0) }
    LaunchedEffect(cardRequests) {
        if (cardRequests > lastSeenRequest) {
            lastSeenRequest = cardRequests
            cardOpen = true
        }
    }

    val context = LocalContext.current
    val currentVersionCode = BuildConfig.VERSION_CODE.toLong()
    val updateAvailable = flags.latestVersionCode > currentVersionCode && flags.updateUrl.isNotBlank()
    var updateDismissedThisSession by remember(flags.latestVersionCode) { mutableStateOf(false) }
    val showUpdatePrompt = updateAvailable &&
        (flags.updateForce || (!updateDismissedThisSession && local.dismissedUpdateCode < flags.latestVersionCode))

    val rcAnnouncement = flags.announcementOrNull()
    val bannerVisible = rcAnnouncement != null &&
        rcAnnouncement.id != local.dismissedId &&
        !cardOpen && !showUpdatePrompt

    if (bannerVisible && rcAnnouncement != null) {
        Box(Modifier.fillMaxSize()) {
            AnnouncementBanner(
                modern = modern,
                announcement = rcAnnouncement,
                onOpen = { AnnouncementStore.requestCard(rcAnnouncement) },
                onDismiss = { AnnouncementStore.markDismissed(rcAnnouncement.id) },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 76.dp, start = 16.dp, end = 16.dp),
            )
        }
    }

    if (cardOpen) {
        val announcement = currentAnnouncement(flags, local, pending)
        AnnouncementCardDialog(
            modern = modern,
            announcement = announcement,
            onDismiss = {
                announcement?.let { AnnouncementStore.markRead(it.id) }
                AnnouncementStore.clearPendingCard()
                cardOpen = false
            },
        )
    }

    if (showUpdatePrompt) {
        UpdatePromptDialog(
            modern = modern,
            flags = flags,
            onDownload = {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(flags.updateUrl)))
                }
            },
            onDismiss = {
                if (!flags.updateForce) {
                    AnnouncementStore.markUpdateDismissed(flags.latestVersionCode)
                    updateDismissedThisSession = true
                }
            },
        )
    }
}

/**
 * The bell in both headers (Modern Home top bar, Classic
 * FrostedTopBar). Always present and always does something: with an
 * unread announcement it wears a badge dot and opens the card;
 * without one it still opens the card's polite empty state — never
 * a dead button.
 */
@Composable
fun AnnouncementBellButton(modern: Boolean) {
    val flags by AdminFlags.flags.collectAsStateWithLifecycle()
    val local by AnnouncementStore.state.collectAsStateWithLifecycle()
    val unread = hasUnreadAnnouncement(flags, local)
    IconButton(onClick = { AnnouncementStore.requestCard(null) }) {
        Box {
            Icon(
                imageVector = Icons.Rounded.Notifications,
                contentDescription = stringResource(R.string.admin_notifications),
                tint = if (modern) Color.White else MaterialTheme.colorScheme.onSurface,
            )
            if (unread) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
    }
}

/** The standing-announcement banner: title + two lines of body, tap for the full card, X to dismiss. */
@Composable
private fun AnnouncementBanner(
    modern: Boolean,
    announcement: AnnouncementStore.Announcement,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = announcement.title.ifBlank { stringResource(R.string.admin_announcement_label) }
    val content: @Composable () -> Unit = {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpen)
                .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.Notifications,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (announcement.body.isNotBlank()) {
                    Text(
                        text = announcement.body,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.close),
                )
            }
        }
    }
    if (modern) {
        MochyTheme {
            Surface(
                modifier = modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp,
                shadowElevation = 4.dp,
            ) { content() }
        }
    } else {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
            shadowElevation = 4.dp,
        ) { content() }
    }
}

/** The full announcement card/pop-up, or the polite empty state when there is nothing to show. */
@Composable
private fun AnnouncementCardDialog(
    modern: Boolean,
    announcement: AnnouncementStore.Announcement?,
    onDismiss: () -> Unit,
) {
    val title = announcement?.title?.takeIf { it.isNotBlank() }
        ?: if (announcement != null) stringResource(R.string.admin_announcement_label)
        else stringResource(R.string.admin_announcement_empty_title)
    val body = announcement?.body?.takeIf { it.isNotBlank() }
        ?: if (announcement != null) "" else stringResource(R.string.admin_announcement_empty_body)
    if (modern) {
        MochyTheme {
            DefaultDialog(
                onDismiss = onDismiss,
                title = { Text(title) },
                buttons = {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
                },
            ) {
                if (body.isNotBlank()) {
                    Text(
                        text = body,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(title) },
            text = { if (body.isNotBlank()) Text(body) },
            confirmButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
            },
        )
    }
}

/**
 * The Remote Config update prompt. Normal mode can be dismissed
 * (and stays dismissed for that versionCode); force mode
 * (`update_force`) cannot be dismissed at all — the download button
 * is the only way out.
 */
@Composable
private fun UpdatePromptDialog(
    modern: Boolean,
    flags: AdminFlags.Flags,
    onDownload: () -> Unit,
    onDismiss: () -> Unit,
) {
    val title = if (flags.latestVersionName.isNotBlank()) {
        stringResource(R.string.update_available, flags.latestVersionName)
    } else {
        stringResource(R.string.admin_update_title)
    }
    val body = flags.updateMessage.ifBlank { stringResource(R.string.admin_update_default_message) }
    if (modern) {
        MochyTheme {
            DefaultDialog(
                // Force mode: an empty handler makes back/scrim taps no-ops.
                onDismiss = if (flags.updateForce) ({}) else onDismiss,
                title = { Text(title) },
                buttons = {
                    if (!flags.updateForce) {
                        TextButton(onClick = onDismiss) { Text(stringResource(R.string.later)) }
                    }
                    Button(onClick = onDownload) { Text(stringResource(R.string.admin_update_download)) }
                },
            ) {
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    } else {
        AlertDialog(
            onDismissRequest = if (flags.updateForce) ({}) else onDismiss,
            properties = if (flags.updateForce) {
                DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
            } else {
                DialogProperties()
            },
            title = { Text(title) },
            text = { Text(body) },
            confirmButton = {
                Button(onClick = onDownload) { Text(stringResource(R.string.admin_update_download)) }
            },
            dismissButton = if (!flags.updateForce) {
                { TextButton(onClick = onDismiss) { Text(stringResource(R.string.later)) } }
            } else {
                null
            },
        )
    }
}
