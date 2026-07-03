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
     * True für eine Datei, die bekannt ist, aber absichtlich nicht indexiert wird, weil sie die
     * konfigurierte maximale Dateigröße überschreitet. Wird (mit ihrer [fileSizeBytes]) gespeichert,
     * damit sie bei späteren Syncs ohne komplette Cloud-Neuauflistung erneut gegen das Limit bewertet
     * werden kann. Übersprungene Zeilen haben keine file_content_fts-Zeile und erscheinen daher nie
     * in Suchergebnissen.
     */
    val skipped: Boolean = false
)
