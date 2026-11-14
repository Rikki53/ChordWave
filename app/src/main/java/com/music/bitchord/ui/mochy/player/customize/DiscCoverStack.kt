// Ported from Mochy Music (GPL-3.0): ui/player/customize/DiscCoverStack.kt
// ChordWave Modern UI — Phase 3 player covers & visual styles (staging).
// DiscCoverStack.kt

package com.music.bitchord.ui.mochy.player.customize

import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.rememberTextMeasurer
import com.music.bitchord.ui.mochy.constants.DiscLabelContent
import com.music.bitchord.ui.mochy.constants.DiscNameKey
import com.music.bitchord.ui.mochy.constants.DiscRealisticModeKey
import com.music.bitchord.ui.mochy.constants.DiscRotatingAnimationKey
import com.music.bitchord.ui.mochy.constants.PlayerCoverStyle
import com.music.bitchord.ui.mochy.player.CarouselSong
import com.music.bitchord.ui.mochy.utils.rememberPreference
import kotlinx.coroutines.isActive
import timber.log.Timber

private const val SpinDegreesPerSecond = 26f

@Composable
fun DiscCoverStack(
    style: PlayerCoverStyle,
    artworkUrl: String?,
    mediaId: String,
    isPlaying: Boolean,
    spinActive: Boolean,
    editMode: Boolean,
    modifier: Modifier = Modifier,
    /**
     * the player's row of songs the swipe and skips run along (see SongCarousel). Without one -
     * the editor, the previews - there's just the playing song's record, still.
     */
    carousel: com.music.bitchord.ui.mochy.player.SongCarousel? = null,
    /** how much colour the records' pictures keep: greyer while paused */
    saturationProvider: () -> Float = { 1f },
) {
    val (rotatingEnabled) = rememberPreference(DiscRotatingAnimationKey, defaultValue = true)
    val (realistic) = rememberPreference(DiscRealisticModeKey, defaultValue = true)
    val (customName) = rememberPreference(DiscNameKey, defaultValue = "")
    val labelContent by com.music.bitchord.ui.mochy.utils.rememberEnumPreference(
        com.music.bitchord.ui.mochy.constants.DiscLabelContentKey,
        DiscLabelContent.CUSTOM,
    )
    val connection = com.music.bitchord.ui.mochy.playback.LocalPlayerConnection.current
    val track = connection?.uiState?.trackInfo?.collectAsState()?.value

    // what a record's label says, as chosen in the cover editor
    fun nameFor(slot: CarouselSong): String = when (labelContent) {
        DiscLabelContent.CUSTOM -> customName
        DiscLabelContent.MUSIC_NAME -> slot.title
        DiscLabelContent.ARTIST_NAME -> slot.artist
        DiscLabelContent.ALBUM_NAME -> slot.album
        DiscLabelContent.MUSIC_AND_ARTIST -> listOf(slot.title, slot.artist).filter { it.isNotBlank() }.joinToString(" · ")
        DiscLabelContent.NONE -> ""
    }

    // The row: the songs either side of the playing one, from the player's queue. One source for
    // the records, the cover, the title and the background, so the record coming in is exactly the
    // one that then plays.
    val row = carousel?.takeIf { !editMode }?.slots.orEmpty()
    val candidates = row.ifEmpty {
        listOf(
            CarouselSong(
                uid = 0,
                mediaId = mediaId,
                artworkUrl = artworkUrl,
                title = track?.title.orEmpty(),
                artist = track?.artist.orEmpty(),
                album = track?.album.orEmpty(),
                step = carousel?.step ?: 0,
            )
        )
    }
    fun isCurrent(slot: CarouselSong) = carousel == null || slot.step == carousel.step

    // A record new to the row (the next one along, after a skip) joins it once the row has come to
    // rest, not while it slides: building it and drawing its surface into its layer is real work,
    // and done at rest - off screen - nothing on screen waits for it.
    val resting by remember(carousel) {
        derivedStateOf { carousel == null || carousel.position == carousel.step.toFloat() }
    }
    val shownBefore = remember { HashSet<Int>() }
    val slots = candidates.filter { isCurrent(it) || it.uid in shownBefore || resting }
    shownBefore.clear()
    slots.forEach { shownBefore += it.uid }

    val angle = remember { mutableFloatStateOf(0f) }
    val lastFrameNanos = remember { mutableLongStateOf(0L) }
    val spinning = rotatingEnabled && isPlaying && spinActive
    LaunchedEffect(spinning) {
        if (!spinning) {
            lastFrameNanos.longValue = 0L
            return@LaunchedEffect
        }
        while (isActive) {
            withInfiniteAnimationFrameNanos { frameTimeNanos ->
                val previous = lastFrameNanos.longValue
                lastFrameNanos.longValue = frameTimeNanos
                if (previous != 0L) {
                    val deltaSeconds = (frameTimeNanos - previous) / 1_000_000_000f
                    angle.floatValue = (angle.floatValue + SpinDegreesPerSecond * deltaSeconds).mod(360f)
                }
            }
        }
    }

    val measurer = rememberTextMeasurer(cacheSize = 96)

    Box(modifier = modifier) {
        // the Turntable's arm stands on a slab the record sits over
        if (style == PlayerCoverStyle.TURNTABLE) TurntableBase(Modifier.fillMaxSize())

        for (slot in slots) {
            key(slot.uid) {
                // a record keeps the picture it came in with, so it never reloads as it takes the middle
                val art = remember { arrayOfNulls<String>(1) }
                if (art[0] == null) art[0] = slot.artworkUrl
                val shownArt = art[0] ?: slot.artworkUrl
                // the album card wants the album's cover, which isn't always the song's own picture
                val albumArtwork = if (style.hasAlbumCard) rememberAlbumArtwork(slot.mediaId, shownArt) else shownArt
                DiscCover(
                    style = style,
                    artworkUrl = shownArt,
                    discName = nameFor(slot),
                    albumArtworkUrl = albumArtwork,
                    realistic = realistic,
                    editMode = editMode && isCurrent(slot),
                    rotationProvider = { angle.floatValue },
                    textMeasurer = measurer,
                    saturationProvider = saturationProvider,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (carousel == null) Modifier else Modifier.graphicsLayer {
                                translationX = carousel.offsetOf(slot) * discSpacingPx(style, size)
                            }
                        ),
                )
            }
        }

        if (style == PlayerCoverStyle.TURNTABLE) {
            TurntableArm(
                active = spinActive,
                interactive = !editMode,
                awayProvider = { carousel?.away ?: 0f },
                modifier = Modifier.fillMaxSize(),
            )
        } else if (style.hasTonearm) {
            DiscTonearm(
                liftProvider = { ((carousel?.away ?: 0f) * 3f).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * How far apart the records in [style]'s row sit, for a box [size]: enough that, at rest, the ones
 * either side are just past the screen's edges (the box can be inset from them a little).
 */
internal fun discSpacingPx(style: PlayerCoverStyle, size: androidx.compose.ui.geometry.Size): Float {
    val (center, radius) = discCircleIn(style, size)
    val margin = size.width * 0.08f
    return kotlin.math.max(size.width - center.x + radius, center.x + radius) + margin
}

private val PlayerCoverStyle.hasAlbumCard: Boolean get() = discGeometryFor(this).albumCard != null

/**
 * The cover of the album [mediaId] is on.
 *
 * TODO(Phase 3 adapter): the lookup is NOT ported — Mochy reads it from the
 * library first (LocalDatabase.song/album) then YouTube's album page
 * (YouTube.album), and neither the database nor the InnerTube album call is
 * wired in ChordWave yet (see NEEDS.md §(b)). Until the adapter lands, the
 * album card shows the song's own artwork ([fallback]).
 */
@Composable
internal fun rememberAlbumArtwork(mediaId: String, fallback: String?): String? {
    LaunchedEffect(mediaId) {
        Timber.w("rememberAlbumArtwork: album lookup not wired yet; using the song artwork as the album card")
    }
    return fallback
}
