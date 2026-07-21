package space.liushenme.markdownreader.ui.screens.reader

import android.content.Context
import android.widget.TextView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import space.liushenme.markdownreader.markdown.ReaderMarkwonFactory
import space.liushenme.markdownreader.testutil.TestFixtures

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class TocJumpStuckAt174Test {

    @Test
    fun tocJumpWindow_startsAtTargetChapter_notEarlierMermaidSection() {
        val markdown = TestFixtures.fullFormatSampleMarkdown()
        val toc = parseMarkdownToc(markdown)
        val sec18 = toc.first { it.title == "十八、转义字符测试" }
        val sec174 = toc.first { it.title == "17.4 思维导图" }
        val boundaries = computeChapterBoundaries(markdown, toc)
        val (jumpStart, jumpEnd) = computeTocJumpReadingWindow(
            boundaries = boundaries,
            charPos = sec18.sourceOffset,
            contentLen = markdown.length,
        )
        val (readStart, _) = computeReadingWindow(
            boundaries = boundaries,
            charPos = sec18.sourceOffset,
            contentLen = markdown.length,
        )
        assertEquals(sec18.sourceOffset, jumpStart)
        assertTrue(readStart < sec174.sourceOffset)
        assertTrue(jumpEnd > sec18.sourceOffset)
        assertFalse(markdown.substring(jumpStart, jumpEnd).contains("17.4 思维导图"))
    }

    @Test
    fun windowFromSec17_includesHeadingsThroughSection20() {
        val markdown = TestFixtures.fullFormatSampleMarkdown()
        val toc = parseMarkdownToc(markdown)
        val sec18 = toc.first { it.title == "十八、转义字符测试" }
        val boundaries = computeChapterBoundaries(markdown, toc)
        val (winStart, winEnd) = computeTocJumpReadingWindow(
            boundaries = boundaries,
            charPos = sec18.sourceOffset,
            contentLen = markdown.length,
        )
        val slice = markdown.substring(winStart, winEnd)
        val context: Context = RuntimeEnvironment.getApplication()
        val markwon = ReaderMarkwonFactory.create(context)
        val prepared = ReaderMarkwonFactory.prepareMarkdown(slice)
        val rendered = markwon.toMarkdown(prepared.text).toString()
        val headingLines = markdownHeadingSpanStarts(markwon.toMarkdown(prepared.text))
            .map { lineAt(rendered, it) }

        assertTrue("window [$winStart,$winEnd) sliceLen=${slice.length}", winEnd > sec18.sourceOffset)
        assertTrue("rendered contains 18: ${rendered.contains("十八")}", rendered.contains("十八、转义字符测试"))
        assertTrue("heading lines: $headingLines", headingLines.any { it.contains("十八") })
        assertTrue(headingLines.any { it.contains("十九") })
        assertTrue(headingLines.any { it.contains("二十") })

        listOf("十八、转义字符测试", "十九、混合嵌套排版", "二十、长文本滚动测试").forEach { title ->
            val entry = toc.first { it.title == title }
            val resolved = resolveDisplayedCharOffset(
                sourceContent = markdown,
                sourceOffset = entry.sourceOffset,
                displayedText = rendered,
                renderPlainText = false,
                windowStart = winStart,
                windowEnd = winEnd,
                tocEntries = toc,
                preferredEntry = entry,
            )
            val line = lineAt(rendered, resolved)
            assertTrue("jump $title -> $line", titleMatchesRenderedLine(line, title))
        }
    }

    @Test
    fun tocJump_scrollsPast174MindmapLine_withRealTextView() {
        val markdown = TestFixtures.fullFormatSampleMarkdown()
        val toc = parseMarkdownToc(markdown)
        val sec18 = toc.first { it.title == "十八、转义字符测试" }
        val boundaries = computeChapterBoundaries(markdown, toc)
        val (winStart, winEnd) = computeTocJumpReadingWindow(
            boundaries = boundaries,
            charPos = sec18.sourceOffset,
            contentLen = markdown.length,
        )
        val slice = markdown.substring(winStart, winEnd)
        val context: Context = RuntimeEnvironment.getApplication()
        val markwon = ReaderMarkwonFactory.create(context)
        val tv = TextView(context).apply {
            layoutParams = android.view.ViewGroup.LayoutParams(1080, 2400)
            measure(
                android.view.View.MeasureSpec.makeMeasureSpec(1080, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(2400, android.view.View.MeasureSpec.EXACTLY),
            )
            layout(0, 0, 1080, 2400)
        }
        val prepared = ReaderMarkwonFactory.prepareMarkdown(slice)
        markwon.setMarkdown(tv, prepared.text)
        tv.measure(
            android.view.View.MeasureSpec.makeMeasureSpec(1080, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(2400, android.view.View.MeasureSpec.EXACTLY),
        )
        tv.layout(0, 0, 1080, 2400)

        val resolved = resolveDisplayedCharOffset(
            sourceContent = markdown,
            sourceOffset = sec18.sourceOffset,
            displayedText = tv.text,
            renderPlainText = false,
            windowStart = winStart,
            windowEnd = winEnd,
            tocEntries = toc,
            preferredEntry = sec18,
        )
        val resolvedLine = lineAt(tv.text.toString(), resolved)
        assertTrue("resolved line should be sec18 not 17.4: $resolvedLine", resolvedLine.contains("十八"))

        applyPendingScrollToCharOffset(tv, resolved)
        tv.layout(0, 0, 1080, 2400)
        val topOffset = charOffsetAtScrollTop(tv)
        val topLine = lineAt(tv.text.toString(), topOffset)
        assertTrue("viewport top after scroll: $topLine", titleMatchesRenderedLine(topLine, sec18.title))
    }
}
