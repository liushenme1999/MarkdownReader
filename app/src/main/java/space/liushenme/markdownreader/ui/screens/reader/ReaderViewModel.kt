package space.liushenme.markdownreader.ui.screens.reader

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import space.liushenme.markdownreader.R
import space.liushenme.markdownreader.data.backup.BookContentSync
import space.liushenme.markdownreader.data.local.entity.BookEntity
import space.liushenme.markdownreader.data.local.entity.BookmarkEntity
import space.liushenme.markdownreader.data.local.entity.HighlightEntity
import space.liushenme.markdownreader.data.repository.BookRepository
import space.liushenme.markdownreader.data.repository.BookmarkRepository
import space.liushenme.markdownreader.data.repository.HighlightRepository
import space.liushenme.markdownreader.data.repository.ReadingProgressRepository
import space.liushenme.markdownreader.data.repository.ReadingSessionStats
import space.liushenme.markdownreader.data.repository.ReaderSettingsRepository
import space.liushenme.markdownreader.importing.BookContentLoader
import space.liushenme.markdownreader.importing.ExtractedBookText
import space.liushenme.markdownreader.importing.ImportedBookFormat
import space.liushenme.markdownreader.importing.ParsedBookStorage
import space.liushenme.markdownreader.importing.UrlBookDownloader
import space.liushenme.markdownreader.markdown.MarkdownInlineHtml
import space.liushenme.markdownreader.markdown.MarkdownPreprocessor
import space.liushenme.markdownreader.model.HighlightStyle
import space.liushenme.markdownreader.model.ReaderPageTurnMode
import space.liushenme.markdownreader.ui.theme.ReadingTheme
import androidx.compose.ui.graphics.toArgb
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
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
    @ApplicationContext private val appContext: Context,
    private val bookRepository: BookRepository,
    private val bookmarkRepository: BookmarkRepository,
    private val highlightRepository: HighlightRepository,
    private val readingProgressRepository: ReadingProgressRepository,
    private val readerSettingsRepository: ReaderSettingsRepository,
    private val bookContentSync: BookContentSync,
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

    val lastHighlightColorArgb: StateFlow<Int> =
        readerSettingsRepository.lastHighlightColorArgb
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = ReaderSettingsRepository.DEFAULT_HIGHLIGHT_COLOR_ARGB,
            )

    val lastHighlightStyle: StateFlow<HighlightStyle> =
        readerSettingsRepository.lastHighlightStyle
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = HighlightStyle.DEFAULT,
            )

    private val _loadError = MutableStateFlow<String?>(null)
    val loadError: StateFlow<String?> = _loadError.asStateFlow()

    val bookmarks = MutableStateFlow<List<BookmarkEntity>>(emptyList())
    val highlights = MutableStateFlow<List<HighlightEntity>>(emptyList())

    /** 当前前台计时片段起点；0 表示未在计时（已暂停或未开始）。 */
    private var sessionSegmentStartMs: Long = 0L
    /** 当前计时片段开始时的字符位置，用于估算本片段前进字数。 */
    private var sessionSegmentStartCharPos: Int = 0
    /** 阅读页是否处于前台（ON_RESUME～ON_PAUSE）。 */
    private var readingForeground: Boolean = false
    /** 最近一次按视口顶部更新的全书字符下标，退出时优先落盘，避免 float 反算偏差。 */
    private var lastKnownReadingCharPos: Int = 0
    /** 与书签 previewText 同格式的视口顶预览，打开书时与书签跳转共用定位。 */
    private var lastKnownProgressPreview: String = ""

    private var loadBookJob: Job? = null
    private var bookmarkCollectJob: Job? = null
    private var highlightCollectJob: Job? = null
    private var progressPersistJob: Job? = null
    /** 当前 ViewModel 会话内已成功装载的正文 bookId；WebLink 返回等同书时不重复 load。 */
    private var loadedBookId: Long? = null

    fun loadBook(context: Context, bookId: Long) {
        if (loadedBookId == bookId && _content.value.isNotEmpty() && _book.value?.id == bookId) {
            readerOpenDbg("loadBook skip cached bookId=$bookId contentLen=${_content.value.length}")
            return
        }
        loadedBookId = bookId
        val t0 = android.os.SystemClock.uptimeMillis()
        readerOpenDbg("loadBook start bookId=$bookId")

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
                loadedBookId = null
                _content.value = ""
                _loadError.value = appContext.getString(R.string.reader_error_book_not_found)
                _readerLoadEpoch.value = _readerLoadEpoch.value + 1L
                readerOpenDbg("loadBook missing bookId=$bookId +${android.os.SystemClock.uptimeMillis() - t0}ms")
                return@launch
            }

            val extracted = withContext(Dispatchers.IO) {
                loadFileExtracted(context, bookEntity)
            }
            // 打开时可能已从 WebDAV 补齐正文并改写路径，重新取最新书籍记录
            val latestBook = bookRepository.getBookById(bookId) ?: bookEntity
            _book.value = latestBook
            val text = MarkdownPreprocessor.stripLocalRelativeImages(extracted.body)
            _content.value = text
            _structuredToc.value = extracted.toc
                .map {
                    MarkdownTocEntry(
                        level = it.level,
                        title = it.title,
                        sourceOffset = it.sourceOffset,
                        rawTitle = it.rawTitle,
                    )
                }
                .takeIf { it.isNotEmpty() }
            _readingProgress.value = latestBook.readingProgress
            lastKnownReadingCharPos = resolveStoredCharPos(
                currentPosition = latestBook.currentPosition,
                readingProgress = latestBook.readingProgress,
                contentLength = text.length,
                totalChars = latestBook.totalChars.coerceAtLeast(text.length.coerceAtLeast(1)),
            )
            lastKnownProgressPreview = latestBook.progressPreviewText.trim()
            if (text.isNotEmpty() && text.length != latestBook.totalChars) {
                val synced = latestBook.copy(totalChars = text.length)
                bookRepository.updateBook(synced)
                _book.value = synced
            }
            _readerLoadEpoch.value = _readerLoadEpoch.value + 1L
            readerOpenDbg(
                "loadBook contentReady len=${text.length} pos=$lastKnownReadingCharPos " +
                    "toc=${_structuredToc.value?.size ?: 0} epoch=${_readerLoadEpoch.value} " +
                    "+${android.os.SystemClock.uptimeMillis() - t0}ms",
            )

            if (text.isEmpty()) {
                _loadError.value = appContext.getString(R.string.reader_error_cannot_read_body)
            }

            beginSessionSegmentIfNeeded()

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

    /** 阅读页进入前台：开始（或继续）本段阅读计时。 */
    fun onReadingResumed() {
        readingForeground = true
        beginSessionSegmentIfNeeded()
    }

    /**
     * 阅读页进入后台 / 打开外链等：落盘本段时长与字数，并暂停计时，避免后台时间灌水。
     * 与进度落盘一并在 ON_PAUSE 调用，进程被杀前尽量保住统计。
     */
    fun onReadingPaused() {
        readingForeground = false
        flushSessionSegmentBlocking()
    }

    private fun beginSessionSegmentIfNeeded() {
        if (!readingForeground) return
        if (_book.value == null || _content.value.isEmpty()) return
        if (sessionSegmentStartMs > 0L) return
        sessionSegmentStartMs = System.currentTimeMillis()
        sessionSegmentStartCharPos = lastKnownReadingCharPos
    }

    private fun flushSessionSegmentBlocking() {
        val book = _book.value ?: return
        val segmentStart = sessionSegmentStartMs
        if (segmentStart <= 0L) return
        sessionSegmentStartMs = 0L

        val elapsed = (System.currentTimeMillis() - segmentStart).coerceAtLeast(0L)
        val minutesRead = ReadingSessionStats.elapsedMillisToMinutes(elapsed)
        val contentLen = _content.value.length.coerceAtLeast(1)
        val endPosition = lastKnownReadingCharPos.coerceIn(0, contentLen)
        val charsRead = (endPosition - sessionSegmentStartCharPos).coerceAtLeast(0)
        sessionSegmentStartCharPos = endPosition

        if (minutesRead > 0 || charsRead > 0) {
            runBlocking {
                readingProgressRepository.recordReading(book.id, charsRead, minutesRead)
            }
        }
    }

    /** 仅刷新界面进度，不立刻落盘（滚动时实时更新标题栏百分比）。 */
    fun updateVisibleReadingProgress(globalChar: Int) {
        val contentLen = _content.value.length
        if (contentLen <= 0) return
        val pos = globalChar.coerceIn(0, contentLen)
        lastKnownReadingCharPos = pos
        val progress = readingProgressForCharPos(pos, contentLen)
        if ((_readingProgress.value * 100).toInt() == (progress * 100).toInt()) return
        _readingProgress.value = progress
    }

    /** 按全书源码字符下标更新进度（与书签记录字段一致：position + preview）。 */
    fun updateReadingProgressAtChar(globalChar: Int, previewText: String? = null) {
        val contentLen = _content.value.length
        if (contentLen <= 0) return
        val pos = globalChar.coerceIn(0, contentLen)
        lastKnownReadingCharPos = pos
        previewText?.let { lastKnownProgressPreview = normalizeReadingPreviewText(it) }
        val progress = readingProgressForCharPos(pos, contentLen)
        _readingProgress.value = progress
        scheduleProgressPersist(progress, pos, lastKnownProgressPreview)
    }

    fun updateReadingProgressAtCharNow(globalChar: Int, previewText: String? = null) {
        val contentLen = _content.value.length
        if (contentLen <= 0) return
        val pos = globalChar.coerceIn(0, contentLen)
        lastKnownReadingCharPos = pos
        previewText?.let { lastKnownProgressPreview = normalizeReadingPreviewText(it) }
        val progress = pos.toFloat() / contentLen
        _readingProgress.value = progress
        progressPersistJob?.cancel()
        progressPersistJob = null
        viewModelScope.launch {
            persistReadingProgress(progress, pos, lastKnownProgressPreview)
        }
    }

    /** 窗口/翻页恢复时优先用会话内视口锚点，避免 [_book.currentPosition] 滞后于内存进度。 */
    fun readingCharPosForRestore(): Int {
        val contentLen = _content.value.length
        if (contentLen <= 0) return 0
        return lastKnownReadingCharPos.coerceIn(0, contentLen)
    }

    /** 打开书恢复用的视口顶预览（与书签 previewText 同格式）。 */
    fun readingPreviewForRestore(): String? =
        lastKnownProgressPreview.takeIf { it.isNotBlank() }

    /** 退出阅读页时同步落盘，避免 ON_PAUSE 异步写入未完成。 */
    fun persistReadingPositionBlocking(globalChar: Int, previewText: String? = null) {
        val contentLen = _content.value.length
        if (contentLen <= 0) return
        val pos = globalChar.coerceIn(0, contentLen)
        lastKnownReadingCharPos = pos
        previewText?.let { lastKnownProgressPreview = normalizeReadingPreviewText(it) }
        val progress = pos.toFloat() / contentLen
        _readingProgress.value = progress
        progressPersistJob?.cancel()
        progressPersistJob = null
        runBlocking {
            persistReadingProgress(progress, pos, lastKnownProgressPreview)
        }
    }

    fun updateReadingProgress(progress: Float) {
        val contentLen = _content.value.length.coerceAtLeast(1)
        updateReadingProgressAtChar((progress * contentLen).toInt())
    }

    private fun scheduleProgressPersist(progress: Float, position: Int, previewText: String) {
        progressPersistJob?.cancel()
        progressPersistJob = viewModelScope.launch {
            delay(300)
            persistReadingProgress(progress, position, previewText)
        }
    }

    private suspend fun persistReadingProgress(
        progress: Float,
        position: Int,
        previewText: String,
    ) {
        val book = _book.value ?: return
        bookRepository.updateReadingProgress(book.id, progress, position, previewText)
        // 勿同步更新 _book.currentPosition：ReaderScreen 监听该字段会重算窗口并触发滚动恢复，导致阅读中跳动。
    }

    private suspend fun flushReadingProgressNow() {
        progressPersistJob?.cancel()
        progressPersistJob = null
        val contentLen = _content.value.length.coerceAtLeast(1)
        val pos = lastKnownReadingCharPos.coerceIn(0, contentLen)
        val progress = readingProgressForCharPos(pos, contentLen)
        _readingProgress.value = progress
        persistReadingProgress(progress, pos, lastKnownProgressPreview)
    }

    fun addBookmark(previewText: String, note: String? = null) {
        viewModelScope.launch {
            _book.value?.let { book ->
                val contentLen = _content.value.length.coerceAtLeast(1)
                val pos = lastKnownReadingCharPos.coerceIn(0, contentLen)
                val bookmark = BookmarkEntity(
                    bookId = book.id,
                    position = pos,
                    previewText = normalizeReadingPreviewText(previewText)
                        .ifEmpty { appContext.getString(R.string.bookmark_default_preview) },
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

        val pos = (positionForAdd ?: lastKnownReadingCharPos).coerceIn(0, raw.length)
        val bookmarkFallback = appContext.getString(R.string.bookmark_default_preview)
        val preview = normalizeReadingPreviewText(previewForAdd).ifEmpty {
            val from = (pos - 60).coerceAtLeast(0)
            val to = (pos + 80).coerceAtMost(raw.length)
            normalizeReadingPreviewText(
                raw.substring(from, to).ifEmpty { bookmarkFallback },
            ).ifEmpty { bookmarkFallback }
        }
        lastKnownReadingCharPos = pos
        lastKnownProgressPreview = preview
        val progress = pos.toFloat() / raw.length.coerceAtLeast(1)
        _readingProgress.value = progress
        scheduleProgressPersist(progress, pos, preview)
        val window = (total / 40).coerceIn(300, 1500)
        val near = bookmarks.value.find { abs(it.position - pos) <= window }

        return if (near != null) {
            bookmarkRepository.deleteBookmark(near)
            false
        } else {
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

    /**
     * 立即落库一条划线并返回 id；失败返回 null。
     * 用于点「划线」后立刻按当前样式渲染，再在浮窗里改色/样式时 [updateHighlightAppearance]。
     *
     * 使用 [NonCancellable]：Compose 里点划线后的 LaunchedEffect 会在取消选区时被取消，
     * 若不防护，Room 写入会被中断，表现为松手后划线消失、重进也没有。
     */
    suspend fun addHighlightNow(
        selectedText: String,
        color: androidx.compose.ui.graphics.Color,
        style: HighlightStyle = lastHighlightStyle.value,
        sourceStartHint: Int = lastKnownReadingCharPos,
    ): Long? = withContext(NonCancellable) {
        if (selectedText.isBlank()) return@withContext null
        val book = _book.value ?: return@withContext null
        val content = _content.value
        if (content.isEmpty()) return@withContext null
        val hint = sourceStartHint.coerceIn(0, content.length)
        // 源码中找不到完全匹配时仍按 hint 落库，渲染侧靠 highlightedText 在展示层匹配
        val startPos = resolveHighlightSourceStart(content, selectedText, hint)
            ?: hint.coerceIn(0, (content.length - selectedText.length).coerceAtLeast(0))
        val endPos = (startPos + selectedText.length).coerceAtMost(content.length)
        if (endPos <= startPos) return@withContext null
        // 同一区域已有划线则复用，避免连点「划线」重复插入
        highlights.value.firstOrNull {
            it.startPosition == startPos && it.endPosition == endPos
        }?.let { return@withContext it.id }
        val colorArgb = highlightColorArgb(color)
        val highlight = HighlightEntity(
            bookId = book.id,
            startPosition = startPos,
            endPosition = endPos,
            highlightedText = selectedText,
            color = colorArgb,
            style = style.storageKey,
            createTime = Date(),
        )
        val id = highlightRepository.addHighlight(highlight)
        readerSettingsRepository.setLastHighlightPreference(colorArgb, style)
        // Flow 可能略滞后：乐观写入，保证浮窗打开瞬间就能看到划线
        val saved = highlight.copy(id = id)
        val current = highlights.value
        if (current.none { it.id == id }) {
            highlights.value = listOf(saved) + current
        }
        id
    }

    /**
     * 在 viewModelScope 落库，不受 Compose 取消选区影响；结果通过 [onResult] 回传。
     */
    fun addHighlightFromSelection(
        selectedText: String,
        color: androidx.compose.ui.graphics.Color,
        style: HighlightStyle,
        sourceStartHint: Int,
        onResult: (Long?) -> Unit = {},
    ) {
        viewModelScope.launch {
            val id = addHighlightNow(
                selectedText = selectedText,
                color = color,
                style = style,
                sourceStartHint = sourceStartHint,
            )
            onResult(id)
        }
    }

    fun updateHighlightAppearance(
        highlightId: Long,
        color: androidx.compose.ui.graphics.Color,
        style: HighlightStyle,
    ) {
        if (highlightId <= 0L) return
        viewModelScope.launch {
            val existing = highlights.value.find { it.id == highlightId } ?: return@launch
            val colorArgb = highlightColorArgb(color)
            val updated = existing.copy(color = colorArgb, style = style.storageKey)
            // 先乐观更新 UI，再落库
            highlights.value = highlights.value.map { if (it.id == highlightId) updated else it }
            highlightRepository.updateHighlight(updated)
            readerSettingsRepository.setLastHighlightPreference(colorArgb, style)
        }
    }

    fun deleteHighlight(highlight: HighlightEntity) {
        viewModelScope.launch {
            // 先乐观移除，菜单/正文立刻变为未划线
            highlights.value = highlights.value.filterNot { it.id == highlight.id }
            highlightRepository.deleteHighlight(highlight)
        }
    }

    private fun highlightColorArgb(color: androidx.compose.ui.graphics.Color): Int =
        if (color.alpha < 0.06f) {
            ReaderSettingsRepository.DEFAULT_HIGHLIGHT_COLOR_ARGB
        } else {
            color.toArgb()
        }

    /**
     * 在全书源码中定位划线起点：优先命中 [hint]，否则取距 hint 最近的一处匹配。
     */
    private fun resolveHighlightSourceStart(
        content: String,
        selectedText: String,
        hint: Int,
    ): Int? {
        if (selectedText.isEmpty() || content.isEmpty()) return null
        val safeHint = hint.coerceIn(0, content.length)
        if (safeHint + selectedText.length <= content.length &&
            content.regionMatches(safeHint, selectedText, 0, selectedText.length)
        ) {
            return safeHint
        }
        var best = -1
        var bestDist = Int.MAX_VALUE
        var from = 0
        while (from <= content.length - selectedText.length) {
            val idx = content.indexOf(selectedText, from)
            if (idx < 0) break
            val dist = abs(idx - safeHint)
            if (dist < bestDist) {
                bestDist = dist
                best = idx
            }
            from = idx + 1
        }
        return best.takeIf { it >= 0 }
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

    private suspend fun loadFileExtracted(context: Context, book: BookEntity): ExtractedBookText {
        val canonical = ParsedBookStorage.bundleDir(appContext, book.id)
        ParsedBookStorage.readBundle(canonical)
            ?.takeIf { it.body.isNotEmpty() }
            ?.let {
                persistCanonicalPathsIfNeeded(book, canonical)
                return it
            }

        val bundlePath = book.parsedBundlePath
        if (!bundlePath.isNullOrBlank()) {
            val dir = File(bundlePath)
            if (dir.absolutePath != canonical.absolutePath) {
                ParsedBookStorage.readBundle(dir)
                    ?.takeIf { it.body.isNotEmpty() }
                    ?.let { return it }
            }
        }

        // 恢复后正文可能仅在 WebDAV books/{id}.zip，打开时再补拉一次
        if (bookContentSync.ensureLocalBookContent(book.id)) {
            ParsedBookStorage.readBundle(canonical)
                ?.takeIf { it.body.isNotEmpty() }
                ?.let {
                    persistCanonicalPathsIfNeeded(book, canonical)
                    return it
                }
        }

        if (ImportedBookFormat.isRemovedStoredKey(book.importFormat)) {
            return ExtractedBookText.plainBody(
                BookContentLoader.removedFormatPlaceholder(context, book.importFormat)
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
                    context,
                    result.bytes,
                    format,
                    result.charsetFromHeader
                )
            } else if (path.startsWith("content://", ignoreCase = true)) {
                val uri = Uri.parse(path)
                BookContentLoader.loadExtractedFromUri(context, uri, format)
            } else {
                // 他机绝对路径 / 失效本地路径：不再误读
                ExtractedBookText.plainBody("")
            }
        } catch (_: Exception) {
            ExtractedBookText.plainBody("")
        }
    }

    private suspend fun persistCanonicalPathsIfNeeded(book: BookEntity, canonical: File) {
        val bodyFile = File(canonical, ParsedBookStorage.BODY_FILE)
        if (!bodyFile.isFile) return
        val cover = when {
            File(canonical, ParsedBookStorage.COVER_JPG).isFile ->
                File(canonical, ParsedBookStorage.COVER_JPG).absolutePath
            File(canonical, ParsedBookStorage.COVER_PNG).isFile ->
                File(canonical, ParsedBookStorage.COVER_PNG).absolutePath
            else -> book.coverImagePath
        }
        val localBundle = canonical.absolutePath
        val localBody = bodyFile.absolutePath
        if (book.parsedBundlePath == localBundle &&
            book.filePath == localBody &&
            book.coverImagePath == cover
        ) {
            return
        }
        bookRepository.updateBook(
            book.copy(
                parsedBundlePath = localBundle,
                filePath = localBody,
                coverImagePath = cover,
            ),
        )
    }

    override fun onCleared() {
        // onCleared 时 viewModelScope 已取消；进度与未 flush 的会话片段需同步落盘。
        // 正常路径 ON_PAUSE 已写过统计，此处仅兜底（例如未走到 Pause 的销毁）。
        if (_book.value != null) {
            flushSessionSegmentBlocking()
            runBlocking {
                flushReadingProgressNow()
            }
        }
        super.onCleared()
    }
}

/** 书签 / 阅读进度共用的预览文案规范化。 */
internal fun normalizeReadingPreviewText(raw: String?): String =
    raw
        ?.replace('\n', ' ')
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.let { MarkdownInlineHtml.stripTags(it) }
        ?.take(100)
        .orEmpty()

