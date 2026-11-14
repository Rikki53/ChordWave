// Ported from Mochy Music (GPL-3.0): lyrics/EmbeddedLyrics.kt
// CONSTANTS ONLY. Reading lyrics out of a song file is engine work (ChordWave already has
// it: com.music.bitchord.data.lyrics.EmbeddedLyrics) and is NOT ported — see NEEDS.md §(b).
// Only the provider-name sentinel the UI compares against survives.

package com.music.bitchord.ui.mochy.lyrics

object EmbeddedLyrics {
    /** Provider name recorded when the lyrics came from inside the song file itself. */
    const val Provider = "Embedded"
}
