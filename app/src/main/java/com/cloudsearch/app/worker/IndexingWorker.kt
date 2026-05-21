package me.fulltxt.app.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import me.fulltxt.app.data.cloud.googledrive.GoogleDriveConnector
import me.fulltxt.app.data.cloud.onedrive.OneDriveConnector
import me.fulltxt.app.data.repository.IndexRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class IndexingWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val indexRepository: IndexRepository,
    private val googleDriveConnector: GoogleDriveConnector,
    private val oneDriveConnector: OneDriveConnector
) : CoroutineWorker(appContext, params) {

    companion object {
        const val KEY_ACCOUNT_ID = "account_id"
        const val KEY_PROVIDER = "provider"
        const val PROGRESS_CURRENT = "progress_current"
        const val PROGRESS_TOTAL = "progress_total"
        const val PROGRESS_ERRORS = "progress_errors"
    }

    override suspend fun doWork(): Result {
        val accountId = inputData.getString(KEY_ACCOUNT_ID) ?: return Result.failure()
        val provider = inputData.getString(KEY_PROVIDER) ?: return Result.failure()

        val connector = when (provider) {
            "GOOGLE_DRIVE" -> googleDriveConnector
            "ONE_DRIVE" -> oneDriveConnector
            else -> return Result.failure()
        }

        return try {
            val files = connector.listFiles(accountId)
            val total = files.size
            var current = 0
            var errors = 0

            setProgressAsync(workDataOf(
                PROGRESS_CURRENT to 0,
                PROGRESS_TOTAL to total,
                PROGRESS_ERRORS to 0
            ))

            for (file in files) {
                runCatching { indexRepository.indexFile(file, connector) }
                    .onFailure { errors++ }
                current++
                setProgressAsync(workDataOf(
                    PROGRESS_CURRENT to current,
                    PROGRESS_TOTAL to total,
                    PROGRESS_ERRORS to errors
                ))
            }

            indexRepository.markFullyIndexed(accountId)

            if (errors > 0 && current == errors) Result.failure() else Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}
