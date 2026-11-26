// Ported from Mochy Music (GPL-3.0): ui/screens/settings/NowPlayingSettingsScreen.kt
// Adapted to ChordWave's MochyNavigator (no NavController). Batch 43: the
// now-playing rows that used to live inside AppBehaviourSettingsScreen moved
// here, matching Mochy's own page split. Preference keys are unchanged.
// ChordWave-only rows (Disable Spotify Canvas, Show total duration) sit in
// the matching Mochy sections.

package com.music.bitchord.ui.mochy.screens.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.music.bitchord.R
import com.music.bitchord.data.canvas.CanvasOrder
import com.music.bitchord.data.canvas.CanvasSearchSource
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.ui.mochy.component.LocalZoomOutOverlayState
import com.music.bitchord.ui.mochy.component.SettingsHighlight
import com.music.bitchord.ui.mochy.component.SettingsItem
import com.music.bitchord.ui.mochy.component.SubSettingsScaffold
import com.music.bitchord.ui.mochy.component.SubSettingsSearchBar
import com.music.bitchord.ui.mochy.constants.SpotifyCanvasKey
import com.music.bitchord.ui.mochy.screens.settings.integrations.spotify.SpotifyRoute
import com.music.bitchord.ui.mochy.constants.CanvasThumbnailAnimationKey
import com.music.bitchord.ui.mochy.constants.CanvasWifiOnlyKey
import com.music.bitchord.ui.mochy.constants.DisableVideoAutoplayKey
import com.music.bitchord.ui.mochy.constants.LyricsHighBloomKey
import com.music.bitchord.ui.mochy.constants.LyricsMotionStyle
import com.music.bitchord.ui.mochy.constants.LyricsMotionStyleKey
import com.music.bitchord.ui.mochy.constants.LyricsRomanizeAsMainKey
import com.music.bitchord.ui.mochy.constants.LyricsSoftLightKey
import com.music.bitchord.ui.mochy.constants.PlayVideoBackgroundKey
import com.music.bitchord.ui.mochy.constants.ShowFormatBadgeKey
import com.music.bitchord.ui.mochy.constants.ShowFormatSampleRateKey
import com.music.bitchord.ui.mochy.constants.StudioForVideosKey
import com.music.bitchord.ui.mochy.constants.SyncLyricsToVideoKey
import com.music.bitchord.ui.mochy.constants.VideoQuality
import com.music.bitchord.ui.mochy.constants.VideoQualityKey
import com.music.bitchord.ui.mochy.constants.YouTubeCaptionTranslationKey
import com.music.bitchord.ui.mochy.constants.YtVideoBackgroundLyricsSyncKey
import com.music.bitchord.ui.mochy.menu.songedit.LabSelect
import com.music.bitchord.ui.mochy.navigation.MochyNavigator
import com.music.bitchord.ui.mochy.player.SoftLightSupported
import com.music.bitchord.ui.mochy.utils.rememberEnumPreference
import com.music.bitchord.ui.mochy.utils.rememberPreference

const val NowPlayingSettingsRoute = "now_playing_settings"

// settings > now playing: what the open player shows around the song, its badge, lyrics, videos and canvas
@Composable
fun NowPlayingSettingsScreen(navigator: MochyNavigator) {
    var query by remember { mutableStateOf("") }

    SubSettingsScaffold(
        title = stringResource(R.string.settings_now_playing),
        onBack = { navigator.popBackStack() },
        searchBar = { SubSettingsSearchBar(query = query, onQueryChange = { query = it }) },
    ) {
        Section(stringResource(R.string.settings_on_the_player), badgeItems().matching(query))
        Section(stringResource(R.string.settings_section_lyrics), nowPlayingLyricsItems().matching(query))
        Section(stringResource(R.string.settings_section_videos), videoItems().matching(query))
        Section(stringResource(R.string.settings_section_canvas), canvasItems(navigator).matching(query))
        Section(stringResource(R.string.settings_related), nowPlayingRelatedItems(navigator).matching(query))
        Spacer(Modifier.height(140.dp))
    }
}

@Composable
private fun badgeItems(): List<SettingsItem> {
    val (formatBadge, onFormatBadgeChange) = rememberPreference(ShowFormatBadgeKey, defaultValue = true)
    val (formatSampleRate, onFormatSampleRateChange) = rememberPreference(ShowFormatSampleRateKey, defaultValue = false)
    // v1.10 "show total duration" drives the same choice the Studio sheet
    // offers (end timestamp: total vs time left) — one source of truth, the
    // Mochy pref; AppSettings is mirrored for the desktop player.
    val (endTimestamp, onEndTimestampChange) = rememberEnumPreference(
        com.music.bitchord.ui.mochy.constants.PlayerEndTimestampKey,
        defaultValue = com.music.bitchord.ui.mochy.constants.PlayerEndTimestamp.END_TIME,
    )
    return listOf(
        switchItem(
            key = "Audio format badge",
            title = stringResource(R.string.appearance_format_badge),
            description = stringResource(R.string.appearance_format_badge_desc),
            icon = painterResource(R.drawable.graphic_eq),
            checked = formatBadge,
            onCheckedChange = onFormatBadgeChange,
        ),
        switchItem(
            key = "Show sample rate",
            title = stringResource(R.string.appearance_sample_rate),
            description = stringResource(R.string.appearance_sample_rate_desc),
            icon = painterResource(R.drawable.speed),
            checked = formatSampleRate,
            onCheckedChange = onFormatSampleRateChange,
            subOptionOf = formatBadge,
        ),
        switchItem(
            key = "Show total duration",
            title = stringResource(R.string.show_total_duration),
            description = stringResource(R.string.show_total_duration_subtitle),
            icon = painterResource(R.drawable.history),
            checked = endTimestamp == com.music.bitchord.ui.mochy.constants.PlayerEndTimestamp.END_TIME,
            onCheckedChange = { on ->
                onEndTimestampChange(if (on) com.music.bitchord.ui.mochy.constants.PlayerEndTimestamp.END_TIME else com.music.bitchord.ui.mochy.constants.PlayerEndTimestamp.TIME_LEFT)
                AppSettings.setShowTotalDuration(on)
            },
        ),
    )
}

@Composable
private fun nowPlayingLyricsItems(): List<SettingsItem> {
    val (youtubeTranslations, onYoutubeTranslationsChange) = rememberPreference(YouTubeCaptionTranslationKey, defaultValue = true)
    val (syncLyricsToVideo, onSyncLyricsToVideoChange) = rememberPreference(SyncLyricsToVideoKey, defaultValue = true)
    val (romanizeAsMain, onRomanizeAsMainChange) = rememberPreference(LyricsRomanizeAsMainKey, defaultValue = false)
    val (highBloom, onHighBloomChange) = rememberPreference(LyricsHighBloomKey, defaultValue = true)
    val (softLightLyrics, onSoftLightLyricsChange) = rememberPreference(LyricsSoftLightKey, defaultValue = true)
    val (lyricsStyle) = rememberEnumPreference(LyricsMotionStyleKey, defaultValue = LyricsMotionStyle.MOTION)
    val (floatingLyrics, onFloatingLyricsChange) = rememberFloatingLyricsSwitch()
    return listOf(
        switchItem(
            key = "Translations from YouTube",
            title = stringResource(R.string.settings_youtube_translations),
            description = stringResource(R.string.settings_youtube_translations_desc),
            detail = stringResource(R.string.settings_youtube_translations_detail),
            icon = painterResource(R.drawable.translate),
            checked = youtubeTranslations,
            onCheckedChange = onYoutubeTranslationsChange,
        ),
        switchItem(
            key = "Sync lyrics to music videos",
            title = stringResource(R.string.settings_sync_lyrics_video),
            description = stringResource(R.string.settings_sync_lyrics_video_desc),
            detail = stringResource(R.string.settings_sync_lyrics_video_detail),
            icon = painterResource(R.drawable.lyrics),
            checked = syncLyricsToVideo,
            onCheckedChange = onSyncLyricsToVideoChange,
        ),
        switchItem(
            key = "Romanisation as the main line",
            title = stringResource(R.string.settings_romanize_as_main),
            description = stringResource(R.string.settings_romanize_as_main_desc),
            icon = painterResource(R.drawable.lyrics),
            checked = romanizeAsMain,
            onCheckedChange = onRomanizeAsMainChange,
        ),
        // only the shaded lyric styles have a bloom to make softer
        switchItem(
            key = "High quality bloom",
            title = stringResource(R.string.appearance_bloom),
            description = stringResource(R.string.appearance_bloom_desc),
            icon = painterResource(R.drawable.sparks),
            checked = highBloom,
            onCheckedChange = onHighBloomChange,
            visible = lyricsStyle.shaded,
        ),
        // soft light only exists as a layer blend from Android 10, older phones keep the plain dimmed lines
        switchItem(
            key = "Tinted lyrics",
            title = stringResource(R.string.appearance_lyrics_soft_light),
            description = stringResource(R.string.appearance_lyrics_soft_light_desc),
            icon = rememberVectorPainter(Icons.Rounded.Palette),
            checked = softLightLyrics,
            onCheckedChange = onSoftLightLyricsChange,
            visible = SoftLightSupported,
        ),
        switchItem(
            key = "Floating lyrics overlay bubble",
            title = stringResource(R.string.settings_floating_lyrics),
            description = stringResource(R.string.settings_floating_lyrics_desc),
            detail = stringResource(R.string.settings_floating_lyrics_detail),
            icon = painterResource(R.drawable.lyrics),
            checked = floatingLyrics,
            onCheckedChange = onFloatingLyricsChange,
        ),
    )
}

@Composable
private fun videoItems(): List<SettingsItem> {
    val (playVideoBackground, onPlayVideoBackgroundChange) = rememberPreference(PlayVideoBackgroundKey, defaultValue = false)
    val (lyricsSync, onLyricsSyncChange) = rememberPreference(YtVideoBackgroundLyricsSyncKey, defaultValue = true)
    val (disableVideoAutoplay, onDisableVideoAutoplayChange) = rememberPreference(DisableVideoAutoplayKey, defaultValue = false)
    val (studioForVideos, onStudioForVideosChange) = rememberPreference(StudioForVideosKey, defaultValue = false)
    return listOf(
        switchItem(
            key = "Always enable Youtube background",
            title = stringResource(R.string.appearance_youtube_background),
            description = stringResource(R.string.appearance_youtube_background_desc),
            icon = painterResource(R.drawable.slow_motion_video),
            checked = playVideoBackground,
            onCheckedChange = onPlayVideoBackgroundChange,
        ),
        // right under the background switch it belongs to, a sub row only joins the row just above it
        switchItem(
            key = "Timestamp matching",
            title = stringResource(R.string.appearance_timestamp_matching),
            description = stringResource(R.string.appearance_timestamp_matching_desc),
            icon = painterResource(R.drawable.lyrics),
            checked = lyricsSync,
            onCheckedChange = onLyricsSyncChange,
            subOptionOf = playVideoBackground,
        ),
        videoQualityItem(),
        switchItem(
            key = "Disable auto-playing YouTube video",
            title = stringResource(R.string.settings_disable_video_autoplay),
            description = stringResource(R.string.settings_disable_video_autoplay_desc),
            icon = painterResource(R.drawable.slow_motion_video),
            checked = disableVideoAutoplay,
            onCheckedChange = onDisableVideoAutoplayChange,
        ),
        switchItem(
            key = "Studio version for music videos",
            title = stringResource(R.string.settings_studio_for_videos),
            description = stringResource(R.string.settings_studio_for_videos_desc),
            icon = painterResource(R.drawable.music_note),
            checked = studioForVideos,
            onCheckedChange = onStudioForVideosChange,
        ),
    )
}

// how sharp youtube videos play, on the cover and in the full video mode
@Composable
private fun videoQualityItem(): SettingsItem {
    val (quality, onQuality) = rememberEnumPreference(VideoQualityKey, defaultValue = VideoQuality.AUTO)
    val overlay = LocalZoomOutOverlayState.current
    val title = stringResource(R.string.video_quality)
    return SettingsItem(
        title = { Text(title) },
        highlightKey = "Video quality",
        descriptionText = videoQualityName(quality),
        detail = stringResource(R.string.video_quality_settings_desc),
        searchText = title,
        icon = rememberVectorPainter(Icons.Rounded.HighQuality),
        iconShape = CircleShape,
        onClick = { overlay.showVideoQualitySheet(selected = quality, onSelect = onQuality) },
    )
}

@Composable
private fun canvasItems(navigator: MochyNavigator): List<SettingsItem> {
    val (canvasEnabled, onCanvasEnabledChange) = rememberPreference(CanvasThumbnailAnimationKey, defaultValue = true)
    val (canvasWifiOnly, onCanvasWifiOnlyChange) = rememberPreference(CanvasWifiOnlyKey, defaultValue = true)
    val canvasOrder by AppSettings.canvasOrder.collectAsState()
    val canvasBest by AppSettings.canvasBestQuality.collectAsState()
    val disableSpotifyCanvas by AppSettings.disableSpotifyCanvas.collectAsState()
    val (spotifyCanvas) = rememberPreference(SpotifyCanvasKey, defaultValue = false)
    val canvasSourceNames = mapOf(
        CanvasSearchSource.APPLE to stringResource(R.string.canvas_source_apple),
        CanvasSearchSource.TIDAL to stringResource(R.string.canvas_source_tidal),
        CanvasSearchSource.SPOTIFY to stringResource(R.string.canvas_source_spotify),
    )
    return listOf(
        switchItem(
            key = "Enable animated canvas",
            title = stringResource(R.string.appearance_canvas),
            description = stringResource(R.string.appearance_canvas_desc),
            icon = painterResource(R.drawable.sparks),
            checked = canvasEnabled,
            onCheckedChange = onCanvasEnabledChange,
        ),
        SettingsItem(
            title = { Text(stringResource(R.string.canvas_order)) },
            highlightKey = "Canvas order",
            searchText = stringResource(R.string.canvas_order),
            description = {
                LabSelect(
                    options = CanvasOrder.entries.map { order ->
                        order.sources.joinToString(" → ") { canvasSourceNames.getValue(it) }
                    },
                    selectedIndex = canvasOrder.ordinal,
                    onSelect = { AppSettings.setCanvasOrder(CanvasOrder.entries[it]) },
                    contentDescription = stringResource(R.string.canvas_order),
                    // every option here is a real order, none is the "none" one LabSelect mutes
                    mutedIndex = -1,
                    modifier = Modifier.padding(top = 8.dp),
                )
            },
            icon = painterResource(R.drawable.sparks),
            iconShape = CircleShape,
            isVisible = canvasEnabled,
            isSubOption = true,
        ),
        switchItem(
            key = "Disable Spotify Canvas",
            title = stringResource(R.string.disable_spotify_canvas),
            description = stringResource(R.string.disable_spotify_canvas_subtitle),
            icon = painterResource(R.drawable.sparks),
            checked = disableSpotifyCanvas,
            onCheckedChange = AppSettings::setDisableSpotifyCanvas,
            subOptionOf = canvasEnabled,
        ),
        linkItem(
            key = "Spotify canvas",
            title = stringResource(R.string.spotify_canvas),
            description = stringResource(if (spotifyCanvas) R.string.spotify_canvas_row_on else R.string.spotify_canvas_row_off),
            icon = painterResource(R.drawable.sparks),
            onClick = { navigator.navigate(SpotifyRoute) },
            visible = canvasEnabled,
            isSubOption = true,
        ),
        switchItem(
            key = "Canvas on Wi-Fi only",
            title = stringResource(R.string.appearance_canvas_wifi),
            description = stringResource(R.string.appearance_canvas_wifi_desc),
            icon = painterResource(R.drawable.wifi_proxy),
            checked = canvasWifiOnly,
            onCheckedChange = onCanvasWifiOnlyChange,
            subOptionOf = canvasEnabled,
        ),
        switchItem(
            key = "Sharpest animated covers",
            title = stringResource(R.string.canvas_best_quality),
            description = stringResource(R.string.canvas_best_quality_desc),
            icon = rememberVectorPainter(Icons.Rounded.HighQuality),
            checked = canvasBest,
            onCheckedChange = AppSettings::setCanvasBestQuality,
            subOptionOf = canvasEnabled,
        ),
    )
}

// how the player looks lives in customize, these take people to the right card there
@Composable
private fun nowPlayingRelatedItems(navigator: MochyNavigator): List<SettingsItem> {
    val openCustomise = { key: String ->
        SettingsHighlight.pending = key
        navigator.navigate("customisation_settings")
    }
    return listOf(
        linkItem(
            key = "Lyrics animation style",
            title = stringResource(R.string.appearance_lyrics_animation),
            description = stringResource(R.string.settings_lyrics_animation_link_desc),
            icon = painterResource(R.drawable.lyrics),
            onClick = { openCustomise("Lyrics animation") },
        ),
        linkItem(
            key = "Hide Top Bar status bar player",
            title = stringResource(R.string.settings_hide_top_bar),
            description = stringResource(R.string.settings_hide_top_bar_desc),
            icon = painterResource(R.drawable.tune),
            onClick = { openCustomise(TopBarHighlightKey) },
        ),
        linkItem(
            key = "Hide Bottom Bar navigation bar player",
            title = stringResource(R.string.settings_hide_bottom_bar),
            description = stringResource(R.string.settings_hide_bottom_bar_desc),
            icon = painterResource(R.drawable.tune),
            onClick = { openCustomise(BottomBarHighlightKey) },
        ),
    )
}
