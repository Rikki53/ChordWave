// Ported from Mochy Music (GPL-3.0): ui/component/SwipeActions.kt
// (removeImportSwipeAction only)
// RemoveImportSwipeAction.kt
//
// ADAPTATIONS: the library part is real (MenuActions.sendLibraryFeedback,
// the same as the staged library swipe). The import-deletion part is a
// STUB (Timber.w + TODO): no import store exists in CW, so there is
// nothing to delete the song's import (or import session) from. The toast
// therefore uses R.string.removed_from_library — honest, since only the
// library removal happened. The `removed_from_import_and_library` string
// (added per the phase2-library C8 contract) becomes the toast once the
// import store lands.

package com.music.bitchord.ui.mochy.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.music.bitchord.R
import com.music.bitchord.ui.mochy.importer.ImportSource
import com.music.bitchord.ui.mochy.models.SongEntity
import com.music.bitchord.ui.mochy.player.menu.sendLibraryFeedback
import timber.log.Timber

/**
 * Sliding right in "Imported from Spotify": the song stops being one of [source]'s imports and leaves
 * the library, in one go. Red, since it doesn't come back with another swipe the way adding does.
 */
@Composable
fun removeImportSwipeAction(song: SongEntity, source: ImportSource): SwipeAction {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return SwipeAction(
        icon = R.drawable.delete,
        color = SwipeRemoveColor,
        label = stringResource(R.string.remove_from_import_and_library),
    ) {
        // TODO(Phase 3): no import store in CW — nothing to delete the
        // song's import (or import session) from.
        Timber.w("removeImportSwipeAction: import deletion is not wired yet (source ${source.key})")
        // the library part is the same as the library swipe's: the feedback token
        if (song.inLibrary != null) {
            scope.sendLibraryFeedback(song.id, adding = false, song.libraryAddToken, song.libraryRemoveToken)
        }
        TopToaster.show(
            text = context.getString(R.string.removed_from_library),
            thumbnail = song.thumbnailUrl,
            iconRes = R.drawable.delete,
        )
    }
}
