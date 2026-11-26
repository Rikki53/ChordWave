// Ported from Mochy Music (GPL-3.0): ui/screens/settings/MochySettingsScreen.kt
// The Settings tab root. Adapted to ChordWave's MochyNavigator (no
// NavController) and the Phase 4 settings/* route scheme; sub-screens owned
// by other workers are linked by ROUTE STRING only, never imported.
//
// Deviations from Mochy (all documented in NEEDS.md):
// - Devices bubble + device-integration search entry DROPPED: Mochy's
//   Devices/remote-control UI is out of scope (MAPPING.md).
// - Modules bubble + module pages + Apple Music/Monochrome search entries:
//   restored in batch 55b (Mochy's modules system replaces Sources in
//   Modern; the Phase 5 Sources restyle is gone from Modern, Classic keeps
//   its own Sources screen).
// - Scrobble/Discord search entries link to settings/integrations (the root);
//   sub-routes belong to the integrations worker.
// - Profile page is out of scope, so the avatar opens the YouTube account
//   page (youtube_sync) instead of a profile — Mochy's own placement for
//   account access, and the account card below it is the visible sign-in
//   entry point (round 2, item 2): signed out it goes straight to
//   youtube_login, signed in it shows the account and opens youtube_sync.
//   Both routes are registered in MochyApp; the login itself runs through
//   CW's YtMusicLoginScreen + MainViewModel.onWebSession (see
//   importsync/YouTubeLoginScreen.kt), and the signed-in state reads back
//   from MainViewModel.account + auth.SignedIn — no dead wiring.
// - ADDED: the Classic/Modern UiMode switch (Riki's dual-mode decision) as a
//   card under the categories, using the same AppSettings.setUiMode mechanism
//   as the classic settings sheet.
// - Brand: ic_chordwave_mark + "ChordWave" replace the Mochy mark.

package com.music.bitchord.ui.mochy.screens.settings

import androidx.activity.compose.BackHandler
import com.music.bitchord.data.mochydata.modules.browser.ModuleFeed
import com.music.bitchord.ui.mochy.screens.devices.showDeviceIntegration
import com.music.bitchord.ui.mochy.screens.settings.modules.ModuleBrowserRoute
import com.music.bitchord.ui.mochy.screens.settings.modules.ModuleSettingsPages
import com.music.bitchord.ui.mochy.screens.devices.rememberDevicesLink
import com.music.bitchord.ui.mochy.screens.devices.DeviceIntegrationRoute
import com.music.bitchord.ui.mochy.component.LocalZoomOutOverlayState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import com.music.bitchord.R
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.UiMode
import com.music.bitchord.ui.MainViewModel
import com.music.bitchord.ui.mochy.LocalPlayerAwareWindowInsets
import com.music.bitchord.ui.mochy.component.GlassState
import com.music.bitchord.ui.mochy.component.SettingsHighlight
import com.music.bitchord.ui.mochy.component.glassRoot
import com.music.bitchord.ui.mochy.screens.profile.rememberProfilePhotoUrl
import com.music.bitchord.ui.mochy.navigation.MochyNavigator
import com.music.bitchord.ui.mochy.screens.search.SearchAvatar
import com.music.bitchord.ui.mochy.screens.search.SearchField
import com.music.bitchord.ui.mochy.screens.search.SearchGlassTopBar
import com.music.bitchord.ui.mochy.screens.search.rememberScrollCollapseProgress
import com.music.bitchord.ui.mochy.screens.search.searchTopBarHeight
import com.music.bitchord.ui.mochy.theme.InterFontFamily
import com.music.bitchord.ui.mochy.utils.rememberPreference
import com.music.bitchord.ui.mochy.utils.rememberYouTubeSignedIn

private data class SettingsEntry(
    val title: String,
    val page: String,
    val route: String,
    val keywords: String = "",
    val titleRes: Int? = null,
    val pageRes: Int? = null,
) {
    val highlightKey: String get() = title
}

// search ids stay english so rows can find themselves, what's shown and matched follows the app's language
private val LocalizedSettingsTitles = mapOf(
    "Interface" to R.string.settings_ui_mode,
    "Customisation" to R.string.settings_customisation_category,
    "Blur" to R.string.appearance_blur,
    "Progressive blur" to R.string.appearance_progressive_blur,
    "Blur quality" to R.string.appearance_blur_quality,
    "Blur strength" to R.string.appearance_blur_strength,
    "Animation" to R.string.appearance_animation,
    "Lyrics animation" to R.string.appearance_lyrics_animation,
    "Larp mode" to R.string.larp_mode,
    "Disable App Updates notice" to R.string.settings_updates_notice,
    "Offline mode" to R.string.settings_offline_mode,
    "Data saver" to R.string.settings_data_saver,
    "Proxy" to R.string.proxy,
    "Albums from liked songs" to R.string.settings_albums_from_likes,
    "Keep playing even when closed" to R.string.playback_keep_playing,
    "Play alongside other apps" to R.string.playback_mix_other_apps,
    "Save Player's last state" to R.string.playback_save_state,
    "Local song auto metadata" to R.string.settings_local_auto_metadata,
    "Keep shuffle on" to R.string.settings_persistent_shuffle,
    "Disable auto-playing YouTube video" to R.string.settings_disable_video_autoplay,
    "Translations from YouTube" to R.string.settings_youtube_translations,
    "Sync lyrics to music videos" to R.string.settings_sync_lyrics_video,
    "Don't skip on failure" to R.string.settings_dont_skip_failure,
    "Skip timeout failure" to R.string.settings_skip_timeout,
    "Skip Silence" to R.string.playback_skip_silence,
    "Audio format badge" to R.string.appearance_format_badge,
    "Show sample rate" to R.string.appearance_sample_rate,
    "Always enable Youtube background" to R.string.appearance_youtube_background,
    "Enable animated canvas" to R.string.appearance_canvas,
    "Tinted lyrics" to R.string.appearance_lyrics_soft_light,
    "Canvas order" to R.string.canvas_order,
    "Sharpest animated covers" to R.string.canvas_best_quality,
    "Override lyrics sorting" to R.string.settings_lyrics_order_override,
    "Classic" to R.string.ui_mode_classic,
    "Modern" to R.string.ui_mode_modern,
    "High quality bloom" to R.string.appearance_bloom,
    "Audio Pass" to R.string.audiopass_hub_title,
    "Prefer USB DAC" to R.string.playback_usb_prefer,
    "USB DAC passthrough" to R.string.playback_usb_passthrough,
    "Hi-res output" to R.string.playback_hires,
    "Hi-fi resampler" to R.string.playback_hifi,
    "Audio Lab" to R.string.settings_audio_lab_category,
    "Playback" to R.string.settings_playback_category,
    "Now playing" to R.string.settings_now_playing,
    "Pause search history" to R.string.pause_search_history,
    "Count plays on YouTube views" to R.string.settings_youtube_plays,
    "Resume on Bluetooth connection" to R.string.playback_resume_bluetooth,
    "Romanisation as the main line" to R.string.settings_romanize_as_main,
    "Floating lyrics overlay bubble" to R.string.settings_floating_lyrics,
    "Video quality" to R.string.video_quality,
    "Canvas on Wi-Fi only" to R.string.appearance_canvas_wifi,
    "Studio version for music videos" to R.string.settings_studio_for_videos,
    "Storage" to R.string.storage,
    "Modules" to R.string.settings_modules_category,
    "Listen together" to R.string.listen_together,
    "App behaviour" to R.string.settings_app_behaviour,
    "Disable description on settings" to R.string.settings_hide_descriptions,
    "Cover rounding" to R.string.customise_cover_rounding,
    "Playlist styles" to R.string.customise_playlist_styles,
    "Player styles" to R.string.customise_player_styles,
    "Home styles" to R.string.customise_home_styles,
    "Element blur" to R.string.customise_element_blur,
    "Animation speed" to R.string.customise_animation_speed,
    "Top Bar on player" to R.string.customise_top_bar,
    "Gesture" to R.string.customise_gesture,
    "Swipe right" to R.string.customise_swipe_right,
    "Swipe left" to R.string.customise_swipe_left,
    "Swipe down anywhere to close" to R.string.customise_swipe_down_anywhere,
    "Open with your finger" to R.string.customise_swipe_up_follows,
)
private val LocalizedSettingsPages = mapOf(
    "Settings" to R.string.settings,
    "App behaviour" to R.string.settings_app_behaviour,
    "Customisation" to R.string.settings_customisation_category,
    "Storage" to R.string.storage,
    "Modules" to R.string.settings_modules_category,
    "Listen together" to R.string.listen_together,
    "Audio Lab" to R.string.settings_audio_lab_category,
    "Playback" to R.string.settings_playback_category,
    "Now playing" to R.string.settings_now_playing,
    "Integrations" to R.string.integrations,
)

private val SettingsIndex = listOf(
    // Interface — the Classic/Modern switch lives on this page (UiModeCard)
    SettingsEntry("Interface", "Settings", "mochy_settings", "interface classic modern theme switch ui mode appearance"),
    SettingsEntry("Classic", "Settings", "mochy_settings", "classic interface old ui mode switch theme"),
    SettingsEntry("Modern", "Settings", "mochy_settings", "modern interface new ui mode switch theme"),

    // Customisation
    SettingsEntry("Customisation", "Customisation", "customisation_settings", "customize cover rounding playlist player home styles gesture swipe blur animation lyrics"),
    SettingsEntry("Cover rounding", "Customisation", "customisation_settings", "square rounded corner radius px thumbnail art"),
    SettingsEntry("Playlist styles", "Customisation", "customisation_settings", "flow classic minimal header layout"),
    SettingsEntry("Player styles", "Customisation", "customisation_settings", "flagship turntable ipod poster timeline lyrics studio customize player cover"),
    SettingsEntry("Home styles", "Customisation", "customisation_settings", "modern listen now hop in hero layout grid"),
    SettingsEntry("Blur", "Customisation", "customisation_settings", "blur glass frosted performance lag"),
    SettingsEntry("Progressive blur", "Customisation", "customisation_settings", "gradient fade top bar hairline flat"),
    SettingsEntry("Blur quality", "Customisation", "customisation_settings", "low high glass performance"),
    SettingsEntry("Blur strength", "Customisation", "customisation_settings", "radius px glass amount"),
    SettingsEntry("Element blur", "Customisation", "customisation_settings", "lyrics line blur cover zoom title morph menu page wipe"),
    SettingsEntry("Animation", "Customisation", "customisation_settings", "transition motion off reduce disable"),
    SettingsEntry("Animation speed", "Customisation", "customisation_settings", "faster slower duration scale motion"),
    SettingsEntry("Lyrics animation", "Customisation", "customisation_settings", "lyrics style 1 2 boring plain shader glow wave agsl letters"),
    SettingsEntry("Top Bar on player", "Customisation", "customisation_settings", "status bar hide clock battery player top"),
    SettingsEntry("Gesture", "Customisation", "customisation_settings", "swipe slide song list left right queue library download play next playlist details"),
    SettingsEntry("Swipe right", "Customisation", "customisation_settings", "slide song list queue library download play next playlist details action"),
    SettingsEntry("Swipe left", "Customisation", "customisation_settings", "slide song list queue library download play next playlist details action"),
    SettingsEntry("Swipe down anywhere to close", "Customisation", "customisation_settings", "player close dismiss swipe down pull gesture anywhere"),
    SettingsEntry("Open with your finger", "Customisation", "customisation_settings", "player open swipe up mini player follow finger speed velocity gesture"),

    // App behaviour
    SettingsEntry("App behaviour", "App behaviour", "general_settings", "general app language updates network data library privacy"),
    SettingsEntry("Larp mode", "App behaviour", "general_settings", "larp 67 flac 768 khz wadax atlantis dac hi-res lossless bit-perfect flex"),
    SettingsEntry("App language", "App behaviour", "general_settings", "language bahasa locale device system indonesia english translate", R.string.app_language),
    SettingsEntry("Disable App Updates notice", "App behaviour", "general_settings", "update popup notification updater"),
    SettingsEntry("Disable description on settings", "App behaviour", "general_settings", "hide subtitle text rows titles only"),
    SettingsEntry("Offline mode", "App behaviour", "general_settings", "play downloaded cache offline"),
    SettingsEntry("Data saver", "App behaviour", "general_settings", "data saver mobile metered cellular bandwidth lower quality canvas video"),
    SettingsEntry("Proxy", "App behaviour", "proxy_settings", "proxy socks http server vpn network region country blocked bot sign in"),
    SettingsEntry("Albums from liked songs", "App behaviour", "general_settings", "albums library liked songs old way apple music"),
    SettingsEntry("Local song auto metadata", "App behaviour", "general_settings", "match tags id3 covers lyrics"),
    SettingsEntry("Pause search history", "App behaviour", "general_settings", "search history pause private incognito privacy", R.string.pause_search_history),
    SettingsEntry("Count plays on YouTube views", "App behaviour", "general_settings", "youtube history plays views report count privacy", R.string.settings_youtube_plays),
    SettingsEntry("Override lyrics sorting", "App behaviour", "general_settings", "lyrics provider order sort priority duet word sync apple music source", R.string.settings_lyrics_order_override),

    // Playback
    SettingsEntry("Playback", "Playback", "playback_settings", "background bluetooth shuffle skip silence mute queue failure"),
    SettingsEntry("Keep playing even when closed", "Playback", "playback_settings", "background close stop playback"),
    SettingsEntry("Play alongside other apps", "Playback", "playback_settings", "audio focus mix pause other app video game sound together"),
    SettingsEntry("Resume on Bluetooth connection", "Playback", "playback_settings", "bluetooth headphones earbuds car connect resume auto play start", R.string.playback_resume_bluetooth),
    SettingsEntry("Pause when muted", "Playback", "playback_settings", "pause mute volume silent zero off automatic", R.string.pause_music_when_media_is_muted),
    SettingsEntry("Save Player's last state", "Playback", "playback_settings", "persistent queue restore track state"),
    SettingsEntry("Keep shuffle on", "Playback", "playback_settings", "shuffle persistent spotify random keep new playlist"),
    SettingsEntry("Skip Silence", "Playback", "playback_settings", "silence trim gap interval"),
    SettingsEntry("Don't skip on failure", "Playback", "playback_settings", "fail stream error stay skip"),
    SettingsEntry("Skip timeout failure", "Playback", "playback_settings", "skip timeout seconds failed stream"),

    // Now playing
    SettingsEntry("Now playing", "Now playing", "now_playing_settings", "player lyrics video canvas badge format"),
    SettingsEntry("Audio format badge", "Now playing", "now_playing_settings", "format codec opus aac flac badge title quality"),
    SettingsEntry("Show sample rate", "Now playing", "now_playing_settings", "sample rate khz hz format badge quality"),
    SettingsEntry("Translations from YouTube", "Now playing", "now_playing_settings", "captions translate lyrics language"),
    SettingsEntry("Sync lyrics to music videos", "Now playing", "now_playing_settings", "lyrics video timing intro skit"),
    SettingsEntry("Romanisation as the main line", "Now playing", "now_playing_settings", "romaji romanization pinyin latin lyrics japanese korean chinese", R.string.settings_romanize_as_main),
    SettingsEntry("High quality bloom", "Now playing", "now_playing_settings", "lyrics bloom quality extra soft pass glow"),
    SettingsEntry("Tinted lyrics", "Now playing", "now_playing_settings", "lyrics soft light blend tint colour color cover inactive grey gray lines"),
    SettingsEntry("Floating lyrics overlay bubble", "Now playing", "now_playing_settings", "floating lyrics overlay bubble pill other apps draw over", R.string.settings_floating_lyrics),
    SettingsEntry("Always enable Youtube background", "Now playing", "now_playing_settings", "youtube video background canvas"),
    SettingsEntry("Video quality", "Now playing", "now_playing_settings", "video quality resolution 4k 2160p 1440p 1080p 720p 480p sharp", R.string.video_quality),
    SettingsEntry("Disable auto-playing YouTube video", "Now playing", "now_playing_settings", "music video live autoplay cover"),
    SettingsEntry("Studio version for music videos", "Now playing", "now_playing_settings", "music video mv omv audio studio album version skit sound effects official", R.string.settings_studio_for_videos),
    SettingsEntry("Enable animated canvas", "Now playing", "now_playing_settings", "canvas animation spotify looping visual"),
    SettingsEntry("Canvas order", "Now playing", "now_playing_settings", "canvas order apple music tidal spotify source priority", R.string.canvas_order),
    SettingsEntry("Canvas on Wi-Fi only", "Now playing", "now_playing_settings", "canvas wifi mobile data", R.string.appearance_canvas_wifi),
    SettingsEntry("Sharpest animated covers", "Now playing", "now_playing_settings", "canvas animated cover motion apple music quality hd 4k sharp resolution", R.string.canvas_best_quality),

    // Audio Lab
    SettingsEntry("Audio Lab", "Audio Lab", "audio_lab", "sound effects equalizer fx quality"),
    SettingsEntry("Transition", "Audio Lab", "crossmix_settings", "crossfade crossmix automix transition gapless blend"),
    SettingsEntry("Blend skips", "Audio Lab", "crossmix_settings", "skip next previous back crossfade seamless preload transition fade blend"),
    SettingsEntry("Equalizer", "Audio Lab", "equalizer", "eq bass treble band parametric graph autoeq headphones correction preset"),
    SettingsEntry("Headphone correction", "Audio Lab", "equalizer?tab=autoeq", "headphones earbuds correction harman database model"),
    SettingsEntry("Audio Quality", "Audio Lab", "audio_quality", "bitrate stream high low lossless hi-res flac monochrome"),
    SettingsEntry("Audio Pass", "Audio Lab", "audio_pass", "dac usb output headphones bluetooth codec bit perfect exclusive"),
    SettingsEntry("Prefer USB DAC", "Audio Lab", "audio_pass", "usb dac headphones output external audio"),
    SettingsEntry("USB DAC passthrough", "Audio Lab", "audio_pass", "bit perfect bitperfect passthrough sample rate dac usb resample"),
    SettingsEntry("Hi-res output", "Audio Lab", "audio_pass", "hi res 24 bit float precision lossless flac quality"),
    SettingsEntry("Hi-fi resampler", "Audio Lab", "audio_pass", "resample sample rate 44.1 48 khz sinc src quality filter"),
    SettingsEntry("Reverb", "Audio Lab", "audio_fx", "room hall echo space fx effect"),
    SettingsEntry("Loudness", "Audio Lab", "audio_fx", "boost louder compressor limiter night mode fx"),
    SettingsEntry("Audio Normalization", "Audio Lab", "audio_fx", "even out songs loudness volume gain balance replaygain"),
    SettingsEntry("Spatial audio", "Audio Lab", "audio_fx", "crossfeed binaural hrtf virtual speakers 3d headphones"),
    SettingsEntry("Dolby Atmos", "Audio Lab", "audio_fx", "atmos spatial surround 3d immersive"),
    SettingsEntry("Playback speed", "Audio Lab", "audio_fx", "tempo pitch slowed nightcore preserve pitch"),
    SettingsEntry("Exponential volume", "Audio Lab", "audio_fx", "volume curve logarithmic limit"),
    SettingsEntry("Provider order", "Audio Lab", "audio_quality", "source order apple music monochrome youtube priority"),
    SettingsEntry("YouTube Music", "Audio Lab", "audio_quality", "youtube turn off disable provider only monochrome apple music"),

    // Integrations (sub-routes belong to the integrations worker; search links to the root)
    SettingsEntry("Discord", "Integrations", "integrations_settings/discord", "discord rich presence rpc status activity listening playing profile behaviour pause clear"),
    SettingsEntry("Last.fm", "Integrations", "integrations_settings/scrobble/LASTFM", "lastfm scrobble scrobbling history stats account sign in"),
    SettingsEntry("Libre.fm", "Integrations", "integrations_settings/scrobble/LIBREFM", "librefm scrobble scrobbling history stats account sign in"),
    SettingsEntry("Now Playing", "Integrations", "integrations_settings/scrobble/LASTFM", "scrobble now playing status profile lastfm librefm"),
    SettingsEntry("Send likes", "Integrations", "integrations_settings/scrobble/LASTFM", "love loved songs lastfm librefm scrobble"),
    SettingsEntry("Backup", "Integrations", "integrations_settings/backup", "backup cloud dropbox onedrive webdav restore schedule"),
    SettingsEntry("Spotify Canvas", "Integrations", "integrations_settings/spotify", "spotify canvas animated artwork sign in"),
    SettingsEntry("YouTube Music", "Integrations", "youtube_sync", "youtube ytm sync account sign in likes library playlists history"),
    SettingsEntry("YouTube account", "Integrations", "youtube_sync", "account sign in login google youtube profile sync"),
    SettingsEntry("Device integration", "Integrations", "device_integration", "tv television phone tablet car head unit pair link sync stream remote control devices wifi"),

    // Storage
    SettingsEntry("Storage", "Storage", "storage_settings", "space disk size cache downloads clear free junk delete"),
    SettingsEntry("Music cache limit", "Storage", "storage_settings", "cache size limit space song stream"),
    SettingsEntry("Manage downloads", "Storage", "storage_settings", "downloads delete remove biggest offline space"),
    SettingsEntry("Remove all downloads", "Storage", "storage_settings", "delete clear downloads offline space"),

    // Modules (batch 55b, Mochy original)
    SettingsEntry("Modules", "Modules", "modules", "apple music source module plugin extension stream subscription"),
    SettingsEntry("Apple Music", "Modules", "module/applemusic", "apple music itunes subscription stream aac"),
    SettingsEntry("Monochrome", "Modules", "module/monochrome", "monochrome lossless flac hi-res tidal"),

    // Listen together (Phase 5 restyle)
    SettingsEntry("Listen together", "Listen together", "listen_together", "party jam friends together code invite host queue"),
    SettingsEntry("Create party", "Listen together", "listen_together", "party create host code start together"),
    SettingsEntry("Join party", "Listen together", "listen_together", "party join code invite together"),

    // Experimental
    SettingsEntry("Import & sync", "Experimental", "import_sync", "import spotify apple music deezer tidal tunemymusic csv youtube sync export backup transfer"),
    SettingsEntry("Big disc cover styles", "Experimental", "experimental_settings", "disc vinyl cover"),
    SettingsEntry("Music haptics", "Experimental", "experimental_settings", "vibration haptic"),
    SettingsEntry("Cipher", "Experimental", "cipher_settings", "player script signature refresh"),
    SettingsEntry("Playback diagnostics", "Experimental", "playback_diagnostics", "logs potoken stream client debug"),
    SettingsEntry("Repeat initial setup", "Experimental", "experimental_settings", "onboarding wizard setup"),
    SettingsEntry("Show where songs play from", "Experimental", "experimental_settings", "source banner toast apple music monochrome youtube"),
    SettingsEntry("Force playback", "Experimental", "experimental_settings", "fallback skip error stream failed youtube"),
)

private data class RecommendedSetting(
    val titleRes: Int,
    val breadcrumbRes: Int,
    val iconRes: Int,
    // opens a page, the list's own way: over it on a phone, beside it on a wide screen
    val onClick: (open: (String) -> Unit) -> Unit,
)

private val RecommendedOptions = listOf(
    RecommendedSetting(
        titleRes = R.string.settings_rec_blur,
        breadcrumbRes = R.string.settings_crumb_customisation,
        iconRes = R.drawable.contrast,
        onClick = { open ->
            SettingsHighlight.pending = "Blur"
            open("customisation_settings")
        }
    ),
    RecommendedSetting(
        titleRes = R.string.settings_rec_crossfade,
        breadcrumbRes = R.string.settings_crumb_audio_lab,
        iconRes = R.drawable.linear_scale,
        onClick = { open ->
            open("crossmix_settings")
        }
    ),
    RecommendedSetting(
        titleRes = R.string.settings_rec_equalizer,
        breadcrumbRes = R.string.settings_crumb_audio_lab,
        iconRes = R.drawable.equalizer,
        onClick = { open ->
            open("equalizer")
        }
    )
)

private val BubbleColor = Color(0xFF1C1C1E)
private val SelectedBubbleColor = Color(0xFF3A3A3E)

// the header holds the chordwave mark with the big "Settings" under it, taller than search's title-only header
private val SettingsTitleBlockHeight = 88.dp
private val SettingsMarkSize = 32.dp
private val IconBgColor = Color(0xFF333338)

@Composable
fun MochySettingsScreen(
    navigator: MochyNavigator,
    pane: SettingsPane? = null,
    mainViewModel: MainViewModel = viewModel(),
) {
    val focusManager = LocalFocusManager.current
    // on a wide screen the list stays put and its pages open beside it
    val open: (String) -> Unit = { route ->
        if (pane != null) navigator.openSettingsPage(route) else navigator.navigate(route)
    }
    val selected = pane?.selectedRoute
    val scrollState = rememberScrollState()
    val glassState = remember { GlassState() }


    // The account card's state: SignedIn is the same process-wide flow
    // MainViewModel.onWebSession writes after a successful login, and
    // account carries the name/email it loaded — both update live, so the
    // card shows the signed-in account as soon as the login screen pops.
    val signedIn = rememberYouTubeSignedIn()
    val account by mainViewModel.account.collectAsState()
    val accountName = account?.name.orEmpty()
    val accountEmail = account?.email.orEmpty()

    var query by remember { mutableStateOf(TextFieldValue()) }
    var isSearchFocused by remember { mutableStateOf(false) }

    val isSearching = isSearchFocused || query.text.isNotBlank()

    BackHandler(enabled = isSearching) {
        query = TextFieldValue()
        isSearchFocused = false
        focusManager.clearFocus()
    }

    val recommendedItem = remember { RecommendedOptions.random() }

    val context = LocalContext.current
    val sheets = LocalZoomOutOverlayState.current
    val devicesLink = rememberDevicesLink()
    val results = remember(query.text, context) {
        val q = query.text.trim()
        if (q.isBlank()) {
            emptyList()
        } else {
            SettingsIndex.filter {
                it.title.contains(q, ignoreCase = true) ||
                    (it.titleRes ?: LocalizedSettingsTitles[it.title])?.let { id -> context.getString(id).contains(q, ignoreCase = true) } == true ||
                    (it.pageRes ?: LocalizedSettingsPages[it.page])?.let { id -> context.getString(id).contains(q, ignoreCase = true) } == true ||
                    it.keywords.contains(q, ignoreCase = true) ||
                    it.page.contains(q, ignoreCase = true)
            }
        }
    }

    // Same latch-and-settle collapse as Library/Search: crossing the threshold always finishes
    // the morph, even after the user stops scrolling mid-gesture.
    val collapseProgress by rememberScrollCollapseProgress(scrollState)

    // Animated fade for content when search bar is tapped
    val contentAlpha by animateFloatAsState(
        targetValue = if (isSearching) 0f else 1f,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "contentAlpha"
    )
    // flips only at the ends of the fade, so the page isn't recomposed on every frame of it
    val showContent by remember { derivedStateOf { contentAlpha > 0.001f } }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .glassRoot(glassState, isActive = { !scrollState.isScrollInProgress && collapseProgress > 0f }),
        ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
            Spacer(modifier = Modifier.height(searchTopBarHeight(titleBlockHeight = SettingsTitleBlockHeight)))

            // Search results view when user types
            AnimatedVisibility(
                visible = query.text.isNotBlank(),
                enter = fadeIn(tween(160, easing = FastOutSlowInEasing)) +
                    slideInVertically(tween(160, easing = FastOutSlowInEasing)) { 20 },
                exit = fadeOut(tween(120, easing = FastOutSlowInEasing))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (results.isEmpty()) {
                        Text(
                            text = stringResource(R.string.settings_nothing_matches, query.text),
                            fontFamily = InterFontFamily,
                            fontSize = 15.sp,
                            color = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp)
                        )
                    } else {
                        results.forEach { entry ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(BubbleColor)
                                    .clickable {
                                        SettingsHighlight.pending = entry.highlightKey
                                        if (entry.route == "mochy_settings") {
                                            // the search ran on this very page, so navigation
                                            // is a no-op (MochyNavigator ignores a route that
                                            // is already the top of the stack) — clear the
                                            // query and keyboard so the page and the
                                            // highlighted card are visible again
                                            query = TextFieldValue()
                                            focusManager.clearFocus()
                                        }
                                        if (entry.route == DeviceIntegrationRoute) {
                                            sheets.showDeviceIntegration(devicesLink)
                                        } else {
                                            open(entry.route)
                                        }
                                    }
                                    .padding(horizontal = 16.dp, vertical = 14.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(CircleShape)
                                            .background(IconBgColor),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.settings),
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(11.dp))
                                    Column {
                                        Text(
                                            text = (entry.titleRes ?: LocalizedSettingsTitles[entry.title])?.let { stringResource(it) } ?: entry.title,
                                            fontFamily = InterFontFamily,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 16.sp,
                                            color = Color.White
                                        )
                                        Text(
                                            text = (entry.pageRes ?: LocalizedSettingsPages[entry.page])?.let { stringResource(it) } ?: entry.page,
                                            fontFamily = InterFontFamily,
                                            fontSize = 12.sp,
                                            color = Color.White.copy(alpha = 0.55f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Normal Settings Content (fades smoothly to black when search is focused)
            if (showContent) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer { alpha = contentAlpha }
                ) {
                    // The account card: the visible sign-in entry point. Signed out
                    // it goes straight to the login screen; signed in it shows
                    // the account and opens the account/sync page.
                    AccountCard(
                        signedIn = signedIn,
                        accountName = accountName,
                        accountEmail = accountEmail,
                        onClick = {
                            if (signedIn) open("youtube_sync") else open("youtube_login")
                        },
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    // Recommended Item Card
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(BubbleColor)
                            .clickable { recommendedItem.onClick(open) }
                            .padding(18.dp)
                    ) {
                        Column {
                            Text(
                                text = stringResource(R.string.settings_recommended_item),
                                fontFamily = InterFontFamily,
                                fontWeight = FontWeight.Medium,
                                fontSize = 13.sp,
                                color = Color.White.copy(alpha = 0.55f)
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(IconBgColor),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        painter = painterResource(recommendedItem.iconRes),
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = stringResource(recommendedItem.titleRes),
                                        fontFamily = InterFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = Color.White
                                    )
                                    Text(
                                        text = stringResource(recommendedItem.breadcrumbRes),
                                        fontFamily = InterFontFamily,
                                        fontSize = 13.sp,
                                        color = Color.White.copy(alpha = 0.55f)
                                    )
                                }
                            }
                        }
                    }

                    // a build without the module feed (most forks) has no store to open
                    if (ModuleFeed.FeedUrl.isNotBlank()) {
                        Spacer(modifier = Modifier.height(14.dp))
                        ModuleStoreCard(
                            onClick = { navigator.navigate(ModuleBrowserRoute) },
                            modifier = Modifier.padding(horizontal = 20.dp),
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Divider
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .height(0.8.dp)
                            .background(Color.White.copy(alpha = 0.1f))
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    // Separate Bubble Pills for Categories (No outlines, individually bubble-wrapped)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // in the order of the design: sound first, then how the app acts and looks, then the rest
                        CategoryBubble(
                            title = stringResource(R.string.settings_audio_lab_category),
                            iconRes = R.drawable.equalizer,
                            selected = selected == "audio_lab",
                            onClick = { open("audio_lab") }
                        )

                        CategoryBubble(
                            title = stringResource(R.string.settings_playback_category),
                            iconRes = R.drawable.play,
                            selected = selected == "playback_settings",
                            onClick = { open("playback_settings") }
                        )

                        CategoryBubble(
                            title = stringResource(R.string.settings_now_playing),
                            iconRes = R.drawable.lyrics,
                            selected = selected == "now_playing_settings",
                            onClick = { open("now_playing_settings") }
                        )

                        CategoryBubble(
                            title = stringResource(R.string.settings_app_behaviour),
                            iconRes = R.drawable.settings,
                            selected = selected == "general_settings",
                            onClick = { open("general_settings") }
                        )

                        CategoryBubble(
                            title = stringResource(R.string.settings_customisation_category),
                            iconRes = R.drawable.palette,
                            selected = selected == "customisation_settings",
                            onClick = { open("customisation_settings") }
                        )

                        CategoryBubble(
                            title = stringResource(R.string.storage),
                            iconRes = R.drawable.storage,
                            selected = selected == "storage_settings",
                            onClick = { open("storage_settings") }
                        )

                        CategoryBubble(
                            title = stringResource(R.string.integrations),
                            iconRes = R.drawable.integration,
                            selected = selected == "integrations_settings",
                            onClick = { open("integrations_settings") }
                        )

                        CategoryBubble(
                            title = stringResource(R.string.devices_title),
                            iconRes = R.drawable.cast_connected,
                            selected = false,
                            onClick = { sheets.showDeviceIntegration(devicesLink) }
                        )

                        CategoryBubble(
                            title = stringResource(R.string.settings_modules_category),
                            iconRes = R.drawable.library_add,
                            selected = selected == "modules",
                            onClick = { open("modules") }
                        )

                        // pages the modules that are on bring to settings, right under modules
                        ModuleSettingsPages(open = open, selected = selected)

                        CategoryBubble(
                            title = stringResource(R.string.listen_together),
                            iconRes = R.drawable.group,
                            selected = selected == "listen_together",
                            onClick = { open("listen_together") }
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Divider
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .height(0.8.dp)
                            .background(Color.White.copy(alpha = 0.1f))
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    // Classic / Modern switch (Riki's dual-mode decision): the same
                    // AppSettings.setUiMode mechanism as the classic settings sheet.
                    UiModeCard()
                }
            }

            val bottomInset = LocalPlayerAwareWindowInsets.current.asPaddingValues()
            val bottomPadding = bottomInset.calculateBottomPadding()
            val bottomSpacerHeight = maxOf(340.dp, bottomPadding + 160.dp)

            // Headroom spacer for smooth scrolling past collapse threshold and bottom bar
            Spacer(modifier = Modifier.height(bottomSpacerHeight))
        }
        }

        // Top bar: ChordWave mark + "Settings" title slide away on scroll (progressive glass blur
        // behind the gradient, matching Library/Search) while the search field glides up to sit
        // beside the profile avatar.
        SearchGlassTopBar(
            glassState = glassState,
            progressProvider = { collapseProgress },
            pureBlack = true,
            title = stringResource(R.string.settings),
            titleBlockHeight = SettingsTitleBlockHeight,
            titleContent = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(
                        painter = painterResource(R.drawable.ic_chordwave_mark),
                        contentDescription = "ChordWave",
                        tint = Color.White,
                        modifier = Modifier.size(SettingsMarkSize),
                    )
                    Text(
                        text = stringResource(R.string.settings),
                        fontFamily = InterFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 34.sp,
                        lineHeight = 40.sp,
                        color = Color.White,
                        maxLines = 1,
                    )
                }
            },
            trailing = {
                // at rest it sits beside the mark like the design, and drops to the search field's middle as the header folds
                SearchAvatar(
                    modifier = Modifier.graphicsLayer {
                        val lift = (SettingsTitleBlockHeight / 2 - SettingsMarkSize / 2).toPx()
                        translationY = -(1f - collapseProgress) * lift
                    },
                    imageUrl = rememberProfilePhotoUrl(),
                    // Profile is out of scope (MAPPING.md), so the avatar opens
                    // the YouTube account page — Mochy's own placement for
                    // account access, and the sign-in lives one tap from there.
                    onClick = { open("youtube_sync") },
                )
            },
        ) {
            SearchField(
                value = query,
                onValueChange = { query = it },
                onSearch = {},
                placeholder = stringResource(R.string.settings_search_placeholder),
                focused = isSearchFocused,
                onFocusChanged = { isSearchFocused = it },
                trailing = if (query.text.isNotEmpty()) {
                    {
                        Icon(
                            painter = painterResource(R.drawable.close),
                            contentDescription = stringResource(R.string.settings_clear_search),
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .clickable { query = TextFieldValue() },
                        )
                    }
                } else {
                    null
                },
            )
        }
    }
}

@Composable
internal fun CategoryBubble(
    title: String,
    iconRes: Int,
    onClick: () -> Unit,
    // lit while its page is open beside the list on a wide screen
    selected: Boolean = false,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(if (selected) SelectedBubbleColor else BubbleColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(IconBgColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = title,
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 17.sp,
                color = Color.White,
            )
        }
    }
}

// the YouTube account card, in the same bubble style as the recommended
// card above the categories: the signed-in name/email, or the sign-in
// prompt when no account is connected.
@Composable
private fun AccountCard(
    signedIn: Boolean,
    accountName: String,
    accountEmail: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(BubbleColor)
            .clickable(onClick = onClick)
            .padding(18.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(IconBgColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(if (signedIn) R.drawable.account else R.drawable.login),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = if (signedIn) {
                        accountName.ifBlank { "Signed in" }
                    } else {
                        stringResource(R.string.settings_account_sign_in)
                    },
                    fontFamily = InterFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color.White
                )
                Text(
                    text = if (signedIn) {
                        accountEmail.ifBlank { "YouTube Music" }
                    } else {
                        stringResource(R.string.settings_account_sign_in_desc)
                    },
                    fontFamily = InterFontFamily,
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.55f)
                )
            }
        }
    }
}

// the Classic / Modern switch: two pills in the same bubble style as the rest
// of the page. Switching to Classic rebranches the host (MainActivity reads
/** Search keys that point at the Interface card (its own title and both modes). */
private val UiModeHighlightKeys = setOf("Interface", "Classic", "Modern")

// AppSettings.uiMode); Modern is where this screen lives.
@Composable
private fun UiModeCard() {
    val uiMode by AppSettings.uiMode.collectAsState()
    val highlight by snapshotFlow { SettingsHighlight.active }.collectAsState(initial = null)
    val bringIntoView = remember { BringIntoViewRequester() }
    // the search index points "Interface", "Classic" and "Modern" here, and
    // the card answers to all three keys (same flash as a settings row)
    LaunchedEffect(Unit) {
        if (SettingsHighlight.pending in UiModeHighlightKeys) {
            SettingsHighlight.pending = null
            SettingsHighlight.active = "Interface"
            delay(320)
            bringIntoView.bringIntoView()
            delay(2400)
            SettingsHighlight.active = null
        }
    }
    val flash by animateFloatAsState(
        if (highlight == "Interface") 1f else 0f,
        tween(durationMillis = 450),
        label = "uiModeFlash",
    )
    val accent = MaterialTheme.colorScheme.primary
    Column(
        modifier = Modifier
            .bringIntoViewRequester(bringIntoView)
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(BubbleColor)
            // after the background so the flash tints it, behind the text
            .drawBehind { drawRect(accent.copy(alpha = 0.22f * flash)) }
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_ui_mode),
            fontFamily = InterFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = Color.White,
        )
        Text(
            text = stringResource(R.string.settings_ui_mode_desc),
            fontFamily = InterFontFamily,
            fontSize = 13.sp,
            color = Color.White.copy(alpha = 0.55f),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            UiMode.entries.forEach { mode ->
                val selected = mode == uiMode
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (selected) Color.White else IconBgColor)
                        .clickable { AppSettings.setUiMode(mode) }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(
                            if (mode == UiMode.CLASSIC) R.string.ui_mode_classic else R.string.ui_mode_modern
                        ),
                        fontFamily = InterFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = if (selected) Color.Black else Color.White,
                    )
                }
            }
        }
    }
}
