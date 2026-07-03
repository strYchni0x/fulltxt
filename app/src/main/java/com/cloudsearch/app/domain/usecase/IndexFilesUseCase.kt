package me.fulltxt.app.domain.usecase

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import me.fulltxt.app.data.preferences.AppPreferences
import me.fulltxt.app.domain.model.CloudProvider
import me.fulltxt.app.worker.IndexingWorker
import me.fulltxt.app.worker.OcrWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class IndexFilesUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appPreferences: AppPreferences
) {
    companion object {
        fun workTag(accountId: String)  = "indexing_$accountId"
        fun dailyTag(accountId: String) = "daily_delta_$accountId"
        const val OCR_WORK = "ocr_processing"
    }

    /**
     * Plant den fortsetzbaren OCR-Durchlauf, der die OcrQueue abarbeitet. Unique + KEEP, damit immer
     * nur ein OCR-Worker gleichzeitig läuft; er plant sich selbst neu, solange Dateien verbleiben.
     * Benötigt Netzwerk, da eingereihte Dateien zum Rendern erneut heruntergeladen werden.
     */
    fun scheduleOcr() {
        val networkType = if (appPreferences.allowMeteredIndexing) NetworkType.CONNECTED
                          else NetworkType.UNMETERED
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(networkType)
            .build()

        val request = OneTimeWorkRequestBuilder<OcrWorker>()
            .setConstraints(constraints)
            .addTag(OCR_WORK)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(OCR_WORK, ExistingWorkPolicy.KEEP, request)
    }

    fun scheduleInitialIndexing(accountId: String, provider: CloudProvider) {
        val networkType = when {
            provider == CloudProvider.LOCAL        -> NetworkType.NOT_REQUIRED
            appPreferences.allowMeteredIndexing    -> NetworkType.CONNECTED
            else                                   -> NetworkType.UNMETERED
        }
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(networkType)
            .build()

        val request = OneTimeWorkRequestBuilder<IndexingWorker>()
            .setConstraints(constraints)
            .addTag(workTag(accountId))
            .setInputData(workDataOf(
                IndexingWorker.KEY_ACCOUNT_ID to accountId,
                IndexingWorker.KEY_PROVIDER to provider.name
            ))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(workTag(accountId), ExistingWorkPolicy.KEEP, request)
    }

    /**
     * Plant (oder ersetzt) einen periodischen Delta-Sync, der einmal alle 24 Stunden läuft.
     * Der Job berücksichtigt die globale Einstellung für getaktete Netzwerke zum Zeitpunkt der Planung;
     * ein späteres Umschalten der Einstellung erfordert einen erneuten Aufruf dieser Funktion.
     */
    fun scheduleDailyDelta(accountId: String, provider: CloudProvider) {
        val networkType = when {
            provider == CloudProvider.LOCAL     -> NetworkType.NOT_REQUIRED
            appPreferences.allowMeteredIndexing -> NetworkType.CONNECTED
            else                               -> NetworkType.UNMETERED
        }
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(networkType)
            .build()

        val request = PeriodicWorkRequestBuilder<IndexingWorker>(24, TimeUnit.HOURS)
            .setConstraints(constraints)
            .addTag(dailyTag(accountId))
            .setInputData(workDataOf(
                IndexingWorker.KEY_ACCOUNT_ID to accountId,
                IndexingWorker.KEY_PROVIDER to provider.name
            ))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            dailyTag(accountId),
            ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
            request
        )
    }

    /** Bricht den periodischen täglichen Delta-Sync für das angegebene Konto ab. */
    fun cancelDailyDelta(accountId: String) {
        WorkManager.getInstance(context).cancelUniqueWork(dailyTag(accountId))
    }
}
