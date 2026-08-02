package space.liushenme.markdownreader.markdown

import android.content.Context
import android.text.Spanned
import io.noties.markwon.image.AsyncDrawableSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
class LatexInHtmlDivTest {

    @Before
    fun initJlatex() {
        JLatexMathAndroid.init(RuntimeEnvironment.getApplication())
    }

    private val blockInDiv = """
        3.  <strong>缩放（Scaling）：</strong> 将计算出的分数除以一个缩放因子 ${'$'}\\sqrt{d_k}${'$'}（ ${'$'}d_k${'$'} 是K向量的维度）。这一步是为了在反向传播时获得更稳定的梯度，防止点积结果过大导致Softmax函数进入饱和区。
            <div align="center">
            ${'$'}${'$'}\\frac{Q \\cdot K^T}{\\sqrt{d_k}}${'$'}${'$'}
            </div>
    """.trimIndent()

    private val blockPlain = """
        3.  **缩放（Scaling）：** 将计算出的分数除以一个缩放因子 ${'$'}\\sqrt{d_k}${'$'}（ ${'$'}d_k${'$'} 是K向量的维度）。

        ${'$'}${'$'}
        \\frac{Q \\cdot K^T}{\\sqrt{d_k}}
        ${'$'}${'$'}
    """.trimIndent()

    private val blockInDivWithFollowingItems = """
        3.  <strong>缩放（Scaling）：</strong> 将计算出的分数除以一个缩放因子 ${'$'}\\sqrt{d_k}${'$'}（ ${'$'}d_k${'$'} 是K向量的维度）。这一步是为了在反向传播时获得更稳定的梯度，防止点积结果过大导致Softmax函数进入饱和区。
            <div align="center">
            ${'$'}${'$'}\\frac{Q \\cdot K^T}{\\sqrt{d_k}}${'$'}${'$'}
            </div>
        4.  <strong>下一步：</strong> 后续正文应保持普通段落格式，不应变成等宽代码块。
        5.  普通列表项继续。
    """.trimIndent()

    @Test
    fun blockLatexInsideHtmlDiv_doesNotRenderFollowingTextAsCodeBlock() {
        val context: Context = RuntimeEnvironment.getApplication()
        val markwon = ReaderMarkwonFactory.create(context)
        val md = """
            3.  <strong>缩放（Scaling）：</strong> 因子 ${'$'}\\sqrt{d_k}${'$'}。
                <div align="center">
                ${'$'}${'$'}\\frac{Q \\cdot K^T}{\\sqrt{d_k}}${'$'}${'$'}
                </div>
                4.  <strong>Softmax：</strong> 后续正文不应是等宽代码块。
        """.trimIndent()
        val prepared = ReaderMarkwonFactory.prepareMarkdown(md, context)
        assertFalse(prepared.text.contains("\n    4.  <strong>Softmax"))
        val rendered = markwon.toMarkdown(prepared.text)
        val monoRanges = (rendered as? android.text.Spanned)
            ?.getSpans(0, rendered.length, android.text.style.TypefaceSpan::class.java)
            .orEmpty()
            .filter { it.family.equals("monospace", ignoreCase = true) }
        val softMaxIdx = rendered.toString().indexOf("Softmax")
        assertTrue(softMaxIdx >= 0)
        val inMono = monoRanges.any { span ->
            val start = (rendered as android.text.Spanned).getSpanStart(span)
            val end = (rendered as android.text.Spanned).getSpanEnd(span)
            softMaxIdx in start until end
        }
        assertFalse("Softmax paragraph should not use monospace: $rendered", inMono)
    }

    @Test
    fun blockLatexInsideHtmlDiv_doesNotBreakFollowingOrderedListItems() {
        val context: Context = RuntimeEnvironment.getApplication()
        val markwon = ReaderMarkwonFactory.create(context)
        val prepared = ReaderMarkwonFactory.prepareMarkdown(blockInDivWithFollowingItems, context)
        assertTrue(prepared.text.contains("\n4.  <strong>下一步"))
        assertFalse(
            "following list items should not stay indented after block latex",
            prepared.text.contains("\n    4.  <strong>下一步"),
        )
        val rendered = markwon.toMarkdown(prepared.text).toString()
        assertTrue("rendered=$rendered", rendered.contains("下一步"))
        assertTrue(rendered.contains("普通列表项继续"))
        assertFalse("block latex should render: $rendered", rendered.contains("$$"))
    }

    @Test
    fun blockLatexInsideHtmlDiv_rendersAfterPreprocessorUnwrapsDiv() {
        val context: Context = RuntimeEnvironment.getApplication()
        val markwon = ReaderMarkwonFactory.create(context)
        val prepared = ReaderMarkwonFactory.prepareMarkdown(blockInDiv, context)
        assertFalse(
            "preprocessor should unwrap div around block latex",
            prepared.text.contains("<div align=\"center\">", ignoreCase = true),
        )
        val rendered = markwon.toMarkdown(prepared.text).toString()
        assertFalse("block latex should render: $rendered", rendered.contains("$$"))
    }

    @Test
    fun inlineLatexInSameParagraphWithHtml_stillParses() {
        val context: Context = RuntimeEnvironment.getApplication()
        val markwon = ReaderMarkwonFactory.create(context)
        val inline = "因子 ${'$'}\\sqrt{d_k}${'$'} 与 ${'$'}d_k${'$'}"
        val prepared = ReaderMarkwonFactory.prepareMarkdown(inline, context)
        val rendered = markwon.toMarkdown(prepared.text) as Spanned
        // 占位文本会刻意保留 `$…$` 以便复制，不能用 toString 是否含 `$` 判断是否解析成功
        val latexSpans = rendered.getSpans(0, rendered.length, AsyncDrawableSpan::class.java)
        assertEquals(
            "both inline latex formulas should become spans: $rendered",
            2,
            latexSpans.size,
        )
    }

    @Test
    fun blockLatexOnOwnLines_rendersWithoutRawBlockDelimiters() {
        val context: Context = RuntimeEnvironment.getApplication()
        val markwon = ReaderMarkwonFactory.create(context)
        val prepared = ReaderMarkwonFactory.prepareMarkdown(blockPlain, context)
        val rendered = markwon.toMarkdown(prepared.text).toString()
        assertFalse("should not keep raw $$ delimiters: $rendered", rendered.contains("$$"))
    }
}
