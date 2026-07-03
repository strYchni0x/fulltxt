package me.fulltxt.app.data.cloud.strato

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
 * Strato-HiDrive-Connector.
 *
 * HiDrive-WebDAV-Endpunkt: https://webdav.hidrive.strato.com/users/<username>/
 * Authentifizierung: Basic Auth (Strato-Benutzername + Passwort).
 *
 * Die Pfadstruktur unterscheidet sich von Nextcloud, daher wird [NextcloudWebDavClient.listFiles]
 * mit einem expliziten [rootPath] von "/users/<username>/" aufgerufen.
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

    /** fileId ist der vollständige WebDAV-href (z. B. /users/username/docs/file.pdf). */
    override suspend fun downloadFile(fileId: String, accountId: String): ByteArray {
        val auth = requireAuth(accountId)
        return webDavClient.downloadFile(StratoAuthManager.SERVER_URL, auth, fileId)
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
        val cloudPath = Uri.decode(
            href.substringAfter("/users/$username")
                .substringBeforeLast('/').ifEmpty { "/" }
        )

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
            webUrl        = null   // Die HiDrive-Web-Oberfläche hat kein stabiles Deep-Link-Format
        )
    }
}
