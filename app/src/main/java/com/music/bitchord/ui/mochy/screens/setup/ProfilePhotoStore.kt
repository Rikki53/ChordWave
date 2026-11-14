// Ported from Mochy Music (GPL-3.0): ui/screens/profile/ProfileStore.kt
// (copyPhoto only). The full Profile screen is out of scope for the clone;
// the wizard's completion handler needs just this one function: persist the
// picked/cropped profile picture under filesDir so the path survives the
// cache-cleaning that would eat the cropper's cacheDir output.

package com.music.bitchord.ui.mochy.screens.setup

import android.content.Context
import android.net.Uri
import timber.log.Timber
import java.io.File

object ProfilePhotoStore {

    private const val PhotoPrefix = "profile_pic_"

    fun copyPhoto(context: Context, uri: Uri): String? = runCatching {
        // a fresh name each time, so image caches never hand back the old picture
        val file = File(context.filesDir, "$PhotoPrefix${System.currentTimeMillis()}.jpg")
        context.contentResolver.openInputStream(uri)!!.use { input ->
            file.outputStream().use { input.copyTo(it) }
        }
        file.absolutePath
    }.onFailure { Timber.w(it, "Couldn't copy the profile picture") }.getOrNull()
}
