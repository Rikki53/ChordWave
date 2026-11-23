// Ported from Mochy Music (GPL-3.0): songedit/SongEditSaver.kt — ADAPTED:
// Mochy wrote into its Room database (song row + song_edit + lyrics row)
// and its SongTuning store. CW has no song database, so the edit lands in
// the SongEdits store instead (in memory + SharedPreferences JSON, applied
// to queue items by MediaItemExt.toMediaItem), the lyrics offset goes to
// AppSettings' per-song offset store, and lyrics text goes to the lyrics
// override store through the [saveLyrics] callback the sheet supplies
// (MochyPlayerConnection.saveLyrics -> MainViewModel.saveLyricsOverride).
// The field-locking rules are Mochy's: a field is only locked once it
// differs from what YouTube gave, or was locked already.
// NOT saved, because CW has no store for them (and the sheet no longer
// shows those sections): per-song speed and per-song EQ preset —
// draft.speed / draft.eqPresetId are read and dropped.
package com.music.bitchord.ui.mochy.songedit

import android.content.Context
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.ui.mochy.models.MediaMetadata
import com.music.bitchord.ui.mochy.models.SongEdit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object SongEditSaver {
    // writes the edit into the song's hand edits, so every list shows it,
    // and a refetch can't undo it; the offset and lyrics go to their own
    // per-song stores. False only when lyrics text has nowhere to go (no
    // player connection in scope) — checked before anything is written, so
    // a failed save leaves nothing half-saved.
    suspend fun save(
        context: Context,
        songId: String,
        metadata: MediaMetadata,
        draft: SongEditDraft,
        // the lyrics to keep for this song: a picked file's text, or a
        // picked source's lyrics — the sheet folds both into this one value
        lyricsText: String?,
        saveLyrics: ((String) -> Unit)?,
    ): Boolean = withContext(Dispatchers.IO) {
        if (lyricsText != null && saveLyrics == null) return@withContext false
        val appContext = context.applicationContext

        val cover = when (val pick = draft.cover) {
            null -> null
            is CoverPick.Link -> pick.url
            // a picture that won't load leaves the cover as it was rather than failing the whole save
            is CoverPick.Picture -> SongCoverFiles.save(appContext, songId, pick.uri)
        }

        val before = SongEdits.of(songId)
        val title = draft.title.trim().ifBlank { metadata.title }
        val album = draft.album.trim().ifBlank { null }
        val oldArtists = metadata.artists.map { it.id to it.name }
        val newArtists = draft.artists.map { it.id to it.name }
        // names are the comparison that survives generated ids (fromMetadata
        // makes one up for an artist YouTube gave no id for); a real id
        // changing under the same name counts too
        val artistsChanged = draft.artists.isNotEmpty() && (
            newArtists.map { it.second } != oldArtists.map { it.second } ||
                (newArtists.size == oldArtists.size && newArtists.zip(oldArtists).any { (new, old) ->
                    old.first != null && new.first != old.first
                })
            )
        // a field is only locked once it differs from what youtube gave, or was locked already
        val edit = (before ?: SongEdit(songId)).copy(
            title = title.takeIf { it != metadata.title || before?.title != null },
            artists = if (artistsChanged) EditedArtist.encode(draft.artists) else before?.artists,
            album = album.takeIf { it != metadata.album?.title || before?.album != null },
            year = draft.year.takeIf { it != null || before?.year != null },
            cover = cover ?: before?.cover,
        )
        // an edit with nothing locked and no video choice is no edit at all
        if (edit == SongEdit(songId)) SongEdits.remove(songId) else SongEdits.put(edit)

        // the picture that's the cover now stays, every older one of this song goes
        if (cover != null) SongCoverFiles.deleteStale(appContext, songId, cover)

        // only a value that differs from what the song already plays with
        // is written down — opening the sheet and pressing OK must not pin
        // the global offset to this song. 0 clears the per-song offset,
        // falling back to the global one.
        val effectiveOffset = AppSettings.lyricsOffsetForSong(songId) ?: AppSettings.lyricsOffsetMs.value
        if (draft.lyricsOffsetMs != effectiveOffset) {
            AppSettings.setLyricsOffsetForSong(songId, draft.lyricsOffsetMs)
        }

        if (lyricsText != null) saveLyrics?.invoke(lyricsText)
        true
    }

    // the youtube video for the cover video and the video mode: a picked one, none at all, or both null to let the lookup find it again
    fun setVideo(songId: String, videoId: String?, noVideo: Boolean) {
        val edit = (SongEdits.of(songId) ?: SongEdit(songId)).copy(videoId = videoId, noVideo = noVideo)
        if (edit == SongEdit(songId)) SongEdits.remove(songId) else SongEdits.put(edit)
    }
}
