package space.liushenme.markdownreader.ui.screens.reader

import android.util.Log
import android.text.Spanned
import android.text.style.ClickableSpan
import android.text.style.ImageSpan
import android.text.style.ReplacementSpan
import io.noties.markwon.core.spans.HeadingSpan
import io.noties.markwon.image.AsyncDrawableSpan
import space.liushenme.markdownreader.importing.PdfReaderContent
import space.liushenme.markdownreader.markdown.ReaderScrollableCodeBlockSpan

/** 统一的阅读器触摸命中类型。命中结果同时服务点击、长按、预览和划线。 */
internal enum class ReaderHitKind {
    TEXT,
    HEADING,
    INLINE_FORMULA,
    TABLE_CELL,
    LINK,
    CODE_BLOCK,
    IMAGE,
    DIAGRAM,
    EXISTING_HIGHLIGHT,
    OUTSIDE,
}

internal data class ReaderHitTarget(
    val kind: ReaderHitKind,
    val displayedStart: Int = -1,
    val displayedEnd: Int = -1,
    val selectedText: String? = null,
    val span: Any? = null,
    val canSelect: Boolean = false,
    val canClick: Boolean = false,
    val canPreview: Boolean = false,
    val canHighlight: Boolean = false,
) {
    val isTextLike: Boolean
        get() = kind == ReaderHitKind.TEXT ||
            kind == ReaderHitKind.HEADING ||
            kind == ReaderHitKind.INLINE_FORMULA ||
            kind == ReaderHitKind.TABLE_CELL ||
            kind == ReaderHitKind.LINK ||
            kind == ReaderHitKind.EXISTING_HIGHLIGHT
}

internal fun readerHitDbg(message: String) {
    if (space.liushenme.markdownreader.BuildConfig.DEBUG) {
        Log.d("ReaderHitDbg", message)
    }
}

internal fun SafeReaderTextView.readerHitTargetAt(x: Float, y: Float): ReaderHitTarget =
    resolveReaderHitTarget(this, x, y)

internal fun resolveReaderHitTarget(
    textView: SafeReaderTextView,
    x: Float,
    y: Float,
): ReaderHitTarget {
    textView.selectionHandleAtForHitTest(x, y)?.let {
        return ReaderHitTarget(
            kind = ReaderHitKind.EXISTING_HIGHLIGHT,
            canSelect = true,
            canHighlight = true,
        )
    }

    textView.scrollableCodeBlockSpanAt(x, y)?.let { span ->
        return ReaderHitTarget(
            kind = ReaderHitKind.CODE_BLOCK,
            span = span,
            canSelect = true,
            canHighlight = true,
        )
    }

    val spanned = textView.text as? Spanned
    if (spanned == null || spanned.isEmpty()) {
        return ReaderHitTarget(ReaderHitKind.OUTSIDE)
    }

    tableCellHitAt(textView, x, y)?.let { hit ->
        return ReaderHitTarget(
            kind = ReaderHitKind.TABLE_CELL,
            span = hit,
            canSelect = true,
            canHighlight = false,
        )
    }

    val drawable = ReaderTextLinkTouch.findAsyncDrawableSpanAt(textView, x, y)
    if (drawable != null && !textView.isLatexImageSpanForHitTest(drawable)) {
        val destination = drawable.drawable.destination.orEmpty()
        val isDiagram = destination.startsWith("diagram://")
        return ReaderHitTarget(
            kind = if (isDiagram) ReaderHitKind.DIAGRAM else ReaderHitKind.IMAGE,
            span = drawable,
            canClick = true,
            canPreview = !PdfReaderContent.isPageImageDestination(destination),
        )
    }

    val offset = textView.charOffsetForHitTest(x, y)
        ?: return ReaderHitTarget(ReaderHitKind.OUTSIDE)
    val safeEnd = (offset + 1).coerceAtMost(spanned.length)

    ReaderTextLinkTouch.findClickableSpanAt(textView, x, y)?.let { link ->
        val range = spanRange(spanned, link, offset, safeEnd)
        return ReaderHitTarget(
            kind = ReaderHitKind.LINK,
            displayedStart = range.first,
            displayedEnd = range.second,
            selectedText = extractReaderSelectionText(
                spanned,
                range.first,
                range.second,
                textView.context,
            ),
            span = link,
            canSelect = textView.canSelectAtOffsetForHitTest(offset),
            canClick = true,
            canHighlight = textView.canSelectAtOffsetForHitTest(offset),
        )
    }

    readerHighlightRangeCovering(spanned, offset)?.let { range ->
        return ReaderHitTarget(
            kind = ReaderHitKind.EXISTING_HIGHLIGHT,
            displayedStart = range.first,
            displayedEnd = range.last + 1,
            selectedText = extractReaderSelectionText(
                spanned,
                range.first,
                range.last + 1,
                textView.context,
            ),
            canSelect = true,
            canHighlight = true,
        )
    }

    val async = spanned.getSpans(offset, safeEnd, AsyncDrawableSpan::class.java)
        .firstOrNull { textView.isLatexImageSpanForHitTest(it) }
    val formula = spanned.getSpans(offset, safeEnd, ReplacementSpan::class.java)
        .firstOrNull { textView.isInlineFormulaSpanForHitTest(it) }
    if (async != null || formula != null) {
        val span = async ?: formula
        val range = spanRange(spanned, span, offset, safeEnd)
        return ReaderHitTarget(
            kind = ReaderHitKind.INLINE_FORMULA,
            displayedStart = range.first,
            displayedEnd = range.second,
            selectedText = extractReaderSelectionText(
                spanned,
                range.first,
                range.second,
                textView.context,
            ),
            span = span,
            canSelect = true,
            canHighlight = true,
        )
    }

    val heading = spanned.getSpans(offset, safeEnd, HeadingSpan::class.java).firstOrNull()
    if (heading != null) {
        val range = headingRange(spanned, heading, offset)
        return ReaderHitTarget(
            kind = ReaderHitKind.HEADING,
            displayedStart = range.first,
            displayedEnd = range.second,
            selectedText = extractReaderSelectionText(
                spanned,
                range.first,
                range.second,
                textView.context,
            ),
            span = heading,
            canSelect = true,
            canHighlight = true,
        )
    }

    if (!textView.canSelectAtOffsetForHitTest(offset)) {
        val image = spanned.getSpans(offset, safeEnd, ImageSpan::class.java).firstOrNull()
        if (image != null) {
            return ReaderHitTarget(kind = ReaderHitKind.IMAGE, span = image)
        }
        return ReaderHitTarget(ReaderHitKind.OUTSIDE)
    }

    return ReaderHitTarget(
        kind = ReaderHitKind.TEXT,
        displayedStart = offset,
        displayedEnd = safeEnd,
        selectedText = extractReaderSelectionText(spanned, offset, safeEnd, textView.context),
        canSelect = true,
        canHighlight = true,
    )
}

private fun spanRange(spanned: Spanned, span: Any?, fallbackStart: Int, fallbackEnd: Int): Pair<Int, Int> {
    if (span == null) return fallbackStart to fallbackEnd
    val start = spanned.getSpanStart(span).takeIf { it >= 0 } ?: fallbackStart
    val end = spanned.getSpanEnd(span).takeIf { it > start } ?: fallbackEnd
    return start to end.coerceAtMost(spanned.length)
}

private fun headingRange(spanned: Spanned, span: Any, offset: Int): Pair<Int, Int> {
    val start = spanned.getSpanStart(span).coerceIn(0, spanned.length)
    var end = spanned.getSpanEnd(span).coerceIn(start, spanned.length)
    // Markwon 版本之间 HeadingSpan 的结束边界可能包含段落换行；换行不是标题
    // 的可视字符，不能让标题末字长按时把选区延伸到下一段正文。
    while (end > start && (spanned[end - 1] == '\n' || spanned[end - 1] == '\r')) {
        end--
    }
    if (end > start) return start to end
    val safeOffset = offset.coerceIn(0, (spanned.length - 1).coerceAtLeast(0))
    return safeOffset to (safeOffset + 1).coerceAtMost(spanned.length)
}
