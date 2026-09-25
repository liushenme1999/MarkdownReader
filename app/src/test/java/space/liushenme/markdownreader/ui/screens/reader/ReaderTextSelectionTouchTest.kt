package space.liushenme.markdownreader.ui.screens.reader

import android.graphics.Rect
import android.text.Layout
import android.text.Selection
import android.text.Spannable
import android.text.SpannableString
import android.view.View
import android.widget.TextView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ReaderTextSelectionTouchTest {

    @Test
    fun expandSelectionTouchRect_includesHorizontalSlopAndHandleBands() {
        val density = 2f
        val bounds = Rect(100, 200, 300, 240)
        val expanded = ReaderTextSelectionTouch.expandSelectionTouchRect(bounds, density)
        assertTrue(expanded.left < bounds.left)
        assertTrue(expanded.right > bounds.right)
        assertTrue(expanded.top < bounds.top - ReaderTextSelectionTouch.handleBandPx(density))
        assertTrue(expanded.bottom > bounds.bottom + ReaderTextSelectionTouch.handleBandPx(density))
    }

    @Test
    fun containsTouch_pointInsideExpandedRect_returnsTrue() {
        val expanded = Rect(0, 0, 200, 200)
        assertTrue(ReaderTextSelectionTouch.containsTouch(expanded, 50f, 50f))
    }

    @Test
    fun containsTouch_pointFarOutside_returnsFalse() {
        val expanded = Rect(0, 0, 100, 100)
        assertFalse(ReaderTextSelectionTouch.containsTouch(expanded, 500f, 500f))
    }

    @Test
    fun expandSelectionTouchRect_handleBandCoversTypicalHandleOffset() {
        val density = 3f
        val lineHeight = 48
        val bounds = Rect(20, 100, 280, 100 + lineHeight)
        val expanded = ReaderTextSelectionTouch.expandSelectionTouchRect(bounds, density)
        val handleBand = ReaderTextSelectionTouch.handleBandPx(density)
        // 模拟 start 句柄在选区顶线上方、end 句柄在底线下方的触点
        assertTrue(ReaderTextSelectionTouch.containsTouch(expanded, 30f, bounds.top - handleBand + 4f))
        assertTrue(ReaderTextSelectionTouch.containsTouch(expanded, 250f, bounds.bottom + handleBand - 4f))
    }

    @Test
    fun offsetNearestChar_yJustBelowLineBottom_staysOnSameLine() {
        val layout = wrappingLayout()
        assertTrue("need at least two lines", layout.lineCount >= 2)
        val lineRight = layout.getLineRight(0)
        val lineBottom = layout.getLineBottom(0).toFloat()
        val offset = ReaderTextSelectionTouch.offsetNearestChar(
            layout,
            lineRight,
            lineBottom,
        )
        assertEquals(0, layout.getLineForOffset(offset))
        assertTrue(offset < layout.getLineStart(1))
        assertTrue(offset <= ReaderTextSelectionTouch.lastVisibleCharOffset(layout, 0))
    }

    @Test
    fun offsetNearestChar_xAtLineRight_doesNotReturnNextLineStart() {
        val layout = wrappingLayout()
        assertTrue(layout.lineCount >= 2)
        val line = 0
        val y = (layout.getLineTop(line) + layout.getLineBottom(line)) / 2f
        val offset = ReaderTextSelectionTouch.offsetNearestChar(
            layout,
            layout.getLineRight(line),
            y,
        )
        assertEquals(0, layout.getLineForOffset(offset))
        assertTrue(offset < layout.getLineStart(1))
    }

    @Test
    fun rangeAround_doesNotSpanVisualLines() {
        val tv = wrappingTextView()
        val layout = tv.layout!!
        assertTrue(layout.lineCount >= 2)
        val lastOnFirst = ReaderTextSelectionTouch.lastVisibleCharOffset(layout, 0)
        val range = ReaderTextSelectionTouch.rangeAround(tv.text, layout, lastOnFirst)
        assertFalse(range.isEmpty())
        assertEquals(0, layout.getLineForOffset(range.first))
        assertEquals(0, layout.getLineForOffset(range.last))
        val wrapOffset = layout.getLineStart(1)
        val nextRange = ReaderTextSelectionTouch.rangeAround(tv.text, layout, wrapOffset)
        assertFalse(nextRange.isEmpty())
        assertEquals(1, layout.getLineForOffset(nextRange.first))
        assertEquals(1, layout.getLineForOffset(nextRange.last))
    }

    @Test
    fun rangeAround_cjkSoftWrap_selectsSingleCharNotToWrap() {
        val context = RuntimeEnvironment.getApplication()
        val tv = TextView(context).apply {
            text = "甲".repeat(80)
            textSize = 28f
            setPadding(8, 8, 8, 8)
            measure(
                View.MeasureSpec.makeMeasureSpec(100, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1200, View.MeasureSpec.EXACTLY),
            )
            layout(0, 0, 100, 1200)
        }
        val layout = tv.layout ?: error("layout missing")
        val lineStart = layout.getLineStart(0)
        val lastOnFirst = ReaderTextSelectionTouch.lastVisibleCharOffset(layout, 0)
        val mid = (lineStart + lastOnFirst) / 2
        val range = ReaderTextSelectionTouch.rangeAround(tv.text, layout, mid)
        assertEquals(mid, range.first)
        assertEquals(mid, range.last)
        if (layout.lineCount >= 2) {
            assertTrue(
                "mid-line CJK must not end at wrap offset",
                range.last + 1 < layout.getLineStart(1) || mid == lastOnFirst,
            )
        }
    }

    @Test
    fun rangeBetween_latinWord_handleDragCanSelectPartialCharacters() {
        val context = RuntimeEnvironment.getApplication()
        val tv = TextView(context).apply {
            text = "Supercalifragilisticexpialidocious extra"
            textSize = 22f
            measure(
                View.MeasureSpec.makeMeasureSpec(1200, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.EXACTLY),
            )
            layout(0, 0, 1200, 200)
        }
        val layout = tv.layout ?: error("layout missing")
        val text = tv.text
        val wordEnd = text.indexOf(' ')
        val full = ReaderTextSelectionTouch.rangeAround(text, layout, text.indexOf('c'))
        assertEquals(0, full.first)
        assertEquals(wordEnd - 1, full.last)

        val mid = text.indexOf('f')
        val shrunk = ReaderTextSelectionTouch.rangeBetween(
            text,
            layout,
            full.last,
            mid,
            selectLatinWord = false,
        )
        assertEquals(mid, shrunk.first)
        assertEquals(full.last, shrunk.last)
        assertEquals("fragilisticexpialidocious", text.substring(shrunk.first, shrunk.last + 1))
    }

    @Test
    fun rangeAround_latinWord_selectsWholeWordOnSameLine() {
        val context = RuntimeEnvironment.getApplication()
        val tv = TextView(context).apply {
            text = "Hello world"
            textSize = 22f
            measure(
                View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.EXACTLY),
            )
            layout(0, 0, 400, 200)
        }
        val layout = tv.layout ?: error("layout missing")
        val eIndex = tv.text.indexOf('e')
        val range = ReaderTextSelectionTouch.rangeAround(tv.text, layout, eIndex)
        assertEquals(0, range.first)
        assertEquals(4, range.last)
    }

    @Test
    fun rangeAround_plainTextLatin_selectsSingleCharacter() {
        val context = RuntimeEnvironment.getApplication()
        val tv = TextView(context).apply {
            text = "ABCDEFGHIJKLMN"
            textSize = 22f
            measure(
                View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.EXACTLY),
            )
            layout(0, 0, 400, 200)
        }
        val offset = 6
        val range = ReaderTextSelectionTouch.rangeAround(
            tv.text,
            tv.layout!!,
            offset,
            selectLatinWord = false,
        )
        assertEquals(offset, range.first)
        assertEquals(offset, range.last)
    }

    @Test
    fun rangeBetween_plainTextLatin_extendsCharacterByCharacter() {
        val tv = wrappingTextView()
        val text = tv.text
        val from = text.indexOf('A')
        val to = text.indexOf('D')
        val range = ReaderTextSelectionTouch.rangeBetween(
            text,
            tv.layout!!,
            from,
            to,
            selectLatinWord = false,
        )
        assertEquals(from, range.first)
        assertEquals(to, range.last)
    }

    @Test
    fun rangeBetween_twoCjkChars_coversInclusiveSpan() {
        val tv = wrappingTextView()
        val layout = tv.layout!!
        val start = tv.text.indexOf('第')
        val end = tv.text.indexOf('G')
        val range = ReaderTextSelectionTouch.rangeBetween(tv.text, layout, start, end)
        assertEquals(start, range.first)
        assertEquals(end, range.last)
        assertTrue(tv.text.indexOf('A') in range)
    }

    @Test
    fun rangeBetween_acrossHardWrap_endsOnRealChars() {
        val tv = wrappingTextView()
        val layout = tv.layout!!
        assertTrue(layout.lineCount >= 2)
        val first = ReaderTextSelectionTouch.lastVisibleCharOffset(layout, 0)
        val second = layout.getLineStart(1)
        val range = ReaderTextSelectionTouch.rangeBetween(tv.text, layout, first, second)
        assertFalse(range.isEmpty())
        assertEquals(0, layout.getLineForOffset(range.first))
        assertEquals(1, layout.getLineForOffset(range.last))
        assertTrue(range.first < layout.getLineStart(1))
        assertTrue(range.last >= layout.getLineStart(1))
    }

    @Test
    fun isTouchNearSelection_multiLineSideWhitespace_returnsFalse() {
        val tv = wrappingTextView()
        val text = tv.text as Spannable
        val layout = tv.layout!!
        val start = text.indexOf('一')
        val end = text.indexOf('二') + 1
        Selection.setSelection(text, start, end)
        val firstLine = layout.getLineForOffset(start)
        val y = tv.totalPaddingTop +
            (layout.getLineTop(firstLine) + layout.getLineBottom(firstLine)) / 2f

        assertFalse(
            ReaderTextSelectionTouch.isTouchNearSelectionOnTextView(
                tv,
                tv.width - 2f,
                y,
            ),
        )
    }

    @Test
    fun isTouchNearSelection_selectedLineSegment_returnsTrue() {
        val tv = wrappingTextView()
        val text = tv.text as Spannable
        val layout = tv.layout!!
        val start = text.indexOf('一')
        val end = text.indexOf('二') + 1
        Selection.setSelection(text, start, end)
        val line = layout.getLineForOffset(start)
        val x = tv.totalPaddingLeft + layout.getPrimaryHorizontal(start + 1)
        val y = tv.totalPaddingTop +
            (layout.getLineTop(line) + layout.getLineBottom(line)) / 2f

        assertTrue(ReaderTextSelectionTouch.isTouchNearSelectionOnTextView(tv, x, y))
    }

    @Test
    fun isTouchNearSelection_startHandleBand_returnsTrue() {
        val tv = wrappingTextView()
        val text = tv.text as Spannable
        val start = text.indexOf('一')
        val end = text.indexOf('二') + 1
        Selection.setSelection(text, start, end)
        val point = ReaderTextSelectionTouch.selectionHandlePositionOnTextView(
            tv,
            start,
            isEnd = false,
        )!!
        val x = point.first
        val y = point.second + 8f * tv.resources.displayMetrics.density

        assertTrue(ReaderTextSelectionTouch.isTouchNearSelectionOnTextView(tv, x, y))
        assertEquals(
            ReaderTextSelectionTouch.SelectionHandle.START,
            ReaderTextSelectionTouch.selectionHandleAtOnTextView(tv, x, y),
        )
    }

    @Test
    fun selectionHandlePosition_attachesToGlyphBottomNotLineSpacing() {
        val tv = wrappingTextView().apply {
            setLineSpacing(0f, 1.8f)
            measure(
                View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY),
            )
            layout(0, 0, 400, 800)
        }
        val text = tv.text as Spannable
        val start = text.indexOf('一')
        Selection.setSelection(text, start, start + 1)
        val point = ReaderTextSelectionTouch.selectionHandlePositionOnTextView(
            tv,
            start,
            isEnd = false,
        )!!
        val layout = tv.layout!!
        val line = layout.getLineForOffset(start)
        val expectedY = ReaderTextSelectionTouch.glyphBottom(layout, line) +
            tv.extendedPaddingTop - tv.scrollY

        assertEquals(expectedY, point.second, 0.01f)
        // 行距留白全在 descent 里，句柄不能挂到下一行文字上。
        assertTrue(
            "句柄应贴字形底，而不是含行距的行底",
            ReaderTextSelectionTouch.glyphBottom(layout, line) < layout.getLineBottom(line),
        )
    }

    @Test
    fun endHandleAtSoftWrap_usesGlyphBottomOfItsOwnLine() {
        // Robolectric 不做真实字形测量，这里只验证 end 句柄取的是「末字所在行」的字形底，
        // 真实软换行下的表现由 ReaderPlainTextSelectionGeometryInstrumentedTest 覆盖。
        val tv = wrappingTextView()
        val layout = tv.layout!!
        assertTrue(layout.lineCount >= 2)
        val text = tv.text as Spannable
        val start = layout.getLineStart(0)
        val end = ReaderTextSelectionTouch.lastVisibleCharOffset(layout, 0) + 1
        Selection.setSelection(text, start, end)
        val point = ReaderTextSelectionTouch.selectionHandlePositionOnTextView(
            tv,
            end,
            isEnd = true,
        )!!
        val expectedY = ReaderTextSelectionTouch.glyphBottom(layout, 0) +
            tv.extendedPaddingTop - tv.scrollY

        assertEquals(expectedY, point.second, 0.01f)
        assertTrue(point.second <= layout.getLineBottom(0) + tv.extendedPaddingTop - tv.scrollY)
    }

    @Test
    fun selectionHandlePosition_afterScroll_movesWithTextInViewport() {
        val tv = wrappingTextView()
        val text = tv.text as Spannable
        val start = text.indexOf('一')
        val end = text.indexOf('二') + 1
        Selection.setSelection(text, start, end)
        val before = ReaderTextSelectionTouch.selectionHandlePositionOnTextView(
            tv,
            start,
            isEnd = false,
        )!!

        tv.scrollTo(0, 20)

        val after = ReaderTextSelectionTouch.selectionHandlePositionOnTextView(
            tv,
            start,
            isEnd = false,
        )!!
        assertEquals(before.first, after.first, 0.01f)
        assertEquals(before.second - 20f, after.second, 0.01f)
    }

    private fun wrappingTextView(): TextView {
        val context = RuntimeEnvironment.getApplication()
        return TextView(context).apply {
            layoutParams = android.view.ViewGroup.LayoutParams(400, 800)
            setText(
                SpannableString("第一行ABCDEFG\n第二行HIJKLMN"),
                TextView.BufferType.SPANNABLE,
            )
            textSize = 22f
            setPadding(8, 8, 8, 8)
            measure(
                View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY),
            )
            layout(0, 0, 400, 800)
        }
    }

    private fun wrappingLayout(): Layout = wrappingTextView().layout
        ?: error("layout missing")
}
