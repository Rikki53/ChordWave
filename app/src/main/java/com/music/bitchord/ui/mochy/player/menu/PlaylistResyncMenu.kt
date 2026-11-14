// Ported from Mochy Music (GPL-3.0): ui/menu/PlaylistResyncMenu.kt
// PlaylistResyncMenu.kt
//
// "Re-sync with Spotify" in an imported playlist's menus. It reads the playlist again on the
// service it came from and adds the songs that are new there. Most start straight away; one that
// came from an account opens that service's sign-in page first, and an old import that never
// saved where it came from asks for its link.
//
// ADAPTATIONS: CW has no resync engine and no import store, so this is a
// shape-only port: `source` is always null (no title row, no menu item),
// `start()` logs a TODO, and `rememberResyncAll` returns a lambda that
// toasts Mochy's empty-case message. The link dialog is ported verbatim
// (it needs no engine). When the engine lands, wire `PlaylistResyncState`
// to it and drop the stubs.

package com.music.bitchord.ui.mochy.player.menu

import android.content.Context
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.music.bitchord.R
import com.music.bitchord.ui.mochy.component.Material3MenuItemData
import com.music.bitchord.ui.mochy.component.TextFieldDialog
import com.music.bitchord.ui.mochy.component.TopToaster
import com.music.bitchord.ui.mochy.importer.ImportSource
import com.music.bitchord.ui.mochy.navigation.MochyNavigator
import kotlinx.coroutines.CoroutineScope
import timber.log.Timber

/** One playlist's Re-sync, for its menu. [source] is null while it isn't known to have come from a service. */
class PlaylistResyncState internal constructor(
    private val playlistId: String,
    private val scope: CoroutineScope,
    private val context: Context,
    private val navigator: MochyNavigator?,
    private val onDismiss: () -> Unit,
) {
    /** Always null until the import store exists: nothing is known to have come from a service. */
    var source: ImportSource? = null
        internal set

    /** An old import with nothing saved about where it lives: its link is asked for. */
    var askingForLink by mutableStateOf(false)
        private set

    fun start() {
        // TODO(Phase 3): no resync engine in CW.
        Timber.w("PlaylistResyncState: re-sync is not wired yet (playlist $playlistId)")
    }

    fun startFromLink(link: String) {
        askingForLink = false
        // TODO(Phase 3): no resync engine in CW.
        Timber.w("PlaylistResyncState: re-sync from link is not wired yet (playlist $playlistId)")
    }

    fun cancelLink() {
        askingForLink = false
    }

    /** "Re-sync with Spotify"; null for a playlist made here. */
    val title: String? get() = source?.let { "Re-sync with ${it.label}" }

    /** The menu row; null for a playlist made here. */
    fun menuItem(): Material3MenuItemData? {
        val from = source ?: return null
        return Material3MenuItemData(
            title = { Text("Re-sync with ${from.label}") },
            description = { Text("Add the songs that are new on ${from.label}") },
            icon = { Icon(painter = painterResource(R.drawable.sync), contentDescription = null) },
            onClick = ::start,
        )
    }
}

/** Also shows the link dialog when a Re-sync asks for one, over the menu. */
@Composable
fun rememberPlaylistResync(
    playlistId: String,
    browseId: String?,
    navigator: MochyNavigator?,
    onDismiss: () -> Unit,
): PlaylistResyncState {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state = remember(playlistId) {
        PlaylistResyncState(playlistId, scope, context, navigator, onDismiss)
    }
    // No import store: source stays null (nothing to re-sync from).
    if (state.askingForLink) {
        ResyncLinkDialog(sourceLabel = state.source?.label ?: "music service", onDismiss = state::cancelLink, onDone = state::startFromLink)
    }
    return state
}

/**
 * Re-sync all, for the Library's Manage imports: starts every imported playlist it can read
 * straight away, then opens the sign-in page of each service that needs one, one after another.
 *
 * CW: no resync engine and no import store, so this toasts Mochy's
 * empty-case message (the same words Mochy showed when nothing came
 * from a service).
 */
@Composable
fun rememberResyncAll(navigator: MochyNavigator): () -> Unit {
    return remember(navigator) {
        {
            TopToaster.show(
                text = "None of your playlists came from a music service, so there's nothing to re-sync.",
                iconRes = R.drawable.sync,
            )
        }
    }
}

/** Asks for a playlist's link, for a Re-sync that can't find it any other way. */
@Composable
fun ResyncLinkDialog(sourceLabel: String, onDismiss: () -> Unit, onDone: (String) -> Unit) {
    TextFieldDialog(
        icon = { Icon(painter = painterResource(R.drawable.link), contentDescription = null) },
        title = { Text("Paste its $sourceLabel link") },
        placeholder = { Text("https://…") },
        keyboardType = KeyboardType.Uri,
        onDone = onDone,
        onDismiss = onDismiss,
        extraContent = {
            Text(
                "This playlist was imported before ChordWave saved where it came from. Its link is how Re-sync finds it again.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )
        },
    )
}
