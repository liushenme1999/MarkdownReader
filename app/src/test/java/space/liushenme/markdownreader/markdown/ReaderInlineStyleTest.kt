package space.liushenme.markdownreader.markdown

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.text.Spanned
import android.text.style.ReplacementSpan
import io.noties.markwon.image.AsyncDrawableSpan
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ReaderInlineStyleTest {

    @Test
    fun inlineCodeBackground_isRoundedDrawable() {
        val context: Context = RuntimeEnvironment.getApplication()
        assertTrue(ReaderLatexBlockStyle.inlineCodeBackground(context) is GradientDrawable)
    }

    @Test
    fun inlineLatexBackground_isRoundedDrawable() {
        val context: Context = RuntimeEnvironment.getApplication()
        assertTrue(ReaderLatexBlockStyle.inlineLatexBackground(context) is GradientDrawable)
    }

    @Test
    fun inlineCode_usesRoundedReplacementSpan() {
        val context: Context = RuntimeEnvironment.getApplication()
        val markwon = ReaderMarkwonFactory.create(context)
        val md = "遵循`Pre-train -> Fine-tune`的流程。"
        val rendered = markwon.toMarkdown(ReaderMarkwonFactory.prepareMarkdown(md).text) as Spanned
        val spans = rendered.getSpans(0, rendered.length, ReplacementSpan::class.java)
        assertTrue(
            "expected ReaderInlineCodeSpan, got ${spans.map { it.javaClass.simpleName }}",
            spans.any { it is ReaderInlineCodeSpan },
        )
        assertTrue(rendered.contains("Pre-train"))
    }

    @Test
    fun inlineLatex_usesVerticallyCenteredSpan() {
        val context: Context = RuntimeEnvironment.getApplication()
        val markwon = ReaderMarkwonFactory.create(context)
        val d = "$"
        val md = "共 ${d}N${d} 个头"
        val rendered = markwon.toMarkdown(ReaderMarkwonFactory.prepareMarkdown(md).text) as Spanned
        val spans = rendered.getSpans(0, rendered.length, AsyncDrawableSpan::class.java)
        assertTrue(
            "expected ReaderInlineLatexSpan, got ${spans.map { it.javaClass.name }}",
            spans.any { it.javaClass.simpleName == "ReaderInlineLatexSpan" },
        )
    }
}
