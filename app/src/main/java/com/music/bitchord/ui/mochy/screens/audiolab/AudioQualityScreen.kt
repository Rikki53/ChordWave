// audio quality: what streams on wi-fi and on mobile data, and what downloads keep
//
// Ported from Mochy Music (GPL-3.0): ui/screens/audiolab/AudioQualityScreen.kt,
// reworked for ChordWave's quality model (see NEEDS.md):
// - ChordWave has no provider modules, so the per-provider sections are
//   gone, with ProviderOrder (no provider reorder exists in ChordWave).
// - Streaming is two groups — on Wi-Fi and on mobile data — over CW's own
//   AudioQuality (Low/Medium/High/Lossless), wired to
//   AppSettings.setAudioQualityWifi/Cellular.
// - Downloads use CW's DownloadQuality (Standard/High/Lossless) via
//   AppSettings.setDownloadQuality.
// - QualityRow, ProviderLogo and Footnote are kept from Mochy's screen.
package com.music.bitchord.ui.mochy.screens.audiolab

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.R
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.AudioQuality
import com.music.bitchord.data.settings.DownloadQuality
import com.music.bitchord.ui.mochy.navigation.MochyNavigator
import com.music.bitchord.ui.mochy.theme.InterFontFamily

@Composable
fun AudioQualityScreen(navigator: MochyNavigator) {
    val wifiQuality by AppSettings.audioQualityWifi.collectAsStateWithLifecycle()
    val cellularQuality by AppSettings.audioQualityCellular.collectAsStateWithLifecycle()
    val downloadQuality by AppSettings.downloadQuality.collectAsStateWithLifecycle()

    AudioLabPage(
        title = LabTitle("Audio", "Quality"),
        onBack = { navigator.popBackStack() },
    ) {
        LabSheetTitle(stringResource(R.string.audiolab_quality_wifi))
        LabGroup {
            AudioQuality.entries.forEach { quality ->
                QualityRow(
                    title = quality.label,
                    description = stringResource(R.string.quality_hourly, quality.detail, quality.hourly),
                    selected = wifiQuality == quality,
                    enabled = true,
                    onClick = { AppSettings.setAudioQualityWifi(quality) },
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        LabSheetTitle(stringResource(R.string.audiolab_quality_cellular))
        LabGroup {
            AudioQuality.entries.forEach { quality ->
                QualityRow(
                    title = quality.label,
                    description = stringResource(R.string.quality_hourly, quality.detail, quality.hourly),
                    selected = cellularQuality == quality,
                    enabled = true,
                    onClick = { AppSettings.setAudioQualityCellular(quality) },
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        LabSheetTitle(stringResource(R.string.audiolab_quality_downloads))
        LabGroup {
            DownloadQuality.entries.forEach { quality ->
                QualityRow(
                    title = quality.label,
                    description = stringResource(R.string.quality_per_track, quality.detail, quality.perTrack),
                    selected = downloadQuality == quality,
                    enabled = true,
                    onClick = { AppSettings.setDownloadQuality(quality) },
                )
            }
        }
        Footnote(stringResource(R.string.quality_download_footnote))
    }
}

/**
 * One quality rung: name, what it costs, and a tick when it's the pick.
 * Mochy's single QualityRow, shared with AudioPassScreen's mode list —
 * the background, dimming and check all animate.
 */
@Composable
internal fun QualityRow(title: String, description: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(if (selected) Color(0xFF2A2A2E) else Color(0xFF111113), tween(200), label = "row")
    val alpha by animateFloatAsState(if (enabled) 1f else 0.35f, tween(250), label = "enabled")
    val check by animateFloatAsState(if (selected) 1f else 0f, spring(dampingRatio = 0.5f, stiffness = 500f), label = "check")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { this.alpha = alpha }
            .clip(RoundedCornerShape(18.dp))
            .background(bg)
            .border(1.dp, if (selected) Color.White.copy(alpha = 0.22f) else Color.Transparent, RoundedCornerShape(18.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontFamily = InterFontFamily, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
            Text(description, fontFamily = InterFontFamily, fontSize = 11.sp, lineHeight = 14.sp, color = LabColors.Secondary)
        }
        Spacer(Modifier.width(10.dp))
        Box(
            modifier = Modifier.size(22.dp).graphicsLayer { scaleX = check; scaleY = check; this.alpha = check },
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.check), null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
    }
}

/** the round provider logo, from the drawable or a monochrome tile as a fallback */
@Composable
internal fun ProviderLogo(logo: Painter?, provider: String, modifier: Modifier = Modifier) {
    if (logo != null) {
        Image(
            logo, contentDescription = provider, contentScale = ContentScale.Crop,
            modifier = modifier.size(28.dp).clip(CircleShape),
        )
    } else {
        Box(modifier.size(28.dp).clip(CircleShape).background(Color(0xFF2C2C2E)), contentAlignment = Alignment.Center) {
            Text(
                provider.firstOrNull()?.toString().orEmpty(),
                fontFamily = InterFontFamily, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White,
            )
        }
    }
}

/** a small grey note under a group */
@Composable
internal fun Footnote(text: String) {
    Text(
        text,
        fontFamily = InterFontFamily, fontSize = 12.sp, lineHeight = 16.sp, color = LabColors.Secondary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp),
    )
}

/** ChordWave streams from YouTube only, so this is the one provider logo */
@Composable
internal fun youtubeLogo(): Painter = painterResource(R.drawable.provider_youtube_music)
