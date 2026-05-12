package com.example.markdownreader.ui.screens.reader

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.markdownreader.data.local.entity.BookEntity
import com.example.markdownreader.data.local.entity.BookmarkEntity
import com.example.markdownreader.data.local.entity.HighlightEntity
import com.example.markdownreader.data.repository.BookRepository
import com.example.markdownreader.data.repository.BookmarkRepository
import com.example.markdownreader.data.repository.HighlightRepository
import com.example.markdownreader.data.repository.ReadingProgressRepository
import com.example.markdownreader.ui.theme.ReadingTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Date
import javax.inject.Inject

@HiltViewModel
class ReaderViewModel @Inject constructor(
    private val bookRepository: BookRepository,
    private val bookmarkRepository: BookmarkRepository,
    private val highlightRepository: HighlightRepository,
    private val readingProgressRepository: ReadingProgressRepository
) : ViewModel() {

    private val _book = MutableStateFlow<BookEntity?>(null)
    val book: StateFlow<BookEntity?> = _book.asStateFlow()

    private val _content = MutableStateFlow("")
    val content: StateFlow<String> = _content.asStateFlow()

    private val _readingProgress = MutableStateFlow(0f)
    val readingProgress: StateFlow<Float> = _readingProgress.asStateFlow()

    private val _currentTheme = MutableStateFlow<ReadingTheme>(ReadingTheme.Paper)
    val currentTheme: StateFlow<ReadingTheme> = _currentTheme.asStateFlow()

    private val _fontSize = MutableStateFlow(16)
    val fontSize: StateFlow<Int> = _fontSize.asStateFlow()

    val bookmarks = MutableStateFlow<List<BookmarkEntity>>(emptyList())
    val highlights = MutableStateFlow<List<HighlightEntity>>(emptyList())

    private var readingStartTime: Long = 0
    private var startPosition: Int = 0

    fun loadBook(context: Context, bookId: Long) {
        viewModelScope.launch {
            // 加载书籍信息
            val bookEntity = bookRepository.getBookById(bookId)
            _book.value = bookEntity

            bookEntity?.let {
                // 加载内容
                val content = loadFileContent(context, it.filePath)
                _content.value = content

                // 恢复阅读进度
                _readingProgress.value = it.readingProgress

                // 加载书签和高亮
                bookmarkRepository.getBookmarksByBookId(bookId)
                    .collect { bookmarks.value = it }
            }

            highlightRepository.getHighlightsByBookId(bookId)
                .collect { highlights.value = it }

            // 开始计时
            readingStartTime = System.currentTimeMillis()
            startPosition = bookEntity?.currentPosition ?: 0
        }
    }

    fun updateReadingProgress(progress: Float) {
        _readingProgress.value = progress

        viewModelScope.launch {
            _book.value?.let { book ->
                val currentPosition = (progress * book.totalChars).toInt()
                bookRepository.updateReadingProgress(book.id, progress, currentPosition)
            }
        }
    }

    fun addBookmark(previewText: String, note: String? = null) {
        viewModelScope.launch {
            _book.value?.let { book ->
                val bookmark = BookmarkEntity(
                    bookId = book.id,
                    position = (readingProgress.value * book.totalChars).toInt(),
                    previewText = previewText.take(100),
                    note = note,
                    createTime = Date()
                )
                bookmarkRepository.addBookmark(bookmark)
            }
        }
    }

    fun deleteBookmark(bookmark: BookmarkEntity) {
        viewModelScope.launch {
            bookmarkRepository.deleteBookmark(bookmark)
        }
    }

    fun addHighlight(selectedText: String, color: androidx.compose.ui.graphics.Color) {
        viewModelScope.launch {
            _book.value?.let { book ->
                val content = _content.value
                val position = (readingProgress.value * book.totalChars).toInt()
                val startPos = content.indexOf(selectedText, position.coerceAtMost(content.length))

                if (startPos != -1) {
                    val highlight = HighlightEntity(
                        bookId = book.id,
                        startPosition = startPos,
                        endPosition = startPos + selectedText.length,
                        highlightedText = selectedText,
                        color = color.hashCode(),
                        createTime = Date()
                    )
                    highlightRepository.addHighlight(highlight)
                }
            }
        }
    }

    fun setTheme(theme: ReadingTheme) {
        _currentTheme.value = theme
    }

    fun setFontSize(size: Int) {
        _fontSize.value = size
    }

    private fun loadFileContent(context: Context, filePath: String): String {
        return try {
            val uri = Uri.parse(filePath)
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    reader.readText()
                }
            } ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    override fun onCleared() {
        super.onCleared()
        // 记录阅读时长
        viewModelScope.launch {
            _book.value?.let { book ->
                val endTime = System.currentTimeMillis()
                val minutesRead = ((endTime - readingStartTime) / 60000).toInt()
                val endPosition = (readingProgress.value * book.totalChars).toInt()
                val charsRead = (endPosition - startPosition).coerceAtLeast(0)

                if (minutesRead > 0 || charsRead > 0) {
                    readingProgressRepository.recordReading(book.id, charsRead, minutesRead)
                }
            }
        }
    }
}
