package com.example.markdownreader.importing

/**
 * HTML / XHTML → Markdown 流式转换。
 *
 * 设计目标：把 EPUB / MOBI / DOCX 这类「源就是 HTML 的格式」中的结构信息
 * （标题、列表、引用、代码、表格、强调、链接、图片、水平线）保留下来，交给
 * Markwon（已启用 Core / HTML / Tables / Strikethrough / Linkify）渲染。
 *
 * 工程上参考 turndown.js / commonmark-java：用一个容错的扫描器拆出 token，
 * 状态机维护「当前段缓冲 inline + 块级 out」，块边界处 flush。
 *
 * 已支持：
 * - 标题 h1..h6 → ATX `#` ~ `######`
 * - 段落 p / div / section 等 → 段落
 * - 换行 br → 双空格 + LF（HTML 的 hard break）
 * - 强调 strong/b → `**`、em/i → `*`、del/s → `~~`、code(inline) → `` ` ``
 * - 代码块 pre → ``` ```
 * - 引用 blockquote → 每行 `> `（支持嵌套）
 * - 列表 ul/ol/li → `- ` / `1. `（支持嵌套与缩进）
 * - 链接 a[href=http/https/file] → `[文本](href)`；锚点链接（如 EPUB 内部跳转）仅保留文本
 * - 图片 img → `![alt](src)`（保留原 src / MOBI `recindex` 标识）
 * - 水平线 hr → `---`
 * - 表格 table/tr/th/td → GFM 表格（嵌套表格会被打平为文本）
 * - script / style 直接丢弃；HTML 注释忽略
 * - 公共 HTML 实体（含数字实体）解码
 *
 * 同时收集 `<img>` 的 src 列表，便于上层抽取并把内嵌图片打包到资源目录。
 */
internal object HtmlToMarkdownConverter {

    data class Result(
        /** 转换后的 Markdown 文本。 */
        val markdown: String,
        /** 文档中按出现顺序出现过的图片 src（EPUB 相对路径或 MOBI `recindex:00109` 风格），保留原值。 */
        val imageSources: List<String>
    )

    fun convert(html: String): Result {
        if (html.isBlank()) return Result("", emptyList())
        val converter = Converter()
        converter.run(html)
        return Result(converter.markdown(), converter.images())
    }

    /** 纯文本快捷接口（不需要图片清单时用）。 */
    fun convertToMarkdown(html: String): String = convert(html).markdown

    // ===== 内部状态机实现 =====

    private class Converter {
        private val out = StringBuilder()
        private val inline = StringBuilder()
        private val lists = ArrayDeque<ListState>()
        private var blockquoteDepth = 0

        // pre 块：进入后所有纯文本直接进 codeBuf，遇到 </pre> 才产出 fenced code
        private var inPre = false
        private val codeBuf = StringBuilder()
        private var inlineCodeDepth = 0

        // <script>/<style> 跳过
        private var skipDepth = 0

        // 当前打开的 <hN> 级别（1..6），用于 flush 时加 `# ` 前缀
        private var headingLevel = 0

        private var tableState: TableState? = null

        /**
         * <a> 嵌套栈：open 时记下当前 inline / cellBuf 末尾位置；close 时把这段内容
         * 包成 `[文本](href)`。href 为锚点（`#xxx`）或空时只保留文本。
         */
        private val anchorStack = ArrayDeque<AnchorFrame>()

        /** MathML 子树构建栈；非空表示当前在 `<math>` 内。 */
        private val mathStack = ArrayDeque<MathFrame>()
        private var mathDisplay = false

        private val imageSrcs = mutableListOf<String>()

        fun run(html: String) {
            val reader = HtmlReader(html)
            while (true) {
                val tok = reader.next() ?: break
                when (tok) {
                    is Token.Text -> handleText(tok.text)
                    is Token.OpenTag -> handleOpen(tok.name, tok.attrs)
                    is Token.CloseTag -> handleClose(tok.name)
                    Token.Comment -> Unit
                }
            }
            flushParagraph()
            val collapsed = out.toString()
                .replace(Regex("\n{3,}"), "\n\n")
                .trim()
            out.setLength(0)
            out.append(collapsed)
        }

        fun markdown(): String = out.toString()
        fun images(): List<String> = imageSrcs.toList()

        // ----- 文本 -----

        private fun handleText(raw: String) {
            if (skipDepth > 0) return
            val decoded = decodeEntities(raw)
            if (mathStack.isNotEmpty()) {
                mathStack.last().text.append(decoded)
                return
            }
            if (inPre) {
                codeBuf.append(decoded)
                return
            }
            val ts = tableState
            if (ts != null && ts.inCell) {
                ts.cellBuf.append(escapeInline(collapseInlineWhitespace(decoded), forTable = true))
                return
            }
            if (decoded.isEmpty()) return
            inline.append(escapeInline(collapseInlineWhitespace(decoded), forTable = false))
        }

        // ----- 标签处理 -----

        private fun handleOpen(name: String, attrs: Map<String, String>) {
            // 进入 math 子树后，所有标签都按 MathML 含义在 math 栈里建子节点
            if (mathStack.isNotEmpty() && name != "math") {
                val frame = MathFrame(name, attrs)
                mathStack.last().children += frame
                mathStack.addLast(frame)
                return
            }
            when (name) {
                "math" -> {
                    flushParagraph()
                    mathDisplay = attrs["display"].equals("block", ignoreCase = true)
                    mathStack.addLast(MathFrame("math", attrs))
                    return
                }
                "script", "style" -> skipDepth++
                "br" -> {
                    if (inPre) codeBuf.append('\n')
                    else appendToActiveBuffer("<br>", hardBreakInParagraph = true)
                }
                "hr" -> {
                    flushParagraph()
                    appendBlockLine("---")
                }
                "h1", "h2", "h3", "h4", "h5", "h6" -> {
                    flushParagraph()
                    headingLevel = name[1].digitToInt()
                }
                "p", "div", "section", "article", "header", "footer",
                "aside", "main", "figure", "figcaption", "address" -> {
                    flushParagraph()
                }
                "blockquote" -> {
                    flushParagraph()
                    blockquoteDepth++
                }
                "ul" -> {
                    flushParagraph()
                    lists.addLast(ListState(ordered = false))
                }
                "ol" -> {
                    flushParagraph()
                    val start = attrs["start"]?.toIntOrNull() ?: 1
                    lists.addLast(ListState(ordered = true, counter = start))
                }
                "li" -> {
                    flushParagraph()
                    lists.lastOrNull()?.pendingMarker = true
                }
                "pre" -> {
                    flushParagraph()
                    inPre = true
                    codeBuf.setLength(0)
                }
                "code" -> {
                    if (inPre) return
                    inlineCodeDepth++
                    if (inlineCodeDepth == 1) currentBuffer().append('`')
                }
                "strong", "b" -> if (!inPre) currentBuffer().append("**")
                "em", "i" -> if (!inPre) currentBuffer().append('*')
                "del", "s", "strike" -> if (!inPre) currentBuffer().append("~~")
                "sub" -> if (!inPre) currentBuffer().append("<sub>")
                "sup" -> if (!inPre) currentBuffer().append("<sup>")
                "u" -> if (!inPre) currentBuffer().append("<u>")
                "mark" -> if (!inPre) currentBuffer().append("<mark>")
                "a" -> {
                    val href = attrs["href"]?.trim().orEmpty()
                    val inCell = tableState?.inCell == true
                    val target = if (inCell) AnchorTarget.TableCell else AnchorTarget.Inline
                    val buf = activeCellBuffer() ?: inline
                    anchorStack.addLast(AnchorFrame(href, target, startPos = buf.length))
                }
                "img" -> {
                    val src = attrs["src"] ?: attrs["recindex"]?.let { "recindex:$it" }
                    val alt = (attrs["alt"] ?: "").trim()
                    if (!src.isNullOrBlank()) {
                        imageSrcs += src
                        val md = "![${escapeAlt(alt)}](${escapeUrl(src)})"
                        when {
                            inPre -> codeBuf.append(md)
                            else -> (activeCellBuffer() ?: inline).append(md)
                        }
                    }
                }
                "table" -> {
                    flushParagraph()
                    tableState = TableState()
                }
                "thead", "tbody", "tfoot", "colgroup", "col", "caption" -> Unit
                "tr" -> {
                    tableState?.currentRow = mutableListOf()
                }
                "th", "td" -> {
                    tableState?.let { ts ->
                        ts.inCell = true
                        ts.cellBuf.setLength(0)
                        if (name == "th") ts.hasHeaderRow = true
                    }
                }
                else -> Unit
            }
        }

        private fun handleClose(name: String) {
            // 关闭 math 子节点
            if (mathStack.isNotEmpty()) {
                if (name == "math") {
                    val root = mathStack.first()
                    mathStack.clear()
                    val latex = MathMlToLatex.convert(root).trim()
                    if (latex.isNotEmpty()) {
                        if (mathDisplay) {
                            flushParagraph()
                            ensureBlankLine()
                            out.append("$$\n").append(latex).append("\n$$\n\n")
                        } else {
                            currentBuffer().append('$').append(latex).append('$')
                        }
                    }
                    mathDisplay = false
                    return
                }
                // 弹出最近一个匹配名的 math frame（容错：缺少匹配则不动栈）
                if (mathStack.last().name == name) {
                    mathStack.removeLast()
                }
                return
            }
            when (name) {
                "script", "style" -> if (skipDepth > 0) skipDepth--
                "h1", "h2", "h3", "h4", "h5", "h6" -> {
                    if (headingLevel > 0) {
                        flushParagraph()
                        headingLevel = 0
                    }
                }
                "p", "div", "section", "article", "header", "footer",
                "aside", "main", "figure", "figcaption", "address" -> {
                    flushParagraph()
                }
                "blockquote" -> {
                    flushParagraph()
                    if (blockquoteDepth > 0) blockquoteDepth--
                }
                "ul", "ol" -> {
                    flushParagraph()
                    if (lists.isNotEmpty()) lists.removeLast()
                }
                "li" -> flushParagraph()
                "pre" -> {
                    if (inPre) {
                        inPre = false
                        val body = codeBuf.toString().trimEnd('\n')
                        codeBuf.setLength(0)
                        if (body.isNotEmpty()) {
                            ensureBlankLine()
                            out.append("```\n").append(body).append("\n```\n\n")
                        }
                    }
                }
                "code" -> {
                    if (inPre) return
                    if (inlineCodeDepth > 0) {
                        inlineCodeDepth--
                        if (inlineCodeDepth == 0) currentBuffer().append('`')
                    }
                }
                "strong", "b" -> if (!inPre) currentBuffer().append("**")
                "em", "i" -> if (!inPre) currentBuffer().append('*')
                "del", "s", "strike" -> if (!inPre) currentBuffer().append("~~")
                "sub" -> if (!inPre) currentBuffer().append("</sub>")
                "sup" -> if (!inPre) currentBuffer().append("</sup>")
                "u" -> if (!inPre) currentBuffer().append("</u>")
                "mark" -> if (!inPre) currentBuffer().append("</mark>")
                "a" -> {
                    val frame = anchorStack.removeLastOrNull() ?: return
                    val buf = if (frame.target == AnchorTarget.TableCell) tableState?.cellBuf ?: inline else inline
                    val content = buf.substring(frame.startPos)
                    val href = frame.href
                    // 只允许 Android 能真正打开的协议变成 Markdown 链接；其他（EPUB / KF8 内部跳转
                    // 锚点 `#xxx`、`kindle:pos:fid:...`、`recindex:...` 等）退化为纯文本，
                    // 否则 LinkifyPlugin 把它们当成 URL，点击时会 startActivity 失败。
                    val hrefLow = href.lowercase()
                    val useLink = content.isNotBlank() && (
                        hrefLow.startsWith("http://") ||
                            hrefLow.startsWith("https://") ||
                            hrefLow.startsWith("mailto:") ||
                            hrefLow.startsWith("tel:")
                        )
                    if (useLink) {
                        buf.setLength(frame.startPos)
                        buf.append('[').append(content).append("](").append(escapeUrl(href)).append(')')
                    }
                }
                "th", "td" -> {
                    tableState?.let { ts ->
                        if (ts.inCell) {
                            ts.inCell = false
                            ts.currentRow?.add(ts.cellBuf.toString().trim())
                            ts.cellBuf.setLength(0)
                        }
                    }
                }
                "tr" -> {
                    tableState?.let { ts ->
                        val row = ts.currentRow ?: return
                        if (row.isNotEmpty()) ts.rows += row
                        ts.currentRow = null
                    }
                }
                "table" -> {
                    val ts = tableState ?: return
                    tableState = null
                    flushTable(ts)
                }
                else -> Unit
            }
        }

        private fun activeCellBuffer(): StringBuilder? =
            tableState?.takeIf { it.inCell }?.cellBuf

        private fun currentBuffer(): StringBuilder = activeCellBuffer() ?: inline

        /** 表格单元格内写 `<br>`；否则段落内 hard break。 */
        private fun appendToActiveBuffer(text: String, hardBreakInParagraph: Boolean = false) {
            val cell = activeCellBuffer()
            if (cell != null) {
                cell.append(text)
            } else if (hardBreakInParagraph) {
                inline.append("  \n")
            } else {
                inline.append(text)
            }
        }

        // ----- 段落 / 块输出 -----

        private fun flushParagraph() {
            if (inline.isBlank()) {
                inline.setLength(0)
                return
            }
            val text = inline.toString()
                .replace(Regex("[ \t]+\n"), "\n")
                .trim()
            inline.setLength(0)
            if (text.isEmpty()) return

            val prefix = buildPrefix()
            val lines = text.split('\n')
            ensureBlankLine()
            when {
                headingLevel in 1..6 -> {
                    val marker = "#".repeat(headingLevel) + " "
                    out.append(prefix).append(marker).append(lines.joinToString(" ").trim()).append('\n')
                }
                lists.isNotEmpty() && lists.last().pendingMarker -> {
                    val st = lists.last()
                    val marker = if (st.ordered) "${st.counter}. " else "- "
                    st.pendingMarker = false
                    if (st.ordered) st.counter++
                    val indent = listIndent(skipLast = true)
                    out.append(prefix).append(indent).append(marker).append(lines.first()).append('\n')
                    val contIndent = " ".repeat(marker.length)
                    for (i in 1 until lines.size) {
                        out.append(prefix).append(indent).append(contIndent).append(lines[i]).append('\n')
                    }
                }
                else -> {
                    for (l in lines) out.append(prefix).append(l).append('\n')
                }
            }
            out.append('\n')
        }

        private fun appendBlockLine(line: String) {
            ensureBlankLine()
            out.append(buildPrefix()).append(line).append("\n\n")
        }

        private fun buildPrefix(): String =
            if (blockquoteDepth == 0) "" else "> ".repeat(blockquoteDepth)

        private fun listIndent(skipLast: Boolean = false): String {
            if (lists.isEmpty()) return ""
            val limit = if (skipLast) lists.size - 1 else lists.size
            if (limit <= 0) return ""
            val sb = StringBuilder()
            for (i in 0 until limit) {
                val st = lists.elementAt(i)
                sb.append(if (st.ordered) "   " else "  ")
            }
            return sb.toString()
        }

        private fun ensureBlankLine() {
            if (out.isEmpty()) return
            if (!out.endsWith("\n\n")) {
                if (out.endsWith("\n")) out.append('\n') else out.append("\n\n")
            }
        }

        private fun flushTable(ts: TableState) {
            if (ts.rows.isEmpty()) return
            val cols = ts.rows.maxOf { it.size }
            if (cols == 0) return
            val padded = ts.rows.map { row ->
                if (row.size == cols) row else row + List(cols - row.size) { "" }
            }
            ensureBlankLine()
            val header = padded[0]
            out.append("| ").append(header.joinToString(" | ") { sanitizeTableCell(it) }).append(" |\n")
            out.append("|")
            repeat(cols) { out.append(" --- |") }
            out.append('\n')
            for (i in 1 until padded.size) {
                val row = padded[i]
                out.append("| ").append(row.joinToString(" | ") { sanitizeTableCell(it) }).append(" |\n")
            }
            out.append('\n')
        }

        private fun sanitizeTableCell(s: String): String =
            s.replace('\n', ' ').replace(Regex("\\s+"), " ").replace("|", "\\|").trim()
    }

    private data class ListState(
        val ordered: Boolean,
        var counter: Int = 1,
        var pendingMarker: Boolean = false
    )

    private class TableState {
        val rows = mutableListOf<List<String>>()
        var currentRow: MutableList<String>? = null
        var inCell = false
        val cellBuf = StringBuilder()
        var hasHeaderRow = false
    }

    private enum class AnchorTarget { Inline, TableCell }
    private data class AnchorFrame(val href: String, val target: AnchorTarget, val startPos: Int)

    /** 用于在 `<math>` 内构建轻量 DOM，等 `</math>` 时整树转 LaTeX。 */
    internal class MathFrame(val name: String, val attrs: Map<String, String> = emptyMap()) {
        val children = mutableListOf<MathFrame>()
        val text = StringBuilder()
    }

    // ===== inline 工具 =====

    private fun collapseInlineWhitespace(s: String): String {
        if (s.isEmpty()) return s
        return s.replace('\r', '\n')
            .replace(Regex("[\n\t]+"), " ")
            .replace(Regex(" {2,}"), " ")
    }

    private fun escapeInline(s: String, forTable: Boolean): String {
        if (s.isEmpty()) return s
        val sb = StringBuilder(s.length)
        for (ch in s) {
            when (ch) {
                '\\' -> sb.append("\\\\")
                '*' -> sb.append("\\*")
                '_' -> sb.append("\\_")
                '`' -> sb.append("\\`")
                '[' -> sb.append("\\[")
                ']' -> sb.append("\\]")
                '|' -> if (forTable) sb.append("\\|") else sb.append('|')
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    private fun escapeAlt(s: String): String = s.replace("]", "\\]").replace("[", "\\[")
    private fun escapeUrl(s: String): String = s.replace(" ", "%20").replace(")", "%29").replace("(", "%28")

    // ===== HTML 实体解码 =====

    private val NAMED_ENTITIES = mapOf(
        "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'",
        "nbsp" to "\u00A0",
        "copy" to "\u00A9", "reg" to "\u00AE", "trade" to "\u2122",
        "hellip" to "\u2026", "mdash" to "\u2014", "ndash" to "\u2013",
        "lsquo" to "\u2018", "rsquo" to "\u2019", "ldquo" to "\u201C", "rdquo" to "\u201D",
        "laquo" to "\u00AB", "raquo" to "\u00BB",
        "middot" to "\u00B7", "bull" to "\u2022",
        "deg" to "\u00B0", "plusmn" to "\u00B1", "times" to "\u00D7", "divide" to "\u00F7"
    )

    internal fun decodeEntities(s: String): String {
        if (s.indexOf('&') < 0) return s
        val sb = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val ch = s[i]
            if (ch != '&') {
                sb.append(ch); i++; continue
            }
            val semi = s.indexOf(';', i + 1)
            if (semi < 0 || semi - i > 12) {
                sb.append(ch); i++; continue
            }
            val name = s.substring(i + 1, semi)
            val replaced = when {
                name.startsWith("#x") || name.startsWith("#X") ->
                    name.substring(2).toIntOrNull(16)?.let { codePointToString(it) }
                name.startsWith("#") ->
                    name.substring(1).toIntOrNull()?.let { codePointToString(it) }
                else -> NAMED_ENTITIES[name]
            }
            if (replaced != null) {
                sb.append(replaced)
                i = semi + 1
            } else {
                sb.append(ch); i++
            }
        }
        return sb.toString()
    }

    private fun codePointToString(cp: Int): String? {
        if (cp <= 0 || cp > 0x10FFFF) return null
        return try {
            String(Character.toChars(cp))
        } catch (_: Throwable) {
            null
        }
    }

    // ===== HTML token reader =====

    private sealed class Token {
        data class Text(val text: String) : Token()
        data class OpenTag(val name: String, val attrs: Map<String, String>) : Token()
        data class CloseTag(val name: String) : Token()
        object Comment : Token()
    }

    private class HtmlReader(private val s: String) {
        private var pos = 0
        private val n = s.length

        fun next(): Token? {
            if (pos >= n) return null
            val ch = s[pos]
            if (ch != '<') {
                val lt = s.indexOf('<', pos)
                val text = if (lt < 0) s.substring(pos) else s.substring(pos, lt)
                pos = if (lt < 0) n else lt
                return Token.Text(text)
            }
            if (s.startsWith("<!--", pos)) {
                val end = s.indexOf("-->", pos + 4)
                pos = if (end < 0) n else end + 3
                return Token.Comment
            }
            if (s.startsWith("<![CDATA[", pos)) {
                val end = s.indexOf("]]>", pos + 9)
                val text = if (end < 0) s.substring(pos + 9) else s.substring(pos + 9, end)
                pos = if (end < 0) n else end + 3
                return Token.Text(text)
            }
            if (s.startsWith("<!", pos) || s.startsWith("<?", pos)) {
                val end = s.indexOf('>', pos)
                pos = if (end < 0) n else end + 1
                return Token.Comment
            }
            val end = findTagEnd(pos)
            if (end < 0) {
                val text = s.substring(pos)
                pos = n
                return Token.Text(text)
            }
            val inner = s.substring(pos + 1, end).trim()
            pos = end + 1
            return parseTag(inner)
        }

        /** `>` 在属性双引号 / 单引号内不算结束。 */
        private fun findTagEnd(from: Int): Int {
            var i = from + 1
            var quote = 0.toChar()
            while (i < n) {
                val c = s[i]
                if (quote != 0.toChar()) {
                    if (c == quote) quote = 0.toChar()
                } else {
                    if (c == '"' || c == '\'') quote = c
                    else if (c == '>') return i
                }
                i++
            }
            return -1
        }

        private fun parseTag(inner: String): Token {
            if (inner.isEmpty()) return Token.Comment
            if (inner[0] == '/') {
                val name = inner.substring(1).trim().substringBefore(' ').lowercase()
                return Token.CloseTag(stripNs(name))
            }
            val raw = if (inner.endsWith("/")) inner.substring(0, inner.length - 1).trim() else inner
            val parts = raw.splitAttrs()
            if (parts.isEmpty()) return Token.Comment
            val name = stripNs(parts[0].lowercase())
            val attrs = parseAttrs(parts.drop(1))
            return Token.OpenTag(name, attrs)
        }

        private fun stripNs(name: String): String {
            val i = name.indexOf(':')
            return if (i < 0) name else name.substring(i + 1)
        }

        private fun String.splitAttrs(): List<String> {
            val out = mutableListOf<String>()
            val sb = StringBuilder()
            var quote = 0.toChar()
            var i = 0
            while (i < length) {
                val c = this[i]
                if (quote != 0.toChar()) {
                    sb.append(c)
                    if (c == quote) quote = 0.toChar()
                } else if (c == '"' || c == '\'') {
                    quote = c
                    sb.append(c)
                } else if (c.isWhitespace()) {
                    if (sb.isNotEmpty()) {
                        out += sb.toString()
                        sb.setLength(0)
                    }
                } else {
                    sb.append(c)
                }
                i++
            }
            if (sb.isNotEmpty()) out += sb.toString()
            return out
        }

        private fun parseAttrs(parts: List<String>): Map<String, String> {
            if (parts.isEmpty()) return emptyMap()
            val map = linkedMapOf<String, String>()
            for (p in parts) {
                val eq = p.indexOf('=')
                if (eq < 0) {
                    map[p.lowercase()] = ""
                    continue
                }
                val k = p.substring(0, eq).lowercase()
                var v = p.substring(eq + 1)
                if ((v.startsWith("\"") && v.endsWith("\"")) ||
                    (v.startsWith("'") && v.endsWith("'"))
                ) {
                    v = v.substring(1, v.length - 1)
                }
                map[k] = decodeEntities(v)
            }
            return map
        }
    }
}
