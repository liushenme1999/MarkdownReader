package space.liushenme.markdownreader.markdown

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.text.Spanned
import android.text.TextPaint
import android.text.style.LeadingMarginSpan
import android.text.style.LineBackgroundSpan
import android.text.style.MetricAffectingSpan
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
 */
internal class ReaderInlineCodeSpan(
    private val theme: MarkwonTheme,
    context: Context,
) : MetricAffectingSpan(), LineBackgroundSpan {

    private val density = context.resources.displayMetrics.density
    private val padH = (3f * density + 0.5f).toInt()
    private val padV = (2f * density + 0.5f).toInt()
    private val background: Drawable =
        ReaderLatexBlockStyle.inlineCodeBackground(context).mutate()

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
        val spanned = text as? Spanned ?: return
        val spanStart = spanned.getSpanStart(this)
        val spanEnd = spanned.getSpanEnd(this)
        if (spanStart < 0 || spanEnd < 0) return

        var segStart = max(start, spanStart)
        var segEnd = min(end, spanEnd)
        while (segEnd > segStart && text[segEnd - 1] == '\n') {
            segEnd--
        }
        if (segStart >= segEnd) return

        val codePaint = TextPaint(paint).also { theme.applyCodeTextStyle(it) }
        val margin = leadingMargin(spanned, start, end)
        val prefixWidth = measurePrefixWidth(paint, codePaint, text, start, segStart, spanStart)
        val segWidth = codePaint.measureText(text, segStart, segEnd)

        val bgLeft = (left + margin + prefixWidth).toInt() - padH
        val bgRight = (left + margin + prefixWidth + segWidth).toInt() + padH
        val fm = codePaint.fontMetricsInt
        val bgTop = baseline + fm.ascent - padV
        val bgBottom = baseline + fm.descent + padV

        background.setBounds(bgLeft, bgTop, bgRight.coerceAtLeast(bgLeft + 1), bgBottom)
        background.draw(canvas)
    }

    /** 行首到本段代码前的文字宽度（不含 LeadingMargin）。 */
    private fun measurePrefixWidth(
        basePaint: Paint,
        codePaint: TextPaint,
        text: CharSequence,
        lineStart: Int,
        segStart: Int,
        spanStart: Int,
    ): Float {
        if (segStart <= lineStart) return 0f
        return if (segStart <= spanStart) {
            basePaint.measureText(text, lineStart, segStart)
        } else {
            val beforeCode = if (spanStart > lineStart) {
                basePaint.measureText(text, lineStart, spanStart)
            } else {
                0f
            }
            val inCode = codePaint.measureText(text, spanStart, segStart)
            beforeCode + inCode
        }
    }

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
