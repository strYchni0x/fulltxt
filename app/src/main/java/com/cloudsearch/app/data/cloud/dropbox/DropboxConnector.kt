package me.fulltxt.app.data.cloud.dropbox

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.fulltxt.app.data.cloud.CloudConnector
import me.fulltxt.app.domain.model.CloudFile
import me.fulltxt.app.domain.model.CloudProvider
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DropboxConnector @Inject constructor(
    private val authManager: DropboxAuthManager,
    private val apiService: DropboxApiService,
    private val okHttpClient: OkHttpClient
) : CloudConnector {

    companion object {
        private const val CONTENT_BASE = "https://content.dropboxapi.com/2/files/download"

        private val EXTENSION_MIME = mapOf(
            "txt"  to "text/plain",
            "log"  to "text/plain",
            "md"   to "text/markdown",
            "csv"  to "text/csv",
            "pdf"  to "application/pdf",
            "docx" to "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "xlsx" to "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "pptx" to "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        )
    }

    override suspend fun listFiles(accountId: String): List<CloudFile> =
        withContext(Dispatchers.IO) {
            val auth    = authManager.getBearerHeader(accountId)
            val results = mutableListOf<CloudFile>()
            var cursor: String? = null
            var hasMore = true

            while (hasMore) {
                val page = if (cursor == null)
                    apiService.listFolder(auth, ListFolderRequest())
                else
                    apiService.listFolderContinue(auth, ContinueRequest(cursor))

                page.entries
                    .filter { it.tag == "file" && it.isDownloadable != false }
                    .mapNotNull { it.toCloudFile(accountId) }
                    .let(results::addAll)

                cursor  = page.cursor
                hasMore = page.hasMore
            }

            results
        }

    override suspend fun downloadFile(fileId: String, accountId: String): ByteArray =
        withContext(Dispatchers.IO) {
            val auth = authManager.getBearerHeader(accountId)
            // fileId is the Dropbox item ID (id:…); pass it as path in the API arg header
            val apiArg = """{"path":"$fileId"}"""

            val request = Request.Builder()
                .url(CONTENT_BASE)
                .post(okhttp3.RequestBody.create(null, ByteArray(0)))
                .header("Authorization",    auth)
                .header("Dropbox-API-Arg",  apiArg)
                .header("Content-Type",     "application/octet-stream")
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful)
                    throw Exception("Download fehlgeschlagen: HTTP ${response.code} für $fileId")
                response.body?.bytes() ?: ByteArray(0)
            }
        }

    /**
     * Delta sync using Dropbox's cursor mechanism.
     * First call (changeToken == null): fetch all files + save cursor.
     * Subsequent calls: fetch only changed entries since the stored cursor.
     */
    override suspend fun getChanges(
        accountId: String,
        changeToken: String?
    ): Pair<List<CloudFile>, String> = withContext(Dispatchers.IO) {
        val auth = authManager.getBearerHeader(accountId)

        if (changeToken == null) {
            val files  = listFiles(accountId)
            val cursor = apiService.getLatestCursor(auth, ListFolderRequest()).cursor
            return@withContext Pair(files, cursor)
        }

        // Incremental: walk all pages since the stored cursor
        val changed = mutableListOf<CloudFile>()
        var cursor  = changeToken
        var hasMore = true

        while (hasMore) {
            val page = apiService.listFolderContinue(auth, ContinueRequest(cursor))
            page.entries
                .filter { it.tag == "file" && it.isDownloadable != false }
                .mapNotNull { it.toCloudFile(accountId) }
                .let(changed::addAll)
            cursor  = page.cursor
            hasMore = page.hasMore
        }

        Pair(changed, cursor)
    }

    override fun isAuthenticated(accountId: String): Boolean =
        authManager.isAuthenticated(accountId)

    override suspend fun authenticate(accountId: String) {
        authManager.authenticate(accountId)
    }

    override fun signOut(accountId: String) {
        authManager.signOut(accountId)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun DropboxEntry.toCloudFile(accountId: String): CloudFile? {
        val entryId   = id   ?: return null
        val entryName = name ?: return null
        val mime      = EXTENSION_MIME[entryName.substringAfterLast('.').lowercase()] ?: return null

        val path      = pathDisplay ?: "/$entryName"
        val parentDir = path.substringBeforeLast('/').ifEmpty { "/" }
        val modified  = serverModified?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrDefault(0L) } ?: 0L
        val webUrl    = "https://www.dropbox.com/home${Uri.encode(path)}"

        return CloudFile(
            fileId        = entryId,
            fileName      = entryName,
            cloudPath     = parentDir,
            cloudProvider = CloudProvider.DROPBOX,
            accountId     = accountId,
            fileSizeBytes = size ?: 0L,
            createdAt     = modified,
            modifiedAt    = modified,
            mimeType      = mime,
            changeToken   = rev,
            webUrl        = webUrl
        )
    }

    /** URL-encodes a path for the Dropbox web-UI link. */
    private fun Uri.encode(s: String): String =
        android.net.Uri.encode(s, "/")
}
