package space.liushenme.markdownreader.ui.screens.notes

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import space.liushenme.markdownreader.R
import space.liushenme.markdownreader.data.local.entity.BookmarkEntity
import space.liushenme.markdownreader.data.local.entity.HighlightEntity
import space.liushenme.markdownreader.data.repository.BookRepository
import space.liushenme.markdownreader.data.repository.BookmarkRepository
import space.liushenme.markdownreader.data.repository.HighlightRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NotesViewModel @Inject constructor(
    private val highlightRepository: HighlightRepository,
    private val bookmarkRepository: BookmarkRepository,
    private val bookRepository: BookRepository,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val _allHighlights = MutableStateFlow<List<HighlightWithBook>>(emptyList())
    val allHighlights: StateFlow<List<HighlightWithBook>> = _allHighlights.asStateFlow()

    private val _allBookmarks = MutableStateFlow<List<BookmarkWithBook>>(emptyList())
    val allBookmarks: StateFlow<List<BookmarkWithBook>> = _allBookmarks.asStateFlow()

    init {
        loadAllNotes()
    }

    private fun loadAllNotes() {
        viewModelScope.launch {
            // 加载所有高亮
            highlightRepository.getAllHighlights()
                .combine(bookRepository.getAllBooks()) { highlights, books ->
                    highlights.map { highlight ->
                        val book = books.find { it.id == highlight.bookId }
                        HighlightWithBook(
                            highlight = highlight,
                            bookTitle = book?.title ?: appContext.getString(R.string.unknown_book)
                        )
                    }
                }
                .collect { _allHighlights.value = it }
        }

        viewModelScope.launch {
            // 加载所有书签
            bookmarkRepository.getAllBookmarks()
                .combine(bookRepository.getAllBooks()) { bookmarks, books ->
                    bookmarks.map { bookmark ->
                        val book = books.find { it.id == bookmark.bookId }
                        BookmarkWithBook(
                            bookmark = bookmark,
                            bookTitle = book?.title ?: appContext.getString(R.string.unknown_book)
                        )
                    }
                }
                .collect { _allBookmarks.value = it }
        }
    }

    fun deleteHighlight(highlight: HighlightEntity) {
        viewModelScope.launch {
            highlightRepository.deleteHighlight(highlight)
        }
    }

    fun deleteBookmark(bookmark: BookmarkEntity) {
        viewModelScope.launch {
            bookmarkRepository.deleteBookmark(bookmark)
        }
    }

    fun exportToMarkdown(): String {
        val sb = StringBuilder()
        sb.appendLine(appContext.getString(R.string.export_md_title))
        sb.appendLine()
        sb.appendLine(appContext.getString(R.string.export_md_time, java.util.Date().toString()))
        sb.appendLine()

        // 导出划线笔记
        sb.appendLine(appContext.getString(R.string.export_md_highlights_section))
        sb.appendLine()
        _allHighlights.value.forEach { item ->
            sb.appendLine("### ${item.bookTitle}")
            sb.appendLine("> ${item.highlight.highlightedText}")
            if (!item.highlight.note.isNullOrEmpty()) {
                sb.appendLine()
                sb.appendLine(
                    appContext.getString(R.string.export_md_note_label, item.highlight.note)
                )
            }
            sb.appendLine()
        }

        // 导出书签
        sb.appendLine(appContext.getString(R.string.export_md_bookmarks_section))
        sb.appendLine()
        _allBookmarks.value.forEach { item ->
            sb.appendLine("### ${item.bookTitle}")
            sb.appendLine("> ${item.bookmark.previewText}")
            if (!item.bookmark.note.isNullOrEmpty()) {
                sb.appendLine()
                sb.appendLine(
                    appContext.getString(R.string.export_md_note_label, item.bookmark.note)
                )
            }
            sb.appendLine()
        }

        return sb.toString()
    }

    fun exportToJson(): String {
        val exportData = ExportData(
            exportTime = java.util.Date().toString(),
            highlights = _allHighlights.value.map {
                HighlightExport(
                    bookTitle = it.bookTitle,
                    text = it.highlight.highlightedText,
                    note = it.highlight.note,
                    createTime = it.highlight.createTime.toString()
                )
            },
            bookmarks = _allBookmarks.value.map {
                BookmarkExport(
                    bookTitle = it.bookTitle,
                    preview = it.bookmark.previewText,
                    note = it.bookmark.note,
                    createTime = it.bookmark.createTime.toString()
                )
            }
        )
        return com.google.gson.Gson().toJson(exportData)
    }

    data class ExportData(
        val exportTime: String,
        val highlights: List<HighlightExport>,
        val bookmarks: List<BookmarkExport>
    )

    data class HighlightExport(
        val bookTitle: String,
        val text: String,
        val note: String?,
        val createTime: String
    )

    data class BookmarkExport(
        val bookTitle: String,
        val preview: String,
        val note: String?,
        val createTime: String
    )
}
