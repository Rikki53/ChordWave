// Ported from Mochy Music (GPL-3.0): ui/menu/songedit/VideoPickerSheet.kt
// UI verbatim. Engine stubs (NEEDS.md §(b) E4/E6):
// - SEARCH: Mochy searched YouTube for videos as you typed
//   (YouTube.search FILTER_VIDEO, SongItem results). CW has no video search
//   wired here, so typing finds nothing and logs a Timber warning + TODO
//   (once per query). The Auto / No video choices still work as UI state.
// - STARTING CHOICE: reads the staged SongEdits stub (always Auto — no
//   store, so no song has a picked video yet).
// - SAVE: Mochy ran SongEditSaver.setVideo(db, songId, videoId, noVideo).
//   CW has no store, so "Next" with a changed choice logs a Timber warning
//   + TODO and shows the "Couldn't save your changes" toast instead of
//   dismissing as if the choice had been saved. "Next" with an unchanged
//   choice just dismisses, as in Mochy.
// - SongItem results are gone with the search; the result rows render from
//   a local VideoResult (id/title/artists/duration/thumbnail) so the row UI
//   stays verbatim and the adapter only has to fill the list.

package com.music.bitchord.ui.mochy.menu.songedit

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.music.bitchord.R
import com.music.bitchord.ui.mochy.component.LocalMenuState
import com.music.bitchord.ui.mochy.component.PopupSheetHandle
import com.music.bitchord.ui.mochy.component.RemoteFocus
import com.music.bitchord.ui.mochy.component.SheetPillButton
import com.music.bitchord.ui.mochy.component.SheetSearchField
import com.music.bitchord.ui.mochy.component.TopToaster
import com.music.bitchord.ui.mochy.player.menu.MenuRowSurface
import com.music.bitchord.ui.mochy.songedit.SongEdits
import com.music.bitchord.ui.mochy.theme.InterFontFamily
import com.music.bitchord.ui.mochy.utils.makeTimeString
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

private const val SearchSettleMs = 400L

// what the song's video should be: whatever mochy finds, none at all, or one picked here
internal sealed interface VideoChoice {
    data object Auto : VideoChoice
    data object None : VideoChoice
    data class Picked(val videoId: String) : VideoChoice
}

/**
 * One video search hit. Mochy used innertube's SongItem; CW has no video
 * search wired, so this local shape carries what the row renders until the
 * adapter fills it from a real search (NEEDS.md §(b) E6).
 */
internal data class VideoResult(
    val id: String,
    val title: String,
    val artists: List<String>,
    val durationSeconds: Int?,
    val thumbnail: String?,
)

@Stable
private class VideoPickState(query: String, val startChoice: VideoChoice) {
    var query by mutableStateOf(query)
    var choice by mutableStateOf(startChoice)
    val results = mutableStateListOf<VideoResult>()
    var searching by mutableStateOf(false)
    var busy by mutableStateOf(false)
}

// "select video" from the mock: pick the right youtube video for a song, or none, for its cover video and the video mode; opens over whatever asked for it
@Composable
fun VideoPickerSheet(songId: String, title: String, artist: String, onDismiss: () -> Unit) {
    val menuState = LocalMenuState.current
    val context = LocalContext.current
    val currentOnDismiss by rememberUpdatedState(onDismiss)

    DisposableEffect(menuState, songId) {
        val edit = SongEdits.of(songId)
        val start = when {
            edit?.noVideo == true -> VideoChoice.None
            edit?.videoId != null -> VideoChoice.Picked(edit.videoId)
            else -> VideoChoice.Auto
        }
        val state = VideoPickState("$title $artist".trim(), start)
        val handle = arrayOfNulls<PopupSheetHandle>(1)
        var closed = false
        handle[0] = menuState.showWithHandle(
            buttonBar = {
                val scope = rememberCoroutineScope()
                SheetPillButton(
                    text = stringResource(R.string.video_pick_next),
                    enabled = !state.busy,
                    onClick = {
                        val choice = state.choice
                        if (choice == state.startChoice) {
                            handle[0]?.dismiss()
                            return@SheetPillButton
                        }
                        state.busy = true
                        handle[0]?.setLocked(true)
                        scope.launch {
                            // TODO(phase3-adapter): persist the video choice
                            // (NEEDS.md §(b) E4). Mochy ran
                            // SongEditSaver.setVideo(db, songId, videoId,
                            // noVideo) here. CW has no store, so the choice
                            // is dropped: warn, toast, unlock, stay open.
                            Timber.w(
                                "VideoPickerSheet: video choice for %s not saved (no metadata store yet)",
                                songId,
                            )
                            TopToaster.show(
                                text = context.getString(R.string.song_edit_failed),
                                iconRes = R.drawable.edit,
                            )
                            state.busy = false
                            handle[0]?.setLocked(false)
                        }
                    },
                )
            },
            onClosed = {
                closed = true
                currentOnDismiss()
            },
        ) {
            VideoPickerContent(state)
        }
        onDispose { if (!closed) handle[0]?.dismiss() }
    }
}

@Composable
private fun VideoPickerContent(state: VideoPickState) {
    LaunchedEffect(state.query) {
        val text = state.query.trim()
        if (text.isEmpty()) {
            state.results.clear()
            return@LaunchedEffect
        }
        delay(SearchSettleMs)
        state.searching = true
        // TODO(phase3-adapter): video search (NEEDS.md §(b) E6). Mochy ran
        // YouTube.search(text, FILTER_VIDEO) here; CW has no video search
        // wired, so nothing comes back.
        Timber.w("VideoPickerSheet: video search not available (no engine yet)")
        state.results.clear()
        state.searching = false
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(top = 8.dp, bottom = 12.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.85f)),
        ) {
            Icon(
                painter = painterResource(R.drawable.slow_motion_video),
                contentDescription = null,
                tint = Color.Black,
                modifier = Modifier.size(24.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.video_pick_title),
            color = Color.White,
            fontFamily = InterFontFamily,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.04).em,
        )
        Text(
            text = stringResource(R.string.video_pick_subtitle),
            color = Color.White.copy(alpha = 0.55f),
            fontFamily = InterFontFamily,
            fontSize = 19.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.03).em,
        )
        Spacer(Modifier.height(22.dp))
        SheetSearchField(
            value = state.query,
            onValueChange = { state.query = it },
            placeholder = stringResource(R.string.video_pick_search),
        )
        Spacer(Modifier.height(12.dp))

        ChoiceRow(
            title = stringResource(R.string.video_pick_auto),
            detail = stringResource(R.string.video_pick_auto_desc),
            picked = state.choice == VideoChoice.Auto,
            onClick = { state.choice = VideoChoice.Auto },
        )
        Spacer(Modifier.height(8.dp))
        ChoiceRow(
            title = stringResource(R.string.video_pick_none),
            detail = stringResource(R.string.video_pick_none_desc),
            picked = state.choice == VideoChoice.None,
            onClick = { state.choice = VideoChoice.None },
        )

        // the video picked before, even when this search doesn't bring it up
        val earlier = state.startChoice as? VideoChoice.Picked
        if (earlier != null && state.results.none { it.id == earlier.videoId }) {
            Spacer(Modifier.height(8.dp))
            VideoRow(
                thumbnail = "https://i.ytimg.com/vi/${earlier.videoId}/mqdefault.jpg",
                title = stringResource(R.string.song_edit_video_picked),
                detail = null,
                picked = state.choice == earlier,
                onClick = { state.choice = earlier },
            )
        }
        for (video in state.results) {
            Spacer(Modifier.height(8.dp))
            val choice = VideoChoice.Picked(video.id)
            VideoRow(
                thumbnail = video.thumbnail,
                title = video.title,
                detail = listOfNotNull(
                    video.artists.joinToString { it }.takeIf { it.isNotBlank() },
                    video.durationSeconds?.let { makeTimeString(it * 1000L) },
                ).joinToString(" · "),
                picked = state.choice == choice,
                onClick = { state.choice = choice },
            )
        }
        if (!state.searching && state.results.isEmpty() && state.query.isNotBlank()) {
            Text(
                text = stringResource(R.string.video_pick_nothing),
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 13.sp,
                modifier = Modifier.padding(start = 4.dp, top = 14.dp),
            )
        }
    }
}

private val PickedRow = lerp(MenuRowSurface, Color.White, 0.12f)

@Composable
private fun ChoiceRow(title: String, detail: String, picked: Boolean, onClick: () -> Unit) {
    PickRow(picked = picked, onClick = onClick) {
        Column(modifier = Modifier.weight(1f).padding(horizontal = 6.dp, vertical = 4.dp)) {
            Text(text = title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(text = detail, color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp)
        }
    }
}

// the mock's result row: the video's picture, then its title and channel
@Composable
private fun VideoRow(thumbnail: String?, title: String, detail: String?, picked: Boolean, onClick: () -> Unit) {
    PickRow(picked = picked, onClick = onClick) {
        AsyncImage(
            model = thumbnail,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .width(112.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.08f)),
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!detail.isNullOrBlank()) {
                Text(
                    text = detail,
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun PickRow(picked: Boolean, onClick: () -> Unit, content: @Composable RowScope.() -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (picked) PickedRow else MenuRowSurface)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = RemoteFocus,
                onClick = onClick,
            )
            .padding(10.dp),
    ) {
        content()
        if (picked) {
            Spacer(Modifier.width(10.dp))
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(Color.White),
            ) {
                Icon(
                    painter = painterResource(R.drawable.check),
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}
