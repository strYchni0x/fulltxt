package me.fulltxt.app.data.cloud.local

import android.content.Context
import android.provider.DocumentsContract
import dagger.hilt.android.qualifiers.ApplicationContext
import me.fulltxt.app.data.cloud.CloudConnector
import me.fulltxt.app.data.cloud.SyncChanges
import me.fulltxt.app.data.repository.IndexRepository
import me.fulltxt.app.domain.model.CloudFile
import me.fulltxt.app.domain.model.CloudProvider
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalFolderConnector @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authManager: LocalFolderAuthManager,
    private val indexRepository: IndexRepository
) : CloudConnector {

    override suspend fun listFiles(accountId: String): List<CloudFile> {
        val treeUri = authManager.getFolderUri(accountId) ?: return emptyList()
        return listFilesRecursively(treeUri, accountId)
    }

    override suspend fun getChanges(accountId: String, changeToken: String?): SyncChanges {
        val treeUri = authManager.getFolderUri(accountId)
            ?: return SyncChanges(emptyList(), emptyList(), System.currentTimeMillis().toString())

        val currentFiles = listFilesRecursively(treeUri, accountId)
        val currentIds = currentFiles.map { it.fileId }.toSet()

        val deletedIds = indexRepository.getFileIdsByAccount("LOCAL", accountId)
            .filter { it !in currentIds }

        return SyncChanges(
            changed = currentFiles,
            deletedIds = deletedIds,
            newChangeToken = System.currentTimeMillis().toString()
        )
    }

    override suspend fun downloadFile(fileId: String, accountId: String): ByteArray {
        val uri = android.net.Uri.parse(fileId)
        return context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IOException("Cannot open local file: $fileId")
    }

    override fun isAuthenticated(accountId: String): Boolean =
        authManager.isPermissionGranted(accountId)

    override suspend fun authenticate(accountId: String) {
        // Auth happens via ACTION_OPEN_DOCUMENT_TREE in the UI layer.
    }

    override fun signOut(accountId: String) {
        authManager.removeFolder(accountId)
    }

    private fun listFilesRecursively(treeUri: android.net.Uri, accountId: String): List<CloudFile> {
        val result = mutableListOf<CloudFile>()
        val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
        collectChildren(treeUri, rootDocId, "", result, accountId)
        return result
    }

    private fun collectChildren(
        treeUri: android.net.Uri,
        parentDocId: String,
        pathPrefix: String,
        result: MutableList<CloudFile>,
        accountId: String
    ) {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
        val cursor = runCatching {
            context.contentResolver.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_SIZE,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                    DocumentsContract.Document.COLUMN_LAST_MODIFIED
                ),
                null, null, null
            )
        }.getOrNull() ?: return

        cursor.use {
            while (it.moveToNext()) {
                val docId    = it.getString(0) ?: continue
                val name     = it.getString(1) ?: continue
                val size     = if (it.isNull(2)) 0L else it.getLong(2)
                val mime     = it.getString(3) ?: "application/octet-stream"
                val modified = if (it.isNull(4)) 0L else it.getLong(4)

                if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                    collectChildren(treeUri, docId, "$pathPrefix$name/", result, accountId)
                } else {
                    val effectiveMime = resolveMime(mime, name) ?: continue
                    val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                    result.add(
                        CloudFile(
                            fileId        = docUri.toString(),
                            fileName      = name,
                            cloudPath     = pathPrefix,
                            cloudProvider = CloudProvider.LOCAL,
                            accountId     = accountId,
                            fileSizeBytes = size,
                            createdAt     = modified,
                            modifiedAt    = modified,
                            mimeType      = effectiveMime,
                            changeToken   = modified.toString(),
                            webUrl        = null
                        )
                    )
                }
            }
        }
    }

    private fun resolveMime(mime: String, fileName: String): String? {
        if (isSupportedMime(mime)) return mime
        return when {
            fileName.endsWith(".pdf",  ignoreCase = true) -> "application/pdf"
            fileName.endsWith(".docx", ignoreCase = true) -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            fileName.endsWith(".xlsx", ignoreCase = true) -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            fileName.endsWith(".pptx", ignoreCase = true) -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            fileName.endsWith(".odt",  ignoreCase = true) -> "application/vnd.oasis.opendocument.text"
            fileName.endsWith(".ods",  ignoreCase = true) -> "application/vnd.oasis.opendocument.spreadsheet"
            fileName.endsWith(".odp",  ignoreCase = true) -> "application/vnd.oasis.opendocument.presentation"
            fileName.endsWith(".txt",  ignoreCase = true) -> "text/plain"
            fileName.endsWith(".md",   ignoreCase = true) -> "text/markdown"
            fileName.endsWith(".csv",  ignoreCase = true) -> "text/csv"
            fileName.endsWith(".log",  ignoreCase = true) -> "text/plain"
            else -> null
        }
    }

    private fun isSupportedMime(mime: String): Boolean = when {
        mime.startsWith("text/") -> true
        mime.startsWith("application/vnd.oasis.opendocument.") -> true
        mime == "application/pdf" -> true
        mime == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> true
        mime == "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> true
        mime == "application/vnd.openxmlformats-officedocument.presentationml.presentation" -> true
        else -> false
    }
}
