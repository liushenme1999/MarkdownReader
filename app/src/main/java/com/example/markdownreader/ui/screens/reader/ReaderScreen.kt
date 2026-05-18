package com.example.markdownreader.ui.screens.reader

import android.app.Activity
import android.content.Context
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.zIndex
import androidx.core.graphics.ColorUtils
import androidx.core.text.PrecomputedTextCompat
import androidx.core.view.WindowCompat
import androidx.core.widget.TextViewCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.markdownreader.data.local.entity.HighlightEntity
import com.example.markdownreader.importing.ImportedBookFormat
import com.example.markdownreader.importing.PdfReaderContent
import com.example.markdownreader.model.ReaderPageTurnMode
import com.example.markdownreader.ui.components.ShelfStyleStatusBarBackdrop
import com.example.markdownreader.ui.components.ShelfStyleSystemBarsEffect
import com.example.markdownreader.ui.components.iconTintForDeleteStrip
import com.example.markdownreader.ui.components.shelfStylePageBackground
import com.example.markdownreader.ui.theme.MarkdownReaderTheme
import com.example.markdownreader.ui.theme.ReadingTheme
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
    var selectedText by remember { mutableStateOf("") }
    var showHighlightMenu by remember { mutableStateOf(false) }
    var showTopBar by remember { mutableStateOf(false) }
    var showReaderPageTurnSheet by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val readerTextView = remember { mutableStateOf<TextView?>(null) }

    val immersiveReading = content.isNotEmpty() && loadError == null

    val structuredToc by viewModel.structuredToc.collectAsState()

    val tocEntries = remember(readerContent, renderPlainText, structuredToc, isPdfBook) {
        if (isPdfBook) {
            return@remember PdfReaderContent.tocEntriesFromBody(readerContent).map {
                MarkdownTocEntry(level = it.level, title = it.title, sourceOffset = it.sourceOffset)
            }
        }
        val stored = structuredToc.orEmpty()
        when {
            renderPlainText -> stored.ifEmpty { parsePlainTextToc(readerContent) }
            stored.isNotEmpty() -> stored
            else -> parseMarkdownToc(readerContent)
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
    val chapterTitle = remember(tocEntries, readingProgress, totalChars) {
        currentChapterTitleForProgress(tocEntries, readingProgress, totalChars)
    }

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
    var pendingScrollRestoreY by remember(readerContent) { mutableStateOf<Int?>(null) }
    /** 向上扩窗后，按全书字符锚点恢复视口，避免跳到章节顶部。 */
    var pendingScrollRestoreGlobalChar by remember(readerContent) { mutableStateOf<Int?>(null) }
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

    LaunchedEffect(bookId, readerContent, pageSpecs, readerLoadEpoch, pageTurnMode, readingProgress) {
        if (readerContent.isEmpty()) return@LaunchedEffect
        if (pageTurnMode == ReaderPageTurnMode.VerticalScroll || pageSpecs.isEmpty()) return@LaunchedEffect
        val totalC = book?.totalChars?.takeIf { it > 0 } ?: readerContent.length
        val charPos = resolveGlobalCharPos(
            (readingProgress * totalC).toInt(),
            readerContent.length,
            totalC,
        )
        jumpToGlobalCharInPager(
            scope = this,
            charPos = charPos,
            contentLen = readerContent.length,
            sourceContent = readerContent,
            renderPlainText = renderPlainText,
            tocEntries = tocEntries,
            pageSpecs = pageSpecs,
            pagerState = pagerState,
            pageTextViews = pageTextViews,
            assignActiveTextView = { readerTextView.value = it },
            onProgress = {
                viewModel.updateReadingProgress(
                    readingProgressForCharPos(charPos, readerContent.length),
                )
            },
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
            viewModel.updateReadingProgress(start.toFloat() / totalChars.coerceAtLeast(1))
        }
    }

    val density = LocalDensity.current
    val hideChromeScrollThresholdPx = remember(density) {
        with(density) { ReaderHideChromeScrollThreshold.roundToPx() }
    }
    var scrollAccumForHideChrome by remember { mutableIntStateOf(0) }

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

    LaunchedEffect(bookId, readerContent, pageTurnMode, readerLoadEpoch) {
        if (readerContent.isEmpty()) {
            displayWindowStartChar = 0
            displayWindowEndChar = 0
            return@LaunchedEffect
        }
        if (pageTurnMode != ReaderPageTurnMode.VerticalScroll) {
            pendingScrollRestoreY = null
            displayWindowStartChar = 0
            displayWindowEndChar = readerContent.length
            return@LaunchedEffect
        }
        val totalC = book?.totalChars?.takeIf { it > 0 } ?: readerContent.length
        val targetChar = resolveGlobalCharPos(
            (readingProgress * totalC).toInt(),
            readerContent.length,
            totalC,
        )
        val (start, end) = computeReadingWindow(chapterBoundaries, targetChar, readerContent.length)
        pendingScrollRestoreY = null
        displayWindowStartChar = start
        displayWindowEndChar = end
        pendingScrollRestoreGlobalChar = targetChar
    }

    // 向下扩窗：复用扩窗前的 scrollY（尾部追加，前面 layout 高度不变）。
    LaunchedEffect(displayWindowEndChar) {
        val savedY = pendingScrollRestoreY ?: return@LaunchedEffect
        val tv = readerTextView.value ?: run {
            pendingScrollRestoreY = null
            return@LaunchedEffect
        }
        repeat(120) {
            val layout = tv.layout
            val tvTextLen = tv.text?.length ?: 0
            val layoutTextLen = layout?.text?.length ?: -1
            if (layout != null && tvTextLen > 0 && layoutTextLen == tvTextLen) {
                val maxScroll = (layout.height - (tv.height - tv.paddingTop - tv.paddingBottom)).coerceAtLeast(0)
                tv.post { tv.scrollTo(0, savedY.coerceIn(0, maxScroll)) }
                pendingScrollRestoreY = null
                return@LaunchedEffect
            }
            delay(32)
        }
        pendingScrollRestoreY = null
    }

    // 目录/书签跳转、向上扩窗：等新窗口正文写入 TextView 后再按锚点滚动。
    LaunchedEffect(
        displayWindowStartChar,
        displayWindowEndChar,
        pendingScrollRestoreGlobalChar,
        renderPlainText,
    ) {
        val anchorGlobal = pendingScrollRestoreGlobalChar ?: return@LaunchedEffect
        if (readerContent.isEmpty()) {
            pendingScrollRestoreGlobalChar = null
            return@LaunchedEffect
        }
        val winStart = displayWindowStartChar
        val expectedLen = if (renderPlainText) {
            (displayWindowEndChar - winStart).coerceAtLeast(0)
        } else {
            null
        }
        val tv = awaitReaderTextViewLayout(
            tvProvider = { readerTextView.value },
            maxAttempts = 120,
            expectedTextLength = expectedLen,
        ) ?: run {
            pendingScrollRestoreGlobalChar = null
            return@LaunchedEffect
        }
        val safeAnchor = anchorGlobal.coerceIn(0, readerContent.length - 1)
        val preferredEntry = tocEntries.find { it.sourceOffset == safeAnchor }
        val offset = resolveDisplayedCharOffset(
            sourceContent = readerContent,
            sourceOffset = safeAnchor,
            displayedText = tv.text,
            renderPlainText = renderPlainText,
            windowStart = winStart,
            tocEntries = tocEntries,
            preferredEntry = preferredEntry,
        )
        tv.post { scrollTextViewToCharOffset(tv, offset) }
        pendingScrollRestoreGlobalChar = null
    }

    val systemBarChromeColor = if (isPdfBook) {
        shelfStylePageBackground()
    } else {
        readingChromeShade(currentTheme.backgroundColor)
    }

    val view = LocalView.current
    ShelfStyleSystemBarsEffect(systemBarChromeColor)

    DisposableEffect(Unit) {
        val activity = view.context as? Activity
        if (activity == null) {
            return@DisposableEffect onDispose { }
        }
        val window = activity.window
        val controller = WindowCompat.getInsetsController(window, view)
        val prevStatusColor = window.statusBarColor
        val prevLightStatusBars = controller.isAppearanceLightStatusBars
        val prevNavColor = window.navigationBarColor
        val prevLightNavBars = controller.isAppearanceLightNavigationBars
        onDispose {
            window.statusBarColor = prevStatusColor
            controller.isAppearanceLightStatusBars = prevLightStatusBars
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
            if (!immersiveReading) {
                ReaderTopAppBar(
                    title = book?.title ?: "阅读中",
                    chapterTitle = chapterTitle,
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
                        val onReaderTextSelected: (String) -> Unit = { text ->
                            selectedText = text
                            showHighlightMenu = text.isNotEmpty()
                        }
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
                                when (viewModel.toggleBookmarkAtSwipe(previewForAdd = preview)) {
                                    true -> snackbarHostState.showBriefSnackbar("已添加书签")
                                    false -> snackbarHostState.showBriefSnackbar("已取消书签")
                                    null -> { }
                                }
                            }
                        }
                        // 沉浸模式章节小标题常驻，不随大顶栏/底栏显隐改变布局（避免点击唤出工具栏时正文跳动）
                        val immersiveChapterTitle = chapterTitle.takeIf { immersiveReading && !isPdfBook }
                        val readerPaddingTopDp = if (immersiveChapterTitle != null) {
                            minOf(ReaderChapterStripBodyTopPaddingDp, readerPaddingDp)
                        } else {
                            readerPaddingDp
                        }

                        @Composable
                        fun ReaderHost(modifier: Modifier) {
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
                                    modifier = modifier,
                                    onTextSelected = onReaderTextSelected,
                                    onScroll = { localProgress ->
                                        val winStart = displayWindowStartChar
                                        val winEnd = displayWindowEndChar
                                        val winSpan = (winEnd - winStart).coerceAtLeast(1)
                                        val total = readerContent.length.coerceAtLeast(1)
                                        val globalChar = (winStart + localProgress * winSpan)
                                            .toInt()
                                            .coerceIn(0, readerContent.length)
                                        viewModel.updateReadingProgress(
                                            globalChar.toFloat() / total
                                        )
                                        val tv = readerTextView.value
                                        if (winStart > 0 &&
                                            pendingScrollRestoreGlobalChar == null &&
                                            pendingScrollRestoreY == null &&
                                            localProgress <= READER_EXPAND_TRIGGER_NEAR_START_PROGRESS
                                        ) {
                                            val topGlobal = winStart + (tv?.let { charOffsetAtScrollTop(it) } ?: 0)
                                            pendingScrollRestoreGlobalChar = topGlobal.coerceIn(0, readerContent.length)
                                            displayWindowStartChar = previousWindowStart(
                                                chapterBoundaries, winStart, 0
                                            )
                                        } else if (winEnd < readerContent.length &&
                                            pendingScrollRestoreY == null &&
                                            pendingScrollRestoreGlobalChar == null &&
                                            localProgress >= READER_EXPAND_TRIGGER_LOCAL_PROGRESS
                                        ) {
                                            pendingScrollRestoreY = tv?.scrollY ?: 0
                                            displayWindowEndChar = nextWindowEnd(
                                                chapterBoundaries, winEnd, readerContent.length
                                            )
                                        }
                                    },
                                    onReadingVerticalScroll = onReaderVerticalScroll,
                                    onViewReady = { tv -> readerTextView.value = tv },
                                    allowVerticalScroll = true,
                                    onSwipeRightBookmark = onReaderSwipeBookmark,
                                    onCenterTap = { showTopBar = !showTopBar },
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
                                    modifier = modifier,
                                    onTextSelected = onReaderTextSelected,
                                    onReadingVerticalScroll = onReaderVerticalScroll,
                                    onSwipeDownBookmark = onReaderSwipeBookmark,
                                    onCenterTap = { showTopBar = !showTopBar },
                                    onPageTextViewReady = { pageIdx, tv ->
                                        if (pageIdx == pagerState.currentPage) {
                                            readerTextView.value = tv
                                        }
                                    },
                                    pdfFullWidthImages = isPdfBook,
                                )
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
                visible = immersiveReading && showTopBar,
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
                            chapterTitle = chapterTitle,
                            onNavigateBack = { navController.navigateUp() },
                            theme = currentTheme,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = immersiveReading && showTopBar,
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
            onBookmarkClick = { position ->
                if (readerContent.isNotEmpty()) {
                    val contentLen = readerContent.length
                    val totalC = book?.totalChars?.takeIf { it > 0 } ?: contentLen
                    val charPos = resolveGlobalCharPos(position, contentLen, totalC)
                    val p = readingProgressForCharPos(charPos, contentLen)
                    if (pageTurnMode != ReaderPageTurnMode.VerticalScroll && pageSpecs.isNotEmpty()) {
                        jumpToGlobalCharInPager(
                            scope = scope,
                            charPos = charPos,
                            contentLen = contentLen,
                            sourceContent = readerContent,
                            renderPlainText = renderPlainText,
                            tocEntries = tocEntries,
                            pageSpecs = pageSpecs,
                            pagerState = pagerState,
                            pageTextViews = pageTextViews,
                            assignActiveTextView = { readerTextView.value = it },
                            onProgress = { viewModel.updateReadingProgress(p) }
                        )
                    } else {
                        pendingScrollRestoreY = null
                        jumpToCharInChunkWindow(
                            contentLen = contentLen,
                            chapterBoundaries = chapterBoundaries,
                            charPos = charPos,
                            setReadingWindow = { start, end ->
                                displayWindowStartChar = start
                                displayWindowEndChar = end
                            },
                            onAnchorGlobalChar = { pendingScrollRestoreGlobalChar = it },
                            onProgress = { viewModel.updateReadingProgress(p) },
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
                    val p = readingProgressForCharPos(charPos, contentLen)
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
                            onProgress = { viewModel.updateReadingProgress(p) }
                        )
                    } else {
                        pendingScrollRestoreY = null
                        jumpToCharInChunkWindow(
                            contentLen = contentLen,
                            chapterBoundaries = chapterBoundaries,
                            charPos = charPos,
                            setReadingWindow = { start, end ->
                                displayWindowStartChar = start
                                displayWindowEndChar = end
                            },
                            onAnchorGlobalChar = { pendingScrollRestoreGlobalChar = it },
                            onProgress = { viewModel.updateReadingProgress(p) },
                        )
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

    // 高亮菜单
    if (showHighlightMenu) {
        HighlightActionSheet(
            selectedText = selectedText,
            onHighlight = { color ->
                viewModel.addHighlight(selectedText, color)
                showHighlightMenu = false
            },
            onAddBookmark = { note ->
                viewModel.addBookmark(selectedText, note)
                showHighlightMenu = false
            },
            onDismiss = {
                showHighlightMenu = false
                if (immersiveReading) showTopBar = false
            }
        )
    }
}

