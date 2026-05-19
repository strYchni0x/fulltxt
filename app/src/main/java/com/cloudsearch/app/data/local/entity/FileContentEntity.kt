package me.fulltxt.app.data.local.entity

import androidx.room.Entity
import androidx.room.Fts4

@Entity(tableName = "file_content_fts")
@Fts4
data class FileContentEntity(
    val fileId: String,
    val fileName: String,
    val content: String
)
