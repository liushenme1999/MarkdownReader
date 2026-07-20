package space.liushenme.markdownreader.markdown

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
    fun expandFootnotes_insertsBlankLineBeforeRuleSoLastParagraphIsNotSetextHeading() {
        val md = """
            # 十二、脚注
            引用[^note1]
            [^note1]: 定义

            # 二十、末章
            最后一行正文
        """.trimIndent()
        val out = MarkdownPreprocessor.expandFootnotes(md)
        assertTrue(
            "footnote footer must be separated from last paragraph by a blank line",
            out.contains("最后一行正文\n\n---"),
        )
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
    fun convertNetworkImagesToHtmlImg_linkedImageWithoutKnownSizeStillUsesHtmlAnchor() {
        val md = "[![点击跳转官网](https://vcg05.cfp.cn/creative/vcg/nowater800/new/VCG211377589313.jpg)](https://www.example.com)"
        val out = MarkdownPreprocessor.convertNetworkImagesToHtmlImg(md)
        assertTrue(out.contains("<a href=\"https://www.example.com\">"))
        assertTrue(out.contains("src=\"https://vcg05.cfp.cn/creative/vcg/nowater800/new/VCG211377589313.jpg\""))
        assertTrue(out.contains("alt=\"点击跳转官网\""))
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
    fun unwrapCenteredLatexDivs_repairsIndentedTailWithoutCenterTag() {
        val d = "$"
        val md = """
            3. item
            <div align="center">
            ${d}${d}a${d}${d}
            </div>
                4. next item
        """.trimIndent()
        val out = MarkdownPreprocessor.prepare(md)
        assertFalse(out.contains("<div", ignoreCase = true))
        assertTrue(out.contains("\n4. next item"))
        assertFalse(out.contains("\n    4. next item"))
    }

    @Test
    fun prepare_expandsCenteredDivBlockLatexToMultiline() {
        val d = "$"
        val md = """
            text
            <div align="center">
            ${d}${d}\\text{Score}(Q_i, K_j) = Q_i \\cdot K_j${d}${d}
            </div>
        """.trimIndent()
        val out = MarkdownPreprocessor.prepare(md)
        assertFalse(out.contains("<div", ignoreCase = true))
        assertFalse("single-line block should be expanded: $out", out.contains("${d}${d}\\text{Score}"))
        val lines = out.lines().map { it.trim() }.filter { it.isNotEmpty() }
        assertEquals("text", lines[0])
        assertEquals("${d}${d}", lines[1])
        assertTrue(lines[2].contains("text{Score}"))
        assertEquals("${d}${d}", lines[3])
    }

    @Test
    fun expandSingleLineBlockLatex_splitsDelimitersOntoOwnLines() {
        val d = "$"
        val md = "${d}${d}\\frac{a}{b}${d}${d}"
        val out = MarkdownPreprocessor.expandSingleLineBlockLatex(md)
        assertEquals(
            """
            ${d}${d}
            \frac{a}{b}
            ${d}${d}
            """.trimIndent(),
            out,
        )
    }

    @Test
    fun normalizeBlockLatexSurroundings_dedentsIndentedParagraphAfterFormula() {
        val d = "$"
        val md = """
            3. item
            ${d}${d}a${d}${d}
                后续说明段落，不应显示为代码块。
        """.trimIndent()
        val out = MarkdownPreprocessor.normalizeBlockLatexSurroundings(md)
        assertTrue(out.contains("\n后续说明段落"))
        assertFalse(out.contains("\n    后续说明"))
    }

    @Test
    fun normalizeBlockLatexSurroundings_dedentsOrderedListAfterFormula() {
        val d = "$"
        val md = """
            3. item
            ${d}${d}a${d}${d}
                4. next
                5. more
        """.trimIndent()
        val out = MarkdownPreprocessor.normalizeBlockLatexSurroundings(md)
        assertTrue(out.contains("\n4. next"))
        assertFalse(out.contains("\n    4. next"))
    }

    @Test
    fun unwrapCenteredLatexDivs_extractsBlockFormula() {
        val d = "$"
        val md = """
            说明文字
            <div align="center">
            ${d}${d}\frac{a}{b}${d}${d}
            </div>
        """.trimIndent()
        val out = MarkdownPreprocessor.unwrapCenteredLatexDivs(md)
        assertFalse(out.contains("<div", ignoreCase = true))
        assertTrue(out.contains("${d}${d}\\frac{a}{b}${d}${d}"))
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

    @Test
    fun ensureBlankLineBeforeTables_insertsBlankWhenParagraphDirectlyPrecedesTable() {
        val md = """
            **核心概念：**
            | 概念 | 解释 |
            |------|------|
            | 节点 | 说明 |
        """.trimIndent()
        val out = MarkdownPreprocessor.ensureBlankLineBeforeTables(md)
        assertTrue(out.contains("**核心概念：**\n\n| 概念 | 解释 |"))
    }

    @Test
    fun ensureBlankLineBeforeTables_leavesExistingBlankLineUntouched() {
        val md = """
            **标题**

            | A | B |
            |---|---|
        """.trimIndent()
        val out = MarkdownPreprocessor.ensureBlankLineBeforeTables(md)
        assertEquals(md, out)
    }

    @Test
    fun ensureBlankLineBeforeTables_doesNotTreatPipeInParagraphAsTable() {
        val md = "Use | a | b | in text\n\nMore body"
        val out = MarkdownPreprocessor.ensureBlankLineBeforeTables(md)
        assertEquals(md, out)
    }

    @Test
    fun prepare_rendersTableAfterBoldWithoutManualBlankLine() {
        val md = """
            **核心概念：**
            | 概念 | 解释 |
            |------|------|
            | 节点 | 说明 |
        """.trimIndent()
        val out = MarkdownPreprocessor.prepare(md)
        assertTrue(out.contains("**核心概念：**\n\n| 概念 | 解释 |"))
    }
}
