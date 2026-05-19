package me.fulltxt.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import me.fulltxt.app.data.local.entity.FileContentEntity
import me.fulltxt.app.data.local.entity.FileMetadataEntity
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

    @Query("SELECT * FROM file_metadata WHERE fileId IN (SELECT fileId FROM file_content_fts WHERE file_content_fts MATCH :query)")
    fun search(query: String): Flow<List<FileMetadataEntity>>

    @Query("SELECT * FROM file_metadata WHERE cloudProvider = :provider AND accountId = :accountId")
    suspend fun getAllByAccount(provider: String, accountId: String): List<FileMetadataEntity>

    @Query("SELECT COUNT(*) FROM file_metadata WHERE accountId = :accountId")
    suspend fun getIndexedFileCount(accountId: String): Int

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
