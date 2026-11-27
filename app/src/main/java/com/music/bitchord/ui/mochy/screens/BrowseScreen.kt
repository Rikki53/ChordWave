// Ported from Mochy Music (GPL-3.0): ui/screens/BrowseScreen.kt
// BrowseScreen.kt — A browse endpoint's items as a grid (albums/playlists/artists).
//
// Port notes (see staging/phase2-detail/NEEDS.md for the full list):
// - Navigation goes through MochyNavigator; route strings are Mochy's verbatim.
//   Back is popBackStack(); back-to-main is clearDetails().
// - The screen never touches the database: data comes through the VM contract.
// - IconButton is ported in this dir (component/IconButton.kt); the
//   browseId param comes from the route (NEEDS.md §(d).3).

package com.music.bitchord.ui.mochy.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.bitchord.ui.mochy.LocalPlayerAwareWindowInsets
import com.music.bitchord.ui.mochy.navigation.MochyNavigator
import com.music.bitchord.ui.mochy.playback.LocalPlayerConnection
import com.music.bitchord.R
import com.music.bitchord.ui.mochy.constants.GridItemSize
import com.music.bitchord.ui.mochy.constants.GridItemsSizeKey
import com.music.bitchord.ui.mochy.constants.GridThumbnailHeight
import com.music.bitchord.ui.mochy.component.IconButton
import com.music.bitchord.ui.mochy.component.LocalMenuState
import com.music.bitchord.ui.mochy.component.YouTubeGridItem
import com.music.bitchord.ui.mochy.component.shimmer.GridItemPlaceHolder
import com.music.bitchord.ui.mochy.component.shimmer.ShimmerHost
import com.music.bitchord.ui.mochy.player.menu.YouTubeAlbumMenu
import com.music.bitchord.ui.mochy.player.menu.YouTubeArtistMenu
import com.music.bitchord.ui.mochy.player.menu.YouTubePlaylistMenu
import com.music.bitchord.ui.mochy.utils.rememberEnumPreference
import com.music.bitchord.ui.mochy.viewmodels.BrowseViewModel

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(
    navigator: MochyNavigator,
    scrollBehavior: TopAppBarScrollBehavior,
    browseId: String?,
    viewModel: BrowseViewModel = hiltViewModel(),
) {
    // Coordinator: route args rule — browseId comes from the host route; load when present.
    LaunchedEffect(browseId) { browseId?.let { viewModel.load(it) } }
     val menuState = LocalMenuState.current
     val playerConnection = LocalPlayerConnection.current ?: return
     val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

     val title by viewModel.title.collectAsState()
     val items by viewModel.items.collectAsState()

     val coroutineScope = rememberCoroutineScope()
     val gridItemSize by rememberEnumPreference(GridItemsSizeKey, GridItemSize.BIG)

     val distinctItems = remember(items) { items?.distinctBy { it.id } }

     LazyVerticalGrid(
         columns = GridCells.Adaptive(minSize = GridThumbnailHeight + if (gridItemSize == GridItemSize.BIG) 24.dp else (-24).dp),
         contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues()
     ) {
         distinctItems?.let { items ->
             items(
                 items = items,
                 key = { it.id },
                 contentType = { it::class }
             ) { item ->
                 YouTubeGridItem(
                     item = item,
                     fillMaxWidth = true,
                     coroutineScope = coroutineScope,
                     modifier = Modifier
                         .combinedClickable(
                             onClick = {
                                 when (item) {
                                     is AlbumItem -> navigator.navigate("album/${item.id}")
                                     is PlaylistItem -> navigator.navigate("online_playlist/${item.id}")
                                     is ArtistItem -> navigator.navigate("artist/${item.id}")
                                     else -> {

                                     }
                                 }
                             },
                             onLongClick = {
                                 menuState.show {
                                     when (item) {
                                         is AlbumItem ->
                                             YouTubeAlbumMenu(
                                                 albumItem = item,
                                                 navigator = navigator,
                                                 onDismiss = menuState::dismiss
                                             )

                                         is PlaylistItem -> {
                                             YouTubePlaylistMenu(
                                                 playlist = item,
                                                 coroutineScope = coroutineScope,
                                                 onDismiss = menuState::dismiss,
                                                 onImportedPlaylist = { playlistId ->
                                                     navigator.navigate("local_playlist/$playlistId")
                                                 }
                                             )
                                         }

                                         is ArtistItem -> {
                                             YouTubeArtistMenu(
                                                 artist = item,
                                                 onDismiss = menuState::dismiss
                                             )
                                         }

                                         else -> {

                                         }
                                     }
                                 }
                             }
                         )
                 )
             }

             if (items.isEmpty()) {
                 items(8) {
                     ShimmerHost {
                         GridItemPlaceHolder(fillMaxWidth = true)
                     }
                 }
             }
         }
     }

     TopAppBar(
         title = { Text(title ?: "") },
         navigationIcon = {
             IconButton(
                 onClick = { navigator.popBackStack() },
                 onLongClick = { navigator.clearDetails() }
             ) {
                 Icon(
                     painterResource(R.drawable.arrow_back_ios),
                     contentDescription = null
                 )
             }
         }
     )
 }
