// The Library's Replay tab (batch 39): ChordWave's Replay — headline cards,
// the four charts, stories and the shareable poster — living as a tab beside
// All / Playlists / Local / Cached / History / Genres / Imported instead of
// only as its own page. The content is the Replay page's own items
// (replayItems in screens/replay/ReplayScreen.kt), drawn under the Library
// scaffold's collapsing header like every other tab; the state is the same
// one the `replay` route uses, so the numbers never disagree.

package com.music.bitchord.ui.mochy.screens.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.music.bitchord.ui.mochy.component.RevealSeenState
import com.music.bitchord.ui.mochy.navigation.MochyNavigator
import com.music.bitchord.ui.mochy.screens.replay.ReplayPageState
import com.music.bitchord.ui.mochy.screens.replay.rememberReplayPageState
import com.music.bitchord.ui.mochy.screens.replay.replayItems

/** The Replay tab's state; null until the player service binds. */
@Composable
fun rememberLibraryReplayState(navigator: MochyNavigator): ReplayPageState? =
    rememberReplayPageState(navigator)

fun LazyListScope.libraryReplayTab(
    state: ReplayPageState?,
    revealSeen: RevealSeenState,
) {
    if (state == null) {
        item(key = "replay_tab_loading") {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(64.dp),
                Alignment.Center,
            ) {
                CircularProgressIndicator(color = Color.White.copy(alpha = 0.6f))
            }
        }
        return
    }
    replayItems(state, revealSeen, embedded = true)
}
