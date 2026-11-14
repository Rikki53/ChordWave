// Ported from Mochy Music (GPL-3.0): ui/component/AppNavigation.kt
// Adapted for ChordWave: the navigation-controller hooks are replaced by MochyNavigator
// (see NEEDS.md in this staging dir for every adaptation).

package com.music.bitchord.ui.mochy.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.stringResource
import com.music.bitchord.ui.mochy.LocalGlassState
import com.music.bitchord.ui.mochy.navigation.MochyNavigator
import com.music.bitchord.ui.mochy.screens.Screens
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.size

@Immutable
private data class NavItemState(
    val isSelected: Boolean,
    val iconRes: Int
)

// Mochy's navigateToTab extension is NOT ported: tab switching lives on
// MochyNavigator.navigateToTab. One behaviour does not carry over — Mochy restored the tab's
// saved page on re-entry, while MochyNavigator keeps no per-tab stacks, so re-entering a tab
// always lands on the tab's own page. See NEEDS.md.

/**
 * a tap on a tab. another tab: switch to it (any open detail pages close — MochyNavigator keeps
 * no per-tab saved stacks, so there is no page to come back to). the tab you're in: from one of
 * its pages, back to the tab's own page (as every tab bar does); already there, the page scrolls
 * to its top.
 *
 * Adapted from Mochy's original: the scroll-to-top signal is the navigator's counter now
 * ([MochyNavigator.requestScrollToTop], which the tab's screen collects), not a saved-state flag
 * plus a callback, and going back to the tab's own page is [MochyNavigator.clearDetails]
 * instead of popping to the tab's entry. The outcome is the same either way.
 */
fun MochyNavigator.onTabTapped(screen: Screens, isSelectedTab: Boolean) {
    if (!isSelectedTab) {
        navigateToTab(screen.route)
        return
    }
    if (detailStack.value.isEmpty()) {
        requestScrollToTop()
        return
    }
    clearDetails()
}

@Stable
private fun isRouteSelected(currentRoute: String?, screenRoute: String, navigationItems: List<Screens>): Boolean {
    if (currentRoute == null) return false
    if (currentRoute == screenRoute) return true
    return navigationItems.any { it.route == screenRoute } &&
           currentRoute.startsWith("$screenRoute/")
}

private val RouteOwners: List<Pair<String, String>> = listOf(
    "search/" to "search_input",
    "genre/" to "search_input",
    "youtube_browse/" to "search_input",
    "browse/" to "search_input",
    "library/" to "library",
    "cipher_settings" to "settings",
    "playback_diagnostics" to "settings",
    "mochy_settings" to "settings",
    "general_settings" to "settings",
    "experimental_settings" to "settings",
    "developer_space" to "settings",
    "developer_playground" to "settings",
    "storage_settings" to "settings",
    "import_sync" to "settings",
    "import_providers" to "settings",
    "import_tunemymusic" to "settings",
    "import_progress" to "settings",
    "youtube_sync" to "settings",
    "youtube_login" to "settings",
    "account_import/" to "settings",
    "equalizer" to "settings",
)

private fun owningTabRoute(currentRoute: String?, navigationItems: List<Screens>): String? {
    if (currentRoute == null) return null
    navigationItems.firstOrNull { isRouteSelected(currentRoute, it.route, navigationItems) }
        ?.let { return it.route }
    RouteOwners.firstOrNull { (prefix, _) ->
        currentRoute == prefix || currentRoute.startsWith(prefix)
    }?.let { return it.second }

    // Catch-all so RouteOwners does not have to stay exhaustive: every settings sub-screen is
    // owned by the settings tab without anyone having to list it. Only a fallback now; the back
    // stack decides the tab whenever it has a tab page in it (see owningTab).
    if (currentRoute.endsWith("_settings") || currentRoute.startsWith("settings")) return "settings"
    return null
}

/**
 * the tab the page on screen belongs to: the navigator's current tab. detail pages belong to the
 * tab they were opened from, and MochyNavigator always knows which tab that is, so there is no
 * stack to search the way Mochy's version did — the tab is simply
 * navigationItems[currentTab].route. the route fallback below only runs when the tab index names
 * no tab (it should not happen); it goes by the detail on screen, else the first tab.
 */
fun owningTab(currentTab: Int, detailStack: List<String>, navigationItems: List<Screens>): String {
    navigationItems.getOrNull(currentTab)?.let { return it.route }
    // no tab at that index: go by the detail on screen, else the first tab
    return owningTabRoute(detailStack.lastOrNull(), navigationItems)
        ?: navigationItems.firstOrNull()?.route.orEmpty()
}

// Mochy's TabOwnership class is NOT ported: it exists to place pages in per-tab saved stacks,
// and MochyNavigator keeps none. The tab slide that read it (to slide a detail page like its
// tab) will need another way to find a page's tab when it is ported. See NEEDS.md.

/** The bar itself, a stadium the mini player's 64dp matches. */
internal val NavBarHeight = 64.dp
internal val NavBarShape = RoundedCornerShape(50)

/** The lit tab's highlight sits this far in from the bar's top, bottom and both ends. */
private val NavHighlightInset = 8.dp

/** Room between a highlight and where its neighbour's would be. */
private val NavHighlightGap = 4.dp

// half the gap is the row's padding and half is inside each slot, so the highlight sits as far from the ends as from the top and bottom
internal val NavRowPadding = NavHighlightInset - NavHighlightGap / 2

/** How bright an unlit tab's icon is. */
private const val UnlitIconAlpha = 0.7f

/**
 * The highlight's front edge springs to the new tab faster than its back edge, so it stretches
 * toward the tab and settles into it instead of sliding over as a rigid block.
 */
private val NavHighlightLead = spring<Float>(dampingRatio = 0.82f, stiffness = 700f)
private val NavHighlightTrail = spring<Float>(dampingRatio = 0.82f, stiffness = 320f)

@Composable
fun AppNavigationBar(
    navigationItems: List<Screens>,
    currentRoute: String?,
    /** from [owningTab]: the tab the page on screen belongs to */
    selectedTabRoute: String,
    onItemClick: (Screens, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    pureBlack: Boolean = false,
    slimNav: Boolean = false,
    /** a second quick tap on Search: straight into typing */
    onSearchDoubleTap: (() -> Unit)? = null
) {
    val glassState = LocalGlassState.current ?: remember { GlassState() }
    val accentWash = rememberChromeAccentWash()
    val selectedIndex = navigationItems.indexOfFirst { it.route == selectedTabRoute }

    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal))
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .align(Alignment.TopCenter)
                .height(NavBarHeight)
                .press3D(maxTilt = 4f, pressedScale = 0.985f)
                .dropShadow(NavBarShape, ChromeShadow)
                .clip(NavBarShape)
                .drawWithContent {
                    drawContent()
                    drawChromeOutline(Offset.Zero, size, cornerRadius = size.height / 2f)
                }
        ) {
            FrostedChromeGlass(
                state = glassState,
                pureBlack = pureBlack,
                accentWash = accentWash,
                modifier = Modifier.fillMaxSize(),
            )

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = NavRowPadding)
                    .navHighlight(selectedIndex, navigationItems.size),
                verticalAlignment = Alignment.CenterVertically
            ) {
                navigationItems.forEach { screen ->
                    // lit = the tab this page belongs to. what a tap on it does (scroll to the top
                    // on the tab's own page, back to that page from deeper in) is the caller's call
                    NavTab(
                        screen = screen,
                        isSelected = screen.route == selectedTabRoute,
                        onItemClick = onItemClick,
                        onDoubleTap = onSearchDoubleTap.takeIf { screen == Screens.Search },
                    )
                }
            }
        }
    }
}

/**
 * One highlight behind the lit tab that slides to the next one, instead of each tab fading its own
 * in and out. Only read while drawing, so the slide never recomposes the bar.
 */
@Composable
internal fun Modifier.navHighlight(selectedIndex: Int, tabCount: Int): Modifier {
    val start = remember { Animatable(selectedIndex.coerceAtLeast(0).toFloat()) }
    val end = remember { Animatable(selectedIndex.coerceAtLeast(0).toFloat()) }
    val shown = remember { Animatable(if (selectedIndex >= 0) 1f else 0f) }

    LaunchedEffect(selectedIndex) {
        if (selectedIndex < 0) {
            shown.animateTo(0f, tween(200))
            return@LaunchedEffect
        }
        val target = selectedIndex.toFloat()
        // back from no lit tab, it fades in where it belongs rather than sliding over from the old one
        if (shown.value == 0f) {
            start.snapTo(target)
            end.snapTo(target)
        }
        launch { shown.animateTo(1f, tween(200)) }
        val movingRight = target > (start.value + end.value) / 2f
        launch { start.animateTo(target, if (movingRight) NavHighlightTrail else NavHighlightLead) }
        end.animateTo(target, if (movingRight) NavHighlightLead else NavHighlightTrail)
    }

    return drawBehind {
        val alpha = shown.value
        if (alpha <= 0f || tabCount == 0) return@drawBehind
        val slot = size.width / tabCount
        val halfGap = NavHighlightGap.toPx() / 2f
        val inset = NavHighlightInset.toPx()
        val left = start.value * slot + halfGap
        val right = (end.value + 1f) * slot - halfGap
        val height = size.height - inset * 2f
        drawRoundRect(
            color = ChromeHighlightColor,
            topLeft = Offset(left, inset),
            size = Size(right - left, height),
            cornerRadius = CornerRadius(height / 2f),
            alpha = alpha,
        )
    }
}

/**
 * A tab's tap, or with [onDoubleTap] a second tap close behind the first: the first still goes to
 * the tab at once, the second does [onDoubleTap] instead. [lastTap] holds the time of the one before.
 */
private fun tabTapped(lastTap: LongArray, doubleTapTimeoutMillis: Long, onDoubleTap: (() -> Unit)?, onTap: () -> Unit) {
    val now = android.os.SystemClock.uptimeMillis()
    if (onDoubleTap != null && now - lastTap[0] < doubleTapTimeoutMillis) {
        lastTap[0] = 0L
        onDoubleTap()
    } else {
        lastTap[0] = now
        onTap()
    }
}

/**
 * One tab of the bar. Its icon swaps to the filled one and brightens as it lights up, and it
 * squishes under a press. [onDoubleTap] is for Search, where two taps open the search field.
 * The Library's icon turns into a download badge while songs download.
 */
@Composable
internal fun RowScope.NavTab(
    screen: Screens,
    isSelected: Boolean,
    onItemClick: (Screens, Boolean) -> Unit,
    onDoubleTap: (() -> Unit)?,
) {
    val viewConfiguration = LocalViewConfiguration.current
    val interactionSource = remember { MutableInteractionSource() }
    val lastTap = remember { longArrayOf(0L) }
    val lit = animateFloatAsState(
        targetValue = if (isSelected) 1f else 0f,
        animationSpec = tween(200),
        label = "nav_tab_lit"
    )

    // Both icons stay loaded, so lighting a tab never shows an empty frame while its filled icon loads.
    val unlitIcon = coil3.compose.rememberAsyncImagePainter(model = screen.iconIdInactive)
    val litIcon = coil3.compose.rememberAsyncImagePainter(model = screen.iconIdActive)
    val iconSize = if (screen == Screens.Home || screen == Screens.Search) 22.dp else 24.dp

    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .pressBounce(interactionSource, pressedScale = 0.85f)
            .clickable(
                interactionSource = interactionSource,
                indication = RemoteFocus,
                onClick = {
                    tabTapped(lastTap, viewConfiguration.doubleTapTimeoutMillis, onDoubleTap) {
                        onItemClick(screen, isSelected)
                    }
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        if (screen == Screens.Library) {
            LibraryDownloadIcon { TabIcons(screen, unlitIcon, litIcon, iconSize) { lit.value } }
        } else {
            TabIcons(screen, unlitIcon, litIcon, iconSize) { lit.value }
        }
    }
}

/** a tab's two icons, the filled one fading in over the outline as the tab lights up */
@Composable
private fun TabIcons(
    screen: Screens,
    unlitIcon: androidx.compose.ui.graphics.painter.Painter,
    litIcon: androidx.compose.ui.graphics.painter.Painter,
    iconSize: androidx.compose.ui.unit.Dp,
    lit: () -> Float,
) {
    Box(contentAlignment = Alignment.Center) {
        Icon(
            painter = unlitIcon,
            contentDescription = stringResource(screen.titleId),
            tint = Color.White,
            modifier = Modifier
                .size(iconSize)
                .graphicsLayer {
                    alpha = UnlitIconAlpha * (1f - lit())
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                }
        )
        Icon(
            painter = litIcon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier
                .size(iconSize)
                .graphicsLayer {
                    alpha = lit()
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                }
        )
    }
}
