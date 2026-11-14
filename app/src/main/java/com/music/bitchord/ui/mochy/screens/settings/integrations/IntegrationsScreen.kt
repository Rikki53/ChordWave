// settings → integrations: a row per service, always all of them, tapping one opens its own page
// Ported from Mochy Music (GPL-3.0). Navigation is MochyNavigator route strings; the YouTube
// Music row points at the import-sync route ("youtube_sync"), whose screen belongs to the
// settings/import-sync port — the route string is literal here so this file does not depend
// on that port's constant.

package com.music.bitchord.ui.mochy.screens.settings.integrations

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.music.bitchord.R
import com.music.bitchord.ui.mochy.component.SubSettingsScaffold
import com.music.bitchord.ui.mochy.navigation.MochyNavigator
import com.music.bitchord.ui.mochy.screens.settings.integrations.discord.DiscordRoute

const val IntegrationsRoute = "integrations_settings"

// the youtube music logo is a red disc, this fills its corners so it sits as a tile like the others
private val YouTubeMusicRed = Color(0xFFFF0000)

@Composable
fun IntegrationsScreen(navigator: MochyNavigator) {
    SubSettingsScaffold(
        title = stringResource(R.string.integrations),
        onBack = { navigator.popBackStack() },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            IntegrationRow(
                name = "Discord",
                description = stringResource(R.string.integration_discord_desc),
                icon = R.drawable.integration_discord,
                onClick = { navigator.navigate(DiscordRoute) },
            )
            IntegrationRow(
                name = ScrobbleSite.LASTFM.label,
                description = stringResource(R.string.integration_scrobbling_desc, ScrobbleSite.LASTFM.label),
                icon = R.drawable.integration_lastfm,
                onClick = { navigator.navigate(scrobbleServiceRoute(ScrobbleSite.LASTFM)) },
            )
            IntegrationRow(
                name = ScrobbleSite.LIBREFM.label,
                description = stringResource(R.string.integration_scrobbling_desc, ScrobbleSite.LIBREFM.label),
                icon = R.drawable.integration_librefm,
                onClick = { navigator.navigate(scrobbleServiceRoute(ScrobbleSite.LIBREFM)) },
            )
            IntegrationRow(
                name = stringResource(R.string.module_youtube_name),
                description = stringResource(R.string.module_youtube_desc),
                icon = R.drawable.provider_youtube_music,
                iconBackground = YouTubeMusicRed,
                onClick = { navigator.navigate("youtube_sync") },
            )
        }

        Spacer(Modifier.height(140.dp))
    }
}
