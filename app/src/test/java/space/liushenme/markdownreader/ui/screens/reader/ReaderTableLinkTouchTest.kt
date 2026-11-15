package space.liushenme.markdownreader.ui.screens.reader

import android.graphics.Bitmap
import android.graphics.Canvas
import android.text.Spanned
import android.text.style.ClickableSpan
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import space.liushenme.markdownreader.markdown.ReaderMarkwonFactory
import space.liushenme.markdownreader.markdown.ReaderTableRowSpan

/**
 * 表格单元格里的链接必须可点；点同一单元格的非链接文字、其它单元格、表格外仍然不能命中链接。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReaderTableLinkTouchTest {

    private val markdown = """
        | 说明 | 链接 |
        | --- | --- |
        | 前面文字 [表格链接](https://example.com/table-link) 后面文字 | 没有链接 |
        | 纯文本 | 另一个单元格 |
    """.trimIndent()

    private class Fixture(
        val view: SafeReaderTextView,
        val row: ReaderTableRowSpan,
        val cellLayout: android.text.Layout,
        val link: ClickableSpan,
        val cellLeft: Float,
        val cellTop: Float,
    )

    private fun fixture(): Fixture {
        val context = RuntimeEnvironment.getApplication()
        val rendered = ReaderMarkwonFactory.create(context)
            .toMarkdown(ReaderMarkwonFactory.prepareMarkdown(markdown, context).text) as Spanned
        val view = SafeReaderTextView(context).apply {
            includeFontPadding = false
            textSize = 18f
            setPadding(8, 8, 8, 8)
            setText(rendered, TextView.BufferType.SPANNABLE)
            measure(
                View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            )
            layout(0, 0, measuredWidth, measuredHeight)
        }
        FrameLayout(context).apply {
            addView(view)
            measure(
                View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(view.measuredHeight, View.MeasureSpec.EXACTLY),
            )
            layout(0, 0, measuredWidth, measuredHeight)
        }
        view.draw(Canvas(Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)))

        val bodyLayout = requireNotNull(view.layout)
        val rows = rendered.getSpans(0, rendered.length, ReaderTableRowSpan::class.java)
            .sortedBy { rendered.getSpanStart(it) }
        val row = rows[1]
        val column = 0
        val cellLayout = requireNotNull(row.cellLayout(column))
        val cellText = cellLayout.text as Spanned
        val link = requireNotNull(cellText.getSpans(0, cellText.length, ClickableSpan::class.java).firstOrNull())

        val line = bodyLayout.getLineForOffset(rendered.getSpanStart(row))
        val left = bodyLayout.getLineLeft(line)
        val top = bodyLayout.getLineTop(line).toFloat()
        val bottom = bodyLayout.getLineBottom(line).toFloat()
        val rowHeight = (0 until row.columnCount).mapNotNull(row::cellLayout).maxOf { it.height }
        val cellWidth = row.cellWidth()
        val cellLeft = left + column * cellWidth + row.cellPadding
        val cellTop = top + row.cellPadding + (((bottom - top).toInt() - rowHeight) / 4).coerceAtLeast(0)

        return Fixture(view, row, cellLayout, link, cellLeft, cellTop)
    }

    /** 链接在当前单元格里所占的第一行（单元格窄时链接会折行）。 */
    private fun Fixture.linkLine(): Int {
        val text = cellLayout.text as Spanned
        val start = text.getSpanStart(link)
        val end = text.getSpanEnd(link)
        for (line in 0 until cellLayout.lineCount) {
            val lineStart = cellLayout.getLineStart(line)
            val lineEnd = cellLayout.getLineEnd(line)
            if (start < lineEnd && end > lineStart) return line
        }
        error("link not laid out")
    }

    /** 链接文字在单元格该行上的字形范围中点。 */
    private fun Fixture.xInsideLink(): Float {
        val text = cellLayout.text as Spanned
        val line = linkLine()
        val lineStart = cellLayout.getLineStart(line)
        val lineEnd = cellLayout.getLineEnd(line)
        val startInLine = maxOf(text.getSpanStart(link), lineStart)
        val endInLine = minOf(text.getSpanEnd(link), lineEnd)
        val left = cellLayout.getPrimaryHorizontal(startInLine)
        val right = if (endInLine >= lineEnd) {
            cellLayout.getLineRight(line)
        } else {
            cellLayout.getPrimaryHorizontal(endInLine)
        }
        return cellLeft + (minOf(left, right) + maxOf(left, right)) / 2f
    }

    private fun Fixture.yInCell(): Float {
        val line = linkLine()
        return cellTop + (cellLayout.getLineTop(line) + cellLayout.getLineBottom(line)) / 2f
    }

    @Test
    fun tapOnLinkTextInsideTableCell_isLink() {
        val f = fixture()
        val target = f.view.readerHitTargetAt(f.xInsideLink(), f.yInCell())
        assertEquals(ReaderHitKind.LINK, target.kind)
        assertNotNull(target.span)
        assertTrue(target.span is ClickableSpan)
    }

    @Test
    fun tapOnNonLinkTextInsideSameCell_isTableCell() {
        val f = fixture()
        val text = f.cellLayout.text as Spanned
        val line = f.linkLine()
        val lineStart = f.cellLayout.getLineStart(line)
        val startInLine = maxOf(text.getSpanStart(f.link), lineStart)
        // 链接前的「前面文字」区域（同一行、链接左侧）
        val x = f.cellLeft +
            (f.cellLayout.getPrimaryHorizontal(lineStart) + f.cellLayout.getPrimaryHorizontal(startInLine)) / 2f
        val target = f.view.readerHitTargetAt(x, f.yInCell())
        assertEquals(ReaderHitKind.TABLE_CELL, target.kind)
    }

    @Test
    fun tapOnCellWithoutLink_isTableCell() {
        val f = fixture()
        val plainColumn = 1
        val plainLayout = requireNotNull(f.row.cellLayout(plainColumn))
        val bodyLayout = requireNotNull(f.view.layout)
        val line = bodyLayout.getLineForOffset((f.view.text as Spanned).getSpanStart(f.row))
        val x = bodyLayout.getLineLeft(line) + plainColumn * f.row.cellWidth() + f.row.cellPadding +
            (plainLayout.getPrimaryHorizontal(0) + plainLayout.getPrimaryHorizontal(1)) / 2f
        val target = f.view.readerHitTargetAt(x, f.yInCell())
        assertEquals(ReaderHitKind.TABLE_CELL, target.kind)
    }

    @Test
    fun tapOutsideTable_hasNoTableLink() {
        val f = fixture()
        assertNull(ReaderTableLinkTouch.findClickableSpanAt(f.view, 4f, f.view.height - 2f))
    }
}