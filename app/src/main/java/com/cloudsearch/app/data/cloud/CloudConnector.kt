package me.fulltxt.app.data.cloud

import me.fulltxt.app.domain.model.CloudFile

interface CloudConnector {
    suspend fun listFiles(accountId: String): List<CloudFile>
    suspend fun downloadFile(fileId: String, accountId: String): ByteArray
    /**
     * Returns changes since [changeToken].
     * Pass null for the first run to get a full file list + the initial token.
     */
    suspend fun getChanges(accountId: String, changeToken: String?): SyncChanges
    fun isAuthenticated(accountId: String): Boolean
    suspend fun authenticate(accountId: String)
    fun signOut(accountId: String)
}
