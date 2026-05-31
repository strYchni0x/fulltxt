package me.fulltxt.app.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import me.fulltxt.app.data.cloud.dropbox.DropboxConnector
import me.fulltxt.app.data.preferences.AppPreferences
import me.fulltxt.app.data.repository.IndexRepository
import me.fulltxt.app.domain.model.CloudFile
import me.fulltxt.app.domain.model.CloudProvider
import me.fulltxt.app.domain.model.SearchFilter
import me.fulltxt.app.domain.model.SearchResult
import me.fulltxt.app.domain.usecase.SearchFilesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchFilesUseCase: SearchFilesUseCase,
    private val dropboxConnector: DropboxConnector,
    private val appPreferences: AppPreferences,
    private val indexRepository: IndexRepository
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _filter = MutableStateFlow(SearchFilter())
    val filter: StateFlow<SearchFilter> = _filter.asStateFlow()

    private val _recentSearches =
        MutableStateFlow(appPreferences.recentSearches.take(appPreferences.recentSearchLimit))
    val recentSearches: StateFlow<List<String>> = _recentSearches.asStateFlow()

    private val _connectedProviders = MutableStateFlow<List<CloudProvider>>(emptyList())
    val connectedProviders: StateFlow<List<CloudProvider>> = _connectedProviders.asStateFlow()

    private val _openingFile = MutableStateFlow(false)
    val openingFile: StateFlow<Boolean> = _openingFile.asStateFlow()

    private val _openUrl = MutableSharedFlow<String>()
    val openUrl = _openUrl.asSharedFlow()

    private val _errorMessage = MutableSharedFlow<String>()
    val errorMessage = _errorMessage.asSharedFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val results: StateFlow<List<SearchResult>> = combine(_query, _filter) { q, f -> q to f }
        .debounce(300L)
        .flatMapLatest { (q, f) ->
            if (q.length < 2) flowOf(emptyList()) else searchFilesUseCase(q, f)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        _connectedProviders.value = indexRepository.getConnectedAccounts()
            .map { it.provider }
            .distinct()
    }

    fun onQueryChange(query: String) {
        _query.value = query
    }

    fun saveRecentSearch(query: String) {
        val trimmed = query.trim()
        if (trimmed.length < 2) return
        val updated = (listOf(trimmed) + appPreferences.recentSearches.filter { it != trimmed })
            .take(appPreferences.recentSearchLimit)
        appPreferences.recentSearches = updated
        _recentSearches.value = updated
    }

    fun updateFilter(filter: SearchFilter) {
        _filter.value = filter
    }

    fun openFile(file: CloudFile) {
        viewModelScope.launch {
            when (file.cloudProvider) {
                CloudProvider.DROPBOX -> {
                    _openingFile.value = true
                    try {
                        val url = dropboxConnector.getTemporaryLink(file.fileId, file.accountId)
                        _openUrl.emit(url)
                    } catch (e: Exception) {
                        val fallback = file.webUrl
                        if (fallback != null) _openUrl.emit(fallback)
                        else _errorMessage.emit("Datei konnte nicht geöffnet werden")
                    } finally {
                        _openingFile.value = false
                    }
                }
                CloudProvider.GOOGLE_DRIVE -> {
                    val url = file.webUrl ?: "https://drive.google.com/open?id=${file.fileId}"
                    _openUrl.emit(url)
                }
                else -> {
                    val url = file.webUrl
                    if (url != null) _openUrl.emit(url)
                    else _errorMessage.emit("Keine URL für diese Datei verfügbar")
                }
            }
        }
    }
}
