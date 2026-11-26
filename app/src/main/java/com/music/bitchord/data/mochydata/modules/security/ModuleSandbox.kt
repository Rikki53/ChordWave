// keeps module shaders, routes, settings and callers inside strict boundaries so no look or page can ever hijack accounts, secrets or freeze the gpu

package com.music.bitchord.data.mochydata.modules.security

import androidx.datastore.preferences.core.Preferences
import com.music.bitchord.ui.mochy.constants.AlbumsFromLikedSongsKey
import com.music.bitchord.ui.mochy.constants.AnimationSpeedKey
import com.music.bitchord.ui.mochy.constants.AudioNormalizationKey
import com.music.bitchord.ui.mochy.constants.AudioQuality
import com.music.bitchord.ui.mochy.constants.AudioQualityKey
import com.music.bitchord.ui.mochy.constants.AutoLoadMoreKey
import com.music.bitchord.ui.mochy.constants.BlurQuality
import com.music.bitchord.ui.mochy.constants.BlurQualityKey
import com.music.bitchord.ui.mochy.constants.BlurStrengthPxKey
import com.music.bitchord.ui.mochy.constants.CanvasBestQualityKey
import com.music.bitchord.ui.mochy.constants.CanvasThumbnailAnimationKey
import com.music.bitchord.ui.mochy.constants.CanvasWifiOnlyKey
import com.music.bitchord.ui.mochy.constants.CarLyricsKey
import com.music.bitchord.ui.mochy.constants.ChromeGlassStyle
import com.music.bitchord.ui.mochy.constants.ChromeGlassStyleKey
import com.music.bitchord.ui.mochy.constants.CrossmixMode
import com.music.bitchord.ui.mochy.constants.CrossmixModeKey
import com.music.bitchord.ui.mochy.constants.DataSaverKey
import com.music.bitchord.ui.mochy.constants.DataSaverMobileOnlyKey
import com.music.bitchord.ui.mochy.constants.DisableBlurKey
import com.music.bitchord.ui.mochy.constants.DisableVideoAutoplayKey
import com.music.bitchord.ui.mochy.constants.DontSkipOnFailureKey
import com.music.bitchord.ui.mochy.constants.MochyDownloadQuality
import com.music.bitchord.ui.mochy.constants.DownloadQualityKey
import com.music.bitchord.ui.mochy.constants.DynamicThemeKey
import com.music.bitchord.ui.mochy.constants.ElementBlurKey
import com.music.bitchord.ui.mochy.constants.EnableElementBlurAnimatorKey
import com.music.bitchord.ui.mochy.constants.EnableHighRefreshRateKey
import com.music.bitchord.ui.mochy.constants.EnableProgressiveBlurKey
import com.music.bitchord.ui.mochy.constants.EnableSpatialAudioKey
import com.music.bitchord.ui.mochy.constants.FlagshipBackgroundStyleKey
import com.music.bitchord.ui.mochy.constants.FloatingLyricsKey
import com.music.bitchord.ui.mochy.constants.HiFiResamplerKey
import com.music.bitchord.ui.mochy.constants.HiResOutputKey
import com.music.bitchord.ui.mochy.constants.HideExplicitKey
import com.music.bitchord.ui.mochy.constants.HideSettingsDescriptionsKey
import com.music.bitchord.ui.mochy.constants.HideUpdateNoticeKey
import com.music.bitchord.ui.mochy.constants.HideVideoSongsKey
import com.music.bitchord.ui.mochy.constants.HomeStyle
import com.music.bitchord.ui.mochy.constants.HomeStyleKey
import com.music.bitchord.ui.mochy.constants.KeepScreenOn
import com.music.bitchord.ui.mochy.constants.LocalSongAutoMetadataKey
import com.music.bitchord.ui.mochy.constants.LyricsGlowEffectKey
import com.music.bitchord.ui.mochy.constants.LyricsHighBloomKey
import com.music.bitchord.ui.mochy.constants.LyricsLineSpacingKey
import com.music.bitchord.ui.mochy.constants.LyricsMotionStyle
import com.music.bitchord.ui.mochy.constants.LyricsMotionStyleKey
import com.music.bitchord.ui.mochy.constants.LyricsOrderOverrideKey
import com.music.bitchord.ui.mochy.constants.LyricsRomanizeAsMainKey
import com.music.bitchord.ui.mochy.constants.LyricsSoftLightKey
import com.music.bitchord.ui.mochy.constants.LyricsTextSizeKey
import com.music.bitchord.ui.mochy.constants.MixWithOtherAppsKey
import com.music.bitchord.ui.mochy.constants.MusicHapticsEnabledKey
import com.music.bitchord.ui.mochy.constants.OfflineModeKey
import com.music.bitchord.ui.mochy.constants.PauseListenHistoryKey
import com.music.bitchord.ui.mochy.constants.PauseOnMute
import com.music.bitchord.ui.mochy.constants.PauseSearchHistoryKey
import com.music.bitchord.ui.mochy.constants.PersistentQueueKey
import com.music.bitchord.ui.mochy.constants.PersistentShuffleAcrossQueuesKey
import com.music.bitchord.ui.mochy.constants.PlayVideoBackgroundKey
import com.music.bitchord.ui.mochy.constants.PlayerBackgroundStyle
import com.music.bitchord.ui.mochy.constants.PlayerBackgroundStyleKey
import com.music.bitchord.ui.mochy.constants.PlayerNavBarDefault
import com.music.bitchord.ui.mochy.constants.PlayerNavBarKey
import com.music.bitchord.ui.mochy.constants.PlayerStatusBarDefault
import com.music.bitchord.ui.mochy.constants.PlayerStatusBarKey
import com.music.bitchord.ui.mochy.constants.PlayerStyle
import com.music.bitchord.ui.mochy.constants.PlayerStyleKey
import com.music.bitchord.ui.mochy.constants.PlayerSwipeDownAnywhereKey
import com.music.bitchord.ui.mochy.constants.PlayerSwipeUpFollowsKey
import com.music.bitchord.ui.mochy.constants.PlaylistStyle
import com.music.bitchord.ui.mochy.constants.PlaylistStyleKey
import com.music.bitchord.ui.mochy.constants.PreferUsbDacKey
import com.music.bitchord.ui.mochy.constants.PureBlackKey
import com.music.bitchord.ui.mochy.constants.ResumeOnBluetoothConnectKey
import com.music.bitchord.ui.mochy.constants.ShowArtistDescriptionKey
import com.music.bitchord.ui.mochy.constants.ShowArtistSubscriberCountKey
import com.music.bitchord.ui.mochy.constants.ShowArtistVideoKey
import com.music.bitchord.ui.mochy.constants.ShowFormatBadgeKey
import com.music.bitchord.ui.mochy.constants.ShowFormatSampleRateKey
import com.music.bitchord.ui.mochy.constants.ShowMonthlyListenersKey
import com.music.bitchord.ui.mochy.constants.SkipBlendKey
import com.music.bitchord.ui.mochy.constants.SkipSilenceKey
import com.music.bitchord.ui.mochy.constants.SlimNavBarKey
import com.music.bitchord.ui.mochy.constants.StopPlaybackOnTaskRemovedKey
import com.music.bitchord.ui.mochy.constants.StudioForVideosKey
import com.music.bitchord.ui.mochy.constants.SyncLyricsToVideoKey
import com.music.bitchord.ui.mochy.constants.ThumbnailCornerRadiusKey
import com.music.bitchord.ui.mochy.constants.TranslateLyricsKey
import com.music.bitchord.ui.mochy.constants.VideoQuality
import com.music.bitchord.ui.mochy.constants.VideoQualityKey
import com.music.bitchord.ui.mochy.constants.YouTubeCaptionTranslationKey
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/**
 * static gpu-dos check for module agsl/sksl shaders before skia compiles them.
 * blocks unbounded while/do loops, huge for-loop bounds, excessive texture taps, and directives.
 */
internal object ShaderSecurityGuard {

    const val MaxShaderBytes = 64 * 1024
    const val MaxLoops = 6
    const val MaxLoopIterations = 128
    const val MaxEvalCalls = 32

    private val BlockComment = Regex("""/\*[\s\S]*?\*/""")
    private val LineComment = Regex("""//[^\n]*""")
    private val ForbiddenDirective = Regex("""^\s*#\s*(include|pragma|extension|version)\b""", setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE))
    private val UnboundedLoop = Regex("""\b(while|do)\s*[\(\{]""")
    private val ForHeader = Regex("""\bfor\s*\(([^;]*);([^;]*);([^\)]*)\)""")
    private val NumericLiteral = Regex("""-?\d+""")
    private val EvalCall = Regex("""\.\s*eval\s*\(""")

    /** returns null when safe, or a short reason why the shader was refused */
    fun validate(source: String): String? {
        if (source.isBlank()) return "shader is empty"
        if (source.toByteArray(Charsets.UTF_8).size > MaxShaderBytes) return "shader exceeds ${MaxShaderBytes / 1024} KB"
        if (source.any { (it.code < 0x20 && it != '\n' && it != '\r' && it != '\t') || it.code == 0x7F }) {
            return "shader contains illegal control characters"
        }
        val stripped = stripComments(source)
        if (ForbiddenDirective.containsMatchIn(stripped)) {
            return "preprocessor directive not allowed in module shader"
        }
        if (UnboundedLoop.containsMatchIn(stripped)) {
            return "unbounded while/do loop not allowed in module shader"
        }
        val loops = ForHeader.findAll(stripped).toList()
        if (loops.size > MaxLoops) {
            return "too many loops in module shader (max $MaxLoops)"
        }
        for (match in loops) {
            val cond = match.groupValues[2].trim()
            if (cond.isEmpty()) return "unbounded for loop not allowed in module shader"
            val bounds = NumericLiteral.findAll(cond).mapNotNull { it.value.toIntOrNull() }.toList()
            if (bounds.any { abs(it) > MaxLoopIterations }) {
                return "for loop bound exceeds sandbox limit ($MaxLoopIterations)"
            }
        }
        val evals = EvalCall.findAll(stripped).count()
        if (evals > MaxEvalCalls) {
            return "too many shader sample calls (max $MaxEvalCalls)"
        }
        return null
    }

    private fun stripComments(source: String): String =
        source.replace(BlockComment, " ").replace(LineComment, " ")
}

/**
 * strict allowlist of routes a module caller, button, menu item or custom settings page may open.
 * blocks login, backup, proxy, cipher, imports, diagnostics and any unknown or traversed route.
 */
internal object SafeModuleRoutes {

    private val PlainRoutes = setOf(
        // browsing & library
        "home", "search_input", "library", "library/songs", "library/playlists", "library/added",
        "library/local/added", "library/local/albums", "library/local/artists", "library/local/playlists",
        "library/cached/added", "library/cached/albums", "library/cached/artists", "library/cached/playlists",
        "library/imported", "library/imported/playlists", "library/imported/albums",
        "history", "stats", "explore", "mood_and_genres", "new_release", "local_genre",
        "section_detail", "coming_soon",
        // safe settings destinations
        "mochy_settings", "audio_lab", "equalizer", "audio_fx", "audio_stage", "audio_quality",
        "audio_pass", "crossmix_settings", "playback_settings", "now_playing_settings",
        "general_settings", "customisation_settings", "gesture_settings", "storage_settings",
        "integrations", "modules", "module_browser",
    )

    val SafeSettingsRoutes = setOf(
        "mochy_settings", "audio_lab", "equalizer", "audio_fx", "audio_stage", "audio_quality",
        "audio_pass", "crossmix_settings", "playback_settings", "now_playing_settings",
        "general_settings", "customisation_settings", "gesture_settings", "storage_settings",
        "integrations", "modules", "module_browser",
    )

    private val Aliases = mapOf(
        "settings" to "mochy_settings",
        "settings_hub" to "mochy_settings",
        "customisation" to "customisation_settings",
        "customization" to "customisation_settings",
        "appearance" to "customisation_settings",
        "playback" to "playback_settings",
        "now_playing" to "now_playing_settings",
        "nowplaying" to "now_playing_settings",
        "general" to "general_settings",
        "behaviour" to "general_settings",
        "behavior" to "general_settings",
        "app_behaviour" to "general_settings",
        "storage" to "storage_settings",
        "gestures" to "gesture_settings",
        "gesture" to "gesture_settings",
        "crossmix" to "crossmix_settings",
        "transition" to "crossmix_settings",
        "search" to "search_input",
        "playlists" to "library/playlists",
        "songs" to "library/songs",
        "liked" to "auto_playlist/liked",
        "downloaded" to "auto_playlist/downloaded",
        "top" to "top_playlist/50",
        "cached" to "cache_playlist/cached",
        "store" to "module_browser",
        "module_store" to "module_browser",
    )

    private val AutoKinds = setOf("liked", "downloaded", "uploaded")
    private val CacheKinds = setOf("cached", "all")

    // screens where module backgrounds and overlays must never appear
    private val SensitivePrefixes = setOf(
        "login", "youtube_login", "youtube_sync", "backup", "proxy_settings", "cipher_settings",
        "account_import", "import_sync", "import_providers", "import_tunemymusic", "import_progress",
        "discord", "scrobble_service", "setup", "dev_playground", "experimental_settings",
        "playback_diagnostics", "modules", "module", "module_browser",
    )

    /** validates and normalises a route a module asks to open; null when forbidden or malformed */
    fun sanitize(raw: String?): String? {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty() || trimmed.length > 220) return null
        if (trimmed.startsWith("/") || ".." in trimmed || "?" in trimmed || "#" in trimmed || "://" in trimmed || '\\' in trimmed) return null
        if (trimmed.any { it.code < 0x20 || it.code == 0x7F }) return null
        val lower = trimmed.lowercase()
        val resolved = Aliases[lower] ?: trimmed
        val resolvedLower = resolved.lowercase()
        if (resolvedLower in PlainRoutes) return resolvedLower

        val slash = resolved.indexOf('/')
        if (slash <= 0) return null
        val prefix = resolved.substring(0, slash).lowercase()
        val rest = resolved.substring(slash + 1)
        if ('/' in rest) return null
        val arg = ModuleSanitizer.safeNavArg(rest) ?: return null
        return when (prefix) {
            "album", "artist", "local_playlist", "online_playlist" -> "$prefix/$arg"
            "auto_playlist" -> arg.lowercase().takeIf { it in AutoKinds }?.let { "auto_playlist/$it" }
            "cache_playlist" -> arg.lowercase().takeIf { it in CacheKinds }?.let { "cache_playlist/$it" }
            "top_playlist" -> arg.toIntOrNull()?.takeIf { it in 10..500 }?.let { "top_playlist/$it" }
            else -> null
        }
    }

    fun isSafeSettingsRoute(raw: String?): Boolean {
        val clean = sanitize(raw) ?: return false
        return clean in SafeSettingsRoutes
    }

    /** true on auth, backup, proxy, or module-management routes where app-wide module backgrounds stay off */
    fun isSensitiveRoute(route: String?): Boolean {
        if (route.isNullOrBlank()) return false
        val head = route.substringBefore('?').substringBefore('/').lowercase()
        return head in SensitivePrefixes
    }
}

/**
 * strict allowlist of non-sensitive mochy preference keys a custom settings page may show and toggle.
 * accounts, cookies, passwords, tokens, proxy, backups, screenshot flags and dev tools are never here.
 */
internal object SafeAppSettings {

    sealed interface Spec {
        val id: String
        val category: String
        val title: String
        val description: String
    }

    data class Toggle(
        override val id: String,
        override val category: String,
        override val title: String,
        override val description: String,
        val key: Preferences.Key<Boolean>,
        val default: Boolean,
        /** when true, the preference stores the opposite boolean (e.g. DisableBlurKey for "blur") */
        val inverted: Boolean = false,
    ) : Spec

    data class Slider(
        override val id: String,
        override val category: String,
        override val title: String,
        override val description: String,
        val key: Preferences.Key<Float>,
        val default: Float,
        val min: Float,
        val max: Float,
        val step: Float? = null,
        val unit: String = "",
    ) : Spec

    data class Choice(
        override val id: String,
        override val category: String,
        override val title: String,
        override val description: String,
        val key: Preferences.Key<String>,
        val default: String,
        val options: List<Pair<String, String>>,
    ) : Spec

    val All: List<Spec> = listOf(
        // blur & glass
        Toggle("blur", "blur", "Blur", "Frosted glass and backdrop blur across the app", DisableBlurKey, default = false, inverted = true),
        Toggle("progressiveblur", "blur", "Progressive blur", "Smooth gradient blur fade on top and bottom chrome", EnableProgressiveBlurKey, default = true),
        Choice(
            "blurquality", "blur", "Blur quality", "Balance glass sharpness and performance", BlurQualityKey,
            default = BlurQuality.HIGH.name,
            options = listOf(BlurQuality.LOW.name to "Low", BlurQuality.HIGH.name to "High"),
        ),
        Slider("blurstrength", "blur", "Blur strength", "Backdrop glass blur radius", BlurStrengthPxKey, default = 25f, min = 10f, max = 50f, step = 1f, unit = "px"),
        Toggle("elementblur", "blur", "Element blur", "Blur on lyric lines, transitions, and open menus", ElementBlurKey, default = true),
        Choice(
            "bottombar", "blur", "Bottom bar glass", "Frosted glass or active look module shader", ChromeGlassStyleKey,
            default = ChromeGlassStyle.FROSTED.name,
            options = listOf(ChromeGlassStyle.FROSTED.name to "Frosted", ChromeGlassStyle.MODULE.name to "Module"),
        ),

        // customisation
        Toggle("pureblack", "customisation", "Pure black", "True AMOLED black surfaces", PureBlackKey, default = false),
        Toggle("dynamictheme", "customisation", "Dynamic theme", "Tint the app from the current cover art", DynamicThemeKey, default = true),
        Choice(
            "playerstyle", "customisation", "Player style", "Choose the full player layout", PlayerStyleKey,
            default = PlayerStyle.FLAGSHIP.name,
            options = listOf(
                PlayerStyle.FLAGSHIP.name to "Flagship",
                PlayerStyle.TURNTABLE.name to "Turntable",
                PlayerStyle.POSTER.name to "Poster",
                PlayerStyle.TIMELINE.name to "Timeline",
                PlayerStyle.LYRICS.name to "Lyrics stage",
                PlayerStyle.IPOD.name to "iPod",
                PlayerStyle.BEDSIDE_TILES.name to "Bedside",
                PlayerStyle.MODULE.name to "Module",
            ),
        ),
        Choice(
            "homestyle", "customisation", "Home style", "Top layout of the Home tab", HomeStyleKey,
            default = HomeStyle.MOCHYY.name,
            options = HomeStyle.entries.map { it.name to it.displayName },
        ),
        Choice(
            "playliststyle", "customisation", "Playlist style", "Header layout for playlists and albums", PlaylistStyleKey,
            default = PlaylistStyle.FLOW.name,
            options = PlaylistStyle.entries.map { it.name to it.displayName },
        ),
        Slider("coverrounding", "customisation", "Cover rounding", "Corner radius on album art", ThumbnailCornerRadiusKey, default = 16f, min = 0f, max = 45f, step = 1f, unit = "dp"),
        Choice(
            "playerbackground", "customisation", "Player background", "Backdrop style behind controls", PlayerBackgroundStyleKey,
            default = PlayerBackgroundStyle.COVER_GRADIENT.name,
            options = listOf(
                PlayerBackgroundStyle.AMBIENT.name to "Ambient",
                PlayerBackgroundStyle.APPLE_MUSIC.name to "Blurred cover",
                PlayerBackgroundStyle.COVER_GRADIENT.name to "Cover gradient",
                PlayerBackgroundStyle.DARK_GRADIENT.name to "Dark gradient",
                PlayerBackgroundStyle.SOLID.name to "Solid",
            ),
        ),
        Choice(
            "flagshipbackground", "customisation", "Flagship background", "Backdrop style for the Flagship player", FlagshipBackgroundStyleKey,
            default = PlayerBackgroundStyle.AMBIENT.name,
            options = listOf(
                PlayerBackgroundStyle.AMBIENT.name to "Ambient",
                PlayerBackgroundStyle.APPLE_MUSIC.name to "Blurred cover",
                PlayerBackgroundStyle.COVER_GRADIENT.name to "Cover gradient",
                PlayerBackgroundStyle.DARK_GRADIENT.name to "Dark gradient",
                PlayerBackgroundStyle.SOLID.name to "Solid",
            ),
        ),
        Toggle("animations", "customisation", "Animations", "UI motion and smooth transitions", EnableElementBlurAnimatorKey, default = true),
        Slider("animationspeed", "customisation", "Animation speed", "Scale transition duration", AnimationSpeedKey, default = 1f, min = 0.5f, max = 2f, step = 0.1f, unit = "x"),
        Toggle("slimnavbar", "customisation", "Slim navigation bar", "Compact bottom tab pill", SlimNavBarKey, default = false),
        Toggle("playerstatusbar", "customisation", "Top bar on player", "Show status bar over the open player", PlayerStatusBarKey, default = PlayerStatusBarDefault),
        Toggle("playernavbar", "customisation", "Nav bar on player", "Show system navigation bar under the open player", PlayerNavBarKey, default = PlayerNavBarDefault),
        Toggle("swipedownanywhere", "customisation", "Swipe down anywhere to close", "Pull down anywhere on the player to dismiss", PlayerSwipeDownAnywhereKey, default = false),
        Toggle("swipeupfollows", "customisation", "Open with your finger", "Drag the mini player smoothly with your thumb", PlayerSwipeUpFollowsKey, default = false),
        Toggle("highrefreshrate", "customisation", "High refresh rate", "Use 90/120 Hz during smooth player animations", EnableHighRefreshRateKey, default = true),

        // now playing & lyrics
        Choice(
            "lyricsmotion", "lyrics", "Lyrics animation", "How the active line animates", LyricsMotionStyleKey,
            default = LyricsMotionStyle.MOTION.name,
            options = LyricsMotionStyle.entries.map { it.name to it.displayName },
        ),
        Toggle("lyricsbloom", "lyrics", "High quality bloom", "Extra soft glow pass on active lyrics", LyricsHighBloomKey, default = true),
        Toggle("lyricssoftlight", "lyrics", "Tinted lyrics", "Softly tint inactive lyrics with cover tones", LyricsSoftLightKey, default = true),
        Toggle("lyricsglow", "lyrics", "Lyrics glow", "Glow highlight on sung words", LyricsGlowEffectKey, default = true),
        Slider("lyricstextsize", "lyrics", "Lyrics text size", "Base lyric line size", LyricsTextSizeKey, default = 26f, min = 14f, max = 44f, step = 1f, unit = "sp"),
        Slider("lyricslinespacing", "lyrics", "Lyrics line spacing", "Vertical space between lyric lines", LyricsLineSpacingKey, default = 1.2f, min = 0.8f, max = 2f, step = 0.1f, unit = "x"),
        Toggle("floatinglyrics", "lyrics", "Floating lyrics bubble", "Show synced lyrics over other apps", FloatingLyricsKey, default = false),
        Toggle("carlyrics", "lyrics", "Car lyrics", "Show sung lyric line on Android Auto", CarLyricsKey, default = true),
        Toggle("romanizeasmain", "lyrics", "Romanisation as main line", "Show romanised text as the primary lyric line", LyricsRomanizeAsMainKey, default = false),
        Toggle("translatelyrics", "lyrics", "Translate lyrics", "Show translated lines below lyrics", TranslateLyricsKey, default = false),
        Toggle("youtubecaptiontranslation", "lyrics", "Translations from YouTube", "Use uploaded YouTube caption translations", YouTubeCaptionTranslationKey, default = true),
        Toggle("synclyricstovideo", "lyrics", "Sync lyrics to music videos", "Align lyric timestamps with video intros", SyncLyricsToVideoKey, default = false),
        Toggle("formatbadge", "now_playing", "Audio format badge", "Show codec badge beside the song title", ShowFormatBadgeKey, default = true),
        Toggle("formatsamplerate", "now_playing", "Show sample rate", "Include sample rate in the format badge", ShowFormatSampleRateKey, default = false),

        // canvas & video
        Toggle("animatedcanvas", "canvas", "Animated canvas", "Looping artwork motion on the player", CanvasThumbnailAnimationKey, default = true),
        Toggle("canvaswifionly", "canvas", "Canvas on Wi-Fi only", "Only stream animated covers over Wi-Fi", CanvasWifiOnlyKey, default = false),
        Toggle("canvasbestquality", "canvas", "Sharpest animated covers", "Prefer highest resolution motion covers", CanvasBestQualityKey, default = false),
        Toggle("youtubebackground", "video", "YouTube video background", "Show matching YouTube video behind the player", PlayVideoBackgroundKey, default = false),
        Toggle("disablevideoautoplay", "video", "Disable video autoplay", "Keep music videos on cover art instead of autoplaying", DisableVideoAutoplayKey, default = false),
        Toggle("studioforvideos", "video", "Studio audio for music videos", "Prefer studio upload over music video audio", StudioForVideosKey, default = false),
        Choice(
            "videoquality", "video", "Video quality", "Preferred resolution for music videos", VideoQualityKey,
            default = VideoQuality.AUTO.name,
            options = listOf(
                VideoQuality.AUTO.name to "Auto",
                VideoQuality.Q2160.name to "2160p",
                VideoQuality.Q1440.name to "1440p",
                VideoQuality.Q1080.name to "1080p",
                VideoQuality.Q720.name to "720p",
                VideoQuality.Q480.name to "480p",
                VideoQuality.Q360.name to "360p",
            ),
        ),

        // playback
        Toggle("skipsilence", "playback", "Skip silence", "Trim quiet gaps during playback", SkipSilenceKey, default = false),
        Toggle("audionormalization", "playback", "Audio normalization", "Balance loudness across songs", AudioNormalizationKey, default = true),
        Toggle("persistentqueue", "playback", "Save player's last state", "Restore queue and track on relaunch", PersistentQueueKey, default = true),
        Toggle("persistentshuffle", "playback", "Keep shuffle on", "Keep shuffle enabled across new queues", PersistentShuffleAcrossQueuesKey, default = false),
        Toggle("autoloadmore", "playback", "Auto load more", "Continue radio when the queue ends", AutoLoadMoreKey, default = true),
        Toggle("mixwithotherapps", "playback", "Play alongside other apps", "Don't pause when other apps play sound", MixWithOtherAppsKey, default = false),
        Toggle("resumeonbluetooth", "playback", "Resume on Bluetooth", "Auto-play when headphones connect", ResumeOnBluetoothConnectKey, default = false),
        Toggle("pauseonmute", "playback", "Pause when muted", "Pause music when media volume hits zero", PauseOnMute, default = false),
        Toggle("keepplayingwhenclosed", "playback", "Keep playing when closed", "Continue playback after swiping app away", StopPlaybackOnTaskRemovedKey, default = false, inverted = true),
        Toggle("dontskiponfailure", "playback", "Don't skip on failure", "Stay on track instead of skipping failed streams", DontSkipOnFailureKey, default = false),
        Toggle("keepscreenon", "playback", "Keep screen on", "Prevent sleep while the player is open", KeepScreenOn, default = false),
        Choice(
            "crossmixmode", "playback", "Transition mode", "Crossmix or crossfade between tracks", CrossmixModeKey,
            default = CrossmixMode.CROSSMIX.name,
            options = listOf(
                CrossmixMode.CROSSMIX.name to "Crossmix",
                CrossmixMode.AUTO_CROSSFADE.name to "Auto crossfade",
                CrossmixMode.MANUAL_CROSSFADE.name to "Manual crossfade",
                CrossmixMode.OFF.name to "Off",
            ),
        ),
        Toggle("skipblend", "playback", "Blend skips", "Smoothly crossfade manual next/previous skips", SkipBlendKey, default = false),

        // app behaviour
        Toggle("offlinemode", "behaviour", "Offline mode", "Play only downloaded and cached tracks", OfflineModeKey, default = false),
        Toggle("datasaver", "behaviour", "Data saver", "Use lighter audio and pause video/canvas", DataSaverKey, default = false),
        Toggle("datasavermobileonly", "behaviour", "Data saver on mobile only", "Only activate data saver on cellular networks", DataSaverMobileOnlyKey, default = true),
        Toggle("hideexplicit", "behaviour", "Hide explicit", "Filter explicit songs from browse and artists", HideExplicitKey, default = false),
        Toggle("hidevideosongs", "behaviour", "Hide video songs", "Filter music videos from artist shelves", HideVideoSongsKey, default = false),
        Toggle("albumsfromlikedsongs", "behaviour", "Albums from liked songs", "Show albums of liked tracks in Library", AlbumsFromLikedSongsKey, default = false),
        Toggle("localautometadata", "behaviour", "Local song auto metadata", "Match local files with artwork and lyrics", LocalSongAutoMetadataKey, default = false),
        Toggle("hidesettingsdescriptions", "behaviour", "Hide settings descriptions", "Show titles only in settings lists", HideSettingsDescriptionsKey, default = false),
        Toggle("hideupdatenotice", "behaviour", "Hide update notice", "Suppress update banner popups", HideUpdateNoticeKey, default = false),
        Toggle("pausesearchhistory", "behaviour", "Pause search history", "Stop recording new searches", PauseSearchHistoryKey, default = false),
        Toggle("pauselistenhistory", "behaviour", "Pause listen history", "Stop recording listening history", PauseListenHistoryKey, default = false),
        Toggle("lyricsorderoverride", "behaviour", "Override lyrics sorting", "Strictly follow provider order", LyricsOrderOverrideKey, default = false),
        Toggle("showartistdescription", "behaviour", "Artist description", "Show artist bio on artist pages", ShowArtistDescriptionKey, default = true),
        Toggle("showartistsubscribers", "behaviour", "Artist subscriber count", "Show subscriber counts on artist pages", ShowArtistSubscriberCountKey, default = true),
        Toggle("showmonthlylisteners", "behaviour", "Monthly listeners", "Show monthly listener counts", ShowMonthlyListenersKey, default = true),
        Toggle("showartistvideo", "behaviour", "Artist banner motion", "Play editorial motion on artist banners", ShowArtistVideoKey, default = true),

        // audio lab
        Choice(
            "audioquality", "audio_lab", "Audio quality", "Preferred streaming tier", AudioQualityKey,
            default = AudioQuality.AUTO.name,
            options = listOf(
                AudioQuality.AUTO.name to "Auto",
                AudioQuality.LOW.name to "Low",
                AudioQuality.MEDIUM.name to "Medium",
                AudioQuality.HIGH.name to "High",
                AudioQuality.LOSSLESS.name to "Lossless",
                AudioQuality.HI_RES_LOSSLESS.name to "Hi-Res",
                AudioQuality.DOLBY_ATMOS.name to "Dolby Atmos",
            ),
        ),
        Choice(
            "downloadquality", "audio_lab", "Download quality", "Quality for YouTube downloads", DownloadQualityKey,
            default = DownloadQuality.HIGH.name,
            options = DownloadQuality.entries.map { it.name to it.title },
        ),
        Toggle("preferusbdac", "audio_lab", "Prefer USB DAC", "Route playback to a connected USB DAC", PreferUsbDacKey, default = false),
        Toggle("hiresoutput", "audio_lab", "Hi-res float output", "Keep 24-bit precision on supported outputs", HiResOutputKey, default = false),
        Toggle("hifiresampler", "audio_lab", "Hi-fi resampler", "Use high-quality sinc resampling", HiFiResamplerKey, default = false),
        Toggle("spatialaudio", "audio_lab", "Spatial audio", "Enable spatial audio processing", EnableSpatialAudioKey, default = false),
        Toggle("musichaptics", "audio_lab", "Music haptics", "Tactile vibration synced to playback", MusicHapticsEnabledKey, default = false),
    )

    private val ById: Map<String, Spec> = All.associateBy { it.id }

    private val KeyAliases = mapOf(
        "disableblur" to "blur",
        "enableprogressiveblur" to "progressiveblur",
        "blurstrengthpx" to "blurstrength",
        "thumbnailcornerradius" to "coverrounding",
        "lyricsmotionstyle" to "lyricsmotion",
        "lyricshighbloom" to "lyricsbloom",
        "lyricsgloweffect" to "lyricsglow",
        "canvas" to "animatedcanvas",
        "canvasthumbnailanimation" to "animatedcanvas",
        "playvideobackground" to "youtubebackground",
        "showformatbadge" to "formatbadge",
        "showformatsamplerate" to "formatsamplerate",
        "enablehighrefreshrate" to "highrefreshrate",
        "normalization" to "audionormalization",
        "crossmix" to "crossmixmode",
        "behavior" to "behaviour",
        "general" to "behaviour",
        "appearance" to "customisation",
        "customization" to "customisation",
        "nowplaying" to "now_playing",
        "audiolab" to "audio_lab",
    )

    val Categories: Set<String> = setOf("blur", "customisation", "now_playing", "lyrics", "canvas", "video", "playback", "behaviour", "audio_lab")

    fun normalizeKey(raw: String?): String = raw?.trim()?.lowercase()?.replace("_", "")?.replace("-", "").orEmpty()

    fun find(raw: String?): Spec? {
        val clean = normalizeKey(raw)
        if (clean.isEmpty() || SecretGuard.looksLikeSecretProbe(clean)) return null
        return ById[clean] ?: KeyAliases[clean]?.let(ById::get)
    }

    fun normalizeCategory(raw: String?): String? {
        val clean = raw?.trim()?.lowercase()?.replace("-", "_") ?: return null
        val mapped = when (clean) {
            "appearance", "customization" -> "customisation"
            "behavior", "general", "app_behaviour", "app_behavior" -> "behaviour"
            "nowplaying" -> "now_playing"
            "audiolab", "audio" -> "audio_lab"
            else -> clean
        }
        return mapped.takeIf { it in Categories }
    }

    fun byCategory(category: String): List<Spec> {
        val cat = normalizeCategory(category) ?: return emptyList()
        return when (cat) {
            "now_playing" -> All.filter { it.category in setOf("now_playing", "lyrics", "canvas", "video") }
            "customisation" -> All.filter { it.category in setOf("customisation", "blur") }
            else -> All.filter { it.category == cat }
        }
    }
}

/**
 * runtime rate-limiting, sheet-nesting cap, and final-url secret check for module callers.
 */
internal object ModuleActionGuard {

    const val MaxModuleSheetDepth = 2
    private const val WindowMs = 1_000L
    private const val MaxTriggersPerWindow = 6

    private val windows = ConcurrentHashMap<String, ArrayDeque<Long>>()

    fun allowTrigger(moduleId: String, nowMs: Long = System.currentTimeMillis()): Boolean {
        val queue = windows.computeIfAbsent(moduleId) { ArrayDeque() }
        synchronized(queue) {
            while (queue.isNotEmpty()) {
                val oldest = queue.peekFirst() ?: break
                if (nowMs - oldest <= WindowMs) break
                queue.removeFirst()
            }
            if (queue.size >= MaxTriggersPerWindow) return false
            queue.addLast(nowMs)
            return true
        }
    }

    fun isSafeFinalUrl(url: String?): Boolean =
        ModuleUrlGuard.isSafeRemoteUrl(url) && !SecretGuard.containsSecret(url)

    fun resetForTests() {
        windows.clear()
    }
}
