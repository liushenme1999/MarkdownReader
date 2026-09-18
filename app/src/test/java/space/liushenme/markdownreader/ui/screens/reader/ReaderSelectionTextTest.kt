package space.liushenme.markdownreader.ui.screens.reader

import android.content.Context
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
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ReaderSelectionTextTest {

    private val context: Context
        get() = RuntimeEnvironment.getApplication()

    @Test
    fun prepareInlineLatexPlaceholder_keepsCopyableSource() {
        assertEquals("\$E=mc^2\$", prepareInlineLatexPlaceholder("E=mc^2"))
        assertEquals("\$a+b\$", prepareInlineLatexPlaceholder("\$a+b\$"))
        assertEquals("\$x y\$", prepareInlineLatexPlaceholder("x\ny"))
    }

    @Test
    fun extract_plainText_unchanged() {
        val body = "你好世界"
        assertEquals("好世", extractReaderSelectionText(body, 1, 3, context))
    }

    @Test
    fun extract_skipsOrphanObjectReplacement() {
        val body = "前\uFFFC后"
        assertEquals("前后", extractReaderSelectionText(body, 0, body.length, context))
        assertFalse(readerSelectionHasActionableText("\uFFFC", 0, 1, context))
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

        assertEquals("\$\\otimes\$", extractReaderSelectionText(body, start, end, context))
        assertTrue(readerSelectionHasActionableText(body, start, end, context))
    }

    @Test
    fun formatInlineLatexSelectionText_wrapsDollarsOnce() {
        assertEquals("\$a\$", formatInlineLatexSelectionText("a"))
        assertEquals("\$a\$", formatInlineLatexSelectionText("\$a\$"))
    }

    @Test
    fun extract_codeBlockSelection_returnsSubstring() {
        val markwon = space.liushenme.markdownreader.markdown.ReaderMarkwonFactory.create(context)
        val rendered = markwon.toMarkdown("```kotlin\nval hello = 1\n```")
        val span = rendered.getSpans(
            0,
            rendered.length,
            space.liushenme.markdownreader.markdown.ReaderScrollableCodeBlockSpan::class.java,
        ).single()
        val start = rendered.getSpanStart(span)
        val end = rendered.getSpanEnd(span)
        val idx = span.rawCode.indexOf("hello")
        span.setSelection(idx, idx + 5)
        assertEquals("hello", extractReaderSelectionText(rendered, start, end, context))
        assertTrue(readerSelectionHasActionableText(rendered, start, end, context))
    }
}
