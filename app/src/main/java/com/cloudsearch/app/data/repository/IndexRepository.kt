package me.fulltxt.app.data.repository

import android.content.Context
import me.fulltxt.app.data.cloud.CloudConnector
import me.fulltxt.app.data.extractor.TextExtractor
import me.fulltxt.app.data.local.dao.FileIndexDao
import me.fulltxt.app.data.local.entity.FileContentEntity
import me.fulltxt.app.data.local.entity.FileMetadataEntity
import me.fulltxt.app.domain.model.CloudFile
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IndexRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: FileIndexDao
) {

    suspend fun getIndexedFileCount(accountId: String): Int =
        dao.getIndexedFileCount(accountId)

    suspend fun indexFile(file: CloudFile, connector: CloudConnector) {
        val tempFile = File(context.cacheDir, "${file.fileId}.tmp")
        try {
            tempFile.writeBytes(connector.downloadFile(file.fileId, file.accountId))
            val text = TextExtractor.extract(tempFile, file.mimeType)
            dao.upsertFile(
                metadata = file.toMetadataEntity(),
                content = FileContentEntity(fileId = file.fileId, fileName = file.fileName, content = text)
            )
        } finally {
            tempFile.delete()
        }
    }

    suspend fun removeFile(fileId: String) = dao.deleteFile(fileId)

    private fun CloudFile.toMetadataEntity() = FileMetadataEntity(
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
        indexedAt = System.currentTimeMillis()
    )
}
