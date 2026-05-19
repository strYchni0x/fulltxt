package me.fulltxt.app.data.cloud.onedrive

import me.fulltxt.app.data.cloud.CloudConnector
import me.fulltxt.app.domain.model.CloudFile
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OneDriveConnector @Inject constructor(
    private val authManager: MsalAuthManager,
    private val httpClient: OkHttpClient
) : CloudConnector {

    companion object {
        private const val GRAPH_BASE_URL = "https://graph.microsoft.com/v1.0"
        private val SUPPORTED_EXTENSIONS = setOf("txt", "md", "csv", "log", "pdf", "docx", "xlsx", "pptx")
    }

    override suspend fun listFiles(accountId: String): List<CloudFile> {
        TODO("Paginated listing via /me/drive/root/search or /me/drive/items with children, filter by extension")
    }

    override suspend fun downloadFile(fileId: String, accountId: String): ByteArray {
        TODO("Download via /me/drive/items/{fileId}/content with Bearer token")
    }

    override suspend fun getChanges(accountId: String, changeToken: String?): Pair<List<CloudFile>, String> {
        TODO("Delta sync via /me/drive/root/delta; fetch initial delta link if changeToken is null")
    }

    override fun isAuthenticated(accountId: String): Boolean {
        TODO("Check for existing MSAL account and valid cached token")
    }

    override suspend fun authenticate(accountId: String) {
        TODO("Launch MSAL interactive authentication activity")
    }

    override fun signOut(accountId: String) {
        authManager.removeAccount(accountId)
    }
}
