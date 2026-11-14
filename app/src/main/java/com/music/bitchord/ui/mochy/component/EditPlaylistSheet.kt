// Ported from Mochy Music (GPL-3.0): ui/component/EditPlaylistSheet.kt
// EditPlaylistSheet.kt
//
// ADAPTATIONS:
// - The `database: MusicDatabase` param is DROPPED per the phase2-playlist
//   contract (the port calls it with database dropped).
// - Rename: OfflinePlaylists.rename(browseId, name) for an offline
//   playlist, else YtMusicRepository.renamePlaylist(browseId, name) when
//   a browseId exists, else Timber.w + TODO (no store).
// - Description + cover changes are DROPPED (no store holds them, no
//   cover engine): the form still shows the fields (Mochy's layout), but
//   Done only renames. The cover carousel is the simplified static
//   preview from PlaylistFormContent; startPage is always 0.
// - The `context` param is kept for signature parity (unused now that
//   the cover store is gone).

package com.music.bitchord.ui.mochy.component

import android.content.Context
import androidx.compose.runtime.rememberCoroutineScope
import com.music.bitchord.data.OfflinePlaylists
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.ui.mochy.models.PlaylistEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Pushes the "Edit playlist" sheet (the mock under "Edit playlist"): the cover preview, title and
 * description, filled in from [playlist], with Done to save. Called from inside the playlist
 * menu, so it opens over it and the menu comes back forward behind it.
 *
 * Only the rename is wired: description and cover changes have no store
 * to land in yet.
 *
 * @param topSongId the playlist's top song, which a newly generated cover takes its colours from.
 */
@Suppress("UNUSED_PARAMETER")
fun PopupSheetState.showEditPlaylistSheet(
    playlist: PlaylistEntity,
    context: Context,
    topSongId: String?,
    topSongArtworkUrl: String?,
) {
    var sheet: PopupSheetHandle? = null
    val form = PlaylistFormState(playlist.name, playlist.description.orEmpty())

    sheet = showWithHandle(
        buttonBar = {
            val scope = rememberCoroutineScope()
            SheetPillButton(
                text = if (form.busy) "Saving…" else "Done",
                enabled = form.name.isNotBlank() && !form.busy,
                onClick = {
                    form.busy = true
                    // Nothing may close the sheet under the work below.
                    sheet?.setLocked(true)
                    scope.launch(Dispatchers.IO) {
                        try {
                            val name = form.name.trim()
                            val titleChanged = name != playlist.name
                            if (titleChanged) {
                                val browseId = playlist.browseId
                                when {
                                    browseId != null && OfflinePlaylists.isOfflinePlaylist(browseId) ->
                                        OfflinePlaylists.rename(browseId, name)
                                    browseId != null ->
                                        YtMusicRepository.renamePlaylist(browseId, name)
                                    else ->
                                        // TODO(Phase 3): no CW store holds this playlist.
                                        Timber.w("EditPlaylistSheet: cannot rename playlist ${playlist.id} — no browseId and no store")
                                }
                            }
                            // Description + cover edits are dropped: no store holds them.

                            withContext(Dispatchers.Main) { sheet?.dismiss() }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Timber.tag("EditPlaylistSheet").e(e, "Saving the playlist failed")
                            form.busy = false
                            sheet?.setLocked(false)
                        }
                    }
                },
            )
        },
    ) {
        PlaylistFormContent(
            form = form,
            seed = playlist.id,
            artworkUrl = topSongArtworkUrl,
            startPage = 0,
            existingImage = playlist.thumbnailUrl,
            keepAllowed = true,
        )
    }
}
