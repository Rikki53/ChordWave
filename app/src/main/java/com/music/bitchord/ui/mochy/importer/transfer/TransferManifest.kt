// Ported from Mochy Music (GPL-3.0): importer/transfer/TransferArchive.kt
// (TransferManifest data class only — the archive reader/writer and the
// TransferExporter/TransferImporter engine are NOT ported; TransferViewModel
// is a stub, see NEEDS.md §(b) B4. The "mochy-transfer" format string is kept
// verbatim: it identifies Mochy export zips on disk, it is not user-visible.)

package com.music.bitchord.ui.mochy.importer.transfer

import org.json.JSONObject

/** The format string a transfer zip's manifest carries. */
private const val TransferFormat = "mochy-transfer"

/** what the manifest says about a zip, enough to check it and to tell the user what's inside */
data class TransferManifest(
    val formatVersion: Int,
    val createdAt: Long,
    val appId: String,
    val appVersion: String,
    val dataDir: String,
    val databaseVersion: Int,
    val songs: Int,
    val playlists: Int,
    val plays: Int,
    val localSongs: Int,
    val bytes: Long,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("format", TransferFormat)
        .put("version", formatVersion)
        .put("createdAt", createdAt)
        .put("appId", appId)
        .put("appVersion", appVersion)
        .put("dataDir", dataDir)
        .put("databaseVersion", databaseVersion)
        .put("songs", songs)
        .put("playlists", playlists)
        .put("plays", plays)
        .put("localSongs", localSongs)
        .put("bytes", bytes)

    companion object {
        /** null when the json isn't a transfer manifest at all */
        fun fromJson(json: JSONObject): TransferManifest? {
            if (json.optString("format") != TransferFormat) return null
            return TransferManifest(
                formatVersion = json.optInt("version"),
                createdAt = json.optLong("createdAt"),
                appId = json.optString("appId"),
                appVersion = json.optString("appVersion"),
                dataDir = json.optString("dataDir"),
                databaseVersion = json.optInt("databaseVersion"),
                songs = json.optInt("songs"),
                playlists = json.optInt("playlists"),
                plays = json.optInt("plays"),
                localSongs = json.optInt("localSongs"),
                bytes = json.optLong("bytes"),
            )
        }
    }
}
