// Ported from Mochy Music (GPL-3.0): viewmodels/MoodAndGenresViewModel.kt
// The mood/genre browse for the Modern Search tab, re-implemented over
// ChordWave sources: YtMusicRepository.moodAndGenres() supplies the
// sections, moodGenreShelves() supplies each tile's covers. Mochy's shape
// is kept: a shared covers cache + `requested` dedup set (each tile's
// LaunchedEffect fires per composition), a 3-permit gate, and a failed
// cover fetch releasing its key so a later composition retries.
//
// Adaptations vs Mochy:
// - coverKey lives in ui.mochy.screens.search (CoverKey.kt, ported by
//   phase2-search) and is imported here, not redeclared.
// - Stripe colour: CW's MoodGenre DOES carry YouTube's own stripe colour
//   (stripeColor: Long?, ARGB) — used when present. Only buttons with no
//   colour fall back to a stable colour picked from a fixed palette by the
//   title hash, so the same tile always gets the same stripe.
// - Covers browse through moodGenreShelves(), which the repo caches per
//   browseId+params — a tile whose cover already appeared does not cost a
//   second request when tapped (Mochy re-browsed).
// - Failures log through Timber (Mochy used reportException / a silent
//   empty block) and never crash; a failed sections fetch leaves the
//   previous value (null = the loading dots stay).

package com.music.bitchord.ui.mochy.viewmodels

import androidx.compose.runtime.mutableStateMapOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.model.MoodGenre
import com.music.bitchord.data.model.MoodGenreSection
import com.music.bitchord.ui.mochy.models.innertube.BrowseEndpoint
import com.music.bitchord.ui.mochy.models.innertube.MoodAndGenres
import com.music.bitchord.ui.mochy.screens.search.coverKey
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import timber.log.Timber

private object MoodCoverCache {
    val covers = mutableStateMapOf<String, List<String>>()
    val requested = java.util.Collections.synchronizedSet(mutableSetOf<String>())
}

@HiltViewModel
class MoodAndGenresViewModel @Inject constructor() : ViewModel() {
    val moodAndGenres = MutableStateFlow<List<MoodAndGenres>?>(null)

    val covers = MoodCoverCache.covers

    private val gate = Semaphore(3)

    init {
        // on io: the page is parsed where it's read, and on the main thread that hitched the search tab opening
        viewModelScope.launch(Dispatchers.IO) {
            YtMusicRepository
                .moodAndGenres()
                .onSuccess { sections ->
                    moodAndGenres.value = sections.map { it.toMochy() }
                }.onFailure {
                    Timber.w(it, "Search: moodAndGenres() failed, keeping previous sections")
                }
        }
    }

    fun requestCovers(browseId: String, params: String?) {
        val key = coverKey(browseId, params)
        if (!MoodCoverCache.requested.add(key)) return
        viewModelScope.launch(Dispatchers.IO) {
            gate.withPermit {
                YtMusicRepository.moodGenreShelves(browseId, params)
                    .onSuccess { shelves ->
                        val urls = shelves
                            .asSequence()
                            .flatMap { it.items.asSequence() }
                            .mapNotNull { it.thumbnailUrl }
                            .distinct()
                            .take(2)
                            .toList()
                        if (urls.isNotEmpty()) {
                            MoodCoverCache.covers[key] = urls
                        }
                    }
                    .onFailure {
                        MoodCoverCache.requested.remove(key)
                        Timber.w(it, "Search: moodGenreShelves failed for $browseId")
                    }
            }
        }
    }
}

private fun MoodGenreSection.toMochy(): MoodAndGenres =
    MoodAndGenres(
        title = title,
        items = items.map { genre ->
            MoodAndGenres.Item(
                title = genre.title,
                stripeColor = genre.stripeColorOrFallback(),
                endpoint = BrowseEndpoint(browseId = genre.browseId, params = genre.params),
            )
        },
    )

/** YouTube's own stripe colour when the button carries one, else a stable palette pick by title hash. */
private fun MoodGenre.stripeColorOrFallback(): Long =
    stripeColor ?: STRIPE_PALETTE[Math.floorMod(title.hashCode(), STRIPE_PALETTE.size)]

/** Muted ARGB stripes for buttons YouTube sent without a colour. */
private val STRIPE_PALETTE = listOf(
    0xFFE57373L,
    0xFFBA68C8L,
    0xFF64B5F6L,
    0xFF4DB6ACL,
    0xFFAED581L,
    0xFFFFD54FL,
    0xFFFF8A65L,
    0xFF90A4AEL,
)
