// the app version sheet: the app, the version you're on, what's newer, and what you can do about it
// Ported from Mochy Music (GPL-3.0). The updater engine is NOT Mochy's: everything here reads
// ChordWave's own AppUpdateChecker (GitHub releases, Rikki53/ChordWave). Two channels: Release
// (stable tags) and Dev (the rolling dev-mochy-ui pre-release, installed as the separate
// ChordWave Dev app). The downgrade row offers the previous stable release; "Help us!" opens
// the repo on GitHub (there is no donate link). Mochy's DevSpace hook is gone.
// Every text here carries its own white: the sheets' ambient content colour renders dark
// on device, so nothing here relies on it.

package com.music.bitchord.ui.mochy.screens.update

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Context
import androidx.core.graphics.drawable.toBitmap
import com.music.bitchord.BuildConfig
import com.music.bitchord.R
import com.music.bitchord.data.AppUpdateChecker
import com.music.bitchord.data.AppUpdateChecker.DownloadState
import com.music.bitchord.data.AppUpdateChecker.UpdateChannel
import com.music.bitchord.data.AppUpdateChecker.UpdateInfo
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.ui.mochy.component.BetaBadge
import com.music.bitchord.ui.mochy.component.PopupSheetState
import com.music.bitchord.ui.mochy.component.RemoteFocus
import com.music.bitchord.ui.mochy.component.pressBounce
import com.music.bitchord.ui.mochy.player.menu.MenuRowSurface
import kotlinx.coroutines.launch

/** what the version sheet knows: checking, a newer release, up to date, or a check that failed */
sealed interface UpdateState {
    data object Checking : UpdateState
    data class Available(val release: UpdateInfo) : UpdateState
    data object UpToDate : UpdateState
    data class Failed(val message: String) : UpdateState
}

@Composable
fun rememberUpdateState(): State<UpdateState> {
    val context = LocalContext.current
    val channel by AppSettings.updateChannel.collectAsState()
    val available by AppUpdateChecker.available.collectAsState()
    val state = remember { mutableStateOf<UpdateState>(UpdateState.Checking) }
    LaunchedEffect(channel) {
        state.value = checkUpdateFor(channel, context)
    }
    // a check elsewhere (launch-time, another sheet) finding an update while this one is open
    LaunchedEffect(available) {
        // Local copy: `available` is a delegated property, which the
        // compiler cannot smart-cast after the null check.
        val info = available
        // the launch-time check only follows the release channel, so its
        // result means nothing while the dev channel is selected
        if (channel == UpdateChannel.RELEASE && info != null && state.value !is UpdateState.Available) {
            state.value = UpdateState.Available(info)
        }
    }
    return state
}

/** asks github whether a newer build is out on [channel]. AppUpdateChecker swallows check errors, so a
 * failed check reads as up to date here — Failed only exists for the card's error state. */
private suspend fun checkUpdateFor(channel: UpdateChannel, context: Context): UpdateState {
    return when (channel) {
        UpdateChannel.RELEASE -> {
            AppUpdateChecker.check()
            AppUpdateChecker.available.value?.let { UpdateState.Available(it) }
                ?: UpdateState.UpToDate
        }
        UpdateChannel.DEV -> {
            AppUpdateChecker.checkDev(context)?.let { UpdateState.Available(it) }
                ?: UpdateState.UpToDate
        }
    }
}

/** shows the app version sheet as a PopupSheet, see docs/popup-sheet.skill.md */
fun PopupSheetState.showUpdateSheet(updateState: UpdateState) {
    val popup = this
    show(buttonBar = { SheetPrimaryButton(text = "Done", onClick = popup::dismiss) }) {
        UpdateSheetContent(initialState = updateState, popup = popup)
    }
}

private fun PopupSheetState.showUpdateDetailSheet(release: UpdateInfo) {
    val popup = this
    popup.show {
        UpdateDetailContent(
            release = release,
            // pushed on top of the version sheet, so back just pops down to it again
            onBack = popup::dismiss,
            // locks this same sheet during a download, re-showing it locked would push a second copy with fresh state
            onLocked = popup::setLocked,
        )
    }
}

@Composable
internal fun SheetPrimaryButton(text: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .pressBounce(interaction, pressedScale = 0.97f)
            .fillMaxWidth()
            .height(50.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.26f))
            .clickable(interactionSource = interaction, indication = RemoteFocus, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, fontSize = 17.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp, color = Color.White)
    }
}

private val StableGreen = Color(0xFF9CF58A)
private const val StableLabel = "STABLE"

/** "8.1.0 build#1165" -> "8.1.0", "8.0.2 (1100)" -> "8.0.2" */
internal fun shortVersion(version: String): String = version.substringBefore(' ')

/** the app version sheet: the app, the version you're on and its channel, what's newer, and what you can do about it */
@Composable
private fun ColumnScope.UpdateSheetContent(
    initialState: UpdateState,
    popup: PopupSheetState,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf(initialState) }
    val channel by AppSettings.updateChannel.collectAsState()
    // the previous stable release, for the downgrade row; dev builds have no
    // "previous" (the dev pre-release is a single rolling build)
    var previous by remember { mutableStateOf<UpdateInfo?>(null) }
    LaunchedEffect(channel) {
        previous = if (channel == UpdateChannel.RELEASE) AppUpdateChecker.getPreviousRelease() else null
    }

    suspend fun refresh() {
        state = checkUpdateFor(channel, context)
    }
    val checkAgain: () -> Unit = {
        if (state !is UpdateState.Checking) {
            state = UpdateState.Checking
            scope.launch { refresh() }
        }
    }

    val installed = shortVersion(BuildConfig.VERSION_NAME)
    Box(Modifier.fillMaxWidth()) {
        // centred just inside the sheet's top-right corner so its edge cuts the ring off, no size of its own so it never pushes content around
        VersionRing(
            text = "CHORDWAVE $installed",
            modifier = Modifier
                .align(Alignment.TopEnd)
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0, maxWidth = Constraints.Infinity, maxHeight = Constraints.Infinity))
                    layout(0, 0) {
                        placeable.place(x = (-195).dp.roundToPx(), y = (-134).dp.roundToPx())
                    }
                },
        )

        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(MenuRowSurface),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_chordwave_mark),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(38.dp),
                )
            }

            Spacer(Modifier.height(14.dp))
            Text("ChordWave", fontSize = 17.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp, color = Color.White)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    installed,
                    fontSize = 44.sp,
                    lineHeight = 46.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-2).sp,
                    color = Color.White,
                )
                Spacer(Modifier.width(14.dp))
                ChannelBadge()
            }

            Spacer(Modifier.height(16.dp))
            UpdateStatusCard(
                state = state,
                onUpdate = { popup.showUpdateDetailSheet(it) },
                onRetry = checkAgain,
            )

            Spacer(Modifier.height(22.dp))
            Text("Actions", fontSize = 17.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp, color = Color.White)
            Spacer(Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionRow(
                    icon = R.drawable.refresh,
                    label = if (state is UpdateState.Checking) "Checking…" else "Check for update",
                    spinning = state is UpdateState.Checking,
                    onClick = checkAgain,
                )
                AnimatedVisibility(visible = previous != null) {
                    previous?.let { prev ->
                        ActionRow(
                            icon = R.drawable.download,
                            label = "Downgrade to ${shortVersion(prev.version)}",
                            onClick = { popup.showUpdateDetailSheet(prev) },
                        )
                    }
                }
                ActionRow(
                    icon = R.drawable.sync,
                    label = if (channel == UpdateChannel.RELEASE) "Switch to dev" else "Switch to release",
                    onClick = {
                        popup.showPathChangeSheet(toNightly = channel == UpdateChannel.RELEASE) {
                            val next = if (channel == UpdateChannel.RELEASE) UpdateChannel.DEV else UpdateChannel.RELEASE
                            AppSettings.setUpdateChannel(next)
                            state = UpdateState.Checking
                            scope.launch { state = checkUpdateFor(next, context) }
                        }
                    },
                )
                ActionRow(
                    icon = R.drawable.info,
                    label = "About app",
                    onClick = { popup.showAboutSheet() },
                )
                ActionRow(
                    icon = R.drawable.ic_untitled_heart,
                    label = "Help us!",
                    onClick = { context.openUrl(ChordWaveRepoUrl) },
                )
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

/** which channel this build is: the dev flavour installs as its own app, the rest is stable */
@Composable
private fun ChannelBadge() {
    // the dev flavour's package ends in .dev; a "-betaN" suffix alone says
    // nothing (a sideloaded release beta carries one too)
    val label = if (BuildConfig.APPLICATION_ID.endsWith(".dev")) "DEV" else StableLabel
    AnimatedContent(
        targetState = label,
        transitionSpec = {
            (scaleIn(spring(dampingRatio = 0.5f, stiffness = 500f), initialScale = 0.6f) + fadeIn())
                .togetherWith(scaleOut(targetScale = 0.6f) + fadeOut())
        },
        label = "channelBadge",
    ) { text ->
        // the same outlined tag as every other beta mark in the app, green while this build is stable
        if (text == StableLabel) BetaBadge(label = text, color = StableGreen) else BetaBadge(label = text)
    }
}

/** the card under the version: what github said, and the one thing to do about it */
@Composable
private fun UpdateStatusCard(
    state: UpdateState,
    onUpdate: (UpdateInfo) -> Unit,
    onRetry: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MenuRowSurface)
            .padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        AnimatedContent(
            targetState = state,
            contentKey = { it::class },
            transitionSpec = {
                (fadeIn(tween(220, delayMillis = 80)) + slideInVertically(spring(dampingRatio = 0.8f, stiffness = 400f)) { it / 4 })
                    .togetherWith(fadeOut(tween(120)))
                    .using(SizeTransform(clip = false))
            },
            label = "updateStatus",
        ) { shown ->
            when (shown) {
                is UpdateState.Available -> StatusRow(
                    title = "New update!",
                    big = shortVersion(shown.release.version),
                    action = "UPDATE NOW",
                    onAction = { onUpdate(shown.release) },
                )
                UpdateState.Checking -> StatusRow(title = "Looking for updates", big = "…", pulsing = true)
                UpdateState.UpToDate -> StatusRow(title = "You're on the latest", big = "All good")
                is UpdateState.Failed -> StatusRow(
                    title = "Couldn't check for updates",
                    big = "Offline?",
                    action = "TRY AGAIN",
                    onAction = onRetry,
                )
            }
        }
    }
}

@Composable
private fun StatusRow(
    title: String,
    big: String,
    action: String? = null,
    onAction: () -> Unit = {},
    pulsing: Boolean = false,
) {
    val pulse = if (pulsing) {
        rememberInfiniteTransition(label = "statusPulse").animateFloat(
            initialValue = 0.35f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
            label = "statusPulseAlpha",
        )
    } else {
        null
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp, color = Color.White)
            Text(
                big,
                fontSize = 40.sp,
                lineHeight = 42.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-1.8).sp,
                color = Color.White,
                maxLines = 1,
                modifier = Modifier.graphicsLayer { alpha = pulse?.value ?: 1f },
            )
        }
        if (action != null) {
            val interaction = remember { MutableInteractionSource() }
            Text(
                action,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.6.sp,
                color = Color.White,
                modifier = Modifier
                    .pressBounce(interaction)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.2f))
                    .border(1.dp, Color.White.copy(alpha = 0.22f), CircleShape)
                    .clickable(interactionSource = interaction, indication = RemoteFocus, onClick = onAction)
                    .padding(horizontal = 16.dp, vertical = 7.dp),
            )
        }
    }
}

/** a row under actions: a light disc with the icon, then the label, dimmed ones can't be tapped */
@Composable
private fun ActionRow(
    icon: Int,
    label: String,
    onClick: () -> Unit,
    hint: String? = null,
    enabled: Boolean = true,
    spinning: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val contentAlpha = if (enabled) 1f else 0.4f
    val spin = remember { Animatable(0f) }
    LaunchedEffect(spinning) {
        if (spinning) {
            while (true) {
                spin.animateTo(spin.value + 360f, tween(900, easing = LinearEasing))
            }
        } else {
            // finish the turn it's on instead of snapping back
            val rest = (spin.value / 360f).let { kotlin.math.ceil(it) * 360f }
            spin.animateTo(rest, spring(dampingRatio = 0.7f, stiffness = 300f))
        }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .pressBounce(interaction, pressedScale = 0.97f)
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MenuRowSurface.copy(alpha = if (enabled) 1f else 0.6f))
            .clickable(enabled = enabled, interactionSource = interaction, indication = RemoteFocus, onClick = onClick)
            .padding(horizontal = 16.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .graphicsLayer { alpha = contentAlpha }
                .size(28.dp)
                .clip(CircleShape)
                .background(Color(0xFFDADADA)),
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = Color(0xFF1C1C1E),
                modifier = Modifier
                    .size(17.dp)
                    .graphicsLayer { rotationZ = spin.value },
            )
        }
        Spacer(Modifier.width(14.dp))
        Text(
            label,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.5).sp,
            color = Color.White.copy(alpha = contentAlpha),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (hint != null) {
            Text(hint, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.4f))
        }
    }
}

const val UpdateHeadline = "Theres an update~!"

@Composable
private fun ColumnScope.UpdateDetailContent(
    release: UpdateInfo,
    onBack: () -> Unit,
    onLocked: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // the download lives in AppUpdateChecker, so a finished APK survives this sheet closing
    val downloadState by AppUpdateChecker.download.collectAsState()
    val downloading = downloadState is DownloadState.Downloading
    val progress = (downloadState as? DownloadState.Downloading)?.fraction ?: 0f
    val error = (downloadState as? DownloadState.Failed)?.message

    // a tv remote starts on Install, the back row at the top would close this the moment OK is pressed
    val installFocus = remember { FocusRequester() }
    val remote = LocalInputModeManager.current.inputMode == InputMode.Keyboard
    LaunchedEffect(Unit) {
        if (remote && release.apkUrl != null) runCatching { installFocus.requestFocus() }
    }
    LaunchedEffect(downloading) { onLocked(downloading) }

    fun startDownloadAndInstall() {
        scope.launch {
            AppUpdateChecker.downloadApk(context)
            val ready = AppUpdateChecker.download.value as? DownloadState.Ready
            ready?.let { AppUpdateChecker.installApk(context, it.file) }
        }
    }

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                enabled = !downloading,
                interactionSource = remember { MutableInteractionSource() },
                indication = RemoteFocus,
                onClick = onBack,
            )
            .padding(vertical = 4.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.arrow_back),
            contentDescription = "Back",
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Icon(
            painter = painterResource(R.drawable.ic_chordwave_mark),
            contentDescription = null,
            modifier = Modifier.size(22.dp),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = "ChordWave ${release.version}",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }

    Spacer(modifier = Modifier.height(16.dp))

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MenuRowSurface)
            .padding(16.dp)
            .animateContentSize()
    ) {
        Text(text = "Changelog", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(modifier = Modifier.height(4.dp))
        ChangelogMarkdown(
            markdown = release.notes.orEmpty(),
            color = Color.White.copy(alpha = 0.8f),
            strongColor = Color.White,
        )
    }

    Spacer(modifier = Modifier.height(18.dp))

    Text(
        text = "To Be Installed -",
        color = Color.White.copy(alpha = 0.55f),
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
    )
    Spacer(modifier = Modifier.height(8.dp))

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MenuRowSurface)
            .padding(14.dp)
    ) {
        AppIconImage(modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)))
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = release.fileName ?: "ChordWave-v${release.version}.apk",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${release.packageName} · v${release.version}",
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }

    if (release.packageName != BuildConfig.APPLICATION_ID) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Installs as a separate app alongside this one.",
            color = Color.White.copy(alpha = 0.55f),
            fontSize = 12.sp,
        )
    }

    error?.let {
        Spacer(modifier = Modifier.height(10.dp))
        Text(text = it, color = Color(0xFFF2B8B5), fontSize = 12.sp)
    }

    Spacer(modifier = Modifier.height(18.dp))

    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 120),
        label = "downloadProgress",
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(50))
            .background(MenuRowSurface)
            .focusRequester(installFocus)
            .clickable(
                // stays pressable while downloading, so a tv remote resting on it isn't thrown off the sheet
                enabled = release.apkUrl != null,
                interactionSource = remember { MutableInteractionSource() },
                indication = RemoteFocus,
                onClick = {
                    if (downloading) return@clickable
                    val ready = downloadState as? DownloadState.Ready
                    if (ready != null) {
                        AppUpdateChecker.installApk(context, ready.file)
                    } else {
                        startDownloadAndInstall()
                    }
                },
            )
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .fillMaxWidth(fraction = animatedProgress)
                .background(Color(0xFFE0E0E0).copy(alpha = 0.3f))
        )
        Text(
            text = when {
                downloading -> "Downloading… ${(animatedProgress * 100).toInt()}%"
                release.apkUrl == null -> "No APK in this release"
                downloadState is DownloadState.Ready -> "Install here"
                error != null -> "Try downloading again"
                else -> "Install here"
            },
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
    }

    Spacer(modifier = Modifier.height(10.dp))

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(50))
            .background(MenuRowSurface)
            .clickable(
                enabled = !downloading,
                interactionSource = remember { MutableInteractionSource() },
                indication = RemoteFocus,
                onClick = { context.openUrl(release.releaseUrl) },
            )
    ) {
        Text(text = "Open the web", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
    }
    }
}

internal fun android.content.Context.openUrl(url: String) {
    runCatching {
        startActivity(
            android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

@Composable
internal fun AppIconImage(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val icon = remember(context) {
        runCatching {
            context.packageManager
                .getApplicationIcon(context.packageName)
                .toBitmap(width = 192, height = 192)
                .asImageBitmap()
        }.getOrNull()
    }

    if (icon != null) {
        Image(bitmap = icon, contentDescription = null, modifier = modifier)
    } else {
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier.background(MenuRowSurface),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_chordwave_mark),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(0.6f),
            )
        }
    }
}
