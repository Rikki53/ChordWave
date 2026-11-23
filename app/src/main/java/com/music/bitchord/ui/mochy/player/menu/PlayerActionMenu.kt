// Ported from Mochy Music (GPL-3.0): ui/player/menu/PlayerActionMenu.kt
//
// ADAPTATIONS (CW has no LocalDatabase / LocalDownloadUtil / Mochy YouTube
// engine / modules system — see NEEDS.md):
// - Track info: Mochy read playerConnection.uiState.trackInfo; CW's
//   MochyPlayerConnection has no uiState, so the header is built from
//   mediaMetadata (trackInfoFromMetadata, below) — same fields.
// - Download state comes from CW's Downloads flows (downloadStateFor);
//   download/remove go through Downloads.enqueue() / removeDownload()
//   (same pattern as the ported SongMenu).
// - In-library state comes from MediaMetadata.inLibrary (no db re-read).
// - LyricsMenuViewModel + the lyricsMetadata/lyricsEntity collections are
//   DROPPED: dead in Mochy (collected but never read in this file's body);
//   the lyrics sheets read the stubs themselves.
// - Share is a plain ACTION_SEND intent with the watch link (Mochy's
//   SongShare sheet is not ported) — same as SongMenu.
// - "Wrong video? / Find video": hasFoundVideo (ui.mochy.player) reads
//   the video-lookup cache + SongEdits, so the row reads "Wrong video?"
//   once a video was found for the song.
// - ModuleMenuRows call DROPPED: the file is skipped (modules system out of
//   scope, NEEDS.md §(c) D3). The skin-customise sheet block is kept so a
//   future caller can wire it.
// - Floating lyrics row: CW has no floating-lyrics engine; the switch stays
//   off and logs a Timber warning + TODO on tap (NEEDS.md §(b) F1).
// - Sleep timer row reads CW's SleepTimer (remainingMs / afterTrack).
// - AddToPlaylistDialog is the ALREADY-PORTED one in this package; the db
//   insert Mochy did before opening it is dropped (no db).
// - KeepScreenOn / PlayVideoBackgroundKey come from the staged
//   constants/PlayerMenuKeys.kt.

package com.music.bitchord.ui.mochy.player.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.music.bitchord.R
import com.music.bitchord.download.Downloads
import com.music.bitchord.playback.MusicLink
import com.music.bitchord.playback.SleepTimer
import com.music.bitchord.karaoke.KaraokeFeature
import com.music.bitchord.ui.mochy.component.AppSwitch
import com.music.bitchord.ui.mochy.component.RemoteFocus
import com.music.bitchord.ui.mochy.component.SheetActionCard
import com.music.bitchord.ui.mochy.component.SheetActionCardRow
import com.music.bitchord.ui.mochy.component.SheetDownloadCard
import com.music.bitchord.ui.mochy.component.SheetHeaderGap
import com.music.bitchord.ui.mochy.component.SheetIcon
import com.music.bitchord.ui.mochy.component.downloadStateFor
import com.music.bitchord.ui.mochy.component.toCwSong
import com.music.bitchord.ui.mochy.constants.KeepScreenOn
import com.music.bitchord.ui.mochy.constants.PlayVideoBackgroundKey
import com.music.bitchord.ui.mochy.menu.songedit.SongEditSheet
import com.music.bitchord.ui.mochy.menu.songedit.VideoPickerSheet
import com.music.bitchord.ui.mochy.models.MediaMetadata
import com.music.bitchord.ui.mochy.playback.LocalPlayerConnection
import com.music.bitchord.ui.mochy.player.hasFoundVideo
import com.music.bitchord.ui.mochy.player.models.TrackInfo
import com.music.bitchord.ui.mochy.utils.rememberPreference
import com.music.bitchord.ui.mochy.utils.resize
import timber.log.Timber

/**
 * @param fromLyrics true when the menu was opened from the lyrics page. The lyrics tools are only
 *   meaningful there — offering "edit lyrics" or "refetch lyrics" from the player, where there may
 *   not even be lyrics on screen, is an action with no visible consequence.
 */
@Composable
fun PlayerActionMenu(
    onDismiss: () -> Unit,
    onEditPlayer: () -> Unit,
    modifier: Modifier = Modifier,
    fromLyrics: Boolean = false,

    onReveal: ((Float) -> Unit)? = null,
    // turns the player into the full video mode; no row without it
    onFullVideo: (() -> Unit)? = null,
    // opens the equalizer page; no row without it
    onEqualizer: (() -> Unit)? = null,
    // opens a page of the app for a module's row (its own page, a search)
    // Kept for signature compatibility; unused while ModuleMenuRows is skipped.
    onOpenRoute: ((String) -> Unit)? = null,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // The song playing right now - what add to playlist, library and download act on.
    val currentMetadata by playerConnection.mediaMetadata.collectAsState()
    // Mochy read uiState.trackInfo; CW's connection has no uiState, so the
    // header is derived from the same metadata (same fields).
    val trackInfo = trackInfoFromMetadata(currentMetadata)
    val songId = currentMetadata?.id
    val downloadState = downloadStateFor(songId ?: "")
    val inLibrary = currentMetadata?.inLibrary != null
    // only a song autoplay picked is ours to stop recommending, a song you chose yourself stays as it is
    val queue by playerConnection.queueSnapshot.collectAsState()
    val isAutoplaySong = queue.items.getOrNull(queue.currentIndex)?.isAutoplay == true

    var showChoosePlaylistDialog by remember { mutableStateOf(false) }
    var showSleepTimer by remember { mutableStateOf(false) }
    var showKaraoke by remember { mutableStateOf(false) }
    var showAudioDevices by remember { mutableStateOf(false) }
    var showPlaybackSpeed by remember { mutableStateOf(false) }
    var showLyricsEditor by remember { mutableStateOf(false) }
    var showLyricsProvider by remember { mutableStateOf(false) }
    var showLyricsTranslation by remember { mutableStateOf(false) }
    var showLyricsOffset by remember { mutableStateOf(false) }
    var showSkinCustomise by remember { mutableStateOf(false) }
    // the song as it was when the row was tapped, so a skip doesn't swap the sheet's song under you
    var songToEdit by remember { mutableStateOf<MediaMetadata?>(null) }
    var songForVideo by remember { mutableStateOf<MediaMetadata?>(null) }
    val videoBackgroundOn by rememberPreference(PlayVideoBackgroundKey, defaultValue = false)

    // Shared by this menu and every sheet it opens, so they stack: the menu shrinks back and
    // peeks above "Change device output", a lyrics tool, etc. instead of being dimmed under it.
    val sheetStack = rememberMenuSheetStack()

    CompositionLocalProvider(LocalMenuSheetStack provides sheetStack) {
    MenuSheetSurface(
        onDismiss = onDismiss,
        modifier = modifier,
        revealProvider = onReveal,
    ) { _ ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp)
                    .padding(top = 16.dp, bottom = 24.dp)
            ) {

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(trackInfo.thumbnailUrl?.resize(240, 240))
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.08f)),
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = trackInfo.title,
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = buildString {
                                append("by ")
                                append(trackInfo.artist.ifBlank { "unknown" })
                                if (trackInfo.album.isNotBlank()) {
                                    append(" on ")
                                    append(trackInfo.album)
                                }
                            },
                            color = Color(0xFFB3B3B3),
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(SheetHeaderGap))

                // Opened from the lyrics page, the lyrics tools are what the menu is for, so they
                // come first and take the two big cards.
                if (fromLyrics) {
                    SheetActionCardRow {
                        SheetActionCard(
                            icon = R.drawable.translate,
                            title = "Ai translation",
                            // Greyed out until the AI translation settings are filled in — an
                            // enabled button that silently does nothing is worse than a disabled
                            // one that says so.
                            enabled = false,
                            onClick = {},
                            modifier = Modifier.weight(1f),
                        )
                        SheetActionCard(
                            icon = R.drawable.edit,
                            title = "Edit lyrics",
                            onClick = { showLyricsEditor = true },
                            modifier = Modifier.weight(1f),
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Refetch removed — picking a provider already re-fetches, so a separate
                    // button that re-ran the same query with unchanged settings had no visible
                    // outcome.
                    MenuRow(
                        icon = R.drawable.lyrics,
                        title = "Lyrics provider",
                        onClick = { showLyricsProvider = true },
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    MenuRow(
                        icon = R.drawable.fast_forward,
                        title = "Lyrics offset",
                        onClick = { showLyricsOffset = true },
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    MenuRow(
                        icon = R.drawable.translate,
                        title = "lyrics translation",
                        onClick = { showLyricsTranslation = true },
                    )

                    Spacer(modifier = Modifier.height(SectionGap))
                }

                // what people open this menu for most, as a two by two grid of big cards
                SheetActionCardRow {
                    // the song keeps playing and everything after it becomes songs like it, as spotify and apple music do; a file on the phone has no station
                    SheetActionCard(
                        icon = R.drawable.radio,
                        title = stringResource(R.string.start_radio),
                        enabled = currentMetadata != null && currentMetadata?.localUri == null,
                        onClick = {
                            playerConnection.startRadioSeamlessly()
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f),
                    )
                    SheetActionCard(
                        icon = R.drawable.playlist_add,
                        title = "Add to playlist",
                        enabled = currentMetadata != null,
                        onClick = { showChoosePlaylistDialog = true },
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                SheetActionCardRow {
                    SheetDownloadCard(
                        downloadState = downloadState,
                        onDownload = {
                            currentMetadata?.let { metadata ->
                                Downloads.enqueue(context, metadata.toCwSong(), from = "menu")
                            }
                        },
                        // Done, or on its way: tapping takes it back out.
                        onRemove = {
                            currentMetadata?.let { metadata ->
                                coroutineScope.removeDownload(context, metadata.id)
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                    // opens the song share over everything: a card, its lyrics, a video with the song, or the link
                    SheetActionCard(
                        icon = R.drawable.share,
                        title = stringResource(R.string.share),
                        enabled = currentMetadata != null,
                        onClick = {
                            currentMetadata?.let { metadata ->
                                onDismiss()
                                // Mochy's SongShare sheet is not ported; a plain
                                // share intent with the ChordWave song link.
                                context.shareText(MusicLink.songLink(metadata.id))
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                }

                if (onFullVideo != null) {
                    Spacer(modifier = Modifier.height(SectionGap))
                    MenuRow(
                        icon = R.drawable.fullscreen,
                        title = stringResource(R.string.video_mode),
                        onClick = {
                            onDismiss()
                            onFullVideo()
                        },
                    )
                }

                // with videos on, the song's video can be put right: "wrong video?" once one was found, "find video" when none was
                val videoSong = currentMetadata
                if (videoSong != null && (videoBackgroundOn || videoSong.isVideoSong)) {
                    val found = remember(videoSong.id) { hasFoundVideo(videoSong.id, videoSong.isVideoSong) }
                    Spacer(modifier = Modifier.height(if (onFullVideo != null) 8.dp else SectionGap))
                    MenuRow(
                        icon = R.drawable.slow_motion_video,
                        title = stringResource(if (found) R.string.video_wrong else R.string.video_find),
                        onClick = { songForVideo = videoSong },
                    )
                }

                Spacer(modifier = Modifier.height(SectionGap))

                DeviceVolumeRow()
                Spacer(modifier = Modifier.height(8.dp))
                MenuRow(
                    icon = R.drawable.cast,
                    title = "Change device output",
                    onClick = { showAudioDevices = true },
                )
                // a linked tv with mochy open: play there, line it up there, or work it as a remote
                DeviceMenuRows(onDismiss = onDismiss)
                Spacer(modifier = Modifier.height(8.dp))
                SleepTimerRow(onClick = { showSleepTimer = true })
                // Karaoke sits next to the sleep timer, as in Classic.
                // The row renders nothing while KaraokeFeature.ENABLED
                // is off, so the spacer is gated too — otherwise a
                // stray 8.dp gap would sit here while parked.
                if (KaraokeFeature.ENABLED) {
                    Spacer(modifier = Modifier.height(8.dp))
                    KaraokeMenuRow(onOpenKaraoke = { showKaraoke = true })
                }
                Spacer(modifier = Modifier.height(8.dp))
                KeepScreenOnRow()
                Spacer(modifier = Modifier.height(8.dp))
                FloatingLyricsRow()
                Spacer(modifier = Modifier.height(8.dp))
                MenuRow(
                    icon = R.drawable.speed,
                    title = "Playback speed and tempo",
                    onClick = { showPlaybackSpeed = true },
                )
                if (onEqualizer != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    MenuRow(
                        icon = R.drawable.equalizer,
                        title = stringResource(R.string.player_menu_equalizer),
                        onClick = {
                            onDismiss()
                            onEqualizer()
                        },
                    )
                }

                Spacer(modifier = Modifier.height(SectionGap))

                MenuRow(
                    icon = if (trackInfo.liked) R.drawable.ic_untitled_heart else R.drawable.ic_untitled_heart_unfill,
                    title = if (trackInfo.liked) "remove from liked" else "add to liked",
                    onClick = { playerConnection.toggleLike() },
                )
                Spacer(modifier = Modifier.height(8.dp))
                MenuRow(
                    icon = if (inLibrary) R.drawable.library_add_check else R.drawable.library_add,
                    title = if (inLibrary) "remove from library" else "add to library",
                    enabled = currentMetadata != null,
                    onClick = { playerConnection.toggleLibrary() },
                )
                Spacer(modifier = Modifier.height(8.dp))
                MenuRow(
                    icon = R.drawable.edit,
                    title = stringResource(R.string.song_edit_menu),
                    enabled = currentMetadata != null,
                    onClick = { songToEdit = currentMetadata },
                )
                if (isAutoplaySong) {
                    Spacer(modifier = Modifier.height(8.dp))
                    MenuRow(
                        icon = R.drawable.thumb_down,
                        title = stringResource(R.string.not_recommended_menu),
                        onClick = {
                            onDismiss()
                            playerConnection.stopRecommendingCurrent()
                        },
                    )
                }

                Spacer(modifier = Modifier.height(SectionGap))

                MenuRow(
                    icon = R.drawable.edit,
                    title = "edit player",
                    onClick = {
                        onDismiss()
                        onEditPlayer()
                    },
                )

                // ModuleMenuRows DROPPED: the modules system is out of scope
                // (NEEDS.md §(c) D3). Its customise row was the only thing
                // that opened SkinCustomiseSheet; that sheet stays staged
                // for a future caller.
            }
    }

    // Outside the menu surface, like the sheets below: the picker is a sheet of its own.
    currentMetadata?.let { metadata ->
        AddToPlaylistDialog(
            isVisible = showChoosePlaylistDialog,
            onGetSong = {
                // Mochy inserted the song into its db first; CW has no db,
                // and the ported dialog stores the song by id as-is.
                listOf(metadata.id)
            },
            onDismiss = { showChoosePlaylistDialog = false },
            // A playlist made from here starts with this song, so its cover takes the song's colours.
            topSongId = metadata.id,
            topSongArtworkUrl = metadata.thumbnailUrl,
        )
    }
    if (showSleepTimer) {
        SleepTimerSheet(onDismiss = { showSleepTimer = false })
    }
    if (showKaraoke) {
        KaraokeSheet(
            onDismiss = { showKaraoke = false },
            onOpenRemote = {
                showKaraoke = false
                onOpenRoute?.invoke("karaoke_remote")
            },
        )
    }
    if (showAudioDevices) {
        DeviceOutputSheet(onDismiss = { showAudioDevices = false })
    }
    if (showPlaybackSpeed) {
        PlaybackSpeedSheet(onDismiss = { showPlaybackSpeed = false })
    }
    if (showLyricsEditor) {
        LyricsEditorSheet(onDismiss = { showLyricsEditor = false })
    }
    if (showLyricsProvider) {
        LyricsProviderSheet(onDismiss = { showLyricsProvider = false })
    }
    if (showSkinCustomise) {
        SkinCustomiseSheet(onDismiss = { showSkinCustomise = false })
    }
    if (showLyricsOffset) {
        LyricsOffsetSheet(onDismiss = { showLyricsOffset = false })
    }
    if (showLyricsTranslation) {
        LyricsTranslationSheet(onDismiss = { showLyricsTranslation = false })
    }
    songToEdit?.let { song ->
        SongEditSheet(metadata = song, onDismiss = { songToEdit = null })
    }
    songForVideo?.let { song ->
        VideoPickerSheet(
            songId = song.id,
            title = song.title,
            artist = song.artists.joinToString { it.name },
            onDismiss = { songForVideo = null },
        )
    }
    }
}

/** Space between the menu's groups of rows. It used to be a line; the sheets don't draw lines any more. */
private val SectionGap = 16.dp

/** Mochy's uiState.trackInfo, derived from the playing metadata instead. */
private fun trackInfoFromMetadata(metadata: MediaMetadata?): TrackInfo = TrackInfo(
    mediaId = metadata?.id.orEmpty(),
    title = metadata?.title.orEmpty(),
    artist = metadata?.artists?.joinToString(", ") { it.name }.orEmpty(),
    album = metadata?.album?.title.orEmpty(),
    thumbnailUrl = metadata?.thumbnailUrl,
    liked = metadata?.liked == true,
)

@Composable
internal fun MenuRow(
    icon: Int,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
) {
    val contentAlpha = if (enabled) 1f else 0.35f
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MenuRowSurface)
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = RemoteFocus,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 14.dp)
    ) {
        SheetIcon(icon, tint = Color.White.copy(alpha = contentAlpha))
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = title,
            color = Color.White.copy(alpha = contentAlpha),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        trailing?.invoke()
    }
}

/** The phone doesn't sleep while Mochy is open: for following lyrics, or a phone on a car mount. */
@Composable
private fun KeepScreenOnRow() {
    val (keepScreenOn, setKeepScreenOn) = rememberPreference(KeepScreenOn, defaultValue = false)
    MenuRow(
        icon = R.drawable.lock_open,
        title = "Keep screen on",
        onClick = { setKeepScreenOn(!keepScreenOn) },
        trailing = { AppSwitch(checked = keepScreenOn, onCheckedChange = setKeepScreenOn) },
    )
}

// the line being sung floating over other apps once mochy is out of sight
@Composable
private fun FloatingLyricsRow() {
    // STUB (NEEDS.md §(b) F1): CW has no floating-lyrics engine. The switch
    // stays off and logs instead of promising an overlay that never appears.
    // Mochy read floating.rememberFloatingLyricsSwitch().
    MenuRow(
        icon = R.drawable.lyrics,
        title = stringResource(R.string.settings_floating_lyrics),
        onClick = {
            // TODO(phase3-adapter): floating lyrics engine (NEEDS.md §(b) F1).
            Timber.w("PlayerActionMenu: floating lyrics not available (no engine yet)")
        },
        trailing = {
            AppSwitch(
                checked = false,
                onCheckedChange = {
                    // TODO(phase3-adapter): floating lyrics engine (NEEDS.md §(b) F1).
                    Timber.w("PlayerActionMenu: floating lyrics not available (no engine yet)")
                },
            )
        },
    )
}

@Composable
private fun SleepTimerRow(onClick: () -> Unit) {
    val afterTrack by SleepTimer.afterTrack.collectAsState()
    val deadline by SleepTimer.deadline.collectAsState()

    var remainingLabel by remember { mutableStateOf("") }
    LaunchedEffect(deadline, afterTrack) {
        while (true) {
            remainingLabel = when {
                afterTrack -> "ends with song"
                deadline != null -> {
                    val left = SleepTimer.remainingMs() ?: 0L
                    if (left <= 0L) "" else formatRemaining(left)
                }
                else -> ""
            }
            if (remainingLabel.isEmpty() || afterTrack) break
            kotlinx.coroutines.delay(1000)
        }
    }

    MenuRow(
        icon = R.drawable.sleep_timer,
        title = "Sleep timer",
        onClick = onClick,
        trailing = {
            if (remainingLabel.isNotEmpty()) {
                Text(
                    text = "$remainingLabel remaining",
                    color = Color(0xFF9A9A9A),
                    fontSize = 12.sp,
                )
            }
        },
    )
}

internal fun formatRemaining(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return when {
        h > 0 -> "${h}h ${m}m"
        m > 0 -> "${m}m ${s}s"
        else -> "${s}s"
    }
}

@Composable
private fun DeviceVolumeRow() {
    val context = LocalContext.current
    val audioManager = remember {
        context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
    }
    val maxVolume = remember {
        audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    }
    var volume by remember {
        mutableStateOf(
            audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC).toFloat() / maxVolume
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MenuRowSurface)
            .padding(horizontal = 14.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SheetIcon(R.drawable.volume_up)
            Spacer(modifier = Modifier.width(14.dp))
            Text(
                text = android.os.Build.MODEL ?: "this device",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.volume_down),
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(16.dp),
            )
            LineSlider(
                value = volume,
                onValueChange = { next ->
                    volume = next
                    audioManager.setStreamVolume(
                        android.media.AudioManager.STREAM_MUSIC,
                        (next * maxVolume).toInt(),
                        0,
                    )
                },
                modifier = Modifier.weight(1f),
            )
            Icon(
                painter = painterResource(R.drawable.volume_up),
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(16.dp),
            )
        }
    }
}
