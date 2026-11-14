// Ported from Mochy Music (GPL-3.0): viewmodels/IpodLibraryViewModel.kt (data + API surface only)
// [CW] Mochy's IpodLibraryViewModel is a Hilt ViewModel over its Room database and is NOT
// ported (ViewModels are out of scope for this chunk). What the iPod player needs from it
// is captured here as IpodLibrarySource: the coordinator's adapter round provides an
// implementation backed by ChordWave's library (see NEEDS.md §(b) B5 for the exact data
// each page needs). Until then IpodPlayerStyle runs with library = null and its library
// pages stay on their loading state — the wheel, Now Playing and Cover Flow shells still work.
package com.music.bitchord.ui.mochy.player.ipod

import androidx.media3.common.MediaItem

// one album in cover flow: its id, name, artist and cover url, as Mochy's IpodAlbum holds it
class IpodAlbum(val id: String, val title: String, val artist: String, val cover: String?)

// the library as the iPod player's menus see it; Mochy warms the cache in the background so
// opening a menu doesn't wait on the database — the adapter should do the same.
interface IpodLibrarySource {
    // covers for the main menu's slideshow, warmed in memory; null until the first load
    val cachedCovers: List<String>?

    // the cover-flow albums, warmed in memory; null until the first load
    val cachedCoverFlow: List<IpodAlbum>?

    // a page's list when it's already warmed in memory, null when it still needs loading
    fun cached(page: IpodPage): IpodList?

    // loads a page's list (playlists, artists, albums, songs, liked songs, one playlist's,
    // one artist's or one album's songs); suspends on a background dispatcher
    suspend fun load(page: IpodPage): IpodList

    // re-reads a page in the background after showing its warmed list
    fun refreshAsync(page: IpodPage)

    // up to 24 cover urls for the main menu's slideshow (albums, most recent first)
    suspend fun covers(): List<String>

    // every album for cover flow, by name
    suspend fun coverFlow(): List<IpodAlbum>

    // the whole library shuffled, as media items ready to queue
    suspend fun shuffledLibrary(): List<MediaItem>
}
