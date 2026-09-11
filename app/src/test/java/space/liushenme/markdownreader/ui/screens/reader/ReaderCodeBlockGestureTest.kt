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

    private fun exactly(px: Int): Int =
        View.MeasureSpec.makeMeasureSpec(px, View.MeasureSpec.EXACTLY)
}
