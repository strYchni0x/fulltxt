package me.fulltxt.app.data.cloud

import me.fulltxt.app.domain.model.CloudFile

interface CloudConnector {
    suspend fun listFiles(accountId: String): List<CloudFile>
    suspend fun downloadFile(fileId: String, accountId: String): ByteArray
    /**
     * Gibt Änderungen seit [changeToken] zurück.
     * Beim ersten Lauf null übergeben, um eine vollständige Dateiliste + das initiale Token zu erhalten.
     */
    suspend fun getChanges(accountId: String, changeToken: String?): SyncChanges
    fun isAuthenticated(accountId: String): Boolean
    suspend fun authenticate(accountId: String)
    fun signOut(accountId: String)
}
