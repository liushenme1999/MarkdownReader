package space.liushenme.markdownreader.markdown

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.text.Spanned
import io.noties.markwon.image.AsyncDrawableSpan
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
class ReaderLatexBlockStyleTest {

    @Test
    fun blockBackground_isRoundedDrawable() {
        val context: Context = RuntimeEnvironment.getApplication()
        val drawable = ReaderLatexBlockStyle.blockBackground(context)
        assertTrue(drawable is GradientDrawable)
    }

    @Test
    fun centeredDivFormula_rendersWithoutRawDelimiters() {
        val context: Context = RuntimeEnvironment.getApplication()
        val markwon = ReaderMarkwonFactory.create(context)
        val md = """
            text
            <div align="center">
            ${'$'}${'$'}\\text{Score}(Q_i, K_j) = Q_i \\cdot K_j${'$'}${'$'}
            </div>
        """.trimIndent()
        val prepared = ReaderMarkwonFactory.prepareMarkdown(md, context)
        assertFalse(
            "single-line block should be expanded",
            prepared.text.contains("${'$'}${'$'}\\text{Score}"),
        )
        val rendered = markwon.toMarkdown(prepared.text)
        assertTrue(rendered.contains("Score"))
        assertFalse(rendered.contains("$$"))
        val spanned = rendered as Spanned
        val span = spanned.getSpans(0, spanned.length, AsyncDrawableSpan::class.java).single()
        assertEquals("JLatexAsyncDrawableSpan", span.javaClass.simpleName)
    }
}
