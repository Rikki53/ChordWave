// SongAbout.kt — ported from Mochy Music (GPL-3.0): ui/player/info/SongAbout.kt
//
// Only the data is ported: SongAbout, AboutArtist, StoreLink, the derived
// facts and the date formatting. SongAboutLoader (the engine: library reads
// plus Musixmatch/Genius/Wikipedia/YouTube/module fetches) is NOT ported —
// see NEEDS.md §(b) for its contract.
//
// Mochy's source types (MusixmatchSong, GeniusSong, WikiSong, the modules'
// TrackMetadata) come from network libraries ChordWave does not have, so
// this file carries small staged stand-ins with the same fields the sheets
// read: SongAboutMusixmatch, SongAboutGenius, SongAboutWiki,
// SongAboutModule. The adapter round fills them from real fetchers.

package com.music.bitchord.ui.mochy.player.info

import com.music.bitchord.ui.mochy.importer.account.AccountService
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** everything the about sheet shows for one song, as each source said it; the facts shown are picked from them */
data class SongAbout(
    val artist: String? = null,
    val lengthSeconds: Int? = null,
    val libraryYear: Int? = null,
    val libraryAlbum: String? = null,
    val musixmatchDone: Boolean = false,
    val musixmatch: SongAboutMusixmatch? = null,
    /** musixmatch's page (what the song is about) has been read, or there was none to read */
    val musixmatchPageDone: Boolean = false,
    val geniusDone: Boolean = false,
    val genius: SongAboutGenius? = null,
    val wikiDone: Boolean = false,
    val wiki: SongAboutWiki? = null,
    val youtubeDone: Boolean = false,
    val youtubeDescription: String? = null,
    // what the song facts modules that are on said, laid together, and whose words the about text is
    val moduleDone: Boolean = false,
    val module: SongAboutModule? = null,
    val moduleName: String? = null,
    val artists: List<AboutArtist> = emptyList(),
    val links: List<StoreLink> = emptyList(),
) {
    /** every source that can fill in the facts has answered (or given up) */
    val factsDone: Boolean get() = musixmatchDone && geniusDone && wikiDone && moduleDone

    // each fact from the first source that has it: a module that's on, then musixmatch, wikipedia, genius, then the library
    val genre: String?
        get() = listOf(module?.genres, musixmatch?.genres, wiki?.genres, genius?.genres).firstOrNull { !it.isNullOrEmpty() }
            ?.take(2)?.joinToString(", ") { it.replaceFirstChar(Char::titlecase) }
    val released: String?
        get() = (module?.releaseDate ?: musixmatch?.releaseDate ?: wiki?.releaseDate ?: genius?.releaseDate)?.let(::readableDate) ?: libraryYear?.toString()
    val album: String? get() = libraryAlbum ?: module?.album ?: musixmatch?.album ?: genius?.album
    val writtenBy: String?
        get() = module?.writers.orEmpty().ifEmpty { genius?.writers.orEmpty() }.ifEmpty { wiki?.writers.orEmpty() }.joinToString(", ").ifBlank { null }
    val producedBy: String?
        get() = module?.producers.orEmpty().ifEmpty { genius?.producers.orEmpty() }.ifEmpty { wiki?.producers.orEmpty() }.joinToString(", ").ifBlank { null }
    val label: String? get() = module?.label ?: wiki?.label
}

/** what musixmatch said about the song (stand-in for Mochy's MusixmatchSong) */
data class SongAboutMusixmatch(
    /** what the song is about, from musixmatch's page */
    val meaning: String?,
    val pageUrl: String?,
    val genres: List<String>?,
    val releaseDate: String?,
    val album: String?,
)

/** what genius said about the song (stand-in for Mochy's GeniusSong) */
data class SongAboutGenius(
    val about: String?,
    val pageUrl: String?,
    val genres: List<String>?,
    val releaseDate: String?,
    val album: String?,
    val writers: List<String>?,
    val producers: List<String>?,
)

/** what wikipedia said about the song (stand-in for Mochy's WikiSong) */
data class SongAboutWiki(
    val about: String?,
    val articleUrl: String?,
    val genres: List<String>?,
    val releaseDate: String?,
    val writers: List<String>?,
    val producers: List<String>?,
    val label: String?,
)

/** what the song facts modules said (stand-in for the modules' TrackMetadata) */
data class SongAboutModule(
    val about: String?,
    val genres: List<String>?,
    val releaseDate: String?,
    val album: String?,
    val writers: List<String>?,
    val producers: List<String>?,
    val label: String?,
)

data class AboutArtist(val id: String?, val name: String, val imageUrl: String?)

data class StoreLink(val service: AccountService, val url: String)

// "2014-03-17" as the phone writes dates, "2014-03" as a month and year, "2014" as it is
private fun readableDate(iso: String): String? = runCatching {
    LocalDate.parse(iso).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
}.recoverCatching {
    YearMonth.parse(iso).format(DateTimeFormatter.ofPattern("MMM yyyy"))
}.getOrNull() ?: iso.take(4).takeIf { it.length == 4 && it.all(Char::isDigit) }
