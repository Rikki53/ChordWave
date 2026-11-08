package com.music.bitchord.karaoke.ai

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.music.bitchord.R
import com.music.bitchord.data.model.Song
import com.music.bitchord.download.Downloads
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Foreground service that builds one song's AI karaoke stems, following
 * the DownloadService pattern: promote first, own no durable state (the
 * job's progress lives in [KaraokeAi.job] where the UI reads it), reflect
 * progress into a notification, and stop when there is nothing to do.
 *
 * Pipeline: ensure the model (download + SHA-256 verify if missing) ->
 * obtain the song's audio file through the app's own download engine
 * (an already-downloaded or local file is used as-is) -> decode to
 * stereo float 44.1 kHz -> [MdxSeparator.separateFull] -> write both
 * stems via `.part` files renamed into place only when complete, so a
 * cancellation or a crash never leaves a half stem behind.
 */
class KaraokeAiService : Service() {

    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private var work: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        promote()
        if (intent?.action == ACTION_CANCEL) {
            KaraokeAi.cancelRequested = true
            work?.cancel()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_START && work?.isActive != true) {
            KaraokeAi.cancelRequested = false
            val song = Song(
                videoId = intent.getStringExtra(EXTRA_ID).orEmpty(),
                title = intent.getStringExtra(EXTRA_TITLE).orEmpty(),
                artist = intent.getStringExtra(EXTRA_ARTIST).orEmpty(),
                thumbnailUrl = null,
                localUri = intent.getStringExtra(EXTRA_LOCAL_URI),
                localPath = intent.getStringExtra(EXTRA_LOCAL_PATH),
            )
            if (song.videoId.isNotEmpty()) {
                work = scope.launch { runJob(song) }
            } else {
                shutdown()
            }
        }
        return START_NOT_STICKY
    }

    private suspend fun runJob(song: Song) {
        val ctx = this@KaraokeAiService
        val safe = KaraokeAi.safeId(song.videoId)
        val instPart = File(KaraokeAi.stemsDir(ctx), "${safe}_inst.wav.part")
        val vocalPart = File(KaraokeAi.stemsDir(ctx), "${safe}_vocal.wav.part")
        fun state(phase: String, fraction: Float, error: String? = null, done: Boolean = false, cancelled: Boolean = false) {
            KaraokeAi.job.value = KaraokeAi.JobState(
                songId = song.videoId, title = song.title, phase = phase,
                fraction = fraction, error = error, done = done, cancelled = cancelled,
            )
            postNotification()
        }
        try {
            // 1) Model.
            if (!KaraokeAi.modelReady(ctx)) {
                state("Mengunduh model AI…", 0f)
                withContext(Dispatchers.IO) {
                    AudioPcm.downloadModel(
                        KaraokeAi.modelFile(ctx),
                        progress = { f -> state("Mengunduh model AI…", f) },
                        cancelled = { KaraokeAi.cancelRequested },
                    )
                }
                if (work?.isActive != true || KaraokeAi.cancelRequested) {
                    throw CancellationException("cancelled")
                }
            }
            // 2) Source audio.
            state("Menyiapkan lagu…", -1f)
            val sourceUri = resolveSource(song)
                ?: throw IllegalStateException(
                    "Lagu ini tidak bisa diunduh untuk diproses. Putar/unduh dulu dari aplikasi, lalu coba lagi.",
                )
            // 3) Decode.
            state("Membaca audio…", -1f)
            val mix = withContext(Dispatchers.IO) {
                AudioPcm.decodeStereo44100(ctx, sourceUri)
            }
            if (work?.isActive != true || KaraokeAi.cancelRequested) {
                throw CancellationException("cancelled")
            }
            // 4) Separate + write.
            state("Memisahkan vokal (AI)…", 0f)
            val jobRef = work
            withContext(Dispatchers.Default) {
                val separator = MdxSeparator(KaraokeAi.modelFile(ctx))
                try {
                    val (vocal, instrumental) = separator.separateFull(
                        mix,
                        progress = { f -> state("Memisahkan vokal (AI)…", f) },
                        cancelled = { KaraokeAi.cancelRequested || jobRef?.isActive != true },
                    )
                    if (KaraokeAi.cancelRequested || jobRef?.isActive != true) {
                        throw CancellationException("cancelled")
                    }
                    AudioPcm.writeWav16(vocalPart, vocal)
                    AudioPcm.writeWav16(instPart, instrumental)
                } finally {
                    separator.close()
                }
            }
            check(vocalPart.renameTo(KaraokeAi.vocalFile(ctx, song.videoId)) &&
                instPart.renameTo(KaraokeAi.instFile(ctx, song.videoId))) {
                "Tidak bisa menyimpan berkas stem"
            }
            state("Selesai", 1f, done = true)
        } catch (e: CancellationException) {
            instPart.delete(); vocalPart.delete()
            state("Dibatalkan", 0f, cancelled = true)
        } catch (e: InterruptedException) {
            instPart.delete(); vocalPart.delete()
            state("Dibatalkan", 0f, cancelled = true)
        } catch (t: Throwable) {
            instPart.delete(); vocalPart.delete()
            state("Gagal", 0f, error = t.message ?: t.javaClass.simpleName)
        } finally {
            shutdown()
        }
    }

    /**
     * The song's audio as a readable uri: its own local file first, then
     * the download record, then — for a catalogue song — the app's
     * download engine itself, awaited here ([Downloads.downloadNow]).
     */
    private suspend fun resolveSource(song: Song): android.net.Uri? {
        val ctx = this@KaraokeAiService
        song.localPath?.let { path ->
            val f = File(path)
            if (f.isFile) return android.net.Uri.fromFile(f)
        }
        song.localUri?.let { return it.toUri() }
        Downloads.savedUri(ctx, song.videoId)?.let { return it }
        if (song.videoId.startsWith("content://") || song.videoId.startsWith("file://")) {
            return song.videoId.toUri()
        }
        // Catalogue song not on disk yet: use the existing download engine.
        runCatching { Downloads.downloadNow(ctx, listOf(song), null) }
        return Downloads.savedUri(ctx, song.videoId)
    }

    private fun shutdown() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    // ---- Notification -------------------------------------------------------

    private fun promote() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun postNotification() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        runCatching { manager.notify(NOTIFICATION_ID, buildNotification()) }
    }

    private fun buildNotification(): Notification {
        val state = KaraokeAi.job.value
        val percent = state?.fraction?.takeIf { it >= 0f }?.times(100)?.toInt()
        val cancel = PendingIntent.getService(
            this, 0,
            Intent(this, KaraokeAiService::class.java).setAction(ACTION_CANCEL),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_logo)
            .setContentTitle(state?.title?.takeIf { it.isNotBlank() } ?: "Karaoke AI")
            .setContentText(
                buildString {
                    append(state?.phase ?: "Menyiapkan…")
                    if (percent != null) append(" $percent%")
                },
            )
            .setProgress(100, percent ?: 0, percent == null)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(0, "Batal", cancel)
            .build()
    }

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Karaoke AI", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Pembuatan versi karaoke AI"
                setShowBadge(false)
            },
        )
    }

    companion object {
        private const val CHANNEL_ID = "karaoke_ai"
        private const val NOTIFICATION_ID = 0x8176
        const val ACTION_START = "com.music.bitchord.karaokeai.START"
        const val ACTION_CANCEL = "com.music.bitchord.karaokeai.CANCEL"
        private const val EXTRA_ID = "id"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_ARTIST = "artist"
        private const val EXTRA_LOCAL_URI = "localUri"
        private const val EXTRA_LOCAL_PATH = "localPath"

        fun start(context: Context, song: Song) {
            val intent = Intent(context, KaraokeAiService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_ID, song.videoId)
                putExtra(EXTRA_TITLE, song.title)
                putExtra(EXTRA_ARTIST, song.artist)
                putExtra(EXTRA_LOCAL_URI, song.localUri)
                putExtra(EXTRA_LOCAL_PATH, song.localPath)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun cancel(context: Context) {
            runCatching {
                context.startService(
                    Intent(context, KaraokeAiService::class.java).setAction(ACTION_CANCEL),
                )
            }
        }
    }
}
