package space.liushenme.markdownreader.ui.screens.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderPagingEngineTest {

    @Test
    fun splitMarkdownToPages_splitsAtParagraphBreak() {
        val content = "a\n\n" + "b".repeat(500)
        val pages = splitMarkdownToPages(content, targetChars = 200)
        assertTrue(pages.size >= 2)
        assertEquals(0, pages.first().second)
        val joined = pages.joinToString("") { it.first }
        assertEquals(content, joined)
    }

    @Test
    fun computeReadingWindow_includesLookbehindBeforeJumpTarget() {
        val content = "x".repeat(200_000)
        val boundaries = intArrayOf(0, 150_000, 200_000)
        val (start, end) = computeReadingWindow(boundaries, charPos = 155_000, contentLen = content.length)
        assertTrue(start < 150_000)
        assertTrue(end > 155_000)
    }

    @Test
    fun previousWindowStart_alignsToEarlierChapter() {
        val boundaries = intArrayOf(0, 50_000, 100_000, 200_000)
        assertEquals(50_000, previousWindowStart(boundaries, 100_000))
        assertEquals(0, previousWindowStart(boundaries, 50_000))
    }

    @Test
    fun readingProgressForCharPos_mapsLinearly() {
        assertEquals(0.5f, readingProgressForCharPos(500, 1000), 0.001f)
    }

    @Test
    fun pdfPageIndexForTocOrBookmark_prefersTocEntryIndex() {
        val toc = listOf(
            MarkdownTocEntry(2, "第 1 页", 0),
            MarkdownTocEntry(2, "第 2 页", 100),
        )
        val idx = pdfPageIndexForTocOrBookmark(
            isPdfBook = true,
            tocEntries = toc,
            readerContent = "",
            charPos = 0,
            tocEntry = toc[1],
        )
        assertEquals(1, idx)
    }

    @Test
    fun resolveStoredCharPos_prefersCurrentPositionOverProgress() {
        val pos = resolveStoredCharPos(
            currentPosition = 800,
            readingProgress = 0.1f,
            contentLength = 1000,
            totalChars = 1000,
        )
        assertEquals(800, pos)
    }

    @Test
    fun proportionalSourceOffset_mapsRenderedOffsetLinearly() {
        val source = proportionalSourceOffset(
            windowStart = 10_000,
            windowEnd = 42_000,
            displayedLen = 5_000,
            renderedOffset = 2_500,
        )
        assertTrue(kotlin.math.abs(source - 26_000) <= 500)
    }

    @Test
    fun progressAnchorMapping_roundTripsAcrossCompressedWindow() {
        val windowStart = 10_000
        val windowEnd = 42_000
        val displayedLen = 5_000
        val sourceOffset = 26_000
        val rendered = resolveDisplayedCharOffsetForProgressRestore(
            sourceOffset = sourceOffset,
            windowStart = windowStart,
            windowEnd = windowEnd,
            displayedLen = displayedLen,
            renderPlainText = false,
        )
        val back = resolveSourceCharOffset(
            sourceContent = "x".repeat(windowEnd),
            windowStart = windowStart,
            windowEnd = windowEnd,
            displayedText = "y".repeat(displayedLen),
            renderedOffset = rendered,
            renderPlainText = false,
            tocEntries = emptyList(),
        )
        assertTrue(kotlin.math.abs(back - sourceOffset) <= 32)
    }

    @Test
    fun resolveSourceCharOffset_plainText_matchesDisplayedOffset() {
        val prefix = "intro\n\n"
        val chapter = "第三章"
        val body = "y".repeat(200)
        val full = prefix + chapter + "\n" + body
        val winStart = 0
        val displayed = full
        val sourceOffset = full.indexOf(chapter)
        val rendered = resolveDisplayedCharOffset(
            sourceContent = full,
            sourceOffset = sourceOffset,
            displayedText = displayed,
            renderPlainText = true,
            windowStart = winStart,
            tocEntries = emptyList(),
            preferredEntry = null,
        )
        val back = resolveSourceCharOffset(
            sourceContent = full,
            windowStart = winStart,
            windowEnd = full.length,
            displayedText = displayed,
            renderedOffset = rendered,
            renderPlainText = true,
            tocEntries = emptyList(),
        )
        assertEquals(sourceOffset, back)
    }

    @Test
    fun resolveDisplayedCharOffset_plainText_prefersTitleLineInWindow() {
        val prefix = "intro\n\n"
        val chapter = "第三章 风暴"
        val body = "y".repeat(200)
        val full = prefix + chapter + "\n" + body
        val winStart = 0
        val displayed = full.substring(winStart)
        val sourceOffset = full.indexOf(chapter)
        val offset = resolveDisplayedCharOffset(
            sourceContent = full,
            sourceOffset = sourceOffset,
            displayedText = displayed,
            renderPlainText = true,
            windowStart = winStart,
            tocEntries = listOf(
                MarkdownTocEntry(1, chapter, sourceOffset)
            ),
            preferredEntry = MarkdownTocEntry(1, chapter, sourceOffset),
        )
        assertEquals(sourceOffset, offset)
    }

    @Test
    fun resolveDisplayedCharOffset_markdown_tocJumpPrefersTitleOverProportional() {
        val section1 = "# 一、标题\n" + "x\n".repeat(40)
        val latexBlock = "上标：\$x^2\$" + " ".repeat(120) + "\n下标：\$y_1\$" + " ".repeat(120) + "\n"
        val section2Title = "# 二、基础文本样式"
        val full = section1 + latexBlock + section2Title + "\n正文"
        val sourceOffset = full.indexOf(section2Title)
        val rendered = "一、标题\n" + "x\n".repeat(40) +
            "上标：x²\n下标：y₁\n" +
            "二、基础文本样式\n正文"
        val toc = listOf(
            MarkdownTocEntry(1, "一、标题", 0),
            MarkdownTocEntry(1, "二、基础文本样式", sourceOffset),
        )
        val expected = rendered.indexOf("二、基础文本样式")
        val headingOffset = resolveDisplayedCharOffset(
            sourceContent = full,
            sourceOffset = sourceOffset,
            displayedText = rendered,
            renderPlainText = false,
            windowStart = 0,
            windowEnd = full.length,
            tocEntries = toc,
            preferredEntry = toc[1],
        )
        val proportional = resolveDisplayedCharOffsetForProgressRestore(
            sourceOffset = sourceOffset,
            windowStart = 0,
            windowEnd = full.length,
            displayedLen = rendered.length,
            renderPlainText = false,
        )
        assertEquals(expected, headingOffset)
        assertTrue(proportional != expected)
    }
}
