// Ported from Mochy Music (GPL-3.0): ui/menu/YouTubeArtistMenu.kt
// YouTubeArtistMenu.kt
//
// NOTE on the source path: the task brief placed these menus under
// ui/player/menu/, but in the Mochy source they live in ui/menu/ — the
// header above names the real file. The CW package below
// (ui.mochy.player.menu) follows the brief's output layout.
//
// ADAPTATIONS (CW has no LocalDatabase / Mochy YouTube engine — see NEEDS.md):
// - Subscribe state comes in as a param (Mochy read it from its db artist
//   record) and is kept in local rememberSaveable state; the write is
//   YtMusicRepository.setSubscribed(channelId, subscribed), with the
//   channel id from the item or, failing that, the artist id itself.
// - Shuffle / Radio build YouTubeQueue from the item's endpoints exactly as
//   in Mochy and hand it to MochyPlayerConnection.playQueue (Phase 3 stub).
// - Pin state comes in as a param; the toggle is a caller callback, and
//   without one the action logs a Phase 3 TODO (CW has no pin store yet).
// - Share is a plain ACTION_SEND intent with the item's share link.
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
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
import com.music.bitchord.ui.mochy.models.innertube.ArtistItem
import com.music.bitchord.ui.mochy.playback.LocalPlayerConnection
import com.music.bitchord.ui.mochy.playback.queues.YouTubeQueue
import kotlinx.coroutines.launch
import timber.log.Timber

@OptIn(ExperimentalMaterial3Api::class)

@Composable
fun YouTubeArtistMenu(
    artist: ArtistItem,
    onDismiss: () -> Unit,
    isSubscribed: Boolean = false,
    isPinned: Boolean = false,
    onTogglePin: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val coroutineScope = rememberCoroutineScope()
    var subscribed by rememberSaveable { mutableStateOf(isSubscribed) }

    SheetHeader(
        thumbnailUrl = artist.thumbnail,
        title = artist.title,
        subtitle = "Artist",
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
                        // Shuffle and radio first: they're the two cards, and what an artist's menu is for.
                        artist.shuffleEndpoint?.let { shuffleEndpoint ->
                            add(
                                NewAction(
                                    icon = {
                                        Icon(
                                            painter = painterResource(R.drawable.shuffle),
                                            contentDescription = null,
                                        )
                                    },
                                    text = stringResource(R.string.shuffle),
                                    onClick = {
                                        playerConnection.playQueue(YouTubeQueue(shuffleEndpoint))
                                        onDismiss()
                                    }
                                )
                            )
                        }
                        artist.radioEndpoint?.let { watchEndpoint ->
                            add(
                                NewAction(
                                    icon = {
                                        Icon(
                                            painter = painterResource(R.drawable.radio),
                                            contentDescription = null,
                                            modifier = Modifier.size(28.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    },
                                    text = stringResource(R.string.start_an_radio),
                                    onClick = {
                                        playerConnection.playQueue(YouTubeQueue(watchEndpoint))
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
                                    Timber.w("YouTubeArtistMenu: pin needs Phase 3 (pin store)")
                                }
                            }
                        )
                    )

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
                                context.shareText(artist.shareLink)
                                onDismiss()
                            }
                        )
                    )
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
                                painter = painterResource(
                                    if (subscribed) {
                                        R.drawable.subscribed
                                    } else {
                                        R.drawable.subscribe
                                    }
                                ),
                                contentDescription = null,
                            )
                        },
                        onClick = {
                            subscribed = !subscribed
                            coroutineScope.launch {
                                YtMusicRepository.setSubscribed(
                                    artist.channelId ?: artist.id,
                                    subscribed,
                                )
                            }
                        }
                    )
                )
            )
        }
    }
}
