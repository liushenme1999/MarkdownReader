package space.liushenme.markdownreader.ui.screens.reader

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import space.liushenme.markdownreader.R
import space.liushenme.markdownreader.data.local.entity.BookEntity
import space.liushenme.markdownreader.data.local.entity.BookmarkEntity
import space.liushenme.markdownreader.data.local.entity.HighlightEntity
import space.liushenme.markdownreader.data.repository.IBookRepository
import space.liushenme.markdownreader.data.repository.IBookmarkRepository
import space.liushenme.markdownreader.data.repository.IHighlightRepository
import space.liushenme.markdownreader.data.repository.ReaderPersistenceQueue
import space.liushenme.markdownreader.data.repository.ReadingFormatProfile
import space.liushenme.markdownreader.data.repository.ReaderSettingsRepository
import space.liushenme.markdownreader.document.DocumentOpenFailure
import space.liushenme.markdownreader.document.DocumentOpenResult
import space.liushenme.markdownreader.document.DocumentRepository
import space.liushenme.markdownreader.document.DocumentSnapshot
import space.liushenme.markdownreader.importing.ImportedBookFormat
import space.liushenme.markdownreader.importing.PdfReaderContent
import space.liushenme.markdownreader.markdown.MarkdownInlineHtml
import space.liushenme.markdownreader.markdown.CodeHighlightTheme
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
    private val bookRepository: IBookRepository,
    private val bookmarkRepository: IBookmarkRepository,
    private val highlightRepository: IHighlightRepository,
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

    val codeLineNumbers: StateFlow<Boolean> = readerSettingsRepository.codeLineNumbers
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ReaderSettingsRepository.DEFAULT_CODE_LINE_NUMBERS,
        )

    val codeHighlightTheme: StateFlow<CodeHighlightTheme> =
        readerSettingsRepository.codeHighlightTheme
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = CodeHighlightTheme.DEFAULT,
            )

    val hideSystemBars: StateFlow<Boolean> = readerSettingsRepository.hideSystemBars
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ReaderSettingsRepository.DEFAULT_HIDE_SYSTEM_BARS,
        )

    /**
     * 右滑加书签的首次引导是否还没展示过。
     * 直接读 DataStore（suspend），不走 StateFlow：该 UI 只在一处临时读取，
     * 用 stateIn(WhileSubscribed) 的 `.value` 会因为无人订阅而永远停在初始值，导致反复提示。
     */
    suspend fun shouldShowSwipeBookmarkHint(): Boolean =
        readerSettingsRepository.swipeBookmarkHintShown.first().not()

    /** 记录右滑书签引导已展示，避免每次命中手势都弹说明。 */
    fun markSwipeBookmarkHintShown() {
        viewModelScope.launch {
            readerSettingsRepository.setSwipeBookmarkHintShown(true)
        }
    }

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
    /** 窗口模式：当前 content 在全书中的起始字符偏移（0 表示 content 即全文）。 */
    private val _contentBaseChar = MutableStateFlow(0)
    private var contentBaseChar: Int
        get() = _contentBaseChar.value
        set(value) { _contentBaseChar.value = value }

    /** 窗口模式：全书总字符数（用于进度百分比；0 表示用 content.length）。 */
    private val _contentTotalChars = MutableStateFlow(0)
    private var contentTotalChars: Int
        get() = _contentTotalChars.value
        set(value) { _contentTotalChars.value = value }

    /** 打开书后待恢复的目标位置（全书偏移）；恢复完成前页码变化不得覆盖它。 */
    private var pendingRestoreGlobalChar: Int? = null

    /** 窗口模式：content 是否已追加到全书末尾。 */
    private var contentWindowComplete: Boolean = true
    private var windowAppendJob: Job? = null

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
                        rangeStart = event.rangeStart,
                        rangeEnd = event.rangeEnd,
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
        val codeLineNumbers: Boolean,
        val hideBars: Boolean,
        val showTocInLandscape: Boolean,
        val pageMode: ReaderPageTurnMode,
        val highlightColor: Int,
        val highlightStyle: HighlightStyle,
        val codeHighlightTheme: CodeHighlightTheme,
    )

    private data class CodeInteractionState(
        val wrap: Boolean,
        val lineNumbers: Boolean,
        val hideBars: Boolean,
        val showTocInLandscape: Boolean,
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
        val windowStartChar: Int = 0,
        val totalCharsGlobal: Int = 0,
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
            combine(
                codeBlockWrap,
                codeLineNumbers,
                hideSystemBars,
                showTocInLandscape,
            ) { wrap, lineNumbers, hideBars, showToc ->
                CodeInteractionState(wrap, lineNumbers, hideBars, showToc)
            },
            pageTurnMode,
            lastHighlightColorArgb,
            lastHighlightStyle,
            codeHighlightTheme,
        ) { base, pageMode, color, style, codeTheme ->
            InteractionState(
                base.wrap,
                base.lineNumbers,
                base.hideBars,
                base.showTocInLandscape,
                pageMode,
                color,
                style,
                codeTheme,
            )
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
        combine(
            viewportTopChar,
            viewportBottomChar,
            visibleChapterOffset,
            _contentBaseChar,
            _contentTotalChars,
        ) { top, bottom, chapter, base, total ->
            ViewportState(top, bottom, chapter, base, total)
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
            codeLineNumbers = interaction.codeLineNumbers,
            hideSystemBars = interaction.hideBars,
            showTocInLandscape = interaction.showTocInLandscape,
            pageTurnMode = interaction.pageMode,
            lastHighlightColorArgb = interaction.highlightColor,
            lastHighlightStyle = interaction.highlightStyle,
            codeHighlightTheme = interaction.codeHighlightTheme,
            showMarkFinishedPrompt = runtime.finishPrompt,
            windowStartChar = viewport.windowStartChar,
            totalCharsGlobal = viewport.totalCharsGlobal,
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
            // 切换阅读样式档位：不同文件格式各自一套「颜色与背景 / 代码样式 / 字体排版 / 翻页方式」。
            readerSettingsRepository.setActiveProfile(
                ReadingFormatProfile.fromBookFormat(
                    ImportedBookFormat.fromStored(bookEntity?.importFormat),
                ),
            )

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
            contentBaseChar = snapshot.windowStartChar.coerceAtLeast(0)
            contentTotalChars = if (snapshot.totalCharsGlobal > 0) {
                snapshot.totalCharsGlobal
            } else {
                contentBaseChar + text.length
            }
            contentWindowComplete = contentTotalChars <= contentBaseChar + text.length
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
            // 会话内的阅读位置统一用「全书字符偏移」（与进度、书签、目录同一坐标系）：
            // 窗口模式下 UI 侧局部位置由 ReaderScreen 负责与 windowStartChar 互相换算。
            val storedGlobalPos = resolveStoredCharPos(
                currentPosition = latestBook.currentPosition,
                readingProgress = latestBook.readingProgress,
                // 必须以「全书坐标空间」换算：传窗口长度会把全局位置按比例压缩成窗口内位置。
                contentLength = contentTotalChars,
                totalChars = latestBook.totalChars.coerceAtLeast(contentTotalChars),
            ).coerceIn(0, (contentTotalChars - 1).coerceAtLeast(0))
            pendingRestoreGlobalChar = storedGlobalPos
            session.restoreBookState(
                progress = latestBook.readingProgress,
                position = storedGlobalPos,
                contentLength = contentTotalChars.coerceAtLeast(1),
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

    /** 阅读页进入前台：开始（或继续）本段阅读计时，并重新确立本册的阅读样式档位。 */
    fun onReadingResumed() {
        // 从「我的 → 阅读设置」返回时，设置页可能切换过档位，这里按当前书籍再确立一次。
        _book.value?.let { book ->
            readerSettingsRepository.setActiveProfile(
                ReadingFormatProfile.fromBookFormat(
                    ImportedBookFormat.fromStored(book.importFormat),
                ),
            )
        }
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
        // 位置是全书偏移，按全书长度裁剪，避免窗口模式下把阅读字数算成 0。
        val delta = session.onReadingPaused(effectiveTotalChars()) ?: return
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
        val delta = session.onReadingPaused(effectiveTotalChars()) ?: return
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
        val contentLen = effectiveTotalChars()
        if (contentLen <= 0) return
        setVisibleChapterOffset(chapterOffset)
        session.updateVisibleProgress(
            contentBaseChar + globalChar,
            reachedEnd && windowReachedBookEnd(),
            bottomChar?.let { contentBaseChar + it },
            contentLen,
        )
    }

    /**
     * 窗口模式：读到当前窗口尾部附近时异步追加下一批文本。
     * 追加只改 content，不重置阅读位置；追加完成前忽略重复请求。
     */
    fun appendTxtWindowIfNeeded(localChar: Int) {
        if (contentWindowComplete || windowAppendJob?.isActive == true) return
        val current = _content.value
        if (current.isEmpty()) return
        val remaining = current.length - localChar
        if (remaining > WINDOW_APPEND_THRESHOLD_CHARS) return
        val book = _book.value ?: return
        val fromGlobal = contentBaseChar + current.length
        windowAppendJob = viewModelScope.launch {
            val next = withContext(Dispatchers.IO) {
                documentRepository.readNextTxtWindow(book.id, fromGlobal)
            } ?: run {
                contentWindowComplete = true
                return@launch
            }
            val appended = _content.value + next.text
            contentWindowComplete = contentBaseChar + appended.length >= contentTotalChars
            _content.value = appended
        }
    }

    /** 窗口模式：是否真的读到了全书末尾（窗口末尾不等于全书末尾）。 */
    private fun windowReachedBookEnd(): Boolean =
        contentWindowComplete || contentBaseChar + _content.value.length >= contentTotalChars

    /**
     * 窗口模式：把「全书字符位置」换算成当前窗口内的局部位置；
     * 若目标不在窗口内，先按目标位置重新加载窗口。非窗口模式直接返回 clamp 结果。
     */
    suspend fun ensureLocalCharForGlobal(globalChar: Int): Int {
        val current = _content.value
        val local = globalChar - contentBaseChar
        if (contentTotalChars <= 0 || (local in 0..(current.length - 1))) {
            return local.coerceIn(0, (current.length - 1).coerceAtLeast(0))
        }
        val book = _book.value ?: return 0
        val window = withContext(Dispatchers.IO) { documentRepository.reloadTxtWindowAt(book.id, globalChar) }
            ?: return local.coerceIn(0, (current.length - 1).coerceAtLeast(0))
        contentBaseChar = window.startChar
        contentWindowComplete = contentTotalChars <= window.startChar + window.text.length
        _content.value = window.text
        return (globalChar - window.startChar).coerceIn(0, (window.text.length - 1).coerceAtLeast(0))
    }

    /** 进度计算用的「全书字符总数」（窗口模式取索引总字数）。 */
    private fun effectiveTotalChars(): Int =
        if (contentTotalChars > 0) contentTotalChars else _content.value.length

    /** 按全书源码字符下标更新进度（与书签记录字段一致：position + preview）。 */
    /**
     * Compatibility entry point for the incremental event migration.
     * UI callers can keep their existing signature while all progress changes
     * now pass through the single ReaderEvent channel.
     */
    /**
     * 只更新顶栏「当前章节」指针：不写库、不改阅读进度。
     * 分页翻页时即使在工具栏可见（正文重排）阶段也要让章节名跟随。
     */
    fun updateVisibleChapterOffset(offset: Int?) {
        session.setVisibleChapterOffset(offset)
    }

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
        val contentLen = effectiveTotalChars()
        if (contentLen <= 0) return
        val snapshot = session.updateProgressAtChar(
            globalChar = contentBaseChar + globalChar,
            previewText = previewText?.let(::normalizeReadingPreviewText),
            reachedEnd = reachedEnd && windowReachedBookEnd(),
            bottomChar = bottomChar,
            chapterOffset = chapterOffset,
            contentLength = contentLen,
        )
        if (session.canPersistProgress) {
            // snapshot.position 已经是全书偏移（上面按 contentBaseChar + globalChar 传入），
            // 再叠加 contentBaseChar 会把进度存到「2×窗口起点 + 局部位置」，逐次打开越跑越偏。
            scheduleProgressPersist(
                snapshot.progress,
                snapshot.position,
                snapshot.previewText,
            )
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
        val contentLen = effectiveTotalChars()
        if (contentLen <= 0) return
        val snapshot = session.updateProgressAtChar(
            globalChar = contentBaseChar + globalChar,
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

    /** 用户真实翻页/滑动后调用：允许在到达书末时提示「标记为已读完」。 */
    fun notifyUserInteraction() {
        session.markUserInteraction()
    }

    fun markAsFinished() {
        val contentLen = effectiveTotalChars()
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
        val contentLen = effectiveTotalChars()
        if (contentLen <= 0) return
        val snapshot = session.captureProgress(
            contentLength = contentLen,
            globalChar = globalChar?.let { contentBaseChar + it },
            previewText = previewText?.let(::normalizeReadingPreviewText),
        )
        progressPersistJob?.cancel()
        progressPersistJob = null
        persistReadingProgress(snapshot.progress, snapshot.position, snapshot.previewText)
    }

    /** 窗口/翻页恢复时优先用会话内视口锚点，避免 [_book.currentPosition] 滞后于内存进度。 */
    fun readingCharPosForRestore(): Int {
        val contentLen = effectiveTotalChars()
        if (contentLen <= 0) return 0
        // 打开书后的首次恢复必须以「加载时算出的目标位置」为准：
        // 分页宿主会先按第 0 页上报一次进度（窗口起点），用会话里的实时位置会被它覆盖，
        // 表现为每次重新进入都回到窗口第一页（阅读历史看起来失效）。
        pendingRestoreGlobalChar?.let { return it.coerceIn(0, contentLen) }
        return lastKnownReadingCharPos.coerceIn(0, contentLen)
    }

    /** 恢复目标是否仍在等待首次定位（用于抑制页码变化过早覆盖进度）。 */
    fun hasPendingRestoreChar(): Boolean = pendingRestoreGlobalChar != null

    /** 恢复目标在当前窗口内的局部位置；未在恢复中返回 null。 */
    fun pendingRestoreLocalChar(): Int? {
        val pending = pendingRestoreGlobalChar ?: return null
        val length = _content.value.length
        if (length <= 0) return null
        return (pending - contentBaseChar).coerceIn(0, length - 1)
    }

    /** 首次定位已完成，之后以会话内实时位置为准。 */
    fun clearPendingRestoreChar() {
        pendingRestoreGlobalChar = null
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
        val contentLen = effectiveTotalChars()
        if (contentLen <= 0) return
        val snapshot = session.capturePosition(
            contentLength = contentLen,
            globalChar = contentBaseChar + globalChar,
            previewText = previewText?.let(::normalizeReadingPreviewText),
            reachedEnd = reachedEnd,
        )
        progressPersistJob?.cancel()
        progressPersistJob = null
        persistReadingProgress(snapshot.progress, snapshot.position, snapshot.previewText)
    }

    fun updateReadingProgress(progress: Float) {
        val contentLen = effectiveTotalChars().coerceAtLeast(1)
        updateReadingProgressAtChar((progress * contentLen).toInt())
    }

    /**
     * 退出/进入后台时立即落盘当前位置。
     *
     * 用会话内的阅读位置（页码变化、恢复定位时更新），而不是当前页 TextView 的起点：
     * 退出时系统栏显隐会触发分页重排，pager 可能先跳到别的页，直接读页起点会把进度写回上一页，
     * 表现就是「退出再进回到前几页」。位置是全书偏移，按全书长度裁剪。
     */
    fun persistCurrentReadingPositionNow(previewText: String? = null) {
        val contentLen = effectiveTotalChars()
        if (contentLen <= 0) return
        // 取「恢复目标」与「本次会话最近阅读位置」的较大值：打开时的被动重排可能把会话位置
        // 往回挪，但绝不能把已经读到的位置写回去。
        val position = maxOf(readingCharPosForRestore(), lastKnownReadingCharPos)
        // 防御：任何情况下都不能让「裁剪」把已读位置写小（历史 bug 就是位置被压回窗口起点）。
        val clampLength = maxOf(contentLen, position + 1)
        val snapshot = session.captureProgress(
            contentLength = clampLength,
            globalChar = position,
            previewText = previewText?.let(::normalizeReadingPreviewText),
        )
        progressPersistJob?.cancel()
        progressPersistJob = null
        persistReadingProgress(snapshot.progress, snapshot.position, snapshot.previewText)
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
        // 必须按全书长度裁剪：会话位置是全书偏移，用窗口长度(_content.length)裁剪会把
        // 位置压回窗口起点附近（几十万字的大书变成几万字），下次打开直接回到开头。
        val contentLen = effectiveTotalChars()
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
        rangeStart: Int? = null,
        rangeEnd: Int? = null,
    ): Boolean? = toggleBookmarkAtSwipeInternal(previewForAdd, positionForAdd, rangeStart, rangeEnd)

    private suspend fun toggleBookmarkAtSwipeInternal(
        previewForAdd: String? = null,
        positionForAdd: Int? = null,
        rangeStart: Int? = null,
        rangeEnd: Int? = null,
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
        // 只在「当前可见范围」内取消书签；不再用大字符窗口去重，
        // 否则翻 1-2 页后加书签会把上一页的书签删掉。
        val near = findBookmarkToToggle(
            bookmarks = bookmarks.value,
            position = pos,
            rangeStart = rangeStart?.coerceAtLeast(0),
            rangeEnd = rangeEnd?.coerceAtLeast(0),
        )

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
            if (!format.isCode &&
                (format.isPdf || PdfReaderContent.looksLikePdfBody(content))
            ) {
                return@let null
            }
            val toc = if (format.isCode) {
                emptyList()
            } else {
                _structuredToc.value?.takeIf { it.isNotEmpty() }
                    ?: if (format.usesReaderPlainBody) {
                        parsePlainTextToc(content)
                    } else {
                        parseMarkdownToc(content)
                    }
            }
            captureTextAnchor(
                document = content,
                plainText = format.usesReaderPlainBody || format.isCode,
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

    fun setShowTocInLandscape(show: Boolean) {
        viewModelScope.launch { readerSettingsRepository.setShowTocInLandscape(show) }
    }

    fun setCodeBlockWrap(enabled: Boolean) {
        viewModelScope.launch {
            readerSettingsRepository.setCodeBlockWrap(enabled)
        }
    }

    fun setCodeLineNumbers(show: Boolean) {
        viewModelScope.launch {
            readerSettingsRepository.setCodeLineNumbers(show)
        }
    }

    fun setCodeHighlightTheme(theme: CodeHighlightTheme) {
        viewModelScope.launch {
            readerSettingsRepository.setCodeHighlightTheme(theme)
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

/** 距窗口末尾不足该字符数时追加下一批正文（窗口模式）。 */
private const val WINDOW_APPEND_THRESHOLD_CHARS = 8000
