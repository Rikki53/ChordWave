// Ported from Mochy Music (GPL-3.0): ui/screens/library/LibraryTabs.kt
// ADAPTATIONS: navigation param -> MochyNavigator (route strings verbatim);
// Mochy packages -> com.music.bitchord.ui.mochy.* staged equivalents. See NEEDS.md.
// LibraryTabs.kt
//
// The All / Playlist / Local / Cached / History / Genres / Imported switch at the top of the Library, and the
// small pieces the tabs share: the two-wide card grid, the caps section label and the "Manage" pill
// of a tab. The strip is one row that scrolls sideways when the pills don't fit — it never wraps.

package com.music.bitchord.ui.mochy.screens.library

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.bitchord.R
import com.music.bitchord.ui.mochy.screens.search.SearchColors
import com.music.bitchord.ui.mochy.viewmodels.LibraryShelfKind
import com.music.bitchord.ui.mochy.component.RemoteFocus

/**
 * What the Library shows: everything, what plays from the phone, what the player has cached, or
 * what was imported. [shelf] is the slice of the library a tab is built from, if it is one.
 */
enum class LibraryTab(val labelRes: Int, val shelf: LibraryShelfKind? = null) {
    All(R.string.library_tab_all),
    Playlists(R.string.library_tab_playlists),
    Local(R.string.library_tab_local, LibraryShelfKind.Local),
    Cached(R.string.cached, LibraryShelfKind.Cached),
    History(R.string.history),
    Replay(R.string.library_tab_replay),
    Genres(R.string.library_tab_genres),
    Imported(R.string.library_tab_imported),
}

private val ChipTrack = Color(0xFF161618)
private val Chip = Color(0xFF232326)
private val ChipSelected = Color(0xFF3D3D41)

private val ChipTrackPadding = 3.dp
private val ChipGap = 4.dp

/** The tabs as small pills in a dark track, the picked one a step lighter. */
@Composable
fun LibraryTabChips(
    selected: LibraryTab,
    onSelect: (LibraryTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    // one row, always: when the pills don't fit, the row scrolls sideways
    // instead of wrapping to a second row — the tab strip stays one tidy line
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(ChipTrack)
            .padding(ChipTrackPadding),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(ChipGap),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            LibraryTab.entries.forEach { tab ->
                LibraryTabChip(tab, isSelected = tab == selected, onSelect = onSelect)
            }
        }
    }
}

@Composable
private fun LibraryTabChip(tab: LibraryTab, isSelected: Boolean, onSelect: (LibraryTab) -> Unit) {
    val background by animateColorAsState(
        targetValue = if (isSelected) ChipSelected else Chip,
        animationSpec = tween(220),
        label = "libraryTabChip",
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(background)
            .clickable(
                role = Role.Tab,
                interactionSource = remember { MutableInteractionSource() },
                indication = RemoteFocus,
                onClick = { onSelect(tab) },
            )
            .heightIn(min = 28.dp)
            .padding(horizontal = 14.dp, vertical = 5.dp),
    ) {
        Text(
            text = stringResource(tab.labelRes),
            color = if (isSelected) SearchColors.Primary else SearchColors.Primary.copy(alpha = 0.72f),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** "ALL MUSIC", "PLAYLISTS": a small caps label over a group of cards. */
@Composable
fun LibraryCapsLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        color = SearchColors.Primary,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.4.sp,
        modifier = modifier.padding(horizontal = LibraryHorizontalPadding),
    )
}

/** The small centred pill at the bottom of a tab that opens its Manage sheet. */
@Composable
fun LibraryManagePill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .clip(RoundedCornerShape(percent = 50))
                .background(Color(0xFF2E2E31))
                .clickable(role = Role.Button, onClick = onClick)
                .heightIn(min = 30.dp)
                .padding(horizontal = 18.dp, vertical = 6.dp),
        ) {
            Text(
                text = text,
                color = SearchColors.Primary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/** One card in a [LibraryCardGrid]. */
@Immutable
class LibraryCardEntry(
    val title: String,
    val count: Int,
    val covers: List<String?>,
    val onClick: () -> Unit,
    val roundCovers: Boolean = false,
    val icon: (@Composable () -> Unit)? = null,
)

/** Cards two to a row, the last one keeping its half width when it is alone. */
@Composable
fun LibraryCardGrid(entries: List<LibraryCardEntry>, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = LibraryHorizontalPadding),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        entries.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEach { entry ->
                    LibraryCategoryCard(
                        title = entry.title,
                        count = entry.count,
                        covers = entry.covers,
                        onClick = entry.onClick,
                        icon = entry.icon,
                        roundCovers = entry.roundCovers,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (pair.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}
