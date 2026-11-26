// Batch 55a: ChordWave's stand-in for Mochy's TransferExporter, with the
// same calls the cloud backup engine makes. Mochy's exporter packs its Room
// database; ChordWave has none, so the zip carries the manifest plus
// ChordWave's own Backup JSON (preferences + listening history).

package com.music.bitchord.ui.mochy.importer.transfer

import android.content.Context
import com.music.bitchord.BuildConfig
import com.music.bitchord.data.stats.Backup
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

class TransferException(message: String, cause: Throwable? = null) : Exception(message, cause)

@Singleton
class TransferExporter @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val running = Mutex()

    /** packs a backup zip at [target] (no songs), onProgress runs 0 to 1 */
    suspend fun exportBackup(target: File, onProgress: (Float) -> Unit): Result<TransferManifest> =
        withContext(Dispatchers.IO) {
            runCatching {
                running.withLock {
                    onProgress(0f)
                    val manifest = TransferManifest(
                        formatVersion = 1,
                        createdAt = System.currentTimeMillis(),
                        appId = BuildConfig.APPLICATION_ID,
                        appVersion = BuildConfig.VERSION_NAME,
                        dataDir = "",
                        databaseVersion = 0,
                        songs = 0,
                        playlists = 0,
                        plays = 0,
                        localSongs = 0,
                        bytes = 0,
                    )
                    target.outputStream().use { raw ->
                        ZipOutputStream(raw).use { zip ->
                            zip.putNextEntry(ZipEntry(TransferArchive.ManifestEntry))
                            zip.write(manifest.toJson().toString().toByteArray(Charsets.UTF_8))
                            zip.closeEntry()
                            onProgress(0.2f)
                            zip.putNextEntry(ZipEntry(TransferArchive.BackupEntry))
                            Backup.writeTo(zip, pretty = false)
                            zip.closeEntry()
                        }
                    }
                    onProgress(1f)
                    manifest.copy(bytes = target.length())
                }
            }
        }
}
