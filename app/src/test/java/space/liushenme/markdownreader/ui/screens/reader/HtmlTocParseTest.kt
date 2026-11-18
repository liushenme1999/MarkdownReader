package space.liushenme.markdownreader.ui.screens.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HtmlTocParseTest {

    @Test
    fun parsesQuotedJsonArrayFromEvaluateJavascript() {
        val raw = """"[{\"t\":\"一、结论\",\"l\":2,\"i\":\"section-0\"}]""""
        val entries = parseHtmlToc(raw)
        assertEquals(1, entries.size)
        assertEquals("一、结论", entries[0].title)
        assertEquals(2, entries[0].level)
        assertEquals("section-0", entries[0].anchorId)
    }

    @Test
    fun parsesPlainJsonArray() {
        val raw = """[{"t":"1.1 先说结论","l":3,"i":"h-1"}]"""
        val entries = parseHtmlToc(raw)
        assertEquals(listOf("1.1 先说结论"), entries.map { it.title })
        assertEquals(listOf(3), entries.map { it.level })
    }

    @Test
    fun skipsEntriesWithoutTitleOrAnchor() {
        val raw = """[{"t":"","l":2,"i":"a"},{"t":"标题","l":2,"i":""},{"t":"保留","l":2,"i":"b"}]"""
        val entries = parseHtmlToc(raw)
        assertEquals(listOf("保留"), entries.map { it.title })
    }

    @Test
    fun clampsLevelIntoHeadingRange() {
        val raw = """[{"t":"深","l":9,"i":"a"},{"t":"浅","l":0,"i":"b"}]"""
        val entries = parseHtmlToc(raw)
        assertEquals(listOf(6, 1), entries.map { it.level })
    }

    @Test
    fun invalidInputReturnsEmptyList() {
        assertTrue(parseHtmlToc(null).isEmpty())
        assertTrue(parseHtmlToc("").isEmpty())
        assertTrue(parseHtmlToc("null").isEmpty())
        assertTrue(parseHtmlToc("not json").isEmpty())
        assertTrue(parseHtmlToc("[]").isEmpty())
    }
}
