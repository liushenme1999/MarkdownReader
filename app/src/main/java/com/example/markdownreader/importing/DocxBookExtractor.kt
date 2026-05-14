package com.example.markdownreader.importing

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.util.zip.ZipFile

/**
 * DOCX (Office Open XML) → Markdown 结构化提取。
 *
 * 参考 docx 解析的常见做法（mammoth.js、Apache POI 的 ExtractorFactory），但只做轻量解析：
 * - `word/document.xml` —— 主文档
 *   - `w:p` 段落 / `w:pPr/w:pStyle@w:val=Heading\d` → ATX 标题
 *   - `w:r/w:rPr` 中 `w:b` `w:i` `w:strike` → `**` / `*` / `~~`
 *   - `w:r/w:t` 文本
 *   - `w:hyperlink` + `w:rId` → `[text](url)`（通过 relationships 表查 url）
 *   - `w:drawing` 中 `a:blip@r:embed=rId` → `![alt](book-asset://<id>)`
 *   - `w:tbl/w:tr/w:tc` → GFM 表格
 *   - `w:numPr/w:ilvl` 列表层级近似为 `- ` 嵌套（DOCX 的 numbering 不解析，仅做简单缩进）
 * - `word/_rels/document.xml.rels` —— 关系映射 rId → Target
 * - `word/media/` 目录 —— 图片资源
 *
 * 复杂样式（脚注、批注、目录字段、SmartArt）暂不处理；含公式（OMML）会被尽量保留为文字。
 */
internal object DocxBookExtractor {

    private const val MAX_DOCX_BYTES = 16 * 1024 * 1024

    fun extract(file: File): ExtractedBookText? {
        if (file.length() > MAX_DOCX_BYTES) return null
        return ZipFile(file).use { zip ->
            val relsXml = zip.entries().asSequence()
                .firstOrNull { it.name.equals("word/_rels/document.xml.rels", ignoreCase = true) }
                ?.let { ent -> zip.getInputStream(ent).use { it.readBytes().decodeToString() } }
                .orEmpty()
            val relsMap = parseRelationships(relsXml)
            val docEntry = zip.entries().asSequence()
                .firstOrNull { it.name.equals("word/document.xml", ignoreCase = true) }
                ?: return null
            val docXml = zip.getInputStream(docEntry).use { it.readBytes().decodeToString() }
            val assets = linkedMapOf<String, ByteArray>()
            val markdown = renderDocumentXmlToMarkdown(docXml, relsMap, zip, assets)
            if (markdown.isBlank()) return null
            val body = markdown.take(MAX_TEXT_CHARS_DOCX)
            val toc = parseHeadingsForToc(body)
            ExtractedBookText(body = body, toc = toc, coverImageBytes = null, assets = assets)
        }
    }

    private const val MAX_TEXT_CHARS_DOCX = 8 * 1024 * 1024

    private fun parseRelationships(xml: String): Map<String, String> {
        if (xml.isBlank()) return emptyMap()
        val map = linkedMapOf<String, String>()
        return try {
            val parser = newParser()
            parser.setInput(xml.reader())
            var ev = parser.eventType
            while (ev != XmlPullParser.END_DOCUMENT) {
                if (ev == XmlPullParser.START_TAG && (parser.name ?: "").lowercase() == "relationship") {
                    val id = parser.getAttributeValue(null, "Id") ?: ""
                    val target = parser.getAttributeValue(null, "Target") ?: ""
                    if (id.isNotEmpty() && target.isNotEmpty()) map[id] = target
                }
                ev = parser.next()
            }
            map
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun renderDocumentXmlToMarkdown(
        xml: String,
        rels: Map<String, String>,
        zip: ZipFile,
        assets: MutableMap<String, ByteArray>
    ): String {
        val out = StringBuilder()
        val parser = newParser()
        parser.setInput(xml.reader())

        var inParagraph = false
        var paragraphStyle: String? = null
        var listLevel = -1
        var isListItem = false

        val inlineBuf = StringBuilder()

        // 当前 run 的样式
        var rBold = false
        var rItalic = false
        var rStrike = false

        // 当前 cell / table
        var inTable = false
        val rows = mutableListOf<List<String>>()
        var currentRow: MutableList<String>? = null
        var inCell = false
        val cellBuf = StringBuilder()
        var cellInlineBuf: StringBuilder? = null

        // hyperlink
        var hyperlinkAnchorStart = -1
        var hyperlinkHref: String? = null
        var hyperlinkActiveBuf: StringBuilder? = null

        fun activeInline(): StringBuilder = cellInlineBuf ?: inlineBuf

        fun appendText(text: String) {
            if (text.isEmpty()) return
            val sb = StringBuilder(text.length + 4)
            for (ch in text) when (ch) {
                '\\' -> sb.append("\\\\")
                '*' -> sb.append("\\*")
                '_' -> sb.append("\\_")
                '`' -> sb.append("\\`")
                '[' -> sb.append("\\[")
                ']' -> sb.append("\\]")
                '|' -> if (inCell) sb.append("\\|") else sb.append('|')
                else -> sb.append(ch)
            }
            val piece = sb.toString()
            val target = activeInline()
            // 应用当前 run 样式
            val wrappedStart = StringBuilder()
            val wrappedEnd = StringBuilder()
            if (rBold) { wrappedStart.append("**"); wrappedEnd.insert(0, "**") }
            if (rItalic) { wrappedStart.append("*"); wrappedEnd.insert(0, "*") }
            if (rStrike) { wrappedStart.append("~~"); wrappedEnd.insert(0, "~~") }
            target.append(wrappedStart).append(piece).append(wrappedEnd)
        }

        fun flushParagraph() {
            val text = inlineBuf.toString().trim()
            inlineBuf.setLength(0)
            if (text.isEmpty() && paragraphStyle == null && !isListItem) return

            val level = paragraphStyle?.let { headingLevelFromStyle(it) }
            when {
                level != null -> {
                    if (out.isNotEmpty() && !out.endsWith("\n\n")) ensureBlank(out)
                    out.append("#".repeat(level)).append(' ').append(text).append("\n\n")
                }
                isListItem -> {
                    val indent = "  ".repeat(listLevel.coerceAtLeast(0))
                    out.append(indent).append("- ").append(text).append('\n')
                }
                else -> {
                    if (out.isNotEmpty() && !out.endsWith("\n\n")) ensureBlank(out)
                    out.append(text).append("\n\n")
                }
            }
        }

        fun finishCell() {
            currentRow?.add(cellBuf.toString().trim())
            cellBuf.setLength(0)
            cellInlineBuf = null
            inCell = false
        }

        fun finishRow() {
            currentRow?.let { rows += it }
            currentRow = null
        }

        fun flushTable() {
            if (rows.isEmpty()) return
            val cols = rows.maxOf { it.size }
            if (cols == 0) { rows.clear(); return }
            val padded = rows.map { row ->
                if (row.size == cols) row else row + List(cols - row.size) { "" }
            }
            ensureBlank(out)
            val header = padded[0]
            out.append("| ").append(header.joinToString(" | ") { sanitizeCell(it) }).append(" |\n")
            out.append("|")
            repeat(cols) { out.append(" --- |") }
            out.append('\n')
            for (i in 1 until padded.size) {
                val row = padded[i]
                out.append("| ").append(row.joinToString(" | ") { sanitizeCell(it) }).append(" |\n")
            }
            out.append('\n')
            rows.clear()
        }

        var ev = parser.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            when (ev) {
                XmlPullParser.START_TAG -> {
                    val local = (parser.name ?: "").substringAfter(':').lowercase()
                    when (local) {
                        "p" -> {
                            inParagraph = true
                            paragraphStyle = null
                            isListItem = false
                            listLevel = -1
                        }
                        "pstyle" -> {
                            val v = parser.getAttributeValue(null, "val")
                                ?: parser.getAttributeValue("http://schemas.openxmlformats.org/wordprocessingml/2006/main", "val")
                            paragraphStyle = v
                        }
                        "numpr" -> isListItem = true
                        "ilvl" -> {
                            val v = parser.getAttributeValue(null, "val")
                                ?: parser.getAttributeValue("http://schemas.openxmlformats.org/wordprocessingml/2006/main", "val")
                            listLevel = v?.toIntOrNull() ?: 0
                        }
                        "r" -> { rBold = false; rItalic = false; rStrike = false }
                        "b" -> rBold = true
                        "i" -> rItalic = true
                        "strike", "dstrike" -> rStrike = true
                        "br" -> activeInline().append("  \n")
                        "tab" -> activeInline().append("    ")
                        "t" -> {
                            val text = readTextAccumulated(parser)
                            appendText(text)
                            // readTextAccumulated 已把 parser 推进到 END_TAG，避免 next() 重复消费
                            continue
                        }
                        "hyperlink" -> {
                            val rId = parser.getAttributeValue(null, "id")
                                ?: parser.getAttributeValue("http://schemas.openxmlformats.org/officeDocument/2006/relationships", "id")
                            hyperlinkHref = rId?.let { rels[it] }
                            hyperlinkActiveBuf = activeInline()
                            hyperlinkAnchorStart = hyperlinkActiveBuf!!.length
                        }
                        "tbl" -> {
                            flushParagraph()
                            inTable = true
                            rows.clear()
                        }
                        "tr" -> if (inTable) currentRow = mutableListOf()
                        "tc" -> if (inTable) {
                            inCell = true
                            cellBuf.setLength(0)
                            cellInlineBuf = cellBuf
                        }
                        "blip" -> {
                            val rId = parser.getAttributeValue(null, "embed")
                                ?: parser.getAttributeValue("http://schemas.openxmlformats.org/officeDocument/2006/relationships", "embed")
                            if (rId != null) {
                                val target = rels[rId]
                                if (!target.isNullOrBlank()) {
                                    val collected = collectDocxAssetWithSize(zip, target, assets)
                                    if (collected != null) {
                                        // 用 HTML <img width=".." height=".."> 预声明尺寸——首次 measure 就给图片
                                        // 预留高度，避免后续异步加载完成时 TextView 整段 relayout（连环卡顿根因）。
                                        activeInline().append(
                                            ImageAssetUtils.buildImgTag(
                                                "book-asset://${collected.assetId}",
                                                alt = null,
                                                width = collected.width,
                                                height = collected.height
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    val local = (parser.name ?: "").substringAfter(':').lowercase()
                    when (local) {
                        "p" -> {
                            if (inCell) {
                                if (cellBuf.isNotEmpty()) cellBuf.append("<br>")
                            } else {
                                flushParagraph()
                            }
                            inParagraph = false
                            paragraphStyle = null
                            isListItem = false
                            listLevel = -1
                        }
                        "hyperlink" -> {
                            val buf = hyperlinkActiveBuf
                            val href = hyperlinkHref
                            if (buf != null && hyperlinkAnchorStart >= 0 && !href.isNullOrBlank()) {
                                val content = buf.substring(hyperlinkAnchorStart)
                                if (content.isNotBlank()) {
                                    buf.setLength(hyperlinkAnchorStart)
                                    buf.append('[').append(content).append("](").append(href).append(')')
                                }
                            }
                            hyperlinkHref = null
                            hyperlinkActiveBuf = null
                            hyperlinkAnchorStart = -1
                        }
                        "tc" -> if (inTable) finishCell()
                        "tr" -> if (inTable) finishRow()
                        "tbl" -> {
                            inTable = false
                            flushTable()
                        }
                        "r" -> { rBold = false; rItalic = false; rStrike = false }
                    }
                }
            }
            ev = parser.next()
        }
        // 收尾
        flushParagraph()
        return out.toString().replace(Regex("\n{3,}"), "\n\n").trim()
    }

    /** `<w:t>...</w:t>` 节点内通常只有文本但也可能含 `xml:space="preserve"`，需要一次性读完。 */
    private fun readTextAccumulated(parser: XmlPullParser): String {
        val sb = StringBuilder()
        val depth = parser.depth
        var ev = parser.next()
        while (!(ev == XmlPullParser.END_TAG && parser.depth == depth)) {
            if (ev == XmlPullParser.TEXT) sb.append(parser.text)
            if (ev == XmlPullParser.END_DOCUMENT) break
            ev = parser.next()
        }
        return sb.toString()
    }

    private data class CollectedAsset(val assetId: String, val width: Int?, val height: Int?)

    private fun collectDocxAssetWithSize(
        zip: ZipFile,
        target: String,
        assets: MutableMap<String, ByteArray>
    ): CollectedAsset? {
        val cleaned = target.trim().trimStart('/').removePrefix("./")
        // Target 相对 word/_rels/document.xml.rels 所在的 word/ 目录
        val tryPaths = listOf("word/$cleaned", cleaned)
        val ent = tryPaths.firstNotNullOfOrNull { p ->
            zip.entries().asSequence().firstOrNull { it.name.equals(p, ignoreCase = true) }
        } ?: return null
        val bytes = runCatching { zip.getInputStream(ent).use { it.readBytes() } }.getOrNull() ?: return null
        if (bytes.isEmpty()) return null
        val ext = ent.name.substringAfterLast('.', "").lowercase().take(6).ifBlank { "bin" }
        val hash = ent.name.lowercase().hashCode().toUInt().toString(16).padStart(8, '0')
        val assetId = "docx_$hash.$ext"
        assets.putIfAbsent(assetId, bytes)
        val size = ImageAssetUtils.decodeImageSize(bytes)
        return CollectedAsset(assetId, size?.first, size?.second)
    }

    private fun parseHeadingsForToc(markdown: String): List<ImportedTocEntry> {
        val out = mutableListOf<ImportedTocEntry>()
        val re = Regex("(?m)^(#{1,6})\\s+(.+?)\\s*$")
        for (m in re.findAll(markdown)) {
            val level = m.groupValues[1].length.coerceIn(1, 6)
            val title = m.groupValues[2].trim().take(120)
            if (title.isEmpty()) continue
            out += ImportedTocEntry(level = level, title = title, sourceOffset = m.range.first)
        }
        return out.distinctBy { it.sourceOffset to it.title }
    }

    private fun headingLevelFromStyle(style: String): Int? {
        // Word 标题样式：Heading1..Heading9 / 标题 1..标题 9
        val s = style.lowercase()
        val m = Regex("(?:heading|title)?\\s*(\\d+)").find(s)
        val n = m?.groupValues?.get(1)?.toIntOrNull() ?: return null
        return n.coerceIn(1, 6)
    }

    private fun sanitizeCell(s: String): String =
        s.replace('\n', ' ').replace(Regex("\\s+"), " ").replace("|", "\\|").trim()

    private fun ensureBlank(sb: StringBuilder) {
        if (sb.isEmpty()) return
        if (sb.endsWith("\n\n")) return
        if (sb.endsWith("\n")) sb.append('\n') else sb.append("\n\n")
    }

    private fun newParser(): XmlPullParser =
        XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }.newPullParser()
}
