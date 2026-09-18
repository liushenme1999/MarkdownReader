package space.liushenme.markdownreader.ui.screens.reader

import android.graphics.Canvas
import android.graphics.Paint
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ReplacementSpan
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
class ReaderTallLineViewportTest {

    @Test
    fun skipBringPointIntoView_whenTallLineAlreadyIntersectsViewport() {
        assertTrue(
            shouldSkipBringPointIntoViewForVisibleTallLine(
                lineTop = 0,
                lineBottom = 2000,
                scrollY = 800,
                innerHeight = 600,
            ),
        )
    }

    @Test
    fun allowBringPointIntoView_whenLineFitsInViewport() {
        assertFalse(
            shouldSkipBringPointIntoViewForVisibleTallLine(
                lineTop = 0,
                lineBottom = 40,
                scrollY = 800,
                innerHeight = 600,
            ),
        )
    }

    @Test
    fun allowBringPointIntoView_whenTallLineIsOffscreen() {
        assertFalse(
            shouldSkipBringPointIntoViewForVisibleTallLine(
                lineTop = 0,
                lineBottom = 2000,
                scrollY = 2500,
                innerHeight = 600,
            ),
        )
    }

    @Test
    fun tapDoesNotSnapPdfPageMiddleBackToPageStart() {
        val context = RuntimeEnvironment.getApplication()
        val pageHeight = 2000
        val viewportHeight = 600
        val midPageScroll = 800
        val body = SpannableString("\uFFFC")
        body.setSpan(TallLineSpan(width = 200, height = pageHeight), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        val textView = SafeReaderTextView(context).apply {
            setText(body, TextView.BufferType.SPANNABLE)
            textSize = 16f
            setPadding(0, 0, 0, 0)
            measure(
                android.view.View.MeasureSpec.makeMeasureSpec(400, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(viewportHeight, android.view.View.MeasureSpec.EXACTLY),
            )
            layout(0, 0, 400, viewportHeight)
        }
        val layout = textView.layout ?: error("layout missing")
        assertTrue(layout.getLineBottom(0) - layout.getLineTop(0) > viewportHeight)
        textView.scrollTo(0, midPageScroll)
        assertEquals(midPageScroll, textView.scrollY)

        val moved = textView.bringPointIntoView(0)

        assertFalse(moved)
        assertEquals(midPageScroll, textView.scrollY)
    }

    private class TallLineSpan(
        private val width: Int,
        private val height: Int,
    ) : ReplacementSpan() {
        override fun getSize(
            paint: Paint,
            text: CharSequence?,
            start: Int,
            end: Int,
            fm: Paint.FontMetricsInt?,
        ): Int {
            fm?.let {
                it.ascent = -height
                it.descent = 0
                it.top = -height
                it.bottom = 0
            }
            return width
        }

        override fun draw(
            canvas: Canvas,
            text: CharSequence?,
            start: Int,
            end: Int,
            x: Float,
            top: Int,
            y: Int,
            bottom: Int,
            paint: Paint,
        ) = Unit
    }
}
