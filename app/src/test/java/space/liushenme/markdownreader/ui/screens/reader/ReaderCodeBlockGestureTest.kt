package space.liushenme.markdownreader.ui.screens.reader

import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import io.noties.markwon.Markwon
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import space.liushenme.markdownreader.markdown.ReaderCodeBlockSettings
import space.liushenme.markdownreader.markdown.ReaderMarkwonFactory
import space.liushenme.markdownreader.markdown.ReaderScrollableCodeBlockSpan

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ReaderCodeBlockGestureTest {

    @After
    fun tearDown() {
        ReaderCodeBlockSettings.wrapEnabled = true
        ReaderCodeBlockSettings.viewportWidthPx = 0
    }

    @Test
    fun dispatchTouchEvent_scrollsOverflowingCodeBlock() {
        val context = RuntimeEnvironment.getApplication()
        ReaderCodeBlockSettings.wrapEnabled = false
        ReaderCodeBlockSettings.viewportWidthPx = 352
        val markwon: Markwon = ReaderMarkwonFactory.create(context)
        val longLine = "val result = " + "abcdefghij".repeat(60)
        val rendered = markwon.toMarkdown("```kotlin\n$longLine\n```")
        val span = rendered
            .getSpans(0, rendered.length, ReaderScrollableCodeBlockSpan::class.java)
            .single()

        val textView = SafeReaderTextView(context).apply {
            textSize = 18f
            setPadding(24, 24, 24, 24)
        }
        markwon.setParsedMarkdown(textView, rendered)
        val parent = FrameLayout(context).apply { addView(textView) }
        parent.measure(exactly(400), exactly(800))
        parent.layout(0, 0, 400, 800)
        textView.measure(exactly(400), exactly(700))
        textView.layout(0, 0, 400, 700)

        bindReaderGesturesAndScroll(
            textView = textView,
            touchState = ReaderTouchState(),
            slop = 8,
            allowVerticalScroll = true,
            onScroll = {},
            onReadingVerticalScroll = {},
            onSwipeRightBookmark = {},
            onCenterTap = {},
        )

        val spanned = textView.text as android.text.Spanned
        val offset = spanned.getSpanStart(span)
        val line = textView.layout.getLineForOffset(offset)
        val y = textView.totalPaddingTop +
            (textView.layout.getLineTop(line) + textView.layout.getLineBottom(line)) / 2f
        val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 300f, y, 0)
        val move = MotionEvent.obtain(0, 30, MotionEvent.ACTION_MOVE, 120f, y, 0)
        val up = MotionEvent.obtain(0, 50, MotionEvent.ACTION_UP, 120f, y, 0)
        try {
            assertTrue(textView.dispatchTouchEvent(down))
            assertTrue(textView.dispatchTouchEvent(move))
            textView.dispatchTouchEvent(up)
        } finally {
            down.recycle()
            move.recycle()
            up.recycle()
        }

        assertTrue("scrollX=${span.scrollX}, max=${span.maxScrollX()}", span.scrollX > 0)
    }

    @Test
    fun longPressInsideCodeBlock_selectsInnerCharacters() {
        val context = RuntimeEnvironment.getApplication()
        ReaderCodeBlockSettings.wrapEnabled = true
        ReaderCodeBlockSettings.viewportWidthPx = 400
        val markwon: Markwon = ReaderMarkwonFactory.create(context)
        val rendered = markwon.toMarkdown("```kotlin\nval hello = 1\n```")
        val span = rendered
            .getSpans(0, rendered.length, ReaderScrollableCodeBlockSpan::class.java)
            .single()
        val textView = SafeReaderTextView(context).apply {
            textSize = 18f
            setPadding(24, 24, 24, 24)
        }
        markwon.setParsedMarkdown(textView, rendered)
        val parent = FrameLayout(context).apply { addView(textView) }
        parent.measure(exactly(400), exactly(800))
        parent.layout(0, 0, 400, 800)
        textView.measure(exactly(400), exactly(700))
        textView.layout(0, 0, 400, 700)

        val spanned = textView.text as android.text.Spanned
        val offset = spanned.getSpanStart(span)
        val line = textView.layout.getLineForOffset(offset)
        val y = textView.totalPaddingTop +
            textView.layout.getLineTop(line) + span.headerHeightPx(textView.paint) + 16f
        val x = textView.totalPaddingLeft + 36f
        val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, x, y, 0)
        try {
            textView.onTouchEvent(down)
            assertTrue(textView.isSelectionLongPressScheduledForTest())
            assertTrue(textView.fireScheduledSelectionLongPressForTest())
            val selected = textView.codeSelectionSpanForTest()
            assertTrue(selected != null && selected.hasSelection())
            assertTrue(selected!!.selectedText().isNotEmpty())
            val rect = textView.selectionActionModeRectForTest()
            assertTrue("action mode rect missing", rect != null)
            val blockTop = textView.totalPaddingTop + textView.layout.getLineTop(line)
            val header = span.headerHeightPx(textView.paint)
            assertTrue(
                "copy menu should hug selected code, not the block top. rect=$rect blockTop=$blockTop",
                rect!!.top >= blockTop + header / 2,
            )
            assertTrue(
                "copy menu anchor should not span the whole code block, h=${rect.height()}",
                rect.height() < 120,
            )
        } finally {
            down.recycle()
        }
    }

    @Test
    fun tapInsideCodeBlockAwayFromSelection_clearsSelection() {
        val context = RuntimeEnvironment.getApplication()
        ReaderCodeBlockSettings.wrapEnabled = true
        ReaderCodeBlockSettings.viewportWidthPx = 400
        val markwon: Markwon = ReaderMarkwonFactory.create(context)
        val rendered = markwon.toMarkdown("```kotlin\nval hello = 1\nsecond line here\n```")
        val span = rendered
            .getSpans(0, rendered.length, ReaderScrollableCodeBlockSpan::class.java)
            .single()
        val textView = layoutReader(context, markwon, rendered)

        val spanned = textView.text as android.text.Spanned
        val offset = spanned.getSpanStart(span)
        val line = textView.layout.getLineForOffset(offset)
        val y = textView.totalPaddingTop +
            textView.layout.getLineTop(line) + span.headerHeightPx(textView.paint) + 16f
        val selectX = textView.totalPaddingLeft + 36f
        val otherX = textView.width - textView.totalPaddingRight - 24f
        val otherY = y
        assertTrue(textView.scrollableCodeBlockSpanAt(otherX, otherY) != null)
        val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, selectX, y, 0)
        val up = MotionEvent.obtain(0, 40, MotionEvent.ACTION_UP, selectX, y, 0)
        val tapDown = MotionEvent.obtain(80, 80, MotionEvent.ACTION_DOWN, otherX, otherY, 0)
        val tapUp = MotionEvent.obtain(80, 100, MotionEvent.ACTION_UP, otherX, otherY, 0)
        try {
            textView.onTouchEvent(down)
            assertTrue(textView.fireScheduledSelectionLongPressForTest())
            assertTrue(textView.codeSelectionSpanForTest()?.hasSelection() == true)
            textView.onTouchEvent(up)
            assertTrue(textView.codeSelectionSpanForTest()?.hasSelection() == true)

            textView.onTouchEvent(tapDown)
            textView.onTouchEvent(tapUp)
            assertTrue(
                "code selection should be cleared",
                textView.codeSelectionSpanForTest() == null ||
                    textView.codeSelectionSpanForTest()?.hasSelection() != true,
            )
            assertTrue(!textView.hasVisibleTextSelection())
        } finally {
            down.recycle()
            up.recycle()
            tapDown.recycle()
            tapUp.recycle()
        }
    }

    @Test
    fun tapCenterOnCodeBlock_invokesCenterChromeCallback() {
        val context = RuntimeEnvironment.getApplication()
        ReaderCodeBlockSettings.wrapEnabled = true
        ReaderCodeBlockSettings.viewportWidthPx = 400
        val markwon: Markwon = ReaderMarkwonFactory.create(context)
        val body = (1..40).joinToString("\n") { "code line $it content" }
        val rendered = markwon.toMarkdown("```\n$body\n```")
        val textView = layoutReader(context, markwon, rendered)
        var centerTapped = 0
        textView.onReaderCenterTap = { centerTapped += 1 }

        val x = textView.width / 2f
        val y = textView.height / 2f
        assertTrue(textView.scrollableCodeBlockSpanAt(x, y) != null)
        val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, x, y, 0)
        val up = MotionEvent.obtain(0, 40, MotionEvent.ACTION_UP, x, y, 0)
        try {
            textView.onTouchEvent(down)
            textView.onTouchEvent(up)
        } finally {
            down.recycle()
            up.recycle()
        }
        assertTrue("center tap should toggle chrome, count=$centerTapped", centerTapped == 1)
    }

    @Test
    fun processTextExport_usesInnerCodeSelectionNotObjectReplacement() {
        val context = RuntimeEnvironment.getApplication()
        ReaderCodeBlockSettings.wrapEnabled = true
        ReaderCodeBlockSettings.viewportWidthPx = 400
        val markwon: Markwon = ReaderMarkwonFactory.create(context)
        val rendered = markwon.toMarkdown("```kotlin\nval hello = 1\n```")
        val span = rendered
            .getSpans(0, rendered.length, ReaderScrollableCodeBlockSpan::class.java)
            .single()
        val textView = layoutReader(context, markwon, rendered)
        val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 40f, 80f, 0)
        try {
            textView.onTouchEvent(down)
            assertTrue(textView.fireScheduledSelectionLongPressForTest())
            val idx = span.rawCode.indexOf("hello")
            textView.setCodeInnerSelectionForTest(idx, idx + 5)
            val body = textView.text as android.text.Spanned
            val start = body.getSpanStart(span)
            val end = body.getSpanEnd(span)
            assertEquals("hello", body.subSequence(start, end).toString())
            assertEquals("hello", textView.currentSelectedTextForTest())
        } finally {
            down.recycle()
        }
    }

    @Test
    fun codeHandleDrag_keepsOppositeEndWhenExtendingStartThenEnd() {
        val context = RuntimeEnvironment.getApplication()
        ReaderCodeBlockSettings.wrapEnabled = true
        ReaderCodeBlockSettings.viewportWidthPx = 400
        val markwon: Markwon = ReaderMarkwonFactory.create(context)
        val rendered = markwon.toMarkdown("```\nabcdefghijklmnop\n```")
        val span = rendered
            .getSpans(0, rendered.length, ReaderScrollableCodeBlockSpan::class.java)
            .single()
        val textView = layoutReader(context, markwon, rendered)
        val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 40f, 80f, 0)
        val up = MotionEvent.obtain(0, 40, MotionEvent.ACTION_UP, 40f, 80f, 0)
        try {
            textView.onTouchEvent(down)
            assertTrue(textView.fireScheduledSelectionLongPressForTest())
            textView.onTouchEvent(up)
            val code = span.rawCode
            assertTrue(code.length >= 16)
            // 第一次从前往后：选中 [4, 10)，锚点仍停在起点 4
            textView.setCodeInnerSelectionForTest(start = 4, end = 10, anchor = 4)
            assertEquals(code.substring(4, 10), span.selectedText())
            assertTrue(!textView.wouldExtendCodeSelectionOnMoveForTest())

            // 再从起点往前拖：应固定末尾
            assertTrue(
                textView.dragCodeSelectionHandleToOffsetForTest(
                    ReaderTextSelectionTouch.SelectionHandle.START,
                    0,
                ),
            )
            assertEquals(code.substring(0, 10), span.selectedText())

            // 从末尾往后拖：应固定起点
            assertTrue(
                textView.dragCodeSelectionHandleToOffsetForTest(
                    ReaderTextSelectionTouch.SelectionHandle.END,
                    14,
                ),
            )
            assertEquals(code.substring(0, 15), span.selectedText())
        } finally {
            down.recycle()
            up.recycle()
        }
    }

    @Test
    fun codeHandleDrag_firstBackwardThenExtendEndKeepsStart() {
        val context = RuntimeEnvironment.getApplication()
        ReaderCodeBlockSettings.wrapEnabled = true
        ReaderCodeBlockSettings.viewportWidthPx = 400
        val markwon: Markwon = ReaderMarkwonFactory.create(context)
        val rendered = markwon.toMarkdown("```\nabcdefghijklmnop\n```")
        val span = rendered
            .getSpans(0, rendered.length, ReaderScrollableCodeBlockSpan::class.java)
            .single()
        val textView = layoutReader(context, markwon, rendered)
        val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 40f, 80f, 0)
        val up = MotionEvent.obtain(0, 40, MotionEvent.ACTION_UP, 40f, 80f, 0)
        try {
            textView.onTouchEvent(down)
            assertTrue(textView.fireScheduledSelectionLongPressForTest())
            textView.onTouchEvent(up)
            val code = span.rawCode
            assertTrue(code.length >= 16)
            // 第一次从后往前：选中 [4, 10)，锚点停在原按下的末尾
            textView.setCodeInnerSelectionForTest(start = 4, end = 10, anchor = 9)
            assertTrue(
                textView.dragCodeSelectionHandleToOffsetForTest(
                    ReaderTextSelectionTouch.SelectionHandle.END,
                    14,
                ),
            )
            assertEquals(code.substring(4, 15), span.selectedText())
        } finally {
            down.recycle()
            up.recycle()
        }
    }

    @Test
    fun unwrappedCode_dragSelectionAtRightEdge_scrollsToKeepHandleVisible() {
        val context = RuntimeEnvironment.getApplication()
        ReaderCodeBlockSettings.wrapEnabled = false
        ReaderCodeBlockSettings.viewportWidthPx = 352
        val markwon: Markwon = ReaderMarkwonFactory.create(context)
        val longLine = "val result = " + "abcdefghij".repeat(60)
        val rendered = markwon.toMarkdown("```kotlin\n$longLine\n```")
        val span = rendered
            .getSpans(0, rendered.length, ReaderScrollableCodeBlockSpan::class.java)
            .single()
        val textView = layoutReader(context, markwon, rendered)
        val spanned = textView.text as android.text.Spanned
        val offset = spanned.getSpanStart(span)
        val line = textView.layout.getLineForOffset(offset)
        val y = textView.totalPaddingTop +
            textView.layout.getLineTop(line) + span.headerHeightPx(textView.paint) + 16f
        val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 40f, y, 0)
        val up = MotionEvent.obtain(0, 40, MotionEvent.ACTION_UP, 40f, y, 0)
        try {
            textView.dispatchTouchEvent(down)
            assertTrue(textView.fireScheduledSelectionLongPressForTest())
            textView.dispatchTouchEvent(up)
            textView.setCodeInnerSelectionForTest(start = 0, end = 2, anchor = 0)
            val beforeScroll = span.scrollX
            val beforeEnd = span.selectionEnd
            val edgeX = (textView.width - 2).toFloat()
            repeat(10) {
                textView.extendCodeBlockSelectionToForTest(edgeX, y)
            }
            assertTrue(
                "right-edge drag should pan the code, scrollX=${span.scrollX}",
                span.scrollX > beforeScroll,
            )
            assertTrue(
                "selection should grow with the pan, end=${span.selectionEnd}",
                span.selectionEnd > beforeEnd,
            )
            assertTrue(textView.codeBlockScrollXForTest() == span.scrollX)
        } finally {
            down.recycle()
            up.recycle()
        }
    }

    @Test
    fun unwrappedCode_dragSelectionAtLeftEdge_scrollsBack() {
        val context = RuntimeEnvironment.getApplication()
        ReaderCodeBlockSettings.wrapEnabled = false
        ReaderCodeBlockSettings.viewportWidthPx = 352
        val markwon: Markwon = ReaderMarkwonFactory.create(context)
        val longLine = "val result = " + "abcdefghij".repeat(60)
        val rendered = markwon.toMarkdown("```kotlin\n$longLine\n```")
        val span = rendered
            .getSpans(0, rendered.length, ReaderScrollableCodeBlockSpan::class.java)
            .single()
        val textView = layoutReader(context, markwon, rendered)
        val spanned = textView.text as android.text.Spanned
        val offset = spanned.getSpanStart(span)
        val line = textView.layout.getLineForOffset(offset)
        val y = textView.totalPaddingTop +
            textView.layout.getLineTop(line) + span.headerHeightPx(textView.paint) + 16f
        val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 40f, y, 0)
        val up = MotionEvent.obtain(0, 40, MotionEvent.ACTION_UP, 40f, y, 0)
        try {
            textView.dispatchTouchEvent(down)
            assertTrue(textView.fireScheduledSelectionLongPressForTest())
            textView.dispatchTouchEvent(up)
            assertTrue(span.scrollBy(180f))
            val start = 40
            textView.setCodeInnerSelectionForTest(start = start, end = start + 8, anchor = start + 7)
            val beforeScroll = span.scrollX
            val leftX = textView.totalPaddingLeft + 2f
            repeat(10) {
                textView.extendCodeBlockSelectionToForTest(leftX, y)
            }
            assertTrue(
                "left-edge drag should pan back, before=$beforeScroll after=${span.scrollX}",
                span.scrollX < beforeScroll,
            )
        } finally {
            down.recycle()
            up.recycle()
        }
    }

    private fun layoutReader(
        context: android.content.Context,
        markwon: Markwon,
        rendered: android.text.Spanned,
    ): SafeReaderTextView {
        val textView = SafeReaderTextView(context).apply {
            textSize = 18f
            setPadding(24, 24, 24, 24)
        }
        markwon.setParsedMarkdown(textView, rendered)
        val parent = FrameLayout(context).apply { addView(textView) }
        parent.measure(exactly(400), exactly(800))
        parent.layout(0, 0, 400, 800)
        textView.measure(exactly(400), exactly(700))
        textView.layout(0, 0, 400, 700)
        return textView
    }

    private fun exactly(px: Int): Int =
        View.MeasureSpec.makeMeasureSpec(px, View.MeasureSpec.EXACTLY)
}
