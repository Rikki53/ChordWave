// Ported from Mochy Music (GPL-3.0): lyrics/YouTubeCaptionTranslation.kt
// REQUEST TYPE ONLY. The engine (finding the video, fetching its captions, aligning them
// with the lyrics) is NOT ported — see NEEDS.md §(b). Only the Request the UI builds for
// LyricsTranslationHelper.translateWithYouTubeCaptions survives, so the call site keeps
// its shape for the coordinator to wire.

package com.music.bitchord.ui.mochy.lyrics

object YouTubeCaptionTranslation {

    class Request(
        val songId: String,
        val title: String,
        val artist: String,
        // the track is the video itself, so no search is needed to find it
        val isVideo: Boolean,
        val lines: List<LyricsEntry>,
        val targetLanguage: String,
        // where the song sits inside a separate music video, by listening to both, for when its captions can't be lined up with the lyrics
        val matchAudio: (suspend (videoId: String) -> SongVideoMap?)? = null,
    )
}
