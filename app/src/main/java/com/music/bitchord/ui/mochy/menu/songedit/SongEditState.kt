// Ported from Mochy Music (GPL-3.0): ui/menu/songedit/SongEditState.kt
// State class verbatim (fields, draft()). The LOADER changed: Mochy's
// load(database, tuning, songId) read the song row + per-song tuning from
// its db; CW has no song db and no tuning store, so the state is built from
// the MediaMetadata the caller already has (fromMetadata). Everything the
// db used to supply falls back to the metadata / neutral defaults:
//   title/artists/album  <- metadata (artists keep their ids; a missing id
//                           gets a generated local id — see SongEditSupport)
//   currentCover         <- metadata.thumbnailUrl
//   year                 <- null (MediaMetadata carries no year)
//   lyricsOffsetMs       <- 0 (no lyrics store yet — NEEDS.md §(b) L1)
//   speed                <- 1f (no per-song tuning store — NEEDS.md §(b) P1)
//   eqPresetId           <- null (no per-song EQ store — NEEDS.md §(b) E2)
// Types come from the staged songedit support file (EditedArtist,
// CoverPick, SongEditDraft, PickedLyrics) and lyrics/ProviderCandidate.

package com.music.bitchord.ui.mochy.menu.songedit

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.music.bitchord.ui.mochy.lyrics.ProviderCandidate
import com.music.bitchord.ui.mochy.models.MediaMetadata
import com.music.bitchord.ui.mochy.songedit.CoverPick
import com.music.bitchord.ui.mochy.songedit.EditedArtist
import com.music.bitchord.ui.mochy.songedit.PickedLyrics
import com.music.bitchord.ui.mochy.songedit.SongEditDraft
import com.music.bitchord.ui.mochy.songedit.generateEditedArtistId
import kotlin.math.abs

// lyric offset range from the mock: two minutes early to three minutes late
internal const val OffsetMinMs = -120_000
internal const val OffsetMaxMs = 180_000

internal const val SpeedMin = 0.25f
internal const val SpeedMax = 5f

// what the edit sheet is changing; plain state outside composition, because the popup host composes the body and the ok bar apart
@Stable
internal class SongEditState(
    val metadata: MediaMetadata,
    val currentCover: String?,
    title: String,
    artists: List<EditedArtist>,
    album: String,
    year: Int?,
    lyricsOffsetMs: Int,
    speed: Float,
    eqPresetId: String?,
) {
    private val startSpeed = speed

    var title by mutableStateOf(title)
    var cover by mutableStateOf<CoverPick?>(null)
    val artists = mutableStateListOf<EditedArtist>().apply { addAll(artists) }
    var album by mutableStateOf(album)
    var year by mutableStateOf(year?.toString().orEmpty())
    var lyricsOffsetMs by mutableIntStateOf(lyricsOffsetMs)
    var speed by mutableFloatStateOf(speed)
    var eqPresetId by mutableStateOf(eqPresetId)
    var pickedLyrics by mutableStateOf<PickedLyrics?>(null)
    // another source's lyrics picked in the dropdown, applied on ok
    var lyricsSource by mutableStateOf<ProviderCandidate?>(null)

    // set while ok is saving: the button is off and the sheet can't be pulled away
    var busy by mutableStateOf(false)

    val songId: String get() = metadata.id

    fun draft() = SongEditDraft(
        title = title,
        cover = cover,
        artists = artists.toList(),
        album = album,
        year = year.trim().toIntOrNull(),
        lyricsOffsetMs = lyricsOffsetMs,
        speed = speed.takeIf { abs(it - startSpeed) > 0.001f },
        eqPresetId = eqPresetId,
        customLyrics = pickedLyrics?.text,
    )

    companion object {
        // The song as the caller has it; null only when there is no song to
        // edit at all (never, in practice — the caller always passes one).
        // Mochy's load() read the db row + tuning here; CW builds from the
        // metadata instead (see the header note).
        fun fromMetadata(metadata: MediaMetadata): SongEditState =
            SongEditState(
                metadata = metadata,
                currentCover = metadata.thumbnailUrl,
                title = metadata.title,
                artists = metadata.artists.map {
                    EditedArtist(it.id ?: generateEditedArtistId(), it.name)
                },
                album = metadata.album?.title.orEmpty(),
                year = null,
                lyricsOffsetMs = 0,
                speed = 1f,
                eqPresetId = null,
            )
    }
}
