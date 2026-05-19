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
    val indexedAt: Long
)
