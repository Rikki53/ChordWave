// Ported from Mochy Music (GPL-3.0): ui/player/share/song/export/ShareTargets.kt
// ShareTargets.kt

package com.music.bitchord.ui.mochy.player.share.song.export

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.media3.datasource.DataSource
import com.music.bitchord.R
import com.music.bitchord.ui.mochy.player.share.song.ShareLayers
import com.music.bitchord.ui.mochy.player.share.song.SongShareSession
import com.music.bitchord.ui.mochy.player.share.song.refreshMusicVideo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

// what one export produced, ready to hand to another app
internal class ShareFile(val file: File, val mimeType: String)

internal class ShareRequest(
    val context: Context,
    val session: SongShareSession,
    val theme: ShareTheme,
    val quality: ShareQuality,
    val songSource: DataSource.Factory,
    val job: ShareExportJob,
)

// the whole picture, or a layer of it, as a video or an image depending on the share's mode
private suspend fun ShareRequest.export(layers: ShareLayers): ShareFile =
    if (session.isVideo) {
        ShareFile(exportVideoRetrying(layers), "video/mp4")
    } else {
        ShareFile(exportStill(context, session, theme, quality, layers), "image/png")
    }

// one more try when a video export fails: fresh music-video links first (a
// googlevideo link is tied to the address that minted it and dies quietly
// when that changes), and from 4k a step down to 1080p — a finished 1080p
// video beats none at all. A cancelled export is never retried, and when
// neither change applies the failure stands as it is.
private suspend fun ShareRequest.exportVideoRetrying(layers: ShareLayers): File {
    try {
        return exportVideo(context, session, theme, quality, layers, songSource, job)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        if (job.cancelled) throw e
        val refreshed = refreshMusicVideo(session)
        val retryQuality = if (quality == ShareQuality.UltraHd) ShareQuality.FullHd else quality
        if (!refreshed && retryQuality == quality) throw e
        Timber.w(e, "SongShare: video export failed; retrying with fresh links=$refreshed at $retryQuality")
        return exportVideo(context, session, theme, retryQuality, layers, songSource, job)
    }
}

// the whole picture as one file, ready to save or to hand to another app
internal suspend fun ShareRequest.exportFile(): ShareFile = export(ShareLayers.Everything)

// the phone's own share sheet, with the file and the song's link
internal fun openShareSheet(context: Context, session: SongShareSession, file: ShareFile) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = file.mimeType
        putExtra(Intent.EXTRA_STREAM, uriOf(context, file.file))
        putExtra(Intent.EXTRA_TEXT, caption(context, session))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(
        Intent.createChooser(send, context.getString(R.string.song_share_chooser_title)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

internal fun copySongLink(context: Context, session: SongShareSession): Boolean {
    val link = session.link ?: return false
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return false
    clipboard.setPrimaryClip(ClipData.newPlainText(session.share.title, link))
    return true
}

// what travels with the picture or video: the song's name, then an
// invitation to hear it together in ChordWave — the link opens the song in
// the app when it is installed and the song's page when it is not
private fun caption(context: Context, session: SongShareSession): String {
    val line = context.getString(R.string.song_share_caption, session.share.title, session.share.artist)
    return session.link?.let { link -> "$line\n${context.getString(R.string.song_share_caption_invite, link)}" } ?: line
}

private fun uriOf(context: Context, file: File): Uri =
    // ChordWave's FileProvider authority is "<package>.fileprovider"
    // (lowercase; see AndroidManifest.xml) — Mochy used ".FileProvider".
    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

// the shared folders only take files without a permission from android 10 on
internal suspend fun saveToGallery(context: Context, file: ShareFile): Boolean = withContext(Dispatchers.IO) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return@withContext false
    runCatching {
        val video = file.mimeType.startsWith("video")
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, file.file.name)
            put(MediaStore.MediaColumns.MIME_TYPE, file.mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, (if (video) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES) + "/ChordWave")
        }
        val collection = if (video) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val uri = context.contentResolver.insert(collection, values) ?: return@runCatching false
        context.contentResolver.openOutputStream(uri)?.use { out -> file.file.inputStream().use { it.copyTo(out) } } != null
    }.getOrDefault(false)
}
