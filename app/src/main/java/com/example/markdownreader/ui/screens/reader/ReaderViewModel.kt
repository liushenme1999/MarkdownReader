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
import com.example.markdownreader.importing.ExtractedBookText
import com.example.markdownreader.importing.ImportedBookFormat
import com.example.markdownreader.importing.ParsedBookStorage
import com.example.markdownreader.importing.UrlBookDownloader
import com.example.markdownreader.markdown.MarkdownPreprocessor
import com.example.markdownreader.model.ReaderPageTurnMode
import com.example.markdownreader.ui.theme.ReadingTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
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

    /** 导入时写入的目录；非空时阅读页优先使用，不再从正文猜标题。 */
    private val _structuredToc = MutableStateFlow<List<MarkdownTocEntry>?>(null)
    val structuredToc: StateFlow<List<MarkdownTocEntry>?> = _structuredToc.asStateFlow()

    /** 每次 [loadBook] 完成正文/进度装载后递增，用于强制触发滚动恢复（避免与上次正文相同导致 StateFlow 不发射）。 */
    private val _readerLoadEpoch = MutableStateFlow(0L)
    val readerLoadEpoch: StateFlow<Long> = _readerLoadEpoch.asStateFlow()

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
    private var progressPersistJob: Job? = null

    fun loadBook(context: Context, bookId: Long) {
        loadBookJob?.cancel()
        bookmarkCollectJob?.cancel()
        highlightCollectJob?.cancel()

        _loadError.value = null
        bookmarks.value = emptyList()
        highlights.value = emptyList()
        _structuredToc.value = null

        loadBookJob = viewModelScope.launch {
            val bookEntity = bookRepository.getBookById(bookId)
            _book.value = bookEntity

            if (bookEntity == null) {
                _content.value = ""
                _loadError.value = "找不到该书，可能已被删除。"
                _readerLoadEpoch.value = _readerLoadEpoch.value + 1L
                return@launch
            }

            val extracted = withContext(Dispatchers.IO) {
                loadFileExtracted(context, bookEntity)
            }
            val text = MarkdownPreprocessor.stripLocalRelativeImages(extracted.body)
            _content.value = text
            _structuredToc.value = extracted.toc
                .map { MarkdownTocEntry(it.level, it.title, it.sourceOffset) }
                .takeIf { it.isNotEmpty() }
            _readingProgress.value = bookEntity.readingProgress
            if (text.isNotEmpty() && text.length != bookEntity.totalChars) {
                val synced = bookEntity.copy(totalChars = text.length)
                bookRepository.updateBook(synced)
                _book.value = synced
            }
            _readerLoadEpoch.value = _readerLoadEpoch.value + 1L

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

    /** 按全书源码字符下标更新进度（与书签/目录跳转坐标一致）。 */
    fun updateReadingProgressAtChar(globalChar: Int) {
        val contentLen = _content.value.length
        if (contentLen <= 0) return
        val pos = globalChar.coerceIn(0, contentLen)
        val progress = pos.toFloat() / contentLen
        _readingProgress.value = progress
        scheduleProgressPersist(progress, pos)
    }

    fun updateReadingProgressAtCharNow(globalChar: Int) {
        val contentLen = _content.value.length
        if (contentLen <= 0) return
        val pos = globalChar.coerceIn(0, contentLen)
        val progress = pos.toFloat() / contentLen
        _readingProgress.value = progress
        progressPersistJob?.cancel()
        progressPersistJob = null
        viewModelScope.launch {
            _book.value?.let { book ->
                bookRepository.updateReadingProgress(book.id, progress, pos)
            }
        }
    }

    fun updateReadingProgress(progress: Float) {
        val contentLen = _content.value.length.coerceAtLeast(1)
        updateReadingProgressAtChar((progress * contentLen).toInt())
    }

    private fun scheduleProgressPersist(progress: Float, position: Int) {
        progressPersistJob?.cancel()
        progressPersistJob = viewModelScope.launch {
            delay(300)
            _book.value?.let { book ->
                bookRepository.updateReadingProgress(book.id, progress, position)
            }
        }
    }

    private suspend fun flushReadingProgressNow() {
        progressPersistJob?.cancel()
        progressPersistJob = null
        val book = _book.value ?: return
        val contentLen = _content.value.length.coerceAtLeast(1)
        val pos = (_readingProgress.value * contentLen).toInt().coerceIn(0, contentLen)
        val progress = pos.toFloat() / contentLen
        bookRepository.updateReadingProgress(book.id, progress, pos)
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
     * @param positionForAdd 当前视口顶部在全书正文中的字符下标；传入后不再依赖可能滞后的 readingProgress。
     */
    suspend fun toggleBookmarkAtSwipe(
        previewForAdd: String? = null,
        positionForAdd: Int? = null,
    ): Boolean? {
        val book = _book.value ?: return null
        val total = book.totalChars.coerceAtLeast(1)
        val raw = _content.value
        if (raw.isEmpty()) return null

        val pos = (positionForAdd ?: (readingProgress.value * total).toInt()).coerceIn(0, raw.length)
        val progress = pos.toFloat() / raw.length.coerceAtLeast(1)
        _readingProgress.value = progress
        scheduleProgressPersist(progress, pos)
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

    private fun loadFileExtracted(context: Context, book: BookEntity): ExtractedBookText {
        val bundlePath = book.parsedBundlePath
        if (!bundlePath.isNullOrBlank()) {
            val dir = File(bundlePath)
            ParsedBookStorage.readBundle(dir)
                ?.takeIf { it.body.isNotEmpty() }
                ?.let { return it }
        }
        if (ImportedBookFormat.isRemovedStoredKey(book.importFormat)) {
            return ExtractedBookText.plainBody(
                BookContentLoader.removedFormatPlaceholder(book.importFormat)
            )
        }
        val path = book.filePath
        val format = ImportedBookFormat.fromStored(book.importFormat)
        return try {
            if (path.startsWith("http://", ignoreCase = true) ||
                path.startsWith("https://", ignoreCase = true)
            ) {
                val result = UrlBookDownloader.download(path)
                BookContentLoader.loadExtractedFromUrlBytes(
                    result.bytes,
                    format,
                    result.charsetFromHeader
                )
            } else {
                val uri = Uri.parse(path)
                BookContentLoader.loadExtractedFromUri(context, uri, format)
            }
        } catch (_: Exception) {
            ExtractedBookText.plainBody("")
        }
    }

    override fun onCleared() {
        val book = _book.value
        if (book != null) {
            // onCleared 调用时 viewModelScope 已被取消，必须用 runBlocking 同步落盘，
            // 否则在此处 launch 出来的协程会被立即取消，进度与阅读时长会丢失。
            val endTime = System.currentTimeMillis()
            val minutesRead = ((endTime - readingStartTime) / 60_000).toInt()
            val contentLen = _content.value.length.coerceAtLeast(1)
            val endPosition = (_readingProgress.value * contentLen).toInt().coerceIn(0, contentLen)
            val charsRead = (endPosition - startPosition).coerceAtLeast(0)
            runBlocking {
                flushReadingProgressNow()
                if (minutesRead > 0 || charsRead > 0) {
                    readingProgressRepository.recordReading(book.id, charsRead, minutesRead)
                }
            }
        }
        super.onCleared()
    }
}
