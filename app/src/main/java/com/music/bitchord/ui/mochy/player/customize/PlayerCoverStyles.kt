// Ported from Mochy Music (GPL-3.0): ui/player/customize/PlayerCoverStyles.kt
// ChordWave Modern UI — Phase 3 player covers & visual styles (staging).
// PlayerCoverStyles.kt

package com.music.bitchord.ui.mochy.player.customize

import androidx.annotation.DrawableRes
import androidx.datastore.preferences.core.Preferences
import com.music.bitchord.R
import com.music.bitchord.ui.mochy.constants.CanvasThumbnailAnimationKey
import com.music.bitchord.ui.mochy.constants.DiscNameKey
import com.music.bitchord.ui.mochy.constants.DiscRealisticModeKey
import com.music.bitchord.ui.mochy.constants.DiscRotatingAnimationKey
import com.music.bitchord.ui.mochy.constants.PlayVideoBackgroundKey
import com.music.bitchord.ui.mochy.constants.PlayerCoverStyle
import com.music.bitchord.ui.mochy.constants.YtVideoBackgroundLyricsSyncKey

val PlayerCoverStyle.isDisc: Boolean
    get() = when (this) {
        PlayerCoverStyle.EDGE_TO_EDGE, PlayerCoverStyle.SQUARED -> false
        else -> true
    }

val PlayerCoverStyle.usesBoxedStage: Boolean
    get() = when (this) {
        PlayerCoverStyle.EDGE_TO_EDGE,
        PlayerCoverStyle.DISC_BIG_FULL,
        PlayerCoverStyle.DISC_BIG_LABEL,
        PlayerCoverStyle.TURNTABLE,
        -> false
        else -> true
    }

val PlayerCoverStyle.isBigDisc: Boolean
    get() = this == PlayerCoverStyle.DISC_BIG_FULL || this == PlayerCoverStyle.DISC_BIG_LABEL

val PlayerCoverStyle.hasTonearm: Boolean
    get() = this == PlayerCoverStyle.DISC_BIG_LABEL

val PlayerCoverStyle.displayName: String
    get() = when (this) {
        PlayerCoverStyle.EDGE_TO_EDGE -> "Full edge to edge"
        PlayerCoverStyle.SQUARED -> "Squared"
        PlayerCoverStyle.DISC_SMALL_FULL -> "Disc Music - inside - small"
        PlayerCoverStyle.DISC_SMALL_LABEL -> "Disc Music - label - small"
        PlayerCoverStyle.DISC_BIG_FULL -> "Disc Music - inside - big"
        PlayerCoverStyle.DISC_BIG_LABEL -> "Disc Music - label - big"
        PlayerCoverStyle.DISC_ALBUM -> "Disc + album Music"
        PlayerCoverStyle.TURNTABLE -> "Turntable"
    }

sealed interface CoverOption {
    val title: String
    val description: String
    @get:DrawableRes val icon: Int

    data class Switch(
        override val title: String,
        override val description: String,
        @param:DrawableRes override val icon: Int,
        val key: Preferences.Key<Boolean>,
        val default: Boolean,
        val inverted: Boolean = false,
        val parent: Switch? = null,
    ) : CoverOption

    data class Text(
        override val title: String,
        override val description: String,
        @param:DrawableRes override val icon: Int,
        val key: Preferences.Key<String>,
        val placeholder: String,
    ) : CoverOption
}

private val AnimatedCanvasOption = CoverOption.Switch(
    title = "Animated canvas",
    description = "Animated canvas ( not from youtube )",
    icon = R.drawable.sparks,
    key = CanvasThumbnailAnimationKey,
    default = true,
)

private val YtVideoOption = CoverOption.Switch(
    title = "Yt video background",
    description = "Plays yt videos on canvas",
    icon = R.drawable.slow_motion_video,
    key = PlayVideoBackgroundKey,
    default = false,
)

private val TimestampMatchingOption = CoverOption.Switch(
    title = "Timestamp matching",
    description = "Based on subtitle",
    icon = R.drawable.lyrics,
    key = YtVideoBackgroundLyricsSyncKey,
    default = true,
    parent = YtVideoOption,
)

private val RotatingAnimationOption = CoverOption.Switch(
    title = "Rotating animation",
    description = "For the disc",
    icon = R.drawable.refresh,
    key = DiscRotatingAnimationKey,
    default = true,
)

private val RealisticModeOption = CoverOption.Switch(
    title = "Realistic mode",
    description = "For the disc",
    icon = R.drawable.palette,
    key = DiscRealisticModeKey,
    default = true,
)

private val DiscNameOption = CoverOption.Text(
    title = "Disc name",
    description = "Printed on the platter",
    icon = R.drawable.edit,
    key = DiscNameKey,
    placeholder = "Leave empty to hide",
)

private val DiscOptions = listOf(RotatingAnimationOption, RealisticModeOption, DiscNameOption)

val PlayerCoverStyle.options: List<CoverOption>
    get() = when (this) {
        PlayerCoverStyle.SQUARED -> listOf(AnimatedCanvasOption)
        PlayerCoverStyle.EDGE_TO_EDGE -> listOf(
            YtVideoOption,
            TimestampMatchingOption,
            AnimatedCanvasOption,
        )
        else -> DiscOptions
    }
