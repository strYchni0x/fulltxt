package me.fulltxt.app.data.cloud

import me.fulltxt.app.domain.model.CloudFile

interface CloudConnector {
    suspend fun listFiles(accountId: String): List<CloudFile>
    suspend fun downloadFile(fileId: String, accountId: String): ByteArray
    suspend fun getChanges(accountId: String, changeToken: String?): Pair<List<CloudFile>, String>
    fun isAuthenticated(accountId: String): Boolean
    suspend fun authenticate(accountId: String)
    fun signOut(accountId: String)
}
