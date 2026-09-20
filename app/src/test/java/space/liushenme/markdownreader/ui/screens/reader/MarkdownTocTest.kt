package space.liushenme.markdownreader.ui.screens.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MarkdownTocTest {

    private val toc = listOf(
        MarkdownTocEntry(level = 3, title = "9.4.4 与 ContextBuilder 的深度集成", sourceOffset = 1000),
        MarkdownTocEntry(level = 2, title = "9.4 NoteTool: 结构化笔记", sourceOffset = 5000),
        MarkdownTocEntry(level = 3, title = "9.4.1 设计理念与应用场景", sourceOffset = 8000),
    )

    @Test
    fun viewport_prefersHeadingAlreadyOnScreen() {
        val entry = currentChapterEntryForViewport(
            tocEntries = toc,
            topChar = 4800,
            bottomChar = 6200,
        )
        assertEquals("9.4 NoteTool: 结构化笔记", entry?.title)
    }

    @Test
    fun viewport_keepsPreviousSectionWhenNextHeadingNotVisible() {
        val entry = currentChapterEntryForViewport(
            tocEntries = toc,
            topChar = 2000,
            bottomChar = 3500,
        )
        assertEquals("9.4.4 与 ContextBuilder 的深度集成", entry?.title)
    }

    @Test
    fun viewport_usesLastHeadingWhenBodyHasNoVisibleTitle() {
        val entry = currentChapterEntryForViewport(
            tocEntries = toc,
            topChar = 6100,
            bottomChar = 7500,
        )
        assertEquals("9.4 NoteTool: 结构化笔记", entry?.title)
    }

    @Test
    fun viewport_picksTopmostVisibleHeadingWhenSeveralFit() {
        val entry = currentChapterEntryForViewport(
            tocEntries = toc,
            topChar = 4900,
            bottomChar = 9000,
        )
        assertEquals("9.4 NoteTool: 结构化笔记", entry?.title)
    }

    @Test
    fun progressApi_stillMapsPercentToLastHeadingAtPosition() {
        val entry = currentChapterEntryForProgress(toc, progress = 0.25f, totalChars = 10000)
        assertEquals("9.4.4 与 ContextBuilder 的深度集成", entry?.title)
    }

    @Test
    fun emptyToc_returnsNull() {
        assertNull(currentChapterEntryForViewport(emptyList(), 100, 200))
    }

    @Test
    fun headingSpans_preferVisibleOnScreenTitle() {
        val displayed = """
            最优配置。

            9.4 NoteTool: 结构化笔记

            Notetool 是为长时程任务提供的
            9.4.1 设计理念与应用场景
        """.trimIndent()
        val headingStarts = listOf(
            displayed.indexOf("9.4 NoteTool"),
            displayed.indexOf("9.4.1"),
        )
        val entry = currentChapterEntryFromHeadingStarts(
            tocEntries = toc,
            headingStarts = headingStarts,
            viewportTop = 0,
            viewportBottom = displayed.indexOf("Notetool"),
            windowStart = 0,
            windowEnd = 20000,
            displayedText = displayed,
        )
        assertEquals("9.4 NoteTool: 结构化笔记", entry?.title)
    }

    @Test
    fun headingSpans_keepPreviousSectionWhenNextHeadingNotVisible() {
        val displayed = """
            5. A/B测试对于关键参数
            最优配置。
            后续段落还在上一节。
        """.trimIndent()
        val headingStarts = listOf(0, 80)
        val windowToc = listOf(
            MarkdownTocEntry(level = 3, title = "9.4.4 与 ContextBuilder 的深度集成", sourceOffset = 10),
            MarkdownTocEntry(level = 2, title = "9.4 NoteTool: 结构化笔记", sourceOffset = 80),
        )
        val entry = currentChapterEntryFromHeadingStarts(
            tocEntries = windowToc,
            headingStarts = headingStarts,
            viewportTop = 20,
            viewportBottom = 50,
            windowStart = 0,
            windowEnd = 200,
            displayedText = displayed,
        )
        assertEquals("9.4.4 与 ContextBuilder 的深度集成", entry?.title)
    }

    @Test
    fun titleBar_usesPinnedVisibleChapterOffset() {
        val entry = chapterEntryForTitleBar(
            tocEntries = toc,
            topChar = 2000,
            bottomChar = 3500,
            visibleChapterOffset = 5000,
        )
        assertEquals("9.4 NoteTool: 结构化笔记", entry?.title)
    }
}
