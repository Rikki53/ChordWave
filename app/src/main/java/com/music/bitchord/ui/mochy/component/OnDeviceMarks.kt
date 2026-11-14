// Ported from Mochy Music (GPL-3.0): ui/component/OnDeviceMarks.kt
// OnDeviceMarks.kt
//
// What a song row shows when the song plays from this device: a grey disc with a down arrow, as
// Apple Music marks its downloads, and for a file imported from the device its format ("FLAC",
// "MP3") in the player's badge.

package com.music.bitchord.ui.mochy.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.music.bitchord.R
import com.music.bitchord.download.Downloads
import com.music.bitchord.ui.mochy.player.StreamFormatBadge

/** Imported files have ids of their own; everything else is a YouTube video id. */
fun isImportedFile(songId: String): Boolean = songId.startsWith("LOCAL_")

/**
 * Whether the song with [songId] plays from this device: a file imported from it, or a finished
 * download. Follows the download, so the mark comes and goes as it finishes or is removed.
 */
@Composable
fun rememberOnDevice(songId: String?): Boolean {
    if (songId == null) return false
    if (isImportedFile(songId)) return true
    // Adapted for ChordWave (no LocalDownloadUtil): a finished download is
    // a key in Downloads.saved. In-progress downloads read as not-on-device,
    // same as Mochy's STATE_COMPLETED check.
    val saved by Downloads.saved.collectAsState()
    return songId in saved
}

/** An imported file's format, as the player's badge names it: "FLAC", "MP3", "AAC"... */
// STUBBED: CW has no format store (no FormatEntity equivalent), so the badge
// never shows for imported files. The function stays so the call site is
// untouched when a format source lands.
@Composable
private fun rememberFileFormat(songId: String): String? {
    return null
}

/** The marks for [songId], or nothing when it doesn't play from this device. */
@Composable
fun OnDeviceMarks(songId: String?, modifier: Modifier = Modifier) {
    if (songId == null || !rememberOnDevice(songId)) return
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier,
    ) {
        if (isImportedFile(songId)) {
            rememberFileFormat(songId)?.let { StreamFormatBadge(text = it, height = 15.dp) }
        }
        OnDeviceIcon()
    }
}

/** A grey disc with a dark down arrow: on this device. */
@Composable
fun OnDeviceIcon(modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(17.dp)
            .background(Color.White.copy(alpha = 0.45f), CircleShape),
    ) {
        Icon(
            painter = painterResource(R.drawable.arrow_downward),
            contentDescription = "On this device",
            tint = Color.Black.copy(alpha = 0.8f),
            modifier = Modifier.size(12.dp),
        )
    }
}
