package me.fulltxt.app.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import me.fulltxt.app.data.cloud.dropbox.DropboxConnector
import me.fulltxt.app.domain.model.CloudFile
import me.fulltxt.app.domain.model.CloudProvider
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
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchFilesUseCase: SearchFilesUseCase,
    private val dropboxConnector: DropboxConnector
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _openingFile = MutableStateFlow(false)
    val openingFile: StateFlow<Boolean> = _openingFile.asStateFlow()

    private val _openUrl = MutableSharedFlow<String>()
    val openUrl = _openUrl.asSharedFlow()

    private val _errorMessage = MutableSharedFlow<String>()
    val errorMessage = _errorMessage.asSharedFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val results: StateFlow<List<SearchResult>> = _query
        .debounce(300L)
        .flatMapLatest { q -> if (q.length < 2) flowOf(emptyList()) else searchFilesUseCase(q) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onQueryChange(query: String) {
        _query.value = query
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
                        // Fall back to webUrl if temp link fails
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
