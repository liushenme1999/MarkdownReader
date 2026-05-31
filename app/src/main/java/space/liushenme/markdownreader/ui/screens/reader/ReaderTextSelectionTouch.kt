package space.liushenme.markdownreader.ui.screens.reader

import android.graphics.Rect
import android.text.Layout
import android.text.Selection
import android.text.Spannable
import android.widget.TextView

/**
 * 阅读器文本选区的触摸命中辅助：把选区文本矩形扩展为含左右留白、上下句柄带的区域。
 */
internal object ReaderTextSelectionTouch {

    /** 左右扩展，便于点选多行选区左右边缘。 */
    const val HORIZONTAL_SLOP_DP = 48f

    /** 选区上方/下方额外高度，覆盖系统 start/end 句柄（通常在文本块外）。 */
    const val HANDLE_BAND_DP = 56f

    fun horizontalSlopPx(density: Float): Int = (HORIZONTAL_SLOP_DP * density).toInt()

    fun handleBandPx(density: Float): Int = (HANDLE_BAND_DP * density).toInt()

    fun expandSelectionTouchRect(bounds: Rect, density: Float): Rect {
        val hSlop = horizontalSlopPx(density)
        val handleBand = handleBandPx(density)
        return Rect(bounds).apply {
            inset(-hSlop, -hSlop)
            top -= handleBand
            bottom += handleBand
        }
    }

    fun containsTouch(expanded: Rect, x: Float, y: Float): Boolean =
        expanded.contains(x.toInt(), y.toInt())

    /**
     * 将当前 Spannable 选区映射到 TextView 视口坐标（不含句柄，仅文本 bounds）。
     */
    fun selectionTextBoundsInView(
        layout: Layout,
        spannable: CharSequence,
        scrollX: Int,
        scrollY: Int,
        paddingLeft: Int,
        paddingTop: Int,
        outRect: Rect,
    ): Boolean {
        if (spannable !is Spannable) return false
        var start = Selection.getSelectionStart(spannable)
        var end = Selection.getSelectionEnd(spannable)
        if (start < 0 || end < 0 || start == end) return false
        if (start > end) {
            val swap = start
            start = end
            end = swap
        }
        val lineStart = layout.getLineForOffset(start)
        val lineEnd = layout.getLineForOffset(end)
        var left = layout.getPrimaryHorizontal(start)
        var right = layout.getPrimaryHorizontal(end)
        if (lineStart != lineEnd || right < left) {
            right = layout.getLineRight(lineEnd)
            if (lineStart == lineEnd) left = layout.getLineLeft(lineStart)
        }
        val top = layout.getLineTop(lineStart) + paddingTop - scrollY
        val bottom = layout.getLineBottom(lineEnd) + paddingTop - scrollY
        val leftPx = (left + paddingLeft).toInt()
        val rightPx = (right + paddingLeft).toInt().coerceAtLeast(leftPx + 1)
        outRect.set(leftPx, top, rightPx, bottom)
        return true
    }

    fun isTouchNearSelectionOnTextView(tv: TextView, x: Float, y: Float): Boolean {
        val layout = tv.layout ?: return false
        val text = tv.text ?: return false
        val bounds = Rect()
        if (!selectionTextBoundsInView(
                layout = layout,
                spannable = text,
                scrollX = tv.scrollX,
                scrollY = tv.scrollY,
                paddingLeft = tv.totalPaddingLeft,
                paddingTop = tv.totalPaddingTop,
                outRect = bounds,
            )
        ) {
            return false
        }
        val expanded = expandSelectionTouchRect(bounds, tv.resources.displayMetrics.density)
        return containsTouch(expanded, x, y)
    }
}
