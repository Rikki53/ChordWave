// Ported from Mochy Music (GPL-3.0): ui/player/share/song/SongShareBackgroundSheet.kt
// SongShareBackgroundSheet.kt

package com.music.bitchord.ui.mochy.player.share.song

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.music.bitchord.R
import com.music.bitchord.ui.mochy.component.PopupSheetHandle
import com.music.bitchord.ui.mochy.player.menu.MenuRowSurface
import com.music.bitchord.ui.mochy.component.PopupSheetState
import com.music.bitchord.ui.mochy.component.SheetPillButton
import com.music.bitchord.ui.mochy.player.share.AddCircle
import com.music.bitchord.ui.mochy.player.share.BackdropOf
import com.music.bitchord.ui.mochy.player.share.ColourBackgroundControls
import com.music.bitchord.ui.mochy.player.share.PictureBackgroundControls
import com.music.bitchord.ui.mochy.player.share.ShareBackground
import com.music.bitchord.ui.mochy.player.share.ShareMuted
import com.music.bitchord.ui.mochy.player.share.ShareBasicColours
import com.music.bitchord.ui.mochy.player.share.ShareHint
import com.music.bitchord.ui.mochy.player.share.backgroundPageCard
import com.music.bitchord.ui.mochy.player.share.shareBackgroundBlur
import com.music.bitchord.ui.mochy.screens.audiolab.LabCard
import com.music.bitchord.ui.mochy.screens.audiolab.LabSlider
import com.music.bitchord.ui.mochy.theme.InterFontFamily
import com.music.bitchord.ui.mochy.utils.resize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

// the video pages come first and the still ones after; a pager shows one kind or the other
private enum class BackgroundPage(@StringRes val label: Int, @StringRes val hint: Int?, val video: Boolean) {
    MusicVideo(R.string.song_share_bg_music_video, R.string.song_share_bg_music_video_hint, video = true),
    OwnVideo(R.string.song_share_bg_own_video, R.string.song_share_bg_own_video_hint, video = true),
    Music(R.string.song_share_bg_music, R.string.song_share_bg_music_hint, video = false),
    Picture(R.string.song_share_bg_picture, null, video = false),
    Colour(R.string.song_share_bg_colour, null, video = false),
}

// after the type page: swipe between that kind's backgrounds, and the page you land on is used at once; ok goes straight back to the editor, back goes to the type page
internal fun PopupSheetState.showBackgroundPager(session: SongShareSession, video: Boolean, typePage: PopupSheetHandle) {
    val popup = this
    lateinit var pager: PopupSheetHandle
    val done = {
        pager.dismiss()
        typePage.dismiss()
    }
    pager = showWithHandle(buttonBar = { SheetPillButton(text = stringResource(R.string.song_share_ok), onClick = done) }) {
        SongBackgroundContent(session, popup, video)
    }
}

private fun pageOf(background: SongBackground): BackgroundPage = when (background) {
    is SongBackground.Video -> when (background.source) {
        is ShareVideoSource.Own -> BackgroundPage.OwnVideo
        ShareVideoSource.MusicVideo -> BackgroundPage.MusicVideo
    }
    is SongBackground.Still -> when (background.background) {
        ShareBackground.FromMusic -> BackgroundPage.Music
        is ShareBackground.Picture -> BackgroundPage.Picture
        is ShareBackground.Solid -> BackgroundPage.Colour
    }
}

@Composable
private fun SongBackgroundContent(session: SongShareSession, popup: PopupSheetState, video: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pages = remember(video, session.musicVideo is Fetched.Found) {
        BackgroundPage.entries.filter { page ->
            page.video == video && when (page) {
                BackgroundPage.MusicVideo -> session.musicVideo is Fetched.Found
                else -> true
            }
        }
    }
    // the last picture, colour and video are kept while you swipe, so coming back to a page finds it as you left it
    var picture by remember { mutableStateOf(session.lastStill as? ShareBackground.Picture) }
    var colour by remember { mutableStateOf((session.lastStill as? ShareBackground.Solid)?.color ?: session.palette.firstOrNull() ?: ShareBasicColours[0]) }
    var dim by remember { mutableFloatStateOf((session.background as? SongBackground.Video)?.dim ?: session.lastVideo?.dim ?: 0.85f) }
    var blur by remember { mutableFloatStateOf((session.background as? SongBackground.Video)?.blur ?: session.lastVideo?.blur ?: 0f) }
    // opens on what's up now, or on the last of this kind that was
    val opensOn = when {
        (session.background is SongBackground.Video) == video -> session.background
        video -> session.lastVideo
        else -> SongBackground.Still(session.lastStill)
    }
    val pager = rememberPagerState(initialPage = opensOn?.let { pages.indexOf(pageOf(it)) }?.coerceAtLeast(0) ?: 0) { pages.size }

    // the page you land on is used straight away, the one it opens on included, so picking a kind shows it at once
    fun applyPage(page: Int) {
        when (pages.getOrNull(page) ?: return) {
            BackgroundPage.OwnVideo -> session.lastOwnVideo?.let { session.setVideo(SongBackground.Video(it, dim, blur)) }
            BackgroundPage.MusicVideo -> session.setVideo(SongBackground.Video(ShareVideoSource.MusicVideo, dim, blur))
            BackgroundPage.Music -> session.setStill(ShareBackground.FromMusic)
            // no picture yet: keep what was there until one is picked
            BackgroundPage.Picture -> picture?.let(session::setStill)
            BackgroundPage.Colour -> session.setStill(ShareBackground.Solid(colour))
        }
    }
    LaunchedEffect(pager) { snapshotFlow { pager.settledPage }.collect(::applyPage) }

    val pictureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) {
            val next = ShareBackground.Picture(uri, blur = picture?.blur ?: 0.6f, opacity = picture?.opacity ?: 0.85f)
            picture = next
            session.setStill(next)
        }
    }
    val videoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val duration = videoDurationMs(context, uri)
            if (duration <= 0L) return@launch
            val own = ShareVideoSource.Own(uri, startMs = 0L, lengthMs = duration.coerceAtMost(OwnVideoLongestMs), durationMs = duration)
            session.lastOwnVideo = own
            session.setVideo(SongBackground.Video(own, dim, blur))
            // a video longer than the clip asks which part to use
            if (duration > OwnVideoLongestMs) popup.showVideoPart(session)
        }
    }
    val pickPicture = { pictureLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    val pickVideo = { videoLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) }
    val setDim: (Float) -> Unit = { value ->
        dim = value
        val shown = session.background
        if (shown is SongBackground.Video) session.setVideo(shown.copy(dim = value))
    }
    val setBlur: (Float) -> Unit = { value ->
        blur = value
        val shown = session.background
        if (shown is SongBackground.Video) session.setVideo(shown.copy(blur = value))
    }
    val cardHeight = (LocalConfiguration.current.screenHeightDp * 0.4f).dp

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        Spacer(Modifier.height(10.dp))
        HorizontalPager(
            state = pager,
            contentPadding = PaddingValues(horizontal = 56.dp),
            pageSpacing = 14.dp,
            modifier = Modifier.fillMaxWidth().height(cardHeight),
        ) { page ->
            Box(contentAlignment = Alignment.Center, modifier = Modifier.backgroundPageCard(pager, page)) {
                // only the page in the middle plays its video, so swiping never runs three decoders at once
                val current = pager.settledPage == page
                when (pages[page]) {
                    BackgroundPage.OwnVideo -> {
                        val own = session.lastOwnVideo
                        if (own != null) {
                            // the card shows the video as it will sit behind the words, dim included,
                            // so the brightness slider below answers at once (batch 26)
                            if (current) Box(Modifier.fillMaxSize().background(Color.Black)) { ShareVideoLayer(session, playing = session.onScreen, source = own, modifier = Modifier.fillMaxSize().graphicsLayer { alpha = dim }.shareBackgroundBlur(blur)) } else CoverStill(session)
                        } else {
                            AddCircle(contentDescription = stringResource(R.string.song_share_bg_pick_video), onClick = pickVideo)
                        }
                    }
                    BackgroundPage.MusicVideo ->
                        if (current) Box(Modifier.fillMaxSize().background(Color.Black)) { ShareVideoLayer(session, playing = session.onScreen, source = ShareVideoSource.MusicVideo, modifier = Modifier.fillMaxSize().graphicsLayer { alpha = dim }.shareBackgroundBlur(blur)) } else CoverStill(session)
                    BackgroundPage.Music -> BackdropOf(ShareBackground.FromMusic, session.song.thumbnailUrl, session.palette, animate = true)
                    BackgroundPage.Colour -> BackdropOf(ShareBackground.Solid(colour), null, session.palette, animate = false)
                    BackgroundPage.Picture -> {
                        val chosen = picture
                        if (chosen != null) {
                            BackdropOf(chosen, null, session.palette, animate = false)
                        } else {
                            AddCircle(contentDescription = stringResource(R.string.song_share_bg_pick_picture), onClick = pickPicture)
                        }
                    }
                }
            }
        }

        AnimatedContent(
            targetState = pages.getOrNull(pager.currentPage),
            transitionSpec = { (fadeIn() + slideInVertically { it / 3 }).togetherWith(fadeOut()) },
            label = "songBackgroundPage",
            modifier = Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 16.dp),
        ) { page ->
            if (page == null) return@AnimatedContent
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(
                    stringResource(page.label),
                    fontFamily = InterFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    letterSpacing = (-0.4).sp,
                    color = Color.White,
                )
                Spacer(Modifier.height(12.dp))
                when (page) {
                    BackgroundPage.OwnVideo -> if (session.lastOwnVideo == null) {
                        ShareHint(stringResource(R.string.song_share_bg_own_video_hint))
                    } else {
                        DimCard(dim, blur, setDim, setBlur) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                                TextAction(stringResource(R.string.song_share_bg_which_part)) { popup.showVideoPart(session) }
                                TextAction(stringResource(R.string.song_share_bg_change_video), onClick = pickVideo)
                            }
                        }
                    }
                    BackgroundPage.MusicVideo -> {
                        page.hint?.let { ShareHint(stringResource(it)) }
                        Spacer(Modifier.height(12.dp))
                        DimCard(dim, blur, setDim, setBlur) {}
                    }
                    BackgroundPage.Music -> page.hint?.let { ShareHint(stringResource(it)) }
                    BackgroundPage.Picture -> {
                        val chosen = picture
                        if (chosen == null) {
                            ShareHint(stringResource(R.string.song_share_bg_picture_hint))
                        } else {
                            PictureBackgroundControls(
                                picture = chosen,
                                onChange = { next ->
                                    picture = next
                                    session.setStill(next)
                                },
                                onPickAnother = pickPicture,
                            )
                        }
                    }
                    BackgroundPage.Colour -> ColourBackgroundControls(colour, session.palette) { picked ->
                        colour = picked
                        session.setStill(ShareBackground.Solid(picked))
                    }
                }
            }
        }
    }
}

@Composable
internal fun CoverStill(session: SongShareSession) {
    AsyncImage(
        model = session.song.thumbnailUrl?.resize(544, 544),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize(),
    )
}

// how bright and how soft the video shows under the card
@Composable
private fun DimCard(dim: Float, blur: Float, onDim: (Float) -> Unit, onBlur: (Float) -> Unit, extra: @Composable () -> Unit) {
    LabCard(padding = 10.dp) {
        LabSlider(
            label = stringResource(R.string.song_share_bg_brightness),
            valueText = "${(dim * 100).roundToInt()}%",
            value = dim,
            onValueChange = onDim,
            valueRange = 0.2f..1f,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
        )
        LabSlider(
            label = stringResource(R.string.mochy_share_blur),
            valueText = "${(blur * 100).roundToInt()}%",
            value = blur,
            onValueChange = onBlur,
            valueRange = 0f..1f,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
        )
        extra()
    }
}

@Composable
private fun TextAction(text: String, onClick: () -> Unit) {
    Text(
        text,
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        color = Color.White,
        modifier = Modifier
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
    )
}

// one provider's row: its name, what it offers or why it can't, and a filled dot when it is the one playing
@Composable
internal fun CanvasSourceRow(label: String, hint: String, enabled: Boolean, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = if (enabled) 1f else 0.45f }
            .clip(RoundedCornerShape(16.dp))
            .background(MenuRowSurface)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color.White)
            Text(hint, fontFamily = InterFontFamily, fontWeight = FontWeight.Medium, fontSize = 12.sp, color = ShareMuted)
        }
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .border(2.dp, Color.White.copy(alpha = if (selected) 1f else 0.5f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(Color.White))
            }
        }
    }
}

internal suspend fun videoDurationMs(context: Context, uri: Uri): Long = withContext(Dispatchers.IO) {
    runCatching {
        MediaMetadataRetriever().run {
            try {
                setDataSource(context, uri)
                extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            } finally {
                release()
            }
        }
    }.getOrDefault(0L)
}
