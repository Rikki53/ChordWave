// ArtistPageStore.kt — the in-memory cache of mapped Mochy artist pages,
// the page half of Mochy's ArtistPageCache. The staged ArtistPageCache
// (ui.mochy.utils) only holds headers and picks — its page map was dropped
// at staging because Mochy's innertube ArtistPage was not ported then. Now
// that the type exists (models/innertube/ArtistPage.kt), this store holds
// the mapped pages so ArtistScreen, ArtistSimilarScreen and
// AlbumViewModel.moreByArtist share one fetch per artist per run instead of
// each re-browsing the page. At integration the coordinator may fold this
// into ArtistPageCache as page()/putPage() (phase2-detail NEEDS.md §(a) C4)
// and delete this file; the VMs only touch page()/putPage().
package com.music.bitchord.ui.mochy.viewmodels

import com.music.bitchord.ui.mochy.models.innertube.ArtistPage
import java.util.concurrent.ConcurrentHashMap

internal object ArtistPageStore {
    private val pages = ConcurrentHashMap<String, ArtistPage>()

    fun page(artistId: String): ArtistPage? = pages[artistId]

    fun putPage(artistId: String, page: ArtistPage) {
        pages[artistId] = page
    }
}
