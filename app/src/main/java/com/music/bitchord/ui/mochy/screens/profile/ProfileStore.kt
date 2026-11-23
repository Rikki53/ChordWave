// Ported from Mochy Music (GPL-3.0): ui/screens/profile/ProfileStore.kt
// (save only — copyPhoto already lives in CW as ProfilePhotoStore).
//
// Saving the profile's name and picture. The picture is copied into the
// app's own files, because the one the picker hands over can vanish (a
// cropped copy in the cache, a deleted gallery photo).

package com.music.bitchord.ui.mochy.screens.profile

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.edit
import com.music.bitchord.ui.mochy.constants.ProfilePicUriKey
import com.music.bitchord.ui.mochy.constants.UsernameKey
import com.music.bitchord.ui.mochy.screens.setup.ProfilePhotoStore
import com.music.bitchord.ui.mochy.utils.dataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.File

object ProfileStore {

    private const val PhotoPrefix = "profile_pic_"

    /**
     * Saves [name], and [photo] if a new one was picked. It finishes even
     * if the screen that asked goes away halfway, so a closed sheet never
     * leaves a picture copied but not saved.
     */
    suspend fun save(context: Context, name: String, photo: Uri?) = withContext(NonCancellable + Dispatchers.IO) {
        val app = context.applicationContext
        val newPath = photo?.let { ProfilePhotoStore.copyPhoto(app, it) }
        var oldPath: String? = null
        app.dataStore.edit { prefs ->
            prefs[UsernameKey] = name
            if (newPath != null) {
                oldPath = prefs[ProfilePicUriKey]
                prefs[ProfilePicUriKey] = newPath
            }
        }
        oldPath?.let { deleteOldPhoto(app, it) }
    }

    /** Removes a picture this class copied earlier; anything else (a content uri, someone else's file) stays. */
    private fun deleteOldPhoto(context: Context, path: String) {
        val file = File(path)
        if (file.parentFile == context.filesDir && file.name.startsWith(PhotoPrefix)) file.delete()
    }
}
