package space.liushenme.markdownreader.ui.screens.reader

import android.text.Selection
import android.text.Spannable
import android.text.SpannableString
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.util.Log
import android.widget.TextView
import space.liushenme.markdownreader.data.local.entity.HighlightEntity
import space.liushenme.markdownreader.markdown.CodeBlockHighlightRange
import space.liushenme.markdownreader.markdown.ReaderScrollableCodeBlockSpan
import space.liushenme.markdownreader.model.HighlightStyle

internal fun applyHighlightDecorationAtRange(
    textView: TextView,
    start: Int,
    end: Int,
    colorArgb: Int,
    style: space.liushenme.markdownreader.model.HighlightStyle,
    highlightId: Long = 0L,
) {
    val text = ensureReaderSpannable(textView) ?: return
    if (start < 0 || end > text.length || start >= end) return
    val codeSpan = text.getSpans(start, end, ReaderScrollableCodeBlockSpan::class.java)
        .firstOrNull { span ->
            val spanStart = text.getSpanStart(span)
            val spanEnd = text.getSpanEnd(span)
            spanStart >= 0 && spanStart < end && spanEnd > start && span.hasSelection()
        }
    if (codeSpan != null) {
        codeSpan.addHighlightRange(
            CodeBlockHighlightRange(
                start = codeSpan.selectionStart,
                end = codeSpan.selectionEnd,
                color = colorArgb,
                underline = style != space.liushenme.markdownreader.model.HighlightStyle.Background,
                wavy = style == space.liushenme.markdownreader.model.HighlightStyle.Wavy,
                highlightId = highlightId,
            ),
        )
        textView.invalidate()
        return
    }
    text.getSpans(start, end, HighlightBackgroundSpan::class.java).forEach {
        if (it.highlightId == highlightId) text.removeSpan(it)
    }
    text.getSpans(start, end, HighlightUnderlineSpan::class.java).forEach {
        if (it.highlightId == highlightId) text.removeSpan(it)
    }
    val span: Any = when (style) {
        space.liushenme.markdownreader.model.HighlightStyle.Background ->
            HighlightBackgroundSpan(colorArgb, highlightId)
        space.liushenme.markdownreader.model.HighlightStyle.Underline ->
            HighlightUnderlineSpan(colorArgb, wavy = false, highlightId = highlightId)
        space.liushenme.markdownreader.model.HighlightStyle.Wavy ->
            HighlightUnderlineSpan(colorArgb, wavy = true, highlightId = highlightId)
    }
    text.setSpan(span, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
    textView.invalidate()
}

/** 将草稿划线原位绑定到 Room 返回的真实 id。 */
internal fun rebindReaderHighlightDecorationId(
    textView: TextView,
    oldId: Long,
    newId: Long,
) {
    if (oldId == newId || newId <= 0L) return
    val text = ensureReaderSpannable(textView) ?: return
    var changed = false
    text.getSpans(0, text.length, HighlightBackgroundSpan::class.java).forEach { span ->
        if (span.highlightId != oldId) return@forEach
        val start = text.getSpanStart(span)
        val end = text.getSpanEnd(span)
        if (start < 0 || end <= start) return@forEach
        text.removeSpan(span)
        text.setSpan(
            HighlightBackgroundSpan(span.backgroundColor, newId),
            start,
            end,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
        changed = true
    }
    text.getSpans(0, text.length, HighlightUnderlineSpan::class.java).forEach { span ->
        if (span.highlightId != oldId) return@forEach
        val start = text.getSpanStart(span)
        val end = text.getSpanEnd(span)
        if (start < 0 || end <= start) return@forEach
        text.removeSpan(span)
        text.setSpan(
            HighlightUnderlineSpan(span.color, span.wavy, newId),
            start,
            end,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
        changed = true
    }
    text.getSpans(0, text.length, ReaderScrollableCodeBlockSpan::class.java).forEach { span ->
        if (span.replaceHighlightId(oldId, newId)) changed = true
    }
    if (changed) textView.invalidate()
}

/** 删除指定真实或草稿 id 的装饰，不影响重叠的其它划线。 */
internal fun removeReaderHighlightDecorationById(textView: TextView, highlightId: Long) {
    val text = ensureReaderSpannable(textView) ?: return
    var changed = false
    text.getSpans(0, text.length, HighlightBackgroundSpan::class.java).forEach { span ->
        if (span.highlightId == highlightId) {
            text.removeSpan(span)
            changed = true
        }
    }
    text.getSpans(0, text.length, HighlightUnderlineSpan::class.java).forEach { span ->
        if (span.highlightId == highlightId) {
            text.removeSpan(span)
            changed = true
        }
    }
    text.getSpans(0, text.length, ReaderScrollableCodeBlockSpan::class.java).forEach { span ->
        if (span.removeHighlightRangesById(highlightId)) changed = true
    }
    if (changed) textView.invalidate()
}

/** 已绘制划线在 TextView 中的占位偏移；代码块返回 ReplacementSpan 起点。 */
internal fun displayedOffsetForHighlightId(textView: TextView, highlightId: Long): Int? {
    if (highlightId <= 0L) return null
    val text = textView.text as? Spanned ?: return null
    text.getSpans(0, text.length, HighlightBackgroundSpan::class.java)
        .firstOrNull { it.highlightId == highlightId }
        ?.let { return text.getSpanStart(it).takeIf { start -> start >= 0 } }
    text.getSpans(0, text.length, HighlightUnderlineSpan::class.java)
        .firstOrNull { it.highlightId == highlightId }
        ?.let { return text.getSpanStart(it).takeIf { start -> start >= 0 } }
    text.getSpans(0, text.length, ReaderScrollableCodeBlockSpan::class.java).forEach { span ->
        if (span.hasHighlightId(highlightId)) {
            return text.getSpanStart(span).takeIf { start -> start >= 0 }
        }
    }
    return null
}

/** 清除由阅读划线写入的 span，保留 Markdown 自身背景色等。 */
internal fun clearReaderHighlightSpans(textView: TextView) {
    val text = textView.text
    if (text !is Spannable || text.isEmpty()) return
    text.getSpans(0, text.length, HighlightBackgroundSpan::class.java).forEach { text.removeSpan(it) }
    text.getSpans(0, text.length, HighlightUnderlineSpan::class.java).forEach { text.removeSpan(it) }
    text.getSpans(0, text.length, ReaderScrollableCodeBlockSpan::class.java)
        .forEach { it.clearHighlightRanges() }
    textView.invalidate()
}

/** 清除指定展示区间上的划线装饰（点「取消划线」时立刻去掉预览 span）。 */
internal fun clearReaderHighlightSpansAtRange(textView: TextView, start: Int, end: Int) {
    val text = ensureReaderSpannable(textView) ?: return
    if (start < 0 || end > text.length || start >= end) return
    text.getSpans(start, end, HighlightBackgroundSpan::class.java).forEach { text.removeSpan(it) }
    text.getSpans(start, end, HighlightUnderlineSpan::class.java).forEach { text.removeSpan(it) }
    text.getSpans(start, end, ReaderScrollableCodeBlockSpan::class.java).forEach { span ->
        if (span.hasSelection()) {
            span.removeHighlightRangeMatching(span.selectedText())
        }
    }
    textView.invalidate()
}

/** Markwon 偶发给出只读 Spanned；划线需要可变 [Spannable]。 */
internal fun ensureReaderSpannable(textView: TextView): Spannable? {
    val raw = textView.text ?: return null
    if (raw is Spannable) return raw
    if (raw.isEmpty()) return null
    val mutable = SpannableString(raw)
    textView.setText(mutable, TextView.BufferType.SPANNABLE)
    return textView.text as? Spannable
}

private data class PreservedHighlightDecoration(
    val highlightId: Long,
    val start: Int,
    val end: Int,
    val color: Int,
    val underline: Boolean,
    val wavy: Boolean,
    val quote: String,
    val codeSpan: ReaderScrollableCodeBlockSpan? = null,
)

/** 重刷前记下已经画在展示层上的划线，源码投影失败时再放回去。 */
private fun snapshotReaderHighlightDecorations(textView: TextView): List<PreservedHighlightDecoration> {
    val text = textView.text as? Spanned ?: return emptyList()
    if (text.isEmpty()) return emptyList()
    val context = textView.context
    val out = ArrayList<PreservedHighlightDecoration>()
    text.getSpans(0, text.length, HighlightBackgroundSpan::class.java).forEach { span ->
        val start = text.getSpanStart(span)
        val end = text.getSpanEnd(span)
        if (start < 0 || end <= start) return@forEach
        out += PreservedHighlightDecoration(
            highlightId = span.highlightId,
            start = start,
            end = end,
            color = span.backgroundColor,
            underline = false,
            wavy = false,
            quote = extractReaderSelectionText(text, start, end, context),
        )
    }
    text.getSpans(0, text.length, HighlightUnderlineSpan::class.java).forEach { span ->
        val start = text.getSpanStart(span)
        val end = text.getSpanEnd(span)
        if (start < 0 || end <= start) return@forEach
        out += PreservedHighlightDecoration(
            highlightId = span.highlightId,
            start = start,
            end = end,
            color = span.color,
            underline = true,
            wavy = span.wavy,
            quote = extractReaderSelectionText(text, start, end, context),
        )
    }
    text.getSpans(0, text.length, ReaderScrollableCodeBlockSpan::class.java).forEach { codeSpan ->
        codeSpan.highlightRangesForTest().forEach { range ->
            val quote = codeSpan.rawCode.substring(
                range.start.coerceIn(0, codeSpan.rawCode.length),
                range.end.coerceIn(0, codeSpan.rawCode.length),
            )
            out += PreservedHighlightDecoration(
                highlightId = range.highlightId,
                start = range.start,
                end = range.end,
                color = range.color,
                underline = range.underline,
                wavy = range.wavy,
                quote = quote,
                codeSpan = codeSpan,
            )
        }
    }
    return out
}

/**
 * 源码坐标暂时对不上展示层时，保留取消选区前已经画出的划线。
 * 只恢复仍在库里的记录；删掉的划线不会被放回。
 */
private fun restoreReaderHighlightDecorations(
    textView: TextView,
    preserved: List<PreservedHighlightDecoration>,
) {
    if (preserved.isEmpty()) return
    val safeTextView = textView as? SafeReaderTextView
    val live = safeTextView?.retainedHighlights.orEmpty()
    if (live.isEmpty() && preserved.none { safeTextView?.isDraftHighlightActive(it.highlightId) == true }) return
    val text = ensureReaderSpannable(textView) ?: return
    val painted = HashSet<Long>()
    text.getSpans(0, text.length, HighlightBackgroundSpan::class.java).forEach { span ->
        if (span.highlightId > 0L) painted += span.highlightId
    }
    text.getSpans(0, text.length, HighlightUnderlineSpan::class.java).forEach { span ->
        if (span.highlightId > 0L) painted += span.highlightId
    }
    text.getSpans(0, text.length, ReaderScrollableCodeBlockSpan::class.java).forEach { codeSpan ->
        codeSpan.highlightRangesForTest().forEach { range ->
            if (range.highlightId > 0L) painted += range.highlightId
        }
    }
    var restored = false
    for (item in preserved) {
        if (!preservedDecorationStillMatches(textView, text, item)) continue
        val owner = item.highlightId.takeIf { it > 0L }?.let { id -> live.firstOrNull { it.id == id } }
        val draftActive = safeTextView?.isDraftHighlightActive(item.highlightId) == true
        if (owner == null && !draftActive) continue
        val restoredId = owner?.id ?: item.highlightId
        if (restoredId in painted) continue
        val style = owner?.let {
            space.liushenme.markdownreader.model.HighlightStyle.fromStorageKey(it.style)
        }
        val color = owner?.let {
            if (android.graphics.Color.alpha(it.color) < 16) item.color else it.color
        } ?: item.color
        if (item.codeSpan != null) {
            item.codeSpan.addHighlightRange(
                CodeBlockHighlightRange(
                    start = item.start,
                    end = item.end,
                    color = color,
                    underline = style?.let { it != space.liushenme.markdownreader.model.HighlightStyle.Background }
                        ?: item.underline,
                    wavy = style?.let { it == space.liushenme.markdownreader.model.HighlightStyle.Wavy }
                        ?: item.wavy,
                    highlightId = restoredId,
                ),
            )
        } else {
            val span: Any = when {
                style == space.liushenme.markdownreader.model.HighlightStyle.Background ||
                    (style == null && !item.underline) -> HighlightBackgroundSpan(color, restoredId)
                style == space.liushenme.markdownreader.model.HighlightStyle.Wavy ||
                    (style == null && item.wavy) -> HighlightUnderlineSpan(color, wavy = true, highlightId = restoredId)
                else -> HighlightUnderlineSpan(color, wavy = false, highlightId = restoredId)
            }
            text.setSpan(span, item.start, item.end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        painted += restoredId
        restored = true
    }
    if (restored) textView.invalidate()
}

private fun preservedDecorationStillMatches(
    textView: TextView,
    text: Spanned,
    item: PreservedHighlightDecoration,
): Boolean {
    if (item.quote.isEmpty()) return false
    val codeSpan = item.codeSpan
    if (codeSpan != null) {
        val raw = codeSpan.rawCode
        if (item.start < 0 || item.end > raw.length || item.end <= item.start) return false
        return raw.substring(item.start, item.end) == item.quote
    }
    if (item.start < 0 || item.end > text.length || item.end <= item.start) return false
    if (text.substring(item.start, item.end) == item.quote) return true
    return extractReaderSelectionText(text, item.start, item.end, textView.context) == item.quote
}

internal fun refreshReaderHighlightSpans(
    textView: TextView,
    highlights: List<HighlightEntity>,
    highlightColorArgb: Int,
    sourceContentLength: Int,
    highlightSig: String,
    sourceText: String? = null,
) {
    val preserved = snapshotReaderHighlightDecorations(textView)
    clearReaderHighlightSpans(textView)
    applyHighlightsToRenderedText(
        textView = textView,
        highlights = highlights,
        highlightColorArgb = highlightColorArgb,
        sourceContentLength = sourceContentLength,
        sourceText = sourceText,
    )
    restoreReaderHighlightDecorations(textView, preserved)
    textView.setTag(TAG_READER_HIGHLIGHT_SIG, highlightSig)
}

/**
 * 将已经确定的源码区间投影到展示层 [start, end)。
 * 只在该区间对应的渲染邻域里找引文；源码对得上且展示层只有一处时，允许渲染伸缩把比例映射拉得很远。
 * 同一段引文出现多次时，不在全文里挑最近的一处。
 */
internal fun resolveHighlightDisplayedRange(
    displayed: String,
    highlight: space.liushenme.markdownreader.data.local.entity.HighlightEntity,
    sourceContentLength: Int,
    sourceText: String? = null,
): IntRange? {
    val snippet = highlight.highlightedText
    if (snippet.isEmpty() || displayed.isEmpty()) return null
    val s = highlight.startPosition
    val e = highlight.endPosition
    if (s in 0..displayed.length && e in (s + 1)..displayed.length &&
        displayed.substring(s, e) == snippet
    ) {
        return s until e
    }
    if (s in 0 until displayed.length &&
        s + snippet.length <= displayed.length &&
        displayed.regionMatches(s, snippet, 0, snippet.length)
    ) {
        return s until (s + snippet.length)
    }
    val estimate = when {
        sourceContentLength > 0 ->
            ((s.toLong() * displayed.length) / sourceContentLength)
                .toInt()
                .coerceIn(0, displayed.length)
        else -> s.coerceIn(0, displayed.length)
    }
    if (estimate + snippet.length <= displayed.length &&
        displayed.regionMatches(estimate, snippet, 0, snippet.length)
    ) {
        return estimate until (estimate + snippet.length)
    }
    val nearRadius = maxOf(snippet.length * 8, 160)
    val sourceAnchored = sourceContainsSnippetNear(sourceText, s, snippet)
    val anchor = renderedContextAnchorForSourceOffset(
        displayed = displayed,
        sourceText = sourceText,
        sourceOffset = s,
        estimate = estimate,
    )
    val pivot = anchor ?: estimate
    fun accept(idx: Int): IntRange? {
        if (idx < 0 || idx + snippet.length > displayed.length) return null
        return idx until (idx + snippet.length)
    }
    closestSnippetIndexNear(displayed, snippet, pivot, nearRadius)?.let { idx ->
        accept(idx)?.let { return it }
    }
    if (anchor != null) accept(anchor)?.let { return it }
    if (sourceAnchored) {
        val only = displayed.indexOf(snippet)
        if (only >= 0 && displayed.indexOf(snippet, only + 1) < 0) {
            return only until (only + snippet.length)
        }
    }
    return null
}

/**
 * 用划线后的源码上下文反查展示层位置。源码里大段 HTML、链接地址、代码标记被渲染吞掉时，
 * 全局长度比例会严重漂移；向后指纹仍能把同文的具体一次出现锁定下来。
 */
private fun renderedContextAnchorForSourceOffset(
    displayed: String,
    sourceText: String?,
    sourceOffset: Int,
    estimate: Int,
): Int? {
    if (sourceText.isNullOrEmpty() || sourceOffset !in sourceText.indices || displayed.isEmpty()) return null
    val beforeFrom = (sourceOffset - 40).coerceAtLeast(0)
    locateLooseSnippetNear(
        haystack = displayed,
        snippet = looseSnippetFrom(sourceText, beforeFrom, rawChars = 220, keepChars = 72),
        expectedIndex = estimate,
    )?.let { return it }

    val forwardFingerprint = looseSnippetFrom(
        text = sourceText,
        from = sourceOffset,
        rawChars = 260,
        keepChars = 96,
    )
    return locateLooseSnippetNear(
        haystack = displayed,
        snippet = forwardFingerprint,
        expectedIndex = estimate,
    )
}

/** 保存的源码坐标附近确实有这段划线文字。 */
internal fun sourceContainsSnippetNear(sourceText: String?, start: Int, snippet: String): Boolean {
    if (sourceText.isNullOrEmpty() || snippet.isEmpty() || start !in sourceText.indices) return false
    if (start + snippet.length <= sourceText.length &&
        sourceText.regionMatches(start, snippet, 0, snippet.length)
    ) {
        return true
    }
    return closestSnippetIndexNear(
        sourceText,
        snippet,
        start,
        maxOf(snippet.length * 4, 80),
    ) != null
}

/** 在 [anchor] 前后 [radius] 个字符内找离锚点最近的 [snippet]，远处的同文不参与。 */
internal fun closestSnippetIndexNear(
    haystack: String,
    snippet: String,
    anchor: Int,
    radius: Int,
): Int? {
    if (snippet.isEmpty() || haystack.isEmpty() || radius < 0) return null
    if (snippet.length > haystack.length) return null
    val safeAnchor = anchor.coerceIn(0, haystack.length)
    val fromLimit = (safeAnchor - radius).coerceAtLeast(0)
    val toLimit = (safeAnchor + radius).coerceAtMost(haystack.length - snippet.length)
    if (fromLimit > toLimit) return null
    var best = -1
    var bestDist = Int.MAX_VALUE
    var from = fromLimit
    while (from <= toLimit) {
        val idx = haystack.indexOf(snippet, from)
        if (idx < 0 || idx > toLimit) break
        val dist = kotlin.math.abs(idx - safeAnchor)
        if (dist < bestDist) {
            bestDist = dist
            best = idx
        }
        from = idx + 1
    }
    return best.takeIf { it >= 0 }
}

/** 落点所在的划线展示区间；没有划线时返回 null。 */
internal fun readerHighlightRangeCovering(text: Spanned, offset: Int): IntRange? {
    if (offset < 0 || offset >= text.length) return null
    val spans = text.getSpans(offset, offset + 1, HighlightBackgroundSpan::class.java).toList() +
        text.getSpans(offset, offset + 1, HighlightUnderlineSpan::class.java).toList()
    val mark = spans.minByOrNull { span ->
        val length = text.getSpanEnd(span) - text.getSpanStart(span)
        if (length > 0) length else Int.MAX_VALUE
    } ?: return null
    val start = text.getSpanStart(mark)
    val end = text.getSpanEnd(mark)
    if (start < 0 || end <= start) return null
    return start until end
}

/** 选区与某条已绘制划线重叠时返回其 id。 */
internal fun readerHighlightIdCovering(text: Spanned, start: Int, end: Int): Long? {
    if (end <= start) return null
    val spans = text.getSpans(start, end, HighlightBackgroundSpan::class.java).toList() +
        text.getSpans(start, end, HighlightUnderlineSpan::class.java).toList()
    return spans.mapNotNull { span ->
        val spanStart = text.getSpanStart(span)
        val spanEnd = text.getSpanEnd(span)
        if (spanStart < 0 || spanEnd <= spanStart || start >= spanEnd || end <= spanStart) {
            return@mapNotNull null
        }
        val id = when (span) {
            is HighlightBackgroundSpan -> span.highlightId
            is HighlightUnderlineSpan -> span.highlightId
            else -> 0L
        }
        if (id <= 0L) return@mapNotNull null
        val overlap = minOf(end, spanEnd) - maxOf(start, spanStart)
        overlap to id
    }.maxByOrNull { it.first }?.second
}
