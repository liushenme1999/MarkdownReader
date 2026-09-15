package space.liushenme.markdownreader.ui.screens.reader

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.text.Layout
import android.text.Spanned
import android.text.TextPaint
import android.text.style.CharacterStyle
import android.text.style.UpdateAppearance
import android.widget.TextView
import kotlin.math.max
import kotlin.math.min

/**
 * 阅读划线纯色背景标记（[CharacterStyle]，不参与段落度量）。
 *
 * 切勿使用 [android.text.style.LineBackgroundSpan]：它是 ParagraphStyle，挂到正文后会干扰
 * StaticLayout 段落测量，进页时易出现卡住/空白。实际绘制见 [drawReaderHighlightDecorations]。
 */
internal class HighlightBackgroundSpan(
    val backgroundColor: Int,
) : CharacterStyle(), UpdateAppearance {
    override fun updateDrawState(tp: TextPaint) {
        // 不走系统 bgColor；由 TextView.onDraw 自定义上下边距
    }
}

/**
 * 阅读划线直线/波浪线标记。绘制见 [drawReaderHighlightDecorations]。
 */
internal class HighlightUnderlineSpan(
    val color: Int,
    val wavy: Boolean,
) : CharacterStyle(), UpdateAppearance {
    override fun updateDrawState(tp: TextPaint) = Unit
}

private val highlightFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.FILL
}
private val highlightStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.STROKE
    strokeWidth = 3.5f
}
private val highlightWavePath = Path()

/**
 * 在正文 Layout 坐标系绘制划线装饰。
 * [underText] 为 true 时只画纯色底（应在 super.onDraw 之前）；为 false 时只画下划线（之后）。
 *
 * 变换须与 [TextView.onDraw] 一致：只平移到 padding，**不要再减 scroll**。
 * View 在 HW DisplayList / 软件绘制路径里已对 canvas 做过 `translate(-scrollX, -scrollY)`；
 * 若此处再减 scroll，划线会以约 2× 速度相对正文漂移（滑动后色块悬空）。
 */
internal fun drawReaderHighlightDecorations(
    textView: TextView,
    canvas: Canvas,
    underText: Boolean,
) {
    val layout = textView.layout ?: return
    val text = textView.text
    if (text !is Spanned || text.isEmpty()) return

    val padL = textView.compoundPaddingLeft
    val padT = textView.extendedPaddingTop
    val padR = textView.compoundPaddingRight
    val padB = textView.extendedPaddingBottom
    val viewW = textView.width
    val viewH = textView.height
    if (viewW - padR <= padL || viewH - padB <= padT) return

    canvas.save()
    // View.draw 已 translate(-scrollX,-scrollY)：可视区在 canvas 上是 scroll 偏移后的矩形。
    // 必须带上 scroll，否则滚动后正文划线被裁光；不裁则会画出 View 顶边盖住章节条/状态栏。
    val scrollX = textView.scrollX
    val scrollY = textView.scrollY
    canvas.clipRect(
        scrollX + padL,
        scrollY + padT,
        scrollX + viewW - padR,
        scrollY + viewH - padB,
    )
    // 对齐 TextView.onDraw：compoundPaddingLeft + extendedPaddingTop（gravity=TOP 时无额外 voffset）
    canvas.translate(padL.toFloat(), padT.toFloat())

    if (underText) {
        text.getSpans(0, text.length, HighlightBackgroundSpan::class.java).forEach { span ->
            val start = text.getSpanStart(span)
            val end = text.getSpanEnd(span)
            if (start < 0 || end <= start) return@forEach
            drawHighlightBackgroundRange(canvas, layout, start, end, span.backgroundColor)
        }
    } else {
        val density = textView.resources.displayMetrics.density.coerceAtLeast(1f)
        text.getSpans(0, text.length, HighlightUnderlineSpan::class.java).forEach { span ->
            val start = text.getSpanStart(span)
            val end = text.getSpanEnd(span)
            if (start < 0 || end <= start) return@forEach
            drawHighlightUnderlineRange(
                canvas = canvas,
                layout = layout,
                start = start,
                end = end,
                color = span.color,
                wavy = span.wavy,
                density = density,
            )
        }
    }
    canvas.restore()
}

private fun drawHighlightBackgroundRange(
    canvas: Canvas,
    layout: Layout,
    start: Int,
    end: Int,
    color: Int,
) {
    val last = (end - 1).coerceAtLeast(start)
    val firstLine = layout.getLineForOffset(start)
    val lastLine = layout.getLineForOffset(last)
    highlightFillPaint.color = color
    for (line in firstLine..lastLine) {
        val lineStart = layout.getLineStart(line)
        val lineEnd = layout.getLineEnd(line)
        val drawStart = max(start, lineStart)
        val drawEnd = min(end, lineEnd)
        if (drawStart >= drawEnd) continue
        // 跳过行尾仅换行
        if (drawEnd == lineEnd && drawStart == drawEnd - 1 && textIsNewline(layout, drawStart)) continue

        val (left, right) = horizontalRangeOnLine(layout, line, drawStart, drawEnd) ?: continue

        val top = layout.getLineTop(line)
        val baseline = layout.getLineBaseline(line)
        val (yTop, yBottom) = resolveHighlightVerticalBounds(top = top, baseline = baseline)
        canvas.drawRect(left, yTop, right, yBottom, highlightFillPaint)
    }
}

private fun drawHighlightUnderlineRange(
    canvas: Canvas,
    layout: Layout,
    start: Int,
    end: Int,
    color: Int,
    wavy: Boolean,
    density: Float,
) {
    val last = (end - 1).coerceAtLeast(start)
    val firstLine = layout.getLineForOffset(start)
    val lastLine = layout.getLineForOffset(last)
    highlightStrokePaint.color = color
    // 相对 baseline 下移，避免贴住汉字底部；波浪上拱再额外让出振幅。
    val gapBelowBaseline = (5.5f * density).coerceIn(8f, 22f)
    val waveAmp = (2.2f * density).coerceIn(3f, 8f)
    val waveStep = (5.5f * density).coerceIn(8f, 16f)
    highlightStrokePaint.strokeWidth = (1.15f * density).coerceIn(2.5f, 4.5f)
    for (line in firstLine..lastLine) {
        val lineStart = layout.getLineStart(line)
        val lineEnd = layout.getLineEnd(line)
        val drawStart = max(start, lineStart)
        val drawEnd = min(end, lineEnd)
        if (drawStart >= drawEnd) continue
        if (drawEnd == lineEnd && drawStart == drawEnd - 1 && textIsNewline(layout, drawStart)) continue

        val (left, right) = horizontalRangeOnLine(layout, line, drawStart, drawEnd) ?: continue

        val baseline = layout.getLineBaseline(line)
        // 直线：baseline 下方留空隙；波浪：中线再下移 waveAmp，使波峰仍在字下。
        val y = baseline + gapBelowBaseline + if (wavy) waveAmp else 0f
        if (wavy) {
            highlightWavePath.reset()
            var x = left
            var up = true
            highlightWavePath.moveTo(x, y)
            while (x < right) {
                val next = (x + waveStep).coerceAtMost(right)
                val mid = (x + next) / 2f
                val cy = if (up) y - waveAmp else y + waveAmp
                highlightWavePath.quadTo(mid, cy, next, y)
                up = !up
                x = next
            }
            canvas.drawPath(highlightWavePath, highlightStrokePaint)
        } else {
            canvas.drawLine(left, y, right, y, highlightStrokePaint)
        }
    }
}

/**
 * 行内 `[drawStart, drawEnd)` 的水平范围，空区间返回 null。
 *
 * 终点落在行尾时不能用 [Layout.getPrimaryHorizontal]：软换行处它给的是下一行行首的 x
 * （≈ 行左边界），跨行划线会被画成「行首 → 选区起点」，中间整行还会因宽度为 0 被跳过。
 */
internal fun horizontalRangeOnLine(
    layout: Layout,
    line: Int,
    drawStart: Int,
    drawEnd: Int,
): Pair<Float, Float>? {
    val xStart = layout.getPrimaryHorizontal(drawStart)
    val xEnd = if (drawEnd >= layout.getLineEnd(line)) {
        layout.getLineRight(line)
    } else {
        layout.getPrimaryHorizontal(drawEnd)
    }
    val left = min(xStart, xEnd)
    val right = max(xStart, xEnd)
    return if (right <= left + 1f) null else left to right
}

private fun textIsNewline(layout: Layout, offset: Int): Boolean {
    val text = layout.text
    return offset in text.indices && text[offset] == '\n'
}

/**
 * 纯色划线上下边界：用行 top/baseline（含标题放大），压缩 descent，相对视觉中线对称。
 */
private fun resolveHighlightVerticalBounds(
    top: Int,
    baseline: Int,
): Pair<Float, Float> {
    val ascentAbs = (baseline - top).toFloat().coerceAtLeast(1f)
    val usableDescent = ascentAbs * 0.22f
    val glyphTop = baseline - ascentAbs
    val glyphBottom = baseline + usableDescent
    val mid = (glyphTop + glyphBottom) / 2f
    val half = (glyphBottom - glyphTop) / 2f
    val pad = (ascentAbs * 0.08f).coerceIn(2f, 14f)
    return (mid - half - pad) to (mid + half + pad)
}
