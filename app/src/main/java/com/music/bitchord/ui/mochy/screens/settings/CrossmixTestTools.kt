// Ported from Mochy Music (GPL-3.0): ui/screens/settings/CrossmixTestTools.kt
//
// ADAPTATIONS (ChordWave has no crossmix engine to test):
// - Mochy's status row reads playerConnection.crossmixStatus (a StateFlow
//   fed by its crossmix planner) and "Test on this song" calls
//   playerConnection.testCrossmix(). Neither exists on
//   MochyPlayerConnection, and ChordWave has no planner to report on.
//   The status row instead describes ChordWave's REAL transition state
//   (Automix on / plain crossfade N s / off) from AppSettings, and the
//   test row is DISABLED (enabled = false, no onClick) with a description
//   that says so. See NEEDS.md §(b) B3.
// - LocalPlayerConnection is only used to tell "no player connected" from
//   "connected but idle"; the connection itself is nullable-safe throughout.

package com.music.bitchord.ui.mochy.screens.settings

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.R
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.ui.mochy.component.SettingsItem
import com.music.bitchord.ui.mochy.playback.LocalPlayerConnection


@Composable
fun crossmixTestItems(isVisible: Boolean = true): List<SettingsItem> {
    val playerConnection = LocalPlayerConnection.current
    val smartFade by AppSettings.smartFadeEnabled.collectAsStateWithLifecycle()
    val crossfadeSeconds by AppSettings.crossfadeSeconds.collectAsStateWithLifecycle()
    val status = when {
        playerConnection == null -> "No player connected"
        smartFade -> "Automix is on — ChordWave plans each transition from tempo, energy and structure"
        crossfadeSeconds > 0 -> "Plain crossfade, ${crossfadeSeconds}s — no beat-matched mixing"
        else -> "Transitions are off"
    }
    return listOf(
        SettingsItem(
            title = { Text("Crossmix status") },
            descriptionText = status,
            icon = painterResource(R.drawable.info),
            iconShape = CircleShape,
            isVisible = isVisible,
        ),
        SettingsItem(
            title = { Text("Test on this song") },
            highlightKey = "Test on this song",
            descriptionText = "Not available — ChordWave has no test mix",
            detail = "Plans the mix into the next song, then jumps to a few seconds before it — but ChordWave has no crossmix engine to test",
            icon = painterResource(R.drawable.fast_forward),
            iconShape = CircleShape,
            enabled = false,
            isVisible = isVisible,
        ),
    )
}
