package com.example.markdownreader.importing

import com.example.markdownreader.ui.screens.reader.findPlainTextChapterOffsetInDisplayed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class PlainTextTocParserTest {

    @Test
    fun parse_usesTrimmedTitleStart_notLineLeadingWhitespace() {
        val text = "前言\n\n  第一章 开端\n正文"
        val entries = PlainTextTocParser.parse(text)
        val chapter = entries.single { it.title.startsWith("第一章") }
        assertEquals(text.indexOf("第一章"), chapter.sourceOffset)
    }

    @Test
    fun parse_handlesCrLfLineEndings() {
        val text = "第一章\r\n第二节\r\n"
        val entries = PlainTextTocParser.parse(text)
        assertEquals(2, entries.size)
        assertEquals(0, entries[0].sourceOffset)
        assertEquals(text.indexOf("第二节"), entries[1].sourceOffset)
    }

    @Test
    fun findPlainTextChapterOffsetInDisplayed_locatesTitleNearHint() {
        val full = "a\n\n" + "x".repeat(500) + "\n第二章\nbody"
        val windowStart = full.indexOf("第二章") - 20
        val displayed = full.substring(windowStart)
        val hint = full.indexOf("第二章") - windowStart
        val found = findPlainTextChapterOffsetInDisplayed(displayed, "第二章", hint)
        assertNotNull(found)
        assertEquals(hint, found)
    }
}
