package space.liushenme.markdownreader.ui.screens.reader

import android.content.ClipboardManager
import android.content.Context
import android.text.Selection
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ClickableSpan
import android.view.MotionEvent
import android.widget.FrameLayout
import android.widget.PopupMenu
import android.widget.TextView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import io.noties.markwon.image.AsyncDrawableSpan
import space.liushenme.markdownreader.markdown.ReaderMarkwonFactory
import io.noties.markwon.core.spans.HeadingSpan

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
    fun linkLongPress_startsSelectionInsteadOfOnlyClickHandling() {
        val context = RuntimeEnvironment.getApplication()
        val body = SpannableString("前文 链接文字 后文")
        val linkStart = body.indexOf("链接文字")
        var clicked = false
        body.setSpan(
            object : ClickableSpan() {
                override fun onClick(widget: android.view.View) {
                    clicked = true
                }
            },
            linkStart,
            linkStart + "链接文字".length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
        val view = SafeReaderTextView(context).apply {
            setText(body, TextView.BufferType.SPANNABLE)
            textSize = 22f
            setPadding(8, 8, 8, 8)
            measure(
                android.view.View.MeasureSpec.makeMeasureSpec(400, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(200, android.view.View.MeasureSpec.EXACTLY),
            )
            layout(0, 0, 400, 200)
        }
        FrameLayout(context).apply {
            addView(view)
            measure(
                android.view.View.MeasureSpec.makeMeasureSpec(400, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(200, android.view.View.MeasureSpec.EXACTLY),
            )
            layout(0, 0, 400, 200)
        }
        val (x, y) = offsetXYForSpanOn(view, linkStart, linkStart + 1)
        val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, x, y, 0)
        val up = MotionEvent.obtain(0, 700, MotionEvent.ACTION_UP, x, y, 0)
        try {
            view.onTouchEvent(down)
            assertTrue(view.isSelectionLongPressScheduledForTest())
            assertTrue(view.fireScheduledSelectionLongPressForTest())
            assertTrue(view.hasVisibleTextSelection())
            val selectedText = view.text as Spanned
            assertTrue(Selection.getSelectionEnd(selectedText) > Selection.getSelectionStart(selectedText))
            view.onTouchEvent(up)
            assertFalse("long press must not dispatch the link click on ACTION_UP", clicked)
        } finally {
            down.recycle()
            up.recycle()
        }
    }

    @Test
    fun consumedLinkTap_cancelsPendingLongPressSelection() {
        val context = RuntimeEnvironment.getApplication()
        val body = SpannableString("前文 链接文字 后文")
        val linkStart = body.indexOf("链接文字")
        body.setSpan(
            object : ClickableSpan() {
                override fun onClick(widget: android.view.View) = Unit
            },
            linkStart,
            linkStart + "链接文字".length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
        val view = SafeReaderTextView(context).apply {
            setText(body, TextView.BufferType.SPANNABLE)
            textSize = 22f
            setPadding(8, 8, 8, 8)
            measure(
                android.view.View.MeasureSpec.makeMeasureSpec(400, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(200, android.view.View.MeasureSpec.EXACTLY),
            )
            layout(0, 0, 400, 200)
        }
        FrameLayout(context).apply {
            addView(view)
            measure(
                android.view.View.MeasureSpec.makeMeasureSpec(400, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(200, android.view.View.MeasureSpec.EXACTLY),
            )
            layout(0, 0, 400, 200)
        }
        val (x, y) = offsetXYForSpanOn(view, linkStart, linkStart + 1)
        val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, x, y, 0)
        try {
            view.onTouchEvent(down)
            assertTrue(view.isSelectionLongPressScheduledForTest())
            assertTrue(view.dispatchLinkClickIfPresent(x, y))
            assertFalse(view.isSelectionLongPressScheduledForTest())
        } finally {
            down.recycle()
        }
    }

    @Test
    fun inlineLatexAsyncDrawable_isSelectableForLongPress() {
        val context = RuntimeEnvironment.getApplication()
        val markwon = ReaderMarkwonFactory.create(context)
        val rendered = markwon.toMarkdown("前文 ${'$'}x^2${'$'} 后文") as Spanned
        val latex = rendered.getSpans(0, rendered.length, AsyncDrawableSpan::class.java)
            .firstOrNull { it.javaClass.simpleName.contains("Latex", ignoreCase = true) }
        assertNotNull("inline latex should be rendered as a latex drawable span", latex)
        val view = SafeReaderTextView(context).apply {
            setText(rendered, TextView.BufferType.SPANNABLE)
            textSize = 22f
            setPadding(8, 8, 8, 8)
            measure(
                android.view.View.MeasureSpec.makeMeasureSpec(600, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(200, android.view.View.MeasureSpec.EXACTLY),
            )
            layout(0, 0, 600, 200)
        }
        FrameLayout(context).apply {
            addView(view)
            measure(
                android.view.View.MeasureSpec.makeMeasureSpec(600, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(200, android.view.View.MeasureSpec.EXACTLY),
            )
            layout(0, 0, 600, 200)
        }
        val start = rendered.getSpanStart(latex)
        assertTrue(view.canSelectAtOffsetForTest(start))
        val (x, y) = offsetXYForSpanOn(view, start, start + 1)
        val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, x, y, 0)
        try {
            view.onTouchEvent(down)
            assertTrue(view.isSelectionLongPressScheduledForTest())
            assertTrue(view.fireScheduledSelectionLongPressForTest())
            assertTrue(view.hasVisibleTextSelection())
            val selectedStart = Selection.getSelectionStart(view.text as Spanned)
            val selectedEnd = Selection.getSelectionEnd(view.text as Spanned)
            assertTrue(selectedEnd > selectedStart)
        } finally {
            down.recycle()
        }
    }

    @Test
    fun markdownHeadingLongPress_isSelectableLikeBodyText() {
        val context = RuntimeEnvironment.getApplication()
        val markwon = ReaderMarkwonFactory.create(context)
        val rendered = markwon.toMarkdown("## (1) 准确率奖励\n\n正文内容") as Spanned
        val heading = rendered.getSpans(0, rendered.length, HeadingSpan::class.java).single()
        val headingStart = rendered.getSpanStart(heading)
        val titleStart = rendered.toString().indexOf("准确率奖励", headingStart)
        assertTrue(titleStart >= headingStart)
        val view = SafeReaderTextView(context).apply {
            setText(rendered, TextView.BufferType.SPANNABLE)
            textSize = 22f
            setPadding(8, 8, 8, 8)
            measure(
                android.view.View.MeasureSpec.makeMeasureSpec(600, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(240, android.view.View.MeasureSpec.EXACTLY),
            )
            layout(0, 0, 600, 240)
        }
        FrameLayout(context).apply {
            addView(view)
            measure(
                android.view.View.MeasureSpec.makeMeasureSpec(600, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(240, android.view.View.MeasureSpec.EXACTLY),
            )
            layout(0, 0, 600, 240)
        }
        val (x, y) = offsetXYForSpanOn(view, titleStart, titleStart + 1)
        val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, x, y, 0)
        try {
            view.onTouchEvent(down)
            assertTrue(view.isSelectionLongPressScheduledForTest())
            assertTrue(view.fireScheduledSelectionLongPressForTest())
            assertTrue(view.hasVisibleTextSelection())
            val selected = view.text as Spanned
            assertTrue(Selection.getSelectionEnd(selected) > Selection.getSelectionStart(selected))
        } finally {
            down.recycle()
        }
    }

    @Test
    fun markdownHeadingSelection_resolvesBackToSourceForHighlight() {
        val context = RuntimeEnvironment.getApplication()
        val source = "## (1) 准确率奖励\n\n正文内容"
        val markwon = ReaderMarkwonFactory.create(context)
        val rendered = markwon.toMarkdown(source) as Spanned
        val heading = rendered.getSpans(0, rendered.length, HeadingSpan::class.java).single()
        val titleStart = rendered.toString().indexOf("准确率奖励", rendered.getSpanStart(heading))
        val selected = "准确率奖励"
        val resolved = resolveSourceSpanForDisplayedSelection(
            displayed = rendered,
            displayedStart = titleStart,
            displayedEnd = titleStart + selected.length,
            selectedText = selected,
            source = source,
            renderPlainText = false,
            windowStart = 0,
            windowEnd = source.length,
            toc = listOf(MarkdownTocEntry(2, "(1) 准确率奖励", 0)),
        )
        assertNotNull("heading selection should map to source", resolved)
        assertEquals(source.indexOf("准确率奖励"), resolved!!.start)
    }

    @Test
    fun markdownHeadingSelection_resolvesWithoutLoadedToc() {
        val context = RuntimeEnvironment.getApplication()
        val source = "### 图 11.5 奖励函数设计\n\n正文内容"
        val rendered = ReaderMarkwonFactory.create(context).toMarkdown(source) as Spanned
        val heading = rendered.getSpans(0, rendered.length, HeadingSpan::class.java).single()
        val start = rendered.toString().indexOf("奖励函数设计", rendered.getSpanStart(heading))
        val resolved = resolveSourceSpanForDisplayedSelection(
            displayed = rendered,
            displayedStart = start,
            displayedEnd = start + "奖励函数设计".length,
            selectedText = "奖励函数设计",
            source = source,
            renderPlainText = false,
            windowStart = 0,
            windowEnd = source.length,
            toc = emptyList(),
        )
        assertNotNull(resolved)
        assertEquals(source.indexOf("奖励函数设计"), resolved!!.start)
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

    @Test
    fun outsideDownWhileStylePickerShowing_dismissesSelection() {
        activateSelection()
        textView.highlightStylePickerShowing = true
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
        } finally {
            down.recycle()
        }
        assertFalse(textView.isInTextSelection())
        assertFalse(textView.hasVisibleTextSelection())
        assertFalse(textView.highlightStylePickerShowing)
    }

    @Test
    fun outsideDownAfterHighlight_keepsActiveDraftThroughDatabaseRefresh() {
        activateSelection()
        val start = textView.text.indexOf("选中")
        val draftId = -101L
        textView.highlightStylePickerShowing = true
        textView.registerDraftHighlight(draftId)
        applyHighlightDecorationAtRange(
            textView = textView,
            start = start,
            end = start + 2,
            colorArgb = 0xFFFFFF00.toInt(),
            style = space.liushenme.markdownreader.model.HighlightStyle.Background,
            highlightId = draftId,
        )

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
        } finally {
            down.recycle()
        }
        refreshReaderHighlightSpans(
            textView = textView,
            highlights = emptyList(),
            highlightColorArgb = 0xFFFFFF00.toInt(),
            sourceContentLength = textView.text.length,
            highlightSig = "outside-tap-draft",
        )

        assertFalse(textView.isInTextSelection())
        assertTrue(textView.isDraftHighlightActive(draftId))
        val text = textView.text as android.text.Spanned
        val spans = text.getSpans(0, text.length, HighlightBackgroundSpan::class.java)
        assertEquals(listOf(draftId), spans.map { it.highlightId })
        assertEquals(start, text.getSpanStart(spans.single()))
        assertEquals(start + 2, text.getSpanEnd(spans.single()))
    }

    @Test
    fun systemClosesMenuWhileStylePickerOpen_clearsSelection() {
        activateSelection()
        textView.highlightStylePickerShowing = true
        textView.simulateSystemActionModeDestroyForTest()
        assertFalse(textView.isInTextSelection())
        assertFalse(textView.hasVisibleTextSelection())
        assertFalse(textView.highlightStylePickerShowing)
    }

    @Test
    fun tapHighlight_selectsRange_outsideTapClearsIt() {
        val view = wrappingReaderTextView("hello world")
        val text = view.text as SpannableString
        val start = text.indexOf("world")
        text.setSpan(
            HighlightBackgroundSpan(0xFFFFFF00.toInt(), highlightId = 3L),
            start,
            start + 5,
            android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
        val layout = view.layout!!
        val line = layout.getLineForOffset(start)
        val y = (layout.getLineTop(line) + layout.getLineBottom(line)) / 2f + view.extendedPaddingTop
        var x = view.totalPaddingLeft + 1f
        var hitX = -1f
        while (x < view.width) {
            val offset = ReaderTextSelectionTouch.offsetNearestCharOnTextView(view, x, y)
            if (offset != null && offset in start until start + 5) {
                hitX = x
                break
            }
            x += 1f
        }
        assertTrue("no touch point maps onto the highlight", hitX >= 0f)
        assertTrue(view.selectCoveringHighlightAt(hitX, y))
        assertTrue(view.isInTextSelection())
        assertEquals(start, Selection.getSelectionStart(text))
        assertEquals(start + 5, Selection.getSelectionEnd(text))
        val menu = PopupMenu(view.context, view).menu
        view.populateSelectionMenuForTest(menu)
        assertEquals(SafeReaderTextView.MENU_ID_CANCEL_HIGHLIGHT, menu.getItem(1).itemId)

        val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 8f, view.height - 4f, 0)
        val up = MotionEvent.obtain(0, 40, MotionEvent.ACTION_UP, 8f, view.height - 4f, 0)
        try {
            view.onTouchEvent(down)
            view.onTouchEvent(up)
        } finally {
            down.recycle()
            up.recycle()
        }
        assertFalse(view.isInTextSelection())
        assertFalse(view.hasVisibleTextSelection())
    }
}
