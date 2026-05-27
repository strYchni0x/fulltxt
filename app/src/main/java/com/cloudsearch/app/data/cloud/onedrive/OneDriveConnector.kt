package me.fulltxt.app.data.cloud.onedrive

import me.fulltxt.app.data.cloud.CloudConnector
import me.fulltxt.app.data.cloud.SyncChanges
import me.fulltxt.app.domain.model.CloudFile
import me.fulltxt.app.domain.model.CloudProvider
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OneDriveConnector @Inject constructor(
    private val authManager: MsalAuthManager,
    private val graphApiService: GraphApiService
) : CloudConnector {

    companion object {
        private const val GRAPH_BASE_URL = "https://graph.microsoft.com/v1.0"
        private const val SELECT_FIELDS =
            "id,name,size,createdDateTime,lastModifiedDateTime,file,parentReference,eTag,deleted,webUrl"
        private val SUPPORTED_MIME_TYPES = setOf(
            "text/plain", "text/csv", "text/markdown",
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        )
    }

    override suspend fun listFiles(accountId: String): List<CloudFile> {
        val bearer = "Bearer ${authManager.acquireToken(accountId)}"
        val results = mutableListOf<CloudFile>()
        var url: String? = "$GRAPH_BASE_URL/me/drive/root/delta?\$select=$SELECT_FIELDS"

        while (url != null) {
            val page = graphApiService.getPage(url, bearer)
            page.items
                .filter { it.deleted == null && it.file?.mimeType in SUPPORTED_MIME_TYPES }
                .mapNotNull { it.toCloudFile(accountId) }
                .let(results::addAll)
            url = page.nextLink
        }

        return results
    }

    override suspend fun downloadFile(fileId: String, accountId: String): ByteArray {
        val bearer = "Bearer ${authManager.acquireToken(accountId)}"
        return graphApiService.downloadFile(bearer, fileId).bytes()
    }

    override suspend fun getChanges(
        accountId: String,
        changeToken: String?
    ): SyncChanges {
        val bearer     = "Bearer ${authManager.acquireToken(accountId)}"
        val changed    = mutableListOf<CloudFile>()
        val deletedIds = mutableListOf<String>()
        var url: String? = changeToken ?: "$GRAPH_BASE_URL/me/drive/root/delta?\$select=$SELECT_FIELDS"
        var deltaLink = ""

        while (url != null) {
            val page = graphApiService.getPage(url, bearer)
            page.items.forEach { item ->
                when {
                    item.deleted != null -> deletedIds.add(item.id)
                    item.file?.mimeType in SUPPORTED_MIME_TYPES ->
                        item.toCloudFile(accountId)?.let { changed.add(it) }
                }
            }
            page.deltaLink?.let { deltaLink = it }
            url = page.nextLink
        }

        return SyncChanges(changed, deletedIds, deltaLink)
    }

    override fun isAuthenticated(accountId: String): Boolean =
        authManager.isAuthenticated(accountId)

    override suspend fun authenticate(accountId: String) {
        authManager.acquireToken(accountId)
    }

    override fun signOut(accountId: String) {
        authManager.removeAccount(accountId)
    }

    private fun GraphDriveItem.toCloudFile(accountId: String): CloudFile? {
        val mimeType = file?.mimeType ?: return null
        val path = parentReference?.path
            ?.removePrefix("/drive/root:")
            ?.ifEmpty { "/" }
            ?: "/"
        return CloudFile(
            fileId = id,
            fileName = name,
            cloudPath = path,
            cloudProvider = CloudProvider.ONE_DRIVE,
            accountId = accountId,
            fileSizeBytes = size ?: 0L,
            createdAt = createdDateTime?.let { Instant.parse(it).toEpochMilli() } ?: 0L,
            modifiedAt = lastModifiedDateTime?.let { Instant.parse(it).toEpochMilli() } ?: 0L,
            mimeType = mimeType,
            changeToken = eTag,
            webUrl = webUrl
        )
    }
}
