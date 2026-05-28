package com.example.markdownreader.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownPreprocessorTest {

    @Test
    fun prepare_leavesTocPlaceholderUntouched() {
        val md = "# Alpha\n\n[TOC]\n\n## Beta\n"
        val out = MarkdownPreprocessor.prepare(md)
        assertTrue(out.contains("[TOC]"))
    }

    @Test
    fun expandFootnotes_replacesReferencesAndRemovesDefinitions() {
        val md = """
            Text[^note] here.

            [^note]: Foot body
        """.trimIndent()
        val out = MarkdownPreprocessor.expandFootnotes(md)
        assertFalse(out.contains("[^note]:"))
        assertTrue(out.contains("Foot body"))
        assertTrue(out.contains("<sup"))
        assertTrue(out.contains("脚注"))
        assertTrue(out.contains("↩"))
    }

    @Test
    fun expandDiagramFences_convertsMermaidToDiagramUri() {
        val md = """
            ```mermaid
            graph TD
              A-->B
            ```
        """.trimIndent()
        val out = MarkdownPreprocessor.expandDiagramFences(md)
        assertTrue(out.contains("diagram://mermaid/"))
        assertFalse(out.contains("```mermaid"))
        assertFalse(out.contains("flowchart LR")) // 源码已移入 payload store
    }

    @Test
    fun expandDiagramFences_convertsEchartsAlias() {
        val md = """
            ```chart
            {"xAxis":{},"yAxis":{},"series":[]}
            ```
        """.trimIndent()
        val out = MarkdownPreprocessor.expandDiagramFences(md)
        assertTrue(out.contains("diagram://echarts/"))
    }

    @Test
    fun diagramUri_usesShortId() {
        val uri = MarkdownPreprocessor.diagramUri("mermaid", "graph TD")
        assertTrue(uri.startsWith("diagram://mermaid/"))
        val id = uri.substringAfter("diagram://mermaid/")
        assertTrue(id.isNotEmpty())
        assertTrue(id.length < 64)
    }

    @Test
    fun normalizeReaderImageLayout_tightensHeadingBeforeImage() {
        val md = "## 7.4 HTML\n\n\n<img src=\"https://x.test/a.jpg\" alt=\"a\">\n"
        val out = MarkdownPreprocessor.normalizeReaderImageLayout(md)
        assertFalse(out.contains("## 7.4 HTML\n\n\n"))
        assertTrue(out.contains("## 7.4 HTML\n<img"))
    }

    @Test
    fun normalizeReaderImageLayout_tightensSingleNewlineBeforeImage() {
        val md = "## 7.4 HTML\n<img src=\"https://x.test/a.jpg\" alt=\"a\">\n"
        val out = MarkdownPreprocessor.normalizeReaderImageLayout(md)
        assertEquals("## 7.4 HTML\n<img src=\"https://x.test/a.jpg\" alt=\"a\">\n", out)
    }

    @Test
    fun normalizeReaderImageLayout_stripsEmptyCenterAndSurroundingBlankLines() {
        val md = "## 7.5\n<center>\n\n</center>\n\n## 7.6\n"
        val out = MarkdownPreprocessor.normalizeReaderImageLayout(md)
        assertFalse(out.contains("<center>"))
        assertEquals("## 7.5\n## 7.6\n", out)
    }

    @Test
    fun convertNetworkImagesToHtmlImg_linkedImageUsesHtmlWithSize() {
        val md = "[![点击](https://picsum.photos/400/200)](https://www.example.com)"
        val out = MarkdownPreprocessor.convertNetworkImagesToHtmlImg(md)
        assertTrue(out.contains("<img"))
        assertTrue(out.contains("width=\"400\""))
        assertTrue(out.contains("height=\"200\""))
        assertTrue(out.contains("<a href=\"https://www.example.com\">"))
        assertFalse(out.contains("!["))
    }

    @Test
    fun enrichHtmlImgTags_addsHeightForPicsumUrl() {
        val md = """<img src="https://picsum.photos/500/250" width="75%" alt="自适应">"""
        val out = MarkdownPreprocessor.enrichHtmlImgTags(md)
        assertTrue(out.contains("height=\"250\""))
        assertTrue(out.contains("<img"))
    }

    @Test
    fun prepare_keepsInlineLatexSingleDollar() {
        val d = "$"
        val input = "上标：${d}x^2${d}\n下标：${d}y_1${d}"
        val out = MarkdownPreprocessor.prepare(input)
        assertTrue(out.contains("上标：${d}x^2${d}"))
        assertTrue(out.contains("下标：${d}y_1${d}"))
        assertFalse(out.contains("${d}${d}"))
    }

    @Test
    fun expandHighlight_convertsMarkSyntax() {
        val out = MarkdownPreprocessor.expandHighlight("==高亮文本==")
        assertTrue(out.contains("<mark>高亮文本</mark>"))
    }
}
