package com.music.bitchord.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.SettingsRemote
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.R
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.ui.screens.RowDivider
import com.music.bitchord.ui.screens.SettingsGroup
import com.music.bitchord.ui.screens.SettingsRow
import kotlin.math.roundToInt

/**
 * The karaoke block: a mic switch and the vocal slider, in the settings
 * idiom, for whichever audio page hosts it.
 *
 * The switch and slider write straight to [AppSettings]; PlaybackService
 * observes those flows and hands them to the DSP chain, so this block is
 * presentation only. [onOpenRemote] is the TV karaoke remote's door — null
 * on surfaces where driving a TV makes no sense.
 */
@Composable
fun KaraokeSettingsGroup(
    modifier: Modifier = Modifier,
    topSpacing: androidx.compose.ui.unit.Dp = 26.dp,
    onOpenRemote: (() -> Unit)? = null,
) {
    val enabled by AppSettings.karaokeEnabled.collectAsStateWithLifecycle()
    val vocalLevel by AppSettings.karaokeVocalLevel.collectAsStateWithLifecycle()

    SettingsGroup(
        footer = stringResource(R.string.karaoke_footer),
        topSpacing = topSpacing,
    ) {
        SettingsRow(
            icon = Icons.Rounded.Mic,
            title = stringResource(R.string.karaoke),
            subtitle = stringResource(R.string.karaoke_subtitle),
            trailing = {
                IosSwitch(
                    checked = enabled,
                    onCheckedChange = { AppSettings.setKaraokeEnabled(it) },
                )
            },
            onClick = { AppSettings.setKaraokeEnabled(!enabled) },
        )
        if (enabled) {
            RowDivider()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.karaoke_vocal_volume),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "${(vocalLevel * 100).roundToInt()}%",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Slider(
                    value = vocalLevel,
                    onValueChange = { AppSettings.setKaraokeVocalLevel(it) },
                    valueRange = 0f..1f,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(2.dp))
            }
        }
        if (onOpenRemote != null) {
            RowDivider()
            SettingsRow(
                icon = Icons.Rounded.SettingsRemote,
                title = stringResource(R.string.karaoke_remote_title),
                subtitle = stringResource(R.string.karaoke_remote_subtitle),
                onClick = onOpenRemote,
            )
        }
    }
}
