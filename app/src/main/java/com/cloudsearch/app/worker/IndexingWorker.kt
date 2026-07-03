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
import me.fulltxt.app.data.cloud.yandex.YandexConnector
import me.fulltxt.app.data.ocr.OcrQueue
import me.fulltxt.app.data.preferences.AppPreferences
import me.fulltxt.app.data.repository.IndexRepository
import me.fulltxt.app.domain.usecase.IndexFilesUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

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
    private val yandexConnector: YandexConnector,
    private val localFolderConnector: LocalFolderConnector,
    private val ocrQueue: OcrQueue,
    private val appPreferences: AppPreferences,
    private val indexFilesUseCase: IndexFilesUseCase
) : CoroutineWorker(appContext, params) {

    companion object {
        const val KEY_ACCOUNT_ID   = "account_id"
        const val KEY_PROVIDER     = "provider"
        const val PROGRESS_CURRENT = "progress_current"
        const val PROGRESS_TOTAL   = "progress_total"
        const val PROGRESS_ERRORS  = "progress_errors"
        const val PROGRESS_SKIPPED = "progress_skipped"
        /** Menschenlesbarer Fehlergrund, wird bei Result.failure() in die Ausgabedaten des Workers geschrieben. */
        const val KEY_ERROR        = "error_message"
        private const val NOTIFICATION_ID = 1001
        private const val MAX_RETRIES = 3
        private const val AUTH_MESSAGE =
            "Anmeldung fehlgeschlagen – Benutzername, Passwort und Server-URL prüfen."
    }

    override suspend fun getForegroundInfo(): ForegroundInfo =
        buildForegroundInfo(0, 0, 0, 0)

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
            "YANDEX_DISK"    -> yandexConnector
            "LOCAL"          -> localFolderConnector
            else             -> return Result.failure()
        }

        // Als Foreground-Service laufen — Android darf diesen Prozess nicht beenden.
        setForeground(buildForegroundInfo(0, 0, 0, 0))

        return try {
            // Gespeichertes Token laden; null = erster vollständiger Scan.
            val changeToken = indexRepository.getChangeToken(accountId)
            val sync = connector.getChanges(accountId, changeToken)

            val total   = sync.changed.size
            var current = 0
            var errors  = 0
            var skipped = 0
            val maxFileBytes = appPreferences.maxFileSizeMb.toLong() * 1024 * 1024

            setForeground(buildForegroundInfo(0, total, 0, 0))
            setProgressAsync(workDataOf(
                PROGRESS_CURRENT to 0,
                PROGRESS_TOTAL   to total,
                PROGRESS_ERRORS  to 0,
                PROGRESS_SKIPPED to 0
            ))

            // --- Neue / geänderte Dateien indexieren ---
            for (file in sync.changed) {
                if (file.fileSizeBytes > maxFileBytes) {
                    // Absichtlich übersprungen (zu groß) — kein Fehler. Mit seiner Größe erfasst, damit
                    // es später ohne Cloud-Neuauflistung erneut gegen das Limit bewertet werden kann.
                    runCatching { indexRepository.markSkipped(file) }
                    skipped++
                } else {
                    runCatching { indexRepository.indexFile(file, connector) }
                        .onFailure { errors++ }
                }
                current++
                if (current % 10 == 0 || current == total) {
                    setForeground(buildForegroundInfo(current, total, errors, skipped))
                    setProgressAsync(workDataOf(
                        PROGRESS_CURRENT to current,
                        PROGRESS_TOTAL   to total,
                        PROGRESS_ERRORS  to errors,
                        PROGRESS_SKIPPED to skipped
                    ))
                }
            }

            // --- Gelöschte Dateien aus dem lokalen Index entfernen ---
            for (fileId in sync.deletedIds) {
                runCatching { indexRepository.removeFile(fileId) }
            }

            // Bereits bekannte Dateien gegen das aktuelle Größenlimit neu bewerten (ohne die Cloud
            // neu zu listen). Nimmt Dateien auf, die jetzt in ein erhöhtes Limit passen, und verwirft
            // Dateien über einem gesenkten Limit — wichtig für Cursor-Delta-Anbieter, die unveränderte
            // Dateien nicht erneut besuchen.
            runCatching {
                indexRepository.reEvaluateSkippedAgainstLimit(accountId, connector, maxFileBytes)
            }

            // Das neue Change-Token für den nächsten inkrementellen Sync speichern.
            if (sync.newChangeToken.isNotEmpty()) {
                indexRepository.saveChangeToken(accountId, sync.newChangeToken)
            }

            indexRepository.markFullyIndexed(accountId)

            // Bei diesem Durchlauf gefundene gescannte PDFs werden von einem separaten, fortsetzbaren
            // Worker OCR-verarbeitet, damit diese (potenziell lange) OCR-Arbeit den Haupt-Index nicht
            // mehr blockiert oder neu startet.
            if (ocrQueue.size() > 0) indexFilesUseCase.scheduleOcr()

            if (total > 0 && current == errors) {
                Result.failure(workDataOf(KEY_ERROR to
                    "Keine Datei konnte verarbeitet werden – bitte Verbindung und Zugangsdaten prüfen."))
            } else {
                Result.success()
            }
        } catch (e: CancellationException) {
            // Das System hat den Worker gestoppt (z. B. FGS-Zeitlimit). WorkManager die Neuplanung
            // selbst überlassen — einen Stopp nicht in einen für den Benutzer sichtbaren Fehler verwandeln.
            throw e
        } catch (e: Exception) {
            // Die eigentliche Ursache sichtbar machen: Der Worker verschluckt sie sonst (nur eine
            // generische Meldung erreicht die UI beim finalen Fehler), was die OneDrive-ClassCastException
            // undurchsichtig machte.
            android.util.Log.e("IndexingWorker", "doWork failed (provider=$provider, attempt=$runAttemptCount)", e)
            // Falsche Zugangsdaten/URL werden auch bei einem erneuten Versuch nie funktionieren, daher
            // mit einer klaren Meldung schnell scheitern, statt in einer Schleife zu laufen (die stille
            // Retry-Schleife hat die App außerdem als "fehlerhaft" markieren lassen).
            if (isAuthError(e)) {
                Result.failure(workDataOf(KEY_ERROR to AUTH_MESSAGE))
            } else if (runAttemptCount < MAX_RETRIES) {
                Result.retry()
            } else {
                Result.failure(workDataOf(KEY_ERROR to transientMessage(e)))
            }
        }
    }

    /** Auth-Fehler (falscher Benutzername/Passwort, fehlende oder abgelehnte Zugangsdaten) — nicht wiederholbar. */
    private fun isAuthError(e: Throwable): Boolean {
        if (e is IllegalStateException) return true   // Connectors werfen dies bei fehlenden Zugangsdaten
        val msg = e.message ?: return false
        return msg.contains("401") || msg.contains("403") ||
            msg.contains("Authentifiz", ignoreCase = true) ||
            msg.contains("Zugangsdaten", ignoreCase = true) ||
            msg.contains("unauthorized", ignoreCase = true) ||
            msg.contains("forbidden", ignoreCase = true)
    }

    private fun transientMessage(e: Throwable): String = when (e) {
        is UnknownHostException ->
            "Server nicht erreichbar – Server-URL und Internetverbindung prüfen."
        is ConnectException, is SocketTimeoutException, is IOException ->
            "Verbindung zum Server fehlgeschlagen – bitte später erneut versuchen."
        else -> "Indexierung fehlgeschlagen: ${e.message ?: e.javaClass.simpleName}"
    }

    private fun buildForegroundInfo(current: Int, total: Int, errors: Int, skipped: Int): ForegroundInfo {
        val notification = buildNotification(current, total, errors, skipped)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(current: Int, total: Int, errors: Int, skipped: Int): Notification {
        val openIntent = PendingIntent.getActivity(
            appContext, 0,
            Intent(appContext, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val contentText = if (total == 0) {
            "Dateiliste wird abgerufen…"
        } else buildString {
            append("$current / $total Dateien")
            if (errors > 0) append(" · $errors Fehler")
            if (skipped > 0) append(" · $skipped übersprungen")
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
