package com.example.markdownreader.ui.screens.reader

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
}
