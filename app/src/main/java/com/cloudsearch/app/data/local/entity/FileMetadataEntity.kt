package me.fulltxt.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "file_metadata")
data class FileMetadataEntity(
    @PrimaryKey val fileId: String,
    val fileName: String,
    val cloudPath: String,
    val cloudProvider: String,
    val accountId: String,
    val fileSizeBytes: Long,
    val createdAt: Long,
    val modifiedAt: Long,
    val mimeType: String,
    val changeToken: String?,
    val checksum: String?,
    val indexedAt: Long,
    val webUrl: String? = null,
    /**
     * True for a file that is known but intentionally not indexed because it exceeds the
     * configured max file size. Stored (with its [fileSizeBytes]) so it can be re-evaluated
     * against the limit on later syncs without a full cloud re-list. Skipped rows have no
     * file_content_fts row and so never appear in search results.
     */
    val skipped: Boolean = false
)
