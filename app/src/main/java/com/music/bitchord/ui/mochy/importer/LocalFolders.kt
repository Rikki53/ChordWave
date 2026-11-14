// LocalFolders.kt — ChordWave adapter for Mochy's importer/LocalFolders.kt
// (same shape, CW-owned storage: the picked music folders, kept for later
// re-scans, and the audio files inside them that aren't in the library yet).
//
// A folder is kept as its tree uri in SharedPreferences, with a persistable
// read grant taken at pick time; a folder whose grant is gone (revoked in
// Settings, or the app reinstalled) is skipped on re-scan rather than
// crashing the walk.

package com.music.bitchord.ui.mochy.importer

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import org.json.JSONArray
import timber.log.Timber

object LocalFolders {

    private const val PREFS_NAME = "mochy_local_folders"
    private const val KEY_TREES = "trees_json"

    private val audioExtensions = setOf(
        "mp3", "m4a", "flac", "ogg", "opus", "wav", "aac", "wma",
        "aiff", "aif", "mid", "midi", "amr", "awb", "mp4",
    )

    /** The saved music folders whose access Android still grants. */
    fun read(context: Context): List<Uri> {
        val granted = context.contentResolver.persistedUriPermissions.mapTo(HashSet()) { it.uri.toString() }
        return storedTrees(context).filter { it in granted }.map { Uri.parse(it) }
    }

    /**
     * Keeps [tree] for later re-scans. Throws (SecurityException) when Android
     * won't grant lasting access — the caller reports that and asks again.
     */
    fun add(context: Context, tree: Uri) {
        context.contentResolver.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val trees = (storedTrees(context) + tree.toString()).distinct()
        val array = JSONArray()
        trees.forEach { array.put(it) }
        prefs.edit().putString(KEY_TREES, array.toString()).apply()
    }

    /** The audio files under [tree] that aren't in [known] yet (document uris). */
    fun newAudioFiles(context: Context, tree: Uri, known: Set<String>): List<Uri> {
        val out = mutableListOf<Uri>()
        collectAudio(context, tree, tree, known, out, depth = 0)
        return out
    }

    private fun collectAudio(
        context: Context,
        tree: Uri,
        dir: Uri,
        known: Set<String>,
        out: MutableList<Uri>,
        depth: Int,
    ) {
        if (depth > 16) return
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            tree,
            DocumentsContract.getDocumentId(dir),
        )
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        )
        runCatching {
            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val mimeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val nameCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                while (cursor.moveToNext()) {
                    val docId = cursor.getString(idCol) ?: continue
                    val mime = cursor.getString(mimeCol).orEmpty()
                    val name = cursor.getString(nameCol).orEmpty()
                    val docUri = DocumentsContract.buildDocumentUriUsingTree(tree, docId)
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        collectAudio(context, tree, docUri, known, out, depth + 1)
                    } else if (isAudio(mime, name) && docUri.toString() !in known) {
                        out += docUri
                    }
                }
            }
        }.onFailure { Timber.w(it, "LocalFolders: couldn't list $dir") }
    }

    private fun isAudio(mime: String, name: String): Boolean =
        mime.startsWith("audio/") || name.substringAfterLast('.', "").lowercase() in audioExtensions

    /**
     * The MediaStore uri for a document uri, when the file is one MediaStore
     * knows: a file picked through the media provider ("audio:12345"), or a
     * file in a picked folder ("primary:Music/song.mp3", found by its path).
     * Null for anything else (non-primary volumes, providers MediaStore never
     * indexed) — the caller then keeps the document uri itself.
     */
    fun mediaStoreUri(context: Context, docUri: Uri): Uri? {
        val docId = runCatching { DocumentsContract.getDocumentId(docUri) }.getOrNull() ?: return null
        if (docId.startsWith("audio:")) {
            val id = docId.removePrefix("audio:").toLongOrNull() ?: return null
            return ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
        }
        val path = docId.removePrefix("primary:").takeIf { docId.startsWith("primary:") } ?: return null
        val fullPath = "/storage/emulated/0/$path"
        return runCatching {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Audio.Media._ID),
                "${MediaStore.Audio.Media.DATA} = ?",
                arrayOf(fullPath),
                null,
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)),
                )
            }
        }.getOrNull()
    }

    private fun storedTrees(context: Context): List<String> {
        val raw = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_TREES, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            List(array.length()) { array.optString(it) }.filter { it.isNotBlank() }
        }.getOrDefault(emptyList())
    }
}
