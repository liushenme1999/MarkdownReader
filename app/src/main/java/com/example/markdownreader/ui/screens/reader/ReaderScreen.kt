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
import com.example.markdownreader.model.ReaderPageTurnMode
import com.example.markdownreader.ui.components.iconTintForDeleteStrip
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
    val renderPlainText = importFormat?.usesReaderPlainBody == true

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

    val tocEntries = remember(content, renderPlainText, structuredToc, importFormat) {
        val stored = structuredToc.orEmpty()
        when {
            renderPlainText -> parsePlainTextToc(content)
            importFormat == ImportedBookFormat.MOBI || importFormat == ImportedBookFormat.AZW3 -> {
                val fromBody = parseMarkdownToc(content)
                when {
                    fromBody.isNotEmpty() -> fromBody
                    stored.isNotEmpty() -> stored
                    else -> parsePlainTextToc(content)
                }
            }
            importFormat == ImportedBookFormat.EPUB && stored.isNotEmpty() -> stored
            stored.isNotEmpty() -> stored
            else -> parseMarkdownToc(content)
        }
    }
    val emptyTocMessage = remember(importFormat, renderPlainText) {
        when (importFormat) {
            ImportedBookFormat.EPUB ->
                "未解析到 EPUB 目录（toc.ncx 或 nav）。正文已按 spine 合并，仍可按进度阅读。"
            ImportedBookFormat.MOBI, ImportedBookFormat.AZW3 ->
                "未从正文识别到常见章节标题。若为 Huff/CDIC 压缩的 MOBI，当前版本可能无法解压。"
            else ->
                if (renderPlainText) {
                    "未识别到章节。请将章节标题单独成行，例如：\n第一章 …、第1节 …、Chapter 1 …"
                } else {
                    "未识别到标题。请使用 Markdown ATX 语法，例如：\n# 一级标题\n## 二级标题"
                }
        }
    }
    val totalChars = book?.totalChars?.takeIf { it > 0 } ?: content.length.coerceAtLeast(1)
    val chapterTitle = remember(tocEntries, readingProgress, totalChars) {
        currentChapterTitleForProgress(tocEntries, readingProgress, totalChars)
    }

    val pageSpecs = remember(content, pageTurnMode, fontSize, configuration.screenHeightDp, configuration.screenWidthDp) {
        when (pageTurnMode) {
            ReaderPageTurnMode.VerticalScroll -> emptyList()
            else -> splitMarkdownToPages(
                content,
                estimateTargetCharsPerPage(fontSize, configuration.screenHeightDp, configuration.screenWidthDp)
            )
        }
    }
    val pageTextViews = remember(content) { mutableMapOf<Int, TextView>() }
    val pagerState = rememberPagerState(pageCount = { pageSpecs.size.coerceAtLeast(1) })

    // ===== 章节惰性渲染窗口（仅 VerticalScroll 模式生效）=====
    val chapterBoundaries = remember(content, tocEntries) {
        computeChapterBoundaries(content, tocEntries)
    }
    var displayWindowStartChar by remember(content, readerLoadEpoch) { mutableIntStateOf(0) }
    var displayWindowEndChar by remember(content, readerLoadEpoch) { mutableIntStateOf(0) }
    var pendingScrollRestoreY by remember(content) { mutableStateOf<Int?>(null) }
    val displayedContent = remember(content, displayWindowStartChar, displayWindowEndChar) {
        when {
            content.isEmpty() -> ""
            displayWindowStartChar >= content.length -> ""
            displayWindowEndChar <= displayWindowStartChar -> ""
            displayWindowEndChar >= content.length -> content.substring(displayWindowStartChar)
            else -> content.substring(displayWindowStartChar, displayWindowEndChar)
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

    LaunchedEffect(bookId, content, pageSpecs, readerLoadEpoch, pageTurnMode, readingProgress) {
        if (content.isEmpty()) return@LaunchedEffect
        if (pageTurnMode == ReaderPageTurnMode.VerticalScroll || pageSpecs.isEmpty()) return@LaunchedEffect
        val totalC = book?.totalChars?.takeIf { it > 0 } ?: content.length
        val charPos = resolveGlobalCharPos(
            (readingProgress * totalC).toInt(),
            content.length,
            totalC
        )
        jumpToGlobalCharInPager(
            scope = this,
            charPos = charPos,
            contentLen = content.length,
            sourceContent = content,
            renderPlainText = renderPlainText,
            tocEntries = tocEntries,
            pageSpecs = pageSpecs,
            pagerState = pagerState,
            pageTextViews = pageTextViews,
            assignActiveTextView = { readerTextView.value = it },
            onProgress = {
                viewModel.updateReadingProgress(readingProgressForCharPos(charPos, content.length))
            }
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

    LaunchedEffect(bookId, content, pageTurnMode, readerLoadEpoch) {
        if (content.isEmpty()) {
            displayWindowStartChar = 0
            displayWindowEndChar = 0
            return@LaunchedEffect
        }
        if (pageTurnMode != ReaderPageTurnMode.VerticalScroll) {
            pendingScrollRestoreY = null
            displayWindowStartChar = 0
            displayWindowEndChar = content.length
            return@LaunchedEffect
        }
        val totalC = book?.totalChars?.takeIf { it > 0 } ?: content.length
        val targetChar = resolveGlobalCharPos(
            (readingProgress * totalC).toInt(),
            content.length,
            totalC
        )
        val (start, end) = computeReadingWindow(chapterBoundaries, targetChar, content.length)
        pendingScrollRestoreY = null
        displayWindowStartChar = start
        displayWindowEndChar = end
        val tv = awaitReaderTextViewLayout({ readerTextView.value }, maxAttempts = 80)
        tv?.post {
            val offset = resolveDisplayedCharOffset(
                sourceContent = content,
                sourceOffset = targetChar,
                displayedText = tv.text,
                renderPlainText = renderPlainText,
                windowStart = start,
                tocEntries = tocEntries,
            )
            scrollTextViewToCharOffset(tv, offset)
        }
    }

    // 扩窗（displayWindowEndChar 增加）后还原扩窗前 scrollY，避免视口跳到顶部。
    // 因为 displayedContent 是前缀且只在尾部追加，前面字符的 layout 高度保持稳定，
    // scrollY 可以直接复用扩窗前的像素值。
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

    val view = LocalView.current
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

    SideEffect {
        val activity = view.context as? Activity ?: return@SideEffect
        val window = activity.window
        val controller = WindowCompat.getInsetsController(window, view)
        val chromeArgb = readingChromeShade(currentTheme.backgroundColor).toArgb()
        val lightSystemBars = ColorUtils.calculateLuminance(chromeArgb) < 0.5
        window.statusBarColor = chromeArgb
        controller.isAppearanceLightStatusBars = lightSystemBars
        window.navigationBarColor = chromeArgb
        controller.isAppearanceLightNavigationBars = lightSystemBars
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
                .background(currentTheme.backgroundColor)
        ) {
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
                        val immersiveChapterTitle = chapterTitle.takeIf { immersiveReading }
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
                                    readerPaddingTopDp = readerPaddingTopDp,
                                    readerLineSpacingMultiplier = readerLineSpacingMultiplier,
                                    highlights = displayedHighlights,
                                    modifier = modifier,
                                    onTextSelected = onReaderTextSelected,
                                    onScroll = { localProgress ->
                                        val winStart = displayWindowStartChar
                                        val winEnd = displayWindowEndChar
                                        val winSpan = (winEnd - winStart).coerceAtLeast(1)
                                        val total = content.length.coerceAtLeast(1)
                                        val globalChar = (winStart + localProgress * winSpan)
                                            .toInt()
                                            .coerceIn(0, content.length)
                                        viewModel.updateReadingProgress(
                                            globalChar.toFloat() / total
                                        )
                                        if (winEnd < content.length &&
                                            pendingScrollRestoreY == null &&
                                            localProgress >= READER_EXPAND_TRIGGER_LOCAL_PROGRESS
                                        ) {
                                            val tv = readerTextView.value
                                            pendingScrollRestoreY = tv?.scrollY ?: 0
                                            displayWindowEndChar = nextWindowEnd(
                                                chapterBoundaries, winEnd, content.length
                                            )
                                        }
                                    },
                                    onReadingVerticalScroll = onReaderVerticalScroll,
                                    onViewReady = { tv -> readerTextView.value = tv },
                                    allowVerticalScroll = true,
                                    onSwipeRightBookmark = onReaderSwipeBookmark,
                                    onCenterTap = { showTopBar = !showTopBar }
                                )
                            } else {
                                ReaderPagedMarkdownHost(
                                    pages = pageSpecs,
                                    pageTurnMode = pageTurnMode,
                                    pagerState = pagerState,
                                    theme = currentTheme,
                                    fontSize = fontSize,
                                    readerPaddingDp = readerPaddingDp,
                                    readerPaddingTopDp = readerPaddingTopDp,
                                    readerLineSpacingMultiplier = readerLineSpacingMultiplier,
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
                                    }
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
                    color = readingChromeShade(currentTheme.backgroundColor)
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
                    chromeBackground = readingChromeShade(currentTheme.backgroundColor),
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
            totalChars = book?.totalChars?.takeIf { it > 0 } ?: content.length.coerceAtLeast(1),
            onBookmarkClick = { position ->
                if (content.isNotEmpty()) {
                    val contentLen = content.length
                    val totalC = book?.totalChars?.takeIf { it > 0 } ?: contentLen
                    val charPos = resolveGlobalCharPos(position, contentLen, totalC)
                    val p = readingProgressForCharPos(charPos, contentLen)
                    if (pageTurnMode != ReaderPageTurnMode.VerticalScroll && pageSpecs.isNotEmpty()) {
                        jumpToGlobalCharInPager(
                            scope = scope,
                            charPos = charPos,
                            contentLen = contentLen,
                            sourceContent = content,
                            renderPlainText = renderPlainText,
                            tocEntries = tocEntries,
                            pageSpecs = pageSpecs,
                            pagerState = pagerState,
                            pageTextViews = pageTextViews,
                            assignActiveTextView = { readerTextView.value = it },
                            onProgress = { viewModel.updateReadingProgress(p) }
                        )
                    } else {
                        jumpToCharInChunkWindow(
                            scope = scope,
                            tvProvider = { readerTextView.value },
                            sourceContent = content,
                            renderPlainText = renderPlainText,
                            contentLen = contentLen,
                            chapterBoundaries = chapterBoundaries,
                            charPos = charPos,
                            tocEntries = tocEntries,
                            setReadingWindow = { start, end ->
                                displayWindowStartChar = start
                                displayWindowEndChar = end
                            },
                            clearPendingRestore = { pendingScrollRestoreY = null },
                            onProgress = { viewModel.updateReadingProgress(p) }
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
                if (content.isNotEmpty()) {
                    val contentLen = content.length
                    val charPos = entry.sourceOffset.coerceIn(0, (contentLen - 1).coerceAtLeast(0))
                    val p = readingProgressForCharPos(charPos, contentLen)
                    if (pageTurnMode != ReaderPageTurnMode.VerticalScroll && pageSpecs.isNotEmpty()) {
                        jumpToGlobalCharInPager(
                            scope = scope,
                            charPos = charPos,
                            contentLen = contentLen,
                            sourceContent = content,
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
                        jumpToCharInChunkWindow(
                            scope = scope,
                            tvProvider = { readerTextView.value },
                            sourceContent = content,
                            renderPlainText = renderPlainText,
                            contentLen = contentLen,
                            chapterBoundaries = chapterBoundaries,
                            charPos = charPos,
                            tocEntries = tocEntries,
                            preferredTocEntry = entry,
                            setReadingWindow = { start, end ->
                                displayWindowStartChar = start
                                displayWindowEndChar = end
                            },
                            clearPendingRestore = { pendingScrollRestoreY = null },
                            onProgress = { viewModel.updateReadingProgress(p) }
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

