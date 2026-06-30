package me.fulltxt.app.data.repository

import android.content.Context
import me.fulltxt.app.data.cloud.CloudConnector
import me.fulltxt.app.data.extractor.PdfOcr
import me.fulltxt.app.data.extractor.TextExtractor
import me.fulltxt.app.data.ocr.OcrQueue
import me.fulltxt.app.data.preferences.AppPreferences
import me.fulltxt.app.data.local.dao.FileIndexDao
import me.fulltxt.app.data.local.entity.FileContentEntity
import me.fulltxt.app.data.local.entity.FileMetadataEntity
import me.fulltxt.app.domain.model.CloudAccount
import me.fulltxt.app.domain.model.CloudFile
import me.fulltxt.app.domain.model.CloudProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IndexRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: FileIndexDao,
    private val appPreferences: AppPreferences,
    private val ocrQueue: OcrQueue
) {
    private val syncPrefs = context.getSharedPreferences("fulltxt_sync", Context.MODE_PRIVATE)

    // --- Sync state ---

    fun getChangeToken(accountId: String): String? =
        syncPrefs.getString("ct_$accountId", null)

    fun saveChangeToken(accountId: String, token: String) =
        syncPrefs.edit().putString("ct_$accountId", token).apply()

    fun isFullyIndexed(accountId: String): Boolean =
        syncPrefs.getBoolean("fi_$accountId", false)

    fun markFullyIndexed(accountId: String) =
        syncPrefs.edit().putBoolean("fi_$accountId", true).apply()

    /**
     * Count of files currently skipped (too large) for an account, for the account card.
     * Backed by the [skipped][FileMetadataEntity.skipped] flag, so it stays correct across
     * cursor-delta syncs and re-evaluations of the limit.
     */
    suspend fun getSkippedCount(accountId: String): Int = dao.getSkippedCount(accountId)

    fun clearSyncState(accountId: String) =
        syncPrefs.edit().remove("ct_$accountId").remove("fi_$accountId").apply()

    // --- Account persistence ---

    fun getConnectedAccounts(): List<CloudAccount> {
        val ids = syncPrefs.getStringSet("acct_ids", emptySet()) ?: emptySet()
        return ids.mapNotNull { id -> syncPrefs.getString("acct_$id", null)?.parseAccount() }
    }

    fun saveAccount(account: CloudAccount) {
        val ids = (syncPrefs.getStringSet("acct_ids", emptySet()) ?: emptySet()).toMutableSet()
        ids.add(account.accountId)
        syncPrefs.edit()
            .putStringSet("acct_ids", ids)
            .putString("acct_${account.accountId}", "${account.provider.name}|${account.accountId}|${account.email}|${account.displayName}")
            .apply()
    }

    fun removeAccount(accountId: String) {
        val ids = (syncPrefs.getStringSet("acct_ids", emptySet()) ?: emptySet()).toMutableSet()
        ids.remove(accountId)
        syncPrefs.edit()
            .putStringSet("acct_ids", ids)
            .remove("acct_$accountId")
            .apply()
        clearSyncState(accountId)
    }

    suspend fun deleteAllByAccount(accountId: String) = dao.deleteAllByAccount(accountId)

    private fun String.parseAccount(): CloudAccount? {
        val p = split("|", limit = 4)
        if (p.size != 4) return null
        return CloudAccount(
            accountId = p[1],
            provider = runCatching { CloudProvider.valueOf(p[0]) }.getOrNull() ?: return null,
            email = p[2],
            displayName = p[3]
        )
    }

    // --- Index operations ---

    suspend fun getIndexedFileCount(accountId: String): Int =
        dao.getIndexedFileCount(accountId)

    suspend fun getFileIdsByAccount(provider: String, accountId: String): List<String> =
        dao.getAllByAccount(provider, accountId).map { it.fileId }

    /**
     * Downloads, extracts and stores a single file.
     * Returns false if the file was skipped because its changeToken matches the stored one.
     */
    suspend fun indexFile(file: CloudFile, connector: CloudConnector): Boolean {
        val stored = dao.getMetadata(file.fileId)
        // Skip re-download only if unchanged AND not currently skipped. A skipped row must still be
        // (re-)indexed here when it now fits the limit, even though its changeToken is unchanged.
        if (stored != null && file.changeToken != null &&
            stored.changeToken == file.changeToken && !stored.skipped) {
            return false
        }

        // Use createTempFile so the OS generates a safe, unique filename.
        // Avoids path-traversal: file IDs from some providers (e.g. Dropbox) contain '/'
        // which would make File(cacheDir, fileId) resolve to an absolute path outside cacheDir.
        val tempFile = File.createTempFile("idx_", ".tmp", context.cacheDir)
        try {
            tempFile.writeBytes(connector.downloadFile(file.fileId, file.accountId))
            val text = TextExtractor.extract(tempFile, file.mimeType)
            dao.upsertFile(
                metadata = file.toMetadataEntity(),
                content = FileContentEntity(
                    fileId = file.fileId,
                    fileName = file.fileName,
                    content = text
                )
            )
            // Scanned PDFs (no text layer) are queued for a separate, resumable OCR pass instead
            // of being OCR'd inline here — that keeps the main indexing pass fast.
            if (appPreferences.ocrEnabled && TextExtractor.pdfNeedsOcr(file.mimeType, text)) {
                ocrQueue.add(file.fileId)
            } else {
                ocrQueue.remove(file.fileId)
            }
        } finally {
            tempFile.delete()
        }
        return true
    }

    /**
     * Downloads a single queued file, runs OCR on it and replaces its stored content.
     * [connectorFor] resolves a [CloudConnector] for the file's provider (the worker owns the
     * connector instances). Returns false if the file is gone or its provider is unavailable;
     * the file is removed from the OCR queue on success so the pass can resume after interruption.
     */
    suspend fun ocrPendingFile(fileId: String, connectorFor: (String) -> CloudConnector?): Boolean {
        val meta = dao.getMetadata(fileId)
        if (meta == null) {
            ocrQueue.remove(fileId)
            return false
        }
        val connector = connectorFor(meta.cloudProvider) ?: return false

        val tempFile = File.createTempFile("ocr_", ".tmp", context.cacheDir)
        try {
            tempFile.writeBytes(connector.downloadFile(meta.fileId, meta.accountId))
            val text = PdfOcr.extract(tempFile)
            dao.upsertFile(
                metadata = meta,
                content = FileContentEntity(
                    fileId = meta.fileId,
                    fileName = meta.fileName,
                    content = text
                )
            )
        } finally {
            tempFile.delete()
        }
        ocrQueue.remove(fileId)
        return true
    }

    suspend fun removeFile(fileId: String) = dao.deleteFile(fileId)

    /** Records a too-large file as known-but-skipped (metadata only, no content). */
    suspend fun markSkipped(file: CloudFile) = dao.markSkipped(file.toMetadataEntity())

    /**
     * Re-evaluates already-known files against the current size limit, without re-listing the
     * cloud — so a changed limit takes effect on the next sync even for cursor-delta providers
     * (Dropbox/OneDrive):
     *  - skipped files that now fit the limit are downloaded and indexed,
     *  - indexed files that now exceed it have their content dropped and become skipped.
     */
    suspend fun reEvaluateSkippedAgainstLimit(
        accountId: String,
        connector: CloudConnector,
        maxFileBytes: Long
    ) {
        dao.getSkippedAtOrBelow(accountId, maxFileBytes).forEach { meta ->
            runCatching { indexFile(meta.toCloudFile(), connector) }
        }
        dao.getIndexedAbove(accountId, maxFileBytes).forEach { meta ->
            runCatching { dao.markSkipped(meta) }
        }
    }

    private fun CloudFile.toMetadataEntity(skipped: Boolean = false) = FileMetadataEntity(
        fileId = fileId,
        fileName = fileName,
        cloudPath = cloudPath,
        cloudProvider = cloudProvider.name,
        accountId = accountId,
        fileSizeBytes = fileSizeBytes,
        createdAt = createdAt,
        modifiedAt = modifiedAt,
        mimeType = mimeType,
        changeToken = changeToken,
        checksum = null,
        indexedAt = System.currentTimeMillis(),
        webUrl = webUrl,
        skipped = skipped
    )

    private fun FileMetadataEntity.toCloudFile() = CloudFile(
        fileId = fileId,
        fileName = fileName,
        cloudPath = cloudPath,
        cloudProvider = CloudProvider.valueOf(cloudProvider),
        accountId = accountId,
        fileSizeBytes = fileSizeBytes,
        createdAt = createdAt,
        modifiedAt = modifiedAt,
        mimeType = mimeType,
        changeToken = changeToken,
        webUrl = webUrl
    )
}
