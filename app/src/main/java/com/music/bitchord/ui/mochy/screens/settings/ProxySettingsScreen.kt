// Ported from Mochy Music (GPL-3.0): ui/screens/settings/ProxySettingsScreen.kt
//
// ADAPTATIONS (ChordWave has NO proxy engine — do not invent one):
// - Mochy's screen reads/writes AppProxy (address parsing, ProxyConfig,
//   connection check with ProxyRefusedException mapping). ChordWave has
//   no AppProxy equivalent (verified: the Phase 3 adapter round recorded
//   "no AppProxy (CW has no proxy)" as a deferred item, and no proxy
//   symbol exists in the ChordWave tree). The UI is ported with ALL
//   controls DISABLED (enabled = false, no onClick) plus a short note
//   saying proxy support isn't available yet. The stored prefs are still
//   read (so a future engine finds whatever was saved), but nothing here
//   writes them and the address/type/sign-in dialogs are dropped with
//   the engine — dead dialogs over a parser that doesn't exist would
//   only invite a fake working UI. See NEEDS.md §(b) B5.
// - NavController -> MochyNavigator; ProxySettingsRoute is the Modern
//   route "proxy_settings" (Mochy: "proxy_settings").
// - "Mochy" rebranded to "ChordWave" in the intro string.

package com.music.bitchord.ui.mochy.screens.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.music.bitchord.R
import com.music.bitchord.ui.mochy.component.AppSwitch
import com.music.bitchord.ui.mochy.component.SettingsGroup
import com.music.bitchord.ui.mochy.component.SettingsGroupStyle
import com.music.bitchord.ui.mochy.component.SettingsItem
import com.music.bitchord.ui.mochy.component.SubSettingsScaffold
import com.music.bitchord.ui.mochy.constants.ProxyEnabledKey
import com.music.bitchord.ui.mochy.constants.ProxyTypeKey
import com.music.bitchord.ui.mochy.constants.ProxyUrlKey
import com.music.bitchord.ui.mochy.constants.ProxyUsernameKey
import com.music.bitchord.ui.mochy.navigation.MochyNavigator
import com.music.bitchord.ui.mochy.utils.rememberPreference

// the route the app behaviour row opens
const val ProxySettingsRoute = "proxy_settings"

// settings > app behaviour > proxy: youtube through another server, for blocked networks and countries youtube music skips
@Composable
fun ProxySettingsScreen(navigator: MochyNavigator) {
    // read-only: nothing on this screen writes them until a proxy engine lands
    val (enabled) = rememberPreference(ProxyEnabledKey, defaultValue = false)
    val (address) = rememberPreference(ProxyUrlKey, defaultValue = "")
    val (typeName) = rememberPreference(ProxyTypeKey, defaultValue = "HTTP")
    val (username) = rememberPreference(ProxyUsernameKey, defaultValue = "")
    // ProxyPasswordKey is declared (see NEEDS.md) but unread: nothing here
    // ever shows a password, and nothing writes one until an engine lands.

    val typeLabel = stringResource(
        if (typeName == "SOCKS") R.string.proxy_type_socks else R.string.proxy_type_http
    )

    SubSettingsScaffold(
        title = stringResource(R.string.proxy),
        onBack = { navigator.popBackStack() },
    ) {
        Text(
            text = stringResource(R.string.proxy_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.6f),
            modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 14.dp),
        )

        Text(
            text = "Proxy support isn't available in ChordWave yet — these settings are shown for a future release and do nothing for now.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 14.dp),
        )

        SettingsGroup(
            style = SettingsGroupStyle.Grouped,
            items = listOf(
                SettingsItem(
                    title = { Text(stringResource(R.string.proxy_use)) },
                    descriptionText = stringResource(if (address.isEmpty()) R.string.proxy_use_needs_address else R.string.proxy_use_desc),
                    icon = painterResource(R.drawable.wifi_proxy),
                    iconShape = CircleShape,
                    enabled = false,
                    trailingContent = {
                        AppSwitch(checked = enabled && address.isNotEmpty(), onCheckedChange = null, enabled = false)
                    },
                ),
                SettingsItem(
                    title = { Text(stringResource(R.string.proxy_address)) },
                    descriptionText = address.ifEmpty { stringResource(R.string.proxy_address_not_set) },
                    icon = painterResource(R.drawable.link),
                    iconShape = CircleShape,
                    enabled = false,
                ),
                SettingsItem(
                    title = { Text(stringResource(R.string.proxy_type)) },
                    descriptionText = typeLabel,
                    icon = painterResource(R.drawable.network_node),
                    iconShape = CircleShape,
                    enabled = false,
                ),
                SettingsItem(
                    title = { Text(stringResource(R.string.proxy_login)) },
                    descriptionText = if (username.isEmpty()) stringResource(R.string.proxy_login_none) else stringResource(R.string.proxy_login_set, username),
                    icon = painterResource(R.drawable.key),
                    iconShape = CircleShape,
                    enabled = false,
                ),
            ),
        )

        Spacer(Modifier.height(18.dp))

        SettingsGroup(
            style = SettingsGroupStyle.Grouped,
            items = listOf(
                SettingsItem(
                    title = { Text(stringResource(R.string.proxy_check)) },
                    descriptionText = stringResource(R.string.proxy_check_desc),
                    icon = painterResource(R.drawable.network_update),
                    iconShape = CircleShape,
                    enabled = false,
                ),
            ),
        )

        Spacer(Modifier.height(140.dp))
    }
}
