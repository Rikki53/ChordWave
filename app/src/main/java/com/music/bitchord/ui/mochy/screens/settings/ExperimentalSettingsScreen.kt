// Ported from Mochy Music (GPL-3.0): ui/screens/settings/ExperimentalSettingsScreen.kt
//
// ADAPTATIONS:
// - NavController -> MochyNavigator. "Cipher" opens settings/cipher and
//   "Playback diagnostics" opens settings/diagnostics (both ported in this
//   staging dir). "Import & sync" opens settings/import-sync — the route
//   the importsync port owns; confirm the string with that port at merge
//   (see NEEDS.md §(a) A6).
// - Flags with no ChordWave effect stay as pref toggles (the Mochy prefs
//   are stored, nothing reads them yet) and are listed honestly in
//   NEEDS.md §(b): stream backend, force playback, stream-source banner,
//   force monthly recap, music haptics + sensitivity + focus. Flags with a
//   real reader keep working: unlock landscape, force 1500 px YouTube
//   blur (read by MorphingCover), big disc cover styles, crossmix test
//   tools (this staging dir's CrossmixTestTools).
// - "Repeat initial setup" writes SetupCompletedKey=false through the
//   Mochy-mode DataStore, verbatim.
// - "Mochy card" rebranded to "ChordWave card" (user-visible).
// - Mochy's dead imports (Scaffold, TopAppBar, Icon, IconButton, scroll
//   helpers) dropped; the screen only ever used SubSettingsScaffold.

package com.music.bitchord.ui.mochy.screens.settings

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.datastore.preferences.core.toMutablePreferences
import com.music.bitchord.R
import com.music.bitchord.ui.mochy.component.AppSwitch
import com.music.bitchord.ui.mochy.component.SettingsGroup
import com.music.bitchord.ui.mochy.component.SettingsGroupStyle
import com.music.bitchord.ui.mochy.component.SettingsItem
import com.music.bitchord.ui.mochy.component.SubSettingsScaffold
import com.music.bitchord.ui.mochy.constants.CrossmixTestToolsKey
import com.music.bitchord.ui.mochy.constants.ForceMonthlyRecapKey
import com.music.bitchord.ui.mochy.constants.ForcePlaybackKey
import com.music.bitchord.ui.mochy.constants.ForceYtBackdropBlur1500Key
import com.music.bitchord.ui.mochy.constants.HapticFocus
import com.music.bitchord.ui.mochy.constants.HapticFocusKey
import com.music.bitchord.ui.mochy.constants.HapticSensitivity
import com.music.bitchord.ui.mochy.constants.LandscapeUnlockedKey
import com.music.bitchord.ui.mochy.constants.MusicHapticsEnabledKey
import com.music.bitchord.ui.mochy.constants.MusicHapticsSensitivityKey
import com.music.bitchord.ui.mochy.constants.SetupCompletedKey
import com.music.bitchord.ui.mochy.screens.setup.SetupRerun
import com.music.bitchord.ui.mochy.constants.ShowBigDiscStylesKey
import com.music.bitchord.ui.mochy.constants.ShowStreamSourceToastKey
import com.music.bitchord.ui.mochy.constants.StreamBackend
import com.music.bitchord.ui.mochy.constants.StreamBackendKey
import com.music.bitchord.ui.mochy.navigation.MochyNavigator
import com.music.bitchord.ui.mochy.utils.dataStore
import com.music.bitchord.ui.mochy.utils.rememberEnumPreference
import com.music.bitchord.ui.mochy.utils.rememberPreference
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExperimentalSettingsScreen(navigator: MochyNavigator) {
    val (musicHapticsEnabled, onMusicHapticsEnabledChange) = rememberPreference(MusicHapticsEnabledKey, defaultValue = false)
    val (musicHapticsSensitivity, onMusicHapticsSensitivityChange) = rememberEnumPreference(MusicHapticsSensitivityKey, defaultValue = HapticSensitivity.MEDIUM)
    val (hapticFocus, onHapticFocusChange) = rememberEnumPreference(HapticFocusKey, defaultValue = HapticFocus.VIBE)
    val (showBigDiscStyles, onShowBigDiscStylesChange) = rememberPreference(
        ShowBigDiscStylesKey,
        defaultValue = false,
    )
    val (forceRecap, onForceRecapChange) = rememberPreference(
        ForceMonthlyRecapKey,
        defaultValue = false,
    )
    val (forceYtBackdropBlur1500, onForceYtBackdropBlur1500Change) = rememberPreference(
        ForceYtBackdropBlur1500Key,
        defaultValue = false,
    )

    val (crossmixTestTools, onCrossmixTestToolsChange) = rememberPreference(
        CrossmixTestToolsKey,
        defaultValue = false,
    )
    val crossmixTools = crossmixTestItems(isVisible = crossmixTestTools)
    val (sourceBanner, onSourceBannerChange) = rememberPreference(ShowStreamSourceToastKey, defaultValue = false)
    val (forcePlayback, onForcePlaybackChange) = rememberPreference(ForcePlaybackKey, defaultValue = false)
    val (streamBackend, onStreamBackendChange) = rememberEnumPreference(
        StreamBackendKey,
        defaultValue = StreamBackend.INNERTUBEX,
    )
    val streamBackendTitle = stringResource(R.string.experimental_stream_backend)
    // off by default: the whole app stays upright until the wide layout is ready
    val (landscapeUnlocked, onLandscapeUnlockedChange) = rememberPreference(
        LandscapeUnlockedKey,
        defaultValue = false,
    )
    val unlockLandscapeTitle = stringResource(R.string.experimental_unlock_landscape)

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    SubSettingsScaffold(
        title = "Experimental",
        onBack = { navigator.popBackStack() },
    ) {
            SettingsGroup(
                style = SettingsGroupStyle.Grouped,
                items = buildList {
                    add(
                        SettingsItem(
                            title = { Text(unlockLandscapeTitle) },
                            highlightKey = unlockLandscapeTitle,
                            descriptionText = stringResource(R.string.experimental_unlock_landscape_desc),
                            detail = stringResource(R.string.experimental_unlock_landscape_detail),
                            icon = painterResource(R.drawable.screen_rotation),
                            iconShape = CircleShape,
                            onClick = { onLandscapeUnlockedChange(!landscapeUnlocked) },
                            trailingContent = {
                                AppSwitch(checked = landscapeUnlocked, onCheckedChange = onLandscapeUnlockedChange)
                            }
                        )
                    )
                    add(
                        SettingsItem(
                            title = { Text("Import & sync") },
                            highlightKey = "Import & sync",
                            descriptionText = "Bring playlists from other services, sync with YouTube, export a backup",
                            icon = painterResource(R.drawable.sync),
                            iconShape = CircleShape,
                            onClick = { navigator.navigate("import_sync") }
                        )
                    )
                    add(
                        SettingsItem(
                            title = { Text("Crossmix test tools") },
                            highlightKey = "Crossmix test tools",
                            descriptionText = "Live status and a jump-to-mix button",
                            detail = "Live status and a button that jumps to just before the next mix; also shown in Crossmix",
                            icon = painterResource(R.drawable.graphic_eq),
                            iconShape = CircleShape,
                            onClick = { onCrossmixTestToolsChange(!crossmixTestTools) },
                            trailingContent = {
                                AppSwitch(
                                    checked = crossmixTestTools,
                                    onCheckedChange = onCrossmixTestToolsChange,
                                )
                            }
                        )
                    )
                    crossmixTools.forEach { add(it.copy(isSubOption = true)) }
                    add(
                        SettingsItem(
                            title = { Text("Show where songs play from") },
                            highlightKey = "Show where songs play from",
                            descriptionText = "Banner showing each song's source and quality",
                            detail = "A banner at the top names each song's source: Apple Music, Monochrome or YouTube, and its quality",
                            icon = painterResource(R.drawable.info),
                            iconShape = CircleShape,
                            onClick = { onSourceBannerChange(!sourceBanner) },
                            trailingContent = {
                                AppSwitch(checked = sourceBanner, onCheckedChange = onSourceBannerChange)
                            }
                        )
                    )
                    add(
                        SettingsItem(
                            title = { Text(streamBackendTitle) },
                            highlightKey = streamBackendTitle,
                            description = {
                                Text(
                                    stringResource(
                                        when (streamBackend) {
                                            StreamBackend.INNERTUBEX -> R.string.experimental_stream_backend_innertubex
                                            StreamBackend.CLASSIC -> R.string.experimental_stream_backend_classic
                                        }
                                    )
                                )
                            },
                            detail = stringResource(R.string.experimental_stream_backend_detail),
                            icon = painterResource(R.drawable.key),
                            iconShape = CircleShape,
                            onClick = {
                                onStreamBackendChange(
                                    when (streamBackend) {
                                        StreamBackend.INNERTUBEX -> StreamBackend.CLASSIC
                                        StreamBackend.CLASSIC -> StreamBackend.INNERTUBEX
                                    }
                                )
                            }
                        )
                    )
                    add(
                        SettingsItem(
                            title = { Text("Force playback") },
                            highlightKey = "Force playback",
                            descriptionText = "If a provider fails, try the next one",
                            detail = "Never stop on a song: if one provider can't play it, the next in the provider order does; if none can, it's skipped. A banner says which",
                            icon = painterResource(R.drawable.play),
                            iconShape = CircleShape,
                            onClick = { onForcePlaybackChange(!forcePlayback) },
                            trailingContent = {
                                AppSwitch(checked = forcePlayback, onCheckedChange = onForcePlaybackChange)
                            }
                        )
                    )
                    add(
                        SettingsItem(
                            title = { Text("Force 1500 px YouTube blur") },
                            highlightKey = "Force 1500 px YouTube blur",
                            descriptionText = "Override the 120 px default; may increase GPU load",
                            icon = painterResource(R.drawable.tune),
                            iconShape = CircleShape,
                            onClick = { onForceYtBackdropBlur1500Change(!forceYtBackdropBlur1500) },
                            trailingContent = {
                                AppSwitch(
                                    checked = forceYtBackdropBlur1500,
                                    onCheckedChange = onForceYtBackdropBlur1500Change,
                                )
                            }
                        )
                    )
                    add(
                        SettingsItem(
                            title = { Text("Force show monthly recap") },
                            highlightKey = "Force show monthly recap",
                            descriptionText = "Unlock the recap early",
                            detail = "Unlock the recap behind the ChordWave card now, before the 3rd or 10 songs",
                            icon = painterResource(R.drawable.history),
                            iconShape = CircleShape,
                            onClick = { onForceRecapChange(!forceRecap) },
                            trailingContent = {
                                AppSwitch(
                                    checked = forceRecap,
                                    onCheckedChange = onForceRecapChange,
                                )
                            }
                        )
                    )
                    add(
                        SettingsItem(
                            title = { Text("Big disc cover styles") },
                            highlightKey = "Big disc cover styles",
                            descriptionText = "Unfinished — oversized discs that bleed off screen",
                            icon = painterResource(R.drawable.album),
                            iconShape = CircleShape,
                            onClick = { onShowBigDiscStylesChange(!showBigDiscStyles) },
                            trailingContent = {
                                AppSwitch(
                                    checked = showBigDiscStyles,
                                    onCheckedChange = onShowBigDiscStylesChange,
                                )
                            }
                        )
                    )
                    add(
                        SettingsItem(
                            title = { Text("Music haptics") },
                            highlightKey = "Music haptics",
                            descriptionText = "Vibrate to the beat — uses battery",
                            icon = painterResource(R.drawable.music_note),
                            iconShape = CircleShape,
                            onClick = { onMusicHapticsEnabledChange(!musicHapticsEnabled) },
                            trailingContent = {
                                AppSwitch(checked = musicHapticsEnabled, onCheckedChange = onMusicHapticsEnabledChange)
                            }
                        )
                    )
                    add(
                        SettingsItem(
                            title = { Text("Haptics sensitivity") },
                            description = {
                                Text(
                                    when (musicHapticsSensitivity) {
                                        HapticSensitivity.LOW -> "Low"
                                        HapticSensitivity.MEDIUM -> "Medium"
                                        HapticSensitivity.HIGH -> "High"
                                    }
                                )
                            },
                            icon = painterResource(R.drawable.sliders),
                            iconShape = CircleShape,
                            enabled = musicHapticsEnabled,
                            onClick = {
                                onMusicHapticsSensitivityChange(
                                    when (musicHapticsSensitivity) {
                                        HapticSensitivity.LOW -> HapticSensitivity.MEDIUM
                                        HapticSensitivity.MEDIUM -> HapticSensitivity.HIGH
                                        HapticSensitivity.HIGH -> HapticSensitivity.LOW
                                    }
                                )
                            }
                        )
                    )
                    add(
                        SettingsItem(
                            title = { Text("Haptic focus") },
                            description = {
                                Text(
                                    when (hapticFocus) {
                                        HapticFocus.BALANCE -> "Balance"
                                        HapticFocus.VOCAL -> "Focus on vocal"
                                        HapticFocus.VIBE -> "Focus on vibe"
                                        HapticFocus.BASS -> "Focus on bass"
                                    }
                                )
                            },
                            icon = painterResource(R.drawable.equalizer),
                            iconShape = CircleShape,
                            enabled = musicHapticsEnabled,
                            onClick = {
                                onHapticFocusChange(
                                    when (hapticFocus) {
                                        HapticFocus.BALANCE -> HapticFocus.VOCAL
                                        HapticFocus.VOCAL -> HapticFocus.VIBE
                                        HapticFocus.VIBE -> HapticFocus.BASS
                                        HapticFocus.BASS -> HapticFocus.BALANCE
                                    }
                                )
                            }
                        )
                    )
                    add(
                        SettingsItem(
                            title = { Text("Cipher") },
                            highlightKey = "Cipher",
                            descriptionText = "Player script status, countdown and refresh",
                            icon = painterResource(R.drawable.key),
                            iconShape = CircleShape,
                            onClick = { navigator.navigate("cipher_settings") }
                        )
                    )
                    add(
                        SettingsItem(
                            title = { Text("Playback diagnostics") },
                            highlightKey = "Playback diagnostics",
                            descriptionText = "Which source served the playing track, and the live stream snapshot",
                            icon = painterResource(R.drawable.info),
                            iconShape = CircleShape,
                            onClick = { navigator.navigate("playback_diagnostics") }
                        )
                    )
                    add(
                        SettingsItem(
                            title = { Text("Repeat initial setup") },
                            highlightKey = "Repeat initial setup",
                            descriptionText = "Re-run the setup wizard",
                            icon = painterResource(R.drawable.restore),
                            iconShape = CircleShape,
                            onClick = {
                                coroutineScope.launch {
                                    context.dataStore.updateData { prefs ->
                                        prefs.toMutablePreferences().apply {
                                            set(SetupCompletedKey, false)
                                        }
                                    }
                                    // The key alone cannot re-show the
                                    // wizard for an onboarded user (the
                                    // host hides it once classic
                                    // onboarding is done) — ask the host
                                    // explicitly.
                                    SetupRerun.request()
                                }
                            }
                        )
                    )
                }
            )
    }
}
