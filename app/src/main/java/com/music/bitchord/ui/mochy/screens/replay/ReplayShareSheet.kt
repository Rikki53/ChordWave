// Modern (Mochy-style) restyle of the Replay share sheet.
//
// Shown through Mochy's PopupSheetState (LocalMenuState.show { ... }) from
// the Modern ReplayScreen — header share button (whole Replay) or a story
// card's share button (that card). The poster itself is the untouched engine:
// renderReplayPoster in ui/replay/ReplayPoster.kt draws the 1080×1920
// picture, and the hand-off helpers cacheForSharing / saveToGallery in
// ui/replay/ReplayShareSheet.kt (internal to the app module, so callable
// here) do the FileProvider share and the MediaStore save. This file only
// re-draws the sheet in the Modern visual language — dark surface, Mochy
// theme colours, pill buttons — and wires the same two actions.
//
// No new dependencies. No "Mochy" strings anywhere user-visible.
package com.music.bitchord.ui.mochy.screens.replay

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.music.bitchord.R
import com.music.bitchord.data.stats.ReplaySummary
import com.music.bitchord.ui.replay.ReplayStoryPage
import com.music.bitchord.ui.replay.cacheForSharing
import com.music.bitchord.ui.replay.renderReplayPoster
import com.music.bitchord.ui.replay.saveToGallery
import com.music.bitchord.ui.replay.sendIntent
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * The picture, and the two things you can do with it.
 *
 * Deliberately not the system chooser on its own: a chooser is a modal list
 * of app names with no sight of what is about to be sent, and what is about
 * to be sent here is the entire point. So the image is the sheet, full size,
 * and Save / Share sit under it. "Share" still opens the chooser, because
 * the system list is kept up to date and ordered by what this user actually
 * shares to — reimplementing it would show a worse list.
 *
 * @param page the card being sent, or null for the whole Replay.
 */
@Composable
fun ReplayShareSheet(
    summary: ReplaySummary,
    holder: String,
    memberSince: String?,
    page: ReplayStoryPage?,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var poster by remember { mutableStateOf<Bitmap?>(null) }
    var failed by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }

    LaunchedEffect(summary, page) {
        poster = runCatching { renderReplayPoster(context, summary, holder, memberSince, page) }
            .onFailure { error ->
                Timber.w(error, "Replay: poster render failed (page=$page)")
                failed = true
            }
            .getOrNull()
    }

    Column(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(bottom = 20.dp),
    ) {
        Text(
            text = stringResource(R.string.share_my_replay),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.W800,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        Text(
            text = if (page == null) {
                stringResource(R.string.replay_share_summary_description)
            } else {
                stringResource(R.string.replay_share_card_description)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))

        // Small enough that the buttons under it are on screen with it. A
        // preview that pushes the actions below the fold is a preview
        // nobody scrolls past, and the actions are the point of the sheet.
        Box(
            Modifier
                .fillMaxWidth(0.42f)
                .align(Alignment.CenterHorizontally)
                .aspectRatio(9f / 16f)
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            val image = poster
            when {
                image != null -> Image(
                    bitmap = image.asImageBitmap(),
                    contentDescription = stringResource(R.string.your_replay),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth(),
                )
                failed -> Text(
                    text = stringResource(R.string.couldnt_draw_picture),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                else -> CircularProgressIndicator()
            }
        }

        Spacer(Modifier.height(22.dp))

        // Disabled rather than hidden while the poster renders: buttons
        // that appear a second after the sheet does are buttons that get
        // tapped at exactly the moment they move.
        val ready = poster != null
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ShareAction(
                label = if (saved) stringResource(R.string.saved) else stringResource(R.string.save),
                icon = Icons.Rounded.Download,
                accent = false,
                enabled = ready && !saved,
                saved = saved,
                modifier = Modifier.weight(1f),
            ) {
                val image = poster ?: return@ShareAction
                scope.launch {
                    saved = saveToGallery(
                        context,
                        image,
                        summary.label,
                        prefix = "chordwave-replay",
                    )
                    if (!saved) {
                        Timber.w("Replay: saveToGallery failed for ${summary.label}")
                    }
                }
            }
            ShareAction(
                label = stringResource(R.string.share),
                icon = Icons.Rounded.IosShare,
                accent = true,
                enabled = ready,
                modifier = Modifier.weight(1f),
            ) {
                val image = poster ?: return@ShareAction
                scope.launch {
                    val uri = cacheForSharing(context, image)
                    if (uri == null) {
                        Timber.w("Replay: cacheForSharing failed; share aborted")
                        return@launch
                    }
                    context.startActivity(
                        Intent.createChooser(
                            sendIntent(uri),
                            context.getString(R.string.share_your_replay),
                        ),
                    )
                    onDismiss()
                }
            }
        }
    }
}

/**
 * One of the sheet's two buttons.
 *
 * Share is the accent (MochyTheme primary: near-white on black) because it
 * is the one that should be reached for; Save sits on surfaceVariant. The
 * icon and the word crossfade rather than swap on a frame, so the change
 * reads as the button settling into its new state instead of flickering.
 */
@Composable
private fun ShareAction(
    label: String,
    icon: ImageVector,
    accent: Boolean,
    enabled: Boolean,
    saved: Boolean = false,
    savedLabel: String? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val background = when {
        accent -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val foreground = when {
        accent -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurface
    }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(background.copy(alpha = if (enabled) 1f else 0.4f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 15.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Crossfade(targetState = saved, label = "shareAction") { isSaved ->
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = if (isSaved) Icons.Rounded.Check else icon,
                    contentDescription = null,
                    tint = foreground,
                    modifier = Modifier.size(19.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = if (isSaved) (savedLabel ?: label) else label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.W700,
                    color = foreground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
