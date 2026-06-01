package me.fulltxt.app.data.cloud.googledrive

import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.fulltxt.app.BuildConfig
import me.fulltxt.app.data.cloud.CloudConnector
import me.fulltxt.app.data.cloud.SyncChanges
import me.fulltxt.app.data.repository.IndexRepository
import me.fulltxt.app.domain.model.CloudFile
import me.fulltxt.app.domain.model.CloudProvider
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoogleDriveConnector @Inject constructor(
    private val authManager: GoogleAuthManager,
    private val selectionStore: GoogleDriveSelectionStore,
    private val indexRepository: IndexRepository
) : CloudConnector {

    companion object {
        private const val APP_NAME = "FullTXT"
        private const val FILE_FIELDS = "id,name,mimeType,size,createdTime,modifiedTime,parents,md5Checksum,webViewLink"
        private const val PAGE_SIZE = 100
        private const val FOLDER_MIME = "application/vnd.google-apps.folder"

        /**
         * The playstore edition uses the drive.file scope and can only see what the user picked
         * via the Google Picker, so it enumerates from the saved selection. The dev edition uses
         * drive.readonly and lists the whole Drive.
         */
        private val USE_SELECTION = BuildConfig.FLAVOR == "playstore"

        private val SUPPORTED_MIME_TYPES = setOf(
            "text/plain", "text/csv", "text/markdown",
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        )
        private val MIME_TYPE_QUERY = SUPPORTED_MIME_TYPES
            .joinToString(" or ") { "mimeType='$it'" }
    }

    private fun buildService(accountId: String): Drive =
        Drive.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            authManager.getCredential(accountId)
        )
        .setApplicationName(APP_NAME)
        .build()

    override suspend fun listFiles(accountId: String): List<CloudFile> =
        withContext(Dispatchers.IO) {
            listFilesInternal(buildService(accountId), accountId)
        }

    override suspend fun downloadFile(fileId: String, accountId: String): ByteArray =
        withContext(Dispatchers.IO) {
            buildService(accountId)
                .files()
                .get(fileId)
                .executeMediaAsInputStream()
                .readBytes()
        }

    override suspend fun getChanges(
        accountId: String,
        changeToken: String?
    ): SyncChanges = withContext(Dispatchers.IO) {
        val drive = buildService(accountId)

        // drive.file (playstore): the Changes API only reports files the app can access and the
        // selection can shrink, so re-list the selection each sync and diff against the index —
        // same approach as LocalFolderConnector. No change token is used.
        if (USE_SELECTION) {
            val current = listFilesInternal(drive, accountId)
            val currentIds = current.map { it.fileId }.toSet()
            val deletedIds = indexRepository.getFileIdsByAccount("GOOGLE_DRIVE", accountId)
                .filter { it !in currentIds }
            return@withContext SyncChanges(current, deletedIds, System.currentTimeMillis().toString())
        }

        if (changeToken == null) {
            // First run: full list + grab the start page token for future incremental syncs.
            val files = listFilesInternal(drive, accountId)
            val token = drive.changes().getStartPageToken().execute().startPageToken
            return@withContext SyncChanges(files, emptyList(), token)
        }

        val changed    = mutableListOf<CloudFile>()
        val deletedIds = mutableListOf<String>()
        var pageToken: String? = changeToken
        var newToken = changeToken

        while (pageToken != null) {
            val response = drive.changes().list(pageToken)
                .setFields("nextPageToken, newStartPageToken, changes(fileId, removed, file($FILE_FIELDS))")
                .setPageSize(PAGE_SIZE)
                .execute()

            response.changes?.forEach { change ->
                if (change.removed) {
                    change.fileId?.let { deletedIds.add(it) }
                } else if (change.file != null) {
                    change.file.toCloudFile(accountId)?.let { changed.add(it) }
                }
            }

            response.newStartPageToken?.let { newToken = it }
            pageToken = response.nextPageToken
        }

        SyncChanges(changed, deletedIds, newToken ?: "")
    }

    override fun isAuthenticated(accountId: String): Boolean =
        authManager.isAuthenticated(accountId)

    override suspend fun authenticate(accountId: String) {
        authManager.signIn()
    }

    override fun signOut(accountId: String) {
        authManager.signOut(accountId)
    }

    private fun listFilesInternal(drive: Drive, accountId: String): List<CloudFile> =
        if (USE_SELECTION) listFromSelection(drive, accountId)
        else listAll(drive, accountId)

    /** dev edition: every supported file in the Drive. */
    private fun listAll(drive: Drive, accountId: String): List<CloudFile> {
        val results = mutableListOf<CloudFile>()
        var pageToken: String? = null
        do {
            val response = drive.files().list()
                .setQ("($MIME_TYPE_QUERY) and trashed = false")
                .setFields("nextPageToken, files($FILE_FIELDS)")
                .setPageSize(PAGE_SIZE)
                .setPageToken(pageToken)
                .execute()
            response.files?.mapNotNull { it.toCloudFile(accountId) }?.let(results::addAll)
            pageToken = response.nextPageToken
        } while (pageToken != null)
        return results
    }

    /** playstore edition: only the folders (recursively) and files the user picked. */
    private fun listFromSelection(drive: Drive, accountId: String): List<CloudFile> {
        val byId = LinkedHashMap<String, CloudFile>()

        selectionStore.getFolders(accountId).forEach { folderId ->
            collectFolder(drive, folderId, accountId, byId, mutableSetOf())
        }

        selectionStore.getFiles(accountId).forEach { fileId ->
            if (byId.containsKey(fileId)) return@forEach
            runCatching {
                drive.files().get(fileId).setFields(FILE_FIELDS).execute().toCloudFile(accountId)
            }.getOrNull()?.let { byId[it.fileId] = it }
        }

        return byId.values.toList()
    }

    private fun collectFolder(
        drive: Drive,
        folderId: String,
        accountId: String,
        out: MutableMap<String, CloudFile>,
        visited: MutableSet<String>
    ) {
        if (!visited.add(folderId)) return  // guard against cycles / shared folders
        var pageToken: String? = null
        do {
            val response = drive.files().list()
                .setQ("'$folderId' in parents and trashed = false")
                .setFields("nextPageToken, files($FILE_FIELDS)")
                .setPageSize(PAGE_SIZE)
                .setPageToken(pageToken)
                .execute()
            response.files?.forEach { file ->
                if (file.mimeType == FOLDER_MIME) {
                    file.id?.let { collectFolder(drive, it, accountId, out, visited) }
                } else {
                    file.toCloudFile(accountId)?.let { out[it.fileId] = it }
                }
            }
            pageToken = response.nextPageToken
        } while (pageToken != null)
    }

    private fun com.google.api.services.drive.model.File.toCloudFile(accountId: String): CloudFile? {
        if (mimeType == null || mimeType !in SUPPORTED_MIME_TYPES) return null
        return CloudFile(
            fileId = id,
            fileName = name,
            cloudPath = parents?.firstOrNull()?.let { "/$it/$name" } ?: "/$name",
            cloudProvider = CloudProvider.GOOGLE_DRIVE,
            accountId = accountId,
            fileSizeBytes = size?.toLong() ?: 0L,
            createdAt = createdTime?.value ?: 0L,
            modifiedAt = modifiedTime?.value ?: 0L,
            mimeType = mimeType,
            changeToken = md5Checksum,
            webUrl = webViewLink
        )
    }
}
