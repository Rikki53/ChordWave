// Ported from Mochy Music (GPL-3.0): ui/menu/songedit/SongEditSheet.kt
//
// ADAPTATIONS (CW has no metadata editor / song db / SongTuning /
// AudioLabStore — see NEEDS.md §(b) E1):
// - The "add to library first" gate is DROPPED: Mochy asked it when the
//   song wasn't in its db (song_edit_need_library). With no db there is
//   nothing to check, so the editor opens straight from the metadata. The
//   gate's strings (song_edit_add_to_library / song_edit_need_library /
//   song_edit_not_now) are NOT staged — the adapter adds them back with the
//   store (NEEDS.md §(b) E1).
// - SongEditState comes from fromMetadata() (no db row, no tuning).
// - SAVE IS STUBBED: Mochy ran SongEditSaver.save(db, tuning, draft) +
//   LyricsMenuViewModel.useSource for a picked lyrics source. CW has no
//   metadata store, so OK logs a Timber warning + TODO and shows the
//   "Couldn't save your changes" toast, then unlocks — it never dismisses
//   as if the save had worked, and never fakes success.
// - No Hilt, no AudioLabEntryPoint, no ViewModel in this file.

package com.music.bitchord.ui.mochy.menu.songedit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.music.bitchord.R
import com.music.bitchord.ui.mochy.component.LocalMenuState
import com.music.bitchord.ui.mochy.component.PopupSheetHandle
import com.music.bitchord.ui.mochy.component.PopupSheetState
import com.music.bitchord.ui.mochy.component.SheetPillButton
import com.music.bitchord.ui.mochy.component.TopToaster
import com.music.bitchord.ui.mochy.models.MediaMetadata
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber

// opens the song edit sheet for a song; one that isn't in the library yet first asks to be added, like the mock's second sheet
@Composable
fun SongEditSheet(metadata: MediaMetadata, onDismiss: () -> Unit) {
    val menuState = LocalMenuState.current
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    // outlives each sheet, so "add to library" can finish and open the editor after its own sheet has gone
    val scope = rememberCoroutineScope()
    val flow = remember { SongEditFlow(menuState, scope) { currentOnDismiss() } }

    LaunchedEffect(metadata.id) {
        // Mochy checked the db here and asked to add the song to the library
        // first when it wasn't saved. CW has no db, so the editor opens
        // straight from the metadata (see the header note).
        flow.openEditor(metadata)
    }
    DisposableEffect(flow) { onDispose { flow.closeAll() } }
}

// the sheets one edit opens, one after another; the caller hears about it once the last of them is gone
private class SongEditFlow(
    private val menuState: PopupSheetState,
    private val scope: CoroutineScope,
    private val onDone: () -> Unit,
) {
    private val open = mutableListOf<PopupSheetHandle>()
    // set between closing the library question and the editor opening, so that gap doesn't end the flow
    private var movingOn = false
    private var finished = false

    fun openEditor(metadata: MediaMetadata) {
        val state = SongEditState.fromMetadata(metadata)
        movingOn = false
        if (finished) {
            endIfIdle()
            return
        }
        var handle: PopupSheetHandle? = null
        handle = show(
            buttonBar = {
                SaveButton(
                    state = state,
                    onLock = { locked -> handle?.setLocked(locked) },
                    onSaved = { handle?.dismiss() },
                )
            },
        ) {
            SongEditContent(state)
        }
    }

    fun closeAll() {
        finished = true
        open.toList().forEach { it.dismiss() }
    }

    private fun show(
        buttonBar: @Composable () -> Unit,
        content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
    ): PopupSheetHandle {
        var handle: PopupSheetHandle? = null
        handle = menuState.showWithHandle(
            buttonBar = buttonBar,
            onClosed = {
                handle?.let(open::remove)
                endIfIdle()
            },
            content = content,
        )
        open.add(handle)
        return handle
    }

    private fun endIfIdle() {
        if (open.isEmpty() && !movingOn && !finished) {
            finished = true
            onDone()
        }
    }
}

// the pinned ok: saves everything, then closes; locked while it works so the sheet can't be pulled away mid-save
@Composable
private fun SaveButton(state: SongEditState, onLock: (Boolean) -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    SheetPillButton(
        text = if (state.busy) stringResource(R.string.song_edit_saving) else stringResource(android.R.string.ok),
        enabled = !state.busy && state.title.isNotBlank(),
        onClick = {
            state.busy = true
            onLock(true)
            scope.launch {
                // TODO(phase3-adapter): metadata save (NEEDS.md §(b) E1).
                // Mochy ran SongEditSaver.save(db, tuning, state.draft())
                // here (title/cover/artists/album/year/offset/speed/EQ/
                // custom lyrics + picked lyrics source). CW has no metadata
                // store, so nothing is saved: warn, toast the failure, and
                // unlock — the sheet stays open and onSaved is NOT called.
                Timber.w(
                    "SongEditSheet: saving edits for %s not available (no metadata store yet); draft dropped",
                    state.songId,
                )
                onLock(false)
                TopToaster.show(text = context.getString(R.string.song_edit_failed), iconRes = R.drawable.edit)
                state.busy = false
            }
        },
    )
}
