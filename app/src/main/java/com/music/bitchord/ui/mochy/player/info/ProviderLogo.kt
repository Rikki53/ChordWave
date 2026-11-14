// ProviderLogo.kt — ported from Mochy Music (GPL-3.0): ui/player/info/ProviderLogo.kt
//
// Adaptation from Mochy: SourceModules.YouTubeId (modules system, out of
// scope) is the staged YouTubeProviderId const; the "monochrome" id is kept
// as-is because the drawable ships with this chunk.

package com.music.bitchord.ui.mochy.player.info

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.music.bitchord.R
import com.music.bitchord.ui.mochy.importer.account.AccountService
import com.music.bitchord.ui.mochy.playback.PlayingFrom
import com.music.bitchord.ui.mochy.playback.YouTubeProviderId
import com.music.bitchord.ui.mochy.screens.setup.onboarding.ProviderSquircle
import com.music.bitchord.ui.mochy.screens.setup.onboarding.SquircleShape

/** a provider's own icon, the same squircle the onboarding uses; a letter for a module with none */
@Composable
internal fun ProviderLogo(providerId: String, name: String, size: Dp) {
    when (providerId) {
        YouTubeProviderId -> ProviderSquircle(service = AccountService.YOUTUBE_MUSIC, size = size)
        "applemusic" -> ProviderSquircle(service = AccountService.APPLE_MUSIC, size = size)
        "monochrome" -> Image(
            painter = painterResource(R.drawable.provider_monochrome),
            contentDescription = name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(size).clip(SquircleShape),
        )
        PlayingFrom.Device, PlayingFrom.Downloads -> Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(size).clip(SquircleShape).background(Color.White.copy(alpha = 0.9f)),
        ) {
            Icon(
                imageVector = if (providerId == PlayingFrom.Device) Icons.Rounded.Smartphone else Icons.Rounded.DownloadDone,
                contentDescription = name,
                tint = Color.Black.copy(alpha = 0.75f),
                modifier = Modifier.size(size * 0.5f),
            )
        }
        else -> Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(size).clip(SquircleShape).background(Color.White.copy(alpha = 0.16f)),
        ) {
            Text(text = name.take(1).uppercase(), color = Color.White, fontSize = (size.value * 0.42f).sp, fontWeight = FontWeight.Bold)
        }
    }
}
