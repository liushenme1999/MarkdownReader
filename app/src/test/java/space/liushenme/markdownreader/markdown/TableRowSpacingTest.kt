package space.liushenme.markdownreader.markdown

import android.content.Context
import android.text.Spanned
import android.widget.TextView
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class TableRowSpacingTest {

    private val tableMd = """
        <strong>总结：</strong>
        | 特性         | MHA (Multi-Head Attention) | MQA (Multi-Query Attention) | GQA (Grouped-Query Attention) |
        | :----------- | :------------------------- | :-------------------------- | :---------------------------- |
        | <strong>结构</strong>     | N个Q头, N个K头, N个V头     | N个Q头, 1个K头, 1个V头      | N个Q头, G个K头, G个V头        |
        | <strong>模型质量</strong> | 最高                       | 可能下降                    | 接近MHA，优于MQA              |
        | <strong>推理效率</strong> | 最低 (KV Cache大)          | 最高 (KV Cache小)           | 居中，远好于MHA               |
        | <strong>应用</strong>     | BERT, GPT-3                | PaLM                        | Llama 2, Mixtral              |
    """.trimIndent()

    @Test
    fun table_usesReaderTableRowSpan() {
        val context: Context = RuntimeEnvironment.getApplication()
        val markwon = ReaderMarkwonFactory.create(context)
        val prepared = ReaderMarkwonFactory.prepareMarkdown(tableMd)
        val rendered = markwon.toMarkdown(prepared.text) as Spanned
        val rowSpans = rendered.getSpans(0, rendered.length, ReaderTableRowSpan::class.java)
        assertTrue("expected ReaderTableRowSpan, got ${rowSpans.size}", rowSpans.size >= 5)
    }

    @Test
    fun table_rowHeight_notInflatedByLineSpacingMultiplier() {
        val context: Context = RuntimeEnvironment.getApplication()
        ReaderTableSpacing.lineSpacingMultiplier = 1.5f
        val markwon = ReaderMarkwonFactory.create(context)
        val prepared = ReaderMarkwonFactory.prepareMarkdown(tableMd)
        val rendered = markwon.toMarkdown(prepared.text) as Spanned
        val tv = TextView(context).apply {
            setLineSpacing(0f, 1.5f)
            text = rendered
            measure(
                android.view.View.MeasureSpec.makeMeasureSpec(1080, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED),
            )
        }
        val layout = requireNotNull(tv.layout)
        val rows = rendered.getSpans(0, rendered.length, ReaderTableRowSpan::class.java)
            .sortedBy { rendered.getSpanStart(it) }
        require(rows.size >= 3) { "need 3+ rows" }
        val heights = rows.take(3).map { row ->
            val line = layout.getLineForOffset(rendered.getSpanStart(row))
            layout.getLineBottom(line) - layout.getLineTop(line)
        }
        val maxHeight = heights.maxOrNull() ?: 0
        val minHeight = heights.minOrNull() ?: 0
        assertTrue("heights=$heights max/min=${maxHeight.toFloat() / minHeight.coerceAtLeast(1)}", maxHeight < minHeight * 2)
    }
}
