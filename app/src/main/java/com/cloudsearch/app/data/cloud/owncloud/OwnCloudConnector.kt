package me.fulltxt.app.data.cloud.owncloud

import android.net.Uri
import me.fulltxt.app.data.cloud.CloudConnector
import me.fulltxt.app.data.cloud.SyncChanges
import me.fulltxt.app.data.cloud.nextcloud.NextcloudWebDavClient
import me.fulltxt.app.domain.model.CloudFile
import me.fulltxt.app.domain.model.CloudProvider
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ownCloud-Connector.
 *
 * ownCloud 10+ nutzt dasselbe WebDAV-Endpunkt-Layout wie Nextcloud
 * (/remote.php/dav/files/<username>/), daher verwenden wir [NextcloudWebDavClient] direkt weiter.
 * Nur die Verwaltung der Zugangsdaten und das CloudProvider-Tag unterscheiden sich.
 */
@Singleton
class OwnCloudConnector @Inject constructor(
    private val authManager: OwnCloudAuthManager,
    private val webDavClient: NextcloudWebDavClient   // gemeinsam mit Nextcloud genutzt
) : CloudConnector {

    override suspend fun listFiles(accountId: String): List<CloudFile> {
        val creds = requireCreds(accountId)
        val auth  = requireAuth(accountId)
        return webDavClient
            .listFiles(creds.serverUrl, creds.username, auth)
            .map { it.toCloudFile(accountId, creds.serverUrl) }
    }

    /** fileId ist der WebDAV-href-Pfad. */
    override suspend fun downloadFile(fileId: String, accountId: String): ByteArray {
        val creds = requireCreds(accountId)
        return webDavClient.downloadFile(creds.serverUrl, requireAuth(accountId), fileId)
    }

    /**
     * ownCloud WebDAV hat keine Delta-Token-API.
     * Es wird komplett neu gelistet; unveränderte Dateien werden im IndexRepository per eTag übersprungen.
     */
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
        if (!authManager.isAuthenticated(accountId)) {
            throw IllegalStateException("ownCloud-Zugangsdaten nicht gesetzt")
        }
    }

    override fun signOut(accountId: String) {
        authManager.removeCredentials(accountId)
    }

    // --- Hilfsfunktionen ---

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
        val encodedPath = href
            .substringAfter("/remote.php/dav/files/")
            .substringAfter("/")
            .let { "/$it" }
            .substringBeforeLast('/').ifEmpty { "/" }
        val cloudPath = Uri.decode(encodedPath)

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
            webUrl        = "${serverUrl.trimEnd('/')}/apps/files?dir=$encodedPath"
        )
    }
}
