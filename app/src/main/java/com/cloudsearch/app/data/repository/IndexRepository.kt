package me.fulltxt.app.data.repository

import android.content.Context
import me.fulltxt.app.data.cloud.CloudConnector
import me.fulltxt.app.data.extractor.TextExtractor
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
    private val dao: FileIndexDao
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

    /**
     * Downloads, extracts and stores a single file.
     * Returns false if the file was skipped because its changeToken matches the stored one.
     */
    suspend fun indexFile(file: CloudFile, connector: CloudConnector): Boolean {
        val stored = dao.getMetadata(file.fileId)
        if (stored != null && file.changeToken != null && stored.changeToken == file.changeToken) {
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
        } finally {
            tempFile.delete()
        }
        return true
    }

    suspend fun removeFile(fileId: String) = dao.deleteFile(fileId)

    private fun CloudFile.toMetadataEntity() = FileMetadataEntity(
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
        webUrl = webUrl
    )
}
