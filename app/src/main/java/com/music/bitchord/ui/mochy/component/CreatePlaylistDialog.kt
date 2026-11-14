// Ported from Mochy Music (GPL-3.0): ui/component/CreatePlaylistDialog.kt
// CreatePlaylistDialog.kt
//
// ADAPTATIONS:
// - Signed-in is read from the staged rememberYouTubeSignedIn() (auth.
//   SignedIn) instead of Mochy's InnerTube cookie preference.
// - The sync-enabled check is YtmSyncKey + signed-in. Mochy also checked
//   internet connectivity (isSyncEnabled); that check is skipped — CW has
//   no equivalent helper, and the create itself fails loudly offline.
// - Create: OfflinePlaylists.create(name) for the local playlist (real;
//   its id goes to onPlaylistCreated). If sync is on, a YouTube playlist
//   is ALSO created via YtMusicRepository.createPlaylist(name,
//   PlaylistPrivacy.PRIVATE, ...) — Mochy's innertube create sends title
//   only (no privacy field), PRIVATE is the closest default. The
//   local<->online link is LOST: no CW store holds the online browseId
//   against the local playlist.
// - Cover generation is skipped (the carousel engine is not ported); the
//   success toast uses the top song's artwork as its thumbnail.

package com.music.bitchord.ui.mochy.component

import android.widget.Toast
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
import com.music.bitchord.data.OfflinePlaylists
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.model.PlaylistPrivacy
import com.music.bitchord.ui.mochy.constants.YtSyncNewPlaylistsKey
import com.music.bitchord.ui.mochy.constants.YtmSyncKey
import com.music.bitchord.ui.mochy.models.PlaylistEntity
import com.music.bitchord.ui.mochy.utils.rememberPreference
import com.music.bitchord.ui.mochy.utils.rememberYouTubeSignedIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Creating a playlist, as a sheet (the "Create playlist" mock): a cover preview, then the title
 * and description. The name is kept for the old dialog this replaces; it is shown through the
 * app's popup system like every other sheet, and [onDismiss] is called once it has closed,
 * however it was closed.
 *
 * @param topSongId the song the playlist is being made for, if there is one. It becomes the top
 *   song, so the covers take their colours from its [topSongArtworkUrl]; without one they take a
 *   colour of their own and are redrawn from the first song that is added.
 */
@Composable
fun CreatePlaylistDialog(
    onDismiss: () -> Unit,
    initialTextFieldValue: String? = null,
    allowSyncing: Boolean = true,
    onPlaylistCreated: ((String) -> Unit)? = null,
    topSongId: String? = null,
    topSongArtworkUrl: String? = null,
) {
    val menuState = LocalMenuState.current
    val context = LocalContext.current

    val isSignedIn = rememberYouTubeSignedIn()
    val syncNewPlaylists by rememberPreference(YtSyncNewPlaylistsKey, false)
    val syncEnabled by rememberPreference(YtmSyncKey, true)

    // Decided up front so the cover's file, its seeded colours and the playlist all agree on it.
    val playlistId = remember { PlaylistEntity.generatePlaylistId() }
    val form = remember { PlaylistFormState(initialTextFieldValue.orEmpty(), "") }
    LaunchedEffect(allowSyncing, syncNewPlaylists, isSignedIn) {
        form.sync = allowSyncing && syncNewPlaylists && isSignedIn
    }

    // The sheet's content is composed later, by the popup host, from lambdas made here - what can
    // change after that is read through these.
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val currentOnCreated by rememberUpdatedState(onPlaylistCreated)
    val signedIn by rememberUpdatedState(isSignedIn)
    val syncOn by rememberUpdatedState(syncEnabled)

    DisposableEffect(menuState) {
        var closed = false
        // Set by the show() below, before anything here can run: the sheet is composed afterwards.
        var sheet: PopupSheetHandle? = null
        sheet = menuState.showWithHandle(
            buttonBar = {
                val scope = rememberCoroutineScope()
                SheetPillButton(
                    text = if (form.busy) "Creating…" else "Create playlist",
                    enabled = form.name.isNotBlank() && !form.busy,
                    onClick = {
                        form.busy = true
                        // Nothing may close the sheet under the work below.
                        sheet?.setLocked(true)
                        scope.launch(Dispatchers.IO) {
                            try {
                                val name = form.name.trim()
                                if (form.sync && !signedIn) {
                                    Timber.tag("CreatePlaylistDialog").w("Not signed in")
                                    form.busy = false
                                    sheet?.setLocked(false)
                                    return@launch
                                }

                                if (form.sync) {
                                    // A failed YouTube create still leaves the user a local playlist.
                                    // The online playlist's id is not stored anywhere: no CW store
                                    // links a local playlist to its YouTube twin.
                                    YtMusicRepository.createPlaylist(name, PlaylistPrivacy.PRIVATE)
                                        .onFailure {
                                            Timber.tag("CreatePlaylistDialog").w("YouTube create failed: ${it.message}")
                                        }
                                }

                                val created = OfflinePlaylists.create(name)

                                withContext(Dispatchers.Main) {
                                    currentOnCreated?.invoke(created.id)
                                    sheet?.dismiss()
                                    TopToaster.show("Successfully created the playlist", thumbnail = topSongArtworkUrl, iconRes = R.drawable.playlist_add)
                                }
                            } catch (e: kotlinx.coroutines.CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                Timber.tag("CreatePlaylistDialog").e(e, "Creating the playlist failed")
                                form.busy = false
                                sheet?.setLocked(false)
                            }
                        }
                    },
                )
            },
            onClosed = {
                closed = true
                currentOnDismiss()
            },
        ) {
            PlaylistFormContent(
                form = form,
                seed = playlistId,
                artworkUrl = topSongArtworkUrl,
                startPage = 0,
                existingImage = null,
                keepAllowed = false,
                extra = if (allowSyncing) {
                    {
                        val ctx = LocalContext.current
                        SheetOptionRow(
                            title = stringResource(R.string.sync_playlist),
                            subtitle = stringResource(R.string.allows_for_sync_witch_youtube),
                            leading = { SheetIcon(R.drawable.sync) },
                            trailing = {
                                AppSwitch(
                                    checked = form.sync,
                                    onCheckedChange = null,
                                )
                            },
                            onClick = {
                                if (form.sync) {
                                    // switching off is always allowed
                                    form.sync = false
                                } else if (!signedIn) {
                                    Toast.makeText(ctx, ctx.getString(R.string.not_logged_in_youtube), Toast.LENGTH_SHORT).show()
                                } else if (!syncOn) {
                                    Toast.makeText(ctx, ctx.getString(R.string.sync_disabled), Toast.LENGTH_SHORT).show()
                                } else {
                                    form.sync = true
                                }
                            },
                        )
                    }
                } else {
                    null
                },
            )
        }
        // The caller took the dialog away with the sheet still up: take the sheet away with it.
        onDispose { if (!closed) sheet?.dismiss() }
    }
}
