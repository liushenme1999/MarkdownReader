package space.liushenme.markdownreader.markdown

import space.liushenme.markdownreader.importing.ImageAssetUtils

/**
 * 在 Markwon 解析前扩展 GFM / 增强语法：脚注、Mermaid/ECharts 围栏图等。
 * 不在正文展开 `[TOC]`，以免改变字符长度导致阅读进度（源码坐标）与视口错位。
 */
object MarkdownPreprocessor {

    private val DIAGRAM_LANGS = setOf("mermaid", "echarts", "chart")
    private val FENCE_OPEN = Regex("^```\\s*(\\S+)?\\s*$")

    private val HTML_IMG = Regex("""<img\s+([^>]*?)\s*/?>""", RegexOption.IGNORE_CASE)
    private val ATTR_SRC = Regex("""\bsrc\s*=\s*("([^"]*)"|'([^']*)'|([^"'\s>]+))""", RegexOption.IGNORE_CASE)
    private val ATTR_ALT = Regex("""\balt\s*=\s*("([^"]*)"|'([^']*)'|([^"'\s>]+))""", RegexOption.IGNORE_CASE)
    /** 去掉空 center 及其前后空行，避免留下无内容段落。 */
    private val EMPTY_CENTER_BLOCK = Regex("""\n*<center>\s*</center>\n*""", RegexOption.IGNORE_CASE)
    /** 标题行后若紧跟图片 / HTML 图，去掉中间空行（避免 H2 下划线与图之间大块留白）。 */
    private val HEADING_BEFORE_IMAGE = Regex(
        """(?m)^(#{1,6} .+)\n+(?=<img\s|\!\[|<a\s)""",
    )

    private val MD_LINKED_IMAGE = Regex("""\[\!\[([^\]]*)\]\(([^)\s]+)\)\]\(([^)\s]+)\)""")
    private val MD_IMAGE = Regex("""!\[([^\]]*)\]\(([^)\s]+)\)""")
    private val MD_LOCAL_IMAGE = Regex("""!\[([^\]]*)\]\(([^)\s]+)(?:\s+"[^"]*")?\)""")

    /** GFM 表格分隔行，如 `| --- | :---: |` */
    private val TABLE_SEPARATOR = Regex("""^\|(?:\s*:?-+:?\s*\|)+\s*$""")

    /**
     * 导出文档常见 `<div align="center">$$...$$</div>`；
     * [io.noties.markwon.html.HtmlPlugin] 不会对其中的 `$$` 再走 LaTeX 解析。
     */
    private val CENTERED_LATEX_DIV = Regex(
        """<div\s+align\s*=\s*["']center["'][^>]*>\s*(\$\$[\s\S]*?\$\$)\s*</div>""",
        RegexOption.IGNORE_CASE,
    )
    private val LATEX_ONLY_DIV = Regex(
        """<div[^>]*>\s*(\$\$[\s\S]*?\$\$)\s*</div>""",
        RegexOption.IGNORE_CASE,
    )
    private val BLOCK_LATEX_LINE = Regex("""^\s*\$\$[\s\S]*\$\$\s*$""")
    /** 单行 `$$…$$`：Markwon 块解析器要求 `$$` 独占一行，否则走 InlineProcessor。 */
    private val SINGLE_LINE_BLOCK_LATEX = Regex("""^\s*\$\$(.+)\$\$\s*$""")

    fun prepare(markdown: String): String {
        if (markdown.isEmpty()) return markdown
        var out = expandHighlight(markdown)
        out = unwrapCenteredLatexDivs(out)
        out = expandSingleLineBlockLatex(out)
        out = normalizeBlockLatexSurroundings(out)
        out = expandFootnotes(out)
        out = expandDiagramFences(out)
        out = ensureBlankLineBeforeTables(out)
        out = stripLocalRelativeImages(out)
        out = normalizeReaderImageLayout(out)
        return out
    }

    /**
     * Typora 等编辑器允许「段落/标题后直接接表格」；CommonMark / Markwon 要求表格前有空行。
     * 仅在检测到 GFM 表格块（表头行 + 分隔行）且上一行非空、非表格行时补一行，不改变书籍源码存储。
     */
    internal fun ensureBlankLineBeforeTables(markdown: String): String {
        if (!markdown.contains('|')) return markdown
        val lines = markdown.split('\n')
        if (lines.size < 2) return markdown
        return buildString {
            lines.forEachIndexed { i, line ->
                if (i > 0) {
                    if (isTableBlockStart(lines, i)) {
                        val prev = lines[i - 1]
                        if (prev.isNotBlank() && !isTableLine(prev)) {
                            append('\n')
                        }
                    }
                    append('\n')
                }
                append(line)
            }
        }
    }

    private fun isTableBlockStart(lines: List<String>, index: Int): Boolean {
        if (index + 1 >= lines.size) return false
        return isTableLine(lines[index]) && isTableSeparatorLine(lines[index + 1])
    }

    private fun isTableLine(line: String): Boolean {
        val trimmed = line.trim()
        if (!trimmed.startsWith('|')) return false
        return trimmed.indexOf('|', startIndex = 1) >= 0
    }

    private fun isTableSeparatorLine(line: String): Boolean {
        val trimmed = line.trim()
        if (!trimmed.startsWith('|')) return false
        return TABLE_SEPARATOR.matches(trimmed)
    }

    /**
     * - 去掉空的 `<center></center>`
     * - 保留 HTML `<img>`（带 width/height 时 Markwon 可预占位，避免「空白高度=图片高度」的双倍占位）
     * - 为 picsum 等 URL 补全像素 height（仅有 width% 时）
     * - 收紧「标题 → 图片」之间的空行
     */
    internal fun normalizeReaderImageLayout(markdown: String): String {
        var out = markdown.replace(EMPTY_CENTER_BLOCK, "\n")
        out = convertNetworkImagesToHtmlImg(out)
        out = enrichHtmlImgTags(out)
        out = HEADING_BEFORE_IMAGE.replace(out) { m -> "${m.groupValues[1]}\n" }
        return out
    }

    /**
     * 去掉无法由 Markwon 加载的相对 / 本地路径图片（如 `static/foo.jpg`），
     * 仅保留 alt 文本，避免 `No scheme is found` 日志与 layout 抖动。
     */
    internal fun stripLocalRelativeImages(markdown: String): String {
        if (!markdown.contains("![") && !markdown.contains("<img", ignoreCase = true)) return markdown
        var out = MD_LOCAL_IMAGE.replace(markdown) { m ->
            val alt = m.groupValues[1]
            val raw = m.groupValues[2].trim()
            if (isLoadableImageRef(raw)) return@replace m.value
            alt
        }
        if (!out.contains("<img", ignoreCase = true)) return out
        return HTML_IMG.replace(out) { m ->
            val attrs = m.groupValues[1]
            val src = extractAttr(ATTR_SRC, attrs) ?: return@replace m.value
            if (isLoadableImageRef(src)) return@replace m.value
            extractAttr(ATTR_ALT, attrs).orEmpty()
        }
    }

    internal fun isLoadableImageRef(raw: String): Boolean {
        val path = raw.trim()
        if (path.startsWith("diagram://") || path.startsWith("book-asset://")) return true
        val scheme = path.substringBefore(':', missingDelimiterValue = path)
        if (scheme == path) return false
        return scheme.length in 2..32 && scheme.all { it.isLetter() }
    }

    private val PICSUM_SIZE_IN_URL = Regex("""picsum\.photos/(\d+)/(\d+)""")

    /**
     * 将网络图从 `![]()` / `[![](img)](url)` 转为 HTML，与 [ImageAssetUtils] 一致；
     * 含尺寸的 URL 可预占位，嵌套超链接统一为 `<a><img></a>` 以便 Markwon 正确挂接 LinkSpan。
     */
    internal fun convertNetworkImagesToHtmlImg(markdown: String): String {
        if (!markdown.contains("![")) return markdown
        var out = MD_LINKED_IMAGE.replace(markdown) { m ->
            val alt = m.groupValues[1]
            val imgUrl = m.groupValues[2]
            val linkUrl = m.groupValues[3]
            if (imgUrl.startsWith("diagram://")) return@replace m.value
            val dims = picsumPixelSize(imgUrl)
            val img = if (dims != null) {
                ImageAssetUtils.buildImgTag(imgUrl, alt.takeIf { it.isNotBlank() }, dims.first, dims.second)
            } else {
                ImageAssetUtils.buildImgTag(imgUrl, alt.takeIf { it.isNotBlank() })
            }
            """<a href="$linkUrl">$img</a>"""
        }
        out = MD_IMAGE.replace(out) { m ->
            val alt = m.groupValues[1]
            val url = m.groupValues[2]
            if (url.startsWith("diagram://")) return@replace m.value
            val dims = picsumPixelSize(url) ?: return@replace m.value
            ImageAssetUtils.buildImgTag(url, alt.takeIf { it.isNotBlank() }, dims.first, dims.second)
        }
        return out
    }

    private fun picsumPixelSize(url: String): Pair<Int, Int>? {
        val m = PICSUM_SIZE_IN_URL.find(url) ?: return null
        val w = m.groupValues[1].toIntOrNull()?.takeIf { it > 0 } ?: return null
        val h = m.groupValues[2].toIntOrNull()?.takeIf { it > 0 } ?: return null
        return w to h
    }

    /** 保留 `<img>`，仅补全缺失的 height（与 [importing.ImageAssetUtils] 策略一致）。 */
    internal fun enrichHtmlImgTags(markdown: String): String {
        if (!markdown.contains("<img", ignoreCase = true)) return markdown
        return HTML_IMG.replace(markdown) { match ->
            val attrs = match.groupValues[1]
            if (attrs.contains("height=", ignoreCase = true)) {
                return@replace match.value
            }
            val src = extractAttr(ATTR_SRC, attrs) ?: return@replace match.value
            val dims = picsumPixelSize(src) ?: return@replace match.value
            val ih = dims.second
            val tag = match.value.trimEnd().removeSuffix("/>").removeSuffix(">")
            val withHeight = "$tag height=\"$ih\"/>"
            withHeight
        }
    }

    private fun extractAttr(pattern: Regex, attrs: String): String? {
        val m = pattern.find(attrs) ?: return null
        return m.groupValues.drop(2).firstOrNull { it.isNotEmpty() }
    }

    /** 将 HTML 包裹的块级 `$$...$$` 还原为独立公式块（去掉 div/center 与行首缩进）。 */
    internal fun unwrapCenteredLatexDivs(markdown: String): String {
        if (!markdown.contains("$$")) return markdown
        if (!markdown.contains("<div", ignoreCase = true) &&
            !markdown.contains("<center", ignoreCase = true)
        ) {
            return markdown
        }
        var out = CENTERED_LATEX_DIV.replace(markdown) { unwrapLatexBlockReplacement(it.groupValues[1]) }
        out = LATEX_ONLY_DIV.replace(out) { unwrapLatexBlockReplacement(it.groupValues[1]) }
        if (markdown.contains("<center", ignoreCase = true)) {
            out = Regex(
                """<center>\s*(\$\$[\s\S]*?\$\$)\s*</center>""",
                RegexOption.IGNORE_CASE,
            ).replace(out) { unwrapLatexBlockReplacement(it.groupValues[1]) }
        }
        return out
    }

    /**
     * 将单行 `$$…$$` 展开为 Markwon 可识别的块级格式（开/闭 `$$` 各占一行）。
     * 否则 [io.noties.markwon.ext.latex.JLatexMathInlineProcessor] 会将其当作行内公式，
     * 块级主题的居中与边框不会生效。
     */
    internal fun expandSingleLineBlockLatex(markdown: String): String {
        if (!markdown.contains("$$")) return markdown
        val lines = markdown.split('\n')
        return buildString {
            lines.forEachIndexed { index, line ->
                if (index > 0) append('\n')
                val expanded = expandSingleLineBlockLatexLine(line)
                expanded.forEachIndexed { expandedIndex, expandedLine ->
                    if (expandedIndex > 0) append('\n')
                    append(expandedLine)
                }
            }
        }
    }

    private fun expandSingleLineBlockLatexLine(line: String): List<String> {
        val match = SINGLE_LINE_BLOCK_LATEX.matchEntire(line.trim()) ?: return listOf(line)
        val body = match.groupValues[1].trim()
        if (body.isEmpty()) return listOf("$$", "$$")
        return listOf("$$", body, "$$")
    }

    private fun unwrapLatexBlockReplacement(body: String): String {
        val latex = body.trim()
        if (!latex.startsWith("$$") || !latex.endsWith("$$")) return body
        return "\n$latex\n"
    }

    /**
     * 块级 `$$` 被提到行首后会结束当前列表/缩进块；若前文原本有缩进，则把后续仍缩进的正文还原到行首，
     * 否则 CommonMark 会把 `    4. …` / `        段落` 判成代码块。
     */
    internal fun normalizeBlockLatexSurroundings(markdown: String): String {
        if (!markdown.contains("$$")) return markdown
        val lines = markdown.split('\n')
        val out = ArrayList<String>(lines.size + 8)
        var repairIndentedTail = false
        var inBlockLatex = false
        for (index in lines.indices) {
            val line = lines[index]
            val trimmed = line.trim()
            if (line.isBlank()) {
                if (repairIndentedTail) continue
                if (inBlockLatex) {
                    out.add("")
                    continue
                }
                if (out.lastOrNull()?.trim() == "$$") continue
                if (out.lastOrNull()?.isBlank() == true) continue
                out.add("")
                continue
            }
            if (trimmed == "$$") {
                if (inBlockLatex) {
                    out.add("$$")
                    inBlockLatex = false
                    repairIndentedTail = hasIndentedFollowingLine(lines, index)
                } else {
                    out.add("$$")
                    inBlockLatex = true
                }
                continue
            }
            if (inBlockLatex) {
                out.add(line)
                continue
            }
            if (BLOCK_LATEX_LINE.matches(line)) {
                out.addAll(expandSingleLineBlockLatexLine(line))
                repairIndentedTail = hasIndentedFollowingLine(lines, index)
                continue
            }
            if (repairIndentedTail) {
                if (shouldStopRepairAfterBlockLatex(trimmed, line)) {
                    repairIndentedTail = false
                    out.add(line)
                    continue
                }
                out.add(line.trimStart())
                continue
            }
            out.add(line)
        }
        return out.joinToString("\n")
    }

    private fun hasIndentedFollowingLine(lines: List<String>, blockLatexIndex: Int): Boolean {
        for (i in blockLatexIndex + 1 until lines.size) {
            val next = lines[i]
            if (next.isBlank()) continue
            return next.startsWith(" ") || next.startsWith("\t")
        }
        return false
    }

    private fun shouldStopRepairAfterBlockLatex(trimmed: String, raw: String): Boolean {
        if (trimmed.startsWith("#")) return true
        if (trimmed.startsWith("```")) return true
        if (trimmed.startsWith("|") && trimmed.lastIndexOf('|') > 0) return true
        return !raw.startsWith(" ") && !raw.startsWith("\t")
    }

    /** `==高亮==` → `<mark>`（由 [ReaderHtmlPlugin] 渲染）。 */
    internal fun expandHighlight(markdown: String): String {
        if (!markdown.contains("==")) return markdown
        return Regex("""==([^=\n][^=\n]*?)==""").replace(markdown) { match ->
            val inner = match.groupValues[1]
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
            "<mark>$inner</mark>"
        }
    }

    /**
     * GFM 脚注：引用 `[^id]`，定义 `[^id]: text`（支持续行缩进）。
     * 转为 HTML，由 [io.noties.markwon.html.HtmlPlugin] 渲染。
     */
    internal fun expandFootnotes(markdown: String): String {
        val lines = markdown.split('\n')
        val definitions = linkedMapOf<String, String>()
        val defLineIndices = mutableSetOf<Int>()
        var i = 0
        while (i < lines.size) {
            val trimmed = lines[i].trim()
            val defMatch = Regex("""^\[\^([^\]]+)\]:\s?(.*)$""").matchEntire(trimmed)
            if (defMatch != null) {
                val id = defMatch.groupValues[1]
                val body = StringBuilder(defMatch.groupValues[2].trim())
                defLineIndices.add(i)
                var j = i + 1
                while (j < lines.size) {
                    val next = lines[j]
                    if (next.isEmpty()) break
                    if (next.startsWith("    ") || next.startsWith("\t")) {
                        if (body.isNotEmpty()) body.append(' ')
                        body.append(next.trim())
                        defLineIndices.add(j)
                        j++
                    } else {
                        break
                    }
                }
                definitions[id] = body.toString()
                i = j
                continue
            }
            i++
        }
        if (definitions.isEmpty()) return markdown

        val refPattern = Regex("""\[\^([^\]]+)\]""")
        val bodyWithoutDefs = buildString {
            lines.forEachIndexed { index, line ->
                if (index in defLineIndices) return@forEachIndexed
                append(line)
                if (index < lines.lastIndex) append('\n')
            }
        }
        val refOrder = mutableListOf<String>()
        refPattern.findAll(bodyWithoutDefs).forEach { m ->
            val id = m.groupValues[1]
            if (id in definitions && id !in refOrder) refOrder.add(id)
        }
        val body = refPattern.replace(bodyWithoutDefs) { m ->
            val id = m.groupValues[1]
            if (id !in definitions) return@replace m.value
            if (id !in refOrder) refOrder.add(id)
            val num = refOrder.indexOf(id) + 1
            """<sup id="fnref-$num"><a href="#fn-$num">$num</a></sup>"""
        }

        val reordered = refOrder.ifEmpty { definitions.keys.toList() }
        val footer = buildString {
            // 脚注区前必须有空行，否则 `---` 会被 Markwon 解析成 Setext 标题下划线，
            // 把正文最后一行（如「# 二十、…」下的长段落）误渲染为 HeadingSpan，导致目录 rank 错位。
            appendLine()
            appendLine()
            appendLine("---")
            appendLine("**脚注**")
            appendLine("<ol>")
            reordered.forEachIndexed { idx, id ->
                val n = idx + 1
                val text = definitions[id].orEmpty()
                    .replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                append("""<li id="fn-$n">$text <a href="#fnref-$n">↩</a></li>""")
                appendLine()
            }
            appendLine("</ol>")
        }
        return body.trimEnd() + footer
    }

    /** 将 ` ```mermaid ` / ` ```echarts ` 围栏转为 diagram:// 图片，供 [DiagramSchemeHandler] 异步渲染。 */
    internal fun expandDiagramFences(markdown: String): String {
        val lines = markdown.split('\n')
        val out = StringBuilder(markdown.length + 256)
        var i = 0
        var inFence = false
        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trim()
            if (!inFence && trimmed.startsWith("```")) {
                val open = FENCE_OPEN.matchEntire(trimmed)
                if (open != null) {
                    val lang = open.groupValues.getOrNull(1)?.substringBefore(' ')?.lowercase().orEmpty()
                    if (lang in DIAGRAM_LANGS) {
                        val code = StringBuilder()
                        i++
                        while (i < lines.size && !lines[i].trim().startsWith("```")) {
                            if (code.isNotEmpty()) code.append('\n')
                            code.append(lines[i])
                            i++
                        }
                        if (i < lines.size) i++
                        val type = if (lang == "chart") "echarts" else lang
                        val uri = diagramUri(type, code.toString().trim())
                        out.append("![")
                        out.append(type)
                        out.append(" diagram](")
                        out.append(uri)
                        out.append(")\n\n")
                        continue
                    }
                    inFence = true
                }
            } else if (inFence && trimmed.startsWith("```")) {
                inFence = false
            }
            out.append(line)
            if (i < lines.lastIndex) out.append('\n')
            i++
        }
        return out.toString()
    }

    internal fun diagramUri(type: String, source: String): String {
        val id = DiagramPayloadStore.put(type, source)
        return "diagram://$type/$id"
    }
}
