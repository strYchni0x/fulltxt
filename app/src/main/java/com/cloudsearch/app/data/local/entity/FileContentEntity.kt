package me.fulltxt.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.FtsOptions

@Entity(tableName = "file_content_fts")
@Fts4(ftsVersion = FtsOptions.FTS_VERSION_5)
data class FileContentEntity(
    val fileId: String,
    val fileName: String,
    val content: String
)
