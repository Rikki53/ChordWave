// KaraokeSheet.kt — Phase 5 worker 5E (Karaoke restyle).
//
// The Modern (Mochy-style) karaoke controls for the player: the mic
// switch, the vocal slider, the mode picker (Instant / AI / Karaoke YT),
// the AI stem and Karaoke YT sections, and the TV remote's door. A restyle
// of the classic ui/components/KaraokeControls.kt KaraokeSettingsGroup —
// same AppSettings flows, same KaraokeAi / KaraokeYt engines, same
// PlaybackService DSP chain underneath (KaraokeProcessor). Presentation
// only: nothing here changes how karaoke sounds or when it applies.
//
// GATE — READ THIS FIRST: karaoke is deliberately hidden by the user.
// The ONE flag is KaraokeFeature.ENABLED (shared/.../karaoke/
// KaraokeFeature.kt), a compile-time const that is currently FALSE. This
// sheet and its menu row stay behind that same gate: KaraokeMenuRow
// renders nothing while the flag is off, and KaraokeSheet shows only an
// "unavailable" note. Do NOT change the flag's default (see NEEDS.md §2).
// No AI vocal separation is built here — deferred, the instrumental was
// not clean (the AI section below only surfaces the EXISTING on-device
// MDX stem flow, exactly as the classic block does).
//
// Wiring (NEEDS.md §3): PlayerActionMenu renders KaraokeMenuRow (gated)
// and, when tapped, this sheet with onOpenRemote navigating to the
// "karaoke_remote" route.

package com.music.bitchord.ui.mochy.player.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.R
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.KaraokeMode
import com.music.bitchord.karaoke.KaraokeFeature
import com.music.bitchord.karaoke.ai.KaraokeAi
import com.music.bitchord.karaoke.ai.KaraokeAiService
import com.music.bitchord.karaoke.yt.KaraokeYt
import com.music.bitchord.ui.mochy.component.AppSwitch
import com.music.bitchord.ui.mochy.component.LocalMenuState
import com.music.bitchord.ui.mochy.component.RemoteFocus
import com.music.bitchord.ui.mochy.component.SheetIcon
import com.music.bitchord.ui.mochy.component.toCwSong
import com.music.bitchord.ui.mochy.models.MediaMetadata
import com.music.bitchord.ui.mochy.playback.LocalPlayerConnection
import kotlinx.coroutines.delay
import timber.log.Timber
import kotlin.math.roundToInt

/**
 * The karaoke row for the player's ⋯ menu (PlayerActionMenu): renders
 * NOTHING while KaraokeFeature is off — the gate lives here, at the entry
 * point, exactly like the classic SongActionsMenu's `onOpenKaraoke != null`
 * row. When karaoke is on, the trailing value names the mode, as the
 * classic row does.
 */
@Composable
fun KaraokeMenuRow(onOpenKaraoke: () -> Unit) {
    if (!KaraokeFeature.ENABLED) return
    val enabled by AppSettings.karaokeEnabled.collectAsStateWithLifecycle()
    val mode by AppSettings.karaokeMode.collectAsStateWithLifecycle()
    MenuRow(
        icon = R.drawable.mic,
        title = stringResource(R.string.karaoke),
        onClick = onOpenKaraoke,
        trailing = {
            if (enabled) {
                Text(
                    text = stringResource(
                        when (mode) {
                            KaraokeMode.INSTANT -> R.string.karaoke_mode_instant
                            KaraokeMode.AI -> R.string.karaoke_mode_ai
                            KaraokeMode.YT -> R.string.karaoke_mode_yt
                        },
                    ),
                    color = Color(0xFF9A9A9A),
                    fontSize = 12.sp,
                )
            }
        },
    )
}

/**
 * The karaoke sheet: switch, vocal slider, mode picker, AI / YT sections,
 * and the TV remote's door. Opened from the player's ⋯ menu; [onOpenRemote]
 * navigates to the "karaoke_remote" route (the caller dismisses this sheet
 * first, as the equalizer row does).
 */
@Composable
fun KaraokeSheet(
    onDismiss: () -> Unit,
    onOpenRemote: () -> Unit,
) {
    MenuSheetSurface(onDismiss = onDismiss, halfDetent = 0.72f, fullDetent = 0.94f) { _ ->
        if (!KaraokeFeature.ENABLED) {
            // Defence in depth: the menu row is gated, so reaching here
            // while parked means a stale caller. Say so, don't render
            // controls that could never do anything.
            LaunchedEffect(Unit) {
                Timber.w("KaraokeSheet: opened while KaraokeFeature is off")
            }
            Column(
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp)
                    .padding(top = 10.dp, bottom = 28.dp),
            ) {
                Text(
                    text = stringResource(R.string.karaoke),
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    text = stringResource(R.string.mochy_karaoke_unavailable),
                    color = Color(0xFF9A9A9A),
                    fontSize = 14.sp,
                )
            }
            return@MenuSheetSurface
        }
        KaraokeSheetContent(onDismiss = onDismiss, onOpenRemote = onOpenRemote)
    }
}

@Composable
private fun KaraokeSheetContent(
    onDismiss: () -> Unit,
    onOpenRemote: () -> Unit,
) {
    val enabled by AppSettings.karaokeEnabled.collectAsStateWithLifecycle()
    val vocalLevel by AppSettings.karaokeVocalLevel.collectAsStateWithLifecycle()
    val mode by AppSettings.karaokeMode.collectAsStateWithLifecycle()
    val metadata = currentMetadata()

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = 24.dp)
            .padding(top = 10.dp, bottom = 28.dp),
    ) {
        Text(
            text = stringResource(R.string.karaoke),
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.karaoke_subtitle),
            color = Color(0xFF9A9A9A),
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(14.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(MenuRowSurface)
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            SheetIcon(R.drawable.mic)
            Spacer(Modifier.width(14.dp))
            Text(
                text = stringResource(R.string.karaoke),
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            AppSwitch(
                checked = enabled,
                onCheckedChange = { AppSettings.setKaraokeEnabled(it) },
            )
        }

        if (enabled) {
            Spacer(Modifier.height(12.dp))
            // The vocal level only exists in Instan and AI. In Karaoke YT
            // mode the track itself is the karaoke take — there is no
            // vocal stem to fade — so the slider is not drawn at all
            // rather than drawn disabled; the note below points at the
            // Versi asli / Versi karaoke buttons instead. The stored level
            // is left untouched. (Classic KaraokeControls, verbatim logic.)
            if (mode != KaraokeMode.YT) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(MenuRowSurface)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.karaoke_vocal_volume),
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "${(vocalLevel * 100).roundToInt()}%",
                            color = Color(0xFF9A9A9A),
                            fontSize = 12.sp,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    LineSlider(
                        value = vocalLevel,
                        onValueChange = { AppSettings.setKaraokeVocalLevel(it) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(12.dp))
            }
            if (mode == KaraokeMode.YT) {
                Text(
                    text = stringResource(R.string.karaoke_yt_slider_note),
                    color = Color(0xFF9A9A9A),
                    fontSize = 12.sp,
                )
                Spacer(Modifier.height(12.dp))
            }
            KaraokeModePills(
                mode = mode,
                onSelect = { AppSettings.setKaraokeMode(it) },
            )
            if (mode == KaraokeMode.AI) {
                Spacer(Modifier.height(12.dp))
                KaraokeAiSheetSection(song = metadata)
            }
            if (mode == KaraokeMode.YT) {
                Spacer(Modifier.height(12.dp))
                KaraokeYtSheetSection(song = metadata)
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.karaoke_footer),
                color = Color(0xFF9A9A9A),
                fontSize = 12.sp,
            )
        }

        Spacer(Modifier.height(16.dp))
        // The TV remote's door: always here while the feature is on, even
        // with karaoke itself switched off — driving a TV makes sense on
        // its own, as in the classic Equalizer block.
        MenuRow(
            icon = R.drawable.cast,
            title = stringResource(R.string.karaoke_remote_title),
            onClick = {
                onDismiss()
                onOpenRemote()
            },
        )
    }
}

/** The song on air, or null when there is no player connection (same early-return pattern as PlayerActionMenu). */
@Composable
private fun currentMetadata(): MediaMetadata? {
    val playerConnection = LocalPlayerConnection.current ?: return null
    val metadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()
    return metadata
}

/** The three modes as Mochy pills: Instan / AI / Karaoke YT. */
@Composable
private fun KaraokeModePills(
    mode: KaraokeMode,
    onSelect: (KaraokeMode) -> Unit,
) {
    val labels = listOf(
        stringResource(R.string.karaoke_mode_instant),
        stringResource(R.string.karaoke_mode_ai),
        stringResource(R.string.karaoke_mode_yt),
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MenuRowSurface)
            .padding(4.dp),
    ) {
        KaraokeMode.entries.forEachIndexed { index, entry ->
            val selected = entry == mode
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (selected) Color.White.copy(alpha = 0.16f) else Color.Transparent,
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = RemoteFocus,
                        onClick = { onSelect(entry) },
                    )
                    .padding(vertical = 10.dp),
            ) {
                Text(
                    text = labels[index],
                    color = Color.White.copy(alpha = if (selected) 1f else 0.6f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

/**
 * The mode-AI half of the sheet: create / progress / stem cache. Mirrors
 * the classic KaraokeAiSection — the song comes from the Modern player
 * connection (MediaMetadata) instead of rememberPlayerState, and is
 * converted at the boundary with toCwSong() for KaraokeAiService.start().
 */
@Composable
private fun KaraokeAiSheetSection(song: MediaMetadata?) {
    val context = LocalContext.current
    val job by KaraokeAi.job.collectAsStateWithLifecycle()
    val currentJob = job // local val: smart casts fail on `by` delegates
    val stemDeletedLabel = stringResource(R.string.mochy_karaoke_ai_stem_deleted)

    // Stem existence is a disk fact, not a flow: re-check it on a slow
    // tick while this section is on screen (creation finishing, a delete).
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(song?.id, job?.done) {
        while (true) {
            tick++
            delay(1000)
        }
    }
    @Suppress("UNUSED_EXPRESSION") tick
    val hasStem = song != null && KaraokeAi.hasStem(context, song.id)
    val stemBytes = if (song != null) KaraokeAi.stemBytes(context, song.id) else 0L
    val totalBytes = KaraokeAi.totalBytes(context)
    // The running job as its own val: `running` being true would not
    // smart-cast the nullable `currentJob` in the branch below.
    val runningJob = currentJob?.takeIf { !it.done && it.error == null && !it.cancelled }
    val running = runningJob != null
    val jobForSong = currentJob?.takeIf { it.songId == song?.id }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MenuRowSurface)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        when {
            runningJob != null -> {
                Text(
                    text = buildString {
                        append(runningJob.phase)
                        if (runningJob.fraction >= 0f) {
                            append(" ${(runningJob.fraction * 100).roundToInt()}%")
                        }
                        runningJob.title.takeIf { it.isNotBlank() }?.let { append(" — $it") }
                    },
                    color = Color(0xFF9A9A9A),
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(8.dp))
                if (runningJob.fraction >= 0f) {
                    LinearProgressIndicator(
                        progress = { runningJob.fraction.coerceIn(0f, 1f) },
                        color = Color.White,
                        trackColor = Color.White.copy(alpha = 0.2f),
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    LinearProgressIndicator(
                        color = Color.White,
                        trackColor = Color.White.copy(alpha = 0.2f),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(8.dp))
                KaraokeTextButton(
                    label = stringResource(R.string.karaoke_ai_cancel),
                    onClick = { KaraokeAiService.cancel(context) },
                )
            }
            song == null -> {
                Text(
                    text = stringResource(R.string.karaoke_ai_no_song),
                    color = Color(0xFF9A9A9A),
                    fontSize = 13.sp,
                )
            }
            hasStem -> {
                Text(
                    text = "${stringResource(R.string.karaoke_ai_ready)} · ${formatKaraokeBytes(stemBytes)}",
                    color = Color(0xFF9A9A9A),
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(8.dp))
                KaraokeTextButton(
                    label = stringResource(R.string.karaoke_ai_delete),
                    onClick = {
                        KaraokeAi.deleteStem(context, song.id)
                        // Nudge PlaybackService (it re-applies on a
                        // finished job) so a stem deleted under the playing
                        // song restores its original source instead of
                        // playing a missing file.
                        KaraokeAi.job.value = KaraokeAi.JobState(
                            songId = song.id, title = song.title,
                            phase = stemDeletedLabel, fraction = 1f, done = true,
                        )
                    },
                )
            }
            else -> {
                if (jobForSong?.cancelled == true) {
                    Text(
                        text = stringResource(R.string.mochy_karaoke_ai_cancelled),
                        color = Color(0xFF9A9A9A),
                        fontSize = 13.sp,
                    )
                }
                val jobError = jobForSong?.error
                if (jobError != null) {
                    Text(
                        text = stringResource(R.string.mochy_karaoke_ai_failed, jobError),
                        color = Color(0xFFEE5A52),
                        fontSize = 13.sp,
                    )
                    Spacer(Modifier.height(4.dp))
                } else if (jobForSong?.cancelled != true) {
                    Text(
                        text = stringResource(R.string.karaoke_ai_not_ready),
                        color = Color(0xFF9A9A9A),
                        fontSize = 13.sp,
                    )
                    Spacer(Modifier.height(4.dp))
                }
                Spacer(Modifier.height(4.dp))
                KaraokeSheetButton(
                    label = stringResource(R.string.karaoke_ai_create),
                    onClick = { KaraokeAiService.start(context, song.toCwSong()) },
                )
            }
        }
        if (totalBytes > 0L && !running) {
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.mochy_karaoke_ai_cache, formatKaraokeBytes(totalBytes)),
                    color = Color(0xFF9A9A9A),
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f),
                )
                KaraokeTextButton(
                    label = stringResource(R.string.karaoke_ai_clear_all),
                    onClick = {
                        val currentId = song?.id
                        KaraokeAi.clearAll(context)
                        if (currentId != null) {
                            KaraokeAi.job.value = KaraokeAi.JobState(
                                songId = currentId, title = song?.title.orEmpty(),
                                phase = stemDeletedLabel, fraction = 1f, done = true,
                            )
                        }
                    },
                )
            }
        }
    }
}

/**
 * The mode-Karaoke-YT half of the sheet: match status and corrections.
 * Mirrors the classic KaraokeYtSection; the candidate picker opens as a
 * PopupSheet via LocalMenuState (Mochy's sheet idiom) instead of the
 * classic AlertDialog.
 */
@Composable
private fun KaraokeYtSheetSection(song: MediaMetadata?) {
    val menuState = LocalMenuState.current
    val ytState by KaraokeYt.state.collectAsStateWithLifecycle()

    // The pairing count is a prefs fact, not a flow: re-read it on a slow
    // tick while this section is on screen, like the AI section does.
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(song?.id) {
        while (true) {
            tick++
            delay(1000)
        }
    }
    @Suppress("UNUSED_EXPRESSION") tick
    val pairCount = AppSettings.karaokeYtPairingCount()
    val st = ytState?.takeIf { it.songId == song?.id }
    val pairing = song?.let { AppSettings.karaokeYtPairing(it.id) }
    val hasCandidates = st?.candidates?.isNotEmpty() == true

    fun openPicker() {
        val songId = song?.id
        val candidates = st?.candidates.orEmpty()
        if (songId == null || candidates.isEmpty()) return
        menuState.show {
            KaraokeYtPickerContent(
                candidates = candidates,
                onPick = { candidate ->
                    KaraokeYt.choose(songId, candidate)
                    menuState.dismiss()
                },
                onDismiss = { menuState.dismiss() },
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MenuRowSurface)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        when {
            song == null -> {
                Text(
                    text = stringResource(R.string.karaoke_ai_no_song),
                    color = Color(0xFF9A9A9A),
                    fontSize = 13.sp,
                )
            }
            st?.status == KaraokeYt.Status.SEARCHING -> {
                Text(
                    text = stringResource(R.string.karaoke_yt_searching),
                    color = Color(0xFF9A9A9A),
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.2f),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            st?.status == KaraokeYt.Status.KARAOKE -> {
                Text(
                    text = stringResource(R.string.karaoke_yt_playing),
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                st?.karaokeTitle?.let {
                    Text(
                        text = it,
                        color = Color(0xFF9A9A9A),
                        fontSize = 12.sp,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row {
                    KaraokeTextButton(
                        label = stringResource(R.string.karaoke_yt_use_original),
                        onClick = { KaraokeYt.useOriginal(song.id) },
                    )
                    Spacer(Modifier.width(16.dp))
                    if (hasCandidates) {
                        KaraokeTextButton(
                            label = stringResource(R.string.karaoke_yt_change),
                            onClick = ::openPicker,
                        )
                    }
                }
            }
            st?.status == KaraokeYt.Status.NOT_FOUND -> {
                Text(
                    text = stringResource(R.string.karaoke_yt_not_found),
                    color = Color(0xFF9A9A9A),
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(8.dp))
                Row {
                    KaraokeTextButton(
                        label = stringResource(R.string.karaoke_yt_retry),
                        onClick = { KaraokeYt.requestSearch(song.id) },
                    )
                    if (hasCandidates) {
                        Spacer(Modifier.width(16.dp))
                        KaraokeTextButton(
                            label = stringResource(R.string.karaoke_yt_pick),
                            onClick = ::openPicker,
                        )
                    }
                }
            }
            else -> {
                Text(
                    text = stringResource(R.string.karaoke_yt_original),
                    color = Color(0xFF9A9A9A),
                    fontSize = 13.sp,
                )
                pairing?.let {
                    Text(
                        text = it.title,
                        color = Color(0xFF9A9A9A),
                        fontSize = 12.sp,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row {
                    KaraokeTextButton(
                        label = stringResource(R.string.karaoke_yt_use_karaoke),
                        onClick = {
                            KaraokeYt.useKaraoke(song.id)
                            if (pairing == null) KaraokeYt.requestSearch(song.id)
                        },
                    )
                    if (hasCandidates) {
                        Spacer(Modifier.width(16.dp))
                        KaraokeTextButton(
                            label = stringResource(R.string.karaoke_yt_change),
                            onClick = ::openPicker,
                        )
                    }
                }
            }
        }
        if (pairCount > 0) {
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.karaoke_yt_pairs, pairCount),
                    color = Color(0xFF9A9A9A),
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f),
                )
                KaraokeTextButton(
                    label = stringResource(R.string.karaoke_yt_clear_pairs),
                    onClick = { KaraokeYt.clearPairings() },
                )
            }
        }
    }
}

/** The manual correction list as a PopupSheet: tap a candidate to play it. */
@Composable
private fun ColumnScope.KaraokeYtPickerContent(
    candidates: List<KaraokeYt.Candidate>,
    onPick: (KaraokeYt.Candidate) -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .navigationBarsPadding()
            .padding(horizontal = 24.dp)
            .padding(top = 10.dp, bottom = 28.dp),
    ) {
        Text(
            text = stringResource(R.string.karaoke_yt_pick_title),
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(14.dp))
        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
            items(candidates, key = { it.videoId }) { candidate ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = RemoteFocus,
                            onClick = { onPick(candidate) },
                        )
                        .padding(vertical = 8.dp),
                ) {
                    Text(
                        text = candidate.title,
                        color = Color.White,
                        fontSize = 14.sp,
                        maxLines = 2,
                    )
                    Text(
                        text = buildString {
                            append(candidate.artist)
                            candidate.durationSec?.let {
                                append(" \u2022 ")
                                append(formatKaraokeDuration(it))
                            }
                            candidate.durationDiffSec?.let {
                                append(" \u2022 \u0394 ")
                                append(it)
                                append(" dtk")
                            }
                        },
                        color = Color(0xFF9A9A9A),
                        fontSize = 12.sp,
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        KaraokeSheetButton(
            label = stringResource(R.string.karaoke_ai_cancel),
            onClick = onDismiss,
        )
    }
}

/** A quiet text action inside the sheet's cards (the classic TextButton). */
@Composable
private fun KaraokeTextButton(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        color = Color.White,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = RemoteFocus,
            onClick = onClick,
        ),
    )
}

/** The sheet's one main action: a pill a step lighter than the rows. */
@Composable
private fun KaraokeSheetButton(label: String, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MenuRowSurface)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = RemoteFocus,
                onClick = onClick,
            )
            .padding(vertical = 14.dp),
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
    }
}

private fun formatKaraokeBytes(bytes: Long): String = when {
    bytes >= 1_000_000L -> "%.1f MB".format(bytes / 1_000_000.0)
    bytes >= 1_000L -> "%.0f KB".format(bytes / 1_000.0)
    else -> "$bytes B"
}

private fun formatKaraokeDuration(totalSec: Int): String =
    "%d:%02d".format(totalSec / 60, totalSec % 60)
