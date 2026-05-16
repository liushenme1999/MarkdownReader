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

internal fun estimateTargetCharsPerPage(fontSize: Int, screenHeightDp: Int, screenWidthDp: Int): Int {
    val lineHeight = fontSize * 1.55f
    val lines = (screenHeightDp / lineHeight).toInt().coerceAtLeast(4)
    val charsPerLine = (screenWidthDp / (fontSize * 0.48f)).toInt().coerceAtLeast(8)
    return (lines * charsPerLine).coerceIn(900, 14_000)
}

/** 将正文切成多段用于横向分页；返回 (片段, 在全文中的起始下标)。 */
internal fun splitMarkdownToPages(content: String, targetChars: Int): List<Pair<String, Int>> {
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
internal const val READER_EXPAND_CHUNK_CHARS = 32 * 1024

/** 进入阅读 / 跳转时目标位置之后的预读缓冲。 */
internal const val READER_INITIAL_LOOKAHEAD_CHARS = 16 * 1024

/** 单窗口最大字符数，防止单章过长再次卡顿。 */
internal const val READER_MAX_WINDOW_CHARS = 96 * 1024

/** 当窗口内 local 进度超过该比例且仍有未渲染章节，触发扩窗。 */
internal const val READER_EXPAND_TRIGGER_LOCAL_PROGRESS = 0.78f

/** 计算章节边界数组（升序、含 0 与 content.length）。 */
internal fun computeChapterBoundaries(content: String, toc: List<MarkdownTocEntry>): IntArray {
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
internal fun expandWindowEndToCover(boundaries: IntArray, atLeast: Int, hardCap: Int): Int {
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
internal fun nextWindowEnd(boundaries: IntArray, currentEnd: Int, hardCap: Int): Int {
    if (currentEnd >= hardCap) return hardCap
    val target = (currentEnd + READER_EXPAND_CHUNK_CHARS).coerceAtMost(hardCap)
    return expandWindowEndToCover(boundaries, target, hardCap)
}

internal fun chapterStartBefore(boundaries: IntArray, charPos: Int): Int {
    if (boundaries.isEmpty()) return 0
    val idx = boundaries.indexOfLast { it <= charPos }.coerceAtLeast(0)
    return boundaries[idx]
}

/** 围绕 [charPos] 计算阅读窗口 [start, end)，不再从全书开头截取。 */
internal fun computeReadingWindow(
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

internal fun extractSourceLineAt(source: String, sourceOffset: Int): String {
    if (source.isEmpty()) return ""
    val safe = sourceOffset.coerceIn(0, source.lastIndex.coerceAtLeast(0))
    val lineStart = source.lastIndexOf('\n', safe - 1).let { if (it < 0) 0 else it + 1 }
    val lineEnd = source.indexOf('\n', safe).let { if (it < 0) source.length else it }
    return source.substring(lineStart, lineEnd).trim().take(200)
}

internal fun anchorSearchCandidates(line: String, tocTitle: String? = null): List<String> {
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

internal fun normalizeAnchorKey(text: String): String =
    text.replace(Regex("""[*_`~>#\[\]()!]"""), "")
        .replace(Regex("""\s+"""), " ")
        .trim()
        .lowercase()

internal fun titleMatchesRenderedLine(renderedLine: String, tocTitle: String): Boolean {
    val a = normalizeAnchorKey(renderedLine)
    val b = normalizeAnchorKey(tocTitle)
    if (a.isEmpty() || b.isEmpty()) return false
    return a == b || a.contains(b) || b.contains(a)
}

internal fun lineAt(text: String, start: Int): String {
    if (start < 0 || start >= text.length) return ""
    val end = text.indexOf('\n', start).let { if (it < 0) text.length else it }
    return text.substring(start, end).trim()
}

/** Markwon 为 ATX 标题打的 span；顺序与正文中的标题出现顺序一致。 */
internal fun markdownHeadingSpanStarts(text: CharSequence): List<Int> {
    if (text !is Spanned) return emptyList()
    val spans = text.getSpans(0, text.length, HeadingSpan::class.java)
    if (spans.isEmpty()) return emptyList()
    return spans
        .map { text.getSpanStart(it) }
        .distinct()
        .sorted()
}

/** 当前窗口内、位于 [sourceOffset] 之前的目录标题个数（用于第 N 个标题定位）。 */
internal fun headingRankInWindow(
    tocEntries: List<MarkdownTocEntry>,
    sourceOffset: Int,
    windowStart: Int,
): Int = tocEntries.count { it.sourceOffset >= windowStart && it.sourceOffset < sourceOffset }

internal fun tocIndexForSourceOffset(
    tocEntries: List<MarkdownTocEntry>,
    sourceOffset: Int,
): Int {
    val exact = tocEntries.indexOfFirst { it.sourceOffset == sourceOffset }
    if (exact >= 0) return exact
    return tocEntries.indexOfLast { it.sourceOffset <= sourceOffset }.coerceAtLeast(0)
}

/** 在 [haystack] 中查找 [needle] 的第 [occurrence] 次出现（0-based），忽略大小写。 */
internal fun findOccurrenceIndex(haystack: String, needle: String, occurrence: Int): Int {
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
internal fun resolveDisplayedCharOffset(
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
internal fun resolveGlobalCharPos(
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

internal fun readingProgressForCharPos(charPos: Int, contentLength: Int): Float =
    (charPos.toFloat() / contentLength.coerceAtLeast(1)).coerceIn(0f, 1f)

/** layout 已基于当前文本测量完成。 */
internal fun isReaderTextViewLayoutReady(tv: TextView): Boolean {
    val layout = tv.layout ?: return false
    val len = tv.text?.length ?: 0
    return len > 0 && layout.text?.length == len
}

internal suspend fun awaitReaderTextViewLayout(
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
internal fun scrollTextViewToCharOffset(tv: TextView, charOffsetInText: Int) {
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
internal fun jumpToCharInChunkWindow(
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
internal fun jumpToGlobalCharInPager(
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

internal fun highlightsForPageSlice(
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

internal fun pageIndexForGlobalChar(pages: List<Pair<String, Int>>, charPos: Int): Int =
    pages.indexOfLast { it.second <= charPos }.coerceAtLeast(0)
