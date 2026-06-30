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

    /** Returns all metadata rows whose fileName matches any entry in [fileNames].
     *  Used for cross-provider duplicate detection after a search query. */
    @Query("SELECT * FROM file_metadata WHERE fileName IN (:fileNames)")
    suspend fun getByFileNames(fileNames: List<String>): List<FileMetadataEntity>

    // Uses @RawQuery to call FTS4's snippet() auxiliary function,
    // which Room's compile-time validator does not recognise.
    @RawQuery
    suspend fun searchSnippets(query: SupportSQLiteQuery): List<SnippetResult>

    @Query("SELECT * FROM file_metadata WHERE cloudProvider = :provider AND accountId = :accountId")
    suspend fun getAllByAccount(provider: String, accountId: String): List<FileMetadataEntity>

    // Content must be deleted before metadata (subquery references metadata table)
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

    /** Count of files marked skipped (too large) for an account. */
    @Query("SELECT COUNT(*) FROM file_metadata WHERE accountId = :accountId AND skipped = 1")
    suspend fun getSkippedCount(accountId: String): Int

    /** Skipped files now at or below the current limit — candidates to index. */
    @Query("SELECT * FROM file_metadata WHERE accountId = :accountId AND skipped = 1 AND fileSizeBytes <= :maxBytes")
    suspend fun getSkippedAtOrBelow(accountId: String, maxBytes: Long): List<FileMetadataEntity>

    /** Indexed files now above the current limit — must drop content and become skipped. */
    @Query("SELECT * FROM file_metadata WHERE accountId = :accountId AND skipped = 0 AND fileSizeBytes > :maxBytes")
    suspend fun getIndexedAbove(accountId: String, maxBytes: Long): List<FileMetadataEntity>

    /** Records a known-but-not-indexed (too large) file: metadata only, no FTS content row. */
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

    @Transaction
    suspend fun deleteFile(fileId: String) {
        deleteMetadata(fileId)
        deleteContent(fileId)
    }
}
