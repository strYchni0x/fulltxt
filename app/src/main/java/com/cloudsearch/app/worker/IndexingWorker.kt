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
import me.fulltxt.app.data.cloud.dropbox.DropboxConnector
import me.fulltxt.app.data.cloud.googledrive.GoogleDriveConnector
import me.fulltxt.app.data.cloud.local.LocalFolderConnector
import me.fulltxt.app.data.cloud.magenta.MagentaCloudConnector
import me.fulltxt.app.data.cloud.nextcloud.NextcloudConnector
import me.fulltxt.app.data.cloud.onedrive.OneDriveConnector
import me.fulltxt.app.data.cloud.owncloud.OwnCloudConnector
import me.fulltxt.app.data.cloud.strato.StratoConnector
import me.fulltxt.app.data.repository.IndexRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class IndexingWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted params: WorkerParameters,
    private val indexRepository: IndexRepository,
    private val googleDriveConnector: GoogleDriveConnector,
    private val oneDriveConnector: OneDriveConnector,
    private val nextcloudConnector: NextcloudConnector,
    private val ownCloudConnector: OwnCloudConnector,
    private val dropboxConnector: DropboxConnector,
    private val magentaCloudConnector: MagentaCloudConnector,
    private val stratoConnector: StratoConnector,
    private val localFolderConnector: LocalFolderConnector
) : CoroutineWorker(appContext, params) {

    companion object {
        const val KEY_ACCOUNT_ID   = "account_id"
        const val KEY_PROVIDER     = "provider"
        const val PROGRESS_CURRENT = "progress_current"
        const val PROGRESS_TOTAL   = "progress_total"
        const val PROGRESS_ERRORS  = "progress_errors"
        private const val NOTIFICATION_ID = 1001

        /** Files larger than this limit are skipped to avoid OOM. */
        private const val MAX_FILE_BYTES = 50L * 1024 * 1024  // 50 MB
    }

    override suspend fun getForegroundInfo(): ForegroundInfo =
        buildForegroundInfo(0, 0, 0)

    override suspend fun doWork(): Result {
        val accountId = inputData.getString(KEY_ACCOUNT_ID) ?: return Result.failure()
        val provider  = inputData.getString(KEY_PROVIDER)   ?: return Result.failure()

        val connector = when (provider) {
            "GOOGLE_DRIVE"   -> googleDriveConnector
            "ONE_DRIVE"      -> oneDriveConnector
            "NEXTCLOUD"      -> nextcloudConnector
            "OWNCLOUD"       -> ownCloudConnector
            "DROPBOX"        -> dropboxConnector
            "MAGENTA_CLOUD"  -> magentaCloudConnector
            "STRATO_HIDRIVE" -> stratoConnector
            "LOCAL"          -> localFolderConnector
            else             -> return Result.failure()
        }

        // Run as foreground service — Android must not kill this process.
        setForeground(buildForegroundInfo(0, 0, 0))

        return try {
            // Load saved token; null = first full scan.
            val changeToken = indexRepository.getChangeToken(accountId)
            val sync = connector.getChanges(accountId, changeToken)

            val total   = sync.changed.size
            var current = 0
            var errors  = 0

            setForeground(buildForegroundInfo(0, total, 0))
            setProgressAsync(workDataOf(
                PROGRESS_CURRENT to 0,
                PROGRESS_TOTAL   to total,
                PROGRESS_ERRORS  to 0
            ))

            // --- Index new / modified files ---
            for (file in sync.changed) {
                if (file.fileSizeBytes > MAX_FILE_BYTES) {
                    errors++
                } else {
                    runCatching { indexRepository.indexFile(file, connector) }
                        .onFailure { errors++ }
                }
                current++
                if (current % 10 == 0 || current == total) {
                    setForeground(buildForegroundInfo(current, total, errors))
                    setProgressAsync(workDataOf(
                        PROGRESS_CURRENT to current,
                        PROGRESS_TOTAL   to total,
                        PROGRESS_ERRORS  to errors
                    ))
                }
            }

            // --- Remove deleted files from local index ---
            for (fileId in sync.deletedIds) {
                runCatching { indexRepository.removeFile(fileId) }
            }

            // Persist the new change token for the next incremental sync.
            if (sync.newChangeToken.isNotEmpty()) {
                indexRepository.saveChangeToken(accountId, sync.newChangeToken)
            }

            indexRepository.markFullyIndexed(accountId)

            if (errors > 0 && current == errors) Result.failure() else Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    private fun buildForegroundInfo(current: Int, total: Int, errors: Int): ForegroundInfo {
        val notification = buildNotification(current, total, errors)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(current: Int, total: Int, errors: Int): Notification {
        val openIntent = PendingIntent.getActivity(
            appContext, 0,
            Intent(appContext, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val contentText = when {
            total == 0  -> "Dateiliste wird abgerufen…"
            errors == 0 -> "$current / $total Dateien"
            else        -> "$current / $total Dateien · $errors Fehler"
        }

        return NotificationCompat.Builder(appContext, CHANNEL_INDEXING)
            .setContentTitle("FullTXT indexiert…")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(total, current, total == 0)
            .build()
    }
}
