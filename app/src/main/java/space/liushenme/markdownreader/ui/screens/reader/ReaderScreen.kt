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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import androidx.core.graphics.ColorUtils
import androidx.core.text.PrecomputedTextCompat
import androidx.core.view.WindowCompat
import androidx.core.widget.TextViewCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import space.liushenme.markdownreader.data.local.entity.HighlightEntity
import space.liushenme.markdownreader.importing.ImportedBookFormat
import space.liushenme.markdownreader.importing.PdfReaderContent
import space.liushenme.markdownreader.markdown.MarkdownLinkDispatcher
import space.liushenme.markdownreader.model.ReaderPageTurnMode
import space.liushenme.markdownreader.ui.components.ShelfStyleStatusBarBackdrop
import space.liushenme.markdownreader.ui.components.ShelfStyleSystemBarsEffect
import space.liushenme.markdownreader.ui.components.iconTintForDeleteStrip
import space.liushenme.markdownreader.ui.components.shelfStylePageBackground
import space.liushenme.markdownreader.ui.theme.MarkdownReaderTheme
import space.liushenme.markdownreader.ui.theme.ReadingTheme
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
    viewModel: ReaderViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val book by viewModel.book.collectAsState()
    val content by viewModel.content.collectAsState()
    val bookmarks by viewModel.bookmarks.collectAsState()
    val highlights by viewModel.highlights.collectAsState()
    val readingProgress by viewModel.readingProgress.collectAsState()
    val readerLoadEpoch by viewModel.readerLoadEpoch.collectAsState()
    val currentTheme by viewModel.currentTheme.collectAsState()
    val fontSize by viewModel.fontSize.collectAsState()
    val readerPaddingDp by viewModel.readerPaddingDp.collectAsState()
    val readerLineSpacingMultiplier by viewModel.readerLineSpacingMultiplier.collectAsState()
    val loadError by viewModel.loadError.collectAsState()
    val pageTurnMode by viewModel.pageTurnMode.collectAsState()
    val configuration = LocalConfiguration.current

    val importFormat = book?.let { ImportedBookFormat.fromStored(it.importFormat) }
    val isPdfBook = importFormat?.isPdf == true
    val renderPlainText = importFormat?.usesReaderPlainBody == true
    val readerContent = remember(content, isPdfBook) {
        if (isPdfBook) PdfReaderContent.sanitizeStoredBody(content) else content
    }
    val readerHorizontalPaddingDp = if (isPdfBook) 0 else readerPaddingDp
    val readerBodyLineSpacing = if (isPdfBook) 1f else readerLineSpacingMultiplier

    var showReaderThemeSheet by remember { mutableStateOf(false) }
    var showReaderFontSheet by remember { mutableStateOf(false) }
    var showBookmarks by remember { mutableStateOf(false) }
    var showToc by remember { mutableStateOf(false) }
    var showTopBar by remember { mutableStateOf(false) }
    var readerTextSelectionActive by remember { mutableStateOf(false) }
    var showReaderPageTurnSheet by remember { mutableStateOf(false) }
    var diagramPreviewBitmap by remember { mutableStateOf<Bitmap?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    val readerTextView = remember { mutableStateOf<TextView?>(null) }

    val immersiveReading = content.isNotEmpty() && loadError == null
    val readerChromeVisible = immersiveReading && showTopBar && !readerTextSelectionActive

    val onReaderTextSelectionActiveChange: (Boolean) -> Unit = { active ->
        readerTextSelectionActive = active
        if (active) showTopBar = false
    }

    val structuredToc by viewModel.structuredToc.collectAsState()

    val tocEntries = remember(readerContent, renderPlainText, structuredToc, isPdfBook) {
        if (isPdfBook) {
            return@remember PdfReaderContent.tocEntriesFromBody(readerContent).map {
                MarkdownTocEntry(level = it.level, title = it.title, sourceOffset = it.sourceOffset, rawTitle = it.rawTitle)
            }
        }
        val stored = structuredToc.orEmpty()
        when {
            renderPlainText -> stored.ifEmpty { parsePlainTextToc(readerContent) }
            readerContent.isNotEmpty() -> parseMarkdownToc(readerContent).ifEmpty { stored }
            stored.isNotEmpty() -> stored
            else -> emptyList()
        }
    }
    val emptyTocMessage = remember(renderPlainText) {
        if (renderPlainText) {
            "未识别到章节。请将章节标题单独成行，例如：\n第一章 …、第1节 …、Chapter 1 …"
        } else {
            "未识别到标题。请使用 Markdown ATX 语法，例如：\n# 一级标题\n## 二级标题"
        }
    }
    val totalChars = book?.totalChars?.takeIf { it > 0 } ?: content.length.coerceAtLeast(1)
    val chapterEntry = remember(tocEntries, readingProgress, totalChars) {
        val e = currentChapterEntryForProgress(tocEntries, readingProgress, totalChars)
        android.util.Log.d(
            "ReaderCoordDbg",
            "title progress=$readingProgress totalChars=$totalChars contentLen=${content.length} " +
                "pos=${(readingProgress * totalChars).toInt()} entrySrc=${e?.sourceOffset} title=${e?.title} " +
                "tocSize=${tocEntries.size} firstToc=${tocEntries.firstOrNull()?.sourceOffset} " +
                "lastToc=${tocEntries.lastOrNull()?.sourceOffset}",
        )
        e
    }
    val chapterTitle = chapterEntry?.title
    val chapterTitleRaw = chapterEntry?.rawTitle ?: chapterTitle

    val pageSpecs = remember(
        readerContent,
        isPdfBook,
        pageTurnMode,
        fontSize,
        configuration.screenHeightDp,
        configuration.screenWidthDp,
    ) {
        when {
            pageTurnMode == ReaderPageTurnMode.VerticalScroll -> emptyList()
            isPdfBook -> PdfReaderContent.splitToPages(readerContent)
            else -> splitMarkdownToPages(
                readerContent,
                estimateTargetCharsPerPage(fontSize, configuration.screenHeightDp, configuration.screenWidthDp),
            )
        }
    }
    val pageTextViews = remember(content) { mutableMapOf<Int, TextView>() }
    val pagerState = rememberPagerState(pageCount = { pageSpecs.size.coerceAtLeast(1) })

    // ===== 章节惰性渲染窗口（仅 VerticalScroll 模式生效）=====
    val chapterBoundaries = remember(readerContent, tocEntries) {
        computeChapterBoundaries(readerContent, tocEntries)
    }
    var displayWindowStartChar by remember(readerContent, readerLoadEpoch) { mutableIntStateOf(0) }
    var displayWindowEndChar by remember(readerContent, readerLoadEpoch) { mutableIntStateOf(0) }
    /** 向上/向下扩窗后，按全书字符锚点恢复视口，避免跳到章节顶部。 */
    var pendingScrollRestoreGlobalChar by remember(readerContent) { mutableStateOf<Int?>(null) }
    var pendingScrollRestoreBookmarkPreview by remember(readerContent) { mutableStateOf<String?>(null) }
    /** PDF 垂直滚动：按页码恢复视口（源码下标与 TextView 内下标不一致）。 */
    var pendingScrollRestorePdfPageIndex by remember(readerContent) { mutableStateOf<Int?>(null) }
    /** 扩窗/异步 layout 后保留子像素 scroll，避免 snap 到行顶；目录/书签跳转仍为整行对齐。 */
    var pendingScrollRestoreAnchor by remember(readerContent) { mutableStateOf<TextViewScrollAnchor?>(null) }
    var pendingScrollRestoreSnapToLine by remember(readerContent) { mutableStateOf(true) }
    /**
     * 打开书/书签 snap 完成前用主题色遮住正文，避免异步渲染期间以 scrollY=0 露出窗口开头（更早章节）。
     * 独立于 [pendingScrollRestoreGlobalChar]：滚动前须先清 pending，否则 onScroll 会误取消 restore。
     */
    var coverUntilPositionRestore by remember(readerContent, readerLoadEpoch) { mutableStateOf(false) }
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
    ) {
        stashPendingSavedPositionSnap(
            readerTextView.value,
            PendingSavedPositionSnap(
                sourceContent = readerContent,
                sourceOffset = sourceOffset,
                windowStart = windowStart,
                windowEnd = windowEnd,
                preview = preview,
                renderPlainText = renderPlainText,
                tocEntries = tocEntries,
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
    val displayedHighlights = remember(highlights, displayWindowStartChar, displayWindowEndChar, displayedContent) {
        highlightsForPageSlice(
            displayWindowStartChar,
            displayWindowEndChar,
            highlights,
            displayedContent
        )
    }

    fun currentTopGlobalChar(): Int? {
        if (readerContent.isEmpty()) return null
        val tv = readerTextView.value
        if (pageTurnMode != ReaderPageTurnMode.VerticalScroll && pageSpecs.isNotEmpty()) {
            val page = pagerState.currentPage.coerceIn(0, pageSpecs.lastIndex)
            val pageStart = pageSpecs[page].second
            if (isPdfBook) return pageStart.coerceIn(0, readerContent.length)
            val pageEnd = pageSpecs.getOrNull(page + 1)?.second ?: readerContent.length
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

    val displayedRenderSig = remember(
        displayedContent,
        renderPlainText,
        currentTheme,
        fontSize,
        displayedHighlights,
    ) {
        readerRenderSignature(
            content = displayedContent,
            renderPlainText = renderPlainText,
            themeName = currentTheme::class.java.name,
            fontSize = fontSize,
            highlights = displayedHighlights,
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
        if (pageTurnMode == ReaderPageTurnMode.VerticalScroll || pageSpecs.isEmpty()) return@LaunchedEffect
        val charPos = viewModel.readingCharPosForRestore().coerceIn(
            0,
            (readerContent.length - 1).coerceAtLeast(0),
        )
        val pdfPageIndex = if (isPdfBook) {
            PdfReaderContent.pageIndexForSourceOffset(readerContent, charPos)
        } else {
            null
        }
        jumpToGlobalCharInPager(
            scope = this,
            charPos = charPos,
            contentLen = readerContent.length,
            sourceContent = readerContent,
            renderPlainText = renderPlainText,
            tocEntries = tocEntries,
            bookmarkPreviewText = if (isPdfBook) null else viewModel.readingPreviewForRestore(),
            pageSpecs = pageSpecs,
            pagerState = pagerState,
            pageTextViews = pageTextViews,
            assignActiveTextView = { readerTextView.value = it },
            onProgress = {
                viewModel.updateReadingProgressAtChar(
                    charPos,
                    if (isPdfBook) null else viewModel.readingPreviewForRestore(),
                )
            },
            pdfJumpByPageIndex = isPdfBook,
            pdfPageIndex = pdfPageIndex,
        )
    }

    LaunchedEffect(pagerState.currentPage, pageTurnMode, content) {
        if (pageTurnMode == ReaderPageTurnMode.VerticalScroll) return@LaunchedEffect
        readerTextView.value = pageTextViews[pagerState.currentPage]
    }

    LaunchedEffect(pageTurnMode, pageSpecs, pagerState, totalChars) {
        if (pageTurnMode == ReaderPageTurnMode.VerticalScroll || pageSpecs.isEmpty()) return@LaunchedEffect
        snapshotFlow { pagerState.currentPage }.distinctUntilChanged().collect { page ->
            val start = pageSpecs.getOrNull(page)?.second ?: return@collect
            viewModel.updateReadingProgressAtChar(start)
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

    LaunchedEffect(showTopBar) {
        scrollAccumForHideChrome = 0
    }

    BackHandler(enabled = immersiveReading && showTopBar) {
        showTopBar = false
    }

    LaunchedEffect(bookId) {
        viewModel.loadBook(context, bookId)
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
        clearPendingSavedPositionSnap(readerTextView.value)
        if (isPdfBook) {
            // PDF 与书签跳转一致：按页码恢复，不用 Markdown SavedPosition 文本启发式。
            pendingScrollRestoreBookmarkPreview = null
            pendingScrollRestorePdfPageIndex =
                PdfReaderContent.pageIndexForSourceOffset(readerContent, targetChar)
            queueScrollRestore(targetChar)
        } else {
            // Markdown / TXT：position + preview，首帧前 stash snap。
            pendingScrollRestorePdfPageIndex = null
            pendingScrollRestoreBookmarkPreview = viewModel.readingPreviewForRestore()
            stashSavedPositionSnapForPendingRestore(
                sourceOffset = targetChar,
                windowStart = start,
                windowEnd = end,
                preview = pendingScrollRestoreBookmarkPreview,
            )
            queueScrollRestore(targetChar)
        }
        lastScrollTopGlobalChar = targetChar
    }

    // 向下扩窗：与向上扩窗相同，按视口顶部字符锚点恢复，并等待 Markdown 渲染完成。
    // 注意：debounce 后不再要求手指仍按下——边缘拖动常在 180ms 内抬手，否则永远扩不成窗。
    LaunchedEffect(expandWindowDownToken, readerContent, chapterBoundaries) {
        if (expandWindowDownToken == 0) return@LaunchedEffect
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
    LaunchedEffect(prefetchUpTargetChar, displayWindowStartChar, displayedRenderSig) {
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
            expectedRenderSig = displayedRenderSig,
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
        android.util.Log.d(
            "ReaderRestoreDbg2",
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
        android.util.Log.d(
            "ReaderRestoreDbg2",
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
        displayedRenderSig,
        pendingScrollRestoreGlobalChar,
        pendingScrollRestorePdfPageIndex,
        scrollRestoreGeneration,
        renderPlainText,
        isPdfBook,
    ) {
        if (readerContent.isEmpty()) {
            pendingScrollRestoreGlobalChar = null
            pendingScrollRestoreBookmarkPreview = null
            pendingScrollRestorePdfPageIndex = null
            pendingScrollRestoreAnchor = null
            pendingScrollRestoreSnapToLine = true
            coverUntilPositionRestore = false
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
            )
        }

        fun abortRestoreCleanup() {
            if (scrollRestoreGeneration != restoreGen) return
            pendingScrollRestoreGlobalChar = null
            pendingScrollRestoreBookmarkPreview = null
            pendingScrollRestorePdfPageIndex = null
            pendingScrollRestoreAnchor = null
            pendingScrollRestoreSnapToLine = true
            coverUntilPositionRestore = false
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
                expectedRenderSig = displayedRenderSig,
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
                    bookmarkPreview != null || snapToLine -> resolveDisplayedCharOffsetForSavedPosition(
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
                        applyPendingScrollToCharOffset(tv, offset)
                    }
                }
                else -> scrollTextViewToCharOffset(tv, offset)
            }
            if (scrollRestoreGenRef[0] == restoreGen) {
                clearPendingSavedPositionSnap(tv)
                coverUntilPositionRestore = false
                windowExpandInFlight = false
                expandRestoreGuard[0] = false
            }
        }
    }

    val systemBarChromeColor = if (isPdfBook) {
        shelfStylePageBackground()
    } else {
        readingChromeShade(currentTheme.backgroundColor)
    }

    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    ShelfStyleSystemBarsEffect(systemBarChromeColor)
    val persistTopPositionNow by rememberUpdatedState {
        val tv = readerTextView.value
        val preview = if (isPdfBook) {
            null
        } else {
            tv?.let { previewPlainTextFromTextViewTop(it) }
        }
        val topChar = currentTopGlobalChar()
            ?: lastScrollTopGlobalChar.takeIf { it >= 0 }
        topChar?.let { viewModel.persistReadingPositionBlocking(it, preview) }
    }

    DisposableEffect(lifecycleOwner, bookId) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> viewModel.onReadingResumed()
                Lifecycle.Event.ON_PAUSE -> {
                    persistTopPositionNow()
                    viewModel.onReadingPaused()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            viewModel.onReadingResumed()
        }
        onDispose {
            persistTopPositionNow()
            viewModel.onReadingPaused()
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    DisposableEffect(bookId) {
        val previous = MarkdownLinkDispatcher.onBeforeOpenWebUrl
        MarkdownLinkDispatcher.onBeforeOpenWebUrl = {
            persistTopPositionNow()
            viewModel.onReadingPaused()
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

    Scaffold(
        containerColor = currentTheme.backgroundColor,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = if (immersiveReading) {
            WindowInsets(0, 0, 0, 0)
        } else {
            ScaffoldDefaults.contentWindowInsets
        },
        topBar = {
            if (!immersiveReading && !readerTextSelectionActive) {
                ReaderTopAppBar(
                    title = book?.title ?: "阅读中",
                    chapterTitle = chapterTitleRaw,
                    onNavigateBack = { navController.navigateUp() },
                    theme = currentTheme
                )
            }
        }
    ) { paddingValues ->
        val readerContentPadding = if (immersiveReading) {
            PaddingValues(0.dp)
        } else {
            paddingValues
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    if (isPdfBook) shelfStylePageBackground() else currentTheme.backgroundColor,
                ),
        ) {
            if (immersiveReading && isPdfBook) {
                ShelfStyleStatusBarBackdrop(
                    backgroundColor = systemBarChromeColor,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .zIndex(100f),
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(readerContentPadding)
            ) {
            when (val err = loadError) {
                null -> {
                    if (content.isNotEmpty()) {
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
                            scope.launch {
                                val tv = readerTextView.value
                                val preview = tv?.let { previewPlainTextFromTextViewTop(it) }
                                val topChar = currentTopGlobalChar()
                                if (topChar != null) {
                                    viewModel.updateReadingProgressAtCharNow(topChar, preview)
                                }
                                when (
                                    viewModel.toggleBookmarkAtSwipe(
                                        previewForAdd = preview,
                                        positionForAdd = topChar,
                                    )
                                ) {
                                    true -> snackbarHostState.showBriefSnackbar("已添加书签")
                                    false -> snackbarHostState.showBriefSnackbar("已取消书签")
                                    null -> { }
                                }
                            }
                        }
                        // 沉浸模式章节小标题常驻，不随大顶栏/底栏显隐改变布局（避免点击唤出工具栏时正文跳动）
                        val immersiveChapterTitle = chapterTitleRaw.takeIf { immersiveReading && !isPdfBook }
                        val readerPaddingTopDp = if (immersiveChapterTitle != null) {
                            minOf(ReaderChapterStripBodyTopPaddingDp, readerPaddingDp)
                        } else {
                            readerPaddingDp
                        }

                        @Composable
                        fun ReaderHost(modifier: Modifier) {
                            Box(modifier = modifier) {
                            if (pageTurnMode == ReaderPageTurnMode.VerticalScroll) {
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
                                    modifier = Modifier.fillMaxSize(),
                                    onTextSelected = {},
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
                                                pendingScrollRestoreBookmarkPreview != null)
                                        if (cancelSnapRestore) {
                                            restoreCanceledThisScroll = true
                                            bumpScrollRestoreGeneration()
                                            pendingScrollRestoreGlobalChar = null
                                            pendingScrollRestoreBookmarkPreview = null
                                            pendingScrollRestorePdfPageIndex = null
                                            pendingScrollRestoreAnchor = null
                                            pendingScrollRestoreSnapToLine = true
                                            coverUntilPositionRestore = false
                                            clearPendingSavedPositionSnap(readerTv)
                                        }
                                        val restoreBlocking = !restoreCanceledThisScroll &&
                                            (pendingScrollRestoreGlobalChar != null ||
                                                pendingScrollRestorePdfPageIndex != null)
                                        if (readerTv != null &&
                                            readerTv.allowReaderScrollSideEffects &&
                                            !readerTv.shouldSuppressReaderScrollSideEffects() &&
                                            !windowExpandInFlight &&
                                            !restoreBlocking
                                        ) {
                                            val now = System.currentTimeMillis()
                                            if (now - lastScrollProgressSaveMs >= 200L) {
                                                lastScrollProgressSaveMs = now
                                                // PDF 必须走页码反查；TXT/MD 用 currentTopGlobalChar 统一入口。
                                                val estimated = currentTopGlobalChar() ?: return@MarkdownReaderView
                                                lastScrollTopGlobalChar = estimated
                                                android.util.Log.d(
                                                    "ReaderCoordDbg",
                                                    "scrollTop estimated=$estimated scrollY=${readerTv.scrollY} " +
                                                        "winStart=$displayWindowStartChar winEnd=$displayWindowEndChar " +
                                                        "contentLen=${readerContent.length}",
                                                )
                                                val preview = if (isPdfBook) {
                                                    null
                                                } else {
                                                    previewPlainTextFromTextViewTop(readerTv)
                                                }
                                                viewModel.updateReadingProgressAtChar(estimated, preview)
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
                                    onViewReady = { tv -> readerTextView.value = tv },
                                    allowVerticalScroll = true,
                                    onSwipeRightBookmark = onReaderSwipeBookmark,
                                    onCenterTap = {
                                        if (!readerTextSelectionActive) showTopBar = !showTopBar
                                    },
                                    onDiagramTap = { bitmap ->
                                        showTopBar = false
                                        diagramPreviewBitmap = bitmap
                                    },
                                    onReaderTextSelectionActiveChange = onReaderTextSelectionActiveChange,
                                    pdfFullWidthImages = isPdfBook,
                                )
                            } else {
                                ReaderPagedMarkdownHost(
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
                                    onTextSelected = {},
                                    onReadingVerticalScroll = onReaderVerticalScroll,
                                    onSwipeDownBookmark = onReaderSwipeBookmark,
                                    onCenterTap = {
                                        if (!readerTextSelectionActive) showTopBar = !showTopBar
                                    },
                                    onDiagramTap = { bitmap ->
                                        showTopBar = false
                                        diagramPreviewBitmap = bitmap
                                    },
                                    onReaderTextSelectionActiveChange = onReaderTextSelectionActiveChange,
                                    onPageTextViewReady = { pageIdx, tv ->
                                        if (pageIdx == pagerState.currentPage) {
                                            readerTextView.value = tv
                                        }
                                    },
                                    pdfFullWidthImages = isPdfBook,
                                )
                            }
                            // 打开书/书签定位完成前遮住正文，避免先露出窗口开头（更早章节）再跳回。
                            if (coverUntilPositionRestore) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(currentTheme.backgroundColor),
                                )
                            }
                            }
                        }

                        if (immersiveChapterTitle != null) {
                            Column(Modifier.fillMaxSize()) {
                                ReaderImmersiveChapterTitleBar(
                                    title = immersiveChapterTitle,
                                    theme = currentTheme
                                )
                                ReaderHost(
                                    Modifier
                                        .weight(1f)
                                        .fillMaxWidth()
                                )
                            }
                        } else {
                            ReaderHost(Modifier.fillMaxSize())
                        }
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
                else -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = err,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(onClick = { navController.navigateUp() }) {
                            Text("返回书架")
                        }
                    }
                }
            }

            }

            // 阅读进度：浮层，不随大顶栏/底栏挤占正文布局
            if (immersiveReading && !showTopBar) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .height(32.dp)
                        .wrapContentWidth(align = Alignment.End)
                        .navigationBarsPadding()
                        .padding(end = 16.dp, bottom = 10.dp)
                        .zIndex(0.5f),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Text(
                        text = "${(readingProgress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = currentTheme.textColor.copy(alpha = 0.7f),
                        modifier = Modifier
                            .background(
                                color = currentTheme.backgroundColor.copy(alpha = 0.6f),
                                shape = RoundedCornerShape(6.dp)
                            )
                            .padding(vertical = 2.dp, horizontal = 8.dp)
                    )
                }
            }

            // 大顶栏 / 底栏：浮层，显隐不改变正文与章节小标题的布局
            AnimatedVisibility(
                visible = readerChromeVisible,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .zIndex(2f),
                enter = slideInVertically(
                    initialOffsetY = { -it },
                    animationSpec = tween(300)
                ) + fadeIn(animationSpec = tween(300)),
                exit = slideOutVertically(
                    targetOffsetY = { -it },
                    animationSpec = tween(300)
                ) + fadeOut(animationSpec = tween(300)),
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RectangleShape,
                    tonalElevation = 3.dp,
                    shadowElevation = 8.dp,
                    color = systemBarChromeColor
                ) {
                    Column(Modifier.fillMaxWidth()) {
                        Spacer(
                            Modifier
                                .fillMaxWidth()
                                .windowInsetsTopHeight(WindowInsets.statusBars)
                        )
                        ReaderTopAppBar(
                            title = book?.title ?: "阅读中",
                            chapterTitle = chapterTitleRaw,
                            onNavigateBack = { navController.navigateUp() },
                            theme = currentTheme,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = readerChromeVisible,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .zIndex(2f),
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = tween(300)
                ) + fadeIn(animationSpec = tween(300)),
                exit = slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = tween(300)
                ) + fadeOut(animationSpec = tween(300)),
            ) {
                ReaderImmersiveBottomBar(
                    modifier = Modifier.fillMaxWidth(),
                    theme = currentTheme,
                    chromeBackground = systemBarChromeColor,
                    onToc = { showToc = true },
                    onBookmarks = { showBookmarks = true },
                    onThemeBackground = { showReaderThemeSheet = true },
                    onFont = { showReaderFontSheet = true },
                    onPageTurn = { showReaderPageTurnSheet = true }
                )
            }
        }
    }

    if (showReaderPageTurnSheet) {
        ReaderPageTurnSheet(
            currentMode = pageTurnMode,
            onModeSelected = { viewModel.setPageTurnMode(it) },
            onDismiss = {
                showReaderPageTurnSheet = false
                if (immersiveReading) showTopBar = false
            }
        )
    }

    diagramPreviewBitmap?.let { bitmap ->
        DiagramPreviewDialog(
            bitmap = bitmap,
            onDismiss = { diagramPreviewBitmap = null },
        )
    }

    if (showReaderThemeSheet) {
        ReaderThemeSheet(
            currentTheme = currentTheme,
            onThemeChange = { viewModel.setTheme(it) },
            onDismiss = {
                showReaderThemeSheet = false
                if (immersiveReading) showTopBar = false
            }
        )
    }

    if (showReaderFontSheet) {
        ReaderFontSheet(
            fontSize = fontSize,
            readerPaddingDp = readerPaddingDp,
            readerLineSpacingMultiplier = readerLineSpacingMultiplier,
            onFontSizeChange = { viewModel.setFontSize(it) },
            onPaddingDpChange = { viewModel.setReaderPaddingDp(it) },
            onLineSpacingChange = { viewModel.setReaderLineSpacingMultiplier(it) },
            onDismiss = {
                showReaderFontSheet = false
                if (immersiveReading) showTopBar = false
            }
        )
    }

    // 书签列表
    if (showBookmarks) {
        BookmarksSheet(
            bookmarks = bookmarks,
            totalChars = book?.totalChars?.takeIf { it > 0 } ?: readerContent.length.coerceAtLeast(1),
            onBookmarkClick = { bookmark ->
                if (readerContent.isNotEmpty()) {
                    val position = bookmark.position
                    val bookmarkPreview = bookmark.previewText
                    val contentLen = readerContent.length
                    val totalC = book?.totalChars?.takeIf { it > 0 } ?: contentLen
                    val charPos = resolveGlobalCharPos(position, contentLen, totalC)
                    val pdfPageIndex = pdfPageIndexForTocOrBookmark(
                        isPdfBook = isPdfBook,
                        tocEntries = tocEntries,
                        readerContent = readerContent,
                        charPos = charPos,
                        tocEntry = null,
                    )
                    if (pageTurnMode != ReaderPageTurnMode.VerticalScroll && pageSpecs.isNotEmpty()) {
                        jumpToGlobalCharInPager(
                            scope = scope,
                            charPos = charPos,
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
                                viewModel.updateReadingProgressAtChar(charPos, bookmarkPreview)
                            },
                            pdfJumpByPageIndex = isPdfBook,
                            pdfPageIndex = pdfPageIndex,
                        )
                    } else if (isPdfBook && pdfPageIndex != null) {
                        pendingScrollRestoreGlobalChar = null
                        pendingScrollRestoreBookmarkPreview = null
                        pendingScrollRestoreAnchor = null
                        pendingScrollRestoreSnapToLine = true
                        clearPendingSavedPositionSnap(readerTextView.value)
                        coverUntilPositionRestore = true
                        bumpScrollRestoreGeneration()
                        jumpToPdfPageVertically(
                            contentLen = contentLen,
                            chapterBoundaries = chapterBoundaries,
                            pageIndex = pdfPageIndex,
                            tocEntries = tocEntries,
                            setReadingWindow = { start, end ->
                                displayWindowStartChar = start
                                displayWindowEndChar = end
                            },
                            onPendingPdfPageIndex = { pendingScrollRestorePdfPageIndex = it },
                            onProgress = {
                                viewModel.updateReadingProgressAtChar(charPos, null)
                            },
                        )
                    } else {
                        clearPendingScrollCharOffset(readerTextView.value)
                        pendingScrollRestoreBookmarkPreview = bookmarkPreview
                        pendingScrollRestoreAnchor = null
                        pendingScrollRestoreSnapToLine = true
                        coverUntilPositionRestore = true
                        jumpToCharInChunkWindow(
                            contentLen = contentLen,
                            chapterBoundaries = chapterBoundaries,
                            charPos = charPos,
                            setReadingWindow = { start, end ->
                                displayWindowStartChar = start
                                displayWindowEndChar = end
                                stashSavedPositionSnapForPendingRestore(
                                    sourceOffset = charPos,
                                    windowStart = start,
                                    windowEnd = end,
                                    preview = bookmarkPreview,
                                )
                            },
                            onAnchorGlobalChar = { queueScrollRestore(it) },
                            onProgress = {
                                viewModel.updateReadingProgressAtChar(charPos, bookmarkPreview)
                            },
                        )
                    }
                }
                showBookmarks = false
            },
            onDeleteBookmark = { bookmark ->
                viewModel.deleteBookmark(bookmark)
            },
            onDismiss = {
                showBookmarks = false
                if (immersiveReading) showTopBar = false
            }
        )
    }

    // 目录
    if (showToc) {
        TocSheet(
            entries = tocEntries,
            emptyTocMessage = emptyTocMessage,
            onEntryClick = { entry ->
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
                    if (pageTurnMode != ReaderPageTurnMode.VerticalScroll && pageSpecs.isNotEmpty()) {
                        jumpToGlobalCharInPager(
                            scope = scope,
                            charPos = charPos,
                            contentLen = contentLen,
                            sourceContent = readerContent,
                            renderPlainText = renderPlainText,
                            tocEntries = tocEntries,
                            preferredTocEntry = entry,
                            pageSpecs = pageSpecs,
                            pagerState = pagerState,
                            pageTextViews = pageTextViews,
                            assignActiveTextView = { readerTextView.value = it },
                            onProgress = { viewModel.updateReadingProgressAtChar(charPos) },
                            pdfJumpByPageIndex = isPdfBook,
                            pdfPageIndex = pdfPageIndex,
                        )
                    } else if (isPdfBook && pdfPageIndex != null) {
                        pendingScrollRestoreGlobalChar = null
                        pendingScrollRestoreBookmarkPreview = null
                        pendingScrollRestoreAnchor = null
                        pendingScrollRestoreSnapToLine = true
                        clearPendingSavedPositionSnap(readerTextView.value)
                        coverUntilPositionRestore = true
                        bumpScrollRestoreGeneration()
                        jumpToPdfPageVertically(
                            contentLen = contentLen,
                            chapterBoundaries = chapterBoundaries,
                            pageIndex = pdfPageIndex,
                            tocEntries = tocEntries,
                            setReadingWindow = { start, end ->
                                displayWindowStartChar = start
                                displayWindowEndChar = end
                            },
                            onPendingPdfPageIndex = { pendingScrollRestorePdfPageIndex = it },
                            onProgress = { viewModel.updateReadingProgressAtChar(charPos) },
                        )
                    } else {
                        // 目录跳转：目标章节严格置顶，走独立置顶通道，不与 offset/anchor 恢复竞争。
                        clearPendingScrollCharOffset(readerTextView.value)
                        pendingScrollRestoreGlobalChar = null
                        pendingScrollRestoreBookmarkPreview = null
                        pendingScrollRestorePdfPageIndex = null
                        pendingScrollRestoreAnchor = null
                        pendingScrollRestoreSnapToLine = true
                        // 使任何在途的 offset 恢复 LaunchedEffect 失效。
                        bumpScrollRestoreGeneration()
                        windowExpandInFlight = false
                        expandRestoreGuard[0] = false
                        val (_, winEnd) = computeTocJumpReadingWindow(
                            chapterBoundaries,
                            charPos,
                            contentLen,
                        )
                        val winStart = charPos
                        requestReaderScrollToTop(readerTextView.value)
                        displayWindowStartChar = winStart
                        displayWindowEndChar = winEnd.coerceAtLeast((winStart + 1).coerceAtMost(contentLen))
                        viewModel.updateReadingProgressAtChar(charPos)
                        // 目标章节渲染完成后台预扩上文，避免下滑看前文时撞硬顶停顿。
                        prefetchUpTargetChar = if (winStart > 0) winStart else -1
                    }
                }
                showToc = false
                if (immersiveReading) showTopBar = false
            },
            onDismiss = {
                showToc = false
                if (immersiveReading) showTopBar = false
            }
        )
    }

}

@Composable
private fun DiagramPreviewDialog(
    bitmap: Bitmap,
    onDismiss: () -> Unit,
) {
    var scale by remember(bitmap) { mutableFloatStateOf(1f) }
    var offsetX by remember(bitmap) { mutableFloatStateOf(0f) }
    var offsetY by remember(bitmap) { mutableFloatStateOf(0f) }
    val image = remember(bitmap) { bitmap.asImageBitmap() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.94f))
                .pointerInput(bitmap, scale, offsetX, offsetY) {
                    detectTapGestures { tap ->
                        val imageW = bitmap.width.toFloat().coerceAtLeast(1f)
                        val imageH = bitmap.height.toFloat().coerceAtLeast(1f)
                        val fitScale = min(size.width / imageW, size.height / imageH)
                        val drawnW = imageW * fitScale * scale
                        val drawnH = imageH * fitScale * scale
                        val centerX = size.width / 2f + offsetX
                        val centerY = size.height / 2f + offsetY
                        val insideImage =
                            tap.x in (centerX - drawnW / 2f)..(centerX + drawnW / 2f) &&
                                tap.y in (centerY - drawnH / 2f)..(centerY + drawnH / 2f)
                        if (!insideImage) onDismiss()
                    }
                },
        ) {
            Image(
                bitmap = image,
                contentDescription = "图表预览",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(bitmap) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(0.5f, 8f)
                            offsetX += pan.x
                            offsetY += pan.y
                        }
                    }
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offsetX
                        translationY = offsetY
                    },
            )

            Text(
                text = "双指缩放 / 拖动查看",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.72f),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 20.dp),
            )

            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(12.dp)
                    .background(Color.Black.copy(alpha = 0.35f), CircleShape),
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "关闭图表预览",
                    tint = Color.White,
                )
            }
        }
    }
}

