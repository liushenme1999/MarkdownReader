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

    @Test
    fun chapterStarts_forceNewPagesWithTitleAtTop() {
        // 每章 12 行，章标题独占一行；页面高度只够约 8 行。
        // 不强制分页时标题会落在上一页底部，这正是本次要修的问题。
        val chapterLines = 12
        val chapters = 6
        val starts = ArrayList<Int>(chapters)
        val builder = StringBuilder()
        for (chapter in 0 until chapters) {
            starts += builder.length
            builder.append("第").append(chapter + 1).append("章 测试章节标题\n")
            for (line in 1 until chapterLines) {
                builder.append("　　第").append(chapter + 1).append("章正文第").append(line)
                    .append("行，用于撑满页面高度。\n")
            }
        }
        val content = builder.toString()

        val pages = splitTextToPagesByLines(
            content = content,
            textSizePx = 45f,
            widthPx = 1000,
            heightPx = 600,
            lineSpacingMultiplier = 1.2f,
            chapterStarts = starts,
        )

        assertEquals(chapters, starts.size)
        // 每个章节起点必须正好是新一页的第一行
        val pageStarts = pages.map { it.first }.toSet()
        for (start in starts) {
            assertTrue("章节 $start 没有从新页开始：$pageStarts", start in pageStarts)
        }
        assertTrue(pages.all { it.count() > 0 })
        val badBreaks = pages.zipWithNext().filter { (a, b) ->
            !(b.first > a.last && content.substring(a.last + 1, b.first).isBlank())
        }
        assertTrue("页区间丢失正文：${badBreaks.take(3)}", badBreaks.isEmpty())
    }

    @Test
    fun chapterStartsOutsideRange_areIgnored() {
        val content = "第1章 标题\n" + "正文内容用于测试分页。\n".repeat(40)
        val withoutBreaks = splitTextToPagesByLines(
            content = content,
            textSizePx = 45f,
            widthPx = 1000,
            heightPx = 600,
            lineSpacingMultiplier = 1.2f,
        )
        val withOutOfRange = splitTextToPagesByLines(
            content = content,
            textSizePx = 45f,
            widthPx = 1000,
            heightPx = 600,
            lineSpacingMultiplier = 1.2f,
            chapterStarts = listOf(-5, 0, content.length + 10),
        )
        assertEquals(withoutBreaks, withOutOfRange)
    }

    @Test
    fun chapterStartsInsideTitleLine_alignToLineStart() {
        // TXT 章节标题常带全角缩进，解析出的偏移可能落在行中间（缩进之后）。
        val content = buildString {
            append("第1章 前情\n")
            repeat(6) { append("　　第一章的正文内容，用于占满一页。\n") }
            append("　　第2章 标题带缩进\n")
            repeat(6) { append("　　第二章的正文内容，用于占满一页。\n") }
            append("第3章 结尾\n")
            repeat(6) { append("　　第三章的正文内容，用于占满一页。\n") }
        }
        val titleLineStart = content.indexOf("　　第2章 标题带缩进")
        val midTitleOffset = titleLineStart + 2 // 落在全角缩进之后

        val pages = splitTextToPagesByLines(
            content = content,
            textSizePx = 45f,
            widthPx = 1000,
            heightPx = 400,
            lineSpacingMultiplier = 1.2f,
            chapterStarts = listOf(midTitleOffset),
        )

        assertTrue("页首必须是标题行行首，而不是标题行末尾", pages.any { it.first == titleLineStart })
        val titlePage = pages.first { it.first == titleLineStart }
        val titlePageText = content.substring(titlePage.first, titlePage.last + 1)
        assertTrue(titlePageText.contains("第2章 标题带缩进"))
        assertTrue("标题页不能只有一个标题", titlePageText.contains("第二章的正文"))
    }
}
