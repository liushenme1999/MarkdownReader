package space.liushenme.markdownreader.markdown

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.text.style.LineBackgroundSpan
import android.text.style.MetricAffectingSpan
import android.text.style.ReplacementSpan
import io.noties.markwon.image.AsyncDrawableSpan
import org.junit.Assert.assertEquals
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
    fun inlineCode_usesWrappableMetricSpan() {
        val context: Context = RuntimeEnvironment.getApplication()
        val markwon = ReaderMarkwonFactory.create(context)
        val md = "遵循`Pre-train -> Fine-tune`的流程。"
        val rendered = markwon.toMarkdown(ReaderMarkwonFactory.prepareMarkdown(md).text)
        val codeSpans = rendered.getSpans(0, rendered.length, ReaderInlineCodeSpan::class.java)
        assertTrue(
            "expected ReaderInlineCodeSpan, got ${
                rendered.getSpans(0, rendered.length, Any::class.java).map { it.javaClass.simpleName }
            }",
            codeSpans.isNotEmpty(),
        )
        assertTrue(codeSpans[0] is MetricAffectingSpan)
        assertTrue(codeSpans[0] is LineBackgroundSpan)
        assertTrue(rendered.contains("Pre-train"))
    }

    @Test
    fun inlineCode_longJson_keepsFullTextWithWrappableSpan() {
        val context: Context = RuntimeEnvironment.getApplication()
        val markwon = ReaderMarkwonFactory.create(context)
        val md =
            "函数执行完毕后，会返回一个结果（例如，`{\"temperature\": 32, \"condition\": \"sunny\"}`）。"
        val rendered = markwon.toMarkdown(ReaderMarkwonFactory.prepareMarkdown(md).text)
        val text = rendered.toString()
        assertTrue(text.contains("\"temperature\""))
        assertTrue(text.contains("sunny"))
        val codeSpans = rendered.getSpans(0, rendered.length, ReaderInlineCodeSpan::class.java)
        assertEquals(1, codeSpans.size)
        // ReplacementSpan 会把整段代码当成原子块，窄屏无法换行而裁切；必须可拆分
        assertTrue(codeSpans[0] is MetricAffectingSpan)
        assertEquals(
            0,
            rendered.getSpans(0, rendered.length, ReplacementSpan::class.java).size,
        )
    }

    @Test
    fun inlineCode_insideListItem_stillUsesWrappableSpan() {
        val context: Context = RuntimeEnvironment.getApplication()
        val markwon = ReaderMarkwonFactory.create(context)
        val md =
            "* 结果（例如，`{\"temperature\": 32, \"condition\": \"sunny\"}`）。"
        val rendered = markwon.toMarkdown(ReaderMarkwonFactory.prepareMarkdown(md).text)
        assertTrue(
            rendered.getSpans(0, rendered.length, ReaderInlineCodeSpan::class.java).isNotEmpty(),
        )
        assertTrue(
            rendered.getSpans(0, rendered.length, android.text.style.LeadingMarginSpan::class.java)
                .isNotEmpty(),
        )
    }

    @Test
    fun inlineLatex_usesVerticallyCenteredSpan() {
        val context: Context = RuntimeEnvironment.getApplication()
        val markwon = ReaderMarkwonFactory.create(context)
        val d = "$"
        val md = "共 ${d}N${d} 个头"
        val rendered = markwon.toMarkdown(ReaderMarkwonFactory.prepareMarkdown(md).text)
        val spans = rendered.getSpans(0, rendered.length, AsyncDrawableSpan::class.java)
        assertTrue(
            "expected ReaderInlineLatexSpan, got ${spans.map { it.javaClass.name }}",
            spans.any { it.javaClass.simpleName == "ReaderInlineLatexSpan" },
        )
    }
}
