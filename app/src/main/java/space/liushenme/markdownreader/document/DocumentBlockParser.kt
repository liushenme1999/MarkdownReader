package space.liushenme.markdownreader.document

import java.security.MessageDigest
import space.liushenme.markdownreader.importing.PlainTextTocParser

object DocumentBlockParser {
    private val heading = Regex("""^(#{1,6})\s+(.+?)\s*$""")
    private val unorderedList = Regex("""^\s*[-+*]\s+.+""")
    private val orderedList = Regex("""^\s*\d+[.)]\s+.+""")
    private val tableDivider = Regex("""^\s*\|?\s*:?-{3,}:?\s*(?:\|\s*:?-{3,}:?\s*)+\|?\s*$""")
    private val markdownImage = Regex("""!\[([^]]*)]\(([^)\s]+)(?:\s+["'][^"']*["'])?\)""")
    private val htmlImage = Regex("""<img\b[^>]*\bsrc\s*=\s*["']([^"']+)["'][^>]*>""", RegexOption.IGNORE_CASE)
    private val diagramUri = Regex("""diagram://[A-Za-z0-9_-]+/[A-Za-z0-9._-]+""")

    fun parse(content: String, format: DocumentFormat): List<DocumentBlock> {
        if (content.isEmpty()) return emptyList()
        if (format == DocumentFormat.Pdf) return parsePdf(content)
        if (format == DocumentFormat.PlainText) return parsePlainText(content)
        val lines = sourceLines(content)
        val blocks = ArrayList<DocumentBlock>()
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.text.trim()
            if (trimmed.isEmpty()) {
                i++
                continue
            }
            val fence = trimmed.takeIf { it.startsWith("```") || it.startsWith("~~~") }
            if (fence != null) {
                val marker = fence.take(3)
                val endLine = findClosingLine(lines, i + 1) { it.text.trim().startsWith(marker) }
                val range = SourceRange(line.start, lines[endLine].endExclusive)
                blocks += DocumentBlock.CodeBlock(
                    id = blockId("code", range, content),
                    range = range,
                    source = content.substring(range.start, range.endExclusive),
                    language = trimmed.drop(3).trim().takeIf { it.isNotEmpty() },
                )
                i = endLine + 1
                continue
            }
            if (trimmed.startsWith("$$")) {
                val endLine = if (trimmed.length > 2 && trimmed.endsWith("$$")) {
                    i
                } else {
                    findClosingLine(lines, i + 1) { it.text.trim().endsWith("$$") }
                }
                val range = SourceRange(line.start, lines[endLine].endExclusive)
                val source = content.substring(range.start, range.endExclusive)
                blocks += DocumentBlock.Formula(
                    id = blockId("formula", range, content),
                    range = range,
                    source = source,
                    expression = source.trim().removePrefix("$$").removeSuffix("$$").trim(),
                    block = true,
                )
                i = endLine + 1
                continue
            }
            heading.matchEntire(trimmed)?.let { match ->
                val range = line.range
                blocks += DocumentBlock.Heading(
                    id = blockId("heading", range, content),
                    range = range,
                    source = content.substring(range.start, range.endExclusive),
                    level = match.groupValues[1].length,
                    title = match.groupValues[2].trim(),
                )
                i++
                continue
            }
            if (i + 1 < lines.size && trimmed.contains('|') && tableDivider.matches(lines[i + 1].text)) {
                val endLine = consumeWhile(lines, i + 2) { it.text.isNotBlank() && it.text.contains('|') }
                blocks += blockForRange("table", line.start, lines[endLine].endExclusive, content) { id, range, source ->
                    DocumentBlock.Table(id, range, source)
                }
                i = endLine + 1
                continue
            }
            diagramUri.find(line.text)?.let { diagram ->
                val range = line.range
                blocks += DocumentBlock.Diagram(
                    id = blockId("diagram", range, content),
                    range = range,
                    source = content.substring(range.start, range.endExclusive),
                    destination = diagram.value,
                )
                i++
                continue
            }
            imageDestination(line.text)?.let { (alt, destination) ->
                val range = line.range
                blocks += DocumentBlock.Image(
                    id = blockId("image", range, content),
                    range = range,
                    source = content.substring(range.start, range.endExclusive),
                    destination = destination,
                    altText = alt,
                )
                i++
                continue
            }
            if (trimmed.startsWith('>')) {
                val endLine = consumeWhile(lines, i + 1) { it.text.trimStart().startsWith('>') }
                blocks += blockForRange("quote", line.start, lines[endLine].endExclusive, content) { id, range, source ->
                    DocumentBlock.Quote(id, range, source)
                }
                i = endLine + 1
                continue
            }
            if (unorderedList.matches(line.text) || orderedList.matches(line.text)) {
                val ordered = orderedList.matches(line.text)
                val endLine = consumeWhile(lines, i + 1) {
                    it.text.isBlank() || unorderedList.matches(it.text) || orderedList.matches(it.text) || it.text.startsWith("  ")
                }
                blocks += blockForRange("list", line.start, lines[endLine].endExclusive, content) { id, range, source ->
                    DocumentBlock.ListBlock(id, range, source, ordered)
                }
                i = endLine + 1
                continue
            }
            val endLine = consumeWhile(lines, i + 1) { next ->
                val value = next.text.trim()
                value.isNotEmpty() &&
                    !heading.matches(value) &&
                    !(value.startsWith("```") || value.startsWith("~~~") || value.startsWith("$$")) &&
                    !next.text.trimStart().startsWith('>') &&
                    !unorderedList.matches(next.text) && !orderedList.matches(next.text) &&
                    imageDestination(next.text) == null && diagramUri.find(next.text) == null
            }
            blocks += blockForRange("paragraph", line.start, lines[endLine].endExclusive, content) { id, range, source ->
                DocumentBlock.Paragraph(id, range, source)
            }
            i = endLine + 1
        }
        return blocks
    }

    fun buildChunkIndex(
        contentLength: Int,
        blocks: List<DocumentBlock>,
        targetChars: Int = 32 * 1024,
        maxChars: Int = 96 * 1024,
    ): DocumentChunkIndex {
        if (contentLength <= 0) return DocumentChunkIndex.EMPTY
        if (blocks.isEmpty()) return fixedChunkIndex(contentLength, targetChars)
        val chunks = ArrayList<DocumentChunk>()
        var firstBlock = 0
        var chunkStart = 0
        for (index in blocks.indices) {
            val blockEnd = blocks[index].range.endExclusive.coerceAtMost(contentLength)
            val chunkLength = blockEnd - chunkStart
            val nextWouldOverflow = index < blocks.lastIndex &&
                blocks[index + 1].range.endExclusive - chunkStart > maxChars
            if (chunkLength >= targetChars || nextWouldOverflow || index == blocks.lastIndex) {
                val end = if (index == blocks.lastIndex) contentLength else blockEnd.coerceAtLeast(chunkStart + 1)
                chunks += DocumentChunk(
                    index = chunks.size,
                    range = SourceRange(chunkStart, end),
                    firstBlockIndex = firstBlock,
                    lastBlockIndex = index,
                )
                chunkStart = end
                firstBlock = index + 1
            }
        }
        if (chunkStart < contentLength) {
            chunks += DocumentChunk(
                index = chunks.size,
                range = SourceRange(chunkStart, contentLength),
                firstBlockIndex = firstBlock.coerceAtMost(blocks.lastIndex),
                lastBlockIndex = blocks.lastIndex,
            )
        }
        return DocumentChunkIndex(contentLength, chunks)
    }

    private fun parsePdf(content: String): List<DocumentBlock> {
        val matches = Regex("""<img\b[^>]*\bsrc\s*=\s*["']([^"']+)["'][^>]*>""", RegexOption.IGNORE_CASE)
            .findAll(content)
            .toList()
        return matches.mapIndexed { index, match ->
            val range = SourceRange(match.range.first, match.range.last + 1)
            DocumentBlock.Image(
                id = blockId("pdf-page", range, content),
                range = range,
                source = match.value,
                destination = match.groupValues[1],
                altText = "PDF page ${index + 1}",
                pdfPageIndex = index,
            )
        }
    }

    private fun parsePlainText(content: String): List<DocumentBlock> {
        val lines = sourceLines(content)
        val tocByOffset = PlainTextTocParser.parse(content).associateBy { it.sourceOffset }
        val blocks = ArrayList<DocumentBlock>()
        var index = 0
        while (index < lines.size) {
            val line = lines[index]
            if (line.text.isBlank()) {
                index++
                continue
            }
            val trimmedStart = line.start + line.text.indexOfFirst { !it.isWhitespace() }.coerceAtLeast(0)
            val headingEntry = tocByOffset[trimmedStart]
            if (headingEntry != null) {
                val range = line.range
                blocks += DocumentBlock.Heading(
                    id = blockId("plain-heading", range, content),
                    range = range,
                    source = content.substring(range.start, range.endExclusive),
                    level = headingEntry.level,
                    title = headingEntry.title,
                )
                index++
                continue
            }
            val endLine = consumeWhile(lines, index + 1) { next ->
                if (next.text.isBlank()) return@consumeWhile false
                val nextStart = next.start + next.text.indexOfFirst { !it.isWhitespace() }.coerceAtLeast(0)
                nextStart !in tocByOffset
            }
            blocks += blockForRange(
                "plain-paragraph",
                line.start,
                lines[endLine].endExclusive,
                content,
            ) { id, range, source -> DocumentBlock.Paragraph(id, range, source) }
            index = endLine + 1
        }
        return blocks
    }

    private fun fixedChunkIndex(contentLength: Int, targetChars: Int): DocumentChunkIndex {
        val step = targetChars.coerceAtLeast(1)
        val chunks = buildList {
            var start = 0
            while (start < contentLength) {
                val end = (start + step).coerceAtMost(contentLength)
                add(DocumentChunk(size, SourceRange(start, end), 0, 0))
                start = end
            }
        }
        return DocumentChunkIndex(contentLength, chunks)
    }

    private data class SourceLine(val text: String, val start: Int, val endExclusive: Int) {
        val range: SourceRange get() = SourceRange(start, endExclusive)
    }

    private fun sourceLines(content: String): List<SourceLine> {
        val out = ArrayList<SourceLine>()
        var start = 0
        while (start < content.length) {
            val newline = content.indexOf('\n', start)
            val end = if (newline < 0) content.length else newline + 1
            val textEnd = if (newline < 0) end else newline
            out += SourceLine(content.substring(start, textEnd).trimEnd('\r'), start, end)
            start = end
        }
        return out
    }

    private fun findClosingLine(lines: List<SourceLine>, start: Int, predicate: (SourceLine) -> Boolean): Int {
        for (index in start until lines.size) if (predicate(lines[index])) return index
        return lines.lastIndex
    }

    private fun consumeWhile(lines: List<SourceLine>, start: Int, predicate: (SourceLine) -> Boolean): Int {
        var last = (start - 1).coerceAtLeast(0)
        var index = start
        while (index < lines.size && predicate(lines[index])) {
            last = index
            index++
        }
        return last
    }

    private fun imageDestination(line: String): Pair<String, String>? {
        markdownImage.find(line)?.let { return it.groupValues[1] to it.groupValues[2] }
        htmlImage.find(line)?.let { return "" to it.groupValues[1] }
        return null
    }

    private inline fun <T : DocumentBlock> blockForRange(
        kind: String,
        start: Int,
        end: Int,
        content: String,
        factory: (String, SourceRange, String) -> T,
    ): T {
        val range = SourceRange(start, end)
        return factory(blockId(kind, range, content), range, content.substring(start, end))
    }

    private fun blockId(kind: String, range: SourceRange, content: String): String {
        val seed = "$kind:${content.substring(range.start, range.endExclusive).trim()}"
        val digest = MessageDigest.getInstance("SHA-256").digest(seed.toByteArray())
        return digest.take(8).joinToString("") { "%02x".format(it) }
    }
}
