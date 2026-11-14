// MochyNavigator — coordinator-written (15 Nov 2026). The Modern host's
// navigation: a hand-rolled back stack in the spirit of CW's AppNavigator
// (no NavHost — same decision as MAPPING.md), but a SEPARATE stack of
// Mochy route strings, because the Modern tree's destinations (Mochy detail
// pages, section_detail, mood_and_genres, history) are not CW AppRoutes and
// must not pollute the Classic navigator. The Classic host keeps its own
// AppNavigator; the two hosts never share navigation state.
//
// Route strings are Mochy's own ("album/{id}", "artist/{id}",
// "online_playlist/{id}", "local_playlist/{id}", "auto_playlist/liked",
// "youtube_browse/{id}?params=...", "section_detail/{id}",
// "mood_and_genres", "history"), so ported screens call navigate() with the
// same strings Mochy used. Tab switches go through navigateToTab().
// Detail pages render from detailStack in the Modern host; until a detail
// screen is ported (Phase 2), the host shows a placeholder for its route.
package com.music.bitchord.ui.mochy.navigation

import androidx.compose.runtime.Stable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Stable
class MochyNavigator {

    private val _currentTab = MutableStateFlow(0)
    val currentTab: StateFlow<Int> = _currentTab.asStateFlow()

    private val _detailStack = MutableStateFlow<List<String>>(emptyList())
    val detailStack: StateFlow<List<String>> = _detailStack.asStateFlow()

    /** The detail route on screen, or null when the tab itself is. */
    val currentDetail: String? get() = _detailStack.value.lastOrNull()

    /** Opens a Mochy route over the current tab. Tab routes ("home",
     *  "search"/"search_input", "library", "settings") switch tabs instead
     *  of pushing. */
    fun navigate(route: String) {
        when (route) {
            "home", "search", "search_input", "library", "settings" -> navigateToTab(route)
            else -> {
                if (_detailStack.value.lastOrNull() == route) return
                _detailStack.value = _detailStack.value + route
            }
        }
    }

    /** Switches tab and clears any open detail pages (Mochy's
     *  AppNavigation.navigateToTab behavior). Takes a route string, like
     *  Mochy's — the nav bar passes Screens.*.route. Tab order follows
     *  Screens.MainScreens: Home 0, Search 1, Library 2, Settings 3. */
    fun navigateToTab(route: String) {
        val index = when (route) {
            "home" -> 0
            "search", "search_input" -> 1
            "library" -> 2
            "settings" -> 3
            else -> return
        }
        navigateToTabIndex(index)
    }

    /** Same as [navigateToTab] by tab index (for the nav bar, which works
     *  with indices). */
    fun navigateToTabIndex(index: Int) {
        _detailStack.value = emptyList()
        _currentTab.value = index
    }

    /** One step back: the detail page on top is closed. False when the tab
     *  itself is on screen (the host then handles back — e.g. exits). */
    fun popBackStack(): Boolean {
        val stack = _detailStack.value
        if (stack.isEmpty()) return false
        _detailStack.value = stack.dropLast(1)
        return true
    }

    /** Back to the bare tab: every detail page closed. */
    fun clearDetails() {
        _detailStack.value = emptyList()
    }

    // Scroll-to-top: the nav bar's re-tap on the current tab bumps this
    // counter (see AppNavigation.onTabTapped); the tab's screen collects
    // it and animates its list to the top. A counter, not a boolean, so
    // two quick re-taps are two requests.
    private val _scrollToTopRequests = MutableStateFlow(0)
    val scrollToTopRequests: StateFlow<Int> = _scrollToTopRequests.asStateFlow()

    fun requestScrollToTop() {
        _scrollToTopRequests.value += 1
    }

    // Search focus requests (phase2-search NEEDS.md §(a) A7): the
    // double-tap-on-Search-tab signal, mirroring requestScrollToTop
    // exactly. SearchScreen collects it and focuses the field (or
    // re-requests focus when already active). A counter, not a boolean,
    // so two quick double-taps are two requests.
    private val _searchFocusRequests = MutableStateFlow(0)
    val searchFocusRequests: StateFlow<Int> = _searchFocusRequests.asStateFlow()

    fun requestSearchFocus() {
        _searchFocusRequests.value += 1
    }
}
