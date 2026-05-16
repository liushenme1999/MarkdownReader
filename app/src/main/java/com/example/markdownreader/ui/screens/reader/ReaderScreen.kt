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

/** 略深于阅读区底色，用于顶栏/状态栏/底栏/系统导航条，便于与正文区分 */
private fun readingChromeShade(readingBackground: Color): Color =
    lerp(readingBackground, Color.Black, 0.04f)

/** 删除条为 error 底时，避免 onError 与底色过于接近或 IconButton 的 contentColor 盖住矢量，保证垃圾桶可见。 */
private fun iconTintForDeleteStrip(error: Color): Color {
    val l = error.red * 0.299f + error.green * 0.587f + error.blue * 0.114f
    return if (l > 0.55f) Color(0xFF1C1B1F) else Color.White
}

/** [TextView] 上用于判断是否需要重新执行 Markwon 渲染的 tag key */
private const val TAG_READER_RENDER_SIG = 0x4d445f52 // "MD_R"

/** 顶栏/底栏显示时，累计垂直滚动超过该像素后再收起（避免轻微抖动误触） */
private val ReaderHideChromeScrollThreshold = 56.dp

private val ReaderImmersiveBottomBarHeight = 56.dp

/** 沉浸章节条显示时，正文 TextView 顶部额外留白（dp），略小于常规页边距 */
private const val ReaderChapterStripBodyTopPaddingDp = 4

@Composable
private fun ReaderImmersiveChapterTitleBar(
    title: String,
    theme: ReadingTheme,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(theme.backgroundColor)
    ) {
        Spacer(
            Modifier
                .fillMaxWidth()
                .windowInsetsTopHeight(WindowInsets.statusBars)
        )
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = theme.textColor.copy(alpha = 0.7f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun ReaderImmersiveBottomBar(
    modifier: Modifier = Modifier,
    theme: ReadingTheme,
    chromeBackground: Color,
    onToc: () -> Unit,
    onBookmarks: () -> Unit,
    onThemeBackground: () -> Unit,
    onFont: () -> Unit,
    onPageTurn: () -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        tonalElevation = 3.dp,
        shadowElevation = 8.dp,
        color = chromeBackground
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ReaderImmersiveBottomBarHeight),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
            val iconTint = theme.textColor
            IconButton(onClick = onToc) {
                Icon(
                    imageVector = Icons.Default.Toc,
                    contentDescription = "目录",
                    tint = iconTint
                )
            }
            IconButton(onClick = onBookmarks) {
                Icon(
                    imageVector = Icons.Default.Bookmark,
                    contentDescription = "书签",
                    tint = iconTint
                )
            }
            IconButton(onClick = onThemeBackground) {
                Icon(
                    imageVector = Icons.Default.Palette,
                    contentDescription = "阅读背景",
                    tint = iconTint
                )
            }
            IconButton(onClick = onFont) {
                Icon(
                    imageVector = Icons.Default.TextFields,
                    contentDescription = "字体",
                    tint = iconTint
                )
            }
            IconButton(onClick = onPageTurn) {
                Icon(
                    imageVector = Icons.Default.ImportContacts,
                    contentDescription = "翻页设置",
                    tint = iconTint
                )
            }
            }
            // 底栏背景延伸到屏幕底，图标区在导航条/小白条之上
            Spacer(
                Modifier
                    .fillMaxWidth()
                    .windowInsetsBottomHeight(WindowInsets.navigationBars)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderTopAppBar(
    title: String,
    chapterTitle: String?,
    onNavigateBack: () -> Unit,
    theme: ReadingTheme,
    modifier: Modifier = Modifier
) {
    TopAppBar(
        modifier = modifier,
        // Scaffold topBar 或外层 statusBarsPadding 已处理顶 inset，避免与 TopAppBar 默认 insets 叠加
        windowInsets = WindowInsets(),
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = readingChromeShade(theme.backgroundColor),
            titleContentColor = theme.textColor,
            navigationIconContentColor = theme.textColor,
            actionIconContentColor = theme.textColor
        ),
        title = {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = theme.textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (chapterTitle != null) {
                    Text(
                        text = chapterTitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = theme.secondaryTextColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "返回")
            }
        }
    )
}

private suspend fun SnackbarHostState.showBriefSnackbar(message: String, visibleMs: Long = 1400L) {
    coroutineScope {
        val snackJob = launch {
            showSnackbar(message = message, duration = SnackbarDuration.Indefinite)
        }
        delay(visibleMs)
        currentSnackbarData?.dismiss()
        snackJob.join()
    }
}

private fun estimateTargetCharsPerPage(fontSize: Int, screenHeightDp: Int, screenWidthDp: Int): Int {
    val lineHeight = fontSize * 1.55f
    val lines = (screenHeightDp / lineHeight).toInt().coerceAtLeast(4)
    val charsPerLine = (screenWidthDp / (fontSize * 0.48f)).toInt().coerceAtLeast(8)
    return (lines * charsPerLine).coerceIn(900, 14_000)
}

/** 将正文切成多段用于横向分页；返回 (片段, 在全文中的起始下标)。 */
private fun splitMarkdownToPages(content: String, targetChars: Int): List<Pair<String, Int>> {
    if (content.isEmpty()) return listOf("" to 0)
    if (targetChars < 200) return listOf(content to 0)
    val result = mutableListOf<Pair<String, Int>>()
    var idx = 0
    while (idx < content.length) {
        val start = idx
        var end = (start + targetChars).coerceAtMost(content.length)
        if (end < content.length) {
            val slice = content.substring(start, end)
            val paraBreak = slice.lastIndexOf("\n\n")
            val lineBreak = slice.lastIndexOf('\n')
            val breakAt = maxOf(
                if (paraBreak >= targetChars / 6) paraBreak else -1,
                if (lineBreak >= targetChars / 6) lineBreak else -1
            )
            if (breakAt >= 0) {
                end = start + breakAt + 1
            }
        }
        if (end <= start) {
            end = (start + 1).coerceAtMost(content.length)
        }
        result += content.substring(start, end) to start
        idx = end
    }
    return result
}

// ============== 章节惰性渲染窗口工具（仅用于 VerticalScroll 模式） ==============
// 仅渲染 [windowStart, windowEnd) 片段，避免目录跳转到书末时把 0..目标 整段前缀塞进 TextView。
// 章节边界优先使用 TOC.sourceOffset，无 TOC 时按固定步长虚构边界。

/** 一次扩窗最少推进的字符数；同时也是无 TOC 时虚构边界的步长。 */
private const val READER_EXPAND_CHUNK_CHARS = 32 * 1024

/** 进入阅读 / 跳转时目标位置之后的预读缓冲。 */
private const val READER_INITIAL_LOOKAHEAD_CHARS = 16 * 1024

/** 单窗口最大字符数，防止单章过长再次卡顿。 */
private const val READER_MAX_WINDOW_CHARS = 96 * 1024

/** 当窗口内 local 进度超过该比例且仍有未渲染章节，触发扩窗。 */
private const val READER_EXPAND_TRIGGER_LOCAL_PROGRESS = 0.78f

/** 计算章节边界数组（升序、含 0 与 content.length）。 */
private fun computeChapterBoundaries(content: String, toc: List<MarkdownTocEntry>): IntArray {
    if (content.isEmpty()) return intArrayOf(0)
    val set = sortedSetOf(0, content.length)
    for (e in toc) {
        if (e.sourceOffset in 1 until content.length) set.add(e.sourceOffset)
    }
    // 无 TOC 或粗粒度时，按固定步长补齐切点，保证巨型 TXT/MD 也能惰性化
    if (set.size <= 2 && content.length > READER_EXPAND_CHUNK_CHARS * 2) {
        var p = READER_EXPAND_CHUNK_CHARS
        while (p < content.length) {
            set.add(p)
            p += READER_EXPAND_CHUNK_CHARS
        }
    }
    return set.toIntArray()
}

/** 把窗口右沿撑到「至少覆盖 atLeast」的下一个章节边界，硬上限 hardCap。 */
private fun expandWindowEndToCover(boundaries: IntArray, atLeast: Int, hardCap: Int): Int {
    if (hardCap <= 0) return 0
    if (boundaries.isEmpty()) return hardCap
    val target = atLeast.coerceIn(0, hardCap)
    var lo = 0
    var hi = boundaries.size - 1
    while (lo < hi) {
        val mid = (lo + hi) ushr 1
        if (boundaries[mid] >= target) hi = mid else lo = mid + 1
    }
    return boundaries[lo].coerceAtMost(hardCap)
}

/** 在当前 windowEnd 基础上至少推进 [READER_EXPAND_CHUNK_CHARS] 并对齐到下一章节边界。 */
private fun nextWindowEnd(boundaries: IntArray, currentEnd: Int, hardCap: Int): Int {
    if (currentEnd >= hardCap) return hardCap
    val target = (currentEnd + READER_EXPAND_CHUNK_CHARS).coerceAtMost(hardCap)
    return expandWindowEndToCover(boundaries, target, hardCap)
}

private fun chapterStartBefore(boundaries: IntArray, charPos: Int): Int {
    if (boundaries.isEmpty()) return 0
    val idx = boundaries.indexOfLast { it <= charPos }.coerceAtLeast(0)
    return boundaries[idx]
}

/** 围绕 [charPos] 计算阅读窗口 [start, end)，不再从全书开头截取。 */
private fun computeReadingWindow(
    boundaries: IntArray,
    charPos: Int,
    contentLen: Int,
    lookaheadChars: Int = READER_INITIAL_LOOKAHEAD_CHARS,
): Pair<Int, Int> {
    if (contentLen <= 0) return 0 to 0
    val safe = charPos.coerceIn(0, contentLen - 1)
    var start = chapterStartBefore(boundaries, safe)
    var end = expandWindowEndToCover(boundaries, safe + lookaheadChars, contentLen)
    if (end <= start) end = (start + 1).coerceAtMost(contentLen)
    if (end - start > READER_MAX_WINDOW_CHARS) {
        start = (safe - READER_MAX_WINDOW_CHARS / 5).coerceAtLeast(chapterStartBefore(boundaries, safe))
        end = (start + READER_MAX_WINDOW_CHARS).coerceAtMost(contentLen)
        if (safe >= end) end = (safe + 1).coerceAtMost(contentLen)
    }
    return start to end
}

private fun extractSourceLineAt(source: String, sourceOffset: Int): String {
    if (source.isEmpty()) return ""
    val safe = sourceOffset.coerceIn(0, source.lastIndex.coerceAtLeast(0))
    val lineStart = source.lastIndexOf('\n', safe - 1).let { if (it < 0) 0 else it + 1 }
    val lineEnd = source.indexOf('\n', safe).let { if (it < 0) source.length else it }
    return source.substring(lineStart, lineEnd).trim().take(200)
}

private fun anchorSearchCandidates(line: String, tocTitle: String? = null): List<String> {
    val out = mutableListOf<String>()
    if (!tocTitle.isNullOrBlank()) out += tocTitle.trim()
    if (line.isNotEmpty()) {
        val plain = line.trimStart('#').trim()
        val stripped = plain.replace(Regex("""[*_`>#\[\]()!]"""), "").trim()
        out += listOf(line, plain, stripped)
    }
    return out
        .map { it.trim() }
        .filter { it.length >= 2 }
        .distinct()
}

private fun normalizeAnchorKey(text: String): String =
    text.replace(Regex("""[*_`~>#\[\]()!]"""), "")
        .replace(Regex("""\s+"""), " ")
        .trim()
        .lowercase()

private fun titleMatchesRenderedLine(renderedLine: String, tocTitle: String): Boolean {
    val a = normalizeAnchorKey(renderedLine)
    val b = normalizeAnchorKey(tocTitle)
    if (a.isEmpty() || b.isEmpty()) return false
    return a == b || a.contains(b) || b.contains(a)
}

private fun lineAt(text: String, start: Int): String {
    if (start < 0 || start >= text.length) return ""
    val end = text.indexOf('\n', start).let { if (it < 0) text.length else it }
    return text.substring(start, end).trim()
}

/** Markwon 为 ATX 标题打的 span；顺序与正文中的标题出现顺序一致。 */
private fun markdownHeadingSpanStarts(text: CharSequence): List<Int> {
    if (text !is Spanned) return emptyList()
    val spans = text.getSpans(0, text.length, HeadingSpan::class.java)
    if (spans.isEmpty()) return emptyList()
    return spans
        .map { text.getSpanStart(it) }
        .distinct()
        .sorted()
}

/** 当前窗口内、位于 [sourceOffset] 之前的目录标题个数（用于第 N 个标题定位）。 */
private fun headingRankInWindow(
    tocEntries: List<MarkdownTocEntry>,
    sourceOffset: Int,
    windowStart: Int,
): Int = tocEntries.count { it.sourceOffset >= windowStart && it.sourceOffset < sourceOffset }

private fun tocIndexForSourceOffset(
    tocEntries: List<MarkdownTocEntry>,
    sourceOffset: Int,
): Int {
    val exact = tocEntries.indexOfFirst { it.sourceOffset == sourceOffset }
    if (exact >= 0) return exact
    return tocEntries.indexOfLast { it.sourceOffset <= sourceOffset }.coerceAtLeast(0)
}

/** 在 [haystack] 中查找 [needle] 的第 [occurrence] 次出现（0-based），忽略大小写。 */
private fun findOccurrenceIndex(haystack: String, needle: String, occurrence: Int): Int {
    if (needle.length < 2 || occurrence < 0) return -1
    var count = 0
    var from = 0
    while (from < haystack.length) {
        val idx = haystack.indexOf(needle, from, ignoreCase = true)
        if (idx < 0) return -1
        if (count == occurrence) return idx
        count++
        from = idx + 1
    }
    return -1
}

/**
 * 将「源码字符下标」映射到当前 TextView 已渲染文本中的下标。
 * Markwon 渲染后长度与 Markdown 源码不一致：优先用 HeadingSpan 序号，其次按窗口内第 N 次标题文本匹配。
 */
private fun resolveDisplayedCharOffset(
    sourceContent: String,
    sourceOffset: Int,
    displayedText: CharSequence?,
    renderPlainText: Boolean,
    windowStart: Int,
    tocEntries: List<MarkdownTocEntry>,
    preferredEntry: MarkdownTocEntry? = null,
): Int {
    val displayed = displayedText?.toString().orEmpty()
    val len = displayed.length
    if (len == 0) return 0
    if (renderPlainText) {
        return (sourceOffset - windowStart).coerceIn(0, (len - 1).coerceAtLeast(0))
    }

    val entry = preferredEntry
        ?: tocEntries.getOrNull(tocIndexForSourceOffset(tocEntries, sourceOffset))
    val rankInWindow = headingRankInWindow(tocEntries, sourceOffset, windowStart)
    val rankByTitleInWindow = entry?.let { e ->
        tocEntries.count {
            it.sourceOffset >= windowStart &&
                it.sourceOffset < e.sourceOffset &&
                normalizeAnchorKey(it.title) == normalizeAnchorKey(e.title)
        }
    } ?: rankInWindow

    val headingStarts = markdownHeadingSpanStarts(displayedText ?: displayed)
    if (headingStarts.isNotEmpty()) {
        if (rankInWindow in headingStarts.indices) {
            val atRank = headingStarts[rankInWindow]
            if (entry == null || titleMatchesRenderedLine(lineAt(displayed, atRank), entry.title)) {
                return atRank.coerceIn(0, (len - 1).coerceAtLeast(0))
            }
        }
        if (entry != null) {
            var titleMatchIndex = 0
            for (start in headingStarts) {
                if (titleMatchesRenderedLine(lineAt(displayed, start), entry.title)) {
                    if (titleMatchIndex == rankByTitleInWindow) {
                        return start.coerceIn(0, (len - 1).coerceAtLeast(0))
                    }
                    titleMatchIndex++
                }
            }
        }
    }

    val anchorLine = extractSourceLineAt(sourceContent, sourceOffset)
    val candidates = anchorSearchCandidates(anchorLine, entry?.title)
    for (candidate in candidates) {
        val idx = findOccurrenceIndex(displayed, candidate, rankByTitleInWindow)
        if (idx >= 0) return idx.coerceIn(0, (len - 1).coerceAtLeast(0))
    }

    return (sourceOffset - windowStart).coerceIn(0, (len - 1).coerceAtLeast(0))
}

/** 将存储坐标（书签 position / totalChars）映射到当前正文字符下标。 */
private fun resolveGlobalCharPos(
    storedOffset: Int,
    contentLength: Int,
    totalChars: Int,
): Int {
    if (contentLength <= 0) return 0
    val mapped = when {
        totalChars <= 0 || totalChars == contentLength -> storedOffset
        else -> (storedOffset.toLong() * contentLength / totalChars.toLong()).toInt()
    }
    return mapped.coerceIn(0, (contentLength - 1).coerceAtLeast(0))
}

private fun readingProgressForCharPos(charPos: Int, contentLength: Int): Float =
    (charPos.toFloat() / contentLength.coerceAtLeast(1)).coerceIn(0f, 1f)

/** layout 已基于当前文本测量完成。 */
private fun isReaderTextViewLayoutReady(tv: TextView): Boolean {
    val layout = tv.layout ?: return false
    val len = tv.text?.length ?: 0
    return len > 0 && layout.text?.length == len
}

private suspend fun awaitReaderTextViewLayout(
    tvProvider: () -> TextView?,
    maxAttempts: Int = 100,
): TextView? {
    repeat(maxAttempts) {
        val tv = tvProvider()
        if (tv != null && isReaderTextViewLayoutReady(tv)) {
            return tv
        }
        kotlinx.coroutines.delay(32)
    }
    return tvProvider()
}

/** 按 layout 行顶滚动到字符偏移，比「字符比例 ≈ scrollY」更贴近目录/书签目标。 */
private fun scrollTextViewToCharOffset(tv: TextView, charOffsetInText: Int) {
    val layout = tv.layout ?: return
    val len = tv.text?.length ?: 0
    if (len == 0) return
    val offset = charOffsetInText.coerceIn(0, (len - 1).coerceAtLeast(0))
    val line = layout.getLineForOffset(offset).coerceIn(0, (layout.lineCount - 1).coerceAtLeast(0))
    val innerH = tv.height - tv.paddingTop - tv.paddingBottom
    if (innerH <= 0) return
    val lineTop = layout.getLineTop(line)
    val maxScroll = (layout.height - innerH).coerceAtLeast(0)
    tv.scrollTo(0, lineTop.coerceIn(0, maxScroll))
}

/**
 * 章节窗口模式的目录/书签跳转：切换窗口片段 → 等待渲染 → 锚点定位滚动。
 */
private fun jumpToCharInChunkWindow(
    scope: kotlinx.coroutines.CoroutineScope,
    tvProvider: () -> TextView?,
    sourceContent: String,
    renderPlainText: Boolean,
    contentLen: Int,
    chapterBoundaries: IntArray,
    charPos: Int,
    tocEntries: List<MarkdownTocEntry>,
    preferredTocEntry: MarkdownTocEntry? = null,
    setReadingWindow: (start: Int, end: Int) -> Unit,
    clearPendingRestore: () -> Unit,
    onProgress: () -> Unit,
) {
    val safeCharPos = charPos.coerceIn(0, (contentLen - 1).coerceAtLeast(0))
    val (winStart, winEnd) = computeReadingWindow(chapterBoundaries, safeCharPos, contentLen)
    clearPendingRestore()
    setReadingWindow(winStart, winEnd)

    scope.launch {
        val tv = awaitReaderTextViewLayout(tvProvider)
        if (tv != null) {
            tv.post {
                val displayedOffset = resolveDisplayedCharOffset(
                    sourceContent = sourceContent,
                    sourceOffset = safeCharPos,
                    displayedText = tv.text,
                    renderPlainText = renderPlainText,
                    windowStart = winStart,
                    tocEntries = tocEntries,
                    preferredEntry = preferredTocEntry,
                )
                scrollTextViewToCharOffset(tv, displayedOffset)
                onProgress()
            }
        } else {
            onProgress()
        }
    }
}

/** 横向分页模式：切页后按锚点/页内偏移滚动。 */
private fun jumpToGlobalCharInPager(
    scope: kotlinx.coroutines.CoroutineScope,
    charPos: Int,
    contentLen: Int,
    sourceContent: String,
    renderPlainText: Boolean,
    tocEntries: List<MarkdownTocEntry>,
    preferredTocEntry: MarkdownTocEntry? = null,
    pageSpecs: List<Pair<String, Int>>,
    pagerState: PagerState,
    pageTextViews: MutableMap<Int, TextView>,
    assignActiveTextView: (TextView) -> Unit,
    onProgress: () -> Unit,
) {
    if (pageSpecs.isEmpty()) {
        onProgress()
        return
    }
    val safeCharPos = charPos.coerceIn(0, (contentLen - 1).coerceAtLeast(0))
    val page = pageIndexForGlobalChar(pageSpecs, safeCharPos).coerceIn(0, pageSpecs.lastIndex)
    val globalStart = pageSpecs[page].second

    scope.launch {
        pagerState.scrollToPage(page)
        val tv = awaitReaderTextViewLayout({ pageTextViews[page] })
        if (tv != null) {
            assignActiveTextView(tv)
            tv.post {
                val displayedOffset = resolveDisplayedCharOffset(
                    sourceContent = sourceContent,
                    sourceOffset = safeCharPos,
                    displayedText = tv.text,
                    renderPlainText = renderPlainText,
                    windowStart = globalStart,
                    tocEntries = tocEntries,
                    preferredEntry = preferredTocEntry,
                )
                scrollTextViewToCharOffset(tv, displayedOffset)
                onProgress()
            }
        } else {
            onProgress()
        }
    }
}

private fun highlightsForPageSlice(
    globalStart: Int,
    globalExclusiveEnd: Int,
    highlights: List<HighlightEntity>,
    slice: String
): List<HighlightEntity> {
    if (slice.isEmpty()) return emptyList()
    val endCap = globalExclusiveEnd.coerceAtMost(globalStart + slice.length)
    return highlights.mapNotNull { h ->
        if (h.endPosition <= globalStart || h.startPosition >= endCap) return@mapNotNull null
        val s = (h.startPosition - globalStart).coerceIn(0, slice.length)
        val e = (h.endPosition - globalStart).coerceIn(0, slice.length)
        if (e <= s) return@mapNotNull null
        val text = slice.substring(s, e)
        h.copy(startPosition = s, endPosition = e, highlightedText = text)
    }
}

private fun pageIndexForGlobalChar(pages: List<Pair<String, Int>>, charPos: Int): Int =
    pages.indexOfLast { it.second <= charPos }.coerceAtLeast(0)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ReaderPagedMarkdownHost(
    pages: List<Pair<String, Int>>,
    pageTurnMode: ReaderPageTurnMode,
    pagerState: PagerState,
    theme: ReadingTheme,
    fontSize: Int,
    readerPaddingDp: Int,
    readerPaddingTopDp: Int = readerPaddingDp,
    readerLineSpacingMultiplier: Float,
    highlights: List<HighlightEntity>,
    pageTextViews: MutableMap<Int, TextView>,
    renderPlainText: Boolean,
    modifier: Modifier = Modifier,
    onTextSelected: (String) -> Unit,
    onReadingVerticalScroll: (Int) -> Unit,
    onSwipeDownBookmark: () -> Unit,
    onCenterTap: () -> Unit,
    onPageTextViewReady: (Int, TextView) -> Unit
) {
    val layoutDirection = LocalLayoutDirection.current
    val cameraDistancePx = with(LocalDensity.current) { 12f * density * 80f }

    HorizontalPager(
        state = pagerState,
        modifier = modifier.fillMaxSize()
    ) { pageIndex ->
        val outOfCenter = (pagerState.currentPage - pageIndex) + pagerState.currentPageOffsetFraction
        val pageModifier = when (pageTurnMode) {
            ReaderPageTurnMode.HorizontalSwipe ->
                Modifier.fillMaxSize()
            ReaderPageTurnMode.SimulationPageTurn ->
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        this.cameraDistance = cameraDistancePx
                        transformOrigin = TransformOrigin(
                            pivotFractionX = if (layoutDirection == LayoutDirection.Ltr) 0f else 1f,
                            pivotFractionY = 0.5f
                        )
                        rotationY = (-outOfCenter * 62f).coerceIn(-82f, 82f)
                        alpha = 1f - 0.12f * abs(outOfCenter).coerceIn(0f, 1.5f)
                    }
            ReaderPageTurnMode.CoverPageTurn ->
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = size.width * outOfCenter * 0.92f
                    }
            else -> Modifier.fillMaxSize()
        }

        val (slice, globalStart) = pages[pageIndex]
        val globalEndExclusive = if (pageIndex + 1 < pages.size) {
            pages[pageIndex + 1].second
        } else {
            Int.MAX_VALUE
        }
        val pageHighlights = remember(highlights, slice, globalStart, globalEndExclusive) {
            highlightsForPageSlice(globalStart, globalEndExclusive, highlights, slice)
        }

        Box(modifier = pageModifier) {
            MarkdownReaderView(
                content = slice,
                renderPlainText = renderPlainText,
                theme = theme,
                fontSize = fontSize,
                readerPaddingDp = readerPaddingDp,
                readerPaddingTopDp = readerPaddingTopDp,
                readerLineSpacingMultiplier = readerLineSpacingMultiplier,
                highlights = pageHighlights,
                modifier = Modifier.fillMaxSize(),
                onTextSelected = onTextSelected,
                onScroll = { },
                onReadingVerticalScroll = onReadingVerticalScroll,
                onViewReady = { tv ->
                    pageTextViews[pageIndex] = tv
                    onPageTextViewReady(pageIndex, tv)
                },
                allowVerticalScroll = false,
                onSwipeDownBookmark = onSwipeDownBookmark,
                onSwipeRightBookmark = {},
                onCenterTap = onCenterTap
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderPageTurnSheet(
    currentMode: ReaderPageTurnMode,
    onModeSelected: (ReaderPageTurnMode) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Text(
                text = "翻页方式",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "横向模式按段落估算分页，复杂排版可能与上下滚动略有差异。左右滑动/仿真/覆盖翻页时：仅可左右翻页，向下滑动添加书签，再次滑动取消书签。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            ReaderPageTurnMode.values().forEach { mode ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onModeSelected(mode)
                            onDismiss()
                        }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = mode == currentMode,
                        onClick = {
                            onModeSelected(mode)
                            onDismiss()
                        }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = mode.label,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

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

/** 长纯文本 PrecomputedText 在后台算布局，减轻主线程测量（MIUI 上易触发 ANR 日志） */
private val readerPlainTextPrecomputeExecutor =
    java.util.concurrent.Executors.newSingleThreadExecutor { r ->
        Thread(r, "reader-plain-precompute").apply { isDaemon = true }
    }

/** Markwon 解析 + 渲染（Markdown → Spanned）也搬到后台线程，主线程只剩 setText + measure。 */
private val readerMarkwonRenderExecutor =
    java.util.concurrent.Executors.newSingleThreadExecutor { r ->
        Thread(r, "reader-markwon-render").apply {
            isDaemon = true
            priority = Thread.NORM_PRIORITY - 1
        }
    }

private const val PLAIN_TEXT_PRECOMPUTE_THRESHOLD = 6000

/** Markwon 渲染的"小内容"门槛：< 该长度直接主线程同步，避免线程切换开销让首屏更慢。 */
private const val MARKWON_BACKGROUND_RENDER_THRESHOLD = 4000

private fun applyReaderTextContent(
    textView: TextView,
    content: String,
    renderPlainText: Boolean,
    renderSig: String,
    markwon: Markwon,
    highlights: List<HighlightEntity>,
    highlightColorArgb: Int
) {
    textView.setTag(TAG_READER_RENDER_SIG, renderSig)
    if (!renderPlainText) {
        applyMarkdownContent(textView, content, renderSig, markwon, highlights, highlightColorArgb)
        return
    }
    if (content.length <= PLAIN_TEXT_PRECOMPUTE_THRESHOLD) {
        val sp = SpannableString(content)
        textView.setText(sp, TextView.BufferType.SPANNABLE)
        applyHighlightsToRenderedText(textView, highlights, highlightColorArgb)
        return
    }
    val params = TextViewCompat.getTextMetricsParams(textView)
    readerPlainTextPrecomputeExecutor.execute {
        val pre = PrecomputedTextCompat.create(content, params)
        textView.post {
            if (textView.getTag(TAG_READER_RENDER_SIG) != renderSig) return@post
            TextViewCompat.setPrecomputedText(textView, pre)
            applyHighlightsToRenderedText(textView, highlights, highlightColorArgb)
        }
    }
}

private fun applyMarkdownContent(
    textView: TextView,
    content: String,
    renderSig: String,
    markwon: Markwon,
    highlights: List<HighlightEntity>,
    highlightColorArgb: Int
) {
    if (content.length <= MARKWON_BACKGROUND_RENDER_THRESHOLD) {
        // 短文本同步走完，UI 首帧响应更直接
        markwon.setMarkdown(textView, content)
        applyHighlightsToRenderedText(textView, highlights, highlightColorArgb)
        return
    }
    // 长 Markdown：在后台线程做 CommonMark parse + Markwon render（产 Spanned），
    // 这一步通常 200~500ms（取决于内容长度与图片数）。主线程只剩 setText + measure。
    readerMarkwonRenderExecutor.execute {
        val rendered: CharSequence = runCatching { markwon.toMarkdown(content) }
            .getOrNull() ?: content
        textView.post {
            if (textView.getTag(TAG_READER_RENDER_SIG) != renderSig) return@post
            // setParsedMarkdown 会在主线程上把 Spanned 应用到 TextView，并执行
            // Markwon 各插件的 `afterSetText`（如启动 AsyncDrawable 图片加载）。
            markwon.setParsedMarkdown(textView, rendered as? android.text.Spanned ?: android.text.SpannableString(rendered))
            applyHighlightsToRenderedText(textView, highlights, highlightColorArgb)
        }
    }
}

@Composable
private fun MarkdownReaderView(
    content: String,
    renderPlainText: Boolean,
    theme: ReadingTheme,
    fontSize: Int,
    readerPaddingDp: Int,
    readerPaddingTopDp: Int = readerPaddingDp,
    readerLineSpacingMultiplier: Float,
    highlights: List<com.example.markdownreader.data.local.entity.HighlightEntity>,
    modifier: Modifier = Modifier.fillMaxSize(),
    onTextSelected: (String) -> Unit,
    onScroll: (Float) -> Unit,
    onReadingVerticalScroll: (verticalScrollDeltaPx: Int) -> Unit,
    onViewReady: (TextView) -> Unit,
    allowVerticalScroll: Boolean = true,
    onSwipeRightBookmark: () -> Unit = {},
    onSwipeDownBookmark: (() -> Unit)? = null,
    onCenterTap: () -> Unit
) {
    val context = LocalContext.current
    val markwon = remember { createMarkwon(context) }
    val touchState = remember { ReaderTouchState() }
    val slop = ViewConfiguration.get(context).scaledTouchSlop

    AndroidView(
        factory = { ctx ->
            SafeReaderTextView(ctx).apply {
                this.allowVerticalScroll = allowVerticalScroll
                movementMethod = LinkMovementMethod.getInstance()
                setTextColor(theme.textColor.toArgb())
                setBackgroundColor(theme.backgroundColor.toArgb())
                textSize = fontSize.toFloat()
                val density = resources.displayMetrics.density
                val padPx = (readerPaddingDp * density).toInt().coerceAtLeast(0)
                val padTopPx = (readerPaddingTopDp * density).toInt().coerceAtLeast(0)
                setPadding(padPx, padTopPx, padPx, padPx)
                setLineSpacing(0f, readerLineSpacingMultiplier)

                val hlKey0 = highlights.joinToString("|") { "${it.id}_${it.startPosition}_${it.endPosition}" }
                val sig0 =
                    "${renderPlainText}_${content.length}_${content.hashCode()}_${theme::class.java.name}_${fontSize}_$hlKey0"
                applyReaderTextContent(
                    textView = this,
                    content = content,
                    renderPlainText = renderPlainText,
                    renderSig = sig0,
                    markwon = markwon,
                    highlights = highlights,
                    highlightColorArgb = theme.highlightColor.toArgb()
                )

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
                    allowVerticalScroll = allowVerticalScroll,
                    onScroll = onScroll,
                    onReadingVerticalScroll = onReadingVerticalScroll,
                    onSwipeRightBookmark = onSwipeRightBookmark,
                    onSwipeDownBookmark = onSwipeDownBookmark,
                    onCenterTap = onCenterTap
                )
                post { onViewReady(this) }
            }
        },
        update = { textView ->
            (textView as? SafeReaderTextView)?.allowVerticalScroll = allowVerticalScroll
            textView.movementMethod = LinkMovementMethod.getInstance()
            textView.setTextColor(theme.textColor.toArgb())
            textView.setBackgroundColor(theme.backgroundColor.toArgb())
            textView.textSize = fontSize.toFloat()
            val density = textView.resources.displayMetrics.density
            val padPx = (readerPaddingDp * density).toInt().coerceAtLeast(0)
            val padTopPx = (readerPaddingTopDp * density).toInt().coerceAtLeast(0)
            textView.setPadding(padPx, padTopPx, padPx, padPx)
            textView.setLineSpacing(0f, readerLineSpacingMultiplier)

            val hlKey = highlights.joinToString("|") { "${it.id}_${it.startPosition}_${it.endPosition}" }
            val renderSig =
                "${renderPlainText}_${content.length}_${content.hashCode()}_${theme::class.java.name}_${fontSize}_$hlKey"
            val prevSig = textView.getTag(TAG_READER_RENDER_SIG) as? String
            if (prevSig != renderSig) {
                applyReaderTextContent(
                    textView = textView,
                    content = content,
                    renderPlainText = renderPlainText,
                    renderSig = renderSig,
                    markwon = markwon,
                    highlights = highlights,
                    highlightColorArgb = theme.highlightColor.toArgb()
                )
            }

            bindReaderGesturesAndScroll(
                textView = textView,
                touchState = touchState,
                slop = slop,
                allowVerticalScroll = allowVerticalScroll,
                onScroll = onScroll,
                onReadingVerticalScroll = onReadingVerticalScroll,
                onSwipeRightBookmark = onSwipeRightBookmark,
                onSwipeDownBookmark = onSwipeDownBookmark,
                onCenterTap = onCenterTap
            )
            if (!allowVerticalScroll) {
                textView.scrollTo(0, 0)
            }
            textView.post { onViewReady(textView) }
        },
        modifier = modifier
    )
}

private class ReaderTouchState(
    var downX: Float = 0f,
    var downY: Float = 0f,
    var scrollYOnDown: Int = 0
)

private fun bindReaderGesturesAndScroll(
    textView: TextView,
    touchState: ReaderTouchState,
    slop: Int,
    allowVerticalScroll: Boolean,
    onScroll: (Float) -> Unit,
    onReadingVerticalScroll: (verticalScrollDeltaPx: Int) -> Unit,
    onSwipeRightBookmark: () -> Unit,
    onSwipeDownBookmark: (() -> Unit)? = null,
    onCenterTap: () -> Unit
) {
    textView.setOnScrollChangeListener { v, _, scrollY, _, oldScrollY ->
        if (!allowVerticalScroll) return@setOnScrollChangeListener
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
        val tv = v as? TextView
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchState.downX = e.x
                touchState.downY = e.y
                touchState.scrollYOnDown = tv?.scrollY ?: 0
                if (!allowVerticalScroll) {
                    v.parent?.requestDisallowInterceptTouchEvent(false)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (!allowVerticalScroll && tv != null) {
                    val dx = e.x - touchState.downX
                    val dy = e.y - touchState.downY
                    val adx = kotlin.math.abs(dx)
                    val ady = kotlin.math.abs(dy)
                    when {
                        ady > adx && ady > slop -> {
                            // 纵向手势由 TextView 消费，避免上下滚动；横向交给 HorizontalPager 翻页
                            v.parent?.requestDisallowInterceptTouchEvent(true)
                            return@setOnTouchListener true
                        }
                        adx > ady && adx > slop -> {
                            v.parent?.requestDisallowInterceptTouchEvent(false)
                            return@setOnTouchListener false
                        }
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!allowVerticalScroll) {
                    v.parent?.requestDisallowInterceptTouchEvent(false)
                }
                if (e.actionMasked != MotionEvent.ACTION_UP) return@setOnTouchListener false
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
                } else if (allowVerticalScroll && dx > 120f && dx > ady * 2f) {
                    onSwipeRightBookmark()
                } else if (onSwipeDownBookmark != null &&
                    dy > 120f &&
                    dy > adx * 2f &&
                    (!allowVerticalScroll ||
                        (tv != null && touchState.scrollYOnDown == 0 && tv.scrollY == 0))
                ) {
                    onSwipeDownBookmark.invoke()
                    return@setOnTouchListener true
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

/** 取 TextView 当前视口顶部附近可见的纯文本，用作书签预览。 */
private fun previewPlainTextFromTextViewTop(tv: TextView): String {
    val layout = tv.layout ?: return ""
    val text = tv.text ?: return ""
    val len = text.length
    if (len == 0) return ""
    val padTop = tv.compoundPaddingTop
    val y = (tv.scrollY + padTop).coerceAtLeast(0)
    val line = layout.getLineForVertical(y).coerceIn(0, (layout.lineCount - 1).coerceAtLeast(0))
    val start = layout.getLineStart(line).coerceIn(0, (len - 1).coerceAtLeast(0))
    val end = (start + 160).coerceAtMost(len)
    return text.substring(start, end)
        .replace('\n', ' ')
        .trim()
        .ifEmpty { "书签" }
        .take(100)
}

/**
 * 阅读器用的 TextView 子类，吞掉两类已知 Android 框架 bug：
 *
 * 1. `Editor.performLongClick` 里访问尚未初始化的
 *    `SelectionModifierCursorController` 抛 `NullPointerException`
 *    （https://issuetracker.google.com/issues/37095917 起就存在，
 *    MIUI / Android 11/12 上仍可复现）。
 *
 * 2. `ArrowKeyMovementMethod.onTouchEvent` 在长文 selection 边界
 *    偶发 `IndexOutOfBoundsException`。
 *
 * 这些都是 framework 内部状态问题，无法在应用层根治；包一层 catch
 * 让长按时退化为「不进入文本选择」即可，比直接 crash 体验好得多。
 */
private class SafeReaderTextView(context: Context) : TextView(context) {
    /** 分页翻页模式下为 false：禁止上下滚动，仅由 HorizontalPager 横向翻页 */
    var allowVerticalScroll: Boolean = true

    init {
        // 必须显式开启 textIsSelectable，否则 Editor.startSelectionActionMode() 会走
        // textCanBeSelected() 检查直接取消选择，日志表现为
        //   "TextView does not support text selection. Selection cancelled."
        // setTextIsSelectable(true) 会顺带把 movementMethod 重置为 ArrowKeyMovementMethod，
        // 立刻覆盖回 LinkMovementMethod 以保留 url 点击行为；mTextIsSelectable=true
        // 不受影响，长按选词仍能进入 ActionMode。
        setTextIsSelectable(true)
        movementMethod = LinkMovementMethod.getInstance()
        isVerticalScrollBarEnabled = false
    }

    override fun canScrollVertically(direction: Int): Boolean =
        allowVerticalScroll && super.canScrollVertically(direction)

    override fun scrollTo(x: Int, y: Int) {
        if (allowVerticalScroll) {
            super.scrollTo(x, y)
        } else {
            super.scrollTo(x, 0)
        }
    }

    override fun performLongClick(): Boolean = try {
        super.performLongClick()
    } catch (_: NullPointerException) {
        false
    }

    override fun performLongClick(x: Float, y: Float): Boolean = try {
        super.performLongClick(x, y)
    } catch (_: NullPointerException) {
        false
    }

    override fun onTouchEvent(event: MotionEvent): Boolean = try {
        if (!allowVerticalScroll) {
            // 分页模式：禁用纵向滚动，保留链接点击
            val spannable = text as? Spannable
            movementMethod?.onTouchEvent(this, spannable, event) == true
        } else {
            super.onTouchEvent(event)
        }
    } catch (_: NullPointerException) {
        false
    } catch (_: IndexOutOfBoundsException) {
        false
    }
}

private fun createMarkwon(context: Context): Markwon {
    return Markwon.builder(context)
        .usePlugin(CorePlugin.create())
        // JLatexMathPlugin 启用 inline `$...$` 模式后会 require 这个插件；
        // 同时它的 commonmark inline parser 会替换 CorePlugin 默认的解析器，
        // 让 `$...$` 不再被当作普通文本拆分。
        .usePlugin(MarkwonInlineParserPlugin.create())
        .usePlugin(HtmlPlugin.create())
        .usePlugin(StrikethroughPlugin.create())
        .usePlugin(TablePlugin.create(context))
        .usePlugin(LinkifyPlugin.create())
        .usePlugin(
            ImagesPlugin.create { plugin ->
                // 仅启用 file:// 本地图片：EPUB/MOBI 内嵌图已落盘到 parsed_books/<id>/assets/，
                // ParsedBookStorage.readBundle 读取时把 book-asset:// 占位换成了 file://。
                // 出于隐私 / 流量考虑暂不启用 HTTP 远程图片加载。
                plugin.addSchemeHandler(FileSchemeHandler.create())
            }
        )
        .usePlugin(
            // `$$...$$` 块、`$...$` 内联 LaTeX 公式渲染（基于 jlatexmath）。
            // EPUB/MOBI 中如有 MathML，HtmlToMarkdownConverter 会先把它转换为 $...$ / $$...$$。
            JLatexMathPlugin.create(context.resources.getDimension(android.R.dimen.app_icon_size) / 2f) { builder ->
                builder.inlinesEnabled(true)
            }
        )
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
    val allThemes = ReadingTheme.allThemes()
    val firstRow = allThemes.take(3)
    val secondRow = allThemes.drop(3)

    ModalBottomSheet(
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp)
        ) {
            Text(
                text = "阅读主题",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))

            // 第一行：3个主题
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                firstRow.forEach { theme ->
                    ThemeCardOption(
                        theme = theme,
                        isSelected = theme == currentTheme,
                        onClick = { onThemeChange(theme) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 第二行：剩余主题居中
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.weight(1f))
                secondRow.forEach { theme ->
                    ThemeCardOption(
                        theme = theme,
                        isSelected = theme == currentTheme,
                        onClick = { onThemeChange(theme) },
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
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
private fun ThemeCardOption(
    theme: ReadingTheme,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accentColor = MaterialTheme.colorScheme.primary
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = theme.backgroundColor
            ),
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .then(
                    if (isSelected) {
                        Modifier.border(
                            width = 2.5.dp,
                            color = accentColor,
                            shape = RoundedCornerShape(12.dp)
                        )
                    } else {
                        Modifier.border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                ),
            elevation = CardDefaults.cardElevation(
                defaultElevation = if (isSelected) 4.dp else 1.dp
            )
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(6.dp)
                ) {
                    // 文字颜色预览行
                    Text(
                        text = "Aa",
                        style = MaterialTheme.typography.bodyMedium,
                        color = theme.textColor,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "文字",
                        style = MaterialTheme.typography.labelSmall,
                        color = theme.secondaryTextColor
                    )
                }

                // 选中勾选标记
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .size(18.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            theme.name,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) accentColor else MaterialTheme.colorScheme.onSurfaceVariant
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
    emptyTocMessage: String,
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
                    emptyTocMessage,
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
        Box(
            modifier = Modifier.matchParentSize(),
            contentAlignment = Alignment.CenterEnd
        ) {
            Box(
                modifier = Modifier
                    .width(72.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.error),
                contentAlignment = Alignment.Center
            ) {
                val err = MaterialTheme.colorScheme.error
                val deleteIconTint = iconTintForDeleteStrip(err)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable {
                            onDelete()
                            offsetPx = 0f
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "删除",
                        modifier = Modifier.size(28.dp),
                        tint = deleteIconTint
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                // 背景必须在「与位移同一层」或位移在内层，否则会整块铺宽不随 offset 移动，盖住下层红色删除条（点击能删但看不见）
                .graphicsLayer { translationX = offsetPx }
                .background(MaterialTheme.colorScheme.surface)
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
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // "无颜色"选项
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = CircleShape
                            )
                            .border(
                                width = 1.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant,
                                shape = CircleShape
                            )
                            .clickable { onHighlight(Color.Transparent) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "无颜色",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    Text("无", style = MaterialTheme.typography.labelSmall)
                }

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
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(
                                    width = 2.dp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                                    shape = CircleShape
                                )
                                .clickable { onHighlight(color) }
                        )
                        Spacer(modifier = Modifier.height(3.dp))
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

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true, name = "阅读器顶栏与底栏")
@Composable
private fun ReaderChromePreview() {
    MarkdownReaderTheme {
        val theme = ReadingTheme.Paper
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(theme.backgroundColor)
        ) {
            ReaderTopAppBar(
                title = "示例 Markdown 阅读",
                chapterTitle = "第一章 预览章节标题",
                onNavigateBack = { },
                theme = theme,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
            )
            Text(
                text = "（正文为 AndroidView TextView，预览中仅占位）",
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = theme.textColor.copy(alpha = 0.65f)
            )
            ReaderImmersiveBottomBar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
                theme = theme,
                chromeBackground = readingChromeShade(theme.backgroundColor),
                onToc = { },
                onBookmarks = { },
                onThemeBackground = { },
                onFont = { },
                onPageTurn = { }
            )
        }
    }
}
