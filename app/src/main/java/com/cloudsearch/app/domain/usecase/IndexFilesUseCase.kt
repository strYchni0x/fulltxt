package me.fulltxt.app.domain.usecase

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import me.fulltxt.app.domain.model.CloudProvider
import me.fulltxt.app.worker.IndexingWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class IndexFilesUseCase @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun scheduleInitialIndexing(accountId: String, provider: CloudProvider) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.UNMETERED)
            .setRequiresCharging(true)
            .build()

        val request = OneTimeWorkRequestBuilder<IndexingWorker>()
            .setConstraints(constraints)
            .setInputData(workDataOf(
                IndexingWorker.KEY_ACCOUNT_ID to accountId,
                IndexingWorker.KEY_PROVIDER to provider.name
            ))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context).enqueue(request)
    }
}
