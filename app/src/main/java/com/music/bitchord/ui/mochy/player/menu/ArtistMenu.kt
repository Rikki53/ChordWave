// Ported from Mochy Music (GPL-3.0): ui/menu/ArtistMenu.kt
// ArtistMenu.kt
//
// NOTE on the source path: the task brief placed these menus under
// ui/player/menu/, but in the Mochy source they live in ui/menu/ — the
// header above names the real file. The CW package below
// (ui.mochy.player.menu) follows the brief's output layout.
//
// ADAPTATIONS (CW has no LocalDatabase / Mochy YouTube engine — see NEEDS.md):
// - The artist is the passed-in `originalArtist`; Play / Shuffle fetch the
//   artist's tracks from YtMusicRepository.artistPage(artist.id) instead of
//   the db's artistSongs. A local artist (no YouTube page) loads no tracks,
//   so Play / Shuffle stay inert for it.
// - Subscribe state is local rememberSaveable state; the write is
//   YtMusicRepository.setSubscribed(channelId, subscribed), with the
//   channel id from the entity or, failing that, the artist id when it
//   looks like a channel (UC…). A local artist has neither, so subscribing
//   to one logs a TODO instead.
// - Pin state comes in as a param; the toggle is a caller callback, and
//   without one the action logs a Phase 3 TODO (CW has no pin store yet).
// - Share is a plain ACTION_SEND intent with the channel link.
// - DROPPED: the isPortrait/configuration dead reads.

package com.music.bitchord.ui.mochy.player.menu

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.music.bitchord.R
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.ui.mochy.component.Material3MenuGroup
import com.music.bitchord.ui.mochy.component.Material3MenuItemData
import com.music.bitchord.ui.mochy.component.NewAction
import com.music.bitchord.ui.mochy.component.NewActionGrid
import com.music.bitchord.ui.mochy.component.SheetHeader
import com.music.bitchord.ui.mochy.component.SheetHeaderGap
import com.music.bitchord.ui.mochy.extensions.toMediaItem
import com.music.bitchord.ui.mochy.models.Artist
import com.music.bitchord.ui.mochy.models.toMochySong
import com.music.bitchord.ui.mochy.playback.LocalPlayerConnection
import com.music.bitchord.ui.mochy.playback.queues.ListQueue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber

@Composable
fun ArtistMenu(
    originalArtist: Artist,
    coroutineScope: CoroutineScope,
    onDismiss: () -> Unit,
    isPinned: Boolean = false,
    onTogglePin: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val artist = originalArtist
    var subscribed by rememberSaveable { mutableStateOf(artist.artist.bookmarkedAt != null) }

    SheetHeader(
        thumbnailUrl = artist.artist.thumbnailUrl,
        title = artist.artist.name,
        subtitle = if (artist.songCount == 1) "1 song in your library" else "${artist.songCount} songs in your library",
    )

    Spacer(Modifier.height(SheetHeaderGap))

    LazyColumn(
        contentPadding = PaddingValues(
            start = 0.dp,
            top = 0.dp,
            end = 0.dp,
            bottom = 8.dp + WindowInsets.systemBars.asPaddingValues().calculateBottomPadding(),
        ),
    ) {
        item {
            NewActionGrid(
                actions = buildList {
                    if (true) {
                        if (artist.songCount > 0) {
                            add(
                                NewAction(
                                    icon = {
                                        Icon(
                                            painter = painterResource(R.drawable.play),
                                            contentDescription = null,
                                            modifier = Modifier.size(28.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    },
                                    text = stringResource(R.string.play),
                                    onClick = {
                                        coroutineScope.launch {
                                            val songs = YtMusicRepository.artistPage(artist.id)
                                                .getOrNull()?.songs.orEmpty()
                                                .map { it.toMochySong().toMediaItem() }
                                            if (songs.isNotEmpty()) {
                                                playerConnection.playQueue(
                                                    ListQueue(
                                                        title = artist.artist.name,
                                                        items = songs,
                                                    ),
                                                )
                                            }
                                        }
                                        onDismiss()
                                    }
                                )
                            )

                            add(
                                NewAction(
                                    icon = {
                                        Icon(
                                            painter = painterResource(R.drawable.shuffle),
                                            contentDescription = null,
                                            modifier = Modifier.size(28.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    },
                                    text = stringResource(R.string.shuffle),
                                    onClick = {
                                        coroutineScope.launch {
                                            val songs = YtMusicRepository.artistPage(artist.id)
                                                .getOrNull()?.songs.orEmpty()
                                                .map { it.toMochySong().toMediaItem() }
                                                .shuffled()
                                            if (songs.isNotEmpty()) {
                                                playerConnection.playQueue(
                                                    ListQueue(
                                                        title = artist.artist.name,
                                                        items = songs,
                                                    ),
                                                )
                                            }
                                        }
                                        onDismiss()
                                    }
                                )
                            )
                        }
                    }

                    add(
                        NewAction(
                            icon = {
                                Icon(
                                    painter = painterResource(if (isPinned) R.drawable.remove else R.drawable.add),
                                    contentDescription = null,
                                    modifier = Modifier.size(28.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            text = if (isPinned) "Unpin" else "Pin",
                            onClick = {
                                if (onTogglePin != null) {
                                    onTogglePin()
                                    onDismiss()
                                } else {
                                    // TODO(Phase 3): CW has no pin store yet.
                                    Timber.w("ArtistMenu: pin needs Phase 3 (pin store)")
                                }
                            }
                        )
                    )

                    if (artist.artist.isYouTubeArtist) {
                        add(
                            NewAction(
                                icon = {
                                    Icon(
                                        painter = painterResource(R.drawable.share),
                                        contentDescription = null,
                                        modifier = Modifier.size(28.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                text = stringResource(R.string.share),
                                onClick = {
                                    onDismiss()
                                    context.shareText("https://music.youtube.com/channel/${artist.id}")
                                }
                            )
                        )
                    }
                },
                modifier = Modifier.padding(bottom = 12.dp),
                columns = if (false) 1 else 3
            )
        }

        item {
            Material3MenuGroup(
                items = listOf(
                    Material3MenuItemData(
                        title = {
                            Text(text = if (subscribed) stringResource(R.string.subscribed) else stringResource(R.string.subscribe))
                        },
                        icon = {
                            Icon(
                                painter = painterResource(if (subscribed) R.drawable.subscribed else R.drawable.subscribe),
                                contentDescription = null,
                            )
                        },
                        onClick = {
                            val channelId = artist.artist.channelId
                                ?: artist.id.takeIf { it.startsWith("UC") }
                            if (channelId == null) {
                                // TODO(Phase 3): a local artist has no channel
                                // to subscribe to, and CW has no local
                                // bookmark store for one.
                                Timber.w("ArtistMenu: cannot subscribe to a local artist (no channel id)")
                            } else {
                                subscribed = !subscribed
                                coroutineScope.launch {
                                    YtMusicRepository.setSubscribed(channelId, subscribed)
                                }
                            }
                        }
                    )
                )
            )
        }
    }
}
