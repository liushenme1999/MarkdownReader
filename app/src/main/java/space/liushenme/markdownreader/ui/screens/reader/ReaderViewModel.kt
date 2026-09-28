package space.liushenme.markdownreader.ui.screens.reader

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import space.liushenme.markdownreader.R
import space.liushenme.markdownreader.data.local.entity.BookEntity
import space.liushenme.markdownreader.data.local.entity.BookmarkEntity
import space.liushenme.markdownreader.data.local.entity.HighlightEntity
import space.liushenme.markdownreader.data.repository.BookRepository
import space.liushenme.markdownreader.data.repository.BookmarkRepository
import space.liushenme.markdownreader.data.repository.HighlightRepository
import space.liushenme.markdownreader.data.repository.ReaderPersistenceQueue
import space.liushenme.markdownreader.data.repository.ReaderSettingsRepository
import space.liushenme.markdownreader.document.DocumentOpenFailure
import space.liushenme.markdownreader.document.DocumentOpenResult
import space.liushenme.markdownreader.document.DocumentRepository
import space.liushenme.markdownreader.document.DocumentSnapshot
import space.liushenme.markdownreader.importing.ImportedBookFormat
import space.liushenme.markdownreader.importing.PdfReaderContent
import space.liushenme.markdownreader.markdown.MarkdownInlineHtml
import space.liushenme.markdownreader.model.HighlightStyle
import space.liushenme.markdownreader.ui.screens.reader.anchor.captureTextAnchor
import space.liushenme.markdownreader.ui.screens.reader.anchor.sourceSpanForRenderedQuote
import space.liushenme.markdownreader.ui.screens.reader.anchor.withCapturedAnchor
import space.liushenme.markdownreader.model.ReaderPageTurnMode
import space.liushenme.markdownreader.ui.theme.ReadingStyleState
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
import kotlinx.coroutines.withContext
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
    private val persistenceQueue: ReaderPersistenceQueue,
    private val readerSettingsRepository: ReaderSettingsRepository,
    private val documentRepository: DocumentRepository,
) : ViewModel() {

    private val _book = MutableStateFlow<BookEntity?>(null)
    val book: StateFlow<BookEntity?> = _book.asStateFlow()

    private val _content = MutableStateFlow("")
    val content: StateFlow<String> = _content.asStateFlow()

    private val _documentSnapshot = MutableStateFlow<DocumentSnapshot?>(null)
    val documentSnapshot: StateFlow<DocumentSnapshot?> = _documentSnapshot.asStateFlow()

    /** 导入时写入的目录；非空时阅读页优先使用，不再从正文猜标题。 */
    private val _structuredToc = MutableStateFlow<List<MarkdownTocEntry>?>(null)
    val structuredToc: StateFlow<List<MarkdownTocEntry>?> = _structuredToc.asStateFlow()

    /** 每次 [loadBook] 完成正文/进度装载后递增，用于强制触发滚动恢复（避免与上次正文相同导致 StateFlow 不发射）。 */
    private val _readerLoadEpoch = MutableStateFlow(0L)
    val readerLoadEpoch: StateFlow<Long> = _readerLoadEpoch.asStateFlow()

    private val session = ReaderSessionController()
    private val _readingProgress = session.readingProgress
    val readingProgress: StateFlow<Float> = session.readingProgress

    /** 视口顶部源码坐标；章节小标题按它（及底部）定位，不经过百分比量化。 */
    val viewportTopChar: StateFlow<Int> = session.viewportTopChar

    /** 视口底部源码坐标；页面上已露出的标题优先于视口顶之上的上一节。 */
    val viewportBottomChar: StateFlow<Int> = session.viewportBottomChar

    /**
     * 当前应对齐小标题栏的目录项 [MarkdownTocEntry.sourceOffset]。
     * 由阅读页按可见 HeadingSpan 写入；未就绪时为 null，标题栏退回视口区间估算。
     */
    val visibleChapterOffset: StateFlow<Int?> = session.visibleChapterOffset

    val readingStyleState: StateFlow<ReadingStyleState> =
        readerSettingsRepository.readingStyleState
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = ReadingStyleState.DEFAULT,
            )

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

    val codeBlockWrap: StateFlow<Boolean> = readerSettingsRepository.codeBlockWrap
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ReaderSettingsRepository.DEFAULT_CODE_BLOCK_WRAP,
        )

    val hideSystemBars: StateFlow<Boolean> = readerSettingsRepository.hideSystemBars
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ReaderSettingsRepository.DEFAULT_HIDE_SYSTEM_BARS,
        )

    val showTocInLandscape: StateFlow<Boolean> = readerSettingsRepository.showTocInLandscape
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ReaderSettingsRepository.DEFAULT_SHOW_TOC_IN_LANDSCAPE,
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

    private val _loadFailure = MutableStateFlow<ReaderLoadState?>(null)
    private val _effects = MutableSharedFlow<ReaderEffect>(extraBufferCapacity = 8)
    val effects: SharedFlow<ReaderEffect> = _effects.asSharedFlow()

    private val _isLoading = MutableStateFlow(false)

    val bookmarks = MutableStateFlow<List<BookmarkEntity>>(emptyList())
    val highlights = MutableStateFlow<List<HighlightEntity>>(emptyList())

    /** 最近一次按视口顶部更新的全书字符下标。 */
    private val lastKnownReadingCharPos: Int
        get() = session.lastKnownReadingCharPos
    private val _showMarkFinishedPrompt = session.showMarkFinishedPrompt
    val showMarkFinishedPrompt: StateFlow<Boolean> = _showMarkFinishedPrompt
    /** 与书签 previewText 同格式的视口顶预览，打开书时与书签跳转共用定位。 */
    private val lastKnownProgressPreview: String
        get() = session.lastKnownProgressPreview

    private fun setVisibleChapterOffset(offset: Int?) {
        session.setVisibleChapterOffset(offset)
    }

    private var loadBookJob: Job? = null
    private var bookmarkCollectJob: Job? = null
    private var highlightCollectJob: Job? = null
    private var progressPersistJob: Job? = null
    /** 当前 ViewModel 会话内已成功装载的正文 bookId；WebLink 返回等同书时不重复 load。 */
    private var loadedBookId: Long? = null

    fun dispatch(event: ReaderEvent) {
        when (event) {
            is ReaderEvent.LoadBook -> loadBook(appContext, event.bookId)
            ReaderEvent.ReadingResumed -> onReadingResumed()
            ReaderEvent.ReadingPaused -> onReadingPaused()
            is ReaderEvent.VisibleProgress -> updateVisibleReadingProgressInternal(
                event.globalChar,
                event.reachedEnd,
                event.bottomChar,
                event.chapterOffset,
            )
            is ReaderEvent.ProgressAtChar -> if (event.immediate) {
                updateReadingProgressAtCharNowInternal(event.globalChar, event.previewText, event.reachedEnd)
            } else {
                updateReadingProgressAtCharInternal(
                    event.globalChar,
                    event.previewText,
                    event.reachedEnd,
                    event.bottomChar,
                    event.chapterOffset,
                )
            }
            is ReaderEvent.PersistVisibleProgress -> enqueueVisibleProgressSave(
                event.globalChar,
                event.previewText,
            )
            is ReaderEvent.PersistReadingPosition -> enqueueReadingPositionSave(
                event.globalChar,
                event.previewText,
                event.reachedEnd,
            )
            is ReaderEvent.FinishPromptEnabled -> setFinishPromptEnabled(event.enabled)
            is ReaderEvent.DocumentEndChanged -> onDocumentEndChanged(event.atEnd)
            ReaderEvent.MarkFinished -> markAsFinished()
            ReaderEvent.DismissFinishPrompt -> dismissFinishPrompt()
            ReaderEvent.RetryLoad -> _book.value?.id?.let { loadBook(appContext, it) }
            is ReaderEvent.AddBookmark -> addBookmarkInternal(event.previewText, event.note)
            is ReaderEvent.DeleteBookmark -> deleteBookmarkInternal(event.bookmark)
            is ReaderEvent.DeleteHighlight -> deleteHighlightInternal(event.highlight)
            is ReaderEvent.DeleteHighlightById -> deleteHighlightByIdInternal(event.highlightId)
            is ReaderEvent.UpdateHighlightAppearance -> updateHighlightAppearanceInternal(
                event.highlightId,
                event.colorArgb,
                event.style,
            )
            is ReaderEvent.ToggleBookmarkAtSwipe -> {
                viewModelScope.launch {
                    toggleBookmarkAtSwipeInternal(
                        previewForAdd = event.previewText,
                        positionForAdd = event.positionForAdd,
                    )?.let { added ->
                        _effects.emit(ReaderEffect.BookmarkToggled(added))
                    }
                }
            }
        }
    }

    private data class DocumentState(
        val book: BookEntity?,
        val content: String,
        val toc: List<MarkdownTocEntry>?,
        val snapshot: DocumentSnapshot?,
        val epoch: Long,
        val failure: ReaderLoadState?,
    )

    private data class AppearanceState(
        val styles: ReadingStyleState,
        val theme: ReadingTheme,
        val fontSize: Int,
        val padding: Int,
        val lineSpacing: Float,
    )

    private data class InteractionState(
        val codeWrap: Boolean,
        val hideBars: Boolean,
        val showTocInLandscape: Boolean,
        val pageMode: ReaderPageTurnMode,
        val highlightColor: Int,
        val highlightStyle: HighlightStyle,
    )

    private data class RuntimeState(
        val progress: Float,
        val finishPrompt: Boolean,
        val bookmarks: List<BookmarkEntity>,
        val highlights: List<HighlightEntity>,
        val loading: Boolean,
    )

    private data class ViewportState(
        val topChar: Int,
        val bottomChar: Int,
        val chapterOffset: Int?,
    )

    val uiState: StateFlow<ReaderUiState> = combine(
        combine(_book, _content, _structuredToc, _documentSnapshot, _readerLoadEpoch, _loadFailure) {
                values ->
            @Suppress("UNCHECKED_CAST")
            DocumentState(
                book = values[0] as BookEntity?,
                content = values[1] as String,
                toc = values[2] as List<MarkdownTocEntry>?,
                snapshot = values[3] as DocumentSnapshot?,
                epoch = values[4] as Long,
                failure = values[5] as ReaderLoadState?,
            )
        },
        combine(
            readingStyleState,
            currentTheme,
            fontSize,
            readerPaddingDp,
            readerLineSpacingMultiplier,
        ) { styles, theme, size, padding, spacing ->
            AppearanceState(styles, theme, size, padding, spacing)
        },
        combine(
            combine(codeBlockWrap, hideSystemBars, showTocInLandscape) { wrap, hideBars, showToc ->
                Triple(wrap, hideBars, showToc)
            },
            pageTurnMode,
            lastHighlightColorArgb,
            lastHighlightStyle,
        ) { base, pageMode, color, style ->
            InteractionState(base.first, base.second, base.third, pageMode, color, style)
        },
        combine(
            _readingProgress,
            _showMarkFinishedPrompt,
            bookmarks,
            highlights,
            _isLoading,
        ) { progress, finishPrompt, bookmarks, highlights, loading ->
            RuntimeState(progress, finishPrompt, bookmarks, highlights, loading)
        },
        combine(viewportTopChar, viewportBottomChar, visibleChapterOffset) { top, bottom, chapter ->
            ViewportState(top, bottom, chapter)
        },
    ) { document, appearance, interaction, runtime, viewport ->
        val loadState = when {
            runtime.loading -> ReaderLoadState.Loading
            document.failure != null -> document.failure
            document.content.isEmpty() -> ReaderLoadState.Empty
            else -> ReaderLoadState.Ready
        }
        ReaderUiState(
            loadState = loadState,
            book = document.book,
            content = document.content,
            structuredToc = document.toc,
            documentSnapshot = document.snapshot,
            readerLoadEpoch = document.epoch,
            readingProgress = runtime.progress,
            viewportTopChar = viewport.topChar,
            viewportBottomChar = viewport.bottomChar,
            visibleChapterOffset = viewport.chapterOffset,
            bookmarks = runtime.bookmarks,
            highlights = runtime.highlights,
            readingStyleState = appearance.styles,
            currentTheme = appearance.theme,
            fontSize = appearance.fontSize,
            readerPaddingDp = appearance.padding,
            readerLineSpacingMultiplier = appearance.lineSpacing,
            codeBlockWrap = interaction.codeWrap,
            hideSystemBars = interaction.hideBars,
            showTocInLandscape = interaction.showTocInLandscape,
            pageTurnMode = interaction.pageMode,
            lastHighlightColorArgb = interaction.highlightColor,
            lastHighlightStyle = interaction.highlightStyle,
            showMarkFinishedPrompt = runtime.finishPrompt,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ReaderUiState(),
    )

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

        _loadFailure.value = null
        _isLoading.value = true
        bookmarks.value = emptyList()
        highlights.value = emptyList()
        _structuredToc.value = null
        _documentSnapshot.value = null
        session.resetForLoad()

        val currentLoadJob = viewModelScope.launch {
            val bookEntity = bookRepository.getBookById(bookId)
            _book.value = bookEntity

            if (bookEntity == null) {
                loadedBookId = null
                _content.value = ""
                _loadFailure.value = ReaderLoadState.MissingSource(
                    appContext.getString(R.string.reader_error_book_not_found),
                )
                _readerLoadEpoch.value = _readerLoadEpoch.value + 1L
                _effects.tryEmit(
                    ReaderEffect.LoadFailed(
                        ReaderLoadState.MissingSource(appContext.getString(R.string.reader_error_book_not_found)),
                    ),
                )
                readerOpenDbg("loadBook missing bookId=$bookId +${android.os.SystemClock.uptimeMillis() - t0}ms")
                return@launch
            }

            val openResult = withContext(Dispatchers.IO) { documentRepository.open(bookId) }
            if (openResult is DocumentOpenResult.Failed) {
                val state = openResult.toReaderLoadState(appContext)
                _book.value = openResult.book ?: bookEntity
                _content.value = ""
                _documentSnapshot.value = null
                _loadFailure.value = state
                _readerLoadEpoch.value = _readerLoadEpoch.value + 1L
                _effects.tryEmit(ReaderEffect.LoadFailed(state))
                return@launch
            }
            openResult as DocumentOpenResult.Ready
            val latestBook = openResult.book
            val snapshot = openResult.snapshot
            _book.value = latestBook
            _documentSnapshot.value = snapshot
            val text = snapshot.content
            _content.value = text
            _structuredToc.value = snapshot.toc
                .map {
                    MarkdownTocEntry(
                        level = it.level,
                        title = it.title,
                        sourceOffset = it.sourceOffset,
                        rawTitle = it.rawTitle,
                    )
                }
                .takeIf { it.isNotEmpty() }
            session.restoreBookState(
                progress = latestBook.readingProgress,
                position = resolveStoredCharPos(
                    currentPosition = latestBook.currentPosition,
                    readingProgress = latestBook.readingProgress,
                    contentLength = text.length,
                    totalChars = latestBook.totalChars.coerceAtLeast(text.length.coerceAtLeast(1)),
                ),
                contentLength = text.length,
                previewText = latestBook.progressPreviewText,
            )
            _readerLoadEpoch.value = _readerLoadEpoch.value + 1L
            readerOpenDbg(
                "loadBook contentReady len=${text.length} pos=$lastKnownReadingCharPos " +
                    "toc=${_structuredToc.value?.size ?: 0} epoch=${_readerLoadEpoch.value} " +
                    "+${android.os.SystemClock.uptimeMillis() - t0}ms",
            )

            if (text.isEmpty()) {
                val isPdf = ImportedBookFormat.fromStored(latestBook.importFormat).isPdf
                _loadFailure.value = if (isPdf) {
                    ReaderLoadState.BrokenBundle(
                        appContext.getString(R.string.reader_error_pdf_bundle_incomplete),
                    )
                } else {
                    ReaderLoadState.MissingSource(
                        appContext.getString(R.string.reader_error_cannot_read_body),
                    )
                }
                _effects.tryEmit(_loadFailure.value?.let(ReaderEffect::LoadFailed) ?: return@launch)
            }

            if (text.isNotEmpty()) {
                _effects.tryEmit(ReaderEffect.BookLoaded(bookId))
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
        loadBookJob = currentLoadJob
        currentLoadJob.invokeOnCompletion {
            if (loadBookJob === currentLoadJob) {
                _isLoading.value = false
            }
        }
    }

    /** 阅读页进入前台：开始（或继续）本段阅读计时。 */
    fun onReadingResumed() {
        session.onReadingResumed(
            hasBook = _book.value != null,
            hasContent = _content.value.isNotEmpty(),
        )
    }

    /**
     * 阅读页进入后台 / 打开外链等：落盘本段时长与字数，并暂停计时，避免后台时间灌水。
     * 与进度落盘一并在 ON_PAUSE 调用，进程被杀前尽量保住统计。
     */
    fun onReadingPaused() {
        val book = _book.value ?: return
        val delta = session.onReadingPaused(_content.value.length) ?: return
        persistenceQueue.recordSession(book.id, delta.charsRead, delta.minutesRead)
    }

    private fun beginSessionSegmentIfNeeded() {
        session.onReadingResumed(
            hasBook = _book.value != null,
            hasContent = _content.value.isNotEmpty(),
        )
    }

    private fun enqueueSessionSegmentSave() {
        val book = _book.value ?: return
        val delta = session.onReadingPaused(_content.value.length) ?: return
        persistenceQueue.recordSession(book.id, delta.charsRead, delta.minutesRead)
    }

    /**
     * Compatibility entry point for viewport updates. All viewport changes are
     * now normalized through [ReaderEvent.VisibleProgress].
     */
    fun updateVisibleReadingProgress(
        globalChar: Int,
        reachedEnd: Boolean = false,
        bottomChar: Int? = null,
        chapterOffset: Int? = null,
    ) {
        dispatch(
            ReaderEvent.VisibleProgress(
                globalChar = globalChar,
                reachedEnd = reachedEnd,
                bottomChar = bottomChar,
                chapterOffset = chapterOffset,
            )
        )
    }

    /** Only updates in-memory viewport state; persistence is handled separately. */
    private fun updateVisibleReadingProgressInternal(
        globalChar: Int,
        reachedEnd: Boolean = false,
        bottomChar: Int? = null,
        chapterOffset: Int? = null,
    ) {
        val contentLen = _content.value.length
        if (contentLen <= 0) return
        setVisibleChapterOffset(chapterOffset)
        session.updateVisibleProgress(globalChar, reachedEnd, bottomChar, contentLen)
    }

    /** 按全书源码字符下标更新进度（与书签记录字段一致：position + preview）。 */
    /**
     * Compatibility entry point for the incremental event migration.
     * UI callers can keep their existing signature while all progress changes
     * now pass through the single ReaderEvent channel.
     */
    fun updateReadingProgressAtChar(
        globalChar: Int,
        previewText: String? = null,
        reachedEnd: Boolean = false,
        bottomChar: Int? = null,
        chapterOffset: Int? = null,
    ) {
        dispatch(
            ReaderEvent.ProgressAtChar(
                globalChar = globalChar,
                previewText = previewText,
                reachedEnd = reachedEnd,
                bottomChar = bottomChar,
                chapterOffset = chapterOffset,
            )
        )
    }

    private fun updateReadingProgressAtCharInternal(
        globalChar: Int,
        previewText: String? = null,
        reachedEnd: Boolean = false,
        bottomChar: Int? = null,
        chapterOffset: Int? = null,
    ) {
        val contentLen = _content.value.length
        if (contentLen <= 0) return
        val snapshot = session.updateProgressAtChar(
            globalChar = globalChar,
            previewText = previewText?.let(::normalizeReadingPreviewText),
            reachedEnd = reachedEnd,
            bottomChar = bottomChar,
            chapterOffset = chapterOffset,
            contentLength = contentLen,
        )
        if (session.canPersistProgress) {
            scheduleProgressPersist(snapshot.progress, snapshot.position, snapshot.previewText)
        }
    }

    /** Immediate compatibility entry point; persistence still uses the event channel. */
    fun updateReadingProgressAtCharNow(
        globalChar: Int,
        previewText: String? = null,
        reachedEnd: Boolean = false,
    ) {
        dispatch(
            ReaderEvent.ProgressAtChar(
                globalChar = globalChar,
                previewText = previewText,
                reachedEnd = reachedEnd,
                immediate = true,
            )
        )
    }

    private fun updateReadingProgressAtCharNowInternal(
        globalChar: Int,
        previewText: String? = null,
        reachedEnd: Boolean = false,
    ) {
        val contentLen = _content.value.length
        if (contentLen <= 0) return
        val snapshot = session.updateProgressAtChar(
            globalChar = globalChar,
            previewText = previewText?.let(::normalizeReadingPreviewText),
            reachedEnd = reachedEnd,
            bottomChar = null,
            chapterOffset = null,
            contentLength = contentLen,
        )
        progressPersistJob?.cancel()
        progressPersistJob = null
        persistReadingProgress(snapshot.progress, snapshot.position, snapshot.previewText)
    }

    /** 首屏定位完成后再允许「标记已读完」弹窗。 */
    fun setFinishPromptEnabled(enabled: Boolean) {
        session.setFinishPromptEnabled(enabled)
    }

    fun onDocumentEndChanged(atEnd: Boolean) {
        session.onDocumentEndChanged(atEnd)
    }

    fun markAsFinished() {
        val contentLen = _content.value.length
        if (contentLen <= 0) return
        val snapshot = session.markAsFinished(contentLen)
        progressPersistJob?.cancel()
        progressPersistJob = null
        persistReadingProgress(snapshot.progress, snapshot.position, snapshot.previewText)
    }

    fun dismissFinishPrompt() {
        session.dismissFinishPrompt()
    }

    /**
     * 退出时把当前内存进度提交到串行写入队列。已确认的 100% 不再用视口顶重算。
     */
    fun enqueueVisibleProgressSave(
        globalChar: Int? = null,
        previewText: String? = null,
    ) {
        val contentLen = _content.value.length
        if (contentLen <= 0) return
        val snapshot = session.captureProgress(
            contentLength = contentLen,
            globalChar = globalChar,
            previewText = previewText?.let(::normalizeReadingPreviewText),
        )
        progressPersistJob?.cancel()
        progressPersistJob = null
        persistReadingProgress(snapshot.progress, snapshot.position, snapshot.previewText)
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

    /** 退出阅读页时立即提交到串行写入队列，避免等待滚动防抖任务。 */
    fun enqueueReadingPositionSave(
        globalChar: Int,
        previewText: String? = null,
        reachedEnd: Boolean? = null,
    ) {
        val contentLen = _content.value.length
        if (contentLen <= 0) return
        val snapshot = session.capturePosition(
            contentLength = contentLen,
            globalChar = globalChar,
            previewText = previewText?.let(::normalizeReadingPreviewText),
            reachedEnd = reachedEnd,
        )
        progressPersistJob?.cancel()
        progressPersistJob = null
        persistReadingProgress(snapshot.progress, snapshot.position, snapshot.previewText)
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

    private fun persistReadingProgress(
        progress: Float,
        position: Int,
        previewText: String,
    ) {
        val book = _book.value ?: return
        persistenceQueue.saveProgress(book.id, progress, position, previewText)
        // 勿同步更新 _book.currentPosition：ReaderScreen 监听该字段会重算窗口并触发滚动恢复，导致阅读中跳动。
    }

    private fun enqueueReadingProgressSave() {
        progressPersistJob?.cancel()
        progressPersistJob = null
        val contentLen = _content.value.length.coerceAtLeast(1)
        val snapshot = session.snapshot(contentLen)
        persistReadingProgress(snapshot.progress, snapshot.position, snapshot.previewText)
    }

    fun addBookmark(previewText: String, note: String? = null) {
        dispatch(ReaderEvent.AddBookmark(previewText, note))
    }

    private fun addBookmarkInternal(previewText: String, note: String? = null) {
        viewModelScope.launch {
            _book.value?.let { book ->
                val raw = _content.value
                val contentLen = raw.length.coerceAtLeast(1)
                val pos = lastKnownReadingCharPos.coerceIn(0, contentLen)
                val bookmark = BookmarkEntity(
                    bookId = book.id,
                    position = pos,
                    previewText = resolveBookmarkPreviewText(
                        previewForAdd = previewText,
                        sourceContent = raw,
                        position = pos,
                        context = appContext,
                    ),
                    note = note,
                    createTime = Date(),
                ).let { entity ->
                    anchorFor(raw, pos, pos)?.let(entity::withCapturedAnchor) ?: entity
                }
                bookmarkRepository.addBookmark(bookmark)
            }
        }
    }

    fun deleteBookmark(bookmark: BookmarkEntity) {
        dispatch(ReaderEvent.DeleteBookmark(bookmark))
    }

    private fun deleteBookmarkInternal(bookmark: BookmarkEntity) {
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
    ): Boolean? = toggleBookmarkAtSwipeInternal(previewForAdd, positionForAdd)

    private suspend fun toggleBookmarkAtSwipeInternal(
        previewForAdd: String? = null,
        positionForAdd: Int? = null,
    ): Boolean? {
        val book = _book.value ?: return null
        val total = book.totalChars.coerceAtLeast(1)
        val raw = _content.value
        if (raw.isEmpty()) return null

        val pos = (positionForAdd ?: lastKnownReadingCharPos).coerceIn(0, raw.length)
        val preview = resolveBookmarkPreviewText(
            previewForAdd = previewForAdd,
            sourceContent = raw,
            position = pos,
            context = appContext,
        )
        val isPdf = PdfReaderContent.looksLikePdfBody(raw)
        session.updateBookmarkPosition(pos, preview, raw.length, isPdf)
        val snapshot = session.snapshot(raw.length)
        scheduleProgressPersist(
            snapshot.progress,
            snapshot.position,
            if (isPdf) "" else snapshot.previewText,
        )
        val window = (total / 40).coerceIn(300, 1500)
        val near = bookmarks.value.find { abs(it.position - pos) <= window }

        return if (near != null) {
            bookmarkRepository.deleteBookmark(near)
            false
        } else {
            val bookmark = BookmarkEntity(
                bookId = book.id,
                position = pos,
                previewText = preview,
                note = null,
                createTime = Date(),
            ).let { entity ->
                anchorFor(raw, pos, pos)?.let(entity::withCapturedAnchor) ?: entity
            }
            bookmarkRepository.addBookmark(bookmark)
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
        forcedSourceSpan: Pair<Int, Int>? = null,
        sourceSearchRange: IntRange? = null,
    ): Long? = withContext(NonCancellable) {
        if (selectedText.isBlank()) return@withContext null
        val book = _book.value ?: return@withContext null
        val content = _content.value
        if (content.isEmpty()) return@withContext null
        val hint = sourceStartHint.coerceIn(0, content.length)
        // 选区是排版后的文字，源码里可能夹着标记。标题选区用标题行，避免正文同句抢走。
        val forced = forcedSourceSpan?.takeIf { (start, end) ->
            start in 0 until content.length && end in (start + 1)..content.length
        }
        val searchStart = sourceSearchRange?.first?.coerceIn(0, content.length) ?: 0
        val searchEnd = sourceSearchRange?.last?.plus(1)?.coerceIn(searchStart, content.length) ?: content.length
        val span = forced ?: sourceSpanForRenderedQuote(
            source = content,
            rendered = selectedText,
            hint = hint,
            searchStart = searchStart,
            searchEnd = searchEnd,
        )
        val startPos = span?.first ?: return@withContext null
        val endPos = span.second
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
        ).let { entity ->
            anchorFor(content, startPos, endPos)?.let(entity::withCapturedAnchor) ?: entity
        }
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
        forcedSourceSpan: Pair<Int, Int>? = null,
        sourceSearchRange: IntRange? = null,
        onResult: (Long?) -> Unit = {},
    ) {
        viewModelScope.launch {
            val id = runCatching {
                addHighlightNow(
                    selectedText = selectedText,
                    color = color,
                    style = style,
                    sourceStartHint = sourceStartHint,
                    forcedSourceSpan = forcedSourceSpan,
                    sourceSearchRange = sourceSearchRange,
                )
            }.getOrNull()
            onResult(id)
        }
    }

    fun updateHighlightAppearance(
        highlightId: Long,
        color: androidx.compose.ui.graphics.Color,
        style: HighlightStyle,
    ) {
        dispatch(
            ReaderEvent.UpdateHighlightAppearance(
                highlightId = highlightId,
                colorArgb = highlightColorArgb(color),
                style = style,
            )
        )
    }

    private fun updateHighlightAppearanceInternal(
        highlightId: Long,
        colorArgb: Int,
        style: HighlightStyle,
    ) {
        if (highlightId <= 0L) return
        viewModelScope.launch {
            val existing = highlights.value.find { it.id == highlightId } ?: return@launch
            val updated = existing.copy(color = colorArgb, style = style.storageKey)
            // 先乐观更新 UI，再落库
            highlights.value = highlights.value.map { if (it.id == highlightId) updated else it }
            highlightRepository.updateHighlight(updated)
            readerSettingsRepository.setLastHighlightPreference(colorArgb, style)
        }
    }

    fun deleteHighlight(highlight: HighlightEntity) {
        dispatch(ReaderEvent.DeleteHighlight(highlight))
    }

    private fun deleteHighlightInternal(highlight: HighlightEntity) {
        viewModelScope.launch {
            // 先乐观移除，菜单/正文立刻变为未划线
            highlights.value = highlights.value.filterNot { it.id == highlight.id }
            highlightRepository.deleteHighlight(highlight)
        }
    }

    fun deleteHighlightById(highlightId: Long) {
        dispatch(ReaderEvent.DeleteHighlightById(highlightId))
    }

    private fun deleteHighlightByIdInternal(highlightId: Long) {
        if (highlightId <= 0L) return
        viewModelScope.launch {
            val existing = highlights.value.firstOrNull { it.id == highlightId } ?: return@launch
            highlights.value = highlights.value.filterNot { it.id == highlightId }
            highlightRepository.deleteHighlight(existing)
        }
    }

    /** PDF 不写注解锚点。目录优先用导入时解析好的，没有再按正文现切。 */
    private fun anchorFor(content: String, start: Int, end: Int) =
        _book.value?.let { book ->
            val format = ImportedBookFormat.fromStored(book.importFormat)
            if (format.isPdf || PdfReaderContent.looksLikePdfBody(content)) return@let null
            val toc = _structuredToc.value?.takeIf { it.isNotEmpty() }
                ?: if (format.usesReaderPlainBody) {
                    parsePlainTextToc(content)
                } else {
                    parseMarkdownToc(content)
                }
            captureTextAnchor(
                document = content,
                plainText = format.usesReaderPlainBody,
                start = start,
                end = end,
                toc = toc,
                docHash = book.contentHash,
            )
        }

    private fun highlightColorArgb(color: androidx.compose.ui.graphics.Color): Int =
        if (color.alpha < 0.06f) {
            ReaderSettingsRepository.DEFAULT_HIGHLIGHT_COLOR_ARGB
        } else {
            color.toArgb()
        }


    fun setTheme(theme: ReadingTheme) {
        viewModelScope.launch {
            readerSettingsRepository.setReadingTheme(theme)
        }
    }

    fun selectReadingStyle(index: Int) {
        viewModelScope.launch { readerSettingsRepository.selectReadingStyle(index) }
    }

    fun addReadingStyle(onCreated: (Int) -> Unit) {
        viewModelScope.launch {
            onCreated(readerSettingsRepository.addReadingStyle())
        }
    }

    fun updateReadingStyle(index: Int, theme: ReadingTheme) {
        viewModelScope.launch { readerSettingsRepository.updateReadingStyle(index, theme) }
    }

    fun deleteReadingStyle(index: Int) {
        viewModelScope.launch { readerSettingsRepository.deleteReadingStyle(index) }
    }

    fun resetAllReadingStyles(keepCustom: Boolean) {
        viewModelScope.launch { readerSettingsRepository.resetAllReadingStyles(keepCustom) }
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

    fun setCodeBlockWrap(enabled: Boolean) {
        viewModelScope.launch {
            readerSettingsRepository.setCodeBlockWrap(enabled)
        }
    }

    fun setHideSystemBars(hide: Boolean) {
        viewModelScope.launch {
            readerSettingsRepository.setHideSystemBars(hide)
        }
    }

    fun setPageTurnMode(mode: ReaderPageTurnMode) {
        viewModelScope.launch {
            readerSettingsRepository.setPageTurnMode(mode)
        }
    }

    override fun onCleared() {
        // onCleared 时 viewModelScope 已取消；进度与未提交的会话片段交给应用级队列落盘。
        // 正常路径 ON_PAUSE 已写过统计，此处仅兜底（例如未走到 Pause 的销毁）。
        if (_book.value != null) {
            enqueueSessionSegmentSave()
            enqueueReadingProgressSave()
        }
        super.onCleared()
    }
}

private fun DocumentOpenResult.Failed.toReaderLoadState(context: Context): ReaderLoadState = when (reason) {
    DocumentOpenFailure.BookNotFound -> ReaderLoadState.MissingSource(
        detail ?: context.getString(R.string.reader_error_book_not_found),
    )
    DocumentOpenFailure.MissingSource -> ReaderLoadState.MissingSource(
        detail ?: context.getString(R.string.reader_error_cannot_read_body),
    )
    DocumentOpenFailure.BrokenBundle -> ReaderLoadState.BrokenBundle(
        detail ?: context.getString(R.string.reader_error_pdf_bundle_incomplete),
    )
    DocumentOpenFailure.NetworkRequired -> ReaderLoadState.NetworkRequired(
        detail ?: context.getString(R.string.reader_error_network_required),
    )
    DocumentOpenFailure.UnsupportedFormat -> ReaderLoadState.UnsupportedFormat(
        detail ?: context.getString(R.string.reader_error_cannot_read_body),
    )
    DocumentOpenFailure.PermissionDenied -> ReaderLoadState.PermissionDenied(
        detail ?: context.getString(R.string.reader_error_permission_denied),
    )
    DocumentOpenFailure.Failed -> ReaderLoadState.Failed(
        detail ?: context.getString(R.string.reader_error_cannot_read_body),
    )
}

/** 书签 / 阅读进度共用的预览文案规范化。 */
internal fun normalizeReadingPreviewText(raw: String?): String =
    raw
        ?.replace('\uFFFC', ' ')
        ?.replace('\n', ' ')
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.let { MarkdownInlineHtml.stripTags(it) }
        ?.trim()
        ?.take(100)
        .orEmpty()

/** PDF 书签记页码；其它格式用视口文字，必要时回退到源码附近片段。 */
internal fun resolveBookmarkPreviewText(
    previewForAdd: String?,
    sourceContent: String,
    position: Int,
    context: Context,
): String {
    val fallback = context.getString(R.string.bookmark_default_preview)
    if (PdfReaderContent.looksLikePdfBody(sourceContent)) {
        return PdfReaderContent.bookmarkPreview(context, sourceContent, position)
    }
    return normalizeReadingPreviewText(previewForAdd).ifEmpty {
        val from = (position - 60).coerceAtLeast(0)
        val to = (position + 80).coerceAtMost(sourceContent.length)
        normalizeReadingPreviewText(
            sourceContent.substring(from, to).ifEmpty { fallback },
        ).ifEmpty { fallback }
    }
}
