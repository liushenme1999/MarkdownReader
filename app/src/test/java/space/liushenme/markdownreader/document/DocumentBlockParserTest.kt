package space.liushenme.markdownreader.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentBlockParserTest {

    @Test
    fun markdownParser_buildsImmutableBlocksWithSourceRanges() {
        val markdown = """
            # Title

            Intro paragraph.

            > quoted text

            - first
            - second

            | A | B |
            |---|---|
            | 1 | 2 |

            ```kotlin
            val answer = 42
            ```

            ${'$'}${'$'}x + y${'$'}${'$'}

            ![cover](https://example.com/cover.png)

            ![](diagram://mermaid/abc123)
        """.trimIndent()

        val blocks = DocumentBlockParser.parse(markdown, DocumentFormat.Markdown)

        assertTrue(blocks.any { it is DocumentBlock.Heading })
        assertTrue(blocks.any { it is DocumentBlock.Paragraph })
        assertTrue(blocks.any { it is DocumentBlock.Quote })
        assertTrue(blocks.any { it is DocumentBlock.ListBlock })
        assertTrue(blocks.any { it is DocumentBlock.Table })
        assertTrue(blocks.any { it is DocumentBlock.CodeBlock && it.language == "kotlin" })
        assertTrue(blocks.any { it is DocumentBlock.Formula })
        assertTrue(blocks.any { it is DocumentBlock.Image })
        assertTrue(blocks.any { it is DocumentBlock.Diagram })
        blocks.forEach { block ->
            assertEquals(block.source, markdown.substring(block.range.start, block.range.endExclusive))
            assertTrue(block.id.isNotBlank())
        }
    }

    @Test
    fun pdfParser_outputsPageImageBlocks() {
        val body = """
            <img src="book-asset://pdf_page_001.png" width="100%"/>
            <img src="book-asset://pdf_page_002.png" width="100%"/>
        """.trimIndent()

        val blocks = DocumentBlockParser.parse(body, DocumentFormat.Pdf)

        assertEquals(2, blocks.size)
        assertEquals(0, (blocks[0] as DocumentBlock.Image).pdfPageIndex)
        assertEquals(1, (blocks[1] as DocumentBlock.Image).pdfPageIndex)
    }

    @Test
    fun plainTextParser_usesNovelChapterRulesInsteadOfMarkdownSyntax() {
        val text = "第十二章 新旅程\n这里是正文。\n\n# 只是普通文本"

        val blocks = DocumentBlockParser.parse(text, DocumentFormat.PlainText)

        assertTrue(blocks.first() is DocumentBlock.Heading)
        assertEquals("第十二章 新旅程", (blocks.first() as DocumentBlock.Heading).title)
        assertTrue(blocks.last() is DocumentBlock.Paragraph)
    }

    @Test
    fun chunkIndex_keepsLargeDocumentWindowsBlockAligned() {
        val content = buildString {
            repeat(2_000) { index ->
                append("## Section ").append(index).append('\n')
                append("A paragraph with enough content to exercise incremental windows.\n\n")
            }
        }
        val blocks = DocumentBlockParser.parse(content, DocumentFormat.Markdown)
        val index = DocumentBlockParser.buildChunkIndex(
            contentLength = content.length,
            blocks = blocks,
            targetChars = 8 * 1024,
            maxChars = 16 * 1024,
        )

        assertTrue(index.chunks.size > 3)
        assertEquals(0, index.chunks.first().range.start)
        assertEquals(content.length, index.chunks.last().range.endExclusive)
        index.chunks.zipWithNext().forEach { (left, right) ->
            assertEquals(left.range.endExclusive, right.range.start)
        }
        val window = index.windowAround(content.length / 2)
        assertTrue(content.length / 2 in window)
        assertTrue(window.length < content.length)
    }

    @Test
    fun blockId_remainsStableWhenBlockMovesWithinDocument() {
        val original = DocumentBlockParser.parse("# Stable heading\n\nBody", DocumentFormat.Markdown)
        val moved = DocumentBlockParser.parse("Preface\n\n# Stable heading\n\nBody", DocumentFormat.Markdown)

        val firstHeading = original.filterIsInstance<DocumentBlock.Heading>().single()
        val movedHeading = moved.filterIsInstance<DocumentBlock.Heading>().single()
        assertEquals(firstHeading.id, movedHeading.id)
        assertTrue(firstHeading.range.start != movedHeading.range.start)
    }
}
