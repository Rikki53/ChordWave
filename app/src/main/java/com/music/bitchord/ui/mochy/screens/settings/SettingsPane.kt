// Ported from Mochy Music (GPL-3.0): ui/screens/settings/SettingsPane.kt
// Adapted to ChordWave's MochyNavigator (no NavHost/NavController): the pane
// helpers read the navigator's detail stack (plain route strings) instead of
// NavBackStackEntries, and the Settings tab route is "settings".
//
// Route scheme: the Phase 4 sub-screens use settings/* routes (see NEEDS.md);
// the Audio Lab / customisation / app-behaviour pages keep Mochy's verbatim
// route strings until their owners land.

package com.music.bitchord.ui.mochy.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.bitchord.R
import com.music.bitchord.ui.mochy.navigation.MochyNavigator
import com.music.bitchord.ui.mochy.theme.InterFontFamily

// the settings list's own route; on a wide screen it stays at the left while its pages open beside it
const val SettingsListRoute = "settings"

// the pages that open beside the list on a wide screen; anything else (an artist, the player's equalizer from home) takes the whole width
val SettingsPaneRoutes = setOf(
    SettingsListRoute,
    "audio_lab",
    "equalizer",
    "audio_fx",
    "audio_quality",
    "audio_pass",
    "crossmix_settings",
    "general_settings",
    "playback_settings",
    "now_playing_settings",
    "customisation_settings",
    "storage_settings",
    "integrations_settings",
    "proxy_settings",
    "cipher_settings",
    "experimental_settings",
    "playback_diagnostics",
    "import_sync",
    "settings/update",
    "import_progress",
    "sources",
    "listen_together",
)

// how wide the list is beside the open page: a phone's column on a phone on its side, a little more on a tablet
private fun settingsListWidth(windowWidth: Dp) = (windowWidth * 0.38f).coerceIn(300.dp, 400.dp)

// the list on a wide screen: the page open beside it, so its bubble stays lit
@Immutable
class SettingsPane(val selectedRoute: String?)

// the list stays at the left while the page in front is one of its own
// (Settings is no longer a tab since round 2 — it opens as a detail over
// any tab — so this is route-based, not tab-index-based)
fun showsSettingsPane(currentTab: Int, currentRoute: String?): Boolean =
    currentRoute in SettingsPaneRoutes

// the page the list opened, read off the stack so a page opened from inside it keeps its category lit
fun settingsPaneSelection(detailStack: List<String>): String? =
    detailStack.firstOrNull()

// a page from the list opens beside it in place of the one that was open, so back goes to the list rather than through every page tried
fun MochyNavigator.openSettingsPage(route: String) {
    clearDetails()
    navigate(route)
}

// the list at the left of a wide screen, a hairline between it and the page beside it
@Composable
fun SettingsListPane(navigator: MochyNavigator, selectedRoute: String?, modifier: Modifier = Modifier) {
    val windowWidth = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp
    Box(modifier = modifier.width(settingsListWidth(windowWidth)).fillMaxHeight()) {
        MochySettingsScreen(navigator = navigator, pane = SettingsPane(selectedRoute))
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(0.8.dp)
                .fillMaxHeight()
                .background(Color.White.copy(alpha = 0.1f)),
        )
    }
}

// the space beside the list before anything is picked
@Composable
fun SettingsPanePlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(horizontal = 40.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_chordwave_mark),
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.35f),
                modifier = Modifier.size(40.dp),
            )
            Text(
                text = stringResource(R.string.settings_pane_hint),
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp,
                color = Color.White.copy(alpha = 0.5f),
                textAlign = TextAlign.Center,
            )
        }
    }
}
