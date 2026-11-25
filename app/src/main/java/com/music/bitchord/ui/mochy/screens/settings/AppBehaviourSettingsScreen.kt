// Ported from Mochy Music (GPL-3.0): ui/screens/settings/AppBehaviourSettingsScreen.kt
// Adapted to ChordWave's MochyNavigator (no NavController) and the Phase 4
// settings/* route scheme (proxy -> settings/proxy; Audio Lab / customisation
// pages keep Mochy's verbatim routes until their owners land).
//
// Batch 43 (paritas Mochy): halaman ini sekarang hanya berisi App behaviour
// persis Mochy (General / Network & data / Library & privacy / Lyrics /
// Related). Baris Playback pindah ke PlaybackSettingsScreen dan baris
// Now playing pindah ke NowPlayingSettingsScreen, sama seperti Mochy.
// Kunci preferensi tidak berubah — hanya halamannya yang dipisah.
//
// Deviations from Mochy (all documented in NEEDS.md):
// - "Disable App Updates notice" only persists HideUpdateNoticeKey: ChordWave's
//   AppUpdateChecker has no notifications setting of its own, so there is no
//   saveUpdateNotificationsSetting equivalent; the settings/update worker reads
//   the pref.
// - "Override lyrics sorting": ChordWave always tries providers in the
//   listener's order (there is no separate override switch like Mochy's), so
//   the row opens the sheet that arranges the order instead of a switch.
// - Missing Mochy pref keys are referenced as if present in
//   ui.mochy.constants (see NEEDS.md — the coordinator adds them).

package com.music.bitchord.ui.mochy.screens.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DataSaverOn
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.bitchord.R
import com.music.bitchord.data.canvas.CanvasOrder
import com.music.bitchord.data.canvas.CanvasSearchSource
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.playback.FloatingLyricsService
import com.music.bitchord.ui.mochy.component.AppSwitch
import com.music.bitchord.ui.mochy.component.DefaultPlayerSeekBarSlider
import com.music.bitchord.ui.mochy.component.LocalZoomOutOverlayState
import com.music.bitchord.ui.mochy.component.SettingsGroup
import com.music.bitchord.ui.mochy.component.SettingsGroupStyle
import com.music.bitchord.ui.mochy.component.SettingsHighlight
import com.music.bitchord.ui.mochy.component.SettingsItem
import com.music.bitchord.ui.mochy.component.SubSettingsScaffold
import com.music.bitchord.ui.mochy.component.SubSettingsSearchBar
import com.music.bitchord.ui.mochy.component.TopToaster
import com.music.bitchord.ui.mochy.constants.AlbumsFromLikedSongsKey
import com.music.bitchord.ui.mochy.constants.CanvasThumbnailAnimationKey
import com.music.bitchord.ui.mochy.constants.CanvasWifiOnlyKey
import com.music.bitchord.ui.mochy.constants.DataSaverKey
import com.music.bitchord.ui.mochy.constants.DataSaverMobileOnlyKey
import com.music.bitchord.ui.mochy.constants.DisableVideoAutoplayKey
import com.music.bitchord.ui.mochy.constants.DontSkipOnFailureKey
import com.music.bitchord.ui.mochy.constants.HideSettingsDescriptionsKey
import com.music.bitchord.ui.mochy.constants.HideUpdateNoticeKey
import com.music.bitchord.ui.mochy.constants.LarpLevelKey
import com.music.bitchord.ui.mochy.constants.LarpModeKey
import com.music.bitchord.ui.mochy.constants.LocalSongAutoMetadataKey
import com.music.bitchord.ui.mochy.constants.LyricsHighBloomKey
import com.music.bitchord.ui.mochy.constants.LyricsSoftLightKey
import com.music.bitchord.ui.mochy.constants.LyricsMotionStyle
import com.music.bitchord.ui.mochy.constants.LyricsMotionStyleKey
import com.music.bitchord.ui.mochy.constants.LyricsRomanizeAsMainKey
import com.music.bitchord.ui.mochy.constants.MixWithOtherAppsKey
import com.music.bitchord.ui.mochy.constants.OfflineModeKey
import com.music.bitchord.ui.mochy.constants.PauseOnMute
import com.music.bitchord.ui.mochy.constants.PauseSearchHistoryKey
import com.music.bitchord.ui.mochy.constants.PersistentQueueKey
import com.music.bitchord.ui.mochy.constants.PersistentShuffleAcrossQueuesKey
import com.music.bitchord.ui.mochy.constants.PlayVideoBackgroundKey
import com.music.bitchord.ui.mochy.constants.ProxyEnabledKey
import com.music.bitchord.ui.mochy.constants.ProxyUrlKey
import com.music.bitchord.ui.mochy.constants.ResumeOnBluetoothConnectKey
import com.music.bitchord.ui.mochy.constants.SYSTEM_DEFAULT
import com.music.bitchord.ui.mochy.constants.ShowFormatBadgeKey
import com.music.bitchord.ui.mochy.constants.ShowFormatSampleRateKey
import com.music.bitchord.ui.mochy.constants.SkipSilenceKey
import com.music.bitchord.ui.mochy.constants.StopPlaybackOnTaskRemovedKey
import com.music.bitchord.ui.mochy.constants.StreamFailureTimeoutKey
import com.music.bitchord.ui.mochy.constants.StudioForVideosKey
import com.music.bitchord.ui.mochy.constants.SyncLyricsToVideoKey
import com.music.bitchord.ui.mochy.constants.VideoQuality
import com.music.bitchord.ui.mochy.constants.VideoQualityKey
import com.music.bitchord.ui.mochy.constants.YouTubeCaptionTranslationKey
import com.music.bitchord.ui.mochy.constants.YtReportPlaysKey
import com.music.bitchord.ui.mochy.constants.YtVideoBackgroundLyricsSyncKey
import com.music.bitchord.ui.mochy.i18n.AppLanguage
import com.music.bitchord.ui.mochy.i18n.activityOrNull
import com.music.bitchord.ui.mochy.menu.songedit.LabSelect
import com.music.bitchord.ui.mochy.navigation.MochyNavigator
import com.music.bitchord.ui.mochy.player.SoftLightSupported
import com.music.bitchord.ui.mochy.player.LarpLevel
import com.music.bitchord.ui.mochy.theme.InterFontFamily
import com.music.bitchord.ui.mochy.utils.AppProxy
import com.music.bitchord.ui.mochy.utils.rememberEnumPreference
import com.music.bitchord.ui.mochy.utils.rememberPreference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import timber.log.Timber

// settings > app behaviour: the app itself, its network and your library; playback and the player's looks have their own pages now
@Composable
fun AppBehaviourSettingsScreen(navigator: MochyNavigator) {
    var query by remember { mutableStateOf("") }

    SubSettingsScaffold(
        title = stringResource(R.string.settings_app_behaviour),
        onBack = { navigator.popBackStack() },
        searchBar = { SubSettingsSearchBar(query = query, onQueryChange = { query = it }) },
    ) {
        // larpaspriority
        Section(title = null, items = larpItems().matching(query))
        Section(stringResource(R.string.general_app_settings), generalItems().matching(query))
        Section(stringResource(R.string.settings_section_network), networkItems(navigator).matching(query))
        Section(stringResource(R.string.settings_section_library_privacy), libraryItems().matching(query))
        Section(stringResource(R.string.settings_section_lyrics), lyricsItems().matching(query))
        Section(stringResource(R.string.settings_related), relatedItems(navigator).matching(query))
        Spacer(Modifier.height(140.dp))
    }
}

// a section's title (if it has one) and its grouped rows, left out whole when the search hides every row
@Composable
internal fun Section(title: String?, items: List<SettingsItem>) {
    if (items.isEmpty()) return
    if (title != null) {
        Text(
            text = title,
            fontFamily = InterFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = Color.White,
            modifier = Modifier.padding(bottom = 10.dp, start = 4.dp),
        )
    }
    SettingsGroup(style = SettingsGroupStyle.Grouped, items = items)
    Spacer(Modifier.height(18.dp))
}

internal fun List<SettingsItem>.matching(query: String): List<SettingsItem> {
    if (query.isBlank()) return this
    return filter {
        it.highlightKey?.contains(query, ignoreCase = true) == true ||
            it.searchText?.contains(query, ignoreCase = true) == true ||
            it.descriptionText?.contains(query, ignoreCase = true) == true ||
            it.detail?.contains(query, ignoreCase = true) == true
    }
}

// there's no limit to the larp
@Composable
private fun larpItems(): List<SettingsItem> {
    val (larping, setLarping) = rememberPreference(LarpModeKey, defaultValue = false)
    val (level, setLevel) = rememberEnumPreference(LarpLevelKey, defaultValue = LarpLevel.MAXXING)
    val overlay = LocalZoomOutOverlayState.current
    // the design's plain circle, nothing drawn in it
    val blank = remember { ColorPainter(Color.Transparent) }
    val openSheet = {
        overlay.showLarpSheet(current = level) { picked ->
            setLevel(picked)
            setLarping(true)
        }
    }
    val title = stringResource(R.string.larp_mode)
    return listOf(
        // the row opens the sheet to change level too, the switch only turns it off without asking
        SettingsItem(
            title = { Text(title) },
            highlightKey = "Larp mode",
            searchText = title,
            descriptionText = if (larping) stringResource(level.title) else stringResource(R.string.larp_mode_desc),
            detail = stringResource(R.string.larp_mode_detail),
            icon = blank,
            iconShape = CircleShape,
            onClick = openSheet,
            trailingContent = {
                AppSwitch(checked = larping, onCheckedChange = { on -> if (on) openSheet() else setLarping(false) })
            },
        ),
    )
}

@Composable
private fun generalItems(): List<SettingsItem> {
    val (noticeHidden, onNoticeHidden) = rememberPreference(HideUpdateNoticeKey, defaultValue = false)
    val (hideDescriptions, onHideDescriptions) = rememberPreference(HideSettingsDescriptionsKey, defaultValue = true)
    // ChordWave's AppUpdateChecker has no notifications setting of its own, so
    // the switch only persists the Mochy pref; the settings/update worker reads
    // HideUpdateNoticeKey when it wires the update notice.
    val setNoticeHidden = { hidden: Boolean ->
        onNoticeHidden(hidden)
    }
    return listOf(
        languageItem(),
        switchItem(
            key = "Disable App Updates notice",
            title = stringResource(R.string.settings_updates_notice),
            description = stringResource(R.string.settings_updates_notice_desc),
            icon = painterResource(R.drawable.update),
            checked = noticeHidden,
            onCheckedChange = setNoticeHidden,
        ),
        switchItem(
            key = "Disable description on settings",
            title = stringResource(R.string.settings_hide_descriptions),
            description = stringResource(R.string.settings_hide_descriptions_desc),
            icon = painterResource(R.drawable.info),
            checked = hideDescriptions,
            onCheckedChange = onHideDescriptions,
        ),
    )
}

// the app's own language, apart from the phone's; android 13+ keeps it in the system's per-app setting too
@Composable
private fun languageItem(): SettingsItem {
    val overlay = LocalZoomOutOverlayState.current
    val context = LocalContext.current
    val activity = remember(context) { context.activityOrNull() }
    val language = remember(context) { AppLanguage.selection(context) }
    val languageName = if (language == SYSTEM_DEFAULT) stringResource(R.string.system_default) else appLanguageNativeName(language)
    val failure = stringResource(R.string.app_language_update_failed)
    val scope = rememberCoroutineScope()
    val pick = { tag: String ->
        if (tag != language && activity != null) {
            scope.launch {
                try {
                    AppLanguage.select(activity, tag)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    Toast.makeText(context, failure, Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    return SettingsItem(
        title = { Text(stringResource(R.string.app_language)) },
        highlightKey = "App language",
        descriptionText = languageName,
        searchText = stringResource(R.string.app_language),
        icon = painterResource(R.drawable.translate),
        iconShape = CircleShape,
        enabled = activity != null,
        onClick = { overlay.showAppLanguageSheet(selected = language, onSelect = { pick(it) }) },
    )
}

@Composable
private fun networkItems(navigator: MochyNavigator): List<SettingsItem> {
    val (offlineMode, onOfflineModeChange) = rememberPreference(OfflineModeKey, defaultValue = false)
    val (dataSaver, onDataSaverChange) = rememberPreference(DataSaverKey, defaultValue = false)
    val (dataSaverMobileOnly, onDataSaverMobileOnlyChange) = rememberPreference(DataSaverMobileOnlyKey, defaultValue = true)
    val (proxyOn) = rememberPreference(ProxyEnabledKey, defaultValue = false)
    val (proxyAddress) = rememberPreference(ProxyUrlKey, defaultValue = "")
    return listOf(
        switchItem(
            key = "Offline mode",
            title = stringResource(R.string.settings_offline_mode),
            description = stringResource(R.string.settings_offline_mode_desc),
            icon = painterResource(R.drawable.download),
            checked = offlineMode,
            onCheckedChange = onOfflineModeChange,
        ),
        // lighter streams and still covers, for metered plans; the second switch decides whether wi-fi counts too
        switchItem(
            key = "Data saver",
            title = stringResource(R.string.settings_data_saver),
            description = stringResource(R.string.settings_data_saver_desc),
            detail = stringResource(R.string.settings_data_saver_detail),
            icon = rememberVectorPainter(Icons.Rounded.DataSaverOn),
            checked = dataSaver,
            onCheckedChange = onDataSaverChange,
        ),
        switchItem(
            key = "Data saver only on mobile data",
            title = stringResource(R.string.settings_data_saver_mobile),
            description = stringResource(R.string.settings_data_saver_mobile_desc),
            icon = rememberVectorPainter(Icons.Rounded.SignalCellularAlt),
            checked = dataSaverMobileOnly,
            onCheckedChange = onDataSaverMobileOnlyChange,
            subOptionOf = dataSaver,
        ),
        SettingsItem(
            title = { Text(stringResource(R.string.proxy)) },
            highlightKey = "Proxy",
            descriptionText = if (proxyOn && AppProxy.parseAddress(proxyAddress) != null) {
                stringResource(R.string.proxy_status_on, proxyAddress)
            } else {
                stringResource(R.string.proxy_status_off)
            },
            searchText = stringResource(R.string.proxy),
            icon = painterResource(R.drawable.wifi_proxy),
            iconShape = CircleShape,
            onClick = { navigator.navigate("proxy_settings") },
            trailingContent = {
                Icon(
                    painter = painterResource(R.drawable.navigate_next),
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(20.dp),
                )
            },
        ),
    )
}

@Composable
private fun libraryItems(): List<SettingsItem> {
    val (albumsFromLikes, onAlbumsFromLikesChange) = rememberPreference(AlbumsFromLikedSongsKey, defaultValue = false)
    val (autoMetadata, onAutoMetadataChange) = rememberPreference(LocalSongAutoMetadataKey, defaultValue = true)
    val (pauseSearchHistory, onPauseSearchHistoryChange) = rememberPreference(PauseSearchHistoryKey, defaultValue = false)
    val (reportPlays, onReportPlaysChange) = rememberPreference(YtReportPlaysKey, defaultValue = true)
    return listOf(
        switchItem(
            key = "Albums from liked songs",
            title = stringResource(R.string.settings_albums_from_likes),
            description = stringResource(R.string.settings_albums_from_likes_desc),
            detail = stringResource(R.string.settings_albums_from_likes_detail),
            icon = painterResource(R.drawable.album),
            checked = albumsFromLikes,
            onCheckedChange = onAlbumsFromLikesChange,
        ),
        switchItem(
            key = "Local song auto metadata",
            title = stringResource(R.string.settings_local_auto_metadata),
            description = stringResource(R.string.settings_local_auto_metadata_desc),
            icon = painterResource(R.drawable.cached),
            checked = autoMetadata,
            onCheckedChange = onAutoMetadataChange,
        ),
        switchItem(
            key = "Pause search history",
            title = stringResource(R.string.pause_search_history),
            description = stringResource(R.string.settings_pause_search_history_desc),
            icon = painterResource(R.drawable.history),
            checked = pauseSearchHistory,
            onCheckedChange = onPauseSearchHistoryChange,
        ),
        // the same switch as sync's youtube history one, signed out it still counts the play
        switchItem(
            key = "Count plays on YouTube views",
            title = stringResource(R.string.settings_youtube_plays),
            description = stringResource(R.string.settings_youtube_plays_desc),
            detail = stringResource(R.string.settings_youtube_plays_detail),
            icon = painterResource(R.drawable.trending_up),
            checked = reportPlays,
            onCheckedChange = onReportPlaysChange,
        ),
    )
}

@Composable
private fun lyricsItems(): List<SettingsItem> {
    val overlay = LocalZoomOutOverlayState.current
    return listOf(
        // ChordWave always tries providers in the listener's order (there is no
        // separate "override" switch like Mochy's — the order IS the lookup
        // sequence, with word-synced answers winning over plain ones within
        // it), so the row just opens the sheet that arranges it.
        SettingsItem(
            title = { Text(stringResource(R.string.settings_lyrics_order_override)) },
            highlightKey = "Override lyrics sorting",
            searchText = stringResource(R.string.settings_lyrics_order_override),
            descriptionText = stringResource(R.string.settings_lyrics_order_override_desc),
            detail = stringResource(R.string.settings_lyrics_order_override_detail),
            icon = painterResource(R.drawable.lyrics),
            iconShape = CircleShape,
            onClick = { overlay.showLyricsProviderSortSheet() },
            trailingContent = {
                Icon(
                    painter = painterResource(R.drawable.navigate_next),
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(20.dp),
                )
            },
        ),
    )
}

// most of what used to sit here moved to its own page, these keep it a tap away for anyone who still looks here
@Composable
private fun relatedItems(navigator: MochyNavigator): List<SettingsItem> = listOf(
    linkItem(
        key = "Playback page",
        title = stringResource(R.string.settings_playback_category),
        description = stringResource(R.string.settings_playback_link_desc),
        icon = painterResource(R.drawable.play),
        onClick = { navigator.navigate(PlaybackSettingsRoute) },
    ),
    linkItem(
        key = "Now playing page",
        title = stringResource(R.string.settings_now_playing),
        description = stringResource(R.string.settings_now_playing_link_desc),
        icon = painterResource(R.drawable.lyrics),
        onClick = { navigator.navigate(NowPlayingSettingsRoute) },
    ),
)

// what customize answers to when a search or the related row asks for its top bar card
const val TopBarHighlightKey = "Top Bar on player"

// the same for its bottom bar card
const val BottomBarHighlightKey = "Bottom Bar on player"

// a row title with its value on the right, for the rows with a slider under them
@Composable
internal fun ValueTitle(title: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title)
        Text(
            text = value,
            fontFamily = InterFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = Color.White.copy(alpha = 0.70f),
        )
    }
}

// one switch row: key is the english name search results point at, subOptionOf tucks it under a parent that's on
internal fun switchItem(
    key: String,
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.painter.Painter,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    detail: String? = null,
    subOptionOf: Boolean? = null,
    visible: Boolean = true,
) = SettingsItem(
    title = { Text(title) },
    highlightKey = key,
    searchText = title,
    descriptionText = description,
    detail = detail,
    icon = icon,
    iconShape = CircleShape,
    isVisible = visible && subOptionOf != false,
    isSubOption = subOptionOf != null,
    onClick = { onCheckedChange(!checked) },
    trailingContent = {
        AppSwitch(checked = checked, onCheckedChange = onCheckedChange, enabled = subOptionOf != false)
    },
)

// one link row: opens another page, the chevron says so
internal fun linkItem(
    key: String,
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.painter.Painter,
    onClick: () -> Unit,
    visible: Boolean = true,
    isSubOption: Boolean = false,
) = SettingsItem(
    title = { Text(title) },
    highlightKey = key,
    searchText = title,
    descriptionText = description,
    icon = icon,
    iconShape = CircleShape,
    isVisible = visible,
    isSubOption = isSubOption,
    onClick = onClick,
    trailingContent = {
        Icon(
            painter = painterResource(R.drawable.navigate_next),
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.5f),
            modifier = Modifier.size(20.dp),
        )
    },
)

// The floating-lyrics bubble (batch 32): a real switch over
// [FloatingLyricsService]. Turning it on needs the "display over other apps"
// grant, which a sideloaded app never gets silently — the switch sends the
// listener to the system screen and re-checks on the way back.
@Composable
internal fun rememberFloatingLyricsSwitch(): Pair<Boolean, (Boolean) -> Unit> {
    val context = LocalContext.current
    val enabled by AppSettings.floatingLyricsEnabled.collectAsState()
    val permissionNeeded = stringResource(R.string.settings_floating_lyrics_permission_needed)
    var pendingEnable by remember { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (pendingEnable) {
            pendingEnable = false
            if (Settings.canDrawOverlays(context)) {
                AppSettings.setFloatingLyricsEnabled(true)
                FloatingLyricsService.start(context)
            } else {
                TopToaster.show(permissionNeeded, iconRes = R.drawable.lyrics)
            }
        }
    }
    val onChange: (Boolean) -> Unit = { on ->
        if (on) {
            if (Settings.canDrawOverlays(context)) {
                AppSettings.setFloatingLyricsEnabled(true)
                FloatingLyricsService.start(context)
            } else {
                pendingEnable = true
                runCatching {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:" + context.packageName),
                        ),
                    )
                }.onFailure { pendingEnable = false }
            }
        } else {
            AppSettings.setFloatingLyricsEnabled(false)
            FloatingLyricsService.stop(context)
        }
    }
    return enabled to onChange
}
