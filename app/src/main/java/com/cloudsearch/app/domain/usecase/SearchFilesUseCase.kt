package me.fulltxt.app.domain.usecase

import me.fulltxt.app.data.repository.SearchRepository
import me.fulltxt.app.domain.model.SearchFilter
import me.fulltxt.app.domain.model.SearchResult
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class SearchFilesUseCase @Inject constructor(
    private val repository: SearchRepository
) {
    operator fun invoke(
        query: String,
        filter: SearchFilter = SearchFilter(),
        limit: Int = SearchRepository.DEFAULT_RESULT_LIMIT
    ): Flow<List<SearchResult>> =
        repository.search(query, filter, limit)
}
