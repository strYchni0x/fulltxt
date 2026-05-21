package me.fulltxt.app.data.repository

import androidx.sqlite.db.SimpleSQLiteQuery
import me.fulltxt.app.data.local.dao.FileIndexDao
import me.fulltxt.app.data.local.entity.FileMetadataEntity
import me.fulltxt.app.domain.model.CloudFile
import me.fulltxt.app.domain.model.CloudProvider
import me.fulltxt.app.domain.model.SearchResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SearchRepository @Inject constructor(
    private val dao: FileIndexDao
) {
    companion object {
        // Delimiters wrapping matched terms in snippets, e.g. "foo [[bar]] baz".
        // Parsed by SnippetText in the UI layer to render matches in bold.
        const val SNIPPET_START = "[["
        const val SNIPPET_END = "]]"
    }

    fun search(query: String): Flow<List<SearchResult>> = flow {
        val sanitized = sanitizeQuery(query)
        if (sanitized.isEmpty()) {
            emit(emptyList())
            return@flow
        }

        val snippetResults = runCatching {
            dao.searchSnippets(
                SimpleSQLiteQuery(
                    "SELECT fileId," +
                    " snippet(file_content_fts, '[[', ']]', ' … ', 2, 20) AS snippet" +
                    " FROM file_content_fts WHERE file_content_fts MATCH ? LIMIT 100",
                    arrayOf(sanitized)
                )
            )
        }.getOrElse {
            emit(emptyList())
            return@flow
        }

        if (snippetResults.isEmpty()) {
            emit(emptyList())
            return@flow
        }

        val fileIds = snippetResults.map { it.fileId }
        val snippetMap = snippetResults.associateBy { it.fileId }
        val metadataMap = dao.getMetadataByIds(fileIds).associateBy { it.fileId }

        val results = fileIds.mapNotNull { fileId ->
            metadataMap[fileId]?.toSearchResult(snippetMap[fileId]?.snippet ?: "")
        }

        emit(results)
    }

    private fun sanitizeQuery(raw: String): String {
        val booleanKeywords = setOf("AND", "OR", "NOT")
        val tokens = raw.trim()
            .replace(Regex("""["*()\-+]"""), " ")
            .split(Regex("""\s+"""))
            .filter { it.isNotEmpty() && it.uppercase() !in booleanKeywords }
        return tokens.joinToString(" AND ") { "$it*" }
    }

    private fun FileMetadataEntity.toSearchResult(snippet: String) = SearchResult(
        file = CloudFile(
            fileId = fileId,
            fileName = fileName,
            cloudPath = cloudPath,
            cloudProvider = CloudProvider.valueOf(cloudProvider),
            accountId = accountId,
            fileSizeBytes = fileSizeBytes,
            createdAt = createdAt,
            modifiedAt = modifiedAt,
            mimeType = mimeType,
            changeToken = changeToken,
            webUrl = webUrl
        ),
        snippet = snippet,
        score = 0.0
    )
}
