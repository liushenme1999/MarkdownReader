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
import com.example.markdownreader.R
import com.example.markdownreader.data.local.entity.HighlightEntity
import com.example.markdownreader.importing.PdfReaderContent
import com.example.markdownreader.markdown.DiagramSchemeHandler
import io.noties.markwon.image.AsyncDrawable
import io.noties.markwon.image.AsyncDrawableSpan
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

/** 进入阅读 / 跳转时目标位置之前的预读缓冲，便于跳章后仍能上滑回看上一章。 */
internal const val READER_INITIAL_LOOKBEHIND_CHARS = 16 * 1024

/** 单窗口最大字符数，防止单章过长再次卡顿。 */
internal const val READER_MAX_WINDOW_CHARS = 96 * 1024

/** 当窗口内 local 进度超过该比例且仍有未渲染章节，触发向下扩窗。 */
internal const val READER_EXPAND_TRIGGER_LOCAL_PROGRESS = 0.78f

/** 当窗口内滚动接近顶部且 start > 0 时，触发向上扩窗。 */
internal const val READER_EXPAND_TRIGGER_NEAR_START_PROGRESS = 0.22f

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

/** 把窗口左沿至少回退 [atLeastChars]，并对齐到章节边界。 */
internal fun expandWindowStartBackward(
    boundaries: IntArray,
    currentStart: Int,
    atLeastChars: Int,
    hardMin: Int = 0,
): Int {
    if (currentStart <= hardMin) return hardMin
    if (boundaries.isEmpty()) {
        return (currentStart - atLeastChars).coerceAtLeast(hardMin)
    }
    val target = (currentStart - atLeastChars).coerceAtLeast(hardMin)
    val idx = boundaries.indexOfLast { it <= target }.coerceAtLeast(0)
    return boundaries[idx].coerceAtLeast(hardMin)
}

/** 在当前 windowStart 基础上至少回退 [READER_EXPAND_CHUNK_CHARS] 并对齐到上一章节边界。 */
internal fun previousWindowStart(
    boundaries: IntArray,
    currentStart: Int,
    hardMin: Int = 0,
): Int {
    if (currentStart <= hardMin) return hardMin
    return expandWindowStartBackward(
        boundaries,
        currentStart,
        READER_EXPAND_CHUNK_CHARS,
        hardMin
    )
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
    lookbehindChars: Int = READER_INITIAL_LOOKBEHIND_CHARS,
): Pair<Int, Int> {
    if (contentLen <= 0) return 0 to 0
    val safe = charPos.coerceIn(0, contentLen - 1)
    var start = chapterStartBefore(boundaries, safe)
    if (lookbehindChars > 0 && start > 0) {
        start = expandWindowStartBackward(boundaries, start, lookbehindChars, 0)
    }
    var end = expandWindowEndToCover(boundaries, safe + lookaheadChars, contentLen)
    if (end <= start) end = (start + 1).coerceAtMost(contentLen)
    if (end - start > READER_MAX_WINDOW_CHARS) {
        val chapterStart = chapterStartBefore(boundaries, safe)
        val minStart = if (lookbehindChars > 0) {
            expandWindowStartBackward(boundaries, chapterStart, lookbehindChars, 0)
        } else {
            chapterStart
        }
        start = (safe - READER_MAX_WINDOW_CHARS / 5).coerceAtLeast(minStart)
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
/** 在窗口片段中定位章节标题行（相对 [displayed] 的下标）；优先在 [hintOffset] 附近匹配。 */
internal fun findPlainTextChapterOffsetInDisplayed(
    displayed: String,
    title: String,
    hintOffset: Int,
): Int? {
    val needle = title.trim()
    if (needle.length < 2) return null
    val searchFrom = (hintOffset - 2048).coerceAtLeast(0)
    val searchTo = (hintOffset + 2048).coerceAtMost(displayed.length)
    var idx = displayed.indexOf(needle, searchFrom)
    while (idx >= 0 && idx < searchTo) {
        if (isPlainTextLineTitleAt(displayed, idx, needle)) return idx
        idx = displayed.indexOf(needle, idx + 1)
    }
    idx = displayed.indexOf(needle)
    while (idx >= 0) {
        if (isPlainTextLineTitleAt(displayed, idx, needle)) return idx
        idx = displayed.indexOf(needle, idx + 1)
    }
    return null
}

internal fun isPlainTextLineTitleAt(displayed: String, index: Int, title: String): Boolean {
    if (index < 0 || index >= displayed.length) return false
    if (!displayed.regionMatches(index, title, 0, title.length, ignoreCase = false)) return false
    val lineStart = displayed.lastIndexOf('\n', index - 1).let { if (it < 0) 0 else it + 1 }
    val between = displayed.substring(lineStart, index)
    if (between.isNotEmpty() && !between.all { it.isWhitespace() }) return false
    val after = index + title.length
    if (after >= displayed.length) return true
    return displayed[after] == '\n' || displayed[after].isWhitespace()
}

/**
 * PDF 正文为 HTML `<img>`，渲染后每页对应一个 [AsyncDrawableSpan] 占位符；
 * 源码字符下标与 TextView 内下标不一致，需按页码定位图片 span。
 */
internal fun resolvePdfDisplayedCharOffset(
    displayedText: CharSequence?,
    tocEntries: List<MarkdownTocEntry>,
    targetPageIndex: Int,
    windowStart: Int,
): Int {
    val spanned = displayedText as? Spanned ?: return 0
    val imageStarts = spanned.getSpans(0, spanned.length, AsyncDrawableSpan::class.java)
        .map { spanned.getSpanStart(it) }
        .sorted()
    if (imageStarts.isEmpty()) return 0

    val targetSourceOffset = tocEntries.getOrNull(targetPageIndex)?.sourceOffset
        ?: return imageStarts.first()
    val rankInWindow = tocEntries.count {
        it.sourceOffset >= windowStart && it.sourceOffset < targetSourceOffset
    }
    return imageStarts.getOrNull(rankInWindow.coerceIn(0, imageStarts.lastIndex))
        ?: imageStarts.last()
}

internal fun resolvePdfSourceOffsetAtTextViewTop(
    textView: TextView?,
    tocEntries: List<MarkdownTocEntry>,
    windowStart: Int,
): Int? {
    val spanned = textView?.text as? Spanned ?: return null
    val imageStarts = spanned.getSpans(0, spanned.length, AsyncDrawableSpan::class.java)
        .map { spanned.getSpanStart(it) }
        .sorted()
    if (imageStarts.isEmpty()) return null
    val renderedTop = charOffsetAtScrollTop(textView)
    val rankInWindow = imageStarts.indexOfLast { it <= renderedTop }.coerceAtLeast(0)
    val entriesInWindow = tocEntries.filter { it.sourceOffset >= windowStart }
    return entriesInWindow.getOrNull(rankInWindow)?.sourceOffset
        ?: entriesInWindow.lastOrNull()?.sourceOffset
}

internal fun findBookmarkPreviewOffset(
    displayedText: CharSequence?,
    previewText: String?,
): Int? {
    val displayed = displayedText?.toString().orEmpty()
    val preview = previewText
        ?.replace(Regex("""\s+"""), " ")
        ?.trim()
        .orEmpty()
    if (displayed.isEmpty() || preview.length < 2) return null

    displayed.indexOf(preview).takeIf { it >= 0 }?.let { return lineStartForOffset(displayed, it) }

    val (normalizedDisplayed, indexMap) = normalizeForBookmarkSearch(displayed)
    val candidates = buildList {
        add(preview)
        if (preview.length > 80) add(preview.take(80))
        if (preview.length > 48) add(preview.take(48))
        if (preview.length > 28) add(preview.take(28))
    }.distinct()
    for (candidate in candidates) {
        val normalizedCandidate = candidate.replace(Regex("""\s+"""), " ").trim()
        val idx = normalizedDisplayed.indexOf(normalizedCandidate)
        if (idx >= 0) {
            val original = indexMap.getOrNull(idx) ?: return null
            return lineStartForOffset(displayed, original)
        }
    }
    return null
}

private fun normalizeForBookmarkSearch(text: String): Pair<String, List<Int>> {
    val out = StringBuilder(text.length)
    val indexMap = ArrayList<Int>(text.length)
    var previousWhitespace = true
    text.forEachIndexed { index, ch ->
        if (ch.isWhitespace()) {
            if (!previousWhitespace) {
                out.append(' ')
                indexMap.add(index)
                previousWhitespace = true
            }
        } else {
            out.append(ch)
            indexMap.add(index)
            previousWhitespace = false
        }
    }
    if (out.isNotEmpty() && out.last() == ' ') {
        out.deleteAt(out.lastIndex)
        indexMap.removeAt(indexMap.lastIndex)
    }
    return out.toString() to indexMap
}

private fun lineStartForOffset(text: String, offset: Int): Int {
    if (text.isEmpty()) return 0
    val safe = offset.coerceIn(0, text.lastIndex)
    return text.lastIndexOf('\n', safe - 1).let { if (it < 0) 0 else it + 1 }
}

internal fun resolveDisplayedCharOffset(
    sourceContent: String,
    sourceOffset: Int,
    displayedText: CharSequence?,
    renderPlainText: Boolean,
    windowStart: Int,
    tocEntries: List<MarkdownTocEntry>,
    preferredEntry: MarkdownTocEntry? = null,
    preferredText: String? = null,
    windowEnd: Int? = null,
): Int {
    val displayed = displayedText?.toString().orEmpty()
    val len = displayed.length
    if (len == 0) return 0
    findBookmarkPreviewOffset(displayedText, preferredText)?.let {
        return it.coerceIn(0, (len - 1).coerceAtLeast(0))
    }
    if (renderPlainText) {
        val hint = (sourceOffset - windowStart).coerceIn(0, len)
        val entry = preferredEntry
            ?: tocEntries.getOrNull(tocIndexForSourceOffset(tocEntries, sourceOffset))
        if (entry != null) {
            findPlainTextChapterOffsetInDisplayed(displayed, entry.title, hint)?.let { return it }
        }
        return hint.coerceIn(0, (len - 1).coerceAtLeast(0))
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

    return proportionalDisplayedOffset(
        sourceOffset = sourceOffset,
        windowStart = windowStart,
        windowEnd = windowEnd ?: sourceContent.length,
        displayedLen = len,
    )
}

/** Markdown 渲染后长度远小于源码窗口，线性比例比 `(sourceOffset - windowStart)` 更单调。 */
internal fun proportionalDisplayedOffset(
    sourceOffset: Int,
    windowStart: Int,
    windowEnd: Int,
    displayedLen: Int,
): Int {
    if (displayedLen <= 1) return 0
    val sourceSpan = (windowEnd - windowStart).coerceAtLeast(1)
    val ratio = ((sourceOffset - windowStart).toFloat() / sourceSpan).coerceIn(0f, 1f)
    return (ratio * (displayedLen - 1)).toInt().coerceIn(0, displayedLen - 1)
}

internal fun proportionalSourceOffset(
    windowStart: Int,
    windowEnd: Int,
    displayedLen: Int,
    renderedOffset: Int,
): Int {
    if (displayedLen <= 1) return windowStart.coerceAtLeast(0)
    val sourceSpan = (windowEnd - windowStart).coerceAtLeast(1)
    val ratio = (renderedOffset.toFloat() / (displayedLen - 1).coerceAtLeast(1)).coerceIn(0f, 1f)
    return (windowStart + ratio * sourceSpan).toInt()
        .coerceIn(windowStart, (windowEnd - 1).coerceAtLeast(windowStart))
}

/** 视口顶部字符是否落在 diagram:// 占位 span 上。 */
internal fun isDiagramSpanAtOffset(text: CharSequence?, offset: Int): Boolean {
    if (text !is Spanned) return false
    val len = text.length
    if (len == 0) return false
    val check = offset.coerceIn(0, len - 1)
    val spans = text.getSpans(check, check + 1, AsyncDrawableSpan::class.java) ?: return false
    return spans.any { span ->
        span.drawable.destination.startsWith("${DiagramSchemeHandler.SCHEME}://")
    }
}

internal fun isViewportTopOnDiagramSpan(tv: TextView): Boolean =
    isDiagramSpanAtOffset(tv.text, charOffsetAtScrollTop(tv))

/**
 * 阅读进度保存/恢复专用：在窗口内用单调比例映射，避免标题/锚点启发式导致前后偏移。
 */
internal fun resolveDisplayedCharOffsetForProgressRestore(
    sourceOffset: Int,
    windowStart: Int,
    windowEnd: Int,
    displayedLen: Int,
    renderPlainText: Boolean,
): Int {
    if (displayedLen <= 0) return 0
    if (renderPlainText) {
        return (sourceOffset - windowStart).coerceIn(0, displayedLen - 1)
    }
    var lo = 0
    var hi = displayedLen - 1
    var best = 0
    while (lo <= hi) {
        val mid = lo + (hi - lo) / 2
        val src = proportionalSourceOffset(windowStart, windowEnd, displayedLen, mid)
        if (src <= sourceOffset) {
            best = mid
            lo = mid + 1
        } else {
            hi = mid - 1
        }
    }
    return best
}

/**
 * 将 TextView 内已渲染文本的字符下标反查为全书 Markdown 源码下标（与进度恢复映射互逆）。
 */
internal fun resolveSourceCharOffset(
    sourceContent: String,
    windowStart: Int,
    windowEnd: Int,
    displayedText: CharSequence?,
    renderedOffset: Int,
    renderPlainText: Boolean,
    tocEntries: List<MarkdownTocEntry>,
): Int {
    val displayed = displayedText?.toString().orEmpty()
    if (displayed.isEmpty()) return windowStart.coerceIn(0, sourceContent.length)
    val rend = renderedOffset.coerceIn(0, (displayed.length - 1).coerceAtLeast(0))
    if (renderPlainText) {
        return (windowStart + rend).coerceIn(windowStart, (windowEnd - 1).coerceAtLeast(windowStart))
    }
    if (displayedText is Spanned && isDiagramSpanAtOffset(displayedText, rend)) {
        return proportionalSourceOffset(windowStart, windowEnd, displayed.length, rend)
    }
    var lo = windowStart
    var hi = (windowEnd - 1).coerceAtLeast(windowStart)
    var best = lo
    while (lo <= hi) {
        val mid = lo + (hi - lo) / 2
        val disp = proportionalDisplayedOffset(
            sourceOffset = mid,
            windowStart = windowStart,
            windowEnd = windowEnd,
            displayedLen = displayed.length,
        )
        if (disp <= rend) {
            best = mid
            lo = mid + 1
        } else {
            hi = mid - 1
        }
    }
    return best.coerceIn(0, sourceContent.length)
}

/** 按窗口内滚动比例快速估算源码坐标（用于滚动时保存进度，避免每帧二分查找卡顿）。 */
internal fun globalCharEstimateFromScrollFraction(
    windowStart: Int,
    windowEnd: Int,
    localProgress: Float,
    contentLength: Int,
): Int {
    val span = (windowEnd - windowStart).coerceAtLeast(1)
    return (windowStart + localProgress * span)
        .toInt()
        .coerceIn(0, contentLength.coerceAtLeast(0))
}

/** 视口顶部在全书源码中的字符下标（用于保存进度 / 向上扩窗锚点）。 */
internal fun globalSourceCharAtTextViewTop(
    sourceContent: String,
    windowStart: Int,
    windowEnd: Int,
    textView: TextView?,
    renderPlainText: Boolean,
    tocEntries: List<MarkdownTocEntry>,
): Int {
    if (textView == null || sourceContent.isEmpty()) {
        return windowStart.coerceIn(0, sourceContent.length)
    }
    val renderedTop = charOffsetAtScrollTop(textView)
    return resolveSourceCharOffset(
        sourceContent = sourceContent,
        windowStart = windowStart,
        windowEnd = windowEnd,
        displayedText = textView.text,
        renderedOffset = renderedTop,
        renderPlainText = renderPlainText,
        tocEntries = tocEntries,
    ).coerceIn(0, sourceContent.length)
}

/** 打开书籍时优先用 [currentPosition]（源码坐标），否则由 [readingProgress] 推算。 */
internal fun resolveStoredCharPos(
    currentPosition: Int,
    readingProgress: Float,
    contentLength: Int,
    totalChars: Int,
): Int {
    if (contentLength <= 0) return 0
    val total = totalChars.coerceAtLeast(1)
    return when {
        currentPosition > 0 ->
            resolveGlobalCharPos(currentPosition, contentLength, total)
        readingProgress > 0f ->
            resolveGlobalCharPos((readingProgress * total).toInt(), contentLength, total)
        else -> 0
    }
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
internal fun isReaderTextViewLayoutReady(
    tv: TextView,
    expectedTextLength: Int? = null,
): Boolean {
    val layout = tv.layout ?: return false
    val len = tv.text?.length ?: 0
    if (len <= 0 || layout.text.length != len) return false
    if (expectedTextLength != null && len != expectedTextLength) return false
    return true
}

/** 等待 Markwon 异步渲染完成且 layout 就绪（避免在空白/旧文本上恢复滚动）。 */
internal suspend fun awaitReaderMarkdownRenderReady(
    tvProvider: () -> TextView?,
    expectedRenderSig: String,
    maxAttempts: Int = 200,
): TextView? {
    repeat(maxAttempts) {
        val tv = tvProvider()
        if (tv != null &&
            tv.getTag(R.id.reader_markdown_render_complete) == expectedRenderSig &&
            isReaderTextViewLayoutReady(tv)
        ) {
            kotlinx.coroutines.delay(48)
            if (tv.getTag(R.id.reader_markdown_render_complete) == expectedRenderSig &&
                isReaderTextViewLayoutReady(tv)
            ) {
                return tv
            }
        }
        kotlinx.coroutines.delay(32)
    }
    return tvProvider()?.takeIf {
        it.getTag(R.id.reader_markdown_render_complete) == expectedRenderSig &&
            isReaderTextViewLayoutReady(it)
    }
}

internal suspend fun awaitReaderTextViewLayout(
    tvProvider: () -> TextView?,
    maxAttempts: Int = 100,
    expectedTextLength: Int? = null,
): TextView? {
    repeat(maxAttempts) {
        val tv = tvProvider()
        if (tv != null && isReaderTextViewLayoutReady(tv, expectedTextLength)) {
            return tv
        }
        kotlinx.coroutines.delay(32)
    }
    return tvProvider()?.takeIf { isReaderTextViewLayoutReady(it, expectedTextLength) }
}

/** 横向分页：等待指定页 [TextView] 完成 layout（PDF 勿传源码长度，渲染后长度与 HTML 不一致）。 */
internal suspend fun awaitPagerPageTextView(
    pageTextViews: Map<Int, TextView>,
    page: Int,
    maxAttempts: Int = 150,
    expectedTextLength: Int? = null,
): TextView? {
    repeat(maxAttempts) {
        val tv = pageTextViews[page]
        if (tv != null && isReaderTextViewLayoutReady(tv, expectedTextLength)) {
            return tv
        }
        kotlinx.coroutines.delay(32)
    }
    return pageTextViews[page]?.takeIf { isReaderTextViewLayoutReady(it, expectedTextLength) }
}

/** 当前视口顶部对应的正文字符下标（相对 TextView 内文本）。 */
internal fun charOffsetAtScrollTop(tv: TextView): Int {
    val layout = tv.layout ?: return 0
    val len = tv.text?.length ?: 0
    if (len == 0) return 0
    val y = (tv.scrollY + tv.paddingTop).coerceAtLeast(0)
    val line = layout.getLineForVertical(y).coerceIn(0, (layout.lineCount - 1).coerceAtLeast(0))
    return layout.getLineStart(line).coerceIn(0, (len - 1).coerceAtLeast(0))
}

/** 按视口顶部的字符下标估算窗口内阅读进度（0..1），不受 Mermaid/大图行高影响。 */
internal fun localCharProgressAtScrollTop(tv: TextView): Float {
    val len = tv.text?.length ?: 0
    if (len <= 1) return 0f
    return charOffsetAtScrollTop(tv).toFloat() / (len - 1).toFloat()
}

/** 单行高度超过视口，常见于 diagram / 大图 AsyncDrawableSpan。 */
internal fun isTallLineAtOffset(tv: TextView, charOffset: Int): Boolean {
    val layout = tv.layout ?: return false
    val innerH = tv.height - tv.paddingTop - tv.paddingBottom
    if (innerH <= 0) return false
    val len = tv.text?.length ?: 0
    if (len == 0) return false
    val line = layout.getLineForOffset(charOffset.coerceIn(0, len - 1))
    return layout.getLineBottom(line) - layout.getLineTop(line) > innerH
}

/**
 * 是否应向下扩窗：以字符进度为主；若视口落在大图行内，还需滚到该行底部，
 * 避免在 Mermaid 上滑动时因 scrollY 虚高而连续扩窗到全书末尾。
 */
internal fun shouldTriggerReaderExpandDown(
    tv: TextView,
    threshold: Float = READER_EXPAND_TRIGGER_LOCAL_PROGRESS,
): Boolean {
    if (isViewportTopOnDiagramSpan(tv)) return false
    val charProgress = localCharProgressAtScrollTop(tv)
    if (charProgress < threshold) return false
    val charAtTop = charOffsetAtScrollTop(tv)
    if (!isTallLineAtOffset(tv, charAtTop)) return true
    val layout = tv.layout ?: return false
    val innerH = tv.height - tv.paddingTop - tv.paddingBottom
    if (innerH <= 0) return false
    val line = layout.getLineForOffset(charAtTop)
    val viewportBottom = tv.scrollY + tv.paddingTop + innerH
    return viewportBottom >= layout.getLineBottom(line) - 8
}

/** 是否应向上扩窗：字符进度接近顶部；大图行内需滚到该行顶部。 */
internal fun shouldTriggerReaderExpandUp(
    tv: TextView,
    threshold: Float = READER_EXPAND_TRIGGER_NEAR_START_PROGRESS,
): Boolean {
    if (isViewportTopOnDiagramSpan(tv)) return false
    val charProgress = localCharProgressAtScrollTop(tv)
    if (charProgress > threshold) return false
    val charAtTop = charOffsetAtScrollTop(tv)
    if (!isTallLineAtOffset(tv, charAtTop)) return true
    val layout = tv.layout ?: return false
    val line = layout.getLineForOffset(charAtTop)
    val viewportTop = tv.scrollY + tv.paddingTop
    return viewportTop <= layout.getLineTop(line) + 8
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

/** 记录当前 scrollY、视口顶行 lineTop 及窗口起点，供 layout 重排后恢复子像素位置。 */
internal data class TextViewScrollAnchor(
    val scrollY: Int,
    val lineTop: Int,
    val windowStart: Int,
)

internal fun captureTextViewScrollAnchor(tv: TextView, windowStart: Int): TextViewScrollAnchor {
    val layout = tv.layout
    if (layout == null || layout.lineCount <= 0) {
        return TextViewScrollAnchor(tv.scrollY, tv.scrollY, windowStart)
    }
    val scrollY = tv.scrollY
    val y = (scrollY + tv.paddingTop).coerceAtLeast(0)
    val line = layout.getLineForVertical(y).coerceIn(0, layout.lineCount - 1)
    return TextViewScrollAnchor(scrollY, layout.getLineTop(line), windowStart)
}

internal fun scrollTextViewPreservingScrollY(tv: TextView, savedScrollY: Int) {
    val layout = tv.layout ?: return
    val innerH = tv.height - tv.paddingTop - tv.paddingBottom
    if (innerH <= 0) return
    val maxScroll = (layout.height - innerH).coerceAtLeast(0)
    tv.scrollTo(0, savedScrollY.coerceIn(0, maxScroll))
}

/**
 * 扩窗后恢复滚动：向下扩窗时 layout 前缀不变，直接保留 scrollY；
 * 向上扩窗时在原 scrollY 上叠加 prepend 段高度。
 */
internal fun restoreTextViewScrollAfterWindowChange(
    tv: TextView,
    anchor: TextViewScrollAnchor,
    newWindowStart: Int,
    newWindowEnd: Int,
    renderPlainText: Boolean,
) {
    if (newWindowStart >= anchor.windowStart) {
        scrollTextViewPreservingScrollY(tv, anchor.scrollY)
        return
    }
    val layout = tv.layout ?: run {
        scrollTextViewPreservingScrollY(tv, anchor.scrollY)
        return
    }
    val len = tv.text?.length ?: 0
    if (len <= 0) {
        scrollTextViewPreservingScrollY(tv, anchor.scrollY)
        return
    }
    val boundaryOffset = if (renderPlainText) {
        (anchor.windowStart - newWindowStart).coerceIn(0, len - 1)
    } else {
        resolveDisplayedCharOffsetForProgressRestore(
            sourceOffset = anchor.windowStart,
            windowStart = newWindowStart,
            windowEnd = newWindowEnd,
            displayedLen = len,
            renderPlainText = false,
        ).coerceIn(0, len - 1)
    }
    val prependedHeight = layout.getLineTop(
        layout.getLineForOffset(boundaryOffset).coerceIn(0, layout.lineCount - 1),
    )
    scrollTextViewPreservingScrollY(tv, anchor.scrollY + prependedHeight)
}

/** 按源码行内比例滚动，避免标题/正文在行顶 snap（用于进度恢复，非目录/书签跳转）。 */
internal fun scrollTextViewToSourceProgressAnchor(
    tv: TextView,
    sourceContent: String,
    sourceOffset: Int,
    windowStart: Int,
    windowEnd: Int,
    renderPlainText: Boolean,
) {
    val layout = tv.layout ?: return
    val len = tv.text?.length ?: 0
    if (len <= 0 || sourceContent.isEmpty()) return
    val safeSource = sourceOffset.coerceIn(0, sourceContent.length - 1)
    val displayedOffset = resolveDisplayedCharOffsetForProgressRestore(
        sourceOffset = safeSource,
        windowStart = windowStart,
        windowEnd = windowEnd,
        displayedLen = len,
        renderPlainText = renderPlainText,
    ).coerceIn(0, len - 1)
    val innerH = tv.height - tv.paddingTop - tv.paddingBottom
    if (innerH <= 0) return
    val maxScroll = (layout.height - innerH).coerceAtLeast(0)
    val line = layout.getLineForOffset(displayedOffset).coerceIn(0, layout.lineCount - 1)
    val lineTop = layout.getLineTop(line)
    val lineBottom = layout.getLineBottom(line)
    val lineStartInSource = sourceContent.lastIndexOf('\n', safeSource - 1).let { if (it < 0) 0 else it + 1 }
    val lineEndInSource = sourceContent.indexOf('\n', safeSource).let {
        if (it < 0) sourceContent.length else it
    }
    val lineLenInSource = (lineEndInSource - lineStartInSource).coerceAtLeast(1)
    val inLineRatio = ((safeSource - lineStartInSource).toFloat() / lineLenInSource).coerceIn(0f, 1f)
    val lineHeight = (lineBottom - lineTop).coerceAtLeast(1)
    val targetScrollY = lineTop + (lineHeight * inLineRatio).toInt()
    tv.scrollTo(0, targetScrollY.coerceIn(0, maxScroll))
}

/**
 * 章节窗口模式的目录/书签跳转：切换窗口片段后由 [onAnchorGlobalChar] 触发锚点恢复滚动
 *（避免大 TXT 异步 PrecomputedText 尚未写入时提前 scroll 到错误位置）。
 */
internal fun jumpToCharInChunkWindow(
    contentLen: Int,
    chapterBoundaries: IntArray,
    charPos: Int,
    setReadingWindow: (start: Int, end: Int) -> Unit,
    onAnchorGlobalChar: (Int) -> Unit,
    onProgress: () -> Unit,
) {
    val safeCharPos = charPos.coerceIn(0, (contentLen - 1).coerceAtLeast(0))
    val (winStart, winEnd) = computeReadingWindow(chapterBoundaries, safeCharPos, contentLen)
    setReadingWindow(winStart, winEnd)
    onAnchorGlobalChar(safeCharPos)
    onProgress()
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
    bookmarkPreviewText: String? = null,
    pageSpecs: List<Pair<String, Int>>,
    pagerState: PagerState,
    pageTextViews: MutableMap<Int, TextView>,
    assignActiveTextView: (TextView) -> Unit,
    onProgress: () -> Unit,
    pdfJumpByPageIndex: Boolean = false,
    pdfPageIndex: Int? = null,
) {
    if (pageSpecs.isEmpty()) {
        onProgress()
        return
    }
    val page = if (pdfJumpByPageIndex && pdfPageIndex != null) {
        pdfPageIndex.coerceIn(0, pageSpecs.lastIndex)
    } else {
        pageIndexForGlobalChar(pageSpecs, charPos.coerceIn(0, (contentLen - 1).coerceAtLeast(0)))
            .coerceIn(0, pageSpecs.lastIndex)
    }
    val globalStart = pageSpecs[page].second
    val safeCharPos = charPos.coerceIn(0, (contentLen - 1).coerceAtLeast(0))

    // PDF 经 Markwon 渲染后 TextView 长度与 HTML 片段长度无关，不能按源码长度校验。
    val expectedLen = if (pdfJumpByPageIndex) null else pageSpecs[page].first.length
    scope.launch {
        pagerState.scrollToPage(page)
        val tv = awaitPagerPageTextView(
            pageTextViews = pageTextViews,
            page = page,
            expectedTextLength = expectedLen,
        )
        if (tv != null) {
            assignActiveTextView(tv)
            tv.post {
                if (pdfJumpByPageIndex) {
                    tv.scrollTo(0, 0)
                } else {
                    val displayedOffset = resolveDisplayedCharOffset(
                        sourceContent = sourceContent,
                        sourceOffset = safeCharPos,
                        displayedText = tv.text,
                        renderPlainText = renderPlainText,
                        windowStart = globalStart,
                        tocEntries = tocEntries,
                        preferredEntry = preferredTocEntry,
                        preferredText = bookmarkPreviewText,
                    )
                    scrollTextViewToCharOffset(tv, displayedOffset)
                }
                onProgress()
            }
        } else {
            onProgress()
        }
    }
}

/** PDF 目录/书签：垂直滚动模式下按页码滚到对应图片 span。 */
internal fun jumpToPdfPageVertically(
    contentLen: Int,
    chapterBoundaries: IntArray,
    pageIndex: Int,
    tocEntries: List<MarkdownTocEntry>,
    setReadingWindow: (start: Int, end: Int) -> Unit,
    onPendingPdfPageIndex: (Int) -> Unit,
    onProgress: () -> Unit,
) {
    val charPos = tocEntries.getOrNull(pageIndex)?.sourceOffset
        ?: 0
    val safeCharPos = charPos.coerceIn(0, (contentLen - 1).coerceAtLeast(0))
    val (winStart, winEnd) = computeReadingWindow(chapterBoundaries, safeCharPos, contentLen)
    setReadingWindow(winStart, winEnd)
    onPendingPdfPageIndex(pageIndex)
    onProgress()
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

/** 横向 PDF：目录项下标与 [PdfReaderContent.splitToPages] 页序一致；书签等回退按源码偏移推算。 */
internal fun pdfPageIndexForTocOrBookmark(
    isPdfBook: Boolean,
    tocEntries: List<MarkdownTocEntry>,
    readerContent: String,
    charPos: Int,
    tocEntry: MarkdownTocEntry?,
): Int? {
    if (!isPdfBook) return null
    if (tocEntry != null) {
        val idx = tocEntries.indexOfFirst { it.sourceOffset == tocEntry.sourceOffset }
        if (idx >= 0) return idx
    }
    return PdfReaderContent.pageIndexForSourceOffset(readerContent, charPos)
}
