package space.liushenme.markdownreader.markdown

import android.content.Context
import android.text.Spanned
import io.noties.markwon.ext.latex.ReaderCompoundInlineLatexSpan
import io.noties.markwon.image.AsyncDrawableSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import ru.noties.jlatexmath.JLatexMathAndroid

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class OtimesParseTest {

    @Before
    fun initJlatex() {
        JLatexMathAndroid.init(RuntimeEnvironment.getApplication())
    }

    @Test
    fun userParagraph_usesFontFallbackForOtimes() {
        val context: Context = RuntimeEnvironment.getApplication()
        val markwon = ReaderMarkwonFactory.create(context)
        val md = """
            * <strong>工作原理：</strong> 它将前馈网络（FFN）的第一个线性层的输出 ${'$'}X${'$'} 分成两部分， ${'$'}A${'$'} 和 ${'$'}B${'$'} 。然后通过公式 ${'$'}Swish(A) \otimes B${'$'} 计算输出，其中 ${'$'}Swish(x) = x \cdot \sigma(x)${'$'} ， ${'$'}\sigma${'$'} 是Sigmoid函数， ${'$'}\otimes${'$'} 是逐元素相乘。
        """.trimIndent()
        val rendered = markwon.toMarkdown(ReaderMarkwonFactory.prepareMarkdown(md).text) as Spanned
        assertTrue(rendered.contains("\u2297"))
        assertEquals(1, rendered.getSpans(0, rendered.length, ReaderMathSymbolSpan::class.java).size)
        assertEquals(1, rendered.getSpans(0, rendered.length, ReaderCompoundInlineLatexSpan::class.java).size)
        assertEquals(5, rendered.getSpans(0, rendered.length, AsyncDrawableSpan::class.java).size)
    }

    @Test
    fun standaloneOtimes_usesReaderMathSymbolSpan() {
        val context: Context = RuntimeEnvironment.getApplication()
        val markwon = ReaderMarkwonFactory.create(context)
        val md = "符号 ${'$'}\\otimes${'$'} 结束"
        val rendered = markwon.toMarkdown(ReaderMarkwonFactory.prepareMarkdown(md).text) as Spanned
        assertTrue(rendered.getSpans(0, rendered.length, ReaderMathSymbolSpan::class.java).isNotEmpty())
    }

    @Test
    fun swishFormula_usesCompoundSpan() {
        val context: Context = RuntimeEnvironment.getApplication()
        val markwon = ReaderMarkwonFactory.create(context)
        val md = "公式 ${'$'}Swish(A) \\otimes B${'$'} 结束"
        val rendered = markwon.toMarkdown(ReaderMarkwonFactory.prepareMarkdown(md).text) as Spanned
        val compound = rendered.getSpans(0, rendered.length, ReaderCompoundInlineLatexSpan::class.java)
        assertEquals(1, compound.size)
        assertEquals(0, rendered.getSpans(0, rendered.length, AsyncDrawableSpan::class.java).size)
    }

    @Test
    fun parseCompound_splitsSwishOtimes() {
        val segments = ReaderMathSymbolFallback.parseCompound("Swish(A) \\otimes B")
        assertNotNull(segments)
        assertEquals(3, segments!!.size)
    }
}
