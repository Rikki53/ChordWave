// A folder the user picked in the system picker, filled with plain
// DocumentsContract calls; each folder is listed once and remembered.
// Adapted from Mochy Music's utils/DocumentTree.kt (GPL-3.0). Framework APIs
// only — no androidx.documentfile dependency needed. Named ExportDocumentTree
// so a later verbatim port of Mochy's utils/DocumentTree can never collide
// with it.

package com.music.bitchord.ui.mochy.viewmodels

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import java.io.IOException
import java.io.OutputStream

// characters most file systems refuse in a name, plus anything invisible
private val BadNameChars = Regex("[\\\\/:*?\"<>|\\p{Cntrl}]")

internal class ExportDocumentTree(private val resolver: ContentResolver, private val tree: Uri) {
    val rootId: String = DocumentsContract.getTreeDocumentId(tree)
    private val listed = HashMap<String, MutableMap<String, String>>()

    /** The folder called [name] inside [parentId], made if it isn't there yet. */
    fun folder(parentId: String, name: String): String {
        val children = childrenOf(parentId)
        children[name]?.let { return it }
        val uri = DocumentsContract.createDocument(
            resolver,
            documentUri(parentId),
            DocumentsContract.Document.MIME_TYPE_DIR,
            name,
        ) ?: throw IOException("couldn't make the folder $name")
        val id = DocumentsContract.getDocumentId(uri)
        children[name] = id
        return id
    }

    /** Writes a file called [name] inside [parentId], replacing one that's already there so an export run twice stays tidy. */
    fun write(parentId: String, name: String, block: (OutputStream) -> Unit) {
        val children = childrenOf(parentId)
        children.remove(name)?.let { old ->
            runCatching { DocumentsContract.deleteDocument(resolver, documentUri(old)) }
        }
        // a generic type, so the provider never tacks a second extension onto the name
        val uri = DocumentsContract.createDocument(
            resolver,
            documentUri(parentId),
            "application/octet-stream",
            name,
        ) ?: throw IOException("couldn't make the file $name")
        try {
            val stream = resolver.openOutputStream(uri, "w") ?: throw IOException("couldn't open $name")
            stream.buffered(256 * 1024).use(block)
        } catch (error: Throwable) {
            // a half-written song is worse than none
            runCatching { DocumentsContract.deleteDocument(resolver, uri) }
            throw error
        }
        children[name] = DocumentsContract.getDocumentId(uri)
    }

    private fun documentUri(id: String): Uri = DocumentsContract.buildDocumentUriUsingTree(tree, id)

    private fun childrenOf(parentId: String): MutableMap<String, String> = listed.getOrPut(parentId) {
        val out = HashMap<String, String>()
        val query = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
        val columns = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        )
        runCatching {
            resolver.query(query, columns, null, null, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getString(0) ?: continue
                    val name = cursor.getString(1) ?: continue
                    out[name] = id
                }
            }
        }
        out
    }

    companion object {
        /** A name any drive takes: no slashes or colons, no trailing dots, not too long. */
        fun safeName(raw: String, fallback: String): String {
            val cleaned = raw.replace(BadNameChars, " ").replace(Regex("\\s+"), " ").trim().trimEnd('.', ' ')
            return cleaned.take(120).trim().ifEmpty { fallback }
        }
    }
}
