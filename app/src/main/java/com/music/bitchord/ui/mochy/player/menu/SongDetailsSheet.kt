// Ported from Mochy Music (GPL-3.0): ui/player/menu/SongDetailsSheet.kt
// UI ported; the two engine-backed sections are wired by the Phase 3
// adapter round (NEEDS.md §(b) I1/I2):
// - ABOUT: SongAboutLoader (phase3-adapter-host, player/info) loads the
//   artist bio from YtMusicRepository.artistLanding + release facts from
//   SongInfoRepository; the "About song" row opens AboutSongSheet
//   (phase3-player-queue-info, ui/player/info), stacked on this sheet.
// - STREAM INFO: the row is enabled whenever this sheet renders — its song
//   IS the playing song (the sheet returns early with no metadata) — and
//   opens StreamInfoSheet, whose inputs come from StreamStatsAdapter
//   (NerdStats snapshot). FormatEntity stays null: CW formats resolve at
//   play time and are not kept.
// - ARTIST AVATARS: rememberArtistAvatar read the db, then YouTube. With
//   neither available it returns null and the row shows its person glyph.
//   Contract in NEEDS.md §(b) I1.
// - Artist/album rows are fully wired to the onOpenArtist/onOpenAlbum
//   callbacks, as in Mochy.

package com.music.bitchord.ui.mochy.player.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.music.bitchord.R
import com.music.bitchord.ui.mochy.component.RemoteFocus
import com.music.bitchord.ui.mochy.models.MediaMetadata
import com.music.bitchord.ui.mochy.playback.LocalPlayerConnection
import com.music.bitchord.ui.mochy.player.info.AboutSongSheet
import com.music.bitchord.ui.mochy.player.info.StreamInfoSheet
import com.music.bitchord.ui.mochy.player.info.rememberSongAbout
import com.music.bitchord.ui.mochy.utils.resize

/** tapping the player's title opens this: the song, a few lines about it, its artists and album, then about song and stream info */
@Composable
fun SongDetailsSheet(
    onDismiss: () -> Unit,
    onOpenArtist: (String) -> Unit,
    onOpenAlbum: (String) -> Unit,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val metadata by playerConnection.mediaMetadata.collectAsState()
    val song = metadata ?: return
    // Mochy enabled stream info only while service.playingFrom contained
    // the song. This sheet only renders for the playing song (it returns
    // early with no metadata), so the row is always enabled here.
    val streaming = true
    // Shared by this sheet and the about sheet, so the about sheet stacks on this one (it shrinks
    // back and peeks its handle above) instead of opening over it as a second dimmed sheet.
    val stack = rememberMenuSheetStack()
    var showAbout by remember { mutableStateOf(false) }
    var showStreamInfo by remember { mutableStateOf(false) }

    CompositionLocalProvider(LocalMenuSheetStack provides stack) {
        MenuSheetSurface(onDismiss = onDismiss, wrapHeight = true) { _ ->
            val closeSheet = LocalMenuSheetClose.current
            SheetColumn {
                SongHeader(song)
                Spacer(modifier = Modifier.height(18.dp))
                // STUB: Mochy showed AboutPreviewCard(about) here, loaded by
                // SongAboutLoader from ui/player/info (another chunk's scope,
                // not in the worktree). Omitted until that chunk lands —
                // NEEDS.md §(a) A2.

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Every artist credited, each its own row: a duet or a feature lists two or
                    // three. One with no id (a local file) has no page to open, so it isn't offered.
                    song.artists.filter { !it.id.isNullOrBlank() }.forEach { artist ->
                        val artistId = artist.id!!
                        val avatar by rememberArtistAvatar(artistId)
                        DetailRow(
                            title = "View ${artist.name}",
                            onClick = { onOpenArtist(artistId) },
                        ) {
                            DetailImage(
                                url = avatar,
                                shape = CircleShape,
                                placeholder = R.drawable.person,
                            )
                        }
                    }
                    song.album?.let { album ->
                        DetailRow(
                            title = "View ${album.title}",
                            onClick = { onOpenAlbum(album.id) },
                        ) {
                            DetailImage(
                                url = song.thumbnailUrl?.resize(120, 120),
                                shape = RoundedCornerShape(7.dp),
                                placeholder = R.drawable.album,
                            )
                        }
                    }
                    DetailRow(
                        title = "About song",
                        onClick = { showAbout = true },
                    ) {
                        DetailGlyph(R.drawable.info)
                    }
                    DetailRow(
                        title = "Stream info",
                        enabled = streaming,
                        onClick = { showStreamInfo = true },
                    ) {
                        DetailGlyph(R.drawable.graphic_eq)
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))
                DoneButton(onClick = closeSheet)
            }
        }

        // Stacked on this sheet (same stack): the loader only starts when
        // the about sheet opens, so opening Details never fetches.
        if (showAbout) {
            val about by rememberSongAbout(song)
            AboutSongSheet(
                song = song,
                about = about,
                onDismiss = { showAbout = false },
                onOpenArtist = onOpenArtist,
            )
        }
        if (showStreamInfo) {
            StreamInfoSheet(onDismiss = { showStreamInfo = false })
        }
    }
}

@Composable
private fun ColumnScope.SheetColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .weight(1f, fill = false)
            // the full video mode's card is only as tall as the phone is wide, so the rows scroll there
            .verticalScroll(rememberScrollState())
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(top = 12.dp, bottom = 22.dp),
        content = content,
    )
}

/** The song itself at the top of the song's sheets, like the player's action menu has it. */
@Composable
internal fun SongHeader(song: MediaMetadata) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        AsyncImage(
            model = song.thumbnailUrl?.resize(240, 240),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.08f)),
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                color = Color.White,
                fontSize = 16.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Bold,
                // two lines before it gives up: long titles and features were cut halfway through a word
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val artists = song.artists.joinToString(", ") { it.name }
            if (artists.isNotBlank()) {
                Text(
                    text = artists,
                    color = Color(0xFFB3B3B3),
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private val DetailImageSize = 28.dp

/** A row of the details sheet: the picture it's about, then what tapping it does. */
@Composable
private fun DetailRow(
    title: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    leading: @Composable () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = if (enabled) 1f else 0.4f }
            .clip(RoundedCornerShape(16.dp))
            .background(MenuRowSurface)
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = RemoteFocus,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        leading()
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = title,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

/** a row's white square with a glyph on it, for the rows that aren't a person or a cover */
@Composable
private fun DetailGlyph(icon: Int) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(DetailImageSize)
            .clip(RoundedCornerShape(7.dp))
            .background(Color.White.copy(alpha = 0.9f)),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = Color.Black.copy(alpha = 0.75f),
            modifier = Modifier.size(16.dp),
        )
    }
}

/** A photo or cover in a row; the glyph shows until (or instead of, if there's none) it loads. */
@Composable
private fun DetailImage(url: String?, shape: Shape, placeholder: Int) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(DetailImageSize)
            .clip(shape)
            .background(Color.White.copy(alpha = 0.9f)),
    ) {
        Icon(
            painter = painterResource(placeholder),
            contentDescription = null,
            tint = Color.Black.copy(alpha = 0.6f),
            modifier = Modifier.size(16.dp),
        )
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(DetailImageSize),
            )
        }
    }
}

/** The mock's "done": a full-width pill, lighter than the rows so it reads as the way out. */
@Composable
private fun DoneButton(onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.24f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = RemoteFocus,
                onClick = onClick,
            ),
    ) {
        Text(text = "done", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

/**
 * The artist's own photo for [artistId].
 *
 * STUB (NEEDS.md §(b) I1): Mochy read the library's copy from its db, else
 * the artist's YouTube Music page. CW has neither source wired here, so this
 * always returns null and the row shows its placeholder glyph.
 */
@Composable
private fun rememberArtistAvatar(artistId: String): State<String?> {
    return remember(artistId) { mutableStateOf<String?>(null) }
}
