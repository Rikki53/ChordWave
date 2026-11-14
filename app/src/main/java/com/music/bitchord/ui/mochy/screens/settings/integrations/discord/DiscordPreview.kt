// the top of the discord page: the switch and a preview of the status, built by
// buildCwPresenceActivity, which mirrors DiscordRPC.updateSong line for line so the preview
// shows what Discord actually receives.
// Ported from Mochy Music (GPL-3.0). Mochy's Presence* engine types are replaced by local
// Cw* types over CW's own Song and AppSettings; the status badge and spinning disc are gone
// (CW sends no small image and no rotating artwork), and the banner is a flat colour because
// CW stores no banner data.

package com.music.bitchord.ui.mochy.screens.settings.integrations.discord

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.music.bitchord.R
import com.music.bitchord.data.discord.DiscordRPC
import com.music.bitchord.data.model.ArtistRef
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.artworkAt
import com.music.bitchord.ui.mochy.component.AppSwitch
import com.music.bitchord.ui.screens.DiscordActivityKind
import java.util.Locale
import kotlinx.coroutines.delay

private val BannerHeight = 104.dp
private val AvatarSize = 72.dp
private val AvatarRing = 5.dp

/** what CW stores about the connected Discord account (AppSettings discordUsername/Name/Avatar) */
data class CwDiscordProfile(
    val displayName: String,
    val username: String,
    val avatarUrl: String?,
)

/** the track the preview renders, from the live player or from [sample] when nothing is playing */
data class CwNowPlaying(
    val song: Song,
    val positionMs: Long,
    val durationMs: Long,
    val isPlaying: Boolean,
    val speed: Float,
    /** the measured-quality line, or null for an ordinary lossy stream */
    val qualityLine: String?,
) {
    companion object {
        /** the stand-in track, so the page still shows a full card with the player idle */
        fun sample() = CwNowPlaying(
            song = Song(
                videoId = "sample",
                title = "Fake Plastic Trees",
                artist = "Radiohead",
                thumbnailUrl = null,
                durationText = "4:12",
                artists = listOf(ArtistRef("Radiohead")),
                albumName = "The Bends",
            ),
            positionMs = 64_000L,
            durationMs = 252_000L,
            isPlaying = true,
            speed = 1f,
            qualityLine = null,
        )
    }
}

/** the presence settings as the preview needs them, read off AppSettings by DiscordScreen */
data class CwPresenceSettings(
    val enabled: Boolean,
    val activityType: String,
    val activityName: String,
    val useDetails: Boolean,
    val showAudioQuality: Boolean,
    val button1Text: String,
    val button1Visible: Boolean,
    val button2Text: String,
    val button2Visible: Boolean,
)

/** one picture as discord shows it: the track artwork, or the app mark when there is none */
sealed interface CwPresencePicture {
    data class Web(val url: String) : CwPresencePicture
    data object Logo : CwPresencePicture
}

data class CwPresenceButton(val label: String)

/** the presence Discord renders, built the way DiscordRPC.updateSong builds it */
data class CwPresenceActivity(
    val name: String,
    val kind: DiscordActivityKind,
    val details: String?,
    val state: String?,
    val largeText: String?,
    val largeImage: CwPresencePicture?,
    val smallImage: CwPresencePicture?,
    val startMs: Long?,
    val endMs: Long?,
    val buttons: List<CwPresenceButton>,
)

/**
 * Mirrors [DiscordRPC.updateSong]: the name, the speed suffix on the title, the artwork rule,
 * the button labels (resolved variables, defaults when empty) and the start/end timestamps
 * with the playback speed divided out. One preview-only guard: with no known duration the
 * end timestamp is null, so the card counts up instead of counting down to a nonsense instant.
 */
fun buildCwPresenceActivity(
    track: CwNowPlaying,
    settings: CwPresenceSettings,
    nowMs: Long,
    appName: String,
): CwPresenceActivity {
    val song = track.song
    val speed = track.speed.takeIf { it > 0f } ?: 1f
    val details = if (speed != 1f) {
        "${song.title} [${String.format(Locale.ROOT, "%.2fx", speed)}]"
    } else {
        song.title
    }
    val buttons = buildList {
        if (settings.button1Visible) {
            add(CwPresenceButton(DiscordRPC.resolveVariables(settings.button1Text.ifEmpty { DiscordRPC.DEFAULT_BUTTON_1 }, song)))
        }
        if (settings.button2Visible) {
            add(CwPresenceButton(DiscordRPC.resolveVariables(settings.button2Text.ifEmpty { DiscordRPC.DEFAULT_BUTTON_2 }, song)))
        }
    }
    val adjustedPosition = (track.positionMs / speed).toLong()
    return CwPresenceActivity(
        name = settings.activityName.ifEmpty { appName },
        kind = DiscordActivityKind.entries.find { it.value == settings.activityType } ?: DiscordActivityKind.LISTENING,
        details = details,
        state = song.artist,
        largeText = track.qualityLine.takeIf { settings.showAudioQuality },
        largeImage = song.artworkAt(480)
            ?.takeIf { it.startsWith("http") }
            ?.let(CwPresencePicture::Web)
            ?: CwPresencePicture.Logo,
        smallImage = null,
        startMs = nowMs - adjustedPosition,
        endMs = if (track.durationMs > 0) {
            nowMs + ((track.durationMs - track.positionMs) / speed).toLong()
        } else {
            null
        },
        buttons = buttons,
    )
}

@Composable
internal fun DiscordToggleCard(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    status: String,
    enabled: Boolean,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(DiscordColors.ToggleCard)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Image(
            painter = painterResource(R.drawable.integration_discord),
            contentDescription = null,
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape),
        )
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        ) {
            Text("Enable rich presence", color = DiscordColors.Primary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(status, color = DiscordColors.Secondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
        AppSwitch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

/** the user's discord profile card, banner to bio, with the status underneath */
@Composable
internal fun DiscordProfileCard(
    profile: CwDiscordProfile?,
    track: CwNowPlaying,
    settings: CwPresenceSettings,
    appName: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(DiscordColors.Profile),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(BannerHeight + AvatarSize / 2),
        ) {
            Banner()
            Avatar(
                url = profile?.avatarUrl,
                modifier = Modifier
                    .padding(start = 22.dp)
                    .align(Alignment.BottomStart),
            )
        }
        Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 10.dp)) {
            Text(
                text = profile?.displayName?.takeIf { it.isNotBlank() } ?: "Not connected",
                color = DiscordColors.Primary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = profile?.username?.takeIf { it.isNotBlank() } ?: "Log in to show your profile here",
                color = DiscordColors.Secondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        DiscordActivityPreview(
            track = track,
            settings = settings,
            appName = appName,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 24.dp),
        )
    }
}

/** CW stores no banner, so the profile card's banner is always the flat mock colour */
@Composable
private fun Banner() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(BannerHeight)
            .background(DiscordColors.Banner),
    )
}

@Composable
private fun Avatar(url: String?, modifier: Modifier) {
    Box(
        modifier = modifier
            .size(AvatarSize)
            .clip(CircleShape)
            .background(DiscordColors.Banner)
            .padding(AvatarRing)
            .clip(CircleShape)
            .background(Color(0xFFD9D9D9)),
    ) {
        if (url != null) {
            AsyncImage(model = url, contentDescription = "Your Discord picture", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

/** the status card itself: listening to ..., the pictures, the lines, the bar and the buttons */
@Composable
internal fun DiscordActivityPreview(
    track: CwNowPlaying,
    settings: CwPresenceSettings,
    appName: String,
    modifier: Modifier = Modifier,
) {
    // a once-a-second clock moves the bar and the ring, like discord's own card
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(1_000)
            value = System.currentTimeMillis()
        }
    }
    val activity = remember(track, settings, now, appName) { buildCwPresenceActivity(track, settings, now, appName) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(DiscordColors.Activity)
            .padding(14.dp),
    ) {
        Text(
            text = activity.kind.verb + " " + activity.name,
            color = DiscordColors.Primary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Pictures(large = activity.largeImage, small = activity.smallImage)
            Column(
                Modifier
                    .weight(1f)
                    .padding(start = 14.dp),
            ) {
                activity.details?.let { Line(it, size = 16, weight = FontWeight.Bold, color = DiscordColors.Primary) }
                activity.state?.let { Line(it) }
                activity.largeText?.let { Line(it) }
                Times(activity, now)
            }
        }
        if (activity.buttons.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                activity.buttons.forEach { button ->
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(30.dp)
                            .clip(CircleShape)
                            .background(DiscordColors.Profile),
                    ) {
                        Text(button.label, color = DiscordColors.Primary, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun Line(text: String, size: Int = 11, weight: FontWeight = FontWeight.Normal, color: Color = DiscordColors.Secondary) {
    Text(text, color = color, fontSize = size.sp, fontWeight = weight, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/** listening shows a bar from start to end, playing counts up, a paused song shows neither */
@Composable
private fun Times(activity: CwPresenceActivity, now: Long) {
    val start = activity.startMs ?: return
    val mono = Modifier.padding(top = 6.dp)
    val end = activity.endMs
    if (end == null) {
        Text("${clock(now - start)} elapsed", color = DiscordColors.Secondary, fontSize = 10.sp, fontFamily = FontFamily.Monospace, modifier = mono)
        return
    }
    val total = (end - start).coerceAtLeast(1)
    val elapsed = (now - start).coerceIn(0, total)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = mono) {
        Text(clock(elapsed), color = DiscordColors.Secondary, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
        Box(
            Modifier
                .weight(1f)
                .padding(horizontal = 8.dp)
                .height(3.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.2f)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(elapsed.toFloat() / total)
                    .height(3.dp)
                    .background(Color.White),
            )
        }
        Text(clock(total), color = DiscordColors.Secondary, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
    }
}

private fun clock(ms: Long): String {
    val seconds = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(seconds / 60, seconds % 60)
}

/** the big picture with the small round one tucked into its corner, as discord lays them out */
@Composable
private fun Pictures(large: CwPresencePicture?, small: CwPresencePicture?) {
    Box(Modifier.size(66.dp)) {
        Box(
            Modifier
                .size(60.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(DiscordColors.Cover),
        ) {
            large?.let { PictureView(it) }
        }
        if (large != null && small != null) {
            Box(
                Modifier
                    .size(26.dp)
                    .align(Alignment.BottomEnd)
                    .offset(x = 2.dp, y = 2.dp)
                    .clip(CircleShape)
                    .background(DiscordColors.Activity)
                    .padding(3.dp)
                    .clip(CircleShape),
            ) {
                PictureView(small)
            }
        }
    }
}

/** one picture as discord shows it: the track artwork, or the ChordWave mark when there is none */
@Composable
private fun PictureView(picture: CwPresencePicture) {
    val fill = Modifier.fillMaxSize()
    when (picture) {
        is CwPresencePicture.Web -> AsyncImage(model = picture.url, contentDescription = null, contentScale = ContentScale.Crop, modifier = fill)
        CwPresencePicture.Logo -> Box(fill.background(Color(0xFF1A1A1A))) {
            Image(
                painter = painterResource(R.drawable.ic_chordwave_mark),
                contentDescription = "ChordWave",
                contentScale = ContentScale.Crop,
                modifier = fill.graphicsLayer {
                    scaleX = 1.5f
                    scaleY = 1.5f
                },
            )
        }
    }
}

/** the status again, floating at the bottom while the user edits further down the page */
@Composable
internal fun DiscordFloatingPreview(
    visible: Boolean,
    track: CwNowPlaying,
    settings: CwPresenceSettings,
    appName: String,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(220)) + slideInVertically(tween(260)) { it / 3 },
        exit = fadeOut(tween(160)) + slideOutVertically(tween(200)) { it / 3 },
        modifier = modifier,
    ) {
        DiscordActivityPreview(
            track = track,
            settings = settings,
            appName = appName,
            modifier = Modifier
                .shadow(elevation = 18.dp, shape = RoundedCornerShape(14.dp))
                .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(14.dp)),
        )
    }
}
