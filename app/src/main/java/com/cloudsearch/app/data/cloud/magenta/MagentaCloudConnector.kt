package me.fulltxt.app.data.cloud.magenta

import me.fulltxt.app.data.cloud.CloudConnector
import me.fulltxt.app.data.cloud.SyncChanges
import me.fulltxt.app.data.cloud.nextcloud.NextcloudWebDavClient
import me.fulltxt.app.data.cloud.nextcloud.WebDavFile
import me.fulltxt.app.domain.model.CloudFile
import me.fulltxt.app.domain.model.CloudProvider
import javax.inject.Inject
import javax.inject.Singleton

/**
 * MagentaCloud connector (Deutsche Telekom).
 *
 * MagentaCloud runs on a Nextcloud backend and exposes the identical WebDAV endpoint
 * layout (/remote.php/dav/files/<username>/). The [NextcloudWebDavClient] is reused
 * directly – only auth management and the CloudProvider tag differ.
 *
 * Server URL: https://magentacloud.de (pre-filled in UI, editable for custom instances).
 */
@Singleton
class MagentaCloudConnector @Inject constructor(
    private val authManager: MagentaCloudAuthManager,
    private val webDavClient: NextcloudWebDavClient
) : CloudConnector {

    override suspend fun listFiles(accountId: String): List<CloudFile> {
        val creds = requireCreds(accountId)
        val auth  = requireAuth(accountId)
        return webDavClient
            .listFiles(creds.serverUrl, creds.username, auth)
            .map { it.toCloudFile(accountId, creds.serverUrl) }
    }

    override suspend fun downloadFile(fileId: String, accountId: String): ByteArray {
        val creds = requireCreds(accountId)
        return webDavClient.downloadFile(creds.serverUrl, requireAuth(accountId), fileId)
    }

    override suspend fun getChanges(
        accountId: String,
        changeToken: String?
    ): SyncChanges {
        val files = listFiles(accountId)
        return SyncChanges(files, emptyList(), System.currentTimeMillis().toString())
    }

    override fun isAuthenticated(accountId: String): Boolean =
        authManager.isAuthenticated(accountId)

    override suspend fun authenticate(accountId: String) {
        if (!authManager.isAuthenticated(accountId))
            throw IllegalStateException("MagentaCloud-Zugangsdaten nicht gesetzt")
    }

    override fun signOut(accountId: String) = authManager.removeCredentials(accountId)

    private fun requireCreds(accountId: String) =
        authManager.getCredentials(accountId)
            ?: throw IllegalStateException("Keine MagentaCloud-Zugangsdaten für $accountId")

    private fun requireAuth(accountId: String) =
        authManager.getBasicAuthHeader(accountId)
            ?: throw IllegalStateException("Keine MagentaCloud-Zugangsdaten für $accountId")

    private fun WebDavFile.toCloudFile(accountId: String, serverUrl: String): CloudFile {
        val cloudPath = href
            .substringAfter("/remote.php/dav/files/")
            .substringAfter("/")
            .let { "/$it" }
            .substringBeforeLast('/').ifEmpty { "/" }
        return CloudFile(
            fileId        = href,
            fileName      = name,
            cloudPath     = cloudPath,
            cloudProvider = CloudProvider.MAGENTA_CLOUD,
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
