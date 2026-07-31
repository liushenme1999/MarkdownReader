package space.liushenme.markdownreader.ui.screens.reader

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
import space.liushenme.markdownreader.R
import space.liushenme.markdownreader.data.local.entity.HighlightEntity
import space.liushenme.markdownreader.importing.PdfReaderContent
import space.liushenme.markdownreader.markdown.DiagramSchemeHandler
import io.noties.markwon.image.AsyncDrawable
import io.noties.markwon.image.AsyncDrawableSpan
import space.liushenme.markdownreader.model.ReaderPageTurnMode
import space.liushenme.markdownreader.ui.components.iconTintForDeleteStrip
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

/**
 * 目录/书签跳转专用：窗口从目标章节边界起算，不向前回溯 [READER_INITIAL_LOOKBEHIND_CHARS]。
 * 避免跳转到十八节后仍渲染十七节 Mermaid（尤其 17.4 思维导图）导致滚动被撑高行 clamp 在 17.4。
 */
internal fun computeTocJumpReadingWindow(
    boundaries: IntArray,
    charPos: Int,
    contentLen: Int,
): Pair<Int, Int> = computeReadingWindow(
    boundaries = boundaries,
    charPos = charPos,
    contentLen = contentLen,
    lookaheadChars = READER_INITIAL_LOOKAHEAD_CHARS,
    lookbehindChars = 0,
)

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
    if (headingStarts.isNotEmpty() && entry != null) {
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
    if (headingStarts.isNotEmpty()) {
        if (rankInWindow in headingStarts.indices) {
            val atRank = headingStarts[rankInWindow]
            if (entry == null || titleMatchesRenderedLine(lineAt(displayed, atRank), entry.title)) {
                return atRank.coerceIn(0, (len - 1).coerceAtLeast(0))
            }
        }
    }

    val anchorLine = extractSourceLineAt(sourceContent, sourceOffset)
    val candidates = anchorSearchCandidates(anchorLine, entry?.title)
    for (candidate in candidates) {
        val idx = findOccurrenceIndex(displayed, candidate, rankByTitleInWindow)
        if (idx >= 0) return idx.coerceIn(0, (len - 1).coerceAtLeast(0))
    }

    if (entry != null) {
        val hint = proportionalDisplayedOffset(
            sourceOffset = sourceOffset,
            windowStart = windowStart,
            windowEnd = windowEnd ?: sourceContent.length,
            displayedLen = len,
        )
        findPlainTextChapterOffsetInDisplayed(displayed, entry.title, hint)?.let {
            return it.coerceIn(0, (len - 1).coerceAtLeast(0))
        }
    }

    return proportionalDisplayedOffset(
        sourceOffset = sourceOffset,
        windowStart = windowStart,
        windowEnd = windowEnd ?: sourceContent.length,
        displayedLen = len,
    )
}

/**
 * 打开书 / 书签跳转共用：按「源码位置 + 可选预览文本」定位到渲染文本偏移。
 * 与目录标题跳转（[resolveDisplayedCharOffset]）分离——中间位置不应吸附到章节标题。
 */
internal fun resolveDisplayedCharOffsetForSavedPosition(
    sourceContent: String,
    sourceOffset: Int,
    displayedText: CharSequence?,
    renderPlainText: Boolean,
    windowStart: Int,
    windowEnd: Int,
    tocEntries: List<MarkdownTocEntry>,
    preferredText: String? = null,
): Int {
    val displayed = displayedText?.toString().orEmpty()
    val len = displayed.length
    if (len == 0) return 0
    if (renderPlainText) {
        return (sourceOffset - windowStart).coerceIn(0, (len - 1).coerceAtLeast(0))
    }
    val hint = proportionalDisplayedOffset(
        sourceOffset = sourceOffset,
        windowStart = windowStart,
        windowEnd = windowEnd,
        displayedLen = len,
    )
    // 书签/进度预览：优先宽容匹配到视口顶字符（不强制行首）。
    if (!preferredText.isNullOrBlank()) {
        val fingerprint = preferredText.replace(Regex("""\s+"""), " ").trim().take(48)
        locateLooseSnippetNear(displayed, fingerprint, hint)?.let {
            return it.coerceIn(0, (len - 1).coerceAtLeast(0))
        }
        findBookmarkPreviewOffset(displayedText, preferredText)?.let {
            return it.coerceIn(0, (len - 1).coerceAtLeast(0))
        }
    }
    return resolveDisplayedCharOffsetForBookOpenRestore(
        sourceContent = sourceContent,
        sourceOffset = sourceOffset,
        displayedText = displayedText,
        renderPlainText = false,
        windowStart = windowStart,
        windowEnd = windowEnd,
        tocEntries = tocEntries,
    )
}

/**
 * 打开书/无预览书签的进度恢复：定位到「上次阅读字符所在行」本身。
 * 不能用 [resolveDisplayedCharOffset]——那是目录跳转的标题解析器，会把章节中间的位置
 * 吸附到章节标题；标题序号/文本匹配出偏差时甚至落到前后章节开头。
 * 这里用源码行文本在渲染文本中按比例提示附近宽容查找；恰好停在章节标题时仍走标题精确定位。
 */
internal fun resolveDisplayedCharOffsetForBookOpenRestore(
    sourceContent: String,
    sourceOffset: Int,
    displayedText: CharSequence?,
    renderPlainText: Boolean,
    windowStart: Int,
    windowEnd: Int,
    tocEntries: List<MarkdownTocEntry>,
): Int {
    val displayed = displayedText?.toString().orEmpty()
    val len = displayed.length
    if (len == 0) return 0
    if (renderPlainText) {
        return (sourceOffset - windowStart).coerceIn(0, (len - 1).coerceAtLeast(0))
    }
    tocEntries.find { it.sourceOffset == sourceOffset }?.let { exactEntry ->
        return resolveDisplayedCharOffset(
            sourceContent = sourceContent,
            sourceOffset = sourceOffset,
            displayedText = displayedText,
            renderPlainText = false,
            windowStart = windowStart,
            windowEnd = windowEnd,
            tocEntries = tocEntries,
            preferredEntry = exactEntry,
        )
    }
    val hint = proportionalDisplayedOffset(
        sourceOffset = sourceOffset,
        windowStart = windowStart,
        windowEnd = windowEnd,
        displayedLen = len,
    )
    // 从存储位置「向前」截取源码片段做宽容匹配：保存的是视口顶行的源码位置（长段落折行时
    // 位于段落中间），若取整行文本会命中段首、恢复到视口上方几行。
    val forwardSnippet = looseSnippetFrom(sourceContent, sourceOffset)
    locateLooseSnippetNear(displayed, forwardSnippet, hint)?.let {
        return it.coerceIn(0, (len - 1).coerceAtLeast(0))
    }
    // 兜底：整行文本匹配（存档位置指向行首但行文本含密集标记时向前片段可能失配）。
    val anchorLine = extractSourceLineAt(sourceContent, sourceOffset)
    for (candidate in anchorSearchCandidates(anchorLine)) {
        val fingerprint = candidate.replace(Regex("""\s+"""), " ").trim().take(48)
        val idx = locateExpandFingerprintNear(displayed, fingerprint, hint)
        if (idx != null) {
            return idx.coerceIn(0, (len - 1).coerceAtLeast(0))
        }
    }
    return hint.coerceIn(0, (len - 1).coerceAtLeast(0))
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

/** Markdown 行内标记/结构字符：宽容匹配时既作词元分隔符，也作词元间允许的填充。 */
private val LOOSE_SNIPPET_SEPARATOR = Regex("""[\s*_`~>#\[\]()!|:.\-+=\uFFFC]+""")
private const val LOOSE_SNIPPET_SEPARATOR_CLASS = """[\s*_`~>#\[\]()!|:.\-+=\uFFFC]*"""

/**
 * 在 [haystack] 中宽容查找 [snippet]，返回离 [expectedIndex] 最近的匹配起点。
 * 与 [locateExpandFingerprintNear] 的区别：把 Markdown 标记字符也视为分隔/填充，
 * 因此「渲染文本片段」能在源码中命中（`**`、链接括号等被吸收），反之亦然。
 * 用于阅读进度的存/取两端做源码⇆渲染坐标的精确互查。
 */
internal fun locateLooseSnippetNear(
    haystack: String,
    snippet: String?,
    expectedIndex: Int,
): Int? {
    if (snippet.isNullOrBlank() || haystack.isEmpty()) return null
    val tokens = snippet.split(LOOSE_SNIPPET_SEPARATOR).filter { it.isNotEmpty() }
    if (tokens.isEmpty() || tokens.sumOf { it.length } < 8) return null
    val regex = runCatching {
        Regex(tokens.joinToString(separator = LOOSE_SNIPPET_SEPARATOR_CLASS) { Regex.escape(it) })
    }.getOrNull() ?: return null
    var best: Int? = null
    var bestDist = Int.MAX_VALUE
    var from = 0
    while (from < haystack.length) {
        val match = regex.find(haystack, from) ?: break
        val dist = abs(match.range.first - expectedIndex)
        if (dist < bestDist) {
            bestDist = dist
            best = match.range.first
        }
        from = match.range.first + 1
    }
    return best
}

/** 截取 [from] 起的前向片段并折叠空白，作为宽容匹配的指纹。 */
internal fun looseSnippetFrom(text: String, from: Int, rawChars: Int = 160, keepChars: Int = 48): String {
    if (text.isEmpty()) return ""
    val safe = from.coerceIn(0, text.length)
    return text.substring(safe, (safe + rawChars).coerceAtMost(text.length))
        .replace(Regex("""\s+"""), " ")
        .trim()
        .take(keepChars)
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
    // 先用视口顶渲染片段在源码窗口内宽容回查：比例映射在标记密集处（长 URL、代码围栏、
    // 图表源码）会偏差上千字，保存偏了的位置恢复端再精确也只是精确地回到错误的行。
    val safeWinEnd = windowEnd.coerceIn(windowStart, sourceContent.length)
    if (safeWinEnd > windowStart) {
        val snippet = looseSnippetFrom(displayed, rend)
        val hint = proportionalSourceOffset(windowStart, windowEnd, displayed.length, rend)
        locateLooseSnippetNear(
            haystack = sourceContent.substring(windowStart, safeWinEnd),
            snippet = snippet,
            expectedIndex = hint - windowStart,
        )?.let { return (windowStart + it).coerceIn(0, sourceContent.length) }
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

/**
 * 等待 Markwon 异步渲染完成且 layout 就绪（避免在空白/旧文本上恢复滚动）。
 *
 * [expectedRenderSig] 必须与 [TAG_READER_RENDER_SIG] / `reader_markdown_render_complete` 一致，
 * 即 [readerContentSignature]（**不含划线**）。若误传 [readerRenderSignature]，签名永远对不上，
 * 会空等到约 maxAttempts×32ms 超时，进页遮罩长时间不揭开。
 */
internal suspend fun awaitReaderMarkdownRenderReady(
    tvProvider: () -> TextView?,
    expectedRenderSig: String,
    maxAttempts: Int = 200,
): TextView? {
    val t0 = android.os.SystemClock.uptimeMillis()
    readerOpenDbg("awaitMarkdown enter expectLen=${expectedRenderSig.length} expectTail=${expectedRenderSig.takeLast(48)}")
    var lastActual: Any? = "__unset__"
    repeat(maxAttempts) { attempt ->
        val tv = tvProvider()
        val actual = tv?.getTag(R.id.reader_markdown_render_complete)
        if (actual != lastActual) {
            lastActual = actual
            readerOpenDbg(
                "awaitMarkdown tag@${attempt} actual=${(actual as? String)?.takeLast(48)} " +
                    "match=${actual == expectedRenderSig} layoutReady=${tv?.let { isReaderTextViewLayoutReady(it) }}",
            )
        }
        if (tv != null &&
            actual == expectedRenderSig &&
            isReaderTextViewLayoutReady(tv)
        ) {
            kotlinx.coroutines.delay(48)
            if (tv.getTag(R.id.reader_markdown_render_complete) == expectedRenderSig &&
                isReaderTextViewLayoutReady(tv)
            ) {
                readerOpenDbg("awaitMarkdown ready attempt=$attempt +${android.os.SystemClock.uptimeMillis() - t0}ms")
                return tv
            }
        }
        kotlinx.coroutines.delay(32)
    }
    val timedOut = tvProvider()?.takeIf {
        it.getTag(R.id.reader_markdown_render_complete) == expectedRenderSig &&
            isReaderTextViewLayoutReady(it)
    }
    readerOpenDbg(
        "awaitMarkdown ${if (timedOut != null) "late-ready" else "TIMEOUT"} " +
            "+${android.os.SystemClock.uptimeMillis() - t0}ms lastActual=${(lastActual as? String)?.takeLast(48)}",
    )
    return timedOut
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
    if (isReaderTextViewAtScrollBottom(tv)) return true
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
    // 目录跳转后窗口顶在章节开头，scrollY≈0；此时只能靠边缘拖动触发，进度阈值仍满足。
    if (tv.scrollY <= 0) return true
    val charProgress = localCharProgressAtScrollTop(tv)
    if (charProgress > threshold) return false
    val charAtTop = charOffsetAtScrollTop(tv)
    if (!isTallLineAtOffset(tv, charAtTop)) return true
    val layout = tv.layout ?: return false
    val line = layout.getLineForOffset(charAtTop)
    val viewportTop = tv.scrollY + tv.paddingTop
    return viewportTop <= layout.getLineTop(line) + 8
}

/** TextView 是否已滚到内容底部（无法再向下滚时靠边缘拖动触发向下扩窗）。 */
internal fun isReaderTextViewAtScrollBottom(tv: TextView): Boolean {
    val layout = tv.layout ?: return false
    val innerH = tv.height - tv.paddingTop - tv.paddingBottom
    if (innerH <= 0) return false
    val maxScroll = (layout.height - innerH).coerceAtLeast(0)
    return tv.scrollY >= maxScroll - 1
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

/** 目录/书签跳转：锁定渲染文本下标，Mermaid/大图异步改行高后仍回到同一标题行。 */
internal fun applyPendingScrollToCharOffset(tv: TextView, charOffsetInText: Int) {
    val len = tv.text?.length ?: 0
    if (len <= 0) return
    val offset = charOffsetInText.coerceIn(0, (len - 1).coerceAtLeast(0))
    tv.setTag(R.id.reader_pending_scroll_char_offset, offset)
    scrollTextViewToCharOffset(tv, offset)
}

internal fun clearPendingScrollCharOffset(tv: TextView?) {
    tv?.setTag(R.id.reader_pending_scroll_char_offset, null)
    cancelPendingScrollReapply(tv)
}

/**
 * 目录跳转「置顶」意图：目标章节即窗口首行，只需 scrollY=0，无需按字符 offset 解析、
 * 也不进入 offset/anchor 恢复的 LaunchedEffect 竞争。用户开始拖动时清除即可放弃置顶。
 */
internal fun requestReaderScrollToTop(tv: TextView?) {
    tv?.setTag(R.id.reader_pending_scroll_to_top, true)
}

internal fun clearReaderScrollToTop(tv: TextView?) {
    tv?.setTag(R.id.reader_pending_scroll_to_top, null)
}

internal fun hasReaderScrollToTop(tv: TextView?): Boolean =
    tv?.getTag(R.id.reader_pending_scroll_to_top) == true

/**
 * 若存在置顶意图，滚到顶部。[clearAfter] 为 true（渲染完成后）时清除意图；
 * onLayout 首帧只保证在顶部但不清，等 finishMarkdownRender 匹配新内容后再清。
 */
internal fun applyReaderScrollToTopIfAny(tv: TextView, clearAfter: Boolean): Boolean {
    if (tv.getTag(R.id.reader_pending_scroll_to_top) != true) return false
    if (tv.scrollY != 0) tv.scrollTo(0, 0)
    if (clearAfter) clearReaderScrollToTop(tv)
    return true
}

/**
 * 打开书 / 书签跳转：在 Markdown 写入后、首帧 draw 前按「源码位置+预览」定位。
 * 与扩窗 stash 同理——若等 Compose LaunchedEffect，会先以 scrollY=0 画出窗口开头再跳回。
 */
internal data class PendingSavedPositionSnap(
    val sourceContent: String,
    val sourceOffset: Int,
    val windowStart: Int,
    val windowEnd: Int,
    val preview: String?,
    val renderPlainText: Boolean,
    val tocEntries: List<MarkdownTocEntry>,
)

internal fun stashPendingSavedPositionSnap(tv: TextView?, snap: PendingSavedPositionSnap?) {
    tv?.setTag(R.id.reader_pending_snap_restore, snap)
}

internal fun clearPendingSavedPositionSnap(tv: TextView?) {
    tv?.setTag(R.id.reader_pending_snap_restore, null)
}

internal fun hasPendingSavedPositionSnap(tv: TextView?): Boolean =
    tv?.getTag(R.id.reader_pending_snap_restore) is PendingSavedPositionSnap

internal fun applyStashedSavedPositionSnapIfAny(tv: TextView): Boolean {
    val snap = tv.getTag(R.id.reader_pending_snap_restore) as? PendingSavedPositionSnap ?: return false
    if (!isReaderTextViewLayoutReady(tv)) return false
    val offset = resolveDisplayedCharOffsetForSavedPosition(
        sourceContent = snap.sourceContent,
        sourceOffset = snap.sourceOffset,
        displayedText = tv.text,
        renderPlainText = snap.renderPlainText,
        windowStart = snap.windowStart,
        windowEnd = snap.windowEnd,
        tocEntries = snap.tocEntries,
        preferredText = snap.preview,
    )
    applyPendingScrollToCharOffset(tv, offset)
    clearPendingSavedPositionSnap(tv)
    return true
}

/** 抓取视口顶附近文本作为恢复指纹（空白折叠为单空格，与 [locateExpandFingerprintNear] 的宽容匹配配套）。 */
internal fun captureExpandFingerprint(tv: TextView): String =
    previewPlainTextFromTextViewTop(tv)
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(48)

/**
 * 在显示文本中查找扩窗指纹，返回离 [expectedIndex] 最近的匹配起点。
 * 指纹在 stash 时已把空白折叠为单空格，而渲染文本里词元间可能是换行/空行，
 * 因此按词元构造 `\s+` 连接的正则做宽容匹配（直接 indexOf 跨行必失败）。
 */
internal fun locateExpandFingerprintNear(
    text: String,
    fingerprint: String?,
    expectedIndex: Int,
): Int? {
    if (fingerprint.isNullOrBlank() || fingerprint.length < 8 || text.isEmpty()) return null
    val tokens = fingerprint.split(' ').filter { it.isNotEmpty() }
    if (tokens.isEmpty()) return null
    val regex = runCatching {
        Regex(tokens.joinToString(separator = "\\s+") { Regex.escape(it) })
    }.getOrNull() ?: return null
    var best: Int? = null
    var bestDist = Int.MAX_VALUE
    var from = 0
    while (from < text.length) {
        val match = regex.find(text, from) ?: break
        val dist = abs(match.range.first - expectedIndex)
        if (dist < bestDist) {
            bestDist = dist
            best = match.range.first
        }
        from = match.range.first + 1
    }
    return best
}

/**
 * 扩窗前写入恢复信息。
 * - [anchor]：向上扩窗时优先按「旧 scrollY + 旧窗口起点」叠加 prepend 高度（视觉连续，避免停在目标上方）。
 * - sourceOffset：兜底比例映射。
 */
internal fun stashPendingSourceScrollRestore(
    tv: TextView?,
    sourceOffset: Int,
    windowStart: Int,
    windowEnd: Int,
    anchor: TextViewScrollAnchor? = null,
) {
    if (tv == null) return
    tv.setTag(R.id.reader_pending_source_scroll_offset, sourceOffset)
    tv.setTag(R.id.reader_pending_source_scroll_win_start, windowStart)
    tv.setTag(R.id.reader_pending_source_scroll_win_end, windowEnd)
    // 记录扩窗前旧显示文本长度（此刻 tv 仍显示旧窗口内容），向上扩窗恢复时用差值精确定位前置段。
    tv.setTag(R.id.reader_pending_expand_old_displayed_len, tv.text?.length ?: 0)
    if (anchor != null) {
        tv.setTag(R.id.reader_pending_expand_anchor_scroll_y, anchor.scrollY)
        tv.setTag(R.id.reader_pending_expand_anchor_line_top, anchor.lineTop)
        tv.setTag(R.id.reader_pending_expand_anchor_window_start, anchor.windowStart)
    } else {
        tv.setTag(R.id.reader_pending_expand_anchor_scroll_y, null)
        tv.setTag(R.id.reader_pending_expand_anchor_line_top, null)
        tv.setTag(R.id.reader_pending_expand_anchor_window_start, null)
    }
    // 扩窗前抓取视口顶附近文本，恢复时精确查找，避免 Markdown 比例映射偏到目标上方。
    val fingerprint = captureExpandFingerprint(tv)
    tv.setTag(
        R.id.reader_pending_expand_fingerprint,
        fingerprint.takeIf { it.length >= 4 },
    )
    tv.setTag(R.id.reader_pending_expand_fingerprint_start, charOffsetAtScrollTop(tv))
}

/**
 * 扩窗渲染在途时用户可能继续滑动旧内容；在新内容写入 TextView 前重抓锚点/指纹，
 * 使恢复落在用户当前视口而非 stash 时的旧位置（否则表现为「滑着滑着被拉回去」）。
 * 仅当 TextView 仍显示 stash 时的旧内容（长度一致）才刷新，否则坐标系已失效。
 */
internal fun refreshStashedExpandAnchorBeforeContentSwap(tv: TextView) {
    if (!hasPendingSourceScrollRestore(tv)) return
    val oldDisplayedLen = tv.getTag(R.id.reader_pending_expand_old_displayed_len) as? Int ?: return
    if ((tv.text?.length ?: 0) != oldDisplayedLen || !isReaderTextViewLayoutReady(tv)) return
    if (tv.getTag(R.id.reader_pending_expand_anchor_scroll_y) as? Int == null) return
    val anchorWindowStart =
        tv.getTag(R.id.reader_pending_expand_anchor_window_start) as? Int ?: return
    val anchor = captureTextViewScrollAnchor(tv, anchorWindowStart)
    tv.setTag(R.id.reader_pending_expand_anchor_scroll_y, anchor.scrollY)
    tv.setTag(R.id.reader_pending_expand_anchor_line_top, anchor.lineTop)
    val fingerprint = captureExpandFingerprint(tv)
    tv.setTag(
        R.id.reader_pending_expand_fingerprint,
        fingerprint.takeIf { it.length >= 4 },
    )
    tv.setTag(R.id.reader_pending_expand_fingerprint_start, charOffsetAtScrollTop(tv))
}

internal fun clearPendingSourceScrollRestore(tv: TextView?) {
    tv?.setTag(R.id.reader_pending_source_scroll_offset, null)
    tv?.setTag(R.id.reader_pending_source_scroll_win_start, null)
    tv?.setTag(R.id.reader_pending_source_scroll_win_end, null)
    tv?.setTag(R.id.reader_pending_expand_anchor_scroll_y, null)
    tv?.setTag(R.id.reader_pending_expand_anchor_line_top, null)
    tv?.setTag(R.id.reader_pending_expand_anchor_window_start, null)
    tv?.setTag(R.id.reader_pending_expand_old_displayed_len, null)
    tv?.setTag(R.id.reader_pending_expand_fingerprint, null)
    tv?.setTag(R.id.reader_pending_expand_fingerprint_start, null)
}

internal fun hasPendingSourceScrollRestore(tv: TextView?): Boolean =
    tv?.getTag(R.id.reader_pending_source_scroll_offset) != null

/** 在 layout 就绪后恢复滚动；须在首帧 draw 前调用（[SafeReaderTextView.onLayout]）。 */
internal fun applyStashedSourceScrollRestoreIfAny(
    tv: TextView,
    renderPlainText: Boolean,
): Boolean {
    val sourceOffset = tv.getTag(R.id.reader_pending_source_scroll_offset) as? Int ?: return false
    val windowStart = tv.getTag(R.id.reader_pending_source_scroll_win_start) as? Int ?: return false
    val windowEnd = tv.getTag(R.id.reader_pending_source_scroll_win_end) as? Int ?: return false
    if (!isReaderTextViewLayoutReady(tv)) return false
    val text = tv.text?.toString().orEmpty()
    val len = text.length
    if (len <= 0) return false

    val anchorScrollY = tv.getTag(R.id.reader_pending_expand_anchor_scroll_y) as? Int
    val anchorLineTop = tv.getTag(R.id.reader_pending_expand_anchor_line_top) as? Int
    val anchorWindowStart = tv.getTag(R.id.reader_pending_expand_anchor_window_start) as? Int
    // 向上扩窗是纯前置插入：旧窗口内容在新布局里原样下移。
    // 前置段显示长度 = 新显示长度 - 旧显示长度（旧窗口对齐章节边界，不会与前置段合并渲染），
    // 取该边界处行顶像素即前置段高度，叠加到旧 scrollY 上即可精确还原，无需比例/指纹映射。
    val oldDisplayedLen = tv.getTag(R.id.reader_pending_expand_old_displayed_len) as? Int
    android.util.Log.d(
        "ReaderRestoreDbg2",
        "enter anchorScrollY=$anchorScrollY anchorWinStart=$anchorWindowStart " +
            "winStart=$windowStart winEnd=$windowEnd len=$len oldDisplayedLen=$oldDisplayedLen " +
            "srcOff=$sourceOffset fp=${(tv.getTag(R.id.reader_pending_expand_fingerprint) as? String)?.take(12)}",
    )
    if (anchorScrollY != null && anchorWindowStart != null && windowStart < anchorWindowStart) {
        // 时序 guard：向上扩窗是纯前置插入，前置段（含章节标题）渲染后显示长度必然增加。
        // 若新显示长度未超过旧长度，说明新内容还没写进 TextView，本次不恢复也不清 stash，等就绪帧再来。
        if (oldDisplayedLen != null && len <= oldDisplayedLen) {
            android.util.Log.d(
                "ReaderRestoreDbg2",
                "expandUp SKIP notReady len=$len oldDisplayedLen=$oldDisplayedLen",
            )
            return false
        }
        val layout = tv.layout
        // 优先指纹精确定位旧视口顶行：length-diff 假设「后缀渲染长度不变」，但 Markdown
        // 整窗重解析时前文可能改变后缀渲染（链接引用/未闭合围栏等），差值边界偏早会把视口顶回前文。
        // 指纹在旧视口顶抓取，宽容匹配后直接得到该行在新布局中的位置，天然免疫上述偏差。
        val fingerprint = tv.getTag(R.id.reader_pending_expand_fingerprint) as? String
        val storedFingerprintStart = tv.getTag(R.id.reader_pending_expand_fingerprint_start) as? Int
        if (layout != null && oldDisplayedLen != null && oldDisplayedLen in 0..len) {
            val lengthDiffBoundary = (len - oldDisplayedLen).coerceIn(0, (len - 1).coerceAtLeast(0))
            val expectedViewportTopIdx =
                (lengthDiffBoundary + (storedFingerprintStart ?: 0)).coerceIn(0, len - 1)
            val fingerprintIdx = locateExpandFingerprintNear(
                text = text,
                fingerprint = fingerprint,
                expectedIndex = expectedViewportTopIdx,
            )
            if (fingerprintIdx != null) {
                val line = layout.getLineForOffset(fingerprintIdx)
                    .coerceIn(0, (layout.lineCount - 1).coerceAtLeast(0))
                val lineTop = layout.getLineTop(line)
                val inLineOffset = (anchorScrollY - (anchorLineTop ?: anchorScrollY)).coerceAtLeast(0)
                android.util.Log.d(
                    "ReaderRestoreDbg2",
                    "expandUp fingerprint idx=$fingerprintIdx expected=$expectedViewportTopIdx " +
                        "lineTop=$lineTop inLineOffset=$inLineOffset",
                )
                scrollTextViewPreservingScrollY(tv, lineTop + inLineOffset)
                clearPendingSourceScrollRestore(tv)
                return true
            }
            val boundaryLine = layout.getLineForOffset(lengthDiffBoundary)
                .coerceIn(0, (layout.lineCount - 1).coerceAtLeast(0))
            val prependedHeight = layout.getLineTop(boundaryLine)
            android.util.Log.d(
                "ReaderRestoreDbg2",
                "expandUp exact boundaryOffset=$lengthDiffBoundary prependedHeight=$prependedHeight " +
                    "anchorScrollY=$anchorScrollY -> targetScrollY=${anchorScrollY + prependedHeight}",
            )
            scrollTextViewPreservingScrollY(tv, anchorScrollY + prependedHeight)
        } else {
            android.util.Log.d("ReaderRestoreDbg2", "expandUp FALLBACK heightAccum")
            // 缺少旧长度或 layout 未就绪时回退到高度累加实现。
            restoreTextViewScrollAfterWindowChange(
                tv = tv,
                anchor = TextViewScrollAnchor(
                    scrollY = anchorScrollY,
                    lineTop = anchorLineTop ?: anchorScrollY,
                    windowStart = anchorWindowStart,
                ),
                newWindowStart = windowStart,
                newWindowEnd = windowEnd,
                renderPlainText = renderPlainText,
            )
        }
        clearPendingSourceScrollRestore(tv)
        return true
    }
    if (anchorScrollY != null && anchorWindowStart != null && windowStart >= anchorWindowStart) {
        scrollTextViewPreservingScrollY(tv, anchorScrollY)
        clearPendingSourceScrollRestore(tv)
        return true
    }

    val fingerprint = tv.getTag(R.id.reader_pending_expand_fingerprint) as? String
    if (!fingerprint.isNullOrBlank()) {
        val storedTop = tv.getTag(R.id.reader_pending_expand_fingerprint_start) as? Int
        val expectedIndex = when {
            anchorWindowStart != null && windowStart < anchorWindowStart ->
                resolveDisplayedCharOffsetForProgressRestore(
                    sourceOffset = anchorWindowStart,
                    windowStart = windowStart,
                    windowEnd = windowEnd,
                    displayedLen = len,
                    renderPlainText = renderPlainText,
                ).coerceIn(0, (len - 1).coerceAtLeast(0)) + (storedTop ?: 0)
            storedTop != null -> storedTop.coerceIn(0, (len - 1).coerceAtLeast(0))
            else -> 0
        }
        val idx = locateExpandFingerprintNear(
            text = text,
            fingerprint = fingerprint,
            expectedIndex = expectedIndex.coerceIn(0, (len - 1).coerceAtLeast(0)),
        )
        android.util.Log.d(
            "ReaderRestoreDbg2",
            "fingerprint branch fp='${fingerprint.take(16)}' expected=$expectedIndex idx=$idx",
        )
        if (idx != null && idx >= 0) {
            val layout = tv.layout
            val savedScrollY = anchorScrollY ?: tv.scrollY
            val savedLineTop = anchorLineTop ?: savedScrollY
            if (layout != null) {
                val line = layout.getLineForOffset(idx)
                val lineTop = layout.getLineTop(line)
                val inLineOffset = (savedScrollY - savedLineTop).coerceAtLeast(0)
                scrollTextViewPreservingScrollY(tv, lineTop + inLineOffset)
            } else {
                scrollTextViewToCharOffset(tv, idx)
            }
            clearPendingSourceScrollRestore(tv)
            return true
        }
    }

    val displayed = resolveDisplayedCharOffsetForProgressRestore(
        sourceOffset = sourceOffset,
        windowStart = windowStart,
        windowEnd = windowEnd,
        displayedLen = len,
        renderPlainText = renderPlainText,
    )
    android.util.Log.d("ReaderRestoreDbg2", "PROPORTIONAL fallthrough displayed=$displayed srcOff=$sourceOffset")
    scrollTextViewToCharOffset(tv, displayed)
    clearPendingSourceScrollRestore(tv)
    return true
}

internal fun cancelPendingScrollReapply(tv: TextView?) {
    if (tv == null) return
    val nextGen = (tv.getTag(R.id.reader_pending_scroll_reapply_gen) as? Int ?: 0) + 1
    tv.setTag(R.id.reader_pending_scroll_reapply_gen, nextGen)
}

internal fun reapplyPendingScrollCharOffsetIfAny(tv: TextView) {
    val offset = tv.getTag(R.id.reader_pending_scroll_char_offset) as? Int ?: return
    scrollTextViewToCharOffset(tv, offset)
}

/**
 * 跳转后短暂补一次滚动（等首帧 layout），完成后立刻清除锁定标记。
 * 若保留 pending 标记，用户滑走后 Mermaid/大图 setResult 仍会按标题 offset 拉回，
 * 表现为「滑动后跳到稍微前一点」。
 */
internal fun schedulePendingScrollReapply(
    tv: TextView,
    delaysMs: LongArray = longArrayOf(120),
) {
    val gen = (tv.getTag(R.id.reader_pending_scroll_reapply_gen) as? Int ?: 0) + 1
    tv.setTag(R.id.reader_pending_scroll_reapply_gen, gen)
    delaysMs.forEach { delay ->
        tv.postDelayed({
            if (tv.getTag(R.id.reader_pending_scroll_reapply_gen) != gen) return@postDelayed
            if (tv.getTag(R.id.reader_pending_scroll_char_offset) != null) {
                reapplyPendingScrollCharOffsetIfAny(tv)
                // 补滚一次即解锁，后续布局变化只保留当前 scrollY。
                clearPendingScrollCharOffset(tv)
            }
        }, delay)
    }
}

internal fun hasPendingScrollCharOffset(tv: TextView?): Boolean =
    tv?.getTag(R.id.reader_pending_scroll_char_offset) != null

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
    val (winStart, winEnd) = computeTocJumpReadingWindow(chapterBoundaries, safeCharPos, contentLen)
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
                    val displayedOffset = if (preferredTocEntry != null) {
                        resolveDisplayedCharOffset(
                            sourceContent = sourceContent,
                            sourceOffset = safeCharPos,
                            displayedText = tv.text,
                            renderPlainText = renderPlainText,
                            windowStart = globalStart,
                            tocEntries = tocEntries,
                            preferredEntry = preferredTocEntry,
                            preferredText = bookmarkPreviewText,
                        )
                    } else {
                        // 打开书 / 书签：与垂直模式同一套「位置+预览」定位。
                        resolveDisplayedCharOffsetForSavedPosition(
                            sourceContent = sourceContent,
                            sourceOffset = safeCharPos,
                            displayedText = tv.text,
                            renderPlainText = renderPlainText,
                            windowStart = globalStart,
                            windowEnd = globalStart + (tv.text?.length ?: 0),
                            tocEntries = tocEntries,
                            preferredText = bookmarkPreviewText,
                        )
                    }
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
        // 保留用户选中的展示层原文，供 Markdown 渲染后匹配；勿用源码切片覆盖（含 #/` 等时会对不上）
        val snippet = h.highlightedText.ifBlank { slice.substring(s, e) }
        h.copy(startPosition = s, endPosition = e, highlightedText = snippet)
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
