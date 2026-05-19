package me.fulltxt.app.data.cloud.googledrive

import me.fulltxt.app.data.cloud.CloudConnector
import me.fulltxt.app.domain.model.CloudFile
import me.fulltxt.app.domain.model.CloudProvider
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoogleDriveConnector @Inject constructor(
    private val authManager: GoogleAuthManager
) : CloudConnector {

    companion object {
        private const val APP_NAME = "FULLTXT"
        private const val FILE_FIELDS = "id,name,mimeType,size,createdTime,modifiedTime,parents,md5Checksum"
        private const val PAGE_SIZE = 100
        private val SUPPORTED_MIME_TYPES = listOf(
            "text/plain", "text/csv", "text/markdown",
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        )
    }

    private fun buildService(accountId: String): Drive =
        Drive.Builder(NetHttpTransport(), GsonFactory.getDefaultInstance(), authManager.getCredential(accountId))
            .setApplicationName(APP_NAME)
            .build()

    override suspend fun listFiles(accountId: String): List<CloudFile> {
        TODO("Paginated listing via files.list with mimeType filter and nextPageToken")
    }

    override suspend fun downloadFile(fileId: String, accountId: String): ByteArray {
        TODO("Stream download via files.get with alt=media")
    }

    override suspend fun getChanges(accountId: String, changeToken: String?): Pair<List<CloudFile>, String> {
        TODO("Delta sync via changes.list; fetch startPageToken if changeToken is null")
    }

    override fun isAuthenticated(accountId: String): Boolean {
        TODO("Verify credential selectedAccountName is set and token is non-expired")
    }

    override suspend fun authenticate(accountId: String) {
        TODO("Launch account picker intent via GoogleSignIn / Credential Manager")
    }

    override fun signOut(accountId: String) {
        TODO("Revoke and clear OAuth2 credential")
    }
}
