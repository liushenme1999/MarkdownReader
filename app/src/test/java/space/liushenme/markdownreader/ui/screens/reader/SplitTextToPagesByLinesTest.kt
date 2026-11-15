package space.liushenme.markdownreader.ui.screens.reader

import android.text.StaticLayout
import android.text.TextPaint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 纯文本行级分页的回归测试。
 *
 * [splitTextToPagesByLines] 会分块调用 `StaticLayout.Builder.obtain(content, chunkStart, chunkEnd, ...)`。
 * `getLineStart()` 返回的是相对**整段 content** 的绝对偏移（Builder 的 start 已计入），
 * 若再叠加 chunkStart，从第二块起页边界就会超前约一个块长：
 * 每块产生一个「超大页」（渲染时被裁掉大半）并跳过大量正文；
 * 页区间还会超出正文长度，于是越往后翻页，页面上能看到的文字越少，
 * 最后整片空白页。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SplitTextToPagesByLinesTest {

    private fun probeText(paragraphs: Int = 2_000): String {
        val line = "　　这是一行用于测试分页的正文内容，长度大约四十个汉字左右。"
        return buildString {
            for (i in 0 until paragraphs) {
                append("第").append(i).append("段 ").append(line).append('\n')
            }
        }
    }

    @Test
    fun chunkedStaticLayoutLineStart_isAbsoluteContentOffset() {
        val content = probeText(paragraphs = 400)
        val paint = TextPaint().apply { textSize = 45f }
        val chunkStart = 6_000
        val layout = StaticLayout.Builder
            .obtain(content, chunkStart, 12_000, paint, 900)
            .setLineSpacing(0f, 1.2f)
            .setIncludePad(false)
            .build()
        assertTrue(layout.lineCount > 0)
        assertEquals(chunkStart, layout.getLineStart(0))
        assertTrue(layout.getLineStart(1) > chunkStart)
    }

    @Test
    fun pages_coverWholeTextWithoutHugeOrBlankPages() {
        val content = probeText()
        val pages = splitTextToPagesByLines(
            content = content,
            textSizePx = 45f,
            widthPx = 1000,
            heightPx = 1700,
            lineSpacingMultiplier = 1.2f,
        )

        assertEquals(0, pages.first().first)
        assertEquals(content.length - 1, pages.last().last)
        // 页区间必须覆盖全部正文；页间只允许跳过空白字符（不显示的空行）。
        val badBreaks = pages.zipWithNext().filter { (a, b) ->
            !(b.first > a.last && content.substring(a.last + 1, b.first).isBlank())
        }
        assertTrue("页区间丢失正文或重叠：${badBreaks.take(3)}", badBreaks.isEmpty())
        // 不允许出现空页（起点越过正文末尾）。
        assertEquals(0, pages.count { it.first >= content.length })
        assertTrue(pages.all { it.count() > 0 })
        // 不允许出现「超大页」：修复前每 6000 字块会产出一个 ~6500 字的页。
        val sorted = pages.map { it.count() }.sorted()
        val median = sorted[sorted.size / 2]
        assertTrue("存在超大页：max=${sorted.last()} median=$median", sorted.last() <= median * 3)
    }
}
