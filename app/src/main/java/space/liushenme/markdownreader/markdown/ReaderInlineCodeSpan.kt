package space.liushenme.markdownreader.markdown

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.text.Spanned
import android.text.TextPaint
import android.text.style.LeadingMarginSpan
import android.text.style.LineBackgroundSpan
import android.text.style.MetricAffectingSpan
import android.text.style.ReplacementSpan
import io.noties.markwon.core.MarkwonTheme
import kotlin.math.max
import kotlin.math.min

/**
 * 行内 `` `code` ``：圆角底色，仅包裹代码本身（非整行）。
 *
 * 使用 [MetricAffectingSpan] + [LineBackgroundSpan] 而非 [android.text.style.ReplacementSpan]，
 * 以便长代码（如 JSON）可在空格处正常换行，避免窄屏裁切。
 *
 * 背景水平位置需加上列表等 [LeadingMarginSpan] 缩进，否则换行后底色会偏到文字左侧。
 * 前缀宽度必须按同行其它 [MetricAffectingSpan] / [ReplacementSpan] 分段测量，
 * 否则同一行第二段行内代码会用正文字体去量前面的代码，底色会盖到左侧文字上。
 */
internal class ReaderInlineCodeSpan(
    private val theme: MarkwonTheme,
    context: Context,
    paperColorArgb: Int = ReaderHighlightSurface.DEFAULT_PAPER_ARGB,
) : MetricAffectingSpan(), LineBackgroundSpan {

    private val density = context.resources.displayMetrics.density
    private val padH = ReaderLatexBlockStyle.inlineCodePadHPx(density)
    private val padV = ReaderLatexBlockStyle.inlineCodePadVPx(density)
    private val background: Drawable =
        ReaderLatexBlockStyle.inlineCodeBackground(context, paperColorArgb).mutate()

    override fun updateMeasureState(textPaint: TextPaint) {
        theme.applyCodeTextStyle(textPaint)
    }

    override fun updateDrawState(tp: TextPaint) {
        theme.applyCodeTextStyle(tp)
        tp.bgColor = Color.TRANSPARENT
    }

    override fun drawBackground(
        canvas: Canvas,
        paint: Paint,
        left: Int,
        right: Int,
        top: Int,
        baseline: Int,
        bottom: Int,
        text: CharSequence,
        start: Int,
        end: Int,
        lineNumber: Int,
    ) {
        val bounds = computeBackgroundBounds(paint, left, baseline, text, start, end) ?: return
        background.setBounds(bounds)
        background.draw(canvas)
    }

    /** 当前行内本段代码的底色矩形（含左右留白）。 */
    internal fun computeBackgroundBounds(
        paint: Paint,
        left: Int,
        baseline: Int,
        text: CharSequence,
        lineStart: Int,
        lineEnd: Int,
    ): Rect? {
        val spanned = text as? Spanned ?: return null
        val spanStart = spanned.getSpanStart(this)
        val spanEnd = spanned.getSpanEnd(this)
        if (spanStart < 0 || spanEnd < 0) return null

        var segStart = max(lineStart, spanStart)
        var segEnd = min(lineEnd, spanEnd)
        while (segEnd > segStart && text[segEnd - 1] == '\n') {
            segEnd--
        }
        if (segStart >= segEnd) return null

        val margin = leadingMargin(spanned, lineStart, lineEnd)
        val prefixWidth = measureStyledTextWidth(text, lineStart, segStart, paint)
        val originX = left + margin + prefixWidth
        val codePaint = TextPaint(paint).also { theme.applyCodeTextStyle(it) }
        val horizontal = inlineCodeBackgroundHorizontal(
            codePaint = codePaint,
            basePaint = paint,
            text = text,
            lineStart = lineStart,
            lineEnd = lineEnd,
            start = segStart,
            end = segEnd,
            originX = originX,
            edgePadPx = padH,
        )
        val fm = codePaint.fontMetricsInt
        val bgTop = baseline + fm.ascent - padV
        val bgBottom = baseline + fm.descent + padV
        return Rect(horizontal.first, bgTop, horizontal.second.coerceAtLeast(horizontal.first + 1), bgBottom)
    }

    internal fun horizontalPaddingPx(): Int = padH

    /** 列表 / 引用等 LeadingMargin，文字实际起点 = left + margin。 */
    private fun leadingMargin(text: Spanned, lineStart: Int, lineEnd: Int): Int {
        val spanEnd = max(lineEnd, lineStart + 1).coerceAtMost(text.length)
        if (lineStart >= spanEnd) return 0
        val spans = text.getSpans(lineStart, spanEnd, LeadingMarginSpan::class.java)
        if (spans.isEmpty()) return 0
        val isFirstLine = lineStart == 0 || text[lineStart - 1] == '\n'
        var margin = 0
        for (span in spans) {
            margin += span.getLeadingMargin(isFirstLine)
        }
        return margin
    }
}

/**
 * 行内代码底色水平范围：贴字形墨水区，再向左右各铺「与邻字间隙」的 1/3。
 * 排版间距不变；行首/行尾没有邻字时用 [edgePadPx]。
 */
internal fun inlineCodeBackgroundHorizontal(
    codePaint: Paint,
    basePaint: Paint,
    text: CharSequence,
    lineStart: Int,
    lineEnd: Int,
    start: Int,
    end: Int,
    originX: Float,
    edgePadPx: Int,
): Pair<Int, Int> {
    val advance = codePaint.measureText(text, start, end).coerceAtLeast(1f)
    val ink = Rect()
    val snippet = text.subSequence(start, end).toString()
    if (snippet.isNotEmpty()) {
        codePaint.getTextBounds(snippet, 0, snippet.length, ink)
    }
    val codeLeft = if (ink.width() > 0) originX + ink.left else originX
    val codeRight = if (ink.width() > 0) originX + ink.right else originX + advance
    val fraction = ReaderLatexBlockStyle.INLINE_CODE_SIDE_GAP_FRACTION

    val leftNeighborRight = leftNeighborInkRight(text, lineStart, start, originX, basePaint)
    val rightNeighborLeft = rightNeighborInkLeft(text, end, lineEnd, originX + advance, basePaint)
    val leftExtend = if (leftNeighborRight != null) {
        (codeLeft - leftNeighborRight).coerceAtLeast(0f) * fraction
    } else {
        edgePadPx.toFloat()
    }
    val rightExtend = if (rightNeighborLeft != null) {
        (rightNeighborLeft - codeRight).coerceAtLeast(0f) * fraction
    } else {
        edgePadPx.toFloat()
    }
    val bgLeft = (codeLeft - leftExtend).toInt()
    val bgRight = (codeRight + rightExtend).toInt()
    return bgLeft to bgRight.coerceAtLeast(bgLeft + 1)
}

private fun leftNeighborInkRight(
    text: CharSequence,
    lineStart: Int,
    segStart: Int,
    originX: Float,
    basePaint: Paint,
): Float? {
    var end = segStart
    var x = originX
    while (end > lineStart) {
        val start = previousCharStart(text, end)
        if (start < lineStart) break
        val advance = measureStyledTextWidth(text, start, end, basePaint)
        x -= advance
        glyphInkRange(text, start, end, x, basePaint)?.let { return it.second }
        end = start
    }
    return null
}

private fun rightNeighborInkLeft(
    text: CharSequence,
    segEnd: Int,
    lineEnd: Int,
    codeAdvanceEndX: Float,
    basePaint: Paint,
): Float? {
    var start = segEnd
    var x = codeAdvanceEndX
    val limit = min(lineEnd, text.length)
    while (start < limit && text[start] != '\n') {
        val end = nextCharEnd(text, start)
        glyphInkRange(text, start, end, x, basePaint)?.let { return it.first }
        x += measureStyledTextWidth(text, start, end, basePaint)
        start = end
    }
    return null
}

private fun glyphInkRange(
    text: CharSequence,
    start: Int,
    end: Int,
    originX: Float,
    basePaint: Paint,
): Pair<Float, Float>? {
    val paint = if (text is Spanned) {
        styledPaint(text, start, end, basePaint)
    } else {
        TextPaint(basePaint)
    }
    val snippet = text.subSequence(start, end).toString()
    if (snippet.isEmpty() || snippet.isBlank()) return null
    val ink = Rect()
    paint.getTextBounds(snippet, 0, snippet.length, ink)
    if (ink.width() > 0) {
        return (originX + ink.left) to (originX + ink.right)
    }
    val advance = paint.measureText(snippet).coerceAtLeast(1f)
    return originX to originX + advance
}

private fun previousCharStart(text: CharSequence, index: Int): Int {
    if (index <= 0) return -1
    val count = Character.charCount(Character.codePointBefore(text, index))
    return index - count
}

private fun nextCharEnd(text: CharSequence, index: Int): Int {
    if (index >= text.length) return text.length
    return index + Character.charCount(Character.codePointAt(text, index))
}

/**
 * 按实际生效的度量 span 分段测宽，与 StaticLayout 排版一致。
 */
internal fun measureStyledTextWidth(
    text: CharSequence,
    start: Int,
    end: Int,
    basePaint: Paint,
): Float {
    if (end <= start) return 0f
    val spanned = text as? Spanned ?: return basePaint.measureText(text, start, end)
    var width = 0f
    var index = start
    val limit = end.coerceAtMost(text.length)
    while (index < limit) {
        val replacement = replacementSpanAt(spanned, index, limit)
        if (replacement != null) {
            val spanEnd = min(spanned.getSpanEnd(replacement), limit)
            val paint = styledPaint(spanned, index, spanEnd, basePaint)
            width += replacement.getSize(paint, text, index, spanEnd, null).toFloat()
            index = spanEnd
            continue
        }
        val next = nextStyledBoundary(spanned, index, limit)
        val paint = styledPaint(spanned, index, next, basePaint)
        width += paint.measureText(text, index, next)
        index = next
    }
    return width
}

private fun replacementSpanAt(spanned: Spanned, index: Int, end: Int): ReplacementSpan? {
    if (index >= end) return null
    val probeEnd = (index + 1).coerceAtMost(spanned.length)
    if (index >= probeEnd) return null
    val spans = spanned.getSpans(index, probeEnd, ReplacementSpan::class.java)
    return spans.firstOrNull { spanned.getSpanStart(it) <= index && spanned.getSpanEnd(it) > index }
}

private fun nextStyledBoundary(spanned: Spanned, index: Int, end: Int): Int {
    val metricNext = spanned.nextSpanTransition(index, end, MetricAffectingSpan::class.java)
    val replacementNext = spanned.nextSpanTransition(index, end, ReplacementSpan::class.java)
    return min(metricNext, replacementNext)
}

private fun styledPaint(
    spanned: Spanned,
    start: Int,
    end: Int,
    base: Paint,
): TextPaint {
    val paint = TextPaint(base)
    val spans = spanned.getSpans(start, end, MetricAffectingSpan::class.java)
    for (span in spans) {
        if (spanned.getSpanStart(span) < end && spanned.getSpanEnd(span) > start) {
            span.updateMeasureState(paint)
        }
    }
    return paint
}
