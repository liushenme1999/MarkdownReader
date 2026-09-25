package space.liushenme.markdownreader.ui.screens.reader

import android.graphics.Rect
import android.text.Layout
import android.text.Selection
import android.text.Spannable
import android.widget.TextView
import java.text.BreakIterator
import kotlin.math.roundToInt

/**
 * 阅读器文本选区的触摸命中辅助：把选区文本矩形扩展为含左右留白、上下句柄带的区域；
 * 长按划词按按下点就近字符命中，避免落到换行 offset。
 */
internal object ReaderTextSelectionTouch {

    enum class SelectionHandle {
        START,
        END,
    }

    /** 左右扩展，便于点选多行选区左右边缘。 */
    const val HORIZONTAL_SLOP_DP = 48f

    /** 选区上方/下方额外高度，覆盖系统 start/end 句柄（通常在文本块外）。 */
    const val HANDLE_BAND_DP = 56f

    /** 正文选中区域只做小幅容错；大触摸带只保留在首尾句柄附近。 */
    private const val TEXT_HIT_SLOP_DP = 8f
    internal const val HANDLE_TOUCH_RADIUS_DP = 28f

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
        val text = tv.text as? Spannable ?: return false
        var start = Selection.getSelectionStart(text)
        var end = Selection.getSelectionEnd(text)
        if (start < 0 || end < 0 || start == end) return false
        if (start > end) {
            val swap = start
            start = end
            end = swap
        }
        val density = tv.resources.displayMetrics.density
        val textSlop = TEXT_HIT_SLOP_DP * density
        val startLine = layout.getLineForOffset(start)
        // Selection 的 end 是 exclusive；若它正好等于下一行行首，真正末字仍在上一行。
        val endLine = layout.getLineForOffset((end - 1).coerceAtLeast(start))

        for (line in startLine..endLine) {
            val lineStart = layout.getLineStart(line)
            val lineEnd = layout.getLineEnd(line).coerceAtMost(text.length)
            val segmentStart = if (line == startLine) start else lineStart
            val segmentEnd = if (line == endLine) end else lineEnd
            val leftAtStart = layout.getPrimaryHorizontal(segmentStart.coerceIn(lineStart, lineEnd))
            val rightAtEnd = if (segmentEnd >= lineEnd) {
                layout.getLineRight(line)
            } else {
                layout.getPrimaryHorizontal(segmentEnd.coerceIn(lineStart, lineEnd))
            }
            val left = minOf(leftAtStart, rightAtEnd) + tv.compoundPaddingLeft - tv.scrollX
            val right = maxOf(leftAtStart, rightAtEnd) + tv.compoundPaddingLeft - tv.scrollX
            val top = layout.getLineTop(line) + tv.extendedPaddingTop - tv.scrollY
            val bottom = layout.getLineBottom(line) + tv.extendedPaddingTop - tv.scrollY
            if (x >= left - textSlop && x <= right + textSlop &&
                y >= top - textSlop && y <= bottom + textSlop
            ) {
                return true
            }
        }

        return selectionHandleAtOnTextView(tv, x, y) != null
    }

    fun selectionHandleAtOnTextView(
        tv: TextView,
        x: Float,
        y: Float,
    ): SelectionHandle? {
        val text = tv.text as? Spannable ?: return null
        var start = Selection.getSelectionStart(text)
        var end = Selection.getSelectionEnd(text)
        if (start < 0 || end < 0 || start == end) return null
        if (start > end) {
            val swap = start
            start = end
            end = swap
        }
        val startPoint = selectionHandlePositionOnTextView(tv, start, isEnd = false) ?: return null
        val endPoint = selectionHandlePositionOnTextView(tv, end, isEnd = true) ?: return null
        val radius = HANDLE_TOUCH_RADIUS_DP * tv.resources.displayMetrics.density
        val startDistance = squaredDistance(x, y, startPoint.first, startPoint.second)
        val endDistance = squaredDistance(x, y, endPoint.first, endPoint.second)
        val maxDistance = radius * radius
        val startHit = startDistance <= maxDistance
        val endHit = endDistance <= maxDistance
        return when {
            startHit && endHit ->
                if (startDistance <= endDistance) SelectionHandle.START else SelectionHandle.END
            startHit -> SelectionHandle.START
            endHit -> SelectionHandle.END
            else -> null
        }
    }

    fun selectionHandlePositionOnTextView(
        tv: TextView,
        offset: Int,
        isEnd: Boolean,
    ): Pair<Float, Float>? {
        val layout = tv.layout ?: return null
        val textLength = layout.text.length
        if (textLength <= 0) return null
        val lineOffset = if (isEnd) (offset - 1).coerceAtLeast(0) else offset
        val line = layout.getLineForOffset(lineOffset.coerceIn(0, textLength - 1))
        val lineStart = layout.getLineStart(line)
        val lineEnd = layout.getLineEnd(line).coerceAtMost(textLength)
        val horizontal = if (isEnd && offset >= lineEnd) {
            layout.getLineRight(line)
        } else {
            layout.getPrimaryHorizontal(offset.coerceIn(lineStart, lineEnd))
        }
        val endpointX = horizontal + tv.compoundPaddingLeft - tv.scrollX
        val endpointY = glyphBottom(layout, line) + tv.extendedPaddingTop - tv.scrollY
        return endpointX to endpointY
    }

    /**
     * 字形底部。阅读器行距倍数把留白全部加进了 getLineDescent / getLineBottom，照它放句柄会压到
     * 下一行文字上；而用 getSelectionPath 反推，在软换行处末端 offset 属于下一行，同样会掉下去。
     */
    fun glyphBottom(layout: Layout, line: Int): Float {
        val safeLine = line.coerceIn(0, (layout.lineCount - 1).coerceAtLeast(0))
        val fontBottom = layout.getLineBaseline(safeLine) + layout.paint.fontMetricsInt.descent
        return minOf(fontBottom, layout.getLineBottom(safeLine)).toFloat()
    }

    private fun squaredDistance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x1 - x2
        val dy = y1 - y2
        return dx * dx + dy * dy
    }

    /**
     * 将 TextView 视口坐标换算为就近可见字符 offset，避免行底/行右缘落到换行处。
     */
    fun offsetNearestCharOnTextView(tv: TextView, x: Float, y: Float): Int? {
        val layout = tv.layout ?: return null
        val len = tv.text?.length ?: 0
        if (len == 0) return null
        val contentX = x + tv.scrollX - tv.compoundPaddingLeft
        val contentY = y + tv.scrollY - tv.extendedPaddingTop
        return offsetNearestChar(layout, contentX, contentY).coerceIn(0, len - 1)
    }

    /**
     * [contentX]/[contentY] 为 StaticLayout 内容坐标（已扣 padding、含 scrollY）。
     */
    fun offsetNearestChar(layout: Layout, contentX: Float, contentY: Float): Int {
        val textLen = layout.text.length
        if (layout.lineCount <= 0 || textLen <= 0) return 0
        val maxLine = layout.lineCount - 1
        var line = layout.getLineForVertical(contentY.roundToInt().coerceAtLeast(0))
            .coerceIn(0, maxLine)
        // 行底与下一行行顶重合时 getLineForVertical 落到下一行；Y 仍不超过上一行行底则拉回。
        if (line > 0 && contentY <= layout.getLineBottom(line - 1)) {
            line -= 1
        }
        val lineLeft = layout.getLineLeft(line)
        val lineRight = layout.getLineRight(line)
        val hx = if (lineRight <= lineLeft) {
            lineLeft
        } else {
            contentX.coerceIn(lineLeft, lineRight - 0.1f)
        }
        val lineStart = layout.getLineStart(line)
        val lastOnLine = lastVisibleCharOffset(layout, line)
        var offset = layout.getOffsetForHorizontal(line, hx)
            .coerceIn(lineStart, lastOnLine)
        // getOffsetForHorizontal 给的是离触点最近的「光标位」：按在某个字的右半边时，
        // 它会跳到下一个字。划词要的是手指正下方那个字，所以边界落在触点右侧时回退一格。
        if (offset > lineStart &&
            layout.getParagraphDirection(line) == Layout.DIR_LEFT_TO_RIGHT &&
            layout.getPrimaryHorizontal(offset) > hx
        ) {
            offset = precedingCharOffset(layout.text, offset, lineStart)
        }
        return offset.coerceIn(0, textLen - 1)
    }

    private fun precedingCharOffset(text: CharSequence, offset: Int, lowerBound: Int): Int {
        if (offset - 1 <= lowerBound) return lowerBound
        val previous = offset - 1
        return if (Character.isLowSurrogate(text[previous]) &&
            Character.isHighSurrogate(text[previous - 1])
        ) {
            (previous - 1).coerceAtLeast(lowerBound)
        } else {
            previous
        }
    }

    /** 本行最后一个可见字符 offset（不含换行；不含下一行行首的 wrap offset）。 */
    fun lastVisibleCharOffset(layout: Layout, line: Int): Int {
        val text = layout.text
        if (text.isEmpty()) return 0
        val lineStart = layout.getLineStart(line)
        var end = if (line + 1 < layout.lineCount) {
            layout.getLineStart(line + 1)
        } else {
            layout.getLineEnd(line).coerceAtMost(text.length)
        }
        while (end > lineStart && (text[end - 1] == '\n' || text[end - 1] == '\r')) {
            end--
        }
        if (end <= lineStart) {
            return lineStart.coerceIn(0, text.length - 1)
        }
        return (end - 1).coerceIn(0, text.length - 1)
    }

    /**
     * 落点附近的选区：汉字/假名等按字（grapheme），拉丁词在同一视觉行内按词。
     * 不跨越视觉行；排除点不落在换行 offset 上，避免行末+下一行行首双高亮。
     */
    fun rangeAround(
        text: CharSequence,
        layout: Layout,
        offset: Int,
        selectLatinWord: Boolean = true,
    ): IntRange {
        val len = text.length
        if (len <= 0) return 0 until 0
        val o = offset.coerceIn(0, len - 1)
        val line = layout.getLineForOffset(o).coerceIn(0, (layout.lineCount - 1).coerceAtLeast(0))
        val lineStart = layout.getLineStart(line)
        val lineLimit = visibleLineLimit(text, layout, line)
        if (lineLimit <= lineStart) {
            val end = (lineStart + 1).coerceAtMost(len)
            return if (end > lineStart) lineStart until end else o until (o + 1).coerceAtMost(len)
        }
        val grapheme = graphemeRangeAt(text, o, lineStart, lineLimit)
        if (grapheme.isEmpty()) return o until (o + 1).coerceAtMost(len)
        val cp = Character.codePointAt(text, grapheme.first)
        if (!selectLatinWord || isCjkScript(cp)) return grapheme

        val iterator = BreakIterator.getWordInstance()
        iterator.setText(text.toString())
        var start = iterator.preceding(o + 1)
        if (start == BreakIterator.DONE) start = lineStart
        var end = iterator.following(o)
        if (end == BreakIterator.DONE) end = lineLimit
        if (start >= end || o < start || o >= end) return grapheme
        start = start.coerceIn(lineStart, lineLimit - 1)
        end = end.coerceIn(start + 1, lineLimit)
        if (end <= start) return grapheme
        if (containsNonLatin(text, start, end)) return grapheme
        return start until end
    }

    /**
     * 从锚点字到当前字的连续选区：两端各自按 [rangeAround] 取单元，再取闭包。
     * 中间可跨行，但起止落在真实字符上，而不是单独的换行 offset。
     *
     * 长按扩选可 [selectLatinWord] = true 按词走；拖动句柄应收成字符级，传入 false。
     */
    fun rangeBetween(
        text: CharSequence,
        layout: Layout,
        offsetA: Int,
        offsetB: Int,
        selectLatinWord: Boolean = true,
    ): IntRange {
        val a = rangeAround(text, layout, offsetA, selectLatinWord)
        val b = rangeAround(text, layout, offsetB, selectLatinWord)
        if (a.isEmpty()) return b
        if (b.isEmpty()) return a
        val start = minOf(a.first, b.first)
        val endExclusive = maxOf(a.last, b.last) + 1
        if (endExclusive <= start) return a
        return start until endExclusive
    }

    private fun visibleLineLimit(text: CharSequence, layout: Layout, line: Int): Int {
        val lineStart = layout.getLineStart(line)
        var lineLimit = if (line + 1 < layout.lineCount) {
            layout.getLineStart(line + 1)
        } else {
            text.length
        }
        while (lineLimit > lineStart && (text[lineLimit - 1] == '\n' || text[lineLimit - 1] == '\r')) {
            lineLimit--
        }
        return lineLimit
    }

    private fun graphemeRangeAt(
        text: CharSequence,
        offset: Int,
        lineStart: Int,
        lineLimit: Int,
    ): IntRange {
        val o = offset.coerceIn(lineStart, (lineLimit - 1).coerceAtLeast(lineStart))
        val end = (o + Character.charCount(Character.codePointAt(text, o))).coerceAtMost(lineLimit)
        return if (end > o) o until end else o until (o + 1).coerceAtMost(text.length)
    }

    private fun containsNonLatin(text: CharSequence, start: Int, end: Int): Boolean {
        var i = start
        while (i < end) {
            val cp = Character.codePointAt(text, i)
            if (!isLatinWordCodePoint(cp)) return true
            i += Character.charCount(cp)
        }
        return false
    }

    private fun isLatinWordCodePoint(cp: Int): Boolean {
        if (cp == '_'.code || cp == '\''.code || cp == '-'.code) return true
        return cp <= 0x24F && Character.isLetterOrDigit(cp)
    }

    private fun isCjkScript(cp: Int): Boolean = when (Character.UnicodeScript.of(cp)) {
        Character.UnicodeScript.HAN,
        Character.UnicodeScript.HIRAGANA,
        Character.UnicodeScript.KATAKANA,
        Character.UnicodeScript.HANGUL,
        Character.UnicodeScript.BOPOMOFO,
        -> true
        else -> false
    }
}
