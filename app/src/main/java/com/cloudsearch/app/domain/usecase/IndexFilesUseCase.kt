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
     * Schedules (or replaces) a periodic delta sync that runs once every 24 hours.
     * The job respects the global metered-network preference at the time it is scheduled;
     * toggling the metered setting afterwards requires calling this function again.
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

    /** Cancels the periodic daily delta sync for the given account. */
    fun cancelDailyDelta(accountId: String) {
        WorkManager.getInstance(context).cancelUniqueWork(dailyTag(accountId))
    }
}
