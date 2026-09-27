package space.liushenme.markdownreader.ui.screens.reader.anchor

import space.liushenme.markdownreader.data.local.BookContentHasher
import space.liushenme.markdownreader.ui.screens.reader.MarkdownTocEntry
import kotlin.math.abs

/** 源码块：下标、半开区间、原文，以及归一化文本的 SHA-256。 */
internal data class SourceBlock(
    val index: Int,
    val start: Int,
    val end: Int,
    val text: String,
    val hash: String,
)

/**
 * 一篇正文的块表。Markdown 与 TXT 用不同切分规则，哈希只看归一化后的块文本。
 */
internal class TextBlockIndex(
    val blocks: List<SourceBlock>,
) {
    fun blockCovering(offset: Int): SourceBlock? {
        if (blocks.isEmpty()) return null
        var lo = 0
        var hi = blocks.lastIndex
        var best = 0
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (blocks[mid].start <= offset) {
                best = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        val candidate = blocks[best]
        return candidate.takeIf { offset >= it.start && offset < it.end }
    }

    companion object {
        const val QUOTE_CONTEXT_CHARS = 48
        const val HEADING_PATH_SEPARATOR = "\u001f"
        const val MAX_HEADING_DEPTH = 4
        const val LONG_BLOCK_CHARS = 2000
        const val SUBBLOCK_CHARS = 1000

        private val atxLine = Regex("""^#{1,6}(?:\s+|$)""")

        private fun isAtxLine(line: String): Boolean = atxLine.containsMatchIn(line)
        private val listLine = Regex("""^\s*(?:[-*+]|\d{1,3}[.)])\s+""")
        private val blankParagraph = Regex("""\n[ \t]*\n""")

        fun build(text: String, plainText: Boolean): TextBlockIndex {
            val ranges = if (plainText) plainRanges(text) else markdownRanges(text)
            val blocks = ranges.mapIndexed { index, (start, end) ->
                val body = text.substring(start, end)
                SourceBlock(
                    index = index,
                    start = start,
                    end = end,
                    text = body,
                    hash = BookContentHasher.sha256Hex(normalize(body)),
                )
            }
            return TextBlockIndex(blocks)
        }

        /** 归一化只用于哈希和比较：统一换行、压缩水平空白、去掉首尾空白。 */
        fun normalize(text: String): String =
            text
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replace(Regex("""[^\S\n]+"""), " ")
                .trim()

        /**
         * 位置之前的标题栈，最多 4 级，用单元分隔符拼接。
         * 同级或更深的标题会把栈顶换掉，因此路径表示当前位置所在的章节。
         */
        fun headingPath(toc: List<MarkdownTocEntry>, position: Int): String {
            if (toc.isEmpty()) return ""
            val stack = ArrayDeque<MarkdownTocEntry>()
            for (entry in toc) {
                if (entry.sourceOffset > position) break
                while (stack.isNotEmpty() && stack.last().level >= entry.level) {
                    stack.removeLast()
                }
                stack.addLast(entry)
            }
            if (stack.isEmpty()) return ""
            return stack.takeLast(MAX_HEADING_DEPTH)
                .joinToString(HEADING_PATH_SEPARATOR) { it.title }
        }

        /**
         * [headingPath] 对应的章节半开区间。路径为空或对不上目录时返回 null，
         * 调用方再决定是搜全文还是只搜提示块附近。
         */
        fun sectionRange(
            textLength: Int,
            toc: List<MarkdownTocEntry>,
            headingPath: String?,
            positionHint: Int,
        ): IntRange? {
            if (headingPath.isNullOrEmpty() || toc.isEmpty() || textLength <= 0) return null
            val wanted = headingPath.split(HEADING_PATH_SEPARATOR)
            val stack = ArrayDeque<MarkdownTocEntry>()
            var best: MarkdownTocEntry? = null
            var bestDist = Int.MAX_VALUE
            for (entry in toc) {
                while (stack.isNotEmpty() && stack.last().level >= entry.level) {
                    stack.removeLast()
                }
                stack.addLast(entry)
                val path = stack.map { it.title }.takeLast(MAX_HEADING_DEPTH)
                if (path != wanted) continue
                val dist = abs(entry.sourceOffset - positionHint)
                if (dist < bestDist) {
                    bestDist = dist
                    best = entry
                }
            }
            val hit = best ?: return null
            val next = toc.firstOrNull { it.sourceOffset > hit.sourceOffset && it.level <= hit.level }
            val end = (next?.sourceOffset ?: textLength).coerceIn(hit.sourceOffset, textLength)
            return hit.sourceOffset until end
        }

        private fun markdownRanges(text: String): List<Pair<Int, Int>> {
            if (text.isEmpty()) return emptyList()
            val lines = lineSpans(text)
            val raw = mutableListOf<Pair<Int, Int>>()
            var i = 0
            while (i < lines.size) {
                val content = text.substring(lines[i].start, lines[i].contentEnd)
                if (content.isBlank()) {
                    i++
                    continue
                }
                val fence = fenceMarker(content)
                if (fence != null) {
                    var j = i + 1
                    while (j < lines.size) {
                        val inner = text.substring(lines[j].start, lines[j].contentEnd)
                        val closed = closesFence(inner, fence.first, fence.second)
                        j++
                        if (closed) break
                    }
                    addTrimmed(text, lines[i].start, lines[j - 1].contentEnd, raw)
                    i = j
                    continue
                }
                if (isAtxLine(content)) {
                    addTrimmed(text, lines[i].start, lines[i].contentEnd, raw)
                    i++
                    continue
                }
                if (listLine.containsMatchIn(content)) {
                    var j = i + 1
                    while (j < lines.size) {
                        val inner = text.substring(lines[j].start, lines[j].contentEnd)
                        if (inner.isBlank()) break
                        if (fenceMarker(inner) != null || isAtxLine(inner) || listLine.containsMatchIn(inner)) {
                            break
                        }
                        if (!inner.startsWith(" ") && !inner.startsWith("\t")) break
                        j++
                    }
                    addTrimmed(text, lines[i].start, lines[j - 1].contentEnd, raw)
                    i = j
                    continue
                }
                var j = i + 1
                while (j < lines.size) {
                    val inner = text.substring(lines[j].start, lines[j].contentEnd)
                    if (inner.isBlank()) break
                    if (fenceMarker(inner) != null || isAtxLine(inner) || listLine.containsMatchIn(inner)) {
                        break
                    }
                    j++
                }
                addTrimmed(text, lines[i].start, lines[j - 1].contentEnd, raw)
                i = j
            }
            return raw
        }

        private fun plainRanges(text: String): List<Pair<Int, Int>> {
            if (text.isEmpty()) return emptyList()
            val pieces = if (blankParagraph.containsMatchIn(text)) {
                splitOnBlankLines(text)
            } else {
                lineSpans(text).mapNotNull { span ->
                    trimRange(text, span.start, span.contentEnd)
                }
            }
            return pieces.flatMap { (start, end) -> subdividePlain(text, start, end) }
        }

        private fun splitOnBlankLines(text: String): List<Pair<Int, Int>> {
            val out = mutableListOf<Pair<Int, Int>>()
            var cursor = 0
            for (match in blankParagraph.findAll(text)) {
                trimRange(text, cursor, match.range.first)?.let { out += it }
                cursor = match.range.last + 1
            }
            trimRange(text, cursor, text.length)?.let { out += it }
            return out
        }

        private fun subdividePlain(text: String, start: Int, end: Int): List<Pair<Int, Int>> {
            if (end - start <= LONG_BLOCK_CHARS) return listOf(start to end)
            val out = mutableListOf<Pair<Int, Int>>()
            var cursor = start
            while (cursor < end) {
                val hard = (cursor + SUBBLOCK_CHARS).coerceAtMost(end)
                if (hard == end) {
                    trimRange(text, cursor, end)?.let { out += it }
                    break
                }
                val newline = text.lastIndexOf('\n', hard - 1)
                if (newline > cursor) {
                    trimRange(text, cursor, newline)?.let { out += it }
                    cursor = newline + 1
                } else {
                    trimRange(text, cursor, hard)?.let { out += it }
                    cursor = hard
                }
            }
            return out
        }

        private fun addTrimmed(text: String, start: Int, end: Int, out: MutableList<Pair<Int, Int>>) {
            trimRange(text, start, end)?.let { out += it }
        }

        private fun trimRange(text: String, start: Int, end: Int): Pair<Int, Int>? {
            var s = start.coerceIn(0, text.length)
            var e = end.coerceIn(s, text.length)
            while (s < e && text[s].isWhitespace()) s++
            while (e > s && text[e - 1].isWhitespace()) e--
            if (s >= e) return null
            return s to e
        }

        private data class LineSpan(val start: Int, val contentEnd: Int, val nextStart: Int)

        private fun lineSpans(text: String): List<LineSpan> {
            val out = ArrayList<LineSpan>()
            var i = 0
            while (i < text.length) {
                val start = i
                while (i < text.length && text[i] != '\n') i++
                var contentEnd = i
                if (contentEnd > start && text[contentEnd - 1] == '\r') contentEnd--
                val next = if (i < text.length && text[i] == '\n') i + 1 else i
                out += LineSpan(start, contentEnd, next)
                if (next <= start) break
                i = next
            }
            return out
        }

        /** 行首围栏：标记字符和长度。允许最多 3 个前导空格。 */
        private fun fenceMarker(line: String): Pair<Char, Int>? {
            var spaces = 0
            while (spaces < line.length && spaces < 3 && line[spaces] == ' ') spaces++
            val rest = line.substring(spaces)
            if (rest.length < 3) return null
            val marker = rest[0]
            if (marker != '`' && marker != '~') return null
            var count = 0
            while (count < rest.length && rest[count] == marker) count++
            if (count < 3) return null
            return marker to count
        }

        private fun closesFence(line: String, marker: Char, count: Int): Boolean {
            var spaces = 0
            while (spaces < line.length && spaces < 3 && line[spaces] == ' ') spaces++
            val rest = line.substring(spaces).trimEnd()
            if (rest.length < count) return false
            return rest.all { it == marker }
        }
    }
}
