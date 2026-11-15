// Ported from Mochy Music (GPL-3.0): ui/component/EditPlaylistSheet.kt
// EditPlaylistSheet.kt
//
// ADAPTATIONS:
// - The `database: MusicDatabase` param is DROPPED per the phase2-playlist
//   contract (the port calls it with database dropped). The fresh read
//   Mochy did from its DB is OfflinePlaylists.get() for a local playlist,
//   or the passed-in playlist for a server one.
// - Rename: OfflinePlaylists.rename(browseId, name) for an offline
//   playlist, else YtMusicRepository.renamePlaylist(browseId, name) when
//   a browseId exists, else Timber.w + TODO (no store).
// - Cover: Mochy's logic verbatim — the cover is only touched if the user
//   touched the carousel, except a generated one is redrawn when the title
//   changed (the title is part of the picture). The new cover lands in
//   OfflinePlaylists.setCover for a LOCAL playlist; a server playlist has
//   no local mirror store in ChordWave, so a cover picked there is told
//   honestly via a toast instead of being silently dropped.
// - Description changes are still DROPPED (no store holds them): Done
//   saves the rename + the cover only.

package com.music.bitchord.ui.mochy.component

import android.content.Context
import androidx.compose.runtime.rememberCoroutineScope
import com.music.bitchord.data.OfflinePlaylists
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.ui.mochy.models.PlaylistEntity
import com.music.bitchord.ui.mochy.playlistcover.CoverChoice
import com.music.bitchord.ui.mochy.playlistcover.PlaylistCoverStore
import com.music.bitchord.ui.mochy.playlistcover.PlaylistCoverStyle
import com.music.bitchord.ui.mochy.playlistcover.PlaylistCovers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Pushes the "Edit playlist" sheet (the mock under "Edit playlist"): the same carousel, title and
 * description as creating one, filled in from [playlist], with Done to save. Called from inside
 * the playlist menu, so it opens over it and the menu comes back forward behind it.
 *
 * The cover is only touched if the user touched the carousel - except that a generated one is
 * redrawn when the title changed, because the title is part of the picture.
 *
 * @param topSongId the playlist's top song, which a newly generated cover takes its colours from.
 */
fun PopupSheetState.showEditPlaylistSheet(
    playlist: PlaylistEntity,
    context: Context,
    topSongId: String?,
    topSongArtworkUrl: String?,
) {
    val appContext = context.applicationContext
    var sheet: PopupSheetHandle? = null
    val form = PlaylistFormState(playlist.name, playlist.description.orEmpty())

    // Which card the carousel opens on: the style this cover was drawn in, or the picture card for
    // one of the user's own pictures; anything else (song collage, YouTube's image) has no card.
    val styles = PlaylistCoverStyle.entries
    val generated = PlaylistCoverStore.parse(playlist.thumbnailUrl)
    val ownImage = playlist.thumbnailUrl.takeIf { generated == null && PlaylistCoverStore.isManaged(it) }
    val startPage = when {
        generated != null -> styles.indexOf(generated.style)
        ownImage != null -> styles.size
        else -> 0
    }

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
                            val browseId = playlist.browseId
                            val isOffline = browseId != null && OfflinePlaylists.isOfflinePlaylist(browseId)
                            // the playlist as it is now: a cover redrawn while the sheet was open would otherwise be pointed back at a file that's gone
                            val current = if (isOffline) {
                                OfflinePlaylists.get(browseId)?.let {
                                    playlist.copy(name = it.title, thumbnailUrl = it.coverUrl)
                                } ?: playlist
                            } else {
                                playlist
                            }
                            val currentGenerated = PlaylistCoverStore.parse(current.thumbnailUrl)
                            val name = form.name.trim()
                            val titleChanged = name != current.name
                            var thumbnail = current.thumbnailUrl

                            val choice = form.choice
                            val redraw = when {
                                choice != null -> choice
                                currentGenerated != null && titleChanged -> CoverChoice.Generated(currentGenerated.style)
                                else -> null
                            }
                            var coverSkippedOnline = false
                            if (redraw != null) {
                                if (isOffline) {
                                    PlaylistCovers.create(
                                        context = appContext,
                                        playlistId = playlist.id,
                                        title = name,
                                        choice = redraw,
                                        topSongId = topSongId,
                                        artworkUrl = topSongArtworkUrl,
                                    )?.let { thumbnail = it }
                                } else {
                                    // No local mirror store holds a server playlist's cover.
                                    coverSkippedOnline = true
                                }
                            }

                            if (titleChanged) {
                                when {
                                    isOffline ->
                                        OfflinePlaylists.rename(browseId!!, name)
                                    browseId != null ->
                                        YtMusicRepository.renamePlaylist(browseId, name)
                                    else ->
                                        // TODO(Phase 3): no CW store holds this playlist.
                                        Timber.w("EditPlaylistSheet: cannot rename playlist ${playlist.id} — no browseId and no store")
                                }
                            }
                            // Description edits are dropped: no store holds them.

                            if (isOffline && thumbnail != current.thumbnailUrl) {
                                OfflinePlaylists.setCover(browseId!!, thumbnail)
                                PlaylistCoverStore.deleteStale(appContext, playlist.id, thumbnail)
                            }

                            withContext(Dispatchers.Main) {
                                sheet?.dismiss()
                                if (coverSkippedOnline) {
                                    TopToaster.show("Covers can only be saved on local playlists")
                                }
                            }
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
            startPage = startPage,
            existingImage = ownImage,
            keepAllowed = true,
        )
    }
}
