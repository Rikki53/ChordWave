// the "there's an update" prompt: what's new, the APK, install here or open the web or snooze
// Ported from Mochy Music (GPL-3.0). The download/install is ChordWave's own AppUpdateChecker;
// the snooze itself is the host's job (onSnooze — see NEEDS.md, the host owns the 24h store).
package com.music.bitchord.ui.mochy.screens.update

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.bitchord.BuildConfig
import com.music.bitchord.R
import com.music.bitchord.data.AppUpdateChecker
import com.music.bitchord.data.AppUpdateChecker.DownloadState
import com.music.bitchord.data.AppUpdateChecker.UpdateInfo
import com.music.bitchord.ui.mochy.component.RemoteFocus
import com.music.bitchord.ui.mochy.player.menu.MenuRowSurface
import com.music.bitchord.ui.mochy.player.menu.MenuSheetSurface
import kotlinx.coroutines.launch

private val CardSurface = MenuRowSurface

@Composable
fun UpdatePromptSheet(
    release: UpdateInfo,
    userName: String,
    onSnooze: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onReveal: ((Float) -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // the download lives in AppUpdateChecker, so a finished APK survives this sheet closing
    val downloadState by AppUpdateChecker.download.collectAsState()
    val downloading = downloadState is DownloadState.Downloading
    val progress = (downloadState as? DownloadState.Downloading)?.fraction ?: 0f
    val error = (downloadState as? DownloadState.Failed)?.message
    val readyFile = (downloadState as? DownloadState.Ready)?.file

    MenuSheetSurface(
        onDismiss = onDismiss,
        modifier = modifier,
        wrapHeight = true,
        fullDetent = 0.92f,

        dismissEnabled = !downloading,
        revealProvider = onReveal,
    ) { _ ->
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(top = 12.dp, bottom = 24.dp)
                .animateContentSize()
        ) {

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(CardSurface)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_chordwave_mark),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(52.dp),
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = if (userName.isBlank()) "Update available!" else "Update available, $userName!",
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(modifier = Modifier.height(16.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(CardSurface)
                    .padding(16.dp)
            ) {
                Text(
                    text = "What changed on ${release.version}",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(4.dp))
                ChangelogMarkdown(markdown = release.notes.orEmpty())
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "To Be Installed -",
                color = Color(0xFF8A8A8A),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(CardSurface)
                    .padding(14.dp)
            ) {

                AppIconImage(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp)),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "ChordWave-v${release.version}.apk",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${BuildConfig.APPLICATION_ID} · v${release.version}",
                        color = Color(0xFF9A9A9A),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            error?.let {
                Spacer(modifier = Modifier.height(10.dp))
                Text(text = it, color = Color(0xFFE0736B), fontSize = 12.sp)
            }

            Spacer(modifier = Modifier.height(22.dp))

            val animatedProgress by animateFloatAsState(
                targetValue = progress,
                animationSpec = tween(durationMillis = 120),
                label = "promptDownloadProgress",
            )
            PromptButton(
                enabled = !downloading && release.apkUrl != null,
                progress = animatedProgress,
                label = when {
                    downloading -> "Downloading… ${(animatedProgress * 100).toInt()}%"
                    release.apkUrl == null -> "No APK in this release"
                    error != null -> "Try downloading again"
                    else -> "Install here"
                },
                dimmed = release.apkUrl == null,
                onClick = {
                    val ready = readyFile
                    if (ready != null) {
                        AppUpdateChecker.installApk(context, ready)
                    } else {
                        scope.launch {
                            AppUpdateChecker.downloadApk(context)
                            val downloaded = AppUpdateChecker.download.value as? DownloadState.Ready
                            downloaded?.let { AppUpdateChecker.installApk(context, it.file) }
                        }
                    }
                },
            )

            Spacer(modifier = Modifier.height(10.dp))

            PromptButton(
                enabled = !downloading,
                label = "Open the web",
                dimmed = downloading,
                onClick = { context.openReleaseUrl(release.releaseUrl) },
            )

            Spacer(modifier = Modifier.height(10.dp))

            PromptButton(
                enabled = !downloading,
                label = "no thanks remind me in 24 hour",
                dimmed = downloading,
                onClick = onSnooze,
            )
        }
    }
}

@Composable
private fun PromptButton(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    dimmed: Boolean = false,
    progress: Float = 0f,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)

            .clip(RoundedCornerShape(50))
            .background(CardSurface)
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = RemoteFocus,
                onClick = onClick,
            )
    ) {
        if (progress > 0f) {
            Box(
                modifier = Modifier

                    .align(Alignment.CenterStart)
                    .fillMaxHeight()
                    .fillMaxWidth(fraction = progress)
                    .background(Color(0xFF444444))
            )
        }
        Text(
            text = label,
            color = Color.White.copy(alpha = if (dimmed) 0.5f else 1f),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

private fun android.content.Context.openReleaseUrl(url: String) {
    runCatching {
        startActivity(
            android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
