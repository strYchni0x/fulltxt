package me.fulltxt.app.data.repository

import me.fulltxt.app.data.local.dao.FileIndexDao
import me.fulltxt.app.data.local.entity.FileMetadataEntity
import me.fulltxt.app.domain.model.CloudFile
import me.fulltxt.app.domain.model.CloudProvider
import me.fulltxt.app.domain.model.SearchResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SearchRepository @Inject constructor(
    private val dao: FileIndexDao
) {

    fun search(query: String): Flow<List<SearchResult>> =
        dao.search(query).map { entities -> entities.map { it.toSearchResult() } }

    private fun FileMetadataEntity.toSearchResult() = SearchResult(
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
            changeToken = changeToken
        ),
        snippet = "",
        score = 0.0
    )
}
