// Ported from Mochy Music (GPL-3.0): ui/screens/settings/CipherSettingsScreen.kt
//
// ADAPTATIONS (ChordWave has no cipher engine):
// - Mochy's screen reads/writes PlayerJsFetcher (its YouTube player-script
//   cache: status, countdown, refresh, clear). ChordWave's YouTube client
//   (InnerTubeX) manages the player script internally and exposes no cache
//   status, refresh or clear API (verified: no PlayerJs symbol anywhere in
//   the ChordWave tree). The UI is ported with the status card kept, the
//   values honestly blank, and both actions DISABLED (enabled = false, no
//   onClick) — never a fake working toggle. See NEEDS.md §(b) B1.
// - NavController -> MochyNavigator; onBack pops the detail stack.
// - The refresh rate limiter + countdown ticker are dropped with the
//   engine (nothing to rate-limit); the progress bar rests at 0.

package com.music.bitchord.ui.mochy.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.music.bitchord.R
import com.music.bitchord.ui.mochy.component.SettingsGroup
import com.music.bitchord.ui.mochy.component.SettingsGroupStyle
import com.music.bitchord.ui.mochy.component.SettingsItem
import com.music.bitchord.ui.mochy.component.SubSettingsScaffold
import com.music.bitchord.ui.mochy.navigation.MochyNavigator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CipherSettingsScreen(navigator: MochyNavigator) {
    SubSettingsScaffold(
        title = "Cipher",
        onBack = { navigator.popBackStack() },
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Managed by ChordWave",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )

                Spacer(Modifier.height(4.dp))

                Text(
                    text = "YouTube signs its stream URLs with a player script that changes " +
                        "periodically. ChordWave's YouTube client handles that script " +
                        "internally and does not expose its cache, so there is no status " +
                        "to show and nothing to refresh by hand.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(16.dp))

                CipherInfoRow("Player ID", "—")
                CipherInfoRow("Last updated", "—")
                CipherInfoRow("Script size", "—")
                CipherInfoRow("Next refresh", "Automatic")

                Spacer(Modifier.height(12.dp))

                LinearProgressIndicator(
                    progress = { 0f },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        SettingsGroup(
            style = SettingsGroupStyle.Grouped,
            items = listOf(
                SettingsItem(
                    title = { Text("Refresh now") },
                    descriptionText = "Not available — ChordWave refreshes the player script automatically",
                    icon = painterResource(R.drawable.refresh),
                    iconShape = CircleShape,
                    enabled = false,
                ),
                SettingsItem(
                    title = { Text("Clear cached script") },
                    descriptionText = "Not available in ChordWave",
                    icon = painterResource(R.drawable.restore),
                    iconShape = CircleShape,
                    enabled = false,
                ),
            ),
        )
    }
}

@Composable
private fun CipherInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 16.dp),
        )
    }
}
