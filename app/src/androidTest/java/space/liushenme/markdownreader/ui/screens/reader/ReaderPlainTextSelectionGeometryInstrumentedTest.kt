package space.liushenme.markdownreader.ui.screens.reader

import android.os.SystemClock
import android.text.Selection
import android.text.Spannable
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import space.liushenme.markdownreader.MainActivity
import space.liushenme.markdownreader.markdown.ReaderMarkwonFactory

/**
 * TXT 正文一度被写成 PrecomputedText，DynamicLayout 下 getPrimaryHorizontal /
 * getOffsetForHorizontal / getSelectionPath 会把行内每个 offset 都算成行右边界，
 * 于是长按只能命中行首、选区画成整行。这里守住「逐字命中」这条底线。
 */
@RunWith(AndroidJUnit4::class)
class ReaderPlainTextSelectionGeometryInstrumentedTest {

    private val paragraph =
        "一位真正的作家永远只为内心写作，只有内心才会真实地告诉他，他的自私、他的高尚是多么突出。"

    private fun readerView(activity: MainActivity, content: String): SafeReaderTextView {
        val view = SafeReaderTextView(activity).apply {
            renderPlainTextBody = true
            textSize = 20f
            setPadding(48, 24, 48, 24)
            setLineSpacing(0f, 1.6f)
        }
        activity.setContentView(
            FrameLayout(activity).apply {
                addView(view, FrameLayout.LayoutParams(WIDTH, HEIGHT))
            },
        )
        applyReaderTextContent(
            textView = view,
            content = content,
            renderPlainText = true,
            renderSig = "test-sig",
            markwon = ReaderMarkwonFactory.create(activity),
            highlights = emptyList(),
            highlightColorArgb = 0x8000FF00.toInt(),
        )
        view.measure(
            View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, WIDTH, HEIGHT)
        return view
    }

    @Test
    fun longPlainBody_keepsPerCharacterHorizontals() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                // 长度远超旧的 6000 字预测量阈值，走的正是曾经出问题的那条分支。
                val view = readerView(activity, paragraph.repeat(400))
                val layout = view.layout
                assertTrue(layout.lineCount > 3)
                val line = 3
                val lineStart = layout.getLineStart(line)
                val lineRight = layout.getLineRight(line)
                var previous = layout.getPrimaryHorizontal(lineStart)
                for (i in 1..5) {
                    val current = layout.getPrimaryHorizontal(lineStart + i)
                    assertTrue(
                        "offset ${lineStart + i} 应在行内推进，实际 $current（行右 $lineRight）",
                        current > previous && current < lineRight,
                    )
                    previous = current
                }
            }
        }
    }

    @Test
    fun longPressMidLine_selectsPressedCharacterOnly() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val view = readerView(activity, paragraph.repeat(400))
                val layout = view.layout
                val line = 3
                val target = layout.getLineStart(line) + 5
                val x = view.compoundPaddingLeft +
                    (layout.getPrimaryHorizontal(target) +
                        layout.getPrimaryHorizontal(target + 1)) / 2f
                val y = view.extendedPaddingTop +
                    (layout.getLineTop(line) + layout.getLineBottom(line)) / 2f
                val now = SystemClock.uptimeMillis()
                val down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0)
                try {
                    view.onTouchEvent(down)
                    assertTrue(view.fireScheduledSelectionLongPressForTest())
                    val spannable = view.text as Spannable
                    assertEquals(target, Selection.getSelectionStart(spannable))
                    assertEquals(target + 1, Selection.getSelectionEnd(spannable))
                } finally {
                    down.recycle()
                }
            }
        }
    }

    @Test
    fun pressOnRightHalfOfGlyph_selectsThatGlyphNotTheNextOne() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val view = readerView(activity, paragraph.repeat(400))
                val layout = view.layout
                val line = 3
                val target = layout.getLineStart(line) + 4
                val left = layout.getPrimaryHorizontal(target)
                val right = layout.getPrimaryHorizontal(target + 1)
                assertTrue("字宽必须可辨", right > left)
                val y = view.extendedPaddingTop +
                    (layout.getLineTop(line) + layout.getLineBottom(line)) / 2f
                val x = view.compoundPaddingLeft + left + (right - left) * 0.75f

                assertEquals(
                    target,
                    ReaderTextSelectionTouch.offsetNearestCharOnTextView(view, x, y),
                )
            }
        }
    }

    @Test
    fun endHandleAtSoftWrap_staysOnItsOwnLine() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val view = readerView(activity, paragraph.repeat(400))
                val layout = view.layout
                // 整段无换行符，第 0 行结束处就是软换行：end 等于下一行行首。
                val start = layout.getLineStart(0)
                val end = ReaderTextSelectionTouch.lastVisibleCharOffset(layout, 0) + 1
                assertEquals(layout.getLineStart(1), end)
                Selection.setSelection(view.text as Spannable, start, end)
                val point = ReaderTextSelectionTouch.selectionHandlePositionOnTextView(
                    view,
                    end,
                    isEnd = true,
                )!!
                // 行距留白算在首行底里，句柄必须停在字形底，不能压到第二行文字上。
                val secondLineTop = layout.getLineTop(1) + view.extendedPaddingTop

                assertTrue(
                    "end 句柄 y=${point.second} 不应掉到第二行（第二行顶 $secondLineTop）",
                    point.second < secondLineTop,
                )
            }
        }
    }

    private companion object {
        const val WIDTH = 1000
        const val HEIGHT = 1800
    }
}
