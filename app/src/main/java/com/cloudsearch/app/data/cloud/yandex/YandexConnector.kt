package me.fulltxt.app.data.cloud.yandex

import android.net.Uri
import me.fulltxt.app.data.cloud.CloudConnector
import me.fulltxt.app.data.cloud.SyncChanges
import me.fulltxt.app.data.cloud.nextcloud.NextcloudWebDavClient
import me.fulltxt.app.data.cloud.nextcloud.WebDavFile
import me.fulltxt.app.domain.model.CloudFile
import me.fulltxt.app.domain.model.CloudProvider
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Yandex Disk connector.
 *
 * WebDAV endpoint: https://webdav.yandex.com/ (files live directly under "/").
 * Authentication: Basic Auth (Yandex login + password, or an app password when 2FA is on).
 *
 * Yandex does not support Depth: infinity, so [NextcloudWebDavClient.listFiles] transparently
 * falls back to recursive Depth:1 traversal.
 */
@Singleton
class YandexConnector @Inject constructor(
    private val authManager: YandexAuthManager,
    private val webDavClient: NextcloudWebDavClient
) : CloudConnector {

    override suspend fun listFiles(accountId: String): List<CloudFile> {
        val creds = requireCreds(accountId)
        val auth  = requireAuth(accountId)
        return webDavClient
            .listFiles(YandexAuthManager.SERVER_URL, creds.username, auth, rootPath = "/")
            .map { it.toCloudFile(accountId) }
    }

    /** fileId is the full WebDAV href (e.g. /Documents/report.pdf). */
    override suspend fun downloadFile(fileId: String, accountId: String): ByteArray {
        val auth = requireAuth(accountId)
        return webDavClient.downloadFile(YandexAuthManager.SERVER_URL, auth, fileId)
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
        val creds = requireCreds(accountId)
        val auth  = requireAuth(accountId)
        // Validate credentials against the server with a Depth:0 PROPFIND.
        webDavClient.testConnection(YandexAuthManager.SERVER_URL, creds.username, auth, rootPath = "/")
    }

    override fun signOut(accountId: String) = authManager.removeCredentials(accountId)

    private fun requireCreds(accountId: String) =
        authManager.getCredentials(accountId)
            ?: throw IllegalStateException("Keine Yandex-Zugangsdaten für $accountId")

    private fun requireAuth(accountId: String) =
        authManager.getBasicAuthHeader(accountId)
            ?: throw IllegalStateException("Keine Yandex-Zugangsdaten für $accountId")

    private fun WebDavFile.toCloudFile(accountId: String): CloudFile {
        // href: /folder/file.pdf  →  cloudPath: /folder
        val cloudPath = Uri.decode(
            href.substringBeforeLast('/').ifEmpty { "/" }
        )

        return CloudFile(
            fileId        = href,
            fileName      = name,
            cloudPath     = cloudPath,
            cloudProvider = CloudProvider.YANDEX_DISK,
            accountId     = accountId,
            fileSizeBytes = sizeBytes,
            createdAt     = createdAt,
            modifiedAt    = modifiedAt,
            mimeType      = mimeType ?: "application/octet-stream",
            changeToken   = etag,
            webUrl        = null   // Yandex Disk web UI has no stable deep-link format
        )
    }
}
