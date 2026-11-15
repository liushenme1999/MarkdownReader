package space.liushenme.markdownreader.ui.screens.reader

import android.text.Layout
import android.text.Spanned
import android.text.style.ClickableSpan
import android.widget.TextView
import space.liushenme.markdownreader.markdown.ReaderTableRowSpan

/**
 * 表格单元格里的链接命中。
 *
 * 表格行是 [ReaderTableRowSpan]（ReplacementSpan）：外层 TextView 只有一个 `\u00A0`
 * 占位符，真正的单元格文字由 span 内部的 StaticLayout 各自绘制，链接 span 也只存在于
 * 对应单元格的 CharSequence 里。因此 [ReaderTextLinkTouch.findClickableSpanAt] 在外层
 * 命中不到任何链接——这正是「表格里的链接点了没反应」的原因。
 *
 * 这里把点击坐标换算进单元格内部排版：按 Markwon `TableRowSpan.draw` 的绘制原点
 * （行左边 + 列宽 * 列号 + 单元格内边距，行顶 + 内边距 + 垂直居中偏移）反推本地坐标，
 * 再在单元格 layout 里取字符偏移，只有落在链接文字的字形范围内才算命中。
 */
internal object ReaderTableLinkTouch {

    fun findClickableSpanAt(textView: TextView, x: Float, y: Float): ClickableSpan? {
        val spanned = textView.text as? Spanned ?: return null
        val layout = textView.layout ?: return null
        if (spanned.isEmpty()) return null

        val contentX = x + textView.scrollX - textView.compoundPaddingLeft
        val contentY = y + textView.scrollY - textView.extendedPaddingTop

        for (row in spanned.getSpans(0, spanned.length, ReaderTableRowSpan::class.java)) {
            val spanStart = spanned.getSpanStart(row)
            if (spanStart < 0 || row.columnCount <= 0) continue
            val line = layout.getLineForOffset(spanStart.coerceIn(0, layout.text.length))
            val top = layout.getLineTop(line).toFloat()
            val bottom = layout.getLineBottom(line).toFloat()
            // 表格行的行框比实际画出的单元格高（ReplacementSpan 的行高由内容高 + 上下内边距算出），
            // 单元格文字贴在该行框的顶部附近，点正文时容易落到上一行框里。
            // 这里给行框上下留一点容差，命中离点击点最近的表格行。
            val slack = ((bottom - top) * 0.35f).coerceIn(0f, MAX_ROW_SLACK_PX)
            if (contentY < top - slack || contentY > bottom + slack) continue
            val left = layout.getLineLeft(line)
            val right = layout.getLineRight(line)
            if (contentX < left || contentX > right) continue

            val cellWidth = row.cellWidth().takeIf { it > 0 }
                ?: ((right - left) / row.columnCount).toInt().coerceAtLeast(1)
            val column = ((contentX - left) / cellWidth).toInt().coerceIn(0, row.columnCount - 1)
            val cellLayout = row.cellLayout(column) ?: continue
            val cellText = cellLayout.text as? Spanned ?: continue

            val rowHeight = (0 until row.columnCount).mapNotNull(row::cellLayout)
                .maxOfOrNull { it.height }
                ?: cellLayout.height
            // 与 Markwon TableRowSpan.draw 一致：单元格内容顶部 = 行顶 + 内边距 + (行高 - 内容高) / 4
            val verticalOffset = (((bottom - top).toInt() - rowHeight) / 4).coerceAtLeast(0)
            val cellTop = top + row.cellPadding + verticalOffset
            val cellLeft = left + column * cellWidth + row.cellPadding
            val localX = contentX - cellLeft
            val localY = contentY - cellTop
            if (localX < -slack || localX > cellWidth + slack) continue
            if (cellLayout.height <= 0 || localY < -slack || localY > cellLayout.height + slack) continue
            // 单元格文字实际画得比行框略靠上（ReplacementSpan 行高按内容高 + 内边距算），
            // 落在容差内就夹到单元格内，避免「点到了文字却因为差几像素被丢掉」。
            val cellLine = cellLayout.getLineForVertical(localY.toInt().coerceIn(0, cellLayout.height - 1))
            val offset = cellLayout.getOffsetForHorizontal(cellLine, localX.coerceAtLeast(0f))
                .coerceIn(0, cellText.length - 1)
            val span = ReaderTextLinkTouch.spanAtOffset(cellText, offset) ?: continue
            if (isSpanAtCellHorizontal(cellLayout, cellText, span, cellLine, localX)) return span
        }
        return null
    }

    private const val MAX_ROW_SLACK_PX = 90f

    /** 点击点是否落在 [span] 在单元格该行显示出来的字形横向范围内。 */
    private fun isSpanAtCellHorizontal(
        layout: Layout,
        spannable: Spanned,
        span: ClickableSpan,
        line: Int,
        localX: Float,
    ): Boolean = ReaderTextLinkTouch.isSpanAtHorizontal(layout, spannable, span, line, localX)
}