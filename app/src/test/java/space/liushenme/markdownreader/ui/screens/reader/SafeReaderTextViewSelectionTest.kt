package space.liushenme.markdownreader.ui.screens.reader

import android.content.ClipboardManager
import android.content.Context
import android.text.Selection
import android.text.SpannableString
import android.view.MotionEvent
import android.widget.FrameLayout
import android.widget.PopupMenu
import android.widget.TextView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SafeReaderTextViewSelectionTest {

    private lateinit var textView: SafeReaderTextView

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        textView = SafeReaderTextView(context).apply {
            val body = SpannableString("长按选中这段文字，然后拖动句柄扩展选区。")
            setText(body, TextView.BufferType.SPANNABLE)
            textSize = 18f
            setPadding(24, 24, 24, 24)
            measure(
                android.view.View.MeasureSpec.makeMeasureSpec(400, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(600, android.view.View.MeasureSpec.EXACTLY),
            )
            layout(0, 0, 400, 600)
        }
        FrameLayout(context).apply {
            addView(textView)
            measure(
                android.view.View.MeasureSpec.makeMeasureSpec(400, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(800, android.view.View.MeasureSpec.EXACTLY),
            )
            layout(0, 0, 400, 800)
        }
    }

    private fun activateSelection(word: String = "选中") {
        val text = textView.text as SpannableString
        val start = text.indexOf(word)
        assertTrue("fixture contains '$word'", start >= 0)
        textView.applySelectionRangeForTest(start, start + word.length)
    }

    private fun offsetXYForSpan(start: Int, end: Int): Pair<Float, Float> =
        offsetXYForSpanOn(textView, start, end)

    private fun offsetXYForSpanOn(
        view: SafeReaderTextView,
        start: Int,
        end: Int,
    ): Pair<Float, Float> {
        val layout = view.layout ?: error("layout missing")
        val mid = (start + end) / 2
        val line = layout.getLineForOffset(mid)
        val x = layout.getPrimaryHorizontal(mid) + view.totalPaddingLeft
        val y = (layout.getLineTop(line) + layout.getLineBottom(line)) / 2f + view.totalPaddingTop
        return x to y
    }

    private fun wrappingReaderTextView(body: String): SafeReaderTextView {
        val context = RuntimeEnvironment.getApplication()
        val view = SafeReaderTextView(context).apply {
            setText(SpannableString(body), TextView.BufferType.SPANNABLE)
            textSize = 22f
            setPadding(8, 8, 8, 8)
            measure(
                android.view.View.MeasureSpec.makeMeasureSpec(400, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(800, android.view.View.MeasureSpec.EXACTLY),
            )
            layout(0, 0, 400, 800)
        }
        FrameLayout(context).apply {
            addView(view)
            measure(
                android.view.View.MeasureSpec.makeMeasureSpec(400, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(800, android.view.View.MeasureSpec.EXACTLY),
            )
            layout(0, 0, 400, 800)
        }
        return view
    }

    @Test
    fun prepareForNewTouch_onHandleBand_doesNotDismissImmediately() {
        activateSelection()
        val text = textView.text as SpannableString
        val start = Selection.getSelectionStart(text)
        val layout = textView.layout!!
        val lineStart = layout.getLineForOffset(start)
        val handleX = layout.getPrimaryHorizontal(start) + textView.totalPaddingLeft
        val handleY = layout.getLineTop(lineStart) + textView.totalPaddingTop -
            ReaderTextSelectionTouch.handleBandPx(textView.resources.displayMetrics.density) / 2f

        textView.prepareForNewTouch(handleX, handleY)
        assertTrue(textView.isInTextSelection())
        assertTrue(textView.hasVisibleTextSelection())
    }

    @Test
    fun prepareForNewTouch_farOutside_marksPendingDismissWithoutClearingSelection() {
        activateSelection()
        textView.prepareForNewTouch(10f, 10f)
        assertTrue(textView.isInTextSelection())
        assertTrue(textView.hasVisibleTextSelection())
    }

    @Test
    fun outsideTapUp_dismissesSelection() {
        activateSelection()
        val outsideX = 12f
        val outsideY = 580f
        val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, outsideX, outsideY, 0)
        val up = MotionEvent.obtain(0, 50, MotionEvent.ACTION_UP, outsideX, outsideY, 0)
        try {
            textView.prepareForNewTouch(outsideX, outsideY)
            textView.onTouchEvent(down)
            textView.onTouchEvent(up)
        } finally {
            down.recycle()
            up.recycle()
        }
        assertFalse(textView.hasVisibleTextSelection())
        assertFalse(textView.isInTextSelection())
    }

    @Test
    fun outsideDown_dismissesSelectionImmediately() {
        activateSelection()
        val down = MotionEvent.obtain(
            0,
            0,
            MotionEvent.ACTION_DOWN,
            textView.width - 2f,
            textView.height - 2f,
            0,
        )
        try {
            textView.onTouchEvent(down)
            assertFalse(textView.hasVisibleTextSelection())
            assertFalse(textView.isInTextSelection())
        } finally {
            down.recycle()
        }
    }

    @Test
    fun downInStartHandleBand_doesNotDismissSelection() {
        activateSelection()
        val text = textView.text as SpannableString
        val start = Selection.getSelectionStart(text)
        val point = ReaderTextSelectionTouch.selectionHandlePositionOnTextView(
            textView,
            start,
            isEnd = false,
        )!!
        val x = point.first
        val y = point.second + 8f * textView.resources.displayMetrics.density
        val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, x, y, 0)
        try {
            textView.onTouchEvent(down)
            assertTrue(textView.hasVisibleTextSelection())
            assertTrue(textView.isInTextSelection())
        } finally {
            down.recycle()
        }
    }

    @Test
    fun dragCustomEndHandle_extendsSingleSelection() {
        activateSelection()
        val text = textView.text as SpannableString
        val originalStart = Selection.getSelectionStart(text)
        val originalEnd = Selection.getSelectionEnd(text)
        val endPoint = ReaderTextSelectionTouch.selectionHandlePositionOnTextView(
            textView,
            originalEnd,
            isEnd = true,
        )!!
        val destination = text.indexOf("句柄")
        val (destX, destY) = offsetXYForSpan(destination, destination + 1)
        val density = textView.resources.displayMetrics.density
        val down = MotionEvent.obtain(
            0,
            0,
            MotionEvent.ACTION_DOWN,
            endPoint.first,
            endPoint.second + 8f * density,
            0,
        )
        val move = MotionEvent.obtain(0, 40, MotionEvent.ACTION_MOVE, destX, destY, 0)
        val up = MotionEvent.obtain(0, 60, MotionEvent.ACTION_UP, destX, destY, 0)
        try {
            textView.onTouchEvent(down)
            textView.onTouchEvent(move)
            textView.onTouchEvent(up)

            val newStart = Selection.getSelectionStart(text)
            val newEnd = Selection.getSelectionEnd(text)
            // Robolectric 对同行 CJK 的水平坐标可能重合，首尾句柄会被判为同一触点；
            // 无论命中哪一端，都必须只保留一段连续选区并扩到当前字符。
            assertTrue(newStart >= originalStart)
            assertTrue(newEnd - newStart > originalEnd - originalStart)
            assertTrue(destination in newStart until newEnd)
            assertTrue(textView.hasVisibleTextSelection())
            assertTrue(textView.isInTextSelection())
        } finally {
            down.recycle()
            move.recycle()
            up.recycle()
        }
    }

    @Test
    fun outsideDrag_afterEditorClearedRange_doesNotRestoreOrphanHighlight() {
        // 区外按下后手指移动超出 slop，shouldDismiss 不成立；若系统已清 range，
        // 旧逻辑会 restoreSavedSelectionRange → 只剩选区阴影、无首尾句柄。
        activateSelection()
        val text = textView.text as SpannableString
        val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 12f, 580f, 0)
        val up = MotionEvent.obtain(0, 50, MotionEvent.ACTION_UP, 12f, 400f, 0)
        try {
            textView.onTouchEvent(down)
            Selection.removeSelection(text)
            textView.onTouchEvent(up)
        } finally {
            down.recycle()
            up.recycle()
        }
        assertFalse(textView.hasVisibleTextSelection())
        assertFalse(textView.isInTextSelection())
        assertTrue(Selection.getSelectionStart(text) == Selection.getSelectionEnd(text))
    }

    @Test
    fun actionModeDestroyedBySystem_clearsSelectionHighlightWithHandles() {
        activateSelection()
        assertTrue(textView.isInTextSelection())
        assertTrue(textView.hasVisibleTextSelection())

        textView.simulateSystemActionModeDestroyForTest()

        assertFalse(textView.hasVisibleTextSelection())
        assertFalse(textView.isInTextSelection())
    }

    @Test
    fun touchInsideSelectionText_doesNotDismissOnUp() {
        activateSelection()
        val text = textView.text as SpannableString
        val start = Selection.getSelectionStart(text)
        val end = Selection.getSelectionEnd(text)
        val (x, y) = offsetXYForSpan(start, end)
        val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, x, y, 0)
        val up = MotionEvent.obtain(0, 30, MotionEvent.ACTION_UP, x, y, 0)
        try {
            textView.onTouchEvent(down)
            textView.onTouchEvent(up)
        } finally {
            down.recycle()
            up.recycle()
        }
        assertTrue(textView.hasVisibleTextSelection())
    }

    @Test
    fun longPressPrep_makesTextSelectable() {
        val text = textView.text as SpannableString
        val word = "长按"
        val start = text.indexOf(word)
        val (x, y) = offsetXYForSpan(start, start + word.length)
        val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, x, y, 0)
        try {
            textView.onTouchEvent(down)
            assertTrue(textView.isSelectionLongPressScheduledForTest())
            assertTrue(textView.fireScheduledSelectionLongPressForTest())
            assertFalse(textView.performLongClick(x, y))
        } finally {
            down.recycle()
        }
        // Robolectric 下系统长按未必画出选区，但应完成 prep 并进入可选中状态
        assertTrue(textView.isTextSelectable)
        assertFalse(textView.isSelectionLongPressScheduledForTest())
        assertTrue(textView.hasVisibleTextSelection())
        val selStart = Selection.getSelectionStart(textView.text as SpannableString)
        val selEnd = Selection.getSelectionEnd(textView.text as SpannableString)
        val pressOffset = ReaderTextSelectionTouch.offsetNearestCharOnTextView(textView, x, y)!!
        assertTrue(
            "selection $selStart..$selEnd should cover press offset $pressOffset",
            pressOffset in selStart until selEnd,
        )
        val layout = textView.layout!!
        assertEquals(layout.getLineForOffset(selStart), layout.getLineForOffset(selEnd - 1))
    }

    @Test
    fun longPress_thenMoveWithinSlop_keepsPressedChar() {
        val text = textView.text as SpannableString
        val word = "长按"
        val start = text.indexOf(word)
        val (x, y) = offsetXYForSpan(start, start + word.length)
        val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, x, y, 0)
        val move = MotionEvent.obtain(0, 60, MotionEvent.ACTION_MOVE, x + 2f, y + 2f, 0)
        try {
            textView.onTouchEvent(down)
            assertTrue(textView.fireScheduledSelectionLongPressForTest())
            assertTrue(textView.hasVisibleTextSelection())
            val beforeStart = Selection.getSelectionStart(text)
            val beforeEnd = Selection.getSelectionEnd(text)
            textView.onTouchEvent(move)
            assertEquals(beforeStart, Selection.getSelectionStart(text))
            assertEquals(beforeEnd, Selection.getSelectionEnd(text))
            val layout = textView.layout!!
            assertEquals(layout.getLineForOffset(beforeStart), layout.getLineForOffset(beforeEnd - 1))
        } finally {
            down.recycle()
            move.recycle()
        }
    }

    @Test
    fun plainTextLongPress_latinSelectionChangesCharacterByCharacter() {
        val plain = wrappingReaderTextView("ABCDEFGHIJKLMN\nOPQRSTUVWXYZ").apply {
            renderPlainTextBody = true
        }
        val text = plain.text as SpannableString
        val pressOffset = text.indexOf('D')
        val destination = text.indexOf('O')
        val (x, y) = offsetXYForSpanOn(plain, pressOffset, pressOffset + 1)
        val (destX, destY) = offsetXYForSpanOn(plain, destination, destination + 1)
        val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, x, y, 0)
        val move = MotionEvent.obtain(0, 80, MotionEvent.ACTION_MOVE, destX, destY, 0)
        try {
            plain.onTouchEvent(down)
            assertTrue(plain.fireScheduledSelectionLongPressForTest())
            assertEquals(1, Selection.getSelectionEnd(text) - Selection.getSelectionStart(text))
            val initialStart = Selection.getSelectionStart(text)

            plain.onTouchEvent(move)

            assertEquals(initialStart, Selection.getSelectionStart(text))
            assertEquals(destination + 1, Selection.getSelectionEnd(text))
        } finally {
            down.recycle()
            move.recycle()
        }
    }

    @Test
    fun longPress_thenMoveBeyondSlop_extendsToCurrentChar() {
        val wrapping = wrappingReaderTextView("第一行ABCDEFG\n第二行HIJKLMN")
        val text = wrapping.text as SpannableString
        val pressStart = text.indexOf('第')
        val destStart = text.indexOf('二')
        val (x, y) = offsetXYForSpanOn(wrapping, pressStart, pressStart + 1)
        val (destX, destY) = offsetXYForSpanOn(wrapping, destStart, destStart + 1)
        val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, x, y, 0)
        val move = MotionEvent.obtain(0, 80, MotionEvent.ACTION_MOVE, destX, destY, 0)
        try {
            wrapping.onTouchEvent(down)
            assertTrue(wrapping.fireScheduledSelectionLongPressForTest())
            assertTrue(wrapping.hasVisibleTextSelection())
            val beforeStart = Selection.getSelectionStart(text)
            val beforeEnd = Selection.getSelectionEnd(text)
            val pressOffset = ReaderTextSelectionTouch.offsetNearestCharOnTextView(wrapping, x, y)!!
            val destOffset = ReaderTextSelectionTouch.offsetNearestCharOnTextView(wrapping, destX, destY)!!
            assertTrue("dest must be a different char", destOffset != pressOffset)
            wrapping.onTouchEvent(move)
            val afterStart = Selection.getSelectionStart(text)
            val afterEnd = Selection.getSelectionEnd(text)
            assertTrue(
                "drag should grow selection from $beforeStart..$beforeEnd to $afterStart..$afterEnd",
                afterEnd - afterStart > beforeEnd - beforeStart,
            )
            assertTrue(pressOffset in afterStart until afterEnd)
            assertTrue(destOffset in afterStart until afterEnd)
            val layout = wrapping.layout!!
            assertEquals(
                layout.getLineForOffset(destOffset),
                layout.getLineForOffset(afterEnd - 1),
            )
        } finally {
            down.recycle()
            move.recycle()
        }
    }

    @Test
    fun longPress_upAfterDragExtend_keepsExtendedRange() {
        val wrapping = wrappingReaderTextView("第一行ABCDEFG\n第二行HIJKLMN")
        val text = wrapping.text as SpannableString
        val pressStart = text.indexOf('第')
        val destStart = text.indexOf('二')
        val (x, y) = offsetXYForSpanOn(wrapping, pressStart, pressStart + 1)
        val (destX, destY) = offsetXYForSpanOn(wrapping, destStart, destStart + 1)
        val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, x, y, 0)
        val move = MotionEvent.obtain(0, 80, MotionEvent.ACTION_MOVE, destX, destY, 0)
        val up = MotionEvent.obtain(0, 90, MotionEvent.ACTION_UP, destX, destY, 0)
        try {
            wrapping.onTouchEvent(down)
            assertTrue(wrapping.fireScheduledSelectionLongPressForTest())
            val beforeStart = Selection.getSelectionStart(text)
            val beforeEnd = Selection.getSelectionEnd(text)
            wrapping.onTouchEvent(move)
            val dragStart = Selection.getSelectionStart(text)
            val dragEnd = Selection.getSelectionEnd(text)
            assertTrue(dragEnd - dragStart > beforeEnd - beforeStart)
            wrapping.onTouchEvent(up)
            assertEquals(dragStart, Selection.getSelectionStart(text))
            assertEquals(dragEnd, Selection.getSelectionEnd(text))
            assertTrue(wrapping.hasVisibleTextSelection())
            val pressOffset = ReaderTextSelectionTouch.offsetNearestCharOnTextView(wrapping, x, y)!!
            val destOffset = ReaderTextSelectionTouch.offsetNearestCharOnTextView(wrapping, destX, destY)!!
            assertTrue(pressOffset in dragStart until dragEnd)
            assertTrue(destOffset in dragStart until dragEnd)
        } finally {
            down.recycle()
            move.recycle()
            up.recycle()
        }
    }

    @Test
    fun longPress_dragExtend_thenOutsideDown_dismissesWithoutRestore() {
        val wrapping = wrappingReaderTextView("第一行ABCDEFG\n第二行HIJKLMN")
        val text = wrapping.text as SpannableString
        val pressStart = text.indexOf('第')
        val destStart = text.indexOf('二')
        val (x, y) = offsetXYForSpanOn(wrapping, pressStart, pressStart + 1)
        val (destX, destY) = offsetXYForSpanOn(wrapping, destStart, destStart + 1)
        val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, x, y, 0)
        val move = MotionEvent.obtain(0, 80, MotionEvent.ACTION_MOVE, destX, destY, 0)
        val up = MotionEvent.obtain(0, 90, MotionEvent.ACTION_UP, destX, destY, 0)
        val outsideDown = MotionEvent.obtain(
            100,
            100,
            MotionEvent.ACTION_DOWN,
            wrapping.width - 2f,
            wrapping.height - 2f,
            0,
        )
        try {
            wrapping.onTouchEvent(down)
            assertTrue(wrapping.fireScheduledSelectionLongPressForTest())
            wrapping.onTouchEvent(move)
            wrapping.onTouchEvent(up)
            assertTrue(wrapping.hasVisibleTextSelection())

            wrapping.onTouchEvent(outsideDown)

            assertFalse(wrapping.hasVisibleTextSelection())
            assertFalse(wrapping.isInTextSelection())
            assertTrue(Selection.getSelectionStart(text) == Selection.getSelectionEnd(text))
        } finally {
            down.recycle()
            move.recycle()
            up.recycle()
            outsideDown.recycle()
        }
    }

    @Test
    fun longPress_dragToNextLine_selectsCharsNotWrapOffsets() {
        val wrapping = wrappingReaderTextView("第一行ABCDEFG\n第二行HIJKLMN")
        val text = wrapping.text as SpannableString
        val pressStart = text.indexOf('第')
        val destStart = text.indexOf('二')
        val (x, y) = offsetXYForSpanOn(wrapping, pressStart, pressStart + 1)
        val (destX, destY) = offsetXYForSpanOn(wrapping, destStart, destStart + 1)
        val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, x, y, 0)
        val move = MotionEvent.obtain(0, 80, MotionEvent.ACTION_MOVE, destX, destY, 0)
        val up = MotionEvent.obtain(0, 90, MotionEvent.ACTION_UP, destX, destY, 0)
        try {
            wrapping.onTouchEvent(down)
            assertTrue(wrapping.fireScheduledSelectionLongPressForTest())
            wrapping.onTouchEvent(move)
            wrapping.onTouchEvent(up)
            val afterStart = Selection.getSelectionStart(text)
            val afterEnd = Selection.getSelectionEnd(text)
            val layout = wrapping.layout!!
            assertTrue(layout.lineCount >= 2)
            assertEquals(0, layout.getLineForOffset(afterStart))
            assertEquals(1, layout.getLineForOffset(afterEnd - 1))
            val pressOffset = ReaderTextSelectionTouch.offsetNearestCharOnTextView(wrapping, x, y)!!
            val destOffset = ReaderTextSelectionTouch.offsetNearestCharOnTextView(wrapping, destX, destY)!!
            assertTrue(pressOffset in afterStart until afterEnd)
            assertTrue(destOffset in afterStart until afterEnd)
            assertTrue(afterStart < layout.getLineStart(1))
            assertTrue(afterEnd - 1 >= layout.getLineStart(1))
        } finally {
            down.recycle()
            move.recycle()
            up.recycle()
        }
    }

    @Test
    fun fingerDown_doesNotSelectBeforeLongPressTimeout() {
        val text = textView.text as SpannableString
        val word = "长按"
        val start = text.indexOf(word)
        val (x, y) = offsetXYForSpan(start, start + word.length)
        val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, x, y, 0)
        try {
            textView.onTouchEvent(down)
            assertEquals(
                600L,
                SafeReaderTextView.SELECTION_LONG_PRESS_TIMEOUT_MS,
            )
            // 必须晚于系统长按，否则 Editor 会抢在前面另起一套选区。
            assertTrue(
                SafeReaderTextView.SELECTION_LONG_PRESS_TIMEOUT_MS >
                    android.view.ViewConfiguration.getLongPressTimeout(),
            )
            assertTrue(textView.isSelectionLongPressScheduledForTest())
            assertFalse(textView.performLongClick(x, y))
            assertFalse(textView.isInTextSelection())
            assertFalse(textView.hasVisibleTextSelection())
            assertTrue(textView.isSelectionLongPressScheduledForTest())
        } finally {
            down.recycle()
        }
    }

    @Test
    fun verticalScroll_cancelsDelayedSelection() {
        val text = textView.text as SpannableString
        val word = "长按"
        val start = text.indexOf(word)
        val (x, y) = offsetXYForSpan(start, start + word.length)
        val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, x, y, 0)
        val move = MotionEvent.obtain(0, 40, MotionEvent.ACTION_MOVE, x, y + 80f, 0)
        try {
            textView.onTouchEvent(down)
            assertTrue(textView.isSelectionLongPressScheduledForTest())
            textView.onTouchEvent(move)
            assertFalse(textView.isSelectionLongPressScheduledForTest())
            assertFalse(textView.fireScheduledSelectionLongPressForTest())
            assertFalse(textView.isInTextSelection())
            assertFalse(textView.hasVisibleTextSelection())
        } finally {
            down.recycle()
            move.recycle()
        }
    }

    @Test
    fun fingerUp_cancelsDelayedSelection() {
        val text = textView.text as SpannableString
        val word = "长按"
        val start = text.indexOf(word)
        val (x, y) = offsetXYForSpan(start, start + word.length)
        val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, x, y, 0)
        val up = MotionEvent.obtain(0, 80, MotionEvent.ACTION_UP, x, y, 0)
        try {
            textView.onTouchEvent(down)
            assertTrue(textView.isSelectionLongPressScheduledForTest())
            textView.onTouchEvent(up)
            assertFalse(textView.isSelectionLongPressScheduledForTest())
            assertFalse(textView.fireScheduledSelectionLongPressForTest())
            assertFalse(textView.isInTextSelection())
            assertFalse(textView.hasVisibleTextSelection())
        } finally {
            down.recycle()
            up.recycle()
        }
    }

    @Test
    fun existingSelection_doesNotScheduleDelayedLongPress() {
        activateSelection()
        val text = textView.text as SpannableString
        val start = Selection.getSelectionStart(text)
        val end = Selection.getSelectionEnd(text)
        val (x, y) = offsetXYForSpan(start, end)
        val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, x, y, 0)
        try {
            textView.onTouchEvent(down)
            assertFalse(textView.isSelectionLongPressScheduledForTest())
        } finally {
            down.recycle()
        }
    }

    @Test
    fun selectionMenu_containsCopyAndHighlight() {
        val menu = PopupMenu(textView.context, textView).menu
        textView.populateSelectionMenuForTest(menu)
        assertEquals(2, menu.size())
        assertEquals(SafeReaderTextView.MENU_ID_COPY, menu.getItem(0).itemId)
        assertEquals(
            textView.context.getString(space.liushenme.markdownreader.R.string.selection_menu_copy),
            menu.getItem(0).title.toString(),
        )
        assertEquals(SafeReaderTextView.MENU_ID_HIGHLIGHT, menu.getItem(1).itemId)
        assertEquals(
            textView.context.getString(space.liushenme.markdownreader.R.string.selection_menu_highlight),
            menu.getItem(1).title.toString(),
        )
    }

    @Test
    fun copyMenu_writesClipboardAndDismissesSelection() {
        activateSelection("选中")
        assertTrue(textView.performSelectionMenuActionForTest(SafeReaderTextView.MENU_ID_COPY))
        val clipboard = textView.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        assertEquals(
            "选中",
            clipboard.primaryClip?.getItemAt(0)?.coerceToText(textView.context)?.toString(),
        )
        assertFalse(textView.isInTextSelection())
        assertFalse(textView.hasVisibleTextSelection())
    }

    @Test
    fun highlightMenu_invokesCallbackAndKeepsSelection() {
        activateSelection("选中")
        var got: String? = null
        var gotStart = -1
        var gotEnd = -1
        var gotBounds: android.graphics.Rect? = null
        textView.onHighlightMenuClick = { text, start, end, bounds ->
            got = text
            gotStart = start
            gotEnd = end
            gotBounds = android.graphics.Rect(bounds)
        }
        assertTrue(textView.performSelectionMenuActionForTest(SafeReaderTextView.MENU_ID_HIGHLIGHT))
        assertEquals("选中", got)
        assertEquals(2, gotStart)
        assertEquals(4, gotEnd)
        val bounds = gotBounds
        assertTrue(bounds != null && !bounds.isEmpty)
        // 点划线后应保留选区与系统菜单
        assertTrue(textView.isInTextSelection())
        assertTrue(textView.hasVisibleTextSelection())
    }

    @Test
    fun selectionMenu_showsCancelWhenExistingHighlight() {
        activateSelection("选中")
        textView.resolveExistingHighlightId = { _, _, _ -> 42L }
        val menu = PopupMenu(textView.context, textView).menu
        textView.populateSelectionMenuForTest(menu)
        assertEquals(SafeReaderTextView.MENU_ID_CANCEL_HIGHLIGHT, menu.getItem(1).itemId)
        assertEquals(
            textView.context.getString(space.liushenme.markdownreader.R.string.selection_menu_cancel_highlight),
            menu.getItem(1).title.toString(),
        )
    }

    @Test
    fun cancelHighlightMenu_invokesRemoveCallback() {
        activateSelection("选中")
        var removedId: Long? = null
        textView.resolveExistingHighlightId = { _, _, _ -> 7L }
        textView.onRemoveHighlightClick = { removedId = it }
        assertTrue(
            textView.performSelectionMenuActionForTest(SafeReaderTextView.MENU_ID_CANCEL_HIGHLIGHT),
        )
        assertEquals(7L, removedId)
        assertTrue(textView.isInTextSelection())
        // Flow 未更新前 resolve 仍可能返回旧 id，菜单也应立刻回到「划线」
        val menu = PopupMenu(textView.context, textView).menu
        textView.populateSelectionMenuForTest(menu)
        assertEquals(SafeReaderTextView.MENU_ID_HIGHLIGHT, menu.getItem(1).itemId)
        assertEquals(
            textView.context.getString(space.liushenme.markdownreader.R.string.selection_menu_highlight),
            menu.getItem(1).title.toString(),
        )
    }

    @Test
    fun bindSelectionHighlightId_switchesMenuToCancel() {
        activateSelection("选中")
        textView.resolveExistingHighlightId = { _, _, _ -> null }
        val menu = PopupMenu(textView.context, textView).menu
        textView.populateSelectionMenuForTest(menu)
        assertEquals(SafeReaderTextView.MENU_ID_HIGHLIGHT, menu.getItem(1).itemId)
        assertEquals(
            textView.context.getString(space.liushenme.markdownreader.R.string.selection_menu_highlight),
            menu.getItem(1).title.toString(),
        )

        textView.bindSelectionHighlightId(11L, 2, 4)
        textView.populateSelectionMenuForTest(menu)
        assertEquals(SafeReaderTextView.MENU_ID_CANCEL_HIGHLIGHT, menu.getItem(1).itemId)
        assertEquals(
            textView.context.getString(space.liushenme.markdownreader.R.string.selection_menu_cancel_highlight),
            menu.getItem(1).title.toString(),
        )

        var removedId: Long? = null
        textView.onRemoveHighlightClick = { removedId = it }
        assertTrue(
            textView.performSelectionMenuActionForTest(SafeReaderTextView.MENU_ID_CANCEL_HIGHLIGHT),
        )
        assertEquals(11L, removedId)
        textView.populateSelectionMenuForTest(menu)
        assertEquals(SafeReaderTextView.MENU_ID_HIGHLIGHT, menu.getItem(1).itemId)
        assertEquals(
            textView.context.getString(space.liushenme.markdownreader.R.string.selection_menu_highlight),
            menu.getItem(1).title.toString(),
        )
    }
}
