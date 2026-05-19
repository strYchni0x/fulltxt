package me.fulltxt.app.ui.indexing

import androidx.lifecycle.ViewModel
import me.fulltxt.app.domain.usecase.IndexFilesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class IndexingViewModel @Inject constructor(
    private val indexFilesUseCase: IndexFilesUseCase
) : ViewModel() {

    private val _isIndexing = MutableStateFlow(false)
    val isIndexing: StateFlow<Boolean> = _isIndexing.asStateFlow()
}
