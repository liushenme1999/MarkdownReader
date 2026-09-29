package space.liushenme.markdownreader.ui.screens.reader

import android.graphics.Bitmap
import android.graphics.Canvas
import android.content.ClipboardManager
import android.text.Spanned
import android.view.View
import android.view.MotionEvent
import android.widget.PopupMenu
import android.widget.FrameLayout
import android.widget.TextView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import space.liushenme.markdownreader.markdown.ReaderMarkwonFactory
import space.liushenme.markdownreader.markdown.ReaderTableRowSpan

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ReaderTableSelectionTest {

    private val markdown = """
        | A | B | C |
        | --- | --- | --- |
        | 1 | 2 | 3 |
        | 4 | 5 | 6 |
    """.trimIndent()

    @Test
    fun hitTest_resolvesIndividualColumnsAndRows() {
        val view = tableView()
        val rendered = view.text as Spanned
        val rows = rendered.getSpans(0, rendered.length, ReaderTableRowSpan::class.java)
            .sortedBy { rendered.getSpanStart(it) }
        assertTrue(rows.size >= 3)

        val secondRowSecondCell = tableCellBoundsInView(
            view,
            rendered,
            requireNotNull(view.layout),
            rows[1],
            1,
        )
        assertNotNull(secondRowSecondCell)
        val rect = requireNotNull(secondRowSecondCell)
        val hit = tableCellHitAt(view, rect.centerX(), rect.centerY())

        assertNotNull(hit)
        assertEquals(1, requireNotNull(hit).row)
        assertEquals(1, requireNotNull(hit).column)
    }

    @Test
    fun selection_extendNormalizesReverseDragWithinSameTable() {
        val view = tableView()
        val rendered = view.text as Spanned
        val rows = rendered.getSpans(0, rendered.length, ReaderTableRowSpan::class.java)
            .sortedBy { rendered.getSpanStart(it) }
        val anchor = ReaderTableCellHit(rows[2], rows[2].rowIndex, 2)
        val selection = ReaderTableSelection(
            tableId = anchor.span.tableId,
            startRow = anchor.row,
            endRow = anchor.row,
            startColumn = anchor.column,
            endColumn = anchor.column,
        )
        val extended = requireNotNull(selection.extendTo(ReaderTableCellHit(rows[0], 0, 0)))

        assertEquals(0, extended.startRow)
        assertEquals(2, extended.endRow)
        assertEquals(0, extended.startColumn)
        assertEquals(2, extended.endColumn)
    }

    @Test
    fun selectionText_exportsExcelCompatibleTsv() {
        val view = tableView()
        val rendered = view.text as Spanned
        val rows = rendered.getSpans(0, rendered.length, ReaderTableRowSpan::class.java)
            .sortedBy { rendered.getSpanStart(it) }
        val selection = ReaderTableSelection(
            tableId = rows.first().tableId,
            startRow = 1,
            endRow = 2,
            startColumn = 0,
            endColumn = 1,
        )

        assertEquals("1\t2\n4\t5", tableSelectionText(view, selection))
    }

    @Test
    fun longPressThenDrag_selectsRectangularCells() {
        val view = tableView()
        val rendered = view.text as Spanned
        val rows = rendered.getSpans(0, rendered.length, ReaderTableRowSpan::class.java)
            .sortedBy { rendered.getSpanStart(it) }
        val startRect = requireNotNull(
            tableCellBoundsInView(view, rendered, requireNotNull(view.layout), rows[1], 0),
        )
        val endRect = requireNotNull(
            tableCellBoundsInView(view, rendered, requireNotNull(view.layout), rows[2], 1),
        )
        val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, startRect.centerX(), startRect.centerY(), 0)
        val move = MotionEvent.obtain(0, 30, MotionEvent.ACTION_MOVE, endRect.centerX(), endRect.centerY(), 0)
        val moveBack = MotionEvent.obtain(0, 40, MotionEvent.ACTION_MOVE, startRect.centerX(), startRect.centerY(), 0)
        val up = MotionEvent.obtain(0, 50, MotionEvent.ACTION_UP, endRect.centerX(), endRect.centerY(), 0)
        try {
            assertTrue(view.onTouchEvent(down))
            assertTrue(view.fireScheduledSelectionLongPressForTest())
            assertEquals(
                ReaderTableSelection(rows[1].tableId, 1, 1, 0, 0),
                view.tableSelectionForTest(),
            )
            assertTrue(view.onTouchEvent(move))
            assertEquals(
                ReaderTableSelection(rows[1].tableId, 1, 2, 0, 1),
                view.tableSelectionForTest(),
            )
            assertTrue(view.onTouchEvent(moveBack))
            assertEquals(
                ReaderTableSelection(rows[1].tableId, 1, 1, 0, 0),
                view.tableSelectionForTest(),
            )
            assertTrue(view.onTouchEvent(move))
            assertTrue(view.onTouchEvent(up))
        } finally {
            down.recycle()
            move.recycle()
            moveBack.recycle()
            up.recycle()
        }
        assertEquals("1\t2\n4\t5", view.selectedTextForTest())
        val menu = PopupMenu(view.context, view).menu
        view.populateSelectionMenuForTest(menu)
        assertEquals(1, menu.size())
        assertEquals(SafeReaderTextView.MENU_ID_COPY, menu.getItem(0).itemId)
        assertTrue(view.performSelectionMenuActionForTest(SafeReaderTextView.MENU_ID_COPY))
        val clipboard = RuntimeEnvironment.getApplication()
            .getSystemService(ClipboardManager::class.java)
        assertEquals("1\t2\n4\t5", clipboard.primaryClip?.getItemAt(0)?.text?.toString())
        assertTrue(!view.hasVisibleTextSelection())
    }

    private fun tableView(): SafeReaderTextView {
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
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        return view
    }
}
