// Ported from Mochy Music (GPL-3.0): ui/menu/songedit/SongEditContent.kt
// UI verbatim. Adaptations (NEEDS.md §(b) E1/E2/L2):
// - SIGNATURE: Mochy took (state, audioLab: AudioLabStore). CW has no
//   AudioLabStore, so the sheet takes only (state); the EQ select is stubbed
//   (below) instead of reading the store.
// - LyricsSourceSelect: reads the connection's per-provider lookup states
//   (rememberLyricsSources), so the dropdown offers every provider that
//   answered for the song — wired in the Nov 2026 lyrics merge.
// - EqPresetSelect: Mochy listed EqPresets.BuiltIn + the store's saved
//   presets. CW has an equalizer but no per-song preset store, so the
//   dropdown offers only "Same as every other song" and a pick logs a
//   Timber warning + TODO (unreachable with one option, kept for the
//   adapter). NEEDS.md §(b) E2.
// - VideoRow reads the staged SongEdits stub (always "auto").
// - LabSelect is the staged copy in this package (see LabSelect.kt).

package com.music.bitchord.ui.mochy.menu.songedit

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.bitchord.R
import com.music.bitchord.ui.mochy.component.RemoteFocus
import com.music.bitchord.ui.mochy.component.SheetIcon
import com.music.bitchord.ui.mochy.component.SheetLabel
import com.music.bitchord.ui.mochy.component.SheetTextField
import com.music.bitchord.ui.mochy.component.bleedHorizontally
import com.music.bitchord.ui.mochy.lyrics.rememberLyricsSources
import com.music.bitchord.ui.mochy.lyrics.LyricsProviderRegistry
import com.music.bitchord.ui.mochy.lyrics.LyricsSourceState
import com.music.bitchord.ui.mochy.lyrics.LyricsUtils
import com.music.bitchord.ui.mochy.player.menu.LineSlider
import com.music.bitchord.ui.mochy.player.menu.MenuRowSurface
import com.music.bitchord.ui.mochy.songedit.SongEdits
import com.music.bitchord.ui.mochy.theme.InterFontFamily
import timber.log.Timber
import java.util.Locale
import kotlin.math.roundToInt

// the popup sheet pads its content by this much, the cover row and the artist row reach past it to the edges
private val SheetSidePadding = 20.dp
private val SectionGap = 22.dp
private val LabelGap = 10.dp

private const val MaxTitleLength = 200
private const val OffsetStepMs = 100
private const val SpeedStep = 0.05f

// the body of the song edit sheet, top to bottom as in the mock; scrolls on its own while ok stays pinned under it
@Composable
internal fun SongEditContent(state: SongEditState) {
    var showVideoPicker by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 12.dp),
    ) {
        Text(
            text = stringResource(R.string.edit_song),
            color = Color.White,
            fontFamily = InterFontFamily,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(14.dp))
        SongCoverCarousel(state = state, modifier = Modifier.bleedHorizontally(SheetSidePadding))

        Spacer(Modifier.height(SectionGap))
        SheetLabel(stringResource(R.string.song_edit_name))
        Spacer(Modifier.height(LabelGap))
        SheetTextField(
            value = state.title,
            onValueChange = { state.title = it.take(MaxTitleLength) },
            placeholder = stringResource(R.string.song_edit_name_hint),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
        )

        Spacer(Modifier.height(SectionGap))
        LyricsSection(state)

        Spacer(Modifier.height(SectionGap))
        OffsetSection(state)

        Spacer(Modifier.height(SectionGap))
        SpeedSection(state)

        Spacer(Modifier.height(SectionGap))
        SheetLabel(stringResource(R.string.song_edit_eq))
        Spacer(Modifier.height(LabelGap))
        EqPresetSelect(state)

        Spacer(Modifier.height(SectionGap))
        SheetLabel(stringResource(R.string.song_edit_artist))
        Spacer(Modifier.height(LabelGap))
        SongArtistPicker(state = state, sidePadding = SheetSidePadding)

        Spacer(Modifier.height(SectionGap))
        SheetLabel(stringResource(R.string.song_edit_more))
        Spacer(Modifier.height(LabelGap))
        MoreDetails(state)

        Spacer(Modifier.height(SectionGap))
        SheetLabel(stringResource(R.string.song_edit_video))
        Spacer(Modifier.height(LabelGap))
        VideoRow(songId = state.songId, onClick = { showVideoPicker = true })
    }

    if (showVideoPicker) {
        VideoPickerSheet(
            songId = state.songId,
            title = state.title,
            artist = state.artists.joinToString { it.name },
            onDismiss = { showVideoPicker = false },
        )
    }
}

// "custom lyrics": a dashed box to bring in a lyrics file, then a choice of what the lyrics sources found
@Composable
private fun LyricsSection(state: SongEditState) {
    SheetLabel(stringResource(R.string.song_edit_lyrics))
    Spacer(Modifier.height(LabelGap))
    LyricsFileBox(
        picked = state.pickedLyrics,
        onPicked = { state.pickedLyrics = it },
        onRemove = { state.pickedLyrics = null },
    )

    // a file of your own wins, so the sources only matter without one
    if (state.pickedLyrics == null) {
        Spacer(Modifier.height(10.dp))
        LyricsSourceSelect(state)
    }
}

@Composable
private fun LyricsSourceSelect(state: SongEditState) {
    val sources by rememberLyricsSources().collectAsState()

    val found = remember(sources) {
        sources.values
            .filterIsInstance<LyricsSourceState.Available>()
            .map { it.candidate }
            .sortedBy { it.order }
    }
    val word = stringResource(R.string.song_edit_lyrics_word)
    val line = stringResource(R.string.song_edit_lyrics_line)
    val plain = stringResource(R.string.song_edit_lyrics_plain)
    val keep = stringResource(R.string.song_edit_lyrics_keep)
    val format = stringResource(R.string.song_edit_lyrics_source)
    val options = listOf(keep) + found.map { candidate ->
        val kind = when (candidate.sync) {
            LyricsUtils.SyncKind.WORD -> word
            LyricsUtils.SyncKind.LINE -> line
            else -> plain
        }
        String.format(format, LyricsProviderRegistry.getDisplayName(candidate.providerName), kind)
    }
    val selected = state.lyricsSource?.let { pick -> found.indexOfFirst { it.providerName == pick.providerName } + 1 } ?: 0

    LabSelect(
        options = options,
        selectedIndex = selected.coerceAtLeast(0),
        onSelect = { index -> state.lyricsSource = found.getOrNull(index - 1) },
        contentDescription = stringResource(R.string.song_edit_lyrics_source_desc),
    )
}

// "set lyric offset": the pill on the right shows it and puts it back to zero, the line under it slides from two minutes early to three late
@Composable
private fun OffsetSection(state: SongEditState) {
    val seconds = String.format(Locale.getDefault(), "%+.1f", state.lyricsOffsetMs / 1000f)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        SheetLabel(stringResource(R.string.song_edit_offset), modifier = Modifier.weight(1f))
        ValuePill(
            text = stringResource(R.string.song_edit_offset_value, seconds),
            description = stringResource(R.string.song_edit_offset_reset),
            onClick = { state.lyricsOffsetMs = 0 },
        )
    }
    Spacer(Modifier.height(6.dp))
    LineSlider(
        value = (state.lyricsOffsetMs - OffsetMinMs).toFloat() / (OffsetMaxMs - OffsetMinMs),
        onValueChange = { fraction ->
            val raw = OffsetMinMs + fraction * (OffsetMaxMs - OffsetMinMs)
            state.lyricsOffsetMs = (raw / OffsetStepMs).roundToInt() * OffsetStepMs
        },
    )
    RangeLabels(stringResource(R.string.song_edit_offset_min), stringResource(R.string.song_edit_offset_max))
}

// "set playback speed": saved with the song, so it plays this fast every time; the pill puts it back to normal
@Composable
private fun SpeedSection(state: SongEditState) {
    val value = String.format(Locale.getDefault(), "%.2f", state.speed)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        SheetLabel(stringResource(R.string.song_edit_speed), modifier = Modifier.weight(1f))
        ValuePill(
            text = stringResource(R.string.song_edit_speed_value, value),
            description = null,
            onClick = { state.speed = 1f },
        )
    }
    Spacer(Modifier.height(6.dp))
    LineSlider(
        value = (state.speed - SpeedMin) / (SpeedMax - SpeedMin),
        onValueChange = { fraction ->
            val raw = SpeedMin + fraction * (SpeedMax - SpeedMin)
            state.speed = ((raw / SpeedStep).roundToInt() * SpeedStep).coerceIn(SpeedMin, SpeedMax)
        },
    )
    RangeLabels(stringResource(R.string.song_edit_speed_min), stringResource(R.string.song_edit_speed_max))
    Spacer(Modifier.height(6.dp))
    Text(text = stringResource(R.string.song_edit_speed_hint), color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
}

@Composable
private fun ValuePill(text: String, description: String?, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.85f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = RemoteFocus,
                onClickLabel = description,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Text(text = text, color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun RangeLabels(start: String, end: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(text = start, color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Text(text = end, color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    }
}

// "set equalizer preset": the song's own curve while it plays, or the same as every other song
@Composable
private fun EqPresetSelect(state: SongEditState) {
    // STUB (NEEDS.md §(b) E2): Mochy listed EqPresets.BuiltIn + the
    // AudioLabStore's saved presets. CW has no per-song EQ store, so the
    // only option is "same as every other song" and the state keeps null.
    val none = stringResource(R.string.song_edit_eq_none)
    LabSelect(
        options = listOf(none),
        selectedIndex = 0,
        onSelect = {
            // TODO(phase3-adapter): per-song EQ preset store (NEEDS.md §(b) E2).
            Timber.w("SongEditContent: per-song EQ presets not available (no store yet)")
            state.eqPresetId = null
        },
        contentDescription = stringResource(R.string.song_edit_eq_desc),
    )
}

// "other music metadata": the album and the year
@Composable
private fun MoreDetails(state: SongEditState) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(2f)) {
            Text(text = stringResource(R.string.song_edit_album), color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
            Spacer(Modifier.height(6.dp))
            SheetTextField(
                value = state.album,
                onValueChange = { state.album = it.take(MaxTitleLength) },
                placeholder = stringResource(R.string.song_edit_album_hint),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = stringResource(R.string.song_edit_year), color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
            Spacer(Modifier.height(6.dp))
            SheetTextField(
                value = state.year,
                onValueChange = { typed -> state.year = typed.filter(Char::isDigit).take(4) },
                placeholder = stringResource(R.string.song_edit_year_hint),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            )
        }
    }
}

// the song's video as it stands, tapping opens select video over the sheet
@Composable
private fun VideoRow(songId: String, onClick: () -> Unit) {
    val edits by SongEdits.all.collectAsState()
    val edit = edits[songId]
    val status = when {
        edit?.noVideo == true -> R.string.song_edit_video_none
        edit?.videoId != null -> R.string.song_edit_video_picked
        else -> R.string.song_edit_video_auto
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MenuRowSurface)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = RemoteFocus,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 14.dp),
    ) {
        SheetIcon(R.drawable.slow_motion_video)
        Spacer(Modifier.width(14.dp))
        Text(
            text = stringResource(R.string.video_pick_title),
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        Text(text = stringResource(status), color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp)
    }
}
