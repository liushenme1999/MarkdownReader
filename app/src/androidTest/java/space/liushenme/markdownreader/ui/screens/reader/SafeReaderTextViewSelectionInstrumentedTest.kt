package space.liushenme.markdownreader.ui.screens.reader

import android.os.SystemClock
import android.graphics.Path
import android.graphics.RectF
import android.text.Selection
import android.text.Spannable
import android.text.SpannableString
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import space.liushenme.markdownreader.MainActivity

@RunWith(AndroidJUnit4::class)
class SafeReaderTextViewSelectionInstrumentedTest {

    @Test
    fun longPress_usesSingleCustomSelection_andOutsideDownDismisses() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val view = SafeReaderTextView(activity).apply {
                    setText(
                        SpannableString("长按选择这段文字，然后拖动到下一行。\n第二行继续测试句柄。"),
                        TextView.BufferType.SPANNABLE,
                    )
                    textSize = 20f
                    setPadding(24, 24, 24, 24)
                    isFocusableInTouchMode = true
                }
                activity.setContentView(
                    FrameLayout(activity).apply {
                        addView(
                            view,
                            FrameLayout.LayoutParams(
                                FrameLayout.LayoutParams.MATCH_PARENT,
                                FrameLayout.LayoutParams.MATCH_PARENT,
                            ),
                        )
                    },
                )
                val width = activity.resources.displayMetrics.widthPixels
                val height = activity.resources.displayMetrics.heightPixels
                view.measure(
                    View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
                )
                view.layout(0, 0, width, height)
                view.requestFocus()
                assertTrue(view.width > 0 && view.height > 0)
                val text = view.text
                val offset = text.indexOf('选')
                val layout = view.layout
                val line = layout.getLineForOffset(offset)
                val x = layout.getPrimaryHorizontal(offset) + view.totalPaddingLeft
                val y = (layout.getLineTop(line) + layout.getLineBottom(line)) / 2f +
                    view.totalPaddingTop
                val now = SystemClock.uptimeMillis()
                val down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0)
                val up = MotionEvent.obtain(now, now + 20, MotionEvent.ACTION_UP, x, y, 0)
                val outsideDown = MotionEvent.obtain(
                    now + 40,
                    now + 40,
                    MotionEvent.ACTION_DOWN,
                    view.width - 2f,
                    view.height - 2f,
                    0,
                )
                try {
                    view.onTouchEvent(down)
                    assertTrue(view.fireScheduledSelectionLongPressForTest())
                    assertFalse(view.performLongClick(x, y))
                    assertTrue(view.hasVisibleTextSelection())
                    val spannable = view.text as Spannable
                    val selectionStart = Selection.getSelectionStart(spannable)
                    val selectionEnd = Selection.getSelectionEnd(spannable)
                    assertTrue(selectionStart >= 0 && selectionEnd > selectionStart)
                    val startHandle = ReaderTextSelectionTouch.selectionHandlePositionOnTextView(
                        view,
                        selectionStart,
                        isEnd = false,
                    )!!
                    assertTrue(
                        ReaderTextSelectionTouch.selectionHandleAtOnTextView(
                            view,
                            startHandle.first,
                            startHandle.second + 8f * view.resources.displayMetrics.density,
                        ) == ReaderTextSelectionTouch.SelectionHandle.START,
                    )
                    view.onTouchEvent(up)

                    view.onTouchEvent(outsideDown)

                    assertFalse(view.hasVisibleTextSelection())
                    assertFalse(view.isInTextSelection())
                    assertTrue(view.movementMethod != null)
                    assertTrue(view.visibility == View.VISIBLE)
                } finally {
                    down.recycle()
                    up.recycle()
                    outsideDown.recycle()
                }
            }
        }
    }

    @Test
    fun txtSoftWrap_selectsRealCharacterOnWrappedLine() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val view = SafeReaderTextView(activity).apply {
                    setText(SpannableString("甲".repeat(200)), TextView.BufferType.SPANNABLE)
                    textSize = 22f
                    setPadding(16, 16, 16, 16)
                }
                activity.setContentView(
                    FrameLayout(activity).apply {
                        addView(view, FrameLayout.LayoutParams(240, 1000))
                    },
                )
                view.measure(
                    View.MeasureSpec.makeMeasureSpec(240, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY),
                )
                view.layout(0, 0, 240, 1000)
                view.requestFocus()
                val layout = view.layout
                assertTrue(layout.lineCount >= 2)
                val offset = layout.getLineStart(1)
                val x = view.compoundPaddingLeft +
                    (layout.getPrimaryHorizontal(offset) +
                        layout.getPrimaryHorizontal(offset + 1)) / 2f
                val y = view.extendedPaddingTop +
                    (layout.getLineTop(1) + layout.getLineBottom(1)) / 2f
                val now = SystemClock.uptimeMillis()
                val down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0)
                val up = MotionEvent.obtain(now, now + 20, MotionEvent.ACTION_UP, x, y, 0)
                try {
                    view.onTouchEvent(down)
                    assertTrue(view.fireScheduledSelectionLongPressForTest())
                    view.onTouchEvent(up)
                    val spannable = view.text as Spannable
                    val start = Selection.getSelectionStart(spannable)
                    val end = Selection.getSelectionEnd(spannable)
                    assertTrue(offset in start until end)
                    assertTrue(end > start)
                    val path = Path()
                    val bounds = RectF()
                    layout.getSelectionPath(start, end, path)
                    path.computeBounds(bounds, true)
                    assertTrue(bounds.width() > 0f)
                    assertTrue(bounds.top >= layout.getLineTop(1))
                    assertTrue(bounds.bottom <= layout.getLineBottom(1))
                } finally {
                    down.recycle()
                    up.recycle()
                }
            }
        }
    }
}
