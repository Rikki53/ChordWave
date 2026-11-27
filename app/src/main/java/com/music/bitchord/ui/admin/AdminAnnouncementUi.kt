package com.music.bitchord.ui.admin

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.BuildConfig
import com.music.bitchord.R
import com.music.bitchord.data.admin.AdminFlags
import com.music.bitchord.data.admin.AnnouncementStore
import com.music.bitchord.data.admin.currentAnnouncement
import com.music.bitchord.data.admin.hasUnreadAnnouncement
import com.music.bitchord.ui.mochy.component.DefaultDialog
import com.music.bitchord.ui.mochy.player.LikeRed
import com.music.bitchord.ui.mochy.theme.MochyTheme
import kotlinx.coroutines.delay

/**
 * The visible half of the admin announcement / update system
 * (Sistem Admin Firebase, Tahap 5), hosted by MainActivity above
 * both engines so Modern and Classic behave identically:
 *
 *  - the announcement card/pop-up — opened only by tapping the bell
 *    or a system notification (the tap intent lands in
 *    [AnnouncementStore.consumeIntent] and asks for the card here).
 *    There is no in-app banner: an unread announcement is signalled
 *    by the red dot on the bell alone (batch 73);
 *  - the update prompt when Remote Config advertises a newer
 *    versionCode — dismissible in normal mode (and not shown again
 *    for the same versionCode), blocking in force mode.
 *
 * FAIL-OPEN: with the in-code defaults (empty announcement, latest
 * versionCode 0) nothing here renders at all.
 *
 * Two faces, one logic (the parity rule): Modern wears Mochy's
 * dialog tokens under [MochyTheme] like [MaintenanceScreen],
 * Classic wears Material dialogs under the caller's theme.
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
 * unread announcement it wears a red dot and opens the card;
 * without one it still opens the card's polite empty state — never
 * a dead button. The dot goes out once the card has been read.
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
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(LikeRed),
                )
            }
        }
    }
}

/** Mochy's emphasized easing, the same curve RevealOnAppear runs on. */
private val CardEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
private const val CardEnterMs = 420
private const val CardExitMs = 200

/** RevealOnAppear's burst step: card pieces land one after another, not all at once. */
private const val CardStaggerMs = 60

/**
 * The full announcement card/pop-up, or the polite empty state when
 * there is nothing to show. Batch 73: no longer a plain dialog —
 * the card rises in with Mochy's reveal motion (fade + scale + a
 * 20dp rise on the emphasized curve, content staggered in 60ms
 * steps) and sinks back out the same way before it is dismissed.
 */
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

    var shown by remember { mutableStateOf(false) }
    var dismissing by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    LaunchedEffect(dismissing) {
        if (dismissing) {
            // Let the exit motion finish before the dialog leaves composition.
            delay(CardExitMs.toLong())
            onDismiss()
        }
    }
    val risePx = with(LocalDensity.current) { 20.dp.roundToPx() }

    Dialog(
        onDismissRequest = { dismissing = true },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        AnimatedVisibility(
            visible = shown && !dismissing,
            enter = fadeIn(tween(CardEnterMs, easing = CardEasing)) +
                scaleIn(tween(CardEnterMs, easing = CardEasing), initialScale = 0.94f) +
                slideInVertically(tween(CardEnterMs, easing = CardEasing)) { risePx },
            exit = fadeOut(tween(CardExitMs, easing = CardEasing)) +
                scaleOut(tween(CardExitMs, easing = CardEasing), targetScale = 0.96f) +
                slideOutVertically(tween(CardExitMs, easing = CardEasing)) { risePx / 2 },
        ) {
            if (modern) {
                MochyTheme {
                    AnnouncementCard(
                        modern = true,
                        title = title,
                        body = body,
                        onDismiss = { dismissing = true },
                    )
                }
            } else {
                AnnouncementCard(
                    modern = false,
                    title = title,
                    body = body,
                    onDismiss = { dismissing = true },
                )
            }
        }
    }
}

/**
 * The card itself. Modern follows Mochy's dialog face (icon badge
 * and title centred, like DefaultDialog with an icon); Classic
 * keeps the Material dialog face (start-aligned) under the Classic
 * theme. Both use the M3 dialog tokens (28dp shape,
 * surfaceContainerHigh, 6dp tonal elevation) so the card matches
 * DefaultDialog's surface while staying animatable as one piece.
 */
@Composable
private fun AnnouncementCard(
    modern: Boolean,
    title: String,
    body: String,
    onDismiss: () -> Unit,
) {
    val alignment = if (modern) Alignment.CenterHorizontally else Alignment.Start
    val textAlign = if (modern) TextAlign.Center else TextAlign.Start
    Surface(
        modifier = Modifier
            .padding(24.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
    ) {
        Column(
            horizontalAlignment = alignment,
            modifier = Modifier.padding(24.dp),
        ) {
            CardReveal(delayMillis = 0) {
                AnnouncementIconBadge()
            }
            Spacer(Modifier.height(16.dp))
            CardReveal(delayMillis = CardStaggerMs) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = textAlign,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (body.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                CardReveal(delayMillis = CardStaggerMs * 2) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = body,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = textAlign,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            CardReveal(delayMillis = CardStaggerMs * 3) {
                Box(Modifier.fillMaxWidth()) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.align(Alignment.CenterEnd),
                    ) { Text(stringResource(R.string.close)) }
                }
            }
        }
    }
}

/** The bell in a tonal circle, growing in on Mochy's no-bounce spring. */
@Composable
private fun AnnouncementIconBadge() {
    val scale = remember { Animatable(0.6f) }
    LaunchedEffect(Unit) {
        scale.animateTo(
            1f,
            spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow,
            ),
        )
    }
    Box(
        modifier = Modifier
            .size(56.dp)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.Notifications,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(28.dp),
        )
    }
}

/**
 * One card piece landing: fade + 16dp rise on the card's curve,
 * delayed by [delayMillis] so the pieces cascade in — the same
 * recipe RevealOnAppear uses, scoped to the card.
 */
@Composable
private fun CardReveal(delayMillis: Int, content: @Composable () -> Unit) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(CardEnterMs, delayMillis = delayMillis, easing = CardEasing))
    }
    val risePx = with(LocalDensity.current) { 16.dp.toPx() }
    Box(
        Modifier.graphicsLayer {
            compositingStrategy = CompositingStrategy.ModulateAlpha
            alpha = progress.value
            translationY = (1f - progress.value) * risePx
        }
    ) { content() }
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
