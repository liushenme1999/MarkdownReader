package space.liushenme.markdownreader.ui.screens.reader

import android.graphics.Rect
import android.graphics.RectF
import android.text.Layout
import android.text.Spanned
import android.widget.TextView
import space.liushenme.markdownreader.markdown.ReaderTableRowSpan

internal data class ReaderTableCellHit(
    val span: ReaderTableRowSpan,
    val row: Int,
    val column: Int,
)

internal data class ReaderTableSelection(
    val tableId: Int,
    val startRow: Int,
    val endRow: Int,
    val startColumn: Int,
    val endColumn: Int,
) {
    fun includes(row: Int, column: Int): Boolean =
        row in startRow..endRow && column in startColumn..endColumn

    fun extendTo(hit: ReaderTableCellHit): ReaderTableSelection? =
        if (hit.span.tableId != tableId) {
            null
        } else {
            copy(
                startRow = minOf(startRow, hit.row),
                endRow = maxOf(endRow, hit.row),
                startColumn = minOf(startColumn, hit.column),
                endColumn = maxOf(endColumn, hit.column),
            )
        }

    companion object {
        fun between(anchor: ReaderTableCellHit, current: ReaderTableCellHit): ReaderTableSelection? {
            if (anchor.span.tableId != current.span.tableId) return null
            return ReaderTableSelection(
                tableId = anchor.span.tableId,
                startRow = minOf(anchor.row, current.row),
                endRow = maxOf(anchor.row, current.row),
                startColumn = minOf(anchor.column, current.column),
                endColumn = maxOf(anchor.column, current.column),
            )
        }
    }
}

internal fun tableCellHitAt(textView: TextView, x: Float, y: Float): ReaderTableCellHit? {
    val spanned = textView.text as? Spanned ?: return null
    val layout = textView.layout ?: return null
    val spans = spanned.getSpans(0, spanned.length, ReaderTableRowSpan::class.java)
    if (spans.isEmpty()) return null

    val contentX = x + textView.scrollX - textView.compoundPaddingLeft
    val contentY = y + textView.scrollY - textView.extendedPaddingTop
    for (span in spans) {
        val spanStart = spanned.getSpanStart(span)
        if (spanStart < 0 || span.columnCount <= 0) continue
        val line = layout.getLineForOffset(spanStart.coerceIn(0, layout.text.length))
        val top = layout.getLineTop(line).toFloat()
        val bottom = layout.getLineBottom(line).toFloat()
        if (contentY < top || contentY > bottom) continue

        val left = layout.getLineLeft(line)
        val right = layout.getLineRight(line)
        if (contentX < left || contentX > right) continue
        val cellWidth = span.cellWidth().takeIf { it > 0 }
            ?: ((right - left) / span.columnCount).toInt().coerceAtLeast(1)
        val column = ((contentX - left) / cellWidth).toInt()
            .coerceIn(0, span.columnCount - 1)
        return ReaderTableCellHit(span, span.rowIndex, column)
    }
    return null
}

internal fun tableSelectionText(
    textView: TextView,
    selection: ReaderTableSelection,
): String {
    val spanned = textView.text as? Spanned ?: return ""
    val rows = spanned.getSpans(0, spanned.length, ReaderTableRowSpan::class.java)
        .filter { it.tableId == selection.tableId }
        .associateBy { it.rowIndex }
    return buildString {
        for (row in selection.startRow..selection.endRow) {
            if (row > selection.startRow) append('\n')
            for (column in selection.startColumn..selection.endColumn) {
                if (column > selection.startColumn) append('\t')
                val cell = rows[row]?.cellText(column)
                val raw = cell?.let { extractReaderSelectionText(it, 0, it.length, textView.context) }
                    .orEmpty()
                append(raw.replace('\t', ' ').replace('\r', ' ').replace('\n', ' ').trim())
            }
        }
    }
}

internal fun tableSelectionBoundsInView(
    textView: TextView,
    selection: ReaderTableSelection,
): Rect? {
    val spanned = textView.text as? Spanned ?: return null
    val layout = textView.layout ?: return null
    val spans = spanned.getSpans(0, spanned.length, ReaderTableRowSpan::class.java)
        .filter { it.tableId == selection.tableId && it.rowIndex in selection.startRow..selection.endRow }
    if (spans.isEmpty()) return null

    var union: RectF? = null
    for (span in spans) {
        for (column in selection.startColumn..selection.endColumn) {
            val rect = tableCellBoundsInView(textView, spanned, layout, span, column) ?: continue
            union = union?.apply { union(rect) } ?: RectF(rect)
        }
    }
    return union?.let {
        Rect(it.left.toInt(), it.top.toInt(), it.right.toInt(), it.bottom.toInt())
    }
}

internal fun tableCellBoundsInContent(
    spanned: Spanned,
    layout: Layout,
    span: ReaderTableRowSpan,
    column: Int,
): RectF? {
    if (column !in 0 until span.columnCount) return null
    val spanStart = spanned.getSpanStart(span)
    if (spanStart < 0) return null
    val line = layout.getLineForOffset(spanStart.coerceIn(0, layout.text.length))
    val left = layout.getLineLeft(line)
    val right = layout.getLineRight(line)
    val cellWidth = span.cellWidth().takeIf { it > 0 }
        ?: ((right - left) / span.columnCount).toInt().coerceAtLeast(1)
    return RectF(
        left + column * cellWidth,
        layout.getLineTop(line).toFloat(),
        left + (column + 1) * cellWidth,
        layout.getLineBottom(line).toFloat(),
    )
}

internal fun tableCellBoundsInView(
    textView: TextView,
    spanned: Spanned,
    layout: Layout,
    span: ReaderTableRowSpan,
    column: Int,
): RectF? {
    val content = tableCellBoundsInContent(spanned, layout, span, column) ?: return null
    return RectF(
        content.left + textView.compoundPaddingLeft - textView.scrollX,
        content.top + textView.extendedPaddingTop - textView.scrollY,
        content.right + textView.compoundPaddingLeft - textView.scrollX,
        content.bottom + textView.extendedPaddingTop - textView.scrollY,
    )
}
