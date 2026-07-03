package me.fulltxt.app.data.repository

import androidx.sqlite.db.SimpleSQLiteQuery
import me.fulltxt.app.data.local.dao.FileIndexDao
import me.fulltxt.app.data.local.entity.FileMetadataEntity
import me.fulltxt.app.domain.model.CloudFile
import me.fulltxt.app.domain.model.CloudProvider
import me.fulltxt.app.domain.model.DateFilter
import me.fulltxt.app.domain.model.FileTypeFilter
import me.fulltxt.app.domain.model.SearchFilter
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
        // Begrenzer, die gefundene Begriffe in Snippets umschließen, z. B. "foo [[bar]] baz".
        // Werden von SnippetText in der UI-Schicht geparst, um Treffer fett darzustellen.
        const val SNIPPET_START = "[["
        const val SNIPPET_END = "]]"

        const val DEFAULT_RESULT_LIMIT = 100
    }

    fun search(
        query: String,
        filter: SearchFilter = SearchFilter(),
        limit: Int = DEFAULT_RESULT_LIMIT
    ): Flow<List<SearchResult>> = flow {
        val sanitized = sanitizeQuery(query)
        if (sanitized.isEmpty()) {
            emit(emptyList())
            return@flow
        }

        val snippetResults = runCatching {
            dao.searchSnippets(
                SimpleSQLiteQuery(
                    "SELECT fileId," +
                    // FTS4 snippet(table, start, end, ellipsis, column, tokens).
                    // column = -1 lässt FTS die Spalte wählen, die den Treffer tatsächlich enthält
                    // (z. B. einen Treffer im Dateinamen, nicht nur im Inhalt), und eine negative
                    // Token-Anzahl zentriert das Fragment auf dem Treffer statt am Spaltenanfang.
                    " snippet(file_content_fts, '[[', ']]', ' … ', -1, -20) AS snippet" +
                    " FROM file_content_fts WHERE file_content_fts MATCH ? LIMIT ?",
                    arrayOf(sanitized, limit)
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

        var results = fileIds.mapNotNull { fileId ->
            metadataMap[fileId]?.toSearchResult(snippetMap[fileId]?.snippet ?: "")
        }

        if (filter.providers.isNotEmpty()) {
            results = results.filter { it.file.cloudProvider in filter.providers }
        }
        if (filter.fileTypes.isNotEmpty()) {
            val extensions = filter.fileTypes.flatMap { it.extensions }.toSet()
            results = results.filter { r ->
                extensions.any { ext -> r.file.fileName.endsWith(".$ext", ignoreCase = true) }
            }
        }
        filter.dateFilter?.let { df ->
            val cutoff = df.cutoffMillis
            results = results.filter { it.file.modifiedAt >= cutoff }
        }

        // Duplikaterkennung: Dateien mit gleichem Namen + gleicher Größe über mehrere Anbieter/Konten finden.
        val allFileNames = results.map { it.file.fileName }.distinct()
        val candidates = dao.getByFileNames(allFileNames)

        // Nach (fileName, fileSizeBytes) gruppieren. Nur Gruppen mit >1 unterschiedlichen accountId sind echte Duplikate.
        val dupeProviders: Map<Pair<String, Long>, List<CloudProvider>> = candidates
            .groupBy { it.fileName to it.fileSizeBytes }
            .filter { (_, group) -> group.map { it.accountId }.distinct().size > 1 }
            .mapValues { (_, group) ->
                group.map { CloudProvider.valueOf(it.cloudProvider) }.distinct()
            }

        val enriched = results.map { r ->
            val key = r.file.fileName to r.file.fileSizeBytes
            r.copy(duplicateProviders = dupeProviders[key] ?: emptyList())
        }

        emit(enriched)
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
