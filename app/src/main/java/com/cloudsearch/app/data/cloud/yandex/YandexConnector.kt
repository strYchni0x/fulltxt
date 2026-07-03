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
 * Yandex-Disk-Connector.
 *
 * WebDAV-Endpunkt: https://webdav.yandex.com/ (Dateien liegen direkt unter "/").
 * Authentifizierung: Basic Auth (Yandex-Login + Passwort oder ein App-Passwort bei aktiver 2FA).
 *
 * Yandex unterstützt kein Depth: infinity, daher fällt [NextcloudWebDavClient.listFiles]
 * transparent auf eine rekursive Depth:1-Traversierung zurück.
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

    /** fileId ist der vollständige WebDAV-href (z. B. /Documents/report.pdf). */
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
        // Zugangsdaten mit einem Depth:0-PROPFIND gegen den Server prüfen.
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
            webUrl        = null   // Die Yandex-Disk-Web-Oberfläche hat kein stabiles Deep-Link-Format
        )
    }
}
