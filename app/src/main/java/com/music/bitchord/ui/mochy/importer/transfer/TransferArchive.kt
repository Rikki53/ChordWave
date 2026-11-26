// Batch 55a: the pieces of Mochy's importer/transfer/TransferArchive.kt that
// the cloud backup engine reads. ChordWave has no Room database or Mochy
// transfer payload, so a ChordWave backup zip carries ChordWave's own
// Backup JSON (data/stats/Backup.kt) under the same manifest-first layout.

package com.music.bitchord.ui.mochy.importer.transfer

internal object TransferArchive {
    const val ManifestEntry = "manifest.json"
    const val BackupEntry = "backup.json"

    /** shared-preference files a backup carries, watched for the last-change check */
    val PrefsToCarry = mapOf(
        "settings" to "bitchord_settings",
    )

    /** folders of downloaded songs; a cloud backup never carries the audio itself */
    val SongFolders: Set<String> = emptySet()

    private val FilesLeftBehind = setOf("scrobble_queue.json")

    fun carriesFile(name: String): Boolean = name !in FilesLeftBehind
}
