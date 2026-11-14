// Ported from Mochy Music (GPL-3.0): providers/canvas/.../AppleMusicArtistBackgroundProvider.kt
// (appleArtworkUrl only — a pure function; the provider itself is engine and
// stays a NEEDS item for the ArtistViewModel adapter).
// Fills an Apple Music artwork URL template ({w}/{h}/{f}, or a trailing
// 600x600bb.jpg-style size) at the wanted size and format.

package com.music.bitchord.ui.mochy.utils

fun appleArtworkUrl(template: String, width: Int, height: Int, format: String = "jpg"): String =
    template
        .replace("{w}", width.toString())
        .replace("{h}", height.toString())
        .replace("{f}", format)
        .replace(Regex("""(\d+x\d+(?:bb|sr|cc)(?:-\d+)?)\.(jpg|png|webp)$"""), "$1.$format")
