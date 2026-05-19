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
class DeltaSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val indexRepository: IndexRepository,
    private val googleDriveConnector: GoogleDriveConnector,
    private val oneDriveConnector: OneDriveConnector
) : CoroutineWorker(appContext, params) {

    companion object {
        const val KEY_ACCOUNT_ID = "account_id"
        const val KEY_PROVIDER = "provider"
        const val KEY_CHANGE_TOKEN = "change_token"
    }

    override suspend fun doWork(): Result {
        val accountId = inputData.getString(KEY_ACCOUNT_ID) ?: return Result.failure()
        val provider = inputData.getString(KEY_PROVIDER) ?: return Result.failure()
        val changeToken = inputData.getString(KEY_CHANGE_TOKEN)

        return try {
            val connector = when (provider) {
                "GOOGLE_DRIVE" -> googleDriveConnector
                "ONE_DRIVE" -> oneDriveConnector
                else -> return Result.failure()
            }
            val (changedFiles, newToken) = connector.getChanges(accountId, changeToken)
            changedFiles.forEach { indexRepository.indexFile(it, connector) }
            Result.success(workDataOf(KEY_CHANGE_TOKEN to newToken))
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}
