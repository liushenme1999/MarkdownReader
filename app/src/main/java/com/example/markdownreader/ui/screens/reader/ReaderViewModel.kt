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
import com.example.markdownreader.data.repository.ReaderSettingsRepository
import com.example.markdownreader.importing.BookContentLoader
import com.example.markdownreader.importing.ImportedBookFormat
import com.example.markdownreader.importing.UrlBookDownloader
import com.example.markdownreader.model.ReaderPageTurnMode
import com.example.markdownreader.ui.theme.ReadingTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.roundToInt

@HiltViewModel
class ReaderViewModel @Inject constructor(
    private val bookRepository: BookRepository,
    private val bookmarkRepository: BookmarkRepository,
    private val highlightRepository: HighlightRepository,
    private val readingProgressRepository: ReadingProgressRepository,
    private val readerSettingsRepository: ReaderSettingsRepository
) : ViewModel() {

    private val _book = MutableStateFlow<BookEntity?>(null)
    val book: StateFlow<BookEntity?> = _book.asStateFlow()

    private val _content = MutableStateFlow("")
    val content: StateFlow<String> = _content.asStateFlow()

    private val _readingProgress = MutableStateFlow(0f)
    val readingProgress: StateFlow<Float> = _readingProgress.asStateFlow()

    val currentTheme: StateFlow<ReadingTheme> = readerSettingsRepository.readingTheme
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ReadingTheme.Paper
        )

    val fontSize: StateFlow<Int> = readerSettingsRepository.fontSize
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 16
        )

    /** 正文四周边距（dp），用于 TextView padding。 */
    val readerPaddingDp: StateFlow<Int> = readerSettingsRepository.readerPaddingDp
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 32
        )

    /** 行距倍数，对应 [android.widget.TextView.setLineSpacing] 的 multiplier。 */
    val readerLineSpacingMultiplier: StateFlow<Float> =
        readerSettingsRepository.readerLineSpacingMultiplier
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = 1.5f
            )

    val pageTurnMode: StateFlow<ReaderPageTurnMode> = readerSettingsRepository.pageTurnMode
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ReaderPageTurnMode.VerticalScroll
        )

    private val _loadError = MutableStateFlow<String?>(null)
    val loadError: StateFlow<String?> = _loadError.asStateFlow()

    val bookmarks = MutableStateFlow<List<BookmarkEntity>>(emptyList())
    val highlights = MutableStateFlow<List<HighlightEntity>>(emptyList())

    private var readingStartTime: Long = 0
    private var startPosition: Int = 0

    private var loadBookJob: Job? = null
    private var bookmarkCollectJob: Job? = null
    private var highlightCollectJob: Job? = null

    fun loadBook(context: Context, bookId: Long) {
        loadBookJob?.cancel()
        bookmarkCollectJob?.cancel()
        highlightCollectJob?.cancel()

        _loadError.value = null
        bookmarks.value = emptyList()
        highlights.value = emptyList()

        loadBookJob = viewModelScope.launch {
            val bookEntity = bookRepository.getBookById(bookId)
            _book.value = bookEntity

            if (bookEntity == null) {
                _content.value = ""
                _loadError.value = "找不到该书，可能已被删除。"
                return@launch
            }

            val text = withContext(Dispatchers.IO) {
                loadFileContent(context, bookEntity)
            }
            _content.value = text
            _readingProgress.value = bookEntity.readingProgress

            if (text.isEmpty()) {
                _loadError.value =
                    "无法读取正文（文件权限失效或路径无效）。请返回书架删除该书后，使用「导入」重新选择文件。"
            }

            readingStartTime = System.currentTimeMillis()
            startPosition = bookEntity.currentPosition

            bookmarkCollectJob = viewModelScope.launch {
                bookmarkRepository.getBookmarksByBookId(bookId)
                    .collect { bookmarks.value = it }
            }
            highlightCollectJob = viewModelScope.launch {
                highlightRepository.getHighlightsByBookId(bookId)
                    .collect { highlights.value = it }
            }
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

    /**
     * 右滑 / 分页模式下拉：当前阅读位置附近已有书签则删除，否则添加。
     * @param previewForAdd 添加时用于书签列表的预览文案；非空则优先使用（一般为屏幕顶部可见文字），否则按源码位置估算。
     */
    suspend fun toggleBookmarkAtSwipe(previewForAdd: String? = null): Boolean? {
        val book = _book.value ?: return null
        val total = book.totalChars.coerceAtLeast(1)
        val raw = _content.value
        if (raw.isEmpty()) return null

        val pos = (readingProgress.value * total).toInt().coerceIn(0, total)
        val window = (total / 40).coerceIn(300, 1500)
        val near = bookmarks.value.find { abs(it.position - pos) <= window }

        return if (near != null) {
            bookmarkRepository.deleteBookmark(near)
            false
        } else {
            val preview = previewForAdd
                ?.replace('\n', ' ')
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: run {
                    val from = (pos - 60).coerceAtLeast(0)
                    val to = (pos + 80).coerceAtMost(raw.length)
                    raw.substring(from, to)
                        .replace('\n', ' ')
                        .trim()
                        .ifEmpty { "书签" }
                        .take(100)
                }
            bookmarkRepository.addBookmark(
                BookmarkEntity(
                    bookId = book.id,
                    position = pos,
                    previewText = preview,
                    note = null,
                    createTime = Date()
                )
            )
            true
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
        viewModelScope.launch {
            readerSettingsRepository.setReadingTheme(theme)
        }
    }

    fun setFontSize(size: Int) {
        viewModelScope.launch {
            readerSettingsRepository.setFontSize(size.coerceIn(10, 40))
        }
    }

    fun setReaderPaddingDp(dp: Int) {
        viewModelScope.launch {
            readerSettingsRepository.setReaderPaddingDp(dp.coerceIn(8, 56))
        }
    }

    fun setReaderLineSpacingMultiplier(mult: Float) {
        val snapped = (mult * 20f).roundToInt() / 20f
        viewModelScope.launch {
            readerSettingsRepository.setReaderLineSpacingMultiplier(snapped.coerceIn(1f, 2.5f))
        }
    }

    fun setPageTurnMode(mode: ReaderPageTurnMode) {
        viewModelScope.launch {
            readerSettingsRepository.setPageTurnMode(mode)
        }
    }

    private fun loadFileContent(context: Context, book: BookEntity): String {
        val path = book.filePath
        val format = ImportedBookFormat.fromStored(book.importFormat)
        return try {
            if (path.startsWith("http://", ignoreCase = true) ||
                path.startsWith("https://", ignoreCase = true)
            ) {
                val result = UrlBookDownloader.download(path)
                BookContentLoader.loadFromUrlBytes(
                    result.bytes,
                    format,
                    result.charsetFromHeader
                )
            } else {
                val uri = Uri.parse(path)
                BookContentLoader.loadFromUri(context, uri, format)
            }
        } catch (_: Exception) {
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
