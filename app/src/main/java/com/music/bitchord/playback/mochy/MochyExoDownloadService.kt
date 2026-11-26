// MochyExoDownloadService — the Mochy engine's download service
// (two-engine Tahap 5), ported from Mochy's playback/ExoDownloadService.kt.
//
// The foreground face of MochyDownloadUtil's DownloadManager: progress
// notification with a cancel action, a failed notification per failed
// song, and the platform scheduler so held downloads resume when their
// requirements are met. Branding is ChordWave's (the download drawable,
// the shared strings); the channel and ids are Mochy's.

package com.music.bitchord.playback.mochy

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import androidx.annotation.OptIn
import androidx.media3.common.util.NotificationUtil
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.PlatformScheduler
import androidx.media3.exoplayer.scheduler.Scheduler
import com.music.bitchord.R

@OptIn(UnstableApi::class)
class MochyExoDownloadService : DownloadService(
    NOTIFICATION_ID,
    1000L,
    CHANNEL_ID,
    R.string.downloading,
    0,
) {
    override fun onCreate() {
        super.onCreate()
        // The service can be started by the scheduler before the UI
        // ever ran; the manager must exist before getDownloadManager.
        MochyDownloadUtil.init(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == REMOVE_ALL_PENDING_DOWNLOADS) {
            downloadManager.currentDownloads.forEach { download ->
                downloadManager.removeDownload(download.request.id)
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    // Android 15 gives a download service six hours a day; when that runs
    // out it has to stop at once or Android ends the whole app (Mochy).
    override fun onTimeout(startId: Int, fgsType: Int) {
        stopSelf()
    }

    override fun getDownloadManager(): DownloadManager =
        MochyDownloadUtil.downloadManager() ?: error("Mochy download manager not initialised")

    override fun getScheduler(): Scheduler = PlatformScheduler(this, JOB_ID)

    override fun getForegroundNotification(
        downloads: MutableList<Download>,
        notMetRequirements: Int,
    ): Notification {
        val helper = MochyDownloadUtil.downloadNotificationHelper()
            ?: DownloadNotificationHelper(this, CHANNEL_ID)
        return Notification.Builder.recoverBuilder(
            this,
            helper.buildProgressNotification(
                this,
                R.drawable.download,
                null,
                if (downloads.size == 1) {
                    Util.fromUtf8Bytes(downloads[0].request.data)
                } else {
                    resources.getQuantityString(R.plurals.n_song, downloads.size, downloads.size)
                },
                downloads,
                notMetRequirements,
            ),
        ).addAction(
            Notification.Action.Builder(
                Icon.createWithResource(this, R.drawable.close),
                getString(android.R.string.cancel),
                PendingIntent.getService(
                    this,
                    0,
                    Intent(this, MochyExoDownloadService::class.java).setAction(REMOVE_ALL_PENDING_DOWNLOADS),
                    PendingIntent.FLAG_IMMUTABLE,
                ),
            ).build(),
        ).build()
    }

    class TerminalStateNotificationHelper(
        private val context: Context,
        private val notificationHelper: DownloadNotificationHelper,
        private var nextNotificationId: Int,
    ) : DownloadManager.Listener {
        override fun onDownloadChanged(
            downloadManager: DownloadManager,
            download: Download,
            finalException: Exception?,
        ) {
            if (download.state == Download.STATE_FAILED) {
                val notification = notificationHelper.buildDownloadFailedNotification(
                    context,
                    R.drawable.warning,
                    null,
                    Util.fromUtf8Bytes(download.request.data),
                )
                NotificationUtil.setNotification(context, nextNotificationId++, notification)
            }
        }
    }

    companion object {
        const val CHANNEL_ID = "download"
        const val NOTIFICATION_ID = 1
        const val JOB_ID = 1
        const val REMOVE_ALL_PENDING_DOWNLOADS = "REMOVE_ALL_PENDING_DOWNLOADS"
    }
}
