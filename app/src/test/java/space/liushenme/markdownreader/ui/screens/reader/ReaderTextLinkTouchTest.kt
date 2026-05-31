package space.liushenme.markdownreader.ui.screens.reader

import android.text.SpannableString
import android.text.style.URLSpan
import android.widget.TextView
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ReaderTextLinkTouchTest {

    @Test
    fun findClickableSpanAt_hitsUrlSpanOnLinkText() {
        val context = RuntimeEnvironment.getApplication()
        val textView = TextView(context).apply {
            val body = SpannableString("教程 Markdown教程 结束")
            val span = URLSpan("https://www.example.com")
            body.setSpan(span, 3, 11, SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE)
            text = body
            textSize = 16f
            setPadding(0, 0, 0, 0)
            measure(
                android.view.View.MeasureSpec.makeMeasureSpec(800, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(400, android.view.View.MeasureSpec.EXACTLY),
            )
            layout(0, 0, measuredWidth, measuredHeight)
        }
        val layout = textView.layout ?: error("layout required")
        val midX = (layout.getPrimaryHorizontal(7) + layout.getPrimaryHorizontal(8)) / 2f
        val line = layout.getLineForOffset(7)
        val y = layout.getLineBottom(line) - 2f
        val found = ReaderTextLinkTouch.findClickableSpanAt(textView, midX, y)
        assertNotNull(found)
        assertSame(URLSpan::class.java, found!!::class.java)
    }
}
