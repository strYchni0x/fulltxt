package me.fulltxt.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Transaction
import androidx.sqlite.db.SupportSQLiteQuery
import me.fulltxt.app.data.local.entity.FileContentEntity
import me.fulltxt.app.data.local.entity.FileMetadataEntity
import me.fulltxt.app.data.local.entity.SnippetResult
import kotlinx.coroutines.flow.Flow

@Dao
interface FileIndexDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMetadata(entity: FileMetadataEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertContent(entity: FileContentEntity)

    @Query("DELETE FROM file_metadata WHERE fileId = :fileId")
    suspend fun deleteMetadata(fileId: String)

    @Query("DELETE FROM file_content_fts WHERE fileId = :fileId")
    suspend fun deleteContent(fileId: String)

    @Query("SELECT * FROM file_metadata WHERE fileId = :fileId")
    suspend fun getMetadata(fileId: String): FileMetadataEntity?

    @Query("SELECT * FROM file_metadata WHERE fileId IN (:fileIds)")
    suspend fun getMetadataByIds(fileIds: List<String>): List<FileMetadataEntity>

    /** Gibt alle Metadaten-Zeilen zurück, deren fileName zu einem Eintrag in [fileNames] passt.
     *  Wird zur anbieterübergreifenden Duplikaterkennung nach einer Suchanfrage verwendet. */
    @Query("SELECT * FROM file_metadata WHERE fileName IN (:fileNames)")
    suspend fun getByFileNames(fileNames: List<String>): List<FileMetadataEntity>

    // Nutzt @RawQuery, um die snippet()-Hilfsfunktion von FTS4 aufzurufen,
    // die Rooms Compile-Zeit-Validator nicht kennt.
    @RawQuery
    suspend fun searchSnippets(query: SupportSQLiteQuery): List<SnippetResult>

    @Query("SELECT * FROM file_metadata WHERE cloudProvider = :provider AND accountId = :accountId")
    suspend fun getAllByAccount(provider: String, accountId: String): List<FileMetadataEntity>

    // Inhalt muss vor den Metadaten gelöscht werden (Unterabfrage referenziert die Metadaten-Tabelle)
    @Query("DELETE FROM file_content_fts WHERE fileId IN (SELECT fileId FROM file_metadata WHERE accountId = :accountId)")
    suspend fun deleteContentByAccount(accountId: String)

    @Query("DELETE FROM file_metadata WHERE accountId = :accountId")
    suspend fun deleteMetadataByAccount(accountId: String)

    @Transaction
    suspend fun deleteAllByAccount(accountId: String) {
        deleteContentByAccount(accountId)
        deleteMetadataByAccount(accountId)
    }

    @Query("SELECT COUNT(*) FROM file_metadata WHERE accountId = :accountId AND skipped = 0")
    suspend fun getIndexedFileCount(accountId: String): Int

    /** Anzahl der als übersprungen (zu groß) markierten Dateien eines Kontos. */
    @Query("SELECT COUNT(*) FROM file_metadata WHERE accountId = :accountId AND skipped = 1")
    suspend fun getSkippedCount(accountId: String): Int

    /** Übersprungene Dateien, die jetzt auf oder unter dem aktuellen Limit liegen — Kandidaten zum Indexieren. */
    @Query("SELECT * FROM file_metadata WHERE accountId = :accountId AND skipped = 1 AND fileSizeBytes <= :maxBytes")
    suspend fun getSkippedAtOrBelow(accountId: String, maxBytes: Long): List<FileMetadataEntity>

    /** Indexierte Dateien, die jetzt über dem aktuellen Limit liegen — müssen Inhalt verwerfen und übersprungen werden. */
    @Query("SELECT * FROM file_metadata WHERE accountId = :accountId AND skipped = 0 AND fileSizeBytes > :maxBytes")
    suspend fun getIndexedAbove(accountId: String, maxBytes: Long): List<FileMetadataEntity>

    /** Erfasst eine bekannte-aber-nicht-indexierte (zu große) Datei: nur Metadaten, keine FTS-Inhaltszeile. */
    @Transaction
    suspend fun markSkipped(metadata: FileMetadataEntity) {
        upsertMetadata(metadata.copy(skipped = true))
        deleteContent(metadata.fileId)
    }

    @Transaction
    suspend fun upsertFile(metadata: FileMetadataEntity, content: FileContentEntity) {
        upsertMetadata(metadata)
        deleteContent(metadata.fileId)
        upsertContent(content)
    }

    @Query("UPDATE file_metadata SET fileName = :fileName, cloudPath = :cloudPath, webUrl = :webUrl WHERE fileId = :fileId")
    suspend fun updateDisplayMetadata(fileId: String, fileName: String, cloudPath: String, webUrl: String?)

    @Query("UPDATE file_content_fts SET fileName = :fileName WHERE fileId = :fileId")
    suspend fun updateContentFileName(fileId: String, fileName: String)

    /**
     * Frischt die reinen Anzeige-Felder einer bereits indexierten Datei (Name/Pfad/Web-Link) auf,
     * ohne ihren Inhalt erneut herunterzuladen. Wird verwendet, wenn die Auflistung bessere Werte
     * liefert als ein früherer Indexlauf gespeichert hat — z. B. WebDAV-Namen/-Pfade, die jetzt
     * prozentdekodiert sind. Aktualisiert außerdem den gespiegelten fileName in der FTS-Zeile,
     * damit die Suche nach Namen konsistent bleibt.
     */
    @Transaction
    suspend fun refreshDisplayMetadata(fileId: String, fileName: String, cloudPath: String, webUrl: String?) {
        updateDisplayMetadata(fileId, fileName, cloudPath, webUrl)
        updateContentFileName(fileId, fileName)
    }

    @Transaction
    suspend fun deleteFile(fileId: String) {
        deleteMetadata(fileId)
        deleteContent(fileId)
    }
}
