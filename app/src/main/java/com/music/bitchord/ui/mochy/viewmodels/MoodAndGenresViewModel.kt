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
// - requestMoodCovers serves Home's Find Your Mood tiles: the same
//   moodGenreShelves() browse, but keyed per TopPickMood (moods share
//   YouTube categories, so a per-category key would paint eight tiles
//   with the same two covers) and steered by the mood's shelfWords, the
//   way TopPicks.moodQueue picks the shelf to play. The repo caches the
//   browse per category, so 27 moods cost at most 11 requests.

package com.music.bitchord.ui.mochy.viewmodels

import androidx.compose.runtime.mutableStateMapOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.model.MoodGenre
import com.music.bitchord.data.model.TopPickMood
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

    /**
     * Covers for one Home mood tile: the mood's own category page, with
     * shelves scored by the mood's shelfWords (same scoring as
     * TopPicks.moodQueue) so moods sharing a category still get covers
     * from the shelves that fit them. Keyed per mood in the shared cache.
     */
    fun requestMoodCovers(mood: TopPickMood) {
        val key = moodCoverKey(mood)
        if (!MoodCoverCache.requested.add(key)) return
        viewModelScope.launch(Dispatchers.IO) {
            gate.withPermit {
                YtMusicRepository.moodGenreShelves(TopPickMood.CATEGORY_BROWSE_ID, mood.params)
                    .onSuccess { shelves ->
                        val score = { shelf: com.music.bitchord.data.model.HomeShelf ->
                            val title = shelf.title.lowercase(java.util.Locale.ROOT)
                            mood.shelfWords.count { it in title }
                        }
                        val urls = shelves
                            .sortedByDescending(score)
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
                        Timber.w(it, "Home: moodGenreShelves failed for $mood")
                    }
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

/**
 * Cache key for a Home mood tile's covers: the category page plus the
 * mood itself, so moods sharing a YouTube category keep distinct covers.
 */
fun moodCoverKey(mood: TopPickMood): String =
    coverKey(TopPickMood.CATEGORY_BROWSE_ID, mood.params + "|" + mood.name)

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
