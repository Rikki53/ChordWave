// Ported from Mochy Music (GPL-3.0): data/entities/FormatEntity.kt
// Plain data class: Mochy's is a Room entity holding one YouTube format
// record per song. ChordWave has no per-song format cache (formats are
// resolved at play time), so AlbumViewModel.format stays null for now and
// the album stats line simply omits the codec/bitrate part — the screen
// already handles a null format. The class is ported verbatim so a later
// phase can fill it from the player without touching the screens.
// (phase2-detail NEEDS.md §(a) C1.)
package com.music.bitchord.ui.mochy.models

import androidx.compose.runtime.Immutable

@Immutable
data class FormatEntity(
    val id: String,
    val itag: Int,
    val mimeType: String,
    val codecs: String,
    val bitrate: Int,
    val sampleRate: Int?,
    val contentLength: Long,
    val loudnessDb: Double?,
    val perceptualLoudnessDb: Double?,
    val playbackUrl: String?,
)
