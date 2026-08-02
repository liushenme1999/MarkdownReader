package space.liushenme.markdownreader.ui.screens.bookshelf

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import space.liushenme.markdownreader.data.repository.ReaderSettingsRepository
import space.liushenme.markdownreader.model.BookshelfGridColumns
import space.liushenme.markdownreader.model.BookshelfLayoutMode
import space.liushenme.markdownreader.model.GitProjectRecentReadCount

@HiltViewModel
class BookshelfLayoutViewModel @Inject constructor(
    private val readerSettingsRepository: ReaderSettingsRepository,
) : ViewModel() {

    val layoutMode = readerSettingsRepository.bookshelfLayoutMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BookshelfLayoutMode.Grid)

    val gridColumns = readerSettingsRepository.bookshelfGridColumns
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BookshelfGridColumns.DEFAULT)

    val gitProjectRecentReadCount = readerSettingsRepository.gitProjectRecentReadCount
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            GitProjectRecentReadCount.DEFAULT,
        )

    fun setLayoutMode(mode: BookshelfLayoutMode) {
        viewModelScope.launch {
            readerSettingsRepository.setBookshelfLayoutMode(mode)
        }
    }

    fun setGridColumns(columns: Int) {
        viewModelScope.launch {
            readerSettingsRepository.setBookshelfGridColumns(columns)
        }
    }

    fun setGitProjectRecentReadCount(count: Int) {
        viewModelScope.launch {
            readerSettingsRepository.setGitProjectRecentReadCount(count)
        }
    }
}
