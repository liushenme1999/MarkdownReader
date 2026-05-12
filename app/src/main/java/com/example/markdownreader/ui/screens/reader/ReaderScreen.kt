package com.example.markdownreader.ui.screens.reader

import android.content.Context
import android.text.Spannable
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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.markdownreader.ui.theme.ReadingTheme
import io.noties.markwon.Markwon
import io.noties.markwon.core.CorePlugin
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.linkify.LinkifyPlugin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** [TextView] 上用于判断是否需要重新执行 Markwon 渲染的 tag key */
private const val TAG_READER_RENDER_SIG = 0x4d445f52 // "MD_R"

/** 顶栏/底栏显示时，累计垂直滚动超过该像素后再收起（避免轻微抖动误触） */
private val ReaderHideChromeScrollThreshold = 56.dp

private val ReaderImmersiveBottomBarHeight = 56.dp

@Composable
private fun ReaderImmersiveBottomBar(
    modifier: Modifier = Modifier,
    onToc: () -> Unit,
    onBookmarks: () -> Unit,
    onThemeBackground: () -> Unit,
    onFont: () -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        tonalElevation = 3.dp,
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(ReaderImmersiveBottomBarHeight),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onToc) {
                Icon(
                    imageVector = Icons.Default.Toc,
                    contentDescription = "目录"
                )
            }
            IconButton(onClick = onBookmarks) {
                Icon(
                    imageVector = Icons.Default.Bookmark,
                    contentDescription = "书签"
                )
            }
            IconButton(onClick = onThemeBackground) {
                Icon(
                    imageVector = Icons.Default.Palette,
                    contentDescription = "阅读背景"
                )
            }
            IconButton(onClick = onFont) {
                Icon(
                    imageVector = Icons.Default.TextFields,
                    contentDescription = "字体"
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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
    val currentTheme by viewModel.currentTheme.collectAsState()
    val fontSize by viewModel.fontSize.collectAsState()
    val readerPaddingDp by viewModel.readerPaddingDp.collectAsState()
    val readerLineSpacingMultiplier by viewModel.readerLineSpacingMultiplier.collectAsState()
    val loadError by viewModel.loadError.collectAsState()

    var showReaderThemeSheet by remember { mutableStateOf(false) }
    var showReaderFontSheet by remember { mutableStateOf(false) }
    var showBookmarks by remember { mutableStateOf(false) }
    var showToc by remember { mutableStateOf(false) }
    var selectedText by remember { mutableStateOf("") }
    var showHighlightMenu by remember { mutableStateOf(false) }
    var showTopBar by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val readerTextView = remember { mutableStateOf<TextView?>(null) }

    val immersiveReading = content.isNotEmpty() && loadError == null
    val showTopBarEffective = !immersiveReading || showTopBar

    val tocEntries = remember(content) { parseMarkdownToc(content) }
    val totalChars = book?.totalChars?.takeIf { it > 0 } ?: content.length.coerceAtLeast(1)
    val chapterTitle = remember(tocEntries, readingProgress, totalChars) {
        currentChapterTitleForProgress(tocEntries, readingProgress, totalChars)
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

    LaunchedEffect(bookId, content) {
        if (content.isEmpty()) return@LaunchedEffect
        repeat(40) {
            val tv = readerTextView.value
            if (tv != null && tv.layout != null) {
                tv.post {
                    scrollTextViewToProgress(tv, book?.readingProgress ?: 0f)
                }
                return@LaunchedEffect
            }
            delay(32)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (showTopBarEffective) {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = book?.title ?: "阅读中",
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (chapterTitle != null) {
                                Text(
                                    text = chapterTitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { navController.navigateUp() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                        }
                    }
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(currentTheme.backgroundColor)
        ) {
            when (val err = loadError) {
                null -> {
                    if (content.isNotEmpty()) {
                        MarkdownReaderView(
                            content = content,
                            theme = currentTheme,
                            fontSize = fontSize,
                            readerPaddingDp = readerPaddingDp,
                            readerLineSpacingMultiplier = readerLineSpacingMultiplier,
                            highlights = highlights,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(
                                    bottom = if (immersiveReading && showTopBar) {
                                        ReaderImmersiveBottomBarHeight
                                    } else {
                                        0.dp
                                    }
                                ),
                            onTextSelected = { text ->
                                selectedText = text
                                showHighlightMenu = text.isNotEmpty()
                            },
                            onScroll = { progress ->
                                viewModel.updateReadingProgress(progress)
                            },
                            onReadingVerticalScroll = { deltaPx ->
                                if (immersiveReading && showTopBar) {
                                    scrollAccumForHideChrome += deltaPx
                                    if (scrollAccumForHideChrome >= hideChromeScrollThresholdPx) {
                                        showTopBar = false
                                    }
                                }
                            },
                            onViewReady = { tv -> readerTextView.value = tv },
                            onSwipeRightBookmark = {
                                scope.launch {
                                    when (viewModel.toggleBookmarkAtSwipe()) {
                                        true -> snackbarHostState.showSnackbar("已添加书签")
                                        false -> snackbarHostState.showSnackbar("已取消书签")
                                        null -> { }
                                    }
                                }
                            },
                            onCenterTap = { showTopBar = !showTopBar }
                        )
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

            if (immersiveReading && !showTopBar) {
                Text(
                    text = "${(readingProgress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = currentTheme.textColor.copy(alpha = 0.55f),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = 16.dp)
                )
            }

            if (immersiveReading && showTopBar) {
                ReaderImmersiveBottomBar(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    onToc = { showToc = true },
                    onBookmarks = { showBookmarks = true },
                    onThemeBackground = { showReaderThemeSheet = true },
                    onFont = { showReaderFontSheet = true }
                )
            }
        }
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
                val b = book
                if (b != null && b.totalChars > 0) {
                    val p = (position.toFloat() / b.totalChars).coerceIn(0f, 1f)
                    val tv = readerTextView.value
                    tv?.post {
                        scrollTextViewToProgress(tv, p)
                        viewModel.updateReadingProgress(p)
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
            onEntryClick = { entry ->
                val total = book?.totalChars?.takeIf { it > 0 } ?: content.length.coerceAtLeast(1)
                val p = (entry.sourceOffset.toFloat() / total).coerceIn(0f, 1f)
                val tv = readerTextView.value
                tv?.post {
                    scrollTextViewToProgress(tv, p)
                    viewModel.updateReadingProgress(p)
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

@Composable
private fun MarkdownReaderView(
    content: String,
    theme: ReadingTheme,
    fontSize: Int,
    readerPaddingDp: Int,
    readerLineSpacingMultiplier: Float,
    highlights: List<com.example.markdownreader.data.local.entity.HighlightEntity>,
    modifier: Modifier = Modifier.fillMaxSize(),
    onTextSelected: (String) -> Unit,
    onScroll: (Float) -> Unit,
    onReadingVerticalScroll: (verticalScrollDeltaPx: Int) -> Unit,
    onViewReady: (TextView) -> Unit,
    onSwipeRightBookmark: () -> Unit,
    onCenterTap: () -> Unit
) {
    val context = LocalContext.current
    val markwon = remember { createMarkwon(context) }
    val touchState = remember { ReaderTouchState() }
    val slop = ViewConfiguration.get(context).scaledTouchSlop

    AndroidView(
        factory = { ctx ->
            TextView(ctx).apply {
                movementMethod = LinkMovementMethod.getInstance()
                setTextColor(theme.textColor.toArgb())
                setBackgroundColor(theme.backgroundColor.toArgb())
                textSize = fontSize.toFloat()
                val density = resources.displayMetrics.density
                val padPx = (readerPaddingDp * density).toInt().coerceAtLeast(0)
                setPadding(padPx, padPx, padPx, padPx)
                setLineSpacing(0f, readerLineSpacingMultiplier)

                markwon.setMarkdown(this, content)
                applyHighlightsToRenderedText(this, highlights, theme.highlightColor.toArgb())

                customSelectionActionModeCallback = object : ActionMode.Callback {
                    override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
                        return true
                    }

                    override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean {
                        return false
                    }

                    override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {
                        return false
                    }

                    override fun onDestroyActionMode(mode: ActionMode?) {
                        val start = selectionStart
                        val end = selectionEnd
                        val len = text.length
                        if (start < 0 || end < 0 || len == 0) {
                            onTextSelected("")
                            return
                        }
                        val from = start.coerceAtMost(end).coerceIn(0, len)
                        val to = start.coerceAtLeast(end).coerceIn(0, len)
                        if (from >= to) {
                            onTextSelected("")
                            return
                        }
                        onTextSelected(text.substring(from, to))
                    }
                }

                bindReaderGesturesAndScroll(
                    textView = this,
                    touchState = touchState,
                    slop = slop,
                    onScroll = onScroll,
                    onReadingVerticalScroll = onReadingVerticalScroll,
                    onSwipeRightBookmark = onSwipeRightBookmark,
                    onCenterTap = onCenterTap
                )
                post { onViewReady(this) }
            }
        },
        update = { textView ->
            textView.movementMethod = LinkMovementMethod.getInstance()
            textView.setTextColor(theme.textColor.toArgb())
            textView.setBackgroundColor(theme.backgroundColor.toArgb())
            textView.textSize = fontSize.toFloat()
            val density = textView.resources.displayMetrics.density
            val padPx = (readerPaddingDp * density).toInt().coerceAtLeast(0)
            textView.setPadding(padPx, padPx, padPx, padPx)
            textView.setLineSpacing(0f, readerLineSpacingMultiplier)

            val hlKey = highlights.joinToString("|") { "${it.id}_${it.startPosition}_${it.endPosition}" }
            val renderSig =
                "${content.length}_${content.hashCode()}_${theme::class.java.name}_${fontSize}_$hlKey"
            val prevSig = textView.getTag(TAG_READER_RENDER_SIG) as? String
            if (prevSig != renderSig) {
                textView.setTag(TAG_READER_RENDER_SIG, renderSig)
                markwon.setMarkdown(textView, content)
                applyHighlightsToRenderedText(textView, highlights, theme.highlightColor.toArgb())
            }

            bindReaderGesturesAndScroll(
                textView = textView,
                touchState = touchState,
                slop = slop,
                onScroll = onScroll,
                onReadingVerticalScroll = onReadingVerticalScroll,
                onSwipeRightBookmark = onSwipeRightBookmark,
                onCenterTap = onCenterTap
            )
            textView.post { onViewReady(textView) }
        },
        modifier = modifier
    )
}

private class ReaderTouchState(
    var downX: Float = 0f,
    var downY: Float = 0f
)

private fun bindReaderGesturesAndScroll(
    textView: TextView,
    touchState: ReaderTouchState,
    slop: Int,
    onScroll: (Float) -> Unit,
    onReadingVerticalScroll: (verticalScrollDeltaPx: Int) -> Unit,
    onSwipeRightBookmark: () -> Unit,
    onCenterTap: () -> Unit
) {
    textView.setOnScrollChangeListener { v, _, scrollY, _, oldScrollY ->
        if (scrollY != oldScrollY) {
            onReadingVerticalScroll(kotlin.math.abs(scrollY - oldScrollY))
        }
        val tv = v as? TextView ?: return@setOnScrollChangeListener
        val layout = tv.layout ?: return@setOnScrollChangeListener
        val innerH = tv.height - tv.paddingTop - tv.paddingBottom
        if (innerH <= 0) return@setOnScrollChangeListener
        val total = layout.height
        if (total <= innerH) {
            onScroll(0f)
            return@setOnScrollChangeListener
        }
        val maxScroll = (total - innerH).coerceAtLeast(1)
        val safeY = scrollY.coerceIn(0, maxScroll)
        onScroll((safeY / maxScroll.toFloat()).coerceIn(0f, 1f))
    }

    textView.setOnTouchListener { v, e ->
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchState.downX = e.x
                touchState.downY = e.y
            }
            MotionEvent.ACTION_UP -> {
                val dx = e.x - touchState.downX
                val dy = e.y - touchState.downY
                val adx = kotlin.math.abs(dx)
                val ady = kotlin.math.abs(dy)
                if (adx < slop && ady < slop) {
                    val w = v.width.toFloat()
                    val h = v.height.toFloat()
                    if (w > 0f && h > 0f &&
                        e.x in (w * 0.32f)..(w * 0.68f) &&
                        e.y in (h * 0.36f)..(h * 0.64f)
                    ) {
                        onCenterTap()
                    }
                } else if (dx > 120f && dx > ady * 2f) {
                    onSwipeRightBookmark()
                }
            }
        }
        false
    }
}

private fun scrollTextViewToProgress(tv: TextView, progress: Float) {
    val layout = tv.layout ?: return
    val innerH = tv.height - tv.paddingTop - tv.paddingBottom
    if (innerH <= 0) return
    val maxScroll = (layout.height - innerH).coerceAtLeast(0)
    val y = (maxScroll * progress.coerceIn(0f, 1f)).toInt()
    tv.scrollTo(0, y)
}

private fun createMarkwon(context: Context): Markwon {
    return Markwon.builder(context)
        .usePlugin(CorePlugin.create())
        .usePlugin(HtmlPlugin.create())
        .usePlugin(StrikethroughPlugin.create())
        .usePlugin(TablePlugin.create(context))
        .usePlugin(LinkifyPlugin.create())
        .build()
}

/**
 * 在 Markwon 渲染后的纯文本上按划线内容做背景高亮（源码下标与渲染后 Spanned 长度不一致，故用文本匹配）。
 */
private fun applyHighlightsToRenderedText(
    textView: TextView,
    highlights: List<com.example.markdownreader.data.local.entity.HighlightEntity>,
    highlightColorArgb: Int
) {
    val text = textView.text
    if (text !is Spannable || highlights.isEmpty()) return
    val full = text.toString()
    highlights.forEach { highlight ->
        val snippet = highlight.highlightedText
        if (snippet.isEmpty()) return@forEach
        var searchFrom = 0
        while (searchFrom < full.length) {
            val idx = full.indexOf(snippet, searchFrom)
            if (idx < 0) break
            val end = idx + snippet.length
            text.setSpan(
                BackgroundColorSpan(highlightColorArgb),
                idx,
                end,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            searchFrom = end
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderWideSliderRow(
    label: String,
    valueText: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = valueText,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderThemeSheet(
    currentTheme: ReadingTheme,
    onThemeChange: (ReadingTheme) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Text(
                text = "阅读主题",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                ReadingTheme.allThemes().forEach { theme ->
                    ThemeOption(
                        theme = theme,
                        isSelected = theme == currentTheme,
                        onClick = { onThemeChange(theme) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderFontSheet(
    fontSize: Int,
    readerPaddingDp: Int,
    readerLineSpacingMultiplier: Float,
    onFontSizeChange: (Int) -> Unit,
    onPaddingDpChange: (Int) -> Unit,
    onLineSpacingChange: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "字体设置",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(20.dp))
            ReaderWideSliderRow(
                label = "字体大小",
                valueText = "${fontSize} sp",
                value = fontSize.toFloat(),
                onValueChange = { v ->
                    onFontSizeChange(v.roundToInt().coerceIn(10, 40))
                },
                valueRange = 10f..40f,
                steps = 29
            )
            Spacer(modifier = Modifier.height(28.dp))
            ReaderWideSliderRow(
                label = "页边距",
                valueText = "${readerPaddingDp} dp",
                value = readerPaddingDp.toFloat(),
                onValueChange = { v ->
                    onPaddingDpChange(v.roundToInt().coerceIn(8, 56))
                },
                valueRange = 8f..56f,
                steps = 47
            )
            Spacer(modifier = Modifier.height(28.dp))
            ReaderWideSliderRow(
                label = "行距",
                valueText = "%.2f 倍".format(readerLineSpacingMultiplier),
                value = readerLineSpacingMultiplier,
                onValueChange = onLineSpacingChange,
                valueRange = 1f..2.5f,
                steps = 29
            )
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun ThemeOption(
    theme: ReadingTheme,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(theme.backgroundColor)
                .then(
                    if (isSelected) {
                        Modifier.padding(2.dp)
                    } else Modifier
                )
        ) {
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = theme.textColor,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            theme.name,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookmarksSheet(
    bookmarks: List<com.example.markdownreader.data.local.entity.BookmarkEntity>,
    totalChars: Int,
    onBookmarkClick: (Int) -> Unit,
    onDeleteBookmark: (com.example.markdownreader.data.local.entity.BookmarkEntity) -> Unit,
    onDismiss: () -> Unit
) {
    var revealedBookmarkId by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(bookmarks) {
        revealedBookmarkId = null
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Text(
                "我的书签",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (bookmarks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "暂无书签",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            } else {
                bookmarks.forEachIndexed { index, bookmark ->
                    if (index > 0) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                        )
                    }
                    BookmarkItem(
                        bookmark = bookmark,
                        totalChars = totalChars,
                        revealedBookmarkId = revealedBookmarkId,
                        onRevealChange = { id -> revealedBookmarkId = id },
                        onClick = { onBookmarkClick(bookmark.position) },
                        onDelete = {
                            onDeleteBookmark(bookmark)
                            if (revealedBookmarkId == bookmark.id) {
                                revealedBookmarkId = null
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TocSheet(
    entries: List<MarkdownTocEntry>,
    onEntryClick: (MarkdownTocEntry) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp)
        ) {
            Text(
                "目录",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))
            if (entries.isEmpty()) {
                Text(
                    "未识别到标题。请使用 Markdown ATX 语法，例如：\n# 一级标题\n## 二级标题",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                )
                Spacer(modifier = Modifier.height(24.dp))
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 520.dp)
                ) {
                    itemsIndexed(
                        entries,
                        key = { _, e -> e.sourceOffset }
                    ) { _, entry ->
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onEntryClick(entry) }
                                    .padding(vertical = 10.dp, horizontal = 4.dp)
                                    .padding(
                                        start = ((entry.level - 1).coerceAtLeast(0) * 14).dp
                                    ),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = entry.title,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun BookmarkItem(
    bookmark: com.example.markdownreader.data.local.entity.BookmarkEntity,
    totalChars: Int,
    revealedBookmarkId: Long?,
    onRevealChange: (Long?) -> Unit,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val density = LocalDensity.current
    val deleteWidthPx = with(density) { 72.dp.toPx() }
    var offsetPx by remember(bookmark.id) { mutableFloatStateOf(0f) }
    val revealedIdSnapshot by rememberUpdatedState(revealedBookmarkId)

    LaunchedEffect(revealedBookmarkId, bookmark.id) {
        if (revealedBookmarkId != bookmark.id) {
            offsetPx = 0f
        }
    }

    val progressPercent = remember(bookmark.position, totalChars) {
        if (totalChars <= 0) null
        else ((bookmark.position * 100f) / totalChars).roundToInt().coerceIn(0, 100)
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .matchParentSize()
                .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    onDelete()
                    offsetPx = 0f
                },
                modifier = Modifier
                    .width(72.dp)
                    .fillMaxHeight()
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetPx.roundToInt(), 0) }
                .pointerInput(bookmark.id, deleteWidthPx) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            offsetPx = (offsetPx + dragAmount).coerceIn(-deleteWidthPx, 0f)
                        },
                        onDragEnd = {
                            val threshold = -deleteWidthPx * 0.35f
                            if (offsetPx < threshold) {
                                offsetPx = -deleteWidthPx
                                onRevealChange(bookmark.id)
                            } else {
                                offsetPx = 0f
                                if (revealedIdSnapshot == bookmark.id) {
                                    onRevealChange(null)
                                }
                            }
                        }
                    )
                }
                .clickable {
                    if (offsetPx < -4f) {
                        offsetPx = 0f
                        onRevealChange(null)
                    } else {
                        onClick()
                    }
                }
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.widthIn(min = 44.dp, max = 56.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Bookmark,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(26.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = progressPercent?.let { "$it%" } ?: "—",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = bookmark.previewText,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                if (!bookmark.note.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = bookmark.note,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HighlightActionSheet(
    selectedText: String,
    onHighlight: (Color) -> Unit,
    onAddBookmark: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    var showNoteDialog by remember { mutableStateOf(false) }
    var noteText by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Text(
                "划线与笔记",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                selectedText.take(100) + if (selectedText.length > 100) "..." else "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 高亮颜色选择
            Text("选择高亮颜色", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                val colors = listOf(
                    Color(0xFFFFFF00) to "黄色",
                    Color(0xFF00FF00) to "绿色",
                    Color(0xFF00FFFF) to "青色",
                    Color(0xFFFF00FF) to "粉色",
                    Color(0xFFFFA500) to "橙色"
                )
                colors.forEach { (color, name) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(color)
                                .clickable { onHighlight(color) }
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(name, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 添加书签按钮
            Button(
                onClick = { showNoteDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.BookmarkAdd, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("添加书签笔记")
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // 笔记输入对话框
    if (showNoteDialog) {
        AlertDialog(
            onDismissRequest = { showNoteDialog = false },
            title = { Text("添加笔记") },
            text = {
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text("笔记内容（可选）") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onAddBookmark(noteText.takeIf { it.isNotEmpty() })
                        showNoteDialog = false
                    }
                ) {
                    Text("添加")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNoteDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}
