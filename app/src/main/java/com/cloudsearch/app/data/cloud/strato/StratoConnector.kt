package me.fulltxt.app.data.cloud.strato

import me.fulltxt.app.data.cloud.CloudConnector
import me.fulltxt.app.data.cloud.nextcloud.NextcloudWebDavClient
import me.fulltxt.app.data.cloud.nextcloud.WebDavFile
import me.fulltxt.app.domain.model.CloudFile
import me.fulltxt.app.domain.model.CloudProvider
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Strato HiDrive connector.
 *
 * HiDrive WebDAV endpoint: https://webdav.hidrive.strato.com/users/<username>/
 * Authentication: Basic Auth (Strato username + password).
 *
 * The path structure differs from Nextcloud, so [NextcloudWebDavClient.listFiles] is called
 * with an explicit [rootPath] of "/users/<username>/".
 */
@Singleton
class StratoConnector @Inject constructor(
    private val authManager: StratoAuthManager,
    private val webDavClient: NextcloudWebDavClient
) : CloudConnector {

    override suspend fun listFiles(accountId: String): List<CloudFile> {
        val creds    = requireCreds(accountId)
        val auth     = requireAuth(accountId)
        val rootPath = "/users/${creds.username}/"
        return webDavClient
            .listFiles(StratoAuthManager.SERVER_URL, creds.username, auth, rootPath)
            .map { it.toCloudFile(accountId, creds.username) }
    }

    /** fileId is the full WebDAV href (e.g. /users/username/docs/file.pdf). */
    override suspend fun downloadFile(fileId: String, accountId: String): ByteArray {
        val auth = requireAuth(accountId)
        return webDavClient.downloadFile(StratoAuthManager.SERVER_URL, auth, fileId)
    }

    override suspend fun getChanges(
        accountId: String,
        changeToken: String?
    ): Pair<List<CloudFile>, String> {
        val files = listFiles(accountId)
        return Pair(files, System.currentTimeMillis().toString())
    }

    override fun isAuthenticated(accountId: String): Boolean =
        authManager.isAuthenticated(accountId)

    override suspend fun authenticate(accountId: String) {
        if (!authManager.isAuthenticated(accountId))
            throw IllegalStateException("Strato HiDrive-Zugangsdaten nicht gesetzt")
    }

    override fun signOut(accountId: String) = authManager.removeCredentials(accountId)

    private fun requireCreds(accountId: String) =
        authManager.getCredentials(accountId)
            ?: throw IllegalStateException("Keine Strato-Zugangsdaten für $accountId")

    private fun requireAuth(accountId: String) =
        authManager.getBasicAuthHeader(accountId)
            ?: throw IllegalStateException("Keine Strato-Zugangsdaten für $accountId")

    private fun WebDavFile.toCloudFile(accountId: String, username: String): CloudFile {
        // href: /users/<username>/folder/file.pdf  →  cloudPath: /folder
        val cloudPath = href
            .substringAfter("/users/$username")
            .substringBeforeLast('/').ifEmpty { "/" }

        return CloudFile(
            fileId        = href,
            fileName      = name,
            cloudPath     = cloudPath,
            cloudProvider = CloudProvider.STRATO_HIDRIVE,
            accountId     = accountId,
            fileSizeBytes = sizeBytes,
            createdAt     = createdAt,
            modifiedAt    = modifiedAt,
            mimeType      = mimeType ?: "application/octet-stream",
            changeToken   = etag,
            webUrl        = null   // HiDrive web UI has no stable deep-link format
        )
    }
}
