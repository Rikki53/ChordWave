// Where downloads and the song cache live, and where they could go.
// Adapted from Mochy Music's playback/StoragePlaces.kt (GPL-3.0), rebadged to
// ChordWave and reshaped to the 4D contract: StoragePlace.root is a String
// path here, not a File, and there is no per-kind placement to read back —
// ChordWave keeps downloads in MediaStore (Music/ChordWave) and the cache in
// its own data dir, both on the phone, so placeOf() is always the phone until
// a real move engine exists. Nothing here writes anything.

package com.music.bitchord.ui.mochy.viewmodels

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import android.provider.DocumentsContract
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import com.music.bitchord.R
import java.io.File

object StoragePlaces {
    private const val PickedFolderName = "ChordWave"
    private const val ExternalStorageAuthority = "com.android.externalstorage.documents"

    /** The place [kind] lives in. Always the phone: ChordWave has no per-kind placement yet. */
    fun placeOf(context: Context, kind: StoredKind): StoragePlace = places(context).first()

    /** The phone first, then every memory card or drive Android gives ChordWave a folder on. */
    fun places(context: Context): List<StoragePlace> {
        val storage = context.getSystemService<StorageManager>()
        val phone = describe(
            context.filesDir,
            context.getString(R.string.storage_place_phone),
            isPhone = true,
            storage = null,
            context = context,
        )
        val drives = context.getExternalFilesDirs(null).drop(1).filterNotNull()
            .filter { Environment.getExternalStorageState(it) == Environment.MEDIA_MOUNTED }
            .map { describe(it, null, isPhone = false, storage = storage, context = context) }
        return listOf(phone) + drives
    }

    private fun describe(
        root: File,
        label: String?,
        isPhone: Boolean,
        storage: StorageManager?,
        context: Context,
    ): StoragePlace {
        val stat = runCatching { StatFs(root.path) }.getOrNull()
        val name = label
            ?: runCatching { storage?.getStorageVolume(root)?.getDescription(context) }.getOrNull()
            ?: context.getString(R.string.storage_place_card)
        return StoragePlace(
            root = root.path,
            label = name,
            free = stat?.availableBytes ?: 0L,
            total = stat?.totalBytes ?: 0L,
            isPhone = isPhone,
            isFolder = false,
        )
    }

    /** The folder ChordWave saves into for one the user picked: its own "ChordWave" folder inside. */
    fun rootForPicked(picked: File): File =
        if (picked.name.equals(PickedFolderName, ignoreCase = true)) picked
        else File(picked, PickedFolderName)

    /** A picked folder described like a drive: where it is, and that drive's room. */
    fun folderPlace(context: Context, root: File): StoragePlace {
        var existing: File? = root
        while (existing != null && !existing.exists()) existing = existing.parentFile
        val stat = runCatching { StatFs((existing ?: root).path) }.getOrNull()
        return StoragePlace(
            root = root.path,
            label = readablePath(context, root),
            free = stat?.availableBytes ?: 0L,
            total = stat?.totalBytes ?: 0L,
            isPhone = false,
            isFolder = true,
        )
    }

    /** A folder from the folder picker as a path ChordWave can write to with plain files, or null for one that isn't on the phone or a card. */
    fun folderFromTree(context: Context, tree: Uri): File? {
        if (tree.authority != ExternalStorageAuthority) return null
        val documentId = runCatching { DocumentsContract.getTreeDocumentId(tree) }.getOrNull() ?: return null
        val volumeId = documentId.substringBefore(':')
        val relative = documentId.substringAfter(':', "").trim('/')
        val volumeRoot = if (volumeId.equals("primary", ignoreCase = true)) {
            @Suppress("DEPRECATION")
            Environment.getExternalStorageDirectory()
        } else {
            cardRoot(context, volumeId)
        } ?: return null
        return if (relative.isEmpty()) volumeRoot else File(volumeRoot, relative)
    }

    /**
     * Whether ChordWave needs all-files access (or the old storage permission)
     * to write in [place]: never for the phone or a card place, and never for
     * one of its own folders. Cheap — path checks and one system flag only, no
     * IO — so it is safe to call during composition.
     */
    fun needsFolderAccess(context: Context, place: StoragePlace): Boolean {
        if (!place.isFolder) return false
        return !isOwnFolder(context, File(place.root)) && !hasFolderAccess(context)
    }

    /** All-files access on Android 11+, the storage permission before it. */
    fun hasFolderAccess(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
        }

    private fun isOwnFolder(context: Context, folder: File): Boolean =
        (listOf(context.filesDir) + context.getExternalFilesDirs(null).filterNotNull()).any { own ->
            folder.path == own.path || folder.path.startsWith(own.path + File.separator)
        }

    /** A memory card's root by its id, from where Android keeps ChordWave's own folder on it. */
    private fun cardRoot(context: Context, volumeId: String): File? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.getSystemService<StorageManager>()?.storageVolumes
                ?.firstOrNull { it.uuid.equals(volumeId, ignoreCase = true) }
                ?.directory?.let { return it }
        }
        return context.getExternalFilesDirs(null).filterNotNull()
            .map { it.path.substringBefore("/Android/") }
            .firstOrNull { it.endsWith("/$volumeId", ignoreCase = true) }
            ?.let(::File)
    }

    /** "Music/ChordWave" on the phone, "card name/Music/ChordWave" on a card. */
    private fun readablePath(context: Context, folder: File): String {
        @Suppress("DEPRECATION")
        val phoneRoot = Environment.getExternalStorageDirectory().path
        if (folder.path.startsWith(phoneRoot)) {
            return folder.path.removePrefix(phoneRoot).trim('/').ifEmpty { folder.name }
        }
        val volume = runCatching { context.getSystemService<StorageManager>()?.getStorageVolume(folder) }.getOrNull()
        val name = runCatching { volume?.getDescription(context) }.getOrNull()
            ?: context.getString(R.string.storage_place_card)
        val cardPath = folder.path.split('/').drop(3).joinToString("/")
        return if (cardPath.isEmpty()) name else "$name/$cardPath"
    }
}
