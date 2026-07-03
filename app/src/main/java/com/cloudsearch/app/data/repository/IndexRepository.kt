package me.fulltxt.app.data.repository

import android.content.Context
import me.fulltxt.app.data.cloud.CloudConnector
import me.fulltxt.app.data.extractor.PdfOcr
import me.fulltxt.app.data.extractor.TextExtractor
import me.fulltxt.app.data.ocr.OcrQueue
import me.fulltxt.app.data.preferences.AppPreferences
import me.fulltxt.app.data.local.dao.FileIndexDao
import me.fulltxt.app.data.local.entity.FileContentEntity
import me.fulltxt.app.data.local.entity.FileMetadataEntity
import me.fulltxt.app.domain.model.CloudAccount
import me.fulltxt.app.domain.model.CloudFile
import me.fulltxt.app.domain.model.CloudProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IndexRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: FileIndexDao,
    private val appPreferences: AppPreferences,
    private val ocrQueue: OcrQueue
) {
    private val syncPrefs = context.getSharedPreferences("fulltxt_sync", Context.MODE_PRIVATE)

    // --- Sync-Zustand ---

    fun getChangeToken(accountId: String): String? =
        syncPrefs.getString("ct_$accountId", null)

    fun saveChangeToken(accountId: String, token: String) =
        syncPrefs.edit().putString("ct_$accountId", token).apply()

    fun isFullyIndexed(accountId: String): Boolean =
        syncPrefs.getBoolean("fi_$accountId", false)

    fun markFullyIndexed(accountId: String) =
        syncPrefs.edit().putBoolean("fi_$accountId", true).apply()

    /**
     * Anzahl der aktuell übersprungenen (zu großen) Dateien eines Kontos, für die Konto-Karte.
     * Gestützt auf das [skipped][FileMetadataEntity.skipped]-Flag, sodass sie über Cursor-Delta-Syncs
     * und Neubewertungen des Limits hinweg korrekt bleibt.
     */
    suspend fun getSkippedCount(accountId: String): Int = dao.getSkippedCount(accountId)

    fun clearSyncState(accountId: String) =
        syncPrefs.edit().remove("ct_$accountId").remove("fi_$accountId").apply()

    // --- Konto-Persistenz ---

    fun getConnectedAccounts(): List<CloudAccount> {
        val ids = syncPrefs.getStringSet("acct_ids", emptySet()) ?: emptySet()
        return ids.mapNotNull { id -> syncPrefs.getString("acct_$id", null)?.parseAccount() }
    }

    fun saveAccount(account: CloudAccount) {
        val ids = (syncPrefs.getStringSet("acct_ids", emptySet()) ?: emptySet()).toMutableSet()
        ids.add(account.accountId)
        syncPrefs.edit()
            .putStringSet("acct_ids", ids)
            .putString("acct_${account.accountId}", "${account.provider.name}|${account.accountId}|${account.email}|${account.displayName}")
            .apply()
    }

    fun removeAccount(accountId: String) {
        val ids = (syncPrefs.getStringSet("acct_ids", emptySet()) ?: emptySet()).toMutableSet()
        ids.remove(accountId)
        syncPrefs.edit()
            .putStringSet("acct_ids", ids)
            .remove("acct_$accountId")
            .apply()
        clearSyncState(accountId)
    }

    suspend fun deleteAllByAccount(accountId: String) = dao.deleteAllByAccount(accountId)

    private fun String.parseAccount(): CloudAccount? {
        val p = split("|", limit = 4)
        if (p.size != 4) return null
        return CloudAccount(
            accountId = p[1],
            provider = runCatching { CloudProvider.valueOf(p[0]) }.getOrNull() ?: return null,
            email = p[2],
            displayName = p[3]
        )
    }

    // --- Index-Operationen ---

    suspend fun getIndexedFileCount(accountId: String): Int =
        dao.getIndexedFileCount(accountId)

    suspend fun getFileIdsByAccount(provider: String, accountId: String): List<String> =
        dao.getAllByAccount(provider, accountId).map { it.fileId }

    /**
     * Lädt eine einzelne Datei herunter, extrahiert sie und speichert sie.
     * Gibt false zurück, wenn die Datei übersprungen wurde, weil ihr changeToken dem gespeicherten
     * entspricht.
     */
    suspend fun indexFile(file: CloudFile, connector: CloudConnector): Boolean {
        val stored = dao.getMetadata(file.fileId)
        // Erneuten Download nur überspringen, wenn unverändert UND aktuell nicht übersprungen. Eine
        // übersprungene Zeile muss hier trotzdem (neu) indexiert werden, wenn sie jetzt ins Limit passt,
        // auch wenn ihr changeToken unverändert ist.
        if (stored != null && file.changeToken != null &&
            stored.changeToken == file.changeToken && !stored.skipped) {
            // Der Inhalt ist unverändert, daher kein erneuter Download. Die Anzeige-Metadaten werden
            // aber aus der Auflistung abgeleitet und können sich zwischen App-Versionen verbessern
            // (z. B. WebDAV-Namen/-Pfade sind jetzt prozentdekodiert). Deshalb an Ort und Stelle
            // auffrischen — sonst bleiben ältere Zeilen mit veralteten Werten wie "Favorite%20Gifts.md"
            // eingefroren, weil sich das eTag nie ändert.
            if (stored.fileName != file.fileName ||
                stored.cloudPath != file.cloudPath ||
                stored.webUrl != file.webUrl) {
                dao.refreshDisplayMetadata(file.fileId, file.fileName, file.cloudPath, file.webUrl)
            }
            return false
        }

        // createTempFile nutzen, damit das OS einen sicheren, eindeutigen Dateinamen erzeugt.
        // Verhindert Path-Traversal: Datei-IDs mancher Anbieter (z. B. Dropbox) enthalten '/',
        // wodurch File(cacheDir, fileId) zu einem absoluten Pfad außerhalb von cacheDir würde.
        val tempFile = File.createTempFile("idx_", ".tmp", context.cacheDir)
        try {
            tempFile.writeBytes(connector.downloadFile(file.fileId, file.accountId))
            val text = TextExtractor.extract(tempFile, file.mimeType)
            dao.upsertFile(
                metadata = file.toMetadataEntity(),
                content = FileContentEntity(
                    fileId = file.fileId,
                    fileName = file.fileName,
                    content = text
                )
            )
            // Gescannte PDFs (ohne Textebene) werden für einen separaten, fortsetzbaren OCR-Durchlauf
            // eingereiht, statt sie hier inline zu OCR-en — das hält den Haupt-Indexlauf schnell.
            if (appPreferences.ocrEnabled && TextExtractor.pdfNeedsOcr(file.mimeType, text)) {
                ocrQueue.add(file.fileId)
            } else {
                ocrQueue.remove(file.fileId)
            }
        } finally {
            tempFile.delete()
        }
        return true
    }

    /**
     * Lädt eine einzelne in der Warteschlange stehende Datei herunter, führt OCR darauf aus und
     * ersetzt ihren gespeicherten Inhalt. [connectorFor] löst einen [CloudConnector] für den Anbieter
     * der Datei auf (der Worker hält die Connector-Instanzen). Gibt false zurück, wenn die Datei nicht
     * mehr existiert oder ihr Anbieter nicht verfügbar ist; die Datei wird bei Erfolg aus der
     * OCR-Warteschlange entfernt, damit der Durchlauf nach einer Unterbrechung fortgesetzt werden kann.
     */
    suspend fun ocrPendingFile(fileId: String, connectorFor: (String) -> CloudConnector?): Boolean {
        val meta = dao.getMetadata(fileId)
        if (meta == null) {
            ocrQueue.remove(fileId)
            return false
        }
        val connector = connectorFor(meta.cloudProvider) ?: return false

        val tempFile = File.createTempFile("ocr_", ".tmp", context.cacheDir)
        try {
            tempFile.writeBytes(connector.downloadFile(meta.fileId, meta.accountId))
            val text = PdfOcr.extract(tempFile)
            dao.upsertFile(
                metadata = meta,
                content = FileContentEntity(
                    fileId = meta.fileId,
                    fileName = meta.fileName,
                    content = text
                )
            )
        } finally {
            tempFile.delete()
        }
        ocrQueue.remove(fileId)
        return true
    }

    suspend fun removeFile(fileId: String) = dao.deleteFile(fileId)

    /** Erfasst eine zu große Datei als bekannt-aber-übersprungen (nur Metadaten, kein Inhalt). */
    suspend fun markSkipped(file: CloudFile) = dao.markSkipped(file.toMetadataEntity())

    /**
     * Bewertet bereits bekannte Dateien gegen das aktuelle Größenlimit neu, ohne die Cloud neu zu
     * listen — damit ein geändertes Limit beim nächsten Sync auch für Cursor-Delta-Anbieter greift
     * (Dropbox/OneDrive):
     *  - übersprungene Dateien, die jetzt ins Limit passen, werden heruntergeladen und indexiert,
     *  - indexierte Dateien, die es jetzt überschreiten, verlieren ihren Inhalt und werden übersprungen.
     */
    suspend fun reEvaluateSkippedAgainstLimit(
        accountId: String,
        connector: CloudConnector,
        maxFileBytes: Long
    ) {
        dao.getSkippedAtOrBelow(accountId, maxFileBytes).forEach { meta ->
            runCatching { indexFile(meta.toCloudFile(), connector) }
        }
        dao.getIndexedAbove(accountId, maxFileBytes).forEach { meta ->
            runCatching { dao.markSkipped(meta) }
        }
    }

    private fun CloudFile.toMetadataEntity(skipped: Boolean = false) = FileMetadataEntity(
        fileId = fileId,
        fileName = fileName,
        cloudPath = cloudPath,
        cloudProvider = cloudProvider.name,
        accountId = accountId,
        fileSizeBytes = fileSizeBytes,
        createdAt = createdAt,
        modifiedAt = modifiedAt,
        mimeType = mimeType,
        changeToken = changeToken,
        checksum = null,
        indexedAt = System.currentTimeMillis(),
        webUrl = webUrl,
        skipped = skipped
    )

    private fun FileMetadataEntity.toCloudFile() = CloudFile(
        fileId = fileId,
        fileName = fileName,
        cloudPath = cloudPath,
        cloudProvider = CloudProvider.valueOf(cloudProvider),
        accountId = accountId,
        fileSizeBytes = fileSizeBytes,
        createdAt = createdAt,
        modifiedAt = modifiedAt,
        mimeType = mimeType,
        changeToken = changeToken,
        webUrl = webUrl
    )
}
