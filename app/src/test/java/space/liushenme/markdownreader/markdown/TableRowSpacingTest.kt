package space.liushenme.markdownreader.markdown

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
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
        val lineSpans = rendered.getSpans(0, rendered.length, ReaderTableRowLineHeightSpan::class.java)
        assertTrue("expected ReaderTableRowLineHeightSpan, got ${lineSpans.size}", lineSpans.size >= 5)
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
        assertTableRowsHaveNoLargeGap(compactTable, widthPx = 1400, lineSpacing = 1.0f)
    }

    @Test
    fun table_interRowBreak_usesZeroHeightSpan() {
        val compactTable = """
            | X | Y |
            | --- | --- |
            | a | b |
            | c | d |
        """.trimIndent()
        val context: Context = RuntimeEnvironment.getApplication()
        val markwon = ReaderMarkwonFactory.create(context)
        val prepared = ReaderMarkwonFactory.prepareMarkdown(compactTable)
        val rendered = markwon.toMarkdown(prepared.text) as Spanned
        val rows = rendered.getSpans(0, rendered.length, ReaderTableRowSpan::class.java)
            .sortedBy { rendered.getSpanStart(it) }
        require(rows.size >= 3)
        for (i in 0 until rows.size - 1) {
            val e = rendered.getSpanEnd(rows[i])
            val s2 = rendered.getSpanStart(rows[i + 1])
            val between = rendered.subSequence(e, s2).toString()
            assertTrue("expected single \\n between rows, got '$between'", between == "\n")
            val breaks = rendered.getSpans(e, s2, ReaderTableRowBreakSpan::class.java)
            assertTrue("expected ReaderTableRowBreakSpan on inter-row \\n", breaks.isNotEmpty())
        }
    }

    @Test
    fun table_noEmptyLineBetweenRows_evenWhenSpanNearlyFullWidth() {
        val compactTable = """
            | X | Y |
            | --- | --- |
            | a | b |
            | c | d |
        """.trimIndent()
        val gap = measureMaxTableRowGap(compactTable, widthPx = 1400, lineSpacing = 1.5f)
        assertTrue("max gap should be ~0 after zero-height break, was $gap", gap <= 1)
    }

    @Test
    fun table_rowHeights_consistent_despiteTrailingNewline() {
        val compactTable = """
            | X | Y |
            | --- | --- |
            | a | b |
            | c | d |
            | e | f |
        """.trimIndent()
        val measured = measureTable(compactTable, widthPx = 1400, lineSpacing = 1.0f)
        val (rendered, rows, layout) = measured
        val heights = rows.map { row ->
            val line = layout.getLineForOffset(rendered.getSpanStart(row))
            layout.getLineBottom(line) - layout.getLineTop(line)
        }
        val maxH = heights.maxOrNull() ?: 0
        val minH = heights.minOrNull() ?: 0
        assertTrue("row heights diverge too much: $heights", maxH - minH <= 2)
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
        val (rendered, rows, layout) = measureTable(markdown, widthPx, lineSpacing)
        var maxGap = 0
        for (i in 0 until rows.size - 1) {
            val l1 = layout.getLineForOffset(rendered.getSpanStart(rows[i]))
            val l2 = layout.getLineForOffset(rendered.getSpanStart(rows[i + 1]))
            val gap = layout.getLineTop(l2) - layout.getLineBottom(l1)
            maxGap = maxOf(maxGap, gap)
        }
        return maxGap
    }

    private data class MeasuredTable(
        val rendered: Spanned,
        val rows: List<ReaderTableRowSpan>,
        val layout: android.text.Layout,
    )

    private fun measureTable(markdown: String, widthPx: Int, lineSpacing: Float): MeasuredTable {
        val context: Context = RuntimeEnvironment.getApplication()
        ReaderTableSpacing.lineSpacingMultiplier = lineSpacing
        val markwon = ReaderMarkwonFactory.create(context)
        val prepared = ReaderMarkwonFactory.prepareMarkdown(markdown)
        val rendered = markwon.toMarkdown(prepared.text) as Spanned
        val rows = rendered.getSpans(0, rendered.length, ReaderTableRowSpan::class.java)
            .sortedBy { rendered.getSpanStart(it) }
        require(rows.size >= 2) { "need 2+ rows" }

        // Warm internal cell layouts so getSize reports real row metrics (like first draw).
        val bmp = Bitmap.createBitmap(widthPx, 64, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint().apply { textSize = 18f * context.resources.displayMetrics.density }
        for (row in rows) {
            val s = rendered.getSpanStart(row)
            val e = rendered.getSpanEnd(row)
            row.draw(canvas, rendered, s, e, 0f, 0, 0, 40, paint)
        }

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
        return MeasuredTable(rendered, rows, requireNotNull(tv.layout))
    }
}
