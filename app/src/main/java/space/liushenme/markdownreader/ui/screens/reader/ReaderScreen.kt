package space.liushenme.markdownreader.ui.screens.reader

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.text.Spannable
import android.text.SpannableString
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.text.method.LinkMovementMethod
import android.view.View
import android.widget.TextView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.graphics.ColorUtils
import androidx.core.text.PrecomputedTextCompat
import androidx.core.view.WindowCompat
import androidx.core.widget.TextViewCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import space.liushenme.markdownreader.R
import space.liushenme.markdownreader.document.DocumentFormat
import space.liushenme.markdownreader.data.local.entity.HighlightEntity
import space.liushenme.markdownreader.importing.ImportedBookFormat
import space.liushenme.markdownreader.importing.ParsedBookStorage
import space.liushenme.markdownreader.importing.PdfReaderContent
import space.liushenme.markdownreader.markdown.MarkdownLinkDispatcher
import space.liushenme.markdownreader.model.HighlightStyle
import space.liushenme.markdownreader.model.ReaderPageTurnMode
import space.liushenme.markdownreader.ui.screens.reader.anchor.AnnotationPaintPlan
import space.liushenme.markdownreader.ui.screens.reader.anchor.planAnnotationAnchors
import space.liushenme.markdownreader.ui.components.ShelfStyleStatusBarBackdrop
import space.liushenme.markdownreader.ui.components.ShelfStyleSystemBarsEffect
import space.liushenme.markdownreader.ui.components.iconTintForDeleteStrip
import space.liushenme.markdownreader.ui.theme.MarkdownReaderTheme
import space.liushenme.markdownreader.ui.theme.ReadingTheme
import space.liushenme.markdownreader.ui.layout.appWindowWidthClass
import space.liushenme.markdownreader.ui.layout.usesWideReaderNavigation
import io.noties.markwon.Markwon
import io.noties.markwon.core.CorePlugin
import io.noties.markwon.core.spans.HeadingSpan
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.latex.JLatexMathPlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.image.ImagesPlugin
import io.noties.markwon.image.file.FileSchemeHandler
import io.noties.markwon.inlineparser.MarkwonInlineParserPlugin
import io.noties.markwon.linkify.LinkifyPlugin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt


@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ReaderScreen(
    navController: NavController,
    bookId: Long,
    jumpKind: String? = null,
    jumpPosition: Int? = null,
    jumpHighlightId: Long? = null,
    jumpBookmarkId: Long? = null,
    jumpPreview: String? = null,
    viewModel: ReaderViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val book = uiState.book
    val content = uiState.content
    val bookmarks = uiState.bookmarks
    val highlights = uiState.highlights
    val readerLoadEpoch = uiState.readerLoadEpoch
    val currentTheme = uiState.currentTheme
    val readingStyleState = uiState.readingStyleState
    val fontSize = uiState.fontSize
    val readerPaddingDp = uiState.readerPaddingDp
    val readerLineSpacingMultiplier = uiState.readerLineSpacingMultiplier
    val codeBlockWrap = uiState.codeBlockWrap
    val hideSystemBarsPref = uiState.hideSystemBars
    val loadError = uiState.loadError
    val pageTurnMode = uiState.pageTurnMode
    val windowSize = LocalWindowInfo.current.containerSize
    val windowDensity = LocalDensity.current
    val windowWidthDp = with(windowDensity) { windowSize.width.toDp().value.roundToInt() }
    val windowHeightDp = with(windowDensity) { windowSize.height.toDp().value.roundToInt() }

    val documentFormat = uiState.documentSnapshot?.document?.format
    val importFormat = book?.let { ImportedBookFormat.fromStored(it.importFormat) }
    val isPdfBook = documentFormat == DocumentFormat.Pdf ||
        importFormat?.isPdf == true ||
        PdfReaderContent.looksLikePdfBody(content)
    val renderPlainText = documentFormat == DocumentFormat.PlainText ||
        importFormat?.usesReaderPlainBody == true
    val readerContent = remember(content, isPdfBook) {
        if (isPdfBook) PdfReaderContent.sanitizeStoredBody(content) else content
    }
    val pdfPages = remember(readerContent, isPdfBook, bookId) {
        if (!isPdfBook) {
            emptyList()
        } else {
            PdfPageCatalog.parse(readerContent, ParsedBookStorage.bundleDir(context, bookId))
        }
    }
    val pdfRestoreAnchor = remember(readerContent, readerLoadEpoch, pdfPages) {
        PdfPageCatalog.decodeProgress(
            pdfPages,
            viewModel.readingCharPosForRestore(),
            readerContent.length,
        )
    }
    var pdfViewportAnchor by remember(readerContent, readerLoadEpoch) {
        mutableStateOf(pdfRestoreAnchor)
    }
    var pdfAtEnd by remember(readerContent) { mutableStateOf(false) }
    var pdfJumpGeneration by remember(readerContent, readerLoadEpoch) { mutableIntStateOf(0) }
    var pdfJumpRequest by remember(readerContent, readerLoadEpoch) {
        mutableStateOf<PdfJumpRequest?>(null)
    }
    val readerHorizontalPaddingDp = if (isPdfBook) 0 else readerPaddingDp
    val readerBodyLineSpacing = if (isPdfBook) 1f else readerLineSpacingMultiplier

    var showReaderSettingsSheet by remember { mutableStateOf(false) }
    var showBookmarks by remember { mutableStateOf(false) }
    var showToc by remember { mutableStateOf(false) }
    var pendingWideTocEntry by remember { mutableStateOf<MarkdownTocEntry?>(null) }
    var showTopBar by remember { mutableStateOf(false) }
    var readerTextSelectionActive by remember { mutableStateOf(false) }
    var externalAnnotationConsumed by remember(bookId, jumpKind, jumpPosition, jumpHighlightId, jumpBookmarkId) {
        mutableStateOf(false)
    }
    var pendingHighlightSelection by remember {
        mutableStateOf<PendingHighlightPicker?>(null)
    }
    var diagramPreviewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var pdfPageZoomed by remember { mutableStateOf(false) }

    val lastHighlightColorArgb = uiState.lastHighlightColorArgb
    val lastHighlightStyle = uiState.lastHighlightStyle

    val snackbarHostState = remember { SnackbarHostState() }
    val annotationLocatingMessage = stringResource(R.string.reader_annotation_locating)
    val annotationLocatedMessage = stringResource(R.string.reader_annotation_located)
    val annotationNearbyMessage = stringResource(R.string.reader_annotation_nearby)
    val readerTextView = remember { mutableStateOf<TextView?>(null) }

    val immersiveReading = content.isNotEmpty() && loadError == null
    val readerChromeVisible = immersiveReading && showTopBar && !readerTextSelectionActive

    val onReaderTextSelectionActiveChange: (Boolean) -> Unit = { active ->
        readerTextSelectionActive = active
        if (active) {
            showTopBar = false
        } else {
            // 选区结束时只收起划线浮窗。草稿已在点击「划线」时同步绘制并发起落库，
            // 不再依赖随时可能被关闭的浮窗 effect。
            pendingHighlightSelection = null
        }
    }

    val structuredToc = uiState.structuredToc

    val tocEntries = remember(readerContent, renderPlainText, structuredToc, isPdfBook, context) {
        if (isPdfBook) {
            return@remember PdfReaderContent.tocEntriesFromBody(readerContent, context).map {
                MarkdownTocEntry(level = it.level, title = it.title, sourceOffset = it.sourceOffset, rawTitle = it.rawTitle)
            }
        }
        val stored = structuredToc.orEmpty()
        when {
            // ViewModel 打开时已按最终正文对齐过 sourceOffset；空则现场解析。
            renderPlainText -> stored.ifEmpty { parsePlainTextToc(readerContent) }
            stored.isNotEmpty() -> stored
            readerContent.isNotEmpty() -> parseMarkdownToc(readerContent)
            else -> emptyList()
        }
    }
    val emptyTocMessage = if (renderPlainText) {
        stringResource(R.string.reader_toc_empty_plain_text)
    } else {
        stringResource(R.string.reader_toc_empty_markdown)
    }
    val readingTitleFallback = stringResource(R.string.reader_title_reading)
    val snackbarBookmarkAdded = stringResource(R.string.snackbar_bookmark_added)
    val snackbarBookmarkRemoved = stringResource(R.string.snackbar_bookmark_removed)

    LaunchedEffect(viewModel, bookId, snackbarBookmarkAdded, snackbarBookmarkRemoved) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is ReaderEffect.BookLoaded -> if (effect.bookId == bookId) showTopBar = false
                is ReaderEffect.LoadFailed -> Unit
                is ReaderEffect.BookmarkToggled -> snackbarHostState.showBriefSnackbar(
                    if (effect.added) snackbarBookmarkAdded else snackbarBookmarkRemoved,
                )
            }
        }
    }

    val totalChars = book?.totalChars?.takeIf { it > 0 } ?: content.length.coerceAtLeast(1)

    val pageSpecs = remember(
        readerContent,
        isPdfBook,
        pageTurnMode,
        fontSize,
        windowHeightDp,
        windowWidthDp,
    ) {
        when {
            pageTurnMode == ReaderPageTurnMode.VerticalScroll || isPdfBook -> emptyList()
            else -> splitMarkdownToPages(
                readerContent,
                estimateTargetCharsPerPage(fontSize, windowHeightDp, windowWidthDp),
            )
        }
    }
    val pageTextViews = remember(content) { mutableMapOf<Int, TextView>() }
    val pagerState = rememberPagerState(
        pageCount = { pageSpecs.size.coerceAtLeast(1) },
    )

    // ===== 章节惰性渲染窗口（仅 VerticalScroll 模式生效）=====
    val documentChunkIndex = uiState.documentSnapshot?.chunkIndex
    val chapterBoundaries = remember(readerContent, tocEntries, documentChunkIndex) {
        val chapter = computeChapterBoundaries(readerContent, tocEntries)
        val blockAligned: IntArray = documentChunkIndex?.boundaries ?: intArrayOf()
        (chapter.toList() + blockAligned.toList()).distinct().sorted().toIntArray()
    }
    // 正文一到齐就同步算出首窗，避免先 (0,0) 空 setText 再整窗二次渲染。
    val initialDisplayWindow = remember(
        readerContent,
        readerLoadEpoch,
        pageTurnMode,
        chapterBoundaries,
    ) {
        if (readerContent.isEmpty()) {
            0 to 0
        } else if (pageTurnMode != ReaderPageTurnMode.VerticalScroll) {
            0 to readerContent.length
        } else {
            val targetChar = viewModel.readingCharPosForRestore().coerceIn(
                0,
                (readerContent.length - 1).coerceAtLeast(0),
            )
            computeReadingWindow(chapterBoundaries, targetChar, readerContent.length)
        }
    }
    var displayWindowStartChar by remember(readerContent, readerLoadEpoch) {
        mutableIntStateOf(initialDisplayWindow.first)
    }
    var displayWindowEndChar by remember(readerContent, readerLoadEpoch) {
        mutableIntStateOf(initialDisplayWindow.second)
    }
    /** 向上/向下扩窗后，按全书字符锚点恢复视口，避免跳到章节顶部。 */
    var pendingScrollRestoreGlobalChar by remember(readerContent) { mutableStateOf<Int?>(null) }
    var pendingScrollRestoreBookmarkPreview by remember(readerContent) { mutableStateOf<String?>(null) }
    var pendingScrollRestoreHighlightId by remember(readerContent) { mutableStateOf<Long?>(null) }
    /** PDF 垂直滚动：按页码恢复视口（源码下标与 TextView 内下标不一致）。 */
    var pendingScrollRestorePdfPageIndex by remember(readerContent) { mutableStateOf<Int?>(null) }
    /** 扩窗/异步 layout 后保留子像素 scroll，避免 snap 到行顶；目录/书签跳转仍为整行对齐。 */
    var pendingScrollRestoreAnchor by remember(readerContent) { mutableStateOf<TextViewScrollAnchor?>(null) }
    var pendingScrollRestoreSnapToLine by remember(readerContent) { mutableStateOf(true) }
    /**
     * 打开书/书签 snap 完成前用主题色遮住正文，避免异步渲染期间以 scrollY=0 露出窗口开头（更早章节）。
     * 独立于 [pendingScrollRestoreGlobalChar]：滚动前须先清 pending，否则 onScroll 会误取消 restore。
     */
    // 有正文时先遮罩，等首帧定位完成再揭开（与同步首窗配套，避免露出章节开头）
    var coverUntilPositionRestore by remember(readerContent, readerLoadEpoch, pageTurnMode, isPdfBook) {
        mutableStateOf(
            readerContent.isNotEmpty() &&
                (isPdfBook || pageTurnMode == ReaderPageTurnMode.VerticalScroll),
        )
    }
    /** 每次请求滚动恢复时递增，避免已取消的 LaunchedEffect 在 tv.post 中仍 snap 到旧锚点。 */
    var scrollRestoreGeneration by remember(readerContent) { mutableIntStateOf(0) }
    /** 与 [scrollRestoreGeneration] 同步，供 tv.post 读取（避免旧 restore 覆盖 tag 后误执行 snap）。 */
    val scrollRestoreGenRef = remember(readerContent) { intArrayOf(0) }
    fun bumpScrollRestoreGeneration() {
        scrollRestoreGeneration++
        scrollRestoreGenRef[0] = scrollRestoreGeneration
    }
    fun queueScrollRestore(globalChar: Int?) {
        bumpScrollRestoreGeneration()
        pendingScrollRestoreGlobalChar = globalChar
    }

    /** 打开书/书签：把 snap 意图写到 TextView，渲染完成同帧定位，避免先画窗口开头。 */
    fun stashSavedPositionSnapForPendingRestore(
        sourceOffset: Int,
        windowStart: Int,
        windowEnd: Int,
        preview: String?,
        highlightId: Long? = null,
    ) {
        val safeStart = windowStart.coerceIn(0, readerContent.length)
        val safeEnd = windowEnd.coerceIn(safeStart, readerContent.length)
        val expectedRenderSig = readerContentSignature(
            content = readerContent.substring(safeStart, safeEnd),
            renderPlainText = renderPlainText,
            themeName = currentTheme.contentSignature(),
            fontSize = fontSize,
            codeBlockWrap = codeBlockWrap,
        )
        stashPendingSavedPositionSnap(
            readerTextView.value,
            PendingSavedPositionSnap(
                sourceContent = readerContent,
                sourceOffset = sourceOffset,
                windowStart = safeStart,
                windowEnd = safeEnd,
                preview = preview,
                renderPlainText = renderPlainText,
                tocEntries = tocEntries,
                highlightId = highlightId,
                expectedRenderSig = expectedRenderSig,
            ),
        )
    }

    val displayedContent = remember(readerContent, displayWindowStartChar, displayWindowEndChar) {
        when {
            readerContent.isEmpty() -> ""
            displayWindowStartChar >= readerContent.length -> ""
            displayWindowEndChar <= displayWindowStartChar -> ""
            displayWindowEndChar >= readerContent.length -> readerContent.substring(displayWindowStartChar)
            else -> readerContent.substring(displayWindowStartChar, displayWindowEndChar)
        }
    }
    val annotationAnchors = remember(
        highlights,
        bookmarks,
        readerContent,
        book?.contentHash,
        tocEntries,
        renderPlainText,
        isPdfBook,
    ) {
        if (isPdfBook || readerContent.isEmpty()) {
            AnnotationPaintPlan(
                paintHighlights = highlights,
                pendingHighlightIds = emptySet(),
                pendingBookmarkIds = emptySet(),
                highlightJump = emptyMap(),
                bookmarkJump = emptyMap(),
            )
        } else {
            planAnnotationAnchors(
                document = readerContent,
                plainText = renderPlainText,
                docHash = book?.contentHash.orEmpty(),
                toc = tocEntries,
                highlights = highlights,
                bookmarks = bookmarks,
            )
        }
    }
    val displayedHighlights = remember(
        annotationAnchors.paintHighlights,
        displayWindowStartChar,
        displayWindowEndChar,
        displayedContent,
    ) {
        highlightsForPageSlice(
            displayWindowStartChar,
            displayWindowEndChar,
            annotationAnchors.paintHighlights,
            displayedContent
        )
    }

    val activeHighlightContent = remember(
        pageTurnMode,
        pageSpecs,
        pagerState.currentPage,
        readerContent,
        displayedContent,
    ) {
        if (pageTurnMode == ReaderPageTurnMode.VerticalScroll || pageSpecs.isEmpty()) {
            displayedContent
        } else {
            pageSlice(readerContent, pageSpecs[pagerState.currentPage.coerceIn(0, pageSpecs.lastIndex)])
        }
    }
    val activeDisplayedHighlights = remember(
        pageTurnMode,
        pageSpecs,
        pagerState.currentPage,
        annotationAnchors.paintHighlights,
        activeHighlightContent,
        displayedHighlights,
    ) {
        if (pageTurnMode == ReaderPageTurnMode.VerticalScroll || pageSpecs.isEmpty()) {
            displayedHighlights
        } else {
            val pageIdx = pagerState.currentPage.coerceIn(0, pageSpecs.lastIndex)
            val globalStart = pageSpecs[pageIdx].first
            val globalEnd = if (pageIdx + 1 < pageSpecs.size) pageSpecs[pageIdx + 1].first else Int.MAX_VALUE
            highlightsForPageSlice(
                globalStart,
                globalEnd,
                annotationAnchors.paintHighlights,
                activeHighlightContent,
            )
        }
    }

    // 取消选区后 / TextView 就绪后按已落库列表重刷划线；key 含 tv，避免划线先到、View 未就绪时永久漏刷
    LaunchedEffect(
        readerTextSelectionActive,
        activeDisplayedHighlights,
        activeHighlightContent,
        currentTheme,
        readerTextView.value,
    ) {
        if (readerTextSelectionActive) return@LaunchedEffect
        val tv = readerTextView.value ?: return@LaunchedEffect
        (tv as? SafeReaderTextView)?.retainedHighlights = highlights
        syncReaderPendingHighlights(
            textView = tv,
            highlights = activeDisplayedHighlights,
            highlightColorArgb = currentTheme.highlightColor.toArgb(),
            sourceContentLength = activeHighlightContent.length,
            sourceText = activeHighlightContent,
        )
        // 等一帧，避免与 ActionMode 销毁抢同一遍 span 更新
        kotlinx.coroutines.delay(16)
        if (readerTextSelectionActive) return@LaunchedEffect
        if (readerTextView.value !== tv) return@LaunchedEffect
        refreshReaderHighlightSpans(
            textView = tv,
            highlights = activeDisplayedHighlights,
            highlightColorArgb = currentTheme.highlightColor.toArgb(),
            sourceContentLength = activeHighlightContent.length,
            highlightSig = readerHighlightSignature(activeDisplayedHighlights),
            sourceText = activeHighlightContent,
        )
    }

    fun currentPageLocalHighlights(): List<HighlightEntity> {
        return activeDisplayedHighlights
    }

    fun resolveExistingHighlightId(
        text: String,
        displayedStart: Int,
        displayedEnd: Int,
    ): Long? = findHighlightIdForDisplayedSelection(
        pageLocalHighlights = currentPageLocalHighlights(),
        text = text,
        displayedStart = displayedStart,
        displayedEnd = displayedEnd,
    )

    fun currentSourceWindow(): Pair<Int, Int> {
        val contentLen = readerContent.length
        if (contentLen <= 0) return 0 to 0
        if (pageTurnMode != ReaderPageTurnMode.VerticalScroll && pageSpecs.isNotEmpty()) {
            val pageIdx = pagerState.currentPage.coerceIn(0, pageSpecs.lastIndex)
            val windowStart = pageSpecs[pageIdx].first
            val windowEnd = if (pageIdx + 1 < pageSpecs.size) {
                pageSpecs[pageIdx + 1].first
            } else {
                contentLen
            }
            return windowStart to windowEnd
        }
        return displayWindowStartChar to displayWindowEndChar.coerceAtMost(contentLen)
    }

    fun estimateHighlightSourceStart(displayedStart: Int): Int {
        val contentLen = readerContent.length
        if (contentLen <= 0) return 0
        val (windowStart, windowEnd) = currentSourceWindow()
        return resolveSourceCharOffset(
            sourceContent = readerContent,
            windowStart = windowStart,
            windowEnd = windowEnd,
            displayedText = readerTextView.value?.text,
            renderedOffset = displayedStart,
            renderPlainText = renderPlainText,
            tocEntries = tocEntries,
        )
    }

    val onHighlightMenuClick: (String, Int, Int, android.graphics.Rect) -> Unit =
        { selected, start, end, bounds ->
            // 公式选区底层曾是 \uFFFC，extract 后应已是 $…$；仍要求非空白，避免空划线。
            normalizeDisplayedSelection(selected, start, end)?.let { normalized ->
                val draftId = nextReaderDraftHighlightId()
                val ownerTextView = readerTextView.value as? SafeReaderTextView
                val initialColor = Color(lastHighlightColorArgb)
                val pending = PendingHighlightPicker(
                    text = normalized.text,
                    displayedStart = normalized.start,
                    displayedEnd = normalized.end,
                    boundsInWindow = bounds,
                    draftHighlightId = draftId,
                    ownerTextView = ownerTextView,
                    color = initialColor,
                    style = lastHighlightStyle,
                )
                // HyperOS 可能在菜单点击后立即销毁 ActionMode。草稿必须在同一调用栈内
                // 完成注册和首次绘制，否则点击区外触发的刷新会先把它清掉。
                ownerTextView?.let { tv ->
                    tv.highlightStylePickerShowing = true
                    tv.registerDraftHighlight(draftId)
                    applyHighlightDecorationAtRange(
                        textView = tv,
                        start = normalized.start,
                        end = normalized.end,
                        colorArgb = lastHighlightColorArgb,
                        style = lastHighlightStyle,
                        highlightId = draftId,
                    )
                }
                pendingHighlightSelection = pending

                // 落库同样在点击回调中启动，不将其生命周期绑到可能马上关闭的样式浮窗。
                val sourceWindow = currentSourceWindow()
                val sourceHint = estimateHighlightSourceStart(normalized.start)
                // 代码块在 TextView 外层只占一个 \uFFFC；优先使用 ReplacementSpan
                // 内部选区映射源码，避免通用展示层 offset 找不到代码正文而导致只画草稿、不落库。
                val sourceSpan = ownerTextView?.let { tv ->
                    sourceSpanForCodeBlockSelection(
                        textView = tv,
                        source = readerContent,
                        selectedText = normalized.text,
                        sourceHint = sourceHint,
                        searchStart = sourceWindow.first,
                        searchEnd = sourceWindow.second,
                    )
                }?.let { span ->
                    ResolvedSourceSelectionSpan(
                        start = span.first,
                        end = span.second,
                        hint = span.first,
                        fromHeading = false,
                    )
                } ?: resolveSourceSpanForDisplayedSelection(
                    displayed = ownerTextView?.text,
                    displayedStart = normalized.start,
                    displayedEnd = normalized.end,
                    selectedText = normalized.text,
                    source = readerContent,
                    renderPlainText = renderPlainText,
                    windowStart = sourceWindow.first,
                    windowEnd = sourceWindow.second,
                    toc = tocEntries,
                )
                viewModel.addHighlightFromSelection(
                    selectedText = normalized.text,
                    color = initialColor,
                    style = lastHighlightStyle,
                    sourceStartHint = sourceSpan?.hint ?: sourceHint,
                    forcedSourceSpan = sourceSpan?.let { it.start to it.end },
                    sourceSearchRange = sourceWindow.first until sourceWindow.second,
                ) { id ->
                    pending.persistedHighlightId = id
                    val owner = pending.ownerTextView
                    val draftWasCancelled = owner?.consumeDraftHighlightCancellation(draftId) == true
                    if (draftWasCancelled) {
                        removeReaderHighlightDecorationById(owner, draftId)
                        owner.unregisterDraftHighlight(draftId)
                        if (id != null) viewModel.dispatch(ReaderEvent.DeleteHighlightById(id))
                        owner.clearPendingHighlightMenuIfUnbound()
                        return@addHighlightFromSelection
                    }
                    if (id != null) {
                        owner?.let {
                            rebindReaderHighlightDecorationId(
                                textView = it,
                                oldId = draftId,
                                newId = id,
                            )
                            it.unregisterDraftHighlight(draftId)
                        }
                        viewModel.dispatch(
                            ReaderEvent.UpdateHighlightAppearance(
                                highlightId = id,
                                colorArgb = if (pending.color.alpha < 0.06f) {
                                    lastHighlightColorArgb
                                } else {
                                    pending.color.toArgb()
                                },
                                style = pending.style,
                            )
                        )
                        if (pendingHighlightSelection === pending &&
                            owner?.isInTextSelection() == true
                        ) {
                            owner.bindSelectionHighlightId(
                                highlightId = id,
                                displayedStart = normalized.start,
                                displayedEnd = normalized.end,
                            )
                        } else {
                            owner?.clearPendingHighlightMenuIfUnbound()
                        }
                    } else {
                        owner?.let {
                            removeReaderHighlightDecorationById(it, draftId)
                            it.unregisterDraftHighlight(draftId)
                        }
                        owner?.clearPendingHighlightMenuIfUnbound()
                    }
                }
            }
        }

    val onRemoveHighlightClick: (Long) -> Unit = { highlightId ->
        val pending = pendingHighlightSelection
        val tv = pending?.ownerTextView ?: (readerTextView.value as? SafeReaderTextView)
        tv?.highlightStylePickerShowing = false
        val cancelledDraftId = pending?.draftHighlightId
            ?.takeIf { highlightId <= 0L }
        if (cancelledDraftId != null) {
            tv?.cancelDraftHighlight(cancelledDraftId)
            tv?.let { removeReaderHighlightDecorationById(it, cancelledDraftId) }
        }
        pendingHighlightSelection = null
        if (highlightId > 0L) {
            highlights.find { it.id == highlightId }?.let {
                viewModel.dispatch(ReaderEvent.DeleteHighlight(it))
            }
        }
        tv?.bindSelectionHighlightId(null, 0, 0)
    }

    fun currentTopGlobalChar(): Int? {
        if (readerContent.isEmpty()) return null
        if (isPdfBook && pdfPages.isNotEmpty()) {
            return PdfPageCatalog.encodeProgress(
                pages = pdfPages,
                pageIndex = pdfViewportAnchor.pageIndex,
                fractionInPage = pdfViewportAnchor.fractionInPage,
                contentLength = readerContent.length,
            )
        }
        val tv = readerTextView.value
        if (pageTurnMode != ReaderPageTurnMode.VerticalScroll && pageSpecs.isNotEmpty()) {
            val page = pagerState.currentPage.coerceIn(0, pageSpecs.lastIndex)
            val pageStart = pageSpecs[page].first
            if (isPdfBook) return pageStart.coerceIn(0, readerContent.length)
            val pageEnd = pageSpecs.getOrNull(page + 1)?.first ?: readerContent.length
            return globalSourceCharAtTextViewTop(
                sourceContent = readerContent,
                windowStart = pageStart,
                windowEnd = pageEnd,
                textView = tv,
                renderPlainText = renderPlainText,
                tocEntries = tocEntries,
            )
        }
        if (isPdfBook) {
            resolvePdfSourceOffsetAtTextViewTop(
                textView = tv,
                tocEntries = tocEntries,
                windowStart = displayWindowStartChar,
            )?.let { return it.coerceIn(0, readerContent.length) }
        }
        return globalSourceCharAtTextViewTop(
            sourceContent = readerContent,
            windowStart = displayWindowStartChar,
            windowEnd = displayWindowEndChar,
            textView = tv,
            renderPlainText = renderPlainText,
            tocEntries = tocEntries,
        )
    }

    fun currentBottomGlobalChar(): Int? {
        if (readerContent.isEmpty()) return currentTopGlobalChar()
        if (isPdfBook) return currentTopGlobalChar()
        val tv = readerTextView.value
        if (pageTurnMode != ReaderPageTurnMode.VerticalScroll && pageSpecs.isNotEmpty()) {
            val page = pagerState.currentPage.coerceIn(0, pageSpecs.lastIndex)
            val pageStart = pageSpecs[page].first
            val pageEnd = pageSpecs.getOrNull(page + 1)?.first ?: readerContent.length
            if (tv == null) return pageEnd.coerceIn(0, readerContent.length)
            return globalSourceCharAtTextViewBottom(
                sourceContent = readerContent,
                windowStart = pageStart,
                windowEnd = pageEnd,
                textView = tv,
                renderPlainText = renderPlainText,
                tocEntries = tocEntries,
            )
        }
        return globalSourceCharAtTextViewBottom(
            sourceContent = readerContent,
            windowStart = displayWindowStartChar,
            windowEnd = displayWindowEndChar,
            textView = tv,
            renderPlainText = renderPlainText,
            tocEntries = tocEntries,
        )
    }

    fun currentDisplayWindow(): Pair<Int, Int> {
        if (pageTurnMode != ReaderPageTurnMode.VerticalScroll && pageSpecs.isNotEmpty()) {
            val page = pagerState.currentPage.coerceIn(0, pageSpecs.lastIndex)
            val start = pageSpecs[page].first
            val end = pageSpecs.getOrNull(page + 1)?.first ?: readerContent.length
            return start to end
        }
        return displayWindowStartChar to displayWindowEndChar
    }

    fun currentChapterEntryNow(): MarkdownTocEntry? {
        if (isPdfBook || tocEntries.isEmpty()) return null
        val top = currentTopGlobalChar() ?: return null
        val bottom = currentBottomGlobalChar() ?: top
        val window = currentDisplayWindow()
        return currentChapterEntryFromDisplayedViewport(
            tocEntries = tocEntries,
            textView = readerTextView.value,
            windowStart = window.first,
            windowEnd = window.second,
            fallbackTopChar = top,
            fallbackBottomChar = bottom,
        )
    }

    fun documentReachedEnd(): Boolean {
        if (readerContent.isEmpty()) return false
        if (isPdfBook && pdfPages.isNotEmpty()) {
            return pdfAtEnd
        }
        if (pageTurnMode != ReaderPageTurnMode.VerticalScroll && pageSpecs.isNotEmpty()) {
            return pagerState.currentPage >= pageSpecs.lastIndex
        }
        val tv = readerTextView.value ?: return false
        return isReaderTextViewAtScrollBottom(tv) && displayWindowEndChar >= readerContent.length
    }

    fun reachedEndForChar(charPos: Int): Boolean {
        if (readerContent.isEmpty()) return false
        if (isPdfBook && pdfPages.isNotEmpty()) {
            val lastStart = pdfPages.last().sourceOffset
            return charPos >= lastStart && pdfAtEnd
        }
        if (pageTurnMode != ReaderPageTurnMode.VerticalScroll && pageSpecs.isNotEmpty()) {
            val lastStart = pageSpecs.last().first
            return charPos >= lastStart
        }
        return charPos >= (readerContent.length - 1).coerceAtLeast(0) &&
            displayWindowEndChar >= readerContent.length
    }

    fun publishVisibleReadingProgress() {
        val estimated = currentTopGlobalChar() ?: return
        viewModel.updateVisibleReadingProgress(
            estimated,
            documentReachedEnd(),
            currentBottomGlobalChar(),
            currentChapterEntryNow()?.sourceOffset,
        )
    }

    LaunchedEffect(readerLoadEpoch, pageTurnMode) {
        viewModel.setFinishPromptEnabled(false)
    }

    LaunchedEffect(coverUntilPositionRestore, pageTurnMode, readerContent, readerLoadEpoch, isPdfBook) {
        if (!isPdfBook && pageTurnMode != ReaderPageTurnMode.VerticalScroll) return@LaunchedEffect
        if (readerContent.isEmpty()) return@LaunchedEffect
        if (!coverUntilPositionRestore) {
            viewModel.setFinishPromptEnabled(true)
            viewModel.onDocumentEndChanged(documentReachedEnd())
        }
    }

    // 与 TAG_READER_RENDER_SIG / reader_markdown_render_complete 一致（不含划线）。
    // 切勿用 readerRenderSignature：划线变化会改签名，await 永远对不上 → 遮罩空等约 6.4s。
    val displayedContentSig = remember(
        displayedContent,
        renderPlainText,
        currentTheme,
        fontSize,
        codeBlockWrap,
    ) {
        readerContentSignature(
            content = displayedContent,
            renderPlainText = renderPlainText,
            themeName = currentTheme.contentSignature(),
            fontSize = fontSize,
            codeBlockWrap = codeBlockWrap,
        )
    }

    // 仅打开/换书/切翻页模式时恢复横向页码；勿监听 readingProgress / currentPosition，避免滚动存盘后被二次拉回。
    LaunchedEffect(
        bookId,
        readerContent,
        pageSpecs,
        readerLoadEpoch,
        pageTurnMode,
        isPdfBook,
    ) {
        if (readerContent.isEmpty()) return@LaunchedEffect
        if (isPdfBook || pageTurnMode == ReaderPageTurnMode.VerticalScroll || pageSpecs.isEmpty()) {
            return@LaunchedEffect
        }
        val charPos = viewModel.readingCharPosForRestore().coerceIn(
            0,
            (readerContent.length - 1).coerceAtLeast(0),
        )
        jumpToGlobalCharInPager(
            scope = this,
            charPos = charPos,
            contentLen = readerContent.length,
            sourceContent = readerContent,
            renderPlainText = renderPlainText,
            tocEntries = tocEntries,
            bookmarkPreviewText = viewModel.readingPreviewForRestore(),
            pageSpecs = pageSpecs,
            pagerState = pagerState,
            pageTextViews = pageTextViews,
            assignActiveTextView = { readerTextView.value = it },
            onProgress = {
                viewModel.updateReadingProgressAtChar(
                    charPos,
                    viewModel.readingPreviewForRestore(),
                    reachedEndForChar(charPos),
                )
            },
            themeSignature = currentTheme.contentSignature(),
            fontSize = fontSize,
            codeBlockWrap = codeBlockWrap,
        )
        viewModel.setFinishPromptEnabled(true)
        viewModel.onDocumentEndChanged(reachedEndForChar(charPos))
    }

    LaunchedEffect(pagerState.currentPage, pageTurnMode, content, isPdfBook) {
        if (isPdfBook || pageTurnMode == ReaderPageTurnMode.VerticalScroll) return@LaunchedEffect
        readerTextView.value = pageTextViews[pagerState.currentPage]
    }

    LaunchedEffect(pageTurnMode, pageSpecs, pagerState, totalChars) {
        if (pageTurnMode == ReaderPageTurnMode.VerticalScroll || pageSpecs.isEmpty()) return@LaunchedEffect
        snapshotFlow { pagerState.currentPage }.distinctUntilChanged().collect { page ->
            val start = pageSpecs.getOrNull(page)?.first ?: return@collect
            val end = pageSpecs.getOrNull(page + 1)?.first ?: readerContent.length
            viewModel.updateReadingProgressAtChar(
                start,
                reachedEnd = page >= pageSpecs.lastIndex,
                bottomChar = end,
                chapterOffset = currentChapterEntryNow()?.sourceOffset,
            )
        }
    }

    val density = LocalDensity.current
    val hideChromeScrollThresholdPx = remember(density) {
        with(density) { ReaderHideChromeScrollThreshold.roundToPx() }
    }
    var scrollAccumForHideChrome by remember { mutableIntStateOf(0) }
    var lastScrollProgressSaveMs by remember { mutableLongStateOf(0L) }
    /** 最近一次滚动保存的全书字符锚点；退出时 TextView 不可用则作 fallback。 */
    var lastScrollTopGlobalChar by remember(readerContent) { mutableIntStateOf(-1) }
    /** 惰性扩窗防抖：连续滑动时合并为一次，避免边滑边整段重排 Markwon。 */
    var expandWindowDownToken by remember { mutableIntStateOf(0) }
    var expandWindowUpToken by remember { mutableIntStateOf(0) }
    /**
     * 跳转后台预扩窗目标：目录跳转用 single_top 窗口（上方零缓冲），下滑看前文必然撞硬顶+等异步整窗重渲染。
     * 跳转渲染完成后自动向上扩一次并把目标章节精确保持在顶部（视觉不动），使前文提前就绪、下滑即丝滑。
     * -1 表示无待预取。
     */
    var prefetchUpTargetChar by remember(readerContent) { mutableIntStateOf(-1) }
    /** 扩窗或锚点恢复完成前不再触发新扩窗，避免 token 风暴导致 LaunchedEffect 永不执行。 */
    var windowExpandInFlight by remember(readerContent) { mutableStateOf(false) }
    /**
     * 同步守卫（非 Compose state）：扩窗触发后到恢复完成前禁止取消 pending restore。
     * 同一下滑手势会在正文替换后继续派发 onScroll；若用 Compose state 判断会有一帧延迟，
     * 误取消恢复后 scrollY 停在 0，表现为跳回开头。
     */
    val expandRestoreGuard = remember(readerContent) { booleanArrayOf(false) }

    /** TextView 侧 snap/置顶已完成后揭罩，并取消仍在跑的 Compose restore，避免二次滚动。 */
    fun onReaderOpenPositionReady() {
        if (!coverUntilPositionRestore &&
            pendingScrollRestoreGlobalChar == null &&
            pendingScrollRestorePdfPageIndex == null &&
            pendingScrollRestoreBookmarkPreview == null &&
            pendingScrollRestoreHighlightId == null
        ) {
            return
        }
        coverUntilPositionRestore = false
        readerOpenDbg("cover=false reason=openPositionReady")
        // 仅取消打开/书签类 snap restore；扩窗 restore（snapToLine=false）仍交给 Compose。
        if (pendingScrollRestoreSnapToLine) {
            bumpScrollRestoreGeneration()
            pendingScrollRestoreGlobalChar = null
            pendingScrollRestoreBookmarkPreview = null
            pendingScrollRestoreHighlightId = null
            pendingScrollRestorePdfPageIndex = null
            pendingScrollRestoreAnchor = null
            pendingScrollRestoreSnapToLine = true
            windowExpandInFlight = false
            expandRestoreGuard[0] = false
        }
        readerTextView.value?.post { publishVisibleReadingProgress() }
    }

    LaunchedEffect(
        readerTextView.value,
        displayedContent,
        pagerState.currentPage,
        displayWindowStartChar,
        displayWindowEndChar,
        coverUntilPositionRestore,
        tocEntries,
    ) {
        if (isPdfBook || coverUntilPositionRestore) return@LaunchedEffect
        val tv = readerTextView.value ?: return@LaunchedEffect
        repeat(10) {
            if (tv.layout != null && !tv.text.isNullOrEmpty()) {
                val top = currentTopGlobalChar()
                if (top != null) lastScrollTopGlobalChar = top
                publishVisibleReadingProgress()
                return@LaunchedEffect
            }
            delay(32)
        }
        if (tv.layout != null) {
            val top = currentTopGlobalChar()
            if (top != null) lastScrollTopGlobalChar = top
            publishVisibleReadingProgress()
        }
    }

    LaunchedEffect(showTopBar) {
        scrollAccumForHideChrome = 0
    }

    BackHandler(enabled = immersiveReading && showTopBar) {
        showTopBar = false
    }

    LaunchedEffect(bookId) {
        viewModel.dispatch(ReaderEvent.LoadBook(bookId))
        showTopBar = false
    }

    // 仅打开/换书/切模式时初始化窗口；勿监听 currentPosition，滚动存盘会误触发整页重排与跳 scroll。
    LaunchedEffect(
        bookId,
        readerContent,
        pageTurnMode,
        readerLoadEpoch,
        isPdfBook,
    ) {
        if (readerContent.isEmpty()) {
            displayWindowStartChar = 0
            displayWindowEndChar = 0
            return@LaunchedEffect
        }
        if (isPdfBook) {
            val targetChar = viewModel.readingCharPosForRestore().coerceIn(
                0,
                (readerContent.length - 1).coerceAtLeast(0),
            )
            coverUntilPositionRestore = true
            readerOpenDbg("cover=true reason=pdfInit target=$targetChar epoch=$readerLoadEpoch")
            pendingScrollRestoreBookmarkPreview = null
            pendingScrollRestoreHighlightId = null
            pendingScrollRestorePdfPageIndex = null
            pendingScrollRestoreGlobalChar = null
            pdfViewportAnchor = PdfPageCatalog.decodeProgress(
                pdfPages,
                targetChar,
                readerContent.length,
            )
            lastScrollTopGlobalChar = targetChar
            return@LaunchedEffect
        }
        if (pageTurnMode != ReaderPageTurnMode.VerticalScroll) {
            displayWindowStartChar = 0
            displayWindowEndChar = readerContent.length
            return@LaunchedEffect
        }
        val targetChar = viewModel.readingCharPosForRestore().coerceIn(
            0,
            (readerContent.length - 1).coerceAtLeast(0),
        )
        val (start, end) = computeReadingWindow(chapterBoundaries, targetChar, readerContent.length)
        displayWindowStartChar = start
        displayWindowEndChar = end
        pendingScrollRestoreAnchor = null
        pendingScrollRestoreSnapToLine = true
        coverUntilPositionRestore = true
        readerOpenDbg(
            "cover=true reason=initWindow win=$start..$end target=$targetChar " +
                "contentLen=${readerContent.length} epoch=$readerLoadEpoch",
        )
        clearPendingSavedPositionSnap(readerTextView.value)
        pendingScrollRestorePdfPageIndex = null
        pendingScrollRestoreBookmarkPreview = viewModel.readingPreviewForRestore()
        pendingScrollRestoreHighlightId = null
        stashSavedPositionSnapForPendingRestore(
            sourceOffset = targetChar,
            windowStart = start,
            windowEnd = end,
            preview = pendingScrollRestoreBookmarkPreview,
            highlightId = null,
        )
        queueScrollRestore(targetChar)
        lastScrollTopGlobalChar = targetChar
    }

    // 向下扩窗：与向上扩窗相同，按视口顶部字符锚点恢复，并等待 Markdown 渲染完成。
    // 注意：debounce 后不再要求手指仍按下——边缘拖动常在 180ms 内抬手，否则永远扩不成窗。
    LaunchedEffect(expandWindowDownToken, readerContent, chapterBoundaries) {
        if (expandWindowDownToken == 0) return@LaunchedEffect
        if (isPdfBook) return@LaunchedEffect
        delay(180)
        val readerTv = readerTextView.value as? SafeReaderTextView
        if (readerTv == null ||
            readerTv.shouldSuppressReaderScrollSideEffects() ||
            readerTv.isGestureOnDiagram() ||
            isViewportTopOnDiagramSpan(readerTv)
        ) {
            expandRestoreGuard[0] = false
            windowExpandInFlight = false
            return@LaunchedEffect
        }
        if (pageTurnMode != ReaderPageTurnMode.VerticalScroll) {
            expandRestoreGuard[0] = false
            windowExpandInFlight = false
            return@LaunchedEffect
        }
        if (pendingScrollRestoreGlobalChar != null) {
            expandRestoreGuard[0] = false
            windowExpandInFlight = false
            return@LaunchedEffect
        }
        val winEnd = displayWindowEndChar
        if (winEnd >= readerContent.length) {
            expandRestoreGuard[0] = false
            windowExpandInFlight = false
            return@LaunchedEffect
        }
        if (!shouldTriggerReaderExpandDown(readerTv)) {
            expandRestoreGuard[0] = false
            windowExpandInFlight = false
            return@LaunchedEffect
        }
        clearPendingScrollCharOffset(readerTv)
        val tv = readerTextView.value
        val scrollAnchor = if (tv != null && tv.layout != null) {
            captureTextViewScrollAnchor(tv, displayWindowStartChar)
        } else {
            null
        }
        pendingScrollRestoreAnchor = scrollAnchor
        val globalChar = currentTopGlobalChar() ?: globalSourceCharAtTextViewTop(
            sourceContent = readerContent,
            windowStart = displayWindowStartChar,
            windowEnd = winEnd,
            textView = tv,
            renderPlainText = renderPlainText,
            tocEntries = tocEntries,
        )
        val newEnd = nextWindowEnd(chapterBoundaries, winEnd, readerContent.length)
        stashPendingSourceScrollRestore(
            tv = tv,
            sourceOffset = globalChar,
            windowStart = displayWindowStartChar,
            windowEnd = newEnd,
            anchor = scrollAnchor,
        )
        expandRestoreGuard[0] = true
        pendingScrollRestoreSnapToLine = false
        queueScrollRestore(globalChar)
        displayWindowEndChar = newEnd
    }

    LaunchedEffect(expandWindowUpToken, readerContent, chapterBoundaries) {
        if (expandWindowUpToken == 0) return@LaunchedEffect
        if (isPdfBook) return@LaunchedEffect
        delay(180)
        val readerTvUp = readerTextView.value as? SafeReaderTextView
        if (readerTvUp == null ||
            readerTvUp.shouldSuppressReaderScrollSideEffects() ||
            readerTvUp.isGestureOnDiagram() ||
            isViewportTopOnDiagramSpan(readerTvUp)
        ) {
            expandRestoreGuard[0] = false
            windowExpandInFlight = false
            return@LaunchedEffect
        }
        if (pageTurnMode != ReaderPageTurnMode.VerticalScroll) {
            expandRestoreGuard[0] = false
            windowExpandInFlight = false
            return@LaunchedEffect
        }
        if (pendingScrollRestoreGlobalChar != null) {
            expandRestoreGuard[0] = false
            windowExpandInFlight = false
            return@LaunchedEffect
        }
        val winStart = displayWindowStartChar
        if (winStart <= 0) {
            expandRestoreGuard[0] = false
            windowExpandInFlight = false
            return@LaunchedEffect
        }
        if (!shouldTriggerReaderExpandUp(readerTvUp)) {
            expandRestoreGuard[0] = false
            windowExpandInFlight = false
            return@LaunchedEffect
        }
        clearPendingScrollCharOffset(readerTvUp)
        val tv = readerTextView.value
        val scrollAnchor = if (tv != null && tv.layout != null) {
            captureTextViewScrollAnchor(tv, winStart)
        } else {
            null
        }
        pendingScrollRestoreAnchor = scrollAnchor
        val globalChar = currentTopGlobalChar() ?: globalSourceCharAtTextViewTop(
            sourceContent = readerContent,
            windowStart = winStart,
            windowEnd = displayWindowEndChar,
            textView = tv,
            renderPlainText = renderPlainText,
            tocEntries = tocEntries,
        )
        val newStart = previousWindowStart(chapterBoundaries, winStart, 0)
        stashPendingSourceScrollRestore(
            tv = tv,
            sourceOffset = globalChar,
            windowStart = newStart,
            windowEnd = displayWindowEndChar,
            anchor = scrollAnchor,
        )
        expandRestoreGuard[0] = true
        pendingScrollRestoreSnapToLine = false
        queueScrollRestore(globalChar)
        displayWindowStartChar = newStart
    }

    // 跳转后台预扩上文：目标章节渲染+置顶完成后，在更宽窗口里用跳转同款精确 resolver
    // 把目标章节重新定位到顶部（视觉不动、前文已就绪），使下滑看前文时无撞硬顶+异步重渲染的停顿。
    // 不走 length-diff 扩窗恢复：大窗口下其「后缀渲染长度不变」假设不成立，会偏移几千字导致乱跳。
    LaunchedEffect(prefetchUpTargetChar, displayWindowStartChar, displayedContentSig) {
        val target = prefetchUpTargetChar
        if (target <= 0) return@LaunchedEffect
        if (pageTurnMode != ReaderPageTurnMode.VerticalScroll) {
            prefetchUpTargetChar = -1
            return@LaunchedEffect
        }
        // 仅当窗口仍停在刚跳转的章节起点（未被后续扩窗/跳转改变）时才预取。
        if (displayWindowStartChar != target) {
            prefetchUpTargetChar = -1
            return@LaunchedEffect
        }
        val newStart = previousWindowStart(chapterBoundaries, target, 0)
        if (newStart >= target) {
            // 上方已无可预取内容。
            prefetchUpTargetChar = -1
            return@LaunchedEffect
        }
        val tv = awaitReaderMarkdownRenderReady(
            tvProvider = { readerTextView.value },
            expectedRenderSig = displayedContentSig,
        ) ?: return@LaunchedEffect
        // 目标窗口若已改变（用户又跳转/扩窗）则放弃。
        if (displayWindowStartChar != target || prefetchUpTargetChar != target) return@LaunchedEffect
        // 用户已开始交互或有在途恢复则放弃，避免与手势/恢复相争。
        val safeTv = tv as? SafeReaderTextView
        if (windowExpandInFlight ||
            pendingScrollRestoreGlobalChar != null ||
            tv.scrollY > 8 ||
            safeTv?.isUserVerticalScrollDrag() == true ||
            safeTv?.shouldSuppressReaderScrollSideEffects() == true
        ) {
            prefetchUpTargetChar = -1
            return@LaunchedEffect
        }
        readerRestoreDbg(
            "prefetchUp trigger target=$target newStart=$newStart scrollY=${tv.scrollY}",
        )
        prefetchUpTargetChar = -1
        windowExpandInFlight = true
        expandRestoreGuard[0] = true
        clearPendingScrollCharOffset(tv)
        clearReaderScrollToTop(tv)
        // 预扩窗是纯前置插入：后缀 [target,winEnd] 文本不变、字符数可加，
        // boundaryOffset = 新显示长度 - 旧显示长度 即前置段字符数，是字符级精确的。
        // 抓当前锚点（target 在顶部、scrollY≈0），onLayout 首帧走 length-diff 分支即可绘制前精确置顶、不闪。
        val prefetchAnchor = captureTextViewScrollAnchor(tv, displayWindowStartChar)
        readerRestoreDbg(
            "prefetch stash anchor scrollY=${prefetchAnchor.scrollY} lineTop=${prefetchAnchor.lineTop} " +
                "winStart=${prefetchAnchor.windowStart} tvLen=${tv.text?.length}",
        )
        stashPendingSourceScrollRestore(
            tv = tv,
            sourceOffset = target,
            windowStart = newStart,
            windowEnd = displayWindowEndChar,
            anchor = prefetchAnchor,
        )
        // 恢复 effect 也走 stash（snapToLine=false + 带 anchor），与 onLayout 同一套 length-diff 逻辑，
        // 不再叠加 snapToLine 的比例映射（两者落点不一致会造成二次滚动抖动）；该分支结束会复位 windowExpandInFlight。
        pendingScrollRestoreBookmarkPreview = null
        pendingScrollRestoreHighlightId = null
        pendingScrollRestorePdfPageIndex = null
        pendingScrollRestoreAnchor = prefetchAnchor
        pendingScrollRestoreSnapToLine = false
        displayWindowStartChar = newStart
        queueScrollRestore(target)
    }

    // 目录/书签跳转、向上扩窗：等新窗口正文写入 TextView 后再按锚点滚动。
    LaunchedEffect(
        displayWindowStartChar,
        displayWindowEndChar,
        displayedContentSig,
        pendingScrollRestoreGlobalChar,
        pendingScrollRestorePdfPageIndex,
        pendingScrollRestoreHighlightId,
        scrollRestoreGeneration,
        renderPlainText,
        isPdfBook,
    ) {
        if (readerContent.isEmpty()) {
            pendingScrollRestoreGlobalChar = null
            pendingScrollRestoreBookmarkPreview = null
            pendingScrollRestoreHighlightId = null
            pendingScrollRestorePdfPageIndex = null
            pendingScrollRestoreAnchor = null
            pendingScrollRestoreSnapToLine = true
            coverUntilPositionRestore = false
            readerOpenDbg("cover=false reason=emptyContent")
            return@LaunchedEffect
        }
        // 必须在 await 前捕获：等待期间用户滑动会递增 generation，完成后应中止而非沿用新 gen 执行旧 snap。
        val restoreGen = scrollRestoreGeneration
        val winStart = displayWindowStartChar
        val winEnd = displayWindowEndChar
        val pdfPageIndex = pendingScrollRestorePdfPageIndex
        val anchorGlobal = pendingScrollRestoreGlobalChar
        if (pdfPageIndex == null && anchorGlobal == null) return@LaunchedEffect
        val scrollAnchor = pendingScrollRestoreAnchor
        val snapToLine = pendingScrollRestoreSnapToLine
        val bookmarkPreview = pendingScrollRestoreBookmarkPreview
        val highlightId = pendingScrollRestoreHighlightId
        // 渲染完成前写入 TextView stash：finishMarkdownRender/onLayout 首帧即可定位。
        // PDF 走页码通道，不要写入 SavedPosition（文本启发式会滚错）。
        if (anchorGlobal != null &&
            !isPdfBook &&
            (snapToLine || bookmarkPreview != null) &&
            pdfPageIndex == null
        ) {
            stashSavedPositionSnapForPendingRestore(
                sourceOffset = anchorGlobal,
                windowStart = winStart,
                windowEnd = winEnd,
                preview = bookmarkPreview,
                highlightId = highlightId,
            )
        }

        fun abortRestoreCleanup() {
            if (scrollRestoreGeneration != restoreGen) return
            pendingScrollRestoreGlobalChar = null
            pendingScrollRestoreBookmarkPreview = null
            pendingScrollRestoreHighlightId = null
            pendingScrollRestorePdfPageIndex = null
            pendingScrollRestoreAnchor = null
            pendingScrollRestoreSnapToLine = true
            coverUntilPositionRestore = false
            readerOpenDbg("cover=false reason=abortRestore gen=$restoreGen")
            windowExpandInFlight = false
            expandRestoreGuard[0] = false
            readerTextView.value?.let {
                clearPendingSavedPositionSnap(it)
                applyStashedSourceScrollRestoreIfAny(it, renderPlainText)
            }
        }

        fun isRestoreStillCurrent(): Boolean =
            scrollRestoreGeneration == restoreGen &&
                pendingScrollRestoreGlobalChar == anchorGlobal &&
                pendingScrollRestorePdfPageIndex == pdfPageIndex

        val tv = when {
            renderPlainText || isPdfBook -> {
                val expectedLen = if (renderPlainText && pdfPageIndex == null) {
                    (winEnd - winStart).coerceAtLeast(0)
                } else {
                    null
                }
                awaitReaderTextViewLayout(
                    tvProvider = { readerTextView.value },
                    maxAttempts = 120,
                    expectedTextLength = expectedLen,
                )
            }
            else -> awaitReaderMarkdownRenderReady(
                tvProvider = { readerTextView.value },
                expectedRenderSig = displayedContentSig,
            )
        } ?: run {
            abortRestoreCleanup()
            return@LaunchedEffect
        }

        if (!isRestoreStillCurrent()) {
            // 扩窗 stash 仍应尽量恢复，避免停在 scrollY=0 的开头。
            if (expandRestoreGuard[0] || !snapToLine) {
                applyStashedSourceScrollRestoreIfAny(tv, renderPlainText)
            }
            return@LaunchedEffect
        }

        val offset = when {
            isPdfBook && pdfPageIndex != null -> resolvePdfDisplayedCharOffset(
                displayedText = tv.text,
                tocEntries = tocEntries,
                targetPageIndex = pdfPageIndex,
                windowStart = winStart,
            )
            anchorGlobal != null -> {
                val safeAnchor = anchorGlobal.coerceIn(0, readerContent.length - 1)
                when {
                    // 打开书 / 书签：同一套「源码位置 + 预览」定位（不吸附章节标题）。
                    bookmarkPreview != null || snapToLine -> highlightId?.let {
                        displayedOffsetForHighlightId(tv, it)
                    } ?: resolveDisplayedCharOffsetForSavedPosition(
                        sourceContent = readerContent,
                        sourceOffset = safeAnchor,
                        displayedText = tv.text,
                        renderPlainText = renderPlainText,
                        windowStart = winStart,
                        windowEnd = winEnd,
                        tocEntries = tocEntries,
                        preferredText = bookmarkPreview,
                    )
                    else -> resolveDisplayedCharOffsetForProgressRestore(
                        sourceOffset = safeAnchor,
                        windowStart = winStart,
                        windowEnd = winEnd,
                        displayedLen = tv.text?.length ?: 0,
                        renderPlainText = renderPlainText,
                    )
                }
            }
            else -> return@LaunchedEffect
        }
        if (scrollRestoreGenRef[0] != restoreGen) return@LaunchedEffect
        tv.post {
            if (scrollRestoreGenRef[0] != restoreGen) return@post
            // 先释放 pending，避免 scrollTextViewToCharOffset 触发的 onScroll 误判为用户滑动并 bump gen。
            // 遮罩用 coverUntilPositionRestore，滚完后再揭开。
            if (scrollRestoreGenRef[0] == restoreGen) {
                pendingScrollRestoreGlobalChar = null
                pendingScrollRestoreBookmarkPreview = null
                pendingScrollRestoreHighlightId = null
                pendingScrollRestorePdfPageIndex = null
                pendingScrollRestoreAnchor = null
                pendingScrollRestoreSnapToLine = true
            }
            when {
                // 扩窗：优先视觉锚点（onLayout/stash 可能已恢复；再补一次保持一致）
                !snapToLine && bookmarkPreview == null && scrollAnchor != null -> {
                    if (!applyStashedSourceScrollRestoreIfAny(tv, renderPlainText) &&
                        hasPendingSourceScrollRestore(tv)
                    ) {
                        restoreTextViewScrollAfterWindowChange(
                            tv = tv,
                            anchor = scrollAnchor,
                            newWindowStart = winStart,
                            newWindowEnd = winEnd,
                            renderPlainText = renderPlainText,
                        )
                    }
                    clearPendingSourceScrollRestore(tv)
                }
                !snapToLine && bookmarkPreview == null && anchorGlobal != null -> {
                    if (!applyStashedSourceScrollRestoreIfAny(tv, renderPlainText) &&
                        hasPendingSourceScrollRestore(tv)
                    ) {
                        scrollTextViewToSourceProgressAnchor(
                            tv = tv,
                            sourceContent = readerContent,
                            sourceOffset = anchorGlobal.coerceIn(0, readerContent.length - 1),
                            windowStart = winStart,
                            windowEnd = winEnd,
                            renderPlainText = renderPlainText,
                        )
                    }
                    clearPendingSourceScrollRestore(tv)
                }
                bookmarkPreview != null || snapToLine -> {
                    // stash 可能已在 finishMarkdownRender/onLayout 首帧定位；此处兜底再滚一次。
                    if (!applyStashedSavedPositionSnapIfAny(tv)) {
                        applyPendingScrollToCharOffset(tv, offset, bookmarkPreview)
                    }
                }
                else -> scrollTextViewToCharOffset(tv, offset)
            }
            if (scrollRestoreGenRef[0] == restoreGen) {
                clearPendingSavedPositionSnap(tv)
                coverUntilPositionRestore = false
                readerOpenDbg("cover=false reason=restoreDone gen=$restoreGen")
                windowExpandInFlight = false
                expandRestoreGuard[0] = false
            }
        }
    }

    val paperBaseColor = rememberPaperBaseColor(currentTheme)
    val systemBarChromeColor = readingChromeShade(paperBaseColor)

    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val hideSystemBarsNow = hideSystemBarsPref &&
        immersiveReading &&
        !readerChromeVisible &&
        !showToc &&
        !showBookmarks &&
        !showReaderSettingsSheet
    ShelfStyleSystemBarsEffect(
        backgroundColor = systemBarChromeColor,
        navigationBarColor = systemBarChromeColor,
        iconContrastColor = paperBaseColor,
        systemBarsVisible = !hideSystemBarsNow,
    )
    val persistVisibleProgressNow by rememberUpdatedState {
        val tv = readerTextView.value
        val preview = if (isPdfBook) {
            null
        } else {
            tv?.let { previewPlainTextFromTextViewTop(it) }
        }
        val topChar = currentTopGlobalChar()
            ?: lastScrollTopGlobalChar.takeIf { it >= 0 }
        viewModel.dispatch(ReaderEvent.PersistVisibleProgress(topChar, preview))
    }

    DisposableEffect(lifecycleOwner, bookId) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> viewModel.dispatch(ReaderEvent.ReadingResumed)
                Lifecycle.Event.ON_PAUSE -> {
                    persistVisibleProgressNow()
                    viewModel.dispatch(ReaderEvent.ReadingPaused)
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            viewModel.dispatch(ReaderEvent.ReadingResumed)
        }
        onDispose {
            persistVisibleProgressNow()
            viewModel.dispatch(ReaderEvent.ReadingPaused)
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    DisposableEffect(bookId) {
        val previous = MarkdownLinkDispatcher.onBeforeOpenWebUrl
        MarkdownLinkDispatcher.onBeforeOpenWebUrl = {
            persistVisibleProgressNow()
            viewModel.dispatch(ReaderEvent.ReadingPaused)
        }
        onDispose {
            MarkdownLinkDispatcher.onBeforeOpenWebUrl = previous
        }
    }

    DisposableEffect(Unit) {
        val activity = view.context as? Activity
        if (activity == null) {
            return@DisposableEffect onDispose { }
        }
        val window = activity.window
        val controller = WindowCompat.getInsetsController(window, view)
        @Suppress("DEPRECATION")
        val prevStatusColor = window.statusBarColor
        val prevLightStatusBars = controller.isAppearanceLightStatusBars
        @Suppress("DEPRECATION")
        val prevNavColor = window.navigationBarColor
        val prevLightNavBars = controller.isAppearanceLightNavigationBars
        onDispose {
            @Suppress("DEPRECATION")
            window.statusBarColor = prevStatusColor
            controller.isAppearanceLightStatusBars = prevLightStatusBars
            @Suppress("DEPRECATION")
            window.navigationBarColor = prevNavColor
            controller.isAppearanceLightNavigationBars = prevLightNavBars
        }
    }

    val readerChapterTitle = rememberChapterTitleForProgress(
        tocEntries = tocEntries,
        viewportTopChar = uiState.viewportTopChar,
        viewportBottomChar = uiState.viewportBottomChar,
        visibleChapterOffset = uiState.visibleChapterOffset,
    )
    val wideReaderNavigation = uiState.showTocInLandscape &&
        windowSize.width > windowSize.height &&
        appWindowWidthClass(
        with(windowDensity) { windowSize.width.toDp() },
    ).usesWideReaderNavigation() && tocEntries.isNotEmpty()
    ReaderContainer(
        isPdf = isPdfBook,
        immersive = immersiveReading,
        textSelectionActive = readerTextSelectionActive,
        hideSystemBars = hideSystemBarsPref,
        theme = currentTheme,
        paperColor = paperBaseColor,
        chromeColor = systemBarChromeColor,
        snackbarHostState = snackbarHostState,
        title = book?.title ?: readingTitleFallback,
        chapterTitle = readerChapterTitle,
        chromeVisible = readerChromeVisible,
        wideNavigation = wideReaderNavigation,
        tocEntries = tocEntries,
        currentTocEntry = chapterEntryForTitleBar(
            tocEntries,
            uiState.viewportTopChar,
            uiState.viewportBottomChar,
            uiState.visibleChapterOffset,
        ),
        tocTitle = stringResource(R.string.reader_toc_title),
        tocEmptyMessage = emptyTocMessage,
        onTocEntryClick = { entry ->
            pendingWideTocEntry = entry
        },
        onNavigateBack = { navController.navigateUp() },
        onToc = { showToc = true },
        onBookmarks = { showBookmarks = true },
        onReadingSettings = { showReaderSettingsSheet = true },
    ) {
        ReaderContent(
            loadState = uiState.loadState,
            hasContent = content.isNotEmpty(),
            isPdf = isPdfBook,
            onRetry = { viewModel.dispatch(ReaderEvent.RetryLoad) },
            onBack = { navController.navigateUp() },
        ) {
                        // 长按选区走系统复制/全选菜单，不在此弹出划线顶栏。
                        val onReaderVerticalScroll: (Int) -> Unit = { deltaPx ->
                            if (immersiveReading && showTopBar) {
                                scrollAccumForHideChrome += deltaPx
                                if (scrollAccumForHideChrome >= hideChromeScrollThresholdPx) {
                                    showTopBar = false
                                }
                            }
                        }
                        val onReaderSwipeBookmark: () -> Unit = {
                            val tv = readerTextView.value
                            val topChar = currentTopGlobalChar()
                            val preview = if (isPdfBook) {
                                PdfReaderContent.bookmarkPreview(
                                    context,
                                    readerContent,
                                    (topChar ?: 0).coerceIn(0, readerContent.length),
                                )
                            } else {
                                tv?.let { previewPlainTextFromTextViewTop(it) }
                            }
                            if (topChar != null) {
                                viewModel.updateReadingProgressAtCharNow(
                                    topChar,
                                    if (isPdfBook) null else preview,
                                    documentReachedEnd(),
                                )
                            }
                            viewModel.dispatch(
                                ReaderEvent.ToggleBookmarkAtSwipe(
                                    previewText = preview,
                                    positionForAdd = topChar,
                                )
                            )
                        }
                        // 沉浸模式章节小标题常驻，不随大顶栏/底栏显隐改变布局（避免点击唤出工具栏时正文跳动）
                        val showImmersiveChapterBar = immersiveReading && !isPdfBook
                        val readerPaddingTopDp = if (showImmersiveChapterBar) {
                            minOf(ReaderChapterStripBodyTopPaddingDp, readerPaddingDp)
                        } else {
                            readerPaddingDp
                        }

                        // 始终同一 Column 槽位：章节条显隐不得切换「裸 ReaderHost / Column 包一层」，
                        // 否则 AndroidView factory 会反复重建 → 周期性全量 Markwon。
                        Column(Modifier.fillMaxSize()) {
                            if (showImmersiveChapterBar) {
                                ReaderImmersiveChapterTitleBar(
                                    tocEntries = tocEntries,
                                    readingProgress = uiState.readingProgress,
                                    viewportTopChar = uiState.viewportTopChar,
                                    viewportBottomChar = uiState.viewportBottomChar,
                                    visibleChapterOffset = uiState.visibleChapterOffset,
                                    fallbackTitle = book?.title ?: readingTitleFallback,
                                    theme = currentTheme,
                                    reserveStatusBarInset = !hideSystemBarsPref,
                                )
                            }
                            key(bookId, if (isPdfBook) "pdf-continuous" else pageTurnMode) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth(),
                                ) {
                                    if (isPdfBook) {
                                        PdfContinuousReader(
                                            pages = pdfPages,
                                            theme = currentTheme,
                                            paperColor = paperBaseColor,
                                            restoreAnchor = pdfRestoreAnchor,
                                            jumpRequest = pdfJumpRequest,
                                            modifier = Modifier.fillMaxSize(),
                                            onViewportChanged = { anchor, atEnd ->
                                                pdfViewportAnchor = anchor
                                                pdfAtEnd = atEnd
                                                val estimated = PdfPageCatalog.encodeProgress(
                                                    pdfPages,
                                                    anchor.pageIndex,
                                                    anchor.fractionInPage,
                                                    readerContent.length,
                                                )
                                                lastScrollTopGlobalChar = estimated
                                                viewModel.updateVisibleReadingProgress(estimated, atEnd)
                                                val now = System.currentTimeMillis()
                                                if (now - lastScrollProgressSaveMs >= 200L) {
                                                    lastScrollProgressSaveMs = now
                                                    viewModel.updateReadingProgressAtChar(
                                                        estimated,
                                                        null,
                                                        atEnd,
                                                    )
                                                }
                                            },
                                            onCenterTap = {
                                                if (!readerTextSelectionActive) showTopBar = !showTopBar
                                            },
                                            onSwipeBookmark = onReaderSwipeBookmark,
                                            onVerticalScroll = onReaderVerticalScroll,
                                            onZoomedChange = { pdfPageZoomed = it },
                                            onOpened = {
                                                coverUntilPositionRestore = false
                                                onReaderOpenPositionReady()
                                            },
                                        )
                                    } else if (pageTurnMode == ReaderPageTurnMode.VerticalScroll) {
                                        MarkdownReaderView(
                                            content = displayedContent,
                                            renderPlainText = renderPlainText,
                                            theme = currentTheme,
                                            fontSize = fontSize,
                                            readerPaddingDp = readerPaddingDp,
                                            readerPaddingHorizontalDp = readerHorizontalPaddingDp,
                                            readerPaddingTopDp = readerPaddingTopDp,
                                            readerLineSpacingMultiplier = readerBodyLineSpacing,
                                            highlights = displayedHighlights,
                                            retainedHighlights = highlights,
                                            modifier = Modifier.fillMaxSize(),
                                            onHighlightMenuClick = onHighlightMenuClick,
                                            resolveExistingHighlightId = ::resolveExistingHighlightId,
                                            onRemoveHighlightClick = onRemoveHighlightClick,
                                            onScroll = { _ ->
                                                val readerTv = readerTextView.value as? SafeReaderTextView
                                                // Compose state 写入同帧不可读：用局部标志避免「已取消 restore 仍挡住扩窗」。
                                                var restoreCanceledThisScroll = false
                                                if (readerTv != null) {
                                                    // 任意滚动都解除标题 snap 锁定（含惯性滑动）；扩窗 restore 不受此 tag 影响。
                                                    clearPendingScrollCharOffset(readerTv)
                                                }
                                                // 任意滚动（含惯性）都取消目录/书签 snap 的 Compose restore；
                                                // 仅手指拖动时取消会漏掉「渲染完成前已松手惯性滑动」，导致晚到的 tv.post 把视口拉回标题上方。
                                                val cancelSnapRestore = !expandRestoreGuard[0] &&
                                                    pendingScrollRestoreSnapToLine &&
                                                    !windowExpandInFlight &&
                                                    (pendingScrollRestoreGlobalChar != null ||
                                                        pendingScrollRestorePdfPageIndex != null ||
                                                        pendingScrollRestoreBookmarkPreview != null ||
                                                        pendingScrollRestoreHighlightId != null)
                                                if (cancelSnapRestore) {
                                                    restoreCanceledThisScroll = true
                                                    bumpScrollRestoreGeneration()
                                                    pendingScrollRestoreGlobalChar = null
                                                    pendingScrollRestoreBookmarkPreview = null
                                                    pendingScrollRestoreHighlightId = null
                                                    pendingScrollRestorePdfPageIndex = null
                                                    pendingScrollRestoreAnchor = null
                                                    pendingScrollRestoreSnapToLine = true
                                                    coverUntilPositionRestore = false
                                                    readerOpenDbg("cover=false reason=scrollCancelSnap")
                                                    clearPendingSavedPositionSnap(readerTv)
                                                }
                                                val restoreBlocking = !restoreCanceledThisScroll &&
                                                    (pendingScrollRestoreGlobalChar != null ||
                                                        pendingScrollRestorePdfPageIndex != null)
                                                if (!restoreBlocking &&
                                                    !windowExpandInFlight &&
                                                    readerTv?.shouldSuppressReaderScrollSideEffects() != true
                                                ) {
                                                    val estimated = currentTopGlobalChar()
                                                    if (estimated != null) {
                                                        lastScrollTopGlobalChar = estimated
                                                        val atEnd = documentReachedEnd()
                                                        viewModel.updateVisibleReadingProgress(
                                                            estimated,
                                                            atEnd,
                                                            currentBottomGlobalChar(),
                                                            currentChapterEntryNow()?.sourceOffset,
                                                        )
                                                        val now = System.currentTimeMillis()
                                                        if (readerTv?.allowReaderScrollSideEffects == true &&
                                                            now - lastScrollProgressSaveMs >= 200L
                                                        ) {
                                                            lastScrollProgressSaveMs = now
                                                            val preview = if (isPdfBook) {
                                                                null
                                                            } else {
                                                                previewPlainTextFromTextViewTop(readerTv)
                                                            }
                                                            viewModel.updateReadingProgressAtChar(
                                                                estimated,
                                                                preview,
                                                                atEnd,
                                                            )
                                                        }
                                                    }
                                                }
                                                if (readerTv?.allowReaderScrollSideEffects != true ||
                                                    !readerTv.isUserVerticalScrollDrag() ||
                                                    readerTv.shouldSuppressReaderScrollSideEffects() ||
                                                    readerTv.isGestureOnDiagram() ||
                                                    isViewportTopOnDiagramSpan(readerTv) ||
                                                    windowExpandInFlight ||
                                                    restoreBlocking
                                                ) {
                                                    return@MarkdownReaderView
                                                }
                                                val winStart = displayWindowStartChar
                                                val winEnd = displayWindowEndChar
                                                // 方向门控：目录置顶后 scrollY=0 且 winStart>0，若无方向判断，向下滑第一帧
                                                // 也会因 shouldTriggerReaderExpandUp(scrollY<=0) 误触发向上扩窗 → 向上乱跳。
                                                if (winStart > 0 &&
                                                    readerTv.isDragTowardPrevious() &&
                                                    shouldTriggerReaderExpandUp(readerTv) &&
                                                    readerTv.consumeWindowExpandThisGesture()
                                                ) {
                                                    windowExpandInFlight = true
                                                    expandWindowUpToken++
                                                } else if (winEnd < readerContent.length &&
                                                    readerTv.isDragTowardNext() &&
                                                    shouldTriggerReaderExpandDown(readerTv) &&
                                                    readerTv.consumeWindowExpandThisGesture()
                                                ) {
                                                    windowExpandInFlight = true
                                                    expandWindowDownToken++
                                                }
                                            },
                                            onReadingVerticalScroll = onReaderVerticalScroll,
                                            onViewReady = { tv ->
                                                readerTextView.value = tv
                                                tv.post { publishVisibleReadingProgress() }
                                            },
                                            allowVerticalScroll = true,
                                            onSwipeRightBookmark = onReaderSwipeBookmark,
                                            onCenterTap = {
                                                if (!readerTextSelectionActive) showTopBar = !showTopBar
                                            },
                                            onDiagramTap = { bitmap ->
                                                if (!isPdfBook) {
                                                    showTopBar = false
                                                    diagramPreviewBitmap = bitmap
                                                }
                                            },
                                            onReaderTextSelectionActiveChange = onReaderTextSelectionActiveChange,
                                            pdfFullWidthImages = isPdfBook,
                                            codeBlockWrap = codeBlockWrap,
                                            onOpenPositionReady = ::onReaderOpenPositionReady,
                                        )
                                    } else {
                                        ReaderPagedMarkdownHost(
                                            sourceContent = readerContent,
                                            pages = pageSpecs,
                                            pageTurnMode = pageTurnMode,
                                            pagerState = pagerState,
                                            theme = currentTheme,
                                            fontSize = fontSize,
                                            readerPaddingDp = readerPaddingDp,
                                            readerPaddingHorizontalDp = readerHorizontalPaddingDp,
                                            readerPaddingTopDp = readerPaddingTopDp,
                                            readerLineSpacingMultiplier = readerBodyLineSpacing,
                                            highlights = highlights,
                                            pageTextViews = pageTextViews,
                                            renderPlainText = renderPlainText,
                                            modifier = Modifier.fillMaxSize(),
                                            onHighlightMenuClick = onHighlightMenuClick,
                                            resolveExistingHighlightId = ::resolveExistingHighlightId,
                                            onRemoveHighlightClick = onRemoveHighlightClick,
                                            onReadingVerticalScroll = onReaderVerticalScroll,
                                            onSwipeDownBookmark = onReaderSwipeBookmark,
                                            onCenterTap = {
                                                if (!readerTextSelectionActive) showTopBar = !showTopBar
                                            },
                                            onDiagramTap = { bitmap ->
                                                if (!isPdfBook) {
                                                    showTopBar = false
                                                    diagramPreviewBitmap = bitmap
                                                }
                                            },
                                            onReaderTextSelectionActiveChange = onReaderTextSelectionActiveChange,
                                            onPageTextViewReady = { pageIdx, tv ->
                                                if (pageIdx == pagerState.currentPage) {
                                                    readerTextView.value = tv
                                                    tv.post { publishVisibleReadingProgress() }
                                                }
                                            },
                                            pdfFullWidthImages = isPdfBook,
                                            codeBlockWrap = codeBlockWrap,
                                            userScrollEnabled = !pdfPageZoomed,
                                        )
                                    }
                                    // 打开书/书签定位完成前遮住正文，避免先露出窗口开头（更早章节）再跳回。
                                    if (coverUntilPositionRestore) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(paperBaseColor),
                                        )
                                    }
                                }
                            }
                        }
        }
    }

    ReaderOverlays(
        uiState = uiState,
        viewModel = viewModel,
        diagramPreviewBitmap = diagramPreviewBitmap,
        showReaderSettings = showReaderSettingsSheet,
        onDismissDiagramPreview = { diagramPreviewBitmap = null },
        onDismissReaderSettings = {
            showReaderSettingsSheet = false
            if (immersiveReading) showTopBar = false
        },
    )

    fun jumpToReaderChar(target: AnnotationJumpTarget) {
        if (readerContent.isEmpty()) return
        val contentLen = readerContent.length
        // 注解锚点已经是当前正文的源码坐标，不能再按历史 totalChars 二次缩放。
        val charPos = annotationSourceOffset(target, contentLen)
        val bookmarkPreview = target.previewText
        val highlightId = target.highlightId.takeIf { target.kind == AnnotationJumpKind.Highlight }
        val pdfPageIndex = pdfPageIndexForTocOrBookmark(
            isPdfBook = isPdfBook,
            tocEntries = tocEntries,
            readerContent = readerContent,
            charPos = charPos,
            tocEntry = null,
        )
        val navigationPlan = createReaderNavigationPlan(
            requestKind = ReaderNavigationRequestKind.Annotation,
            isPdf = isPdfBook,
            pageTurnMode = pageTurnMode,
            hasPageSpecs = pageSpecs.isNotEmpty(),
            pdfPages = pdfPages,
            contentLength = contentLen,
            requestedChar = charPos,
            resolvedPdfPageIndex = pdfPageIndex,
        )
        if (navigationPlan is ReaderNavigationPlan.PdfContinuous) {
            pdfViewportAnchor = PdfViewportAnchor(
                navigationPlan.pageIndex,
                navigationPlan.fractionInPage,
            )
            pdfJumpGeneration++
            pdfJumpRequest = PdfJumpRequest(
                pdfJumpGeneration,
                navigationPlan.pageIndex,
                navigationPlan.fractionInPage,
            )
            coverUntilPositionRestore = true
            lastScrollTopGlobalChar = navigationPlan.progressChar
            viewModel.updateReadingProgressAtChar(
                lastScrollTopGlobalChar,
                null,
                reachedEndForChar(lastScrollTopGlobalChar),
            )
            return
        }
        if (navigationPlan is ReaderNavigationPlan.Paged) {
            jumpToGlobalCharInPager(
                scope = scope,
                charPos = navigationPlan.charPosition,
                contentLen = contentLen,
                sourceContent = readerContent,
                renderPlainText = renderPlainText,
                tocEntries = tocEntries,
                bookmarkPreviewText = bookmarkPreview,
                pageSpecs = pageSpecs,
                pagerState = pagerState,
                pageTextViews = pageTextViews,
                assignActiveTextView = { readerTextView.value = it },
                onProgress = {
                    viewModel.updateReadingProgressAtChar(
                        navigationPlan.charPosition,
                        bookmarkPreview,
                        reachedEndForChar(navigationPlan.charPosition),
                    )
                },
                pdfJumpByPageIndex = isPdfBook,
                pdfPageIndex = navigationPlan.pdfPageIndex,
                highlightId = highlightId,
                themeSignature = currentTheme.contentSignature(),
                fontSize = fontSize,
                codeBlockWrap = codeBlockWrap,
            )
        } else if (navigationPlan is ReaderNavigationPlan.PdfVertical) {
            pendingScrollRestoreGlobalChar = null
            pendingScrollRestoreBookmarkPreview = null
            pendingScrollRestoreHighlightId = null
            pendingScrollRestoreAnchor = null
            pendingScrollRestoreSnapToLine = true
            clearPendingSavedPositionSnap(readerTextView.value)
            coverUntilPositionRestore = true
            bumpScrollRestoreGeneration()
            jumpToPdfPageVertically(
                contentLen = contentLen,
                chapterBoundaries = chapterBoundaries,
                pageIndex = navigationPlan.pageIndex,
                tocEntries = tocEntries,
                setReadingWindow = { start, end ->
                    displayWindowStartChar = start
                    displayWindowEndChar = end
                },
                onPendingPdfPageIndex = { pendingScrollRestorePdfPageIndex = it },
                onProgress = {
                    viewModel.updateReadingProgressAtChar(
                        navigationPlan.charPosition,
                        null,
                        reachedEndForChar(navigationPlan.charPosition),
                    )
                },
            )
        } else {
            check(navigationPlan is ReaderNavigationPlan.ChunkWindow)
            clearPendingScrollCharOffset(readerTextView.value)
            pendingScrollRestoreBookmarkPreview = bookmarkPreview
            pendingScrollRestoreHighlightId = highlightId
            pendingScrollRestoreAnchor = null
            pendingScrollRestoreSnapToLine = true
            coverUntilPositionRestore = true
            jumpToCharInChunkWindow(
                sourceContent = readerContent,
                contentLen = contentLen,
                charPos = navigationPlan.charPosition,
                setReadingWindow = { start, end ->
                    displayWindowStartChar = start
                    displayWindowEndChar = end
                    stashSavedPositionSnapForPendingRestore(
                        sourceOffset = navigationPlan.charPosition,
                        windowStart = start,
                        windowEnd = end,
                        preview = bookmarkPreview,
                        highlightId = highlightId,
                    )
                },
                onAnchorGlobalChar = { queueScrollRestore(it) },
                onProgress = {
                    viewModel.updateReadingProgressAtChar(
                        navigationPlan.charPosition,
                        bookmarkPreview,
                        reachedEndForChar(navigationPlan.charPosition),
                    )
                },
            )
        }
    }

    LaunchedEffect(pendingWideTocEntry, readerContent) {
        val entry = pendingWideTocEntry ?: return@LaunchedEffect
        if (readerContent.isBlank()) return@LaunchedEffect
        pendingWideTocEntry = null
        jumpToReaderChar(
            AnnotationJumpTarget(
                sourceOffset = entry.sourceOffset,
                previewText = null,
                kind = AnnotationJumpKind.Bookmark,
            ),
        )
        if (immersiveReading) showTopBar = false
    }

    LaunchedEffect(
        readerContent,
        uiState.loadState,
        jumpKind,
        jumpPosition,
        jumpHighlightId,
        jumpBookmarkId,
        jumpPreview,
    ) {
        val requestedPosition = jumpPosition ?: return@LaunchedEffect
        if (externalAnnotationConsumed || requestedPosition < 0) return@LaunchedEffect
        if (readerContent.isBlank() || uiState.loadState !is ReaderLoadState.Ready) {
            return@LaunchedEffect
        }
        val kind = when (jumpKind?.lowercase()) {
            "highlight" -> AnnotationJumpKind.Highlight
            "bookmark" -> AnnotationJumpKind.Bookmark
            else -> return@LaunchedEffect
        }
        externalAnnotationConsumed = true
        val resolvedPosition = when {
            kind == AnnotationJumpKind.Highlight && jumpHighlightId != null ->
                annotationAnchors.highlightJump[jumpHighlightId] ?: requestedPosition
            kind == AnnotationJumpKind.Bookmark && jumpBookmarkId != null ->
                annotationAnchors.bookmarkJump[jumpBookmarkId] ?: requestedPosition
            else -> requestedPosition
        }
        val wasClamped = requestedPosition >= readerContent.length
        snackbarHostState.showSnackbar(
            message = annotationLocatingMessage,
            withDismissAction = false,
            duration = SnackbarDuration.Short,
        )
        jumpToReaderChar(
            AnnotationJumpTarget(
                sourceOffset = resolvedPosition,
                previewText = jumpPreview,
                kind = kind,
                highlightId = jumpHighlightId?.takeIf { it > 0L },
            ),
        )
        delay(350L)
        snackbarHostState.showSnackbar(
            message = if (wasClamped) annotationNearbyMessage else annotationLocatedMessage,
            withDismissAction = false,
            duration = SnackbarDuration.Short,
        )
    }

    val readerSafeTv = readerTextView.value as? SafeReaderTextView
    LaunchedEffect(pendingHighlightSelection != null) {
        readerSafeTv?.highlightStylePickerShowing = pendingHighlightSelection != null
    }

    pendingHighlightSelection?.let { pending ->
        var draftColor by remember(pending) { mutableStateOf(pending.color) }
        var draftStyle by remember(pending) { mutableStateOf(pending.style) }

        HighlightStylePopup(
            selectionBoundsInWindow = pending.boundsInWindow,
            selectedColor = draftColor,
            selectedStyle = draftStyle,
            onPick = { color, style ->
                draftColor = color
                draftStyle = style
                pending.color = color
                pending.style = style
                val tv = pending.ownerTextView
                val previewColor = if (color.alpha < 0.06f) {
                    lastHighlightColorArgb
                } else {
                    color.toArgb()
                }
                tv?.let {
                    applyHighlightDecorationAtRange(
                        textView = it,
                        start = pending.displayedStart,
                        end = pending.displayedEnd,
                        colorArgb = previewColor,
                        style = style,
                        highlightId = pending.draftHighlightId,
                    )
                }
                pending.persistedHighlightId?.let { id ->
                    viewModel.dispatch(
                        ReaderEvent.UpdateHighlightAppearance(
                            highlightId = id,
                            colorArgb = if (color.alpha < 0.06f) {
                                lastHighlightColorArgb
                            } else {
                                color.toArgb()
                            },
                            style = style,
                        )
                    )
                }
            },
            onDismiss = {
                val tv = pending.ownerTextView
                tv?.highlightStylePickerShowing = false
                if (tv?.isInTextSelection() == true) {
                    tv.dismissSelection()
                }
                pendingHighlightSelection = null
            },
        )
    }

    val currentTocEntry = remember(
        tocEntries,
        uiState.viewportTopChar,
        uiState.viewportBottomChar,
        uiState.visibleChapterOffset,
    ) {
        chapterEntryForTitleBar(
            tocEntries,
            uiState.viewportTopChar,
            uiState.viewportBottomChar,
            uiState.visibleChapterOffset,
        )
    }
    ReaderNavigationSheets(
        showMarks = showBookmarks,
        showToc = showToc,
        marksState = ReaderMarksSheetState(
            bookmarks = bookmarks,
            highlights = highlights,
            totalChars = book?.totalChars?.takeIf { it > 0 }
                ?: readerContent.length.coerceAtLeast(1),
            isPdf = isPdfBook,
            sourceContent = readerContent,
            pendingBookmarkIds = annotationAnchors.pendingBookmarkIds,
            pendingHighlightIds = annotationAnchors.pendingHighlightIds,
            bookmarkJumpPositions = annotationAnchors.bookmarkJump,
            highlightJumpPositions = annotationAnchors.highlightJump,
        ),
        tocState = ReaderTocSheetState(
            entries = tocEntries,
            emptyMessage = emptyTocMessage,
            currentEntry = currentTocEntry,
        ),
        onAnnotationClick = { target ->
            jumpToReaderChar(target)
            showBookmarks = false
        },
        onDeleteBookmark = { viewModel.dispatch(ReaderEvent.DeleteBookmark(it)) },
        onDeleteHighlight = { viewModel.dispatch(ReaderEvent.DeleteHighlight(it)) },
        onTocEntryClick = { entry ->
            if (readerContent.isNotEmpty()) {
                val contentLen = readerContent.length
                val charPos = entry.sourceOffset.coerceIn(0, (contentLen - 1).coerceAtLeast(0))
                val pdfPageIndex = pdfPageIndexForTocOrBookmark(
                    isPdfBook = isPdfBook,
                    tocEntries = tocEntries,
                    readerContent = readerContent,
                    charPos = charPos,
                    tocEntry = entry,
                )
                val navigationPlan = createReaderNavigationPlan(
                    requestKind = ReaderNavigationRequestKind.Toc,
                    isPdf = isPdfBook,
                    pageTurnMode = pageTurnMode,
                    hasPageSpecs = pageSpecs.isNotEmpty(),
                    pdfPages = pdfPages,
                    contentLength = contentLen,
                    requestedChar = charPos,
                    resolvedPdfPageIndex = pdfPageIndex,
                )
                if (navigationPlan is ReaderNavigationPlan.PdfContinuous) {
                    pdfViewportAnchor = PdfViewportAnchor(
                        navigationPlan.pageIndex,
                        navigationPlan.fractionInPage,
                    )
                    pdfJumpGeneration++
                    pdfJumpRequest = PdfJumpRequest(
                        pdfJumpGeneration,
                        navigationPlan.pageIndex,
                        navigationPlan.fractionInPage,
                    )
                    coverUntilPositionRestore = true
                    val pos = navigationPlan.progressChar
                    lastScrollTopGlobalChar = pos
                    viewModel.updateReadingProgressAtChar(pos, reachedEnd = reachedEndForChar(pos))
                } else if (navigationPlan is ReaderNavigationPlan.Paged) {
                    jumpToGlobalCharInPager(
                        scope = scope,
                        charPos = navigationPlan.charPosition,
                        contentLen = contentLen,
                        sourceContent = readerContent,
                        renderPlainText = renderPlainText,
                        tocEntries = tocEntries,
                        preferredTocEntry = entry,
                        pageSpecs = pageSpecs,
                        pagerState = pagerState,
                        pageTextViews = pageTextViews,
                        assignActiveTextView = { readerTextView.value = it },
                        onProgress = {
                            viewModel.updateReadingProgressAtChar(
                                navigationPlan.charPosition,
                                reachedEnd = reachedEndForChar(navigationPlan.charPosition),
                            )
                        },
                        pdfJumpByPageIndex = isPdfBook,
                        pdfPageIndex = navigationPlan.pdfPageIndex,
                        themeSignature = currentTheme.contentSignature(),
                        fontSize = fontSize,
                        codeBlockWrap = codeBlockWrap,
                    )
                } else if (navigationPlan is ReaderNavigationPlan.PdfVertical) {
                    pendingScrollRestoreGlobalChar = null
                    pendingScrollRestoreBookmarkPreview = null
                    pendingScrollRestoreHighlightId = null
                    pendingScrollRestoreAnchor = null
                    pendingScrollRestoreSnapToLine = true
                    clearPendingSavedPositionSnap(readerTextView.value)
                    coverUntilPositionRestore = true
                    bumpScrollRestoreGeneration()
                    jumpToPdfPageVertically(
                        contentLen = contentLen,
                        chapterBoundaries = chapterBoundaries,
                        pageIndex = navigationPlan.pageIndex,
                        tocEntries = tocEntries,
                        setReadingWindow = { start, end ->
                            displayWindowStartChar = start
                            displayWindowEndChar = end
                        },
                        onPendingPdfPageIndex = { pendingScrollRestorePdfPageIndex = it },
                        onProgress = {
                            viewModel.updateReadingProgressAtChar(
                                navigationPlan.charPosition,
                                reachedEnd = reachedEndForChar(navigationPlan.charPosition),
                            )
                        },
                    )
                } else {
                    check(navigationPlan is ReaderNavigationPlan.ChunkWindow)
                    // 目录跳转：目标章节严格置顶，走独立置顶通道，不与 offset/anchor 恢复竞争。
                    clearPendingScrollCharOffset(readerTextView.value)
                    pendingScrollRestoreGlobalChar = null
                    pendingScrollRestoreBookmarkPreview = null
                    pendingScrollRestoreHighlightId = null
                    pendingScrollRestorePdfPageIndex = null
                    pendingScrollRestoreAnchor = null
                    pendingScrollRestoreSnapToLine = true
                    bumpScrollRestoreGeneration()
                    windowExpandInFlight = false
                    expandRestoreGuard[0] = false
                    val (winStart, winEnd) = computeTocJumpReadingWindow(
                        chapterBoundaries,
                        navigationPlan.charPosition,
                        contentLen,
                    )
                    requestReaderScrollToTop(readerTextView.value)
                    readerTextView.value?.let { tv ->
                        if (tv.scrollY != 0) tv.scrollTo(0, 0)
                    }
                    displayWindowStartChar = winStart
                    displayWindowEndChar = winEnd.coerceAtLeast(
                        (winStart + 1).coerceAtMost(contentLen),
                    )
                    viewModel.updateReadingProgressAtChar(
                        navigationPlan.charPosition,
                        reachedEnd = reachedEndForChar(navigationPlan.charPosition),
                    )
                    prefetchUpTargetChar = if (winStart > 0) winStart else -1
                }
            }
            showToc = false
            if (immersiveReading) showTopBar = false
        },
        onDismissMarks = {
            showBookmarks = false
            if (immersiveReading) showTopBar = false
        },
        onDismissToc = {
            showToc = false
            if (immersiveReading) showTopBar = false
        },
    )

}

private class PendingHighlightPicker(
    val text: String,
    val displayedStart: Int,
    val displayedEnd: Int,
    val boundsInWindow: android.graphics.Rect,
    val draftHighlightId: Long,
    val ownerTextView: SafeReaderTextView?,
    var color: Color,
    var style: HighlightStyle,
) {
    var persistedHighlightId: Long? = null
}

/** 在当前页/窗的相对坐标划线列表中查找与选区匹配的划线 id。 */
internal fun findHighlightIdForDisplayedSelection(
    pageLocalHighlights: List<HighlightEntity>,
    text: String,
    displayedStart: Int,
    displayedEnd: Int,
): Long? {
    if (text.isEmpty() || displayedEnd <= displayedStart) return null
    pageLocalHighlights.firstOrNull {
        it.startPosition == displayedStart && it.endPosition == displayedEnd
    }?.id?.let { return it }
    pageLocalHighlights.firstOrNull {
        it.highlightedText == text && it.startPosition == displayedStart
    }?.id?.let { return it }
    return pageLocalHighlights.firstOrNull { h ->
        h.highlightedText == text &&
            displayedStart < h.endPosition &&
            displayedEnd > h.startPosition &&
            kotlin.math.abs(h.startPosition - displayedStart) <= 2
    }?.id
}
