// PreferenceKeys.kt — SUBSET of Mochy's PreferenceKeys.kt.
// Only the keys the ported ui/mochy code reads so far. Grow this file as
// screens land; the full Mochy file is 1300+ lines and most of it belongs
// to engine/settings features ChordWave does not have (or has elsewhere).
// These keys live in the Mochy DataStore ("settings"), separate from
// ChordWave's SharedPreferences-backed AppSettings.

package com.music.bitchord.ui.mochy.constants

import android.os.Build
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import java.time.LocalDateTime
import java.time.ZoneOffset

val DisableBlurKey = booleanPreferencesKey("disableBlur")

/**
 * How much work the glass blur does. Low is the lighter glass from before 8.1: the bottom bar
 * blurs only what's right under it with no grain on top, and glass draws its backdrop at a lower
 * resolution. High is the newer frosted glass.
 */
val BlurQualityKey = stringPreferencesKey("blurQuality")

enum class BlurQuality { LOW, HIGH }

// the glass of the nav bar and the mini player; a look module can swap mochy's frost for a glass shader it brings itself
val ChromeGlassStyleKey = stringPreferencesKey("chromeGlassStyle")

enum class ChromeGlassStyle {
    FROSTED,
    // the agsl glass the look that's on brings in its zip
    MODULE,
}

val PlayerSwipeUpFollowsKey = booleanPreferencesKey("playerSwipeUpFollows")
val EnableProgressiveBlurKey = booleanPreferencesKey("enableProgressiveBlur")
val BlurStrengthPxKey = floatPreferencesKey("blurStrengthPx")

// --- added for the Phase 2 Home components (NEEDS.md) ---
enum class GridItemSize {
    BIG,
    SMALL,
}

val CanvasThumbnailAnimationKey = booleanPreferencesKey("canvasThumbnailAnimation")
val CanvasWifiOnlyKey = booleanPreferencesKey("canvasWifiOnly")
val ElementBlurKey = booleanPreferencesKey("elementBlur")
val DataSaverKey = booleanPreferencesKey("dataSaver")
val DataSaverMobileOnlyKey = booleanPreferencesKey("dataSaverMobileOnly")

// --- added for the Phase 2 Home screen (HomeScreen NEEDS.md A8) ---
// RENAMED by the coordinator: Mochy's enum value was MOCHYY ("Mochyy") —
// ChordWave's Modern mode calls the same layout MODERN ("Modern"). Safe:
// no Modern release has persisted the old value yet.
enum class HomeStyle(val displayName: String) {
    MODERN("Modern"),
    LISTEN_NOW("Listen now"),
    HOP_IN("Hop in"),
}

val HomeStyleKey = stringPreferencesKey("homeStyle")
val ProfilePicUriKey = stringPreferencesKey("profilePicUri")

// --- added for the Phase 2 HomeViewModel adapter (phase2-adapter NEEDS.md §(b)) ---
// Verbatim Mochy key names; the adapter reads the four filter flags from the
// store (Mochy's own defaults). QuickPicksKey/OfflineModeKey/
// LastPlayedLikedSongsTimeKey are declared for the Settings screen (Phase 4);
// the adapter does not read them yet (no LAST_LISTEN source, network always
// runs, no liked-play tracking).
val HideExplicitKey = booleanPreferencesKey("hideExplicit")
val HideLibraryImportBannerKey = booleanPreferencesKey("hideLibraryImportBanner")
val HideVideoSongsKey = booleanPreferencesKey("hideVideoSongs")
val HideYoutubeShortsKey = booleanPreferencesKey("hideYoutubeShorts")
val DisableAiFilterKey = booleanPreferencesKey("disableAiFilter")
val OfflineModeKey = booleanPreferencesKey("offlineMode")
val LastPlayedLikedSongsTimeKey = longPreferencesKey("last_played_liked_songs_time")

enum class QuickPicks {
    QUICK_PICKS,
    LAST_LISTEN,
}

val QuickPicksKey = stringPreferencesKey("discover")

// --- added for the Phase 2 Search port (phase2-search NEEDS.md §(a) A5/A6) ---
// Verbatim Mochy key names (PreferenceKeys.kt:347, :106) and region map
// (:1233-1275, 42 entries). The suggestions screen persists the chosen
// region slug via SuggestionRegionKey.
val PauseSearchHistoryKey = booleanPreferencesKey("pauseSearchHistory")
val SuggestionRegionKey = stringPreferencesKey("suggestionRegion")

val SuggestionRegionSlugToName =
    mapOf(
        "system" to "System Default",
        "us" to "Global (USA)",
        "in" to "India",
        "gb" to "United Kingdom",
        "ca" to "Canada",
        "au" to "Australia",
        "jp" to "Japan",
        "kr" to "South Korea",
        "de" to "Germany",
        "fr" to "France",
        "br" to "Brazil",
        "mx" to "Mexico",
        "ru" to "Russia",
        "it" to "Italy",
        "es" to "Spain",
        "nl" to "Netherlands",
        "se" to "Sweden",
        "no" to "Norway",
        "dk" to "Denmark",
        "fi" to "Finland",
        "pl" to "Poland",
        "tr" to "Turkey",
        "za" to "South Africa",
        "ng" to "Nigeria",
        "id" to "Indonesia",
        "my" to "Malaysia",
        "ph" to "Philippines",
        "th" to "Thailand",
        "vn" to "Vietnam",
        "tw" to "Taiwan",
        "hk" to "Hong Kong",
        "sg" to "Singapore",
        "ar" to "Argentina",
        "co" to "Colombia",
        "cl" to "Chile",
        "pe" to "Peru",
        "eg" to "Egypt",
        "sa" to "Saudi Arabia",
        "ae" to "United Arab Emirates",
        "il" to "Israel"
    )

// --- added for the Phase 2 Search push (SearchScreen's pureBlack flag) ---
// Verbatim Mochy key (PreferenceKeys.kt:22). Mochy's host reads it with
// default false and passes it to SearchScreen for the page background.
val PureBlackKey = booleanPreferencesKey("pureBlack")

// --- added for the Phase 2.2 search-results push (NEEDS.md B5) ---
// Verbatim Mochy key (PreferenceKeys.kt:47). GridItemSize enum (BIG/SMALL)
// + rememberEnumPreference are already staged.
val GridItemsSizeKey = stringPreferencesKey("gridItemSize")

// --- added for the Phase 2.3 Library/detail/playlist push ---
// Verbatim Mochy keys + sort enums (playlist NEEDS.md C9, detail NEEDS.md C11).
val SongSortTypeKey = stringPreferencesKey("songSortType")
val SongSortDescendingKey = booleanPreferencesKey("songSortDescending")
val PlaylistSongSortTypeKey = stringPreferencesKey("playlistSongSortType")
val PlaylistSongSortDescendingKey = booleanPreferencesKey("playlistSongSortDescending")
val LikedSongSortTypeKey = stringPreferencesKey("likedSongSortType")
val LikedSongSortDescendingKey = booleanPreferencesKey("likedSongSortDescending")
val PlaylistEditLockKey = booleanPreferencesKey("playlistEditLock")
val AccountNameKey = stringPreferencesKey("accountName")
val YtmSyncKey = booleanPreferencesKey("ytmSync")

enum class SongSortType { CREATE_DATE, NAME, ARTIST, PLAY_TIME }
enum class PlaylistSongSortType { CUSTOM, CREATE_DATE, NAME, ARTIST, PLAY_TIME }
/** Liked songs' own sort: the song sorts plus the order things were imported in, by service. */
enum class LikedSongSortType { CREATE_DATE, IMPORT_ORDER, NAME, ARTIST, PLAY_TIME }
enum class PlaylistSortType { CREATE_DATE, NAME, SONG_COUNT, LAST_UPDATED } // VM-side only

enum class MyTopFilter {
    ALL_TIME, DAY, WEEK, MONTH, YEAR;
    fun toTimeMillis(): Long = when (this) {
        DAY -> LocalDateTime.now().minusDays(1).toInstant(ZoneOffset.UTC).toEpochMilli()
        WEEK -> LocalDateTime.now().minusWeeks(1).toInstant(ZoneOffset.UTC).toEpochMilli()
        MONTH -> LocalDateTime.now().minusMonths(1).toInstant(ZoneOffset.UTC).toEpochMilli()
        YEAR -> LocalDateTime.now().minusMonths(12).toInstant(ZoneOffset.UTC).toEpochMilli()
        ALL_TIME -> 0
    }
}

val ShowArtistDescriptionKey = booleanPreferencesKey("showArtistDescription")
val ShowArtistSubscriberCountKey = booleanPreferencesKey("showArtistSubscriberCount")
val ShowMonthlyListenersKey = booleanPreferencesKey("showMonthlyListeners")
val ShowArtistBackgroundVideoKey = booleanPreferencesKey("showArtistBackgroundVideo")
val ArtistSongSortTypeKey = stringPreferencesKey("artistSongSortType")
val ArtistSongSortDescendingKey = booleanPreferencesKey("artistSongSortDescending")

enum class ArtistSongSortType { CREATE_DATE, NAME, PLAY_TIME }

// ---- Player styles (Phase 3, verbatim from Mochy PreferenceKeys.kt:692-811) ----

enum class PlayerStyle(val displayName: String, val selectorName: String) {
    // the flagship as it is now: frosted cover fade, moving cover colours, the new buttons and bottom bar
    FLAGSHIP("Flagship", "Flagship"),
    // the flagship before its layout refresh, kept as a variant under it in the selector
    CLASSIC("Classic", "Classic"),
    /** the flagship with a record player for its cover, whose arm is the seek bar */
    TURNTABLE("Turntable", "Turntable"),
    // the song's youtube video across the whole screen, turned sideways, with its lyrics in a pane
    VIDEO("Full video", "Full video"),
    // the ipod classic, click wheel and all, browsing the library from inside the player
    IPOD("iPod", "iPod"),
    POSTER("Poster", "Poster"),
    TIMELINE("Timeline", "Timeline"),
    LYRICS("Lyrics", "Lyrics stage"),
    // a big quiet clock for the nightstand, with the song a swipe away, that can stay up over the lock screen
    BEDSIDE("Bedside clock", "Big clock"),
    // the nightstand as two tiles side by side, the music on one and the lyrics or a clock on the other, each scrolled on its own
    BEDSIDE_TILES("Bedside", "Bedside"),
    // a look module's own player, drawn from its layout; kept last so the others keep their places, and only offered while such a module is on
    MODULE("Module", "Module");

    val editable: Boolean get() = this == FLAGSHIP || this == CLASSIC || this == TURNTABLE || this == POSTER || this == IPOD || isBedside

    // the two nightstand styles share their sheet, their night look and their lock screen rules
    val isBedside: Boolean get() = this == BEDSIDE || this == BEDSIDE_TILES

    /** drawn by the flagship's own player (cover, controls, card), not a design of its own */
    val flagshipFamily: Boolean get() = this == FLAGSHIP || this == CLASSIC || this == TURNTABLE

    // the two flagships the full editor works on: cover, middle part, controls and bottom bar
    val hasFullEditor: Boolean get() = this == FLAGSHIP || this == CLASSIC

    // the styles whose menu has a rotate button, each with a landscape layout of its own to turn into
    val turnable: Boolean get() = this == TURNTABLE || this == POSTER || this == TIMELINE || this == LYRICS || this == MODULE || isBedside

    // laid out sideways until someone turns it upright, the way a phone stands on a nightstand
    val startsSideways: Boolean get() = this == BEDSIDE_TILES

    // the looks that read well across a room and stay smooth on a tv's gpu; the rest stay on phones
    val availableOnTv: Boolean get() = this == FLAGSHIP || this == POSTER || this == VIDEO || this == TIMELINE || isBedside

    companion object {
        /** the styles these replaced, so a stored choice lands on its nearest successor */
        fun fromStored(name: String?): PlayerStyle? = when (name) {
            "SIMPLE", "SIMPLE_COMPACT" -> TIMELINE
            "LYRICS_ABSTRACT" -> LYRICS
            // everyone on the old flagship moves to the new one once; picking classic later sticks
            "DEFAULT" -> FLAGSHIP
            else -> null
        }

        // the selector's columns, each look with its variants stacked under it, a swipe down away
        val selectorColumns: List<List<PlayerStyle>> = listOf(
            listOf(FLAGSHIP, CLASSIC),
            listOf(TURNTABLE),
            listOf(VIDEO),
            listOf(IPOD),
            // the tiles first, the single big clock a swipe down under them
            listOf(BEDSIDE_TILES, BEDSIDE),
            listOf(POSTER),
            // the lyrics stage sits under the timeline, the way the classic sits under the flagship
            listOf(TIMELINE, LYRICS),
        )
    }
}

val PlayerStyleKey = stringPreferencesKey("playerStyle")

// the tv's "flagship square": the flagship with its whole cover as a rounded square
val TvFlagshipSquareKey = booleanPreferencesKey("tvFlagshipSquare")
// a phone on its side: the flagship's cover fills the left and melts into the controls, instead of a rounded square beside them
val LandscapeFullCoverKey = booleanPreferencesKey("landscapeFullCover")
// Experimental: the app may turn sideways into its wide layout; off (the default) it stays upright
val LandscapeUnlockedKey = booleanPreferencesKey("landscapeUnlocked")

// the ipod player's body colours, ipod.js's three plus a pitch black one with a dark screen
enum class IpodTheme { SILVER, BLACK, U2, TRUE_DARK }

val IpodThemeKey = stringPreferencesKey("ipodTheme")

// the click wheel's own colour, or whatever the body's theme comes with
enum class IpodWheelColor { THEME, WHITE, BLACK, RED }

val IpodWheelColorKey = stringPreferencesKey("ipodWheelColor")

// the screen grows down to the wheel on tall phones instead of keeping the classic's shape
val IpodTallScreenKey = booleanPreferencesKey("ipodTallScreen")

// the soft shading round the edges of the body
val IpodShadowKey = booleanPreferencesKey("ipodShadow")

/** the Poster's bars round its cover, moving with the music; off until turned on in its editor */
val PosterVisualizerKey = booleanPreferencesKey("posterVisualizer")

// the bedside's clock faces, swiped through sideways on the clock or picked in its sheet
enum class BedsideClockFace { BIG, STACKED, ANALOG, COVER }

val BedsideClockFaceKey = stringPreferencesKey("bedsideClockFace")

// the colour the bedside settles into once it's left alone: none, a warm orange, or the cover's own hue
enum class BedsideNightTint { OFF, ORANGE, COVER }

val BedsideNightTintKey = stringPreferencesKey("bedsideNightTint")

// left alone, the bedside dims and drifts a few pixels now and then so an oled screen doesn't burn in overnight
val BedsideScreenSaverKey = booleanPreferencesKey("bedsideScreenSaver")

// the phone doesn't sleep while the bedside is open, on out of the box
val BedsideKeepAwakeKey = booleanPreferencesKey("bedsideKeepAwake")

// lock the phone with the bedside open and it's still there over the lock screen when the screen wakes
val BedsideLockScreenKey = booleanPreferencesKey("bedsideLockScreen")

// "not now" on the bedside's lock screen prompt, so it asks once and then leaves it to the sheet
val BedsideLockPromptDismissedKey = booleanPreferencesKey("bedsideLockPromptDismissed")

// whether a style's rotate button turned it sideways; the same key as before, so a style someone had turned comes back turned
fun playerStyleRotatedKey(style: PlayerStyle) = booleanPreferencesKey("playerStyleRotated_${style.name}")

// the page each bedside tile was left on, so it opens where it was
val BedsideMusicTilePageKey = intPreferencesKey("bedsideMusicTilePage")
val BedsideInfoTilePageKey = intPreferencesKey("bedsideInfoTilePage")

val HideSettingsDescriptionsKey = booleanPreferencesKey("hideSettingsDescriptions")

val PlayerStatusBarKey = booleanPreferencesKey("playerStatusBar")
const val PlayerStatusBarDefault = false

// ---- Lyrics (Phase 3, verbatim from Mochy PreferenceKeys.kt:924,963-990) ----

val LyricsHighBloomKey = booleanPreferencesKey("lyricsHighBloom")
val LyricsSoftLightKey = booleanPreferencesKey("lyricsSoftLight")

/**
 * How the active lyric line animates. A new key rather than [LyricsAnimationStyleKey]: that one
 * belonged to renderers that no longer exist and may still hold their old values on upgrade.
 */
val LyricsMotionStyleKey = stringPreferencesKey("lyricsMotionStyle")

// risesWords: words rise and lines cascade (style 1's motion), shaded: the agsl glow and wave run on top
enum class LyricsMotionStyle(
    val displayName: String,
    val description: String,
    val risesWords: Boolean,
    val shaded: Boolean,
) {
    /** Style 1: words rise into place as they're sung, held notes swell and glow, lines cascade. */
    MOTION("Style 1", "Words rise as they're sung, long notes swell, lines cascade", risesWords = true, shaded = true),

    /** Style 2: the original - a wave across the sung letters, the list scrolls as one piece. */
    LEGACY("Style 2", "The original wave across the sung letters", risesWords = false, shaded = true),

    // style 1 without the shader, for phones that struggle with it
    BORING("Boring", "Style 1's rise, without the glow", risesWords = true, shaded = false),

    // style 2 without the shader, just the colour sweeping along
    PLAIN("Plain", "Style 2's sweep, without the glow or the wave", risesWords = false, shaded = false),

    // each word fades in whole as it's sung (batch 36)
    FADE("Fade", "Words fade in as they're sung", risesWords = false, shaded = false),

    // each word pops bigger as it lands, then settles (batch 36)
    POP("Pop", "Words pop as they're sung", risesWords = false, shaded = true);

    // the same motion with the shader switched off, used when moving people off the old shader switch
    val withoutShader: LyricsMotionStyle get() = if (risesWords) BORING else PLAIN
}

val LyricsAnimationStyleKey = stringPreferencesKey("lyricsAnimationStyle")
enum class LyricsAnimationStyle {
    NONE,
    FADE,
    GLOW,
    SLIDE,
    KARAOKE,
    APPLE,
    APPLE_V2,
    MOCHY_1,
    LYRICS_V2,
    METRO_LYRICS,
}
// ---- Player editor keys (Phase 3, verbatim from Mochy PreferenceKeys.kt) ----

val ShowPlayerBottomCardKey = booleanPreferencesKey("showPlayerBottomCard")

enum class PlayerCoverStyle {
    EDGE_TO_EDGE,
    SQUARED,
    DISC_SMALL_FULL,
    DISC_SMALL_LABEL,
    DISC_BIG_FULL,
    DISC_BIG_LABEL,
    DISC_ALBUM,
    /** the Turntable player's own: a big record and its arm. Not offered on the flagship. */
    TURNTABLE,
}

/** what the timestamp at the right of the seek bar shows */
enum class PlayerEndTimestamp { END_TIME, TIME_LEFT }

val PlayerEndTimestampKey = stringPreferencesKey("playerEndTimestamp")

/** the four actions in the player's chevron menu, top to bottom, as action names joined by commas */
val PlayerMenuSlotsKey = stringPreferencesKey("playerMenuSlots")

/** the action of the round button beside the chevron (a like, out of the box) */
val PlayerSideButtonKey = stringPreferencesKey("playerSideButton")

/** what sits at the bottom of the player, under the play buttons */
enum class PlayerBottomBarStyle(val displayName: String) {
    /** the lyrics and queue cards, one behind the other */
    STACK("Stack"),
    /** the phone's volume, with the lyrics and queue buttons under it */
    VOLUME("Volume"),
}

val PlayerBottomBarStyleKey = stringPreferencesKey("playerBottomBarStyle")

// the new flagship keeps its own bottom bar, so it shows the volume bar it was designed with even where classic has the cards
val FlagshipBottomBarStyleKey = stringPreferencesKey("flagshipBottomBarStyle")

// the new flagship's own background, the moving cover colours out of the box
val FlagshipBackgroundStyleKey = stringPreferencesKey("flagshipBackgroundStyle")

// how the new flagship lays out its play buttons
enum class FlagshipTransportLayout {
    // back, play and next, wide apart with nothing round them
    THREE,
    // ten seconds back and forward on the outside, play in a white circle
    TEN_SECONDS,
}

val FlagshipTransportLayoutKey = stringPreferencesKey("flagshipTransportLayout")

/** how far the player's controls were held and dragged from their usual place in the editor, in dp (down is positive) */
val PlayerControlsShiftKey = floatPreferencesKey("playerControlsShift")

/** how much taller the player's card was pulled in the editor, in dp: room for readings and the next line */
val PlayerCardGrowthKey = floatPreferencesKey("playerCardGrowth")

/** what the disc's label shows */
enum class DiscLabelContent { CUSTOM, MUSIC_NAME, ARTIST_NAME, ALBUM_NAME, MUSIC_AND_ARTIST, NONE }

val DiscLabelContentKey = stringPreferencesKey("discLabelContent")

val SeekBarStyleKey = stringPreferencesKey("seekBarStyle")

val ButtonStyleKey = stringPreferencesKey("buttonStyle")

val PlayerCoverStyleKey = stringPreferencesKey("playerCoverStyle")


/**
 * Cover style to use when the user has not picked one.
 *
 * Edge-to-edge art leans on the backdrop blur behind it to separate the cover from the controls,
 * and that blur needs API 31. Below that the full-bleed art runs straight under the chrome with
 * nothing softening it, so squared reads far better there. This is only the *default* - an
 * explicit choice in Player customise still wins on every device.
 */
val DefaultPlayerCoverStyle: PlayerCoverStyle
    get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        PlayerCoverStyle.EDGE_TO_EDGE
    } else {
        PlayerCoverStyle.SQUARED
    }

val ShowBigDiscStylesKey = booleanPreferencesKey("showBigDiscStyles")

val DiscRotatingAnimationKey = booleanPreferencesKey("discRotatingAnimation")

val DiscRealisticModeKey = booleanPreferencesKey("discRealisticMode")

val DiscNameKey = stringPreferencesKey("discName")

enum class PlayerTextContent {
    MUSIC_NAME,
    ALBUM_NAME,
    ARTIST_NAME,
    COMBINED,
}

val PlayerTitleSlotKey = stringPreferencesKey("playerTitleSlot")
val PlayerSubtitleSlotKey = stringPreferencesKey("playerSubtitleSlot")

val PlayerButtonsStyleKey = stringPreferencesKey("player_buttons_style")
val PlayerBackgroundStyleKey = stringPreferencesKey("playerBackgroundStyle")
val MiniPlayerBackgroundStyleKey = stringPreferencesKey("miniPlayerBackgroundStyle")
val ShowLyricsKey = booleanPreferencesKey("showLyrics")
val SwipeLyricsKey = booleanPreferencesKey("swipeLyrics")

// ---- Player core/covers keys (Phase 3, verbatim from Mochy PreferenceKeys.kt) ----

val EnableHighRefreshRateKey = booleanPreferencesKey("enableHighRefreshRate")

// there's no limit to the larp
val LarpModeKey = booleanPreferencesKey("larpMode")
val LarpLevelKey = stringPreferencesKey("larpLevel")
// a swipe down anywhere on the open player closes it, not only from its top and cover (#121)
val PlayerSwipeDownAnywhereKey = booleanPreferencesKey("playerSwipeDownAnywhere")

val InfiniteQueueKey = booleanPreferencesKey("infiniteQueue")

// a track that is a YouTube video (from a video row or the Videos search tab) shows that video in the player
val DisableVideoAutoplayKey = booleanPreferencesKey("disableVideoAutoplay")

// the style the player goes back to when the full video mode is left
val VideoModeReturnStyleKey = stringPreferencesKey("videoModeReturnStyle")

/** The player's format badge beside the title (OPUS, AAC, FLAC...) */
val ShowFormatBadgeKey = booleanPreferencesKey("showFormatBadge")
/** The badge also says the stream's sample rate */
val ShowFormatSampleRateKey = booleanPreferencesKey("showFormatSampleRate")

val ForceYtBackdropBlur1500Key = booleanPreferencesKey("forceYtBackdropBlur1500")

// ---- Phase 4 (settings + secondary screens, 15 Nov 2026) ----
// One merge edit: 4A settings-main + 4B settings-advanced + 4C onboarding
// + 4E importsync. Dedups applied: ProxyEnabledKey/ProxyUrlKey (4A+4B),
// YtReportPlaysKey (4A+4E), SetupCompletedKey (4B+4C), YtPullHistoryKey
// (4E+4C) — each added once. Mochy's CrossfadeEnabledKey/
// CrossfadeDurationKey are deliberately NOT here: ChordWave owns
// crossfade in classic AppSettings (4B wires Crossmix to it instead).

// 4A — AppBehaviour settings
val AlbumsFromLikedSongsKey = booleanPreferencesKey("albumsFromLikedSongs")
val DontSkipOnFailureKey = booleanPreferencesKey("dontSkipOnFailure")
val HideUpdateNoticeKey = booleanPreferencesKey("hideUpdateNotice")
val LocalSongAutoMetadataKey = booleanPreferencesKey("local_song_auto_metadata")
val MixWithOtherAppsKey = booleanPreferencesKey("mixWithOtherApps")
val PauseOnMute = booleanPreferencesKey("pauseOnMute")
val PersistentQueueKey = booleanPreferencesKey("persistentQueue")
val PersistentShuffleAcrossQueuesKey = booleanPreferencesKey("persistentShuffleAcrossQueues")
val ProxyEnabledKey = booleanPreferencesKey("proxyEnabled")
val ProxyUrlKey = stringPreferencesKey("proxyUrl")
val ResumeOnBluetoothConnectKey = booleanPreferencesKey("resumeOnBluetoothConnect")
val StopPlaybackOnTaskRemovedKey = booleanPreferencesKey("stopPlaybackOnTaskRemoved")
val SyncLyricsToVideoKey = booleanPreferencesKey("syncLyricsToVideo")
val YtReportPlaysKey = booleanPreferencesKey("ytReportPlays")
val StudioForVideosKey = booleanPreferencesKey("studioForVideos")
val TransportFadeEnabledKey = booleanPreferencesKey("transportFadeEnabled")
val TransportFadeSecondsKey = floatPreferencesKey("transportFadeSeconds")
val TransportEffectKey = stringPreferencesKey("transportEffect")
val SkipBlendKey = booleanPreferencesKey("skipBlend")
val SkipBlendAheadKey = intPreferencesKey("skipBlendAhead")
val StreamFailureTimeoutKey = intPreferencesKey("streamFailureTimeout")
val AppLanguageKey = stringPreferencesKey("appLanguage")
const val SYSTEM_DEFAULT = "SYSTEM_DEFAULT"
const val SkipBlendAheadMax = 4
const val TransportFadeDefaultSeconds = 2f
const val TransportFadeMinSeconds = 0.5f
const val TransportFadeMaxSeconds = 10f
enum class TransportEffect(val label: String) { OFF("None"), ECHO("Echo"), REWIND("Rewind"), TAPE_STOP("Tape stop"), FAR_AWAY("Fade to far"), REVERB("Reverb wash"), FILTER("Filter sweep") }

// 4B — Crossmix
val CrossfadeGaplessKey = booleanPreferencesKey("crossfadeGapless")
val CrossmixTestToolsKey = booleanPreferencesKey("crossmixTestTools")
val CrossmixLengthKey = stringPreferencesKey("crossmixLength")
val CrossmixTempoMatchKey = stringPreferencesKey("crossmixTempoMatch")
val CrossmixStyleKey = stringPreferencesKey("crossmixStyle")
const val CrossmixStyleAuto = "AUTO"
val CrossmixStuntsKey = booleanPreferencesKey("crossmixStunts")
val CrossmixAlwaysMixKey = booleanPreferencesKey("crossmixAlwaysMix")
enum class CrossmixLength(val scale: Float) { SHORT(0.6f), MEDIUM(1f), LONG(1.5f) }
enum class CrossmixTempoMatch(val maxSpeedUp: Float) { OFF(0.006f), GENTLE(0.04f), FULL(0.10f) }

// 4B — Experimental
val ForceMonthlyRecapKey = booleanPreferencesKey("forceMonthlyRecap")
val ForcePlaybackKey = booleanPreferencesKey("forcePlayback")
val ShowStreamSourceToastKey = booleanPreferencesKey("show_stream_source_toast")
val StreamBackendKey = stringPreferencesKey("streamBackend")
enum class StreamBackend { INNERTUBEX, CLASSIC }
val MusicHapticsSensitivityKey = stringPreferencesKey("musicHapticsSensitivity")
val HapticFocusKey = stringPreferencesKey("hapticFocus")
enum class HapticSensitivity { LOW, MEDIUM, HIGH }
enum class HapticFocus { BALANCE, VOCAL, VIBE, BASS }
val SetupCompletedKey = booleanPreferencesKey("setupCompleted")

// 4B — Proxy (read-only until an engine lands; ProxyPasswordKey declared but unread)
val ProxyTypeKey = stringPreferencesKey("proxyType")
val ProxyUsernameKey = stringPreferencesKey("proxyUsername")
val ProxyPasswordKey = stringPreferencesKey("proxyPassword")

// 4E — importsync (YtReportPlaysKey above, shared with 4A)
val YtPullHistoryKey = booleanPreferencesKey("ytPullHistory")
val YtExcludeImportsKey = booleanPreferencesKey("ytExcludeImports")

// 4C — onboarding (SetupCompletedKey above, shared with 4B;
// YtReportPlaysKey/YtPullHistoryKey above, shared with 4E)
val UsernameKey = stringPreferencesKey("username")
val ProfileCardNumberKey = stringPreferencesKey("profileCardNumber")
val ProfileJoinedEpochDayKey = longPreferencesKey("profileJoinedEpochDay")
val SelectedYtmPlaylistsKey = stringPreferencesKey("selectedYtmPlaylists")
val YtSyncLikedSongsKey = booleanPreferencesKey("ytSyncLikedSongs")
