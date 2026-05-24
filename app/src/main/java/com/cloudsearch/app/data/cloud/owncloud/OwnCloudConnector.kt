package me.fulltxt.app.data.cloud.owncloud

import me.fulltxt.app.data.cloud.CloudConnector
import me.fulltxt.app.data.cloud.nextcloud.NextcloudWebDavClient
import me.fulltxt.app.domain.model.CloudFile
import me.fulltxt.app.domain.model.CloudProvider
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ownCloud connector.
 *
 * ownCloud 10+ uses the same WebDAV endpoint layout as Nextcloud
 * (/remote.php/dav/files/<username>/), so we reuse [NextcloudWebDavClient] directly.
 * Only credentials management and the CloudProvider tag differ.
 */
@Singleton
class OwnCloudConnector @Inject constructor(
    private val authManager: OwnCloudAuthManager,
    private val webDavClient: NextcloudWebDavClient   // shared with Nextcloud
) : CloudConnector {

    override suspend fun listFiles(accountId: String): List<CloudFile> {
        val creds = requireCreds(accountId)
        val auth  = requireAuth(accountId)
        return webDavClient
            .listFiles(creds.serverUrl, creds.username, auth)
            .map { it.toCloudFile(accountId, creds.serverUrl) }
    }

    /** fileId is the WebDAV href path. */
    override suspend fun downloadFile(fileId: String, accountId: String): ByteArray {
        val creds = requireCreds(accountId)
        return webDavClient.downloadFile(creds.serverUrl, requireAuth(accountId), fileId)
    }

    /**
     * ownCloud WebDAV has no delta-token API.
     * Full re-list is performed; unchanged files are skipped via eTag in IndexRepository.
     */
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
        if (!authManager.isAuthenticated(accountId)) {
            throw IllegalStateException("ownCloud-Zugangsdaten nicht gesetzt")
        }
    }

    override fun signOut(accountId: String) {
        authManager.removeCredentials(accountId)
    }

    // --- Helpers ---

    private fun requireCreds(accountId: String): OwnCloudCredentials =
        authManager.getCredentials(accountId)
            ?: throw IllegalStateException("Keine ownCloud-Zugangsdaten für Account $accountId")

    private fun requireAuth(accountId: String): String =
        authManager.getBasicAuthHeader(accountId)
            ?: throw IllegalStateException("Keine ownCloud-Zugangsdaten für Account $accountId")

    private fun me.fulltxt.app.data.cloud.nextcloud.WebDavFile.toCloudFile(
        accountId: String,
        serverUrl: String
    ): CloudFile {
        val cloudPath = href
            .substringAfter("/remote.php/dav/files/")
            .substringAfter("/")
            .let { "/$it" }
            .substringBeforeLast('/').ifEmpty { "/" }

        return CloudFile(
            fileId        = href,
            fileName      = name,
            cloudPath     = cloudPath,
            cloudProvider = CloudProvider.OWNCLOUD,
            accountId     = accountId,
            fileSizeBytes = sizeBytes,
            createdAt     = createdAt,
            modifiedAt    = modifiedAt,
            mimeType      = mimeType ?: "application/octet-stream",
            changeToken   = etag,
            webUrl        = "${serverUrl.trimEnd('/')}/apps/files?dir=$cloudPath"
        )
    }
}
