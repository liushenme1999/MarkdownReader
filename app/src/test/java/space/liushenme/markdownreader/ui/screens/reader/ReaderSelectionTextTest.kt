package space.liushenme.markdownreader.ui.screens.reader

import android.text.SpannableString
import android.text.Spanned
import android.text.style.ReplacementSpan
import android.graphics.Canvas
import android.graphics.Paint
import io.noties.markwon.ext.latex.prepareInlineLatexPlaceholder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ReaderSelectionTextTest {

    @Test
    fun prepareInlineLatexPlaceholder_keepsCopyableSource() {
        assertEquals("\$E=mc^2\$", prepareInlineLatexPlaceholder("E=mc^2"))
        assertEquals("\$a+b\$", prepareInlineLatexPlaceholder("\$a+b\$"))
        assertEquals("\$x y\$", prepareInlineLatexPlaceholder("x\ny"))
    }

    @Test
    fun extract_plainText_unchanged() {
        val body = "你好世界"
        assertEquals("好世", extractReaderSelectionText(body, 1, 3))
    }

    @Test
    fun extract_skipsOrphanObjectReplacement() {
        val body = "前\uFFFC后"
        assertEquals("前后", extractReaderSelectionText(body, 0, body.length))
        assertFalse(readerSelectionHasActionableText("\uFFFC", 0, 1))
    }

    @Test
    fun extract_replacementSpanWithLatexPlaceholder_isCopyable() {
        val placeholder = prepareInlineLatexPlaceholder("\\otimes")
        val body = SpannableString("结果$placeholder。")
        val start = body.indexOf('$')
        val end = start + placeholder.length
        body.setSpan(object : ReplacementSpan() {
            override fun getSize(
                paint: Paint,
                text: CharSequence?,
                start: Int,
                end: Int,
                fm: Paint.FontMetricsInt?,
            ): Int = 20

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
        }, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

        assertEquals("\$\\otimes\$", extractReaderSelectionText(body, start, end))
        assertTrue(readerSelectionHasActionableText(body, start, end))
    }

    @Test
    fun formatInlineLatexSelectionText_wrapsDollarsOnce() {
        assertEquals("\$a\$", formatInlineLatexSelectionText("a"))
        assertEquals("\$a\$", formatInlineLatexSelectionText("\$a\$"))
    }
}
