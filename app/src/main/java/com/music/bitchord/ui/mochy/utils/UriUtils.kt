// Ported from Mochy Music (GPL-3.0): ui/utils/UriUtils.kt
// UriUtils.kt

package com.music.bitchord.ui.mochy.utils

import android.content.Context
import android.widget.Toast
import androidx.compose.ui.platform.UriHandler
import com.music.bitchord.R

fun UriHandler.safeOpenUri(context: Context, uri: String) {
    if (uri.isBlank()) return

    runCatching {
        openUri(uri)
    }.onFailure {
        Toast.makeText(
            context,
            context.getString(R.string.error_no_stream).replace("stream", "app"),
            Toast.LENGTH_SHORT
        ).show()
    }
}
