package space.liushenme.markdownreader.ui.screens.reader

import android.text.SpannableString
import android.text.Spanned
import android.text.style.ReplacementSpan
import android.widget.TextView
import space.liushenme.markdownreader.markdown.ReaderScrollableCodeBlockSpan
import space.liushenme.markdownreader.model.HighlightStyle
import space.liushenme.markdownreader.ui.screens.reader.anchor.sourceSpanForRenderedQuote

private data class SourceFencedCodeBlock(
    val body: String,
)

private data class SourceCodeBlockRegion(
    val bodyStart: Int,
    val bodyEnd: Int,
    val body: String,
)

/**
 * 把代码块内部选区直接映射回源码正文。
 * ReplacementSpan 在外层只占一个 \uFFFC，不能使用 TextView 的 displayed offset 保存划线。
 */
internal fun sourceSpanForCodeBlockSelection(
    textView: SafeReaderTextView,
    source: String,
    selectedText: String,
    sourceHint: Int,
    searchStart: Int,
    searchEnd: Int,
): Pair<Int, Int>? {
    val selection = textView.codeSelectionSnapshotForSource() ?: return null
    val needle = selectedText.trim().ifEmpty { selection.selectedText.trim() }
    if (needle.isEmpty() || source.isEmpty()) return null
    val from = searchStart.coerceIn(0, source.length)
    val to = searchEnd.coerceIn(from, source.length)
    val windowRegions = sourceCodeBlockRegions(source).filter { region ->
        region.bodyEnd > from && region.bodyStart < to
    }
    // 页面窗口边界是渲染定位的优化范围，不应阻断代码块内部选区落库。
    // ReplacementSpan 可能跨越窗口边界（尤其是横向分页/扩窗过程中），此时
    // 选中的代码仍然属于当前块，退回全源码代码块集合并用 sourceHint 选最近候选。
    val regions = windowRegions.ifEmpty { sourceCodeBlockRegions(source) }
    if (regions.isEmpty()) return null
    val candidates = regions.mapNotNull { region ->
        val local = sourceSpanForRenderedQuote(
            source = region.body,
            rendered = needle,
            hint = selection.start.coerceIn(0, region.body.length),
            searchStart = 0,
            searchEnd = region.body.length,
        ) ?: return@mapNotNull null
        val start = region.bodyStart + local.first
        val end = region.bodyStart + local.second
        if (end <= start) null else start to end
    }
    if (candidates.isEmpty()) return null
    return candidates.minByOrNull { candidate ->
        if (sourceHint in candidate.first until candidate.second) 0
        else kotlin.math.abs(candidate.first - sourceHint)
    }
}

private fun sourceCodeBlockRegions(source: String): List<SourceCodeBlockRegion> {
    if (source.isEmpty()) return emptyList()
    val regions = ArrayList<SourceCodeBlockRegion>()
    var openChar: Char? = null
    var openLength = 0
    var bodyStart = -1
    var lineStart = 0
    while (lineStart <= source.length) {
        val newline = source.indexOf('\n', lineStart)
        val lineEnd = if (newline >= 0) newline else source.length
        val line = source.substring(lineStart, lineEnd).trimEnd('\r')
        val leading = line.indexOfFirst { it != ' ' }.let { if (it < 0) line.length else it }
        val marker = leading.takeIf { it <= 3 }?.let { line.getOrNull(it) }
            ?.takeIf { it == '`' || it == '~' }
        var markerLength = 0
        if (marker != null) {
            while (leading + markerLength < line.length && line[leading + markerLength] == marker) {
                markerLength++
            }
        }
        if (openChar == null) {
            if (marker != null && markerLength >= 3) {
                openChar = marker
                openLength = markerLength
                bodyStart = if (newline >= 0) newline + 1 else lineEnd
            }
        } else if (
            marker == openChar &&
            markerLength >= openLength &&
            line.substring((leading + markerLength).coerceAtMost(line.length)).isBlank()
        ) {
            if (bodyStart <= lineStart) {
                regions += SourceCodeBlockRegion(
                    bodyStart = bodyStart,
                    bodyEnd = lineStart,
                    body = source.substring(bodyStart, lineStart),
                )
            }
            openChar = null
            openLength = 0
            bodyStart = -1
        }
        if (newline < 0) break
        lineStart = newline + 1
    }
    if (openChar != null && bodyStart >= 0 && bodyStart <= source.length) {
        regions += SourceCodeBlockRegion(bodyStart, source.length, source.substring(bodyStart))
    }
    return regions
}

/**
 * 用源码围栏块的完整内容反查渲染后的 ReplacementSpan。
 *
 * 不使用「源码下标 / 源码长度」比例：HTML、公式和 Markdown 标记被压缩后，
 * 代码块占位符与比例估算可能相差数百个字符。
 */
internal fun codeSpanForSourceHighlight(
    codeSpans: Array<ReaderScrollableCodeBlockSpan>,
    sourceText: String?,
    sourceOffset: Int,
    snippet: String,
    displayedLength: Int,
    displayedText: Spanned,
): ReaderScrollableCodeBlockSpan? {
    if (codeSpans.isEmpty() || sourceText.isNullOrEmpty() || snippet.isEmpty()) return null
    val candidates = codeSpans.filter { it.rangeOfSnippet(snippet) != null }
    if (candidates.isEmpty()) return null

    // 代码窗口可能从围栏正文中间开始，无法通过源码围栏反查完整 body。
    // 这时不能因为窗口中存在多个代码块就放弃恢复；用源码/展示层的相对位置
    // 选择最接近的候选，仍限定在「代码块内确实包含该片段」的范围内。
    fun nearestCandidate(items: List<ReaderScrollableCodeBlockSpan>): ReaderScrollableCodeBlockSpan? {
        if (items.isEmpty()) return null
        if (items.size == 1) return items.single()
        val sourceLength = sourceText.length.coerceAtLeast(1)
        val estimatedDisplayed = (sourceOffset.toLong() * displayedLength.coerceAtLeast(1) / sourceLength)
            .toInt()
        val sourceStarts = HashMap<ReaderScrollableCodeBlockSpan, Int?>()
        items.groupBy { it.rawCode }.values.forEach { sameCode ->
            val ordered = sameCode.sortedBy { displayedText.getSpanStart(it) }
            val occurrences = ArrayList<Int>()
            var cursor = 0
            val raw = sameCode.first().rawCode
            while (raw.isNotEmpty()) {
                val start = sourceText.indexOf(raw, cursor)
                if (start < 0) break
                occurrences += start
                cursor = start + 1
            }
            ordered.forEachIndexed { index, span ->
                sourceStarts[span] = occurrences.getOrNull(index)
                    ?: sourceText.indexOf(snippet).takeIf { it >= 0 }
            }
        }
        return items.minByOrNull { span ->
            val start = displayedText.getSpanStart(span).coerceAtLeast(0)
            val sourceStart = sourceStarts[span]
            if (sourceStart != null) {
                // 当前窗口通常仍包含代码正文，即使开栏在上一页；源码位置比展示比例可靠。
                val sourceEnd = sourceStart + span.rawCode.length
                if (sourceOffset in sourceStart until sourceEnd) 0
                else kotlin.math.abs(sourceStart - sourceOffset)
            } else {
                kotlin.math.abs(start - estimatedDisplayed)
            }
        }
    }
    val sourceBlock = sourceFencedCodeBlockCovering(sourceText, sourceOffset)
    if (sourceBlock == null) {
        // 横向分页或惰加载窗可能从围栏中间开始，当前 sourceText 看不到开栏。
        return nearestCandidate(candidates)
    }
    val sourceBody = normalizeCodeBlockForMatch(sourceBlock.body)
    val exact = codeSpans.filter { span ->
        normalizeCodeBlockForMatch(span.rawCode) == sourceBody
    }
    if (exact.size == 1) return exact.single()
    if (exact.size > 1) {
        return nearestCandidate(exact.filter { it.rangeOfSnippet(snippet) != null })
    }
    // 缩进围栏会被 CommonMark 去掉公共缩进；完整文本不等时，仅允许唯一候选。
    return nearestCandidate(candidates)
}

private fun normalizeCodeBlockForMatch(value: String): String =
    value.replace("\r\n", "\n").replace('\r', '\n').trimEnd('\n')

/** 返回 [sourceOffset] 所在的 Markdown ``` / ~~~ 围栏代码块正文。 */
private fun sourceFencedCodeBlockCovering(
    source: String,
    sourceOffset: Int,
): SourceFencedCodeBlock? {
    if (sourceOffset !in source.indices) return null
    var openChar: Char? = null
    var openLength = 0
    var bodyStart = -1
    var lineStart = 0
    while (lineStart <= source.length) {
        val newline = source.indexOf('\n', lineStart)
        val lineEnd = if (newline >= 0) newline else source.length
        val line = source.substring(lineStart, lineEnd).trimEnd('\r')
        val leadingSpaces = line.indexOfFirst { it != ' ' }.let { if (it < 0) line.length else it }
        val markerStart = leadingSpaces.takeIf { it <= 3 }
        val markerChar = markerStart?.let { line.getOrNull(it) }
            ?.takeIf { it == '`' || it == '~' }
        val markerLength = if (markerChar == null || markerStart == null) {
            0
        } else {
            var count = 0
            while (markerStart + count < line.length && line[markerStart + count] == markerChar) count++
            count
        }
        if (openChar == null) {
            if (markerChar != null && markerLength >= 3) {
                openChar = markerChar
                openLength = markerLength
                bodyStart = if (newline >= 0) newline + 1 else lineEnd
            }
        } else if (
            markerChar == openChar &&
            markerLength >= openLength &&
            line.substring((markerStart ?: 0) + markerLength).isBlank()
        ) {
            if (sourceOffset in bodyStart until lineStart) {
                return SourceFencedCodeBlock(source.substring(bodyStart, lineStart))
            }
            openChar = null
            openLength = 0
            bodyStart = -1
        }
        if (newline < 0) break
        lineStart = newline + 1
    }
    if (openChar != null && bodyStart >= 0 && sourceOffset in bodyStart until source.length) {
        return SourceFencedCodeBlock(source.substring(bodyStart))
    }
    return null
}

/**
 * 在展示层选区上立即打上划线样式（不依赖源码坐标映射），用于点「划线」瞬间出默认效果。
 */
