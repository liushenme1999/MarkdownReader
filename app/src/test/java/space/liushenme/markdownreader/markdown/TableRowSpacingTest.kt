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
    fun table_consecutiveRows_haveNoLargeGap() {
        assertTableRowsHaveNoLargeGap(tableMd, widthPx = 1200, lineSpacing = 1.5f)
    }

    @Test
    fun compactTable_onWideScreen_hasNoLargeGap() {
        val compactTable = """
            | A | B | C |
            | --- | --- | --- |
            | 1 | 2 | 3 |
            | 4 | 5 | 6 |
            | 7 | 8 | 9 |
        """.trimIndent()
        assertTableRowsHaveNoLargeGap(compactTable, widthPx = 1400, lineSpacing = 1.5f)
    }

    @Test
    fun table_gapUpdatesWhenLineSpacingChanges() {
        val compactTable = """
            | X | Y |
            | --- | --- |
            | a | b |
            | c | d |
        """.trimIndent()
        val gapAt15 = measureMaxTableRowGap(compactTable, widthPx = 1400, lineSpacing = 1.5f)
        assertTrue("gap at 1.5x should be <= 1, was $gapAt15", gapAt15 <= 1)
        val gapAt10 = measureMaxTableRowGap(compactTable, widthPx = 1400, lineSpacing = 1.0f)
        assertTrue("gap at 1.0x should be <= 1, was $gapAt10", gapAt10 <= 1)
    }

    private fun assertTableRowsHaveNoLargeGap(markdown: String, widthPx: Int, lineSpacing: Float) {
        val gap = measureMaxTableRowGap(markdown, widthPx, lineSpacing)
        assertTrue("max gap is $gap px at width=$widthPx spacing=$lineSpacing", gap <= 1)
    }

    private fun measureMaxTableRowGap(markdown: String, widthPx: Int, lineSpacing: Float): Int {
        val context: Context = RuntimeEnvironment.getApplication()
        ReaderTableSpacing.lineSpacingMultiplier = lineSpacing
        val markwon = ReaderMarkwonFactory.create(context)
        val prepared = ReaderMarkwonFactory.prepareMarkdown(markdown)
        val rendered = markwon.toMarkdown(prepared.text) as Spanned
        val tv = TextView(context).apply {
            includeFontPadding = false
            textSize = 18f
            setLineSpacing(0f, lineSpacing)
            text = rendered
            measure(
                android.view.View.MeasureSpec.makeMeasureSpec(widthPx, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED),
            )
        }
        val layout = requireNotNull(tv.layout)
        val rows = rendered.getSpans(0, rendered.length, ReaderTableRowSpan::class.java)
            .sortedBy { rendered.getSpanStart(it) }
        require(rows.size >= 2) { "need 2+ rows" }
        val lineIndices = rows.map { row -> layout.getLineForOffset(rendered.getSpanStart(row)) }
        var maxGap = 0
        for (i in 0 until lineIndices.size - 1) {
            val gap = layout.getLineTop(lineIndices[i + 1]) - layout.getLineBottom(lineIndices[i])
            maxGap = maxOf(maxGap, gap)
        }
        return maxGap
    }
}
