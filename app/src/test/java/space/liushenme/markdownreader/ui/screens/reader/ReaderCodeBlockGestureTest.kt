package space.liushenme.markdownreader.ui.screens.reader

import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import io.noties.markwon.Markwon
import org.junit.After
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
