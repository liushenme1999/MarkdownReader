package space.liushenme.markdownreader.ui.screens.reader

import android.text.Selection
import android.text.SpannableString
import android.view.MotionEvent
import android.widget.FrameLayout
import android.widget.TextView
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SafeReaderTextViewSelectionTest {

    private lateinit var textView: SafeReaderTextView

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        textView = SafeReaderTextView(context).apply {
            val body = SpannableString("长按选中这段文字，然后拖动句柄扩展选区。")
            setText(body, TextView.BufferType.SPANNABLE)
            textSize = 18f
            setPadding(24, 24, 24, 24)
            measure(
                android.view.View.MeasureSpec.makeMeasureSpec(400, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(600, android.view.View.MeasureSpec.EXACTLY),
            )
            layout(0, 0, 400, 600)
        }
        FrameLayout(context).apply {
            addView(textView)
            measure(
                android.view.View.MeasureSpec.makeMeasureSpec(400, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(800, android.view.View.MeasureSpec.EXACTLY),
            )
            layout(0, 0, 400, 800)
        }
    }

    private fun activateSelection(word: String = "选中") {
        val text = textView.text as SpannableString
        val start = text.indexOf(word)
        assertTrue("fixture contains '$word'", start >= 0)
        textView.applySelectionRangeForTest(start, start + word.length)
    }

    private fun offsetXYForSpan(start: Int, end: Int): Pair<Float, Float> {
        val layout = textView.layout ?: error("layout missing")
        val mid = (start + end) / 2
        val line = layout.getLineForOffset(mid)
        val x = layout.getPrimaryHorizontal(mid) + textView.totalPaddingLeft
        val y = (layout.getLineTop(line) + layout.getLineBottom(line)) / 2f + textView.totalPaddingTop
        return x to y
    }

    @Test
    fun prepareForNewTouch_onHandleBand_doesNotDismissImmediately() {
        activateSelection()
        val text = textView.text as SpannableString
        val start = Selection.getSelectionStart(text)
        val layout = textView.layout!!
        val lineStart = layout.getLineForOffset(start)
        val handleX = layout.getPrimaryHorizontal(start) + textView.totalPaddingLeft
        val handleY = layout.getLineTop(lineStart) + textView.totalPaddingTop -
            ReaderTextSelectionTouch.handleBandPx(textView.resources.displayMetrics.density) / 2f

        textView.prepareForNewTouch(handleX, handleY)
        assertTrue(textView.isInTextSelection())
        assertTrue(textView.hasVisibleTextSelection())
    }

    @Test
    fun prepareForNewTouch_farOutside_marksPendingDismissWithoutClearingSelection() {
        activateSelection()
        textView.prepareForNewTouch(10f, 10f)
        assertTrue(textView.isInTextSelection())
        assertTrue(textView.hasVisibleTextSelection())
    }

    @Test
    fun outsideTapUp_dismissesSelection() {
        activateSelection()
        val outsideX = 12f
        val outsideY = 580f
        val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, outsideX, outsideY, 0)
        val up = MotionEvent.obtain(0, 50, MotionEvent.ACTION_UP, outsideX, outsideY, 0)
        try {
            textView.prepareForNewTouch(outsideX, outsideY)
            textView.onTouchEvent(down)
            textView.onTouchEvent(up)
        } finally {
            down.recycle()
            up.recycle()
        }
        assertFalse(textView.hasVisibleTextSelection())
        assertFalse(textView.isInTextSelection())
    }

    @Test
    fun outsideDrag_afterEditorClearedRange_doesNotRestoreOrphanHighlight() {
        // 区外按下后手指移动超出 slop，shouldDismiss 不成立；若系统已清 range，
        // 旧逻辑会 restoreSavedSelectionRange → 只剩选区阴影、无首尾句柄。
        activateSelection()
        val text = textView.text as SpannableString
        val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 12f, 580f, 0)
        val up = MotionEvent.obtain(0, 50, MotionEvent.ACTION_UP, 12f, 400f, 0)
        try {
            textView.onTouchEvent(down)
            Selection.removeSelection(text)
            textView.onTouchEvent(up)
        } finally {
            down.recycle()
            up.recycle()
        }
        assertFalse(textView.hasVisibleTextSelection())
        assertFalse(textView.isInTextSelection())
        assertTrue(Selection.getSelectionStart(text) == Selection.getSelectionEnd(text))
    }

    @Test
    fun actionModeDestroyedBySystem_clearsSelectionHighlightWithHandles() {
        activateSelection()
        assertTrue(textView.isInTextSelection())
        assertTrue(textView.hasVisibleTextSelection())

        textView.simulateSystemActionModeDestroyForTest()

        assertFalse(textView.hasVisibleTextSelection())
        assertFalse(textView.isInTextSelection())
    }

    @Test
    fun touchInsideSelectionText_doesNotDismissOnUp() {
        activateSelection()
        val text = textView.text as SpannableString
        val start = Selection.getSelectionStart(text)
        val end = Selection.getSelectionEnd(text)
        val (x, y) = offsetXYForSpan(start, end)
        val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, x, y, 0)
        val up = MotionEvent.obtain(0, 30, MotionEvent.ACTION_UP, x, y, 0)
        try {
            textView.onTouchEvent(down)
            textView.onTouchEvent(up)
        } finally {
            down.recycle()
            up.recycle()
        }
        assertTrue(textView.hasVisibleTextSelection())
    }

    @Test
    fun longPressPrep_makesTextSelectable() {
        val text = textView.text as SpannableString
        val word = "长按"
        val start = text.indexOf(word)
        val (x, y) = offsetXYForSpan(start, start + word.length)
        val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, x, y, 0)
        try {
            textView.onTouchEvent(down)
            textView.performLongClick(x, y)
        } finally {
            down.recycle()
        }
        // Robolectric 下系统长按未必画出选区，但应完成 prep 并进入可选中状态
        assertTrue(textView.isTextSelectable)
    }
}
