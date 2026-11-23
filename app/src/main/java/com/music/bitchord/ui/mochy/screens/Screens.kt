// Screens.kt

package com.music.bitchord.ui.mochy.screens

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.music.bitchord.R

@Immutable
sealed class Screens(
    @StringRes val titleId: Int,
    val iconIdInactive: Int,
    val iconIdActive: Int,
    val route: String,
) {
    object Home : Screens(
        titleId = R.string.home,
        iconIdInactive = R.raw.home,
        iconIdActive = R.raw.home_filled,
        route = "home"
    )

    object Search : Screens(
        titleId = R.string.search,
        iconIdInactive = R.raw.search,
        iconIdActive = R.raw.search_filled,
        route = "search_input"
    )

    // Round 2, item 7 (Classic parity): Classic's bar is Home, Explore,
    // Library, Search, Radio — Explore and Radio join the bar, in
    // Classic's order, and Settings leaves it.
    object Explore : Screens(
        titleId = R.string.explore,
        // Mochy ships only the outlined compass (no filled twin); the lit
        // tab still reads through the highlight pill and full alpha.
        iconIdInactive = R.drawable.explore_outlined,
        iconIdActive = R.drawable.explore_outlined,
        route = "explore"
    )

    object Radio : Screens(
        titleId = R.string.radio_title,
        // ChordWave's own broadcast icon; one drawing, both states, as Explore.
        iconIdInactive = R.drawable.radio,
        iconIdActive = R.drawable.radio,
        route = "radio"
    )

    object Library : Screens(
        titleId = R.string.your_library,
        iconIdInactive = R.raw.lib,
        iconIdActive = R.raw.lib_fill,
        route = "library"
    )

    // Not a tab any more (Classic has no Settings tab): kept so the
    // settings route, title and icons stay defined in one place. Settings
    // opens as a detail — the profile avatar in Home/Search/Library/
    // Artist calls navigateToTab("settings"), which MochyNavigator
    // answers by pushing the settings page over the current tab.
    object Settings : Screens(
        titleId = R.string.settings,
        // ChordWave's gear (nav_settings_*), not the Mochy mark: the
        // nav_mochy_* drawables were deleted with the rebrand.
        iconIdInactive = R.drawable.nav_settings_outline,
        iconIdActive = R.drawable.nav_settings_filled,
        route = "settings"
    )

    companion object {
        // Classic's set, in Classic's order (MainActivity's tabs list).
        val MainScreens = listOf(Home, Explore, Library, Search, Radio)
    }
}

enum class NavigationTab {
    HOME, SEARCH
}

enum class DarkMode {
    AUTO, ON, OFF
}

enum class LyricsPosition {
    LEFT, CENTER, RIGHT
}

enum class OptionStats {
    WEEKS, MONTHS, YEARS, CONTINUOUS
}
