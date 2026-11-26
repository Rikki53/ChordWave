// Batch 55a: ChordWave's stand-in for Mochy's TransferImporter, with the
// same calls the cloud backup engine makes. Mochy's importer unpacks to a
// staging folder that the next start swaps in; ChordWave's backup payload
// is preferences + history JSON, which Backup.readFrom applies live, so
// stage() applies it straight away (the page still restarts the app after,
// exactly as Mochy's does).

package com.music.bitchord.ui.mochy.importer.transfer

import android.content.Context
import android.net.Uri
import com.music.bitchord.data.stats.Backup
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransferImporter @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val running = Mutex()

    /** what's inside a zip, from its first entry only, failing with a message ready to show */
    suspend fun peek(source: Uri): Result<TransferManifest> = withContext(Dispatchers.IO) {
        runCatching { readManifest(source) }.friendly()
    }

    /** unpacks and applies source, onProgress goes from 0 to 1; the app restarts after to finish */
    suspend fun stage(source: Uri, onProgress: (Float) -> Unit): Result<TransferManifest> =
        withContext(Dispatchers.IO) {
            if (!running.tryLock()) {
                return@withContext Result.failure(TransferException("An import is already running."))
            }
            try {
                runCatching {
                    onProgress(0f)
                    val (manifest, backup) = readZip(source)
                    onProgress(0.5f)
                    Backup.readFrom(ByteArrayInputStream(backup))
                    onProgress(1f)
                    manifest
                }.friendly()
            } finally {
                running.unlock()
            }
        }

    private fun readManifest(source: Uri): TransferManifest {
        val input = context.contentResolver.openInputStream(source)
            ?: throw NotAnExport()
        input.use {
            ZipInputStream(it).use { zip ->
                val first = zip.nextEntry
                if (first?.name != TransferArchive.ManifestEntry) throw NotAnExport()
                val text = zip.readBytes().toString(Charsets.UTF_8)
                return TransferManifest.fromJson(JSONObject(text)) ?: throw NotAnExport()
            }
        }
    }

    private fun readZip(source: Uri): Pair<TransferManifest, ByteArray> {
        val input = context.contentResolver.openInputStream(source)
            ?: throw NotAnExport()
        input.use {
            ZipInputStream(it).use { zip ->
                var manifest: TransferManifest? = null
                var backup: ByteArray? = null
                while (true) {
                    val entry = zip.nextEntry ?: break
                    when (entry.name) {
                        TransferArchive.ManifestEntry ->
                            manifest = TransferManifest.fromJson(
                                JSONObject(zip.readBytes().toString(Charsets.UTF_8)),
                            )
                        TransferArchive.BackupEntry -> backup = zip.readBytes()
                        else -> zip.readBytes()
                    }
                    zip.closeEntry()
                }
                val m = manifest ?: throw NotAnExport()
                val b = backup ?: throw NotAnExport()
                return m to b
            }
        }
    }

    private fun Result<TransferManifest>.friendly(): Result<TransferManifest> = recoverCatching { error ->
        throw when (error) {
            is TransferException -> error
            is NotAnExport -> TransferException("That isn't a ChordWave export. Pick the .zip you exported from your old app.")
            else -> TransferException("Couldn't import that file. Make sure there's free space and try again.", error)
        }
    }

    private class NotAnExport : Exception()
}
