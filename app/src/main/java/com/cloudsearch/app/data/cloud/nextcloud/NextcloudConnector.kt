package me.fulltxt.app.data.cloud.nextcloud

import me.fulltxt.app.data.cloud.CloudConnector
import me.fulltxt.app.domain.model.CloudFile
import me.fulltxt.app.domain.model.CloudProvider
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NextcloudConnector @Inject constructor(
    private val authManager: NextcloudAuthManager,
    private val webDavClient: NextcloudWebDavClient
) : CloudConnector {

    override suspend fun listFiles(accountId: String): List<CloudFile> {
        val creds = requireCreds(accountId)
        val auth  = requireAuth(accountId)
        return webDavClient
            .listFiles(creds.serverUrl, creds.username, auth)
            .map { it.toCloudFile(accountId, creds.serverUrl) }
    }

    /**
     * fileId for Nextcloud is the WebDAV href path
     * (e.g. /remote.php/dav/files/user/docs/report.pdf).
     */
    override suspend fun downloadFile(fileId: String, accountId: String): ByteArray {
        val creds = requireCreds(accountId)
        return webDavClient.downloadFile(creds.serverUrl, requireAuth(accountId), fileId)
    }

    /**
     * Nextcloud WebDAV has no delta-token API.
     * We do a full re-list; unchanged files are skipped in IndexRepository via eTag comparison.
     * The returned "change token" is a timestamp placeholder for future Activity-API integration.
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
        // Credentials are pre-set via the settings dialog; just verify they exist.
        if (!authManager.isAuthenticated(accountId)) {
            throw IllegalStateException("Nextcloud-Zugangsdaten nicht gesetzt")
        }
    }

    override fun signOut(accountId: String) {
        authManager.removeCredentials(accountId)
    }

    // --- Helpers ---

    private fun requireCreds(accountId: String): NextcloudCredentials =
        authManager.getCredentials(accountId)
            ?: throw IllegalStateException("Keine Nextcloud-Zugangsdaten für Account $accountId")

    private fun requireAuth(accountId: String): String =
        authManager.getBasicAuthHeader(accountId)
            ?: throw IllegalStateException("Keine Nextcloud-Zugangsdaten für Account $accountId")

    private fun WebDavFile.toCloudFile(accountId: String, serverUrl: String): CloudFile {
        // Strip the WebDAV prefix to get a user-visible cloud path
        val cloudPath = href
            .substringAfter("/remote.php/dav/files/")
            .substringAfter("/")   // remove username segment
            .let { "/$it" }
            .substringBeforeLast('/').ifEmpty { "/" }

        // Deep-link into Nextcloud web UI
        val webUrl = "${serverUrl.trimEnd('/')}/apps/files?dir=${cloudPath}"

        return CloudFile(
            fileId        = href,
            fileName      = name,
            cloudPath     = cloudPath,
            cloudProvider = CloudProvider.NEXTCLOUD,
            accountId     = accountId,
            fileSizeBytes = sizeBytes,
            createdAt     = createdAt,
            modifiedAt    = modifiedAt,
            mimeType      = mimeType ?: "application/octet-stream",
            changeToken   = etag,
            webUrl        = webUrl
        )
    }
}
