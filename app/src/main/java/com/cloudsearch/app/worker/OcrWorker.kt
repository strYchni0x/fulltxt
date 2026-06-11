package me.fulltxt.app.worker

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import me.fulltxt.app.FulltxtApplication.Companion.CHANNEL_INDEXING
import me.fulltxt.app.MainActivity
import me.fulltxt.app.R
import me.fulltxt.app.data.cloud.CloudConnector
import me.fulltxt.app.data.cloud.dropbox.DropboxConnector
import me.fulltxt.app.data.cloud.googledrive.GoogleDriveConnector
import me.fulltxt.app.data.cloud.local.LocalFolderConnector
import me.fulltxt.app.data.cloud.magenta.MagentaCloudConnector
import me.fulltxt.app.data.cloud.nextcloud.NextcloudConnector
import me.fulltxt.app.data.cloud.onedrive.OneDriveConnector
import me.fulltxt.app.data.cloud.owncloud.OwnCloudConnector
import me.fulltxt.app.data.cloud.strato.StratoConnector
import me.fulltxt.app.data.cloud.yandex.YandexConnector
import me.fulltxt.app.data.ocr.OcrQueue
import me.fulltxt.app.data.preferences.AppPreferences
import me.fulltxt.app.data.repository.IndexRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Processes the [OcrQueue] of scanned PDFs separately from the main indexing pass. Each file's
 * OCR result is stored and the file removed from the queue immediately, so if the system stops
 * this foreground worker (e.g. the Android 14+ dataSync time limit) the next run resumes from the
 * remaining IDs rather than restarting the whole index.
 */
@HiltWorker
class OcrWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted params: WorkerParameters,
    private val indexRepository: IndexRepository,
    private val ocrQueue: OcrQueue,
    private val appPreferences: AppPreferences,
    private val googleDriveConnector: GoogleDriveConnector,
    private val oneDriveConnector: OneDriveConnector,
    private val nextcloudConnector: NextcloudConnector,
    private val ownCloudConnector: OwnCloudConnector,
    private val dropboxConnector: DropboxConnector,
    private val magentaCloudConnector: MagentaCloudConnector,
    private val stratoConnector: StratoConnector,
    private val yandexConnector: YandexConnector,
    private val localFolderConnector: LocalFolderConnector
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val NOTIFICATION_ID = 1002
        // Keep resuming across interruptions/transient errors, but give up eventually so a
        // permanently failing file cannot loop forever.
        private const val MAX_ATTEMPTS = 25
    }

    private fun connectorFor(provider: String): CloudConnector? = when (provider) {
        "GOOGLE_DRIVE"   -> googleDriveConnector
        "ONE_DRIVE"      -> oneDriveConnector
        "NEXTCLOUD"      -> nextcloudConnector
        "OWNCLOUD"       -> ownCloudConnector
        "DROPBOX"        -> dropboxConnector
        "MAGENTA_CLOUD"  -> magentaCloudConnector
        "STRATO_HIDRIVE" -> stratoConnector
        "YANDEX_DISK"    -> yandexConnector
        "LOCAL"          -> localFolderConnector
        else             -> null
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = buildForegroundInfo(0, 0)

    override suspend fun doWork(): Result {
        // Respect a mid-run toggle: if OCR was turned off, drop the backlog.
        if (!appPreferences.ocrEnabled) {
            ocrQueue.clear()
            return Result.success()
        }

        val pending = ocrQueue.snapshot()
        if (pending.isEmpty()) return Result.success()

        setForeground(buildForegroundInfo(0, pending.size))

        return try {
            var current = 0
            var succeeded = 0
            for (fileId in pending) {
                runCatching { indexRepository.ocrPendingFile(fileId, ::connectorFor) }
                    .onSuccess { if (it) succeeded++ }
                current++
                if (current % 3 == 0 || current == pending.size) {
                    setForeground(buildForegroundInfo(current, pending.size))
                }
            }
            // Files added while this run was in flight, or left behind by the system stopping us,
            // are picked up by a follow-up run as long as we keep making progress.
            if (ocrQueue.size() > 0 && succeeded > 0) Result.retry() else Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
        }
    }

    private fun buildForegroundInfo(current: Int, total: Int): ForegroundInfo {
        val notification = buildNotification(current, total)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(current: Int, total: Int): Notification {
        val openIntent = PendingIntent.getActivity(
            appContext, 0,
            Intent(appContext, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val contentText = if (total == 0) "Wird vorbereitet…" else "$current / $total gescannte PDFs"

        return NotificationCompat.Builder(appContext, CHANNEL_INDEXING)
            .setContentTitle("FullTXT: Texterkennung…")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(total, current, total == 0)
            .build()
    }
}
