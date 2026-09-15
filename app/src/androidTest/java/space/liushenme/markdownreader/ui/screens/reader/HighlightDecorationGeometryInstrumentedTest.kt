package space.liushenme.markdownreader.ui.screens.reader

import android.view.View
import android.widget.FrameLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import space.liushenme.markdownreader.MainActivity
import space.liushenme.markdownreader.markdown.ReaderMarkwonFactory

/**
 * 跨行划线曾被画成「首行行首 → 选区起点」，中间整行还整段消失：行尾用
 * getPrimaryHorizontal 取 x，软换行处它给的是下一行行首。Robolectric 排不出真实软换行，
 * 只能在真机上守。
 */
@RunWith(AndroidJUnit4::class)
class HighlightDecorationGeometryInstrumentedTest {

    private val paragraph =
        "一位真正的作家永远只为内心写作，只有内心才会真实地告诉他，他的自私、他的高尚是多么突出。"

    private fun readerView(activity: MainActivity): SafeReaderTextView {
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
            content = paragraph.repeat(40),
            renderPlainText = true,
            renderSig = "highlight-geometry",
            markwon = ReaderMarkwonFactory.create(activity),
            highlights = emptyList(),
            highlightColorArgb = 0x80FFEB3B.toInt(),
        )
        view.measure(
            View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, WIDTH, HEIGHT)
        return view
    }

    @Test
    fun rangeEndingAtSoftWrap_reachesLineRightInsteadOfNextLineStart() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val layout = readerView(activity).layout
                val line = 1
                val lineEnd = layout.getLineEnd(line)
                val start = layout.getLineStart(line) + 5

                // 这正是踩过的坑：行尾 offset 的 primaryHorizontal 落在下一行行首。
                assertTrue(
                    "软换行处 getPrimaryHorizontal 应给出下一行行首",
                    layout.getPrimaryHorizontal(lineEnd) < layout.getPrimaryHorizontal(start),
                )

                val (left, right) = horizontalRangeOnLine(layout, line, start, lineEnd)!!
                assertEquals(layout.getPrimaryHorizontal(start), left, 0.5f)
                assertEquals(layout.getLineRight(line), right, 0.5f)
            }
        }
    }

    @Test
    fun fullyCoveredLine_spansWholeLine() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val layout = readerView(activity).layout
                val line = 2
                val range = horizontalRangeOnLine(
                    layout,
                    line,
                    layout.getLineStart(line),
                    layout.getLineEnd(line),
                )

                // 旧实现这里 left == right == 下一行行首，整行被当成空区间跳过。
                assertNotNull("跨行划线的中间整行不能被跳过", range)
                assertEquals(layout.getLineLeft(line), range!!.first, 0.5f)
                assertEquals(layout.getLineRight(line), range.second, 0.5f)
            }
        }
    }

    @Test
    fun lastLineOfRange_stopsAtSelectionEnd() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val layout = readerView(activity).layout
                val line = 3
                val end = layout.getLineStart(line) + 6
                val (left, right) = horizontalRangeOnLine(
                    layout,
                    line,
                    layout.getLineStart(line),
                    end,
                )!!

                assertEquals(layout.getLineLeft(line), left, 0.5f)
                assertEquals(layout.getPrimaryHorizontal(end), right, 0.5f)
                assertTrue("末行不应画到行尾", right < layout.getLineRight(line))
            }
        }
    }

    private companion object {
        const val WIDTH = 1000
        const val HEIGHT = 1800
    }
}
