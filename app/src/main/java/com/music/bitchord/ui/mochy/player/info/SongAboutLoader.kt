// SongAboutLoader.kt — ChordWave adapter (NOT a Mochy port): fills the
// staged SongAbout for the About sheet/card from what ChordWave can actually
// say about a song.
//
// Mochy's loader read its library, then fetched Musixmatch (incl. the
// hidden-browser page read), Genius, Wikipedia, the YouTube description,
// module facts and artist photos in parallel, emitting progressive updates.
// ChordWave has no Musixmatch/Genius/Wikipedia/module source, so those
// fields stay null — but their *Done flags are set, so the sheet shows its
// "nothing yet" states instead of spinning forever. What ChordWave DOES
// have, and this loader fills:
// - The lead artist's YouTube Music bio (YtMusicRepository.artistLanding,
//   one request per credited artist): shown on the sheet's YouTube tab.
//   Mochy's YouTube tab showed the song's video description; ChordWave has
//   no video-description fetch, so the artist bio stands in — it genuinely
//   comes from YouTube, and the "provided by youtube" footer stays true.
//   The mapping is documented, not hidden.
// - Artist photos (the same artistLanding header) for the sheet's "By" row.
// - Release year + album (SongInfoRepository.load: the YouTube watch page
//   plus the album header) for the facts section's Released/Album lines.
// - The YouTube Music watch link for the "Also available on" row.
//
// Emits twice: the metadata-only base at once (facts from the playing song,
// artists without photos), then the fetched result. A fetch that fails or
// times out (15 s) still emits the final state with the done flags set —
// the sheet never waits on the network forever. Local files skip the
// network entirely (no YouTube video to look up).

package com.music.bitchord.ui.mochy.player.info

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import com.music.bitchord.data.SongInfoRepository
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.model.Song as CwSong
import com.music.bitchord.ui.mochy.importer.account.AccountService
import com.music.bitchord.ui.mochy.models.MediaMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

object SongAboutLoader {

    @Volatile
    private var loggedMissingSources = false

    /** The about data for [song], emitted first from metadata, then with the fetched details. */
    fun load(song: MediaMetadata): Flow<SongAbout> = flow {
        val base = baseAbout(song)
        emit(base)
        val fetched = runCatching {
            withTimeoutOrNull(FetchTimeoutMs) { fetch(song) }
        }.getOrNull()
        emit(
            base.copy(
                youtubeDescription = fetched?.bio,
                youtubeDone = true,
                musixmatchDone = true,
                musixmatchPageDone = true,
                geniusDone = true,
                wikiDone = true,
                moduleDone = true,
                libraryYear = fetched?.releaseYear,
                libraryAlbum = fetched?.album ?: base.libraryAlbum,
                artists = fetched?.artists ?: base.artists,
            ),
        )
        logMissingSourcesOnce()
    }.flowOn(Dispatchers.IO)

    /** What the sheet can show before anything is fetched: facts from the metadata alone. */
    private fun baseAbout(song: MediaMetadata): SongAbout = SongAbout(
        artist = song.artists.firstOrNull()?.name,
        lengthSeconds = song.duration.takeIf { it > 0 },
        libraryAlbum = song.album?.title,
        artists = song.artists.map { AboutArtist(it.id, it.name, null) },
        links = storeLinks(song),
    )

    private suspend fun fetch(song: MediaMetadata): Fetched = coroutineScope {
        val artistJobs = song.artists.map { artist ->
            async {
                val id = artist.id
                if (id.isNullOrBlank()) {
                    null
                } else {
                    YtMusicRepository.artistLanding(id).getOrNull()
                }
            }
        }
        val infoJob = async {
            // A local file has no YouTube video to look up.
            if (song.localUri != null || song.id.startsWith("LOCAL_")) {
                null
            } else {
                runCatching {
                    SongInfoRepository.load(
                        CwSong(
                            videoId = song.id,
                            title = song.title,
                            artist = song.artists.joinToString { it.name },
                            thumbnailUrl = song.thumbnailUrl,
                            artistId = song.artists.firstOrNull()?.id,
                            albumId = song.album?.id,
                            albumName = song.album?.title,
                        ),
                    )
                }.getOrNull()
            }
        }
        val pages = artistJobs.awaitAll()
        val info = infoJob.await()
        Fetched(
            bio = pages.firstNotNullOfOrNull { it?.description?.trim()?.takeIf { text -> text.isNotEmpty() } },
            artists = song.artists.mapIndexed { index, artist ->
                AboutArtist(artist.id, artist.name, pages.getOrNull(index)?.thumbnailUrl)
            },
            releaseYear = info?.releaseYear?.toIntOrNull(),
            album = info?.album,
        )
    }

    private fun storeLinks(song: MediaMetadata): List<StoreLink> {
        if (song.localUri != null || song.id.startsWith("LOCAL_")) return emptyList()
        return listOf(
            StoreLink(
                AccountService.YOUTUBE_MUSIC,
                "https://music.youtube.com/watch?v=${song.id}",
            ),
        )
    }

    private fun logMissingSourcesOnce() {
        if (loggedMissingSources) return
        loggedMissingSources = true
        Timber.w(
            "SongAboutLoader: Musixmatch/Genius/Wikipedia have no ChordWave source; " +
                "about text comes from the artist's YouTube Music bio only",
        )
    }

    private class Fetched(
        val bio: String?,
        val artists: List<AboutArtist>,
        val releaseYear: Int?,
        val album: String?,
    )

    private const val FetchTimeoutMs = 15_000L
}

/** Collects [SongAboutLoader.load] for the About sheet; the base shows while the fetch runs. */
@Composable
fun rememberSongAbout(song: MediaMetadata): State<SongAbout> {
    val flow = remember(song.id) { SongAboutLoader.load(song) }
    return flow.collectAsState(
        initial = SongAbout(
            artist = song.artists.firstOrNull()?.name,
            lengthSeconds = song.duration.takeIf { it > 0 },
            libraryAlbum = song.album?.title,
            artists = song.artists.map { AboutArtist(it.id, it.name, null) },
        ),
    )
}
