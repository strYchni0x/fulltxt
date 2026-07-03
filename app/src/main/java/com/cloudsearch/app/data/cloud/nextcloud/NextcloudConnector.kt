package me.fulltxt.app.data.cloud.nextcloud

import android.net.Uri
import me.fulltxt.app.data.cloud.CloudConnector
import me.fulltxt.app.data.cloud.SyncChanges
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
     * Die fileId ist bei Nextcloud der WebDAV-href-Pfad
     * (z. B. /remote.php/dav/files/user/docs/report.pdf).
     */
    override suspend fun downloadFile(fileId: String, accountId: String): ByteArray {
        val creds = requireCreds(accountId)
        return webDavClient.downloadFile(creds.serverUrl, requireAuth(accountId), fileId)
    }

    /**
     * Nextcloud WebDAV hat keine Delta-Token-API.
     * Wir listen komplett neu; unveränderte Dateien werden im IndexRepository per eTag-Vergleich
     * übersprungen. Das zurückgegebene "Change-Token" ist ein Zeitstempel-Platzhalter für eine
     * spätere Anbindung der Activity-API.
     */
    /** WebDAV hat kein serverseitiges Löschprotokoll; komplette Neuauflistung, unveränderte Dateien per eTag übersprungen. */
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
        // Zugangsdaten werden über den Einstellungsdialog vorab gesetzt; hier nur prüfen, dass sie existieren.
        if (!authManager.isAuthenticated(accountId)) {
            throw IllegalStateException("Nextcloud-Zugangsdaten nicht gesetzt")
        }
    }

    override fun signOut(accountId: String) {
        authManager.removeCredentials(accountId)
    }

    // --- Hilfsfunktionen ---

    private fun requireCreds(accountId: String): NextcloudCredentials =
        authManager.getCredentials(accountId)
            ?: throw IllegalStateException("Keine Nextcloud-Zugangsdaten für Account $accountId")

    private fun requireAuth(accountId: String): String =
        authManager.getBasicAuthHeader(accountId)
            ?: throw IllegalStateException("Keine Nextcloud-Zugangsdaten für Account $accountId")

    private fun WebDavFile.toCloudFile(accountId: String, serverUrl: String): CloudFile {
        // WebDAV-Präfix entfernen, um den Cloud-Pfad zu erhalten. Die prozentcodierte Form für die
        // Web-Deep-Link-URL beibehalten und eine menschenlesbare Form für die Anzeige dekodieren.
        val encodedPath = href
            .substringAfter("/remote.php/dav/files/")
            .substringAfter("/")   // Benutzername-Segment entfernen
            .let { "/$it" }
            .substringBeforeLast('/').ifEmpty { "/" }
        val cloudPath = Uri.decode(encodedPath)

        // Deep-Link in die Nextcloud-Web-Oberfläche
        val webUrl = "${serverUrl.trimEnd('/')}/apps/files?dir=$encodedPath"

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
